package controller

import (
	"encoding/json"
	"strings"
	"sync"

	db "ucloud.dk/shared/pkg/database"
	orc "ucloud.dk/shared/pkg/orchestrators"
)

var trackedContainerRegistries = struct {
	Mutex  sync.Mutex
	ById   map[string]*orc.ContainerRegistry
	ByName map[string]string
}{
	ById:   map[string]*orc.ContainerRegistry{},
	ByName: map[string]string{},
}

func InitContainerRegistryDatabase() {
	if !RunsServerCode() {
		return
	}

	rows := db.NewTx(func(tx *db.Transaction) []struct {
		Resource string
	} {
		return db.Select[struct{ Resource string }](tx, `select resource from tracked_container_registries`, db.Params{})
	})
	for _, row := range rows {
		var registry orc.ContainerRegistry
		if json.Unmarshal([]byte(row.Resource), &registry) == nil {
			containerRegistryTrackInMemory(registry)
		}
	}
}

func ContainerRegistryTrack(registry orc.ContainerRegistry) {
	containerRegistryTrackInMemory(registry)
	jsonified, _ := json.Marshal(registry)
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(tx, `
			insert into tracked_container_registries(resource_id, repository_name, resource)
			values (:resource_id, :repository_name, cast(:resource as jsonb))
			on conflict (resource_id) do update set
				repository_name = excluded.repository_name,
				resource = excluded.resource
		`, db.Params{
			"resource_id":     registry.Id,
			"repository_name": registry.Specification.Name,
			"resource":        string(jsonified),
		})
	})
}

func containerRegistryTrackInMemory(registry orc.ContainerRegistry) {
	trackedContainerRegistries.Mutex.Lock()
	if previous, ok := trackedContainerRegistries.ById[registry.Id]; ok {
		delete(trackedContainerRegistries.ByName, previous.Specification.Name)
	}
	copy := registry
	trackedContainerRegistries.ById[registry.Id] = &copy
	trackedContainerRegistries.ByName[registry.Specification.Name] = registry.Id
	trackedContainerRegistries.Mutex.Unlock()
}

func ContainerRegistryRemove(id string) {
	trackedContainerRegistries.Mutex.Lock()
	if previous, ok := trackedContainerRegistries.ById[id]; ok {
		delete(trackedContainerRegistries.ByName, previous.Specification.Name)
	}
	delete(trackedContainerRegistries.ById, id)
	trackedContainerRegistries.Mutex.Unlock()

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(tx, `delete from tracked_container_registries where resource_id = :id`, db.Params{"id": id})
	})
}

func ContainerRegistryRetrieveByRepository(repository string) (orc.ContainerRegistry, bool) {
	root, _, _ := strings.Cut(strings.Trim(repository, "/"), "/")
	if root == "" {
		return orc.ContainerRegistry{}, false
	}

	trackedContainerRegistries.Mutex.Lock()
	id, ok := trackedContainerRegistries.ByName[root]
	trackedContainerRegistries.Mutex.Unlock()
	if !ok {
		return orc.ContainerRegistry{}, false
	}

	request := orc.ContainerRegistriesControlRetrieveRequest{Id: id}
	request.IncludeOthers = true
	registry, err := orc.ContainerRegistriesControlRetrieve.Invoke(request)
	if err != nil {
		ContainerRegistryRemove(id)
		return orc.ContainerRegistry{}, false
	}

	ContainerRegistryTrack(registry)
	return registry, true
}
