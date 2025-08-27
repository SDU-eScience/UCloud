package filesystem

import (
	"fmt"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	"path/filepath"
	"strings"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/k8s/shared"
	"ucloud.dk/shared/pkg/apm"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

// InitializeMemberFiles ensures that a drive exists which corresponds to the member files of the given workspace.
// The returned path will be internal.
func InitializeMemberFiles(username string, project util.Option[string]) (string, *orc.Drive, error) {
	timer := util.NewTimer()
	if strings.Contains(username, "..") || strings.Contains(username, "/") {
		return "", nil, fmt.Errorf("unexpected username: %s", username)
	}

	descriptor := DriveDescriptor{}

	timer.Mark()
	if project.Present {
		if strings.Contains(project.Value, "..") || strings.Contains(project.Value, "/") {
			return "", nil, fmt.Errorf("unexpected project: %s", project.Value)
		}

		descriptor.Type = DriveDescriptorTypeMemberFiles
		descriptor.PrimaryReference = project.Value
		descriptor.SecondaryReference = username
	} else {
		descriptor.Type = DriveDescriptorTypeHome
		descriptor.PrimaryReference = username
	}

	providerId := descriptor.ToProviderId()
	if !providerId.Present {
		return "", nil, fmt.Errorf("unexpected error in ToProviderId()")
	}
	metricInitMemberFiles.WithLabelValues("FindReference").Observe(timer.Mark().Seconds())

	timer.Mark()
	retrievedDrive, ok := ctrl.RetrieveDriveByProviderId([]string{providerId.Value}, []string{""})
	needsInit := false
	metricInitMemberFiles.WithLabelValues("RetrieveDriveByProviderId").Observe(timer.Mark().Seconds())

	if !ok {
		log.Info("Creating member files folder for %s in %s", username, project.Value)

		timer.Mark()
		category := shared.ServiceConfig.FileSystem.Name
		resource := orc.ProviderRegisteredResource[orc.DriveSpecification]{
			CreatedBy: util.OptValue(username),
			Project:   project,
			Spec: orc.DriveSpecification{
				Title: descriptor.ToTitle(),
				Product: apm.ProductReference{
					Id:       descriptor.ProductName(),
					Category: category,
					Provider: cfg.Provider.Id,
				},
			},

			ProviderGeneratedId: providerId,
		}

		driveId, err := orc.RegisterDrive(resource)
		if err != nil {
			return "", nil, fmt.Errorf("failed to register drive: %s", err.Error())
		}
		metricInitMemberFiles.WithLabelValues("DriveRegistration").Observe(timer.Mark().Seconds())

		timer.Mark()
		drive, err := orc.RetrieveDrive(driveId)
		if err != nil {
			return "", nil, fmt.Errorf("failed to register drive (retrieve): %s", err.Error())
		}
		retrievedDrive = &drive
		ctrl.TrackDrive(&drive)
		needsInit = true
		metricInitMemberFiles.WithLabelValues("DriveRegistrationRetrieveAndTrack")
	}

	if retrievedDrive == nil {
		return "", nil, fmt.Errorf("retrievedDrive should not be nil now")
	}

	result, ok, _ := DriveToLocalPath(retrievedDrive)

	if !ok {
		return "", nil, fmt.Errorf("DriveToLocalPath should not fail here")
	}

	if needsInit {
		timer.Mark()
		_ = DoCreateFolder(result)
		_ = DoCreateFolder(filepath.Join(result, "Jobs"))
		_ = DoCreateFolder(filepath.Join(result, "Trash"))
		metricInitMemberFiles.WithLabelValues("FolderInit").Observe(timer.Mark().Seconds())
	}

	return result, retrievedDrive, nil
}

var metricInitMemberFiles = promauto.NewSummaryVec(prometheus.SummaryOpts{
	Namespace: "ucloud_im",
	Subsystem: "jobs",
	Name:      "init_member_files_seconds",
	Help:      "Timing of different regions in InitializeMemberFiles",
	Objectives: map[float64]float64{
		0.5:  0.01,
		0.75: 0.01,
		0.95: 0.01,
		0.99: 0.01,
	},
}, []string{"region"})
