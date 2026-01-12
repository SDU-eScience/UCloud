package migrations

import db "ucloud.dk/shared/pkg/database2"

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
						update accounting.allocation_groups
						set tree_usage = tree_usage + retired_tree_usage;
				    `,
					db.Params{},
				)
			}
		},
	}
}
