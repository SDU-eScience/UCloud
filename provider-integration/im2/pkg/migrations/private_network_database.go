package migrations

import (
	db "ucloud.dk/shared/pkg/database"
)

func privateNetworkDatabaseV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "privateNetworkDatabaseV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table tracked_private_networks(
						resource_id text not null primary key,
						created_by text not null,
						project_id text,
						resource jsonb not null
					)
			    `,
				db.Params{},
			)
		},
	}
}
