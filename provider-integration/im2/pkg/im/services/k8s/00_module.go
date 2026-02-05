package k8s

import (
	"encoding/json"
	"fmt"
	"net/http"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	orc "ucloud.dk/shared/pkg/orc2"

	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/k8s/containers"
	"ucloud.dk/pkg/im/services/k8s/filesystem"
	"ucloud.dk/pkg/im/services/k8s/shared"
	"ucloud.dk/shared/pkg/util"
)

func Init(config *cfg.ServicesConfigurationKubernetes) {
	// TODO(Dan): Hacky work-around since these functions need to access the backends, but only sometimes.
	//   While these functions also sometimes need to be used by the sub-systems implementing the compute.
	shared.IsJobLocked = IsJobLocked
	shared.IsJobLockedEx = IsJobLockedEx

	ctrl.LaunchUserInstances = false
	ctrl.MaintenanceMode = cfg.Provider.Maintenance.Enabled
	ctrl.MaintenanceAllowlist = cfg.Provider.Maintenance.UserAllowList

	ctrl.InitJobDatabase()
	ctrl.InitDriveDatabase()
	ctrl.InitScriptsLogDatabase()
	ctrl.Connections = ctrl.ConnectionService{
		Initiate: func(username string, signingKey util.Option[int]) (string, *util.HttpError) {
			_ = ctrl.RegisterConnectionComplete(username, ctrl.UnknownUser, true)
			return cfg.Provider.Hosts.UCloudPublic.ToURL(), nil
		},
		Unlink: func(username string, uid uint32) *util.HttpError {
			return nil
		},
		RetrieveManifest: func() orc.ProviderIntegrationManifest {
			return orc.ProviderIntegrationManifest{
				Enabled: true,
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

		inferenceHandleApmEvent(update)
	}

	initStorageScanCli()
	initInference()

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
	iappName := job.Specification.Product.Category
	iapp, isIapp := containers.IApps[iappName]
	if isIapp && iapp.MutateJobNonPersistent != nil {
		// TODO(Dan): Performance of this deep copy is probably pretty bad
		var jobCopy orc.Job
		bytes, _ := json.Marshal(job)
		_ = json.Unmarshal(bytes, &jobCopy)

		config := ctrl.RetrieveIAppConfiguration(iappName, job.Resource.Owner)
		if config.Present {
			iapp.MutateJobNonPersistent(&jobCopy, config.Value.Configuration)
			return IsJobLockedEx(&jobCopy, nil)
		}
	}

	return IsJobLockedEx(job, nil)
}

func IsJobLockedEx(job *orc.Job, jobAnnotations map[string]string) util.Option[shared.LockedReason] {
	timer := util.NewTimer()
	isLocked := ctrl.IsResourceLocked(job.Resource, job.Specification.Product)
	metricComputeLocked.Observe(timer.Mark().Seconds())

	if isLocked {
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

	timer.Mark()
	mounts := MountedDrivesEx(job, jobAnnotations)
	metricMountedDrives.Observe(timer.Mark().Seconds())

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

		timer.Mark()
		canUse := ctrl.CanUseDrive(job.Owner, mount.Drive.Id, mount.ReadOnly)
		metricCanUseDrive.Observe(timer.Mark().Seconds())

		if !canUse {
			reason := fmt.Sprintf("You no longer have permissions to use this drive: %s (%v).", mount.Drive.Specification.Title, mount.Drive.Id)
			return util.OptValue(shared.LockedReason{
				Reason: reason,
				Err: &util.HttpError{
					StatusCode: http.StatusForbidden,
					Why:        reason,
				},
			})
		}

		timer.Mark()
		storageLocked := ctrl.IsResourceLocked(mount.RealDrive.Resource, mount.RealDrive.Specification.Product)
		metricStorageLocked.Observe(timer.Mark().Seconds())

		if storageLocked {
			reason := fmt.Sprintf("Insufficient funds for %v", mount.RealDrive.Specification.Product.Category)
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

var (
	metricComputeLocked = metricJobLocked("compute_locked")
	metricMountedDrives = metricJobLocked("mounted_drives")
	metricCanUseDrive   = metricJobLocked("can_use_drive")
	metricStorageLocked = metricJobLocked("storage_locked")
)

func metricJobLocked(region string) prometheus.Summary {
	return promauto.NewSummary(prometheus.SummaryOpts{
		Namespace: "ucloud_im",
		Subsystem: "jobs",
		Name:      fmt.Sprintf("job_locked_%s_seconds", region),
		Help:      fmt.Sprintf("Summary of the duration (in seconds) it takes to run the region '%s' of IsJobLocked.", region),
		Objectives: map[float64]float64{
			0.5:  0.01,
			0.75: 0.01,
			0.95: 0.01,
			0.99: 0.01,
		},
	})
}

func mountedFilesFromJob(job *orc.Job) []orc.AppParameterValue {
	timer := util.NewTimer()
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
	metricMountedDrivesTiming.WithLabelValues("JobParameterCollection").Observe(timer.Mark().Seconds())

	if backendIsContainers(job) {
		timer.Mark()
		folder, drive, err := containers.FindJobFolderEx(job, containers.FindJobFolderNoInitFolder)
		metricMountedDrivesTiming.WithLabelValues("FindJobFolder").Observe(timer.Mark().Seconds())

		if err == nil {
			ucloudPath, ok := filesystem.InternalToUCloudWithDrive(drive, folder)
			if ok {
				result = append(result, orc.AppParameterValue{Path: ucloudPath})
			}
		}
		metricMountedDrivesTiming.WithLabelValues("InternalToUCloud").Observe(timer.Mark().Seconds())
	}

	return result
}

type MountedDrive struct {
	DriveInvalid bool
	Drive        orc.Drive
	RealDrive    orc.Drive // Drive to use for storage lock checks
	ReadOnly     bool
}

func MountedDrives(job *orc.Job) []MountedDrive {
	return MountedDrivesEx(job, nil)
}

func MountedDrivesEx(job *orc.Job, jobAnnotations map[string]string) []MountedDrive {
	timer := util.NewTimer()
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

	timer.Mark()
	for _, file := range files {
		driveId, ok := filesystem.DriveIdFromUCloudPath(file.Path)
		if ok {
			insertDrive(driveId, file.ReadOnly)
		}
	}
	metricMountedDrivesTiming.WithLabelValues("InsertDriveParams").Observe(timer.Mark().Seconds())

	timer.Mark()
	if jobAnnotations == nil {
		if backendIsContainers(job) {
			jobAnnotations = containers.JobAnnotations(job, 0)
		}
	}
	metricMountedDrivesTiming.WithLabelValues("JobAnnotations").Observe(timer.Mark().Seconds())

	timer.Mark()
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
	metricMountedDrivesTiming.WithLabelValues("InsertDriveAnnotations").Observe(timer.Mark().Seconds())

	timer.Mark()
	var result []MountedDrive
	for id, entry := range drives {
		drive, ok := ctrl.RetrieveDrive(id)
		var realDrive *orc.Drive

		if ok {
			_, ok, realDrive = filesystem.DriveToLocalPath(drive)
		}

		if ok {
			entry.Drive = *drive
			entry.RealDrive = *realDrive
			entry.DriveInvalid = false
		} else {
			entry.DriveInvalid = true
		}
		result = append(result, entry)
	}
	metricMountedDrivesTiming.WithLabelValues("CollectingMountedDrives").Observe(timer.Mark().Seconds())

	return result
}

var metricMountedDrivesTiming = promauto.NewSummaryVec(prometheus.SummaryOpts{
	Namespace: "ucloud_im",
	Subsystem: "jobs",
	Name:      "mounted_drives_timing",
	Help:      "Timing of different regions in MountedDrives",
	Objectives: map[float64]float64{
		0.5:  0.01,
		0.75: 0.01,
		0.95: 0.01,
		0.99: 0.01,
	},
}, []string{"region"})
