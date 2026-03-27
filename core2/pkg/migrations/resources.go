package migrations

import db "ucloud.dk/shared/pkg/database"

func resourcesV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "resourcesV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`alter table provider.resource add column labels jsonb default null`,
				db.Params{},
			)
		},
	}
}
