package filesystem

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sync"
	"time"

	"golang.org/x/sys/unix"
	"golang.org/x/time/rate"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/shared"
	apm "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type metadataDriveDeletion struct {
	DriveId            string
	Resource           string
	SourcePath         string
	SourceDevice       string
	SourceInode        string
	DriveStagingName   string
	CatalogStagingName string
	DriveStaged        bool
	CatalogObserved    bool
	CatalogDevice      string
	CatalogInode       string
	CatalogStaged      bool
	AccountingReported bool
}

var metadataDriveDeletionWake = make(chan struct{}, 1)

func metadataInitDriveDeletions() {
	for _, pending := range metadataLoadDriveDeletions(true) {
		metadataRetireCatalogState(pending.DriveId)
	}
	go func() {
		ticker := time.NewTicker(time.Minute)
		defer ticker.Stop()
		for util.IsAlive {
			for _, pending := range metadataLoadDriveDeletions(false) {
				metadataRetryDriveDeletion(pending.DriveId)
			}
			select {
			case <-metadataDriveDeletionWake:
			case <-ticker.C:
			}
		}
	}()
}

func metadataLoadDriveDeletions(includeCompleted bool) []metadataDriveDeletion {
	return db.NewTx(func(tx *db.Transaction) []metadataDriveDeletion {
		return db.Select[metadataDriveDeletion](
			tx,
			`
				select
					drive_id,
					resource::text,
					source_path,
					source_device,
					source_inode,
					drive_staging_name,
					catalog_staging_name,
					drive_staged,
					catalog_observed,
					catalog_device,
					catalog_inode,
					catalog_staged,
					accounting_reported
				from fs_metadata_drive_deletions
				where
					:include_completed
					or not (drive_staged and catalog_staged and accounting_reported)
				order by created_at, drive_id
			`,
			db.Params{
				"include_completed": includeCompleted,
			},
		)
	})
}

func metadataLoadDriveDeletion(driveId string) (metadataDriveDeletion, bool) {
	return db.NewTx2(func(tx *db.Transaction) (metadataDriveDeletion, bool) {
		return db.Get[metadataDriveDeletion](
			tx,
			`
				select
					drive_id,
					resource::text,
					source_path,
					source_device,
					source_inode,
					drive_staging_name,
					catalog_staging_name,
					drive_staged,
					catalog_observed,
					catalog_device,
					catalog_inode,
					catalog_staged,
					accounting_reported
				from fs_metadata_drive_deletions
				where drive_id = :drive_id
			`,
			db.Params{
				"drive_id": driveId,
			},
		)
	})
}

func metadataDriveDeletionMutex(driveId string) *sync.Mutex {
	metadataDatabases.Lock()
	lifecycle := metadataCatalogLifecycleLocked(metadataDatabasePath(driveId))
	metadataDatabases.Unlock()
	return &lifecycle.deletion
}

func metadataDeleteDrive(actor rpc.Actor, drive orc.Drive, path string) *util.HttpError {
	mutex := metadataDriveDeletionMutex(drive.Id)
	mutex.Lock()
	defer mutex.Unlock()

	pending, found := metadataLoadDriveDeletion(drive.Id)
	if !found {
		device, inode, err := metadataDeletionPathIdentity(path)
		if err != nil {
			return util.ServerHttpError("unable to inspect drive before deletion: %s", err)
		}
		resource, err := json.Marshal(drive)
		if err != nil {
			return util.ServerHttpError("unable to record drive deletion: %s", err)
		}
		pending = metadataDriveDeletion{
			DriveId:            drive.Id,
			Resource:           string(resource),
			SourcePath:         path,
			SourceDevice:       device,
			SourceInode:        inode,
			DriveStagingName:   "drive-" + drive.Id + "-" + util.RandomToken(16),
			CatalogStagingName: "metadata-" + drive.Id + "-" + util.RandomToken(16),
		}
		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					insert into fs_metadata_drive_deletions(
						drive_id, resource, source_path, source_device, source_inode,
						drive_staging_name, catalog_staging_name
					)
					values (
						:drive_id, cast(:resource as jsonb), :source_path, :source_device, :source_inode,
						:drive_staging_name, :catalog_staging_name
					)
					on conflict (drive_id) do nothing
				`,
				db.Params{
					"drive_id":             pending.DriveId,
					"resource":             pending.Resource,
					"source_path":          pending.SourcePath,
					"source_device":        pending.SourceDevice,
					"source_inode":         pending.SourceInode,
					"drive_staging_name":   pending.DriveStagingName,
					"catalog_staging_name": pending.CatalogStagingName,
				},
			)
		})
		ActivityRecord(actor, ActivityEvent{
			Kind:      ActivityDirect,
			Operation: ActivityOperationDelete,
			Targets:   []ActivityTarget{{UCloudPath: "/" + drive.Id}},
		})
	}
	metadataRetireCatalogState(drive.Id)
	select {
	case metadataDriveDeletionWake <- struct{}{}:
	default:
	}
	if err := metadataStageDriveDeletion(&pending); err != nil {
		return util.ServerHttpError("drive deletion is recorded and will be retried: %s", err)
	}
	return nil
}

func metadataSaveDriveDeletion(pending metadataDriveDeletion) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update fs_metadata_drive_deletions
				set
					drive_staged = :drive_staged,
					catalog_observed = :catalog_observed,
					catalog_device = :catalog_device,
					catalog_inode = :catalog_inode,
					catalog_staged = :catalog_staged,
					accounting_reported = :accounting_reported
				where drive_id = :drive_id
			`,
			db.Params{
				"drive_id":            pending.DriveId,
				"drive_staged":        pending.DriveStaged,
				"catalog_observed":    pending.CatalogObserved,
				"catalog_device":      pending.CatalogDevice,
				"catalog_inode":       pending.CatalogInode,
				"catalog_staged":      pending.CatalogStaged,
				"accounting_reported": pending.AccountingReported,
			},
		)
	})
}

func metadataStageDriveDeletion(pending *metadataDriveDeletion) error {
	if !pending.DriveStaged {
		if err := metadataStageDeletionPath(pending.SourcePath, pending.DriveStagingName, pending.SourceDevice, pending.SourceInode); err != nil {
			return err
		}
		pending.DriveStaged = true
		metadataSaveDriveDeletion(*pending)
	}
	controller.DriveRemoveTracked(pending.DriveId)
	return nil
}

func metadataRetryDriveDeletion(driveId string) {
	mutex := metadataDriveDeletionMutex(driveId)
	mutex.Lock()
	defer mutex.Unlock()
	pending, found := metadataLoadDriveDeletion(driveId)
	if !found {
		return
	}
	if err := metadataFinishDriveDeletion(&pending); err != nil {
		log.Warn("Unable to complete drive deletion for %s; will retry: %s", driveId, err)
	}
}

func metadataFinishDriveDeletion(pending *metadataDriveDeletion) error {
	drained, _ := metadataRetireCatalogState(pending.DriveId)
	if err := metadataStageDriveDeletion(pending); err != nil {
		return err
	}
	select {
	case <-drained:
	default:
		return nil
	}
	if !pending.AccountingReported {
		var drive orc.Drive
		if err := json.Unmarshal([]byte(pending.Resource), &drive); err != nil {
			return fmt.Errorf("decode deleted drive: %w", err)
		}
		if _, err := apm.ReportUsage.Invoke(storageUsageRequest(drive, 0)); err != nil {
			return err.AsError()
		}
		pending.AccountingReported = true
		metadataSaveDriveDeletion(*pending)
	}
	if !pending.CatalogObserved {
		device, inode, err := metadataDeletionPathIdentity(metadataDatabasePath(pending.DriveId))
		if err != nil {
			return err
		}
		pending.CatalogObserved = true
		pending.CatalogDevice = device
		pending.CatalogInode = inode
		metadataSaveDriveDeletion(*pending)
	}
	if !pending.CatalogStaged {
		if err := metadataStageDeletionPath(metadataDatabasePath(pending.DriveId), pending.CatalogStagingName, pending.CatalogDevice, pending.CatalogInode); err != nil {
			return err
		}
		pending.CatalogStaged = true
		metadataSaveDriveDeletion(*pending)
	}
	return nil
}

func metadataOpenDeletionDirectory(path string) (*os.File, error) {
	directory, _, err := metadataOpenScanRoot(context.Background(), path, nil, rate.NewLimiter(rate.Inf, 1))
	return directory, err
}

func metadataDeletionPathIdentity(path string) (string, string, error) {
	parent, err := metadataOpenDeletionDirectory(filepath.Dir(path))
	if errors.Is(err, os.ErrNotExist) {
		return "", "", nil
	}
	if err != nil {
		return "", "", err
	}
	defer parent.Close()
	var stat unix.Stat_t
	err = unix.Fstatat(int(parent.Fd()), filepath.Base(path), &stat, unix.AT_SYMLINK_NOFOLLOW)
	if errors.Is(err, os.ErrNotExist) {
		return "", "", nil
	}
	if err != nil {
		return "", "", err
	}
	if stat.Mode&unix.S_IFMT != unix.S_IFDIR {
		return "", "", errors.New("deletion source is not a directory")
	}
	return fmt.Sprint(uint64(stat.Dev)), fmt.Sprint(stat.Ino), nil
}

func metadataStageDeletionPath(path, stagingName, device, inode string) error {
	if stagingName == "" || stagingName == "." || stagingName == ".." || filepath.IsAbs(stagingName) || filepath.Base(stagingName) != stagingName {
		return errors.New("invalid deletion staging name")
	}
	staging, err := metadataOpenDeletionDirectory(shared.ServiceConfig.FileSystem.TrashStagingArea)
	if err != nil {
		return err
	}
	defer staging.Close()
	parent, parentErr := metadataOpenDeletionDirectory(filepath.Dir(path))
	if parentErr != nil && !errors.Is(parentErr, os.ErrNotExist) {
		return parentErr
	}
	if parent != nil {
		defer parent.Close()
	}
	matches := func(stat *unix.Stat_t) bool {
		return stat.Mode&unix.S_IFMT == unix.S_IFDIR && fmt.Sprint(uint64(stat.Dev)) == device && fmt.Sprint(stat.Ino) == inode
	}
	var stat unix.Stat_t
	err = unix.Fstatat(int(staging.Fd()), stagingName, &stat, unix.AT_SYMLINK_NOFOLLOW)
	if err == nil {
		if !matches(&stat) {
			return errors.New("deletion staging path has a different directory identity")
		}
	} else {
		if !errors.Is(err, os.ErrNotExist) {
			return err
		}
		if parent == nil {
			return metadataSyncDeletionDirectories(staging, parent)
		}
		err = unix.Fstatat(int(parent.Fd()), filepath.Base(path), &stat, unix.AT_SYMLINK_NOFOLLOW)
		if errors.Is(err, os.ErrNotExist) {
			return metadataSyncDeletionDirectories(staging, parent)
		}
		if err != nil {
			return err
		}
		if !matches(&stat) {
			return errors.New("deletion source has a different directory identity")
		}
		if err = unix.Renameat(int(parent.Fd()), filepath.Base(path), int(staging.Fd()), stagingName); err != nil {
			return err
		}
	}
	return metadataSyncDeletionDirectories(staging, parent)
}

func metadataSyncDeletionDirectories(staging, parent *os.File) error {
	if err := staging.Sync(); err != nil {
		return err
	}
	if parent != nil {
		return parent.Sync()
	}
	return nil
}
