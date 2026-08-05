package migrations

import db "ucloud.dk/shared/pkg/database"

func containerRepositoriesV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "containerRepositoriesV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(tx, `
				alter table if exists tracked_container_registries
					rename to tracked_container_repositories;

				create table if not exists tracked_container_repositories(
					resource_id text primary key,
					repository_name text not null unique,
					resource jsonb not null
				)
			`, db.Params{})
			db.Exec(tx, `alter table api_tokens add column if not exists created_by text`, db.Params{})
		},
	}
}
