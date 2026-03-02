package orchestrator

import (
	"net/http"
	"regexp"
	"sync"

	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const providerType = "provider"

func initProviderManagement() {
	InitResourceType(
		providerType,
		resourceTypeCreateAsAllocator,
		providerLoad,
		providerPersist,
		providerTransform,
		nil,
	)

	providersFillNameIndex()
	ResourceAddIndexer(
		providerType,
		func(r *resource) ResourceIndexer {
			return &providerNameIndexer{r: r}
		},
	)

	orcapi.ProviderCreate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ProviderSpecification]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		if !info.Actor.Project.Present {
			return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(http.StatusBadRequest, "Project not set")
		}

		for _, item := range request.Items {
			if !providerIsValidHostname(item.Domain) {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(http.StatusBadRequest, "Domain not valid")
			}

			if !providerIsValidPort(item.Port) {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(http.StatusBadRequest, "Port not valid")
			}

			if item.Id == "" {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(http.StatusBadRequest, "Must enter a name for provider")
			}

			providersByName.Mu.RLock()
			_, exists := providersByName.ByName[item.Id]
			providersByName.Mu.RUnlock()

			if exists {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(http.StatusConflict, "Provider already exists with that name")
			}
		}

		var result []fndapi.FindByStringId
		for _, item := range request.Items {
			resp, err := fndapi.AuthProvidersRenew.Invoke(
				fndapi.BulkRequestOf(
					fndapi.FindByStringId{Id: item.Id},
				),
			)

			if err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			}

			pubKeyAndToken := resp.Responses[0]

			_, provider, err := ResourceCreate[orcapi.Provider](
				info.Actor,
				providerType,
				util.OptNone[accapi.ProductReference](),
				&internalProvider{
					UniqueName:   item.Id,
					Domain:       item.Domain,
					Https:        item.Https,
					Port:         item.Port,
					RefreshToken: pubKeyAndToken.RefreshToken,
					PublicKey:    pubKeyAndToken.PublicKey,
				},
			)

			if err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			}

			ResourceConfirm(providerType, ResourceParseId(provider.Id))
			result = append(result, fndapi.FindByStringId{Id: provider.Id})
		}

		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: result}, nil
	})

	orcapi.ProviderSearch.Handler(func(info rpc.RequestInfo, request orcapi.ProviderSearchRequest) (fndapi.PageV2[orcapi.Provider], *util.HttpError) {
		return ResourceBrowse[orcapi.Provider](
			info.Actor,
			providerType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.Provider) bool {
				return true
			},
			nil,
		), nil
	})

	orcapi.ProviderBrowse.Handler(func(info rpc.RequestInfo, request orcapi.ProviderBrowseRequest) (fndapi.PageV2[orcapi.Provider], *util.HttpError) {
		sortByFn := ResourceDefaultComparator(func(item orcapi.Provider) orcapi.Resource {
			return item.Resource
		}, request.ResourceFlags)

		return ResourceBrowse[orcapi.Provider](
			info.Actor,
			providerType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.Provider) bool { return true },
			sortByFn,
		), nil
	})

	orcapi.ProviderRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.ProviderRetrieveRequest) (orcapi.Provider, *util.HttpError) {
		return ResourceRetrieve[orcapi.Provider](
			info.Actor,
			providerType,
			ResourceParseId(request.Id),
			request.ResourceFlags,
		)
	})

	orcapi.ProviderUpdate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ProviderSpecification]) (util.Empty, *util.HttpError) {
		for _, item := range request.Items {
			if !providerIsValidHostname(item.Domain) {
				return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Domain not valid")
			}
			if !providerIsValidPort(item.Port) {
				return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Port not valid")
			}
		}

		for _, item := range request.Items {
			ResourceUpdate(
				info.Actor,
				providerType,
				ResourceParseId(item.Id),
				orcapi.PermissionAdmin,
				func(r *resource, mapped orcapi.Provider) {
					provider := r.Extra.(*internalProvider)
					provider.Https = item.Https
					provider.Port = item.Port
					provider.Domain = item.Domain
				},
			)
		}
		return util.Empty{}, nil
	})

	orcapi.ProviderRenewToken.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (util.Empty, *util.HttpError) {
		for _, item := range request.Items {
			_, _, _, err := ResourceRetrieveEx[orcapi.Provider](info.Actor, providerType, ResourceParseId(item.Id),
				orcapi.PermissionAdmin, orcapi.ResourceFlags{})
			if err != nil {
				return util.Empty{}, err
			}

			resp, err := fndapi.AuthProvidersRenew.Invoke(
				fndapi.BulkRequestOf(
					fndapi.FindByStringId{Id: item.Id},
				),
			)

			if err != nil {
				return util.Empty{}, err
			}

			pubKeyAndToken := resp.Responses[0]

			ResourceUpdate(
				info.Actor,
				providerType,
				ResourceParseId(item.Id),
				orcapi.PermissionAdmin,
				func(r *resource, mapped orcapi.Provider) {
					provider := r.Extra.(*internalProvider)
					provider.PublicKey = pubKeyAndToken.PublicKey
					provider.RefreshToken = pubKeyAndToken.RefreshToken
				},
			)
		}
		return util.Empty{}, nil
	})

	orcapi.ProviderRetrieveSpecification.Handler(func(info rpc.RequestInfo, request fndapi.FindByStringId) (orcapi.ProviderSpecification, *util.HttpError) {
		provider, err := ResourceRetrieve[orcapi.Provider](
			rpc.ActorSystem,
			providerType,
			ResourceParseId(request.Id),
			orcapi.ResourceFlags{},
		)
		if err != nil {
			return orcapi.ProviderSpecification{}, err
		}
		return provider.Specification, nil
	})

	orcapi.ProviderUpdateAcl.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.UpdatedAcl]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		for _, item := range request.Items {
			err := ResourceUpdateAcl(
				info.Actor,
				providerType,
				item,
			)
			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, err
			}
		}
		return fndapi.BulkResponse[util.Empty]{Responses: make([]util.Empty, len(request.Items))}, nil
	})
}

type internalProvider struct {
	UniqueName   string
	Domain       string
	Https        bool
	Port         int
	RefreshToken string
	PublicKey    string
}

func providerLoad(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource) {
	rows := db.Select[struct {
		UniqueName   string
		Domain       string
		Https        bool
		Port         int
		RefreshToken string
		PublicKey    string
		Resource     int
	}](
		tx,
		`
			select unique_name, domain, https, port, refresh_token, public_key, resource 
			 from provider.providers
			 where resource = some(:ids::int8[])
		`,
		db.Params{
			"ids": ids,
		},
	)
	for _, row := range rows {
		result := &internalProvider{
			UniqueName:   row.UniqueName,
			Domain:       row.Domain,
			Https:        row.Https,
			Port:         row.Port,
			RefreshToken: row.RefreshToken,
			PublicKey:    row.PublicKey,
		}
		resources[ResourceId(row.Resource)].Extra = result
	}
}

func providerPersist(b *db.Batch, r *resource) {
	if r.MarkedForDeletion {
		log.Fatal("Provider must not be deleted")
	} else {
		provider := r.Extra.(*internalProvider)
		db.BatchExec(
			b,
			`insert into provider.providers(unique_name, domain, https, port, refresh_token, public_key, resource)
			 values (:unique_name, :domain, :https, :port, :refresh_token, :public_key, :id)
			 on conflict (resource) do update set resource=excluded.resource`,
			db.Params{
				"unique_name":   provider.UniqueName,
				"domain":        provider.Domain,
				"https":         provider.Https,
				"port":          provider.Port,
				"refresh_token": provider.RefreshToken,
				"public_key":    provider.PublicKey,
				"id":            r.Id,
			},
		)
	}
}

func providerTransform(
	r orcapi.Resource,
	product util.Option[accapi.ProductReference],
	extra any,
	flags orcapi.ResourceFlags,
	actor rpc.Actor,
) any {
	provider := extra.(*internalProvider)
	result := orcapi.Provider{
		Resource: r,
		Specification: orcapi.ProviderSpecification{
			Id:     provider.UniqueName,
			Domain: provider.Domain,
			Https:  provider.Https,
			Port:   provider.Port,
		},
		RefreshToken: provider.RefreshToken,
		PublicKey:    provider.PublicKey,
	}

	return result
}

var hostnameRegex = regexp.MustCompile(`^(?i:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)(?:\.(?i:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?))*$`)

func providerIsValidHostname(host string) bool {
	if len(host) > 255 {
		return false
	}
	return hostnameRegex.MatchString(host)
}

func providerIsValidPort(port int) bool {
	return port > 0 && port <= 65535
}

var providersByName struct {
	Mu     sync.RWMutex
	ByName map[string]ResourceId
}

type providerNameIndexer struct {
	r *resource
}

func (i *providerNameIndexer) Begin() {
	providersByName.Mu.Lock()
}

func (i *providerNameIndexer) Add() {
	ing := i.r.Extra.(*internalProvider)
	providersByName.ByName[ing.UniqueName] = i.r.Id
}

func (i *providerNameIndexer) Remove() {
	ing := i.r.Extra.(*internalProvider)
	delete(providersByName.ByName, ing.UniqueName)
}

func (i *providerNameIndexer) Commit() {
	providersByName.Mu.Unlock()
}

func providersFillNameIndex() {
	if resourceGlobals.Testing.Enabled {
		providersByName.ByName = map[string]ResourceId{}

		return
	}

	db.NewTx0(func(tx *db.Transaction) {
		providersByName.ByName = map[string]ResourceId{}
		rows := db.Select[struct {
			Resource   int
			UniqueName string
		}](
			tx,
			`
				select unique_name, resource
				from provider.providers
			`,
			db.Params{},
		)

		for _, row := range rows {
			providersByName.ByName[row.UniqueName] = ResourceId(row.Resource)
		}
	})
}
