package controller

import (
	"database/sql"
	"encoding/binary"
	"encoding/json"
	"net/netip"
	"slices"
	"strconv"
	"strings"
	"sync"

	cfg "ucloud.dk/pkg/config"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

const (
	PrivateNetworkStateProvisioning = "provisioning"
	PrivateNetworkStateReady        = "ready"
	PrivateNetworkStateDeleting     = "deleting"

	PrivateNetworkLeaseStatePending   = "pending"
	PrivateNetworkLeaseStateReleasing = "releasing"
)

const privateNetworkAdvisoryNamespace = 712367812
const privateNetworkNameAdvisoryNamespace = 712367813
const privateNetworkReconcileAdvisoryNamespace = 712367814

type PrivateNetworkWorkspaceKind string

const (
	PrivateNetworkWorkspaceProject PrivateNetworkWorkspaceKind = "project"
	PrivateNetworkWorkspaceUser    PrivateNetworkWorkspaceKind = "user"
)

type PrivateNetworkWorkspace struct {
	Kind PrivateNetworkWorkspaceKind
	Id   string
}

func PrivateNetworkWorkspaceFromOwner(owner orc.ResourceOwner) PrivateNetworkWorkspace {
	if owner.Project.Present {
		return PrivateNetworkWorkspace{
			Kind: PrivateNetworkWorkspaceProject,
			Id:   owner.Project.Value,
		}
	}

	return PrivateNetworkWorkspace{
		Kind: PrivateNetworkWorkspaceUser,
		Id:   owner.CreatedBy,
	}
}

func PrivateNetworkWorkspaceParse(value string) (PrivateNetworkWorkspace, bool) {
	if project, ok := strings.CutPrefix(value, "project:"); ok {
		return PrivateNetworkWorkspace{Kind: PrivateNetworkWorkspaceProject, Id: project}, true
	}

	if user, ok := strings.CutPrefix(value, "user:"); ok {
		return PrivateNetworkWorkspace{Kind: PrivateNetworkWorkspaceUser, Id: user}, true
	}

	return PrivateNetworkWorkspace{}, false
}

func (w PrivateNetworkWorkspace) String() string {
	if w.Kind == PrivateNetworkWorkspaceProject {
		return "project:" + w.Id
	}

	return "user:" + w.Id
}

var privateNetworks = map[string]*orc.PrivateNetwork{}
var privateNetworkMutex = sync.Mutex{}

var privateNetworkSettingsMu = sync.RWMutex{}
var privateNetworkSettings = privateNetworkParsedSettings{}
var privateNetworkSettingsConfigured = false

type privateNetworkParsedSettings struct {
	Enabled           bool
	Pools             []netip.Prefix
	Forbidden         []netip.Prefix
	DefaultPrefixLen  int
	MaxNetworksPerJob int
}

type privateNetworkTrackedRow struct {
	ResourceId  string
	CreatedBy   string
	ProjectId   sql.Null[string]
	Resource    string
	CidrBlock   sql.Null[string]
	State       string
	WorkspaceId string
}

func PrivateNetworkConfigureDatabase(settings cfg.KubernetesPrivateNetworks) {
	parsed := privateNetworkParsedSettings{
		Enabled:           settings.Enabled,
		DefaultPrefixLen:  settings.DefaultPrefixLen,
		MaxNetworksPerJob: settings.MaxNetworksPerJob,
	}

	if parsed.DefaultPrefixLen <= 0 {
		parsed.DefaultPrefixLen = 24
	}

	if parsed.MaxNetworksPerJob <= 0 {
		parsed.MaxNetworksPerJob = 4
	}

	if parsed.Enabled {
		if parsed.DefaultPrefixLen < 16 || parsed.DefaultPrefixLen > 24 {
			log.Fatal("Private network defaultPrefixLen must be between 16 and 24")
		}

		for _, pool := range settings.CidrPools {
			parsedPool, err := netip.ParsePrefix(pool)
			if err != nil || !parsedPool.Addr().Is4() {
				log.Fatal("Private network CIDR pool '%s' is not a valid IPv4 CIDR", pool)
			}
			parsed.Pools = append(parsed.Pools, parsedPool.Masked())
		}

		slices.SortFunc(parsed.Pools, func(a, b netip.Prefix) int {
			return strings.Compare(a.String(), b.String())
		})

		for _, forbidden := range settings.ForbiddenCidrs {
			parsedForbidden, err := netip.ParsePrefix(forbidden)
			if err != nil || !parsedForbidden.Addr().Is4() {
				log.Fatal("Private network forbidden CIDR '%s' is not a valid IPv4 CIDR", forbidden)
			}
			parsed.Forbidden = append(parsed.Forbidden, parsedForbidden.Masked())
		}
	}

	privateNetworkSettingsMu.Lock()
	privateNetworkSettings = parsed
	privateNetworkSettingsConfigured = true
	privateNetworkSettingsMu.Unlock()
}

func PrivateNetworksFeatureEnabled() bool {
	return privateNetworkCurrentSettings().Enabled
}

func privateNetworkCurrentSettings() privateNetworkParsedSettings {
	privateNetworkSettingsMu.RLock()
	defer privateNetworkSettingsMu.RUnlock()
	return privateNetworkSettings
}

func initPrivateNetworkDatabase() {
	if !RunsServerCode() {
		return
	}

	if !privateNetworkSettingsConfigured {
		if kubernetes := cfg.Services.Kubernetes(); kubernetes != nil {
			PrivateNetworkConfigureDatabase(kubernetes.Compute.PrivateNetworks)
		}
	}

	privateNetworkMutex.Lock()
	privateNetworks = map[string]*orc.PrivateNetwork{}
	privateNetworkMutex.Unlock()

	rows := db.NewTx(func(tx *db.Transaction) []privateNetworkTrackedRow {
		return db.Select[privateNetworkTrackedRow](
			tx,
			`
				select
					resource_id,
					created_by,
					project_id,
					resource,
					cidr_block::text as cidr_block,
					state,
					workspace_id
				from
					tracked_private_networks
			`,
			db.Params{},
		)
	})

	privateNetworkMutex.Lock()
	for _, row := range rows {
		if row.State == PrivateNetworkStateDeleting {
			continue
		}

		var network orc.PrivateNetwork
		if json.Unmarshal([]byte(row.Resource), &network) == nil && network.Id != "" {
			network.Status.CidrBlock = util.SqlNullToOpt(row.CidrBlock)
			copied := network
			privateNetworks[row.ResourceId] = &copied
		}
	}
	privateNetworkMutex.Unlock()

	for _, network := range privateNetworkFetchAll() {
		if network.Status.CidrBlock.Present {
			continue
		}

		privateNetworkImportLegacy(network)
	}
}

func privateNetworkFetchAll() []orc.PrivateNetwork {
	var result []orc.PrivateNetwork
	next := ""

	for {
		request := orc.PrivateNetworksControlBrowseRequest{Next: util.OptStringIfNotEmpty(next)}
		request.IncludeOthers = true
		page, err := orc.PrivateNetworksControlBrowse.Invoke(request)

		if err != nil {
			break
		}

		result = append(result, page.Items...)

		if !page.Next.Present {
			break
		}
		next = page.Next.Value
	}

	return result
}

func PrivateNetworkTrackNew(network orc.PrivateNetwork) {
	cacheLive := false
	db.NewTx0(func(tx *db.Transaction) {
		cacheLive = false

		row, found := privateNetworkSelectNetwork(tx, network.Id)
		if !tx.Ok {
			return
		}

		if !found {
			return
		}

		privateNetworkTrackExisting(tx, &network, row, &cacheLive, true)
	})

	privateNetworkMutex.Lock()
	if cacheLive {
		copied := network
		privateNetworks[network.Id] = &copied
	} else {
		delete(privateNetworks, network.Id)
	}
	privateNetworkMutex.Unlock()
}

func privateNetworkImportLegacy(network orc.PrivateNetwork) {
	jsonified, _ := json.Marshal(network)
	workspace := PrivateNetworkWorkspaceFromOwner(network.Owner)

	cacheLive := false
	db.NewTx0(func(tx *db.Transaction) {
		cacheLive = false

		row, found := privateNetworkSelectNetwork(tx, network.Id)
		if !tx.Ok {
			return
		}

		if found {
			privateNetworkTrackExisting(tx, &network, row, &cacheLive, false)
			return
		}

		privateNetworkLockName(tx)
		if !tx.Ok {
			return
		}

		row, found = privateNetworkSelectNetwork(tx, network.Id)
		if !tx.Ok {
			return
		}

		if found {
			privateNetworkTrackExisting(tx, &network, row, &cacheLive, false)
			return
		}

		conflictCount, _ := db.Get[struct{ Count int }](
			tx,
			`
				select count(*) as count
				from
					tracked_private_networks
				where
					resource_id != :resource_id
					and lower(resource->'specification'->>'subdomain') = lower(:subdomain)
			`,
			db.Params{
				"resource_id": network.Id,
				"subdomain":   network.Specification.Subdomain,
			},
		)
		if !tx.Ok {
			return
		}

		if conflictCount.Count > 0 {
			log.Warn("Skipping import of private network %s: the subdomain is already in use", network.Id)
			return
		}

		db.Exec(
			tx,
			`
				insert into tracked_private_networks(resource_id, created_by, project_id, resource, cidr_block, state, workspace_id)
				values (:resource_id, :created_by, :project_id, :resource, null, 'ready', :workspace_id)
			`,
			db.Params{
				"resource_id":  network.Id,
				"created_by":   network.Owner.CreatedBy,
				"project_id":   network.Owner.Project.Value,
				"resource":     string(jsonified),
				"workspace_id": workspace.String(),
			},
		)
		if !tx.Ok {
			return
		}

		cacheLive = true
	})

	privateNetworkMutex.Lock()
	if cacheLive {
		copied := network
		privateNetworks[network.Id] = &copied
	} else {
		delete(privateNetworks, network.Id)
	}
	privateNetworkMutex.Unlock()
}

func privateNetworkTrackExisting(
	tx *db.Transaction,
	network *orc.PrivateNetwork,
	row privateNetworkTrackedRow,
	cacheLive *bool,
	persist bool,
) {
	if row.State == PrivateNetworkStateDeleting {
		return
	}

	var stored orc.PrivateNetwork
	if json.Unmarshal([]byte(row.Resource), &stored) != nil {
		stored = *network
	}

	merged := *network
	merged.Owner = stored.Owner
	merged.Specification.Subdomain = stored.Specification.Subdomain
	merged.Specification.Cidr = stored.Specification.Cidr
	merged.Status.CidrBlock = util.SqlNullToOpt(row.CidrBlock)

	if persist {
		mergedJson, _ := json.Marshal(merged)
		db.Exec(
			tx,
			`
				update tracked_private_networks
				set
					resource = :resource
				where
					resource_id = :resource_id
					and state != 'deleting'
			`,
			db.Params{
				"resource_id": network.Id,
				"resource":    string(mergedJson),
			},
		)
		if !tx.Ok {
			return
		}
	}

	*cacheLive = true
	*network = merged
}

func PrivateNetworkRetrieve(id string) (orc.PrivateNetwork, bool) {
	privateNetworkMutex.Lock()
	res, ok := privateNetworks[id]
	privateNetworkMutex.Unlock()

	if ok {
		return *res, true
	}

	row, found := db.NewTx2(func(tx *db.Transaction) (privateNetworkTrackedRow, bool) {
		return privateNetworkSelectNetwork(tx, id)
	})
	if !found {
		return orc.PrivateNetwork{}, false
	}

	if row.State == PrivateNetworkStateDeleting {
		return orc.PrivateNetwork{}, false
	}

	var network orc.PrivateNetwork
	if json.Unmarshal([]byte(row.Resource), &network) != nil || network.Id != id {
		return orc.PrivateNetwork{}, false
	}

	network.Status.CidrBlock = util.SqlNullToOpt(row.CidrBlock)

	privateNetworkMutex.Lock()
	copied := network
	privateNetworks[id] = &copied
	privateNetworkMutex.Unlock()

	return network, true
}

func privateNetworkRefreshMetadata(id string) (orc.PrivateNetwork, bool) {
	privateNetworkMutex.Lock()
	delete(privateNetworks, id)
	privateNetworkMutex.Unlock()

	return PrivateNetworkRetrieve(id)
}

func privateNetworkOptFromSql(value sql.Null[string]) util.Option[string] {
	if value.Valid {
		return util.OptValue(value.V)
	}

	return util.OptNone[string]()
}

func privateNetworkSelectNetwork(tx *db.Transaction, networkId string) (privateNetworkTrackedRow, bool) {
	row, found := db.Get[privateNetworkTrackedRow](
		tx,
		`
			select
				resource_id,
				created_by,
				project_id,
				resource,
				cidr_block::text as cidr_block,
				state,
				workspace_id
			from
				tracked_private_networks
			where
				resource_id = :resource_id
		`,
		db.Params{"resource_id": networkId},
	)
	if !tx.Ok {
		return privateNetworkTrackedRow{}, false
	}
	return row, found
}

func privateNetworkLockNetwork(tx *db.Transaction, networkId string) (privateNetworkTrackedRow, bool) {
	row, found := db.Get[privateNetworkTrackedRow](
		tx,
		`
			select
				resource_id,
				created_by,
				project_id,
				resource,
				cidr_block::text as cidr_block,
				state,
				workspace_id
			from
				tracked_private_networks
			where
				resource_id = :resource_id
			for update
		`,
		db.Params{"resource_id": networkId},
	)
	if !tx.Ok {
		return privateNetworkTrackedRow{}, false
	}
	return row, found
}

func privateNetworkLockPools(tx *db.Transaction) {
	settings := privateNetworkCurrentSettings()
	for _, pool := range settings.Pools {
		db.Exec(
			tx,
			`
				select pg_advisory_xact_lock(:namespace, :pool_key)
			`,
			db.Params{
				"namespace": privateNetworkAdvisoryNamespace,
				"pool_key":  int32(privateNetworkAddrToUint32(pool.Addr())),
			},
		)
	}
}

func privateNetworkLockName(tx *db.Transaction) {
	db.Exec(
		tx,
		`
			select pg_advisory_xact_lock(:namespace, :name_key)
		`,
		db.Params{
			"namespace": privateNetworkNameAdvisoryNamespace,
			"name_key":  1,
		},
	)
}

func privateNetworkSortNetworkIds(ids []string) {
	slices.SortFunc(ids, func(a, b string) int {
		aValue, aErr := strconv.ParseUint(a, 10, 64)
		bValue, bErr := strconv.ParseUint(b, 10, 64)

		switch {
		case aErr == nil && bErr == nil:
			if aValue != bValue {
				if aValue < bValue {
					return -1
				}
				return 1
			}
			return strings.Compare(a, b)
		case aErr == nil:
			return -1
		case bErr == nil:
			return 1
		default:
			return strings.Compare(a, b)
		}
	})
}

func privateNetworkAddrToUint32(addr netip.Addr) uint32 {
	bytes := addr.As4()
	return binary.BigEndian.Uint32(bytes[:])
}

func privateNetworkUint32ToAddr(value uint32) netip.Addr {
	var bytes [4]byte
	binary.BigEndian.PutUint32(bytes[:], value)
	return netip.AddrFrom4(bytes)
}

func privateNetworkCidrRange(prefix netip.Prefix) (uint64, uint64) {
	first := uint64(privateNetworkAddrToUint32(prefix.Addr()))
	if prefix.Bits() == 0 {
		return 0, 0xFFFFFFFF
	}

	mask := uint64(0xFFFFFFFF) << uint64(32-prefix.Bits())
	masked := first & mask
	return masked, masked | (^mask & 0xFFFFFFFF)
}

func privateNetworkPrefixOverlapsAny(candidate netip.Prefix, others []netip.Prefix) bool {
	for _, other := range others {
		if orc.PrivateNetworkCidrsOverlap(candidate, other) {
			return true
		}
	}
	return false
}

func privateNetworkAlignUp(value, alignment uint64) uint64 {
	if alignment == 0 {
		return value
	}

	remainder := value % alignment
	if remainder == 0 {
		return value
	}

	return value + (alignment - remainder)
}

func privateNetworkValidateHostAddress(cidr netip.Prefix, value string) (netip.Addr, *util.HttpError) {
	addr, ok := orc.PrivateNetworkParseIpv4(value)
	if !ok {
		return netip.Addr{}, util.UserHttpError("%s is not a valid IPv4 address", value)
	}

	first, last := privateNetworkCidrRange(cidr)
	numeric := uint64(privateNetworkAddrToUint32(addr))
	if numeric < first || numeric > last {
		return netip.Addr{}, util.UserHttpError("%s is not inside the network %s", value, cidr.String())
	}

	if numeric == first {
		return netip.Addr{}, util.UserHttpError("%s is the network address of %s", value, cidr.String())
	}

	if numeric == last {
		return netip.Addr{}, util.UserHttpError("%s is the broadcast address of %s", value, cidr.String())
	}

	if numeric == first+1 {
		return netip.Addr{}, util.UserHttpError("%s is the gateway address of %s", value, cidr.String())
	}

	return addr, nil
}

func privateNetworkAllocateLowestFreeAddress(cidr netip.Prefix, used map[string]struct{}) (string, bool) {
	first, last := privateNetworkCidrRange(cidr)
	for candidate := first + 2; candidate < last; candidate++ {
		address := privateNetworkUint32ToAddr(uint32(candidate)).String()
		if _, taken := used[address]; !taken {
			return address, true
		}
	}

	return "", false
}

type PrivateNetworkSnapshotNetwork struct {
	ResourceId  string
	Subdomain   string
	CidrBlock   util.Option[string]
	State       string
	WorkspaceId string
}

func PrivateNetworkSnapshotNetworks() []PrivateNetworkSnapshotNetwork {
	return db.NewTx(func(tx *db.Transaction) []PrivateNetworkSnapshotNetwork {
		rows := db.Select[struct {
			ResourceId  string
			Subdomain   string
			CidrBlock   sql.Null[string]
			State       string
			WorkspaceId string
		}](
			tx,
			`
				select
					resource_id,
					resource->'specification'->>'subdomain' as subdomain,
					cidr_block::text as cidr_block,
					state,
					workspace_id
				from
					tracked_private_networks
				order by
					resource_id
			`,
			db.Params{},
		)
		if !tx.Ok {
			return nil
		}

		var result []PrivateNetworkSnapshotNetwork
		for _, row := range rows {
			result = append(result, PrivateNetworkSnapshotNetwork{
				ResourceId:  row.ResourceId,
				Subdomain:   row.Subdomain,
				CidrBlock:   privateNetworkOptFromSql(row.CidrBlock),
				State:       row.State,
				WorkspaceId: row.WorkspaceId,
			})
		}
		return result
	})
}

func PrivateNetworkSnapshotRetrieve(networkId string) (PrivateNetworkSnapshotNetwork, bool) {
	return db.NewTx2(func(tx *db.Transaction) (PrivateNetworkSnapshotNetwork, bool) {
		row, found := db.Get[struct {
			ResourceId  string
			Subdomain   string
			CidrBlock   sql.Null[string]
			State       string
			WorkspaceId string
		}](
			tx,
			`
				select
					resource_id,
					resource->'specification'->>'subdomain' as subdomain,
					cidr_block::text as cidr_block,
					state,
					workspace_id
				from
					tracked_private_networks
				where
					resource_id = :resource_id
			`,
			db.Params{"resource_id": networkId},
		)
		if !tx.Ok {
			return PrivateNetworkSnapshotNetwork{}, false
		}

		if !found {
			return PrivateNetworkSnapshotNetwork{}, false
		}

		return PrivateNetworkSnapshotNetwork{
			ResourceId:  row.ResourceId,
			Subdomain:   row.Subdomain,
			CidrBlock:   privateNetworkOptFromSql(row.CidrBlock),
			State:       row.State,
			WorkspaceId: row.WorkspaceId,
		}, true
	})
}

type PrivateNetworkPoolUtilization struct {
	Pool        string
	PrefixLen   int
	TotalBlocks int
	UsedBlocks  int
}

func PrivateNetworkSnapshotPoolUtilization() []PrivateNetworkPoolUtilization {
	settings := privateNetworkCurrentSettings()
	if len(settings.Pools) == 0 {
		return nil
	}

	rows := db.NewTx(func(tx *db.Transaction) []struct{ CidrBlock string } {
		return db.Select[struct{ CidrBlock string }](
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
	})

	var used []netip.Prefix
	for _, row := range rows {
		if parsed, ok := orc.PrivateNetworkParseCidr(row.CidrBlock); ok {
			used = append(used, parsed)
		}
	}

	blockSize := uint64(1) << (32 - uint64(settings.DefaultPrefixLen))
	var result []PrivateNetworkPoolUtilization
	for _, pool := range settings.Pools {
		poolFirst, poolLast := privateNetworkCidrRange(pool)
		total := int((poolLast - poolFirst + 1) / blockSize)

		var intervals [][2]uint64
		for _, usedPrefix := range used {
			usedFirst, usedLast := privateNetworkCidrRange(usedPrefix)
			if usedLast < poolFirst || usedFirst > poolLast {
				continue
			}

			clipFirst := usedFirst
			if clipFirst < poolFirst {
				clipFirst = poolFirst
			}

			clipLast := usedLast
			if clipLast > poolLast {
				clipLast = poolLast
			}

			intervals = append(intervals, [2]uint64{clipFirst / blockSize, clipLast / blockSize})
		}

		slices.SortFunc(intervals, func(a, b [2]uint64) int {
			if a[0] != b[0] {
				if a[0] < b[0] {
					return -1
				}
				return 1
			}
			return 0
		})

		usedBlocks := 0
		openFirst := uint64(0)
		openLast := uint64(0)
		open := false
		for _, interval := range intervals {
			if open && interval[0] <= openLast+1 {
				if interval[1] > openLast {
					openLast = interval[1]
				}
				continue
			}

			if open {
				usedBlocks += int(openLast - openFirst + 1)
			}

			openFirst = interval[0]
			openLast = interval[1]
			open = true
		}

		if open {
			usedBlocks += int(openLast - openFirst + 1)
		}

		result = append(result, PrivateNetworkPoolUtilization{
			Pool:        pool.String(),
			PrefixLen:   settings.DefaultPrefixLen,
			TotalBlocks: total,
			UsedBlocks:  usedBlocks,
		})
	}

	return result
}
