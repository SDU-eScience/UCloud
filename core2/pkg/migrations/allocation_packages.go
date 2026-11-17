package migrations

import db "ucloud.dk/shared/pkg/database"

func accountingAllocationPackagesV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "accountingAllocationPackagesV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table "grant".requested_resources
					add column allocation_package bool not null default false
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					alter table accounting.wallet_allocations_v2
					add column allocation_package bool not null default false
				`,
				db.Params{},
			)
		},
	}
}
