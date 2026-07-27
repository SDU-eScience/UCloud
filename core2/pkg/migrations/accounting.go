package migrations

import (
	"strings"

	db "ucloud.dk/shared/pkg/database"
)

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

/*
func accountingV7() db.MigrationScript {
	return db.MigrationScript{
		Id: "accountingV7",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create unique index wallets_v2_owner_category_unique
						on accounting.wallets_v2(wallet_owner, product_category);
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create unique index allocation_groups_wallet_parent_unique
						on accounting.allocation_groups(associated_wallet, coalesce(parent_wallet, 0));
				`,
				db.Params{},
			)
		},
	}
}
*/

func accountingV6() db.MigrationScript {
	return db.MigrationScript{
		Id: "accountingV6",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table accounting.scoped_usage add column if not exists 
						resource_suffix text generated always as (regexp_replace(key, '.*\n'::text, ''::text)) stored;
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					alter table accounting.scoped_usage
						add column wallet_id int8 references accounting.wallets_v2(id),
						add column last_updated_at timestamptz default now() not null;
				`,
				db.Params{},
			)

			script := `
				delete from accounting.scoped_usage
				where resource_suffix ilike '202%-%'; -- Legacy VMs

				-- Legacy VMs
				delete from accounting.scoped_usage
				where resource_suffix ilike '%-2024-%';

				-- Legacy drives
				delete from accounting.scoped_usage
				where resource_suffix ilike 'hippo-%';

				update accounting.scoped_usage
				set wallet_id = w.id
				from
					provider.resource r
					join accounting.products p on p.id = r.product
					join accounting.product_categories pc on pc.id = p.category
					join accounting.wallet_owner wo  on
					(r.project is not null and wo.project_id = r.project)
						or (r.project is null and wo.username = r.created_by)
					join accounting.wallets_v2 w on w.wallet_owner = wo.id and w.product_category = pc.id
				where
					resource_suffix ilike 'drive-%'
				  and r.id = split_part(replace(replace(resource_suffix, 'aau-k8', ''), 'sdu-odense', ''), '-', 3)::bigint;

				delete from accounting.scoped_usage where resource_suffix ilike 'drive-%' and wallet_id is null;

				with translation_table as (
					select old.id as old_id, new.id as new_id
					from
						accounting.products old
						join accounting.product_categories old_category on old.category = old_category.id
						join accounting.products new on old.name = new.name
						join accounting.product_categories new_category on 
							new.category = new_category.id 
							and new_category.category = old_category.category 
							and new_category.provider != old_category.provider
					where
						old_category.provider = 'ucloud'
						and old_category.product_type = 'COMPUTE'
						and old.name != 'terminal'
						and old.name != 'syncthing'
				)
				update provider.resource
				set product = new_id
				from translation_table
				where product = old_id;

				update accounting.scoped_usage
				set wallet_id = w.id, last_updated_at = coalesce(j.started_at, r.created_at)
				from
					provider.resource r
					join app_orchestrator.jobs j on r.id = j.resource
					join accounting.products p on p.id = r.product
					join accounting.product_categories pc on pc.id = p.category
					join accounting.wallet_owner wo  on
					(r.project is not null and wo.project_id = r.project)
						or (r.project is null and wo.username = r.created_by)
					join accounting.wallets_v2 w on w.wallet_owner = wo.id and w.product_category = pc.id
				where
					resource_suffix ilike 'job-%'
				  and r.id = split_part(resource_suffix, '-', 2)::bigint;
				;

				update accounting.scoped_usage c
				set wallet_id = w.id, last_updated_at = coalesce(j.started_at, r.created_at)
				from
					app_orchestrator.jobs j
					join provider.resource r on r.id = j.resource
					join accounting.products p on p.id = r.product
					join accounting.product_categories pc on pc.id = p.category
					join accounting.wallet_owner wo  on
					(r.project is not null and wo.project_id = r.project)
						or (r.project is null and wo.username = r.created_by)
					join accounting.wallets_v2 w on w.wallet_owner = wo.id and w.product_category = pc.id
				where
					c.resource_suffix = j.resource::text;

				-- These are all missing
				delete from accounting.scoped_usage where resource_suffix ilike 'job-%' and wallet_id is null;

				-- At this point, these are guaranteed to not be valid anymore
				delete from accounting.scoped_usage
				where
					resource_suffix ~ '^[0-9.]+$'
					and wallet_id is null;

				-- Production databases do not require this, but some development databases might
				delete from accounting.scoped_usage where wallet_id is null;
			`

			for _, statement := range strings.Split(script, ";") {
				db.Exec(tx, statement, db.Params{})
			}

			// Existing rows must be assigned to wallets before this statement. A non-empty legacy table intentionally
			// makes the migration fail safely until the deployment-specific key migration is inserted above.
			db.Exec(
				tx,
				`alter table accounting.scoped_usage alter column wallet_id set not null`,
				db.Params{},
			)
			db.Exec(
				tx,
				`alter table accounting.scoped_usage drop constraint scoped_usage_pkey`,
				db.Params{},
			)
			db.Exec(
				tx,
				`alter table accounting.scoped_usage add primary key (wallet_id, key)`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					with this_should_not_exist as (
						select id from accounting.allocation_groups where associated_wallet = parent_wallet
					)
					delete from accounting.wallet_allocations_v2
					using this_should_not_exist as bad
					where associated_allocation_group = bad.id
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`delete from accounting.allocation_groups where parent_wallet = associated_wallet`,
				db.Params{},
			)
		},
	}
}
