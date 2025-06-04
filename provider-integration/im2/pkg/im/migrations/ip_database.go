package migrations

import (
	db "ucloud.dk/shared/pkg/database"
)

func ipDatabaseV1() db.MigrationScript {
	return db.MigrationScript{
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

func ipDatabaseV2() db.MigrationScript {
	return db.MigrationScript{
		Id: "ipDatabaseV2",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table ip_pool add column private_subnet text not null default ''
			    `,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					update ip_pool set private_subnet = subnet where true
			    `,
				db.Params{},
			)
		},
	}
}
