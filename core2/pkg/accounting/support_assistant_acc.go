package accounting

import (
	"net/http"

	accapi "ucloud.dk/shared/pkg/accounting"
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
		return retrieveUserInfo(request.Username, request.Email)
	})
}

func usernameToUserInfo(username string) (accapi.SupportAssistUserInfo, bool) {
	principal, err := foundation.AuthLookupUser.Invoke(foundation.FindByStringId{Id: username})
	if err != nil {
		return accapi.SupportAssistUserInfo{}, false
	}
	actor, found := rpc.LookupActor(username)
	if !found {
		return accapi.SupportAssistUserInfo{}, false
	}
	emailSettings, _ := foundation.MailRetrieveSettings.Invoke(util.Empty{})
	var projects []foundation.Project
	for projectId, _ := range principal.Membership {
		project, err := foundation.ProjectRetrieve.Invoke(foundation.ProjectRetrieveRequest{
			Id: string(projectId),
			ProjectFlags: foundation.ProjectFlags{
				IncludeMembers:  true,
				IncludeGroups:   false,
				IncludeFavorite: false,
				IncludeArchived: false,
				IncludeSettings: false,
				IncludePath:     false,
			},
		})
		if err == nil {
			projects = append(projects, project)
		}

	}
	foundGrants := GrantsBrowse(
		actor,
		accapi.GrantsBrowseRequest{
			ItemsPerPage:                10,
			Filter:                      util.OptValue(accapi.GrantApplicationFilterShowAll),
			IncludeIngoingApplications:  util.OptValue(true),
			IncludeOutgoingApplications: util.OptValue(true),
		},
	)

	var grantResults []accapi.GrantApplication
	for _, grant := range foundGrants.Items {
		reference := grant.CurrentRevision.Document.Recipient.Reference().Value
		if reference == username {
			grantResults = append(grantResults, grant)
		}
	}

	personalWalletOwner := accapi.WalletOwnerFromIds(username, "")
	wallets := WalletsBrowse(
		actor,
		accapi.WalletsBrowseRequest{
			IncludeChildren: false,
		})
	var personalWallets []accapi.WalletV2
	for _, elm := range wallets.Items {
		if elm.Owner == personalWalletOwner {
			personalWallets = append(personalWallets, elm)
		}
	}
	return accapi.SupportAssistUserInfo{
		Username:                 username,
		FirstNames:               principal.FirstNames.Value,
		LastName:                 principal.LastName.Value,
		Email:                    principal.Email.Value,
		EmailSettings:            emailSettings.Settings,
		AssociatedProjects:       projects,
		ActiveGrants:             grantResults,
		PersonalProjectResources: personalWallets,
	}, true
}

func retrieveUserInfo(username string, email string) (accapi.SupportAssistRetrieveUserInfoResponse, *util.HttpError) {
	userInfos := make(map[string]accapi.SupportAssistUserInfo)
	if username != "" {
		userInfo, found := usernameToUserInfo(username)
		if found {
			userInfos[userInfo.Username] = userInfo
		}
	}
	if email != "" {
		users, err := foundation.AuthLookupUsersByEmail.Invoke(foundation.AuthLookupUsersByEmailRequest{Email: email})
		if err != nil {
			return accapi.SupportAssistRetrieveUserInfoResponse{}, err
		}
		for _, user := range users.Users {
			userInfo, foundUserInfo := usernameToUserInfo(user)
			if foundUserInfo {
				userInfos[userInfo.Username] = userInfo
			}
		}
	}
	var results []accapi.SupportAssistUserInfo
	for _, userInfo := range userInfos {
		results = append(results, userInfo)
	}
	return accapi.SupportAssistRetrieveUserInfoResponse{
		Info: results,
	}, nil
}
