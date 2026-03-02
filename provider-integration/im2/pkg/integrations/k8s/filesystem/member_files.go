package filesystem

import (
	"path/filepath"
	"strings"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	cfg "ucloud.dk/pkg/config"
	ctrl "ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/shared"
	apm "ucloud.dk/shared/pkg/accounting"
	fnd "ucloud.dk/shared/pkg/foundation"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

// InitializeMemberFiles ensures that a drive exists which corresponds to the member files of the given workspace.
// The returned path will be internal.
func InitializeMemberFiles(username string, project util.Option[string]) (string, *orc.Drive, *util.HttpError) {
	timer := util.NewTimer()
	if strings.Contains(username, "..") || strings.Contains(username, "/") {
		return "", nil, util.ServerHttpError("unexpected username: %s", username)
	}

	descriptor := DriveDescriptor{}

	timer.Mark()
	if project.Present {
		if strings.Contains(project.Value, "..") || strings.Contains(project.Value, "/") {
			return "", nil, util.ServerHttpError("unexpected project: %s", project.Value)
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
		return "", nil, util.ServerHttpError("unexpected error in ToProviderId()")
	}
	metricInitMemberFiles.WithLabelValues("FindReference").Observe(timer.Mark().Seconds())

	timer.Mark()
	retrievedDrive, ok := ctrl.DriveRetrieveByProviderId([]string{providerId.Value}, []string{""})
	needsInit := false
	metricInitMemberFiles.WithLabelValues("DriveRetrieveByProviderId").Observe(timer.Mark().Seconds())

	if !ok {
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

		driveId, err := orc.DrivesControlRegister.Invoke(fnd.BulkRequestOf(resource))
		if err != nil {
			return "", nil, util.ServerHttpError("failed to register drive: %s", err.AsError().Error())
		}
		metricInitMemberFiles.WithLabelValues("DriveRegistration").Observe(timer.Mark().Seconds())

		timer.Mark()

		retrieveRequest := orc.DrivesControlRetrieveRequest{Id: driveId.Responses[0].Id}
		retrieveRequest.IncludeOthers = true
		drive, err := orc.DrivesControlRetrieve.Invoke(retrieveRequest)
		if err != nil {
			return "", nil, util.ServerHttpError("failed to register drive (retrieve): %s", err.AsError().Error())
		}
		retrievedDrive = &drive
		ctrl.DriveTrack(&drive)
		needsInit = true
		metricInitMemberFiles.WithLabelValues("DriveRegistrationRetrieveAndTrack")
	}

	if retrievedDrive == nil {
		return "", nil, util.ServerHttpError("retrievedDrive should not be nil now")
	}

	result, ok, _ := DriveToLocalPath(retrievedDrive)

	if !ok {
		return "", nil, util.ServerHttpError("DriveToLocalPath should not fail here")
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
