package migrations

import db "ucloud.dk/shared/pkg/database"

func containerRegistriesV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "containerRegistriesV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(tx, `
				create table tracked_container_registries(
					resource_id text primary key,
					repository_name text not null unique,
					resource jsonb not null
				)
			`, db.Params{})
			db.Exec(tx, `alter table api_tokens add column created_by text`, db.Params{})
		},
	}
}
