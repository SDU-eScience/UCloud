package controller

import (
	"database/sql"
	"encoding/json"
	"maps"
	"net/netip"
	"slices"
	"time"

	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

type PrivateNetworkReservationRow struct {
	NetworkId     string
	Ip            string
	ReservationId string
	WorkspaceId   string
}

type PrivateNetworkLeaseRow struct {
	NetworkId     string
	Ip            string
	MacAddress    util.Option[string]
	JobId         string
	Rank          int
	Pinned        bool
	ReservationId util.Option[string]
	State         string
	WorkspaceId   string
}

type PrivateNetworkJobLeases struct {
	NetworkId string
	Subdomain string
	CidrBlock util.Option[string]
	Leases    []PrivateNetworkLeaseRow
}

func PrivateNetworkJobValues(job *orc.Job) ([]orc.AppParameterValue, *util.HttpError) {
	var result []orc.AppParameterValue
	seen := map[string]struct{}{}

	appendValue := func(value orc.AppParameterValue) *util.HttpError {
		if value.Type != orc.AppParameterValueTypePrivateNetwork {
			return nil
		}

		if _, duplicate := seen[value.Id]; duplicate {
			return util.UserHttpError("The private network %s is attached more than once", value.Id)
		}
		seen[value.Id] = struct{}{}

		result = append(result, value)
		return nil
	}

	for _, name := range slices.Sorted(maps.Keys(job.Specification.Parameters)) {
		if err := appendValue(job.Specification.Parameters[name]); err != nil {
			return nil, err
		}
	}

	for _, value := range job.Specification.Resources {
		if err := appendValue(value); err != nil {
			return nil, err
		}
	}

	return result, nil
}

func PrivateNetworkIpTrackMetadata(ip orc.PrivateNetworkIp) {
	if !privateNetworkCurrentSettings().Enabled {
		return
	}

	jsonified, _ := json.Marshal(ip)

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update private_network_ip_reservations
				set
					resource = :resource
				where
					reservation_id = :reservation_id
			`,
			db.Params{
				"reservation_id": ip.Id,
				"resource":       string(jsonified),
			},
		)
	})
}

func PrivateNetworkReservationCreate(reservation *orc.PrivateNetworkIp) *util.HttpError {
	settings := privateNetworkCurrentSettings()
	if !settings.Enabled {
		return util.UserHttpError("Private networks are not enabled on this provider")
	}

	if reservation == nil {
		return util.ServerHttpError("Failed to create reserved IP: reservation is nil")
	}

	workspace := PrivateNetworkWorkspaceFromOwner(reservation.Owner)

	pinnedIp := ""
	if reservation.Specification.IpAddress.Present {
		pinnedIp = reservation.Specification.IpAddress.Value
	}

	allocatedAddress := ""
	notifyRequired := false
	err := db.NewTx(func(tx *db.Transaction) *util.HttpError {
		allocatedAddress = ""
		notifyRequired = false

		if scanErr := privateNetworkRequireInitialScan(); scanErr != nil {
			return scanErr
		}

		network, networkOk := privateNetworkLockNetwork(tx, reservation.Specification.Network)
		if !tx.Ok {
			return nil
		}

		if !networkOk {
			db.RequestRollback(tx)
			return util.UserHttpError("The private network of this reservation no longer exists")
		}

		if network.State != PrivateNetworkStateReady {
			return privateNetworkBusinessRollback(
				tx,
				util.UserHttpError("The private network is not ready for reservations yet"),
			)
		}

		if network.WorkspaceId != workspace.String() {
			return privateNetworkBusinessRollback(
				tx,
				util.UserHttpError("The reservation and its network must belong to the same workspace"),
			)
		}

		if !network.CidrBlock.Valid || network.CidrBlock.V == "" {
			return privateNetworkBusinessRollback(
				tx,
				util.UserHttpError(
					"The private network %s was created before CIDR allocation was introduced. Recreate the network to reserve addresses in it.",
					reservation.Specification.Network,
				),
			)
		}

		existingRow, existingFound := privateNetworkSelectReservation(tx, reservation.Id)
		if !tx.Ok {
			return nil
		}

		if existingFound {
			immutable := existingRow.NetworkId == reservation.Specification.Network &&
				existingRow.WorkspaceId == workspace.String() &&
				(pinnedIp == "" || existingRow.Ip == pinnedIp)
			if !immutable {
				return privateNetworkBusinessRollback(
					tx,
					util.UserHttpError(
						"A reservation with this identifier already exists with different arguments",
					),
				)
			}

			allocatedAddress = existingRow.Ip
			notifyRequired = true
			return nil
		}

		cidr, ok := orc.PrivateNetworkParseCidr(network.CidrBlock.V)
		if !ok {
			return util.ServerHttpError(
				"Private network %s has an invalid stored CIDR %s",
				reservation.Specification.Network,
				network.CidrBlock.V,
			)
		}

		allocatedIp, allocateErr := privateNetworkReservationAllocate(
			tx,
			reservation.Specification.Network,
			cidr,
			pinnedIp,
		)
		if allocateErr != nil {
			return privateNetworkBusinessRollback(tx, allocateErr)
		}

		jsonified, _ := json.Marshal(reservation)
		db.Exec(
			tx,
			`
				insert into private_network_ip_reservations(network_id, ip, reservation_id, workspace_id, created_by, project_id, resource, notified)
				values (:network_id, :ip, :reservation_id, :workspace_id, :created_by, :project_id, :resource, false)
			`,
			db.Params{
				"network_id":     reservation.Specification.Network,
				"ip":             allocatedIp,
				"reservation_id": reservation.Id,
				"workspace_id":   workspace.String(),
				"created_by":     reservation.Owner.CreatedBy,
				"project_id":     reservation.Owner.Project.Value,
				"resource":       string(jsonified),
			},
		)
		if !tx.Ok {
			return nil
		}

		allocatedAddress = allocatedIp
		notifyRequired = true
		return nil
	})
	if err != nil {
		return err
	}

	if notifyRequired && allocatedAddress != "" {
		PrivateNetworkReservationNotifyCore(PrivateNetworkReservationRow{
			NetworkId:     reservation.Specification.Network,
			Ip:            allocatedAddress,
			ReservationId: reservation.Id,
			WorkspaceId:   workspace.String(),
		})
	}

	return nil
}

func privateNetworkReservationAllocate(
	tx *db.Transaction,
	networkId string,
	cidr netip.Prefix,
	pinnedIp string,
) (string, *util.HttpError) {
	used := privateNetworkSelectTakenAddresses(tx, networkId)
	if !tx.Ok {
		return "", nil
	}

	if pinnedIp != "" {
		addr, err := privateNetworkValidateHostAddress(cidr, pinnedIp)
		if err != nil {
			return "", privateNetworkBusinessRollback(tx, err)
		}

		if _, taken := used[addr.String()]; taken {
			return "", privateNetworkBusinessRollback(
				tx,
				util.UserHttpError("The address %s is already reserved or in use", pinnedIp),
			)
		}

		return addr.String(), nil
	}

	allocated, found := privateNetworkAllocateLowestFreeAddress(cidr, used)
	if !found {
		return "", privateNetworkBusinessRollback(
			tx,
			util.UserHttpError("No free address is available in the network for the reservation"),
		)
	}

	return allocated, nil
}

func privateNetworkSelectReservedAddresses(tx *db.Transaction, networkId string) map[string]struct{} {
	used := map[string]struct{}{}

	rows := db.Select[struct{ Ip string }](
		tx,
		`
			select
				host(ip) as ip
			from
				private_network_ip_reservations
			where
				network_id = :network_id
		`,
		db.Params{"network_id": networkId},
	)

	for _, row := range rows {
		used[row.Ip] = struct{}{}
	}

	return used
}

func privateNetworkSelectOccupiedAddresses(tx *db.Transaction, networkId string) map[string]struct{} {
	used := map[string]struct{}{}

	rows := db.Select[struct{ Ip string }](
		tx,
		`
			select
				host(ip) as ip
			from
				private_network_ip_leases
			where
				network_id = :network_id
			union all
			select
				host(ip) as ip
			from
				private_network_quarantined_ips
			where
				network_id = :network_id
		`,
		db.Params{"network_id": networkId},
	)

	for _, row := range rows {
		used[row.Ip] = struct{}{}
	}

	return used
}

func privateNetworkSelectReservation(
	tx *db.Transaction,
	reservationId string,
) (privateNetworkReservationFullRow, bool) {
	row, found := db.Get[privateNetworkReservationFullRow](
		tx,
		`
			select
				network_id,
				host(ip) as ip,
				reservation_id,
				workspace_id,
				resource
			from
				private_network_ip_reservations
			where
				reservation_id = :reservation_id
		`,
		db.Params{"reservation_id": reservationId},
	)
	if !tx.Ok || !found {
		return privateNetworkReservationFullRow{}, false
	}
	return row, true
}

func PrivateNetworkReservationDelete(reservation *orc.PrivateNetworkIp) *util.HttpError {
	if !privateNetworkCurrentSettings().Enabled {
		return util.UserHttpError("Private networks are not enabled on this provider")
	}

	if reservation == nil {
		return util.ServerHttpError("Failed to delete reserved IP: reservation is nil")
	}

	ownerWorkspace := PrivateNetworkWorkspaceFromOwner(reservation.Owner)

	err := db.NewTx(func(tx *db.Transaction) *util.HttpError {
		row, found := privateNetworkSelectReservation(tx, reservation.Id)
		if !tx.Ok {
			return nil
		}

		if !found {
			return nil
		}

		if row.WorkspaceId != ownerWorkspace.String() {
			db.RequestRollback(tx)
			return util.ServerHttpError(
				"Reserved IP %s belongs to a different workspace than the delete request",
				reservation.Id,
			)
		}

		_, networkOk := privateNetworkLockNetwork(tx, row.NetworkId)
		if !tx.Ok {
			return nil
		}

		if networkOk {
			leaseCount, _ := db.Get[struct{ Count int }](
				tx,
				`
					select count(*) as count
					from
						private_network_ip_leases
					where
						reservation_id = :reservation_id
				`,
				db.Params{"reservation_id": reservation.Id},
			)
			if !tx.Ok {
				return nil
			}

			if leaseCount.Count > 0 {
				return privateNetworkBusinessRollback(
					tx,
					util.UserHttpError("The reserved address is currently in use by a job. Detach it first."),
				)
			}
		}

		db.Exec(
			tx,
			`
				delete from private_network_ip_reservations
				where
					reservation_id = :reservation_id
			`,
			db.Params{"reservation_id": reservation.Id},
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

func PrivateNetworkReservationMarkNotified(reservationId string, ip string) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update private_network_ip_reservations
				set
					notified = true
				where
					reservation_id = :reservation_id
					and ip = :ip
			`,
			db.Params{"reservation_id": reservationId, "ip": ip},
		)
	})
}

func PrivateNetworkReservationNotifyCore(reservation PrivateNetworkReservationRow) bool {
	update := orc.PrivateNetworkIpUpdate{
		IpAddress: util.OptValue(reservation.Ip),
		Timestamp: fnd.Timestamp(time.Now()),
	}

	_, err := orc.PrivateNetworkIpsControlAddUpdate.Invoke(
		fnd.BulkRequestOf(orc.ResourceUpdateAndId[orc.PrivateNetworkIpUpdate]{
			Id:     reservation.ReservationId,
			Update: update,
		}),
	)

	if err != nil {
		log.Warn(
			"Failed to notify Core about reserved IP %s of network %s: %v",
			reservation.Ip,
			reservation.NetworkId,
			err,
		)
		return false
	}

	PrivateNetworkReservationMarkNotified(reservation.ReservationId, reservation.Ip)
	return true
}

func PrivateNetworkReservationPendingNotificationSnapshot() []PrivateNetworkReservationRow {
	return db.NewTx(func(tx *db.Transaction) []PrivateNetworkReservationRow {
		rows := db.Select[PrivateNetworkReservationRow](
			tx,
			`
				select
					network_id,
					host(ip) as ip,
					reservation_id,
					workspace_id
				from
					private_network_ip_reservations
				where
					not notified
				order by
					reservation_id
			`,
			db.Params{},
		)
		if !tx.Ok {
			return nil
		}
		return rows
	})
}

func PrivateNetworkLeasesForJob(jobId string) []PrivateNetworkLeaseRow {
	return db.NewTx(func(tx *db.Transaction) []PrivateNetworkLeaseRow {
		return privateNetworkSelectJobLeases(
			tx,
			sql.Null[string]{V: jobId, Valid: jobId != ""},
			sql.Null[string]{},
		)
	})
}

func PrivateNetworkLeasesSnapshot() []PrivateNetworkLeaseRow {
	return db.NewTx(func(tx *db.Transaction) []PrivateNetworkLeaseRow {
		return privateNetworkSelectJobLeases(
			tx,
			sql.Null[string]{},
			sql.Null[string]{},
		)
	})
}

func privateNetworkSelectJobLeases(
	tx *db.Transaction,
	jobId sql.Null[string],
	networkId sql.Null[string],
) []PrivateNetworkLeaseRow {
	rows := db.Select[struct {
		NetworkId     string
		Ip            string
		MacAddress    sql.Null[string]
		JobId         string
		Rank          int
		Pinned        bool
		ReservationId sql.Null[string]
		State         string
		WorkspaceId   string
	}](
		tx,
		`
			select
				network_id,
				host(ip) as ip,
				text(mac_address) as mac_address,
				job_id,
				rank,
				pinned,
				reservation_id,
				state,
				workspace_id
			from
				private_network_ip_leases
			where
				(cast(:job_id as text) is null or job_id = :job_id)
				and (cast(:network_id as text) is null or network_id = :network_id)
			order by
				network_id,
				job_id,
				rank
		`,
		db.Params{
			"job_id":     jobId,
			"network_id": networkId,
		},
	)
	if !tx.Ok {
		return nil
	}

	result := make([]PrivateNetworkLeaseRow, 0, len(rows))
	for _, row := range rows {
		result = append(result, PrivateNetworkLeaseRow{
			NetworkId:     row.NetworkId,
			Ip:            row.Ip,
			MacAddress:    privateNetworkOptFromSql(row.MacAddress),
			JobId:         row.JobId,
			Rank:          row.Rank,
			Pinned:        row.Pinned,
			ReservationId: privateNetworkOptFromSql(row.ReservationId),
			State:         row.State,
			WorkspaceId:   row.WorkspaceId,
		})
	}
	return result
}

func PrivateNetworkLeasesMarkReleasing(jobId string) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update private_network_ip_leases
				set
					state = 'releasing'
				where
					job_id = :job_id
					and state != 'releasing'
			`,
			db.Params{"job_id": jobId},
		)
	})
}

func PrivateNetworkLeasesMarkReleasingForJobNetwork(jobId string, networkId string) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update private_network_ip_leases
				set
					state = 'releasing'
				where
					job_id = :job_id
					and network_id = :network_id
					and state != 'releasing'
			`,
			db.Params{"job_id": jobId, "network_id": networkId},
		)
	})
}

func PrivateNetworkLeaseFinishRelease(networkId string, ip string) *util.HttpError {
	return db.NewTx(func(tx *db.Transaction) *util.HttpError {
		_, _ = privateNetworkLockNetwork(tx, networkId)
		if !tx.Ok {
			return nil
		}

		db.Exec(
			tx,
			`
				delete from private_network_ip_leases
				where
					network_id = :network_id
					and ip = :ip
					and state = 'releasing'
			`,
			db.Params{"network_id": networkId, "ip": ip},
		)
		if !tx.Ok {
			return nil
		}

		return nil
	})
}

func PrivateNetworkJobAllocateLeases(job *orc.Job) ([]PrivateNetworkJobLeases, *util.HttpError) {
	settings := privateNetworkCurrentSettings()

	values, err := PrivateNetworkJobValues(job)
	if err != nil {
		return nil, err
	}

	if len(values) == 0 {
		return nil, nil
	}

	if !settings.Enabled {
		return nil, util.UserHttpError("Private networks are not enabled on this provider")
	}

	if len(values) > settings.MaxNetworksPerJob {
		return nil, util.UserHttpError(
			"A job can attach at most %d private networks",
			settings.MaxNetworksPerJob,
		)
	}

	for _, value := range values {
		if err := privateNetworkValidateAttachment(job, value); err != nil {
			return nil, err
		}
	}

	workspace := PrivateNetworkWorkspaceFromOwner(job.Resource.Owner)
	replicas := job.Specification.Replicas
	if replicas <= 0 {
		replicas = 1
	}

	for _, value := range values {
		if len(value.Ips) != 0 && len(value.Ips) != 1 && len(value.Ips) != replicas {
			return nil, util.UserHttpError(
				"The private network %s has %d pinned addresses. Supply 0, 1, or exactly the number of replicas (%d)",
				value.Id,
				len(value.Ips),
				replicas,
			)
		}

		for _, pin := range value.Ips {
			if pin == "" {
				return nil, util.UserHttpError(
					"The private network %s has an empty pinned address",
					value.Id,
				)
			}

			if _, ok := orc.PrivateNetworkParseIpv4(pin); !ok {
				return nil, util.UserHttpError("%s is not a valid IPv4 address", pin)
			}
		}
	}

	var result []PrivateNetworkJobLeases
	dbErr := db.NewTx(func(tx *db.Transaction) *util.HttpError {
		result = nil

		if scanErr := privateNetworkRequireInitialScan(); scanErr != nil {
			return scanErr
		}

		ids := make([]string, 0, len(values))
		for _, value := range values {
			ids = append(ids, value.Id)
		}
		privateNetworkSortNetworkIds(ids)

		valuesById := map[string]orc.AppParameterValue{}
		for _, value := range values {
			valuesById[value.Id] = value
		}

		lockedNetworks := map[string]privateNetworkTrackedRow{}
		cidrByNetwork := map[string]netip.Prefix{}
		subdomainByNetwork := map[string]string{}
		for _, networkId := range ids {
			network, networkOk := privateNetworkLockNetwork(tx, networkId)
			if !tx.Ok {
				return nil
			}

			if !networkOk {
				return privateNetworkBusinessRollback(
					tx,
					util.UserHttpError("The private network %s no longer exists", networkId),
				)
			}

			if network.State != PrivateNetworkStateReady {
				return privateNetworkBusinessRollback(
					tx,
					util.UserHttpError("The private network %s is not ready yet", networkId),
				)
			}

			if network.WorkspaceId != workspace.String() {
				return privateNetworkBusinessRollback(
					tx,
					util.UserHttpError(
						"The private network %s must belong to the same workspace as the job",
						networkId,
					),
				)
			}

			if !network.CidrBlock.Valid || network.CidrBlock.V == "" {
				return privateNetworkBusinessRollback(
					tx,
					util.UserHttpError(
						"The private network %s was created before CIDR allocation was introduced. Recreate the network to use it with jobs.",
						networkId,
					),
				)
			}

			cidr, ok := orc.PrivateNetworkParseCidr(network.CidrBlock.V)
			if !ok {
				return util.ServerHttpError(
					"Private network %s has an invalid stored CIDR %s",
					networkId,
					network.CidrBlock.V,
				)
			}

			lockedNetworks[networkId] = network
			cidrByNetwork[networkId] = cidr

			var cached orc.PrivateNetwork
			if json.Unmarshal([]byte(network.Resource), &cached) == nil {
				subdomainByNetwork[networkId] = cached.Specification.Subdomain
			}
		}

		present := make([]netip.Prefix, 0, len(ids))
		for _, networkId := range ids {
			present = append(present, cidrByNetwork[networkId])
		}

		for i := 0; i < len(present); i++ {
			for j := i + 1; j < len(present); j++ {
				if orc.PrivateNetworkCidrsOverlap(present[i], present[j]) {
					return privateNetworkBusinessRollback(
						tx,
						util.UserHttpError(
							"The job attaches two private networks with overlapping CIDR blocks",
						),
					)
				}
			}
		}

		perNetwork := map[string][]PrivateNetworkLeaseRow{}
		for _, networkId := range ids {
			leases, leaseErr := privateNetworkAllocateNetworkLeases(
				tx,
				workspace.String(),
				job,
				replicas,
				valuesById[networkId],
				lockedNetworks[networkId],
				cidrByNetwork[networkId],
			)
			if leaseErr != nil {
				return privateNetworkBusinessRollback(tx, leaseErr)
			}

			perNetwork[networkId] = leases
			if !tx.Ok {
				return nil
			}
		}

		for _, value := range values {
			result = append(result, PrivateNetworkJobLeases{
				NetworkId: value.Id,
				Subdomain: subdomainByNetwork[value.Id],
				CidrBlock: privateNetworkOptFromSql(lockedNetworks[value.Id].CidrBlock),
				Leases:    perNetwork[value.Id],
			})
		}

		return nil
	})
	if dbErr != nil {
		return nil, dbErr
	}

	return result, nil
}

func privateNetworkValidateAttachment(job *orc.Job, value orc.AppParameterValue) *util.HttpError {
	accessible, why, _ := JobResourceIsAccessible(job.Resource.Owner, orc.AppParameterValue{
		Type: orc.AppParameterValueTypePrivateNetwork,
		Id:   value.Id,
	})
	if !accessible {
		return util.UserHttpError("%s", why)
	}

	network, ok := PrivateNetworkRetrieve(value.Id)
	if !ok {
		return util.UserHttpError("The private network %s no longer exists", value.Id)
	}

	jobWorkspace := PrivateNetworkWorkspaceFromOwner(job.Resource.Owner)
	networkWorkspace := PrivateNetworkWorkspaceFromOwner(network.Owner)
	if jobWorkspace != networkWorkspace {
		return util.UserHttpError(
			"The private network %s must belong to the same workspace as the job",
			value.Id,
		)
	}

	return nil
}

func privateNetworkAllocateNetworkLeases(
	tx *db.Transaction,
	workspaceId string,
	job *orc.Job,
	replicas int,
	value orc.AppParameterValue,
	network privateNetworkTrackedRow,
	cidr netip.Prefix,
) ([]PrivateNetworkLeaseRow, *util.HttpError) {
	pins := value.Ips

	used := privateNetworkSelectTakenAddresses(tx, value.Id)
	if !tx.Ok {
		return nil, nil
	}

	occupied := privateNetworkSelectOccupiedAddresses(tx, value.Id)
	if !tx.Ok {
		return nil, nil
	}

	existing := privateNetworkSelectJobLeases(
		tx,
		sql.Null[string]{V: job.Id, Valid: true},
		sql.Null[string]{V: value.Id, Valid: true},
	)
	if !tx.Ok {
		return nil, nil
	}

	isVirtualMachine := false
	if job.Status.ResolvedApplication.Present {
		tool := job.Status.ResolvedApplication.Value.Invocation.Tool
		if tool.Tool.Present && tool.Tool.Value.Description.Backend == orc.ToolBackendVirtualMachine {
			isVirtualMachine = true
		}
	}

	if len(existing) > 0 {
		reused, retryErr := privateNetworkReuseJobLeases(
			tx,
			job.Resource.Owner,
			value.Id,
			pins,
			existing,
			replicas,
			isVirtualMachine,
		)
		if retryErr != nil {
			return nil, privateNetworkBusinessRollback(tx, retryErr)
		}
		if !tx.Ok {
			return nil, nil
		}
		return reused, nil
	}

	rankPins := make([]string, replicas)
	for rank := 0; rank < replicas; rank++ {
		switch {
		case len(pins) == 0:
			rankPins[rank] = ""
		case len(pins) == 1:
			if rank == 0 {
				rankPins[rank] = pins[0]
			}
		default:
			rankPins[rank] = pins[rank]
		}
	}

	type pinResult struct {
		address       string
		reservationId string
	}

	pinResults := make([]pinResult, replicas)
	seenPins := map[string]struct{}{}
	for rank, pin := range rankPins {
		if pin == "" {
			continue
		}

		addr, pinErr := privateNetworkValidateHostAddress(cidr, pin)
		if pinErr != nil {
			return nil, privateNetworkBusinessRollback(tx, pinErr)
		}

		canonical := addr.String()
		if _, duplicate := seenPins[canonical]; duplicate {
			return nil, privateNetworkBusinessRollback(
				tx,
				util.UserHttpError("The pinned address %s is used by more than one replica", pin),
			)
		}
		seenPins[canonical] = struct{}{}

		if _, occupiedAddress := occupied[canonical]; occupiedAddress {
			return nil, privateNetworkBusinessRollback(
				tx,
				util.UserHttpError("The pinned address %s is already in use", pin),
			)
		}

		reservationRow, found := privateNetworkSelectReservationByIp(tx, value.Id, canonical)
		if !tx.Ok {
			return nil, nil
		}

		result := pinResult{address: canonical}
		if found {
			if authErr := privateNetworkReservationAuthorized(
				job.Resource.Owner,
				reservationRow,
				workspaceId,
			); authErr != nil {
				return nil, privateNetworkBusinessRollback(tx, authErr)
			}

			result.reservationId = reservationRow.ReservationId
		}

		pinResults[rank] = result
		used[canonical] = struct{}{}
	}

	result := make([]PrivateNetworkLeaseRow, 0, replicas)
	for rank := 0; rank < replicas; rank++ {
		pin := pinResults[rank]

		address := ""
		reservationId := util.OptNone[string]()
		isPinned := false

		if pin.address != "" {
			address = pin.address
			isPinned = true

			if pin.reservationId != "" {
				reservationId = util.OptValue(pin.reservationId)
			}
		} else {
			allocated, found := privateNetworkAllocateLowestFreeAddress(cidr, used)
			if !found {
				return nil, privateNetworkBusinessRollback(
					tx,
					util.UserHttpError("The private network %s has no free addresses", value.Id),
				)
			}

			address = allocated
			used[address] = struct{}{}
		}

		macAddress := util.OptNone[string]()
		if isVirtualMachine {
			inserted := false
			for attempt := 0; attempt < 16 && !inserted; attempt++ {
				mac, macOk := privateNetworkGenerateMacAddress()
				if !macOk {
					return nil, privateNetworkBusinessRollback(
						tx,
						util.ServerHttpError("Could not generate a MAC address"),
					)
				}

				rows := db.Select[struct{ JobId string }](
					tx,
					`
						insert into private_network_ip_leases(network_id, ip, mac_address, job_id, rank, pinned, reservation_id, state, workspace_id)
						values (:network_id, :ip, :mac_address, :job_id, :rank, :pinned, :reservation_id, 'pending', :workspace_id)
						on conflict (network_id, mac_address) do nothing
						returning
							job_id
					`,
					db.Params{
						"network_id":     value.Id,
						"ip":             address,
						"mac_address":    mac,
						"job_id":         job.Id,
						"rank":           rank,
						"pinned":         isPinned,
						"reservation_id": reservationId.Sql(),
						"workspace_id":   workspaceId,
					},
				)
				if !tx.Ok {
					return nil, nil
				}

				if len(rows) == 1 && rows[0].JobId == job.Id {
					inserted = true
					macAddress = util.OptValue(mac)
				}
			}

			if !inserted {
				return nil, privateNetworkBusinessRollback(
					tx,
					util.ServerHttpError("Could not allocate a MAC address in the private network %s", value.Id),
				)
			}
		} else {
			db.Exec(
				tx,
				`
					insert into private_network_ip_leases(network_id, ip, mac_address, job_id, rank, pinned, reservation_id, state, workspace_id)
					values (:network_id, :ip, :mac_address, :job_id, :rank, :pinned, :reservation_id, 'pending', :workspace_id)
				`,
				db.Params{
					"network_id":     value.Id,
					"ip":             address,
					"mac_address":    macAddress.Sql(),
					"job_id":         job.Id,
					"rank":           rank,
					"pinned":         isPinned,
					"reservation_id": reservationId.Sql(),
					"workspace_id":   workspaceId,
				},
			)
			if !tx.Ok {
				return nil, nil
			}
		}

		result = append(result, PrivateNetworkLeaseRow{
			NetworkId:     value.Id,
			Ip:            address,
			MacAddress:    macAddress,
			JobId:         job.Id,
			Rank:          rank,
			Pinned:        isPinned,
			ReservationId: reservationId,
			State:         PrivateNetworkLeaseStatePending,
			WorkspaceId:   workspaceId,
		})
	}

	return result, nil
}

func privateNetworkReuseJobLeases(
	tx *db.Transaction,
	jobOwner orc.ResourceOwner,
	networkId string,
	pins []string,
	existing []PrivateNetworkLeaseRow,
	replicas int,
	requireMacAddress bool,
) ([]PrivateNetworkLeaseRow, *util.HttpError) {
	if len(existing) != replicas {
		return nil, privateNetworkBusinessRollback(
			tx,
			util.UserHttpError(
				"A previous submission of this job attached different networks or a different number of replicas. The old leases must be released before retrying.",
			),
		)
	}

	jobWorkspace := PrivateNetworkWorkspaceFromOwner(jobOwner)

	for _, lease := range existing {
		if lease.State == PrivateNetworkLeaseStateReleasing {
			return nil, privateNetworkBusinessRollback(
				tx,
				util.UserHttpError(
					"The previous use of the private network %s is still being released. Try again later.",
					networkId,
				),
			)
		}

		if lease.WorkspaceId != jobWorkspace.String() {
			return nil, privateNetworkBusinessRollback(
				tx,
				util.UserHttpError(
					"A previous submission of this job has leases of another workspace in the private network %s. The old leases must be released before retrying.",
					networkId,
				),
			)
		}

		if requireMacAddress && !lease.MacAddress.Present {
			return nil, privateNetworkBusinessRollback(
				tx,
				util.UserHttpError(
					"A previous submission of this job has no MAC address in the private network %s. The old leases must be released before retrying.",
					networkId,
				),
			)
		}
	}

	byRank := make([]PrivateNetworkLeaseRow, replicas)
	for _, lease := range existing {
		if lease.Rank < 0 || lease.Rank >= replicas {
			return nil, privateNetworkBusinessRollback(
				tx,
				util.UserHttpError(
					"A previous submission of this job has an invalid rank in the private network %s",
					networkId,
				),
			)
		}
		byRank[lease.Rank] = lease
	}

	expectedPins := make([]string, replicas)
	for rank := 0; rank < replicas; rank++ {
		switch {
		case len(pins) == 0:
			expectedPins[rank] = ""
		case len(pins) == 1:
			if rank == 0 {
				expectedPins[rank] = pins[0]
			}
		default:
			expectedPins[rank] = pins[rank]
		}
	}

	for rank, lease := range byRank {
		expected := expectedPins[rank]
		if expected == "" && lease.Pinned {
			return nil, privateNetworkBusinessRollback(
				tx,
				util.UserHttpError(
					"A previous submission of this job pinned addresses in the private network %s. The old leases must be released before retrying without pins.",
					networkId,
				),
			)
		}

		if expected != "" {
			if !lease.Pinned {
				return nil, privateNetworkBusinessRollback(
					tx,
					util.UserHttpError(
						"A previous submission of this job did not pin addresses in the private network %s. The old leases must be released before retrying with pins.",
						networkId,
					),
				)
			}

			if lease.Ip != expected {
				return nil, privateNetworkBusinessRollback(
					tx,
					util.UserHttpError(
						"A previous submission of this job pinned different addresses in the private network %s. The old leases must be released before retrying with new pins.",
						networkId,
					),
				)
			}

			if lease.ReservationId.Present {
				reservationRow, reservationOk := privateNetworkSelectReservation(tx, lease.ReservationId.Value)
				if !tx.Ok {
					return nil, nil
				}

				if !reservationOk {
					return nil, privateNetworkBusinessRollback(
						tx,
						util.UserHttpError(
							"The pinned address %s belongs to a reservation that is no longer valid",
							expected,
						),
					)
				}

				if authErr := privateNetworkReservationAuthorized(
					jobOwner,
					reservationRow,
					lease.WorkspaceId,
				); authErr != nil {
					return nil, privateNetworkBusinessRollback(tx, authErr)
				}
			}
		}
	}

	return PrivateNetworkLeaseRowsNormalize(existing), nil
}

type privateNetworkReservationFullRow struct {
	NetworkId     string
	Ip            string
	ReservationId string
	WorkspaceId   string
	Resource      string
}

func privateNetworkSelectReservationByIp(
	tx *db.Transaction,
	networkId string,
	ip string,
) (privateNetworkReservationFullRow, bool) {
	row, found := db.Get[privateNetworkReservationFullRow](
		tx,
		`
			select
				network_id,
				host(ip) as ip,
				reservation_id,
				workspace_id,
				resource
			from
				private_network_ip_reservations
			where
				network_id = :network_id
				and ip = :ip
		`,
		db.Params{"network_id": networkId, "ip": ip},
	)
	if !tx.Ok || !found {
		return privateNetworkReservationFullRow{}, false
	}

	return row, true
}

func privateNetworkReservationAuthorized(
	jobOwner orc.ResourceOwner,
	reservation privateNetworkReservationFullRow,
	workspaceId string,
) *util.HttpError {
	var metadata orc.PrivateNetworkIp
	if json.Unmarshal([]byte(reservation.Resource), &metadata) != nil {
		return util.ServerHttpError(
			"The reserved address %s has invalid stored metadata",
			reservation.Ip,
		)
	}

	reservationWorkspace := PrivateNetworkWorkspaceFromOwner(metadata.Owner)
	if reservationWorkspace.String() != workspaceId {
		return util.UserHttpError(
			"The pinned address %s belongs to a reservation of another workspace",
			reservation.Ip,
		)
	}

	if !ResourceCanUse(jobOwner, metadata.Owner, metadata.Permissions, false) {
		return util.UserHttpError(
			"You do not have permission to use the reserved address %s",
			reservation.Ip,
		)
	}

	return nil
}

func privateNetworkSelectTakenAddresses(tx *db.Transaction, networkId string) map[string]struct{} {
	used := privateNetworkSelectReservedAddresses(tx, networkId)
	for ip := range privateNetworkSelectOccupiedAddresses(tx, networkId) {
		used[ip] = struct{}{}
	}
	return used
}

func PrivateNetworkLeaseRowsNormalize(rows []PrivateNetworkLeaseRow) []PrivateNetworkLeaseRow {
	byRank := make([]PrivateNetworkLeaseRow, 0, len(rows))
	used := make(map[int]struct{}, len(rows))
	for _, row := range rows {
		if row.Rank < 0 {
			continue
		}

		if _, duplicate := used[row.Rank]; duplicate {
			continue
		}
		used[row.Rank] = struct{}{}

		byRank = append(byRank, row)
	}

	slices.SortFunc(byRank, func(a, b PrivateNetworkLeaseRow) int {
		return a.Rank - b.Rank
	})
	return byRank
}
