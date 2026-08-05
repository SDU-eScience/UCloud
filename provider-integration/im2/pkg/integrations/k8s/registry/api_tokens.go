package registry

import (
	"ucloud.dk/pkg/controller"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const containerRepositoryApiTokenKind = "containerRepository"

func InitApiTokens() controller.ApiTokenProvider {
	return controller.ApiTokenProvider{
		Kind:    containerRepositoryApiTokenKind,
		Options: containerRepositoryApiTokenOptions(),
		Create:  createContainerRepositoryApiToken,
	}
}

func createContainerRepositoryApiToken(info rpc.RequestInfo, request orcapi.ApiToken) (orcapi.ApiTokenStatus, *util.HttpError) {
	_ = info
	return controller.ApiTokenCreate(containerRepositoryApiTokenKind, "https://"+VirtualHost, request)
}

func containerRepositoryApiTokenOptions() orcapi.ApiTokenOptions {
	return orcapi.ApiTokenOptions{
		AvailablePermissions: []orcapi.ApiTokenPermissionSpecification{
			{
				Name:        containerRepositoryApiTokenKind,
				Title:       "Container repositories",
				Description: "API token used to authenticate with container repositories. Access is limited by repository permissions and the token permissions.",
				Actions: map[string]string{
					"pull": "Pull images",
					"push": "Push images",
				},
			},
		},
	}
}
