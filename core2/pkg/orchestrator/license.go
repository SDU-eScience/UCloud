package orchestrator

import (
	"fmt"
	"net/http"
	"strconv"
	"strings"

	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const licenseType = "license"

func initLicenses() {
	InitResourceType(
		licenseType,
		resourceTypeCreateWithoutAdmin,
		licenseLoad,
		licensePersist,
		licenseTransform,
		nil,
	)

	orcapi.LicensesBrowse.Handler(func(info rpc.RequestInfo, request orcapi.LicensesBrowseRequest) (fndapi.PageV2[orcapi.License], *util.HttpError) {
		return LicenseBrowse(info.Actor, request), nil
	})

	orcapi.LicensesControlBrowse.Handler(func(info rpc.RequestInfo, request orcapi.LicensesControlBrowseRequest) (fndapi.PageV2[orcapi.License], *util.HttpError) {
		return ResourceBrowse[orcapi.License](
			info.Actor,
			licenseType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.License) bool {
				return true
			},
			nil,
		), nil
	})

	orcapi.LicensesCreate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.LicenseSpecification]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		created, err := LicenseCreate(info.Actor, request)
		if err != nil {
			return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
		}

		result := make([]fndapi.FindByStringId, 0, len(created))
		for _, license := range created {
			result = append(result, fndapi.FindByStringId{Id: license.Id})
		}

		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: result}, nil
	})

	orcapi.LicensesDelete.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		return LicenseDelete(info.Actor, request)
	})

	orcapi.LicensesSearch.Handler(func(info rpc.RequestInfo, request orcapi.LicensesSearchRequest) (fndapi.PageV2[orcapi.License], *util.HttpError) {
		return ResourceBrowse[orcapi.License](
			info.Actor,
			licenseType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.License) bool {
				return true
			},
			nil,
		), nil
	})

	orcapi.LicensesRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.LicensesRetrieveRequest) (orcapi.License, *util.HttpError) {
		return ResourceRetrieve[orcapi.License](info.Actor, licenseType, ResourceParseId(request.Id), request.ResourceFlags)
	})

	orcapi.LicensesControlRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.LicensesControlRetrieveRequest) (orcapi.License, *util.HttpError) {
		return ResourceRetrieve[orcapi.License](info.Actor, licenseType, ResourceParseId(request.Id), request.ResourceFlags)
	})

	orcapi.LicensesUpdateAcl.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.UpdatedAcl]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		for _, item := range request.Items {
			err := ResourceUpdateAcl(info.Actor, licenseType, item)
			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, err
			}
		}

		return fndapi.BulkResponse[util.Empty]{Responses: make([]util.Empty, len(request.Items))}, nil
	})

	orcapi.LicensesUpdateLabels.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.LicensesUpdateLabelsRequest]) (util.Empty, *util.HttpError) {
		return util.Empty{}, LicenseUpdateLabels(info.Actor, request)
	})

	orcapi.LicensesRetrieveProducts.Handler(func(info rpc.RequestInfo, request util.Empty) (orcapi.SupportByProvider[orcapi.LicenseSupport], *util.HttpError) {
		return SupportRetrieveProducts[orcapi.LicenseSupport](licenseType), nil
	})

	orcapi.LicensesControlRegister.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ProviderRegisteredResource[orcapi.LicenseSpecification]]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
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

			id, _, err := ResourceCreateEx[orcapi.License](
				licenseType,
				orcapi.ResourceOwner{
					CreatedBy: reqItem.CreatedBy.GetOrDefault("_ucloud"),
					Project:   reqItem.Project,
				},
				nil,
				reqItem.Spec.ResourceSpecification,
				reqItem.ProviderGeneratedId,
				&internalLicense{},
				flags,
			)

			if err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			} else {
				ResourceConfirm(licenseType, id)
				responses = append(responses, fndapi.FindByStringId{Id: fmt.Sprint(id)})
			}
		}

		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: responses}, nil
	})

	orcapi.LicensesControlAddUpdate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ResourceUpdateAndId[orcapi.LicenseUpdate]]) (util.Empty, *util.HttpError) {
		for _, item := range request.Items {
			ok := ResourceUpdate(
				info.Actor,
				licenseType,
				ResourceParseId(item.Id),
				orcapi.PermissionProvider,
				func(r *resource, mapped orcapi.License) {
					// Do nothing
				},
			)

			if !ok {
				return util.Empty{}, util.HttpErr(http.StatusNotFound, "not found or permission denied (%v)", item.Id)
			}
		}
		return util.Empty{}, nil
	})

	orcapi.LicensesControlUpdateLabels.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.LicensesUpdateLabelsRequest]) (util.Empty, *util.HttpError) {
		for _, reqItem := range request.Items {
			err := ResourceUpdateLabels(info.Actor, licenseType, reqItem.Id, reqItem.Labels, orcapi.PermissionProvider)
			if err != nil {
				return util.Empty{}, err
			}
		}

		return util.Empty{}, nil
	})
}

func LicenseCreate(actor rpc.Actor, request fndapi.BulkRequest[orcapi.LicenseSpecification]) ([]orcapi.License, *util.HttpError) {
	var created []orcapi.License
	for _, item := range request.Items {
		license, err := ResourceCreateThroughProvider(
			actor,
			licenseType,
			item.ResourceSpecification,
			&internalLicense{},
			orcapi.LicensesProviderCreate,
		)

		if err != nil {
			return nil, err
		}

		created = append(created, license)
	}

	return created, nil
}

func LicenseBrowse(actor rpc.Actor, request orcapi.LicensesBrowseRequest) fndapi.PageV2[orcapi.License] {
	sortByFn := ResourceDefaultComparator(func(item orcapi.License) orcapi.Resource {
		return item.Resource
	}, request.ResourceFlags)

	return ResourceBrowse[orcapi.License](
		actor,
		licenseType,
		request.Next,
		request.ItemsPerPage,
		request.ResourceFlags,
		func(item orcapi.License) bool {
			return true
		},
		sortByFn,
	)
}

func LicenseDelete(actor rpc.Actor, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
	for _, item := range request.Items {
		err := ResourceDeleteThroughProvider(actor, licenseType, item.Id, orcapi.LicensesProviderDelete)
		if err != nil {
			return fndapi.BulkResponse[util.Empty]{}, err
		}
	}

	return fndapi.BulkResponse[util.Empty]{Responses: make([]util.Empty, len(request.Items))}, nil
}

func LicenseUpdateLabels(actor rpc.Actor, request fndapi.BulkRequest[orcapi.LicensesUpdateLabelsRequest]) *util.HttpError {
	for _, reqItem := range request.Items {
		err := ResourceUpdateLabelsThroughProvider[orcapi.License](
			actor,
			licenseType,
			reqItem.Id,
			reqItem.Labels,
			func(t *orcapi.License, labels map[string]string) {
				t.Specification.Labels = labels
			},
			orcapi.LicensesProviderOnUpdatedLabels,
		)

		if err != nil {
			return err
		}
	}

	return nil
}

type internalLicense struct {
	BoundTo []string
}

func licenseLoad(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource) {
	rows := db.Select[struct {
		Resource      int
		StatusBoundTo []int
	}](
		tx,
		`select resource, status_bound_to from app_orchestrator.licenses where resource = some(:ids::int8[])`,
		db.Params{
			"ids": ids,
		},
	)

	for _, row := range rows {
		var boundTo []string
		for _, jobId := range row.StatusBoundTo {
			boundTo = append(boundTo, fmt.Sprint(jobId))
		}

		result := &internalLicense{
			BoundTo: boundTo,
		}

		resources[ResourceId(row.Resource)].Extra = result
	}
}

func licensePersist(b *db.Batch, r *resource) {
	if r.MarkedForDeletion {
		db.BatchExec(
			b,
			`delete from app_orchestrator.licenses where resource = :id`,
			db.Params{
				"id": r.Id,
			},
		)
	} else {
		license := r.Extra.(*internalLicense)
		boundTo := []int64{}
		for _, jobId := range license.BoundTo {
			id, _ := strconv.ParseInt(jobId, 10, 64)
			boundTo = append(boundTo, id)
		}

		db.BatchExec(
			b,
			`
				insert into app_orchestrator.licenses(current_state, resource, status_bound_to) 
				values ('READY', :id, :bound_to)
				on conflict (resource) do update set
					status_bound_to = excluded.status_bound_to
			`,
			db.Params{
				"id":       r.Id,
				"bound_to": boundTo,
			},
		)
	}
}

func licenseTransform(
	r orcapi.Resource,
	specification orcapi.ResourceSpecification,
	extra any,
	flags orcapi.ResourceFlags,
	actor rpc.Actor,
) any {
	license := extra.(*internalLicense)
	result := orcapi.License{
		Resource: r,
		Specification: orcapi.LicenseSpecification{
			ResourceSpecification: specification,
		},
		Status: orcapi.LicenseStatus{
			State:   "READY",
			BoundTo: license.BoundTo,
		},
	}

	if (flags.IncludeSupport || flags.IncludeProduct) && resourceSpecificationHasProduct(specification) {
		supp, _ := SupportByProduct[orcapi.LicenseSupport](licenseType, specification.Product)

		if flags.IncludeProduct {
			result.Status.ResolvedProduct.Set(supp.Product)
		}
		if flags.IncludeSupport {
			result.Status.ResolvedSupport.Set(supp.ToApi())
		}
	}

	return result
}

func LicenseBind(id string, jobId string) {
	ResourceUpdate[orcapi.License](
		rpc.ActorSystem,
		licenseType,
		ResourceParseId(id),
		orcapi.PermissionRead,
		func(r *resource, mapped orcapi.License) {
			ip := r.Extra.(*internalLicense)
			ip.BoundTo = []string{jobId}
		},
	)
}

func LicenseUnbind(id string, jobId string) {
	ResourceUpdate[orcapi.License](
		rpc.ActorSystem,
		licenseType,
		ResourceParseId(id),
		orcapi.PermissionRead,
		func(r *resource, mapped orcapi.License) {
			ip := r.Extra.(*internalLicense)
			ip.BoundTo = util.RemoveFirst(ip.BoundTo, jobId)
		},
	)
}
