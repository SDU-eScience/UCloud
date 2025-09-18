package slurm

import (
	"path/filepath"
	"strings"
	"time"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

var localPathToDriveCache = util.NewCache[string, orc.Drive](24 * time.Hour)

func CacheDrive(drive orc.Drive) {
	localPathToDriveCache.Set(DriveToLocalPath(drive), drive)
}

func RetrieveDrive(driveId string) (orc.Drive, bool) {
	res, ok := ctrl.RetrieveDrive(driveId)
	if ok {
		return *res, true
	} else {
		return orc.Drive{}, false
	}
}

func DriveToLocalPath(drive orc.Drive) string {
	idx := strings.Index(drive.ProviderGeneratedId, "\n")
	if idx <= 0 || idx+1 >= len(drive.ProviderGeneratedId) {
		localPath, _ := strings.CutPrefix(drive.ProviderGeneratedId, cfg.Provider.Id+"-")
		return localPath
	}
	return drive.ProviderGeneratedId[idx+1:]
}

func ResolveDriveByLocalPath(path string) (orc.Drive, bool) {
	cleanPath := filepath.Clean(path)
	parents := util.Parents(cleanPath)
	for i := len(parents) - 1; i >= 0; i-- {
		result, ok := localPathToDriveCache.GetNow(parents[i])
		if ok {
			return result, true
		}
	}

	prefix := cfg.Provider.Id + "-"

	var prefixes []string
	var suffixes []string

	prefixes = append(prefixes, prefix)
	suffixes = append(suffixes, cleanPath)

	for _, parent := range parents {
		prefixes = append(prefixes, prefix)
		suffixes = append(suffixes, parent)
	}

	res, ok := ctrl.RetrieveDriveByProviderId(prefixes, suffixes)
	if ok {
		return *res, true
	} else {
		return orc.Drive{}, false
	}
}

func ResolveDriveByUCloudPath(path string) (orc.Drive, bool) {
	components := util.Components(path)
	if len(components) == 0 {
		return orc.Drive{}, false
	}

	driveId := components[0]
	return RetrieveDrive(driveId)
}

func DriveIdFromUCloudPath(path string) (string, bool) {
	components := util.Components(path)
	if len(components) == 0 {
		return "", false
	}

	driveId := components[0]
	return driveId, true
}

func UCloudToInternal(path string) (string, bool) {
	components := util.Components(path)
	if len(components) == 0 {
		return path, false
	}

	driveId := components[0]
	withoutDrive := components[1:]

	drive, ok := RetrieveDrive(driveId)
	if !ok {
		return path, false
	}

	localBasePath := DriveToLocalPath(drive)
	return localBasePath + "/" + strings.Join(withoutDrive, "/"), true
}

func UCloudToInternalWithDrive(drive orc.Drive, path string) string {
	CacheDrive(drive)

	components := util.Components(path)
	if len(components) == 0 {
		return path
	}

	driveId := components[0]
	if drive.Id != driveId {
		log.Warn(
			"Was passed a different drive than we were supposed to! %v you are wrong. Got '%v' but expected '%v'! Path=%v.",
			util.GetCaller(),
			drive.Id,
			driveId,
			path,
		)
	}
	withoutDrive := components[1:]

	localBasePath := DriveToLocalPath(drive)
	return localBasePath + "/" + strings.Join(withoutDrive, "/")
}

func InternalToUCloud(path string) (string, bool) {
	cleanPath := filepath.Clean(path)
	drive, ok := ResolveDriveByLocalPath(cleanPath)
	if !ok {
		return "", false
	}

	basePath := DriveToLocalPath(drive) + "/"
	withoutBasePath, _ := strings.CutPrefix(cleanPath, basePath)
	if cleanPath+"/" == basePath {
		return "/" + drive.Id, true
	} else {
		return "/" + drive.Id + "/" + withoutBasePath, true
	}
}

func InternalToUCloudWithDrive(drive orc.Drive, path string) string {
	cleanPath := filepath.Clean(path)
	basePath := DriveToLocalPath(drive) + "/"
	withoutBasePath, _ := strings.CutPrefix(cleanPath, basePath)
	if cleanPath+"/" == basePath {
		return "/" + drive.Id
	} else {
		return "/" + drive.Id + "/" + withoutBasePath
	}
}
