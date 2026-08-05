package controller

import (
	"net/http"

	fnd "ucloud.dk/shared/pkg/foundation"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var ContainerRegistries ContainerRegistryService

type ContainerRegistryService struct {
	Create          func(registry *orc.ContainerRegistry) *util.HttpError
	Delete          func(registry *orc.ContainerRegistry) *util.HttpError
	OnUpdatedLabels func(registry *orc.ContainerRegistry) *util.HttpError
}

func initContainerRegistries() {
	orc.ContainerRegistriesProviderCreate.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orc.ContainerRegistry]) (fnd.BulkResponse[fnd.FindByStringId], *util.HttpError) {
		response := fnd.BulkResponse[fnd.FindByStringId]{}
		for _, item := range request.Items {
			if ContainerRegistries.Create == nil {
				return fnd.BulkResponse[fnd.FindByStringId]{}, util.HttpErr(http.StatusBadRequest, "Container registry creation is not supported")
			}

			if err := ContainerRegistries.Create(&item); err != nil {
				return fnd.BulkResponse[fnd.FindByStringId]{}, err
			}
			ContainerRegistryTrack(item)
			response.Responses = append(response.Responses, fnd.FindByStringId{})
		}
		return response, nil
	})

	orc.ContainerRegistriesProviderDelete.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orc.ContainerRegistry]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
		response := fnd.BulkResponse[util.Empty]{}
		for _, item := range request.Items {
			if ContainerRegistries.Delete == nil {
				return fnd.BulkResponse[util.Empty]{}, util.HttpErr(http.StatusBadRequest, "Container registry deletion is not supported")
			}

			if err := ContainerRegistries.Delete(&item); err != nil {
				return fnd.BulkResponse[util.Empty]{}, err
			}
			ContainerRegistryRemove(item.Id)
			response.Responses = append(response.Responses, util.Empty{})
		}
		return response, nil
	})

	orc.ContainerRegistriesProviderOnUpdatedLabels.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orc.ContainerRegistry]) (util.Empty, *util.HttpError) {
		for _, item := range request.Items {
			if ContainerRegistries.OnUpdatedLabels != nil {
				if err := ContainerRegistries.OnUpdatedLabels(&item); err != nil {
					return util.Empty{}, err
				}
			}
			ContainerRegistryTrack(item)
		}
		return util.Empty{}, nil
	})
}
