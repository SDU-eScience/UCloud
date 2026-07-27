package accounting

import (
	"cmp"
	"database/sql"
	"fmt"
	"slices"

	"gopkg.in/yaml.v3"
	db "ucloud.dk/shared/pkg/database"
)

type duplicateRepairWallet struct {
	RemoveId int64 `yaml:"removeId"`
	KeepId   int64 `yaml:"keepId"`
}

type duplicateRepairGroup struct {
	RemoveId int64 `yaml:"removeId"`
	KeepId   int64 `yaml:"keepId"`
}

type duplicateRepairReport struct {
	Applied               bool                    `yaml:"applied"`
	Wallets               []duplicateRepairWallet `yaml:"wallets"`
	Groups                []duplicateRepairGroup  `yaml:"groups"`
	AllocationsMoved      int                     `yaml:"allocationsMoved"`
	AllocationCountBefore int                     `yaml:"allocationCountBefore"`
	AllocationCountAfter  int                     `yaml:"allocationCountAfter"`
	DeletedHistoricalRows int                     `yaml:"deletedHistoricalRows"`
	Blocked               bool                    `yaml:"blocked"`
	Blockers              []string                `yaml:"blockers,omitempty"`
}

type duplicateRepairWalletRow struct {
	Id              int64
	WalletOwner     int64
	ProductCategory int64
	LocalUsage      int64
}

type duplicateRepairGroupRow struct {
	Id               int64
	ParentWallet     sql.NullInt64
	AssociatedWallet int64
	TreeUsage        int64
}

type duplicateRepairGroupTarget struct {
	Id               int64
	ParentWallet     int64
	AssociatedWallet int64
}

func RepairAccountingDuplicates(apply bool) []byte {
	report := db.NewTx(func(tx *db.Transaction) duplicateRepairReport {
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
						accounting.wallet_allocations_v2, accounting.wallet_samples_v2,
						accounting.wallet_snapshots, accounting.usage_report,
						accounting.intermediate_usage in access exclusive mode
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

		walletRows := db.Select[duplicateRepairWalletRow](
			tx,
			`
				select id, wallet_owner, product_category, local_usage
				from accounting.wallets_v2
				order by id
			`,
			db.Params{},
		)
		groupRows := db.Select[duplicateRepairGroupRow](
			tx,
			`
				select id, parent_wallet, associated_wallet, tree_usage
				from accounting.allocation_groups
				order by id
			`,
			db.Params{},
		)
		allocationIdsBefore := selectAccountingAllocationIds(tx)

		result := planDuplicateRepair(walletRows, groupRows)
		result.AllocationCountBefore = len(allocationIdsBefore)
		result.AllocationCountAfter = len(allocationIdsBefore)
		plannedOldGroupIds := make([]int64, 0, len(result.Groups))
		for _, change := range result.Groups {
			plannedOldGroupIds = append(plannedOldGroupIds, change.RemoveId)
		}
		if len(plannedOldGroupIds) > 0 {
			moved, _ := db.Get[struct{ Count int }](
				tx,
				`
					select count(*) as count from accounting.wallet_allocations_v2
					where associated_allocation_group = any(cast(:ids as int8[]))
				`,
				db.Params{"ids": plannedOldGroupIds},
			)
			result.AllocationsMoved = moved.Count
		}
		if result.Blocked || !apply {
			return result
		}

		oldGroupIds := make([]int64, 0, len(result.Groups))
		newGroupIds := make([]int64, 0, len(result.Groups))
		for _, change := range result.Groups {
			oldGroupIds = append(oldGroupIds, change.RemoveId)
			newGroupIds = append(newGroupIds, change.KeepId)
		}
		if len(oldGroupIds) > 0 {
			moved, _ := db.Get[struct{ Count int }](
				tx,
				`
					with mapping as (
						select unnest(cast(:old_id as int8[])) as old_id,
							unnest(cast(:new_id as int8[])) as new_id
					), updated as (
						update accounting.wallet_allocations_v2 a
						set associated_allocation_group = mapping.new_id
						from mapping
						where a.associated_allocation_group = mapping.old_id
						returning a.id
					)
					select count(*) as count from updated
				`,
				db.Params{"old_id": oldGroupIds, "new_id": newGroupIds},
			)
			result.AllocationsMoved = moved.Count
			db.Exec(
				tx,
				`delete from accounting.allocation_groups where id = any(cast(:ids as int8[]))`,
				db.Params{"ids": oldGroupIds},
			)
		}

		walletKeep := map[int64]int64{}
		for _, row := range walletRows {
			walletKeep[row.Id] = row.Id
		}
		for _, change := range result.Wallets {
			walletKeep[change.RemoveId] = change.KeepId
		}
		groupTargets := buildDuplicateRepairGroupTargets(groupRows, walletKeep, result.Groups)
		if len(groupTargets) > 0 {
			ids := make([]int64, 0, len(groupTargets))
			parents := make([]int64, 0, len(groupTargets))
			wallets := make([]int64, 0, len(groupTargets))
			for _, target := range groupTargets {
				ids = append(ids, target.Id)
				parents = append(parents, target.ParentWallet)
				wallets = append(wallets, target.AssociatedWallet)
			}
			db.Exec(
				tx,
				`
					with data as (
						select unnest(cast(:id as int8[])) as id,
							unnest(cast(:parent as int8[])) as parent,
							unnest(cast(:wallet as int8[])) as wallet
					)
					update accounting.allocation_groups ag
					set associated_wallet = data.wallet,
						parent_wallet = case when data.parent = 0 then null else data.parent end
					from data where ag.id = data.id
				`,
				db.Params{"id": ids, "parent": parents, "wallet": wallets},
			)
		}

		loserWalletIds := make([]int64, 0, len(result.Wallets))
		for _, change := range result.Wallets {
			loserWalletIds = append(loserWalletIds, change.RemoveId)
		}
		if len(loserWalletIds) > 0 {
			for _, table := range []string{
				"accounting.wallet_samples_v2",
				"accounting.usage_report",
				"accounting.intermediate_usage",
			} {
				deleted, _ := db.Get[struct{ Count int }](
					tx,
					fmt.Sprintf(`
						with removed as (delete from %s where wallet_id = any(cast(:ids as int8[])) returning 1)
						select count(*) as count from removed
					`, table),
					db.Params{"ids": loserWalletIds},
				)
				result.DeletedHistoricalRows += deleted.Count
			}
			deleted, _ := db.Get[struct{ Count int }](
				tx,
				`
					with removed as (delete from accounting.wallet_snapshots where id = any(cast(:ids as int8[])) returning 1)
					select count(*) as count from removed
				`,
				db.Params{"ids": loserWalletIds},
			)
			result.DeletedHistoricalRows += deleted.Count

			db.Exec(
				tx,
				`delete from accounting.wallets_v2 where id = any(cast(:ids as int8[]))`,
				db.Params{"ids": loserWalletIds},
			)
		}

		canonicalIds, canonicalUsage := duplicateRepairCanonicalUsage(walletRows)
		if len(canonicalIds) > 0 {
			db.Exec(
				tx,
				`
					with data as (
						select unnest(cast(:id as int8[])) as id,
							unnest(cast(:usage as int8[])) as usage
					)
					update accounting.wallets_v2 w set local_usage = data.usage
					from data where w.id = data.id
				`,
				db.Params{"id": canonicalIds, "usage": canonicalUsage},
			)
		}

		allocationIdsAfter := selectAccountingAllocationIds(tx)
		result.AllocationCountAfter = len(allocationIdsAfter)
		if !slices.Equal(allocationIdsBefore, allocationIdsAfter) {
			panic("duplicate accounting repair changed the allocation ID set")
		}
		remainingWalletDuplicates, _ := db.Get[struct{ Count int }](
			tx,
			`
				select count(*) as count from (
					select 1 from accounting.wallets_v2 group by wallet_owner, product_category having count(*) > 1
				) duplicates
			`,
			db.Params{},
		)
		remainingGroupDuplicates, _ := db.Get[struct{ Count int }](
			tx,
			`
				select count(*) as count from (
					select 1 from accounting.allocation_groups
					group by associated_wallet, coalesce(parent_wallet, 0) having count(*) > 1
				) duplicates
			`,
			db.Params{},
		)
		if remainingWalletDuplicates.Count != 0 || remainingGroupDuplicates.Count != 0 {
			panic("duplicate accounting repair left duplicate wallets or allocation groups")
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

func planDuplicateRepair(walletRows []duplicateRepairWalletRow, groupRows []duplicateRepairGroupRow) duplicateRepairReport {
	result := duplicateRepairReport{Wallets: []duplicateRepairWallet{}, Groups: []duplicateRepairGroup{}}
	walletKeep := map[int64]int64{}
	walletByKey := map[string]int64{}
	for _, row := range walletRows {
		key := fmt.Sprintf("%d/%d", row.WalletOwner, row.ProductCategory)
		keep, exists := walletByKey[key]
		if !exists {
			keep = row.Id
			walletByKey[key] = keep
		} else {
			result.Wallets = append(result.Wallets, duplicateRepairWallet{RemoveId: row.Id, KeepId: keep})
		}
		walletKeep[row.Id] = keep
	}

	groupByKey := map[string]int64{}
	for _, row := range groupRows {
		associated := walletKeep[row.AssociatedWallet]
		parent := int64(0)
		if row.ParentWallet.Valid {
			parent = walletKeep[row.ParentWallet.Int64]
		}
		if parent != 0 && parent == associated {
			result.Blocked = true
			result.Blockers = append(result.Blockers, fmt.Sprintf("group %d becomes a self-allocation on wallet %d", row.Id, associated))
			continue
		}
		key := fmt.Sprintf("%d/%d", associated, parent)
		keep, exists := groupByKey[key]
		if !exists {
			groupByKey[key] = row.Id
		} else {
			result.Groups = append(result.Groups, duplicateRepairGroup{RemoveId: row.Id, KeepId: keep})
		}
	}
	return result
}

func buildDuplicateRepairGroupTargets(rows []duplicateRepairGroupRow, walletKeep map[int64]int64, changes []duplicateRepairGroup) []duplicateRepairGroupTarget {
	removed := map[int64]bool{}
	for _, change := range changes {
		removed[change.RemoveId] = true
	}
	var result []duplicateRepairGroupTarget
	for _, row := range rows {
		if removed[row.Id] {
			continue
		}
		parent := int64(0)
		if row.ParentWallet.Valid {
			parent = walletKeep[row.ParentWallet.Int64]
		}
		associated := walletKeep[row.AssociatedWallet]
		if associated != row.AssociatedWallet || parent != row.ParentWallet.Int64 {
			result = append(result, duplicateRepairGroupTarget{Id: row.Id, ParentWallet: parent, AssociatedWallet: associated})
		}
	}
	return result
}

func duplicateRepairCanonicalUsage(rows []duplicateRepairWalletRow) ([]int64, []int64) {
	type value struct {
		id    int64
		usage int64
	}
	byKey := map[string]value{}
	for _, row := range rows {
		key := fmt.Sprintf("%d/%d", row.WalletOwner, row.ProductCategory)
		current, exists := byKey[key]
		if !exists {
			byKey[key] = value{id: row.Id, usage: row.LocalUsage}
		} else if row.LocalUsage > current.usage {
			current.usage = row.LocalUsage
			byKey[key] = current
		}
	}
	values := make([]value, 0, len(byKey))
	for _, item := range byKey {
		values = append(values, item)
	}
	slices.SortFunc(values, func(a, b value) int { return cmp.Compare(a.id, b.id) })
	ids := make([]int64, 0, len(values))
	usage := make([]int64, 0, len(values))
	for _, item := range values {
		ids = append(ids, item.id)
		usage = append(usage, item.usage)
	}
	return ids, usage
}

func selectAccountingAllocationIds(tx *db.Transaction) []int64 {
	rows := db.Select[struct{ Id int64 }](
		tx,
		`select id from accounting.wallet_allocations_v2 order by id`,
		db.Params{},
	)
	result := make([]int64, 0, len(rows))
	for _, row := range rows {
		result = append(result, row.Id)
	}
	return result
}
