package k8s

import (
	"encoding/json"
	"fmt"
	"net/http"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	config2 "ucloud.dk/pkg/config"
	"ucloud.dk/pkg/controller"
	containers2 "ucloud.dk/pkg/integrations/k8s/containers"
	filesystem2 "ucloud.dk/pkg/integrations/k8s/filesystem"
	shared2 "ucloud.dk/pkg/integrations/k8s/shared"
	orc "ucloud.dk/shared/pkg/orc2"

	"ucloud.dk/shared/pkg/util"
)

func Init(config *config2.ServicesConfigurationKubernetes) {
	// TODO(Dan): Hacky work-around since these functions need to access the backends, but only sometimes.
	//   While these functions also sometimes need to be used by the sub-systems implementing the compute.
	shared2.IsJobLocked = IsJobLocked
	shared2.IsJobLockedEx = IsJobLockedEx

	controller.LaunchUserInstances = false
	controller.MaintenanceMode = config2.Provider.Maintenance.Enabled
	controller.MaintenanceAllowlist = config2.Provider.Maintenance.UserAllowList

	controller.InitJobDatabase()
	controller.InitDriveDatabase()
	controller.InitScriptsLogDatabase()
	controller.Connections = controller.ConnectionService{
		Initiate: func(username string, signingKey util.Option[int]) (string, *util.HttpError) {
			_ = controller.RegisterConnectionComplete(username, controller.UnknownUser, true)
			return config2.Provider.Hosts.UCloudPublic.ToURL(), nil
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
	shared2.ServiceConfig = config
	shared2.Init()

	controller.Files = filesystem2.InitFiles()
	controller.Jobs = InitCompute()

	filesystem2.InitTaskSystem()

	controller.IdentityManagement.HandleProjectNotification = func(updated *controller.NotificationProjectUpdated) bool {
		ok := true
		for _, member := range updated.MembersAddedToProject {
			_, _, err := filesystem2.InitializeMemberFiles(member, util.OptValue(updated.Project.Id))
			if err != nil {
				ok = false
			}
		}
		return ok
	}

	controller.ApmHandler.HandleNotification = func(update *controller.NotificationWalletUpdated) {
		if !update.Project.Present {
			_, _, _ = filesystem2.InitializeMemberFiles(update.Owner.Username, util.OptNone[string]())
		}

		inferenceHandleApmEvent(update)
	}

	initStorageScanCli()
	initInference()

	controller.RegisterProducts(shared2.Machines)
	controller.RegisterProducts(shared2.StorageProducts)
	controller.RegisterProducts(shared2.IpProducts)
	controller.RegisterProducts(shared2.LinkProducts)
	controller.RegisterProducts(shared2.LicenseProducts)
}

func InitLater(config *config2.ServicesConfigurationKubernetes) {
	InitComputeLater()
}

func IsJobLocked(job *orc.Job) util.Option[shared2.LockedReason] {
	iappName := job.Specification.Product.Category
	iapp, isIapp := containers2.IApps[iappName]
	if isIapp && iapp.MutateJobNonPersistent != nil {
		// TODO(Dan): Performance of this deep copy is probably pretty bad
		var jobCopy orc.Job
		bytes, _ := json.Marshal(job)
		_ = json.Unmarshal(bytes, &jobCopy)

		config := controller.RetrieveIAppConfiguration(iappName, job.Resource.Owner)
		if config.Present {
			iapp.MutateJobNonPersistent(&jobCopy, config.Value.Configuration)
			return IsJobLockedEx(&jobCopy, nil)
		}
	}

	return IsJobLockedEx(job, nil)
}

func IsJobLockedEx(job *orc.Job, jobAnnotations map[string]string) util.Option[shared2.LockedReason] {
	timer := util.NewTimer()
	isLocked := controller.IsResourceLocked(job.Resource, job.Specification.Product)
	metricComputeLocked.Observe(timer.Mark().Seconds())

	if isLocked {
		reason := fmt.Sprintf("Insufficient funds for %v", job.Specification.Product.Category)
		return util.OptValue(shared2.LockedReason{
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
			return util.OptValue(shared2.LockedReason{
				Reason: reason,
				Err: &util.HttpError{
					StatusCode: http.StatusForbidden,
					Why:        reason,
				},
			})
		}

		timer.Mark()
		canUse := controller.CanUseDrive(job.Owner, mount.Drive.Id, mount.ReadOnly)
		metricCanUseDrive.Observe(timer.Mark().Seconds())

		if !canUse {
			reason := fmt.Sprintf("You no longer have permissions to use this drive: %s (%v).", mount.Drive.Specification.Title, mount.Drive.Id)
			return util.OptValue(shared2.LockedReason{
				Reason: reason,
				Err: &util.HttpError{
					StatusCode: http.StatusForbidden,
					Why:        reason,
				},
			})
		}

		timer.Mark()
		storageLocked := controller.IsResourceLocked(mount.RealDrive.Resource, mount.RealDrive.Specification.Product)
		metricStorageLocked.Observe(timer.Mark().Seconds())

		if storageLocked {
			reason := fmt.Sprintf("Insufficient funds for %v", mount.RealDrive.Specification.Product.Category)
			return util.OptValue(shared2.LockedReason{
				Reason: reason,
				Err: &util.HttpError{
					StatusCode: http.StatusPaymentRequired,
					Why:        reason,
					ErrorCode:  "NOT_ENOUGH_STORAGE_CREDITS",
				},
			})
		}
	}

	return util.OptNone[shared2.LockedReason]()
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
		folder, drive, err := containers2.FindJobFolderEx(job, containers2.FindJobFolderNoInitFolder)
		metricMountedDrivesTiming.WithLabelValues("FindJobFolder").Observe(timer.Mark().Seconds())

		if err == nil {
			ucloudPath, ok := filesystem2.InternalToUCloudWithDrive(drive, folder)
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
		driveId, ok := filesystem2.DriveIdFromUCloudPath(file.Path)
		if ok {
			insertDrive(driveId, file.ReadOnly)
		}
	}
	metricMountedDrivesTiming.WithLabelValues("InsertDriveParams").Observe(timer.Mark().Seconds())

	timer.Mark()
	if jobAnnotations == nil {
		if backendIsContainers(job) {
			jobAnnotations = containers2.JobAnnotations(job, 0)
		}
	}
	metricMountedDrivesTiming.WithLabelValues("JobAnnotations").Observe(timer.Mark().Seconds())

	timer.Mark()
	driveIdsString := util.OptMapGet(jobAnnotations, shared2.AnnotationMountedDriveIds)
	driveReadOnlyString := util.OptMapGet(jobAnnotations, shared2.AnnotationMountedDriveAsReadOnly)
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
		drive, ok := controller.RetrieveDrive(id)
		var realDrive *orc.Drive

		if ok {
			_, ok, realDrive = filesystem2.DriveToLocalPath(drive)
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
