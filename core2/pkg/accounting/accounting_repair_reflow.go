package accounting

import (
	"cmp"
	"fmt"
	"slices"
	"strings"
	"time"

	"gopkg.in/yaml.v3"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/log"
)

const accountingUnscopedRepairKey = "__ucloud_unscoped__"

type accountingReflowMode string

const (
	accountingReflowNoScopes  accountingReflowMode = "no-scopes"
	accountingReflowAllScoped accountingReflowMode = "all-scoped"
	accountingReflowMixed     accountingReflowMode = "mixed"
)

type accountingReflowEvent struct {
	At       time.Time   `yaml:"at"`
	WalletId AccWalletId `yaml:"walletId"`
	Key      string      `yaml:"key"`
	Usage    int64       `yaml:"usage"`
}

type accountingReflowGrantQuota struct {
	GrantId        int64
	GrantGiver     string
	RevisionNumber int
	Quota          int64
}

type accountingReflowRetiredQuotaRecovery struct {
	AllocationId int64  `yaml:"allocationId"`
	GrantId      int64  `yaml:"grantId"`
	GrantGiver   string `yaml:"grantGiver"`
	Revision     int    `yaml:"revision"`
	Quota        int64  `yaml:"quota"`
}

type accountingReflowReport struct {
	Applied              bool                                   `yaml:"applied"`
	Provider             string                                 `yaml:"provider"`
	Category             string                                 `yaml:"category"`
	Mode                 accountingReflowMode                   `yaml:"mode"`
	RepairAt             time.Time                              `yaml:"repairAt"`
	Wallets              int                                    `yaml:"wallets"`
	Groups               int                                    `yaml:"groups"`
	Allocations          int                                    `yaml:"allocations"`
	Events               []accountingReflowEvent                `yaml:"events"`
	SyntheticScopes      int                                    `yaml:"syntheticScopes"`
	QuotaRecoveries      []accountingReflowRetiredQuotaRecovery `yaml:"retiredQuotaRecoveries"`
	IgnoredCapacityQuota []int64                                `yaml:"ignoredCapacityRetiredQuotaAllocations"`
	Blocked              bool                                   `yaml:"blocked"`
	Blockers             []string                               `yaml:"blockers,omitempty"`
}

func RepairAccountingBucketReflow(provider, category, modeValue string, apply bool) []byte {
	mode := accountingReflowMode(modeValue)
	if mode != accountingReflowNoScopes && mode != accountingReflowAllScoped && mode != accountingReflowMixed {
		panic(fmt.Sprintf("invalid usage source %q; expected no-scopes, all-scoped, or mixed", modeValue))
	}

	report := db.NewTx(func(tx *db.Transaction) accountingReflowReport {
		tx.NoDevResetThisIsNotAHackIPromise = true
		if apply {
			db.Exec(
				tx,
				"set transaction isolation level serializable",
				db.Params{},
			)
			db.Exec(
				tx,
				`
					lock table accounting.wallets_v2, accounting.allocation_groups,
						accounting.wallet_allocations_v2, accounting.scoped_usage in access exclusive mode
				`,
				db.Params{},
			)
		} else {
			db.Exec(
				tx,
				"set transaction isolation level repeatable read, read only",
				db.Params{},
			)
		}

		nowRow, ok := db.Get[struct{ Now time.Time }](
			tx,
			"select transaction_timestamp() as now",
			db.Params{},
		)
		if !ok {
			panic("could not retrieve repair transaction time")
		}
		result := accountingReflowReport{
			Provider:             provider,
			Category:             category,
			Mode:                 mode,
			RepairAt:             nowRow.Now,
			Events:               []accountingReflowEvent{},
			QuotaRecoveries:      []accountingReflowRetiredQuotaRecovery{},
			IgnoredCapacityQuota: []int64{},
		}

		productsLoadFromTx(tx)
		var loadErrors []string
		accountingLoadFromTx(tx, nowRow.Now, func(message string) {
			loadErrors = append(loadErrors, message)
		})
		if len(loadErrors) > 0 {
			result.Blocked = true
			result.Blockers = append(result.Blockers, loadErrors...)
			return result
		}

		bucket := accGlobals.BucketsByCategory[accapi.ProductCategoryIdV2{Name: category, Provider: provider}]
		if bucket == nil {
			result.Blocked = true
			result.Blockers = append(result.Blockers, fmt.Sprintf("bucket %s/%s does not exist", provider, category))
			return result
		}
		result.Wallets = len(bucket.WalletsById)
		result.Allocations = len(bucket.AllocationsById)
		for _, wallet := range bucket.WalletsById {
			result.Groups += len(wallet.AllocationsByParent)
		}
		for _, err := range lValidateAccountingAcyclic(bucket) {
			result.Blockers = append(result.Blockers, err.Error())
		}
		grantQuotas := selectAccountingReflowGrantQuotas(tx, bucket)
		var quotaBlockers []string
		result.QuotaRecoveries, result.IgnoredCapacityQuota, quotaBlockers = recoverAccountingReflowRetiredQuotas(bucket, grantQuotas)
		result.Blockers = append(result.Blockers, quotaBlockers...)

		walletIds := make([]AccWalletId, 0, len(bucket.WalletsById))
		for walletId := range bucket.WalletsById {
			walletIds = append(walletIds, walletId)
		}
		slices.Sort(walletIds)
		for _, walletId := range walletIds {
			wallet := bucket.WalletsById[walletId]
			if wallet.LocalUsage < 0 {
				result.Blockers = append(result.Blockers, fmt.Sprintf("wallet %d has negative local usage %d", walletId, wallet.LocalUsage))
			}
			scopeKeys := make([]string, 0, len(wallet.ScopedUsage))
			for key := range wallet.ScopedUsage {
				scopeKeys = append(scopeKeys, key)
			}
			slices.Sort(scopeKeys)

			scopedTotal := int64(0)
			for _, key := range scopeKeys {
				scope := wallet.ScopedUsage[key]
				if scope.Usage < 0 {
					result.Blockers = append(result.Blockers, fmt.Sprintf("wallet %d scope %q has negative usage %d", walletId, key, scope.Usage))
					continue
				}
				if scope.LastUpdatedAt.After(nowRow.Now) {
					result.Blockers = append(result.Blockers, fmt.Sprintf("wallet %d scope %q was updated in the future at %s", walletId, key, scope.LastUpdatedAt.UTC().Format(time.RFC3339Nano)))
				}
				var overflow bool
				scopedTotal, overflow = checkedAccountingAdd(scopedTotal, scope.Usage)
				if overflow {
					result.Blockers = append(result.Blockers, fmt.Sprintf("wallet %d scoped usage sum overflows int64", walletId))
					break
				}
				if mode != accountingReflowNoScopes {
					result.Events = append(result.Events, accountingReflowEvent{At: scope.LastUpdatedAt, WalletId: walletId, Key: key, Usage: scope.Usage})
				}
			}

			switch mode {
			case accountingReflowNoScopes:
				if len(scopeKeys) != 0 {
					result.Blockers = append(result.Blockers, fmt.Sprintf("wallet %d has %d scoped usage entries", walletId, len(scopeKeys)))
				}
				result.Events = append(result.Events, accountingReflowEvent{At: nowRow.Now, WalletId: walletId, Key: accountingUnscopedRepairKey, Usage: wallet.LocalUsage})
			case accountingReflowAllScoped:
				// The replayed events replace the persisted local usage.
			case accountingReflowMixed:
				if _, exists := wallet.ScopedUsage[accountingUnscopedRepairKey]; exists {
					result.Blockers = append(result.Blockers, fmt.Sprintf("wallet %d already has reserved scope %q", walletId, accountingUnscopedRepairKey))
					continue
				}
				unscoped, overflow := checkedAccountingSub(wallet.LocalUsage, scopedTotal)
				if overflow || unscoped < 0 {
					result.Blockers = append(result.Blockers, fmt.Sprintf("wallet %d local usage %d is below scoped usage sum %d", walletId, wallet.LocalUsage, scopedTotal))
					continue
				}
				if unscoped != 0 {
					result.Events = append(result.Events, accountingReflowEvent{At: nowRow.Now, WalletId: walletId, Key: accountingUnscopedRepairKey, Usage: unscoped})
					result.SyntheticScopes++
				}
			}
		}

		for _, allocation := range bucket.AllocationsById {
			if allocation.Start.After(allocation.End) {
				result.Blockers = append(result.Blockers, fmt.Sprintf("allocation %d has invalid interval [%s, %s)", allocation.Id, allocation.Start, allocation.End))
			}
		}
		if len(result.Blockers) > 0 {
			result.Blocked = true
			return result
		}

		slices.SortFunc(result.Events, func(a, b accountingReflowEvent) int {
			if order := a.At.Compare(b.At); order != 0 {
				return order
			}
			if a.WalletId != b.WalletId {
				return cmp.Compare(a.WalletId, b.WalletId)
			}
			return strings.Compare(a.Key, b.Key)
		})
		replayAccountingBucket(bucket, result.Events, nowRow.Now)
		allWallets := make(map[AccWalletId]bool, len(bucket.WalletsById))
		for walletId := range bucket.WalletsById {
			allWallets[walletId] = true
		}
		lInternalReevaluateAffected(bucket, nowRow.Now, allWallets)
		if err := lValidateAccountingTree(bucket, nowRow.Now); err != nil {
			result.Blocked = true
			result.Blockers = append(result.Blockers, err.Error())
			return result
		}
		if !apply {
			return result
		}

		persistAccountingReflow(tx, bucket, result.Events, mode, nowRow.Now)
		result.Applied = true
		return result
	})

	encoded, err := yaml.Marshal(report)
	if err != nil {
		panic(err)
	}
	return encoded
}

func selectAccountingReflowGrantQuotas(tx *db.Transaction, bucket *internalBucket) map[int64][]accountingReflowGrantQuota {
	grantIds := make([]int64, 0)
	seen := map[int64]bool{}
	for _, allocation := range bucket.AllocationsById {
		if !allocation.Retired || allocation.RetiredQuota > 0 || !allocation.GrantedIn.Present {
			continue
		}
		grantId := int64(allocation.GrantedIn.Value)
		if !seen[grantId] {
			seen[grantId] = true
			grantIds = append(grantIds, grantId)
		}
	}
	if len(grantIds) == 0 {
		return map[int64][]accountingReflowGrantQuota{}
	}
	slices.Sort(grantIds)
	rows := db.Select[accountingReflowGrantQuota](
		tx,
		`
			select rr.application_id as grant_id, coalesce(rr.grant_giver, '') as grant_giver,
				rr.revision_number, coalesce(rr.credits_requested, rr.quota_requested_bytes, 0) as quota
			from "grant".requested_resources rr
			join accounting.product_categories pc on pc.id = rr.product_category
			where rr.application_id = any(cast(:grant_ids as int8[]))
				and pc.provider = :provider and pc.category = :category
			order by rr.application_id, rr.revision_number desc, rr.grant_giver
		`,
		db.Params{
			"grant_ids": grantIds,
			"provider":  bucket.Category.Provider,
			"category":  bucket.Category.Name,
		},
	)
	result := make(map[int64][]accountingReflowGrantQuota)
	for _, row := range rows {
		result[row.GrantId] = append(result[row.GrantId], row)
	}
	return result
}

func accountingReflowAllocationGrantGiver(bucket *internalBucket, allocation *internalAllocation) string {
	if allocation.Parent == internalGraphRoot {
		return ""
	}
	parent := bucket.WalletsById[allocation.Parent]
	if parent == nil {
		return ""
	}
	owner := accGlobals.OwnersById[parent.OwnedBy]
	if owner == nil {
		return ""
	}
	return owner.Reference
}

func recoverAccountingReflowRetiredQuotas(bucket *internalBucket, grantQuotas map[int64][]accountingReflowGrantQuota) ([]accountingReflowRetiredQuotaRecovery, []int64, []string) {
	allocationIds := make([]accAllocId, 0, len(bucket.AllocationsById))
	for allocationId := range bucket.AllocationsById {
		allocationIds = append(allocationIds, allocationId)
	}
	slices.Sort(allocationIds)
	var recoveries []accountingReflowRetiredQuotaRecovery
	var ignoredCapacity []int64
	var blockers []string
	for _, allocationId := range allocationIds {
		allocation := bucket.AllocationsById[allocationId]
		if !allocation.Retired || allocation.RetiredQuota > 0 {
			continue
		}
		grantGiver := accountingReflowAllocationGrantGiver(bucket, allocation)
		grantId := int64(allocation.GrantedIn.GetOrDefault(0))
		recovered, ok := resolveAccountingReflowGrantQuota(grantQuotas[grantId], grantGiver)
		if ok {
			allocation.RetiredQuota = recovered.Quota
			recoveries = append(recoveries, accountingReflowRetiredQuotaRecovery{
				AllocationId: int64(allocation.Id), GrantId: grantId,
				GrantGiver: recovered.GrantGiver, Revision: recovered.RevisionNumber, Quota: recovered.Quota,
			})
		} else if bucket.IsCapacityBased() {
			ignoredCapacity = append(ignoredCapacity, int64(allocation.Id))
		} else if allocation.GrantedIn.Present {
			blockers = append(blockers, fmt.Sprintf("retired allocation %d cannot be rewound from retired quota %d and grant application %d has no unambiguous positive request for %s/%s from %q", allocation.Id, allocation.RetiredQuota, grantId, bucket.Category.Provider, bucket.Category.Name, grantGiver))
		} else {
			blockers = append(blockers, fmt.Sprintf("retired allocation %d cannot be rewound from retired quota %d and has no grant application", allocation.Id, allocation.RetiredQuota))
		}
	}
	return recoveries, ignoredCapacity, blockers
}

func resolveAccountingReflowGrantQuota(candidates []accountingReflowGrantQuota, grantGiver string) (accountingReflowGrantQuota, bool) {
	eligible := make([]accountingReflowGrantQuota, 0, len(candidates))
	for _, candidate := range candidates {
		if candidate.Quota > 0 && (grantGiver == "" || candidate.GrantGiver == grantGiver) {
			eligible = append(eligible, candidate)
		}
	}
	if len(eligible) == 0 {
		return accountingReflowGrantQuota{}, false
	}
	latestRevision := eligible[0].RevisionNumber
	for _, candidate := range eligible[1:] {
		latestRevision = max(latestRevision, candidate.RevisionNumber)
	}
	var result accountingReflowGrantQuota
	found := false
	for _, candidate := range eligible {
		if candidate.RevisionNumber != latestRevision {
			continue
		}
		if !found {
			result = candidate
			found = true
			continue
		}
		if candidate.Quota != result.Quota || grantGiver == "" && candidate.GrantGiver != result.GrantGiver {
			return accountingReflowGrantQuota{}, false
		}
	}
	return result, found
}

func resetAccountingBucketForReplay(bucket *internalBucket) {
	for _, wallet := range bucket.WalletsById {
		wallet.LocalUsage = 0
		wallet.WasLocked = true
		for childId := range wallet.ChildrenUsage {
			wallet.ChildrenUsage[childId] = 0
		}
		for _, group := range wallet.AllocationsByParent {
			group.TreeUsage = 0
		}
	}
	for _, allocation := range bucket.AllocationsById {
		if allocation.Retired {
			allocation.Quota = allocation.RetiredQuota
		}
		allocation.Active = false
		allocation.Retired = false
		allocation.RetiredUsage = 0
		allocation.RetiredQuota = 0
	}
}

type accountingReplayAllocationTransitions struct {
	allocations  []*internalAllocation
	boundaries   []time.Time
	nextBoundary int
	initialized  bool
}

func newAccountingReplayAllocationTransitions(bucket *internalBucket) *accountingReplayAllocationTransitions {
	result := &accountingReplayAllocationTransitions{
		allocations: make([]*internalAllocation, 0, len(bucket.AllocationsById)),
		boundaries:  make([]time.Time, 0, len(bucket.AllocationsById)*2),
	}
	for _, allocation := range bucket.AllocationsById {
		result.allocations = append(result.allocations, allocation)
		result.boundaries = append(result.boundaries, allocation.Start, allocation.End)
	}
	lInternalSortAllocations(result.allocations)
	slices.SortFunc(result.boundaries, func(a, b time.Time) int { return a.Compare(b) })
	return result
}

func (transitions *accountingReplayAllocationTransitions) transition(bucket *internalBucket, now time.Time) {
	if transitions.initialized && (transitions.nextBoundary == len(transitions.boundaries) || now.Before(transitions.boundaries[transitions.nextBoundary])) {
		return
	}

	lInternalTransitionSortedAllocationSet(bucket, now, false, transitions.allocations)
	transitions.initialized = true
	for transitions.nextBoundary < len(transitions.boundaries) && !now.Before(transitions.boundaries[transitions.nextBoundary]) {
		transitions.nextBoundary++
	}
}

func replayAccountingBucket(bucket *internalBucket, events []accountingReflowEvent, now time.Time) {
	resetAccountingBucketForReplay(bucket)
	transitions := newAccountingReplayAllocationTransitions(bucket)
	for i, event := range events {
		if i%1000 == 0 {
			log.Info("Replaying event %v of %v", i, len(events))
		}
		transitions.transition(bucket, event.At)
		wallet := bucket.WalletsById[event.WalletId]
		if event.Usage != 0 {
			lInternalReportUsage(bucket, event.At, wallet, event.Usage)
			wallet.LocalUsage += event.Usage
		}
	}
	log.Info("Done replaying events")
	transitions.transition(bucket, now)
}

func persistAccountingReflow(tx *db.Transaction, bucket *internalBucket, events []accountingReflowEvent, mode accountingReflowMode, now time.Time) {
	walletIds := make([]int64, 0, len(bucket.WalletsById))
	localUsage := make([]int64, 0, len(bucket.WalletsById))
	excessUsage := make([]int64, 0, len(bucket.WalletsById))
	totalAllocated := make([]int64, 0, len(bucket.WalletsById))
	totalRetiredAllocated := make([]int64, 0, len(bucket.WalletsById))
	wasLocked := make([]bool, 0, len(bucket.WalletsById))
	lastSignificantUpdateAt := make([]int64, 0, len(bucket.WalletsById))
	for _, wallet := range bucket.WalletsById {
		derived, overflows := recomputeSnapshotWalletValues(bucket, wallet)
		if len(overflows) != 0 {
			panic(fmt.Sprintf("wallet %d derived values overflow after reflow: %v", wallet.Id, overflows))
		}
		walletIds = append(walletIds, int64(wallet.Id))
		localUsage = append(localUsage, wallet.LocalUsage)
		excessUsage = append(excessUsage, derived.ExcessUsage)
		totalAllocated = append(totalAllocated, derived.TotalAllocated)
		totalRetiredAllocated = append(totalRetiredAllocated, derived.TotalRetiredAllocated)
		wasLocked = append(wasLocked, wallet.WasLocked)
		lastSignificantUpdateAt = append(lastSignificantUpdateAt, wallet.LastSignificantUpdate.UnixMilli())
	}
	db.Exec(
		tx,
		`
			with data as (
				select unnest(cast(:id as int8[])) as id,
					unnest(cast(:local_usage as int8[])) as local_usage,
					unnest(cast(:excess_usage as int8[])) as excess_usage,
					unnest(cast(:total_allocated as int8[])) as total_allocated,
					unnest(cast(:total_retired_allocated as int8[])) as total_retired_allocated,
					unnest(cast(:was_locked as bool[])) as was_locked,
					unnest(cast(:last_significant_update_at as int8[])) as last_significant_update_at
			)
			update accounting.wallets_v2 w set
				local_usage = data.local_usage,
				local_retired_usage = 0,
				excess_usage = data.excess_usage,
				total_allocated = data.total_allocated,
				total_retired_allocated = data.total_retired_allocated,
				was_locked = data.was_locked,
				last_significant_update_at = to_timestamp(data.last_significant_update_at / 1000.0)
			from data where w.id = data.id
		`,
		db.Params{
			"id": walletIds, "local_usage": localUsage, "excess_usage": excessUsage,
			"total_allocated": totalAllocated, "total_retired_allocated": totalRetiredAllocated, "was_locked": wasLocked,
			"last_significant_update_at": lastSignificantUpdateAt,
		},
	)

	var groupIds, treeUsage []int64
	for _, wallet := range bucket.WalletsById {
		for _, group := range wallet.AllocationsByParent {
			groupIds = append(groupIds, int64(group.Id))
			treeUsage = append(treeUsage, group.TreeUsage)
		}
	}
	if len(groupIds) > 0 {
		db.Exec(
			tx,
			`
				with data as (
					select unnest(cast(:id as int8[])) as id,
						unnest(cast(:tree_usage as int8[])) as tree_usage
				)
				update accounting.allocation_groups ag set tree_usage = data.tree_usage, retired_tree_usage = 0
				from data where ag.id = data.id
			`,
			db.Params{"id": groupIds, "tree_usage": treeUsage},
		)
	}

	var allocationIds, quota, retiredUsage, retiredQuota []int64
	var retired []bool
	for _, allocation := range bucket.AllocationsById {
		allocationIds = append(allocationIds, int64(allocation.Id))
		quota = append(quota, allocation.Quota)
		retired = append(retired, allocation.Retired)
		retiredUsage = append(retiredUsage, allocation.RetiredUsage)
		retiredQuota = append(retiredQuota, allocation.RetiredQuota)
	}
	if len(allocationIds) > 0 {
		db.Exec(
			tx,
			`
				with data as (
					select unnest(cast(:id as int8[])) as id,
						unnest(cast(:quota as int8[])) as quota,
						unnest(cast(:retired as bool[])) as retired,
						unnest(cast(:retired_usage as int8[])) as retired_usage,
						unnest(cast(:retired_quota as int8[])) as retired_quota
				)
				update accounting.wallet_allocations_v2 a set
					quota = data.quota, retired = data.retired,
					retired_usage = data.retired_usage, retired_quota = data.retired_quota
				from data where a.id = data.id
			`,
			db.Params{"id": allocationIds, "quota": quota, "retired": retired, "retired_usage": retiredUsage, "retired_quota": retiredQuota},
		)
	}

	if mode == accountingReflowMixed {
		for _, event := range events {
			if event.Key != accountingUnscopedRepairKey || event.Usage == 0 {
				continue
			}
			db.Exec(
				tx,
				`
					insert into accounting.scoped_usage(wallet_id, key, usage, last_updated_at)
					values (:wallet_id, :key, :usage, :updated_at)
				`,
				db.Params{"wallet_id": event.WalletId, "key": event.Key, "usage": event.Usage, "updated_at": now},
			)
		}
	}
}
