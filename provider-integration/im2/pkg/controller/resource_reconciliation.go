package controller

import (
	"time"

	cfg "ucloud.dk/pkg/config"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

const resourceMissingConfirmationThreshold = 3

type trackedResourceReference struct {
	Type     string
	Id       string
	Provider string
}

var trackedResourceMissingConfirmations = make(map[trackedResourceReference]int)

func initTrackedResourceReconciliation() {
	go func() {
		for util.IsAlive {
			reconcileTrackedResources()
			time.Sleep(time.Minute)
		}
	}()
}

func reconcileTrackedResources() {
	tracked := db.NewTx(func(tx *db.Transaction) []trackedResourceReference {
		return db.Select[trackedResourceReference](
			tx,
			`
				select 'file_collection' as type, drive_id as id,
					resource->'specification'->'product'->>'provider' as provider from tracked_drives
				union all
				select 'network_ip' as type, resource_id as id,
					resource->'specification'->'product'->>'provider' as provider from tracked_ips
				union all
				select 'ingress' as type, resource_id as id,
					resource->'specification'->'product'->>'provider' as provider from tracked_ingresses
				union all
				select 'license' as type, resource_id as id,
					resource->'specification'->'product'->>'provider' as provider from tracked_licenses
				union all
				select 'private_network' as type, resource_id as id,
					resource->'specification'->'product'->>'provider' as provider from tracked_private_networks
				union all
				select 'job' as type, job_id as id,
					resource->'specification'->'product'->>'provider' as provider from tracked_jobs
			`,
			db.Params{},
		)
	})

	localTracked := tracked[:0]
	currentlyTracked := make(map[trackedResourceReference]util.Empty, len(tracked))
	for _, reference := range tracked {
		if reference.Provider != cfg.Provider.Id {
			continue
		}
		localTracked = append(localTracked, reference)
		currentlyTracked[reference] = util.Empty{}
	}
	tracked = localTracked
	for reference := range trackedResourceMissingConfirmations {
		if _, ok := currentlyTracked[reference]; !ok {
			delete(trackedResourceMissingConfirmations, reference)
		}
	}

	for len(tracked) > 0 {
		count := min(500, len(tracked))
		chunk := tracked[:count]
		tracked = tracked[count:]

		checks := make([]orc.ResourceExistenceCheck, 0, len(chunk))
		for _, reference := range chunk {
			checks = append(checks, orc.ResourceExistenceCheck{Type: reference.Type, Id: reference.Id})
		}

		response, err := orc.ResourcesControlCheckExistence.Invoke(fnd.BulkRequest[orc.ResourceExistenceCheck]{Items: checks})
		if err != nil || len(response.Responses) != len(chunk) {
			for _, reference := range chunk {
				delete(trackedResourceMissingConfirmations, reference)
			}
			continue
		}

		for i, exists := range response.Responses {
			reference := chunk[i]
			if exists {
				delete(trackedResourceMissingConfirmations, reference)
				continue
			}

			trackedResourceMissingConfirmations[reference]++
			if trackedResourceMissingConfirmations[reference] < resourceMissingConfirmationThreshold {
				continue
			}

			delete(trackedResourceMissingConfirmations, reference)
			purgeTrackedResource(reference)
			log.Warn(
				"Purged %v %v after %v confirmations that it does not exist in Core for this provider",
				reference.Type,
				reference.Id,
				resourceMissingConfirmationThreshold,
			)
		}
	}
}

func purgeTrackedResource(reference trackedResourceReference) {
	switch reference.Type {
	case "job":
		jobPurgeTracked(reference.Id)

	case "file_collection":
		DriveRemoveTracked(reference.Id)
		driveSearchIndexCache.Remove(reference.Id)

	case "network_ip":
		publicIps.Mu.Lock()
		if ip := publicIps.Ips[reference.Id]; ip != nil && ip.Status.IpAddress.Present {
			delete(publicIps.ExternalAddressesInUse, ip.Status.IpAddress.Value)
		}
		for address, resourceId := range publicIps.ExternalAddressesInUse {
			if resourceId == reference.Id {
				delete(publicIps.ExternalAddressesInUse, address)
			}
		}
		delete(publicIps.Ips, reference.Id)
		publicIps.Mu.Unlock()
		deleteTrackedResourceRow("tracked_ips", reference.Id)

	case "ingress":
		ingressesMutex.Lock()
		delete(ingresses, reference.Id)
		ingressesMutex.Unlock()
		deleteTrackedResourceRow("tracked_ingresses", reference.Id)

	case "license":
		licenseMutex.Lock()
		delete(licenses, reference.Id)
		licenseMutex.Unlock()
		deleteTrackedResourceRow("tracked_licenses", reference.Id)

	case "private_network":
		privateNetworkMutex.Lock()
		delete(privateNetworks, reference.Id)
		privateNetworkMutex.Unlock()
		deleteTrackedResourceRow("tracked_private_networks", reference.Id)
	}
}

func deleteTrackedResourceRow(table string, id string) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			"delete from "+table+" where resource_id = :id",
			db.Params{"id": id},
		)
	})
}
