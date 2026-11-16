package filesystem

import (
	"fmt"
	"path/filepath"
	"strings"
	"time"

	ctrl "ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/shared"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

type DriveDescriptorType = int

const (
	DriveDescriptorTypeHome DriveDescriptorType = iota
	DriveDescriptorTypeProjectRepo
	DriveDescriptorTypeMemberFiles
	DriveDescriptorTypeCollection
	DriveDescriptorTypeShare
)

const (
	driveMountHome        = "home"
	driveMountProjects    = "projects"
	driveMountMemberFiles = "Members' Files"
	driveMountCollections = "collections"
)

type DriveDescriptor struct {
	Type               DriveDescriptorType
	PrimaryReference   string
	SecondaryReference string
}

func (d DriveDescriptor) ProductName() string {
	switch d.Type {
	case DriveDescriptorTypeShare:
		return "share"

	case DriveDescriptorTypeMemberFiles:
		return "project-home"

	case DriveDescriptorTypeHome:
		fallthrough
	case DriveDescriptorTypeProjectRepo:
		fallthrough
	case DriveDescriptorTypeCollection:
		fallthrough
	default:
		return shared.ServiceConfig.FileSystem.Name
	}
}

func (d DriveDescriptor) ToProviderId() util.Option[string] {
	switch d.Type {
	case DriveDescriptorTypeHome:
		return util.OptValue(fmt.Sprintf("h-%v", d.PrimaryReference))

	case DriveDescriptorTypeProjectRepo:
		return util.OptValue(fmt.Sprintf("p-%v/%v", d.PrimaryReference, d.SecondaryReference))

	case DriveDescriptorTypeMemberFiles:
		return util.OptValue(fmt.Sprintf("pm-%v/%v", d.PrimaryReference, d.SecondaryReference))

	case DriveDescriptorTypeCollection:
		return util.OptNone[string]()

	case DriveDescriptorTypeShare:
		return util.OptValue(fmt.Sprintf("s-%v", d.PrimaryReference))

	default:
		return util.OptNone[string]()
	}
}

// ToTitle returns the natural title of a drive descriptor. For drives of the "Collection" type this will return
// an empty string. For shares, this will return the share ID and should be changed by the share-system.
func (d DriveDescriptor) ToTitle() string {
	switch d.Type {
	case DriveDescriptorTypeHome:
		return "Home"

	case DriveDescriptorTypeProjectRepo:
		return d.SecondaryReference

	case DriveDescriptorTypeMemberFiles:
		return "Member Files: " + d.SecondaryReference

	case DriveDescriptorTypeCollection:
		return ""

	case DriveDescriptorTypeShare:
		return d.PrimaryReference

	default:
		return ""
	}
}

func ParseDriveDescriptor(providerId util.Option[string]) (DriveDescriptor, bool) {
	pid := providerId.Value
	if !providerId.Present || pid == "" {
		return DriveDescriptor{
			Type: DriveDescriptorTypeCollection,
		}, true
	}

	if strings.HasPrefix(pid, "h-") {
		return DriveDescriptor{
			Type:             DriveDescriptorTypeHome,
			PrimaryReference: pid[2:],
		}, true
	} else if strings.HasPrefix(pid, "p-") {
		rest := pid[2:]
		split := strings.Split(rest, "/")
		if len(split) != 2 {
			return DriveDescriptor{}, false
		}
		return DriveDescriptor{
			Type:               DriveDescriptorTypeProjectRepo,
			PrimaryReference:   split[0],
			SecondaryReference: split[1],
		}, true
	} else if strings.HasPrefix(pid, "pm-") {
		rest := pid[3:]
		split := strings.Split(rest, "/")
		if len(split) != 2 {
			return DriveDescriptor{}, false
		}
		return DriveDescriptor{
			Type:               DriveDescriptorTypeMemberFiles,
			PrimaryReference:   split[0],
			SecondaryReference: split[1],
		}, true
	} else if strings.HasPrefix(pid, "s-") {
		return DriveDescriptor{
			Type:             DriveDescriptorTypeShare,
			PrimaryReference: pid[2:],
		}, true
	} else {
		return DriveDescriptor{}, false
	}
}

var shareCache = util.NewCache[string, orc.Share](1 * time.Minute)
var driveByInternalPrefixCache = util.NewCache[string, *orc.Drive](1 * time.Minute)

func DriveToLocalPath(drive *orc.Drive) (string, bool, *orc.Drive) {
	descriptor, ok := ParseDriveDescriptor(util.OptValue(drive.ProviderGeneratedId))
	mnt := shared.ServiceConfig.FileSystem.MountPoint

	if !ok {
		return "/dev/null", false, drive
	}

	switch descriptor.Type {
	case DriveDescriptorTypeHome:
		return filepath.Join(
			mnt,
			driveMountHome,
			descriptor.PrimaryReference,
		), true, drive

	case DriveDescriptorTypeProjectRepo:
		return filepath.Join(
			mnt,
			driveMountProjects,
			descriptor.PrimaryReference,
			descriptor.SecondaryReference,
		), true, drive

	case DriveDescriptorTypeMemberFiles:
		return filepath.Join(
			mnt,
			driveMountProjects,
			descriptor.PrimaryReference,
			driveMountMemberFiles,
			descriptor.SecondaryReference,
		), true, drive

	case DriveDescriptorTypeShare:
		shareId := descriptor.PrimaryReference
		share, ok := shareCache.Get(shareId, func() (orc.Share, error) {
			share, err := orc.SharesControlRetrieve.Invoke(orc.SharesControlRetrieveRequest{Id: shareId})
			return share, err.AsError()
		})
		if !ok {
			return "/dev/null", false, drive
		} else {
			result, ok, realDrive := UCloudToInternal(share.Specification.SourceFilePath)
			return result, ok, realDrive
		}

	case DriveDescriptorTypeCollection:
		fallthrough
	default:
		return filepath.Join(
			mnt,
			driveMountCollections,
			drive.Id,
		), true, drive
	}
}

func driveFromInternalPath(internalPath string) (*orc.Drive, bool) {
	mount := filepath.Clean(shared.ServiceConfig.FileSystem.MountPoint)
	relative, err := filepath.Rel(mount, filepath.Clean(internalPath))
	if err != nil || relative == "." || relative == ".." || strings.HasPrefix(relative, ".."+string(filepath.Separator)) {
		return nil, false
	}
	components := util.Components(relative)
	if len(components) < 2 {
		return nil, false
	}

	var key string
	var driveId string
	descriptor := DriveDescriptor{}
	switch components[0] {
	case driveMountHome:
		key = strings.Join(components[:2], "/")
		descriptor = DriveDescriptor{
			Type:             DriveDescriptorTypeHome,
			PrimaryReference: components[1],
		}
	case driveMountCollections:
		key = strings.Join(components[:2], "/")
		driveId = components[1]
	case driveMountProjects:
		if len(components) >= 4 && components[2] == driveMountMemberFiles {
			key = strings.Join(components[:4], "/")
			descriptor = DriveDescriptor{
				Type:               DriveDescriptorTypeMemberFiles,
				PrimaryReference:   components[1],
				SecondaryReference: components[3],
			}
		} else if len(components) >= 3 {
			key = strings.Join(components[:3], "/")
			descriptor = DriveDescriptor{
				Type:               DriveDescriptorTypeProjectRepo,
				PrimaryReference:   components[1],
				SecondaryReference: components[2],
			}
		} else {
			return nil, false
		}
	default:
		return nil, false
	}

	drive, _ := driveByInternalPrefixCache.Get(key, func() (*orc.Drive, error) {
		if driveId != "" {
			result, ok := ctrl.DriveRetrieve(driveId)
			if !ok {
				return nil, nil
			}
			return result, nil
		}
		providerId := descriptor.ToProviderId()
		if !providerId.Present {
			return nil, nil
		}
		result, ok := ctrl.DriveRetrieveByProviderId([]string{providerId.Value}, []string{""})
		if !ok {
			return nil, nil
		}
		return result, nil
	})
	if drive == nil {
		return nil, false
	}
	return drive, true
}

func ResolveDrive(id string) (*orc.Drive, bool) {
	return ctrl.DriveRetrieve(id)
}

func ResolveDriveByUCloudPath(path string) (*orc.Drive, string, bool) {
	comps := util.Components(path)
	if len(comps) == 0 {
		return nil, "", false
	}

	driveId := comps[0]
	drive, ok := ResolveDrive(driveId)
	if !ok {
		return nil, "", false
	}

	subpath := filepath.Join(comps[1:]...)
	return drive, subpath, true
}

func UCloudToInternal(path string) (string, bool, *orc.Drive) {
	drive, subpath, ok := ResolveDriveByUCloudPath(path)
	if !ok {
		return "", false, nil
	}

	basePath, ok, drive := DriveToLocalPath(drive)
	if !ok {
		return "/dev/null", false, nil
	}

	return filepath.Join(basePath, subpath), true, drive
}

func InternalToUCloudWithDrive(drive *orc.Drive, path string) (string, bool) {
	cleanPath := filepath.Clean(path)
	basePath, ok, _ := DriveToLocalPath(drive)
	if !ok {
		return "", false
	}
	basePath += "/"

	if cleanPath+"/" == basePath {
		return "/" + drive.Id, true
	}
	withoutBasePath, ok := strings.CutPrefix(cleanPath, basePath)
	if !ok {
		return "", false
	}
	return "/" + drive.Id + "/" + withoutBasePath, true
}

func DriveIdFromUCloudPath(path string) (string, bool) {
	components := util.Components(path)
	if len(components) == 0 {
		return "", false
	}

	driveId := components[0]
	return driveId, true
}

func AllowUCloudPathsTogetherWithProjects(paths []string, projects []string) bool {
	projectIds := map[string]util.Empty{}
	anySensitive := false

	for _, p := range projects {
		projectIds[p] = util.Empty{}
	}

	for _, path := range paths {
		driveId, ok := DriveIdFromUCloudPath(path)
		if ok {
			drive, ok := ResolveDrive(driveId)
			if ok {
				projectIds[drive.Owner.Project.Value] = util.Empty{}
				if DriveIsSensitive(drive) {
					anySensitive = true
				}
			}
		}
	}

	if anySensitive && len(projectIds) > 1 {
		return false
	}

	return true
}

func AllowUCloudPathsTogether(paths []string) bool {
	return AllowUCloudPathsTogetherWithProjects(paths, nil)
}

func UCloudPathIsSensitive(path string) bool {
	driveId, ok := DriveIdFromUCloudPath(path)
	if !ok {
		return true
	}

	drive, ok := ResolveDrive(driveId)
	if !ok {
		return true
	}

	return DriveIsSensitive(drive)
}

func DriveIsSensitive(drive *orc.Drive) bool {
	if drive == nil {
		return true
	}

	return shared.IsSensitiveProject(drive.Owner.Project.Value)
}
