package k8s

import (
	"fmt"
	"net/http"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/k8s/containers"
	"ucloud.dk/pkg/im/services/k8s/filesystem"
	"ucloud.dk/pkg/im/services/k8s/shared"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

func Init(config *cfg.ServicesConfigurationKubernetes) {
	ctrl.LaunchUserInstances = false

	ctrl.InitJobDatabase()
	ctrl.InitDriveDatabase()
	ctrl.InitScriptsLogDatabase()
	ctrl.Connections = ctrl.ConnectionService{
		Initiate: func(username string, signingKey util.Option[int]) (redirectToUrl string) {
			_ = ctrl.RegisterConnectionComplete(username, ctrl.UnknownUser, true)
			return cfg.Provider.Hosts.UCloudPublic.ToURL()
		},
		Unlink: func(username string, uid uint32) error {
			return nil
		},
		RetrieveManifest: func() ctrl.Manifest {
			return ctrl.Manifest{
				Enabled:                true,
				ExpiresAfterMs:         util.Option[uint64]{},
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

	ctrl.ApmHandler.HandleNotification = func(update *ctrl.NotificationWalletUpdated) {

	}

	ctrl.RegisterProducts(shared.Machines)
	ctrl.RegisterProducts(shared.StorageProducts)
	ctrl.RegisterProducts(shared.LinkProducts)
	ctrl.RegisterProducts(shared.IpProducts)
	ctrl.RegisterProducts(shared.LicenseProducts)
}

type LockedReason struct {
	Reason string
	Err    error
}

func IsJobLocked(job *orc.Job) util.Option[LockedReason] {
	if ctrl.IsResourceLocked(job.Resource, job.Specification.Product) {
		reason := fmt.Sprintf("Insufficient funds for %v", job.Specification.Product.Category)
		return util.OptValue(LockedReason{
			Reason: reason,
			Err: &util.HttpError{
				StatusCode: http.StatusPaymentRequired,
				Why:        reason,
				ErrorCode:  "NOT_ENOUGH_COMPUTE_CREDITS",
			},
		})
	}

	drives := MountedDrives(job)
	for _, drive := range drives {
		if ctrl.IsResourceLocked(drive.Resource, drive.Specification.Product) {
			reason := fmt.Sprintf("Insufficient funds for %v", drive.Specification.Product.Category)
			return util.OptValue(LockedReason{
				Reason: reason,
				Err: &util.HttpError{
					StatusCode: http.StatusPaymentRequired,
					Why:        reason,
					ErrorCode:  "NOT_ENOUGH_STORAGE_CREDITS",
				},
			})
		}
	}

	return util.OptNone[LockedReason]()
}

func MountedFiles(job *orc.Job) []orc.AppParameterValue {
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

func MountedDrives(job *orc.Job) []orc.Drive {
	files := MountedFiles(job)
	driveIds := map[string]util.Empty{}

	for _, file := range files {
		driveId, ok := orc.DriveIdFromUCloudPath(file.Path)
		if ok {
			driveIds[driveId] = util.Empty{}
		}
	}

	var drives []orc.Drive
	for id, _ := range driveIds {
		drive, ok := ctrl.RetrieveDrive(id)
		if ok {
			drives = append(drives, *drive)
		}
	}

	return drives
}
