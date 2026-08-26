package inference

import (
	"net/http"

	"ucloud.dk/pkg/controller"
	apm "ucloud.dk/shared/pkg/accounting"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const inferenceApiTokenKind = "inference"

// API tokens
// =====================================================================================================================

func inferenceApiKeyValidate(key string) (apm.WalletOwner, *util.HttpError) {
	owner, permissions, httpErr := controller.ApiTokenValidate(inferenceApiTokenKind, key)
	if httpErr != nil {
		return apm.WalletOwner{}, httpErr
	}

	hasUsePermission := false
	for _, permission := range permissions {
		if permission.Name == inferenceApiTokenKind && permission.Action == "use" {
			hasUsePermission = true
			break
		}
	}
	if !hasUsePermission {
		return apm.WalletOwner{}, util.HttpErr(http.StatusForbidden, "token does not allow inference use")
	}

	if controller.WalletIsLocked(owner, inferenceGlobals.Product.Category.Name).Locked {
		return apm.WalletOwner{}, util.HttpErr(http.StatusPaymentRequired, "no more resources available")
	}
	return owner, nil
}

func InitApiTokens() controller.ApiTokenProvider {
	return controller.ApiTokenProvider{
		Kind:    inferenceApiTokenKind,
		Options: inferenceApiTokenOptions(),
		Create:  inferenceCreateApiToken,
	}
}

func inferenceCreateApiToken(info rpc.RequestInfo, request orcapi.ApiToken) (orcapi.ApiTokenStatus, *util.HttpError) {
	_ = info

	if !inferenceGlobals.Ready.Load() {
		return orcapi.ApiTokenStatus{}, util.HttpErr(http.StatusServiceUnavailable, "inference service is not available")
	}

	return controller.ApiTokenCreate(inferenceApiTokenKind, inferenceServerBase(), request)
}

func inferenceApiTokenOptions() orcapi.ApiTokenOptions {
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
	}
}
