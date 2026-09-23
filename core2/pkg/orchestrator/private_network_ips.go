package orchestrator

import (
	"database/sql"
	"fmt"
	"net/http"
	"strings"

	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const privateNetworkIpType = "private_network_ip"

func initPrivateNetworkIps() {
	InitResourceType(
		privateNetworkIpType,
		resourceTypeCreateWithoutAdmin,
		privateNetworkIpLoad,
		privateNetworkIpPersist,
		privateNetworkIpTransform,
		nil,
	)

	orcapi.PrivateNetworkIpsBrowse.Handler(func(info rpc.RequestInfo, request orcapi.PrivateNetworkIpsBrowseRequest) (fndapi.PageV2[orcapi.PrivateNetworkIp], *util.HttpError) {
		sortByFn := ResourceDefaultComparator(func(item orcapi.PrivateNetworkIp) orcapi.Resource {
			return item.Resource
		}, request.ResourceFlags)

		return ResourceBrowse[orcapi.PrivateNetworkIp](
			info.Actor,
			privateNetworkIpType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.PrivateNetworkIp) bool {
				return true
			},
			sortByFn,
		), nil
	})

	orcapi.PrivateNetworkIpsSearch.Handler(func(info rpc.RequestInfo, request orcapi.PrivateNetworkIpsSearchRequest) (fndapi.PageV2[orcapi.PrivateNetworkIp], *util.HttpError) {
		query := strings.ToLower(request.Query)
		return ResourceBrowse[orcapi.PrivateNetworkIp](
			info.Actor,
			privateNetworkIpType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.PrivateNetworkIp) bool {
				if addr := item.Status.IpAddress; addr.Present && strings.Contains(strings.ToLower(addr.Value), query) {
					return true
				}

				return strings.Contains(strings.ToLower(item.Specification.Network), query)
			},
			nil,
		), nil
	})

	orcapi.PrivateNetworkIpsRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.PrivateNetworkIpsRetrieveRequest) (orcapi.PrivateNetworkIp, *util.HttpError) {
		return ResourceRetrieve[orcapi.PrivateNetworkIp](info.Actor, privateNetworkIpType, ResourceParseId(request.Id), request.ResourceFlags)
	})

	orcapi.PrivateNetworkIpsControlRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.PrivateNetworkIpsControlRetrieveRequest) (orcapi.PrivateNetworkIp, *util.HttpError) {
		return ResourceRetrieve[orcapi.PrivateNetworkIp](info.Actor, privateNetworkIpType, ResourceParseId(request.Id), request.ResourceFlags)
	})

	orcapi.PrivateNetworkIpsControlBrowse.Handler(func(info rpc.RequestInfo, request orcapi.PrivateNetworkIpsControlBrowseRequest) (fndapi.PageV2[orcapi.PrivateNetworkIp], *util.HttpError) {
		return ResourceBrowse[orcapi.PrivateNetworkIp](
			info.Actor,
			privateNetworkIpType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.PrivateNetworkIp) bool {
				return true
			},
			nil,
		), nil
	})

	orcapi.PrivateNetworkIpsCreate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.PrivateNetworkIpSpecification]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		created, err := PrivateNetworkIpCreate(info.Actor, request)
		if err != nil {
			return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
		}

		ids := make([]fndapi.FindByStringId, 0, len(created))
		for _, resc := range created {
			ids = append(ids, fndapi.FindByStringId{Id: resc.Id})
		}

		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: ids}, nil
	})

	orcapi.PrivateNetworkIpsDelete.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		for _, item := range request.Items {
			err := ResourceDeleteThroughProvider(info.Actor, privateNetworkIpType, item.Id, orcapi.PrivateNetworkIpsProviderDelete)
			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, err
			}
		}

		return fndapi.BulkResponse[util.Empty]{Responses: make([]util.Empty, len(request.Items))}, nil
	})

	orcapi.PrivateNetworkIpsUpdateAcl.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.UpdatedAcl]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		for _, item := range request.Items {
			err := ResourceUpdateAcl(info.Actor, privateNetworkIpType, item)
			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, err
			}
		}

		return fndapi.BulkResponse[util.Empty]{Responses: make([]util.Empty, len(request.Items))}, nil
	})

	orcapi.PrivateNetworkIpsUpdateLabels.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.PrivateNetworkIpsUpdateLabelsRequest]) (util.Empty, *util.HttpError) {
		return util.Empty{}, PrivateNetworkIpUpdateLabels(info.Actor, request)
	})

	orcapi.PrivateNetworkIpsRetrieveProducts.Handler(func(info rpc.RequestInfo, request util.Empty) (orcapi.SupportByProvider[orcapi.PrivateNetworkIpSupport], *util.HttpError) {
		return SupportRetrieveProducts[orcapi.PrivateNetworkIpSupport](privateNetworkIpType), nil
	})

	orcapi.PrivateNetworkIpsControlRegister.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ProviderRegisteredResource[orcapi.PrivateNetworkIpSpecification]]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		var responses []fndapi.FindByStringId

		providerId, _ := strings.CutPrefix(info.Actor.Username, fndapi.ProviderSubjectPrefix)
		for _, reqItem := range request.Items {
			if reqItem.Spec.Product.Provider != providerId {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(http.StatusForbidden, "forbidden")
			}
		}

		for _, reqItem := range request.Items {
			owner := orcapi.ResourceOwner{
				CreatedBy: reqItem.CreatedBy.GetOrDefault("_ucloud"),
				Project:   util.OptStringIfNotEmpty(reqItem.Project.Value),
			}

			spec := reqItem.Spec
			spec.IpAddress = privateNetworkIpNormalizePin(spec.IpAddress)

			_, networkErr := privateNetworkIpValidateParent(providerId, owner, spec)
			if networkErr != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, networkErr
			}

			var flags resourceCreateFlags
			if reqItem.ProjectAllRead {
				flags |= resourceCreateAllRead
			}

			if reqItem.ProjectAllWrite {
				flags |= resourceCreateAllWrite
			}

			id, _, err := ResourceCreateEx[orcapi.PrivateNetworkIp](
				privateNetworkIpType,
				owner,
				nil,
				reqItem.Spec.ResourceSpecification,
				reqItem.ProviderGeneratedId,
				&internalPrivateNetworkIp{
					Network:     spec.Network,
					RequestedIp: spec.IpAddress,
				},
				flags,
			)

			if err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			}

			ResourceConfirm(privateNetworkIpType, id)
			responses = append(responses, fndapi.FindByStringId{Id: fmt.Sprint(id)})
		}

		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: responses}, nil
	})

	orcapi.PrivateNetworkIpsControlAddUpdate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ResourceUpdateAndId[orcapi.PrivateNetworkIpUpdate]]) (util.Empty, *util.HttpError) {
		ids := make([]string, 0, len(request.Items))
		for _, item := range request.Items {
			ids = append(ids, item.Id)
		}
		if err := ResourceValidateProviderBatch(info.Actor, privateNetworkIpType, ids); err != nil {
			return util.Empty{}, err
		}

		for _, item := range request.Items {
			update := item.Update
			update.IpAddress = privateNetworkIpNormalizePin(update.IpAddress)

			if update.IpAddress.Present {
				if _, ok := orcapi.PrivateNetworkParseIpv4(update.IpAddress.Value); !ok {
					return util.Empty{}, util.HttpErr(http.StatusBadRequest, "invalid IP address")
				}
			}

			var conflict *util.HttpError
			ok := ResourceUpdate(
				info.Actor,
				privateNetworkIpType,
				ResourceParseId(item.Id),
				orcapi.PermissionProvider,
				func(r *resource, mapped orcapi.PrivateNetworkIp) {
					ip := r.Extra.(*internalPrivateNetworkIp)
					if !update.IpAddress.Present {
						return
					}

					if ip.IpAddress.Present {
						if ip.IpAddress.Value != update.IpAddress.Value {
							conflict = util.HttpErr(
								http.StatusConflict,
								"the effective ip address of a reservation cannot change",
							)
						}
						return
					}

					conflict = privateNetworkIpValidateEffective(r, ip, update.IpAddress.Value)
					if conflict == nil {
						ip.IpAddress = util.OptValue(update.IpAddress.Value)
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

	orcapi.PrivateNetworkIpsControlUpdateLabels.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.PrivateNetworkIpsUpdateLabelsRequest]) (util.Empty, *util.HttpError) {
		for _, reqItem := range request.Items {
			err := ResourceUpdateLabels(info.Actor, privateNetworkIpType, reqItem.Id, reqItem.Labels, orcapi.PermissionProvider)
			if err != nil {
				return util.Empty{}, err
			}
		}

		return util.Empty{}, nil
	})
}

func PrivateNetworkIpCreate(actor rpc.Actor, request fndapi.BulkRequest[orcapi.PrivateNetworkIpSpecification]) ([]orcapi.PrivateNetworkIp, *util.HttpError) {
	var created []orcapi.PrivateNetworkIp

	for _, item := range request.Items {
		_, ok := SupportByProduct[orcapi.PrivateNetworkIpSupport](privateNetworkIpType, item.Product)
		if !ok {
			return nil, util.HttpErr(http.StatusNotFound, "unknown product requested")
		}

		networkId := item.Network
		item.IpAddress = privateNetworkIpNormalizePin(item.IpAddress)

		if item.IpAddress.Present {
			if _, ok := orcapi.PrivateNetworkParseIpv4(item.IpAddress.Value); !ok {
				return nil, util.HttpErr(http.StatusBadRequest, "invalid IP address requested")
			}
		}

		reservationOwner := orcapi.ResourceOwner{
			CreatedBy: actor.Username,
			Project:   util.OptMap(actor.Project, func(value rpc.ProjectId) string { return string(value) }),
		}

		network, networkErr := privateNetworkIpValidateParent(item.Product.Provider, reservationOwner, item)
		if networkErr != nil {
			return nil, networkErr
		}

		_, _, _, err := ResourceRetrieveEx[orcapi.PrivateNetwork](
			actor,
			privateNetworkType,
			ResourceParseId(networkId),
			orcapi.PermissionEdit,
			orcapi.ResourceFlags{},
		)
		if err != nil {
			return nil, util.HttpErr(http.StatusForbidden, "you cannot use this network")
		}

		if !network.Status.CidrBlock.Present {
			return nil, util.HttpErr(
				http.StatusBadRequest,
				"the network is not ready for ip reservations yet",
			)
		}

		resc, err := ResourceCreateThroughProvider(
			actor,
			privateNetworkIpType,
			item.ResourceSpecification,
			&internalPrivateNetworkIp{
				Network:     networkId,
				RequestedIp: item.IpAddress,
			},
			orcapi.PrivateNetworkIpsProviderCreate,
		)

		if err != nil {
			return nil, err
		}

		created = append(created, resc)
	}

	return created, nil
}

func privateNetworkIpNormalizePin(pin util.Option[string]) util.Option[string] {
	if !pin.Present {
		return pin
	}

	trimmed := strings.TrimSpace(pin.Value)
	if trimmed == "" {
		return util.OptNone[string]()
	}

	ip, ok := orcapi.PrivateNetworkParseIpv4(trimmed)
	if !ok {
		return pin
	}

	return util.OptValue(ip.String())
}

func privateNetworkIpValidateParent(
	providerId string,
	reservationOwner orcapi.ResourceOwner,
	spec orcapi.PrivateNetworkIpSpecification,
) (orcapi.PrivateNetwork, *util.HttpError) {
	network, _, _, err := ResourceRetrieveEx[orcapi.PrivateNetwork](
		rpc.ActorSystem,
		privateNetworkType,
		ResourceParseId(spec.Network),
		orcapi.PermissionRead,
		orcapi.ResourceFlags{},
	)
	if err != nil {
		return orcapi.PrivateNetwork{}, util.HttpErr(http.StatusBadRequest, "unknown network requested")
	}

	if network.Specification.Product.Provider != providerId {
		return orcapi.PrivateNetwork{}, util.HttpErr(http.StatusBadRequest, "the reservation must use a network at the same provider")
	}

	if privateNetworkWorkspace(reservationOwner.Project, reservationOwner.CreatedBy) !=
		privateNetworkWorkspace(network.Owner.Project, network.Owner.CreatedBy) {
		return orcapi.PrivateNetwork{}, util.HttpErr(
			http.StatusBadRequest,
			"the reservation and the network must belong to the same workspace",
		)
	}

	if spec.IpAddress.Present {
		ip, ok := orcapi.PrivateNetworkParseIpv4(spec.IpAddress.Value)
		if !ok {
			return orcapi.PrivateNetwork{}, util.HttpErr(http.StatusBadRequest, "invalid IP address requested")
		}

		if network.Status.CidrBlock.Present {
			cidr, cidrOk := orcapi.PrivateNetworkParseCidr(network.Status.CidrBlock.Value)
			if !cidrOk || !orcapi.PrivateNetworkHostAddress(cidr, ip) {
				return orcapi.PrivateNetwork{}, util.HttpErr(http.StatusBadRequest, "the IP address cannot be used in this network")
			}
		}
	}

	return network, nil
}

func privateNetworkIpValidateEffective(r *resource, ip *internalPrivateNetworkIp, effectiveIp string) *util.HttpError {
	network, _, _, err := ResourceRetrieveEx[orcapi.PrivateNetwork](
		rpc.ActorSystem,
		privateNetworkType,
		ResourceParseId(ip.Network),
		orcapi.PermissionRead,
		orcapi.ResourceFlags{},
	)
	if err != nil {
		return util.HttpErr(http.StatusBadRequest, "unknown parent network")
	}

	if resourceSpecificationHasProduct(r.BaseSpec) && r.BaseSpec.Product.Provider != network.Specification.Product.Provider {
		return util.HttpErr(http.StatusConflict, "the reservation belongs to a different provider than its network")
	}

	if privateNetworkWorkspace(r.Owner.Project, r.Owner.CreatedBy) !=
		privateNetworkWorkspace(network.Owner.Project, network.Owner.CreatedBy) {
		return util.HttpErr(http.StatusConflict, "the reservation and the network belong to different workspaces")
	}

	cidrBlock := network.Status.CidrBlock
	if !cidrBlock.Present {
		return util.HttpErr(http.StatusConflict, "the network has no address range yet")
	}

	parsed, ok := orcapi.PrivateNetworkParseIpv4(effectiveIp)
	if !ok {
		return util.HttpErr(http.StatusBadRequest, "invalid IP address")
	}

	cidr, cidrOk := orcapi.PrivateNetworkParseCidr(cidrBlock.Value)
	if !cidrOk || !orcapi.PrivateNetworkHostAddress(cidr, parsed) {
		return util.HttpErr(http.StatusBadRequest, "the IP address cannot be used in this network")
	}

	if ip.RequestedIp.Present && ip.RequestedIp.Value != effectiveIp {
		return util.HttpErr(
			http.StatusConflict,
			"the effective ip address does not match the requested address",
		)
	}

	return nil
}

func PrivateNetworkIpUpdateLabels(actor rpc.Actor, request fndapi.BulkRequest[orcapi.PrivateNetworkIpsUpdateLabelsRequest]) *util.HttpError {
	for _, reqItem := range request.Items {
		err := ResourceUpdateLabelsThroughProvider[orcapi.PrivateNetworkIp](
			actor,
			privateNetworkIpType,
			reqItem.Id,
			reqItem.Labels,
			func(t *orcapi.PrivateNetworkIp, labels map[string]string) {
				t.Specification.Labels = labels
			},
			orcapi.PrivateNetworkIpsProviderOnUpdatedLabels,
		)

		if err != nil {
			return err
		}
	}

	return nil
}

type internalPrivateNetworkIp struct {
	Network     string
	RequestedIp util.Option[string]
	IpAddress   util.Option[string]
}

func privateNetworkIpLoad(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource) {
	rows := db.Select[struct {
		Resource    int64
		Network     string
		RequestedIp sql.Null[string]
		IpAddress   sql.Null[string]
	}](
		tx,
		`
			select resource, network, requested_ip, ip_address
			from app_orchestrator.private_network_ips
			where resource = some(:ids::int8[])
	    `,
		db.Params{
			"ids": ids,
		},
	)

	for _, row := range rows {
		resources[ResourceId(row.Resource)].Extra = &internalPrivateNetworkIp{
			Network:     row.Network,
			RequestedIp: util.SqlNullToOpt(row.RequestedIp),
			IpAddress:   util.SqlNullToOpt(row.IpAddress),
		}
	}
}

func privateNetworkIpPersist(b *db.Batch, r *resource) {
	if r.MarkedForDeletion {
		db.BatchExec(
			b,
			`delete from app_orchestrator.private_network_ips where resource = :resource`,
			db.Params{
				"resource": r.Id,
			},
		)
	} else {
		ip := r.Extra.(*internalPrivateNetworkIp)

		networkId := ResourceParseId(ip.Network)

		db.BatchExec(
			b,
			`
				insert into app_orchestrator.private_network_ips(resource, network, requested_ip, ip_address)
				values (:resource, cast(:network as bigint), :requested_ip, :ip_address)
				on conflict (resource) do update set
					network = excluded.network,
					requested_ip = excluded.requested_ip,
					ip_address = excluded.ip_address
		    `,
			db.Params{
				"resource":     r.Id,
				"network":      networkId,
				"requested_ip": ip.RequestedIp.Sql(),
				"ip_address":   ip.IpAddress.Sql(),
			},
		)
	}
}

func privateNetworkIpTransform(
	r orcapi.Resource,
	specification orcapi.ResourceSpecification,
	extra any,
	flags orcapi.ResourceFlags,
	actor rpc.Actor,
) any {
	ip := extra.(*internalPrivateNetworkIp)

	result := orcapi.PrivateNetworkIp{
		Resource: r,
		Specification: orcapi.PrivateNetworkIpSpecification{
			Network:               ip.Network,
			IpAddress:             ip.RequestedIp,
			ResourceSpecification: specification,
		},
		Status: orcapi.PrivateNetworkIpStatus{
			IpAddress: ip.IpAddress,
		},
	}

	if (flags.IncludeProduct || flags.IncludeSupport) && resourceSpecificationHasProduct(specification) {
		support, _ := SupportByProduct[orcapi.PrivateNetworkIpSupport](privateNetworkIpType, specification.Product)
		result.Status.ResourceStatus = orcapi.ResourceStatus[orcapi.PrivateNetworkIpSupport]{
			ResolvedSupport: util.OptValue(support.ToApi()),
			ResolvedProduct: util.OptValue(support.Product),
		}
	}

	return result
}

func privateNetworkSameWorkspace(actor rpc.Actor, owner orcapi.ResourceOwner) bool {
	var actorProject util.Option[string]
	if actor.Project.Present {
		actorProject = util.OptValue(string(actor.Project.Value))
	}

	return privateNetworkWorkspace(actorProject, actor.Username) ==
		privateNetworkWorkspace(owner.Project, owner.CreatedBy)
}

func privateNetworkWorkspace(project util.Option[string], username string) string {
	if project.Present {
		return "project:" + project.Value
	}
	return "user:" + username
}

func privateNetworkIpReservationsByAddress(network orcapi.PrivateNetwork, normalizedIp string) []*resource {
	reference := network.Owner.Project.GetOrDefault(network.Owner.CreatedBy)

	idxBucket := resourceGetAndLoadIndex(privateNetworkIpType, reference)
	idxBucket.Mu.RLock()
	ids := append([]ResourceId(nil), idxBucket.ByOwner[reference]...)
	idxBucket.Mu.RUnlock()

	var result []*resource
	for _, id := range ids {
		b := resourceGetBucket(privateNetworkIpType, id)
		r, ok, _ := resourcesReadEx(rpc.ActorSystem, privateNetworkIpType, orcapi.PermissionRead, b, id, nil)
		if !ok || r == nil || r.MarkedForDeletion {
			continue
		}

		ip, valid := r.Extra.(*internalPrivateNetworkIp)
		if !valid || ip.Network != network.Id {
			continue
		}

		if ip.IpAddress.GetOrDefault("") != normalizedIp && ip.RequestedIp.GetOrDefault("") != normalizedIp {
			continue
		}

		result = append(result, r)
	}

	return result
}

func privateNetworkIpCheckReservation(
	actor rpc.Actor,
	network orcapi.PrivateNetwork,
	normalizedIp string,
) *util.HttpError {
	networkWorkspace := privateNetworkWorkspace(network.Owner.Project, network.Owner.CreatedBy)

	for _, r := range privateNetworkIpReservationsByAddress(network, normalizedIp) {
		if privateNetworkWorkspace(r.Owner.Project, r.Owner.CreatedBy) != networkWorkspace {
			return util.HttpErr(
				http.StatusForbidden,
				"the reserved ip address '%s' belongs to a different workspace",
				normalizedIp,
			)
		}

		if resourceSpecificationHasProduct(r.BaseSpec) && r.BaseSpec.Product.Provider != network.Specification.Product.Provider {
			return util.HttpErr(
				http.StatusForbidden,
				"the reserved ip address '%s' belongs to a different provider",
				normalizedIp,
			)
		}

		_, _, _, err := ResourceRetrieveEx[orcapi.PrivateNetworkIp](
			actor,
			privateNetworkIpType,
			r.Id,
			orcapi.PermissionEdit,
			orcapi.ResourceFlags{},
		)
		if err != nil {
			return util.HttpErr(
				http.StatusForbidden,
				"the ip address '%s' is reserved, request access to it or use a different address",
				normalizedIp,
			)
		}
	}

	return nil
}
