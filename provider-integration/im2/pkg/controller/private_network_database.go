package controller

import (
	"encoding/json"
	"strings"
	"sync"

	db "ucloud.dk/shared/pkg/database"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

var privateNetworks = map[string]*orc.PrivateNetwork{}

var privateNetworkMutex = sync.Mutex{}

func initPrivateNetworkDatabase() {
	if !RunsServerCode() {
		return
	}

	privateNetworkMutex.Lock()
	defer privateNetworkMutex.Unlock()
	privateNetworkFetchAll()
}

func privateNetworkFetchAll() {
	next := ""

	for {
		request := orc.PrivateNetworksControlBrowseRequest{Next: util.OptStringIfNotEmpty(next)}
		page, err := orc.PrivateNetworksControlBrowse.Invoke(request)

		if err != nil {
			break
		}

		for i := 0; i < len(page.Items); i++ {
			network := &page.Items[i]
			privateNetworks[network.Id] = network
		}

		if !page.Next.Present {
			break
		}
		next = page.Next.Value
	}
}

func PrivateNetworkTrackNew(network orc.PrivateNetwork) {
	privateNetworkMutex.Lock()
	privateNetworks[network.Id] = &network
	privateNetworkMutex.Unlock()

	jsonified, _ := json.Marshal(network)

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into tracked_private_networks(resource_id, created_by, project_id, resource)
				values (:resource_id, :created_by, :project_id, :resource)
				on conflict (resource_id) do update set
					resource = excluded.resource,
					created_by = excluded.created_by,
					project_id = excluded.project_id
			`,
			db.Params{
				"resource_id": network.Id,
				"created_by":  network.Owner.CreatedBy,
				"project_id":  network.Owner.Project.Value,
				"resource":    string(jsonified),
			},
		)
	})
}

func PrivateNetworkDelete(target *orc.PrivateNetwork) *util.HttpError {
	if len(target.Status.Members) > 0 {
		return util.UserHttpError("This private network is currently in use by job: %v", strings.Join(target.Status.Members, ", "))
	}

	privateNetworkMutex.Lock()

	delete(privateNetworks, target.Id)

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from tracked_private_networks
				where resource_id = :id
			`,
			db.Params{
				"id": target.Id,
			},
		)
	})

	privateNetworkMutex.Unlock()
	return nil
}

func PrivateNetworkRetrieve(id string) (orc.PrivateNetwork, bool) {
	privateNetworkMutex.Lock()
	res, ok := privateNetworks[id]
	privateNetworkMutex.Unlock()

	if ok {
		return *res, true
	}

	request := orc.PrivateNetworksControlRetrieveRequest{Id: id}
	network, err := orc.PrivateNetworksControlRetrieve.Invoke(request)
	if err != nil {
		return orc.PrivateNetwork{}, false
	}

	PrivateNetworkTrackNew(network)
	return network, true
}

func PrivateNetworkRetrieveUsedCount(owner orc.ResourceOwner) int {
	return db.NewTx[int](func(tx *db.Transaction) int {
		row, _ := db.Get[struct{ Count int }](
			tx,
			`
				select count(*) as count
				from tracked_private_networks
				where
					(
						coalesce(:project, '') = ''
						and coalesce(project_id, '') = ''
						and created_by = :created_by
					)
					or (
						:project != ''
						and project_id = :project
					)
			`,
			db.Params{
				"created_by": owner.CreatedBy,
				"project":    owner.Project.Value,
			},
		)
		return row.Count
	})
}
