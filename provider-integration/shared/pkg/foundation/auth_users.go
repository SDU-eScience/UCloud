package foundation

import (
	"net/http"

	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// NOTE(Dan): All other endpoints from the old UserDescriptions are no longer required.

const authUsersBaseContext = "auth/users"

type UsersCreateRequest struct {
	Username   string                     `json:"username"`
	Password   string                     `json:"password"`
	Email      string                     `json:"email"`
	Role       util.Option[PrincipalRole] `json:"role"`
	FirstNames util.Option[string]        `json:"firstNames"`
	LastName   util.Option[string]        `json:"lastname"`
	OrgId      util.Option[string]        `json:"orgId"`
}

var UsersCreate = rpc.Call[[]UsersCreateRequest, []AuthenticationTokens]{
	BaseContext: authUsersBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "register",
	Roles:       rpc.RolesPrivileged,
}

type UsersUpdateInfoRequest struct {
	Email      util.Option[string] `json:"email"`
	FirstNames util.Option[string] `json:"firstNames"`
	LastName   util.Option[string] `json:"lastName"`
}

var UsersUpdateInfo = rpc.Call[UsersUpdateInfoRequest, util.Empty]{
	BaseContext: authUsersBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "updateUserInfo",
	Roles:       rpc.RolesEndUser,
}

type UsersRetrieveInfoResponse struct {
	Email        util.Option[string] `json:"email"`
	FirstNames   util.Option[string] `json:"firstNames"`
	LastName     util.Option[string] `json:"lastName"`
	Organization util.Option[string] `json:"organization"`
}

var UsersRetrieveInfo = rpc.Call[util.Empty, UsersRetrieveInfoResponse]{
	BaseContext: authUsersBaseContext,
	Convention:  rpc.ConventionQueryParameters,
	Operation:   "userInfo",
	Roles:       rpc.RolesEndUser,
}

var UsersVerifyUserInfo = rpc.Call[FindByStringId, string]{
	BaseContext: authUsersBaseContext,
	Operation:   "verifyUserInfo",
	Convention:  rpc.ConventionQueryParameters, // intentional
	Roles:       rpc.RolesPublic,               // intentional
	CustomServerProducer: func(response string, err *util.HttpError, w http.ResponseWriter, r *http.Request) {
		if err == nil {
			http.Redirect(w, r, response, http.StatusFound)
		} else {
			rpc.SendResponseOrError(w, nil, err)
		}
	},
}

type UsersChangePasswordRequest struct {
	CurrentPassword string `json:"currentPassword"`
	NewPassword     string `json:"newPassword"`
}

var UsersChangePassword = rpc.Call[UsersChangePasswordRequest, util.Empty]{
	BaseContext: authUsersBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "password",
	Roles:       rpc.RolesEndUser,
}

type OptionalUserInfo struct {
	OrganizationFullName util.Option[string] `json:"organizationFullName"`
	Department           util.Option[string] `json:"department"`
	ResearchField        util.Option[string] `json:"researchField"`
	Position             util.Option[string] `json:"position"`
}

var UsersRetrieveOptionalInfo = rpc.Call[util.Empty, OptionalUserInfo]{
	BaseContext: authUsersBaseContext,
	Convention:  rpc.ConventionRetrieve,
	Operation:   "optionalInfo",
	Roles:       rpc.RolesEndUser,
}

var UsersUpdateOptionalInfo = rpc.Call[OptionalUserInfo, util.Empty]{
	BaseContext: authUsersBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "optionalInfo",
	Roles:       rpc.RolesEndUser,
}
