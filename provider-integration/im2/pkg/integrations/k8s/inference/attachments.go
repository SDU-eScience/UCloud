package inference

import (
	"database/sql"
	"io"
	"mime"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"golang.org/x/sys/unix"
	ctrl "ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const attachmentSubPath = "Inference/Attachments"
const attachmentRetention = 14 * 24 * time.Hour

type Attachment struct {
	Id        string
	CreatedBy string
	ProjectId util.Option[string]
}

type attachmentRow struct {
	Id        string
	CreatedBy string
	ProjectId sql.NullString
}

type attachmentDownloadRequest struct {
	Id string `json:"id"`
}

type attachmentDownloadResponse struct {
	Id   string
	File *os.File
	Info os.FileInfo
}

var attachmentDownloadRpc = rpc.Call[attachmentDownloadRequest, attachmentDownloadResponse]{
	Convention: rpc.ConventionQueryParameters,
	CustomServerProducer: func(response attachmentDownloadResponse, err *util.HttpError, w http.ResponseWriter, r *http.Request) {
		if err != nil {
			http.Error(w, err.Why, err.StatusCode)
			return
		}
		defer util.SilentClose(response.File)

		if contentType := mime.TypeByExtension(filepath.Ext(response.Id)); contentType != "" {
			w.Header().Set("Content-Type", contentType)
		}
		http.ServeContent(w, r, response.Id, response.Info.ModTime(), response.File)
	},
	BaseContext: "inference/attachments",
	Operation:   "download",
	Roles:       rpc.RolesPublic,
}

func AttachmentInit() {
	attachmentDownloadRpc.Handler(func(info rpc.RequestInfo, request attachmentDownloadRequest) (attachmentDownloadResponse, *util.HttpError) {
		attachment, ok := attachmentLookup(request.Id)
		if !ok {
			return attachmentDownloadResponse{}, util.HttpErr(http.StatusNotFound, "attachment not found")
		}

		path, _, err := attachmentPath(attachment)
		if err != nil {
			return attachmentDownloadResponse{}, util.HttpErr(http.StatusNotFound, "attachment not found")
		}

		file, opened := filesystem.OpenFile(path, unix.O_RDONLY, 0)
		if !opened {
			return attachmentDownloadResponse{}, util.HttpErr(http.StatusNotFound, "attachment not found")
		}

		finfo, statErr := file.Stat()
		if statErr != nil || finfo.IsDir() {
			util.SilentClose(file)
			return attachmentDownloadResponse{}, util.HttpErr(http.StatusNotFound, "attachment not found")
		}

		return attachmentDownloadResponse{Id: attachment.Id, File: file, Info: finfo}, nil
	})

	go func() {
		AttachmentDeleteExpired()
		ticker := time.NewTicker(time.Hour)
		defer ticker.Stop()
		for range ticker.C {
			AttachmentDeleteExpired()
		}
	}()
}

func AttachmentCreate(createdBy string, project util.Option[string], filename string) (Attachment, *util.HttpError) {
	createdBy = strings.TrimSpace(createdBy)
	if createdBy == "" {
		return Attachment{}, util.HttpErr(http.StatusBadRequest, "created by is required")
	}

	basePath, drive, err := filesystem.InitializeMemberFiles(createdBy, project)
	if err != nil {
		return Attachment{}, err
	}
	if ctrl.ResourceIsLocked(drive.Resource, drive.Specification.Product) {
		return Attachment{}, util.PaymentError()
	}

	ext := strings.ToLower(filepath.Ext(filepath.Base(filename)))
	id := util.SecureToken() + ext
	path := attachmentDirectory(basePath)
	if err := filesystem.DoCreateFolder(path); err != nil {
		return Attachment{}, err
	}

	file, ok := filesystem.OpenFile(attachmentPathFromBase(basePath, id), unix.O_WRONLY|unix.O_CREAT|unix.O_EXCL, 0660)
	if !ok {
		return Attachment{}, util.HttpErr(http.StatusInternalServerError, "could not create attachment")
	}
	util.SilentClose(file)

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into k8s.inference_attachments(id, created_by, project_id)
				values (:id, :created_by, :project_id)
			`,
			db.Params{
				"id":         id,
				"created_by": createdBy,
				"project_id": attachmentProjectSql(project),
			},
		)
	})

	return Attachment{Id: id, CreatedBy: createdBy, ProjectId: project}, nil
}

func AttachmentAppend(id string, data io.Reader) *util.HttpError {
	attachment, ok := attachmentLookup(id)
	if !ok {
		return util.HttpErr(http.StatusNotFound, "attachment not found")
	}

	path, drive, err := attachmentPath(attachment)
	if err != nil {
		return err
	}
	if ctrl.ResourceIsLocked(drive.Resource, drive.Specification.Product) {
		return util.PaymentError()
	}

	file, opened := filesystem.OpenFile(path, unix.O_WRONLY|unix.O_APPEND, 0)
	if !opened {
		return util.HttpErr(http.StatusNotFound, "attachment not found")
	}
	defer util.SilentClose(file)

	if _, copyErr := io.Copy(file, data); copyErr != nil {
		return util.HttpErr(http.StatusInternalServerError, "could not append attachment")
	}
	attachmentTouch(file)
	return nil
}

func AttachmentDelete(id string) *util.HttpError {
	attachment, ok := attachmentLookup(id)
	if !ok {
		return nil
	}

	path, _, err := attachmentPath(attachment)
	if err == nil {
		file, opened := filesystem.OpenFile(path, unix.O_RDONLY, 0)
		util.SilentClose(file)
		if opened {
			if deleteErr := filesystem.DoDeleteFile(path); deleteErr != nil {
				return deleteErr
			}
		}
	}
	attachmentDeleteMetadata(attachment.Id)
	return nil
}

func AttachmentDeleteExpired() {
	attachments := db.NewTx(func(tx *db.Transaction) []Attachment {
		rows := db.Select[attachmentRow](
			tx,
			`select id, created_by, project_id from k8s.inference_attachments where created_at < now() - cast('14 days' as interval)`,
			db.Params{},
		)
		result := make([]Attachment, 0, len(rows))
		for _, row := range rows {
			result = append(result, attachmentFromRow(row))
		}
		return result
	})

	cutoff := time.Now().Add(-attachmentRetention)
	for _, attachment := range attachments {
		path, _, err := attachmentPath(attachment)
		if err != nil {
			continue
		}

		file, opened := filesystem.OpenFile(path, unix.O_RDONLY, 0)
		if !opened {
			attachmentDeleteMetadata(attachment.Id)
			continue
		}
		info, statErr := file.Stat()
		util.SilentClose(file)
		if statErr != nil {
			continue
		}
		if !info.ModTime().Before(cutoff) {
			continue
		}

		if deleteErr := filesystem.DoDeleteFile(path); deleteErr != nil {
			log.Warn("Could not delete expired inference attachment %s: %v", attachment.Id, deleteErr)
			continue
		}
		attachmentDeleteMetadata(attachment.Id)
	}
}

func attachmentLookup(id string) (Attachment, bool) {
	id = strings.TrimSpace(id)
	if !attachmentValidId(id) {
		return Attachment{}, false
	}

	row, ok := db.NewTx2(func(tx *db.Transaction) (attachmentRow, bool) {
		return db.Get[attachmentRow](
			tx,
			`select id, created_by, project_id from k8s.inference_attachments where id = :id`,
			db.Params{"id": id},
		)
	})
	if !ok {
		return Attachment{}, false
	}
	return attachmentFromRow(row), true
}

func attachmentPath(attachment Attachment) (string, *orc.Drive, *util.HttpError) {
	basePath, drive, err := filesystem.InitializeMemberFiles(attachment.CreatedBy, attachment.ProjectId)
	if err != nil {
		return "", nil, err
	}
	return attachmentPathFromBase(basePath, attachment.Id), drive, nil
}

func attachmentDirectory(basePath string) string {
	return filepath.Join(basePath, attachmentSubPath)
}

func attachmentPathFromBase(basePath string, id string) string {
	return filepath.Join(attachmentDirectory(basePath), id)
}

func attachmentFromRow(row attachmentRow) Attachment {
	project := util.OptNone[string]()
	if row.ProjectId.Valid {
		project = util.OptValue(row.ProjectId.String)
	}
	return Attachment{Id: row.Id, CreatedBy: row.CreatedBy, ProjectId: project}
}

func attachmentProjectSql(project util.Option[string]) sql.NullString {
	if project.Present {
		return sql.NullString{String: project.Value, Valid: true}
	}
	return sql.NullString{}
}

func attachmentDeleteMetadata(id string) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(tx, `delete from k8s.inference_attachments where id = :id`, db.Params{"id": id})
	})
}

func attachmentTouch(file interface{ Fd() uintptr }) {
	now := time.Now()
	times := []unix.Timeval{
		unix.NsecToTimeval(now.UnixNano()),
		unix.NsecToTimeval(now.UnixNano()),
	}
	_ = unix.Futimes(int(file.Fd()), times)
}

func attachmentValidId(id string) bool {
	if id == "" || filepath.Base(id) != id || strings.Contains(id, "..") {
		return false
	}
	return true
}
