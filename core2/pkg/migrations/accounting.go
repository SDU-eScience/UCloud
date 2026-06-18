package migrations

import db "ucloud.dk/shared/pkg/database"

func accountingV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "accountingV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table accounting.wallet_allocations_v2 add column retired_quota int8 default 0
			    `,
				db.Params{},
			)
		},
	}
}

func accountingV2() db.MigrationScript {
	return db.MigrationScript{
		Id: "accountingV2",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table accounting.wallet_snapshots(
						id int8 primary key references accounting.wallets_v2(id),
						created_at timestamptz default now(),
						snapshot jsonb not null
					)
			    `,
				db.Params{},
			)
		},
	}
}

func accountingV3() db.MigrationScript {
	return db.MigrationScript{
		Id: "accountingV3",
		Execute: func(tx *db.Transaction) {
			// This is stupid heuristic, but it works
			row, ok := db.Get[struct{ Count int }](
				tx,
				`select count(*) as count from task.tasks`,
				db.Params{},
			)

			isMigratingFromCore1 := ok && row.Count > 0

			if isMigratingFromCore1 {
				db.Exec(
					tx,
					`
						update accounting.wallet_allocations_v2
						set retired_quota = quota
						where retired = true;
				    `,
					db.Params{},
				)

				db.Exec(
					tx,
					`
						update accounting.wallet_allocations_v2 alloc
						set quota = retired_usage
						from
							accounting.product_categories pc
							join accounting.wallets_v2 w on pc.id = w.product_category
							join accounting.allocation_groups ag on w.id = ag.associated_wallet
						where
							alloc.associated_allocation_group = ag.id
							and pc.accounting_frequency != 'ONCE'
							and alloc.retired = true
				    `,
					db.Params{},
				)

				db.Exec(
					tx,
					`
						update accounting.allocation_groups ag
						set tree_usage = tree_usage + retired_tree_usage
						from
							accounting.wallets_v2 w
							join accounting.product_categories pc on w.product_category = pc.id
						where 
							ag.associated_wallet = w.id
							and pc.accounting_frequency != 'ONCE'
				    `,
					db.Params{},
				)
			}
		},
	}
}

func accountingV4() db.MigrationScript {
	return db.MigrationScript{
		Id: "accountingV4",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					insert into accounting.usage_report(wallet_id, valid_from, report_data)
					with
						expanded_samples as (
							select
								s.wallet_id,
								s.sampled_at,
								s.quota,
								s.retired_tree_usage,
								s.retired_usage,
								s.local_usage,
								s.total_allocated,
								s.tree_usage,
								unnest(s.group_ids) as group_id,
								unnest(s.quota_by_group) as group_quota,
								unnest(s.retried_tree_usage_by_group) as group_retired_tree_usage,
								unnest(s.tree_usage_by_group) as group_tree_usage
							from accounting.wallet_samples_v2 s
						),
						daily_one_per_wallet as (
							select
								s.*,
								row_number() over (
									partition by s.wallet_id, date_trunc('day', s.sampled_at)
									order by s.sampled_at desc
								) as rn
							from expanded_samples s
						),
						wallet_with_subproject_count as (
							select parent.id, count(child.id) as subproject_count
							from
								accounting.wallets_v2 child
								join accounting.allocation_groups ag on ag.associated_wallet = child.id
								join accounting.wallets_v2 parent on ag.parent_wallet = parent.id
							group by parent.id
						),
						first_per_day as (
							select s.* , subproject_count
							from
								daily_one_per_wallet s
								join wallet_with_subproject_count c on s.wallet_id = c.id
							where rn = 1
						)
					select
						s.wallet_id,
						s.sampled_at,
						jsonb_build_object(
							'Wallet', s.wallet_id,
							'Kpis', jsonb_build_object(
								'QuotaAtStart', s.quota,
								'QuotaAtEnd', s.quota,

								'ActiveQuotaAtStart', s.quota,
								'ActiveQuotaAtEnd', s.quota,

								'MaxUsableAtStart', s.quota - s.tree_usage,
								'MaxUsableAtEnd', s.quota - s.tree_usage,

								'LocalUsageAtStart', s.local_usage + s.retired_usage - s.retired_tree_usage,
								'LocalUsageAtEnd', s.local_usage + s.retired_usage - s.retired_tree_usage,

								'TotalUsageAtStart', s.tree_usage + s.retired_tree_usage,
								'TotalUsageAtEnd', s.tree_usage + s.retired_tree_usage,

								'TotalAllocatedAtStart', s.total_allocated,
								'TotalAllocatedAtEnd', s.total_allocated,

								'NextMeaningfulExpiration', '2025-12-31T23:59:59.999Z'
							),
							'SubProjectHealth', jsonb_build_object(
								'Ok', subproject_count,
								'Idle', 0,
								'AtRisk', 0,
								'UnderUtilized', 0,
								'SubProjectCount', subproject_count
							),
							'UsageOverTime', jsonb_build_object(
								'Absolute', jsonb_build_array(jsonb_build_object(
									'Timestamp', to_char(s.sampled_at at time zone 'UTC', 'YYYY-MM-DD') || 'T00:00:00Z',
									'Usage', s.tree_usage + s.retired_tree_usage,
									'UtilizationPercent100', case when s.quota = 0 then 0 else (s.tree_usage::float8 / s.quota::float8) * 100.0 end
								))
							),
							'Dirty', false,
							'ValidFrom', to_char(s.sampled_at at time zone 'UTC', 'YYYY-MM-DD') || 'T00:00:00Z',
							'ValidUntil', null::jsonb
						) as snapshot
					from
						first_per_day s
						join accounting.wallets_v2 w on s.wallet_id = w.id
						join accounting.product_categories pc on w.product_category = pc.id
						join accounting.accounting_units unit on pc.accounting_unit = unit.id
					order by s.sampled_at desc;
			    `,
				db.Params{},
			)
		},
	}
}

func accountingV5() db.MigrationScript {
	return db.MigrationScript{
		Id: "accountingV5",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`alter table accounting.products add column fraction_numerator int`,
				db.Params{},
			)

			db.Exec(
				tx,
				`alter table accounting.products add column fraction_denominator int`,
				db.Params{},
			)
		},
	}
}

func accountingV6() db.MigrationScript {
	return db.MigrationScript{
		Id: "accountingV6",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table accounting.wallets_acc2(
						id bigserial primary key,
						wallet_owner bigint references accounting.wallet_owner,
						product_category bigint references accounting.product_categories,
						consumed bigint default 0 not null,
						locked boolean default false not null,
						last_significant_update_at timestamp with time zone default now() not null,
						promise_demand_ewma int8 not null default 0,
						promise_demand_observed int8 not null default 0,
						promise_demand_trend int8 not null default 0,
						promise_demand_updated_at timestamptz null default null,
						low_balance_notified boolean default false not null,
						unique(wallet_owner, product_category)
					)
			    `,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					create table accounting.promises_acc2(
						id int8 primary key,
						product_category int8 not null references accounting.product_categories(id),
						parent_wallet int8 not null references accounting.wallets_acc2(id),
						child_wallet int8 not null references accounting.wallets_acc2(id),
						start_time timestamptz not null,
						end_time timestamptz not null,
						quota int8 not null,
						grant_id int8 null references "grant".applications(id)
					)
			    `,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					create table accounting.allocations_acc2(
						id int8 primary key,
						product_category int8 not null references accounting.product_categories(id),
						wallet int8 not null references accounting.wallets_acc2(id),
						parent_allocation int8 null references accounting.allocations_acc2(id),
						start_time timestamptz not null,
						end_time timestamptz not null,
						quota_self int8 not null,
						quota_children int8 not null,
						consumed_self int8 not null,
						reserved_children int8 not null,
						retired_quota int8 not null default 0,
						retired_usage int8 not null default 0,
						activated bool not null default false,
						retired bool not null default false,
						grant_id int8 null references "grant".applications(id),
						promise_id int8 null references accounting.promises_acc2(id)
					)
			    `,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					insert into accounting.wallets_acc2(id, wallet_owner, product_category, consumed,
						locked, last_significant_update_at, promise_demand_ewma, promise_demand_observed,
						promise_demand_trend, promise_demand_updated_at, low_balance_notified)
					select id, wallet_owner, product_category, local_usage, was_locked,
						last_significant_update_at, 0, 0, 0, null, low_balance_notified
					from accounting.wallets_v2;
			    `,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					insert into accounting.promises_acc2(id, product_category, parent_wallet,
						child_wallet, start_time, end_time, quota, grant_id)
					select alloc.id, w.product_category, ag.parent_wallet, ag.associated_wallet,
						alloc.allocation_start_time, alloc.allocation_end_time, alloc.quota, alloc.granted_in
					from
						accounting.wallet_allocations_v2 alloc
						join accounting.allocation_groups ag on alloc.associated_allocation_group = ag.id
						join accounting.wallets_v2 w on ag.associated_wallet = w.id
					where
						ag.parent_wallet is not null;
			    `,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					insert into accounting.allocations_acc2(id, product_category, wallet, parent_allocation,
						start_time, end_time, quota_self, quota_children, consumed_self,
						reserved_children, grant_id, promise_id)
					select alloc.id, w.product_category, w.id, null, alloc.allocation_start_time, alloc.allocation_end_time,
						alloc.quota, 0, 0, 0, alloc.granted_in, null
					from
						accounting.wallet_allocations_v2 alloc
						join accounting.allocation_groups ag on alloc.associated_allocation_group = ag.id
						join accounting.wallets_v2 w on ag.associated_wallet = w.id
					where
						ag.parent_wallet is null;
			    `,
				db.Params{},
			)
		},
	}
}
