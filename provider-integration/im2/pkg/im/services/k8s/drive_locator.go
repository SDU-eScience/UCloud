package k8s

import (
	"fmt"
	"path/filepath"
	"strings"
	"ucloud.dk/pkg/apm"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

type DriveDescriptorType = int

const (
	DriveDescriptorTypeHome DriveDescriptorType = iota
	DriveDescriptorTypeProjectRepo
	DriveDescriptorTypeMemberFiles
	DriveDescriptorTypeCollection
	DriveDescriptorTypeShare
)

type DriveDescriptor struct {
	Type               DriveDescriptorType
	PrimaryReference   string
	SecondaryReference string
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
// an empty string. For shares, this will return "Share (ID)" should be changed by the share-system.
func (d DriveDescriptor) ToTitle() string {
	switch d.Type {
	case DriveDescriptorTypeHome:
		return "Home"

	case DriveDescriptorTypeProjectRepo:
		return d.SecondaryReference

	case DriveDescriptorTypeMemberFiles:
		return d.SecondaryReference

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
			Type:               DriveDescriptorTypeProjectRepo,
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

func DriveToLocalPath(drive *orc.Drive) string {
	descriptor, _ := ParseDriveDescriptor(util.OptValue(drive.ProviderGeneratedId))
	mnt := ServiceConfig.FileSystem.MountPoint

	switch descriptor.Type {
	case DriveDescriptorTypeHome:
		return filepath.Join(
			mnt,
			"home",
			descriptor.PrimaryReference,
		)

	case DriveDescriptorTypeProjectRepo:
		return filepath.Join(
			mnt,
			"projects",
			descriptor.PrimaryReference,
			descriptor.SecondaryReference,
		)

	case DriveDescriptorTypeMemberFiles:
		return filepath.Join(
			mnt,
			"projects",
			descriptor.PrimaryReference,
			"Members' Files",
			descriptor.SecondaryReference,
		)

	case DriveDescriptorTypeShare:
		// TODO TODO TODO
		log.Warn("Shares not yet implemented")
		return "/dev/null/TODO"

	case DriveDescriptorTypeCollection:
		fallthrough
	default:
		return filepath.Join(
			mnt,
			"collections",
			drive.Id,
		)
	}
}

func ResolveDrive(id string) (*orc.Drive, bool) {
	return ctrl.RetrieveDrive(id)
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

func ListDrivesByOwner(owner apm.WalletOwner) []*orc.Drive {
	return nil
}

func UCloudToInternal(path string) (string, bool) {
	drive, subpath, ok := ResolveDriveByUCloudPath(path)
	if !ok {
		return "", false
	}

	return filepath.Join(DriveToLocalPath(drive), subpath), true
}

func InternalToUCloudWithDrive(drive *orc.Drive, path string) string {
	cleanPath := filepath.Clean(path)
	basePath := DriveToLocalPath(drive) + "/"
	withoutBasePath, _ := strings.CutPrefix(cleanPath, basePath)
	if cleanPath+"/" == basePath {
		return "/" + drive.Id
	} else {
		return "/" + drive.Id + "/" + withoutBasePath
	}
}

func EnsureDriveExists(descriptor DriveDescriptor) (string, bool) {
	return "", false
}

func DriveIdFromUCloudPath(path string) (string, bool) {
	components := util.Components(path)
	if len(components) == 0 {
		return "", false
	}

	driveId := components[0]
	return driveId, true
}
