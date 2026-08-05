package controller

import (
	"net/http"

	fnd "ucloud.dk/shared/pkg/foundation"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var ContainerRepositories ContainerRepositoryService

type ContainerRepositoryService struct {
	Create          func(repository *orc.ContainerRepository) *util.HttpError
	Delete          func(repository *orc.ContainerRepository) *util.HttpError
	OnUpdatedLabels func(repository *orc.ContainerRepository) *util.HttpError
}

func initContainerRepositories() {
	orc.ContainerRepositoriesProviderCreate.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orc.ContainerRepository]) (fnd.BulkResponse[fnd.FindByStringId], *util.HttpError) {
		response := fnd.BulkResponse[fnd.FindByStringId]{}
		for _, item := range request.Items {
			if ContainerRepositories.Create == nil {
				return fnd.BulkResponse[fnd.FindByStringId]{}, util.HttpErr(http.StatusBadRequest, "Container repository creation is not supported")
			}

			if err := ContainerRepositories.Create(&item); err != nil {
				return fnd.BulkResponse[fnd.FindByStringId]{}, err
			}
			ContainerRepositoryTrack(item)
			response.Responses = append(response.Responses, fnd.FindByStringId{})
		}
		return response, nil
	})

	orc.ContainerRepositoriesProviderDelete.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orc.ContainerRepository]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
		response := fnd.BulkResponse[util.Empty]{}
		for _, item := range request.Items {
			if ContainerRepositories.Delete == nil {
				return fnd.BulkResponse[util.Empty]{}, util.HttpErr(http.StatusBadRequest, "Container repository deletion is not supported")
			}

			if err := ContainerRepositories.Delete(&item); err != nil {
				return fnd.BulkResponse[util.Empty]{}, err
			}
			ContainerRepositoryRemove(item.Id)
			response.Responses = append(response.Responses, util.Empty{})
		}
		return response, nil
	})

	orc.ContainerRepositoriesProviderOnUpdatedLabels.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orc.ContainerRepository]) (util.Empty, *util.HttpError) {
		for _, item := range request.Items {
			if ContainerRepositories.OnUpdatedLabels != nil {
				if err := ContainerRepositories.OnUpdatedLabels(&item); err != nil {
					return util.Empty{}, err
				}
			}
			ContainerRepositoryTrack(item)
		}
		return util.Empty{}, nil
	})
}
