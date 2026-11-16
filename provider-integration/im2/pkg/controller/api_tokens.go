package controller

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"

	apm "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var ApiTokens ApiTokenService

type ApiTokenService struct {
	Providers []ApiTokenProvider
}

type ApiTokenProvider struct {
	Kind    string
	Options orcapi.ApiTokenOptions
	Create  func(info rpc.RequestInfo, request orcapi.ApiToken) (orcapi.ApiTokenStatus, *util.HttpError)
}

func initApiTokens() {
	if RunsUserCode() {
		orcapi.ApiTokenProviderCreate.Handler(func(info rpc.RequestInfo, request orcapi.ApiToken) (orcapi.ApiTokenStatus, *util.HttpError) {
			provider, err := apiTokenProviderFor(request.Specification.RequestedPermissions)
			if err != nil {
				return orcapi.ApiTokenStatus{}, err
			}
			if provider.Create == nil {
				return orcapi.ApiTokenStatus{}, util.ServerHttpError("API token creation not supported")
			}

			return provider.Create(info, request)
		})

		orcapi.ApiTokenProviderRevoke.Handler(func(info rpc.RequestInfo, request fnd.FindByStringId) (util.Empty, *util.HttpError) {
			return ApiTokenRevoke(info, request)
		})

		orcapi.ApiTokenProviderRetrieveOptions.Handler(func(info rpc.RequestInfo, request util.Empty) (orcapi.ApiTokenOptions, *util.HttpError) {
			result := orcapi.ApiTokenOptions{AvailablePermissions: []orcapi.ApiTokenPermissionSpecification{}}
			for _, provider := range ApiTokens.Providers {
				result.AvailablePermissions = append(result.AvailablePermissions, provider.Options.AvailablePermissions...)
			}
			return result, nil
		})
	}
}

func apiTokenProviderFor(permissions []orcapi.ApiTokenPermission) (ApiTokenProvider, *util.HttpError) {
	if _, ok := orcapi.ApiTokenServiceFromPermissions(permissions); !ok {
		return ApiTokenProvider{}, util.HttpErr(http.StatusBadRequest, "a service provider token must request exactly one service")
	}

	var firstErr *util.HttpError
	for _, provider := range ApiTokens.Providers {
		err := ApiTokenValidatePermissions(provider.Options, permissions)
		if err == nil {
			return provider, nil
		}
		if firstErr == nil {
			firstErr = err
		}
	}

	if firstErr != nil {
		return ApiTokenProvider{}, firstErr
	}
	return ApiTokenProvider{}, util.HttpErr(http.StatusBadRequest, "invalid token requested, these permissions are not available")
}

func ApiTokenValidatePermissions(options orcapi.ApiTokenOptions, permissions []orcapi.ApiTokenPermission) *util.HttpError {
	permissionsByName := map[string]orcapi.ApiTokenPermissionSpecification{}
	for _, option := range options.AvailablePermissions {
		permissionsByName[option.Name] = option
	}

	for _, permission := range permissions {
		option, ok := permissionsByName[permission.Name]
		if !ok {
			return util.HttpErr(http.StatusBadRequest, "invalid token requested, %s is not available", permission.Name)
		}
		if _, ok := option.Actions[permission.Action]; !ok {
			return util.HttpErr(http.StatusBadRequest, "invalid token requested, %s/%s is not available", permission.Name, permission.Action)
		}
	}

	return nil
}

func ApiTokenCreate(kind string, server string, request orcapi.ApiToken) (orcapi.ApiTokenStatus, *util.HttpError) {
	if !request.Specification.JobId.Present && request.Specification.ExpiresAt.Time().Before(time.Now()) {
		return orcapi.ApiTokenStatus{}, util.HttpErr(http.StatusBadRequest, "requested token has already expired")
	}

	secret := util.SecureToken()
	hashedToken := util.HashPassword(secret, util.GenSalt())
	permissions, _ := json.Marshal(request.Specification.RequestedPermissions)
	expiresAt := sql.Null[time.Time]{}
	if !request.Specification.JobId.Present {
		expiresAt.V = request.Specification.ExpiresAt.Time()
		expiresAt.Valid = true
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into api_tokens(token_id, token_type, owner, permissions, token_hash, token_salt, expires_at, created_by, job_id)
				values (:token_id, :token_type, :owner, cast(:permissions as jsonb), :token_hash, :token_salt, :expires_at, :created_by, :job_id)
				on conflict (token_id) do update
				set
					token_type = excluded.token_type,
					owner = excluded.owner,
					permissions = excluded.permissions,
					token_hash = excluded.token_hash,
					token_salt = excluded.token_salt,
					expires_at = excluded.expires_at,
					created_by = excluded.created_by,
					job_id = excluded.job_id
			`,
			db.Params{
				"token_id":    request.Id,
				"token_type":  kind,
				"owner":       request.Owner.Project.GetOrDefault(request.Owner.CreatedBy),
				"permissions": string(permissions),
				"token_hash":  hashedToken.HashedPassword,
				"token_salt":  hashedToken.Salt,
				"expires_at":  expiresAt,
				"created_by":  request.Owner.CreatedBy,
				"job_id":      request.Specification.JobId.Sql(),
			},
		)
	})

	status := orcapi.ApiTokenStatus{Server: server}
	status.Token.Set(fmt.Sprintf("uci-%s-%s", request.Id, secret))
	return status, nil
}

type apiTokenAuthentication struct {
	Owner       string
	CreatedBy   string
	Permissions []orcapi.ApiTokenPermission
	ExpiresAt   time.Time
}

var apiTokensCache = util.NewCache[string, apiTokenAuthentication](1 * time.Minute)
var apiTokenIdToCacheKey = util.NewCache[string, string](1 * time.Minute)

type ApiTokenAuthentication struct {
	TokenId     string
	Owner       apm.WalletOwner
	CreatedBy   string
	Permissions []orcapi.ApiTokenPermission
	ExpiresAt   time.Time
}

type ApiTokenIdentity struct {
	TokenId     string
	Owner       apm.WalletOwner
	Username    string
	Permissions []orcapi.ApiTokenPermission
	ExpiresAt   time.Time
}

func ApiTokenValidateWithIdentity(kind string, key string) (ApiTokenIdentity, *util.HttpError) {
	tokenId, _, ok := apiTokenParse(key)
	if !ok {
		return ApiTokenIdentity{}, util.HttpErr(http.StatusForbidden, "invalid key")
	}

	authentication, err := ApiTokenValidate(kind, key)
	if err != nil {
		return ApiTokenIdentity{}, err
	}

	username := authentication.CreatedBy
	if username == "" {
		username = authentication.Owner.Username
	}
	return ApiTokenIdentity{
		TokenId:     tokenId,
		Owner:       authentication.Owner,
		Username:    username,
		Permissions: authentication.Permissions,
		ExpiresAt:   authentication.ExpiresAt,
	}, nil
}

func ApiTokenValidate(kind string, key string) (ApiTokenAuthentication, *util.HttpError) {
	tokenId, secret, ok := apiTokenParse(key)
	if !ok {
		return ApiTokenAuthentication{}, util.HttpErr(http.StatusForbidden, "invalid key")
	}

	cacheKey := kind + "\x1f" + key
	authentication, ok := apiTokensCache.Get(cacheKey, func() (apiTokenAuthentication, error) {
		type rowType struct {
			Owner       string
			CreatedBy   sql.Null[string]
			Permissions json.RawMessage
			TokenHash   []byte
			TokenSalt   []byte
			ExpiresAt   sql.Null[time.Time]
		}
		row, ok := db.NewTx2(func(tx *db.Transaction) (rowType, bool) {
			return db.Get[rowType](
				tx,
				`
					select owner, created_by, permissions, token_hash, token_salt, expires_at
					from api_tokens
					where
						token_id = :token_id
						and token_type = :token_type
						and (expires_at is null or now() <= expires_at)
				`,
				db.Params{
					"token_id":   tokenId,
					"token_type": kind,
				},
			)
		})

		if !ok || !util.CheckPassword(row.TokenHash, row.TokenSalt, secret) {
			return apiTokenAuthentication{}, util.HttpErr(http.StatusForbidden, "invalid key").AsError()
		}

		var permissions []orcapi.ApiTokenPermission
		if err := json.Unmarshal(row.Permissions, &permissions); err != nil {
			return apiTokenAuthentication{}, fmt.Errorf("invalid API token permissions: %w", err)
		}

		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`update api_tokens set last_used_at = now() where token_id = :token_id and token_type = :token_type`,
				db.Params{
					"token_id":   tokenId,
					"token_type": kind,
				},
			)
		})

		return apiTokenAuthentication{
			Owner:       row.Owner,
			CreatedBy:   row.CreatedBy.V,
			Permissions: permissions,
			ExpiresAt:   row.ExpiresAt.V,
		}, nil
	})

	if !ok {
		return ApiTokenAuthentication{}, util.HttpErr(http.StatusForbidden, "invalid key")
	}

	if !authentication.ExpiresAt.IsZero() && !time.Now().Before(authentication.ExpiresAt) {
		apiTokensCache.Invalidate(cacheKey)
		apiTokenIdToCacheKey.Invalidate(tokenId)
		return ApiTokenAuthentication{}, util.HttpErr(http.StatusForbidden, "invalid key")
	}

	apiTokenIdToCacheKey.Set(tokenId, cacheKey)
	owner := apm.WalletOwnerFromReference(authentication.Owner)
	if authentication.Owner == "" || (owner.Username == "" && owner.ProjectId == "") {
		return ApiTokenAuthentication{}, util.HttpErr(http.StatusForbidden, "invalid key")
	}
	return ApiTokenAuthentication{
		TokenId:     tokenId,
		Owner:       owner,
		CreatedBy:   authentication.CreatedBy,
		Permissions: authentication.Permissions,
		ExpiresAt:   authentication.ExpiresAt,
	}, nil
}

func apiTokenParse(raw string) (tokenId string, secret string, ok bool) {
	payload, hasPrefix := strings.CutPrefix(raw, "uci-")
	if !hasPrefix {
		return "", "", false
	}

	tokenId, secret, ok = strings.Cut(payload, "-")
	if !ok || tokenId == "" || secret == "" {
		return "", "", false
	}

	return tokenId, secret, true
}

func ApiTokenRevoke(info rpc.RequestInfo, request fnd.FindByStringId) (util.Empty, *util.HttpError) {
	apiTokenRevoke(request.Id)
	return util.Empty{}, nil
}

func apiTokenRevoke(tokenId string) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`delete from api_tokens where token_id = :token_id`,
			db.Params{"token_id": tokenId},
		)
	})

	if cacheKey, ok := apiTokenIdToCacheKey.GetNow(tokenId); ok {
		apiTokensCache.Invalidate(cacheKey)
	}
	apiTokenIdToCacheKey.Invalidate(tokenId)
}

func ApiTokensReconcileProject(projectId string, members []string, notBefore time.Time) {
	memberSet := map[string]bool{}
	for _, member := range members {
		memberSet[member] = true
	}

	type projectTokenRow struct {
		TokenId   string
		CreatedBy sql.Null[string]
		CreatedAt time.Time
	}

	rows := db.NewTx(func(tx *db.Transaction) []projectTokenRow {
		return db.Select[projectTokenRow](
			tx,
			`
				select
					token_id,
					created_by,
					created_at
				from
					api_tokens
				where
					owner = :project_id
					and job_id is null
			`,
			db.Params{
				"project_id": projectId,
			},
		)
	})

	for _, row := range rows {
		createdBy := strings.TrimSpace(row.CreatedBy.V)
		if !row.CreatedBy.Valid || createdBy == "" {
			continue
		}

		if createdBy == "_ucloud" {
			continue
		}

		if memberSet[createdBy] {
			continue
		}

		if !notBefore.IsZero() && row.CreatedAt.After(notBefore) {
			continue
		}

		apiTokenRevoke(row.TokenId)
	}
}

func ApiTokensReconcileAllProjects() {
	type trackedProject struct {
		ProjectId       string
		MemberUsernames json.RawMessage
		LastUpdate      time.Time
	}

	rows := db.NewTx(func(tx *db.Transaction) []trackedProject {
		return db.Select[trackedProject](
			tx,
			`
				select
					project_id,
					member_usernames,
					last_update
				from
					tracked_projects
			`,
			db.Params{},
		)
	})

	for _, row := range rows {
		var members []string
		err := json.Unmarshal(row.MemberUsernames, &members)
		if err != nil {
			log.Warn("Could not read project members for project %v: %v", row.ProjectId, err)
			continue
		}

		if len(members) == 0 {
			continue
		}

		ApiTokensReconcileProject(row.ProjectId, members, row.LastUpdate)
	}
}
