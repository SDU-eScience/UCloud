package slurm

import (
	"os"
	"os/user"
	"strings"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/log"
)

func lookupDriveUser(ucloudDriveId UCloudDriveId) (DriveInfo, bool) {
	// TODO caching
	resp, err := lookupDrive.Invoke(ucloudDriveId)
	if err != nil {
		log.Warn("Could not look up drive %v: %v", ucloudDriveId, err)
		return DriveInfo{}, false
	}

	return resp, true
}

func lookupDriveByPathUser(path string) (DriveInfo, bool) {
	// TODO caching
	resp, err := lookupDriveByPath.Invoke(path)
	if err != nil {
		log.Warn("Could not look up drive %v: %v", path, err)
		return DriveInfo{}, false
	}

	return resp, true
}

func registerDriveInfoUser(info DriveInfo, flags RegisterDriveInfoFlag) (UCloudDriveId, error) {
	resp, err := registerDrive.Invoke(registerDriveRequest{
		Info:  info,
		flags: flags,
	})

	if err != nil {
		log.Warn("Could not look up drive %v: %v", info, err)
		return UCloudDriveId(0), err
	}

	return resp, nil
}
