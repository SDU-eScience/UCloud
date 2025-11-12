package accounting

import (
	"net/http"

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
		return retrieveUserInfo(request.Username, request.Email)
	})
}

func usernameToUserInfo(tx *db.Transaction, username string) (accapi.SupportAssistUserInfo, bool) {
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
				IncludeMembers:  true,
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
		EmailSettings:            emailSettings,
		AssociatedProjects:       projects,
		ActiveGrants:             grantResults,
		PersonalProjectResources: personalWallets,
	}, true
}

// TODO(Henrik) NewTx0 is resulting in 2 runs. Currently fixed issue of results by using map instead of list, but it seems wierd
func retrieveUserInfo(username string, email string) (accapi.SupportAssistRetrieveUserInfoResponse, *util.HttpError) {

	userInfos := make(map[string]accapi.SupportAssistUserInfo)
	if username != "" {
		db.NewTx0(func(tx *db.Transaction) {
			userInfo, found := usernameToUserInfo(tx, username)
			if found {
				userInfos[userInfo.Username] = userInfo
			}
		})
	}
	if email != "" {
		db.NewTx0(func(tx *db.Transaction) {
			users := fnd.PrincipalLookupByEmail(tx, email)
			for _, user := range users {
				userInfo, foundUserInfo := usernameToUserInfo(tx, user)
				if foundUserInfo {
					userInfos[userInfo.Username] = userInfo
				}
			}
		})
	}
	var results []accapi.SupportAssistUserInfo
	for _, userInfo := range userInfos {
		results = append(results, userInfo)
	}
	return accapi.SupportAssistRetrieveUserInfoResponse{
		Info: results,
	}, nil
}
