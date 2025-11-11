package foundation

import (
	"net/http"
	"sync"
	"time"

	"github.com/coreos/go-oidc/v3/oidc"
	"github.com/golang-jwt/jwt/v5"
	"golang.org/x/oauth2"
	cfg "ucloud.dk/core/pkg/config"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"

	"context"
	"fmt"
)

var oidcGlobals struct {
	Provider             *oidc.Provider
	Config               oauth2.Config
	Verifier             *oidc.IDTokenVerifier
	ClientSecretVerifier *jwt.Parser
	ClientSecretKeyFunc  jwt.Keyfunc

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
			RedirectURL:  selfUrl + fmt.Sprintf("/auth/oidc"),
			Scopes:       append([]string{oidc.ScopeOpenID, "profile", "email"}, config.Scopes...),
		}

		g.Verifier = g.Provider.Verifier(&oidc.Config{ClientID: config.ClientId})

		{
			// NOTE(Dan): Some OIDC providers will sign the ID token with the client secret. This is not supported by
			// the library, so we work around this by falling back to our normal JWT parser in that case.

			var jwtKeyFunc jwt.Keyfunc
			var jwtMethods []string

			jwtMethods = []string{jwt.SigningMethodHS256.Name, jwt.SigningMethodHS384.Name, jwt.SigningMethodHS512.Name}
			jwtKeyFunc = func(token *jwt.Token) (interface{}, error) {
				return []byte(config.ClientSecret), nil
			}

			g.ClientSecretVerifier = jwt.NewParser(
				jwt.WithIssuer(config.Issuer),
				jwt.WithValidMethods(jwtMethods),
				jwt.WithIssuedAt(),
				jwt.WithExpirationRequired(),
			)
			g.ClientSecretKeyFunc = jwtKeyFunc
		}

		// -------------------------------------------------------------------------------------------------------------

		fndapi.AuthBrowseIdentityProviders.Handler(func(info rpc.RequestInfo, request util.Empty) (fndapi.BulkResponse[fndapi.IdentityProvider], *util.HttpError) {
			return fndapi.BulkResponse[fndapi.IdentityProvider]{
				Responses: []fndapi.IdentityProvider{
					{Id: 1, Title: "WAYF"}, // TODO Configurable name?
				},
			}, nil
		})

		fndapi.AuthStartLogin.Handler(func(info rpc.RequestInfo, request fndapi.FindByIntId) (util.Empty, *util.HttpError) {
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

			_ = userInfo // TODO?

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
			}
			err = idToken.Claims(&claimJson)
			if err != nil {
				log.Warn("Failed to retrieve claims: %v", err)
				return util.Empty{}, util.HttpErr(http.StatusBadGateway, "Failed to authenticate you")
			}

			principal, httpErr := PrincipalRetrieveOrCreateFromIdpResponse(IdpResponse{
				Idp:        1,
				Identity:   claimJson.PreferredUsername,
				FirstNames: util.OptValue(claimJson.FirstNames),
				LastName:   util.OptValue(claimJson.LastName),
				OrgId:      util.OptStringIfNotEmpty(claimJson.HomeOrganization),
				Email:      util.OptStringIfNotEmpty(claimJson.Email),
			})

			if httpErr != nil {
				log.Warn("Failed to create principal from idp response: %v", err)
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
	}
}
