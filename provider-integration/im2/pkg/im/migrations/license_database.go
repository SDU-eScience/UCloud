package migrations

import (
	db "ucloud.dk/pkg/database"
)

func licenseDatabaseV1() migrationScript {
	return migrationScript{
		Id: "licenseDatabaseV1",
		Execute: func(ctx *db.Transaction) {
			db.Exec(ctx, `
				alter table generic_license_servers
				rename to license_servers
			`, db.Params{})

			db.Exec(ctx, `
				drop table generic_license_instances
			`, db.Params{})

			db.Exec(ctx, `
				create table tracked_license_servers(
					resource_id text not null primary key,
					created_by text not null,
					project_id text,
					product_id text not null,
					product_category text not null,
					resource jsonb not null
				)
			`, db.Params{})

			db.Exec(ctx, `
				alter table license_servers drop column category
			`, db.Params{})
		},
	}
}
