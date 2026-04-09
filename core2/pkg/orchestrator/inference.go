package orchestrator

import (
	"net/http"
	"slices"

	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initInference() {
	orcapi.InferenceOpenPlayground.Handler(func(info rpc.RequestInfo, request orcapi.InferenceOpenPlaygroundRequest) (orcapi.InferenceOpenPlaygroundResponse, *util.HttpError) {
		providerId, err := inferenceSelectProvider(info.Actor, request.ProviderId)
		if err != nil {
			return orcapi.InferenceOpenPlaygroundResponse{}, err
		}

		resp, err := InvokeProvider(providerId, orcapi.InferenceOpenPlaygroundProvider,
			orcapi.InferenceOpenPlaygroundProviderRequest{Owner: inferenceActorToOwner(info.Actor)},
			ProviderCallOpts{Username: util.OptValue(info.Actor.Username)},
		)
		if err != nil {
			return orcapi.InferenceOpenPlaygroundResponse{}, err
		}

		return orcapi.InferenceOpenPlaygroundResponse(resp), nil
	})
}

func inferenceSelectProvider(actor rpc.Actor, requested util.Option[string]) (string, *util.HttpError) {
	providers, err := accapi.FindRelevantProviders.Invoke(fndapi.BulkRequestOf(accapi.FindRelevantProvidersRequest{
		Username:          actor.Username,
		Project:           util.OptMap(actor.Project, func(value rpc.ProjectId) string { return string(value) }),
		UseProject:        actor.Project.Present,
		FilterProductType: util.OptValue(accapi.ProductTypeLicense),
	}))
	if err != nil || len(providers.Responses) == 0 {
		return "", util.HttpErr(http.StatusPaymentRequired, "could not determine available inference providers")
	}

	available := append([]string{}, providers.Responses[0].Providers...)
	slices.Sort(available)

	filtered := make([]string, 0, len(available))
	for _, provider := range available {
		if inferenceProviderEnabled(provider) {
			filtered = append(filtered, provider)
		}
	}

	if len(filtered) == 0 {
		return "", util.HttpErr(http.StatusNotFound, "no inference providers available")
	}

	if requested.Present {
		for _, provider := range filtered {
			if provider == requested.Value {
				return provider, nil
			}
		}
		return "", util.HttpErr(http.StatusForbidden, "provider is not available for inference")
	}

	return filtered[0], nil
}

func inferenceProviderEnabled(provider string) bool {
	return true // TODO
}

func inferenceActorToOwner(actor rpc.Actor) orcapi.ResourceOwner {
	owner := orcapi.ResourceOwner{CreatedBy: actor.Username}
	if actor.Project.Present {
		owner.Project.Set(string(actor.Project.Value))
	}
	return owner
}
