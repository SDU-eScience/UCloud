package slurm

import (
	"errors"
	"fmt"
	"net/http"
	"path/filepath"
	"ucloud.dk/pkg/im/external/user"

	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/shared/pkg/apm"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

var evaluateLocatorsIpc = ipc.NewCall[apm.WalletOwner, []LocatedDrive]("slurm.drives.evaluateLocators")
var retrieveDriveByIdIpc = ipc.NewCall[fnd.FindByStringId, orc.Drive]("slurm.drives.retrieveById")

func driveIpcServer() {
	retrieveDriveByIdIpc.Handler(func(r *ipc.Request[fnd.FindByStringId]) ipc.Response[orc.Drive] {
		drive, err := orc.RetrieveDrive(r.Payload.Id)
		if err != nil {
			return ipc.Response[orc.Drive]{
				StatusCode: http.StatusNotFound,
				Payload:    orc.Drive{},
			}
		}

		if !ctrl.BelongsToWorkspace(orc.ResourceOwnerToWalletOwner(drive.Resource), r.Uid) {
			return ipc.Response[orc.Drive]{
				StatusCode: http.StatusNotFound,
				Payload:    orc.Drive{},
			}
		}

		return ipc.Response[orc.Drive]{
			StatusCode: http.StatusOK,
			Payload:    drive,
		}
	})
	evaluateLocatorsIpc.Handler(func(r *ipc.Request[apm.WalletOwner]) ipc.Response[[]LocatedDrive] {
		if !ctrl.BelongsToWorkspace(r.Payload, r.Uid) {
			return ipc.Response[[]LocatedDrive]{
				StatusCode: http.StatusForbidden,
				Payload:    nil,
			}
		}

		var drives []LocatedDrive

		for categoryName, _ := range ServiceConfig.FileSystems {
			drives = append(drives, EvaluateLocators(r.Payload, categoryName)...)
		}

		return ipc.Response[[]LocatedDrive]{
			StatusCode: http.StatusOK,
			Payload:    drives,
		}
	})
}

func RegisterDriveInfo(info LocatedDrive) error {
	info.FilePath = filepath.Clean(info.FilePath)

	ownerReference := ""
	if info.Owner.Type == apm.WalletOwnerTypeUser {
		ownerReference = info.Owner.Username
	} else {
		ownerReference = info.Owner.ProjectId
	}

	resource := orc.ProviderRegisteredResource[orc.DriveSpecification]{
		Spec: orc.DriveSpecification{
			Title: info.Title,
			Product: apm.ProductReference{
				Id:       info.CategoryName,
				Category: info.CategoryName,
				Provider: cfg.Provider.Id,
			},
		},

		// TODO(Dan): @migration these are no longer identical
		ProviderGeneratedId: util.OptValue(
			fmt.Sprintf(
				"%v-%v\n%v",
				cfg.Provider.Id,
				ownerReference,
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

func EvaluateAllLocators(owner apm.WalletOwner) []LocatedDrive {
	if cfg.Mode != cfg.ServerModeServer {
		res, err := evaluateLocatorsIpc.Invoke(owner)
		if err != nil {
			return nil
		}
		return res
	} else {
		var result []LocatedDrive
		for categoryName, _ := range ServiceConfig.FileSystems {
			result = append(result, EvaluateLocators(owner, categoryName)...)
		}
		return result
	}
}

func EvaluateLocators(owner apm.WalletOwner, category string) []LocatedDrive {
	if cfg.Mode != cfg.ServerModeServer {
		all := EvaluateAllLocators(owner)
		var result []LocatedDrive
		for _, item := range all {
			if item.CategoryName == category {
				result = append(result, item)
			}
		}
		return result
	}

	var result []LocatedDrive
	recommendedUserOwner := ""
	recommendedGroupOwner := ""
	recommendedPermissions := "0700"
	params := make(map[string]string)

	ownerInformationOk := false

	switch owner.Type {
	case apm.WalletOwnerTypeUser:
		localUid, ok, _ := ctrl.MapUCloudToLocal(owner.Username)
		if ok {
			userInfo, err := user.LookupId(fmt.Sprint(localUid))
			if err != nil {
				log.Warn("Local username is unknown: ucloud=%v uid=%v err=%v", owner.Username, localUid, err)
				return nil
			}
			params["localUsername"] = userInfo.Username
			params["ucloudUsername"] = owner.Username
			params["uid"] = fmt.Sprint(localUid)
			recommendedUserOwner = userInfo.Username
			recommendedGroupOwner = userInfo.Username
			ownerInformationOk = true
		}

	case apm.WalletOwnerTypeProject:
		localGid, ok := ctrl.MapUCloudProjectToLocal(owner.ProjectId)
		if ok {
			groupInfo, err := user.LookupGroupId(fmt.Sprint(localGid))
			if err != nil {
				log.Warn("Local group is unknown: ucloud=%v uid=%v err=%v", owner.ProjectId, localGid, err)
				return nil
			}
			recommendedUserOwner = "root"
			recommendedPermissions = "2770"
			params["localGroupName"] = groupInfo.Name
			params["ucloudProjectId"] = owner.ProjectId
			params["gid"] = fmt.Sprint(localGid)
			recommendedGroupOwner = groupInfo.Name
			ownerInformationOk = true
		}

	default:
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

			if locator.Entity != cfg.SlurmDriveLocatorEntityTypeNone {
				if !ownerInformationOk {
					log.Warn("Unable to resolve owner of drive: %v", owner)
					continue
				}
				if owner.Type == apm.WalletOwnerTypeUser && locator.Entity != cfg.SlurmDriveLocatorEntityTypeUser {
					continue
				}

				if owner.Type == apm.WalletOwnerTypeProject && locator.Entity != cfg.SlurmDriveLocatorEntityTypeProject {
					continue
				}
			}

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

				ext := ctrl.NewScript[map[string]string, scriptResp]()
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
					RecommendedOwnerName:   recommendedUserOwner,
					RecommendedGroupName:   recommendedGroupOwner,
					RecommendedPermissions: recommendedPermissions,
				})
			}
		}
	}

	return result
}
