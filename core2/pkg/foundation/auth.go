package foundation

import (
	"net/http"
	"net/url"

	cfg "ucloud.dk/core/pkg/config"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func authVerifyOrigin(info rpc.RequestInfo) *util.HttpError {
	origin := info.HttpRequest.Header.Get("Origin")
	referer := info.HttpRequest.Header.Get("Referer")

	hostName := ""

	if origin != "" {
		parsed, err := url.Parse(origin)
		if err == nil {
			hostName = parsed.Host
		}
	} else if referer != "" {
		parsed, err := url.Parse(referer)
		if err == nil {
			hostName = parsed.Host
		}
	}

	if hostName == "" {
		return util.HttpErr(http.StatusForbidden, "untrusted origin")
	}

	if hostName == "localhost:9000" {
		return nil
	} else if hostName == cfg.Configuration.SelfPublic.Address {
		return nil
	} else {
		return util.HttpErr(http.StatusForbidden, "untrusted origin")
	}
}

func initAuth() {
	initAuthTokens()

	// TODO Assert that user ID never match a UUID. Breaks too many things which assume that user IDs never collide
	//   with project IDs.

	fndapi.AuthLookupUser.Handler(func(info rpc.RequestInfo, request fndapi.FindByStringId) (rpc.CorePrincipalBaseClaims, *util.HttpError) {
		principal, ok := db.NewTx2(func(tx *db.Transaction) (Principal, bool) {
			return PrincipalRetrieve(tx, request.Id)
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
		if err := authVerifyOrigin(info); err != nil {
			return fndapi.AccessTokenAndCsrf{}, err
		}
		return SessionRefresh(request)
	})

	fndapi.AuthLogout.Handler(func(info rpc.RequestInfo, request fndapi.AuthenticationTokens) (util.Empty, *util.HttpError) {
		err := SessionLogout(request)
		return util.Empty{}, err
	})

	fndapi.AuthLogoutWeb.Handler(func(info rpc.RequestInfo, request fndapi.AuthenticationTokens) (util.Empty, *util.HttpError) {
		if err := authVerifyOrigin(info); err != nil {
			return util.Empty{}, err
		}
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

	fndapi.AuthPasswordLoginServer.Handler(func(info rpc.RequestInfo, request fndapi.PasswordLoginRequest) (fndapi.AuthenticationTokens, *util.HttpError) {
		tokens, err := PasswordLogin(info.HttpRequest, request.Username, request.Password)
		if err != nil {
			return fndapi.AuthenticationTokens{}, err
		} else {
			mfaRequired := false
			_, mfaRequired = MfaCreateChallenge(request.Username)
			mfaRequired = mfaRequired && MfaIsConnectedEx(request.Username)

			if mfaRequired {
				return fndapi.AuthenticationTokens{}, util.HttpErr(http.StatusBadRequest, "not supported")
			}
			return tokens, nil
		}
	})

	fndapi.AuthPasswordLoginWeb.Handler(func(info rpc.RequestInfo, request fndapi.PasswordLoginRequest) (util.Empty, *util.HttpError) {
		if err := authVerifyOrigin(info); err != nil {
			return util.Empty{}, err
		}
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

	fndapi.UsersCreate.Handler(func(info rpc.RequestInfo, request []fndapi.UsersCreateRequest) ([]fndapi.UsersCreateResponse, *util.HttpError) {
		var result []fndapi.UsersCreateResponse
		for _, item := range request {
			if len(item.Username) > 250 {
				result = append(result, fndapi.UsersCreateResponse{Username: item.Username, Created: false, Error: "Username too long"})
				continue
			}

			if len(item.Username) == 0 {
				result = append(result, fndapi.UsersCreateResponse{Username: item.Username, Created: false, Error: "Username is required"})
				continue
			}

			if len(item.Password) > 250 {
				result = append(result, fndapi.UsersCreateResponse{Username: item.Username, Created: false, Error: "Password too long"})
				continue
			}

			if len(item.Password) < 8 {
				result = append(result, fndapi.UsersCreateResponse{Username: item.Username, Created: false, Error: "Password must be at least 8 characters"})
				continue
			}

			if len(item.Email) > 250 {
				result = append(result, fndapi.UsersCreateResponse{Username: item.Username, Created: false, Error: "Email too long"})
				continue
			}

			if len(item.Email) == 0 {
				result = append(result, fndapi.UsersCreateResponse{Username: item.Username, Created: false, Error: "Email is required"})
				continue
			}

			if item.FirstNames.Present {
				if len(item.FirstNames.Value) > 250 {
					result = append(result, fndapi.UsersCreateResponse{Username: item.Username, Created: false, Error: "Firstnames are too long"})
					continue
				}

				if len(item.FirstNames.Value) == 0 {
					result = append(result, fndapi.UsersCreateResponse{Username: item.Username, Created: false, Error: "Firstnames must not be empty"})
					continue
				}
			}

			if item.LastName.Present {
				if len(item.LastName.Value) > 250 {
					result = append(result, fndapi.UsersCreateResponse{Username: item.Username, Created: false, Error: "Lastnames are too long"})
					continue
				}

				if len(item.LastName.Value) == 0 {
					result = append(result, fndapi.UsersCreateResponse{Username: item.Username, Created: false, Error: "Lastnames must not be empty"})
					continue
				}
			}

			role := item.Role.GetOrDefault(fndapi.PrincipalUser)
			if role != fndapi.PrincipalUser && role != fndapi.PrincipalAdmin {
				result = append(result, fndapi.UsersCreateResponse{Username: item.Username, Created: false, Error: "Invalid role supplied"})
				continue
			}

			if item.OrgId.Present {
				if len(item.OrgId.Value) > 250 {
					result = append(result, fndapi.UsersCreateResponse{Username: item.Username, Created: false, Error: "Organization id is too long"})
					continue
				}

				if len(item.OrgId.Value) == 0 {
					result = append(result, fndapi.UsersCreateResponse{Username: item.Username, Created: false, Error: "Organization id must not be empty\""})
					continue
				}
			}

			passwordAndSalt := util.HashPassword(item.Password, util.GenSalt())

			_, err := PrincipalCreate(PrincipalSpecification{
				Type:           "PERSON",
				Id:             item.Username,
				Role:           role,
				FirstNames:     item.FirstNames,
				LastName:       item.LastName,
				HashedPassword: util.OptValue(passwordAndSalt.HashedPassword),
				Salt:           util.OptValue(passwordAndSalt.Salt),
				OrgId:          item.OrgId,
				Email:          util.OptValue(item.Email),
			})

			if err != nil {
				result = append(result, fndapi.UsersCreateResponse{Username: item.Username, Created: false, Error: err.Why})
				continue
			}

			result = append(result, fndapi.UsersCreateResponse{Username: item.Username, Created: true})
		}

		// Henrik: Still gives the frontend the needed info to determine if success or failed
		for _, response := range result {
			if response.Created == false {
				return result, util.HttpErr(http.StatusBadRequest, response.Error)
			}
		}
		return result, nil
	})

	fndapi.UsersChangePassword.Handler(func(info rpc.RequestInfo, request fndapi.UsersChangePasswordRequest) (util.Empty, *util.HttpError) {
		if len(request.NewPassword) > 250 {
			return util.Empty{}, util.HttpErr(http.StatusBadRequest, "password too long")
		}

		if len(request.NewPassword) < 8 {
			return util.Empty{}, util.HttpErr(http.StatusBadRequest, "password must be at least 8 characters")
		}

		err := db.NewTx(func(tx *db.Transaction) *util.HttpError {
			principal, ok := PrincipalRetrieve(tx, info.Actor.Username)
			if !ok || !principal.HashedPassword.Present || !principal.Salt.Present {
				util.CheckPassword(dummyPasswordForTiming.HashedPassword, dummyPasswordForTiming.Salt, request.NewPassword)
				return util.HttpErr(http.StatusUnauthorized, "Incorrect username or password.")
			}

			if !util.CheckPassword(principal.HashedPassword.Value, principal.Salt.Value, request.CurrentPassword) {
				return util.HttpErr(http.StatusUnauthorized, "Incorrect username or password.")
			}
			return nil
		})

		if err != nil {
			return util.Empty{}, err
		}

		ok := PrincipalUpdatePassword(info.Actor.Username, request.NewPassword)
		if !ok {
			return util.Empty{}, util.HttpErr(http.StatusInternalServerError, "Failed to update password")
		}

		return util.Empty{}, nil
	})

	fndapi.UsersRetrieveOptionalInfo.Handler(func(info rpc.RequestInfo, request util.Empty) (fndapi.OptionalUserInfo, *util.HttpError) {
		return UserOptInfoRetrieve(info.Actor), nil
	})

	fndapi.UsersUpdateOptionalInfo.Handler(func(info rpc.RequestInfo, request fndapi.OptionalUserInfo) (util.Empty, *util.HttpError) {
		UsersOptInfoUpdate(info.Actor, request)
		return util.Empty{}, nil
	})

	fndapi.UsersVerifyUserInfo.Handler(func(info rpc.RequestInfo, request fndapi.FindByStringId) (string, *util.HttpError) {
		UsersInfoVerify(request.Id)
		return cfg.Configuration.SelfPublic.ToURL(), nil
	})

	fndapi.UsersRetrieveInfo.Handler(func(info rpc.RequestInfo, request util.Empty) (fndapi.UsersRetrieveInfoResponse, *util.HttpError) {
		return UsersInfoRetrieve(info.Actor), nil
	})

	fndapi.UsersUpdateInfo.Handler(func(info rpc.RequestInfo, request fndapi.UsersUpdateInfoRequest) (util.Empty, *util.HttpError) {
		err := UsersInfoUpdate(info.Actor, request, util.ClientIP(info.HttpRequest).String())
		return util.Empty{}, err
	})
}
