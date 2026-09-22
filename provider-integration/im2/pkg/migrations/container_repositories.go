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

func containerRepositoriesV2() db.MigrationScript {
	return db.MigrationScript{
		Id: "containerRepositoriesV2",
		Execute: func(tx *db.Transaction) {
			db.Exec(tx, `
				create table if not exists container_repository_accounting(
					repository_id text primary key,
					repository_name text not null,
					owner_type text not null,
					username text not null,
					project_id text not null,
					category text not null,
					exact_bytes bigint not null,
					reported_usage bigint not null
				)
			`, db.Params{})
		},
	}
}
