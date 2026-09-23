package orchestrator

import (
	"fmt"
	"net/http"
	"regexp"
	"slices"
	"strconv"
	"strings"

	"database/sql"

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
		return PrivateNetworkBrowse(info.Actor, request), nil
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
		responses, err := PrivateNetworkCreate(info.Actor, request)
		return fndapi.BulkResponse[orcapi.PrivateNetwork]{Responses: responses}, err
	})

	orcapi.PrivateNetworksDelete.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (util.Empty, *util.HttpError) {
		return util.Empty{}, PrivateNetworkDelete(info.Actor, request)
	})

	orcapi.PrivateNetworksSearch.Handler(func(info rpc.RequestInfo, request orcapi.PrivateNetworksSearchRequest) (fndapi.PageV2[orcapi.PrivateNetwork], *util.HttpError) {
		return PrivateNetworkSearch(info.Actor, request), nil
	})

	orcapi.PrivateNetworksRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.PrivateNetworksRetrieveRequest) (orcapi.PrivateNetwork, *util.HttpError) {
		return PrivateNetworkRetrieve(info.Actor, request)
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

	orcapi.PrivateNetworksUpdateLabels.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.PrivateNetworksUpdateLabelsRequest]) (util.Empty, *util.HttpError) {
		return util.Empty{}, PrivateNetworkUpdateLabels(info.Actor, request)
	})

	orcapi.PrivateNetworksRetrieveProducts.Handler(func(info rpc.RequestInfo, request util.Empty) (orcapi.SupportByProvider[orcapi.PrivateNetworkSupport], *util.HttpError) {
		return PrivateNetworkRetrieveProducts(info.Actor), nil
	})

	orcapi.PrivateNetworksControlRegister.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ProviderRegisteredResource[orcapi.PrivateNetworkSpecification]]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		var responses []fndapi.FindByStringId

		providerId, _ := strings.CutPrefix(info.Actor.Username, fndapi.ProviderSubjectPrefix)
		for _, reqItem := range request.Items {
			if reqItem.Spec.Product.Provider != providerId {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(http.StatusForbidden, "forbidden")
			}
		}

		for _, reqItem := range request.Items {
			spec := reqItem.Spec
			spec.Name = strings.TrimSpace(spec.Name)
			spec.Subdomain = strings.ToLower(strings.TrimSpace(spec.Subdomain))

			err := privateNetworkValidateSpecification(spec)
			if err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			}

			if privateNetworkSubdomainTaken(spec.Product.Provider, spec.Subdomain, 0) {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(
					http.StatusConflict,
					"a network with this subdomain already exists, try a different one",
				)
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
				reqItem.Spec.ResourceSpecification,
				reqItem.ProviderGeneratedId,
				&internalPrivateNetwork{
					Name:      spec.Name,
					Subdomain: spec.Subdomain,
					Cidr:      spec.Cidr,
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

	orcapi.PrivateNetworksControlUpdateLabels.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.PrivateNetworksUpdateLabelsRequest]) (util.Empty, *util.HttpError) {
		for _, reqItem := range request.Items {
			err := ResourceUpdateLabels(info.Actor, privateNetworkType, reqItem.Id, reqItem.Labels, orcapi.PermissionProvider)
			if err != nil {
				return util.Empty{}, err
			}
		}

		return util.Empty{}, nil
	})

	orcapi.PrivateNetworksControlAddUpdate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ResourceUpdateAndId[orcapi.PrivateNetworkUpdate]]) (util.Empty, *util.HttpError) {
		ids := make([]string, 0, len(request.Items))
		for _, item := range request.Items {
			ids = append(ids, item.Id)
		}
		if err := ResourceValidateProviderBatch(info.Actor, privateNetworkType, ids); err != nil {
			return util.Empty{}, err
		}

		for _, item := range request.Items {
			if item.Update.CidrBlock.Present {
				if _, ok := orcapi.PrivateNetworkParseCidr(item.Update.CidrBlock.Value); !ok {
					return util.Empty{}, util.HttpErr(http.StatusBadRequest, "invalid private network CIDR block")
				}
			}

			var conflict *util.HttpError
			ok := ResourceUpdate(
				info.Actor,
				privateNetworkType,
				ResourceParseId(item.Id),
				orcapi.PermissionProvider,
				func(r *resource, mapped orcapi.PrivateNetwork) {
					network := r.Extra.(*internalPrivateNetwork)
					if !item.Update.CidrBlock.Present {
						return
					}

					if network.CidrBlock.Present {
						if network.CidrBlock.Value != item.Update.CidrBlock.Value {
							conflict = util.HttpErr(
								http.StatusConflict,
								"the address range of a network cannot change",
							)
						}
						return
					}

					network.CidrBlock = item.Update.CidrBlock
				},
			)

			if conflict != nil {
				return util.Empty{}, conflict
			}

			if !ok {
				return util.Empty{}, util.HttpErr(http.StatusNotFound, "not found or permission denied")
			}
		}

		return util.Empty{}, nil
	})
}

func PrivateNetworkCreate(actor rpc.Actor, request fndapi.BulkRequest[orcapi.PrivateNetworkSpecification]) ([]orcapi.PrivateNetwork, *util.HttpError) {
	var responses []orcapi.PrivateNetwork

	for _, item := range request.Items {
		if strings.HasPrefix(item.Subdomain, "ucloud-") {
			return nil, util.HttpErr(http.StatusBadRequest, "invalid subdomain requested")
		}

		_, ok := SupportByProduct[orcapi.PrivateNetworkSupport](privateNetworkType, item.Product)
		if !ok {
			return nil, util.HttpErr(http.StatusBadRequest, "unsupported operation")
		}

		item.Name = strings.TrimSpace(item.Name)
		item.Subdomain = strings.ToLower(strings.TrimSpace(item.Subdomain))
		if item.Cidr.Present {
			item.Cidr.Set(strings.TrimSpace(item.Cidr.Value))
			if item.Cidr.Value == "" {
				item.Cidr.Clear()
			}
		}

		err := privateNetworkValidateSpecification(item)
		if err != nil {
			return nil, err
		}

		if privateNetworkSubdomainTaken(item.Product.Provider, item.Subdomain, 0) {
			return nil, util.HttpErr(
				http.StatusConflict,
				"a network with this subdomain already exists, try a different one",
			)
		}

		network, err := ResourceCreateThroughProvider(
			actor,
			privateNetworkType,
			item.ResourceSpecification,
			&internalPrivateNetwork{
				Name:      item.Name,
				Subdomain: item.Subdomain,
				Cidr:      item.Cidr,
			},
			orcapi.PrivateNetworksProviderCreate,
		)

		if err != nil {
			return nil, err
		}

		responses = append(responses, network)
	}
	return responses, nil
}

func PrivateNetworkBrowse(actor rpc.Actor, request orcapi.PrivateNetworksBrowseRequest) fndapi.PageV2[orcapi.PrivateNetwork] {
	sortByFn := ResourceDefaultComparator(func(item orcapi.PrivateNetwork) orcapi.Resource {
		return item.Resource
	}, request.ResourceFlags)

	return ResourceBrowse[orcapi.PrivateNetwork](
		actor,
		privateNetworkType,
		request.Next,
		request.ItemsPerPage,
		request.ResourceFlags,
		func(item orcapi.PrivateNetwork) bool {
			return true
		},
		sortByFn,
	)
}

func PrivateNetworkDelete(actor rpc.Actor, request fndapi.BulkRequest[fndapi.FindByStringId]) *util.HttpError {
	for _, item := range request.Items {
		network, _, _, err := ResourceRetrieveEx[orcapi.PrivateNetwork](
			actor,
			privateNetworkType,
			ResourceParseId(item.Id),
			orcapi.PermissionEdit,
			orcapi.ResourceFlags{},
		)
		if err != nil {
			return err
		}

		if len(network.Status.Members) > 0 {
			return util.HttpErr(
				http.StatusConflict,
				"This private network is currently in use by job: %v",
				strings.Join(network.Status.Members, ", "),
			)
		}

		err = privateNetworkDeleteReservedIps(item.Id)
		if err != nil {
			return err
		}

		err = ResourceDeleteThroughProvider(actor, privateNetworkType, item.Id, orcapi.PrivateNetworksProviderDelete)
		if err != nil {
			return err
		}
	}

	return nil
}

func privateNetworkDeleteReservedIps(networkId string) *util.HttpError {
	reservedIpIds := db.NewTx(func(tx *db.Transaction) []string {
		rows := db.Select[struct{ Resource int64 }](
			tx,
			`
				select resource
				from app_orchestrator.private_network_ips
				where network = :network
			`,
			db.Params{
				"network": ResourceParseId(networkId),
			},
		)

		ids := make([]string, 0, len(rows))
		for _, row := range rows {
			ids = append(ids, fmt.Sprint(row.Resource))
		}
		return ids
	})

	for _, id := range reservedIpIds {
		err := ResourceDeleteThroughProvider(
			rpc.ActorSystem,
			privateNetworkIpType,
			id,
			orcapi.PrivateNetworkIpsProviderDelete,
		)
		if err != nil {
			return err
		}
	}

	return nil
}

func PrivateNetworkSearch(actor rpc.Actor, request orcapi.PrivateNetworksSearchRequest) fndapi.PageV2[orcapi.PrivateNetwork] {
	query := strings.ToLower(request.Query)
	return ResourceBrowse[orcapi.PrivateNetwork](
		actor,
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
	)
}

func PrivateNetworkRetrieve(actor rpc.Actor, request orcapi.PrivateNetworksRetrieveRequest) (orcapi.PrivateNetwork, *util.HttpError) {
	return ResourceRetrieve[orcapi.PrivateNetwork](actor, privateNetworkType, ResourceParseId(request.Id), request.ResourceFlags)
}

func PrivateNetworkUpdateLabels(actor rpc.Actor, request fndapi.BulkRequest[orcapi.PrivateNetworksUpdateLabelsRequest]) *util.HttpError {
	for _, reqItem := range request.Items {
		err := ResourceUpdateLabelsThroughProvider[orcapi.PrivateNetwork](
			actor,
			privateNetworkType,
			reqItem.Id,
			reqItem.Labels,
			func(t *orcapi.PrivateNetwork, labels map[string]string) {
				t.Specification.Labels = labels
			},
			orcapi.PrivateNetworksProviderOnUpdatedLabels,
		)

		if err != nil {
			return err
		}
	}

	return nil
}

func PrivateNetworkRetrieveProducts(actor rpc.Actor) orcapi.SupportByProvider[orcapi.PrivateNetworkSupport] {
	return SupportRetrieveProducts[orcapi.PrivateNetworkSupport](privateNetworkType)
}

type internalPrivateNetwork struct {
	Name      string
	Subdomain string
	Cidr      util.Option[string]
	CidrBlock util.Option[string]
	Members   []string
}

func privateNetworkLoad(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource) {
	rows := db.Select[struct {
		Resource  int64
		Name      string
		Subdomain string
		Cidr      sql.Null[string]
		CidrBlock sql.Null[string]
		Members   []int64
	}](
		tx,
		`
			select resource, name, subdomain, cidr, cidr_block, members
			from app_orchestrator.private_networks
			where resource = some(:ids::int8[])
	    `,
		db.Params{
			"ids": ids,
		},
	)

	for _, row := range rows {
		var members []string
		for _, jobId := range row.Members {
			members = append(members, fmt.Sprint(jobId))
		}

		resources[ResourceId(row.Resource)].Extra = &internalPrivateNetwork{
			Name:      row.Name,
			Subdomain: row.Subdomain,
			Cidr:      util.SqlNullToOpt(row.Cidr),
			CidrBlock: util.SqlNullToOpt(row.CidrBlock),
			Members:   members,
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

		members := []int64{}
		for _, jobId := range network.Members {
			id, _ := strconv.ParseInt(jobId, 10, 64)
			members = append(members, id)
		}

		db.BatchExec(
			b,
			`
				insert into app_orchestrator.private_networks(resource, name, subdomain, cidr, cidr_block, members)
				values (:resource, :name, :subdomain, :cidr, :cidr_block, :members)
				on conflict (resource) do update set
					name = excluded.name,
					subdomain = excluded.subdomain,
					cidr = excluded.cidr,
					cidr_block = excluded.cidr_block,
					members = excluded.members
		    `,
			db.Params{
				"resource":   resource.Id,
				"name":       network.Name,
				"subdomain":  network.Subdomain,
				"cidr":       network.Cidr.Sql(),
				"cidr_block": network.CidrBlock.Sql(),
				"members":    members,
			},
		)
	}
}

func privateNetworkTransform(
	r orcapi.Resource,
	specification orcapi.ResourceSpecification,
	extra any,
	flags orcapi.ResourceFlags,
	actor rpc.Actor,
) any {
	network := extra.(*internalPrivateNetwork)

	result := orcapi.PrivateNetwork{
		Resource: r,
		Specification: orcapi.PrivateNetworkSpecification{
			Name:                  network.Name,
			Subdomain:             network.Subdomain,
			Cidr:                  network.Cidr,
			ResourceSpecification: specification,
		},
		Status: orcapi.PrivateNetworkStatus{
			Members:   network.Members,
			CidrBlock: network.CidrBlock,
		},
	}

	if (flags.IncludeProduct || flags.IncludeSupport) && resourceSpecificationHasProduct(specification) {
		support, _ := SupportByProduct[orcapi.PrivateNetworkSupport](privateNetworkType, specification.Product)
		result.Status.ResourceStatus = orcapi.ResourceStatus[orcapi.PrivateNetworkSupport]{
			ResolvedSupport: util.OptValue(support.ToApi()),
			ResolvedProduct: util.OptValue(support.Product),
		}
	}
	return result
}

func PrivateNetworkBind(id string, jobId string) {
	ResourceUpdate[orcapi.PrivateNetwork](
		rpc.ActorSystem,
		privateNetworkType,
		ResourceParseId(id),
		orcapi.PermissionRead,
		func(r *resource, mapped orcapi.PrivateNetwork) {
			network := r.Extra.(*internalPrivateNetwork)
			if !slices.Contains(network.Members, jobId) {
				network.Members = append(network.Members, jobId)
			}
		},
	)
}

func PrivateNetworkUnbind(id string, jobId string) {
	ResourceUpdate[orcapi.PrivateNetwork](
		rpc.ActorSystem,
		privateNetworkType,
		ResourceParseId(id),
		orcapi.PermissionRead,
		func(r *resource, mapped orcapi.PrivateNetwork) {
			network := r.Extra.(*internalPrivateNetwork)
			network.Members = util.RemoveFirst(network.Members, jobId)
		},
	)
}

func privateNetworkSubdomainTaken(provider string, subdomain string, except ResourceId) bool {
	reference := resourceProviderRef(provider)

	idxBucket := resourceGetAndLoadIndex(privateNetworkType, reference)
	idxBucket.Mu.RLock()
	ids := append([]ResourceId(nil), idxBucket.ByOwner[reference]...)
	idxBucket.Mu.RUnlock()

	for _, id := range ids {
		if id == except {
			continue
		}

		b := resourceGetBucket(privateNetworkType, id)
		r, ok, _ := resourcesReadEx(rpc.ActorSystem, privateNetworkType, orcapi.PermissionRead, b, id, nil)
		if !ok || r == nil || r.MarkedForDeletion {
			continue
		}

		network, valid := r.Extra.(*internalPrivateNetwork)
		if valid && strings.EqualFold(network.Subdomain, subdomain) {
			return true
		}
	}

	return false
}

func privateNetworkValidateJobNetworks(actor rpc.Actor, values []orcapi.AppParameterValue) *util.HttpError {
	var attachedNetworks []orcapi.PrivateNetwork

	for _, value := range values {
		if value.Type != orcapi.AppParameterValueTypePrivateNetwork {
			continue
		}

		network, _, _, err := ResourceRetrieveEx[orcapi.PrivateNetwork](
			actor,
			privateNetworkType,
			ResourceParseId(value.Id),
			orcapi.PermissionRead,
			orcapi.ResourceFlags{},
		)
		if err != nil {
			return util.HttpErr(http.StatusForbidden, "you cannot use this network")
		}

		for _, existing := range attachedNetworks {
			if existing.Id == network.Id {
				return util.HttpErr(http.StatusBadRequest, "the same network cannot be attached twice")
			}

			if existing.Status.CidrBlock.Present && network.Status.CidrBlock.Present {
				existingCidr, existingOk := orcapi.PrivateNetworkParseCidr(existing.Status.CidrBlock.Value)
				networkCidr, networkOk := orcapi.PrivateNetworkParseCidr(network.Status.CidrBlock.Value)

				if existingOk && networkOk &&
					orcapi.PrivateNetworkCidrsOverlap(existingCidr, networkCidr) {
					return util.HttpErr(
						http.StatusBadRequest,
						"a job cannot attach two networks with overlapping address ranges",
					)
				}
			}
		}

		attachedNetworks = append(attachedNetworks, network)
	}

	return nil
}

var privateNetworkSubdomainRegex = regexp.MustCompile(`^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$`)

func privateNetworkValidateSpecification(spec orcapi.PrivateNetworkSpecification) *util.HttpError {
	if spec.Name == "" || len(spec.Name) > 256 || strings.Contains(spec.Name, "\n") {
		return util.HttpErr(http.StatusBadRequest, "invalid private network name")
	}

	if spec.Subdomain == "" || len(spec.Subdomain) > 63 || strings.Contains(spec.Subdomain, ".") {
		return util.HttpErr(http.StatusBadRequest, "invalid private network subdomain")
	}

	if !privateNetworkSubdomainRegex.MatchString(spec.Subdomain) {
		return util.HttpErr(http.StatusBadRequest, "invalid private network subdomain")
	}

	if spec.Cidr.Present {
		err := privateNetworkValidateUserCidr(spec.Cidr.Value)
		if err != nil {
			return err
		}
	}

	return nil
}

func privateNetworkValidateUserCidr(cidr string) *util.HttpError {
	prefix, ok := orcapi.PrivateNetworkParseCidr(cidr)
	if !ok || prefix.Bits() < 16 || prefix.Bits() > 24 {
		return util.HttpErr(
			http.StatusBadRequest,
			"invalid private network CIDR requested, it must be an IPv4 CIDR with a prefix length between 16 and 24",
		)
	}

	return nil
}
