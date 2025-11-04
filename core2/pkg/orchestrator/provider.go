package orchestrator

import (
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const providerType = "provider"

func initProvider() {
	InitResourceType(
		providerType,
		resourceTypeCreateWithoutAdmin,
		providerLoad,
		providerPersist,
		providerTransform,
		nil,
	)

	orcapi.ProviderCreate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ProviderSpecification]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		var result []fndapi.FindByStringId
		for _, item := range request.Items {
			_, provider, err := ResourceCreate[orcapi.Provider](
				info.Actor,
				providerType,
				util.OptNone[accapi.ProductReference](),
				&internalProvider{
					UniqueName:   item.Id,
					Domain:       item.Domain,
					Https:        item.Https,
					Port:         item.Port,
					RefreshToken: "", //TODO
					PublicKey:    "", //TODO
				},
			)

			if err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			}
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
				//TODO something
				return true
			},
		), nil
	})

	orcapi.ProviderBrowse.Handler(func(info rpc.RequestInfo, request orcapi.ProviderBrowseRequest) (fndapi.PageV2[orcapi.Provider], *util.HttpError) {
		return ResourceBrowse[orcapi.Provider](
			info.Actor,
			providerType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.Provider) bool { return true },
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

	orcapi.ProviderUpdate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ProviderSpecification]) (fndapi.BulkRequest[fndapi.FindByStringId], *util.HttpError) {
		ResourceUpdate()
	})

	orcapi.ProviderRenewToken.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ProviderSpecification]) (util.Empty, *util.HttpError) {
		//TODO
	})

	orcapi.ProviderRetrieveSpecification.Handler(func(info rpc.RequestInfo, request fndapi.FindByStringId) (orcapi.ProviderSpecification, *util.HttpError) {

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
		`select unique_name, domain, https, port, refresh_token, public_key 
		 from provider.providers
		 where resource = some(:ids::int8[])`,
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
		db.BatchExec(
			b,
			`delete from provider.providers where resource = :id`,
			db.Params{
				"id": r.Id,
			},
		)
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
) any {
	provider := extra.(*internalProvider)
	result := orcapi.Provider{
		Resource:      r,
		Specification: orcapi.ProviderSpecification{},
		RefreshToken:  provider.RefreshToken,
		PublicKey:     provider.PublicKey,
	}

	return result
}
