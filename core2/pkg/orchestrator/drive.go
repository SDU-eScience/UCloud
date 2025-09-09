package orchestrator

import (
	"fmt"
	"net/http"
	"strings"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const (
	driveType = "file_collection"
)

func initDrives() {
	InitResourceType(
		driveType,
		0,
		driveLoad,
		drivePersist,
		driveTransform,
	)

	orcapi.DrivesBrowse.Handler(func(info rpc.RequestInfo, request orcapi.DrivesBrowseRequest) (fndapi.PageV2[orcapi.Drive], *util.HttpError) {
		return ResourceBrowse(
			info.Actor,
			driveType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.Drive) bool {
				return true
			},
		), nil
	})

	orcapi.DrivesRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.DrivesRetrieveRequest) (orcapi.Drive, *util.HttpError) {
		return ResourceRetrieve[orcapi.Drive](info.Actor, driveType, ResourceParseId(request.Id), request.ResourceFlags)
	})

	orcapi.DrivesCreate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.DriveSpecification]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		var responses []fndapi.FindByStringId
		for _, reqItem := range request.Items {
			d, err := DriveCreate(info.Actor, reqItem)
			if err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			} else {
				responses = append(responses, fndapi.FindByStringId{Id: d.Id})
			}
		}
		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: responses}, nil
	})

	orcapi.DrivesRename.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.DriveRenameRequest]) (util.Empty, *util.HttpError) {
		for _, reqItem := range request.Items {
			err := DriveRename(info.Actor, reqItem.Id, reqItem.NewTitle)
			if err != nil {
				return util.Empty{}, err
			}
		}

		return util.Empty{}, nil
	})

	orcapi.DrivesSearch.Handler(func(info rpc.RequestInfo, request orcapi.DrivesSearchRequest) (fndapi.PageV2[orcapi.Drive], *util.HttpError) {
		// TODO Sorting through the items would be ideal

		items := ResourceBrowse[orcapi.Drive](info.Actor, driveType, request.Next, request.ItemsPerPage, request.ResourceFlags, func(item orcapi.Drive) bool {
			return strings.Contains(strings.ToLower(item.Specification.Title), strings.ToLower(request.Query))
		})

		return items, nil
	})

	orcapi.DrivesRetrieveProducts.Handler(func(info rpc.RequestInfo, request util.Empty) (orcapi.SupportByProvider[orcapi.FSSupport], *util.HttpError) {
		return SupportRetrieveProducts[orcapi.FSSupport](driveType), nil
	})

	orcapi.DrivesUpdateAcl.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.UpdatedAcl]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		var responses []util.Empty
		for _, item := range request.Items {
			err := ResourceUpdateAcl(info.Actor, driveType, item)
			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, err
			}
			responses = append(responses, util.Empty{})
		}
		return fndapi.BulkResponse[util.Empty]{Responses: responses}, nil
	})

	orcapi.DrivesDelete.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		var responses []util.Empty
		for _, item := range request.Items {
			err := ResourceDeleteThroughProvider(info.Actor, driveType, item.Id, orcapi.DrivesProviderDelete)
			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, err
			}
			responses = append(responses, util.Empty{})
		}
		return fndapi.BulkResponse[util.Empty]{Responses: responses}, nil
	})

	orcapi.DrivesControlBrowse.Handler(func(info rpc.RequestInfo, request orcapi.DrivesControlBrowseRequest) (fndapi.PageV2[orcapi.Drive], *util.HttpError) {
		return ResourceBrowse(
			info.Actor,
			driveType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.Drive) bool {
				return true
			},
		), nil
	})

	orcapi.DrivesControlRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.DrivesControlRetrieveRequest) (orcapi.Drive, *util.HttpError) {
		return ResourceRetrieve[orcapi.Drive](info.Actor, driveType, ResourceParseId(request.Id), request.ResourceFlags)
	})

	orcapi.DrivesControlRegister.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ProviderRegisteredResource[orcapi.DriveSpecification]]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		var responses []fndapi.FindByStringId

		providerId, _ := strings.CutPrefix(info.Actor.Username, fndapi.ProviderSubjectPrefix)
		for _, reqItem := range request.Items {
			if reqItem.Spec.Product.Provider != providerId {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(http.StatusForbidden, "forbidden")
			}
		}

		for _, reqItem := range request.Items {
			var flags resourceCreateFlags
			if reqItem.ProjectAllRead {
				flags |= resourceCreateAllRead
			}

			if reqItem.ProjectAllWrite {
				flags |= resourceCreateAllWrite
			}

			id, _, err := ResourceCreateEx[orcapi.Drive](
				driveType,
				orcapi.ResourceOwner{
					CreatedBy: reqItem.CreatedBy.GetOrDefault("_ucloud"),
					Project:   reqItem.Project.Value,
				},
				nil,
				util.OptValue(reqItem.Spec.Product),
				reqItem.ProviderGeneratedId,
				&driveInfo{
					Title: reqItem.Spec.Title,
				},
				flags,
			)

			if err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			} else {
				responses = append(responses, fndapi.FindByStringId{Id: fmt.Sprint(id)})
			}
		}

		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: responses}, nil
	})
}

func DriveRename(actor rpc.Actor, id string, title string) *util.HttpError {
	title = strings.TrimSpace(title)
	if title == "" || strings.Contains("\n", title) {
		return util.HttpErr(http.StatusBadRequest, "invalid title specified")
	}

	resc, _, _, err := ResourceRetrieveEx[orcapi.Drive](actor, driveType, ResourceParseId(id), orcapi.PermissionEdit, orcapi.ResourceFlags{})
	if err != nil {
		return err
	}

	p := resc.Specification.Product

	if !featureSupported(driveType, p, driveManagement) {
		return featureNotSupportedError
	}

	_, err = InvokeProvider(
		p.Provider,
		orcapi.DrivesProviderRename,
		fndapi.BulkRequestOf(orcapi.DriveRenameRequest{
			Id:       id,
			NewTitle: title,
		}),
		ProviderCallOpts{
			Username: util.OptValue(actor.Username),
		},
	)

	if err != nil {
		return err
	}

	ResourceUpdate(actor, driveType, ResourceParseId(id), orcapi.PermissionEdit, func(r *resource, mapped orcapi.Drive) {
		r.Extra.(*driveInfo).Title = title
	})
	return nil
}

func DriveCreate(actor rpc.Actor, item orcapi.DriveSpecification) (orcapi.Drive, *util.HttpError) {
	p := item.Product
	if !featureSupported(driveType, p, driveManagement) {
		return orcapi.Drive{}, featureNotSupportedError
	}

	info := &driveInfo{Title: item.Title}
	return ResourceCreateThroughProvider(actor, driveType, p, info, orcapi.DrivesProviderCreate)
}

type driveInfo struct {
	Title string
}

func driveLoad(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource) {
	rows := db.Select[struct {
		Resource int64
		Title    string
	}](
		tx,
		`
			select resource, title
			from file_orchestrator.file_collections d
			where
				d.resource = some(cast(:ids as int8[]))
	    `,
		db.Params{
			"ids": ids,
		},
	)

	for _, row := range rows {
		resources[ResourceId(row.Resource)].Extra = &driveInfo{Title: row.Title}
	}
}

func driveTransform(r orcapi.Resource, product util.Option[accapi.ProductReference], extra any, flags orcapi.ResourceFlags) any {
	info := extra.(*driveInfo)

	result := orcapi.Drive{
		Resource: r,
		Specification: orcapi.DriveSpecification{
			Title:   info.Title,
			Product: product.Value,
		},
		Updates: make([]orcapi.ResourceUpdate, 0),
	}

	if flags.IncludeProduct || flags.IncludeSupport {
		support, _ := SupportByProduct[orcapi.FSSupport](driveType, product.Value)
		result.Status = orcapi.ResourceStatus[orcapi.FSSupport]{
			ResolvedSupport: util.OptValue(support.ToApi()),
			ResolvedProduct: util.OptValue(support.Product),
		}
	}

	return result
}

func drivePersist(b *db.Batch, resource *resource) {
	if resource.MarkedForDeletion {
		db.BatchExec(
			b,
			`
				delete from file_orchestrator.file_collections
				where resource = :resource
		    `,
			db.Params{
				"resource": resource.Id,
			},
		)
	} else {
		db.BatchExec(
			b,
			`
				insert into file_orchestrator.file_collections(resource, title)
				values (:resource, :title)
				on conflict (resource) do update set title = excluded.title
		    `,
			db.Params{
				"resource": resource.Id,
				"title":    resource.Extra.(*driveInfo).Title,
			},
		)
	}
}
