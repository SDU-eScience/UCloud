package filesystem

import (
	"fmt"
	"golang.org/x/sys/unix"
	"os"
	"path/filepath"
	"strings"
	"time"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/k8s/shared"
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

type OpenedDrive struct {
	root            *os.Root
	AbsInternalPath string
	Drive           orc.Drive
}

func (d *OpenedDrive) OpenUCloud(ucloudPath string, flag int, mode os.FileMode) (*os.File, error) {
	if d.root == nil {
		return nil, fmt.Errorf("invalid drive")
	}

	subpath, ok := strings.CutPrefix(ucloudPath, "/"+d.Drive.Id+"/")
	if !ok {
		return nil, fmt.Errorf("invalid path passed to OpenUCloud")
	}

	return d.OpenSubPath(subpath, flag, mode)
}

func (d *OpenedDrive) LStatSubPath(subpath string) (os.FileInfo, error) {
	if d.root == nil {
		return nil, fmt.Errorf("invalid drive")
	}

	return d.root.Lstat(subpath)
}

func (d *OpenedDrive) OpenSubPath(subpath string, flag int, mode os.FileMode) (*os.File, error) {
	if d.root == nil {
		return nil, fmt.Errorf("invalid drive")
	}

	fd, err := d.root.OpenFile(filepath.Clean(subpath), flag, mode)
	if err == nil {
		if mode&unix.O_CREAT != 0 && (mode&unix.O_WRONLY != 0 || mode&unix.O_RDWR != 0) {
			_ = unix.Fchown(int(fd.Fd()), DefaultUid, DefaultUid)
		}
	}

	return fd, err
}

func (d *OpenedDrive) SubPathToUCloud(subpath string) string {
	return fmt.Sprintf("/%s/%s", d.Drive.Id, subpath)
}

func (d *OpenedDrive) Close() {
	if d.root != nil {
		_ = d.root.Close()
	}
}

func (d *OpenedDrive) Mkdirs(subpath string, mode os.FileMode) error {
	components := util.Components(subpath)

	builder := "."
	for i, comp := range components {
		isLast := i == len(components)-1
		fileMode := os.FileMode(0770)
		if isLast {
			fileMode = mode
		}

		builder += "/"
		builder += comp

		err := d.root.Mkdir(builder, fileMode)
		if err == nil {
			file, err := d.root.OpenFile(builder, 0, 0)
			if err == nil {
				_ = file.Chown(DefaultUid, DefaultUid)
				util.SilentClose(file)
			}
		} else if !os.IsExist(err) {
			return err
		}
	}
	return nil
}

func safeOpenRoot(path string) (*os.Root, error) {
	// NOTE(Dan): This function is required to ensure that no symlinks are followed when opening the root which could
	// point to something that is not already user-controlled. This is needed mostly because os.OpenRoot itself doesn't
	// guarantee that a symlink is not followed when opening the root.

	components := util.Components(path)
	if len(components) == 0 {
		return nil, fmt.Errorf("bad path")
	}

	root, err := os.OpenRoot("/" + components[0])
	if err != nil {
		return nil, err
	}

	for i := 1; i < len(components); i++ {
		newRoot, err := root.OpenRoot(components[i])
		_ = root.Close()
		if err != nil {
			return nil, err
		}

		root = newRoot
	}

	return root, nil
}

func OpenDrive(drive *orc.Drive) (OpenedDrive, bool) {
	internalPath, ok := driveToLocalPath(drive)
	if !ok {
		return OpenedDrive{}, false
	}

	root, err := safeOpenRoot(internalPath)
	if err != nil {
		return OpenedDrive{}, false
	}

	return OpenedDrive{
		root:            root,
		AbsInternalPath: internalPath,
		Drive:           *drive,
	}, true
}

func OpenDriveAtUCloudPath(path string) (OpenedDrive, string, bool) {
	driveId, ok := DriveIdFromUCloudPath(path)
	if !ok {
		return OpenedDrive{}, "", false
	}

	drive, ok := ResolveDrive(driveId)
	if !ok {
		return OpenedDrive{}, "", false
	}

	subPath, ok := strings.CutPrefix(path, fmt.Sprintf("/%s/", driveId))
	if path == "/"+driveId {
		subPath, ok = ".", true
	}
	if !ok {
		return OpenedDrive{}, "", false
	}

	openedDrive, ok := OpenDrive(drive)
	if ok {
		return openedDrive, subPath, true
	} else {
		return OpenedDrive{}, "", false
	}
}

func driveToLocalPath(drive *orc.Drive) (string, bool) {
	descriptor, ok := ParseDriveDescriptor(util.OptValue(drive.ProviderGeneratedId))
	mnt := shared.ServiceConfig.FileSystem.MountPoint

	if !ok {
		return "/dev/null", false
	}

	switch descriptor.Type {
	case DriveDescriptorTypeHome:
		return filepath.Join(
			mnt,
			"home",
			descriptor.PrimaryReference,
		), true

	case DriveDescriptorTypeProjectRepo:
		return filepath.Join(
			mnt,
			"projects",
			descriptor.PrimaryReference,
			descriptor.SecondaryReference,
		), true

	case DriveDescriptorTypeMemberFiles:
		return filepath.Join(
			mnt,
			"projects",
			descriptor.PrimaryReference,
			"Members' Files",
			descriptor.SecondaryReference,
		), true

	case DriveDescriptorTypeShare:
		shareId := descriptor.PrimaryReference
		share, ok := shareCache.Get(shareId, func() (orc.Share, error) {
			return orc.RetrieveShare(shareId)
		})
		if !ok {
			return "/dev/null", false
		} else {
			return ucloudToInternal(share.Specification.SourceFilePath)
		}

	case DriveDescriptorTypeCollection:
		fallthrough
	default:
		return filepath.Join(
			mnt,
			"collections",
			drive.Id,
		), true
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

func ucloudToInternal(path string) (string, bool) {
	drive, subpath, ok := ResolveDriveByUCloudPath(path)
	if !ok {
		return "", false
	}

	basePath, ok := driveToLocalPath(drive)
	if !ok {
		return "/dev/null", false
	}

	return filepath.Join(basePath, subpath), true
}

func IReallyNeedUCloudToInternal(path string) (string, bool) {
	// NOTE(Dan): Are you sure you do not need OpenDriveAtUCloudPath() instead? If you are going to use any of the
	// files directly from the code, then you should really use OpenDrive/OpenDriveAtUCloudPath instead.
	return ucloudToInternal(path)
}

func internalToUCloudWithDrive(drive *orc.Drive, path string) (string, bool) {
	cleanPath := filepath.Clean(path)
	basePath, ok := driveToLocalPath(drive)
	basePath += "/"

	if !ok {
		return "", false
	}

	withoutBasePath, _ := strings.CutPrefix(cleanPath, basePath)
	if cleanPath+"/" == basePath {
		return "/" + drive.Id, true
	} else {
		return "/" + drive.Id + "/" + withoutBasePath, true
	}
}

func DriveIdFromUCloudPath(path string) (string, bool) {
	components := util.Components(path)
	if len(components) == 0 {
		return "", false
	}

	driveId := components[0]
	return driveId, true
}
