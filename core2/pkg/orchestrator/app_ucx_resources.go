package orchestrator

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"net/http"
	"slices"
	"strings"

	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"
)

func appUcxResourceHandlers(state *appUcxSessionState, proxy *ucx.Proxy) {
	appUcxCreateResource[orcapi.PrivateNetworkSpecification, orcapi.PrivateNetwork](
		state,
		proxy,
		ucxsvc.PrivateNetworksCreate,
		func(actor rpc.Actor, specs []orcapi.PrivateNetworkSpecification) ([]orcapi.PrivateNetwork, *util.HttpError) {
			return PrivateNetworkCreate(actor, fndapi.BulkRequestOf(specs...))
		},
		func(r orcapi.PrivateNetwork) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxDeleteResource[orcapi.PrivateNetwork](
		state,
		proxy,
		ucxsvc.PrivateNetworksDelete,
		func(actor rpc.Actor, id string) (orcapi.PrivateNetwork, *util.HttpError) {
			return PrivateNetworkRetrieve(actor, orcapi.PrivateNetworksRetrieveRequest{Id: id})
		},
		func(actor rpc.Actor, id string) *util.HttpError {
			return PrivateNetworkDelete(actor, fndapi.BulkRequestOf(fndapi.FindByStringId{Id: id}))
		},
		func(r orcapi.PrivateNetwork) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxBrowseResource[orcapi.PrivateNetworksBrowseRequest, orcapi.PrivateNetwork](
		state,
		proxy,
		ucxsvc.PrivateNetworksBrowse,
		func(req orcapi.PrivateNetworksBrowseRequest) util.Option[string] { return req.Next },
		func(req *orcapi.PrivateNetworksBrowseRequest, next util.Option[string]) { req.Next = next },
		func(req orcapi.PrivateNetworksBrowseRequest) int { return req.ItemsPerPage },
		func(req *orcapi.PrivateNetworksBrowseRequest, itemsPerPage int) { req.ItemsPerPage = itemsPerPage },
		func(req *orcapi.PrivateNetworksBrowseRequest) *orcapi.ResourceFlags {
			return &req.PrivateNetworkFlags.ResourceFlags
		},
		func(actor rpc.Actor, request orcapi.PrivateNetworksBrowseRequest) (fndapi.PageV2[orcapi.PrivateNetwork], *util.HttpError) {
			return PrivateNetworkBrowse(actor, request), nil
		},
	)

	appUcxRetrieveResource[orcapi.PrivateNetworksRetrieveRequest, orcapi.PrivateNetwork](
		state,
		proxy,
		ucxsvc.PrivateNetworksRetrieve,
		func(request orcapi.PrivateNetworksRetrieveRequest) string {
			return request.Id
		},
		func(actor rpc.Actor, id string) (orcapi.PrivateNetwork, *util.HttpError) {
			return PrivateNetworkRetrieve(actor, orcapi.PrivateNetworksRetrieveRequest{Id: id})
		},
		func(r orcapi.PrivateNetwork) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxUpdateLabelsResource[orcapi.PrivateNetworksUpdateLabelsRequest, orcapi.PrivateNetwork](
		state,
		proxy,
		ucxsvc.PrivateNetworksUpdateLabels,
		func(request orcapi.PrivateNetworksUpdateLabelsRequest) string {
			return request.Id
		},
		func(actor rpc.Actor, id string) (orcapi.PrivateNetwork, *util.HttpError) {
			return PrivateNetworkRetrieve(actor, orcapi.PrivateNetworksRetrieveRequest{Id: id})
		},
		PrivateNetworkUpdateLabels,
		func(r orcapi.PrivateNetwork) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxRetrieveProducts[orcapi.PrivateNetworkSupport](
		state,
		proxy,
		ucxsvc.PrivateNetworksRetrieveProducts,
		func(actor rpc.Actor) orcapi.SupportByProvider[orcapi.PrivateNetworkSupport] {
			return PrivateNetworkRetrieveProducts(actor)
		},
	)

	appUcxCreateResource[orcapi.PublicIPSpecification, orcapi.PublicIp](
		state,
		proxy,
		ucxsvc.PublicIpsCreate,
		func(actor rpc.Actor, specs []orcapi.PublicIPSpecification) ([]orcapi.PublicIp, *util.HttpError) {
			return PublicIpCreate(actor, fndapi.BulkRequestOf(specs...))
		},
		func(r orcapi.PublicIp) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxDeleteResource[orcapi.PublicIp](
		state,
		proxy,
		ucxsvc.PublicIpsDelete,
		func(actor rpc.Actor, id string) (orcapi.PublicIp, *util.HttpError) {
			return ResourceRetrieve[orcapi.PublicIp](actor, publicIpType, ResourceParseId(id), orcapi.ResourceFlags{})
		},
		func(actor rpc.Actor, id string) *util.HttpError {
			_, err := PublicIpDelete(actor, fndapi.BulkRequestOf(fndapi.FindByStringId{Id: id}))
			return err
		},
		func(r orcapi.PublicIp) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxBrowseResource[orcapi.PublicIpsBrowseRequest, orcapi.PublicIp](
		state,
		proxy,
		ucxsvc.PublicIpsBrowse,
		func(req orcapi.PublicIpsBrowseRequest) util.Option[string] { return req.Next },
		func(req *orcapi.PublicIpsBrowseRequest, next util.Option[string]) { req.Next = next },
		func(req orcapi.PublicIpsBrowseRequest) int { return req.ItemsPerPage },
		func(req *orcapi.PublicIpsBrowseRequest, itemsPerPage int) { req.ItemsPerPage = itemsPerPage },
		func(req *orcapi.PublicIpsBrowseRequest) *orcapi.ResourceFlags {
			return &req.PublicIpFlags.ResourceFlags
		},
		func(actor rpc.Actor, request orcapi.PublicIpsBrowseRequest) (fndapi.PageV2[orcapi.PublicIp], *util.HttpError) {
			return PublicIpBrowse(actor, request), nil
		},
	)

	appUcxRetrieveResource[orcapi.PublicIpsRetrieveRequest, orcapi.PublicIp](
		state,
		proxy,
		ucxsvc.PublicIpsRetrieve,
		func(request orcapi.PublicIpsRetrieveRequest) string {
			return request.Id
		},
		func(actor rpc.Actor, id string) (orcapi.PublicIp, *util.HttpError) {
			return ResourceRetrieve[orcapi.PublicIp](actor, publicIpType, ResourceParseId(id), orcapi.ResourceFlags{})
		},
		func(r orcapi.PublicIp) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxUpdateLabelsResource[orcapi.PublicIpsUpdateLabelsRequest, orcapi.PublicIp](
		state,
		proxy,
		ucxsvc.PublicIpsUpdateLabels,
		func(request orcapi.PublicIpsUpdateLabelsRequest) string {
			return request.Id
		},
		func(actor rpc.Actor, id string) (orcapi.PublicIp, *util.HttpError) {
			return ResourceRetrieve[orcapi.PublicIp](actor, publicIpType, ResourceParseId(id), orcapi.ResourceFlags{})
		},
		PublicIpUpdateLabels,
		func(r orcapi.PublicIp) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	ucxsvc.PublicIpsUpdateFirewall.HandlerProxy(proxy, func(ctx context.Context, request fndapi.BulkRequest[orcapi.PublicIpUpdateFirewallRequest]) (util.Empty, error) {
		actor := state.Actor()
		allowed := make([]orcapi.PublicIpUpdateFirewallRequest, 0, len(request.Items))
		for _, reqItem := range request.Items {
			resc, err := ResourceRetrieve[orcapi.PublicIp](actor, publicIpType, ResourceParseId(reqItem.Id), orcapi.ResourceFlags{})
			if err == nil && appUcxResourceInSession(state, resc, func(r orcapi.PublicIp) orcapi.ResourceSpecification {
				return r.Specification.ResourceSpecification
			}) {
				allowed = append(allowed, reqItem)
			}
		}

		if len(allowed) == 0 {
			return util.Empty{}, nil
		}

		_ = PublicIpUpdateFirewall(actor, fndapi.BulkRequestOf(allowed...))
		return util.Empty{}, nil
	})

	appUcxRetrieveProducts[orcapi.PublicIpSupport](
		state,
		proxy,
		ucxsvc.PublicIpsRetrieveProducts,
		func(actor rpc.Actor) orcapi.SupportByProvider[orcapi.PublicIpSupport] {
			return SupportRetrieveProducts[orcapi.PublicIpSupport](publicIpType)
		},
	)

	appUcxCreateResource[orcapi.IngressSpecification, orcapi.Ingress](
		state,
		proxy,
		ucxsvc.PublicLinksCreate,
		func(actor rpc.Actor, specs []orcapi.IngressSpecification) ([]orcapi.Ingress, *util.HttpError) {
			return IngressCreate(actor, fndapi.BulkRequestOf(specs...))
		},
		func(r orcapi.Ingress) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxDeleteResource[orcapi.Ingress](
		state,
		proxy,
		ucxsvc.PublicLinksDelete,
		func(actor rpc.Actor, id string) (orcapi.Ingress, *util.HttpError) {
			return ResourceRetrieve[orcapi.Ingress](actor, ingressType, ResourceParseId(id), orcapi.ResourceFlags{})
		},
		func(actor rpc.Actor, id string) *util.HttpError {
			_, err := IngressDelete(actor, fndapi.BulkRequestOf(fndapi.FindByStringId{Id: id}))
			return err
		},
		func(r orcapi.Ingress) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxBrowseResource[orcapi.IngressesBrowseRequest, orcapi.Ingress](
		state,
		proxy,
		ucxsvc.PublicLinksBrowse,
		func(req orcapi.IngressesBrowseRequest) util.Option[string] { return req.Next },
		func(req *orcapi.IngressesBrowseRequest, next util.Option[string]) { req.Next = next },
		func(req orcapi.IngressesBrowseRequest) int { return req.ItemsPerPage },
		func(req *orcapi.IngressesBrowseRequest, itemsPerPage int) { req.ItemsPerPage = itemsPerPage },
		func(req *orcapi.IngressesBrowseRequest) *orcapi.ResourceFlags {
			return &req.IngressFlags.ResourceFlags
		},
		func(actor rpc.Actor, request orcapi.IngressesBrowseRequest) (fndapi.PageV2[orcapi.Ingress], *util.HttpError) {
			return IngressBrowse(actor, request), nil
		},
	)

	appUcxRetrieveResource[orcapi.IngressesRetrieveRequest, orcapi.Ingress](
		state,
		proxy,
		ucxsvc.PublicLinksRetrieve,
		func(request orcapi.IngressesRetrieveRequest) string {
			return request.Id
		},
		func(actor rpc.Actor, id string) (orcapi.Ingress, *util.HttpError) {
			return ResourceRetrieve[orcapi.Ingress](actor, ingressType, ResourceParseId(id), orcapi.ResourceFlags{})
		},
		func(r orcapi.Ingress) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxUpdateLabelsResource[orcapi.IngressesUpdateLabelsRequest, orcapi.Ingress](
		state,
		proxy,
		ucxsvc.PublicLinksUpdateLabels,
		func(request orcapi.IngressesUpdateLabelsRequest) string {
			return request.Id
		},
		func(actor rpc.Actor, id string) (orcapi.Ingress, *util.HttpError) {
			return ResourceRetrieve[orcapi.Ingress](actor, ingressType, ResourceParseId(id), orcapi.ResourceFlags{})
		},
		IngressUpdateLabels,
		func(r orcapi.Ingress) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxRetrieveProducts[orcapi.IngressSupport](
		state,
		proxy,
		ucxsvc.PublicLinksRetrieveProducts,
		func(actor rpc.Actor) orcapi.SupportByProvider[orcapi.IngressSupport] {
			return SupportRetrieveProducts[orcapi.IngressSupport](ingressType)
		},
	)

	appUcxCreateResource[orcapi.LicenseSpecification, orcapi.License](
		state,
		proxy,
		ucxsvc.LicensesCreate,
		func(actor rpc.Actor, specs []orcapi.LicenseSpecification) ([]orcapi.License, *util.HttpError) {
			return LicenseCreate(actor, fndapi.BulkRequestOf(specs...))
		},
		func(r orcapi.License) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxDeleteResource[orcapi.License](
		state,
		proxy,
		ucxsvc.LicensesDelete,
		func(actor rpc.Actor, id string) (orcapi.License, *util.HttpError) {
			return ResourceRetrieve[orcapi.License](actor, licenseType, ResourceParseId(id), orcapi.ResourceFlags{})
		},
		func(actor rpc.Actor, id string) *util.HttpError {
			_, err := LicenseDelete(actor, fndapi.BulkRequestOf(fndapi.FindByStringId{Id: id}))
			return err
		},
		func(r orcapi.License) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxBrowseResource[orcapi.LicensesBrowseRequest, orcapi.License](
		state,
		proxy,
		ucxsvc.LicensesBrowse,
		func(req orcapi.LicensesBrowseRequest) util.Option[string] { return req.Next },
		func(req *orcapi.LicensesBrowseRequest, next util.Option[string]) { req.Next = next },
		func(req orcapi.LicensesBrowseRequest) int { return req.ItemsPerPage },
		func(req *orcapi.LicensesBrowseRequest, itemsPerPage int) { req.ItemsPerPage = itemsPerPage },
		func(req *orcapi.LicensesBrowseRequest) *orcapi.ResourceFlags {
			return &req.LicenseFlags.ResourceFlags
		},
		func(actor rpc.Actor, request orcapi.LicensesBrowseRequest) (fndapi.PageV2[orcapi.License], *util.HttpError) {
			return LicenseBrowse(actor, request), nil
		},
	)

	appUcxRetrieveResource[orcapi.LicensesRetrieveRequest, orcapi.License](
		state,
		proxy,
		ucxsvc.LicensesRetrieve,
		func(request orcapi.LicensesRetrieveRequest) string {
			return request.Id
		},
		func(actor rpc.Actor, id string) (orcapi.License, *util.HttpError) {
			return ResourceRetrieve[orcapi.License](actor, licenseType, ResourceParseId(id), orcapi.ResourceFlags{})
		},
		func(r orcapi.License) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxUpdateLabelsResource[orcapi.LicensesUpdateLabelsRequest, orcapi.License](
		state,
		proxy,
		ucxsvc.LicensesUpdateLabels,
		func(request orcapi.LicensesUpdateLabelsRequest) string {
			return request.Id
		},
		func(actor rpc.Actor, id string) (orcapi.License, *util.HttpError) {
			return ResourceRetrieve[orcapi.License](actor, licenseType, ResourceParseId(id), orcapi.ResourceFlags{})
		},
		LicenseUpdateLabels,
		func(r orcapi.License) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxRetrieveProducts[orcapi.LicenseSupport](
		state,
		proxy,
		ucxsvc.LicensesRetrieveProducts,
		func(actor rpc.Actor) orcapi.SupportByProvider[orcapi.LicenseSupport] {
			return SupportRetrieveProducts[orcapi.LicenseSupport](licenseType)
		},
	)

	appUcxCreateResource[orcapi.DriveSpecification, orcapi.Drive](
		state,
		proxy,
		ucxsvc.DrivesCreate,
		func(actor rpc.Actor, specs []orcapi.DriveSpecification) ([]orcapi.Drive, *util.HttpError) {
			return DriveCreateBulk(actor, fndapi.BulkRequestOf(specs...))
		},
		func(r orcapi.Drive) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxDeleteResource[orcapi.Drive](
		state,
		proxy,
		ucxsvc.DrivesDelete,
		func(actor rpc.Actor, id string) (orcapi.Drive, *util.HttpError) {
			return ResourceRetrieve[orcapi.Drive](actor, driveType, ResourceParseId(id), orcapi.ResourceFlags{})
		},
		func(actor rpc.Actor, id string) *util.HttpError {
			_, err := DriveDelete(actor, fndapi.BulkRequestOf(fndapi.FindByStringId{Id: id}))
			return err
		},
		func(r orcapi.Drive) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxBrowseResource[orcapi.DrivesBrowseRequest, orcapi.Drive](
		state,
		proxy,
		ucxsvc.DrivesBrowse,
		func(req orcapi.DrivesBrowseRequest) util.Option[string] { return req.Next },
		func(req *orcapi.DrivesBrowseRequest, next util.Option[string]) { req.Next = next },
		func(req orcapi.DrivesBrowseRequest) int { return req.ItemsPerPage },
		func(req *orcapi.DrivesBrowseRequest, itemsPerPage int) { req.ItemsPerPage = itemsPerPage },
		func(req *orcapi.DrivesBrowseRequest) *orcapi.ResourceFlags {
			return &req.DriveFlags.ResourceFlags
		},
		func(actor rpc.Actor, request orcapi.DrivesBrowseRequest) (fndapi.PageV2[orcapi.Drive], *util.HttpError) {
			return DriveBrowse(actor, request), nil
		},
	)

	appUcxRetrieveResource[orcapi.DrivesRetrieveRequest, orcapi.Drive](
		state,
		proxy,
		ucxsvc.DrivesRetrieve,
		func(request orcapi.DrivesRetrieveRequest) string {
			return request.Id
		},
		func(actor rpc.Actor, id string) (orcapi.Drive, *util.HttpError) {
			return ResourceRetrieve[orcapi.Drive](actor, driveType, ResourceParseId(id), orcapi.ResourceFlags{})
		},
		func(r orcapi.Drive) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxUpdateLabelsResource[orcapi.DrivesUpdateLabelsRequest, orcapi.Drive](
		state,
		proxy,
		ucxsvc.DrivesUpdateLabels,
		func(request orcapi.DrivesUpdateLabelsRequest) string {
			return request.Id
		},
		func(actor rpc.Actor, id string) (orcapi.Drive, *util.HttpError) {
			return ResourceRetrieve[orcapi.Drive](actor, driveType, ResourceParseId(id), orcapi.ResourceFlags{})
		},
		DriveUpdateLabels,
		func(r orcapi.Drive) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	ucxsvc.DrivesRename.HandlerProxy(proxy, func(ctx context.Context, request fndapi.BulkRequest[orcapi.DriveRenameRequest]) (util.Empty, error) {
		actor := state.Actor()
		for _, reqItem := range request.Items {
			resc, err := ResourceRetrieve[orcapi.Drive](actor, driveType, ResourceParseId(reqItem.Id), orcapi.ResourceFlags{})
			if err == nil && appUcxResourceInSession(state, resc, func(r orcapi.Drive) orcapi.ResourceSpecification {
				return r.Specification.ResourceSpecification
			}) {
				_ = DriveRename(actor, reqItem.Id, reqItem.NewTitle)
			}
		}
		return util.Empty{}, nil
	})

	appUcxRetrieveProducts[orcapi.FSSupport](
		state,
		proxy,
		ucxsvc.DrivesRetrieveProducts,
		func(actor rpc.Actor) orcapi.SupportByProvider[orcapi.FSSupport] {
			return SupportRetrieveProducts[orcapi.FSSupport](driveType)
		},
	)

	appUcxCreateResource[orcapi.JobSpecification, orcapi.Job](
		state,
		proxy,
		ucxsvc.JobsCreate,
		func(actor rpc.Actor, specs []orcapi.JobSpecification) ([]orcapi.Job, *util.HttpError) {
			return JobCreate(actor, fndapi.BulkRequestOf(specs...))
		},
		func(r orcapi.Job) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxBrowseResource[orcapi.JobsBrowseRequest, orcapi.Job](
		state,
		proxy,
		ucxsvc.JobsBrowse,
		func(req orcapi.JobsBrowseRequest) util.Option[string] { return req.Next },
		func(req *orcapi.JobsBrowseRequest, next util.Option[string]) { req.Next = next },
		func(req orcapi.JobsBrowseRequest) int { return req.ItemsPerPage },
		func(req *orcapi.JobsBrowseRequest, itemsPerPage int) { req.ItemsPerPage = itemsPerPage },
		func(req *orcapi.JobsBrowseRequest) *orcapi.ResourceFlags {
			return &req.JobFlags.ResourceFlags
		},
		func(actor rpc.Actor, request orcapi.JobsBrowseRequest) (fndapi.PageV2[orcapi.Job], *util.HttpError) {
			return JobsBrowse(actor, request.Next, request.ItemsPerPage, request.JobFlags)
		},
	)

	appUcxRetrieveResource[orcapi.JobsRetrieveRequest, orcapi.Job](
		state,
		proxy,
		ucxsvc.JobsRetrieve,
		func(request orcapi.JobsRetrieveRequest) string {
			return request.Id
		},
		func(actor rpc.Actor, id string) (orcapi.Job, *util.HttpError) {
			return JobsRetrieve(actor, id, orcapi.JobFlags{})
		},
		func(r orcapi.Job) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	ucxsvc.JobsRename.HandlerProxy(proxy, func(ctx context.Context, request fndapi.BulkRequest[orcapi.JobRenameRequest]) (util.Empty, error) {
		actor := state.Actor()
		allowed := make([]orcapi.JobRenameRequest, 0, len(request.Items))
		for _, reqItem := range request.Items {
			resc, err := JobsRetrieve(actor, reqItem.Id, orcapi.JobFlags{})
			if err == nil && appUcxResourceInSession(state, resc, func(r orcapi.Job) orcapi.ResourceSpecification {
				return r.Specification.ResourceSpecification
			}) {
				allowed = append(allowed, reqItem)
			}
		}

		if len(allowed) > 0 {
			_ = JobsRenameBulk(actor, fndapi.BulkRequestOf(allowed...))
		}

		return util.Empty{}, nil
	})

	ucxsvc.JobsTerminate.HandlerProxy(proxy, func(ctx context.Context, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], error) {
		actor := state.Actor()
		allowed := make([]fndapi.FindByStringId, 0, len(request.Items))
		for _, reqItem := range request.Items {
			resc, err := JobsRetrieve(actor, reqItem.Id, orcapi.JobFlags{})
			if err == nil && appUcxResourceInSession(state, resc, func(r orcapi.Job) orcapi.ResourceSpecification {
				return r.Specification.ResourceSpecification
			}) {
				allowed = append(allowed, reqItem)
			}
		}

		if len(allowed) == 0 {
			return fndapi.BulkResponse[util.Empty]{Responses: []util.Empty{}}, nil
		}

		resp, _ := JobsTerminateBulk(actor, fndapi.BulkRequestOf(allowed...))
		return resp, nil
	})

	ucxsvc.JobsExtend.HandlerProxy(proxy, func(ctx context.Context, request fndapi.BulkRequest[orcapi.JobsExtendRequestItem]) (fndapi.BulkResponse[util.Empty], error) {
		actor := state.Actor()
		allowed := make([]orcapi.JobsExtendRequestItem, 0, len(request.Items))
		for _, reqItem := range request.Items {
			resc, err := JobsRetrieve(actor, reqItem.JobId, orcapi.JobFlags{})
			if err == nil && appUcxResourceInSession(state, resc, func(r orcapi.Job) orcapi.ResourceSpecification {
				return r.Specification.ResourceSpecification
			}) {
				allowed = append(allowed, reqItem)
			}
		}

		if len(allowed) == 0 {
			return fndapi.BulkResponse[util.Empty]{Responses: []util.Empty{}}, nil
		}

		resp, _ := JobsExtendBulk(actor, fndapi.BulkRequestOf(allowed...))
		return resp, nil
	})

	ucxsvc.JobsSuspend.HandlerProxy(proxy, func(ctx context.Context, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], error) {
		actor := state.Actor()
		allowed := make([]fndapi.FindByStringId, 0, len(request.Items))
		for _, reqItem := range request.Items {
			resc, err := JobsRetrieve(actor, reqItem.Id, orcapi.JobFlags{})
			if err == nil && appUcxResourceInSession(state, resc, func(r orcapi.Job) orcapi.ResourceSpecification {
				return r.Specification.ResourceSpecification
			}) {
				allowed = append(allowed, reqItem)
			}
		}

		if len(allowed) == 0 {
			return fndapi.BulkResponse[util.Empty]{Responses: []util.Empty{}}, nil
		}

		resp, _ := JobsSuspendBulk(actor, fndapi.BulkRequestOf(allowed...))
		return resp, nil
	})

	ucxsvc.JobsUnsuspend.HandlerProxy(proxy, func(ctx context.Context, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], error) {
		actor := state.Actor()
		allowed := make([]fndapi.FindByStringId, 0, len(request.Items))
		for _, reqItem := range request.Items {
			resc, err := JobsRetrieve(actor, reqItem.Id, orcapi.JobFlags{})
			if err == nil && appUcxResourceInSession(state, resc, func(r orcapi.Job) orcapi.ResourceSpecification {
				return r.Specification.ResourceSpecification
			}) {
				allowed = append(allowed, reqItem)
			}
		}

		if len(allowed) == 0 {
			return fndapi.BulkResponse[util.Empty]{Responses: []util.Empty{}}, nil
		}

		resp, _ := JobsUnsuspendBulk(actor, fndapi.BulkRequestOf(allowed...))
		return resp, nil
	})

	appUcxRetrieveProducts[orcapi.JobSupport](
		state,
		proxy,
		ucxsvc.JobsRetrieveProducts,
		func(actor rpc.Actor) orcapi.SupportByProvider[orcapi.JobSupport] {
			return SupportRetrieveProducts[orcapi.JobSupport](jobType)
		},
	)
}

func appUcxCreateResource[Spec any, Resc any](
	s *appUcxSessionState,
	p *ucx.Proxy,
	call ucx.Rpc[[]Spec, []Resc],
	creator func(actor rpc.Actor, specs []Spec) ([]Resc, *util.HttpError),
	baseSpecGetter func(r Resc) orcapi.ResourceSpecification,
) {
	call.HandlerProxy(p, func(ctx context.Context, request []Spec) ([]Resc, error) {
		actor := s.Actor()
		created, err := creator(actor, request)
		if err != nil {
			return nil, err.AsError()
		} else {
			for _, resc := range created {
				rescSpec := baseSpecGetter(resc)
				s.registerStacksFromLabels(rescSpec.Labels)
			}
			return created, nil
		}
	})
}

func appUcxResourceInSession[Resc any](
	s *appUcxSessionState,
	resource Resc,
	baseSpecGetter func(r Resc) orcapi.ResourceSpecification,
) bool {
	labels := baseSpecGetter(resource).Labels
	if labels == nil {
		return false
	}

	instance := strings.TrimSpace(labels[resourceLabelStackInstance])
	if instance == "" {
		return false
	}

	s.Mu.RLock()
	_, exists := s.Stacks[instance]
	s.Mu.RUnlock()
	return exists
}

func appUcxRetrieveResource[Req any, Resc any](
	s *appUcxSessionState,
	p *ucx.Proxy,
	call ucx.Rpc[Req, Resc],
	idGetter func(request Req) string,
	retrieve func(actor rpc.Actor, id string) (Resc, *util.HttpError),
	baseSpecGetter func(r Resc) orcapi.ResourceSpecification,
) {
	call.HandlerProxy(p, func(ctx context.Context, request Req) (Resc, error) {
		var empty Resc

		actor := s.Actor()
		resource, err := retrieve(actor, idGetter(request))
		if err != nil {
			return empty, err.AsError()
		}

		if !appUcxResourceInSession(s, resource, baseSpecGetter) {
			return empty, util.HttpErr(http.StatusNotFound, "not found").AsError()
		}

		return resource, nil
	})
}

func appUcxUpdateLabelsResource[ReqItem any, Resc any](
	s *appUcxSessionState,
	p *ucx.Proxy,
	call ucx.Rpc[fndapi.BulkRequest[ReqItem], util.Empty],
	idGetter func(request ReqItem) string,
	retrieve func(actor rpc.Actor, id string) (Resc, *util.HttpError),
	updateLabels func(actor rpc.Actor, request fndapi.BulkRequest[ReqItem]) *util.HttpError,
	baseSpecGetter func(r Resc) orcapi.ResourceSpecification,
) {
	call.HandlerProxy(p, func(ctx context.Context, request fndapi.BulkRequest[ReqItem]) (util.Empty, error) {
		actor := s.Actor()
		allowed := make([]ReqItem, 0, len(request.Items))

		for _, reqItem := range request.Items {
			resource, err := retrieve(actor, idGetter(reqItem))
			if err == nil && appUcxResourceInSession(s, resource, baseSpecGetter) {
				allowed = append(allowed, reqItem)
			}
		}

		if len(allowed) > 0 {
			_ = updateLabels(actor, fndapi.BulkRequestOf(allowed...))
		}

		return util.Empty{}, nil
	})
}

type appUcxBrowseNext struct {
	Stack string `json:"stack"`
	Inner string `json:"inner,omitempty"`
}

const appUcxBrowseNextPrefix = "ucxbrowse:"

func appUcxEncodeBrowseNext(stack string, inner util.Option[string]) util.Option[string] {
	next := appUcxBrowseNext{Stack: stack}
	if inner.Present {
		next.Inner = inner.Value
	}

	payload, err := json.Marshal(next)
	if err != nil {
		return util.OptNone[string]()
	}

	encoded := base64.RawURLEncoding.EncodeToString(payload)
	return util.OptValue(appUcxBrowseNextPrefix + encoded)
}

func appUcxDecodeBrowseNext(next util.Option[string]) (string, util.Option[string]) {
	if !next.Present {
		return "", util.OptNone[string]()
	}

	raw := next.Value
	if !strings.HasPrefix(raw, appUcxBrowseNextPrefix) {
		return "", next
	}

	payload, err := base64.RawURLEncoding.DecodeString(strings.TrimPrefix(raw, appUcxBrowseNextPrefix))
	if err != nil {
		return "", util.OptNone[string]()
	}

	decoded := appUcxBrowseNext{}
	if err := json.Unmarshal(payload, &decoded); err != nil {
		return "", util.OptNone[string]()
	}

	inner := util.OptNone[string]()
	if decoded.Inner != "" {
		inner.Set(decoded.Inner)
	}

	return decoded.Stack, inner
}

func appUcxBrowseResource[Req any, Resc any](
	s *appUcxSessionState,
	p *ucx.Proxy,
	call ucx.Rpc[Req, fndapi.PageV2[Resc]],
	getNext func(request Req) util.Option[string],
	setNext func(request *Req, next util.Option[string]),
	getItemsPerPage func(request Req) int,
	setItemsPerPage func(request *Req, itemsPerPage int),
	resourceFlags func(request *Req) *orcapi.ResourceFlags,
	browse func(actor rpc.Actor, request Req) (fndapi.PageV2[Resc], *util.HttpError),
) {
	call.HandlerProxy(p, func(ctx context.Context, request Req) (fndapi.PageV2[Resc], error) {
		itemsPerPage := fndapi.ItemsPerPage(getItemsPerPage(request))
		result := fndapi.PageV2[Resc]{
			Items:        make([]Resc, 0, itemsPerPage),
			ItemsPerPage: itemsPerPage,
		}

		stacks := s.StackInstances()
		if len(stacks) == 0 {
			return result, nil
		}

		stack, innerNext := appUcxDecodeBrowseNext(getNext(request))
		if stack == "" {
			stack = stacks[0]
		}

		stackIndex := slices.Index(stacks, stack)
		if stackIndex == -1 {
			stackIndex = 0
		}

		actor := s.Actor()
		for len(result.Items) < itemsPerPage && stackIndex < len(stacks) {
			remaining := itemsPerPage - len(result.Items)
			stackName := stacks[stackIndex]

			stackRequest := request
			setItemsPerPage(&stackRequest, remaining)
			setNext(&stackRequest, innerNext)

			flags := resourceFlags(&stackRequest)
			labels := map[string]string{}
			for key, value := range flags.FilterLabels {
				labels[key] = value
			}
			labels[resourceLabelStackInstance] = stackName
			flags.FilterLabels = labels

			page, err := browse(actor, stackRequest)
			if err != nil {
				return fndapi.PageV2[Resc]{}, err.AsError()
			}

			result.Items = append(result.Items, page.Items...)

			if page.Next.Present {
				innerNext = page.Next
			} else {
				stackIndex++
				innerNext = util.OptNone[string]()
			}

			if len(result.Items) == itemsPerPage {
				if page.Next.Present {
					result.Next = appUcxEncodeBrowseNext(stackName, page.Next)
				} else if stackIndex < len(stacks) {
					result.Next = appUcxEncodeBrowseNext(stacks[stackIndex], util.OptNone[string]())
				}
				break
			}
		}

		return result, nil
	})
}

func appUcxDeleteResource[Resc any](
	s *appUcxSessionState,
	p *ucx.Proxy,
	call ucx.Rpc[[]string, util.Empty],
	retrieve func(actor rpc.Actor, id string) (Resc, *util.HttpError),
	delete func(actor rpc.Actor, id string) *util.HttpError,
	baseSpecGetter func(r Resc) orcapi.ResourceSpecification,
) {
	call.HandlerProxy(p, func(ctx context.Context, request []string) (util.Empty, error) {
		actor := s.Actor()
		deleteCount := 0
		for _, id := range request {
			resc, err := retrieve(actor, id)
			if err == nil {
				rescSpec := baseSpecGetter(resc)
				instance := rescSpec.Labels[resourceLabelStackInstance]
				s.Mu.Lock()
				_, exists := s.Stacks[instance]
				s.Mu.Unlock()

				if !exists {
					continue
				} else {
					err = delete(actor, id)
					if err == nil {
						deleteCount++
					}
				}
			}
		}
		return util.Empty{}, nil
	})
}

func appUcxRetrieveProducts[Supp any](
	s *appUcxSessionState,
	p *ucx.Proxy,
	call ucx.Rpc[util.Empty, []orcapi.ResolvedSupport[Supp]],
	retrieveProducts func(actor rpc.Actor) orcapi.SupportByProvider[Supp],
) {
	call.HandlerProxy(p, func(ctx context.Context, request util.Empty) ([]orcapi.ResolvedSupport[Supp], error) {
		actor := s.Actor()
		provider := s.Provider()
		allSupport := retrieveProducts(actor)
		byProvider := allSupport.ProductsByProvider[provider]
		return byProvider, nil
	})
}
