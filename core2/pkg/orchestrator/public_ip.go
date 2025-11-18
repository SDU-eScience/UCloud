package orchestrator

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"strings"

	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const publicIpType = "network_ip"

func initPublicIps() {
	InitResourceType(
		publicIpType,
		resourceTypeCreateWithoutAdmin,
		publicIpLoad,
		publicIpPersist,
		publicIpTransform,
		nil,
	)

	orcapi.PublicIpsBrowse.Handler(func(info rpc.RequestInfo, request orcapi.PublicIpsBrowseRequest) (fndapi.PageV2[orcapi.PublicIp], *util.HttpError) {
		sortByFn := ResourceDefaultComparator(func(item orcapi.PublicIp) orcapi.Resource {
			return item.Resource
		}, request.ResourceFlags)

		return ResourceBrowse(
			info.Actor,
			publicIpType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.PublicIp) bool {
				return true
			},
			sortByFn,
		), nil
	})

	orcapi.PublicIpsControlBrowse.Handler(func(info rpc.RequestInfo, request orcapi.PublicIpsControlBrowseRequest) (fndapi.PageV2[orcapi.PublicIp], *util.HttpError) {
		return ResourceBrowse(
			info.Actor,
			publicIpType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.PublicIp) bool {
				return true
			},
			nil,
		), nil
	})

	orcapi.PublicIpsDelete.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		for _, item := range request.Items {
			err := ResourceDeleteThroughProvider(info.Actor, publicIpType, item.Id, orcapi.PublicIpsProviderDelete)
			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, err
			}
		}

		return fndapi.BulkResponse[util.Empty]{Responses: make([]util.Empty, len(request.Items))}, nil
	})

	orcapi.PublicIpsCreate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.PublicIPSpecification]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		// TODO Check if we have an allocation?
		var ids []fndapi.FindByStringId
		for _, item := range request.Items {
			supp, ok := SupportByProduct[orcapi.PublicIpSupport](publicIpType, item.Product)
			if !ok {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(
					http.StatusNotFound,
					"unknown product requested",
				)
			}

			if item.Firewall.Present {
				if !supp.Has(publicIpFeatureFirewall) {
					return fndapi.BulkResponse[fndapi.FindByStringId]{}, featureNotSupportedError
				}

				err := publicIpValidateFirewall(item.Firewall.Value)
				if err != nil {
					return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
				}
			}

			ip := &internalPublicIp{
				Firewall: item.Firewall.GetOrDefault(orcapi.Firewall{}),
			}

			resc, err := ResourceCreateThroughProvider[orcapi.PublicIp](
				info.Actor,
				publicIpType,
				item.Product,
				ip,
				orcapi.PublicIpsProviderCreate,
			)

			if err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			}

			ids = append(ids, fndapi.FindByStringId{Id: resc.Id})
		}

		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: ids}, nil
	})

	orcapi.PublicIpsSearch.Handler(func(info rpc.RequestInfo, request orcapi.PublicIpsSearchRequest) (fndapi.PageV2[orcapi.PublicIp], *util.HttpError) {
		return ResourceBrowse(
			info.Actor,
			publicIpType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.PublicIp) bool {
				if addr := item.Status.IpAddress; addr.Present && strings.Contains(addr.Value, request.Query) {
					return true
				}
				// TODO Something else?
				return false
			},
			nil,
		), nil
	})

	orcapi.PublicIpsRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.PublicIpsRetrieveRequest) (orcapi.PublicIp, *util.HttpError) {
		return ResourceRetrieve[orcapi.PublicIp](info.Actor, publicIpType, ResourceParseId(request.Id), request.ResourceFlags)
	})

	orcapi.PublicIpsControlRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.PublicIpsControlRetrieveRequest) (orcapi.PublicIp, *util.HttpError) {
		return ResourceRetrieve[orcapi.PublicIp](info.Actor, publicIpType, ResourceParseId(request.Id), request.ResourceFlags)
	})

	orcapi.PublicIpsUpdateAcl.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.UpdatedAcl]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		for _, item := range request.Items {
			err := ResourceUpdateAcl(info.Actor, publicIpType, item)
			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, err
			}
		}

		return fndapi.BulkResponse[util.Empty]{Responses: make([]util.Empty, len(request.Items))}, nil
	})

	orcapi.PublicIpsRetrieveProducts.Handler(func(info rpc.RequestInfo, request util.Empty) (orcapi.SupportByProvider[orcapi.PublicIpSupport], *util.HttpError) {
		return SupportRetrieveProducts[orcapi.PublicIpSupport](publicIpType), nil
	})

	orcapi.PublicIpsControlRegister.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ProviderRegisteredResource[orcapi.PublicIPSpecification]]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
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

			id, _, err := ResourceCreateEx[orcapi.PublicIp](
				publicIpType,
				orcapi.ResourceOwner{
					CreatedBy: reqItem.CreatedBy.GetOrDefault("_ucloud"),
					Project:   reqItem.Project.Value,
				},
				nil,
				util.OptValue(reqItem.Spec.Product),
				reqItem.ProviderGeneratedId,
				&internalPublicIp{
					Firewall: reqItem.Spec.Firewall.GetOrDefault(orcapi.Firewall{}),
				},
				flags,
			)

			ResourceConfirm(publicIpType, id)

			if err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			} else {
				responses = append(responses, fndapi.FindByStringId{Id: fmt.Sprint(id)})
			}
		}

		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: responses}, nil
	})

	orcapi.PublicIpsControlAddUpdate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ResourceUpdateAndId[orcapi.PublicIpUpdate]]) (util.Empty, *util.HttpError) {
		for _, item := range request.Items {
			ok := ResourceUpdate(
				info.Actor,
				publicIpType,
				ResourceParseId(item.Id),
				orcapi.PermissionProvider,
				func(r *resource, mapped orcapi.PublicIp) {
					ip := r.Extra.(*internalPublicIp)

					change := item.Update.ChangeIpAddress.GetOrDefault(false)
					if change {
						ip.IpAddress = item.Update.NewIpAddress
					}
				},
			)

			if !ok {
				return util.Empty{}, util.HttpErr(http.StatusNotFound, "not found or permission denied")
			}
		}

		return util.Empty{}, nil
	})

	orcapi.PublicIpsUpdateFirewall.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.PublicIpUpdateFirewallRequest]) (util.Empty, *util.HttpError) {
		byProvider := map[string][]orcapi.PublicIpProviderUpdateFirewallRequest{}
		for _, item := range request.Items {
			resc, _, _, err := ResourceRetrieveEx[orcapi.PublicIp](
				info.Actor,
				publicIpType,
				ResourceParseId(item.Id),
				orcapi.PermissionEdit,
				orcapi.ResourceFlags{},
			)

			if err != nil {
				return util.Empty{}, err
			}

			supp, ok := SupportByProduct[orcapi.PublicIpSupport](publicIpType, resc.Specification.Product)
			if !ok && !supp.Has(publicIpFeatureFirewall) {
				return util.Empty{}, featureNotSupportedError
			}

			err = publicIpValidateFirewall(item.Firewall)
			if err != nil {
				return util.Empty{}, err
			}

			provider := resc.Specification.Product.Provider
			byProvider[provider] = append(byProvider[provider], orcapi.PublicIpProviderUpdateFirewallRequest{
				PublicIp: resc,
				Firewall: item.Firewall,
			})
		}

		for providerId, items := range byProvider {
			_, err := InvokeProvider(
				providerId,
				orcapi.PublicIpsProviderUpdateFirewall,
				fndapi.BulkRequestOf(items...),
				ProviderCallOpts{
					Username: util.OptValue(info.Actor.Username),
					Reason:   util.OptValue("user initiated firewall update"),
				},
			)

			for _, item := range items {
				_ = ResourceUpdate(
					info.Actor,
					publicIpType,
					ResourceParseId(item.PublicIp.Id),
					orcapi.PermissionEdit,
					func(r *resource, mapped orcapi.PublicIp) {
						ip := r.Extra.(*internalPublicIp)
						ip.Firewall = item.Firewall
					},
				)
			}

			if err != nil {
				return util.Empty{}, err
			}
		}

		return util.Empty{}, nil
	})
}

func PublicIpBind(id string, jobId string) {
	ResourceUpdate[orcapi.PublicIp](
		rpc.ActorSystem,
		publicIpType,
		ResourceParseId(id),
		orcapi.PermissionRead,
		func(r *resource, mapped orcapi.PublicIp) {
			ip := r.Extra.(*internalPublicIp)
			ip.BoundTo = []string{jobId}
		},
	)
}

func PublicIpUnbind(id string, jobId string) {
	ResourceUpdate[orcapi.PublicIp](
		rpc.ActorSystem,
		publicIpType,
		ResourceParseId(id),
		orcapi.PermissionRead,
		func(r *resource, mapped orcapi.PublicIp) {
			ip := r.Extra.(*internalPublicIp)
			ip.BoundTo = util.RemoveFirst(ip.BoundTo, jobId)
		},
	)
}

func publicIpValidateFirewall(firewall orcapi.Firewall) *util.HttpError {
	for i, port := range firewall.OpenPorts {
		var err *util.HttpError
		field := fmt.Sprintf("firewall.openPorts[%d]", i)

		util.ValidateEnum(&port.Protocol, orcapi.IpProtocolOptions, field+".protocol", &err)
		util.ValidateInteger(port.Start, field+".start", util.OptValue(0), util.OptValue(1024*64-1), &err)
		util.ValidateInteger(port.End, field+".end", util.OptValue(0), util.OptValue(1024*64-1), &err)

		if err != nil {
			return err
		}
	}
	return nil
}

type internalPublicIp struct {
	Firewall  orcapi.Firewall
	BoundTo   []string
	IpAddress util.Option[string]
}

func publicIpLoad(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource) {
	rows := db.Select[struct {
		Resource      int
		Firewall      string
		StatusBoundTo []int
		IpAddress     sql.Null[string]
	}](
		tx,
		`
			select resource, firewall, status_bound_to, ip_address
			from app_orchestrator.network_ips
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

		result := &internalPublicIp{
			BoundTo:   boundTo,
			IpAddress: util.SqlNullToOpt(row.IpAddress),
		}
		_ = json.Unmarshal([]byte(row.Firewall), &result.Firewall)

		resources[ResourceId(row.Resource)].Extra = result
	}
}

func publicIpPersist(b *db.Batch, r *resource) {
	if r.MarkedForDeletion {
		db.BatchExec(
			b,
			`delete from app_orchestrator.network_ips where resource = :id`,
			db.Params{
				"id": r.Id,
			},
		)
	} else {
		ip := r.Extra.(*internalPublicIp)

		firewallJson, _ := json.Marshal(ip.Firewall)

		boundTo := []int64{}
		for _, jobId := range ip.BoundTo {
			id, _ := strconv.ParseInt(jobId, 10, 64)
			boundTo = append(boundTo, id)
		}

		db.BatchExec(
			b,
			`
				insert into app_orchestrator.network_ips (current_state, firewall, ip_address, resource, status_bound_to) 
				values ('READY', :firewall, :ip, :id, :bound_to)
				on conflict (resource) do update set
					firewall = excluded.firewall,
					ip_address = excluded.ip_address,
					status_bound_to = excluded.status_bound_to
		    `,
			db.Params{
				"firewall": firewallJson,
				"ip":       ip.IpAddress.Sql(),
				"id":       r.Id,
				"bound_to": boundTo,
			},
		)
	}
}

func publicIpTransform(
	r orcapi.Resource,
	product util.Option[accapi.ProductReference],
	extra any,
	flags orcapi.ResourceFlags,
	actor rpc.Actor,
) any {
	ip := extra.(*internalPublicIp)
	result := orcapi.PublicIp{
		Resource: r,
		Specification: orcapi.PublicIPSpecification{
			Product:  product.Value,
			Firewall: util.OptValue(ip.Firewall),
		},
		Status: orcapi.PublicIpStatus{
			State:     "READY",
			BoundTo:   util.NonNilSlice(ip.BoundTo),
			IpAddress: ip.IpAddress,
		},
	}

	if flags.IncludeSupport || flags.IncludeProduct {
		supp, _ := SupportByProduct[orcapi.PublicIpSupport](publicIpType, product.Value)

		if flags.IncludeProduct {
			result.Status.ResolvedProduct.Set(supp.Product)
		}
		if flags.IncludeSupport {
			result.Status.ResolvedSupport.Set(supp.ToApi())
		}
	}

	return result
}
