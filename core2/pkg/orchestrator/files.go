package orchestrator

import (
	"net/http"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initFiles() {
	orcapi.FilesBrowse.Handler(func(info rpc.RequestInfo, request orcapi.FilesBrowseRequest) (fndapi.PageV2[orcapi.UFile], *util.HttpError) {
		return FilesBrowse(info.Actor, request)
	})

	orcapi.FilesRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.FilesRetrieveRequest) (orcapi.UFile, *util.HttpError) {
		return FilesRetrieve(info.Actor, request)
	})

	orcapi.FilesMove.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.FilesSourceAndDestination]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		return FilesMove(info.Actor, request)
	})

	orcapi.FilesCopy.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.FilesSourceAndDestination]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		return FilesCopy(info.Actor, request)
	})

	orcapi.FilesCreateUpload.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.FilesCreateUploadRequest]) (fndapi.BulkResponse[orcapi.FilesCreateUploadResponse], *util.HttpError) {
		return FilesCreateUpload(info.Actor, request)
	})

	orcapi.FilesCreateDownload.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[orcapi.FilesCreateDownloadResponse], *util.HttpError) {
		return FilesCreateDownload(info.Actor, request)
	})

	orcapi.FilesCreateFolder.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.FilesCreateFolderRequest]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		return FilesCreateFolder(info.Actor, request)
	})

	orcapi.FilesDelete.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		return FilesDelete(info.Actor, request)
	})

	orcapi.FilesTrash.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		return FilesMoveToTrash(info.Actor, request)
	})

	orcapi.FilesEmptyTrash.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		return FilesEmptyTrash(info.Actor, request)
	})
}

func FilesEmptyTrash(actor rpc.Actor, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
	var result fndapi.BulkResponse[util.Empty]
	var paths []string
	for _, reqItem := range request.Items {
		paths = append(paths, reqItem.Id)
		result.Responses = append(result.Responses, util.Empty{})
	}

	drives, err := filesFetchDrives(actor, paths, orcapi.PermissionEdit)
	if err != nil {
		return result, err
	}

	requestsByProvider := map[string][]orcapi.FilesProviderTrashRequest{}
	for _, reqItem := range request.Items {
		driveId, _ := orcapi.DriveIdFromUCloudPath(reqItem.Id)
		drive, ok := drives[driveId]
		if !ok {
			return result, util.HttpErr(http.StatusNotFound, "move to trash requested in unknown drive")
		}

		if featureSupported(driveType, drive.Specification.Product, driveOpsReadOnly) {
			return result, util.HttpErr(http.StatusNotFound, "drive is read only")
		}

		if !featureSupported(driveType, drive.Specification.Product, driveOpsTrash) {
			return result, util.HttpErr(http.StatusNotFound, "trash is not supported")
		}

		providerId := drive.Specification.Product.Provider
		requestsByProvider[providerId] = append(requestsByProvider[providerId], orcapi.FilesProviderTrashRequest{
			Id:                 reqItem.Id,
			ResolvedCollection: drive,
		})
	}

	for provider, requests := range requestsByProvider {
		_, err = InvokeProvider(provider, orcapi.FilesProviderEmptyTrash, fndapi.BulkRequestOf(requests...),
			ProviderCallOpts{
				Username: util.OptValue(actor.Username),
				Reason:   util.OptValue("empty trash"),
			})

		if err != nil {
			return result, err
		}
	}

	return result, nil
}

func FilesMoveToTrash(actor rpc.Actor, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
	var result fndapi.BulkResponse[util.Empty]
	var paths []string
	for _, reqItem := range request.Items {
		paths = append(paths, reqItem.Id)
		result.Responses = append(result.Responses, util.Empty{})
	}

	drives, err := filesFetchDrives(actor, paths, orcapi.PermissionEdit)
	if err != nil {
		return result, err
	}

	requestsByProvider := map[string][]orcapi.FilesProviderTrashRequest{}
	for _, reqItem := range request.Items {
		driveId, _ := orcapi.DriveIdFromUCloudPath(reqItem.Id)
		drive, ok := drives[driveId]
		if !ok {
			return result, util.HttpErr(http.StatusNotFound, "move to trash requested in unknown drive")
		}

		if featureSupported(driveType, drive.Specification.Product, driveOpsReadOnly) {
			return result, util.HttpErr(http.StatusNotFound, "drive is read only")
		}

		if !featureSupported(driveType, drive.Specification.Product, driveOpsTrash) {
			return result, util.HttpErr(http.StatusNotFound, "trash is not supported")
		}

		providerId := drive.Specification.Product.Provider
		requestsByProvider[providerId] = append(requestsByProvider[providerId], orcapi.FilesProviderTrashRequest{
			Id:                 reqItem.Id,
			ResolvedCollection: drive,
		})
	}

	for provider, requests := range requestsByProvider {
		_, err = InvokeProvider(provider, orcapi.FilesProviderTrash, fndapi.BulkRequestOf(requests...),
			ProviderCallOpts{
				Username: util.OptValue(actor.Username),
				Reason:   util.OptValue("move to trash"),
			})

		if err != nil {
			return result, err
		}
	}

	return result, nil
}

func FilesDelete(actor rpc.Actor, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
	var result fndapi.BulkResponse[util.Empty]
	var paths []string
	for _, reqItem := range request.Items {
		paths = append(paths, reqItem.Id)
		result.Responses = append(result.Responses, util.Empty{})
	}

	drives, err := filesFetchDrives(actor, paths, orcapi.PermissionEdit)
	if err != nil {
		return result, err
	}

	requestsByProvider := map[string][]orcapi.UFile{}
	for _, reqItem := range request.Items {
		driveId, _ := orcapi.DriveIdFromUCloudPath(reqItem.Id)
		drive, ok := drives[driveId]
		if !ok {
			return result, util.HttpErr(http.StatusNotFound, "folder creation requested to unknown drive")
		}

		if featureSupported(driveType, drive.Specification.Product, driveOpsReadOnly) {
			return result, util.HttpErr(http.StatusNotFound, "drive is read only")
		}

		providerId := drive.Specification.Product.Provider
		requestsByProvider[providerId] = append(requestsByProvider[providerId], orcapi.UFile{
			Resource: orcapi.Resource{
				Id:          reqItem.Id,
				CreatedAt:   drive.CreatedAt,
				Owner:       drive.Owner,
				Permissions: drive.Permissions,
			},
			Specification: orcapi.UFileSpecification{
				Collection: driveId,
				Product:    drive.Specification.Product,
			},
			Status: orcapi.UFileStatus{},
		})
	}

	for provider, requests := range requestsByProvider {
		_, err = InvokeProvider(provider, orcapi.FilesProviderDelete, fndapi.BulkRequestOf(requests...),
			ProviderCallOpts{
				Username: util.OptValue(actor.Username),
				Reason:   util.OptValue("delete file"),
			})

		if err != nil {
			return result, err
		}
	}

	return result, nil
}

func FilesCreateFolder(actor rpc.Actor, request fndapi.BulkRequest[orcapi.FilesCreateFolderRequest]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
	var result fndapi.BulkResponse[util.Empty]
	var paths []string
	for _, reqItem := range request.Items {
		paths = append(paths, reqItem.Id)
		result.Responses = append(result.Responses, util.Empty{})
	}

	drives, err := filesFetchDrives(actor, paths, orcapi.PermissionEdit)
	if err != nil {
		return result, err
	}

	requestsByProvider := map[string][]orcapi.FilesProviderCreateFolderRequest{}
	for _, reqItem := range request.Items {
		driveId, _ := orcapi.DriveIdFromUCloudPath(reqItem.Id)
		drive, ok := drives[driveId]
		if !ok {
			return result, util.HttpErr(http.StatusNotFound, "folder creation requested to unknown drive")
		}

		if featureSupported(driveType, drive.Specification.Product, driveOpsReadOnly) {
			return result, util.HttpErr(http.StatusNotFound, "drive is read only")
		}

		providerId := drive.Specification.Product.Provider
		requestsByProvider[providerId] = append(requestsByProvider[providerId], orcapi.FilesProviderCreateFolderRequest{
			Id:                 reqItem.Id,
			ConflictPolicy:     reqItem.ConflictPolicy,
			ResolvedCollection: drive,
		})
	}

	for provider, requests := range requestsByProvider {
		_, err = InvokeProvider(provider, orcapi.FilesProviderCreateFolder, fndapi.BulkRequestOf(requests...),
			ProviderCallOpts{
				Username: util.OptValue(actor.Username),
				Reason:   util.OptValue("create folder"),
			})

		if err != nil {
			return result, err
		}
	}

	return result, nil
}

func FilesCreateUpload(
	actor rpc.Actor,
	request fndapi.BulkRequest[orcapi.FilesCreateUploadRequest],
) (fndapi.BulkResponse[orcapi.FilesCreateUploadResponse], *util.HttpError) {
	var result fndapi.BulkResponse[orcapi.FilesCreateUploadResponse]
	var paths []string
	for _, reqItem := range request.Items {
		paths = append(paths, reqItem.Id)
	}

	drives, err := filesFetchDrives(actor, paths, orcapi.PermissionEdit)
	if err != nil {
		return result, err
	}

	requestsByProvider := map[string][]orcapi.FilesProviderCreateUploadRequest{}
	for _, reqItem := range request.Items {
		driveId, _ := orcapi.DriveIdFromUCloudPath(reqItem.Id)
		drive, ok := drives[driveId]
		if !ok {
			return result, util.HttpErr(http.StatusNotFound, "upload requested to unknown drive")
		}

		if featureSupported(driveType, drive.Specification.Product, driveOpsReadOnly) {
			return result, util.HttpErr(http.StatusNotFound, "drive is read only")
		}

		providerId := drive.Specification.Product.Provider
		requestsByProvider[providerId] = append(requestsByProvider[providerId], orcapi.FilesProviderCreateUploadRequest{
			Id:                 reqItem.Id,
			Type:               reqItem.Type,
			SupportedProtocols: reqItem.SupportedProtocols,
			ConflictPolicy:     reqItem.ConflictPolicy,
			ResolvedCollection: drive,
		})
	}

	result.Responses = make([]orcapi.FilesCreateUploadResponse, len(request.Items))

	for provider, requests := range requestsByProvider {
		resp, err := InvokeProvider(provider, orcapi.FilesProviderCreateUpload, fndapi.BulkRequestOf(requests...),
			ProviderCallOpts{
				Username: util.OptValue(actor.Username),
				Reason:   util.OptValue("file upload"),
			})

		if err != nil {
			return result, err
		}

		if len(resp.Responses) != len(requests) {
			return result, util.HttpErr(http.StatusBadGateway, "malformed response from %s", provider)
		}

		for localIdx, respItem := range resp.Responses {
			ok := false
			for reqIdx := 0; reqIdx < len(request.Items); reqIdx++ {
				if request.Items[reqIdx].Id == requests[localIdx].Id {
					if result.Responses[reqIdx].Endpoint == "" {
						result.Responses[reqIdx] = orcapi.FilesCreateUploadResponse{
							Endpoint: respItem.Endpoint,
							Protocol: respItem.Protocol,
							Token:    respItem.Token,
						}
						ok = true
						break
					}
				}
			}

			if !ok {
				return result, util.HttpErr(http.StatusBadGateway, "malformed response from %s (2)", provider)
			}
		}
	}

	return result, nil
}

func FilesCreateDownload(
	actor rpc.Actor,
	request fndapi.BulkRequest[fndapi.FindByStringId],
) (fndapi.BulkResponse[orcapi.FilesCreateDownloadResponse], *util.HttpError) {
	var result fndapi.BulkResponse[orcapi.FilesCreateDownloadResponse]
	var paths []string
	for _, reqItem := range request.Items {
		paths = append(paths, reqItem.Id)
	}

	drives, err := filesFetchDrives(actor, paths, orcapi.PermissionRead)
	if err != nil {
		return result, err
	}

	requestsByProvider := map[string][]orcapi.FilesProviderCreateDownloadRequest{}
	for _, reqItem := range request.Items {
		driveId, _ := orcapi.DriveIdFromUCloudPath(reqItem.Id)
		drive, ok := drives[driveId]
		if !ok {
			return result, util.HttpErr(http.StatusNotFound, "download requested from unknown drive")
		}

		providerId := drive.Specification.Product.Provider
		requestsByProvider[providerId] = append(requestsByProvider[providerId], orcapi.FilesProviderCreateDownloadRequest{
			Id:                 reqItem.Id,
			ResolvedCollection: drive,
		})
	}

	result.Responses = make([]orcapi.FilesCreateDownloadResponse, len(request.Items))

	for provider, requests := range requestsByProvider {
		resp, err := InvokeProvider(provider, orcapi.FilesProviderCreateDownload, fndapi.BulkRequestOf(requests...),
			ProviderCallOpts{
				Username: util.OptValue(actor.Username),
				Reason:   util.OptValue("file upload"),
			})

		if err != nil {
			return result, err
		}

		if len(resp.Responses) != len(requests) {
			return result, util.HttpErr(http.StatusBadGateway, "malformed response from %s", provider)
		}

		for localIdx, respItem := range resp.Responses {
			ok := false
			for reqIdx := 0; reqIdx < len(request.Items); reqIdx++ {
				if request.Items[reqIdx].Id == requests[localIdx].Id {
					if result.Responses[reqIdx].Endpoint == "" {
						result.Responses[reqIdx] = orcapi.FilesCreateDownloadResponse{
							Endpoint: respItem.Endpoint,
						}
						ok = true
						break
					}
				}
			}

			if !ok {
				return result, util.HttpErr(http.StatusBadGateway, "malformed response from %s (2)", provider)
			}
		}
	}

	return result, nil
}

func FilesMove(actor rpc.Actor, request fndapi.BulkRequest[orcapi.FilesSourceAndDestination]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
	return filesCopyOrMove(actor, request, orcapi.FilesProviderMove)
}

func FilesCopy(actor rpc.Actor, request fndapi.BulkRequest[orcapi.FilesSourceAndDestination]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
	return filesCopyOrMove(actor, request, orcapi.FilesProviderCopy)
}

func filesCopyOrMove(
	actor rpc.Actor,
	request fndapi.BulkRequest[orcapi.FilesSourceAndDestination],
	call rpc.Call[fndapi.BulkRequest[orcapi.FilesProviderMoveOrCopyRequest], fndapi.BulkResponse[util.Empty]],
) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
	var result fndapi.BulkResponse[util.Empty]
	var paths []string
	for _, reqItem := range request.Items {
		paths = append(paths, reqItem.SourcePath)
		paths = append(paths, reqItem.DestinationPath)

		result.Responses = append(result.Responses, util.Empty{})
	}

	drives, err := filesFetchDrives(actor, paths, orcapi.PermissionEdit)
	if err != nil {
		return result, err
	}

	requestsByProvider := map[string][]orcapi.FilesProviderMoveOrCopyRequest{}

	for _, reqItem := range request.Items {
		sourceDriveId, _ := orcapi.DriveIdFromUCloudPath(reqItem.SourcePath)
		destinationDriveId, _ := orcapi.DriveIdFromUCloudPath(reqItem.DestinationPath)

		sourceDrive, ok1 := drives[sourceDriveId]
		destinationDrive, ok2 := drives[destinationDriveId]

		if !ok1 || !ok2 || sourceDrive.Specification.Product != destinationDrive.Specification.Product {
			return result, util.HttpErr(http.StatusBadRequest, "files cannot be moved across providers")
		}

		if featureSupported(driveType, destinationDrive.Specification.Product, driveOpsReadOnly) {
			return result, util.HttpErr(http.StatusBadRequest, "destination drive is read only")
		}

		providerId := sourceDrive.Specification.Product.Provider
		requestsByProvider[providerId] = append(requestsByProvider[providerId], orcapi.FilesProviderMoveOrCopyRequest{
			ResolvedOldCollection: sourceDrive,
			ResolvedNewCollection: destinationDrive,
			OldId:                 reqItem.SourcePath,
			NewId:                 reqItem.DestinationPath,
			ConflictPolicy:        reqItem.ConflictPolicy,
		})
	}

	for provider, requests := range requestsByProvider {
		_, err = InvokeProvider(provider, call, fndapi.BulkRequestOf(requests...), ProviderCallOpts{
			Username: util.OptValue(actor.Username),
			Reason:   util.OptValue("user initiated move/copy"),
		})

		if err != nil {
			return result, err
		}
	}

	return result, nil
}

func filesFetchDrives(actor rpc.Actor, paths []string, permission orcapi.Permission) (map[string]orcapi.Drive, *util.HttpError) {
	driveIds := map[string]util.Empty{}
	for _, path := range paths {
		driveId, ok := orcapi.DriveIdFromUCloudPath(path)
		if !ok {
			return map[string]orcapi.Drive{}, util.HttpErr(http.StatusForbidden, "forbidden")
		}

		driveIds[driveId] = util.Empty{}
	}

	result := map[string]orcapi.Drive{}
	for driveId, _ := range driveIds {
		drive, _, _, err := ResourceRetrieveEx[orcapi.Drive](actor, driveType, ResourceParseId(driveId), permission,
			orcapi.ResourceFlagsIncludeAll())
		if err != nil {
			return map[string]orcapi.Drive{}, err
		}
		result[driveId] = drive
	}

	return result, nil
}

func FilesBrowse(actor rpc.Actor, request orcapi.FilesBrowseRequest) (fndapi.PageV2[orcapi.UFile], *util.HttpError) {
	if !request.Path.Present {
		return fndapi.PageV2[orcapi.UFile]{}, util.HttpErr(http.StatusBadRequest, "bad request - no path specified")
	}

	path := request.Path.Value
	driveId, _ := orcapi.DriveIdFromUCloudPath(path)

	drive, err := ResourceRetrieve[orcapi.Drive](actor, driveType, ResourceParseId(driveId), orcapi.ResourceFlagsIncludeAll())
	if err != nil {
		return fndapi.PageV2[orcapi.UFile]{}, err
	}

	resp, err := InvokeProvider(
		drive.Specification.Product.Provider,
		orcapi.FilesProviderBrowse,
		orcapi.FilesProviderBrowseRequest{
			ResolvedCollection: drive,
			Browse: orcapi.ResourceBrowseRequest[orcapi.FileFlags]{
				ItemsPerPage: request.ItemsPerPage,
				Next:         request.Next,
				Flags:        request.FileFlags,
				// TODO sort
			},
		},
		ProviderCallOpts{
			Username: util.OptValue(actor.Username),
			Reason:   util.OptValue("browse files"),
		},
	)

	if err != nil {
		return fndapi.PageV2[orcapi.UFile]{}, err
	}

	files := fileTransform(drive, resp.Items)

	return fndapi.PageV2[orcapi.UFile]{
		Items:        files,
		Next:         resp.Next,
		ItemsPerPage: resp.ItemsPerPage,
	}, nil
}

func FilesRetrieve(actor rpc.Actor, request orcapi.FilesRetrieveRequest) (orcapi.UFile, *util.HttpError) {
	path := request.Id
	driveId, _ := orcapi.DriveIdFromUCloudPath(path)

	drive, err := ResourceRetrieve[orcapi.Drive](actor, driveType, ResourceParseId(driveId), orcapi.ResourceFlagsIncludeAll())
	if err != nil {
		return orcapi.UFile{}, err
	}

	resp, err := InvokeProvider(
		drive.Specification.Product.Provider,
		orcapi.FilesProviderRetrieve,
		orcapi.FilesProviderRetrieveRequest{
			ResolvedCollection: drive,
			Retrieve: orcapi.ResourceRetrieveRequest[orcapi.FileFlags]{
				Id:    path,
				Flags: request.FileFlags,
			},
		},
		ProviderCallOpts{
			Username: util.OptValue(actor.Username),
			Reason:   util.OptValue("Retrieve single file"),
		},
	)

	if err != nil {
		return orcapi.UFile{}, err
	}

	file := fileTransform(drive, []orcapi.ProviderFile{resp})[0]
	return file, nil
}

func fileTransform(driveInfo orcapi.Drive, files []orcapi.ProviderFile) []orcapi.UFile {
	var result []orcapi.UFile
	for _, item := range files {
		result = append(result, orcapi.UFile{
			Resource: orcapi.Resource{
				Id:          item.Id,
				CreatedAt:   item.Status.ModifiedAt,
				Owner:       driveInfo.Owner,
				Permissions: driveInfo.Permissions,
			},
			Specification: orcapi.UFileSpecification{
				Collection: driveInfo.Id,
				Product:    driveInfo.Specification.Product,
			},
			Status: item.Status,
		})
	}
	return result
}
