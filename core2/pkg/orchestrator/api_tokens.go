package orchestrator

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"

	cfg "ucloud.dk/core/pkg/config"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const apiTokenType = "api_token"

func initApiTokens() {
	InitResourceType(
		apiTokenType,
		resourceTypeCreateWithoutAdmin|resourceTypeCreateAsAllocator, // NOTE(Dan): Tokens are tied to a user in a project, thus this makes sense.
		apiTokensLoad,
		apiTokensPersist,
		apiTokensTransform,
		nil,
	)

	orcapi.ApiTokenCreate.Handler(func(info rpc.RequestInfo, request orcapi.ApiTokenSpecification) (orcapi.ApiToken, *util.HttpError) {
		return ApiTokenCreate(info.Actor, request)
	})

	orcapi.ApiTokenBrowse.Handler(func(info rpc.RequestInfo, request orcapi.ApiTokenBrowseRequest) (fndapi.PageV2[orcapi.ApiToken], *util.HttpError) {
		return ApiTokenBrowse(info.Actor, request)
	})

	orcapi.ApiTokenRetrieveOptions.Handler(func(info rpc.RequestInfo, request util.Empty) (orcapi.ApiTokenRetrieveOptionsResponse, *util.HttpError) {
		return ApiTokenRetrieveOptions(info.Actor), nil
	})

	orcapi.ApiTokenRevoke.Handler(func(info rpc.RequestInfo, request fndapi.FindByStringId) (util.Empty, *util.HttpError) {
		return util.Empty{}, ApiTokenRevoke(info.Actor, ResourceParseId(request.Id))
	})

	orcapi.ApiTokenUpdateLabels.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ApiTokenUpdateLabelsRequest]) (util.Empty, *util.HttpError) {
		for _, reqItem := range request.Items {
			err := ResourceUpdateLabels(info.Actor, apiTokenType, reqItem.Id, reqItem.Labels, orcapi.PermissionEdit)
			if err != nil {
				return util.Empty{}, err
			}
		}

		return util.Empty{}, nil
	})

	go func() {
		for {
			db.NewTx0(func(tx *db.Transaction) {
				db.Exec(
					tx,
					`delete from provider.api_tokens where now() > expires_at`,
					db.Params{},
				)
			})
			time.Sleep(10 * time.Minute)
		}
	}()
}

// =====================================================================================================================

func ApiTokenCreate(actor rpc.Actor, request orcapi.ApiTokenSpecification) (orcapi.ApiToken, *util.HttpError) {
	var err *util.HttpError
	util.ValidateString(&request.Title, "title", 0, &err)
	util.ValidateString(&request.Description, "description", util.StringValidationAllowEmpty, &err)
	util.ValidateStringIfPresent(&request.Provider, "provider", 0, &err)

	if err == nil && request.ExpiresAt.Time().Before(time.Now()) {
		err = util.HttpErr(http.StatusBadRequest, "requested token has already expired")
	}

	if err != nil {
		return orcapi.ApiToken{}, err
	}

	optsAvailable := ApiTokenRetrieveOptions(actor)
	optsByProvider, ok := optsAvailable.ByProvider[request.Provider.GetOrDefault("")]
	if ok {
		permsByName := map[string]orcapi.ApiTokenPermissionSpecification{}
		for _, opt := range optsByProvider.AvailablePermissions {
			permsByName[opt.Name] = opt
		}

		for _, reqPerm := range request.RequestedPermissions {
			permSpec, hasName := permsByName[reqPerm.Name]
			if !hasName {
				err = util.HttpErr(http.StatusBadRequest, "invalid token requested, %s is not available", reqPerm.Name)
				break
			} else {
				_, hasAction := permSpec.Actions[reqPerm.Action]
				if !hasAction {
					err = util.HttpErr(http.StatusBadRequest, "invalid token requested, %s/%s is not available", reqPerm.Name, reqPerm.Action)
					break
				}
			}
		}
	} else {
		err = util.HttpErr(http.StatusBadRequest, "invalid token requested, these permissions are not available")
	}

	if err != nil {
		return orcapi.ApiToken{}, err
	}

	itok := &internalApiToken{
		Provider:    request.Provider,
		Title:       request.Title,
		Description: request.Description,
		Permissions: request.RequestedPermissions,
		ExpiresAt:   request.ExpiresAt.Time(),
	}

	var tokId ResourceId
	var tok orcapi.ApiToken
	var userToken string

	if request.Provider.Present {
		tokId, tok, err = ResourceCreate[orcapi.ApiToken](actor, apiTokenType, orcapi.ResourceSpecification{}, itok)
		if err != nil {
			return orcapi.ApiToken{}, err
		}

		status, err := InvokeProvider(request.Provider.Value, orcapi.ApiTokenProviderCreate, tok, ProviderCallOpts{
			Username: util.OptValue(actor.Username),
		})

		if err != nil {
			ResourceDelete(actor, apiTokenType, tokId)
			return orcapi.ApiToken{}, err
		}

		tok.Status = status
		ResourceConfirm(apiTokenType, tokId)

		ResourceUpdate(rpc.ActorSystem, apiTokenType, tokId, orcapi.PermissionEdit, func(r *resource, mapped orcapi.ApiToken) {
			itok := r.Extra.(*internalApiToken)
			itok.Server = status.Server
		})

		if status.Server == "" {
			log.Warn("Provider returned empty token server: provider=%v tokenId=%v", request.Provider.Value, tokId)
		}

	} else {
		userToken = util.SecureToken()
		hashedToken := util.HashPassword(userToken, util.GenSalt())

		itok.TokenHash = hashedToken.HashedPassword
		itok.TokenSalt = hashedToken.Salt

		tokId, tok, err = ResourceCreate[orcapi.ApiToken](actor, apiTokenType, orcapi.ResourceSpecification{}, itok)
		if err != nil {
			return orcapi.ApiToken{}, err
		}

		ResourceConfirm(apiTokenType, tokId)

		userTokenToUse := fmt.Sprintf("uc%x-%s", int(tokId), userToken)
		tok.Status.Token.Set(userTokenToUse)
	}

	return tok, nil
}

func ApiTokenBrowse(actor rpc.Actor, request orcapi.ApiTokenBrowseRequest) (fndapi.PageV2[orcapi.ApiToken], *util.HttpError) {
	return ResourceBrowse[orcapi.ApiToken](
		actor,
		apiTokenType,
		request.Next,
		request.ItemsPerPage,
		orcapi.ResourceFlags{},
		func(item orcapi.ApiToken) bool {
			return true
		},
		func(a orcapi.ApiToken, b orcapi.ApiToken) int {
			return strings.Compare(a.Specification.Title, b.Specification.Title)
		},
	), nil
}

var ApiTokensOptionsCache = util.NewCache[string, orcapi.ApiTokenOptions](5 * time.Minute)

func ApiTokenRetrieveOptions(actor rpc.Actor) orcapi.ApiTokenRetrieveOptionsResponse {
	providers, err := accapi.FindRelevantProviders.Invoke(fndapi.BulkRequestOf(accapi.FindRelevantProvidersRequest{
		Username:         actor.Username,
		IncludeFreeToUse: util.OptValue(false),
		UseProject:       false,
	}))

	optionsByProvider := map[string]orcapi.ApiTokenOptions{}
	if err == nil {
		for _, providerId := range providers.Responses[0].Providers {
			if providerId == "" {
				continue
			}

			options, ok := ApiTokensOptionsCache.Get(providerId, func() (orcapi.ApiTokenOptions, error) {
				resp, err := InvokeProvider(
					providerId,
					orcapi.ApiTokenProviderRetrieveOptions,
					util.Empty{},
					ProviderCallOpts{
						Username: util.OptValue(actor.Username),
						Reason:   util.OptValue("Retrieving API token options"),
					},
				)
				if err != nil {
					return orcapi.ApiTokenOptions{}, err.AsError()
				} else {
					return resp, nil
				}
			})

			if !ok {
				log.Warn("No API Token options found for provider %s", providerId)
				continue
			}

			options.AvailablePermissions = util.NonNilSlice(options.AvailablePermissions)
			optionsByProvider[providerId] = options
		}
	}

	optionsByProvider[""] = orcapi.ApiTokenOptions{}

	return orcapi.ApiTokenRetrieveOptionsResponse{ByProvider: optionsByProvider}
}

func ApiTokenRevoke(actor rpc.Actor, id ResourceId) *util.HttpError {
	tok, _, _, err := ResourceRetrieveEx[orcapi.ApiToken](
		actor,
		apiTokenType,
		id,
		orcapi.PermissionEdit,
		orcapi.ResourceFlags{},
	)
	if err != nil {
		return err
	}

	if tok.Specification.Provider.Present {
		provider := tok.Specification.Provider.Value
		log.Info("Revoking API token through provider: provider=%s tokenId=%s user=%s", provider, tok.Id, actor.Username)

		_, err := InvokeProvider(
			provider,
			orcapi.ApiTokenProviderRevoke,
			fndapi.FindByStringId{Id: tok.Id},
			ProviderCallOpts{
				Username: util.OptValue(actor.Username),
				Reason:   util.OptValue("Deleting resource: " + apiTokenType),
			},
		)
		if err != nil {
			log.Warn("Provider API token revoke failed: provider=%s tokenId=%s user=%s err=%v", provider, tok.Id, actor.Username, err)
			return err
		}
	}

	ok := ResourceDelete(actor, apiTokenType, id)
	if !ok {
		return util.HttpErr(http.StatusNotFound, "permission denied or unknown token specified")
	}
	return nil
}

type internalApiToken struct {
	Provider    util.Option[string]
	Title       string
	Description string
	Permissions []orcapi.ApiTokenPermission
	ExpiresAt   time.Time
	TokenHash   []byte
	TokenSalt   []byte
	Server      string
}

// =====================================================================================================================

func apiTokensLoad(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource) {
	rows := db.Select[struct {
		Resource    int
		Title       string
		Description string
		Provider    sql.Null[string]
		Server      sql.Null[string]
		Permissions string
		TokenHash   sql.Null[[]byte]
		TokenSalt   sql.Null[[]byte]
		ExpiresAt   time.Time
	}](
		tx,
		`
			select resource, title, description, provider, server, permissions, token_hash, token_salt, expires_at
			from provider.api_tokens
			where resource = some(:ids::int8[])
	    `,
		db.Params{
			"ids": ids,
		},
	)

	for _, row := range rows {
		var permissions []orcapi.ApiTokenPermission
		_ = json.Unmarshal([]byte(row.Permissions), &permissions)

		result := &internalApiToken{
			Provider:    util.SqlNullToOpt(row.Provider),
			Title:       row.Title,
			Description: row.Description,
			Server:      util.SqlNullToOpt(row.Server).GetOrDefault(""),
			Permissions: permissions,
			ExpiresAt:   row.ExpiresAt,
			TokenHash:   util.SqlNullToOpt(row.TokenHash).GetOrDefault(nil),
			TokenSalt:   util.SqlNullToOpt(row.TokenSalt).GetOrDefault(nil),
		}

		resources[ResourceId(row.Resource)].Extra = result
	}
}

func apiTokensTransform(
	r orcapi.Resource,
	specification orcapi.ResourceSpecification,
	extra any,
	flags orcapi.ResourceFlags,
	actor rpc.Actor,
) any {
	tok := extra.(*internalApiToken)

	server := ""
	if !tok.Provider.Present {
		server = cfg.Configuration.SelfPublic.ToURL()
	} else {
		server = tok.Server
	}

	return orcapi.ApiToken{
		Resource: r,
		Specification: orcapi.ApiTokenSpecification{
			Title:                tok.Title,
			Description:          tok.Description,
			Provider:             tok.Provider,
			RequestedPermissions: tok.Permissions,
			ExpiresAt:            fndapi.Timestamp(tok.ExpiresAt),
		},
		Status: orcapi.ApiTokenStatus{
			Server: server,
		},
	}
}

func apiTokensPersist(b *db.Batch, r *resource) {
	if r.MarkedForDeletion {
		db.BatchExec(
			b,
			`delete from provider.api_tokens where resource = :id`,
			db.Params{
				"id": r.Id,
			},
		)
	} else {
		tok := r.Extra.(*internalApiToken)
		tokHash := util.Option[[]byte]{}
		tokSalt := util.Option[[]byte]{}

		if tok.TokenHash != nil && tok.TokenSalt != nil {
			tokHash.Set(tok.TokenHash)
			tokSalt.Set(tok.TokenSalt)
		}

		permissions, _ := json.Marshal(tok.Permissions)

		db.BatchExec(
			b,
			`
				insert into provider.api_tokens(resource, title, description, provider, permissions, token_hash, token_salt, expires_at, server) 
				values (:resource, :title, :description, :provider, :permissions, :token_hash, :token_salt, :expires_at, :server)
				on conflict (resource) do update set 
					title = excluded.title,
					description = excluded.description,
					provider = excluded.provider,
					permissions = excluded.permissions,
					token_hash = excluded.token_hash,
					token_salt = excluded.token_salt,
					expires_at = excluded.expires_at,
					server = excluded.server
		    `,
			db.Params{
				"resource":    r.Id,
				"title":       tok.Title,
				"description": tok.Description,
				"provider":    tok.Provider.Sql(),
				"permissions": string(permissions),
				"token_hash":  tokHash.Sql(),
				"token_salt":  tokSalt.Sql(),
				"expires_at":  tok.ExpiresAt,
				"server":      tok.Server,
			},
		)
	}
}
