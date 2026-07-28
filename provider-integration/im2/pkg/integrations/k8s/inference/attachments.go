package inference

import (
	"context"
	"database/sql"
	"io"
	"mime"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"golang.org/x/sys/unix"
	cfg "ucloud.dk/pkg/config"
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
const attachmentMaxChunkBytes = 8 << 20
const attachmentMaxFileBytes = 64 << 20

type Attachment struct {
	Id                   string
	CreatedBy            string
	ProjectId            util.Option[string]
	Filename             string
	MarkdownAttachmentId string
}

type attachmentRow struct {
	Id                   string
	CreatedBy            string
	ProjectId            sql.NullString
	Filename             string
	MarkdownAttachmentId sql.NullString
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
		w.Header().Set("Cross-Origin-Resource-Policy", "cross-origin")
		w.Header().Set("Access-Control-Allow-Origin", cfg.Provider.Hosts.UCloudPublic.ToURL())
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
				insert into k8s.inference_attachments(id, created_by, project_id, filename)
				values (:id, :created_by, :project_id, :filename)
			`,
			db.Params{
				"id":         id,
				"created_by": createdBy,
				"project_id": attachmentProjectSql(project),
				"filename":   filepath.Base(filename),
			},
		)
	})

	return Attachment{Id: id, CreatedBy: createdBy, ProjectId: project, Filename: filepath.Base(filename)}, nil
}

func AttachmentAppend(id string, data io.Reader) *util.HttpError {
	chunk, readErr := io.ReadAll(io.LimitReader(data, attachmentMaxChunkBytes+1))
	if readErr != nil {
		return util.HttpErr(http.StatusBadRequest, "could not read attachment data")
	}
	if len(chunk) > attachmentMaxChunkBytes {
		return util.HttpErr(http.StatusRequestEntityTooLarge, "attachment chunk is too large")
	}

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
	if lockErr := unix.Flock(int(file.Fd()), unix.LOCK_EX); lockErr != nil {
		return util.HttpErr(http.StatusInternalServerError, "could not lock attachment")
	}
	defer func() { _ = unix.Flock(int(file.Fd()), unix.LOCK_UN) }()

	info, statErr := file.Stat()
	if statErr != nil {
		return util.HttpErr(http.StatusInternalServerError, "could not inspect attachment")
	}
	if info.Size()+int64(len(chunk)) > attachmentMaxFileBytes {
		return util.HttpErr(http.StatusRequestEntityTooLarge, "attachment is too large")
	}
	if _, copyErr := file.Write(chunk); copyErr != nil {
		return util.HttpErr(http.StatusInternalServerError, "could not append attachment")
	}
	attachmentTouch(file)
	return nil
}

func AttachmentConvertToMarkdown(ctx context.Context, id string) (Attachment, *util.HttpError) {
	attachment, ok := attachmentLookup(id)
	if !ok {
		return Attachment{}, util.HttpErr(http.StatusNotFound, "attachment not found")
	}
	if attachment.MarkdownAttachmentId != "" {
		markdown, ok := attachmentLookup(attachment.MarkdownAttachmentId)
		if ok {
			return markdown, nil
		}
	}

	path, drive, err := attachmentPath(attachment)
	if err != nil {
		return Attachment{}, err
	}
	source, ok := filesystem.InternalToUCloudWithDrive(drive, path)
	if !ok {
		return Attachment{}, util.HttpErr(http.StatusInternalServerError, "could not resolve attachment path")
	}
	markdownId := attachmentMarkdownId(attachment.Id)
	markdownPath := attachmentPathWithId(attachment, markdownId)
	markdownSource := util.Parent(source)

	waitCtx, cancel := context.WithTimeout(ctx, 125*time.Second)
	defer cancel()

	err = filesystem.TaskSubmitAndWait(
		waitCtx,
		filesystem.TaskSpec{
			Type:            filesystem.TaskTypeMarkItDown,
			Mounts:          []filesystem.TaskMount{{UCloudPath: markdownSource}},
			Source:          source,
			DeadlineSeconds: util.OptValue(120),
			CreationState: struct {
				Username string
				Icon     string
			}{Username: attachment.CreatedBy, Icon: "heroDocumentText"},
		},
		time.Second,
	)

	if err != nil {
		return Attachment{}, err
	}

	file, opened := filesystem.OpenFile(markdownPath, unix.O_RDONLY, 0)
	if !opened {
		return Attachment{}, util.HttpErr(http.StatusInternalServerError, "markdown attachment was not created")
	}
	util.SilentClose(file)

	markdown := Attachment{
		Id:        markdownId,
		CreatedBy: attachment.CreatedBy,
		ProjectId: attachment.ProjectId,
		Filename:  attachmentMarkdownFilename(attachment.Filename),
	}
	attachmentStoreMarkdownMetadata(attachment, markdown)
	return markdown, nil
}

func AttachmentDelete(id string) *util.HttpError {
	attachment, ok := attachmentLookup(id)
	if !ok {
		return nil
	}

	if err := attachmentDeleteFiles(attachment); err != nil {
		return err
	}
	attachmentDeleteMetadata(attachment.Id, attachment.MarkdownAttachmentId)
	return nil
}

func AttachmentDeleteExpired() {
	attachments := db.NewTx(func(tx *db.Transaction) []Attachment {
		rows := db.Select[attachmentRow](
			tx,
			`select id, created_by, project_id, filename, markdown_attachment_id from k8s.inference_attachments where created_at < now() - cast('14 days' as interval)`,
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
			attachmentDeleteMetadata(attachment.Id, attachment.MarkdownAttachmentId)
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

		if deleteErr := attachmentDeleteFiles(attachment); deleteErr != nil {
			log.Warn("Could not delete expired inference attachment %s: %v", attachment.Id, deleteErr)
			continue
		}
		attachmentDeleteMetadata(attachment.Id, attachment.MarkdownAttachmentId)
	}
}

func attachmentLookup(id string) (Attachment, bool) {
	if !attachmentValidId(id) {
		return Attachment{}, false
	}

	row, ok := db.NewTx2(func(tx *db.Transaction) (attachmentRow, bool) {
		return db.Get[attachmentRow](
			tx,
			`select id, created_by, project_id, filename, markdown_attachment_id from k8s.inference_attachments where id = :id`,
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

func attachmentPathWithId(attachment Attachment, id string) string {
	basePath, _, err := filesystem.InitializeMemberFiles(attachment.CreatedBy, attachment.ProjectId)
	if err != nil {
		return ""
	}
	return attachmentPathFromBase(basePath, id)
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
	markdownAttachmentId := ""
	if row.MarkdownAttachmentId.Valid {
		markdownAttachmentId = row.MarkdownAttachmentId.String
	}
	return Attachment{
		Id:                   row.Id,
		CreatedBy:            row.CreatedBy,
		ProjectId:            project,
		Filename:             row.Filename,
		MarkdownAttachmentId: markdownAttachmentId,
	}
}

func attachmentProjectSql(project util.Option[string]) sql.NullString {
	if project.Present {
		return sql.NullString{String: project.Value, Valid: true}
	}
	return sql.NullString{}
}

func attachmentDeleteMetadata(ids ...string) {
	if len(ids) == 0 {
		return
	}
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(tx, `delete from k8s.inference_attachments where id = some(:ids)`, db.Params{"ids": ids})
	})
}

func attachmentStoreMarkdownMetadata(original Attachment, markdown Attachment) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into k8s.inference_attachments(id, created_by, project_id, filename)
				values (:id, :created_by, :project_id, :filename)
				on conflict (id) do update set
					created_by = excluded.created_by,
					project_id = excluded.project_id,
					filename = excluded.filename
			`,
			db.Params{
				"id":         markdown.Id,
				"created_by": markdown.CreatedBy,
				"project_id": attachmentProjectSql(markdown.ProjectId),
				"filename":   markdown.Filename,
			},
		)
		db.Exec(
			tx,
			`update k8s.inference_attachments set markdown_attachment_id = :markdown_id where id = :id`,
			db.Params{"id": original.Id, "markdown_id": markdown.Id},
		)
	})
}

func attachmentDeleteFiles(attachment Attachment) *util.HttpError {
	ids := []string{attachment.Id}
	if attachment.MarkdownAttachmentId != "" {
		ids = append(ids, attachment.MarkdownAttachmentId)
	}
	for _, id := range ids {
		path := attachmentPathWithId(attachment, id)
		if path == "" {
			continue
		}
		file, opened := filesystem.OpenFile(path, unix.O_RDONLY, 0)
		util.SilentClose(file)
		if opened {
			if deleteErr := filesystem.DoDeleteFile(path); deleteErr != nil {
				return deleteErr
			}
		}
	}
	return nil
}

func attachmentMarkdownId(id string) string {
	return strings.TrimSuffix(id, filepath.Ext(id)) + ".md"
}

func attachmentMarkdownFilename(filename string) string {
	base := filepath.Base(filename)
	if base == "." || base == string(filepath.Separator) || base == "" {
		base = "attachment"
	}
	return strings.TrimSuffix(base, filepath.Ext(base)) + ".md"
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
