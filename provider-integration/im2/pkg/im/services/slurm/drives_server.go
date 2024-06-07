package slurm

import (
	"fmt"
	"os"
	"os/user"
	"path/filepath"
	"strings"
	"ucloud.dk/pkg/apm"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

func driveIpcServer() {

}

type DriveInfo struct {
	Owner    apm.WalletOwner
	Title    string
	Path     string
	Category string
}

func RegisterDriveInfo(info DriveInfo) (string, error) {
	info.Path = filepath.Clean(info.Path)

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
		return "", err
	}

	return resp, nil
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
