package foundation

import (
	"net/http"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initAuth() {
	initAuthTokens()

	// TODO origin check for some of these calls
	// TODO origin check for some of these calls
	// TODO origin check for some of these calls

	// TODO Assert that user ID never match a UUID. Breaks too many things which assume that user IDs never collide
	//   with project IDs.

	fndapi.AuthLookupUser.Handler(func(info rpc.RequestInfo, request fndapi.FindByStringId) (rpc.CorePrincipalBaseClaims, *util.HttpError) {
		principal, ok := db.NewTx2(func(tx *db.Transaction) (Principal, bool) {
			return LookupPrincipal(tx, request.Id)
		})

		if ok {
			return CreatePrincipalClaims(principal, util.OptNone[string]()), nil
		} else {
			return rpc.CorePrincipalBaseClaims{}, util.HttpErr(http.StatusNotFound, "not found")
		}
	})

	fndapi.AuthRefresh.Handler(func(info rpc.RequestInfo, request fndapi.AuthenticationTokens) (fndapi.AccessTokenAndCsrf, *util.HttpError) {
		return SessionRefresh(request)
	})

	fndapi.AuthRefreshWeb.Handler(func(info rpc.RequestInfo, request fndapi.AuthenticationTokens) (fndapi.AccessTokenAndCsrf, *util.HttpError) {
		return SessionRefresh(request)
	})

	fndapi.AuthLogout.Handler(func(info rpc.RequestInfo, request fndapi.AuthenticationTokens) (util.Empty, *util.HttpError) {
		err := SessionLogout(request)
		return util.Empty{}, err
	})

	fndapi.AuthLogoutWeb.Handler(func(info rpc.RequestInfo, request fndapi.AuthenticationTokens) (util.Empty, *util.HttpError) {
		err := SessionLogout(request)

		if err == nil {
			w := info.HttpWriter
			http.SetCookie(w, &http.Cookie{
				Name:   "refreshToken",
				Path:   "/",
				MaxAge: -1,
			})
		}

		return util.Empty{}, err
	})

	fndapi.AuthPasswordLoginWeb.Handler(func(info rpc.RequestInfo, request fndapi.PasswordLoginRequest) (util.Empty, *util.HttpError) {
		tokens, err := PasswordLogin(info.HttpRequest, request.Username, request.Password)
		if err != nil {
			return util.Empty{}, err
		} else {
			SessionLoginResponse(info.HttpRequest, info.HttpWriter, tokens, 0)
			return util.Empty{}, nil
		}
	})

	fndapi.AuthListSessions.Handler(func(info rpc.RequestInfo, request util.Empty) (fndapi.Page[fndapi.AuthSessionInfo], *util.HttpError) {
		return SessionListInfo(info.Actor), nil
	})

	fndapi.AuthInvalidateSessions.Handler(func(info rpc.RequestInfo, request util.Empty) (util.Empty, *util.HttpError) {
		SessionInvalidateAll(info.Actor)
		http.SetCookie(info.HttpWriter, &http.Cookie{
			Name:   "refreshToken",
			Value:  "",
			MaxAge: -1,
			Path:   "/",
		})

		return util.Empty{}, nil
	})

	fndapi.AuthBrowseIdentityProviders.Handler(func(info rpc.RequestInfo, request util.Empty) (fndapi.BulkResponse[fndapi.IdentityProvider], *util.HttpError) {
		// TODO implement this
		return fndapi.BulkResponse[fndapi.IdentityProvider]{}, nil
	})

	fndapi.AuthStartLogin.Handler(func(info rpc.RequestInfo, request fndapi.FindByIntId) (util.Empty, *util.HttpError) {
		// TODO Implement this
		return util.Empty{}, util.HttpErr(http.StatusNotFound, "No such identity provider")
	})

	fndapi.AuthProvidersRenew.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[fndapi.PublicKeyAndRefreshToken], *util.HttpError) {
		var result fndapi.BulkResponse[fndapi.PublicKeyAndRefreshToken]
		for _, item := range request.Items {
			keys, err := ProviderRenew(item.Id)
			if err != nil {
				return result, err
			}
			result.Responses = append(result.Responses, keys)
		}

		return result, nil
	})

	fndapi.AuthProvidersRefresh.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.RefreshToken]) (fndapi.BulkResponse[fndapi.AccessTokenAndCsrf], *util.HttpError) {
		var result fndapi.BulkResponse[fndapi.AccessTokenAndCsrf]
		for _, item := range request.Items {
			keys, err := ProviderRefresh(item.RefreshToken)
			if err != nil {
				return result, err
			}
			result.Responses = append(result.Responses, keys)
		}

		return result, nil
	})

	fndapi.AuthProvidersRefreshAsOrchestrator.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByProviderId]) (fndapi.BulkResponse[fndapi.AccessTokenAndCsrf], *util.HttpError) {
		var result fndapi.BulkResponse[fndapi.AccessTokenAndCsrf]
		for _, item := range request.Items {
			keys, err := ProviderRefreshAsOrchestrator(item.ProviderId)
			if err != nil {
				return result, err
			}
			result.Responses = append(result.Responses, keys)
		}

		return result, nil
	})

	fndapi.AuthProvidersGenerateKeyPair.Handler(func(info rpc.RequestInfo, request util.Empty) (fndapi.PublicAndPrivateKey, *util.HttpError) {
		return ProviderGenerateKeys()
	})

	fndapi.SlaFind.Handler(func(info rpc.RequestInfo, request util.Empty) (fndapi.ServiceAgreementText, *util.HttpError) {
		return SlaRetrieveText(), nil
	})

	fndapi.SlaAccept.Handler(func(info rpc.RequestInfo, request fndapi.SlaAcceptRequest) (util.Empty, *util.HttpError) {
		SlaAccept(info.Actor, request.Version)
		return util.Empty{}, nil
	})

	fndapi.AuthMfaAnswerChallenge.Handler(func(info rpc.RequestInfo, request fndapi.MfaChallengeAnswer) (util.Empty, *util.HttpError) {
		err := MfaAnswerChallenge(info.HttpRequest, info.HttpWriter, request.ChallengeId, request.VerificationCode)
		return util.Empty{}, err
	})

	fndapi.AuthMfaCreateCredentials.Handler(func(info rpc.RequestInfo, request util.Empty) (fndapi.MfaCredentials, *util.HttpError) {
		return MfaCreateCredentials(info.Actor)
	})

	fndapi.AuthMfaStatus.Handler(func(info rpc.RequestInfo, request util.Empty) (fndapi.MfaStatus, *util.HttpError) {
		connected := MfaIsConnected(info.Actor)
		return fndapi.MfaStatus{Connected: connected}, nil
	})
}
