package migrations

import db "ucloud.dk/shared/pkg/database2"

func usageV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "usageV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table accounting.usage_report(
						wallet_id int not null references accounting.wallets_v2(id),
						valid_from timestamptz not null,
						report_format int not null default 1,
						report_data jsonb not null,
						primary key (wallet_id, valid_from)
					);
			    `,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					create index usage_wallet on accounting.usage_report(wallet_id);
			    `,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					create index usage_timestamp on accounting.usage_report(valid_from);
			    `,
				db.Params{},
			)
		},
	}
}

func usageV2() db.MigrationScript {
	return db.MigrationScript{
		Id: "usageV2",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table accounting.product_categories drop constraint product_categories_provider_fkey;
			    `,
				db.Params{},
			)
		},
	}
}
