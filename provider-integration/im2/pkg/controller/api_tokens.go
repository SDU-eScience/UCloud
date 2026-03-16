package controller

import (
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var ApiTokens ApiTokenService

type ApiTokenService struct {
	Create func(info rpc.RequestInfo, request orcapi.ApiToken) (orcapi.ApiTokenStatus, *util.HttpError)
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
	}
}
