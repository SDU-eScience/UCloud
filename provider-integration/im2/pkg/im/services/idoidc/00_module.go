package idoidc

import (
	"context"
	"encoding/json"
	"fmt"
	"github.com/coreos/go-oidc/v3/oidc"
	"golang.org/x/oauth2"
	"net/http"
	"sync"
	"time"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

var idp *oidc.Provider
var idpConfig oauth2.Config
var oidcVerifier *oidc.IDTokenVerifier

type authSession struct {
	UCloudUsername string
	State          string
	Nonce          string
}

var oidcAuthSessionsMutex = sync.Mutex{}
var oidcAuthSessions = map[string]authSession{}

type userAuthenticatedRequest struct {
	Claims        json.RawMessage `json:"claims"`
	EmailVerified bool            `json:"emailVerified"`
	Email         string          `json:"email"`
	Subject       string          `json:"subject"`
	Profile       string          `json:"profile"`
}

type userAuthenticatedResponse struct {
	Uid uint32 `json:"uid"`
}

var scriptUserAuthenticated = ctrl.Script[userAuthenticatedRequest, userAuthenticatedResponse]{}

func Init(config *cfg.IdentityManagementOidc, mux *http.ServeMux) {
	if !cfg.Services.Unmanaged {
		panic("OIDC is only supported for unmanaged providers")
	}

	oidcIdp, err := oidc.NewProvider(context.Background(), config.Issuer)
	if err != nil {
		panic("Failed to connect to OIDC provider: " + err.Error())
	}

	scriptUserAuthenticated.Script = config.OnUserConnected

	idp = oidcIdp
	idpConfig = oauth2.Config{
		ClientID:     config.ClientId,
		ClientSecret: config.ClientSecret,
		Endpoint:     idp.Endpoint(),
		RedirectURL:  cfg.Provider.Hosts.SelfPublic.ToURL() + fmt.Sprintf("/ucloud-oidc"),
		Scopes:       append([]string{oidc.ScopeOpenID}, config.Scopes...),
	}

	oidcVerifier = idp.Verifier(&oidc.Config{ClientID: config.ClientId})

	ctrl.IdentityManagement.UnmanagedConnections = true
	ctrl.IdentityManagement.ExpiresAfter.Set(1000 * 60 * 60 * 24 * 7)
	ctrl.IdentityManagement.InitiateConnection = func(username string) (string, error) {
		stateToken := util.RandomToken(16)
		nonce := util.RandomToken(16)

		oidcAuthSessionsMutex.Lock()
		oidcAuthSessions[stateToken] = authSession{
			UCloudUsername: username,
			State:          stateToken,
			Nonce:          nonce,
		}
		oidcAuthSessionsMutex.Unlock()

		return idpConfig.AuthCodeURL(stateToken, oidc.Nonce(nonce)), nil
	}

	ctrl.IdentityManagement.HandleProjectNotification = func(updated *ctrl.NotificationProjectUpdated) bool {
		// Do nothing
		return true
	}

	mux.HandleFunc("/ucloud-oidc", func(w http.ResponseWriter, r *http.Request) {
		code := r.URL.Query().Get("code")
		state := r.URL.Query().Get("state")

		oidcAuthSessionsMutex.Lock()
		session, ok := oidcAuthSessions[state]
		delete(oidcAuthSessions, state)
		oidcAuthSessionsMutex.Unlock()

		if !ok {
			http.Error(w, "Failed to authenticate you", http.StatusBadRequest)
			return
		}

		timeout, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()

		oauthToken, err := idpConfig.Exchange(timeout, code)
		if err != nil {
			log.Warn("Failed to exchange token: %v", err)
			http.Error(w, "Failed to authenticate you", http.StatusBadGateway)
			return
		}

		userInfo, err := idp.UserInfo(timeout, oauth2.StaticTokenSource(oauthToken))
		if err != nil {
			log.Warn("Failed to retrieve userinfo: %v", err)
			http.Error(w, "Failed to authenticate you", http.StatusBadGateway)
			return
		}

		rawIdToken, ok := oauthToken.Extra("id_token").(string)
		if !ok {
			log.Warn("id_token is not present in oauth token but it must be")
			http.Error(w, "Failed to authenticate you", http.StatusBadGateway)
			return
		}

		idToken, err := oidcVerifier.Verify(timeout, rawIdToken)
		if err != nil {
			log.Warn("Failed to verify id_token: %v", err)
			http.Error(w, "Failed to authenticate you", http.StatusBadGateway)
			return
		}

		if session.Nonce != idToken.Nonce {
			http.Error(w, "Failed to authenticate you", http.StatusBadRequest)
			return
		}

		var claimJson json.RawMessage
		err = idToken.Claims(&claimJson)
		if err != nil {
			log.Warn("Failed to retrieve claims: %v", err)
			http.Error(w, "Failed to authenticate you", http.StatusBadGateway)
			return
		}

		request := userAuthenticatedRequest{
			Claims:        claimJson,
			EmailVerified: userInfo.EmailVerified,
			Email:         userInfo.Email,
			Subject:       userInfo.Subject,
			Profile:       userInfo.Profile,
		}

		response, ok := scriptUserAuthenticated.Invoke(request)
		if !ok {
			log.Warn("Failed to run user authenticated script")
			http.Error(w, "Failed to authenticate you", http.StatusInternalServerError)
			return
		}

		err = ctrl.RegisterConnectionComplete(session.UCloudUsername, response.Uid, true)
		if err != nil {
			log.Warn("Failed to register connection complete: %v", err)
			http.Error(w, "Failed to authenticate you", http.StatusBadGateway)
			return
		}

		_, err = ctrl.CreatePersonalProviderProject(session.UCloudUsername)
		if err != nil {
			log.Warn("Failed to register connection complete: %v", err)
			http.Error(w, "Failed to authenticate you", http.StatusBadGateway)
			return
		}

		http.Redirect(w, r, cfg.Provider.Hosts.UCloudPublic.ToURL(), http.StatusFound)
	})
}
