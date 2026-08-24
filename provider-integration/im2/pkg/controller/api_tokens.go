package controller

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"

	apm "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
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
	if request.Specification.ExpiresAt.Time().Before(time.Now()) {
		return orcapi.ApiTokenStatus{}, util.HttpErr(http.StatusBadRequest, "requested token has already expired")
	}

	secret := util.SecureToken()
	hashedToken := util.HashPassword(secret, util.GenSalt())
	permissions, _ := json.Marshal(request.Specification.RequestedPermissions)

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into api_tokens(token_id, token_type, owner, permissions, token_hash, token_salt, expires_at)
				values (:token_id, :token_type, :owner, cast(:permissions as jsonb), :token_hash, :token_salt, :expires_at)
				on conflict (token_id) do update
				set
					token_type = excluded.token_type,
					owner = excluded.owner,
					permissions = excluded.permissions,
					token_hash = excluded.token_hash,
					token_salt = excluded.token_salt,
					expires_at = excluded.expires_at
			`,
			db.Params{
				"token_id":    request.Id,
				"token_type":  kind,
				"owner":       request.Owner.Project.GetOrDefault(request.Owner.CreatedBy),
				"permissions": string(permissions),
				"token_hash":  hashedToken.HashedPassword,
				"token_salt":  hashedToken.Salt,
				"expires_at":  request.Specification.ExpiresAt.Time(),
			},
		)
	})

	status := orcapi.ApiTokenStatus{Server: server}
	status.Token.Set(fmt.Sprintf("uci-%s-%s", request.Id, secret))
	return status, nil
}

type apiTokenAuthentication struct {
	Owner       string
	Permissions []orcapi.ApiTokenPermission
}

var apiTokensCache = util.NewCache[string, apiTokenAuthentication](5 * time.Minute)
var apiTokenIdToCacheKey = util.NewCache[string, string](5 * time.Minute)

func ApiTokenValidate(kind string, key string) (apm.WalletOwner, []orcapi.ApiTokenPermission, *util.HttpError) {
	tokenId, secret, ok := apiTokenParse(key)
	if !ok {
		return apm.WalletOwner{}, nil, util.HttpErr(http.StatusForbidden, "invalid key")
	}

	cacheKey := kind + "\x1f" + key
	authentication, ok := apiTokensCache.Get(cacheKey, func() (apiTokenAuthentication, error) {
		type rowType struct {
			Owner       string
			Permissions json.RawMessage
			TokenHash   []byte
			TokenSalt   []byte
		}
		row, ok := db.NewTx2(func(tx *db.Transaction) (rowType, bool) {
			return db.Get[rowType](
				tx,
				`
					select owner, permissions, token_hash, token_salt
					from api_tokens
					where token_id = :token_id and token_type = :token_type and now() <= expires_at
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

		return apiTokenAuthentication{Owner: row.Owner, Permissions: permissions}, nil
	})

	if !ok {
		return apm.WalletOwner{}, nil, util.HttpErr(http.StatusForbidden, "invalid key")
	}

	apiTokenIdToCacheKey.Set(tokenId, cacheKey)
	owner := apm.WalletOwnerFromReference(authentication.Owner)
	if authentication.Owner == "" || (owner.Username == "" && owner.ProjectId == "") {
		return apm.WalletOwner{}, nil, util.HttpErr(http.StatusForbidden, "invalid key")
	}
	return owner, authentication.Permissions, nil
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
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`delete from api_tokens where token_id = :token_id`,
			db.Params{"token_id": request.Id},
		)
	})

	if cacheKey, ok := apiTokenIdToCacheKey.GetNow(request.Id); ok {
		apiTokensCache.Invalidate(cacheKey)
	}
	apiTokenIdToCacheKey.Invalidate(request.Id)

	return util.Empty{}, nil
}
