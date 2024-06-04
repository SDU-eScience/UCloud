package slurm

import (
	"fmt"
	"os"
	"os/user"
	"path/filepath"
	"strconv"
	"strings"
	"ucloud.dk/pkg/apm"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/pkg/kvdb"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

func driveIpcServer() {
	registerDrive.Handler(func(r *ipc.Request[registerDriveRequest]) ipc.Response {
		return ipc.Response{
			StatusCode: 500,
			Payload:    nil,
		}
	})

	lookupDrive.Handler(func(r *ipc.Request[UCloudDriveId]) ipc.Response {
		return ipc.Response{
			StatusCode: 500,
			Payload:    nil,
		}
	})

	lookupDriveByPath.Handler(func(r *ipc.Request[string]) ipc.Response {
		return ipc.Response{
			StatusCode: 500,
			Payload:    nil,
		}
	})
}

const driveKeyPrefix = "slurm-drive-info-"

func lookupDriveServer(ucloudDriveId UCloudDriveId) (DriveInfo, bool) {
	drive, ok := kvdb.Get[DriveInfo](fmt.Sprintf("%v%v", driveKeyPrefix, ucloudDriveId))
	return drive, ok
}

func lookupDriveByPathServer(path string) (DriveInfo, bool) {
	cleanPath := filepath.Clean(path)
	items := kvdb.ListPrefix[DriveInfo](driveKeyPrefix)
	for _, item := range items {
		if item.Path == cleanPath {
			return item, true
		}
	}
	return DriveInfo{}, false
}

func RegisterDriveInfo(info DriveInfo, flags RegisterDriveInfoFlag) (UCloudDriveId, error) {
	driveId := info.UCloudId
	info.Path = filepath.Clean(info.Path)

	if flags&RegisterWithUCloud != 0 {
		resource := orc.ProviderRegisteredResource[orc.DriveSpecification]{
			Spec: orc.DriveSpecification{
				Title: info.Title,
				Product: apm.ProductReference{
					Id:       info.Category,
					Category: info.Category,
					Provider: cfg.Provider.Id,
				},
			},

			// NOTE(Dan): @migration need to double-check that these are identical
			ProviderGeneratedId: util.OptValue(
				fmt.Sprintf(
					"%v-%v",
					cfg.Provider.Id,
					info.Path,
				),
			),
		}

		if info.Owner.Type == apm.WalletOwnerTypeUser {
			resource.CreatedBy = util.OptValue(info.Owner.Username)
		} else {
			resource.ProjectAllRead = true
			resource.ProjectAllWrite = true
			resource.Project = util.OptValue(info.Owner.ProjectId)
		}

		resp, err := orc.RegisterDrive(resource)
		if err != nil {
			return 0, err
		}
		respId, err := strconv.ParseInt(resp, 10, 64)
		if err != nil {
			return 0, fmt.Errorf("malformed response from UCloud %v", err)
		}

		driveId = UCloudDriveId(respId)
	}

	info.UCloudId = driveId
	info.Path = filepath.Clean(info.Path)
	kvdb.Set(fmt.Sprintf("%v%v", driveKeyPrefix, driveId), info)

	return driveId, nil
}

func EvaluateLocators(owner apm.WalletOwner) {
	if cfg.Mode != cfg.ServerModeServer {
		log.Warn("EvaluateLocators must be called in server mode!")
		return
	}

	params := make(map[string]any)
	if owner.Type == apm.WalletOwnerTypeUser {
		localUid, ok := ctrl.MapUCloudToLocal(owner.Username)
		if !ok {
			log.Warn("Unknown user: %v", owner.Username)
			return
		}

		userInfo, err := user.LookupId(fmt.Sprint(localUid))
		if err != nil {
			log.Warn("Local username is unknown: ucloud=%v uid=%v err=%v", owner.Username, localUid, err)
			return
		}

		params["localUsername"] = userInfo.Username
	} else if owner.Type == apm.WalletOwnerTypeProject {
		log.Warn("Not yet implemented EvaluateLocators for projects")
		return
	} else {
		log.Warn("Unhandled owner type: %v", owner.Type)
		return
	}

	for categoryName, fs := range ServiceConfig.FileSystems {
		for locatorName, locator := range fs.DriveLocators {
			params["locatorName"] = locatorName
			params["categoryName"] = categoryName

			switch locator.Entity {
			case cfg.SlurmDriveLocatorEntityTypeUser:
				path := ""

				if locator.Pattern != "" {
					path = locator.Pattern
					for key, value := range params {
						path = strings.ReplaceAll(path, fmt.Sprintf("#{%v}", key), fmt.Sprint(value))
					}
				} else if locator.Script != "" {
					type scriptResp struct {
						Path string `json:"path"`
					}

					ext := ctrl.NewExtension[map[string]any, scriptResp]()
					ext.Script = locator.Script

					resp, ok := ext.Invoke(params)

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
