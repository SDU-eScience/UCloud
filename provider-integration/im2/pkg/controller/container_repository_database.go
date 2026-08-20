package controller

import (
	"encoding/json"
	"strings"
	"sync"

	db "ucloud.dk/shared/pkg/database"
	orc "ucloud.dk/shared/pkg/orchestrators"
)

var trackedContainerRepositories = struct {
	Mutex  sync.Mutex
	ById   map[string]*orc.ContainerRepository
	ByName map[string]string
}{
	ById:   map[string]*orc.ContainerRepository{},
	ByName: map[string]string{},
}

func InitContainerRepositoryDatabase() {
	if !RunsServerCode() {
		return
	}

	rows := db.NewTx(func(tx *db.Transaction) []struct {
		Resource string
	} {
		return db.Select[struct{ Resource string }](tx, `select resource from tracked_container_repositories`, db.Params{})
	})
	for _, row := range rows {
		var repository orc.ContainerRepository
		if json.Unmarshal([]byte(row.Resource), &repository) == nil {
			containerRepositoryTrackInMemory(repository)
		}
	}
}

func ContainerRepositoryTrack(repository orc.ContainerRepository) {
	containerRepositoryTrackInMemory(repository)
	jsonified, _ := json.Marshal(repository)
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(tx, `
			insert into tracked_container_repositories(resource_id, repository_name, resource)
			values (:resource_id, :repository_name, cast(:resource as jsonb))
			on conflict (resource_id) do update set
				repository_name = excluded.repository_name,
				resource = excluded.resource
		`, db.Params{
			"resource_id":     repository.Id,
			"repository_name": repository.Specification.Name,
			"resource":        string(jsonified),
		})
	})
}

func containerRepositoryTrackInMemory(repository orc.ContainerRepository) {
	trackedContainerRepositories.Mutex.Lock()
	if previous, ok := trackedContainerRepositories.ById[repository.Id]; ok {
		delete(trackedContainerRepositories.ByName, previous.Specification.Name)
	}
	copied := repository
	trackedContainerRepositories.ById[repository.Id] = &copied
	trackedContainerRepositories.ByName[repository.Specification.Name] = repository.Id
	trackedContainerRepositories.Mutex.Unlock()
}

func ContainerRepositoryRemove(id string) {
	trackedContainerRepositories.Mutex.Lock()
	if previous, ok := trackedContainerRepositories.ById[id]; ok {
		delete(trackedContainerRepositories.ByName, previous.Specification.Name)
	}
	delete(trackedContainerRepositories.ById, id)
	trackedContainerRepositories.Mutex.Unlock()

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(tx, `delete from tracked_container_repositories where resource_id = :id`, db.Params{"id": id})
	})
}

func ContainerRepositoryRetrieveByRepository(repositoryName string) (orc.ContainerRepository, bool) {
	root, _, _ := strings.Cut(strings.Trim(repositoryName, "/"), "/")
	if root == "" {
		return orc.ContainerRepository{}, false
	}

	trackedContainerRepositories.Mutex.Lock()
	id, ok := trackedContainerRepositories.ByName[root]
	trackedContainerRepositories.Mutex.Unlock()
	if !ok {
		return orc.ContainerRepository{}, false
	}

	request := orc.ContainerRepositoriesControlRetrieveRequest{Id: id}
	request.IncludeOthers = true
	repository, err := orc.ContainerRepositoriesControlRetrieve.Invoke(request)
	if err != nil {
		ContainerRepositoryRemove(id)
		return orc.ContainerRepository{}, false
	}

	ContainerRepositoryTrack(repository)
	return repository, true
}

func ContainerRepositoryEnumerateKnown() []orc.ContainerRepository {
	trackedContainerRepositories.Mutex.Lock()
	result := make([]orc.ContainerRepository, 0, len(trackedContainerRepositories.ById))
	for _, repository := range trackedContainerRepositories.ById {
		result = append(result, *repository)
	}
	trackedContainerRepositories.Mutex.Unlock()
	return result
}
