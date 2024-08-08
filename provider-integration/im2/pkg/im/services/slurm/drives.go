package slurm

import (
	"fmt"
	"path/filepath"
	"strings"
	"time"
	fnd "ucloud.dk/pkg/foundation"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

var idToDriveCache = util.NewCache[string, orc.Drive](24 * time.Hour)
var localPathToDriveCache = util.NewCache[string, orc.Drive](24 * time.Hour)

func CacheDrive(drive orc.Drive) {
	idToDriveCache.Set(drive.Id, drive)
	localPathToDriveCache.Set(DriveToLocalPath(drive), drive)
}

func RetrieveDrive(driveId string) (orc.Drive, bool) {
	return idToDriveCache.Get(driveId, func() (orc.Drive, error) {
		if cfg.Mode == cfg.ServerModeUser {
			drive, err := retrieveDriveByIdIpc.Invoke(fnd.FindByStringId{Id: driveId})
			return drive, err
		} else {
			return orc.RetrieveDrive(driveId)
		}
	})
}

func DriveToLocalPath(drive orc.Drive) string {
	localPath, _ := strings.CutPrefix(drive.ProviderGeneratedId, cfg.Provider.Id+"-")
	return localPath
}

func ResolveDriveByPath(path string) (orc.Drive, bool) {
	cleanPath := filepath.Clean(path)
	parents := util.Parents(cleanPath)
	for i := len(parents) - 1; i >= 0; i-- {
		result, ok := localPathToDriveCache.GetNow(parents[i])
		if ok {
			return result, true
		}
	}

	_, result, ok := idToDriveCache.FindEntry(func(drive orc.Drive) bool {
		return drive.ProviderGeneratedId == cfg.Provider.Id+"-"+cleanPath
	})

	if ok {
		return result, true
	}

	var potentialIds []string
	for _, parent := range parents {
		potentialId := fmt.Sprintf(
			"%v-%v",
			cfg.Provider.Id,
			parent,
		)

		potentialIds = append(potentialIds, potentialId)
	}

	return orc.Drive{}, false

	/*
		// TODO Need to use server instance to do something along the lines of this:

		drives, err := orc.BrowseDrives("", orc.BrowseDrivesFlags{
			FilterProviderIds: util.OptValue(strings.Join(potentialIds, ",")),
		})

		if err != nil {

		}
	*/
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
	drive, ok := ResolveDriveByPath(cleanPath)
	if !ok {
		return "", false
	}

	basePath := DriveToLocalPath(drive) + "/"
	withoutBasePath, _ := strings.CutPrefix(cleanPath, basePath)
	return "/" + drive.Id + "/" + withoutBasePath, true
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
