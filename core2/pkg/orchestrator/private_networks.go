package orchestrator

import (
	"fmt"
	"net/http"
	"regexp"
	"slices"
	"strconv"
	"strings"
	"time"

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
		actor, err := controlBrowseJobScoped(info, request.JobId, &request.ResourceFlags)
		if err != nil {
			return fndapi.PageV2[orcapi.PrivateNetwork]{}, err
		}

		return ResourceBrowse[orcapi.PrivateNetwork](
			actor,
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
		actor, err := controlRetrieveJobScoped(info, request.JobId, privateNetworkType, request.Id)
		if err != nil {
			return orcapi.PrivateNetwork{}, err
		}
		return ResourceRetrieve[orcapi.PrivateNetwork](actor, privateNetworkType, ResourceParseId(request.Id), request.ResourceFlags)
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

	orcapi.PrivateNetworksControlCreate.Handler(controlCreateServe(
		func(spec *orcapi.PrivateNetworkSpecification) *orcapi.ResourceSpecification {
			return &spec.ResourceSpecification
		},
		PrivateNetworkCreate,
		func(created []orcapi.PrivateNetwork) fndapi.BulkResponse[fndapi.FindByStringId] {
			return controlCreateIdsOf(created, func(r orcapi.PrivateNetwork) string { return r.Id })
		},
	))

	orcapi.PrivateNetworksControlRegister.Handler(resourceControlRegisterServe[orcapi.PrivateNetworkSpecification, orcapi.PrivateNetwork](
		privateNetworkType,
		func(spec *orcapi.PrivateNetworkSpecification) *orcapi.ResourceSpecification {
			return &spec.ResourceSpecification
		},
		func(item *orcapi.ProviderRegisteredResource[orcapi.PrivateNetworkSpecification]) (any, func(), *util.HttpError) {
			spec := item.Spec

			support, ok := SupportByProduct[orcapi.PrivateNetworkSupport](privateNetworkType, spec.Product)
			if !ok {
				return nil, nil, util.HttpErr(http.StatusNotFound, "unknown product requested")
			}

			if err := privateNetworkNormalizeAndValidateSpecification(&spec, support.Support); err != nil {
				return nil, nil, err
			}

			subdomain := orcapi.PrivateNetworkSubdomainFromName(spec.Name)
			for privateNetworkSubdomainTaken(spec.Product.Provider, subdomain, 0) {
				var suffixErr error
				subdomain, suffixErr = orcapi.PrivateNetworkSubdomainAddRandomSuffix(subdomain)
				if suffixErr != nil {
					return nil, nil, util.ServerHttpError("failed to generate a private network subdomain")
				}
			}

			return &internalPrivateNetwork{
				Name:         spec.Name,
				Subdomain:    subdomain,
				Cidr:         spec.Cidr,
				Type:         spec.EffectiveType(),
				LastUsedAt:   fndapi.Timestamp(time.Now()),
				ReclaimState: orcapi.NetworkReclaimActive,
			}, nil, nil
		},
	))

	orcapi.PrivateNetworksControlUpdateLabels.Handler(controlUpdateLabelsServe(
		privateNetworkType,
		func(item orcapi.PrivateNetworksUpdateLabelsRequest) string { return item.Id },
		func(item orcapi.PrivateNetworksUpdateLabelsRequest) map[string]string { return item.Labels },
		func(item *orcapi.PrivateNetworksUpdateLabelsRequest, labels map[string]string) { item.Labels = labels },
		PrivateNetworkUpdateLabels,
	))

	orcapi.PrivateNetworksControlDelete.Handler(controlMutateServe(
		privateNetworkType,
		func(item fndapi.FindByStringId) string { return item.Id },
		func(actor rpc.Actor, items []fndapi.FindByStringId) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
			err := PrivateNetworkDelete(actor, fndapi.BulkRequestOf(items...))
			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, err
			}
			return fndapi.BulkResponse[util.Empty]{Responses: make([]util.Empty, len(items))}, nil
		},
	))

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
			if item.Update.Subdomain.Present {
				if len(item.Update.Subdomain.Value) > 63 || !privateNetworkSubdomainRegex.MatchString(item.Update.Subdomain.Value) {
					return util.Empty{}, util.HttpErr(http.StatusBadRequest, "invalid private network subdomain")
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
					if item.Update.CidrBlock.Present && network.EffectiveType() == orcapi.NetworkTypeInfiniband {
						prefix, ok := orcapi.PrivateNetworkParseCidr(item.Update.CidrBlock.Value)
						if !ok || prefix.String() != item.Update.CidrBlock.Value || prefix.Bits() != orcapi.PrivateNetworkInfinibandPrefixLength {
							conflict = util.HttpErr(
								http.StatusBadRequest,
								"InfiniBand networks must use an aligned /24 address block",
							)
							return
						}
					}
					cidrCannotChange := item.Update.CidrBlock.Present && network.CidrBlock.Present && network.CidrBlock.Value != item.Update.CidrBlock.Value
					if cidrCannotChange {
						conflict = util.HttpErr(
							http.StatusConflict,
							"the address range of a network cannot change",
						)
					}
					subdomainCannotChange := item.Update.Subdomain.Present && network.Subdomain != "" && network.Subdomain != item.Update.Subdomain.Value
					if subdomainCannotChange {
						conflict = util.HttpErr(http.StatusConflict, "a private network subdomain cannot change")
					}
					if conflict != nil {
						return
					}

					if item.Update.CidrBlock.Present && !network.CidrBlock.Present {
						network.CidrBlock = item.Update.CidrBlock
					}
					if item.Update.Subdomain.Present {
						network.Subdomain = item.Update.Subdomain.Value
					}
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

	initPrivateNetworkReclaimer()
}

func PrivateNetworkCreate(actor rpc.Actor, request fndapi.BulkRequest[orcapi.PrivateNetworkSpecification]) ([]orcapi.PrivateNetwork, *util.HttpError) {
	var responses []orcapi.PrivateNetwork

	for _, item := range request.Items {
		support, ok := SupportByProduct[orcapi.PrivateNetworkSupport](privateNetworkType, item.Product)
		if !ok {
			return nil, util.HttpErr(http.StatusBadRequest, "unsupported operation")
		}

		err := privateNetworkNormalizeAndValidateSpecification(&item, support.Support)
		if err != nil {
			return nil, err
		}

		network, err := ResourceCreateThroughProviderEx(
			actor,
			privateNetworkType,
			item.ResourceSpecification,
			&internalPrivateNetwork{
				Name:         item.Name,
				Cidr:         item.Cidr,
				Type:         item.EffectiveType(),
				LastUsedAt:   fndapi.Timestamp(time.Now()),
				ReclaimState: orcapi.NetworkReclaimActive,
			},
			orcapi.PrivateNetworksProviderCreate,
			ProviderCallOpts{Timeout: util.OptValue(2 * time.Minute)},
		)

		if err != nil {
			return nil, err
		}

		updated, err := PrivateNetworkRetrieve(actor, orcapi.PrivateNetworksRetrieveRequest{Id: network.Id})
		if err != nil {
			return nil, err
		}

		responses = append(responses, updated)
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
		_, _, _, authErr := ResourceRetrieveEx[orcapi.PrivateNetwork](
			actor,
			privateNetworkType,
			ResourceParseId(item.Id),
			orcapi.PermissionEdit,
			orcapi.ResourceFlags{},
		)
		if authErr != nil {
			return authErr
		}

		privateNetworkPruneStaleMembers(item.Id)

		err := func() *util.HttpError {
			mutationLock := networkMutationLock(item.Id)
			mutationLock.Lock()
			defer mutationLock.Unlock()
			return privateNetworkDeleteOne(actor, item)
		}()

		if err != nil {
			return err
		}
	}

	return nil
}

func privateNetworkPruneStaleMembers(networkId string) {
	network, _, _, err := ResourceRetrieveEx[orcapi.PrivateNetwork](
		rpc.ActorSystem,
		privateNetworkType,
		ResourceParseId(networkId),
		orcapi.PermissionRead,
		orcapi.ResourceFlags{},
	)
	if err != nil {
		return
	}

	for _, jobId := range network.Status.Members {
		job, _, _, jobErr := ResourceRetrieveEx[orcapi.Job](
			rpc.ActorSystem,
			jobType,
			ResourceParseId(jobId),
			orcapi.PermissionRead,
			orcapi.ResourceFlags{},
		)

		if jobErr != nil || job.Status.State.IsFinal() {
			PrivateNetworkUnbind(networkId, jobId)
		}
	}
}

func privateNetworkDeleteOne(actor rpc.Actor, item fndapi.FindByStringId) *util.HttpError {
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

	if serviceReferencedByNetwork(ResourceParseId(item.Id)) {
		return util.HttpErr(
			http.StatusConflict,
			"This private network is currently in use as the internal endpoint of a service",
		)
	}

	err = privateNetworkDeleteReservedIps(item.Id)
	if err != nil {
		return err
	}

	return ResourceDeleteThroughProvider(actor, privateNetworkType, item.Id, orcapi.PrivateNetworksProviderDelete)
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

	var firstErr *util.HttpError
	for _, id := range reservedIpIds {
		err := ResourceDeleteThroughProvider(
			rpc.ActorSystem,
			privateNetworkIpType,
			id,
			orcapi.PrivateNetworkIpsProviderDelete,
		)
		if err != nil && firstErr == nil {
			firstErr = err
		}
	}

	if firstErr != nil {
		return firstErr
	}

	remaining := db.NewTx(func(tx *db.Transaction) int {
		row, _ := db.Get[struct{ Count int }](
			tx,
			`
				select count(*) as count
				from app_orchestrator.private_network_ips
				where network = :network
			`,
			db.Params{
				"network": ResourceParseId(networkId),
			},
		)
		return row.Count
	})
	if remaining > 0 {
		return util.HttpErr(http.StatusConflict, "the private network still has reserved IP addresses")
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
			subdomain := strings.ToLower(item.Status.Subdomain)

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
	Name         string
	Subdomain    string
	Cidr         util.Option[string]
	CidrBlock    util.Option[string]
	Members      []string
	Type         orcapi.NetworkType
	LastUsedAt   fndapi.Timestamp
	ReclaimState orcapi.NetworkReclaimState
	ReclaimAfter util.Option[fndapi.Timestamp]
}

func (n *internalPrivateNetwork) EffectiveType() orcapi.NetworkType {
	if n.Type == "" {
		return orcapi.NetworkTypeEthernet
	}
	return n.Type
}

func (n *internalPrivateNetwork) EffectiveReclaimState() orcapi.NetworkReclaimState {
	if n.ReclaimState == "" {
		return orcapi.NetworkReclaimActive
	}
	return n.ReclaimState
}

func privateNetworkLoad(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource) {
	rows := db.Select[struct {
		Resource     int64
		Name         string
		Subdomain    string
		Cidr         sql.Null[string]
		CidrBlock    sql.Null[string]
		Members      []int64
		NetworkType  string
		LastUsedAt   time.Time
		ReclaimState string
		ReclaimAfter sql.Null[time.Time]
	}](
		tx,
		`
			select
				resource,
				name,
				subdomain,
				cidr,
				cidr_block,
				members,
				network_type,
				last_used_at,
				reclaim_state,
				reclaim_after
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
			Name:         row.Name,
			Subdomain:    row.Subdomain,
			Cidr:         util.SqlNullToOpt(row.Cidr),
			CidrBlock:    util.SqlNullToOpt(row.CidrBlock),
			Members:      members,
			Type:         orcapi.NetworkType(row.NetworkType),
			LastUsedAt:   fndapi.Timestamp(row.LastUsedAt),
			ReclaimState: orcapi.NetworkReclaimState(row.ReclaimState),
			ReclaimAfter: util.OptMap(util.SqlNullToOpt(row.ReclaimAfter), func(value time.Time) fndapi.Timestamp {
				return fndapi.Timestamp(value)
			}),
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

		reclaimAfter := util.OptMap(network.ReclaimAfter, func(value fndapi.Timestamp) time.Time {
			return value.Time()
		})

		db.BatchExec(
			b,
			`
				insert into app_orchestrator.private_networks(
					resource, name, subdomain, cidr, cidr_block, members, network_type,
					last_used_at, reclaim_state, reclaim_after
				)
				values (
					:resource, :name, :subdomain, :cidr, :cidr_block, :members, :network_type,
					:last_used_at, :reclaim_state, :reclaim_after
				)
				on conflict (resource) do update set
					name = excluded.name,
					subdomain = excluded.subdomain,
					cidr = excluded.cidr,
					cidr_block = excluded.cidr_block,
					members = excluded.members,
					network_type = excluded.network_type,
					last_used_at = excluded.last_used_at,
					reclaim_state = excluded.reclaim_state,
					reclaim_after = excluded.reclaim_after
		    `,
			db.Params{
				"resource":      resource.Id,
				"name":          network.Name,
				"subdomain":     network.Subdomain,
				"cidr":          network.Cidr.Sql(),
				"cidr_block":    network.CidrBlock.Sql(),
				"members":       members,
				"network_type":  string(network.EffectiveType()),
				"last_used_at":  network.LastUsedAt.Time(),
				"reclaim_state": string(network.EffectiveReclaimState()),
				"reclaim_after": reclaimAfter.Sql(),
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
			Type:                  network.EffectiveType(),
			Cidr:                  network.Cidr,
			ResourceSpecification: specification,
		},
		Status: orcapi.PrivateNetworkStatus{
			Subdomain:    network.Subdomain,
			Members:      slices.Clone(network.Members),
			CidrBlock:    network.CidrBlock,
			LastUsedAt:   network.LastUsedAt,
			ReclaimState: network.EffectiveReclaimState(),
			ReclaimAfter: network.ReclaimAfter,
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

func PrivateNetworkBind(id string, jobId string) bool {
	mutationLock := networkMutationLock(id)
	mutationLock.Lock()
	defer mutationLock.Unlock()

	ok := ResourceUpdate[orcapi.PrivateNetwork](
		rpc.ActorSystem,
		privateNetworkType,
		ResourceParseId(id),
		orcapi.PermissionRead,
		func(r *resource, mapped orcapi.PrivateNetwork) {
			network := r.Extra.(*internalPrivateNetwork)
			if !slices.Contains(network.Members, jobId) {
				network.Members = append(network.Members, jobId)
			}
			network.LastUsedAt = fndapi.Timestamp(time.Now())
			network.ReclaimState = orcapi.NetworkReclaimActive
			network.ReclaimAfter = util.OptNone[fndapi.Timestamp]()
		},
	)
	if ok {
		privateNetworkReclaimFailuresReset(id)
	}
	return ok
}

func PrivateNetworkUnbind(id string, jobId string) bool {
	mutationLock := networkMutationLock(id)
	mutationLock.Lock()
	defer mutationLock.Unlock()

	return ResourceUpdate[orcapi.PrivateNetwork](
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

func PrivateNetworkTouch(id string) bool {
	mutationLock := networkMutationLock(id)
	mutationLock.Lock()
	defer mutationLock.Unlock()

	return privateNetworkTouchLocked(id)
}

func privateNetworkTouchJob(job orcapi.Job) {
	for _, value := range jobResourceValues(job.Specification) {
		if orcapi.IsNetworkValue(value) {
			PrivateNetworkTouch(value.Id)
		}
	}
}

func privateNetworkTouchLocked(id string) bool {
	now := fndapi.Timestamp(time.Now())
	ok := ResourceUpdate[orcapi.PrivateNetwork](
		rpc.ActorSystem,
		privateNetworkType,
		ResourceParseId(id),
		orcapi.PermissionRead,
		func(r *resource, mapped orcapi.PrivateNetwork) {
			network := r.Extra.(*internalPrivateNetwork)
			network.LastUsedAt = now
			if network.EffectiveReclaimState() == orcapi.NetworkReclaimPending {
				network.ReclaimState = orcapi.NetworkReclaimActive
				network.ReclaimAfter = util.OptNone[fndapi.Timestamp]()
			}
		},
	)
	if ok {
		privateNetworkReclaimFailuresReset(id)
	}
	return ok
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
		if !ok || r == nil {
			continue
		}

		b.Mu.RLock()
		deleted := r.MarkedForDeletion
		network, valid := r.Extra.(*internalPrivateNetwork)
		var existingSubdomain string
		if valid {
			existingSubdomain = network.Subdomain
		}
		b.Mu.RUnlock()

		if !deleted && valid && strings.EqualFold(existingSubdomain, subdomain) {
			return true
		}
	}

	return false
}

func privateNetworkValidateJobNetworks(actor rpc.Actor, backend orcapi.ToolBackend, values []orcapi.AppParameterValue) *util.HttpError {
	var attachedNetworks []orcapi.PrivateNetwork
	infinibandCount := 0

	for _, value := range values {
		if !orcapi.IsNetworkValue(value) {
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

		if network.Specification.EffectiveType() == orcapi.NetworkTypeInfiniband {
			if backend == orcapi.ToolBackendVirtualMachine {
				return util.HttpErr(http.StatusBadRequest, "InfiniBand networks are not supported for virtual machines")
			}

			infinibandCount++
			if infinibandCount > 1 {
				return util.HttpErr(http.StatusBadRequest, "A job can attach at most one InfiniBand network")
			}
		}

		attachedNetworks = append(attachedNetworks, network)
	}

	return nil
}

var privateNetworkSubdomainRegex = regexp.MustCompile(`^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$`)

func privateNetworkNormalizeAndValidateSpecification(spec *orcapi.PrivateNetworkSpecification, support orcapi.PrivateNetworkSupport) *util.HttpError {
	spec.Name = strings.TrimSpace(spec.Name)
	if spec.Name == "" || len(spec.Name) > 256 || strings.Contains(spec.Name, "\n") {
		return util.HttpErr(http.StatusBadRequest, "invalid private network name")
	}

	if spec.Cidr.Present {
		spec.Cidr.Set(strings.TrimSpace(spec.Cidr.Value))
		if spec.Cidr.Value == "" {
			spec.Cidr.Clear()
		}
	}

	networkType := spec.EffectiveType()
	if !orcapi.NetworkTypeIsValid(networkType) {
		return util.HttpErr(http.StatusBadRequest, "unknown network type")
	}

	supportType := support.EffectiveType()
	if !orcapi.NetworkTypeIsValid(supportType) {
		return util.HttpErr(http.StatusInternalServerError, "the provider advertises an unsupported network type")
	}

	if networkType != supportType {
		return util.HttpErr(http.StatusBadRequest, "the network type does not match the product")
	}

	if networkType == orcapi.NetworkTypeInfiniband {
		if spec.Cidr.Present {
			return util.HttpErr(http.StatusBadRequest, "InfiniBand networks cannot have a user-supplied CIDR")
		}
		spec.Type = orcapi.NetworkTypeInfiniband
		return nil
	}

	if spec.Cidr.Present {
		err := privateNetworkValidateUserCidr(spec.Cidr.Value)
		if err != nil {
			return err
		}

		err = privateNetworkValidateCidrSupport(spec.Cidr.Value, support)
		if err != nil {
			return err
		}
	}

	spec.Type = orcapi.NetworkTypeEthernet
	return nil
}

func privateNetworkValidateUserCidr(cidr string) *util.HttpError {
	prefix, ok := orcapi.PrivateNetworkParseCidr(cidr)
	if !ok || prefix.String() != cidr || prefix.Bits() < 16 || prefix.Bits() > 24 {
		return util.HttpErr(
			http.StatusBadRequest,
			"invalid private network CIDR requested, it must be an aligned IPv4 CIDR with a prefix length between 16 and 24",
		)
	}

	return nil
}

func privateNetworkValidateCidrSupport(cidr string, support orcapi.PrivateNetworkSupport) *util.HttpError {
	prefix, _ := orcapi.PrivateNetworkParseCidr(cidr)
	minPrefixLength := support.MinPrefixLength
	if minPrefixLength == 0 {
		minPrefixLength = 16
	}
	maxPrefixLength := support.MaxPrefixLength
	if maxPrefixLength == 0 {
		maxPrefixLength = 24
	}
	if prefix.Bits() < minPrefixLength || prefix.Bits() > maxPrefixLength {
		return util.HttpErr(
			http.StatusBadRequest,
			fmt.Sprintf("private network CIDR must have a prefix length between %d and %d", minPrefixLength, maxPrefixLength),
		)
	}

	if len(support.AddressPools) > 0 {
		insidePool := false
		for _, addressPool := range support.AddressPools {
			pool, ok := orcapi.PrivateNetworkParseCidr(addressPool)
			if ok && prefix.Bits() >= pool.Bits() && orcapi.PrivateNetworkCidrsOverlap(prefix, pool) {
				insidePool = true
				break
			}
		}
		if !insidePool {
			return util.HttpErr(http.StatusBadRequest, "private network CIDR is outside the provider's supported address pools")
		}
	}

	for _, excludedRange := range support.ExcludedRanges {
		excludedPrefix, ok := orcapi.PrivateNetworkParseCidr(excludedRange)
		if ok && orcapi.PrivateNetworkCidrsOverlap(prefix, excludedPrefix) {
			return util.HttpErr(http.StatusBadRequest, "private network CIDR overlaps a provider-excluded address range")
		}
	}

	return nil
}
