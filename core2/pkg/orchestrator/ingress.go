package orchestrator

import (
	"fmt"
	"net/http"
	"regexp"
	"strconv"
	"strings"
	"sync"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const ingressType = "ingress"

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
		return ResourceBrowse[orcapi.Ingress](
			info.Actor,
			ingressType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.Ingress) bool {
				return true
			},
		), nil
	})

	orcapi.IngressesControlBrowse.Handler(func(info rpc.RequestInfo, request orcapi.IngressesControlBrowseRequest) (fndapi.PageV2[orcapi.Ingress], *util.HttpError) {
		return ResourceBrowse[orcapi.Ingress](
			info.Actor,
			ingressType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.Ingress) bool {
				return true
			},
		), nil
	})

	hostnamePartRegex := regexp.MustCompile(`^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$`)

	orcapi.IngressesCreate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.IngressSpecification]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		// TODO Check if we have an allocation?
		var result []fndapi.FindByStringId
		for _, item := range request.Items {
			supp, ok := SupportByProduct[orcapi.IngressSupport](ingressType, item.Product)
			if !ok {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(http.StatusBadRequest, "unknown product requested")
			}

			// NOTE(Dan): Something like a prefix and a suffix should probably have gone on the product instead of the
			// support info. This is not really a feature you turn on or off.
			prefix, _ := supp.Get(ingressFeaturePrefix)
			suffix, _ := supp.Get(ingressFeatureSuffix)

			withoutPrefix, okPrefix := strings.CutPrefix(item.Domain, prefix)
			userToken, okSuffix := strings.CutSuffix(withoutPrefix, suffix)
			userToken = strings.ToLower(userToken)

			if !okPrefix || !okSuffix {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(
					http.StatusBadRequest,
					"domain must start with '%s' and end with '%s'",
					prefix,
					suffix,
				)
			}

			if len(userToken) < 4 {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(
					http.StatusBadRequest,
					"your domain name must be at least 4 characters long (not including prefix and suffix)",
				)
			}

			if len(item.Domain) > 253 {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(
					http.StatusBadRequest,
					"your domain name is too long",
				)
			}

			if strings.Contains(userToken, ".") {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(
					http.StatusBadRequest,
					"you cannot create further sub-domains in the URL",
				)
			}

			userTokFirst := []rune(userToken)[0]
			if userTokFirst >= '0' && userTokFirst <= '9' {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(
					http.StatusBadRequest,
					"your domain must not start with a digit",
				)
			}

			if !hostnamePartRegex.MatchString(userToken) {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(
					http.StatusBadRequest,
					"your domain name must not contain special characters",
				)
			}

			ingressesByDomain.Mu.RLock()
			_, exists := ingressesByDomain.Domains[item.Domain]
			ingressesByDomain.Mu.RUnlock()
			if exists {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(
					http.StatusBadRequest,
					"your domain name is not unique, try a different one",
				)
			}

			ing, err := ResourceCreateThroughProvider(
				info.Actor,
				ingressType,
				item.Product,
				&internalIngress{
					Domain: item.Domain,
				},
				orcapi.IngressesProviderCreate,
			)

			if err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			} else {
				result = append(result, fndapi.FindByStringId{Id: ing.Id})
			}
		}

		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: result}, nil
	})

	orcapi.IngressesDelete.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		for _, item := range request.Items {
			err := ResourceDeleteThroughProvider[orcapi.Ingress](
				info.Actor,
				ingressType,
				item.Id,
				orcapi.IngressesProviderDelete,
			)

			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, err
			}
		}

		return fndapi.BulkResponse[util.Empty]{Responses: make([]util.Empty, len(request.Items))}, nil
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
					// TODO More?
					return false
				}
			},
		), nil
	})

	orcapi.IngressesRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.IngressesRetrieveRequest) (orcapi.Ingress, *util.HttpError) {
		return ResourceRetrieve[orcapi.Ingress](info.Actor, ingressType, ResourceParseId(request.Id), request.ResourceFlags)
	})

	orcapi.IngressesControlRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.IngressesControlRetrieveRequest) (orcapi.Ingress, *util.HttpError) {
		return ResourceRetrieve[orcapi.Ingress](info.Actor, ingressType, ResourceParseId(request.Id), request.ResourceFlags)
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

	orcapi.IngressesRetrieveProducts.Handler(func(info rpc.RequestInfo, request util.Empty) (orcapi.SupportByProvider[orcapi.IngressSupport], *util.HttpError) {
		return SupportRetrieveProducts[orcapi.IngressSupport](ingressType), nil
	})

	orcapi.IngressesControlRegister.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ProviderRegisteredResource[orcapi.PublicIPSpecification]]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		// TODO
		return fndapi.BulkResponse[fndapi.FindByStringId]{}, nil
	})

	orcapi.IngressesControlAddUpdate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ResourceUpdateAndId[orcapi.IngressUpdate]]) (util.Empty, *util.HttpError) {
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
	Domain  string
	BoundTo []string
}

func ingressLoad(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource) {
	rows := db.Select[struct {
		Domain        string
		Resource      int
		StatusBoundTo []int
	}](
		tx,
		`
			select domain, resource, status_bound_to
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

		resources[ResourceId(row.Resource)].Extra = &internalIngress{
			Domain:  row.Domain,
			BoundTo: boundTo, // TODO Update this when job starts
		}
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
				insert into app_orchestrator.ingresses(domain, current_state, resource, status_bound_to)
				values (:domain, 'READY', :id, :bound_to)
				on conflict (resource) do update set domain = excluded.domain, status_bound_to = excluded.status_bound_to
			`,
			db.Params{
				"domain":   ing.Domain,
				"id":       r.Id,
				"bound_to": boundTo,
			},
		)
	}
}

func ingressTransform(r orcapi.Resource, product util.Option[accapi.ProductReference], extra any, flags orcapi.ResourceFlags) any {
	// TODO Resolved product and support
	ing := extra.(*internalIngress)
	return orcapi.Ingress{
		Resource: r,
		Specification: orcapi.IngressSpecification{
			Domain:  ing.Domain,
			Product: product.Value,
		},
		Status: orcapi.IngressStatus{
			BoundTo: util.NonNilSlice(ing.BoundTo),
			State:   "READY",
		},
	}
}
