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
	BrowseImages    func(request orc.ContainerRepositoriesProviderBrowseImagesRequest) (fnd.PageV2[orc.ContainerRepositoryImage], *util.HttpError)
	DeleteImage     func(request orc.ContainerRepositoriesProviderDeleteImageRequest) *util.HttpError
	OnDeleted       func(repository *orc.ContainerRepository)
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
			if ContainerRepositories.OnDeleted != nil {
				ContainerRepositories.OnDeleted(&item)
			}
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

	orc.ContainerRepositoriesProviderBrowseImages.Handler(func(info rpc.RequestInfo, request orc.ContainerRepositoriesProviderBrowseImagesRequest) (fnd.PageV2[orc.ContainerRepositoryImage], *util.HttpError) {
		if ContainerRepositories.BrowseImages == nil {
			return fnd.PageV2[orc.ContainerRepositoryImage]{}, util.HttpErr(http.StatusBadRequest, "Container repository image browsing is not supported")
		}
		ContainerRepositoryTrack(request.ResolvedRepository)
		return ContainerRepositories.BrowseImages(request)
	})

	orc.ContainerRepositoriesProviderDeleteImage.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orc.ContainerRepositoriesProviderDeleteImageRequest]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
		response := fnd.BulkResponse[util.Empty]{Responses: make([]util.Empty, 0, len(request.Items))}
		for _, item := range request.Items {
			if ContainerRepositories.DeleteImage == nil {
				return fnd.BulkResponse[util.Empty]{}, util.HttpErr(http.StatusBadRequest, "Container repository image deletion is not supported")
			}
			ContainerRepositoryTrack(item.ResolvedRepository)
			if err := ContainerRepositories.DeleteImage(item); err != nil {
				return fnd.BulkResponse[util.Empty]{}, err
			}
			response.Responses = append(response.Responses, util.Empty{})
		}
		return response, nil
	})
}
