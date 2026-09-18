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
			var flags resourceCreateFlags
			if reqItem.ProjectAllRead {
				flags |= resourceCreateAllRead
			}

			if reqItem.ProjectAllWrite {
				flags |= resourceCreateAllWrite
			}

			id, _, err := ResourceCreateEx[orcapi.PrivateNetworkIp](
				privateNetworkIpType,
				orcapi.ResourceOwner{
					CreatedBy: reqItem.CreatedBy.GetOrDefault("_ucloud"),
					Project:   util.OptStringIfNotEmpty(reqItem.Project.Value),
				},
				nil,
				reqItem.Spec.ResourceSpecification,
				reqItem.ProviderGeneratedId,
				&internalPrivateNetworkIp{
					Network:   reqItem.Spec.Network,
					IpAddress: reqItem.Spec.IpAddress,
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
			if item.Update.IpAddress.Present {
				if _, ok := privateNetworkParseIpv4(item.Update.IpAddress.Value); !ok {
					return util.Empty{}, util.HttpErr(http.StatusBadRequest, "invalid IP address")
				}
			}

			ok := ResourceUpdate(
				info.Actor,
				privateNetworkIpType,
				ResourceParseId(item.Id),
				orcapi.PermissionProvider,
				func(r *resource, mapped orcapi.PrivateNetworkIp) {
					ip := r.Extra.(*internalPrivateNetworkIp)
					if item.Update.IpAddress.Present {
						ip.IpAddress = item.Update.IpAddress
					}
				},
			)

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
		network, _, _, err := ResourceRetrieveEx[orcapi.PrivateNetwork](
			actor,
			privateNetworkType,
			ResourceParseId(networkId),
			orcapi.PermissionEdit,
			orcapi.ResourceFlags{IncludeProduct: true},
		)
		if err != nil {
			return nil, util.HttpErr(http.StatusForbidden, "you cannot use this network")
		}

		if !privateNetworkSameWorkspace(actor, network.Owner) {
			return nil, util.HttpErr(http.StatusForbidden, "the reservation and the network must belong to the same workspace")
		}

		if network.Specification.Product.Provider != item.Product.Provider {
			return nil, util.HttpErr(http.StatusForbidden, "you cannot use this network at this provider")
		}

		if item.IpAddress.Present {
			if _, ok := privateNetworkParseIpv4(item.IpAddress.Value); !ok {
				return nil, util.HttpErr(http.StatusBadRequest, "invalid IP address requested")
			}
		}

		resc, err := ResourceCreateThroughProvider(
			actor,
			privateNetworkIpType,
			item.ResourceSpecification,
			&internalPrivateNetworkIp{
				Network:   networkId,
				IpAddress: item.IpAddress,
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
	Network   string
	IpAddress util.Option[string]
}

func privateNetworkIpLoad(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource) {
	rows := db.Select[struct {
		Resource  int64
		Network   string
		IpAddress sql.Null[string]
	}](
		tx,
		`
			select resource, network, ip_address
			from app_orchestrator.private_network_ips
			where resource = some(:ids::int8[])
	    `,
		db.Params{
			"ids": ids,
		},
	)

	for _, row := range rows {
		resources[ResourceId(row.Resource)].Extra = &internalPrivateNetworkIp{
			Network:   row.Network,
			IpAddress: util.SqlNullToOpt(row.IpAddress),
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

		db.BatchExec(
			b,
			`
				insert into app_orchestrator.private_network_ips(resource, network, ip_address)
				values (:resource, :network, :ip_address)
				on conflict (resource) do update set
					network = excluded.network,
					ip_address = excluded.ip_address
		    `,
			db.Params{
				"resource":   r.Id,
				"network":    ip.Network,
				"ip_address": ip.IpAddress.Sql(),
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
			IpAddress:             ip.IpAddress,
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
	jobWorkspace := owner.Project.GetOrDefault(owner.CreatedBy)
	actorWorkspace := string(actor.Project.GetOrDefault(rpc.ProjectId(actor.Username)))
	return jobWorkspace == actorWorkspace
}
