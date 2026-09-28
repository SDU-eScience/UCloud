package controller

import (
	"encoding/json"
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
		copied := rows[i]
		trackedServices[copied.Id] = &copied
	}
}

func ServiceTrack(service orc.Service) {
	trackedServicesMutex.Lock()
	copied := service
	trackedServices[service.Id] = &copied
	trackedServicesMutex.Unlock()

	jsonified, _ := json.Marshal(service)

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

func ServiceDeleteTracked(service orc.Service) {
	trackedServicesMutex.Lock()
	delete(trackedServices, service.Id)
	trackedServicesMutex.Unlock()

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from tracked_services
				where resource_id = :id
		    `,
			db.Params{
				"id": service.Id,
			},
		)
	})
}

func ServiceRetrieveTracked(id string) (orc.Service, bool) {
	trackedServicesMutex.Lock()
	res := trackedServices[id]
	trackedServicesMutex.Unlock()

	if res == nil {
		return orc.Service{}, false
	}
	return *res, true
}

func ServicesRetrieveAllTracked() []orc.Service {
	trackedServicesMutex.Lock()
	result := make([]orc.Service, 0, len(trackedServices))
	for _, svc := range trackedServices {
		result = append(result, *svc)
	}
	trackedServicesMutex.Unlock()
	return result
}

func ServiceReportUpdate(service orc.Service, update orc.ServiceUpdate) {
	trackedServicesMutex.Lock()
	current := trackedServices[service.Id]
	if current != nil {
		current.Status.ProvisioningState = service.Status.ProvisioningState
		current.Status.Message = service.Status.Message
		current.Status.InternalEndpoint = service.Status.InternalEndpoint
		current.Status.Backends = service.Status.Backends
		current.Updates = append(current.Updates, update)
		if len(current.Updates) > 50 {
			current.Updates = current.Updates[len(current.Updates)-50:]
		}
		trackedServices[service.Id] = current
		trackedServicesMutex.Unlock()

		service = *current
	} else {
		service.Updates = append(service.Updates, update)
		if len(service.Updates) > 50 {
			service.Updates = service.Updates[len(service.Updates)-50:]
		}
		trackedServices[service.Id] = &service
		trackedServicesMutex.Unlock()
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
	}

	ServiceTrack(service)
}
