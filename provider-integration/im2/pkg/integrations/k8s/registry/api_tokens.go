package registry

import (
	"time"

	"ucloud.dk/pkg/controller"
	fnd "ucloud.dk/shared/pkg/foundation"
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
	return controller.ApiTokenCreate(containerRepositoryApiTokenKind, Server(), request)
}

type SnapshotToken struct {
	Id     string
	Secret string
}

func CreateSnapshotToken(owner orcapi.ResourceOwner, lifetime time.Duration) (SnapshotToken, *util.HttpError) {
	return createShortLivedToken(
		owner,
		lifetime,
		"Container snapshot",
		"Short-lived token used to publish a container snapshot.",
		[]string{"pull", "push"},
	)
}

func CreatePullToken(owner orcapi.ResourceOwner, lifetime time.Duration) (SnapshotToken, *util.HttpError) {
	return createShortLivedToken(
		owner,
		lifetime,
		"Flavor image pull",
		"Short-lived token used to pull a flavor image.",
		[]string{"pull"},
	)
}

func createShortLivedToken(owner orcapi.ResourceOwner, lifetime time.Duration, title, description string, actions []string) (SnapshotToken, *util.HttpError) {
	now := time.Now()
	tokenId := util.SecureToken()
	permissions := make([]orcapi.ApiTokenPermission, 0, len(actions))
	for _, action := range actions {
		permissions = append(permissions, orcapi.ApiTokenPermission{Name: containerRepositoryApiTokenKind, Action: action})
	}
	request := orcapi.ApiToken{
		Resource: orcapi.Resource{
			Id:        tokenId,
			CreatedAt: fnd.Timestamp(now),
			Owner:     owner,
		},
		Specification: orcapi.ApiTokenSpecification{
			Title:                title,
			Description:          description,
			RequestedPermissions: permissions,
			ExpiresAt:            fnd.Timestamp(now.Add(lifetime)),
		},
	}
	status, err := controller.ApiTokenCreate(containerRepositoryApiTokenKind, Server(), request)
	if err != nil {
		return SnapshotToken{}, err
	}
	if !status.Token.Present {
		return SnapshotToken{}, util.ServerHttpError("registry did not return a snapshot token")
	}
	return SnapshotToken{Id: tokenId, Secret: status.Token.Value}, nil
}

func RevokeSnapshotToken(tokenId string) {
	if tokenId != "" {
		_, _ = controller.ApiTokenRevoke(rpc.RequestInfo{}, fnd.FindByStringId{Id: tokenId})
	}
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
