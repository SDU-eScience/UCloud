package orchestrator

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"strconv"
	"sync"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const privateNetworkReclaimInterval = 5 * time.Minute

type privateNetworkReclaimCandidate struct {
	Resource     int64
	Name         string
	Subdomain    string
	LastUsedAt   time.Time
	ReclaimAfter sql.Null[time.Time]
}

var privateNetworkReclaimMu sync.Mutex

var privateNetworkReclaimFailures struct {
	Mu     sync.Mutex
	Counts map[string]int
}

func initPrivateNetworkReclaimer() {
	if resourceGlobals.Testing.Enabled {
		return
	}

	go func() {
		time.Sleep(5 * time.Second)
		privateNetworkReclaimPass()

		ticker := time.NewTicker(privateNetworkReclaimInterval)
		defer ticker.Stop()

		for range ticker.C {
			privateNetworkReclaimPass()
		}
	}()
}

func privateNetworkReclaimPass() {
	if !privateNetworkReclaimMu.TryLock() {
		log.Warn("Skipping private network reclaim pass because the previous pass is still running")
		return
	}
	defer privateNetworkReclaimMu.Unlock()

	allSupport := SupportRetrieveProducts[orcapi.PrivateNetworkSupport](privateNetworkType)
	for provider, supports := range allSupport.ProductsByProvider {
		for _, item := range supports {
			if item.Support.EffectiveType() != orcapi.NetworkTypeInfiniband {
				continue
			}

			policy := item.Support.ReclaimPolicy
			if !policy.Enabled {
				continue
			}

			privateNetworkReclaimProduct(provider, item.Product.Name, item.Product.Category.Name, item.Support, policy)
		}
	}
}

func privateNetworkReclaimProduct(
	provider string,
	productName string,
	productCategory string,
	support orcapi.PrivateNetworkSupport,
	policy orcapi.ReclaimPolicy,
) {
	totalBlocks := privateNetworkReclaimBlockCount(support.AddressPools)
	totalBlocks -= privateNetworkReclaimBlockCount(support.ExcludedRanges)
	if totalBlocks <= 0 {
		return
	}

	usedBlocks, pendingBlocks := privateNetworkReclaimCounts(provider, productName, productCategory)
	usedBlocks = min(usedBlocks, totalBlocks)
	freeBlocks := totalBlocks - usedBlocks

	privateNetworkFreeBlocks.WithLabelValues(provider, productName).Set(float64(freeBlocks))
	privateNetworkReclaimPending.WithLabelValues(provider, productName).Set(float64(pendingBlocks))

	freePercent := float64(freeBlocks) / float64(totalBlocks) * 100.0
	if freePercent >= float64(policy.HighWatermarkPercent) {
		privateNetworkReclaimCancelPending(provider, productName, productCategory)
		privateNetworkReclaimPending.WithLabelValues(provider, productName).Set(0)
		return
	}

	remaining := policy.MaxPerRun
	if remaining <= 0 {
		return
	}

	lastUsedThreshold := time.Now().Add(-policy.MinAge)
	dueCandidates := privateNetworkReclaimCandidates(
		provider,
		productName,
		productCategory,
		true,
		lastUsedThreshold,
		remaining,
	)

	for _, candidate := range dueCandidates {
		if remaining <= 0 {
			break
		}
		remaining--

		networkId := fmt.Sprint(candidate.Resource)
		privateNetworkReclaimDue(networkId, support, policy, lastUsedThreshold)
	}

	usedBlocks, pendingBlocks = privateNetworkReclaimCounts(provider, productName, productCategory)
	usedBlocks = min(usedBlocks, totalBlocks)
	freeBlocks = totalBlocks - usedBlocks
	freePercent = float64(freeBlocks) / float64(totalBlocks) * 100.0

	if freePercent >= float64(policy.HighWatermarkPercent) {
		privateNetworkReclaimCancelPending(provider, productName, productCategory)
		pendingBlocks = 0
	} else if freePercent < float64(policy.LowWatermarkPercent) {
		targetFreeBlocks := (totalBlocks*policy.HighWatermarkPercent + 99) / 100
		projectedFreeBlocks := freeBlocks + pendingBlocks
		toMark := targetFreeBlocks - projectedFreeBlocks
		if toMark > 0 {
			toMark = min(toMark, remaining)
			candidates := privateNetworkReclaimCandidates(
				provider,
				productName,
				productCategory,
				false,
				lastUsedThreshold,
				toMark,
			)
			for _, candidate := range candidates {
				if toMark <= 0 {
					break
				}
				networkId := fmt.Sprint(candidate.Resource)
				if privateNetworkReclaimMarkPending(networkId, support, policy, lastUsedThreshold) {
					pendingBlocks++
					toMark--
					remaining--
				}
			}
		}
	}

	privateNetworkFreeBlocks.WithLabelValues(provider, productName).Set(float64(freeBlocks))
	privateNetworkReclaimPending.WithLabelValues(provider, productName).Set(float64(pendingBlocks))
}

func privateNetworkReclaimCandidates(
	provider string,
	productName string,
	productCategory string,
	dueOnly bool,
	lastUsedThreshold time.Time,
	limit int,
) []privateNetworkReclaimCandidate {
	if limit <= 0 {
		return nil
	}

	return db.NewTx(func(tx *db.Transaction) []privateNetworkReclaimCandidate {
		return db.Select[privateNetworkReclaimCandidate](
			tx,
			`
				select
					pn.resource,
					pn.name,
					pn.subdomain,
					pn.last_used_at,
					pn.reclaim_after
				from
					app_orchestrator.private_networks pn
					join provider.resource r on r.id = pn.resource
					join accounting.products p on r.product = p.id
					join accounting.product_categories pc on p.category = pc.id
				where
					r.provider = :provider
					and p.name = :product_name
					and pc.category = :product_category
					and pn.network_type = 'infiniband'
					and pn.cidr_block is not null
					and not pn.reclaim_protection
					and pn.last_used_at < :last_used_threshold
					and (
						(cast(:due_only as bool) = false and pn.reclaim_state = 'active')
						or (
							cast(:due_only as bool) = true
							and (
								(
									pn.reclaim_state = 'pending'
									and pn.reclaim_after is not null
									and pn.reclaim_after <= now()
								)
								or pn.reclaim_state = 'reclaiming'
							)
						)
					)
					and not exists (
						select 1
						from app_orchestrator.jobs j
						where
							j.resource = any(pn.members)
							and j.current_state in ('IN_QUEUE', 'RUNNING', 'SUSPENDED')
					)
					and not exists (
						select 1
						from app_orchestrator.private_network_ips ip
						where ip.network = pn.resource
					)
				order by pn.last_used_at asc
				limit :limit
			`,
			db.Params{
				"provider":            provider,
				"product_name":        productName,
				"product_category":    productCategory,
				"due_only":            dueOnly,
				"last_used_threshold": lastUsedThreshold,
				"limit":               limit,
			},
		)
	})
}

func privateNetworkReclaimCounts(provider string, productName string, productCategory string) (usedBlocks int, pendingBlocks int) {
	return db.NewTx2(func(tx *db.Transaction) (int, int) {
		row, ok := db.Get[struct {
			UsedBlocks    int
			PendingBlocks int
		}](
			tx,
			`
				select
					count(*) as used_blocks,
					count(*) filter (where pn.reclaim_state in ('pending', 'reclaiming')) as pending_blocks
				from
					app_orchestrator.private_networks pn
					join provider.resource r on r.id = pn.resource
					join accounting.products p on r.product = p.id
					join accounting.product_categories pc on p.category = pc.id
				where
					r.provider = :provider
					and p.name = :product_name
					and pc.category = :product_category
					and pn.network_type = 'infiniband'
			`,
			db.Params{
				"provider":         provider,
				"product_name":     productName,
				"product_category": productCategory,
			},
		)
		if !ok {
			return 0, 0
		}
		return row.UsedBlocks, row.PendingBlocks
	})
}

func privateNetworkReclaimMarkPending(
	networkId string,
	support orcapi.PrivateNetworkSupport,
	policy orcapi.ReclaimPolicy,
	lastUsedThreshold time.Time,
) bool {
	mutationLock := networkMutationLock(networkId)
	mutationLock.Lock()
	defer mutationLock.Unlock()

	network, _, _, err := ResourceRetrieveEx[orcapi.PrivateNetwork](
		rpc.ActorSystem,
		privateNetworkType,
		ResourceParseId(networkId),
		orcapi.PermissionRead,
		orcapi.ResourceFlags{},
	)
	if err != nil || !privateNetworkReclaimCandidateCheck(network, support, lastUsedThreshold) {
		return false
	}
	if network.Status.ReclaimState != orcapi.NetworkReclaimActive {
		return false
	}

	reclaimAfter := fndapi.Timestamp(time.Now().Add(policy.GracePeriod))
	if !privateNetworkNotifyReclaimPending(network, reclaimAfter) {
		log.Warn("Failed to notify the owner of private network %s about a pending reclaim", networkId)
		return false
	}

	ok := ResourceUpdate[orcapi.PrivateNetwork](
		rpc.ActorSystem,
		privateNetworkType,
		ResourceParseId(networkId),
		orcapi.PermissionRead,
		func(r *resource, mapped orcapi.PrivateNetwork) {
			internal := r.Extra.(*internalPrivateNetwork)
			internal.ReclaimState = orcapi.NetworkReclaimPending
			internal.ReclaimAfter = util.OptValue(reclaimAfter)
		},
	)
	if ok {
		privateNetworkReclaimFailuresReset(networkId)
	}
	return ok
}

func privateNetworkReclaimDue(
	networkId string,
	support orcapi.PrivateNetworkSupport,
	policy orcapi.ReclaimPolicy,
	lastUsedThreshold time.Time,
) bool {
	mutationLock := networkMutationLock(networkId)
	mutationLock.Lock()
	defer mutationLock.Unlock()

	network, _, _, err := ResourceRetrieveEx[orcapi.PrivateNetwork](
		rpc.ActorSystem,
		privateNetworkType,
		ResourceParseId(networkId),
		orcapi.PermissionRead,
		orcapi.ResourceFlags{},
	)
	if err != nil {
		return false
	}
	if network.Status.ReclaimState != orcapi.NetworkReclaimPending &&
		network.Status.ReclaimState != orcapi.NetworkReclaiming {
		return false
	}
	if network.Status.ReclaimState == orcapi.NetworkReclaimPending {
		if !network.Status.ReclaimAfter.Present || network.Status.ReclaimAfter.Value.Time().After(time.Now()) {
			return false
		}
	}

	if !privateNetworkReclaimCandidateCheck(network, support, lastUsedThreshold) {
		privateNetworkReclaimSetActiveLocked(networkId)
		return false
	}

	privateNetworkPruneStaleMembersLocked(networkId)

	network, _, _, err = ResourceRetrieveEx[orcapi.PrivateNetwork](
		rpc.ActorSystem,
		privateNetworkType,
		ResourceParseId(networkId),
		orcapi.PermissionRead,
		orcapi.ResourceFlags{},
	)
	if err != nil || !privateNetworkReclaimCandidateCheck(network, support, lastUsedThreshold) {
		privateNetworkReclaimSetActiveLocked(networkId)
		return false
	}

	ownerActor, ok := rpc.LookupActor(network.Owner.CreatedBy)
	if !ok {
		log.Error("Failed to resolve the owner of private network %s, skipping reclaim", networkId)
		privateNetworkReclaimSetActiveLocked(networkId)
		return false
	}

	ok = ResourceUpdate[orcapi.PrivateNetwork](
		rpc.ActorSystem,
		privateNetworkType,
		ResourceParseId(networkId),
		orcapi.PermissionRead,
		func(r *resource, mapped orcapi.PrivateNetwork) {
			internal := r.Extra.(*internalPrivateNetwork)
			internal.ReclaimState = orcapi.NetworkReclaiming
			internal.ReclaimAfter = util.OptNone[fndapi.Timestamp]()
		},
	)
	if !ok {
		return false
	}

	err = privateNetworkDeleteOne(ownerActor, fndapi.FindByStringId{Id: networkId})
	if err != nil {
		failures := privateNetworkReclaimFailuresIncrement(networkId)
		if failures >= 3 {
			log.Error("Failed to reclaim private network %s after %d attempts: %s", networkId, failures, err.Error())
		} else {
			log.Warn("Failed to reclaim private network %s: %s", networkId, err.Error())
		}
		privateNetworkReclaimMarkFailedLocked(networkId)
		return false
	}

	privateNetworkReclaimFailuresReset(networkId)
	privateNetworkReclaimedTotal.WithLabelValues(
		network.Specification.Product.Provider,
		network.Specification.Product.Id,
	).Inc()
	log.Info(
		"Reclaimed private network %s (%s) because the provider was below the low watermark. Last used at %s",
		networkId,
		network.Specification.Name,
		network.Status.LastUsedAt.Time().UTC().Format(time.RFC3339),
	)
	return true
}

func privateNetworkReclaimCandidateCheck(
	network orcapi.PrivateNetwork,
	support orcapi.PrivateNetworkSupport,
	lastUsedThreshold time.Time,
) bool {
	if network.Specification.EffectiveType() != orcapi.NetworkTypeInfiniband {
		return false
	}
	if support.EffectiveType() != orcapi.NetworkTypeInfiniband {
		return false
	}
	if network.Specification.ReclaimProtection {
		return false
	}
	if !network.Status.CidrBlock.Present {
		return false
	}
	if !network.Status.LastUsedAt.Time().Before(lastUsedThreshold) {
		return false
	}
	if privateNetworkHasActiveJobs(network.Status.Members) {
		return false
	}
	if privateNetworkHasReservedIps(network.Id) {
		return false
	}
	if serviceReferencedByNetwork(ResourceParseId(network.Id)) {
		return false
	}
	return true
}

func privateNetworkHasActiveJobs(members []string) bool {
	ids := make([]int64, 0, len(members))
	for _, member := range members {
		id, err := strconv.ParseInt(member, 10, 64)
		if err == nil {
			ids = append(ids, id)
		}
	}
	if len(ids) == 0 {
		return false
	}

	return db.NewTx(func(tx *db.Transaction) bool {
		row, ok := db.Get[struct{ Count int }](
			tx,
			`
				select count(*) as count
				from app_orchestrator.jobs
				where
					resource = some(:ids::int8[])
					and current_state in ('IN_QUEUE', 'RUNNING', 'SUSPENDED')
			`,
			db.Params{
				"ids": ids,
			},
		)
		return ok && row.Count > 0
	})
}

func privateNetworkHasReservedIps(networkId string) bool {
	return db.NewTx(func(tx *db.Transaction) bool {
		row, ok := db.Get[struct{ Count int }](
			tx,
			`
				select count(*) as count
				from app_orchestrator.private_network_ips
				where network = :network
			`,
			db.Params{
				"network": ResourceParseId(networkId),
			},
		)
		return ok && row.Count > 0
	})
}

func privateNetworkPruneStaleMembersLocked(networkId string) {
	network, _, _, err := ResourceRetrieveEx[orcapi.PrivateNetwork](
		rpc.ActorSystem,
		privateNetworkType,
		ResourceParseId(networkId),
		orcapi.PermissionRead,
		orcapi.ResourceFlags{},
	)
	if err != nil {
		return
	}

	var staleMembers []string
	for _, jobId := range network.Status.Members {
		job, _, _, jobErr := ResourceRetrieveEx[orcapi.Job](
			rpc.ActorSystem,
			jobType,
			ResourceParseId(jobId),
			orcapi.PermissionRead,
			orcapi.ResourceFlags{},
		)
		if jobErr != nil || job.Status.State.IsFinal() {
			staleMembers = append(staleMembers, jobId)
		}
	}

	if len(staleMembers) == 0 {
		return
	}

	ResourceUpdate[orcapi.PrivateNetwork](
		rpc.ActorSystem,
		privateNetworkType,
		ResourceParseId(networkId),
		orcapi.PermissionRead,
		func(r *resource, mapped orcapi.PrivateNetwork) {
			internal := r.Extra.(*internalPrivateNetwork)
			for _, jobId := range staleMembers {
				internal.Members = util.RemoveFirst(internal.Members, jobId)
			}
		},
	)
}

func privateNetworkReclaimSetActiveLocked(networkId string) {
	ResourceUpdate[orcapi.PrivateNetwork](
		rpc.ActorSystem,
		privateNetworkType,
		ResourceParseId(networkId),
		orcapi.PermissionRead,
		func(r *resource, mapped orcapi.PrivateNetwork) {
			internal := r.Extra.(*internalPrivateNetwork)
			internal.ReclaimState = orcapi.NetworkReclaimActive
			internal.ReclaimAfter = util.OptNone[fndapi.Timestamp]()
		},
	)
	privateNetworkReclaimFailuresReset(networkId)
}

func privateNetworkReclaimMarkFailedLocked(networkId string) {
	ResourceUpdate[orcapi.PrivateNetwork](
		rpc.ActorSystem,
		privateNetworkType,
		ResourceParseId(networkId),
		orcapi.PermissionRead,
		func(r *resource, mapped orcapi.PrivateNetwork) {
			internal := r.Extra.(*internalPrivateNetwork)
			internal.ReclaimState = orcapi.NetworkReclaimPending
			internal.ReclaimAfter = util.OptValue(fndapi.Timestamp(time.Now()))
		},
	)
}

func privateNetworkReclaimCancelPending(provider string, productName string, productCategory string) {
	ids := db.NewTx(func(tx *db.Transaction) []string {
		rows := db.Select[struct{ Resource int64 }](
			tx,
			`
				select pn.resource
				from
					app_orchestrator.private_networks pn
					join provider.resource r on r.id = pn.resource
					join accounting.products p on r.product = p.id
					join accounting.product_categories pc on p.category = pc.id
				where
					r.provider = :provider
					and p.name = :product_name
					and pc.category = :product_category
					and pn.network_type = 'infiniband'
					and pn.reclaim_state in ('pending', 'reclaiming')
			`,
			db.Params{
				"provider":         provider,
				"product_name":     productName,
				"product_category": productCategory,
			},
		)

		result := make([]string, 0, len(rows))
		for _, row := range rows {
			result = append(result, fmt.Sprint(row.Resource))
		}
		return result
	})

	for _, networkId := range ids {
		mutationLock := networkMutationLock(networkId)
		mutationLock.Lock()
		privateNetworkReclaimSetActiveLocked(networkId)
		mutationLock.Unlock()
	}
}

func privateNetworkNotifyReclaimPending(network orcapi.PrivateNetwork, reclaimAfter fndapi.Timestamp) bool {
	recipients := privateNetworkReclaimRecipients(network.Owner)
	if len(recipients) == 0 {
		return false
	}

	meta, _ := json.Marshal(map[string]any{
		"networkId":    network.Id,
		"networkName":  network.Specification.Name,
		"reclaimAfter": reclaimAfter.UnixMilli(),
	})

	message := fmt.Sprintf(
		"Network %q is scheduled for deletion at %s unless it is used or protected.",
		network.Specification.Name,
		reclaimAfter.Time().UTC().Format(time.RFC3339),
	)

	request := fndapi.BulkRequest[fndapi.NotificationsCreateRequest]{}
	for _, recipient := range recipients {
		notification := fndapi.Notification{
			Type:    "NETWORK_RECLAIM_PENDING",
			Message: message,
		}
		notification.Meta.Set(meta)
		request.Items = append(request.Items, fndapi.NotificationsCreateRequest{
			User:         recipient,
			Notification: notification,
		})
	}

	_, err := fndapi.NotificationsCreateBulk.Invoke(request)
	if err != nil {
		log.Warn("Failed to create private network reclaim notifications: %s", err.Error())
		return false
	}
	return true
}

func privateNetworkReclaimRecipients(owner orcapi.ResourceOwner) []string {
	recipients := map[string]util.Empty{}
	if owner.CreatedBy != "" {
		recipients[owner.CreatedBy] = util.Empty{}
	}

	if owner.Project.Present {
		members := db.NewTx(func(tx *db.Transaction) []struct {
			Username string
			Role     string
		} {
			return db.Select[struct {
				Username string
				Role     string
			}](
				tx,
				`
					select username, role
					from project.project_members
					where project_id = :project
				`,
				db.Params{
					"project": owner.Project.Value,
				},
			)
		})

		for _, member := range members {
			role := fndapi.ProjectRole(member.Role)
			if role.Satisfies(fndapi.ProjectRoleAdmin) {
				recipients[member.Username] = util.Empty{}
			}
		}
	}

	var result []string
	for username := range recipients {
		result = append(result, username)
	}
	return result
}

func privateNetworkReclaimBlockCount(ranges []string) int {
	result := 0
	for _, value := range ranges {
		prefix, ok := orcapi.PrivateNetworkParseCidr(value)
		if !ok {
			continue
		}
		if prefix.Bits() > orcapi.PrivateNetworkInfinibandPrefixLength {
			continue
		}
		result += 1 << (orcapi.PrivateNetworkInfinibandPrefixLength - prefix.Bits())
	}
	return result
}

func privateNetworkReclaimFailuresIncrement(networkId string) int {
	privateNetworkReclaimFailures.Mu.Lock()
	defer privateNetworkReclaimFailures.Mu.Unlock()

	if privateNetworkReclaimFailures.Counts == nil {
		privateNetworkReclaimFailures.Counts = map[string]int{}
	}
	privateNetworkReclaimFailures.Counts[networkId]++
	return privateNetworkReclaimFailures.Counts[networkId]
}

func privateNetworkReclaimFailuresReset(networkId string) {
	privateNetworkReclaimFailures.Mu.Lock()
	defer privateNetworkReclaimFailures.Mu.Unlock()

	if privateNetworkReclaimFailures.Counts != nil {
		delete(privateNetworkReclaimFailures.Counts, networkId)
	}
}

var (
	privateNetworkReclaimPending = promauto.NewGaugeVec(prometheus.GaugeOpts{
		Namespace: "ucloud",
		Subsystem: "orchestrator",
		Name:      "private_network_reclaim_pending",
		Help:      "The number of InfiniBand networks pending reclaim per provider and product",
	}, []string{"provider", "product"})

	privateNetworkReclaimedTotal = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "ucloud",
		Subsystem: "orchestrator",
		Name:      "private_network_reclaimed_total",
		Help:      "The number of InfiniBand networks successfully reclaimed per provider and product",
	}, []string{"provider", "product"})

	privateNetworkFreeBlocks = promauto.NewGaugeVec(prometheus.GaugeOpts{
		Namespace: "ucloud",
		Subsystem: "orchestrator",
		Name:      "private_network_free_blocks",
		Help:      "The number of free InfiniBand /24 blocks per provider and product",
	}, []string{"provider", "product"})
)
