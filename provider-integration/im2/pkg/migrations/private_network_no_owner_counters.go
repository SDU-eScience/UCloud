package migrations

import (
	db "ucloud.dk/shared/pkg/database"
)

func privateNetworkDatabaseV5() db.MigrationScript {
	return db.MigrationScript{
		Id: "privateNetworkDatabaseV5",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					drop table private_network_owner_counters
				`,
				db.Params{},
			)
		},
	}
}
