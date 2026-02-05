package foundation

import (
	"fmt"
	"net/http"
	"sync"
	"time"

	"github.com/coreos/go-oidc/v3/oidc"
	"golang.org/x/oauth2"
	cfg "ucloud.dk/core/pkg/config"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"

	"context"
)

var oidcGlobals struct {
	Provider *oidc.Provider
	Config   oauth2.Config
	Verifier *oidc.IDTokenVerifier

	Mu       sync.Mutex
	Sessions map[string]oidcAuthSession
}

type oidcAuthSession struct {
	State string
	Nonce string
}

func initAuthOidc() {
	configOpt := cfg.Configuration.OpenIdConnect

	if configOpt.Present {
		config := configOpt.Value

		g := &oidcGlobals
		g.Sessions = map[string]oidcAuthSession{}
		selfUrl := cfg.Configuration.SelfPublic.ToURL()
		oidcProvider, err := oidc.NewProvider(context.Background(), config.Issuer)
		if err != nil {
			panic("Failed to connect to OIDC provider: " + err.Error())
		}

		g.Provider = oidcProvider

		g.Config = oauth2.Config{
			ClientID:     config.ClientId,
			ClientSecret: config.ClientSecret,
			Endpoint:     g.Provider.Endpoint(),
			RedirectURL:  selfUrl + "/auth/oidc",
			Scopes:       append([]string{oidc.ScopeOpenID, "profile", "email"}, config.Scopes...),
		}

		g.Verifier = g.Provider.Verifier(&oidc.Config{ClientID: config.ClientId})

		// -------------------------------------------------------------------------------------------------------------

		fndapi.AuthBrowseIdentityProviders.Handler(func(info rpc.RequestInfo, request util.Empty) (fndapi.BulkResponse[fndapi.IdentityProvider], *util.HttpError) {
			return fndapi.BulkResponse[fndapi.IdentityProvider]{
				Responses: []fndapi.IdentityProvider{
					{Id: 1, Title: config.IdpTitle},
				},
			}, nil
		})

		startLogin := func(info rpc.RequestInfo) (util.Empty, *util.HttpError) {
			stateToken := util.RandomTokenNoTs(16)
			nonce := util.RandomTokenNoTs(16)

			g.Mu.Lock()
			g.Sessions[stateToken] = oidcAuthSession{
				State: stateToken,
				Nonce: nonce,
			}
			g.Mu.Unlock()

			redirectTo := g.Config.AuthCodeURL(stateToken, oidc.Nonce(nonce))
			http.Redirect(info.HttpWriter, info.HttpRequest, redirectTo, http.StatusFound)
			return util.Empty{}, nil
		}

		fndapi.AuthStartLoginSamlLegacy.Handler(func(info rpc.RequestInfo, request util.Empty) (util.Empty, *util.HttpError) {
			return startLogin(info)
		})

		fndapi.AuthStartLogin.Handler(func(info rpc.RequestInfo, request fndapi.FindByIntId) (util.Empty, *util.HttpError) {
			return startLogin(info)
		})

		fndapi.AuthOidcCallback.Handler(func(info rpc.RequestInfo, request fndapi.AuthOidcCallbackRequest) (util.Empty, *util.HttpError) {
			g.Mu.Lock()
			session, ok := g.Sessions[request.State]
			delete(g.Sessions, request.State)
			g.Mu.Unlock()

			if !ok {
				return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Failed to authenticate you")
			}

			timeout, cancel := context.WithTimeout(context.Background(), 10*time.Second)
			defer cancel()

			oauthToken, err := g.Config.Exchange(timeout, request.Code)
			if err != nil {
				log.Warn("Failed to exchange token: %v", err)
				return util.Empty{}, util.HttpErr(http.StatusBadGateway, "Failed to authenticate you")
			}

			userInfo, err := g.Provider.UserInfo(timeout, oauth2.StaticTokenSource(oauthToken))
			if err != nil {
				log.Warn("Failed to retrieve userinfo: %v", err)
				return util.Empty{}, util.HttpErr(http.StatusBadGateway, "Failed to authenticate you")
			}

			_ = userInfo // NOTE(Dan): Unused at the moment.

			rawIdToken, ok := oauthToken.Extra("id_token").(string)
			if !ok {
				log.Warn("id_token is not present in oauth token but it must be")
				return util.Empty{}, util.HttpErr(http.StatusBadGateway, "Failed to authenticate you")
			}

			idToken, err := g.Verifier.Verify(timeout, rawIdToken)
			if err != nil {
				log.Warn("Failed to verify id_token: %v", err)
				return util.Empty{}, util.HttpErr(http.StatusBadGateway, "Failed to authenticate you")
			}

			if session.Nonce != idToken.Nonce {
				return util.Empty{}, util.HttpErr(http.StatusBadGateway, "Failed to authenticate you")
			}

			var claimJson struct {
				Email             string `json:"email"`
				PreferredUsername string `json:"preferred_username"`
				FirstNames        string `json:"gn"`
				LastName          string `json:"sn"`
				HomeOrganization  string `json:"homeOrganization"`
				GivenName         string `json:"given_name"`
				FamilyName        string `json:"family_name"`
				Oid               string `json:"oid"`
				Tid               string `json:"tid"`
			}
			err = idToken.Claims(&claimJson)
			if err != nil {
				log.Warn("Failed to retrieve claims: %v", err)
				return util.Empty{}, util.HttpErr(http.StatusBadGateway, "Failed to authenticate you")
			}

			response := IdpResponse{
				Idp:        1,
				Identity:   claimJson.PreferredUsername,
				FirstNames: util.OptValue(claimJson.FirstNames),
				LastName:   util.OptValue(claimJson.LastName),
				OrgId:      util.OptStringIfNotEmpty(claimJson.HomeOrganization),
				Email:      util.OptStringIfNotEmpty(claimJson.Email),
			}

			// TODO Clean this up
			switch config.Profile {
			case "Entra":
				// NOTE(Dan): For this to work, the client needs to be configured with optional claims (in Entra's UI)
				// which allow these to be returned on the ID token (given_name, family_name and email).
				response.Identity = fmt.Sprintf("%v-%v", claimJson.Tid, claimJson.Oid)
				response.FirstNames = util.OptValue(claimJson.GivenName)
				response.LastName = util.OptValue(claimJson.FamilyName)

			case "WAYF": // Nothing to do
			}

			principal, httpErr := PrincipalRetrieveOrCreateFromIdpResponse(response)

			if httpErr != nil {
				log.Warn("Failed to create principal from idp response: %v", httpErr)
				return util.Empty{}, util.HttpErr(http.StatusBadGateway, "Failed to authenticate you")
			}

			toks := db.NewTx(func(tx *db.Transaction) fndapi.AuthenticationTokens {
				return SessionCreate(info.HttpRequest, tx, principal)
			})

			SessionLoginResponse(info.HttpRequest, info.HttpWriter, toks, 0)
			return util.Empty{}, nil
		})
	} else {
		fndapi.AuthBrowseIdentityProviders.Handler(func(info rpc.RequestInfo, request util.Empty) (fndapi.BulkResponse[fndapi.IdentityProvider], *util.HttpError) {
			return fndapi.BulkResponse[fndapi.IdentityProvider]{}, nil
		})

		fndapi.AuthStartLoginSamlLegacy.Handler(func(info rpc.RequestInfo, request util.Empty) (util.Empty, *util.HttpError) {
			return util.Empty{}, util.HttpErr(http.StatusNotFound, "not configured")
		})
	}
}
