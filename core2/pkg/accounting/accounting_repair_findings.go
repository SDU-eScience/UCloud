package accounting

import (
	"cmp"
	"database/sql"
	"fmt"
	"slices"
	"time"

	"gopkg.in/yaml.v3"
	db "ucloud.dk/shared/pkg/database"
)

type findingsRepairGroupRow struct {
	Id               int64
	ParentWallet     sql.NullInt64
	AssociatedWallet int64
	TreeUsage        int64
}

type findingsRepairAllocationRow struct {
	Id                  int64
	GroupId             int64
	Quota               int64
	AllocationStartTime time.Time
	AllocationEndTime   time.Time
	Retired             bool
	RetiredUsage        int64
	RetiredQuota        int64
	AccountingFrequency string
}

type findingsRepairAllocationDeletion struct {
	Id      int64 `yaml:"id"`
	GroupId int64 `yaml:"groupId"`
}

type findingsRepairGroupDeletion struct {
	Id int64 `yaml:"id"`
}

type findingsRepairClamp struct {
	Kind  string `yaml:"kind"`
	Id    int64  `yaml:"id"`
	Key   string `yaml:"key,omitempty"`
	Field string `yaml:"field"`
	From  int64  `yaml:"from"`
	To    int64  `yaml:"to"`
}

type findingsRepairLifecycleChange struct {
	AllocationId int64  `yaml:"allocationId"`
	Code         string `yaml:"code"`
	Field        string `yaml:"field"`
	From         string `yaml:"from"`
	To           string `yaml:"to"`
	Reason       string `yaml:"reason"`
}

type findingsRepairAllocationUpdate struct {
	Id           int64
	Quota        int64
	Start        time.Time
	End          time.Time
	Retired      bool
	RetiredUsage int64
	RetiredQuota int64
}

type findingsRepairRecomputation struct {
	WalletId  int64  `yaml:"walletId"`
	Code      string `yaml:"code"`
	Field     string `yaml:"field"`
	From      string `yaml:"from"`
	To        string `yaml:"to"`
	IntValue  int64  `yaml:"-"`
	BoolValue bool   `yaml:"-"`
}

type findingsRepairWalletState struct {
	Id                    int64
	ExcessUsage           int64
	TotalAllocated        int64
	TotalRetiredAllocated int64
	WasLocked             bool
}

type findingsRepairPlan struct {
	AllocationDeletions []findingsRepairAllocationDeletion `yaml:"allocationDeletions"`
	GroupDeletions      []findingsRepairGroupDeletion      `yaml:"groupDeletions"`
	Clamps              []findingsRepairClamp              `yaml:"clamps"`
	LifecycleChanges    []findingsRepairLifecycleChange    `yaml:"lifecycleChanges"`
	Recomputations      []findingsRepairRecomputation      `yaml:"recomputations"`
	AllocationUpdates   []findingsRepairAllocationUpdate   `yaml:"-"`
}

type findingsRepairReport struct {
	Applied            bool `yaml:"applied"`
	findingsRepairPlan `yaml:",inline"`
	Blocked            bool     `yaml:"blocked"`
	Blockers           []string `yaml:"blockers,omitempty"`
}

func RepairAccountingFindings(apply bool) []byte {
	report := db.NewTx(func(tx *db.Transaction) findingsRepairReport {
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

		groups := selectFindingsRepairGroups(tx)
		allocations := selectFindingsRepairAllocations(tx)
		result := findingsRepairReport{
			findingsRepairPlan: planFindingsRepair(groups, allocations, nowRow.Now),
		}
		deletedGroups := map[int64]bool{}
		for _, deletion := range result.GroupDeletions {
			deletedGroups[deletion.Id] = true
		}
		for _, clamp := range selectFindingsRepairNonAllocationClamps(tx) {
			if clamp.Kind != "allocation-group" || !deletedGroups[clamp.Id] {
				result.Clamps = append(result.Clamps, clamp)
			}
		}
		result.Recomputations, result.Blockers = planFindingsRepairRecomputations(tx, result.findingsRepairPlan, nowRow.Now)
		result.Blocked = len(result.Blockers) > 0
		if result.Blocked {
			return result
		}
		if !apply {
			return result
		}

		allocationIds := make([]int64, 0, len(result.AllocationDeletions))
		for _, deletion := range result.AllocationDeletions {
			allocationIds = append(allocationIds, deletion.Id)
		}
		if len(allocationIds) > 0 {
			db.Exec(
				tx,
				`delete from accounting.wallet_allocations_v2 where id = any(cast(:ids as int8[]))`,
				db.Params{"ids": allocationIds},
			)
		}

		groupIds := make([]int64, 0, len(result.GroupDeletions))
		for _, deletion := range result.GroupDeletions {
			groupIds = append(groupIds, deletion.Id)
		}
		if len(groupIds) > 0 {
			db.Exec(
				tx,
				`delete from accounting.allocation_groups where id = any(cast(:ids as int8[]))`,
				db.Params{"ids": groupIds},
			)
		}

		persistFindingsRepairAllocationUpdates(tx, result.AllocationUpdates)
		persistFindingsRepairNonAllocationClamps(tx)
		persistFindingsRepairRecomputations(tx, result.Recomputations)

		remainingGroups := selectFindingsRepairGroups(tx)
		if len(findCyclicAccountingGroups(remainingGroups)) > 0 {
			panic("accounting findings repair left an allocation cycle")
		}
		if len(selectFindingsRepairNonAllocationClamps(tx)) > 0 {
			panic("accounting findings repair left a negative wallet, group, or scoped usage value")
		}
		remainingAllocations := selectFindingsRepairAllocations(tx)
		for _, allocation := range remainingAllocations {
			if allocation.Quota < 0 || allocation.RetiredUsage < 0 || allocation.RetiredQuota < 0 {
				panic(fmt.Sprintf("accounting findings repair left negative values on allocation %d", allocation.Id))
			}
			if allocation.AllocationStartTime.After(allocation.AllocationEndTime) {
				panic(fmt.Sprintf("accounting findings repair left an invalid interval on allocation %d", allocation.Id))
			}
			if !allocation.Retired && !nowRow.Now.Before(allocation.AllocationEndTime) {
				panic(fmt.Sprintf("accounting findings repair did not retire expired allocation %d", allocation.Id))
			}
		}
		if remainingPlan := planFindingsRepair(remainingGroups, remainingAllocations, nowRow.Now); len(remainingPlan.AllocationUpdates) > 0 {
			panic("accounting findings repair left stale allocation retirement state")
		}
		if remaining := selectFindingsRepairRecomputationDivergences(tx, nowRow.Now); len(remaining) > 0 {
			panic(fmt.Sprintf("accounting findings repair left %d derived wallet divergences", len(remaining)))
		}
		result.Applied = true
		return result
	})

	encoded, err := yaml.Marshal(report)
	if err != nil {
		panic(err)
	}
	return encoded
}

func selectFindingsRepairGroups(tx *db.Transaction) []findingsRepairGroupRow {
	return db.Select[findingsRepairGroupRow](
		tx,
		`
			select id, parent_wallet, associated_wallet, tree_usage
			from accounting.allocation_groups
			order by id
		`,
		db.Params{},
	)
}

func selectFindingsRepairAllocations(tx *db.Transaction) []findingsRepairAllocationRow {
	return db.Select[findingsRepairAllocationRow](
		tx,
		`
			select a.id, a.associated_allocation_group as group_id, a.quota,
				a.allocation_start_time, a.allocation_end_time, a.retired,
				a.retired_usage, a.retired_quota, pc.accounting_frequency
			from accounting.wallet_allocations_v2 a
			join accounting.allocation_groups ag on ag.id = a.associated_allocation_group
			join accounting.wallets_v2 w on w.id = ag.associated_wallet
			join accounting.product_categories pc on pc.id = w.product_category
			order by a.id
		`,
		db.Params{},
	)
}

func selectFindingsRepairNonAllocationClamps(tx *db.Transaction) []findingsRepairClamp {
	result := []findingsRepairClamp{}
	for _, row := range db.Select[struct {
		Id    int64
		Usage int64
	}](
		tx,
		`select id, local_usage as usage from accounting.wallets_v2 where local_usage < 0 order by id`,
		db.Params{},
	) {
		result = append(result, findingsRepairClamp{Kind: "wallet", Id: row.Id, Field: "local_usage", From: row.Usage, To: 0})
	}
	for _, row := range db.Select[struct {
		Id    int64
		Usage int64
	}](
		tx,
		`select id, tree_usage as usage from accounting.allocation_groups where tree_usage < 0 order by id`,
		db.Params{},
	) {
		result = append(result, findingsRepairClamp{Kind: "allocation-group", Id: row.Id, Field: "tree_usage", From: row.Usage, To: 0})
	}
	for _, row := range db.Select[struct {
		WalletId int64
		Key      string
		Usage    int64
	}](
		tx,
		`select wallet_id, key, usage from accounting.scoped_usage where usage < 0 order by wallet_id, key`,
		db.Params{},
	) {
		result = append(result, findingsRepairClamp{Kind: "scoped-usage", Id: row.WalletId, Key: row.Key, Field: "usage", From: row.Usage, To: 0})
	}
	return result
}

func persistFindingsRepairNonAllocationClamps(tx *db.Transaction) {
	db.Exec(
		tx,
		`update accounting.wallets_v2 set local_usage = 0 where local_usage < 0`,
		db.Params{},
	)
	db.Exec(
		tx,
		`update accounting.allocation_groups set tree_usage = 0 where tree_usage < 0`,
		db.Params{},
	)
	db.Exec(
		tx,
		`update accounting.scoped_usage set usage = 0 where usage < 0`,
		db.Params{},
	)
}

func planFindingsRepairRecomputations(tx *db.Transaction, plan findingsRepairPlan, now time.Time) ([]findingsRepairRecomputation, []string) {
	persistedRows := db.Select[findingsRepairWalletState](
		tx,
		`select id, excess_usage, total_allocated, total_retired_allocated, was_locked from accounting.wallets_v2 order by id`,
		db.Params{},
	)
	persisted := make(map[int64]findingsRepairWalletState, len(persistedRows))
	for _, row := range persistedRows {
		persisted[row.Id] = row
	}

	productsLoadFromTx(tx)
	var blockers []string
	accountingLoadFromTx(tx, now, func(message string) { blockers = append(blockers, message) })
	if len(blockers) > 0 {
		return nil, blockers
	}
	applyFindingsRepairPlanToLoadedModel(plan, now)

	var result []findingsRepairRecomputation
	appendInt := func(walletId int64, code, field string, from, to int64) {
		if from == to {
			return
		}
		result = append(result, findingsRepairRecomputation{
			WalletId: walletId, Code: code, Field: field,
			From: fmt.Sprint(from), To: fmt.Sprint(to), IntValue: to,
		})
	}
	appendBool := func(walletId int64, code, field string, from, to bool) {
		if from == to {
			return
		}
		result = append(result, findingsRepairRecomputation{
			WalletId: walletId, Code: code, Field: field,
			From: fmt.Sprint(from), To: fmt.Sprint(to), BoolValue: to,
		})
	}

	for _, bucket := range accGlobals.BucketsByCategory {
		walletIds := make([]AccWalletId, 0, len(bucket.WalletsById))
		for walletId := range bucket.WalletsById {
			walletIds = append(walletIds, walletId)
		}
		slices.Sort(walletIds)
		for _, walletId := range walletIds {
			wallet := bucket.WalletsById[walletId]
			before := persisted[int64(walletId)]
			derived, overflows := recomputeSnapshotWalletValues(bucket, wallet)
			if len(overflows) > 0 {
				blockers = append(blockers, fmt.Sprintf("wallet %d cannot recompute derived state because %v overflow", walletId, overflows))
				continue
			}
			maxUsable, err := lAccountingMaxUsableSafely(bucket, now, wallet)
			if err != nil {
				blockers = append(blockers, fmt.Sprintf("wallet %d cannot recompute lock state: %v", walletId, err))
				continue
			}
			appendInt(int64(walletId), "excess-usage-divergence", "excess_usage", before.ExcessUsage, derived.ExcessUsage)
			appendInt(int64(walletId), "total-allocated-divergence", "total_allocated", before.TotalAllocated, derived.TotalAllocated)
			appendInt(int64(walletId), "total-retired-allocated-divergence", "total_retired_allocated", before.TotalRetiredAllocated, derived.TotalRetiredAllocated)
			appendBool(int64(walletId), "stale-lock-state", "was_locked", before.WasLocked, maxUsable <= 0)
		}
	}
	slices.SortFunc(result, func(a, b findingsRepairRecomputation) int {
		if order := cmp.Compare(a.WalletId, b.WalletId); order != 0 {
			return order
		}
		return cmp.Compare(a.Field, b.Field)
	})
	return result, blockers
}

func applyFindingsRepairPlanToLoadedModel(plan findingsRepairPlan, now time.Time) {
	for _, deletion := range plan.AllocationDeletions {
		for _, bucket := range accGlobals.BucketsByCategory {
			allocation := bucket.AllocationsById[accAllocId(deletion.Id)]
			if allocation == nil {
				continue
			}
			if wallet := bucket.WalletsById[allocation.BelongsTo]; wallet != nil {
				if group := wallet.AllocationsByParent[allocation.Parent]; group != nil {
					delete(group.Allocations, allocation.Id)
				}
			}
			delete(bucket.AllocationsById, allocation.Id)
		}
	}
	for _, deletion := range plan.GroupDeletions {
		for _, bucket := range accGlobals.BucketsByCategory {
			for _, wallet := range bucket.WalletsById {
				for parentId, group := range wallet.AllocationsByParent {
					if group.Id != accGroupId(deletion.Id) {
						continue
					}
					delete(wallet.AllocationsByParent, parentId)
					if parent := bucket.WalletsById[parentId]; parent != nil {
						delete(parent.ChildrenUsage, wallet.Id)
					}
				}
			}
		}
	}
	for _, clamp := range plan.Clamps {
		for _, bucket := range accGlobals.BucketsByCategory {
			switch clamp.Kind {
			case "wallet":
				if wallet := bucket.WalletsById[AccWalletId(clamp.Id)]; wallet != nil {
					wallet.LocalUsage = clamp.To
				}
			case "allocation-group":
				for _, wallet := range bucket.WalletsById {
					for _, group := range wallet.AllocationsByParent {
						if group.Id == accGroupId(clamp.Id) {
							group.TreeUsage = clamp.To
							if parent := bucket.WalletsById[group.ParentWallet]; parent != nil {
								parent.ChildrenUsage[group.AssociatedWallet] = clamp.To
							}
						}
					}
				}
			}
		}
	}
	for _, update := range plan.AllocationUpdates {
		for _, bucket := range accGlobals.BucketsByCategory {
			allocation := bucket.AllocationsById[accAllocId(update.Id)]
			if allocation == nil {
				continue
			}
			allocation.Quota = update.Quota
			allocation.Start = update.Start
			allocation.End = update.End
			allocation.Retired = update.Retired
			allocation.RetiredUsage = update.RetiredUsage
			allocation.RetiredQuota = update.RetiredQuota
			allocation.Active = !now.Before(update.Start)
		}
	}
}

func persistFindingsRepairRecomputations(tx *db.Transaction, recomputations []findingsRepairRecomputation) {
	for _, field := range []string{"excess_usage", "total_allocated", "total_retired_allocated"} {
		var ids, values []int64
		for _, item := range recomputations {
			if item.Field == field {
				ids = append(ids, item.WalletId)
				values = append(values, item.IntValue)
			}
		}
		if len(ids) > 0 {
			db.Exec(tx, fmt.Sprintf(`with data as (select unnest(cast(:ids as int8[])) id, unnest(cast(:values as int8[])) value) update accounting.wallets_v2 w set %s = data.value from data where w.id = data.id`, field), db.Params{"ids": ids, "values": values})
		}
	}
	var lockIds []int64
	var lockValues []bool
	for _, item := range recomputations {
		if item.Field == "was_locked" {
			lockIds = append(lockIds, item.WalletId)
			lockValues = append(lockValues, item.BoolValue)
		}
	}
	if len(lockIds) > 0 {
		db.Exec(tx, `with data as (select unnest(cast(:ids as int8[])) id, unnest(cast(:values as bool[])) value) update accounting.wallets_v2 w set was_locked = data.value from data where w.id = data.id`, db.Params{"ids": lockIds, "values": lockValues})
	}
}

func selectFindingsRepairRecomputationDivergences(tx *db.Transaction, now time.Time) []findingsRepairRecomputation {
	result, blockers := planFindingsRepairRecomputations(tx, findingsRepairPlan{}, now)
	if len(blockers) > 0 {
		panic(fmt.Sprintf("could not verify derived wallet state: %v", blockers))
	}
	return result
}

func planFindingsRepair(groups []findingsRepairGroupRow, allocations []findingsRepairAllocationRow, now time.Time) findingsRepairPlan {
	result := planFindingsRepairCycles(groups, allocations)
	deletedAllocations := map[int64]bool{}
	for _, deletion := range result.AllocationDeletions {
		deletedAllocations[deletion.Id] = true
	}
	deletedGroups := map[int64]bool{}
	for _, deletion := range result.GroupDeletions {
		deletedGroups[deletion.Id] = true
	}

	groupTreeUsage := map[int64]int64{}
	for _, group := range groups {
		if deletedGroups[group.Id] {
			continue
		}
		groupTreeUsage[group.Id] = max(0, group.TreeUsage)
	}

	remaining := make([]findingsRepairAllocationRow, 0, len(allocations))
	changedAllocations := map[int64]bool{}
	for _, allocation := range allocations {
		if deletedAllocations[allocation.Id] {
			continue
		}
		if allocation.Quota < 0 {
			result.Clamps = append(result.Clamps, findingsRepairClamp{Kind: "allocation", Id: allocation.Id, Field: "quota", From: allocation.Quota, To: 0})
			allocation.Quota = 0
			changedAllocations[allocation.Id] = true
		}
		if allocation.RetiredUsage < 0 {
			result.Clamps = append(result.Clamps, findingsRepairClamp{Kind: "allocation", Id: allocation.Id, Field: "retired_usage", From: allocation.RetiredUsage, To: 0})
			allocation.RetiredUsage = 0
			changedAllocations[allocation.Id] = true
		}
		if allocation.RetiredQuota < 0 {
			result.Clamps = append(result.Clamps, findingsRepairClamp{Kind: "allocation", Id: allocation.Id, Field: "retired_quota", From: allocation.RetiredQuota, To: 0})
			allocation.RetiredQuota = 0
			changedAllocations[allocation.Id] = true
		}
		if allocation.AllocationStartTime.After(allocation.AllocationEndTime) {
			result.LifecycleChanges = append(result.LifecycleChanges, findingsRepairLifecycleChange{
				AllocationId: allocation.Id,
				Code:         "allocation-lifecycle",
				Field:        "allocation_end_time",
				From:         allocation.AllocationEndTime.UTC().Format(time.RFC3339Nano),
				To:           allocation.AllocationStartTime.UTC().Format(time.RFC3339Nano),
				Reason:       "invalid interval",
			})
			allocation.AllocationEndTime = allocation.AllocationStartTime
			changedAllocations[allocation.Id] = true
		}
		if allocation.Retired && now.Before(allocation.AllocationEndTime) {
			appendFindingsLifecycleChange(&result, allocation.Id, "retired", allocation.Retired, false, "allocation end has not passed")
			appendFindingsLifecycleChange(&result, allocation.Id, "quota", allocation.Quota, allocation.RetiredQuota, "restore pre-retirement quota")
			appendFindingsLifecycleChange(&result, allocation.Id, "retired_usage", allocation.RetiredUsage, 0, "clear premature retirement state")
			appendFindingsLifecycleChange(&result, allocation.Id, "retired_quota", allocation.RetiredQuota, 0, "clear premature retirement state")
			allocation.Quota = allocation.RetiredQuota
			allocation.Retired = false
			allocation.RetiredUsage = 0
			allocation.RetiredQuota = 0
			changedAllocations[allocation.Id] = true
		} else if !allocation.Retired {
			appendFindingsLifecycleChange(&result, allocation.Id, "retired_usage", allocation.RetiredUsage, 0, "clear stale unretired state")
			appendFindingsLifecycleChange(&result, allocation.Id, "retired_quota", allocation.RetiredQuota, 0, "clear stale unretired state")
			if allocation.RetiredUsage != 0 || allocation.RetiredQuota != 0 {
				allocation.RetiredUsage = 0
				allocation.RetiredQuota = 0
				changedAllocations[allocation.Id] = true
			}
		} else if allocation.AccountingFrequency == "ONCE" {
			if allocation.RetiredUsage > allocation.RetiredQuota {
				appendFindingsLifecycleChange(&result, allocation.Id, "retired_usage", allocation.RetiredUsage, allocation.RetiredQuota, "capacity retired usage cannot exceed retired quota")
				allocation.RetiredUsage = allocation.RetiredQuota
				changedAllocations[allocation.Id] = true
			}
		} else if allocation.Quota != allocation.RetiredUsage {
			appendFindingsLifecycleChange(&result, allocation.Id, "quota", allocation.Quota, allocation.RetiredUsage, "periodic quota preserves retired usage")
			allocation.Quota = allocation.RetiredUsage
			changedAllocations[allocation.Id] = true
		}
		remaining = append(remaining, allocation)
	}

	retiredUsageByGroup := map[int64]int64{}
	for _, allocation := range remaining {
		if allocation.Retired {
			retiredUsageByGroup[allocation.GroupId] += allocation.RetiredUsage
		}
	}
	slices.SortFunc(remaining, func(a, b findingsRepairAllocationRow) int {
		if order := a.AllocationEndTime.Compare(b.AllocationEndTime); order != 0 {
			return order
		}
		return cmp.Compare(a.Id, b.Id)
	})
	for index := range remaining {
		allocation := &remaining[index]
		if allocation.Retired || now.Before(allocation.AllocationEndTime) {
			continue
		}
		groupRetired := retiredUsageByGroup[allocation.GroupId]
		toRetire := min(allocation.Quota, groupTreeUsage[allocation.GroupId]-groupRetired)
		toRetire = max(0, toRetire)
		retiredUsageByGroup[allocation.GroupId] += toRetire - allocation.RetiredUsage
		appendFindingsLifecycleChange(&result, allocation.Id, "retired", allocation.Retired, true, "exclusive end passed")
		appendFindingsLifecycleChange(&result, allocation.Id, "retired_usage", allocation.RetiredUsage, toRetire, "exclusive end passed")
		appendFindingsLifecycleChange(&result, allocation.Id, "retired_quota", allocation.RetiredQuota, allocation.Quota, "exclusive end passed")
		allocation.Retired = true
		allocation.RetiredUsage = toRetire
		allocation.RetiredQuota = allocation.Quota
		changedAllocations[allocation.Id] = true
		if allocation.AccountingFrequency != "ONCE" {
			appendFindingsLifecycleChange(&result, allocation.Id, "quota", allocation.Quota, toRetire, "periodic allocation retired")
			allocation.Quota = toRetire
		}
	}

	slices.SortFunc(remaining, func(a, b findingsRepairAllocationRow) int { return cmp.Compare(a.Id, b.Id) })
	for _, allocation := range remaining {
		if !changedAllocations[allocation.Id] {
			continue
		}
		result.AllocationUpdates = append(result.AllocationUpdates, findingsRepairAllocationUpdate{
			Id:           allocation.Id,
			Quota:        allocation.Quota,
			Start:        allocation.AllocationStartTime,
			End:          allocation.AllocationEndTime,
			Retired:      allocation.Retired,
			RetiredUsage: allocation.RetiredUsage,
			RetiredQuota: allocation.RetiredQuota,
		})
	}
	return result
}

func appendFindingsLifecycleChange(result *findingsRepairPlan, allocationId int64, field string, from, to any, reason string) {
	if fmt.Sprint(from) == fmt.Sprint(to) {
		return
	}
	result.LifecycleChanges = append(result.LifecycleChanges, findingsRepairLifecycleChange{
		AllocationId: allocationId,
		Code:         "retirement-state",
		Field:        field,
		From:         fmt.Sprint(from),
		To:           fmt.Sprint(to),
		Reason:       reason,
	})
}

func planFindingsRepairCycles(groups []findingsRepairGroupRow, allocations []findingsRepairAllocationRow) findingsRepairPlan {
	result := findingsRepairPlan{
		AllocationDeletions: []findingsRepairAllocationDeletion{},
		GroupDeletions:      []findingsRepairGroupDeletion{},
		Clamps:              []findingsRepairClamp{},
		LifecycleChanges:    []findingsRepairLifecycleChange{},
	}
	remainingGroups := append([]findingsRepairGroupRow(nil), groups...)
	remainingAllocations := append([]findingsRepairAllocationRow(nil), allocations...)
	for {
		cyclicGroups := findCyclicAccountingGroups(remainingGroups)
		if len(cyclicGroups) == 0 {
			break
		}
		allocationCount := map[int64]int{}
		for _, allocation := range remainingAllocations {
			allocationCount[allocation.GroupId]++
		}
		emptyGroupId := int64(0)
		for groupId := range cyclicGroups {
			if allocationCount[groupId] == 0 {
				emptyGroupId = max(emptyGroupId, groupId)
			}
		}
		if emptyGroupId != 0 {
			result.GroupDeletions = append(result.GroupDeletions, findingsRepairGroupDeletion{Id: emptyGroupId})
			remainingGroups = slices.DeleteFunc(remainingGroups, func(group findingsRepairGroupRow) bool { return group.Id == emptyGroupId })
			continue
		}

		allocationIndex := -1
		for index, allocation := range remainingAllocations {
			if cyclicGroups[allocation.GroupId] && (allocationIndex == -1 || allocation.Id > remainingAllocations[allocationIndex].Id) {
				allocationIndex = index
			}
		}
		if allocationIndex == -1 {
			panic("cyclic allocation group has neither allocations nor an empty group candidate")
		}
		allocation := remainingAllocations[allocationIndex]
		result.AllocationDeletions = append(result.AllocationDeletions, findingsRepairAllocationDeletion{Id: allocation.Id, GroupId: allocation.GroupId})
		remainingAllocations = slices.Delete(remainingAllocations, allocationIndex, allocationIndex+1)

		groupStillUsed := false
		for _, candidate := range remainingAllocations {
			if candidate.GroupId == allocation.GroupId {
				groupStillUsed = true
				break
			}
		}
		if !groupStillUsed {
			result.GroupDeletions = append(result.GroupDeletions, findingsRepairGroupDeletion{Id: allocation.GroupId})
			remainingGroups = slices.DeleteFunc(remainingGroups, func(group findingsRepairGroupRow) bool { return group.Id == allocation.GroupId })
		}
	}
	return result
}

func findCyclicAccountingGroups(groups []findingsRepairGroupRow) map[int64]bool {
	adjacency := map[int64][]int64{}
	for _, group := range groups {
		if group.ParentWallet.Valid {
			adjacency[group.ParentWallet.Int64] = append(adjacency[group.ParentWallet.Int64], group.AssociatedWallet)
		}
	}
	index := 0
	indices := map[int64]int{}
	lowLink := map[int64]int{}
	onStack := map[int64]bool{}
	stack := []int64{}
	componentByWallet := map[int64]int{}
	componentSize := map[int]int{}
	component := 0
	var visit func(int64)
	visit = func(walletId int64) {
		index++
		indices[walletId] = index
		lowLink[walletId] = index
		stack = append(stack, walletId)
		onStack[walletId] = true
		for _, childId := range adjacency[walletId] {
			if indices[childId] == 0 {
				visit(childId)
				lowLink[walletId] = min(lowLink[walletId], lowLink[childId])
			} else if onStack[childId] {
				lowLink[walletId] = min(lowLink[walletId], indices[childId])
			}
		}
		if lowLink[walletId] != indices[walletId] {
			return
		}
		for {
			last := len(stack) - 1
			member := stack[last]
			stack = stack[:last]
			onStack[member] = false
			componentByWallet[member] = component
			componentSize[component]++
			if member == walletId {
				break
			}
		}
		component++
	}
	for parentId, children := range adjacency {
		if indices[parentId] == 0 {
			visit(parentId)
		}
		for _, childId := range children {
			if indices[childId] == 0 {
				visit(childId)
			}
		}
	}

	result := map[int64]bool{}
	for _, group := range groups {
		if !group.ParentWallet.Valid {
			continue
		}
		parentId := group.ParentWallet.Int64
		componentId := componentByWallet[parentId]
		if componentId == componentByWallet[group.AssociatedWallet] && (parentId == group.AssociatedWallet || componentSize[componentId] > 1) {
			result[group.Id] = true
		}
	}
	return result
}

func persistFindingsRepairAllocationUpdates(tx *db.Transaction, updates []findingsRepairAllocationUpdate) {
	if len(updates) == 0 {
		return
	}
	ids := make([]int64, 0, len(updates))
	quotas := make([]int64, 0, len(updates))
	starts := make([]time.Time, 0, len(updates))
	ends := make([]time.Time, 0, len(updates))
	retired := make([]bool, 0, len(updates))
	retiredUsage := make([]int64, 0, len(updates))
	retiredQuota := make([]int64, 0, len(updates))
	for _, update := range updates {
		ids = append(ids, update.Id)
		quotas = append(quotas, update.Quota)
		starts = append(starts, update.Start)
		ends = append(ends, update.End)
		retired = append(retired, update.Retired)
		retiredUsage = append(retiredUsage, update.RetiredUsage)
		retiredQuota = append(retiredQuota, update.RetiredQuota)
	}
	db.Exec(
		tx,
		`
			with data as (
				select unnest(cast(:id as int8[])) as id,
					unnest(cast(:quota as int8[])) as quota,
					unnest(cast(:start as timestamptz[])) as allocation_start_time,
					unnest(cast(:end as timestamptz[])) as allocation_end_time,
					unnest(cast(:retired as bool[])) as retired,
					unnest(cast(:retired_usage as int8[])) as retired_usage,
					unnest(cast(:retired_quota as int8[])) as retired_quota
			)
			update accounting.wallet_allocations_v2 a
			set quota = data.quota,
				allocation_start_time = data.allocation_start_time,
				allocation_end_time = data.allocation_end_time,
				retired = data.retired,
				retired_usage = data.retired_usage,
				retired_quota = data.retired_quota
			from data where a.id = data.id
		`,
		db.Params{
			"id":            ids,
			"quota":         quotas,
			"start":         starts,
			"end":           ends,
			"retired":       retired,
			"retired_usage": retiredUsage,
			"retired_quota": retiredQuota,
		},
	)
}
