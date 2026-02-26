package orchestrator

import (
	"fmt"
	"net/http"
	"regexp"
	"strings"

	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const privateNetworkType = "private_network"

func initPrivateNetworks() {
	InitResourceType(
		privateNetworkType,
		resourceTypeCreateWithoutAdmin,
		privateNetworkLoad,
		privateNetworkPersist,
		privateNetworkTransform,
		nil,
	)

	orcapi.PrivateNetworksBrowse.Handler(func(info rpc.RequestInfo, request orcapi.PrivateNetworksBrowseRequest) (fndapi.PageV2[orcapi.PrivateNetwork], *util.HttpError) {
		sortByFn := ResourceDefaultComparator(func(item orcapi.PrivateNetwork) orcapi.Resource {
			return item.Resource
		}, request.ResourceFlags)

		return ResourceBrowse[orcapi.PrivateNetwork](
			info.Actor,
			privateNetworkType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.PrivateNetwork) bool {
				return true
			},
			sortByFn,
		), nil
	})

	orcapi.PrivateNetworksControlBrowse.Handler(func(info rpc.RequestInfo, request orcapi.PrivateNetworksControlBrowseRequest) (fndapi.PageV2[orcapi.PrivateNetwork], *util.HttpError) {
		return ResourceBrowse[orcapi.PrivateNetwork](
			info.Actor,
			privateNetworkType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.PrivateNetwork) bool {
				return true
			},
			nil,
		), nil
	})

	orcapi.PrivateNetworksCreate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.PrivateNetworkSpecification]) (fndapi.BulkResponse[orcapi.PrivateNetwork], *util.HttpError) {
		var responses []orcapi.PrivateNetwork

		for _, item := range request.Items {
			_, ok := SupportByProduct[orcapi.PrivateNetworkSupport](privateNetworkType, item.Product)
			if !ok {
				return fndapi.BulkResponse[orcapi.PrivateNetwork]{}, util.HttpErr(http.StatusBadRequest, "unsupported operation")
			}

			item.Name = strings.TrimSpace(item.Name)
			item.Subdomain = strings.ToLower(strings.TrimSpace(item.Subdomain))

			err := privateNetworkValidateSpecification(item)
			if err != nil {
				return fndapi.BulkResponse[orcapi.PrivateNetwork]{}, err
			}

			network, err := ResourceCreateThroughProvider(
				info.Actor,
				privateNetworkType,
				item.Product,
				&internalPrivateNetwork{
					Name:      item.Name,
					Subdomain: item.Subdomain,
				},
				orcapi.PrivateNetworksProviderCreate,
			)

			if err != nil {
				return fndapi.BulkResponse[orcapi.PrivateNetwork]{}, err
			}

			responses = append(responses, network)
		}

		return fndapi.BulkResponse[orcapi.PrivateNetwork]{Responses: responses}, nil
	})

	orcapi.PrivateNetworksDelete.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (util.Empty, *util.HttpError) {
		for _, item := range request.Items {
			err := ResourceDeleteThroughProvider(info.Actor, privateNetworkType, item.Id, orcapi.PrivateNetworksProviderDelete)
			if err != nil {
				return util.Empty{}, err
			}
		}

		return util.Empty{}, nil
	})

	orcapi.PrivateNetworksSearch.Handler(func(info rpc.RequestInfo, request orcapi.PrivateNetworksSearchRequest) (fndapi.PageV2[orcapi.PrivateNetwork], *util.HttpError) {
		query := strings.ToLower(request.Query)
		return ResourceBrowse[orcapi.PrivateNetwork](
			info.Actor,
			privateNetworkType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.PrivateNetwork) bool {
				name := strings.ToLower(item.Specification.Name)
				subdomain := strings.ToLower(item.Specification.Subdomain)

				if strings.Contains(name, query) {
					return true
				}

				return strings.Contains(subdomain, query)
			},
			nil,
		), nil
	})

	orcapi.PrivateNetworksRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.PrivateNetworksRetrieveRequest) (orcapi.PrivateNetwork, *util.HttpError) {
		return ResourceRetrieve[orcapi.PrivateNetwork](info.Actor, privateNetworkType, ResourceParseId(request.Id), request.ResourceFlags)
	})

	orcapi.PrivateNetworksControlRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.PrivateNetworksControlRetrieveRequest) (orcapi.PrivateNetwork, *util.HttpError) {
		return ResourceRetrieve[orcapi.PrivateNetwork](info.Actor, privateNetworkType, ResourceParseId(request.Id), request.ResourceFlags)
	})

	orcapi.PrivateNetworksUpdateAcl.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.UpdatedAcl]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		for _, item := range request.Items {
			err := ResourceUpdateAcl(info.Actor, privateNetworkType, item)
			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, err
			}
		}

		return fndapi.BulkResponse[util.Empty]{Responses: make([]util.Empty, len(request.Items))}, nil
	})

	orcapi.PrivateNetworksRetrieveProducts.Handler(func(info rpc.RequestInfo, request util.Empty) (orcapi.SupportByProvider[orcapi.PrivateNetworkSupport], *util.HttpError) {
		return SupportRetrieveProducts[orcapi.PrivateNetworkSupport](privateNetworkType), nil
	})

	orcapi.PrivateNetworksControlRegister.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ProviderRegisteredResource[orcapi.PrivateNetworkSpecification]]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		var responses []fndapi.FindByStringId

		for _, reqItem := range request.Items {
			spec := reqItem.Spec
			spec.Name = strings.TrimSpace(spec.Name)
			spec.Subdomain = strings.ToLower(strings.TrimSpace(spec.Subdomain))

			err := privateNetworkValidateSpecification(spec)
			if err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			}

			var flags resourceCreateFlags
			if reqItem.ProjectAllRead {
				flags |= resourceCreateAllRead
			}

			if reqItem.ProjectAllWrite {
				flags |= resourceCreateAllWrite
			}

			id, _, err := ResourceCreateEx[orcapi.PrivateNetwork](
				privateNetworkType,
				orcapi.ResourceOwner{
					CreatedBy: reqItem.CreatedBy.GetOrDefault("_ucloud"),
					Project:   util.OptStringIfNotEmpty(reqItem.Project.Value),
				},
				nil,
				util.OptValue(reqItem.Spec.Product),
				reqItem.ProviderGeneratedId,
				&internalPrivateNetwork{
					Name:      spec.Name,
					Subdomain: spec.Subdomain,
				},
				flags,
			)

			if err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			}

			ResourceConfirm(privateNetworkType, id)
			responses = append(responses, fndapi.FindByStringId{Id: fmt.Sprint(id)})
		}

		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: responses}, nil
	})
}

type internalPrivateNetwork struct {
	Name      string
	Subdomain string
}

func privateNetworkLoad(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource) {
	rows := db.Select[struct {
		Resource  int64
		Name      string
		Subdomain string
	}](
		tx,
		`
			select resource, name, subdomain
			from app_orchestrator.private_networks
			where resource = some(:ids::int8[])
	    `,
		db.Params{
			"ids": ids,
		},
	)

	for _, row := range rows {
		resources[ResourceId(row.Resource)].Extra = &internalPrivateNetwork{
			Name:      row.Name,
			Subdomain: row.Subdomain,
		}
	}
}

func privateNetworkPersist(b *db.Batch, resource *resource) {
	if resource.MarkedForDeletion {
		db.BatchExec(
			b,
			`delete from app_orchestrator.private_networks where resource = :resource`,
			db.Params{
				"resource": resource.Id,
			},
		)
	} else {
		network := resource.Extra.(*internalPrivateNetwork)

		db.BatchExec(
			b,
			`
				insert into app_orchestrator.private_networks(resource, name, subdomain)
				values (:resource, :name, :subdomain)
				on conflict (resource) do update set
					name = excluded.name,
					subdomain = excluded.subdomain
		    `,
			db.Params{
				"resource":  resource.Id,
				"name":      network.Name,
				"subdomain": network.Subdomain,
			},
		)
	}
}

func privateNetworkTransform(
	r orcapi.Resource,
	product util.Option[accapi.ProductReference],
	extra any,
	flags orcapi.ResourceFlags,
	actor rpc.Actor,
) any {
	network := extra.(*internalPrivateNetwork)

	result := orcapi.PrivateNetwork{
		Resource: r,
		Specification: orcapi.PrivateNetworkSpecification{
			Name:      network.Name,
			Subdomain: network.Subdomain,
			ResourceSpecification: orcapi.ResourceSpecification{
				Product: product.Value,
			},
		},
		Status: orcapi.PrivateNetworkStatus{
			Members: []string{},
		},
	}

	if flags.IncludeProduct || flags.IncludeSupport {
		support, _ := SupportByProduct[orcapi.PrivateNetworkSupport](privateNetworkType, product.Value)
		result.Status.ResourceStatus = orcapi.ResourceStatus[orcapi.PrivateNetworkSupport]{
			ResolvedSupport: util.OptValue(support.ToApi()),
			ResolvedProduct: util.OptValue(support.Product),
		}
	}
	return result
}

var privateNetworkSubdomainRegex = regexp.MustCompile(`^[A-Za-z0-9](?:[A-Za-z0-9-]{0,254}[A-Za-z0-9])?$`)

func privateNetworkValidateSpecification(spec orcapi.PrivateNetworkSpecification) *util.HttpError {
	if spec.Name == "" || len(spec.Name) > 256 || strings.Contains(spec.Name, "\n") {
		return util.HttpErr(http.StatusBadRequest, "invalid private network name")
	}

	if spec.Subdomain == "" || len(spec.Subdomain) > 256 || strings.Contains(spec.Subdomain, ".") {
		return util.HttpErr(http.StatusBadRequest, "invalid private network subdomain")
	}

	if !privateNetworkSubdomainRegex.MatchString(spec.Subdomain) {
		return util.HttpErr(http.StatusBadRequest, "invalid private network subdomain")
	}

	return nil
}
