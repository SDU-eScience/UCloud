package orchestrators

import (
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const inferenceBaseContext = "jobs/inference"

type InferenceOpenPlaygroundRequest struct {
	ProviderId util.Option[string] `json:"providerId"`
}

type InferenceOpenPlaygroundResponse struct {
	ConnectTo    string `json:"connectTo"`
	SessionToken string `json:"sessionToken"`
}

var InferenceOpenPlayground = rpc.Call[InferenceOpenPlaygroundRequest, InferenceOpenPlaygroundResponse]{
	BaseContext: inferenceBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "openPlayground",
	Roles:       rpc.RolesEndUser,
}

const inferenceProviderBaseContext = "ucloud/" + rpc.ProviderPlaceholder + "/jobs/inference"

type InferenceOpenPlaygroundProviderRequest struct {
	Owner ResourceOwner `json:"owner"`
}

type InferenceOpenPlaygroundProviderResponse struct {
	ConnectTo    string `json:"connectTo"`
	SessionToken string `json:"sessionToken"`
}

var InferenceOpenPlaygroundProvider = rpc.Call[InferenceOpenPlaygroundProviderRequest, InferenceOpenPlaygroundProviderResponse]{
	BaseContext: inferenceProviderBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "openPlayground",
	Roles:       rpc.RolesService,
}
