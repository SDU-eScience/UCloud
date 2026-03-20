package controller

import (
	fnd "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var ApiTokens ApiTokenService

type ApiTokenService struct {
	Create          func(info rpc.RequestInfo, request orcapi.ApiToken) (orcapi.ApiTokenStatus, *util.HttpError)
	Revoke          func(info rpc.RequestInfo, request fnd.FindByStringId) (util.Empty, *util.HttpError)
	RetrieveOptions func(info rpc.RequestInfo, request util.Empty) (orcapi.ApiTokenOptions, *util.HttpError)
}

func initApiTokens() {
	if RunsUserCode() {
		orcapi.ApiTokenProviderCreate.Handler(func(info rpc.RequestInfo, request orcapi.ApiToken) (orcapi.ApiTokenStatus, *util.HttpError) {
			handler := ApiTokens.Create
			if handler == nil {
				return orcapi.ApiTokenStatus{}, util.ServerHttpError("API token creation not supported")
			}

			return handler(info, request)
		})

		orcapi.ApiTokenProviderRevoke.Handler(func(info rpc.RequestInfo, request fnd.FindByStringId) (util.Empty, *util.HttpError) {
			handler := ApiTokens.Revoke
			if handler == nil {
				return util.Empty{}, util.ServerHttpError("API token revocation not supported")
			}

			return handler(info, request)
		})

		orcapi.ApiTokenProviderRetrieveOptions.Handler(func(info rpc.RequestInfo, request util.Empty) (orcapi.ApiTokenOptions, *util.HttpError) {
			handler := ApiTokens.RetrieveOptions
			if handler == nil {
				return orcapi.ApiTokenOptions{}, util.ServerHttpError("API token option retrieval not supported")
			}

			return handler(info, request)
		})
	}
}
