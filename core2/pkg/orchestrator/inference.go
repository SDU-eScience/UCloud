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
		selection, err := inferenceSelectProvider(info.Actor, request.ProviderId)
		if err != nil {
			return orcapi.InferenceOpenPlaygroundResponse{}, err
		}

		if !selection.HasCredits {
			return orcapi.InferenceOpenPlaygroundResponse{}, util.HttpErr(http.StatusPaymentRequired, "no credits available for inference")
		}

		resp, err := InvokeProvider(selection.ProviderId, orcapi.InferenceOpenPlaygroundProvider,
			orcapi.InferenceOpenPlaygroundProviderRequest{Owner: inferenceActorToOwner(info.Actor)},
			ProviderCallOpts{Username: util.OptValue(info.Actor.Username)},
		)
		if err != nil {
			return orcapi.InferenceOpenPlaygroundResponse{}, err
		}

		return orcapi.InferenceOpenPlaygroundResponse(resp), nil
	})

	orcapi.InferenceListPlaygroundThreads.Handler(func(info rpc.RequestInfo, request orcapi.InferenceListPlaygroundThreadsRequest) (orcapi.InferenceListPlaygroundThreadsResponse, *util.HttpError) {
		selection, err := inferenceSelectProvider(info.Actor, request.ProviderId)
		if err != nil {
			return orcapi.InferenceListPlaygroundThreadsResponse{}, err
		}

		if !selection.HasCredits {
			return orcapi.InferenceListPlaygroundThreadsResponse{}, util.HttpErr(http.StatusPaymentRequired, "no credits available for inference")
		}

		return InvokeProvider(selection.ProviderId, orcapi.InferenceListPlaygroundThreadsProvider,
			orcapi.InferenceListPlaygroundThreadsProviderRequest{Owner: inferenceActorToOwner(info.Actor)},
			ProviderCallOpts{Username: util.OptValue(info.Actor.Username)},
		)
	})

	orcapi.InferenceListModels.Handler(func(info rpc.RequestInfo, request orcapi.InferenceListModelsRequest) (orcapi.InferenceListModelsResponse, *util.HttpError) {
		selection, err := inferenceSelectProvider(info.Actor, request.ProviderId)
		if err != nil {
			return orcapi.InferenceListModelsResponse{}, err
		}

		resp, err := InvokeProvider(selection.ProviderId, orcapi.InferenceListModelsProvider,
			orcapi.InferenceListModelsProviderRequest{Owner: inferenceActorToOwner(info.Actor)},
			ProviderCallOpts{Username: util.OptValue(info.Actor.Username)},
		)
		if err != nil {
			return orcapi.InferenceListModelsResponse{}, err
		}
		resp.ProviderId = selection.ProviderId
		return resp, nil
	})

	orcapi.InferenceUpdateModel.Handler(func(info rpc.RequestInfo, request orcapi.InferenceUpdateModelRequest) (util.Empty, *util.HttpError) {
		selection, err := inferenceSelectProvider(info.Actor, request.ProviderId)
		if err != nil {
			return util.Empty{}, err
		}

		if !selection.HasCredits {
			return util.Empty{}, util.HttpErr(http.StatusForbidden, "no credits available for inference")
		}

		return InvokeProvider(selection.ProviderId, orcapi.InferenceUpdateModelProvider,
			orcapi.InferenceUpdateModelProviderRequest{
				Owner:   inferenceActorToOwner(info.Actor),
				OldName: request.OldName,
				Model:   request.Model,
			},
			ProviderCallOpts{Username: util.OptValue(info.Actor.Username)},
		)
	})

	orcapi.InferenceUpdateBenchmarks.Handler(func(info rpc.RequestInfo, request orcapi.InferenceUpdateBenchmarksRequest) (util.Empty, *util.HttpError) {
		selection, err := inferenceSelectProvider(info.Actor, request.ProviderId)
		if err != nil {
			return util.Empty{}, err
		}

		if !selection.HasCredits {
			return util.Empty{}, util.HttpErr(http.StatusForbidden, "no credits available for inference")
		}

		return InvokeProvider(selection.ProviderId, orcapi.InferenceUpdateBenchmarksProvider,
			orcapi.InferenceUpdateBenchmarksProviderRequest{
				Owner:      inferenceActorToOwner(info.Actor),
				Benchmarks: request.Benchmarks,
			},
			ProviderCallOpts{Username: util.OptValue(info.Actor.Username)},
		)
	})
}

type inferenceProviderSelection struct {
	ProviderId string
	HasCredits bool
}

func inferenceSelectProvider(actor rpc.Actor, requested util.Option[string]) (inferenceProviderSelection, *util.HttpError) {
	creditProviders, err := accapi.FindRelevantProviders.Invoke(fndapi.BulkRequestOf(accapi.FindRelevantProvidersRequest{
		Username:          actor.Username,
		FilterProductType: util.OptValue(accapi.ProductTypeInference),
		UseProject:        false,
	}))
	if err != nil {
		return inferenceProviderSelection{}, util.HttpErr(http.StatusInternalServerError, "could not determine available inference providers")
	}

	var available []string
	if len(creditProviders.Responses) > 0 {
		available = inferenceEnabledProviders(creditProviders.Responses[0].Providers)
	}

	hasCredits := len(available) > 0
	if !hasCredits {
		allProviders, allErr := accapi.FindAllProviders.Invoke(fndapi.BulkRequestOf(accapi.FindAllProvidersRequest{
			FilterProductType: util.OptValue(accapi.ProductTypeInference),
			IncludeFreeToUse:  util.OptValue(true),
		}))
		if allErr != nil {
			return inferenceProviderSelection{}, util.HttpErr(http.StatusInternalServerError, "could not determine available inference providers")
		}

		if len(allProviders.Responses) > 0 {
			available = inferenceEnabledProviders(allProviders.Responses[0].Providers)
		}
	}

	if len(available) == 0 {
		return inferenceProviderSelection{}, util.HttpErr(http.StatusNotFound, "no inference providers available")
	}

	if requested.Present {
		for _, provider := range available {
			if provider == requested.Value {
				return inferenceProviderSelection{ProviderId: provider, HasCredits: hasCredits}, nil
			}
		}
		return inferenceProviderSelection{}, util.HttpErr(http.StatusForbidden, "provider is not available for inference")
	}

	return inferenceProviderSelection{
		ProviderId: inferencePreferredProvider(available),
		HasCredits: hasCredits,
	}, nil
}

func inferenceEnabledProviders(providers []string) []string {
	filtered := make([]string, 0, len(providers))
	for _, provider := range providers {
		if inferenceProviderEnabled(provider) {
			filtered = append(filtered, provider)
		}
	}

	slices.Sort(filtered)
	return filtered
}

func inferencePreferredProvider(providers []string) string {
	if slices.Contains(providers, "ucloud") {
		return "ucloud"
	}

	return providers[0]
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
