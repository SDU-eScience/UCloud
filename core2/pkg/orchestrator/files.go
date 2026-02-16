package orchestrator

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/http"
	"path/filepath"
	"strings"
	"sync"
	"sync/atomic"

	ws "github.com/gorilla/websocket"
	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
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

	orcapi.FilesStreamingSearch.Handler(func(info rpc.RequestInfo, request util.Empty) (util.Empty, *util.HttpError) {
		FilesStreamingSearch(info.WebSocket)
		return util.Empty{}, nil
	})

	orcapi.FilesTransfer.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.FilesTransferRequest]) (util.Empty, *util.HttpError) {
		for _, reqItem := range request.Items {
			err := FilesTransfer(info.Actor, reqItem)
			if err != nil {
				return util.Empty{}, err
			}
		}

		return util.Empty{}, nil
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
			return result, util.HttpErr(http.StatusForbidden, "drive is read only")
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
			return result, util.HttpErr(http.StatusForbidden, "drive is read only")
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
			return result, util.HttpErr(http.StatusNotFound, "file deletion requested to unknown drive")
		}

		if featureSupported(driveType, drive.Specification.Product, driveOpsReadOnly) {
			return result, util.HttpErr(http.StatusForbidden, "drive is read only")
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
			return result, util.HttpErr(http.StatusForbidden, "drive is read only")
		}

		providerId := drive.Specification.Product.Provider
		requestsByProvider[providerId] = append(requestsByProvider[providerId], orcapi.FilesProviderCreateFolderRequest{
			Id:                 reqItem.Id,
			ConflictPolicy:     reqItem.ConflictPolicy,
			ResolvedCollection: drive,
		})

		if util.DevelopmentModeEnabled() && strings.Contains(reqItem.Id, "$$notify-me$$") {
			_, err := fndapi.NotificationsCreate.Invoke(fndapi.NotificationsCreateRequest{
				User: actor.Username,
				Notification: fndapi.Notification{
					Type:    "TEST_NOTIFICATION",
					Message: fmt.Sprintf("Here you go: %s", reqItem.Id),
				},
			})

			if err != nil {
				log.Info("Could not send test notification: %s", err)
			}
		}
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
			return result, util.HttpErr(http.StatusForbidden, "drive is read only")
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
	if actor.Project.Present {
		_, isRestricted := policiesByProject(string(actor.Project.Value))[fndapi.RestrictDownloads.String()]
		if isRestricted {
			return fndapi.BulkResponse[orcapi.FilesCreateDownloadResponse]{}, util.HttpErr(http.StatusForbidden, "This project does not allow downloads")
		}
	}

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
				Reason:   util.OptValue("file download"),
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
	return filesCopyOrMove(actor, request, orcapi.FilesProviderMove, true)
}

func FilesCopy(actor rpc.Actor, request fndapi.BulkRequest[orcapi.FilesSourceAndDestination]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
	return filesCopyOrMove(actor, request, orcapi.FilesProviderCopy, false)
}

func filesCopyOrMove(
	actor rpc.Actor,
	request fndapi.BulkRequest[orcapi.FilesSourceAndDestination],
	call rpc.Call[fndapi.BulkRequest[orcapi.FilesProviderMoveOrCopyRequest], fndapi.BulkResponse[util.Empty]],
	isMove bool,
) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
	var result fndapi.BulkResponse[util.Empty]
	var sourcePaths []string
	var destPaths []string
	for _, reqItem := range request.Items {
		sourcePaths = append(sourcePaths, reqItem.SourcePath)
		destPaths = append(destPaths, reqItem.DestinationPath)

		result.Responses = append(result.Responses, util.Empty{})
	}

	drives := map[string]orcapi.Drive{}
	{
		sourcePermRequired := orcapi.PermissionEdit
		if !isMove {
			sourcePermRequired = orcapi.PermissionRead
		}
		sourceDrives, err := filesFetchDrives(actor, sourcePaths, sourcePermRequired)
		if err != nil {
			return result, err
		}

		destDrives, err := filesFetchDrives(actor, destPaths, orcapi.PermissionEdit)
		if err != nil {
			return result, err
		}

		for _, drive := range sourceDrives {
			drives[drive.Id] = drive
		}

		for _, drive := range destDrives {
			drives[drive.Id] = drive
		}
	}

	requestsByProvider := map[string][]orcapi.FilesProviderMoveOrCopyRequest{}

	for _, reqItem := range request.Items {
		sourceDriveId, _ := orcapi.DriveIdFromUCloudPath(reqItem.SourcePath)
		destinationDriveId, _ := orcapi.DriveIdFromUCloudPath(reqItem.DestinationPath)

		sourceDrive, ok1 := drives[sourceDriveId]
		destinationDrive, ok2 := drives[destinationDriveId]

		if !ok1 || !ok2 || sourceDrive.Specification.Product.Provider != destinationDrive.Specification.Product.Provider {
			return result, util.HttpErr(http.StatusBadRequest, "files cannot be moved across providers")
		}

		if featureSupported(driveType, destinationDrive.Specification.Product, driveOpsReadOnly) {
			return result, util.HttpErr(http.StatusForbidden, "destination drive is read only")
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
		_, err := InvokeProvider(provider, call, fndapi.BulkRequestOf(requests...), ProviderCallOpts{
			Username: util.OptValue(actor.Username),
			Reason:   util.OptValue("user initiated move/copy"),
		})

		if err != nil {
			return result, err
		} else {
			for _, reqItem := range requests {
				MetadataMigrateToNewPath(reqItem.OldId, reqItem.NewId)
			}
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
				ItemsPerPage:  request.ItemsPerPage,
				Next:          request.Next,
				Flags:         request.FileFlags,
				SortBy:        request.SortBy,
				SortDirection: request.SortDirection,
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

	files := fileTransform(actor, drive, resp.Items)

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

	file := fileTransform(actor, drive, []orcapi.ProviderFile{resp})[0]
	return file, nil
}

func fileTransform(actor rpc.Actor, driveInfo orcapi.Drive, files []orcapi.ProviderFile) []orcapi.UFile {
	var result []orcapi.UFile

	metadataByParent := map[string]map[string]MetadataDocument{}
	sensitivityByAncestor := map[string]util.Option[SensitivityLevel]{}

	for _, item := range files {
		cleanPath := filepath.Clean(item.Id)

		inheritedSensitivity := util.OptNone[SensitivityLevel]()
		ancestor := filepath.Dir(cleanPath)
		for ancestor != "" && ancestor != "/" {
			sensitivity, ok := sensitivityByAncestor[ancestor]
			if !ok {
				metadata, _ := MetadataRetrieveAtPath(actor, ancestor)
				sensitivity = metadata.Sensitivity
				sensitivityByAncestor[ancestor] = sensitivity
			}

			if sensitivity.Present && !inheritedSensitivity.Present {
				inheritedSensitivity.Set(sensitivity.Value)
			}

			prev := ancestor
			ancestor = filepath.Dir(ancestor)
			if ancestor == prev { // sanity check
				break
			}
		}

		parent := filepath.Dir(cleanPath)
		metadataInFolder, ok := metadataByParent[parent]
		if !ok {
			metadataInFolder, ok = MetadataBrowseFolder(actor, parent)
			metadataByParent[parent] = metadataInFolder
		}

		myMetadata, hasMetadata := metadataInFolder[cleanPath]

		file := orcapi.UFile{
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
		}

		file.Status.Metadata = orcapi.FileMetadata{Metadata: map[string][]orcapi.FileMetadataDocument{}}

		if !myMetadata.Sensitivity.Present && inheritedSensitivity.Present {
			myMetadata.Sensitivity = inheritedSensitivity

			if !hasMetadata {
				myMetadata.Path = cleanPath
				hasMetadata = true
			}
		}

		if hasMetadata {
			metaMap := file.Status.Metadata.Metadata
			docItems := MetadataDocToApi(actor, myMetadata, MetadataIncludeSensitivity|MetadataIncludeFavorite)
			for _, docItem := range docItems {
				metaMap[docItem.Metadata.Specification.TemplateId] = []orcapi.FileMetadataDocument{docItem.Metadata}
			}
		}

		result = append(result, file)
	}
	return result
}

func FilesStreamingSearch(conn *ws.Conn) {
	defer util.SilentClose(conn)

	var (
		actor        rpc.Actor
		streamId     string
		initialDrive orcapi.Drive
		folder       string
		query        string
		flags        orcapi.FileFlags
	)

	connMutex := sync.Mutex{}
	sendToClient := func(data orcapi.FilesStreamingSearchResult) bool {
		data.Batch = util.NonNilSlice(data.Batch)
		dataBytes := rpc.WSResponseMessageMarshal(streamId, data)

		connMutex.Lock()
		err := conn.WriteMessage(ws.TextMessage, dataBytes)
		connMutex.Unlock()

		return err == nil
	}

	{
		// Read and authenticate request
		// -------------------------------------------------------------------------------------------------------------

		var herr *util.HttpError

		mtype, rawMessage, err := conn.ReadMessage()
		if err != nil || mtype != ws.TextMessage {
			return
		}

		var message rpc.WSRequestMessage[orcapi.FilesStreamingSearchRequest]

		err = json.Unmarshal(rawMessage, &message)
		if err != nil {
			return
		}

		streamId = message.StreamId

		actor, herr = rpc.BearerAuthenticator(message.Bearer, message.Project.GetOrDefault(""))
		if herr != nil {
			return
		}

		driveId, ok := orcapi.DriveIdFromUCloudPath(message.Payload.CurrentFolder)
		if !ok {
			return
		}

		initialDrive, herr = ResourceRetrieve[orcapi.Drive](actor, driveType, ResourceParseId(driveId),
			orcapi.ResourceFlags{})
		if herr != nil {
			return
		}

		if !featureSupported(driveType, initialDrive.Specification.Product, driveOpsStreamingSearch) {
			return
		}

		util.ValidateString(&message.Payload.Query, "query", 0, &herr)
		if herr != nil {
			return
		}

		query = message.Payload.Query

		flags = message.Payload.Flags
		folder = message.Payload.CurrentFolder
	}

	providerId := initialDrive.Specification.Product.Provider
	client, ok := providerClient(providerId)
	if !ok {
		return
	}

	wg := sync.WaitGroup{}
	wg.Add(1)
	keepRunning := atomic.Bool{}
	keepRunning.Store(true)

	go func() {
		defer func() {
			wg.Done()
			keepRunning.Store(false)
		}()

		url := strings.ReplaceAll(client.BasePath, "http://", "ws://")
		url = strings.ReplaceAll(url, "https://", "wss://")
		url = fmt.Sprintf(
			"%s%s?usernameHint=%s",
			url,
			orcapi.FilesProviderStreamingSearchEndpoint(providerId),
			base64.URLEncoding.EncodeToString([]byte(actor.Username)),
		)

		providerConn, _, err := ws.DefaultDialer.Dial(url, http.Header{
			"Authorization": []string{fmt.Sprintf("Bearer %s", client.RetrieveAccessTokenOrRefresh())},
		})

		if err == nil {
			dataBytes, _ := json.Marshal(rpc.WSRequestMessage[orcapi.FilesProviderStreamingSearchRequest]{
				Call:     fmt.Sprintf("files.provider.%s.streamingSearch", providerId),
				StreamId: "ignored",
				Payload: orcapi.FilesProviderStreamingSearchRequest{
					Query: query,
					Owner: initialDrive.Owner,
					Flags: flags,
					Category: accapi.ProductCategoryIdV2{
						Name:     initialDrive.Specification.Product.Category,
						Provider: initialDrive.Specification.Product.Provider,
					},
					CurrentFolder: util.OptValue(folder),
				},
			})

			err = providerConn.WriteMessage(ws.TextMessage, dataBytes)
		}

		if err == nil {
			for keepRunning.Load() {
				mtype, rawMessage, err := providerConn.ReadMessage()
				if err != nil || mtype != ws.TextMessage {
					break
				}

				var message rpc.WSResponseMessage[orcapi.FilesProviderStreamingSearchResult]
				_ = json.Unmarshal(rawMessage, &message)

				transformedBatch := fileTransform(actor, initialDrive, message.Payload.Batch)

				ok = sendToClient(orcapi.FilesStreamingSearchResult{
					Type:  message.Payload.Type,
					Batch: transformedBatch,
				})

				if !ok {
					break
				}
			}
		}

		util.SilentClose(providerConn)
	}()

	wg.Wait()
}

func FilesTransfer(actor rpc.Actor, request orcapi.FilesTransferRequest) *util.HttpError {
	sourcePath := filepath.Clean(request.SourcePath)
	destPath := filepath.Clean(request.DestinationPath)
	sourceDriveId, ok1 := orcapi.DriveIdFromUCloudPath(sourcePath)
	destDriveId, ok2 := orcapi.DriveIdFromUCloudPath(destPath)

	if !ok1 || !ok2 {
		return util.HttpErr(http.StatusForbidden, "cannot transfer between these paths")
	}

	sourceDrive, _, _, err1 := ResourceRetrieveEx[orcapi.Drive](actor, driveType, ResourceParseId(sourceDriveId),
		orcapi.PermissionRead, orcapi.ResourceFlags{})
	destDrive, _, _, err2 := ResourceRetrieveEx[orcapi.Drive](actor, driveType, ResourceParseId(destDriveId),
		orcapi.PermissionEdit, orcapi.ResourceFlags{})

	if err1 != nil || err2 != nil {
		return util.MergeHttpErr(err1, err2)
	}

	if featureSupported(driveType, destDrive.Specification.Product, driveOpsReadOnly) {
		return util.HttpErr(http.StatusForbidden, "cannot transfer between these paths (destination is read-only)")
	}

	sourceProvider := sourceDrive.Specification.Product.Provider
	destProvider := destDrive.Specification.Product.Provider

	if !util.DevelopmentModeEnabled() && sourceProvider == destProvider {
		return util.HttpErr(http.StatusForbidden, "cannot transfer between these paths (same provider)")
	}

	callOpts := ProviderCallOpts{
		Username: util.OptValue(actor.Username),
		Reason:   util.OptValue("user-initiated file-transfer"),
	}

	initiateSrcResp, err := InvokeProvider(sourceProvider, orcapi.FilesProviderTransfer, orcapi.FilesProviderTransferRequest{
		Type: orcapi.FilesProviderTransferReqTypeInitiateSource,
		FilesProviderTransferRequestInitiateSource: orcapi.FilesProviderTransferRequestInitiateSource{
			SourcePath:          request.SourcePath,
			SourceDrive:         sourceDrive,
			DestinationProvider: destProvider,
		},
	}, callOpts)

	if err != nil || initiateSrcResp.Type != orcapi.FilesProviderTransferReqTypeInitiateSource {
		return util.HttpErr(http.StatusBadGateway, "source provider is unable to fulfill this request")
	}

	srcResp := initiateSrcResp.FilesProviderTransferResponseInitiateSource
	if len(srcResp.SupportedProtocols) == 0 {
		return util.HttpErr(http.StatusForbidden, "source provider is unwilling to fulfill this request")
	}

	if srcResp.Session == "" {
		return util.HttpErr(http.StatusForbidden, "source provider is unwilling to fulfill this request")
	}

	initiateDestResp, err := InvokeProvider(destProvider, orcapi.FilesProviderTransfer, orcapi.FilesProviderTransferRequest{
		Type: orcapi.FilesProviderTransferReqTypeInitiateDestination,
		FilesProviderTransferRequestInitiateDestination: orcapi.FilesProviderTransferRequestInitiateDestination{
			DestinationPath:    destPath,
			DestinationDrive:   destDrive,
			SourceProvider:     sourceProvider,
			SupportedProtocols: srcResp.SupportedProtocols,
		},
	}, callOpts)

	if err != nil || initiateDestResp.Type != orcapi.FilesProviderTransferReqTypeInitiateDestination {
		return util.HttpErr(http.StatusBadGateway, "destination provider is unable to fulfill this request")
	}

	destResp := initiateDestResp.FilesProviderTransferResponseInitiateDestination
	found := false
	for _, supported := range srcResp.SupportedProtocols {
		if supported == destResp.SelectedProtocol {
			found = true
			break
		}
	}

	if !found {
		return util.HttpErr(http.StatusBadGateway, "destination provider is unwilling to fulfill this request")
	}

	_, err = InvokeProvider(sourceProvider, orcapi.FilesProviderTransfer, orcapi.FilesProviderTransferRequest{
		Type: orcapi.FilesProviderTransferReqTypeStart,
		FilesProviderTransferRequestStart: orcapi.FilesProviderTransferRequestStart{
			Session:            srcResp.Session,
			SelectedProtocol:   destResp.SelectedProtocol,
			ProtocolParameters: destResp.ProtocolParameters,
		},
	}, callOpts)

	if err != nil {
		return err
	}

	return nil
}
