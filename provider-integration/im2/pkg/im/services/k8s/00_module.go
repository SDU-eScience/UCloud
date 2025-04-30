package k8s

import (
	"encoding/json"
	"fmt"
	"net/http"

	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/k8s/containers"
	"ucloud.dk/pkg/im/services/k8s/filesystem"
	"ucloud.dk/pkg/im/services/k8s/shared"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

func Init(config *cfg.ServicesConfigurationKubernetes) {
	// TODO(Dan): Hacky work-around since these functions need to access the backends, but only sometimes.
	//   While these functions also sometimes need to be used by the sub-systems implementing the compute.
	shared.IsJobLocked = IsJobLocked
	shared.IsJobLockedEx = IsJobLockedEx

	ctrl.LaunchUserInstances = false

	ctrl.InitJobDatabase()
	ctrl.InitDriveDatabase()
	ctrl.InitScriptsLogDatabase()
	ctrl.Connections = ctrl.ConnectionService{
		Initiate: func(username string, signingKey util.Option[int]) (string, error) {
			_ = ctrl.RegisterConnectionComplete(username, ctrl.UnknownUser, true)
			return cfg.Provider.Hosts.UCloudPublic.ToURL(), nil
		},
		Unlink: func(username string, uid uint32) error {
			return nil
		},
		RetrieveManifest: func() ctrl.Manifest {
			return ctrl.Manifest{
				Enabled:                true,
				ExpireAfterMs:          util.Option[uint64]{},
				RequiresMessageSigning: false,
			}
		},
	}

	// Shared init depends on the ctrl databases but needs to run before ordinary service init
	shared.ServiceConfig = config
	shared.Init()

	ctrl.Files = filesystem.InitFiles()
	ctrl.Jobs = InitCompute()

	filesystem.InitTaskSystem()

	ctrl.IdentityManagement.HandleProjectNotification = func(updated *ctrl.NotificationProjectUpdated) bool {
		ok := true
		for _, member := range updated.MembersAddedToProject {
			_, _, err := filesystem.InitializeMemberFiles(member, util.OptValue(updated.Project.Id))
			if err != nil {
				ok = false
			}
		}
		return ok
	}

	ctrl.ApmHandler.HandleNotification = func(update *ctrl.NotificationWalletUpdated) {
		if !update.Project.Present {
			_, _, _ = filesystem.InitializeMemberFiles(update.Owner.Username, util.OptNone[string]())
		}
	}

	ctrl.RegisterProducts(shared.Machines)
	ctrl.RegisterProducts(shared.StorageProducts)
	ctrl.RegisterProducts(shared.IpProducts)
	ctrl.RegisterProducts(shared.LinkProducts)
	ctrl.RegisterProducts(shared.LicenseProducts)
}

func InitLater(config *cfg.ServicesConfigurationKubernetes) {
	InitComputeLater()
}

func IsJobLocked(job *orc.Job) util.Option[shared.LockedReason] {
	return IsJobLockedEx(job, nil)
}

func IsJobLockedEx(job *orc.Job, jobAnnotations map[string]string) util.Option[shared.LockedReason] {
	if ctrl.IsResourceLocked(job.Resource, job.Specification.Product) {
		reason := fmt.Sprintf("Insufficient funds for %v", job.Specification.Product.Category)
		return util.OptValue(shared.LockedReason{
			Reason: reason,
			Err: &util.HttpError{
				StatusCode: http.StatusPaymentRequired,
				Why:        reason,
				ErrorCode:  "NOT_ENOUGH_COMPUTE_CREDITS",
			},
		})
	}

	mounts := MountedDrivesEx(job, jobAnnotations)
	for _, mount := range mounts {
		if mount.DriveInvalid {
			reason := "Drive is no longer valid. Was it deleted?"
			return util.OptValue(shared.LockedReason{
				Reason: reason,
				Err: &util.HttpError{
					StatusCode: http.StatusForbidden,
					Why:        reason,
				},
			})
		}

		if !ctrl.CanUseDrive(job.Owner, mount.Drive.Id, mount.ReadOnly) {
			reason := fmt.Sprintf("You no longer have permissions to use this drive: %s (%v).", mount.Drive.Specification.Title, mount.Drive.Id)
			return util.OptValue(shared.LockedReason{
				Reason: reason,
				Err: &util.HttpError{
					StatusCode: http.StatusForbidden,
					Why:        reason,
				},
			})
		}

		if ctrl.IsResourceLocked(mount.Drive.Resource, mount.Drive.Specification.Product) {
			reason := fmt.Sprintf("Insufficient funds for %v", mount.Drive.Specification.Product.Category)
			return util.OptValue(shared.LockedReason{
				Reason: reason,
				Err: &util.HttpError{
					StatusCode: http.StatusPaymentRequired,
					Why:        reason,
					ErrorCode:  "NOT_ENOUGH_STORAGE_CREDITS",
				},
			})
		}
	}

	return util.OptNone[shared.LockedReason]()
}

func mountedFilesFromJob(job *orc.Job) []orc.AppParameterValue {
	var result []orc.AppParameterValue
	for _, value := range job.Specification.Parameters {
		if value.Type == orc.AppParameterValueTypeFile {
			result = append(result, value)
		}
	}

	for _, value := range job.Specification.Resources {
		if value.Type == orc.AppParameterValueTypeFile {
			result = append(result, value)
		}
	}

	if backendIsContainers(job) {
		folder, drive, err := containers.FindJobFolder(job)
		if err == nil {
			ucloudPath, ok := filesystem.InternalToUCloudWithDrive(drive, folder)
			if ok {
				result = append(result, orc.AppParameterValue{Path: ucloudPath})
			}
		}
	}

	return result
}

type MountedDrive struct {
	DriveInvalid bool
	Drive        orc.Drive
	ReadOnly     bool
}

func MountedDrives(job *orc.Job) []MountedDrive {
	return MountedDrivesEx(job, nil)
}

func MountedDrivesEx(job *orc.Job, jobAnnotations map[string]string) []MountedDrive {
	files := mountedFilesFromJob(job)
	drives := map[string]MountedDrive{}

	insertDrive := func(driveId string, readOnly bool) {
		existing, ok := drives[driveId]
		if ok {
			if existing.ReadOnly {
				drives[driveId] = MountedDrive{
					ReadOnly: readOnly,
				}
			}
		} else {
			drives[driveId] = MountedDrive{
				ReadOnly: readOnly,
			}
		}
	}

	for _, file := range files {
		driveId, ok := orc.DriveIdFromUCloudPath(file.Path)
		if ok {
			insertDrive(driveId, file.ReadOnly)
		}
	}

	if jobAnnotations == nil {
		if backendIsContainers(job) {
			jobAnnotations = containers.JobAnnotations(job, 0)
		}
	}

	driveIdsString := util.OptMapGet(jobAnnotations, shared.AnnotationMountedDriveIds)
	driveReadOnlyString := util.OptMapGet(jobAnnotations, shared.AnnotationMountedDriveAsReadOnly)
	if driveIdsString.Present && driveReadOnlyString.Present {
		var driveIds []string
		var driveReadOnly []bool
		err1 := json.Unmarshal([]byte(driveIdsString.Value), &driveIds)
		err2 := json.Unmarshal([]byte(driveReadOnlyString.Value), &driveReadOnly)

		if err1 == nil && err2 == nil && len(driveIds) == len(driveReadOnly) {
			for i := 0; i < len(driveIds); i++ {
				driveId := driveIds[i]
				readOnly := driveReadOnly[i]

				insertDrive(driveId, readOnly)
			}
		}
	}

	var result []MountedDrive

	for id, entry := range drives {
		drive, ok := ctrl.RetrieveDrive(id)
		if ok {
			entry.Drive = *drive
			entry.DriveInvalid = false
		} else {
			entry.DriveInvalid = true
		}
		result = append(result, entry)
	}

	return result
}
