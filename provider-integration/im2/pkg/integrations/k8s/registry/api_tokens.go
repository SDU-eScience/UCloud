package registry

import (
	"ucloud.dk/pkg/controller"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const containerRegistryApiTokenKind = "containerRegistry"

func InitApiTokens() controller.ApiTokenProvider {
	return controller.ApiTokenProvider{
		Kind:    containerRegistryApiTokenKind,
		Options: containerRegistryApiTokenOptions(),
		Create:  createContainerRegistryApiToken,
	}
}

func createContainerRegistryApiToken(info rpc.RequestInfo, request orcapi.ApiToken) (orcapi.ApiTokenStatus, *util.HttpError) {
	_ = info
	return controller.ApiTokenCreate(containerRegistryApiTokenKind, "https://"+VirtualHost, request)
}

func containerRegistryApiTokenOptions() orcapi.ApiTokenOptions {
	return orcapi.ApiTokenOptions{
		AvailablePermissions: []orcapi.ApiTokenPermissionSpecification{
			{
				Name:        containerRegistryApiTokenKind,
				Title:       "Container registries",
				Description: "API token used to authenticate with container registries. Access is limited by registry permissions and the token permissions.",
				Actions: map[string]string{
					"pull": "Pull images",
					"push": "Push images",
				},
			},
		},
	}
}
