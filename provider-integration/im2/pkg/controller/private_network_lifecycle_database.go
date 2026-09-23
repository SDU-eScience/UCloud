package controller

import (
	"crypto/rand"
	"encoding/json"
	"net/http"
	"net/netip"
	"strings"
	"time"

	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

func privateNetworkBusinessRollback(tx *db.Transaction, httpError *util.HttpError) *util.HttpError {
	db.RequestRollback(tx)
	return httpError
}

func PrivateNetworkCreateAllocate(network *orc.PrivateNetwork) (bool, *util.HttpError) {
	settings := privateNetworkCurrentSettings()
	if !settings.Enabled {
		return false, util.UserHttpError("Private networks are not enabled on this provider")
	}

	if network == nil {
		return false, util.ServerHttpError("Failed to create private network: network is nil")
	}

	workspace := PrivateNetworkWorkspaceFromOwner(network.Owner)

	customCidr := ""
	if network.Specification.Cidr.Present {
		customCidr = network.Specification.Cidr.Value
		if parsed, ok := orc.PrivateNetworkParseCidr(customCidr); ok {
			customCidr = parsed.String()
		}
	}

	subdomainConflict := false
	err := db.NewTx(func(tx *db.Transaction) *util.HttpError {
		privateNetworkLockReconcile(tx, network.Id)
		if !tx.Ok {
			return nil
		}

		if existingErr := privateNetworkCreateExisting(tx, network, workspace, customCidr); existingErr != nil {
			return existingErr
		}

		_, found := privateNetworkSelectNetwork(tx, network.Id)
		if !tx.Ok {
			return nil
		}

		if found {
			return nil
		}

		privateNetworkLockPools(tx)
		privateNetworkLockName(tx)
		if !tx.Ok {
			return nil
		}

		if existingErr := privateNetworkCreateExisting(tx, network, workspace, customCidr); existingErr != nil {
			return existingErr
		}

		_, found = privateNetworkSelectNetwork(tx, network.Id)
		if !tx.Ok {
			return nil
		}

		if found {
			return nil
		}

		subdomainCount, _ := db.Get[struct{ Count int }](
			tx,
			`
				select count(*) as count
				from
					tracked_private_networks
				where
					resource_id != :resource_id
					and lower(coalesce(nullif(resource->'status'->>'subdomain', ''), resource->'specification'->>'subdomain')) = lower(:subdomain)
			`,
			db.Params{
				"resource_id": network.Id,
				"subdomain":   network.Status.Subdomain,
			},
		)
		if !tx.Ok {
			return nil
		}

		if subdomainCount.Count > 0 {
			subdomainConflict = true
			return privateNetworkBusinessRollback(tx, nil)
		}

		var selected string
		var allocateErr *util.HttpError
		if customCidr != "" {
			selected, allocateErr = privateNetworkAllocateCustomCidr(tx, settings, customCidr)
		} else {
			selected, allocateErr = privateNetworkAllocateAutoCidr(tx, settings)
		}

		if allocateErr != nil {
			return allocateErr
		}

		jsonified, _ := json.Marshal(network)
		db.Exec(
			tx,
			`
				insert into tracked_private_networks(resource_id, created_by, project_id, resource, cidr_block, state, workspace_id)
				values (:resource_id, :created_by, :project_id, :resource, :cidr_block, 'provisioning', :workspace_id)
			`,
			db.Params{
				"resource_id":  network.Id,
				"created_by":   network.Owner.CreatedBy,
				"project_id":   network.Owner.Project.Value,
				"resource":     string(jsonified),
				"cidr_block":   selected,
				"workspace_id": workspace.String(),
			},
		)
		if !tx.Ok {
			return nil
		}

		return nil
	})
	if err != nil {
		return false, err
	}

	return subdomainConflict, nil
}

func privateNetworkCreateExisting(
	tx *db.Transaction,
	network *orc.PrivateNetwork,
	workspace PrivateNetworkWorkspace,
	customCidr string,
) *util.HttpError {
	if !tx.Ok {
		return nil
	}

	row, found := privateNetworkSelectNetwork(tx, network.Id)
	if !tx.Ok {
		return nil
	}

	if !found {
		return nil
	}

	if row.State == PrivateNetworkStateDeleting {
		return privateNetworkBusinessRollback(
			tx,
			util.HttpErr(http.StatusConflict, "a private network with this identifier is being deleted"),
		)
	}

	if row.WorkspaceId != workspace.String() {
		return privateNetworkBusinessRollback(
			tx,
			util.HttpErr(http.StatusConflict, "a network with this identifier already exists in another workspace"),
		)
	}

	var existing orc.PrivateNetwork
	parsedExisting := privateNetworkUnmarshalResource(row.Resource, &existing)

	if row.CidrBlock.Valid && row.CidrBlock.V != "" && customCidr != "" && row.CidrBlock.V != customCidr {
		return privateNetworkBusinessRollback(
			tx,
			util.HttpErr(http.StatusConflict, "a network with this identifier already exists with a different CIDR"),
		)
	}

	if parsedExisting {
		merged := *network
		merged.Owner = existing.Owner
		merged.Status.Subdomain = existing.Status.Subdomain
		merged.Specification.Cidr = existing.Specification.Cidr
		merged.Status.CidrBlock = util.SqlNullToOpt(row.CidrBlock)

		jsonified, _ := json.Marshal(merged)
		db.Exec(
			tx,
			`
				update tracked_private_networks
				set
					resource = :resource
				where
					resource_id = :resource_id
			`,
			db.Params{
				"resource_id": network.Id,
				"resource":    string(jsonified),
			},
		)
		return nil
	}

	jsonified, _ := json.Marshal(network)
	db.Exec(
		tx,
		`
			update tracked_private_networks
			set
				resource = :resource
			where
				resource_id = :resource_id
		`,
		db.Params{
			"resource_id": network.Id,
			"resource":    string(jsonified),
		},
	)
	return nil
}

func privateNetworkAllocateCustomCidr(
	tx *db.Transaction,
	settings privateNetworkParsedSettings,
	cidr string,
) (string, *util.HttpError) {
	parsed, ok := orc.PrivateNetworkParseCidr(cidr)
	if !ok {
		return "", privateNetworkBusinessRollback(
			tx,
			util.UserHttpError("%s is not a valid IPv4 CIDR", cidr),
		)
	}

	if parsed.Bits() < 16 || parsed.Bits() > 24 {
		return "", privateNetworkBusinessRollback(
			tx,
			util.UserHttpError("%s must have a prefix length between 16 and 24", cidr),
		)
	}

	for i := range settings.Forbidden {
		if orc.PrivateNetworkCidrsOverlap(parsed, settings.Forbidden[i]) {
			return "", privateNetworkBusinessRollback(
				tx,
				util.UserHttpError("%s overlaps a blocked range", cidr),
			)
		}
	}

	for i := range settings.Pools {
		if orc.PrivateNetworkCidrsOverlap(parsed, settings.Pools[i]) &&
			parsed.Bits() >= settings.Pools[i].Bits() {
			return parsed.String(), nil
		}
	}

	return "", privateNetworkBusinessRollback(
		tx,
		util.UserHttpError("%s is not inside any configured address pool", cidr),
	)
}

func privateNetworkAllocateAutoCidr(
	tx *db.Transaction,
	settings privateNetworkParsedSettings,
) (string, *util.HttpError) {
	rows := db.Select[struct{ CidrBlock string }](
		tx,
		`
			select
				cidr_block::text as cidr_block
			from
				tracked_private_networks
			where
				cidr_block is not null
		`,
		db.Params{},
	)
	if !tx.Ok {
		return "", nil
	}

	var used []netip.Prefix
	for _, row := range rows {
		if parsed, ok := orc.PrivateNetworkParseCidr(row.CidrBlock); ok {
			used = append(used, parsed)
		}
	}

	blockSize := uint64(1) << (32 - uint64(settings.DefaultPrefixLen))
	for _, pool := range settings.Pools {
		poolFirst, poolLast := privateNetworkCidrRange(pool)
		alignedFirst := privateNetworkAlignUp(poolFirst, blockSize)
		for blockStart := alignedFirst; blockStart+blockSize-1 <= poolLast; blockStart += blockSize {
			candidate := netip.PrefixFrom(
				privateNetworkUint32ToAddr(uint32(blockStart)),
				settings.DefaultPrefixLen,
			)

			if privateNetworkPrefixOverlapsAny(candidate, settings.Forbidden) {
				continue
			}

			if !privateNetworkPrefixOverlapsAny(candidate, used) {
				return candidate.String(), nil
			}
		}
	}

	return "", privateNetworkBusinessRollback(
		tx,
		util.UserHttpError("No address space is available for a new private network"),
	)
}

func PrivateNetworkMarkReady(networkId string, expectedCidrBlock string) *util.HttpError {
	err := db.NewTx(func(tx *db.Transaction) *util.HttpError {
		row, found := privateNetworkLockNetwork(tx, networkId)
		if !tx.Ok {
			return nil
		}

		if !found {
			db.RequestRollback(tx)
			return util.UserHttpError("Private network %s no longer exists", networkId)
		}

		if row.State == PrivateNetworkStateDeleting {
			db.RequestRollback(tx)
			return util.UserHttpError("Private network %s is being deleted", networkId)
		}

		if expectedCidrBlock != "" && (!row.CidrBlock.Valid || row.CidrBlock.V != expectedCidrBlock) {
			db.RequestRollback(tx)
			return util.ServerHttpError(
				"Private network %s has CIDR %s but the reconciler expected %s",
				networkId,
				row.CidrBlock.V,
				expectedCidrBlock,
			)
		}

		if row.State == PrivateNetworkStateReady {
			return nil
		}

		db.Exec(
			tx,
			`
				update tracked_private_networks
				set
					state = 'ready'
				where
					resource_id = :resource_id
			`,
			db.Params{"resource_id": networkId},
		)
		if !tx.Ok {
			return nil
		}

		return nil
	})
	if err != nil {
		return err
	}

	return nil
}

func privateNetworkLockReconcile(tx *db.Transaction, networkId string) {
	db.Exec(
		tx,
		`
			select pg_advisory_xact_lock(:namespace, hashtext(:network_id))
		`,
		db.Params{
			"namespace":  privateNetworkReconcileAdvisoryNamespace,
			"network_id": networkId,
		},
	)
}

func PrivateNetworkReconcileSerialized(networkId string, operation func()) {
	db.NewTx0(func(tx *db.Transaction) {
		tx.NoDevResetThisIsNotAHackIPromise = true
		privateNetworkLockReconcile(tx, networkId)
		if !tx.Ok {
			return
		}
		operation()
	})
}

func PrivateNetworkDeleteRequest(target *orc.PrivateNetwork) *util.HttpError {
	if target == nil {
		return util.ServerHttpError("Failed to delete private network: network is nil")
	}

	members := target.Status.Members
	if fresh, ok := privateNetworkRefreshMetadata(target.Id); ok {
		fresh.Status.Members = members
		target = &fresh
	}

	if len(target.Status.Members) > 0 {
		return util.UserHttpError(
			"This private network is currently in use by job: %v",
			strings.Join(target.Status.Members, ", "),
		)
	}

	ownerWorkspace := PrivateNetworkWorkspaceFromOwner(target.Owner)

	err := db.NewTx(func(tx *db.Transaction) *util.HttpError {
		privateNetworkLockReconcile(tx, target.Id)
		if !tx.Ok {
			return nil
		}

		row, found := privateNetworkLockNetwork(tx, target.Id)
		if !tx.Ok {
			return nil
		}

		if !found {
			db.RequestRollback(tx)
			return util.UserHttpError("This private network no longer exists")
		}

		if row.WorkspaceId != ownerWorkspace.String() {
			db.RequestRollback(tx)
			return util.ServerHttpError(
				"Private network %s belongs to a different workspace than the delete request",
				target.Id,
			)
		}

		leaseCount, _ := db.Get[struct{ Count int }](
			tx,
			`
				select count(*) as count
				from
					private_network_ip_leases
				where
					network_id = :network_id
			`,
			db.Params{"network_id": target.Id},
		)
		if !tx.Ok {
			return nil
		}

		if leaseCount.Count > 0 {
			return privateNetworkBusinessRollback(
				tx,
				util.UserHttpError("This private network is currently in use by one or more jobs"),
			)
		}

		reservationCount, _ := db.Get[struct{ Count int }](
			tx,
			`
				select count(*) as count
				from
					private_network_ip_reservations
				where
					network_id = :network_id
			`,
			db.Params{"network_id": target.Id},
		)
		if !tx.Ok {
			return nil
		}

		if reservationCount.Count > 0 {
			return privateNetworkBusinessRollback(
				tx,
				util.UserHttpError("Delete all reserved IP addresses of this private network first"),
			)
		}

		db.Exec(
			tx,
			`
				update tracked_private_networks
				set
					state = 'deleting'
				where
					resource_id = :resource_id
			`,
			db.Params{"resource_id": target.Id},
		)
		if !tx.Ok {
			return nil
		}

		return nil
	})
	if err != nil {
		return err
	}

	return nil
}

func PrivateNetworkFinishDelete(networkId string, ownerWorkspace PrivateNetworkWorkspace) *util.HttpError {
	finished := false
	err := db.NewTx(func(tx *db.Transaction) *util.HttpError {
		row, found := privateNetworkLockNetwork(tx, networkId)
		if !tx.Ok {
			return nil
		}

		if !found {
			return nil
		}

		if row.State != PrivateNetworkStateDeleting {
			db.RequestRollback(tx)
			return util.ServerHttpError("Private network %s is not in the deleting state", networkId)
		}

		if row.WorkspaceId != ownerWorkspace.String() {
			db.RequestRollback(tx)
			return util.ServerHttpError(
				"Private network %s belongs to a different workspace than the finish-delete request",
				networkId,
			)
		}

		leaseCount, _ := db.Get[struct{ Count int }](
			tx,
			`
				select count(*) as count
				from
					private_network_ip_leases
				where
					network_id = :network_id
			`,
			db.Params{"network_id": networkId},
		)
		if !tx.Ok {
			return nil
		}

		if leaseCount.Count > 0 {
			return privateNetworkBusinessRollback(
				tx,
				util.ServerHttpError("Private network %s still has active leases", networkId),
			)
		}

		reservationCount, _ := db.Get[struct{ Count int }](
			tx,
			`
				select count(*) as count
				from
					private_network_ip_reservations
				where
					network_id = :network_id
			`,
			db.Params{"network_id": networkId},
		)
		if !tx.Ok {
			return nil
		}

		if reservationCount.Count > 0 {
			return privateNetworkBusinessRollback(
				tx,
				util.ServerHttpError("Private network %s still has reservations", networkId),
			)
		}

		db.Exec(
			tx,
			`
				delete from tracked_private_networks
				where
					resource_id = :resource_id
			`,
			db.Params{"resource_id": networkId},
		)
		if !tx.Ok {
			return nil
		}

		finished = true
		return nil
	})
	if err != nil {
		return err
	}

	if finished {
		privateNetworkCacheRemoveAfterDelete(networkId)
	}

	return nil
}

func privateNetworkCacheRemoveAfterDelete(networkId string) {
	privateNetworkMutex.Lock()
	delete(privateNetworks, networkId)
	privateNetworkMutex.Unlock()
}

func PrivateNetworkUpdateCoreCidrBlock(networkId string, cidrBlock string) *util.HttpError {
	update := orc.PrivateNetworkUpdate{
		CidrBlock: util.OptValue(cidrBlock),
		Timestamp: fnd.Timestamp(time.Now()),
	}

	_, err := orc.PrivateNetworksControlAddUpdate.Invoke(
		fnd.BulkRequestOf(orc.ResourceUpdateAndId[orc.PrivateNetworkUpdate]{
			Id:     networkId,
			Update: update,
		}),
	)

	if err != nil {
		return err
	}

	privateNetworkMutex.Lock()
	if network, ok := privateNetworks[networkId]; ok {
		copied := *network
		copied.Status.CidrBlock = util.OptValue(cidrBlock)
		privateNetworks[networkId] = &copied
	}
	privateNetworkMutex.Unlock()

	return nil
}

func privateNetworkGenerateMacAddress() (string, bool) {
	buf := make([]byte, 6)
	_, err := rand.Read(buf)
	if err != nil {
		return "", false
	}

	buf[0] = (buf[0] | 0x02) & 0xFE
	var hardware [6]byte
	copy(hardware[:], buf)
	return privateNetworkFormatMac(hardware), true
}

func privateNetworkFormatMac(value [6]byte) string {
	var builder strings.Builder
	for i := 0; i < 6; i++ {
		if i > 0 {
			builder.WriteByte(':')
		}
		builder.WriteString(privateNetworkHexByte(value[i]))
	}
	return builder.String()
}

func privateNetworkHexByte(value byte) string {
	const hexDigits = "0123456789abcdef"
	return string([]byte{hexDigits[value>>4], hexDigits[value&0x0F]})
}
