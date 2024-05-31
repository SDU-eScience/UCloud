package slurm

import (
	"os"
	"os/user"
	"strings"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/slurm"
	"ucloud.dk/pkg/log"
)

type DriveInfo struct {
	UCloudId UCloudDriveId
	Path     string
	Category string
	Locator  string
}

type UCloudDriveId uint64

func LookupDrive(ucloudDriveId UCloudDriveId) (DriveInfo, error) {
	return DriveInfo{}, nil
}

func LookupDriveByPath(path string) (DriveInfo, error) {
	return DriveInfo{}, nil
}

type RegisterDriveInfoFlag int

const (
	RegisterWithUCloud RegisterDriveInfoFlag = 1 << iota
)

func RegisterDriveInfo(info DriveInfo, flags RegisterDriveInfoFlag) (UCloudDriveId, error) {
	switch cfg.Mode {
	case cfg.ServerModeUser:
		log.Warn("RegisterDriveInfo not yet implemented for users")
		return 0, nil

	case cfg.ServerModeServer:
		driveId := info.UCloudId
		if flags&RegisterWithUCloud != 0 {
			// TODO register with ucloud and set drive id
		}

		return driveId, nil

	default:
		log.Warn("RegisterDriveInfo only works for user/server mode")
		return 0, nil
	}
}

func SupportsCollectionCreation(categoryName string) bool {
	_, ok := GetCollectionLocator(categoryName)
	return ok
}

func GetCollectionLocator(categoryName string) (cfg.SlurmDriveLocator, bool) {
	fs, ok := slurm.ServiceConfig.FileSystems[categoryName]
	if !ok {
		return cfg.SlurmDriveLocator{}, false
	}

	for _, v := range fs.DriveLocators {
		if v.Entity == cfg.SlurmDriveLocatorEntityTypeCollection {
			return v, true
		}
	}

	return cfg.SlurmDriveLocator{}, false
}

func EvaluateLocators() {
	if cfg.Mode != cfg.ServerModeUser {
		log.Warn("EvaluateLocators must be called in user mode!")
		return
	}

	userInfo, err := user.Current()
	if err != nil {
		log.Warn("Could not lookup current user: %v", err)
		return
	}

	for categoryName, fs := range slurm.ServiceConfig.FileSystems {
		for locatorName, locator := range fs.DriveLocators {
			switch locator.Entity {
			case cfg.SlurmDriveLocatorEntityTypeUser:
				path := ""

				if locator.Pattern != "" {
					path = strings.ReplaceAll(locator.Pattern, "#{localUsername}", userInfo.Username)
				} else if locator.Script != "" {
					type scriptReq struct {
						CategoryName  string `json:"categoryName"`
						LocatorName   string `json:"locatorName"`
						LocalUsername string `json:"localUsername"`
					}

					type scriptResp struct {
						Path string `json:"path"`
					}

					ext := ctrl.NewExtension[scriptReq, scriptResp]()
					ext.Script = locator.Script

					resp, ok := ext.Invoke(scriptReq{
						CategoryName:  categoryName,
						LocatorName:   locatorName,
						LocalUsername: userInfo.Username,
					})

					if !ok {
						continue
					}

					path = resp.Path
				}

				if path != "" {
					stat, err := os.Stat(path)
					if err != nil {
						log.Warn("Locator %v/%v has returned an invalid path: %v", categoryName, locatorName, err)
						continue
					}
					if !stat.IsDir() {
						log.Warn(
							"Locator %v/%v has returned a path to something which is not a directory",
							categoryName,
							locatorName,
						)
						continue
					}
				}
			}
		}
	}
}
