package filesystem

import (
	"fmt"
	"path/filepath"
	"strings"
	"ucloud.dk/shared/pkg/apm"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/k8s/shared"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

// InitializeMemberFiles ensures that a drive exists which corresponds to the member files of the given workspace.
// The returned path will be internal.
func InitializeMemberFiles(username string, project util.Option[string]) (string, *orc.Drive, error) {
	if strings.Contains(username, "..") || strings.Contains(username, "/") {
		return "", nil, fmt.Errorf("unexpected username: %s", username)
	}

	descriptor := DriveDescriptor{}

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

	retrievedDrive, ok := ctrl.RetrieveDriveByProviderId([]string{providerId.Value}, []string{""})
	needsInit := false
	if !ok {
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

		drive, err := orc.RetrieveDrive(driveId)
		if err != nil {
			return "", nil, fmt.Errorf("failed to register drive (retrieve): %s", err.Error())
		}
		retrievedDrive = &drive
		ctrl.TrackDrive(&drive)
		needsInit = true
	}

	if retrievedDrive == nil {
		return "", nil, fmt.Errorf("retrievedDrive should not be nil now")
	}

	result, ok := DriveToLocalPath(retrievedDrive)

	if !ok {
		return "", nil, fmt.Errorf("DriveToLocalPath should not fail here")
	}

	if needsInit {
		_ = DoCreateFolder(result)
		_ = DoCreateFolder(filepath.Join(result, "Jobs"))
		_ = DoCreateFolder(filepath.Join(result, "Trash"))
	}

	return result, retrievedDrive, nil
}
