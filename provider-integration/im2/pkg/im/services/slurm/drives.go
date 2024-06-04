package slurm

import (
	"sync"
	"ucloud.dk/pkg/apm"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
)

type DriveInfo struct {
	UCloudId UCloudDriveId
	Path     string
	Category string
	Locator  string
	Title    string
	Owner    apm.WalletOwner
}

type UCloudDriveId uint64
type RegisterDriveInfoFlag int

const (
	RegisterWithUCloud RegisterDriveInfoFlag = 1 << iota
)

func LookupDrive(ucloudDriveId UCloudDriveId) (DriveInfo, bool) {
	if cfg.Mode == cfg.ServerModeServer {
		return lookupDriveServer(ucloudDriveId)
	} else {
		log.Warn("TODO")
		return DriveInfo{}, false
	}
}

func LookupDriveByPath(path string) (DriveInfo, bool) {
	if cfg.Mode == cfg.ServerModeServer {
		return lookupDriveByPathServer(path)
	} else {
		log.Warn("TODO")
		return DriveInfo{}, false
	}
}

var driveCache = sync.Map{}

func CacheDrive(drive orc.Drive) {

}
