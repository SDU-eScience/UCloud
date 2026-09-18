package orchestrator

import (
	"fmt"
	"net"
	"net/http"
	"regexp"
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
				err := privateNetworkValidateCidrBlock(item.Update.CidrBlock.Value)
				if err != nil {
					return util.Empty{}, err
				}
			}

			ok := ResourceUpdate(
				info.Actor,
				privateNetworkType,
				ResourceParseId(item.Id),
				orcapi.PermissionProvider,
				func(r *resource, mapped orcapi.PrivateNetwork) {
					network := r.Extra.(*internalPrivateNetwork)
					if item.Update.CidrBlock.Present {
						network.CidrBlock = item.Update.CidrBlock
					}
				},
			)

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
		err := ResourceDeleteThroughProvider(actor, privateNetworkType, item.Id, orcapi.PrivateNetworksProviderDelete)
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
}

func privateNetworkLoad(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource) {
	rows := db.Select[struct {
		Resource  int64
		Name      string
		Subdomain string
		Cidr      sql.Null[string]
		CidrBlock sql.Null[string]
	}](
		tx,
		`
			select resource, name, subdomain, cidr, cidr_block
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
			Cidr:      util.SqlNullToOpt(row.Cidr),
			CidrBlock: util.SqlNullToOpt(row.CidrBlock),
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
				insert into app_orchestrator.private_networks(resource, name, subdomain, cidr, cidr_block)
				values (:resource, :name, :subdomain, :cidr, :cidr_block)
				on conflict (resource) do update set
					name = excluded.name,
					subdomain = excluded.subdomain,
					cidr = excluded.cidr,
					cidr_block = excluded.cidr_block
		    `,
			db.Params{
				"resource":   resource.Id,
				"name":       network.Name,
				"subdomain":  network.Subdomain,
				"cidr":       network.Cidr.Sql(),
				"cidr_block": network.CidrBlock.Sql(),
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
			Members:   []string{},
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

func privateNetworkCidrContainsIp(cidr string, ip net.IP) bool {
	_, parsed, err := net.ParseCIDR(cidr)
	if err != nil || parsed == nil {
		return false
	}

	return parsed.Contains(ip)
}

func privateNetworkParseIpv4(address string) (net.IP, bool) {
	if strings.Contains(address, ":") {
		return nil, false
	}

	ip := net.ParseIP(address)
	if ip == nil || ip.To4() == nil {
		return nil, false
	}

	return ip.To4(), true
}

func privateNetworkIpIsPinnable(cidr string, ip net.IP) bool {
	if !privateNetworkCidrContainsIp(cidr, ip) {
		return false
	}

	_, parsed, err := net.ParseCIDR(cidr)
	if err != nil || parsed == nil {
		return false
	}

	ip = ip.To4()
	base := parsed.IP.To4()
	if ip.Equal(base) {
		return false
	}

	prefixLen, _ := parsed.Mask.Size()
	hostBits := 32 - prefixLen
	if hostBits <= 0 || hostBits >= 32 {
		return false
	}

	broadcast := make(net.IP, 4)
	for i := 0; i < 4; i++ {
		broadcast[i] = base[i] | ^parsed.Mask[i]
	}
	if ip.Equal(broadcast) {
		return false
	}

	gateway := make(net.IP, 4)
	copy(gateway, base)
	gateway[3] = 1
	if ip.Equal(gateway) {
		return false
	}

	return true
}

func privateNetworkCidrOverlap(a string, b string) bool {
	_, aNet, errA := net.ParseCIDR(a)
	_, bNet, errB := net.ParseCIDR(b)
	if errA != nil || errB != nil {
		return false
	}

	return aNet.Contains(bNet.IP) || bNet.Contains(aNet.IP)
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

			if existing.Status.CidrBlock.Present && network.Status.CidrBlock.Present &&
				privateNetworkCidrOverlap(existing.Status.CidrBlock.Value, network.Status.CidrBlock.Value) {
				return util.HttpErr(
					http.StatusBadRequest,
					"a job cannot attach two networks with overlapping address ranges",
				)
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
	prefixLen, err := privateNetworkParseCidr(cidr)
	if err != nil || prefixLen < 16 || prefixLen > 24 {
		return util.HttpErr(
			http.StatusBadRequest,
			"invalid private network CIDR requested, it must be an IPv4 CIDR with a prefix length between 16 and 24",
		)
	}

	return nil
}

func privateNetworkValidateCidrBlock(cidr string) *util.HttpError {
	_, err := privateNetworkParseCidr(cidr)
	if err != nil {
		return util.HttpErr(http.StatusBadRequest, "invalid private network CIDR block")
	}

	return nil
}

func privateNetworkParseCidr(cidr string) (int, *util.HttpError) {
	if strings.Contains(cidr, ":") {
		return 0, util.HttpErr(http.StatusBadRequest, "only IPv4 CIDRs are supported")
	}

	if !strings.Contains(cidr, "/") {
		return 0, util.HttpErr(http.StatusBadRequest, "invalid CIDR requested, a prefix length is required")
	}

	_, parsed, err := net.ParseCIDR(cidr)
	if err != nil || parsed == nil {
		return 0, util.HttpErr(http.StatusBadRequest, "invalid CIDR requested")
	}

	if parsed.IP.To4() == nil {
		return 0, util.HttpErr(http.StatusBadRequest, "only IPv4 CIDRs are supported")
	}

	prefixLen, _ := parsed.Mask.Size()
	return prefixLen, nil
}
