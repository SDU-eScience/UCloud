package orchestrator

import (
	"database/sql"
	"net/http"
	"path/filepath"
	"slices"
	"strings"

	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const shareType = "share"

func initShares() {
	InitResourceType(
		shareType,
		0,
		shareLoad,
		sharePersist,
		shareTransform,
		nil,
	)

	orcapi.SharesBrowse.Handler(func(info rpc.RequestInfo, request orcapi.SharesBrowseRequest) (fndapi.PageV2[orcapi.Share], *util.HttpError) {
		sortByFn := ResourceDefaultComparator(func(item orcapi.Share) orcapi.Resource {
			return item.Resource
		}, request.ResourceFlags)

		return ResourceBrowse[orcapi.Share](
			info.Actor,
			shareType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.Share) bool {
				if request.FilterIngoing {
					return item.Specification.SharedWith == info.Actor.Username
				} else {
					return item.Owner.CreatedBy == info.Actor.Username
				}
			},
			sortByFn,
		), nil
	})

	orcapi.SharesControlBrowse.Handler(func(info rpc.RequestInfo, request orcapi.SharesControlBrowseRequest) (fndapi.PageV2[orcapi.Share], *util.HttpError) {
		return ResourceBrowse[orcapi.Share](
			info.Actor,
			shareType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.Share) bool {
				return true
			},
			nil,
		), nil
	})

	orcapi.SharesCreate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ShareSpecification]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		actor := info.Actor

		var result []fndapi.FindByStringId
		for _, item := range request.Items {
			id, err := ShareCreate(actor, item)
			if err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			} else {
				result = append(result, fndapi.FindByStringId{Id: id})
			}
		}

		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: result}, nil
	})

	orcapi.SharesDelete.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		for _, item := range request.Items {
			err := ShareReject(info.Actor, item.Id)
			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, err
			}
		}

		return fndapi.BulkResponse[util.Empty]{Responses: make([]util.Empty, len(request.Items))}, nil
	})

	orcapi.SharesRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.SharesRetrieveRequest) (orcapi.Share, *util.HttpError) {
		return ResourceRetrieve[orcapi.Share](info.Actor, shareType, ResourceParseId(request.Id), request.ResourceFlags)
	})

	orcapi.SharesControlRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.SharesControlRetrieveRequest) (orcapi.Share, *util.HttpError) {
		return ResourceRetrieve[orcapi.Share](info.Actor, shareType, ResourceParseId(request.Id), request.ResourceFlags)
	})

	orcapi.SharesRetrieveProducts.Handler(func(info rpc.RequestInfo, request util.Empty) (orcapi.SupportByProvider[orcapi.ShareSupport], *util.HttpError) {
		return SupportRetrieveProducts[orcapi.ShareSupport](shareType), nil
	})

	orcapi.SharesControlAddUpdate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ResourceUpdateAndId[orcapi.ShareUpdate]]) (util.Empty, *util.HttpError) {
		for _, item := range request.Items {
			ok := ResourceUpdate(
				info.Actor,
				shareType,
				ResourceParseId(item.Id),
				orcapi.PermissionProvider,
				func(r *resource, mapped orcapi.Share) {
					// NOTE(Dan): Ignore new state, it is not used.

					share := r.Extra.(*internalShare)
					share.AvailableAt = item.Update.ShareAvailableAt
				},
			)

			if !ok {
				return util.Empty{}, util.HttpErr(http.StatusNotFound, "not found or permission denied (%v)", item.Id)
			}
		}
		return util.Empty{}, nil
	})

	orcapi.SharesApprove.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (util.Empty, *util.HttpError) {
		actor := info.Actor
		for _, reqItem := range request.Items {
			err := ShareApprove(actor, reqItem.Id)
			if err != nil {
				return util.Empty{}, err
			}
		}

		return util.Empty{}, nil
	})

	orcapi.SharesReject.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (util.Empty, *util.HttpError) {
		for _, reqItem := range request.Items {
			err := ShareReject(info.Actor, reqItem.Id)
			if err != nil {
				return util.Empty{}, err
			}
		}
		return util.Empty{}, nil
	})

	orcapi.SharesUpdatePermissions.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.SharesUpdatePermissionsRequest]) (util.Empty, *util.HttpError) {
		for _, reqItem := range request.Items {
			err := ShareUpdatePermissions(info.Actor, reqItem.Id, reqItem.Permissions)
			if err != nil {
				return util.Empty{}, err
			}
		}
		return util.Empty{}, nil
	})

	orcapi.SharesBrowseOutgoing.Handler(func(
		info rpc.RequestInfo,
		request orcapi.SharesBrowseOutgoingRequest,
	) (fndapi.PageV2[orcapi.ShareGroupOutgoing], *util.HttpError) {
		page := ResourceBrowse[orcapi.Share](
			info.Actor,
			shareType,
			request.Next,
			5000,
			orcapi.ResourceFlags{},
			func(item orcapi.Share) bool {
				return item.Owner.CreatedBy == info.Actor.Username
			},
			nil,
		)

		groups := map[string]orcapi.ShareGroupOutgoing{}

		for _, share := range page.Items {
			path := filepath.Clean(share.Specification.SourceFilePath)
			existingGroup, hasExisting := groups[path]
			if !hasExisting {
				existingGroup = orcapi.ShareGroupOutgoing{
					SourceFilePath: path,
					StorageProduct: share.Specification.Product,
					SharePreview:   nil,
				}
			}

			existingGroup.SharePreview = append(existingGroup.SharePreview, orcapi.SharePreview{
				SharedWith:  share.Specification.SharedWith,
				Permissions: share.Specification.Permissions,
				State:       share.Status.State,
				ShareId:     share.Id,
			})

			groups[path] = existingGroup
		}

		result := fndapi.PageV2[orcapi.ShareGroupOutgoing]{
			Next: page.Next,
		}

		for _, item := range groups {
			result.Items = append(result.Items, item)
		}

		result.ItemsPerPage = len(result.Items)

		slices.SortFunc(result.Items, func(a, b orcapi.ShareGroupOutgoing) int {
			return strings.Compare(a.SourceFilePath, b.SourceFilePath)
		})

		return result, nil
	})

	// TODO notifications and stuff
}

var shareValidPermissions = []orcapi.Permission{
	orcapi.PermissionRead,
	orcapi.PermissionEdit,
}

func ShareUpdatePermissions(actor rpc.Actor, id string, permissions []orcapi.Permission) *util.HttpError {
	if len(permissions) == 0 {
		return util.HttpErr(http.StatusBadRequest, "you must assign some permissions to the share")
	}

	for _, perm := range permissions {
		if _, ok := util.VerifyEnum(perm, shareValidPermissions); !ok {
			return util.HttpErr(http.StatusBadRequest, "invalid permission specified")
		}
	}

	driveId := ""
	isActive := false
	sharedWith := ""

	ok := ResourceUpdate(
		actor,
		shareType,
		ResourceParseId(id),
		orcapi.PermissionAdmin,
		func(r *resource, mapped orcapi.Share) {
			share := r.Extra.(*internalShare)
			share.Permissions = permissions

			isActive = share.State == orcapi.ShareStateApproved && share.AvailableAt.Present
			driveId, _ = orcapi.DriveIdFromUCloudPath(share.AvailableAt.GetOrDefault(""))
			sharedWith = share.SharedWith
		},
	)

	if !ok {
		return util.HttpErr(http.StatusForbidden, "you cannot update this share")
	} else {
		if isActive {
			err := ResourceUpdateAcl(rpc.ActorSystem, driveType, orcapi.UpdatedAcl{
				Id:      driveId,
				Deleted: []orcapi.AclEntity{orcapi.AclEntityUser(sharedWith)},
				Added: []orcapi.ResourceAclEntry{
					{
						Entity:      orcapi.AclEntityUser(sharedWith),
						Permissions: permissions,
					},
				},
			})

			if err != nil {
				return err
			} else {
				return nil
			}
		} else {
			return nil
		}
	}
}

func ShareCreate(actor rpc.Actor, item orcapi.ShareSpecification) (string, *util.HttpError) {
	if actor.Project.Present {
		return "", util.HttpErr(
			http.StatusBadRequest,
			"you cannot create a share from a project",
		)
	}

	if len(item.Permissions) == 0 {
		return "", util.HttpErr(
			http.StatusForbidden,
			"share has no associated permissions",
		)
	}

	for _, perm := range item.Permissions {
		if _, ok := util.VerifyEnum(perm, shareValidPermissions); !ok {
			return "", util.HttpErr(http.StatusBadRequest, "invalid permission specified")
		}
	}

	if item.SharedWith == actor.Username {
		return "", util.HttpErr(
			http.StatusForbidden,
			"you cannot share a file with yourself",
		)
	}

	sharedWithActor, ok := rpc.LookupActor(item.SharedWith)
	if !ok {
		return "", util.HttpErr(
			http.StatusForbidden,
			"cannot share this file with that user",
		)
	}

	driveId, ok := orcapi.DriveIdFromUCloudPath(item.SourceFilePath)
	if !ok {
		return "", util.HttpErr(
			http.StatusForbidden,
			"cannot share this file",
		)
	}

	drive, _, _, err := ResourceRetrieveEx[orcapi.Drive](actor, driveType, ResourceParseId(driveId),
		orcapi.PermissionAdmin, orcapi.ResourceFlags{})

	if err != nil {
		return "", util.HttpErr(
			http.StatusForbidden,
			"cannot share this file",
		)
	}

	if drive.Owner.Project != "" {
		return "", util.HttpErr(
			http.StatusForbidden,
			"cannot share this file belonging to a project",
		)
	}

	duplicateShares := ResourceBrowse(
		sharedWithActor,
		shareType,
		util.OptNone[string](),
		1,
		orcapi.ResourceFlags{},
		func(s orcapi.Share) bool {
			sharedWithRecipient := s.Specification.SharedWith == sharedWithActor.Username
			if sharedWithRecipient && s.Specification.SourceFilePath == filepath.Clean(item.SourceFilePath) {
				return true
			} else {
				return false
			}
		},
		nil,
	)

	if len(duplicateShares.Items) == 1 {
		return "", util.HttpErr(http.StatusConflict, "this file has already been shared with the user")
	}

	share, err := ResourceCreateThroughProvider(
		actor,
		shareType,
		item.Product,
		&internalShare{
			SharedWith:       item.SharedWith,
			Permissions:      item.Permissions,
			OriginalFilePath: filepath.Clean(item.SourceFilePath),
			State:            orcapi.ShareStatePending,
		},
		orcapi.SharesProviderCreate,
	)

	if err != nil {
		return "", err
	} else {
		err := ResourceUpdateAcl(actor, shareType, orcapi.UpdatedAcl{
			Id: share.Id,
			Added: []orcapi.ResourceAclEntry{
				{
					Entity:      orcapi.AclEntityUser(item.SharedWith),
					Permissions: []orcapi.Permission{orcapi.PermissionRead},
				},
			},
		})

		if err != nil {
			panic(err)
		}
	}

	return share.Id, nil
}

func ShareApprove(actor rpc.Actor, id string) *util.HttpError {
	share, err := ResourceRetrieve[orcapi.Share](actor, shareType, ResourceParseId(id),
		orcapi.ResourceFlags{})

	if err != nil {
		return util.HttpErr(http.StatusForbidden, "unable to accept share (%s)", err.Why)
	}

	if share.Specification.SharedWith != actor.Username {
		return util.HttpErr(http.StatusForbidden, "you cannot accept a share on someone else's behalf")
	}

	if !share.Status.ShareAvailableAt.Present {
		return util.HttpErr(http.StatusBadRequest, "unable to accept this share, try again later")
	}

	driveId, ok := orcapi.DriveIdFromUCloudPath(share.Status.ShareAvailableAt.Value)
	if !ok {
		return util.HttpErr(http.StatusBadRequest, "unable to accept this share, try again later")
	}

	err = ResourceUpdateAcl(rpc.ActorSystem, driveType, orcapi.UpdatedAcl{
		Id: driveId,
		Added: []orcapi.ResourceAclEntry{
			{
				Entity:      orcapi.AclEntityUser(share.Specification.SharedWith),
				Permissions: share.Specification.Permissions,
			},
		},
	})

	if err != nil {
		return util.HttpErr(http.StatusForbidden, "unable to accept share (%s)", err.Why)
	}

	ResourceUpdate(
		rpc.ActorSystem,
		shareType,
		ResourceParseId(id),
		orcapi.PermissionRead,
		func(r *resource, mapped orcapi.Share) {
			ishare := r.Extra.(*internalShare)
			ishare.State = orcapi.ShareStateApproved
		},
	)
	return nil
}

func ShareReject(actor rpc.Actor, id string) *util.HttpError {
	share, err := ResourceRetrieve[orcapi.Share](actor, shareType, ResourceParseId(id),
		orcapi.ResourceFlags{})

	if err != nil {
		return util.HttpErr(http.StatusForbidden, "unable to delete share (%s)", err.Why)
	}

	if share.Status.ShareAvailableAt.Present {
		driveId, ok := orcapi.DriveIdFromUCloudPath(share.Status.ShareAvailableAt.Value)
		if ok {
			ResourceDelete(rpc.ActorSystem, driveType, ResourceParseId(driveId))
		}
	}

	ResourceDelete(rpc.ActorSystem, shareType, ResourceParseId(id))
	return nil
}

type internalShare struct {
	SharedWith       string
	Permissions      []orcapi.Permission
	OriginalFilePath string
	AvailableAt      util.Option[string]
	State            orcapi.ShareState
}

func shareLoad(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource) {
	rows := db.Select[struct {
		Resource         int
		SharedWith       string
		Permissions      []string
		OriginalFilePath string
		AvailableAt      sql.Null[string]
		State            string
	}](
		tx,
		`
			select resource, shared_with, permissions, original_file_path, available_at, state
			from file_orchestrator.shares
			where resource = some(:ids::int8[])
		`,
		db.Params{
			"ids": ids,
		},
	)

	for _, row := range rows {
		result := &internalShare{
			SharedWith:       row.SharedWith,
			OriginalFilePath: row.OriginalFilePath,
			AvailableAt:      util.SqlNullToOpt(row.AvailableAt),
			State:            orcapi.ShareState(row.State),
		}

		for _, perm := range row.Permissions {
			result.Permissions = append(result.Permissions, orcapi.Permission(perm))
		}

		resources[ResourceId(row.Resource)].Extra = result
	}
}

func sharePersist(b *db.Batch, r *resource) {
	if r.MarkedForDeletion {
		db.BatchExec(
			b,
			`delete from file_orchestrator.shares where resource = :id`,
			db.Params{
				"id": r.Id,
			},
		)
	} else {
		share := r.Extra.(*internalShare)

		db.BatchExec(
			b,
			`
				insert into file_orchestrator.shares(resource, shared_with, permissions, original_file_path, available_at)
				values (:id, :shared_with, :permissions, :original_file_path, :available_at)
				on conflict (resource) do update set
					permissions = excluded.permissions,
					original_file_path = excluded.original_file_path,
					available_at = excluded.available_at
			`,
			db.Params{
				"id":                 r.Id,
				"shared_with":        share.SharedWith,
				"permissions":        share.Permissions,
				"original_file_path": share.OriginalFilePath,
				"available_at":       share.AvailableAt.Sql(),
			},
		)
	}
}

func shareTransform(
	r orcapi.Resource,
	product util.Option[accapi.ProductReference],
	extra any,
	flags orcapi.ResourceFlags,
	actor rpc.Actor,
) any {
	share := extra.(*internalShare)
	result := orcapi.Share{
		Resource: r,
		Specification: orcapi.ShareSpecification{
			SharedWith:     share.SharedWith,
			SourceFilePath: share.OriginalFilePath,
			Permissions:    share.Permissions,
			Product:        product.Value,
		},
		Status: orcapi.ShareStatus{
			State:            share.State,
			ShareAvailableAt: share.AvailableAt,
		},
	}

	return result
}
