package accounting

import (
	"net/http"
	fnd "ucloud.dk/core/pkg/foundation"
	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/apm"
	db "ucloud.dk/shared/pkg/database2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initSupportAssistAcc() {
	accapi.SupportAssistRetrieveUserInfo.Handler(func(info rpc.RequestInfo, request accapi.SupportAssistRetrieveUserInfoRequest) (accapi.SupportAssistRetrieveUserInfoResponse, *util.HttpError) {
		if request.Username == "" && request.Email == "" {
			return accapi.SupportAssistRetrieveUserInfoResponse{}, util.HttpErr(http.StatusBadRequest, "Username or email is required")
		}
		if request.Username != "" && request.Email != "" {
			return accapi.SupportAssistRetrieveUserInfoResponse{}, util.HttpErr(http.StatusBadRequest, "Only Username or Email can be specified")
		}
		return retrieveUserInfo(request.Username, request.Email)
	})
}

func usernameToUserInfo(tx *db.Transaction, username string) accapi.SupportAssistUserInfo {
	principal, found := fnd.LookupPrincipal(tx, username)
	if !found {
		return accapi.SupportAssistUserInfo{}
	}
	actor, found := rpc.LookupActor(username)
	if !found {
		return accapi.SupportAssistUserInfo{}
	}

	emailSettings := fnd.RetrieveEmailSettings(username)
	var projects []apm.Project
	for projectId, _ := range principal.Membership {
		project, err := apm.RetrieveProject(
			string(projectId),
			apm.ProjectFlags{
				IncludeMembers:  false,
				IncludeGroups:   false,
				IncludeFavorite: false,
				IncludeArchived: false,
				IncludeSettings: false,
				IncludePath:     false,
			},
		)
		if err == nil {
			projects = append(projects, project)
		}

	}
	activeGrants := GrantsBrowse(
		actor,
		accapi.GrantsBrowseRequest{
			Filter:                      util.OptValue(accapi.GrantApplicationFilterActive),
			IncludeIngoingApplications:  util.OptValue(true),
			IncludeOutgoingApplications: util.OptValue(true),
		},
	)

	personalWalletOwner := accapi.WalletOwnerFromIds(username, "")
	wallets := WalletsBrowse(
		actor,
		accapi.WalletsBrowseRequest{
			IncludeChildren: false,
		})
	var personalWallet accapi.WalletV2
	for _, elm := range wallets.Items {
		if elm.Owner == personalWalletOwner {
			personalWallet = elm
		}
	}
	return accapi.SupportAssistUserInfo{
		Username:                 username,
		FirstNames:               principal.FirstNames.Value,
		LastName:                 principal.LastName.Value,
		Email:                    principal.Email.Value,
		EmailSettings:            emailSettings,
		AssociatedProjects:       projects,
		ActiveGrants:             activeGrants.Items,
		PersonalProjectResources: personalWallet,
	}
}

func retrieveUserInfo(username string, email string) (accapi.SupportAssistRetrieveUserInfoResponse, *util.HttpError) {
	var userInfos []accapi.SupportAssistUserInfo
	if username != "" {
		db.NewTx0(func(tx *db.Transaction) {
			userInfo := usernameToUserInfo(tx, username)
			userInfos = append(userInfos, userInfo)
		})
	}
	if email != "" {
		db.NewTx0(func(tx *db.Transaction) {
			users, found := fnd.LookupUsernamesByEmail(tx, email)
			if !found {
				return
			}
			for _, user := range users {
				userInfo := usernameToUserInfo(tx, user)
				userInfos = append(userInfos, userInfo)
			}
		})
	}
	return accapi.SupportAssistRetrieveUserInfoResponse{
		Info: userInfos,
	}, nil
}
