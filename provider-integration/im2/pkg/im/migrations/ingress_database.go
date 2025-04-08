package migrations

import (
	db "ucloud.dk/pkg/database"
)

func ingressDatabaseV1() migrationScript {
	return migrationScript{
		Id: "ingressDatabaseV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table tracked_ingresses(
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
					create table ingresses(
						domain text not null primary key,
						owner text not null
					)
			    `,
				db.Params{},
			)
		},
	}
}
