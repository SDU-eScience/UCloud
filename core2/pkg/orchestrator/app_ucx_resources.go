package orchestrator

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"net/http"
	"slices"
	"strings"
	"sync"

	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/util"
)

type appUcxBaseState struct {
	Mu                 sync.RWMutex
	Stacks             map[string]util.Empty
	StackEntity        util.Option[string]
	AllowStackCreation bool

	Actor       func() rpc.Actor
	Provider    func() string
	Application func() orcapi.NameAndVersion
	SessionJob  func() (orcapi.Job, bool)
}

func (s *appUcxBaseState) StackInstances() []string {
	s.Mu.RLock()
	instances := make([]string, 0, len(s.Stacks))
	for stack := range s.Stacks {
		instances = append(instances, stack)
	}
	s.Mu.RUnlock()
	slices.Sort(instances)
	return instances
}

func appUcxResourceHandlers(state *appUcxBaseState, proxy *ucx.Proxy) {
	appUcxCreateResource[orcapi.PrivateNetworkSpecification, orcapi.PrivateNetwork](
		state,
		proxy,
		ucxapi.PrivateNetworksCreate,
		func(actor rpc.Actor, specs []orcapi.PrivateNetworkSpecification) ([]orcapi.PrivateNetwork, *util.HttpError) {
			return PrivateNetworkCreate(actor, fndapi.BulkRequestOf(specs...))
		},
		func(r orcapi.PrivateNetwork) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
		func(r *orcapi.PrivateNetworkSpecification) *orcapi.ResourceSpecification {
			return &r.ResourceSpecification
		},
	)

	appUcxDeleteResource[orcapi.PrivateNetwork](
		state,
		proxy,
		ucxapi.PrivateNetworksDelete,
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
		ucxapi.PrivateNetworksBrowse,
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
		ucxapi.PrivateNetworksRetrieve,
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
		ucxapi.PrivateNetworksUpdateLabels,
		func(request orcapi.PrivateNetworksUpdateLabelsRequest) string {
			return request.Id
		},
		func(actor rpc.Actor, id string) (orcapi.PrivateNetwork, *util.HttpError) {
			return PrivateNetworkRetrieve(actor, orcapi.PrivateNetworksRetrieveRequest{Id: id})
		},
		func(request orcapi.PrivateNetworksUpdateLabelsRequest) map[string]string { return request.Labels },
		func(request *orcapi.PrivateNetworksUpdateLabelsRequest, labels map[string]string) {
			request.Labels = labels
		},
		PrivateNetworkUpdateLabels,
		func(r orcapi.PrivateNetwork) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxRetrieveProducts[orcapi.PrivateNetworkSupport](
		state,
		proxy,
		ucxapi.PrivateNetworksRetrieveProducts,
		func(actor rpc.Actor) orcapi.SupportByProvider[orcapi.PrivateNetworkSupport] {
			return PrivateNetworkRetrieveProducts(actor)
		},
	)

	appUcxCreateResource[orcapi.PrivateNetworkIpSpecification, orcapi.PrivateNetworkIp](
		state,
		proxy,
		ucxapi.PrivateNetworkIpsCreate,
		func(actor rpc.Actor, specs []orcapi.PrivateNetworkIpSpecification) ([]orcapi.PrivateNetworkIp, *util.HttpError) {
			return PrivateNetworkIpCreate(actor, fndapi.BulkRequestOf(specs...))
		},
		func(r orcapi.PrivateNetworkIp) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
		func(r *orcapi.PrivateNetworkIpSpecification) *orcapi.ResourceSpecification {
			return &r.ResourceSpecification
		},
	)

	appUcxDeleteResource[orcapi.PrivateNetworkIp](
		state,
		proxy,
		ucxapi.PrivateNetworkIpsDelete,
		func(actor rpc.Actor, id string) (orcapi.PrivateNetworkIp, *util.HttpError) {
			return ResourceRetrieve[orcapi.PrivateNetworkIp](actor, privateNetworkIpType, ResourceParseId(id), orcapi.ResourceFlags{})
		},
		func(actor rpc.Actor, id string) *util.HttpError {
			return ResourceDeleteThroughProvider(actor, privateNetworkIpType, id, orcapi.PrivateNetworkIpsProviderDelete)
		},
		func(r orcapi.PrivateNetworkIp) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxBrowseResource[orcapi.PrivateNetworkIpsBrowseRequest, orcapi.PrivateNetworkIp](
		state,
		proxy,
		ucxapi.PrivateNetworkIpsBrowse,
		func(req orcapi.PrivateNetworkIpsBrowseRequest) util.Option[string] { return req.Next },
		func(req *orcapi.PrivateNetworkIpsBrowseRequest, next util.Option[string]) { req.Next = next },
		func(req orcapi.PrivateNetworkIpsBrowseRequest) int { return req.ItemsPerPage },
		func(req *orcapi.PrivateNetworkIpsBrowseRequest, itemsPerPage int) { req.ItemsPerPage = itemsPerPage },
		func(req *orcapi.PrivateNetworkIpsBrowseRequest) *orcapi.ResourceFlags {
			return &req.PrivateNetworkIpFlags.ResourceFlags
		},
		func(actor rpc.Actor, request orcapi.PrivateNetworkIpsBrowseRequest) (fndapi.PageV2[orcapi.PrivateNetworkIp], *util.HttpError) {
			sortByFn := ResourceDefaultComparator(func(item orcapi.PrivateNetworkIp) orcapi.Resource {
				return item.Resource
			}, request.ResourceFlags)

			return ResourceBrowse[orcapi.PrivateNetworkIp](
				actor,
				privateNetworkIpType,
				request.Next,
				request.ItemsPerPage,
				request.ResourceFlags,
				func(item orcapi.PrivateNetworkIp) bool {
					return true
				},
				sortByFn,
			), nil
		},
	)

	appUcxRetrieveResource[orcapi.PrivateNetworkIpsRetrieveRequest, orcapi.PrivateNetworkIp](
		state,
		proxy,
		ucxapi.PrivateNetworkIpsRetrieve,
		func(request orcapi.PrivateNetworkIpsRetrieveRequest) string {
			return request.Id
		},
		func(actor rpc.Actor, id string) (orcapi.PrivateNetworkIp, *util.HttpError) {
			return ResourceRetrieve[orcapi.PrivateNetworkIp](actor, privateNetworkIpType, ResourceParseId(id), orcapi.ResourceFlags{})
		},
		func(r orcapi.PrivateNetworkIp) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxUpdateLabelsResource[orcapi.PrivateNetworkIpsUpdateLabelsRequest, orcapi.PrivateNetworkIp](
		state,
		proxy,
		ucxapi.PrivateNetworkIpsUpdateLabels,
		func(request orcapi.PrivateNetworkIpsUpdateLabelsRequest) string {
			return request.Id
		},
		func(actor rpc.Actor, id string) (orcapi.PrivateNetworkIp, *util.HttpError) {
			return ResourceRetrieve[orcapi.PrivateNetworkIp](actor, privateNetworkIpType, ResourceParseId(id), orcapi.ResourceFlags{})
		},
		func(request orcapi.PrivateNetworkIpsUpdateLabelsRequest) map[string]string { return request.Labels },
		func(request *orcapi.PrivateNetworkIpsUpdateLabelsRequest, labels map[string]string) {
			request.Labels = labels
		},
		PrivateNetworkIpUpdateLabels,
		func(r orcapi.PrivateNetworkIp) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxRetrieveProducts[orcapi.PrivateNetworkIpSupport](
		state,
		proxy,
		ucxapi.PrivateNetworkIpsRetrieveProducts,
		func(actor rpc.Actor) orcapi.SupportByProvider[orcapi.PrivateNetworkIpSupport] {
			return SupportRetrieveProducts[orcapi.PrivateNetworkIpSupport](privateNetworkIpType)
		},
	)

	appUcxCreateResource[orcapi.PublicIPSpecification, orcapi.PublicIp](
		state,
		proxy,
		ucxapi.PublicIpsCreate,
		func(actor rpc.Actor, specs []orcapi.PublicIPSpecification) ([]orcapi.PublicIp, *util.HttpError) {
			return PublicIpCreate(actor, fndapi.BulkRequestOf(specs...))
		},
		func(r orcapi.PublicIp) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
		func(r *orcapi.PublicIPSpecification) *orcapi.ResourceSpecification {
			return &r.ResourceSpecification
		},
	)

	appUcxDeleteResource[orcapi.PublicIp](
		state,
		proxy,
		ucxapi.PublicIpsDelete,
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
		ucxapi.PublicIpsBrowse,
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
		ucxapi.PublicIpsRetrieve,
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
		ucxapi.PublicIpsUpdateLabels,
		func(request orcapi.PublicIpsUpdateLabelsRequest) string {
			return request.Id
		},
		func(actor rpc.Actor, id string) (orcapi.PublicIp, *util.HttpError) {
			return ResourceRetrieve[orcapi.PublicIp](actor, publicIpType, ResourceParseId(id), orcapi.ResourceFlags{})
		},
		func(request orcapi.PublicIpsUpdateLabelsRequest) map[string]string { return request.Labels },
		func(request *orcapi.PublicIpsUpdateLabelsRequest, labels map[string]string) {
			request.Labels = labels
		},
		PublicIpUpdateLabels,
		func(r orcapi.PublicIp) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	ucxapi.PublicIpsUpdateFirewall.HandlerProxy(proxy, func(ctx context.Context, request fndapi.BulkRequest[orcapi.PublicIpUpdateFirewallRequest]) (util.Empty, error) {
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

		err := PublicIpUpdateFirewall(actor, fndapi.BulkRequestOf(allowed...))
		if err != nil {
			return util.Empty{}, err
		}
		return util.Empty{}, nil
	})

	appUcxRetrieveProducts[orcapi.PublicIpSupport](
		state,
		proxy,
		ucxapi.PublicIpsRetrieveProducts,
		func(actor rpc.Actor) orcapi.SupportByProvider[orcapi.PublicIpSupport] {
			return SupportRetrieveProducts[orcapi.PublicIpSupport](publicIpType)
		},
	)

	appUcxCreateResource[orcapi.IngressSpecification, orcapi.Ingress](
		state,
		proxy,
		ucxapi.PublicLinksCreate,
		func(actor rpc.Actor, specs []orcapi.IngressSpecification) ([]orcapi.Ingress, *util.HttpError) {
			return IngressCreate(actor, fndapi.BulkRequestOf(specs...))
		},
		func(r orcapi.Ingress) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
		func(r *orcapi.IngressSpecification) *orcapi.ResourceSpecification {
			return &r.ResourceSpecification
		},
	)

	appUcxDeleteResource[orcapi.Ingress](
		state,
		proxy,
		ucxapi.PublicLinksDelete,
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
		ucxapi.PublicLinksBrowse,
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
		ucxapi.PublicLinksRetrieve,
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
		ucxapi.PublicLinksUpdateLabels,
		func(request orcapi.IngressesUpdateLabelsRequest) string {
			return request.Id
		},
		func(actor rpc.Actor, id string) (orcapi.Ingress, *util.HttpError) {
			return ResourceRetrieve[orcapi.Ingress](actor, ingressType, ResourceParseId(id), orcapi.ResourceFlags{})
		},
		func(request orcapi.IngressesUpdateLabelsRequest) map[string]string { return request.Labels },
		func(request *orcapi.IngressesUpdateLabelsRequest, labels map[string]string) {
			request.Labels = labels
		},
		IngressUpdateLabels,
		func(r orcapi.Ingress) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxRetrieveProducts[orcapi.IngressSupport](
		state,
		proxy,
		ucxapi.PublicLinksRetrieveProducts,
		func(actor rpc.Actor) orcapi.SupportByProvider[orcapi.IngressSupport] {
			return SupportRetrieveProducts[orcapi.IngressSupport](ingressType)
		},
	)

	appUcxCreateResource[orcapi.ServiceSpecification, orcapi.Service](
		state,
		proxy,
		ucxapi.ServicesCreate,
		func(actor rpc.Actor, specs []orcapi.ServiceSpecification) ([]orcapi.Service, *util.HttpError) {
			return ServiceCreate(actor, fndapi.BulkRequestOf(specs...))
		},
		func(r orcapi.Service) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
		func(r *orcapi.ServiceSpecification) *orcapi.ResourceSpecification {
			return &r.ResourceSpecification
		},
	)

	appUcxDeleteResource[orcapi.Service](
		state,
		proxy,
		ucxapi.ServicesDelete,
		func(actor rpc.Actor, id string) (orcapi.Service, *util.HttpError) {
			return ServiceRetrieve(actor, orcapi.ServicesRetrieveRequest{Id: id})
		},
		func(actor rpc.Actor, id string) *util.HttpError {
			_, err := ServiceDelete(actor, fndapi.BulkRequestOf(fndapi.FindByStringId{Id: id}))
			return err
		},
		func(r orcapi.Service) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxBrowseResource[orcapi.ServicesBrowseRequest, orcapi.Service](
		state,
		proxy,
		ucxapi.ServicesBrowse,
		func(req orcapi.ServicesBrowseRequest) util.Option[string] { return req.Next },
		func(req *orcapi.ServicesBrowseRequest, next util.Option[string]) { req.Next = next },
		func(req orcapi.ServicesBrowseRequest) int { return req.ItemsPerPage },
		func(req *orcapi.ServicesBrowseRequest, itemsPerPage int) { req.ItemsPerPage = itemsPerPage },
		func(req *orcapi.ServicesBrowseRequest) *orcapi.ResourceFlags {
			return &req.ServiceFlags.ResourceFlags
		},
		func(actor rpc.Actor, request orcapi.ServicesBrowseRequest) (fndapi.PageV2[orcapi.Service], *util.HttpError) {
			return ServiceBrowse(actor, request), nil
		},
	)

	appUcxRetrieveResource[orcapi.ServicesRetrieveRequest, orcapi.Service](
		state,
		proxy,
		ucxapi.ServicesRetrieve,
		func(request orcapi.ServicesRetrieveRequest) string {
			return request.Id
		},
		func(actor rpc.Actor, id string) (orcapi.Service, *util.HttpError) {
			return ServiceRetrieve(actor, orcapi.ServicesRetrieveRequest{Id: id})
		},
		func(r orcapi.Service) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxRetrieveProducts[orcapi.ServiceSupport](
		state,
		proxy,
		ucxapi.ServicesRetrieveProducts,
		func(actor rpc.Actor) orcapi.SupportByProvider[orcapi.ServiceSupport] {
			return SupportRetrieveProducts[orcapi.ServiceSupport](serviceType)
		},
	)

	ucxapi.ServicesUpdate.HandlerProxy(proxy, func(ctx context.Context, request fndapi.BulkRequest[orcapi.ResourceUpdateAndId[orcapi.ServicesUpdateRequest]]) (util.Empty, error) {
		actor := state.Actor()

		for _, reqItem := range request.Items {
			svc, err := ServiceRetrieve(actor, orcapi.ServicesRetrieveRequest{Id: reqItem.Id})
			if err != nil {
				return util.Empty{}, err.AsError()
			}

			if !appUcxResourceInSession(state, svc, func(r orcapi.Service) orcapi.ResourceSpecification {
				return r.Specification.ResourceSpecification
			}) {
				return util.Empty{}, util.HttpErr(http.StatusNotFound, "not found").AsError()
			}

			if err := ServiceUpdate(actor, reqItem.Id, reqItem.Update); err != nil {
				return util.Empty{}, err.AsError()
			}
		}

		return util.Empty{}, nil
	})

	ucxapi.ServicesAddMembers.HandlerProxy(proxy, func(ctx context.Context, request fndapi.BulkRequest[orcapi.ServicesMembersRequest]) (util.Empty, error) {
		actor := state.Actor()

		for _, reqItem := range request.Items {
			if err := appUcxValidateServiceMembersRequest(state, actor, reqItem); err != nil {
				return util.Empty{}, err
			}
		}

		for _, reqItem := range request.Items {
			if err := ServiceAddMembers(actor, reqItem.Id, reqItem.JobIds); err != nil {
				return util.Empty{}, err.AsError()
			}
		}

		return util.Empty{}, nil
	})

	ucxapi.ServicesRemoveMembers.HandlerProxy(proxy, func(ctx context.Context, request fndapi.BulkRequest[orcapi.ServicesMembersRequest]) (util.Empty, error) {
		actor := state.Actor()

		for _, reqItem := range request.Items {
			if err := appUcxValidateServiceMembersRequest(state, actor, reqItem); err != nil {
				return util.Empty{}, err
			}
		}

		for _, reqItem := range request.Items {
			if err := ServiceRemoveMembers(actor, reqItem.Id, reqItem.JobIds); err != nil {
				return util.Empty{}, err.AsError()
			}
		}

		return util.Empty{}, nil
	})

	ucxapi.PublicLinksSetTarget.HandlerProxy(proxy, func(ctx context.Context, request fndapi.BulkRequest[orcapi.IngressesSetTargetRequest]) (util.Empty, error) {
		actor := state.Actor()

		for _, reqItem := range request.Items {
			ingress, err := ResourceRetrieve[orcapi.Ingress](actor, ingressType, ResourceParseId(reqItem.Id), orcapi.ResourceFlags{})
			if err != nil {
				return util.Empty{}, err.AsError()
			}

			if !appUcxResourceInSession(state, ingress, func(r orcapi.Ingress) orcapi.ResourceSpecification {
				return r.Specification.ResourceSpecification
			}) {
				return util.Empty{}, util.HttpErr(http.StatusNotFound, "not found").AsError()
			}

			if target := reqItem.Target; target.Present {
				svc, err := ServiceRetrieve(actor, orcapi.ServicesRetrieveRequest{Id: target.Value.ServiceId})
				if err != nil {
					return util.Empty{}, err.AsError()
				}

				if !appUcxResourceInSession(state, svc, func(r orcapi.Service) orcapi.ResourceSpecification {
					return r.Specification.ResourceSpecification
				}) {
					return util.Empty{}, util.HttpErr(http.StatusNotFound, "not found").AsError()
				}
			}

			if err := IngressSetTarget(actor, reqItem.Id, reqItem.Target); err != nil {
				return util.Empty{}, err.AsError()
			}
		}

		return util.Empty{}, nil
	})

	appUcxCreateResource[orcapi.LicenseSpecification, orcapi.License](
		state,
		proxy,
		ucxapi.LicensesCreate,
		func(actor rpc.Actor, specs []orcapi.LicenseSpecification) ([]orcapi.License, *util.HttpError) {
			return LicenseCreate(actor, fndapi.BulkRequestOf(specs...))
		},
		func(r orcapi.License) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
		func(r *orcapi.LicenseSpecification) *orcapi.ResourceSpecification {
			return &r.ResourceSpecification
		},
	)

	appUcxDeleteResource[orcapi.License](
		state,
		proxy,
		ucxapi.LicensesDelete,
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
		ucxapi.LicensesBrowse,
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
		ucxapi.LicensesRetrieve,
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
		ucxapi.LicensesUpdateLabels,
		func(request orcapi.LicensesUpdateLabelsRequest) string {
			return request.Id
		},
		func(actor rpc.Actor, id string) (orcapi.License, *util.HttpError) {
			return ResourceRetrieve[orcapi.License](actor, licenseType, ResourceParseId(id), orcapi.ResourceFlags{})
		},
		func(request orcapi.LicensesUpdateLabelsRequest) map[string]string { return request.Labels },
		func(request *orcapi.LicensesUpdateLabelsRequest, labels map[string]string) {
			request.Labels = labels
		},
		LicenseUpdateLabels,
		func(r orcapi.License) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxRetrieveProducts[orcapi.LicenseSupport](
		state,
		proxy,
		ucxapi.LicensesRetrieveProducts,
		func(actor rpc.Actor) orcapi.SupportByProvider[orcapi.LicenseSupport] {
			return SupportRetrieveProducts[orcapi.LicenseSupport](licenseType)
		},
	)

	appUcxCreateResource[orcapi.DriveSpecification, orcapi.Drive](
		state,
		proxy,
		ucxapi.DrivesCreate,
		func(actor rpc.Actor, specs []orcapi.DriveSpecification) ([]orcapi.Drive, *util.HttpError) {
			return DriveCreateBulk(actor, fndapi.BulkRequestOf(specs...))
		},
		func(r orcapi.Drive) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
		func(r *orcapi.DriveSpecification) *orcapi.ResourceSpecification {
			return &r.ResourceSpecification
		},
	)

	appUcxDeleteResource[orcapi.Drive](
		state,
		proxy,
		ucxapi.DrivesDelete,
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
		ucxapi.DrivesBrowse,
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
		ucxapi.DrivesRetrieve,
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
		ucxapi.DrivesUpdateLabels,
		func(request orcapi.DrivesUpdateLabelsRequest) string {
			return request.Id
		},
		func(actor rpc.Actor, id string) (orcapi.Drive, *util.HttpError) {
			return ResourceRetrieve[orcapi.Drive](actor, driveType, ResourceParseId(id), orcapi.ResourceFlags{})
		},
		func(request orcapi.DrivesUpdateLabelsRequest) map[string]string { return request.Labels },
		func(request *orcapi.DrivesUpdateLabelsRequest, labels map[string]string) {
			request.Labels = labels
		},
		DriveUpdateLabels,
		func(r orcapi.Drive) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	ucxapi.DrivesRename.HandlerProxy(proxy, func(ctx context.Context, request fndapi.BulkRequest[orcapi.DriveRenameRequest]) (util.Empty, error) {
		actor := state.Actor()
		for _, reqItem := range request.Items {
			resc, err := ResourceRetrieve[orcapi.Drive](actor, driveType, ResourceParseId(reqItem.Id), orcapi.ResourceFlags{})
			if err == nil && appUcxResourceInSession(state, resc, func(r orcapi.Drive) orcapi.ResourceSpecification {
				return r.Specification.ResourceSpecification
			}) {
				if renameErr := DriveRename(actor, reqItem.Id, reqItem.NewTitle); renameErr != nil {
					return util.Empty{}, renameErr
				}
			}
		}
		return util.Empty{}, nil
	})

	appUcxRetrieveProducts[orcapi.FSSupport](
		state,
		proxy,
		ucxapi.DrivesRetrieveProducts,
		func(actor rpc.Actor) orcapi.SupportByProvider[orcapi.FSSupport] {
			return SupportRetrieveProducts[orcapi.FSSupport](driveType)
		},
	)

	appUcxCreateResource[orcapi.ContainerRepositorySpecification, orcapi.ContainerRepository](
		state,
		proxy,
		ucxapi.ContainerRepositoriesCreate,
		func(actor rpc.Actor, specs []orcapi.ContainerRepositorySpecification) ([]orcapi.ContainerRepository, *util.HttpError) {
			return ContainerRepositoryCreate(actor, fndapi.BulkRequestOf(specs...))
		},
		func(r orcapi.ContainerRepository) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
		func(r *orcapi.ContainerRepositorySpecification) *orcapi.ResourceSpecification {
			return &r.ResourceSpecification
		},
	)

	appUcxDeleteResource[orcapi.ContainerRepository](
		state,
		proxy,
		ucxapi.ContainerRepositoriesDelete,
		func(actor rpc.Actor, id string) (orcapi.ContainerRepository, *util.HttpError) {
			return ResourceRetrieve[orcapi.ContainerRepository](actor, containerRepositoryType, ResourceParseId(id), orcapi.ResourceFlags{})
		},
		func(actor rpc.Actor, id string) *util.HttpError {
			_, err := ContainerRepositoryDelete(actor, fndapi.BulkRequestOf(fndapi.FindByStringId{Id: id}))
			return err
		},
		func(r orcapi.ContainerRepository) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxBrowseResource[orcapi.ContainerRepositoriesBrowseRequest, orcapi.ContainerRepository](
		state,
		proxy,
		ucxapi.ContainerRepositoriesBrowse,
		func(req orcapi.ContainerRepositoriesBrowseRequest) util.Option[string] { return req.Next },
		func(req *orcapi.ContainerRepositoriesBrowseRequest, next util.Option[string]) { req.Next = next },
		func(req orcapi.ContainerRepositoriesBrowseRequest) int { return req.ItemsPerPage },
		func(req *orcapi.ContainerRepositoriesBrowseRequest, itemsPerPage int) {
			req.ItemsPerPage = itemsPerPage
		},
		func(req *orcapi.ContainerRepositoriesBrowseRequest) *orcapi.ResourceFlags {
			return &req.ContainerRepositoryFlags.ResourceFlags
		},
		func(actor rpc.Actor, request orcapi.ContainerRepositoriesBrowseRequest) (fndapi.PageV2[orcapi.ContainerRepository], *util.HttpError) {
			return ContainerRepositoryBrowse(actor, request), nil
		},
	)

	appUcxRetrieveResource[orcapi.ContainerRepositoriesRetrieveRequest, orcapi.ContainerRepository](
		state,
		proxy,
		ucxapi.ContainerRepositoriesRetrieve,
		func(request orcapi.ContainerRepositoriesRetrieveRequest) string {
			return request.Id
		},
		func(actor rpc.Actor, id string) (orcapi.ContainerRepository, *util.HttpError) {
			return ResourceRetrieve[orcapi.ContainerRepository](actor, containerRepositoryType, ResourceParseId(id), orcapi.ResourceFlags{})
		},
		func(r orcapi.ContainerRepository) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxUpdateLabelsResource[orcapi.ContainerRepositoriesUpdateLabelsRequest, orcapi.ContainerRepository](
		state,
		proxy,
		ucxapi.ContainerRepositoriesUpdateLabels,
		func(request orcapi.ContainerRepositoriesUpdateLabelsRequest) string {
			return request.Id
		},
		func(actor rpc.Actor, id string) (orcapi.ContainerRepository, *util.HttpError) {
			return ResourceRetrieve[orcapi.ContainerRepository](actor, containerRepositoryType, ResourceParseId(id), orcapi.ResourceFlags{})
		},
		func(request orcapi.ContainerRepositoriesUpdateLabelsRequest) map[string]string { return request.Labels },
		func(request *orcapi.ContainerRepositoriesUpdateLabelsRequest, labels map[string]string) {
			request.Labels = labels
		},
		ContainerRepositoryUpdateLabels,
		func(r orcapi.ContainerRepository) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
	)

	appUcxRetrieveProducts[orcapi.FSSupport](
		state,
		proxy,
		ucxapi.ContainerRepositoriesRetrieveProducts,
		func(actor rpc.Actor) orcapi.SupportByProvider[orcapi.FSSupport] {
			return ContainerRepositoryRetrieveProducts()
		},
	)

	appUcxCreateResource[orcapi.JobSpecification, orcapi.Job](
		state,
		proxy,
		ucxapi.JobsCreate,
		func(actor rpc.Actor, specs []orcapi.JobSpecification) ([]orcapi.Job, *util.HttpError) {
			return JobCreate(actor, fndapi.BulkRequestOf(specs...))
		},
		func(r orcapi.Job) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		},
		func(r *orcapi.JobSpecification) *orcapi.ResourceSpecification {
			return &r.ResourceSpecification
		},
	)

	appUcxBrowseResource[orcapi.JobsBrowseRequest, orcapi.Job](
		state,
		proxy,
		ucxapi.JobsBrowse,
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
		ucxapi.JobsRetrieve,
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

	ucxapi.JobsRename.HandlerProxy(proxy, func(ctx context.Context, request fndapi.BulkRequest[orcapi.JobRenameRequest]) (util.Empty, error) {
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
			if err := JobsRenameBulk(actor, fndapi.BulkRequestOf(allowed...)); err != nil {
				return util.Empty{}, err
			}
		}

		return util.Empty{}, nil
	})

	ucxapi.JobsTerminate.HandlerProxy(proxy, func(ctx context.Context, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], error) {
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

	ucxapi.JobsExtend.HandlerProxy(proxy, func(ctx context.Context, request fndapi.BulkRequest[orcapi.JobsExtendRequestItem]) (fndapi.BulkResponse[util.Empty], error) {
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

	ucxapi.JobsSuspend.HandlerProxy(proxy, func(ctx context.Context, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], error) {
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

	ucxapi.JobsUnsuspend.HandlerProxy(proxy, func(ctx context.Context, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], error) {
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
		ucxapi.JobsRetrieveProducts,
		func(actor rpc.Actor) orcapi.SupportByProvider[orcapi.JobSupport] {
			return SupportRetrieveProducts[orcapi.JobSupport](jobType)
		},
	)
}

func appUcxMoveServerLabels(specification *orcapi.ResourceSpecification) {
	specification.ServerLabels = nil
	for label, value := range specification.Labels {
		if !orcapi.ResourceLabelIsReserved(label) {
			continue
		}

		normalized := strings.ToLower(label)
		if normalized == orcapi.ResourceLabelStackEntity {
			delete(specification.Labels, label)
			continue
		}

		specification.ServerLabels = util.MapMerge(specification.ServerLabels, map[string]string{
			normalized: value,
		})
		delete(specification.Labels, label)
	}
}

func appUcxApplyJobStackLabels(s *appUcxBaseState, specification *orcapi.ResourceSpecification) (rpc.Actor, *util.HttpError) {
	if s.SessionJob == nil {
		return rpc.Actor{}, util.HttpErr(http.StatusForbidden, "the UCX job session is not available")
	}

	job, ok := s.SessionJob()
	if !ok {
		return rpc.Actor{}, util.HttpErr(http.StatusForbidden, "the UCX job session is not available")
	}

	stacks := s.StackInstances()
	if len(stacks) != 1 {
		return rpc.Actor{}, util.HttpErr(http.StatusForbidden, "this job is not associated with a stack")
	}
	sessionStack := stacks[0]

	requestedStack, exists := resourceLabelLookup(specification.Labels, orcapi.ResourceLabelStackInstance)
	if !exists || strings.TrimSpace(requestedStack) == "" {
		return rpc.Actor{}, util.HttpErr(http.StatusBadRequest, "the stack instance is required")
	}

	if strings.TrimSpace(requestedStack) != sessionStack {
		return rpc.Actor{}, util.HttpErr(http.StatusForbidden, "the resource must belong to the stack of the job")
	}

	specification.ServerLabels = nil
	isStackController := strings.EqualFold(strings.TrimSpace(job.Specification.Labels[orcapi.ResourceLabelStackController]), "true")

	serverLabels := map[string]string{}
	for _, key := range resourceStackIdentityLabels {
		if value, exists := job.Specification.Labels[key]; exists {
			serverLabels[key] = value
		}
	}

	for _, key := range resourceStackControllerLabels {
		value, exists := resourceLabelLookup(specification.Labels, key)
		if !exists {
			continue
		}

		if !isStackController {
			return rpc.Actor{}, util.HttpErr(http.StatusForbidden, "this job is not allowed to set the label %s", key)
		}

		serverLabels[key] = value
	}

	for label := range specification.Labels {
		if orcapi.ResourceLabelIsReserved(label) {
			delete(specification.Labels, label)
		}
	}

	specification.ServerLabels = util.MapMerge(specification.ServerLabels, serverLabels)

	stackActor := rpc.Actor{Username: job.Owner.CreatedBy}
	if job.Owner.Project.Present {
		stackActor.Project = util.OptValue(rpc.ProjectId(job.Owner.Project.Value))
	}
	return stackActor, nil
}

func appUcxValidateCreateReferences(specification orcapi.ResourceSpecification, item any) *util.HttpError {
	switch typed := item.(type) {
	case orcapi.PrivateNetworkIpSpecification:
		return appUcxValidateReferenceInStack(specification, privateNetworkType, typed.Network)
	case orcapi.ServiceSpecification:
		if typed.InternalEndpoint.Present {
			return appUcxValidateReferenceInStack(specification, privateNetworkType, typed.InternalEndpoint.Value.PrivateNetworkId)
		}
	case orcapi.IngressSpecification:
		if typed.Target.Present {
			return appUcxValidateReferenceInStack(specification, serviceType, typed.Target.Value.ServiceId)
		}
	case orcapi.JobSpecification:
		for _, service := range typed.Services {
			serviceErr := appUcxValidateReferenceInStack(specification, serviceType, service.ServiceId)
			if serviceErr != nil {
				return serviceErr
			}
		}

		for _, value := range typed.Resources {
			valueErr := appUcxValidateJobParameterInStack(specification, value)
			if valueErr != nil {
				return valueErr
			}
		}

		for _, value := range typed.Parameters {
			valueErr := appUcxValidateJobParameterInStack(specification, value)
			if valueErr != nil {
				return valueErr
			}
		}
	}

	return nil
}

func appUcxValidateJobParameterInStack(specification orcapi.ResourceSpecification, value orcapi.AppParameterValue) *util.HttpError {
	switch value.Type {
	case orcapi.AppParameterValueTypeFile:
		return nil
	case orcapi.AppParameterValueTypePeer:
		return appUcxValidateReferenceInStack(specification, jobType, value.JobId)
	case orcapi.AppParameterValueTypeBlockStorage:
		return appUcxValidateReferenceInStack(specification, driveType, value.Id)
	case orcapi.AppParameterValueTypeNetwork:
		return appUcxValidateReferenceInStack(specification, publicIpType, value.Id)
	case orcapi.AppParameterValueTypeIngress:
		return appUcxValidateReferenceInStack(specification, ingressType, value.Id)
	case orcapi.AppParameterValueTypeLicense:
		return appUcxValidateReferenceInStack(specification, licenseType, value.Id)
	case orcapi.AppParameterValueTypePrivateNetwork:
		return appUcxValidateReferenceInStack(specification, privateNetworkType, value.Id)
	}
	return nil
}

func appUcxValidateReferenceInStack(specification orcapi.ResourceSpecification, typeName string, resourceId string) *util.HttpError {
	resourceId = strings.TrimSpace(resourceId)
	if resourceId == "" {
		return nil
	}

	_, _, referencedSpec, err := ResourceRetrieveEx[any](rpc.ActorSystem, typeName, ResourceParseId(resourceId), orcapi.PermissionRead, orcapi.ResourceFlags{})
	if err != nil {
		return util.HttpErr(http.StatusBadRequest, "unknown resource requested")
	}

	return resourceValidateSameStack(specification, referencedSpec.Labels)
}

func appUcxValidateStackApplication(s *appUcxBaseState, actor rpc.Actor, specification *orcapi.ResourceSpecification) *util.HttpError {
	if s.Application == nil {
		return util.HttpErr(http.StatusForbidden, "the application is not available")
	}

	stackInstance := strings.TrimSpace(specification.ServerLabels[orcapi.ResourceLabelStackInstance])
	if stackInstance == "" {
		return nil
	}

	entity, exists := stacksFindEntity(actor, stackInstance)
	if !exists {
		return nil
	}

	_, _, stackSpec, err := ResourceRetrieveEx[orcapi.Stack](actor, stackType, ResourceParseId(entity.Id), orcapi.PermissionEdit, orcapi.ResourceFlags{})
	if err != nil {
		return err
	}

	app := s.Application()
	stackProvider := strings.TrimSpace(stackSpec.Labels[orcapi.ResourceLabelStackProvider])
	if stackProvider != "" && s.Provider != nil && stackProvider != s.Provider() {
		return util.HttpErr(http.StatusForbidden, "the stack belongs to a different provider")
	}

	stackAppName := strings.TrimSpace(stackSpec.Labels[orcapi.ResourceLabelUcxAppName])
	stackAppVersion := strings.TrimSpace(stackSpec.Labels[orcapi.ResourceLabelUcxAppVersion])
	if stackAppName == "" || stackAppVersion == "" {
		return nil
	}

	if stackAppName != app.Name || stackAppVersion != app.Version {
		return util.HttpErr(http.StatusForbidden, "the stack belongs to a different application")
	}

	return nil
}

func appUcxCreateResource[Spec any, Resc any](
	s *appUcxBaseState,
	p *ucx.Proxy,
	call ucx.Rpc[[]Spec, []Resc],
	creator func(actor rpc.Actor, specs []Spec) ([]Resc, *util.HttpError),
	baseSpecGetter func(r Resc) orcapi.ResourceSpecification,
	baseSpecGetterFromRescSpec func(r *Spec) *orcapi.ResourceSpecification,
) {
	call.HandlerProxy(p, func(ctx context.Context, request []Spec) ([]Resc, error) {
		actor := s.Actor()
		s.Mu.RLock()
		allowStackCreation := s.AllowStackCreation
		s.Mu.RUnlock()

		for i := range request {
			specification := baseSpecGetterFromRescSpec(&request[i])
			stackActor := actor
			if allowStackCreation {
				appUcxMoveServerLabels(specification)
				if err := appUcxValidateStackApplication(s, actor, specification); err != nil {
					return nil, err.AsError()
				}
			} else {
				jobStackActor, err := appUcxApplyJobStackLabels(s, specification)
				if err != nil {
					return nil, err.AsError()
				}
				stackActor = jobStackActor
			}

			prepareErr := stacksPrepareResource(stackActor, specification, !allowStackCreation)
			if prepareErr != nil {
				return nil, prepareErr.AsError()
			}

			if !allowStackCreation {
				s.Mu.RLock()
				stackEntity := s.StackEntity
				s.Mu.RUnlock()
				if !stackEntity.Present {
					return nil, util.HttpErr(http.StatusForbidden, "this job is not associated with a stack").AsError()
				}

				preparedEntity := strings.TrimSpace(specification.ServerLabels[orcapi.ResourceLabelStackEntity])
				if preparedEntity != stackEntity.Value {
					return nil, util.HttpErr(http.StatusForbidden, "the stack of this job has changed").AsError()
				}

				stackProvider := strings.TrimSpace(specification.ServerLabels[orcapi.ResourceLabelStackProvider])
				if resourceSpecificationHasProduct(*specification) && stackProvider != "" && specification.Product.Provider != stackProvider {
					return nil, util.HttpErr(http.StatusForbidden, "the resource must use the provider of the stack").AsError()
				}

				referenceErr := appUcxValidateCreateReferences(*specification, request[i])
				if referenceErr != nil {
					return nil, referenceErr.AsError()
				}
			}
		}
		created, createErr := creator(actor, request)
		if createErr != nil {
			return nil, createErr.AsError()
		}

		if allowStackCreation {
			for _, resc := range created {
				rescSpec := baseSpecGetter(resc)
				if rescSpec.Labels == nil {
					continue
				}

				stack := strings.TrimSpace(rescSpec.Labels[orcapi.ResourceLabelStackInstance])
				if stack == "" {
					continue
				}

				s.Mu.Lock()
				s.Stacks[stack] = util.Empty{}
				s.Mu.Unlock()
			}
		}
		return created, nil
	})
}

func appUcxResourceInSession[Resc any](
	s *appUcxBaseState,
	resource Resc,
	baseSpecGetter func(r Resc) orcapi.ResourceSpecification,
) bool {
	labels := baseSpecGetter(resource).Labels
	if labels == nil {
		return false
	}

	instance := strings.TrimSpace(labels[orcapi.ResourceLabelStackInstance])
	if instance == "" {
		return false
	}

	s.Mu.RLock()
	_, exists := s.Stacks[instance]
	stackEntity := s.StackEntity
	s.Mu.RUnlock()
	if !exists {
		return false
	}

	if s.SessionJob != nil {
		if !stackEntity.Present {
			return false
		}

		resourceEntity := strings.TrimSpace(labels[orcapi.ResourceLabelStackEntity])
		return resourceEntity == stackEntity.Value
	}

	return true
}

func appUcxValidateServiceMembersRequest(s *appUcxBaseState, actor rpc.Actor, reqItem orcapi.ServicesMembersRequest) error {
	svc, err := ServiceRetrieve(actor, orcapi.ServicesRetrieveRequest{Id: reqItem.Id})
	if err != nil {
		return err.AsError()
	}

	if !appUcxResourceInSession(s, svc, func(r orcapi.Service) orcapi.ResourceSpecification {
		return r.Specification.ResourceSpecification
	}) {
		return util.HttpErr(http.StatusNotFound, "not found").AsError()
	}

	for _, jobId := range reqItem.JobIds {
		job, err := JobsRetrieve(actor, jobId, orcapi.JobFlags{})
		if err != nil {
			return err.AsError()
		}

		if !appUcxResourceInSession(s, job, func(r orcapi.Job) orcapi.ResourceSpecification {
			return r.Specification.ResourceSpecification
		}) {
			return util.HttpErr(http.StatusNotFound, "not found").AsError()
		}
	}

	return nil
}

func appUcxRetrieveResource[Req any, Resc any](
	s *appUcxBaseState,
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
	s *appUcxBaseState,
	p *ucx.Proxy,
	call ucx.Rpc[fndapi.BulkRequest[ReqItem], util.Empty],
	idGetter func(request ReqItem) string,
	retrieve func(actor rpc.Actor, id string) (Resc, *util.HttpError),
	labelsOf func(request ReqItem) map[string]string,
	setLabels func(request *ReqItem, labels map[string]string),
	updateLabels func(actor rpc.Actor, request fndapi.BulkRequest[ReqItem]) *util.HttpError,
	baseSpecGetter func(r Resc) orcapi.ResourceSpecification,
) {
	call.HandlerProxy(p, func(ctx context.Context, request fndapi.BulkRequest[ReqItem]) (util.Empty, error) {
		actor := s.Actor()
		allowed := make([]ReqItem, 0, len(request.Items))

		for i := range request.Items {
			reqItem := &request.Items[i]
			resource, err := retrieve(actor, idGetter(*reqItem))
			if err != nil || !appUcxResourceInSession(s, resource, baseSpecGetter) {
				continue
			}

			normalized, validateErr := ResourceValidateLabels(labelsOf(*reqItem))
			if validateErr != nil {
				return util.Empty{}, validateErr.AsError()
			}

			_, mergeErr := resourceMergeReservedLabels(baseSpecGetter(resource).Labels, normalized, false, false)
			if mergeErr != nil {
				return util.Empty{}, mergeErr.AsError()
			}

			setLabels(reqItem, normalized)
			allowed = append(allowed, *reqItem)
		}

		if len(allowed) > 0 {
			err := updateLabels(actor, fndapi.BulkRequestOf(allowed...))
			if err != nil {
				return util.Empty{}, err
			}
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
	s *appUcxBaseState,
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
		jobStackEntity := ""
		if s.SessionJob != nil {
			s.Mu.RLock()
			stackEntity := s.StackEntity
			s.Mu.RUnlock()
			if !stackEntity.Present {
				return fndapi.PageV2[Resc]{}, util.HttpErr(http.StatusForbidden, "this job is not associated with a stack").AsError()
			}
			jobStackEntity = stackEntity.Value
		}

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
			labels[orcapi.ResourceLabelStackInstance] = stackName
			if jobStackEntity != "" {
				labels[orcapi.ResourceLabelStackEntity] = jobStackEntity
			}
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
	s *appUcxBaseState,
	p *ucx.Proxy,
	call ucx.Rpc[[]string, util.Empty],
	retrieve func(actor rpc.Actor, id string) (Resc, *util.HttpError),
	delete func(actor rpc.Actor, id string) *util.HttpError,
	baseSpecGetter func(r Resc) orcapi.ResourceSpecification,
) {
	call.HandlerProxy(p, func(ctx context.Context, request []string) (util.Empty, error) {
		actor := s.Actor()
		for _, id := range request {
			resc, err := retrieve(actor, id)
			if err != nil {
				return util.Empty{}, err
			}

			if !appUcxResourceInSession(s, resc, baseSpecGetter) {
				continue
			}

			deleteErr := delete(actor, id)
			if deleteErr != nil {
				return util.Empty{}, deleteErr
			}
		}
		return util.Empty{}, nil
	})
}

func appUcxRetrieveProducts[Supp any](
	s *appUcxBaseState,
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
