package accounting

import (
	"fmt"

	db "ucloud.dk/shared/pkg/database"
)

func accountingUpgradePersistFlow(tx *db.Transaction, bucket *internalBucket) {
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
			panic(fmt.Sprintf("wallet %d derived values overflow after upgrade: %v", wallet.Id, overflows))
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
			"id":                         walletIds,
			"local_usage":                localUsage,
			"excess_usage":               excessUsage,
			"total_allocated":            totalAllocated,
			"total_retired_allocated":    totalRetiredAllocated,
			"was_locked":                 wasLocked,
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
			db.Params{
				"id":         groupIds,
				"tree_usage": treeUsage,
			},
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
			db.Params{
				"id":            allocationIds,
				"quota":         quota,
				"retired":       retired,
				"retired_usage": retiredUsage,
				"retired_quota": retiredQuota,
			},
		)
	}
}
