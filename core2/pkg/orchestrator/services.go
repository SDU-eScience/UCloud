package orchestrator

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"net/http"
	"regexp"
	"slices"
	"strconv"
	"strings"
	"sync"
	"time"

	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const serviceType = "service"

var servicePortNameRegex = regexp.MustCompile(`^[a-z0-9]([-a-z0-9]*[a-z0-9])?$`)

const (
	serviceHealthCheckDefaultIntervalSeconds    = 5
	serviceHealthCheckDefaultTimeoutSeconds     = 2
	serviceHealthCheckDefaultHealthyThreshold   = 2
	serviceHealthCheckDefaultUnhealthyThreshold = 2
	serviceDefaultDrainTimeoutSeconds           = 30
)

func initServices() {
	InitResourceType(
		serviceType,
		resourceTypeCreateWithoutAdmin,
		serviceLoad,
		servicePersist,
		serviceTransform,
		nil,
	)

	ResourceAddIndexer(serviceType, func(r *resource) ResourceIndexer {
		return &serviceMembershipIndexer{r: r}
	})

	ResourceAddIndexer(ingressType, func(r *resource) ResourceIndexer {
		return &ingressTargetIndexer{r: r}
	})

	serviceFillIndex()

	orcapi.ServicesBrowse.Handler(func(info rpc.RequestInfo, request orcapi.ServicesBrowseRequest) (fndapi.PageV2[orcapi.Service], *util.HttpError) {
		return ServiceBrowse(info.Actor, request), nil
	})

	orcapi.ServicesControlBrowse.Handler(func(info rpc.RequestInfo, request orcapi.ServicesControlBrowseRequest) (fndapi.PageV2[orcapi.Service], *util.HttpError) {
		return ResourceBrowse[orcapi.Service](
			info.Actor,
			serviceType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.Service) bool {
				return true
			},
			nil,
		), nil
	})

	orcapi.ServicesCreate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ServiceSpecification]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		created, err := ServiceCreate(info.Actor, request)
		if err != nil {
			return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
		}

		ids := make([]fndapi.FindByStringId, 0, len(created))
		for _, resc := range created {
			ids = append(ids, fndapi.FindByStringId{Id: resc.Id})
		}

		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: ids}, nil
	})

	orcapi.ServicesDelete.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		return ServiceDelete(info.Actor, request)
	})

	orcapi.ServicesRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.ServicesRetrieveRequest) (orcapi.Service, *util.HttpError) {
		return ServiceRetrieve(info.Actor, request)
	})

	orcapi.ServicesControlRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.ServicesControlRetrieveRequest) (orcapi.Service, *util.HttpError) {
		return ResourceRetrieve[orcapi.Service](info.Actor, serviceType, ResourceParseId(request.Id), request.ResourceFlags)
	})

	orcapi.ServicesUpdate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ResourceUpdateAndId[orcapi.ServicesUpdateRequest]]) (util.Empty, *util.HttpError) {
		for _, item := range request.Items {
			err := ServiceUpdate(info.Actor, item.Id, item.Update)
			if err != nil {
				return util.Empty{}, err
			}
		}
		return util.Empty{}, nil
	})

	orcapi.ServicesAddMembers.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ServicesMembersRequest]) (util.Empty, *util.HttpError) {
		for _, item := range request.Items {
			err := ServiceAddMembers(info.Actor, item.Id, item.JobIds)
			if err != nil {
				return util.Empty{}, err
			}
		}
		return util.Empty{}, nil
	})

	orcapi.ServicesRemoveMembers.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ServicesMembersRequest]) (util.Empty, *util.HttpError) {
		for _, item := range request.Items {
			err := ServiceRemoveMembers(info.Actor, item.Id, item.JobIds)
			if err != nil {
				return util.Empty{}, err
			}
		}
		return util.Empty{}, nil
	})

	orcapi.ServicesUpdateAcl.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.UpdatedAcl]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		for _, item := range request.Items {
			err := ResourceUpdateAcl(info.Actor, serviceType, item)
			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, err
			}
		}

		return fndapi.BulkResponse[util.Empty]{Responses: make([]util.Empty, len(request.Items))}, nil
	})

	orcapi.ServicesUpdateLabels.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ServicesUpdateLabelsRequest]) (util.Empty, *util.HttpError) {
		return util.Empty{}, ServiceUpdateLabels(info.Actor, request)
	})

	orcapi.ServicesRetrieveProducts.Handler(func(info rpc.RequestInfo, request util.Empty) (orcapi.SupportByProvider[orcapi.ServiceSupport], *util.HttpError) {
		return SupportRetrieveProducts[orcapi.ServiceSupport](serviceType), nil
	})

	orcapi.ServicesControlAddUpdate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ResourceUpdateAndId[orcapi.ServiceUpdate]]) (util.Empty, *util.HttpError) {
		ids := make([]string, 0, len(request.Items))
		for _, item := range request.Items {
			ids = append(ids, item.Id)
		}
		if err := ResourceValidateProviderBatch(info.Actor, serviceType, ids); err != nil {
			return util.Empty{}, err
		}

		for _, item := range request.Items {
			ok := ResourceUpdate(
				info.Actor,
				serviceType,
				ResourceParseId(item.Id),
				orcapi.PermissionProvider,
				func(r *resource, mapped orcapi.Service) {
					svc := r.Extra.(*internalService)
					update := item.Update

					if update.ProvisioningState.Present {
						svc.ProvisioningState = update.ProvisioningState.Value
					}
					if update.Message.Present {
						svc.StatusMessage = update.Message
					}
					if update.InternalDnsName.Present {
						svc.InternalDnsName = update.InternalDnsName
					}
					if update.Backends.Present {
						svc.Backends = update.Backends.Value
					}
				},
			)

			if !ok {
				return util.Empty{}, util.HttpErr(http.StatusNotFound, "not found or permission denied (ID: %v)", item.Id)
			}
		}

		return util.Empty{}, nil
	})

	orcapi.ServicesControlUpdateLabels.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ServicesUpdateLabelsRequest]) (util.Empty, *util.HttpError) {
		for _, reqItem := range request.Items {
			err := ResourceUpdateLabels(info.Actor, serviceType, reqItem.Id, reqItem.Labels, orcapi.PermissionProvider)
			if err != nil {
				return util.Empty{}, err
			}
		}

		return util.Empty{}, nil
	})
}

func ServiceCreate(actor rpc.Actor, request fndapi.BulkRequest[orcapi.ServiceSpecification]) ([]orcapi.Service, *util.HttpError) {
	var responses []orcapi.Service

	for _, item := range request.Items {
		_, ok := SupportByProduct[orcapi.ServiceSupport](serviceType, item.Product)
		if !ok {
			return nil, util.HttpErr(http.StatusBadRequest, "unsupported operation")
		}

		item.Name = strings.TrimSpace(item.Name)
		item.Ports = serviceNormalizePorts(item.Ports)
		err := serviceValidateSpecification(actor, item)
		if err != nil {
			return nil, err
		}

		svc, err := ResourceCreateThroughProviderEx(
			actor,
			serviceType,
			item.ResourceSpecification,
			serviceToInternal(&item),
			orcapi.ServicesProviderCreate,
			ProviderCallOpts{Timeout: util.OptValue(30 * time.Second)},
		)

		if err != nil {
			return nil, err
		}

		responses = append(responses, svc)
	}
	return responses, nil
}

func ServiceBrowse(actor rpc.Actor, request orcapi.ServicesBrowseRequest) fndapi.PageV2[orcapi.Service] {
	sortByFn := ResourceDefaultComparator(func(item orcapi.Service) orcapi.Resource {
		return item.Resource
	}, request.ResourceFlags)

	return ResourceBrowse[orcapi.Service](
		actor,
		serviceType,
		request.Next,
		request.ItemsPerPage,
		request.ResourceFlags,
		func(item orcapi.Service) bool {
			return true
		},
		sortByFn,
	)
}

func ServiceRetrieve(actor rpc.Actor, request orcapi.ServicesRetrieveRequest) (orcapi.Service, *util.HttpError) {
	return ResourceRetrieve[orcapi.Service](actor, serviceType, ResourceParseId(request.Id), request.ResourceFlags)
}

func ServiceDelete(actor rpc.Actor, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
	for _, item := range request.Items {
		_, _, _, err := ResourceRetrieveEx[orcapi.Service](
			actor,
			serviceType,
			ResourceParseId(item.Id),
			orcapi.PermissionEdit,
			orcapi.ResourceFlags{},
		)
		if err != nil {
			return fndapi.BulkResponse[util.Empty]{}, err
		}

		referencingLinks := serviceReferencingLinks(ResourceParseId(item.Id))
		if len(referencingLinks) > 0 {
			ids := make([]string, 0, len(referencingLinks))
			for _, link := range referencingLinks {
				ids = append(ids, fmt.Sprint(link.ingress))
			}

			return fndapi.BulkResponse[util.Empty]{}, util.HttpErr(
				http.StatusConflict,
				"This service is currently the target of public link: %v",
				strings.Join(ids, ", "),
			)
		}

		err = ResourceDeleteThroughProvider(actor, serviceType, item.Id, orcapi.ServicesProviderDelete)
		if err != nil {
			return fndapi.BulkResponse[util.Empty]{}, err
		}
	}

	return fndapi.BulkResponse[util.Empty]{Responses: make([]util.Empty, len(request.Items))}, nil
}

func ServiceUpdate(actor rpc.Actor, id string, update orcapi.ServicesUpdateRequest) *util.HttpError {
	svc, _, _, err := ResourceRetrieveEx[orcapi.Service](
		actor,
		serviceType,
		ResourceParseId(id),
		orcapi.PermissionEdit,
		orcapi.ResourceFlags{},
	)
	if err != nil {
		return err
	}

	name := svc.Specification.Name
	if update.Name.Present {
		name = strings.TrimSpace(update.Name.Value)
		if name == "" {
			return util.HttpErr(http.StatusBadRequest, "name must not be empty")
		}
	}

	ports := svc.Specification.Ports
	if update.Ports.Present {
		ports = serviceNormalizePorts(update.Ports.Value)

		for _, link := range serviceReferencingLinks(ResourceParseId(id)) {
			newPort, exists := serviceFindPort(ports, link.port)
			if !exists {
				return util.HttpErr(http.StatusConflict, "port '%v' is in use by a public link and cannot be removed", link.port)
			}

			oldPort, _ := serviceFindPort(svc.Specification.Ports, link.port)
			if oldPort.Protocol != newPort.Protocol || oldPort.ApplicationProtocol != newPort.ApplicationProtocol {
				return util.HttpErr(
					http.StatusConflict,
					"port '%v' is in use by a public link and cannot change protocol or application protocol",
					link.port,
				)
			}
		}

		if _, err := serviceValidatePorts(ports); err != nil {
			return err
		}
	}

	_, err = InvokeProvider(svc.Specification.Product.Provider, orcapi.ServicesProviderUpdate, fndapi.BulkRequestOf(orcapi.ServicesProviderUpdateRequest{
		Service: svc,
		Name:    name,
		Ports:   ports,
	}), ProviderCallOpts{
		Username: util.OptValue(actor.Username),
		Reason:   util.OptValue("Updating service"),
	})

	if err != nil {
		return err
	}

	ok := ResourceUpdate(
		actor,
		serviceType,
		ResourceParseId(id),
		orcapi.PermissionEdit,
		func(r *resource, mapped orcapi.Service) {
			svcInternal := r.Extra.(*internalService)
			svcInternal.Name = name
			if update.Ports.Present {
				svcInternal.Ports = ports
			}
		},
	)

	if !ok {
		return util.HttpErr(http.StatusNotFound, "not found or permission denied")
	}
	return nil
}

func ServiceAddMembers(actor rpc.Actor, id string, jobIds []string) *util.HttpError {
	return serviceMutateMembers(actor, id, jobIds, true)
}

func ServiceRemoveMembers(actor rpc.Actor, id string, jobIds []string) *util.HttpError {
	return serviceMutateMembers(actor, id, jobIds, false)
}

func serviceMutateMembers(actor rpc.Actor, id string, jobIds []string, add bool) *util.HttpError {
	svc, _, _, err := ResourceRetrieveEx[orcapi.Service](
		actor,
		serviceType,
		ResourceParseId(id),
		orcapi.PermissionEdit,
		orcapi.ResourceFlags{},
	)
	if err != nil {
		return err
	}

	var added []string
	var removed []string
	provider := svc.Specification.Product.Provider

	for _, jobId := range jobIds {
		job, _, _, err := ResourceRetrieveEx[orcapi.Job](
			actor,
			jobType,
			ResourceParseId(jobId),
			orcapi.PermissionEdit,
			orcapi.ResourceFlags{},
		)
		if err != nil {
			return err
		}

		if job.Specification.Product.Provider != provider {
			return util.HttpErr(http.StatusBadRequest, "job '%v' belongs to a different provider than the service", jobId)
		}

		if add {
			if err := serviceValidateMemberNetworkAttachment(svc, job.Specification.Resources); err != nil {
				return err
			}
			if !slices.Contains(svc.Status.Members, jobId) {
				added = append(added, jobId)
			}
		} else {
			if slices.Contains(svc.Status.Members, jobId) {
				removed = append(removed, jobId)
			}
		}
	}

	if len(added) == 0 && len(removed) == 0 {
		return nil
	}

	_, err = InvokeProvider(provider, orcapi.ServicesProviderUpdateMembers, fndapi.BulkRequestOf(orcapi.ServicesProviderUpdateMembersRequest{
		Service:       svc,
		AddedJobIds:   added,
		RemovedJobIds: removed,
	}), ProviderCallOpts{
		Username: util.OptValue(actor.Username),
		Reason:   util.OptValue("Updating service members"),
	})

	if err != nil {
		return err
	}

	ok := ResourceUpdate(
		actor,
		serviceType,
		ResourceParseId(id),
		orcapi.PermissionEdit,
		func(r *resource, mapped orcapi.Service) {
			svcInternal := r.Extra.(*internalService)
			for _, jobId := range added {
				if !slices.Contains(svcInternal.Members, jobId) {
					svcInternal.Members = append(svcInternal.Members, jobId)
				}
			}
			for _, jobId := range removed {
				svcInternal.Members = util.RemoveFirst(svcInternal.Members, jobId)
			}
		},
	)

	if !ok {
		return util.HttpErr(http.StatusNotFound, "not found or permission denied")
	}

	return nil
}

func serviceValidateMemberNetworkAttachment(svc orcapi.Service, jobResources []orcapi.AppParameterValue) *util.HttpError {
	endpoint := svc.Specification.InternalEndpoint
	if !endpoint.Present {
		return nil
	}

	networkId := endpoint.Value.PrivateNetworkId
	for _, value := range jobResources {
		if value.Type == orcapi.AppParameterValueTypePrivateNetwork && value.Id == networkId {
			return nil
		}
	}

	return util.HttpErr(
		http.StatusBadRequest,
		"member jobs must attach the service's private network ('%v')",
		networkId,
	)
}

func ServiceUpdateLabels(actor rpc.Actor, request fndapi.BulkRequest[orcapi.ServicesUpdateLabelsRequest]) *util.HttpError {
	for _, reqItem := range request.Items {
		err := ResourceUpdateLabelsThroughProvider[orcapi.Service](
			actor,
			serviceType,
			reqItem.Id,
			reqItem.Labels,
			func(t *orcapi.Service, labels map[string]string) {
				t.Specification.Labels = labels
			},
			orcapi.ServicesProviderOnUpdatedLabels,
		)

		if err != nil {
			return err
		}
	}

	return nil
}

func serviceToInternal(spec *orcapi.ServiceSpecification) *internalService {
	result := &internalService{
		Name:              spec.Name,
		Ports:             spec.Ports,
		ProvisioningState: string(orcapi.ServiceStatePreparing),
	}

	if network := spec.InternalEndpoint; network.Present {
		result.InternalNetwork = util.OptValue(ResourceParseId(network.Value.PrivateNetworkId))
	}

	return result
}

func serviceValidateSpecification(actor rpc.Actor, spec orcapi.ServiceSpecification) *util.HttpError {
	if spec.Name == "" {
		return util.HttpErr(http.StatusBadRequest, "name must not be empty")
	}

	if _, err := serviceValidatePorts(spec.Ports); err != nil {
		return err
	}

	if network := spec.InternalEndpoint; network.Present {
		if strings.TrimSpace(network.Value.PrivateNetworkId) == "" {
			return util.HttpErr(http.StatusBadRequest, "internalEndpoint.privateNetworkId must not be empty")
		}

		networkResource, _, _, err := ResourceRetrieveEx[orcapi.PrivateNetwork](
			actor,
			privateNetworkType,
			ResourceParseId(network.Value.PrivateNetworkId),
			orcapi.PermissionEdit,
			orcapi.ResourceFlags{},
		)
		if err != nil {
			return util.HttpErr(http.StatusForbidden, "you cannot use this private network")
		}

		if networkResource.Specification.Product.Provider != spec.Product.Provider {
			return util.HttpErr(http.StatusBadRequest, "the private network belongs to a different provider")
		}
	}

	return nil
}

func serviceNormalizePorts(ports []orcapi.ServicePort) []orcapi.ServicePort {
	result := make([]orcapi.ServicePort, len(ports))
	copy(result, ports)

	for i, port := range result {
		if drain := port.DrainTimeoutSeconds; !drain.Present {
			result[i].DrainTimeoutSeconds = util.OptValue(serviceDefaultDrainTimeoutSeconds)
		}

		if !port.HealthCheck.Present {
			continue
		}

		check := port.HealthCheck.Value
		if check.IntervalSeconds <= 0 {
			check.IntervalSeconds = serviceHealthCheckDefaultIntervalSeconds
		}
		if check.TimeoutSeconds <= 0 {
			check.TimeoutSeconds = serviceHealthCheckDefaultTimeoutSeconds
		}
		if check.HealthyThreshold <= 0 {
			check.HealthyThreshold = serviceHealthCheckDefaultHealthyThreshold
		}
		if check.UnhealthyThreshold <= 0 {
			check.UnhealthyThreshold = serviceHealthCheckDefaultUnhealthyThreshold
		}

		result[i].HealthCheck = util.OptValue(check)
	}

	return result
}

func serviceValidatePorts(ports []orcapi.ServicePort) ([]orcapi.ServicePort, *util.HttpError) {
	if len(ports) == 0 {
		return nil, util.HttpErr(http.StatusBadRequest, "a service must declare at least one port")
	}

	seenNames := map[string]util.Empty{}
	for _, port := range ports {
		if strings.TrimSpace(port.Name) == "" {
			return nil, util.HttpErr(http.StatusBadRequest, "port name must not be empty")
		}
		if len(port.Name) > 63 || !servicePortNameRegex.MatchString(port.Name) {
			return nil, util.HttpErr(
				http.StatusBadRequest,
				"port name must use lowercase letters, numbers and dashes only",
			)
		}
		if _, duplicate := seenNames[port.Name]; duplicate {
			return nil, util.HttpErr(http.StatusBadRequest, "duplicate port name: %v", port.Name)
		}
		seenNames[port.Name] = util.Empty{}

		if port.Port < 1 || port.Port > 65535 {
			return nil, util.HttpErr(http.StatusBadRequest, "port must be a number between 1 and 65535")
		}

		switch port.Protocol {
		case orcapi.ServicePortProtocolTcp, orcapi.ServicePortProtocolUdp:
		default:
			return nil, util.HttpErr(http.StatusBadRequest, "protocol must be either TCP or UDP")
		}

		if appProto := port.ApplicationProtocol; appProto.Present {
			switch appProto.Value {
			case "HTTP", "HTTPS":
			default:
				return nil, util.HttpErr(http.StatusBadRequest, "applicationProtocol must be either HTTP or HTTPS")
			}
		}

		if check := port.HealthCheck; check.Present {
			switch check.Value.Type {
			case orcapi.ServiceHealthCheckTypeTcp, orcapi.ServiceHealthCheckTypeHttp, orcapi.ServiceHealthCheckTypeHttps:
			default:
				return nil, util.HttpErr(http.StatusBadRequest, "healthCheck type must be TCP, HTTP or HTTPS")
			}

			if check.Value.Type != orcapi.ServiceHealthCheckTypeTcp && check.Value.Path == "" {
				return nil, util.HttpErr(http.StatusBadRequest, "healthCheck path is required for HTTP checks")
			}

			if check.Value.IntervalSeconds <= 0 || check.Value.TimeoutSeconds <= 0 {
				return nil, util.HttpErr(http.StatusBadRequest, "healthCheck interval and timeout must be positive")
			}

			if check.Value.HealthyThreshold < 1 || check.Value.UnhealthyThreshold < 1 {
				return nil, util.HttpErr(http.StatusBadRequest, "healthCheck thresholds must be at least 1")
			}

			if portOverride := check.Value.Port; portOverride.Present {
				if portOverride.Value < 1 || portOverride.Value > 65535 {
					return nil, util.HttpErr(http.StatusBadRequest, "healthCheck port must be a number between 1 and 65535")
				}
			}
		}

		if drain := port.DrainTimeoutSeconds; drain.Present && drain.Value <= 0 {
			return nil, util.HttpErr(http.StatusBadRequest, "drainTimeoutSeconds must be positive when set")
		}
	}

	return ports, nil
}

type internalService struct {
	Name              string
	Ports             []orcapi.ServicePort
	InternalNetwork   util.Option[ResourceId]
	ProvisioningState string
	StatusMessage     util.Option[string]
	InternalDnsName   util.Option[string]
	Backends          []orcapi.ServiceBackendStatus
	Members           []string
}

func serviceLoad(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource) {
	rows := db.Select[struct {
		Resource          int64
		Name              string
		Ports             sql.Null[string]
		InternalNetwork   sql.Null[int64]
		ProvisioningState string
		StatusMessage     sql.Null[string]
		InternalDnsName   sql.Null[string]
		Backends          sql.Null[string]
	}](
		tx,
		`
			select resource, name, ports, internal_network, provisioning_state, status_message, internal_dns_name, backends
			from app_orchestrator.services
			where resource = some(:ids::int8[])
	    `,
		db.Params{
			"ids": ids,
		},
	)

	membersByService := map[int64][]string{}
	if len(ids) > 0 {
		memberRows := db.Select[struct {
			Service int64
			Job     int64
		}](
			tx,
			`
				select service, job
				from app_orchestrator.service_members
				where service = some(:ids::int8[])
				order by job
		    `,
			db.Params{
				"ids": ids,
			},
		)

		for _, row := range memberRows {
			membersByService[row.Service] = append(membersByService[row.Service], fmt.Sprint(row.Job))
		}
	}

	for _, row := range rows {
		svc := &internalService{
			Name:              row.Name,
			ProvisioningState: row.ProvisioningState,
			StatusMessage:     util.SqlNullToOpt(row.StatusMessage),
			InternalDnsName:   util.SqlNullToOpt(row.InternalDnsName),
			Members:           membersByService[row.Resource],
		}

		if row.Ports.Valid {
			_ = json.Unmarshal([]byte(row.Ports.V), &svc.Ports)
		}

		if row.InternalNetwork.Valid {
			svc.InternalNetwork = util.OptValue(ResourceId(row.InternalNetwork.V))
		}

		if row.Backends.Valid {
			_ = json.Unmarshal([]byte(row.Backends.V), &svc.Backends)
		}

		resources[ResourceId(row.Resource)].Extra = svc
	}
}

func servicePersist(b *db.Batch, resource *resource) {
	if resource.MarkedForDeletion {
		db.BatchExec(
			b,
			`delete from app_orchestrator.services where resource = :resource`,
			db.Params{
				"resource": resource.Id,
			},
		)
	} else {
		svc := resource.Extra.(*internalService)

		encodedPorts, _ := json.Marshal(util.NonNilSlice(svc.Ports))
		encodedBackends, _ := json.Marshal(util.NonNilSlice(svc.Backends))

		members := []int64{}
		for _, jobId := range svc.Members {
			id, _ := strconv.ParseInt(jobId, 10, 64)
			members = append(members, id)
		}

		db.BatchExec(
			b,
			`
				insert into app_orchestrator.services(resource, name, ports, internal_network, provisioning_state,
					status_message, internal_dns_name, backends)
				values (:resource, :name, :ports, :internal_network, :provisioning_state, :status_message,
					:internal_dns_name, :backends)
				on conflict (resource) do update set
					name = excluded.name,
					ports = excluded.ports,
					internal_network = excluded.internal_network,
					provisioning_state = excluded.provisioning_state,
					status_message = excluded.status_message,
					internal_dns_name = excluded.internal_dns_name,
					backends = excluded.backends
		    `,
			db.Params{
				"resource":           resource.Id,
				"name":               svc.Name,
				"ports":              encodedPorts,
				"internal_network":   svc.InternalNetwork.Sql(),
				"provisioning_state": svc.ProvisioningState,
				"status_message":     svc.StatusMessage.Sql(),
				"internal_dns_name":  svc.InternalDnsName.Sql(),
				"backends":           encodedBackends,
			},
		)

		db.BatchExec(
			b,
			`delete from app_orchestrator.service_members where service = :resource`,
			db.Params{
				"resource": resource.Id,
			},
		)

		for _, jobId := range members {
			db.BatchExec(
				b,
				`
					insert into app_orchestrator.service_members(service, job)
					values (:resource, :job)
					on conflict (service, job) do nothing
			    `,
				db.Params{
					"resource": resource.Id,
					"job":      jobId,
				},
			)
		}
	}
}

func serviceTransform(
	r orcapi.Resource,
	specification orcapi.ResourceSpecification,
	extra any,
	flags orcapi.ResourceFlags,
	actor rpc.Actor,
) any {
	svc := extra.(*internalService)

	result := orcapi.Service{
		Resource: r,
		Specification: orcapi.ServiceSpecification{
			Name:                  svc.Name,
			Ports:                 util.NonNilSlice(svc.Ports),
			ResourceSpecification: specification,
		},
		Status: orcapi.ServiceStatus{
			ProvisioningState: svc.ProvisioningState,
			Message:           svc.StatusMessage,
			Members:           util.NonNilSlice(svc.Members),
			Backends:          util.NonNilSlice(svc.Backends),
		},
	}

	if network := svc.InternalNetwork; network.Present {
		endpoint := orcapi.ServiceInternalEndpoint{
			PrivateNetworkId: fmt.Sprint(network.Value),
		}

		if dnsName := svc.InternalDnsName; dnsName.Present {
			endpoint.DnsName = dnsName.Value
		}

		result.Specification.InternalEndpoint = util.OptValue(orcapi.ServiceInternalEndpointSpec{
			PrivateNetworkId: endpoint.PrivateNetworkId,
		})
		result.Status.InternalEndpoint = util.OptValue(endpoint)
	}

	if (flags.IncludeProduct || flags.IncludeSupport) && resourceSpecificationHasProduct(specification) {
		support, _ := SupportByProduct[orcapi.ServiceSupport](serviceType, specification.Product)
		result.Status.ResourceStatus = orcapi.ResourceStatus[orcapi.ServiceSupport]{
			ResolvedSupport: util.OptValue(support.ToApi()),
			ResolvedProduct: util.OptValue(support.Product),
		}
	}
	return result
}

func serviceRemoveMembershipsOfJob(jobId string) {
	for _, serviceId := range serviceIdsOfJob(jobId) {
		ResourceUpdate[orcapi.Service](
			rpc.ActorSystem,
			serviceType,
			ResourceParseId(serviceId),
			orcapi.PermissionRead,
			func(r *resource, mapped orcapi.Service) {
				svc := r.Extra.(*internalService)
				svc.Members = util.RemoveFirst(svc.Members, jobId)
			},
		)
	}
}

type serviceLinkReference struct {
	ingress ResourceId
	port    string
}

var serviceIndex struct {
	Mu        sync.RWMutex
	ByJob     map[string][]string
	ByNetwork map[ResourceId][]string
	Targets   map[ResourceId][]ResourceId
}

func serviceIdsOfJob(jobId string) []string {
	serviceIndex.Mu.RLock()
	result := append([]string(nil), serviceIndex.ByJob[jobId]...)
	serviceIndex.Mu.RUnlock()
	return result
}

func serviceReferencesOfJob(jobId string) []orcapi.JobServiceReference {
	ids := serviceIdsOfJob(jobId)
	if len(ids) == 0 {
		return nil
	}

	result := make([]orcapi.JobServiceReference, 0, len(ids))
	for _, id := range ids {
		result = append(result, orcapi.JobServiceReference{ServiceId: id})
	}
	return result
}

func serviceFindPort(ports []orcapi.ServicePort, name string) (orcapi.ServicePort, bool) {
	for _, port := range ports {
		if port.Name == name {
			return port, true
		}
	}
	return orcapi.ServicePort{}, false
}

func serviceReferencingLinks(serviceId ResourceId) []serviceLinkReference {
	var result []serviceLinkReference

	serviceIndex.Mu.RLock()
	targets := serviceIndex.Targets[serviceId]
	serviceIndex.Mu.RUnlock()

	for _, id := range targets {
		b := resourceGetBucket(ingressType, id)
		r, ok, _ := resourcesReadEx(rpc.ActorSystem, ingressType, orcapi.PermissionRead, b, id, nil)
		if !ok || r == nil || r.MarkedForDeletion {
			continue
		}

		ing := r.Extra.(*internalIngress)
		if ing.TargetService.Present && ing.TargetService.Value == serviceId && ing.TargetPort.Present {
			result = append(result, serviceLinkReference{ingress: id, port: ing.TargetPort.Value})
		}
	}

	return result
}

func serviceReferencedByNetwork(networkId ResourceId) bool {
	serviceIndex.Mu.RLock()
	defer serviceIndex.Mu.RUnlock()
	return len(serviceIndex.ByNetwork[networkId]) > 0
}

type serviceMembershipIndexer struct {
	r *resource
}

func (i *serviceMembershipIndexer) Begin() {
	serviceIndex.Mu.Lock()
}

func (i *serviceMembershipIndexer) Remove() {
	svc, ok := i.r.Extra.(*internalService)
	if !ok {
		return
	}

	serviceId := fmt.Sprint(i.r.Id)
	if members := svc.Members; members != nil {
		for _, jobId := range members {
			serviceIndex.ByJob[jobId] = util.RemoveFirst(serviceIndex.ByJob[jobId], serviceId)
		}
	}

	if network := svc.InternalNetwork; network.Present {
		serviceIndex.ByNetwork[network.Value] = util.RemoveFirst(serviceIndex.ByNetwork[network.Value], serviceId)
	}
}

func (i *serviceMembershipIndexer) Add() {
	svc, ok := i.r.Extra.(*internalService)
	if !ok {
		return
	}

	serviceId := fmt.Sprint(i.r.Id)
	for _, jobId := range svc.Members {
		if !slices.Contains(serviceIndex.ByJob[jobId], serviceId) {
			serviceIndex.ByJob[jobId] = append(serviceIndex.ByJob[jobId], serviceId)
		}
	}

	if network := svc.InternalNetwork; network.Present {
		if !slices.Contains(serviceIndex.ByNetwork[network.Value], serviceId) {
			serviceIndex.ByNetwork[network.Value] = append(serviceIndex.ByNetwork[network.Value], serviceId)
		}
	}
}

func (i *serviceMembershipIndexer) Commit() {
	serviceIndex.Mu.Unlock()
}

type ingressTargetIndexer struct {
	r *resource
}

func (i *ingressTargetIndexer) Begin() {
	serviceIndex.Mu.Lock()
}

func (i *ingressTargetIndexer) Remove() {
	ing, ok := i.r.Extra.(*internalIngress)
	if !ok {
		return
	}

	if target := ing.TargetService; target.Present {
		serviceIndex.Targets[target.Value] = util.RemoveFirst(serviceIndex.Targets[target.Value], i.r.Id)
	}
}

func (i *ingressTargetIndexer) Add() {
	ing, ok := i.r.Extra.(*internalIngress)
	if !ok {
		return
	}

	if target := ing.TargetService; target.Present {
		if !slices.Contains(serviceIndex.Targets[target.Value], i.r.Id) {
			serviceIndex.Targets[target.Value] = append(serviceIndex.Targets[target.Value], i.r.Id)
		}
	}
}

func (i *ingressTargetIndexer) Commit() {
	serviceIndex.Mu.Unlock()
}

func serviceFillIndex() {
	if resourceGlobals.Testing.Enabled {
		serviceIndex.ByJob = map[string][]string{}
		serviceIndex.ByNetwork = map[ResourceId][]string{}
		serviceIndex.Targets = map[ResourceId][]ResourceId{}
		return
	}

	db.NewTx0(func(tx *db.Transaction) {
		serviceIndex.ByJob = map[string][]string{}
		serviceIndex.ByNetwork = map[ResourceId][]string{}
		serviceIndex.Targets = map[ResourceId][]ResourceId{}

		memberRows := db.Select[struct {
			Service int64
			Job     int64
		}](
			tx,
			`
				select service, job
				from app_orchestrator.service_members
		    `,
			db.Params{},
		)

		for _, row := range memberRows {
			serviceId := fmt.Sprint(row.Service)
			jobId := fmt.Sprint(row.Job)
			if !slices.Contains(serviceIndex.ByJob[jobId], serviceId) {
				serviceIndex.ByJob[jobId] = append(serviceIndex.ByJob[jobId], serviceId)
			}
		}

		networkRows := db.Select[struct {
			Resource        int64
			InternalNetwork sql.Null[int64]
		}](
			tx,
			`
				select resource, internal_network
				from app_orchestrator.services
		    `,
			db.Params{},
		)

		for _, row := range networkRows {
			if row.InternalNetwork.Valid {
				serviceId := fmt.Sprint(row.Resource)
				networkId := ResourceId(row.InternalNetwork.V)
				if !slices.Contains(serviceIndex.ByNetwork[networkId], serviceId) {
					serviceIndex.ByNetwork[networkId] = append(serviceIndex.ByNetwork[networkId], serviceId)
				}
			}
		}

		targetRows := db.Select[struct {
			Resource      int64
			TargetService sql.Null[int64]
		}](
			tx,
			`
				select resource, target_service
				from app_orchestrator.ingresses
				where target_service is not null
		    `,
			db.Params{},
		)

		for _, row := range targetRows {
			serviceIndex.Targets[ResourceId(row.TargetService.V)] = append(
				serviceIndex.Targets[ResourceId(row.TargetService.V)],
				ResourceId(row.Resource),
			)
		}
	})
}
