package migrations

import (
	db "ucloud.dk/pkg/database"
)

func ipDatabaseV1() migrationScript {
	return migrationScript{
		Id: "ipDatabaseV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table tracked_ips(
						resource_id text not null primary key,
						created_by text not null,
						project_id text,
						product_id text not null,
						product_category text not null,
						resource jsonb not null
					)
			    `,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					create table ip_pool(
					    subnet text primary key 
					)
			    `,
				db.Params{},
			)
		},
	}
}
