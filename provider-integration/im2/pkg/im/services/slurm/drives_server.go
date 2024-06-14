package slurm

import (
	"errors"
	"fmt"
	"net/http"
	"os/user"
	"path/filepath"
	"ucloud.dk/pkg/apm"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

func driveIpcServer() {

}

func RegisterDriveInfo(info LocatedDrive) error {
	info.FilePath = filepath.Clean(info.FilePath)

	resource := orc.ProviderRegisteredResource[orc.DriveSpecification]{
		Spec: orc.DriveSpecification{
			Title: info.Title,
			Product: apm.ProductReference{
				Id:       info.CategoryName,
				Category: info.CategoryName,
				Provider: cfg.Provider.Id,
			},
		},

		// NOTE(Dan): @migration need to double-check that these are identical
		ProviderGeneratedId: util.OptValue(
			fmt.Sprintf(
				"%v-%v",
				cfg.Provider.Id,
				info.FilePath,
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

	_, err := orc.RegisterDrive(resource)
	if err != nil {
		var httpErr *util.HttpError
		hOk := errors.As(err, &httpErr)
		if !hOk || httpErr.StatusCode != http.StatusConflict {
			return err
		}
	}

	return nil
}

type LocatedDrive struct {
	Title                  string
	LocatorName            string
	CategoryName           string
	FilePath               string
	Owner                  apm.WalletOwner
	Parameters             map[string]string
	RecommendedOwnerName   string
	RecommendedGroupName   string
	RecommendedPermissions string
}

func EvaluateLocators(owner apm.WalletOwner, category string) []LocatedDrive {
	var result []LocatedDrive
	if cfg.Mode != cfg.ServerModeServer {
		log.Warn("EvaluateLocators must be called in server mode!")
		return nil
	}

	localUsername := ""
	localGroupName := ""
	params := make(map[string]string)
	if owner.Type == apm.WalletOwnerTypeUser {
		localUid, ok := ctrl.MapUCloudToLocal(owner.Username)
		if !ok {
			log.Warn("Unknown user: %v", owner.Username)
			return nil
		}

		userInfo, err := user.LookupId(fmt.Sprint(localUid))
		if err != nil {
			log.Warn("Local username is unknown: ucloud=%v uid=%v err=%v", owner.Username, localUid, err)
			return nil
		}

		params["localUsername"] = userInfo.Username
		localUsername = userInfo.Username
		localGroupName = userInfo.Username
	} else if owner.Type == apm.WalletOwnerTypeProject {
		log.Warn("Not yet implemented EvaluateLocators for projects")
		return nil
	} else {
		log.Warn("Unhandled owner type: %v", owner.Type)
		return nil
	}

	for categoryName, fs := range ServiceConfig.FileSystems {
		if category != categoryName {
			continue
		}

		for locatorName, locator := range fs.DriveLocators {
			params["locatorName"] = locatorName
			params["categoryName"] = categoryName

			switch locator.Entity {
			case cfg.SlurmDriveLocatorEntityTypeUser:
				path := ""

				title := locatorName
				if locator.Title != "" {
					title = util.InjectParametersIntoString(locator.Title, params)
				}

				if locator.Pattern != "" {
					path = locator.Pattern
					path = util.InjectParametersIntoString(locator.Pattern, params)
				} else if locator.Script != "" {
					type scriptResp struct {
						Path string `json:"path"`
					}

					ext := ctrl.NewExtension[map[string]string, scriptResp]()
					ext.Script = locator.Script

					resp, ok := ext.Invoke(params)

					if !ok {
						continue
					}

					path = resp.Path
				}

				if path != "" {
					parametersCopy := make(map[string]string)
					for k, v := range params {
						parametersCopy[k] = v
					}

					result = append(result, LocatedDrive{
						Title:                  title,
						LocatorName:            locatorName,
						CategoryName:           categoryName,
						FilePath:               path,
						Owner:                  owner,
						Parameters:             parametersCopy,
						RecommendedOwnerName:   localUsername,
						RecommendedGroupName:   localGroupName,
						RecommendedPermissions: "0700",
					})
				}
			}
		}
	}

	return result
}
