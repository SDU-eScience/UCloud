package inference

import (
	"fmt"
	"net/http"
	"strings"
	"time"

	"ucloud.dk/pkg/controller"
	apm "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// API tokens
// =====================================================================================================================

var inferenceApiKeysCache = util.NewCache[string, string](5 * time.Minute)
var inferenceTokenIdToKey = util.NewCache[string, string](5 * time.Minute)

func inferenceApiKeyValidate(key string) (apm.WalletOwner, *util.HttpError) {
	tokenId, secret, ok := inferenceParseToken(key)
	if !ok {
		return apm.WalletOwner{}, util.HttpErr(http.StatusForbidden, "invalid key")
	}

	ownerRef, ok := inferenceApiKeysCache.Get(key, func() (string, error) {
		type rowType struct {
			Owner     string
			TokenHash []byte
			TokenSalt []byte
		}
		row, ok := db.NewTx2(func(tx *db.Transaction) (rowType, bool) {
			return db.Get[rowType](
				tx,
				`
					select owner, token_hash, token_salt
					from inference_api_keys
					where token_id = :token_id and now() <= expires_at
				`,
				db.Params{
					"token_id": tokenId,
				},
			)
		})

		if !ok || !util.CheckPassword(row.TokenHash, row.TokenSalt, secret) {
			return "", util.HttpErr(http.StatusForbidden, "invalid key").AsError()
		}

		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`update inference_api_keys set last_used_at = now() where token_id = :token_id`,
				db.Params{
					"token_id": tokenId,
				},
			)
		})

		inferenceTokenIdToKey.Set(tokenId, key)
		return row.Owner, nil
	})

	if !ok {
		return apm.WalletOwner{}, util.HttpErr(http.StatusForbidden, "invalid key")
	}

	inferenceTokenIdToKey.Set(tokenId, key)

	owner := apm.WalletOwnerFromReference(ownerRef)
	if ownerRef == "" || (owner.Username == "" && owner.ProjectId == "") {
		return apm.WalletOwner{}, util.HttpErr(http.StatusForbidden, "invalid key")
	}
	if controller.WalletIsLocked(owner, inferenceGlobals.Product.Category.Name).Locked {
		return apm.WalletOwner{}, util.HttpErr(http.StatusPaymentRequired, "no more resources available")
	} else {
		return owner, nil
	}
}

func InitApiTokens() controller.ApiTokenService {
	return controller.ApiTokenService{
		Create:          inferenceCreateApiToken,
		Revoke:          inferenceRevokeApiToken,
		RetrieveOptions: inferenceRetrieveApiTokenOptions,
	}
}

func inferenceCreateApiToken(info rpc.RequestInfo, request orcapi.ApiToken) (orcapi.ApiTokenStatus, *util.HttpError) {
	_ = info

	if !inferenceGlobals.Ready.Load() {
		return orcapi.ApiTokenStatus{}, util.HttpErr(http.StatusServiceUnavailable, "inference service is not available")
	}

	if request.Specification.ExpiresAt.Time().Before(time.Now()) {
		return orcapi.ApiTokenStatus{}, util.HttpErr(http.StatusBadRequest, "requested token has already expired")
	}

	if err := inferenceValidateRequestedPermissions(request.Specification.RequestedPermissions); err != nil {
		return orcapi.ApiTokenStatus{}, err
	}

	secret := util.SecureToken()
	hashedToken := util.HashPassword(secret, util.GenSalt())

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into inference_api_keys(token_id, owner, token_hash, token_salt, expires_at)
				values (:token_id, :owner, :token_hash, :token_salt, :expires_at)
				on conflict (token_id) do update
				set
					owner = excluded.owner,
					token_hash = excluded.token_hash,
					token_salt = excluded.token_salt,
					expires_at = excluded.expires_at
			`,
			db.Params{
				"token_id":   request.Id,
				"owner":      request.Owner.Project.GetOrDefault(request.Owner.CreatedBy),
				"token_hash": hashedToken.HashedPassword,
				"token_salt": hashedToken.Salt,
				"expires_at": request.Specification.ExpiresAt.Time(),
			},
		)
	})

	status := orcapi.ApiTokenStatus{Server: inferenceServerBase()}
	status.Token.Set(fmt.Sprintf("uci-%s-%s", request.Id, secret))
	return status, nil
}

func inferenceParseToken(raw string) (tokenId string, secret string, ok bool) {
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

func inferenceValidateRequestedPermissions(perms []orcapi.ApiTokenPermission) *util.HttpError {
	for _, perm := range perms {
		if perm.Name != "inference" || perm.Action != "use" {
			return util.HttpErr(
				http.StatusBadRequest,
				"invalid token requested, %s/%s is not available",
				perm.Name,
				perm.Action,
			)
		}
	}

	return nil
}

func inferenceRevokeApiToken(info rpc.RequestInfo, request fnd.FindByStringId) (util.Empty, *util.HttpError) {
	log.Info("Revoking inference API token: tokenId=%s user=%s", request.Id, info.Actor.Username)

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
			delete from inference_api_keys where token_id = :token_id
		    `,
			db.Params{
				"token_id": request.Id,
			},
		)
	})

	if cacheKey, ok := inferenceTokenIdToKey.GetNow(request.Id); ok {
		inferenceApiKeysCache.Invalidate(cacheKey)
	}

	inferenceTokenIdToKey.Invalidate(request.Id)

	return util.Empty{}, nil
}

func inferenceRetrieveApiTokenOptions(info rpc.RequestInfo, request util.Empty) (orcapi.ApiTokenOptions, *util.HttpError) {
	_ = info
	_ = request

	return orcapi.ApiTokenOptions{
		AvailablePermissions: []orcapi.ApiTokenPermissionSpecification{
			{
				Name:        "inference",
				Title:       "Inference",
				Description: "API token required for inference services",
				Actions: map[string]string{
					"use": "Use",
				},
			},
		},
	}, nil
}
