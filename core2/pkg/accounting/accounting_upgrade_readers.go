package accounting

import (
	"time"

	db "ucloud.dk/shared/pkg/database"
)

func accountingUpgradeLoadProducts(tx *db.Transaction) {
	rows := db.Select[accountingProductRow](
		tx,
		`
			select
				p.name,
				pc.category,
				pc.provider,
				p.price,
				p.description,
				p.cpu,
				p.gpu,
				p.memory_in_gigs,
				p.cpu_model,
				p.gpu_model,
				p.memory_model,
				p.hidden_in_grant_applications,
				p.fraction_numerator,
				p.fraction_denominator,
				pc.product_type,
				pc.accounting_frequency,
				case
					when pc.product_type = 'INFERENCE' then 'CONSUMPTION'
					when pc.accounting_frequency = 'ONCE' then 'CAPACITY'
					else 'CONSUMPTION'
				end as accounting_model,
				pc.free_to_use,
				pc.allow_sub_allocations,
				u.name as unit_name,
				u.name_plural as unit_name_plural,
				u.display_frequency_suffix as unit_freq_suffix,
				u.floating_point as unit_floating_point
			from
				accounting.products p
				join accounting.product_categories pc on p.category = pc.id
				join accounting.accounting_units u on pc.accounting_unit = u.id
			order by pc.provider, pc.category, p.name
		`,
		db.Params{},
	)
	accountingLoadProductRows(rows)
}

func accountingUpgradeSelectAllocations(tx *db.Transaction) []accountingAllocationRow {
	return db.Select[accountingAllocationRow](
		tx,
		`
			select
				alloc.id,
				ag.associated_wallet as wallet_id,
				coalesce(ag.parent_wallet, 0) as parent_wallet,
				coalesce(alloc.granted_in, 0) as granted_in,
				false as allocation_package,
				alloc.associated_allocation_group as alloc_group,
				alloc.quota,
				alloc.allocation_start_time,
				alloc.allocation_end_time,
				alloc.retired,
				alloc.retired_usage,
				alloc.retired_quota
			from
				accounting.wallet_allocations_v2 alloc
				join accounting.allocation_groups ag on alloc.associated_allocation_group = ag.id
			order by alloc.id
		`,
		db.Params{},
	)
}

func accountingUpgradeLoadAccounting(tx *db.Transaction, now time.Time, onLoadError func(string)) accountingLoadTimes {
	return accountingLoadWithAllocationsFromTx(tx, now, onLoadError, accountingUpgradeSelectAllocations)
}

func accountingUpgradeSelectRepairAllocations(tx *db.Transaction) []findingsRepairAllocationRow {
	return db.Select[findingsRepairAllocationRow](
		tx,
		`
			select
				a.id,
				a.associated_allocation_group as group_id,
				a.quota,
				a.allocation_start_time,
				a.allocation_end_time,
				a.retired,
				a.retired_usage,
				a.retired_quota,
				case
					when pc.product_type = 'INFERENCE' then 'CONSUMPTION'
					when pc.accounting_frequency = 'ONCE' then 'CAPACITY'
					else 'CONSUMPTION'
				end as accounting_model
			from
				accounting.wallet_allocations_v2 a
				join accounting.allocation_groups ag on ag.id = a.associated_allocation_group
				join accounting.wallets_v2 w on w.id = ag.associated_wallet
				join accounting.product_categories pc on pc.id = w.product_category
			order by a.id
		`,
		db.Params{},
	)
}
