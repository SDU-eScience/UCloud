package foundation

import (
	"encoding/json"
	"net/http"
	"strings"

	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type PrincipalRole string

const (
	PrincipalUser     PrincipalRole = "USER"
	PrincipalAdmin    PrincipalRole = "ADMIN"
	PrincipalService  PrincipalRole = "SERVICE"
	PrincipalProvider PrincipalRole = "PROVIDER"
)

var PrincipalRoleOptions = []PrincipalRole{
	PrincipalUser,
	PrincipalAdmin,
	PrincipalService,
	PrincipalProvider,
}

const AuthContext = "auth"

type AuthenticationTokens struct {
	Username     string              `json:"username"`
	RefreshToken string              `json:"refreshToken"`
	AccessToken  string              `json:"accessToken"`
	CsrfToken    util.Option[string] `json:"csrfToken"`
	ExpiresAt    Timestamp           `json:"expiresAt"`
}

type AccessTokenAndCsrf struct {
	AccessToken string `json:"accessToken"`
	CsrfToken   string `json:"csrfToken"`
}

type PasswordLoginRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
	Service  string `json:"service"`
}

var AuthRefresh = rpc.Call[AuthenticationTokens, AccessTokenAndCsrf]{
	BaseContext: AuthContext,
	Operation:   "refresh",
	Convention:  rpc.ConventionCustom,
	Roles:       rpc.RolesPublic,

	CustomPath:   "/auth/refresh",
	CustomMethod: "POST",

	CustomClientHandler: func(self *rpc.Call[AuthenticationTokens, AccessTokenAndCsrf], client *rpc.Client, request AuthenticationTokens) (AccessTokenAndCsrf, *util.HttpError) {
		panic("Do not call directly via client")
	},

	CustomServerParser: func(w http.ResponseWriter, r *http.Request) (AuthenticationTokens, *util.HttpError) {
		authHeader := r.Header.Get("Authorization")
		token, ok := strings.CutPrefix(authHeader, "Bearer ")
		if !ok {
			return AuthenticationTokens{}, util.HttpErr(http.StatusForbidden, "Forbidden")
		} else {
			return AuthenticationTokens{RefreshToken: token}, nil
		}
	},

	Audit: rpc.AuditRules{
		Transformer: func(request any) json.RawMessage {
			return json.RawMessage("{}")
		},
	},
}

var AuthRefreshWeb = rpc.Call[AuthenticationTokens, AccessTokenAndCsrf]{
	BaseContext: AuthContext,
	Operation:   "refreshWeb",
	Convention:  rpc.ConventionCustom,
	Roles:       rpc.RolesPublic,

	CustomPath:   "/auth/refresh/web",
	CustomMethod: "POST",

	CustomClientHandler: func(self *rpc.Call[AuthenticationTokens, AccessTokenAndCsrf], client *rpc.Client, request AuthenticationTokens) (AccessTokenAndCsrf, *util.HttpError) {
		panic("Do not call directly via client")
	},

	CustomServerParser: func(w http.ResponseWriter, r *http.Request) (AuthenticationTokens, *util.HttpError) {
		csrfToken := r.Header.Get("X-CSRFToken")
		refreshToken, err := r.Cookie("refreshToken")

		if csrfToken == "" || err != nil || refreshToken.Value == "" {
			return AuthenticationTokens{}, util.HttpErr(http.StatusForbidden, "Forbidden")
		}

		return AuthenticationTokens{
			RefreshToken: refreshToken.Value,
			CsrfToken:    util.OptValue(csrfToken),
		}, nil
	},

	Audit: rpc.AuditRules{
		Transformer: func(request any) json.RawMessage {
			return json.RawMessage("{}")
		},
	},
}

var AuthLogout = rpc.Call[AuthenticationTokens, util.Empty]{
	BaseContext: AuthContext,
	Operation:   "logout",
	Convention:  rpc.ConventionCustom,
	Roles:       rpc.RolesPublic,

	CustomPath:   "/auth/logout",
	CustomMethod: "POST",

	CustomClientHandler: func(self *rpc.Call[AuthenticationTokens, util.Empty], client *rpc.Client, request AuthenticationTokens) (util.Empty, *util.HttpError) {
		panic("Do not call directly via client")
	},

	CustomServerParser: func(w http.ResponseWriter, r *http.Request) (AuthenticationTokens, *util.HttpError) {
		authHeader := r.Header.Get("Authorization")
		token, ok := strings.CutPrefix(authHeader, "Bearer ")
		if !ok {
			return AuthenticationTokens{}, util.HttpErr(http.StatusForbidden, "Forbidden")
		} else {
			return AuthenticationTokens{RefreshToken: token}, nil
		}
	},

	Audit: rpc.AuditRules{
		Transformer: func(request any) json.RawMessage {
			return json.RawMessage("{}")
		},
	},
}

var AuthLogoutWeb = rpc.Call[AuthenticationTokens, util.Empty]{
	BaseContext: AuthContext,
	Operation:   "logoutWeb",
	Convention:  rpc.ConventionCustom,
	Roles:       rpc.RolesPublic,

	CustomPath:   "/auth/logout/web",
	CustomMethod: "POST",

	CustomClientHandler: func(self *rpc.Call[AuthenticationTokens, util.Empty], client *rpc.Client, request AuthenticationTokens) (util.Empty, *util.HttpError) {
		panic("Do not call directly via client")
	},

	CustomServerParser: func(w http.ResponseWriter, r *http.Request) (AuthenticationTokens, *util.HttpError) {
		csrfToken := r.Header.Get("X-CSRFToken")
		refreshToken, err := r.Cookie("refreshToken")

		if csrfToken == "" || err != nil || refreshToken.Value == "" {
			return AuthenticationTokens{}, util.HttpErr(http.StatusForbidden, "Forbidden")
		}

		return AuthenticationTokens{
			RefreshToken: refreshToken.Value,
			CsrfToken:    util.OptValue(csrfToken),
		}, nil
	},

	Audit: rpc.AuditRules{
		Transformer: func(request any) json.RawMessage {
			return json.RawMessage("{}")
		},
	},
}

// Used primarily for testing purposes. Does not support 2FA.
var AuthPasswordLoginServer = rpc.Call[PasswordLoginRequest, AuthenticationTokens]{
	BaseContext: AuthContext,
	Operation:   "loginServer",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
	Audit: rpc.AuditRules{
		Transformer: func(request any) json.RawMessage {
			return json.RawMessage("{}")
		},
	},
}

var AuthPasswordLoginWeb = rpc.Call[PasswordLoginRequest, util.Empty]{
	BaseContext: AuthContext,
	Operation:   "loginWeb",
	Convention:  rpc.ConventionCustom,
	Roles:       rpc.RolesPublic,

	CustomPath:   "/auth/login",
	CustomMethod: "POST",

	CustomClientHandler: func(self *rpc.Call[PasswordLoginRequest, util.Empty], client *rpc.Client, request PasswordLoginRequest) (util.Empty, *util.HttpError) {
		panic("Do not call directly via client")
	},

	CustomServerParser: func(w http.ResponseWriter, r *http.Request) (PasswordLoginRequest, *util.HttpError) {
		err := r.ParseMultipartForm(1024 * 64)
		if err != nil {
			return PasswordLoginRequest{}, util.HttpErr(http.StatusBadRequest, "Bad request")
		}

		username := r.PostFormValue("username")
		password := r.PostFormValue("password")
		service := r.PostFormValue("service")

		if username == "" || password == "" || service == "" {
			return PasswordLoginRequest{}, util.HttpErr(http.StatusBadRequest, "Bad request")
		}

		return PasswordLoginRequest{
			Username: username,
			Password: password,
			Service:  service,
		}, nil
	},

	CustomServerProducer: func(response util.Empty, err *util.HttpError, w http.ResponseWriter, r *http.Request) {
		if err != nil {
			rpc.SendResponseOrError(r, w, nil, err)
		} else {
			// Do nothing, already handled.
		}
	},

	Audit: rpc.AuditRules{
		Transformer: func(request any) json.RawMessage {
			newMessage := map[string]any{
				"username": request.(PasswordLoginRequest).Username,
			}

			resp, _ := json.Marshal(newMessage)
			return resp
		},
	},
}

type AuthSessionInfo struct {
	IpAddress string    `json:"ipAddress"`
	UserAgent string    `json:"userAgent"`
	CreatedAt Timestamp `json:"createdAt"`
}

var AuthListSessions = rpc.Call[util.Empty, Page[AuthSessionInfo]]{
	BaseContext: AuthContext,
	Operation:   "sessions",
	Convention:  rpc.ConventionQueryParameters,
	Roles:       rpc.RolesEndUser,
}

var AuthInvalidateSessions = rpc.Call[util.Empty, util.Empty]{
	BaseContext: AuthContext,
	Operation:   "sessions",
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesEndUser,
}

type IdentityProvider struct {
	Id      int                 `json:"id"`
	Title   string              `json:"title"`
	LogoUrl util.Option[string] `json:"logoUrl"`
}

var AuthBrowseIdentityProviders = rpc.Call[util.Empty, BulkResponse[IdentityProvider]]{
	BaseContext: AuthContext,
	Operation:   "identityProviders",
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesPublic,
}

var AuthStartLoginSamlLegacy = rpc.Call[util.Empty, util.Empty]{
	BaseContext: AuthContext + "/saml",
	Operation:   "login",
	Convention:  rpc.ConventionQueryParameters,
	Roles:       rpc.RolesPublic,
	CustomServerProducer: func(response util.Empty, err *util.HttpError, w http.ResponseWriter, r *http.Request) {
		if err != nil {
			rpc.SendResponseOrError(r, w, nil, err)
		} else {
			// Already handled
		}
	},
}

var AuthStartLogin = rpc.Call[FindByIntId, util.Empty]{
	BaseContext: AuthContext,
	Operation:   "startLogin",
	Convention:  rpc.ConventionCustom,
	Roles:       rpc.RolesPublic,

	CustomMethod: http.MethodGet,
	CustomPath:   "/auth/startLogin",
	CustomClientHandler: func(self *rpc.Call[FindByIntId, util.Empty], client *rpc.Client, request FindByIntId) (util.Empty, *util.HttpError) {
		panic("Do not use in a client")
	},
	CustomServerParser: func(w http.ResponseWriter, r *http.Request) (FindByIntId, *util.HttpError) {
		return rpc.ParseRequestFromQuery[FindByIntId](w, r)
	},
	CustomServerProducer: func(response util.Empty, err *util.HttpError, w http.ResponseWriter, r *http.Request) {
		if err != nil {
			rpc.SendResponseOrError(r, w, nil, err)
		} else {
			// Already handled
		}
	},
}

type AuthOidcCallbackRequest struct {
	Code  string `json:"code"`
	State string `json:"state"`
}

var AuthOidcCallback = rpc.Call[AuthOidcCallbackRequest, util.Empty]{
	BaseContext: AuthContext,
	Operation:   "oidcCallback",
	Convention:  rpc.ConventionCustom,
	Roles:       rpc.RolesPublic,

	CustomMethod: http.MethodGet,
	CustomPath:   "/auth/oidc",
	CustomClientHandler: func(self *rpc.Call[AuthOidcCallbackRequest, util.Empty], client *rpc.Client, request AuthOidcCallbackRequest) (util.Empty, *util.HttpError) {
		panic("Do not use in a client")
	},
	CustomServerParser: func(w http.ResponseWriter, r *http.Request) (AuthOidcCallbackRequest, *util.HttpError) {
		return rpc.ParseRequestFromQuery[AuthOidcCallbackRequest](w, r)
	},
	CustomServerProducer: func(response util.Empty, err *util.HttpError, w http.ResponseWriter, r *http.Request) {
		if err != nil {
			rpc.SendResponseOrError(r, w, nil, err)
		} else {
			// Already handled
		}
	},
}

var AuthLookupUser = rpc.Call[FindByStringId, rpc.CorePrincipalBaseClaims]{
	BaseContext: AuthContext,
	Operation:   "lookupUser",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesService,
}
