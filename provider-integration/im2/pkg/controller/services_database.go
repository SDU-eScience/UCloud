package controller

import (
	"encoding/json"
	"slices"
	"sync"

	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
)

var trackedServices = map[string]*orc.Service{}
var trackedServicesMutex = sync.Mutex{}

func initServicesDatabase() {
	if !RunsServerCode() {
		return
	}

	trackedServicesMutex.Lock()
	defer trackedServicesMutex.Unlock()
	servicesFetchAll()
}

func servicesFetchAll() {
	rows := db.NewTx(func(tx *db.Transaction) []orc.Service {
		var result []orc.Service
		rows := db.Select[struct{ Resource string }](
			tx,
			`
				select resource from tracked_services
		    `,
			db.Params{},
		)

		for _, row := range rows {
			var svc orc.Service
			err := json.Unmarshal([]byte(row.Resource), &svc)
			if err == nil {
				result = append(result, svc)
			}
		}
		return result
	})

	for i := range rows {
		copied := cloneService(rows[i])
		trackedServices[copied.Id] = &copied
	}
}

func ServiceTrack(service orc.Service) {
	trackedServicesMutex.Lock()
	defer trackedServicesMutex.Unlock()

	copied := cloneService(service)
	trackedServices[service.Id] = &copied
	servicesPersistLocked(&copied)
}

func ServiceMutateTracked(id string, mutate func(service *orc.Service) bool) (orc.Service, bool) {
	trackedServicesMutex.Lock()
	defer trackedServicesMutex.Unlock()

	service, ok := trackedServices[id]
	if !ok {
		return orc.Service{}, false
	}

	if mutate(service) {
		servicesPersistLocked(service)
	}

	return cloneService(*service), true
}

func ServiceDeleteTracked(service orc.Service) {
	trackedServicesMutex.Lock()
	defer trackedServicesMutex.Unlock()

	delete(trackedServices, service.Id)
	servicesDeleteLocked(service.Id)
}

func ServiceRetrieveTracked(id string) (orc.Service, bool) {
	trackedServicesMutex.Lock()
	defer trackedServicesMutex.Unlock()

	service, ok := trackedServices[id]
	if !ok {
		return orc.Service{}, false
	}
	return cloneService(*service), true
}

func ServicesRetrieveAllTracked() []orc.Service {
	trackedServicesMutex.Lock()
	defer trackedServicesMutex.Unlock()

	result := make([]orc.Service, 0, len(trackedServices))
	for _, service := range trackedServices {
		result = append(result, cloneService(*service))
	}
	return result
}

func ServiceReportUpdate(service orc.Service, update orc.ServiceUpdate) bool {
	_, ok := ServiceMutateTracked(service.Id, func(current *orc.Service) bool {
		current.Status.ProvisioningState = service.Status.ProvisioningState
		current.Status.Message = service.Status.Message
		current.Status.InternalEndpoint = service.Status.InternalEndpoint
		current.Status.Backends = cloneServiceBackends(service.Status.Backends)
		current.Updates = append(current.Updates, update)
		if len(current.Updates) > 50 {
			current.Updates = current.Updates[len(current.Updates)-50:]
		}
		return true
	})
	if !ok {
		log.Warn("Ignoring report for service %s because it is no longer tracked", service.Id)
		return false
	}

	_, err := orc.ServicesControlAddUpdate.Invoke(fnd.BulkRequest[orc.ResourceUpdateAndId[orc.ServiceUpdate]]{
		Items: []orc.ResourceUpdateAndId[orc.ServiceUpdate]{
			{
				Id:     service.Id,
				Update: update,
			},
		},
	})
	if err != nil {
		log.Warn("Failed to report the update of service %s: %s", service.Id, err)
		return false
	}

	return true
}

func servicesPersistLocked(service *orc.Service) {
	jsonified, err := json.Marshal(service)
	if err != nil {
		log.Warn("Failed to encode tracked service %s: %s", service.Id, err)
		return
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into tracked_services(resource_id, created_by, project_id, resource)
				values (:resource_id, :created_by, :project_id, :resource)
				on conflict (resource_id) do update set
					resource = excluded.resource,
					created_by = excluded.created_by,
					project_id = excluded.project_id
			`,
			db.Params{
				"resource_id": service.Id,
				"created_by":  service.Owner.CreatedBy,
				"project_id":  service.Owner.Project.Value,
				"resource":    string(jsonified),
			},
		)
	})
}

func servicesDeleteLocked(id string) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from tracked_services
				where resource_id = :id
		    `,
			db.Params{
				"id": id,
			},
		)
	})
}

func cloneService(service orc.Service) orc.Service {
	result := service
	result.Specification.Ports = slices.Clone(service.Specification.Ports)
	result.Status.Members = slices.Clone(service.Status.Members)
	result.Status.Backends = cloneServiceBackends(service.Status.Backends)
	result.Updates = slices.Clone(service.Updates)

	if service.Specification.Labels != nil {
		result.Specification.Labels = make(map[string]string, len(service.Specification.Labels))
		for key, value := range service.Specification.Labels {
			result.Specification.Labels[key] = value
		}
	}

	return result
}

func cloneServiceBackends(backends []orc.ServiceBackendStatus) []orc.ServiceBackendStatus {
	result := slices.Clone(backends)
	for i := range result {
		result[i].Ports = slices.Clone(result[i].Ports)
	}
	return result
}
