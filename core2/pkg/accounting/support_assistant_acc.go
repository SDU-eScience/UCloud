package accounting

import (
	"net/http"
	"strconv"
	fnd "ucloud.dk/core/pkg/foundation"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database2"
	"ucloud.dk/shared/pkg/foundation"
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
		println("RUNNINGN INFO GET")
		return retrieveUserInfo(request.Username, request.Email)
	})
}

func usernameToUserInfo(tx *db.Transaction, username string) (accapi.SupportAssistUserInfo, bool) {
	println("USERINFO FUNC")
	principal, found := fnd.LookupPrincipal(tx, username)
	if !found {
		return accapi.SupportAssistUserInfo{}, false
	}
	actor, found := rpc.LookupActor(username)
	if !found {
		return accapi.SupportAssistUserInfo{}, false
	}

	emailSettings := fnd.RetrieveEmailSettings(username)
	var projects []foundation.Project
	for projectId, _ := range principal.Membership {
		project, err := fnd.ProjectRetrieve(
			rpc.ActorSystem,
			string(projectId),
			foundation.ProjectFlags{
				IncludeMembers:  false,
				IncludeGroups:   false,
				IncludeFavorite: false,
				IncludeArchived: false,
				IncludeSettings: false,
				IncludePath:     false,
			},
			foundation.ProjectRoleAdmin,
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
	}, true
}

func retrieveUserInfo(username string, email string) (accapi.SupportAssistRetrieveUserInfoResponse, *util.HttpError) {
	println("GIVEN USER: " + username + " email : " + email)
	var userInfos []accapi.SupportAssistUserInfo
	if username != "" {
		println("GETTING BY USERNAME")
		db.NewTx0(func(tx *db.Transaction) {
			userInfo, found := usernameToUserInfo(tx, username)
			if found {
				println("ADDING USER INFO")
				userInfos = append(userInfos, userInfo)
			}
		})
	}
	if email != "" {
		println("GETTING BY EMIAL")

		db.NewTx0(func(tx *db.Transaction) {
			users, found := fnd.LookupUsernamesByEmail(tx, email)
			if !found {
				return
			}
			for _, user := range users {
				userInfo, foundUserInfo := usernameToUserInfo(tx, user)
				if foundUserInfo {
					userInfos = append(userInfos, userInfo)
				}
			}
		})
	}
	for i, userInfo := range userInfos {
		println("USER #" + strconv.Itoa(i+1) + ": " + userInfo.Username)
		println(userInfo.FirstNames)
		println(userInfo.LastName)
	}
	return accapi.SupportAssistRetrieveUserInfoResponse{
		Info: userInfos,
	}, nil
}
