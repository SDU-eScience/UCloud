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
