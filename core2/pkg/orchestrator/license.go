package orchestrator

import (
	"fmt"
	"net/http"
	"strconv"
	"strings"

	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
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
		sortByFn := ResourceDefaultComparator(func(item orcapi.License) orcapi.Resource {
			return item.Resource
		}, request.ResourceFlags)

		return ResourceBrowse[orcapi.License](
			info.Actor,
			licenseType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.License) bool {
				return true
			},
			sortByFn,
		), nil
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
		// TODO Check if we have an allocation?
		var result []fndapi.FindByStringId
		for _, item := range request.Items {
			license, err := ResourceCreateThroughProvider(
				info.Actor,
				licenseType,
				item.Product,
				&internalLicense{},
				orcapi.LicensesProviderCreate,
			)

			if err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			}

			result = append(result, fndapi.FindByStringId{Id: license.Id})
		}

		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: result}, nil
	})

	orcapi.LicensesDelete.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		for _, item := range request.Items {
			err := ResourceDeleteThroughProvider(info.Actor, licenseType, item.Id, orcapi.LicensesProviderDelete)
			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, err
			}
		}

		return fndapi.BulkResponse[util.Empty]{Responses: make([]util.Empty, len(request.Items))}, nil
	})

	orcapi.LicensesSearch.Handler(func(info rpc.RequestInfo, request orcapi.LicensesSearchRequest) (fndapi.PageV2[orcapi.License], *util.HttpError) {
		return ResourceBrowse[orcapi.License](
			info.Actor,
			licenseType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.License) bool {
				// TODO Something
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
					Project:   reqItem.Project.Value,
				},
				nil,
				util.OptValue(reqItem.Spec.Product),
				reqItem.ProviderGeneratedId,
				&internalLicense{},
				flags,
			)

			ResourceConfirm(licenseType, id)

			if err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			} else {
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
	product util.Option[accapi.ProductReference],
	extra any,
	flags orcapi.ResourceFlags,
	actor rpc.Actor,
) any {
	license := extra.(*internalLicense)
	result := orcapi.License{
		Resource: r,
		Specification: orcapi.LicenseSpecification{
			Product: product.Value,
		},
		Status: orcapi.LicenseStatus{
			State:   "READY",
			BoundTo: license.BoundTo,
		},
	}

	if flags.IncludeSupport || flags.IncludeProduct {
		supp, _ := SupportByProduct[orcapi.LicenseSupport](licenseType, product.Value)

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
