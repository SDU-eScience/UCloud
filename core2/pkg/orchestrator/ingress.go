package orchestrator

import (
	"cmp"
	"database/sql"
	"fmt"
	"net/http"
	"regexp"
	"strconv"
	"strings"
	"sync"

	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const ingressType = "ingress"

var hostnamePartRegex = regexp.MustCompile(`^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$`)

func initIngresses() {
	InitResourceType(
		ingressType,
		resourceTypeCreateWithoutAdmin,
		ingressLoad,
		ingressPersist,
		ingressTransform,
		nil,
	)

	ingressesFillIndex()
	ResourceAddIndexer(
		ingressType,
		func(r *resource) ResourceIndexer {
			return &ingressesDomainIndexer{r: r}
		},
	)

	orcapi.IngressesBrowse.Handler(func(info rpc.RequestInfo, request orcapi.IngressesBrowseRequest) (fndapi.PageV2[orcapi.Ingress], *util.HttpError) {
		return IngressBrowse(info.Actor, request), nil
	})

	orcapi.IngressesControlBrowse.Handler(func(info rpc.RequestInfo, request orcapi.IngressesControlBrowseRequest) (fndapi.PageV2[orcapi.Ingress], *util.HttpError) {
		actor, err := controlBrowseJobScoped(info, request.JobId, &request.ResourceFlags)
		if err != nil {
			return fndapi.PageV2[orcapi.Ingress]{}, err
		}

		return ResourceBrowse[orcapi.Ingress](
			actor,
			ingressType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.Ingress) bool {
				return true
			},
			nil,
		), nil
	})

	orcapi.IngressesCreate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.IngressSpecification]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		created, err := IngressCreate(info.Actor, request)
		if err != nil {
			return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
		}

		result := make([]fndapi.FindByStringId, 0, len(created))
		for _, ing := range created {
			result = append(result, fndapi.FindByStringId{Id: ing.Id})
		}

		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: result}, nil
	})

	orcapi.IngressesDelete.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		return IngressDelete(info.Actor, request)
	})

	orcapi.IngressesSearch.Handler(func(info rpc.RequestInfo, request orcapi.IngressesSearchRequest) (fndapi.PageV2[orcapi.Ingress], *util.HttpError) {
		return ResourceBrowse[orcapi.Ingress](
			info.Actor,
			ingressType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.Ingress) bool {
				if strings.Contains(item.Specification.Domain, request.Query) {
					return true
				} else {
					return false
				}
			},
			nil,
		), nil
	})

	orcapi.IngressesRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.IngressesRetrieveRequest) (orcapi.Ingress, *util.HttpError) {
		return ResourceRetrieve[orcapi.Ingress](info.Actor, ingressType, ResourceParseId(request.Id), request.ResourceFlags)
	})

	orcapi.IngressesControlRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.IngressesControlRetrieveRequest) (orcapi.Ingress, *util.HttpError) {
		actor, err := controlRetrieveJobScoped(info, request.JobId, ingressType, request.Id)
		if err != nil {
			return orcapi.Ingress{}, err
		}
		return ResourceRetrieve[orcapi.Ingress](actor, ingressType, ResourceParseId(request.Id), request.ResourceFlags)
	})

	orcapi.IngressesUpdateAcl.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.UpdatedAcl]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		for _, item := range request.Items {
			err := ResourceUpdateAcl(info.Actor, ingressType, item)
			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, err
			}
		}

		return fndapi.BulkResponse[util.Empty]{Responses: make([]util.Empty, len(request.Items))}, nil
	})

	orcapi.IngressesUpdateLabels.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.IngressesUpdateLabelsRequest]) (util.Empty, *util.HttpError) {
		return util.Empty{}, IngressUpdateLabels(info.Actor, request)
	})

	orcapi.IngressesRetrieveProducts.Handler(func(info rpc.RequestInfo, request util.Empty) (orcapi.SupportByProvider[orcapi.IngressSupport], *util.HttpError) {
		return SupportRetrieveProducts[orcapi.IngressSupport](ingressType), nil
	})

	orcapi.IngressesSetTarget.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.IngressesSetTargetRequest]) (util.Empty, *util.HttpError) {
		for _, item := range request.Items {
			err := IngressSetTarget(info.Actor, item.Id, item.Target)
			if err != nil {
				return util.Empty{}, err
			}
		}
		return util.Empty{}, nil
	})

	orcapi.IngressesControlCreate.Handler(controlCreateServe(
		func(spec *orcapi.IngressSpecification) *orcapi.ResourceSpecification {
			return &spec.ResourceSpecification
		},
		IngressCreate,
		func(created []orcapi.Ingress) fndapi.BulkResponse[fndapi.FindByStringId] {
			return controlCreateIdsOf(created, func(r orcapi.Ingress) string { return r.Id })
		},
	))

	orcapi.IngressesControlRegister.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ProviderRegisteredResource[orcapi.IngressSpecification]]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
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

			id, _, err := ResourceCreateEx[orcapi.Ingress](
				ingressType,
				orcapi.ResourceOwner{
					CreatedBy: reqItem.CreatedBy.GetOrDefault("_ucloud"),
					Project:   util.OptStringIfNotEmpty(reqItem.Project.Value),
				},
				nil,
				reqItem.Spec.ResourceSpecification,
				reqItem.ProviderGeneratedId,
				&internalIngress{
					Domain: reqItem.Spec.Domain,
				},
				flags,
			)

			if err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			} else {
				ResourceConfirm(ingressType, id)
				responses = append(responses, fndapi.FindByStringId{Id: fmt.Sprint(id)})
			}
		}

		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: responses}, nil
	})

	orcapi.IngressesControlAddUpdate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ResourceUpdateAndId[orcapi.IngressUpdate]]) (util.Empty, *util.HttpError) {
		ids := make([]string, 0, len(request.Items))
		for _, item := range request.Items {
			ids = append(ids, item.Id)
		}
		if err := ResourceValidateProviderBatch(info.Actor, ingressType, ids); err != nil {
			return util.Empty{}, err
		}

		for _, item := range request.Items {
			ok := ResourceUpdate(
				info.Actor,
				ingressType,
				ResourceParseId(item.Id),
				orcapi.PermissionProvider,
				func(r *resource, mapped orcapi.Ingress) {
					// Do nothing
				},
			)

			if !ok {
				return util.Empty{}, util.HttpErr(http.StatusNotFound, "not found or permission denied (ID: %v)", item.Id)
			}
		}

		return util.Empty{}, nil
	})

	orcapi.IngressesControlUpdateLabels.Handler(func(info rpc.RequestInfo, request orcapi.ControlMutateRequest[orcapi.IngressesUpdateLabelsRequest]) (util.Empty, *util.HttpError) {
		if request.JobId == "" {
			for _, reqItem := range request.Items {
				err := ResourceUpdateLabels(info.Actor, ingressType, reqItem.Id, reqItem.Labels, orcapi.PermissionProvider)
				if err != nil {
					return util.Empty{}, err
				}
			}

			return util.Empty{}, nil
		}

		authorize := controlMutateServe(
			ingressType,
			func(item orcapi.IngressesUpdateLabelsRequest) string { return item.Id },
			func(actor rpc.Actor, items []orcapi.IngressesUpdateLabelsRequest) (util.Empty, *util.HttpError) {
				return util.Empty{}, IngressUpdateLabels(actor, fndapi.BulkRequestOf(items...))
			},
		)
		return authorize(info, request)
	})

	orcapi.IngressesControlSetTarget.Handler(func(info rpc.RequestInfo, request orcapi.ControlMutateRequest[orcapi.IngressesSetTargetRequest]) (util.Empty, *util.HttpError) {
		actor, _, stackInstance, err := controlResolveJobActor(info.Actor, request.JobId)
		if err != nil {
			return util.Empty{}, err
		}

		for _, item := range request.Items {
			err := controlVerifyStackMembership(actor, ingressType, item.Id, stackInstance)
			if err != nil {
				return util.Empty{}, err
			}

			if target := item.Target; target.Present {
				err := controlVerifyStackMembership(actor, serviceType, target.Value.ServiceId, stackInstance)
				if err != nil {
					return util.Empty{}, err
				}
			}
		}

		for _, item := range request.Items {
			err := IngressSetTarget(actor, item.Id, item.Target)
			if err != nil {
				return util.Empty{}, err
			}
		}

		return util.Empty{}, nil
	})

	orcapi.IngressesControlDelete.Handler(func(info rpc.RequestInfo, request orcapi.IngressesControlDeleteRequest) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		actor, _, stackInstance, err := controlResolveJobActor(info.Actor, request.JobId)
		if err != nil {
			return fndapi.BulkResponse[util.Empty]{}, err
		}

		ids := make([]fndapi.FindByStringId, 0, len(request.IngressIds))
		for _, ingressId := range request.IngressIds {
			ingress, _, _, err := ResourceRetrieveEx[orcapi.Ingress](
				actor,
				ingressType,
				ResourceParseId(ingressId),
				orcapi.PermissionEdit,
				orcapi.ResourceFlags{},
			)
			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, util.HttpErr(http.StatusNotFound, "unknown public link")
			}

			if ingress.Specification.Labels[orcapi.ResourceLabelStackInstance] != stackInstance {
				return fndapi.BulkResponse[util.Empty]{}, util.HttpErr(http.StatusForbidden, "the public link does not belong to the stack of the job")
			}

			ids = append(ids, fndapi.FindByStringId{Id: ingressId})
		}

		return IngressDelete(actor, fndapi.BulkRequestOf(ids...))
	})
}

func IngressCreate(actor rpc.Actor, request fndapi.BulkRequest[orcapi.IngressSpecification]) ([]orcapi.Ingress, *util.HttpError) {
	var created []orcapi.Ingress
	for _, item := range request.Items {
		supp, ok := SupportByProduct[orcapi.IngressSupport](ingressType, item.Product)
		if !ok {
			return nil, util.HttpErr(http.StatusBadRequest, "unknown product requested")
		}

		// NOTE(Dan): Something like a prefix and a suffix should probably have gone on the product instead of the
		// support info. This is not really a feature you turn on or off.
		prefix, _ := supp.Get(ingressFeaturePrefix)
		suffix, _ := supp.Get(ingressFeatureSuffix)

		item.Domain = strings.ToLower(item.Domain)
		withoutPrefix, okPrefix := strings.CutPrefix(item.Domain, prefix)
		userToken, okSuffix := strings.CutSuffix(withoutPrefix, suffix)
		userToken = strings.ToLower(userToken)

		if !okPrefix || !okSuffix {
			return nil, util.HttpErr(
				http.StatusBadRequest,
				"domain must start with '%s' and end with '%s'",
				prefix,
				suffix,
			)
		}

		if len(userToken) < 4 {
			return nil, util.HttpErr(
				http.StatusBadRequest,
				"your domain name must be at least 4 characters long (not including prefix and suffix)",
			)
		}

		if len(item.Domain) > 253 {
			return nil, util.HttpErr(
				http.StatusBadRequest,
				"your domain name is too long",
			)
		}

		if strings.Contains(userToken, ".") {
			return nil, util.HttpErr(
				http.StatusBadRequest,
				"you cannot create further sub-domains in the URL",
			)
		}

		userTokFirst := []rune(userToken)[0]
		if userTokFirst >= '0' && userTokFirst <= '9' {
			return nil, util.HttpErr(
				http.StatusBadRequest,
				"your domain must not start with a digit",
			)
		}

		if !hostnamePartRegex.MatchString(userToken) {
			return nil, util.HttpErr(
				http.StatusBadRequest,
				"your domain name must not contain special characters",
			)
		}

		ingressesByDomain.Mu.Lock()
		_, exists := ingressesByDomain.Domains[item.Domain]
		if !exists {
			ingressesByDomain.Domains[item.Domain] = ResourceId(0) // placeholder to ensure that the spot is reserved
		}
		ingressesByDomain.Mu.Unlock()
		if exists {
			return nil, util.HttpErr(
				http.StatusBadRequest,
				"your domain name is not unique, try a different one",
			)
		}

		ing, err := ResourceCreateThroughProvider(
			actor,
			ingressType,
			item.ResourceSpecification,
			&internalIngress{
				Domain: item.Domain,
			},
			orcapi.IngressesProviderCreate,
		)

		if err != nil {
			ingressesByDomain.Mu.Lock()
			if ingressesByDomain.Domains[item.Domain] == ResourceId(0) {
				delete(ingressesByDomain.Domains, item.Domain)
			}
			ingressesByDomain.Mu.Unlock()

			return nil, err
		}

		if item.Target.Present {
			err = IngressSetTarget(actor, ing.Id, item.Target)
			if err != nil {
				_, _ = IngressDelete(actor, fndapi.BulkRequestOf(fndapi.FindByStringId{Id: ing.Id}))
				return nil, err
			}

			updated, retrieveErr := ResourceRetrieve[orcapi.Ingress](actor, ingressType, ResourceParseId(ing.Id), orcapi.ResourceFlags{})
			if retrieveErr != nil {
				return nil, retrieveErr
			}

			created = append(created, updated)
		} else {
			created = append(created, ing)
		}
	}

	return created, nil
}

func IngressBrowse(actor rpc.Actor, request orcapi.IngressesBrowseRequest) fndapi.PageV2[orcapi.Ingress] {
	sortByFn := ResourceDefaultComparator(func(item orcapi.Ingress) orcapi.Resource {
		return item.Resource
	}, request.ResourceFlags)

	switch request.SortBy.GetOrDefault("") {
	case "":
		sortByFn = func(a orcapi.Ingress, b orcapi.Ingress) int {
			return cmp.Compare(strings.ToLower(a.Specification.Domain), strings.ToLower(b.Specification.Domain))
		}
	}

	return ResourceBrowse[orcapi.Ingress](
		actor,
		ingressType,
		request.Next,
		request.ItemsPerPage,
		request.ResourceFlags,
		func(item orcapi.Ingress) bool {
			return true
		},
		sortByFn,
	)
}

func IngressDelete(actor rpc.Actor, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
	for _, item := range request.Items {
		err := ResourceDeleteThroughProvider[orcapi.Ingress](
			actor,
			ingressType,
			item.Id,
			orcapi.IngressesProviderDelete,
		)

		if err != nil {
			return fndapi.BulkResponse[util.Empty]{}, err
		}
	}

	return fndapi.BulkResponse[util.Empty]{Responses: make([]util.Empty, len(request.Items))}, nil
}

func IngressUpdateLabels(actor rpc.Actor, request fndapi.BulkRequest[orcapi.IngressesUpdateLabelsRequest]) *util.HttpError {
	for _, reqItem := range request.Items {
		err := ResourceUpdateLabelsThroughProvider[orcapi.Ingress](
			actor,
			ingressType,
			reqItem.Id,
			reqItem.Labels,
			func(t *orcapi.Ingress, labels map[string]string) {
				t.Specification.Labels = labels
			},
			orcapi.IngressesProviderOnUpdatedLabels,
		)

		if err != nil {
			return err
		}
	}

	return nil
}

var ingressesByDomain struct {
	Mu      sync.RWMutex
	Domains map[string]ResourceId
}

type ingressesDomainIndexer struct {
	r *resource
}

func (i *ingressesDomainIndexer) Begin() {
	ingressesByDomain.Mu.Lock()
}

func (i *ingressesDomainIndexer) Add() {
	ing := i.r.Extra.(*internalIngress)
	ingressesByDomain.Domains[ing.Domain] = i.r.Id
}

func (i *ingressesDomainIndexer) Remove() {
	ing := i.r.Extra.(*internalIngress)
	delete(ingressesByDomain.Domains, ing.Domain)
}

func (i *ingressesDomainIndexer) Commit() {
	ingressesByDomain.Mu.Unlock()
}

func ingressesFillIndex() {
	if resourceGlobals.Testing.Enabled {
		ingressesByDomain.Domains = map[string]ResourceId{}
		return
	}

	db.NewTx0(func(tx *db.Transaction) {
		ingressesByDomain.Domains = map[string]ResourceId{}

		rows := db.Select[struct {
			Id     int
			Domain string
		}](
			tx,
			`
				select i.resource as id, i.domain
				from app_orchestrator.ingresses i
		    `,
			db.Params{},
		)

		for _, row := range rows {
			ingressesByDomain.Domains[row.Domain] = ResourceId(row.Id)
		}
	})
}

type internalIngress struct {
	Domain        string
	BoundTo       []string
	TargetService util.Option[ResourceId]
	TargetPort    util.Option[string]
}

func ingressLoad(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource) {
	rows := db.Select[struct {
		Domain        string
		Resource      int
		StatusBoundTo []int
		TargetService sql.Null[int64]
		TargetPort    sql.Null[string]
	}](
		tx,
		`
			select domain, resource, status_bound_to, target_service, target_port
			from app_orchestrator.ingresses
			where resource = some(:ids::int8[])
	    `,
		db.Params{
			"ids": ids,
		},
	)

	for _, row := range rows {
		var boundTo []string
		for _, jobId := range row.StatusBoundTo {
			boundTo = append(boundTo, fmt.Sprint(jobId))
		}

		ing := &internalIngress{
			Domain:  row.Domain,
			BoundTo: boundTo,
		}

		if row.TargetService.Valid {
			ing.TargetService = util.OptValue(ResourceId(row.TargetService.V))
		}

		if row.TargetPort.Valid {
			ing.TargetPort = util.OptValue(row.TargetPort.V)
		}

		resources[ResourceId(row.Resource)].Extra = ing
	}
}

func ingressPersist(b *db.Batch, r *resource) {
	if r.MarkedForDeletion {
		db.BatchExec(
			b,
			`delete from app_orchestrator.ingresses where resource = :id`,
			db.Params{
				"id": r.Id,
			},
		)
	} else {
		ing := r.Extra.(*internalIngress)

		boundTo := []int64{}
		for _, jobId := range ing.BoundTo {
			id, _ := strconv.ParseInt(jobId, 10, 64)
			boundTo = append(boundTo, id)
		}

		db.BatchExec(
			b,
			`
				insert into app_orchestrator.ingresses(domain, current_state, resource, status_bound_to, target_service, target_port)
				values (:domain, 'READY', :id, :bound_to, :target_service, :target_port)
				on conflict (resource) do update set domain = excluded.domain, status_bound_to = excluded.status_bound_to,
					target_service = excluded.target_service, target_port = excluded.target_port
			`,
			db.Params{
				"domain":         ing.Domain,
				"id":             r.Id,
				"bound_to":       boundTo,
				"target_service": ing.TargetService.Sql(),
				"target_port":    ing.TargetPort.Sql(),
			},
		)
	}
}

func ingressTransform(r orcapi.Resource, specification orcapi.ResourceSpecification, extra any, flags orcapi.ResourceFlags, actor rpc.Actor) any {
	ing := extra.(*internalIngress)
	result := orcapi.Ingress{
		Resource: r,
		Specification: orcapi.IngressSpecification{
			Domain:                ing.Domain,
			ResourceSpecification: specification,
		},
		Status: orcapi.IngressStatus{
			BoundTo: util.NonNilSlice(ing.BoundTo),
			State:   "READY",
		},
	}

	if ing.TargetService.Present && ing.TargetPort.Present {
		result.Specification.Target = util.OptValue(orcapi.PublicLinkServiceTarget{
			ServiceId: fmt.Sprint(ing.TargetService.Value),
			Port:      ing.TargetPort.Value,
		})
	}

	if (flags.IncludeProduct || flags.IncludeSupport) && resourceSpecificationHasProduct(specification) {
		support, _ := SupportByProduct[orcapi.IngressSupport](ingressType, specification.Product)
		result.Status.ResourceStatus = orcapi.ResourceStatus[orcapi.IngressSupport]{
			ResolvedSupport: util.OptValue(support.ToApi()),
			ResolvedProduct: util.OptValue(support.Product),
		}
	}
	return result
}

func IngressBind(id string, jobId string) {
	ResourceUpdate[orcapi.Ingress](
		rpc.ActorSystem,
		ingressType,
		ResourceParseId(id),
		orcapi.PermissionRead,
		func(r *resource, mapped orcapi.Ingress) {
			ip := r.Extra.(*internalIngress)
			ip.BoundTo = []string{jobId}
		},
	)
}

func IngressUnbind(id string, jobId string) {
	ResourceUpdate[orcapi.Ingress](
		rpc.ActorSystem,
		ingressType,
		ResourceParseId(id),
		orcapi.PermissionRead,
		func(r *resource, mapped orcapi.Ingress) {
			ip := r.Extra.(*internalIngress)
			ip.BoundTo = util.RemoveFirst(ip.BoundTo, jobId)
		},
	)
}

func ingressValidateServiceTarget(actor rpc.Actor, ingressProvider string, target orcapi.PublicLinkServiceTarget) (ResourceId, *util.HttpError) {
	svc, _, _, err := ResourceRetrieveEx[orcapi.Service](
		actor,
		serviceType,
		ResourceParseId(target.ServiceId),
		orcapi.PermissionEdit,
		orcapi.ResourceFlags{},
	)
	if err != nil {
		return 0, util.HttpErr(http.StatusForbidden, "you cannot use this service")
	}

	if svc.Specification.Product.Provider != ingressProvider {
		return 0, util.HttpErr(http.StatusBadRequest, "the service belongs to a different provider")
	}

	var matchedPort *orcapi.ServicePort
	for i, port := range svc.Specification.Ports {
		if port.Name == target.Port {
			matchedPort = &svc.Specification.Ports[i]
			break
		}
	}

	if matchedPort == nil {
		return 0, util.HttpErr(http.StatusBadRequest, "the service does not declare a port named '%v'", target.Port)
	}

	if matchedPort.Protocol != orcapi.ServicePortProtocolTcp {
		return 0, util.HttpErr(http.StatusBadRequest, "a public link can only target a TCP port")
	}

	appProto := matchedPort.ApplicationProtocol
	if !appProto.Present || (appProto.Value != "HTTP" && appProto.Value != "HTTPS") {
		return 0, util.HttpErr(http.StatusBadRequest, "a public link can only target a port with applicationProtocol HTTP or HTTPS")
	}

	return ResourceParseId(svc.Id), nil
}

func IngressSetTarget(actor rpc.Actor, id string, target util.Option[orcapi.PublicLinkServiceTarget]) *util.HttpError {
	ing, _, _, err := ResourceRetrieveEx[orcapi.Ingress](
		actor,
		ingressType,
		ResourceParseId(id),
		orcapi.PermissionEdit,
		orcapi.ResourceFlags{},
	)
	if err != nil {
		return err
	}

	if len(ing.Status.BoundTo) > 0 {
		return util.HttpErr(http.StatusConflict, "this link is currently in use by job: %v", strings.Join(ing.Status.BoundTo, ", "))
	}

	provider := ing.Specification.Product.Provider

	var newServiceId util.Option[ResourceId]
	var newPort util.Option[string]

	if target.Present {
		serviceId, err := ingressValidateServiceTarget(actor, provider, target.Value)
		if err != nil {
			return err
		}

		newServiceId = util.OptValue(serviceId)
		newPort = util.OptValue(target.Value.Port)
	}

	providerIngress := ing
	providerIngress.Specification.Target = target

	_, err = InvokeProvider(provider, orcapi.IngressesProviderSetTarget, fndapi.BulkRequestOf(orcapi.IngressesProviderSetTargetRequest{
		Ingress: providerIngress,
		Target:  target,
	}), ProviderCallOpts{
		Username: util.OptValue(actor.Username),
		Reason:   util.OptValue("Setting public link target"),
	})

	if err != nil {
		return err
	}

	ok := ResourceUpdate(
		actor,
		ingressType,
		ResourceParseId(id),
		orcapi.PermissionEdit,
		func(r *resource, mapped orcapi.Ingress) {
			ingInternal := r.Extra.(*internalIngress)
			ingInternal.TargetService = newServiceId
			ingInternal.TargetPort = newPort
		},
	)

	if !ok {
		return util.HttpErr(http.StatusNotFound, "not found or permission denied")
	}

	return nil
}
