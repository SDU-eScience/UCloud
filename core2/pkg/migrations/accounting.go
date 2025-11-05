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
