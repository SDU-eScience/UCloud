package migrations

import db "ucloud.dk/shared/pkg/database"

func containerRepositoriesV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "containerRepositoriesV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table file_orchestrator.container_repositories(
						name text not null,
						provider text not null,
						resource bigint primary key references provider.resource(id),
						unique(provider, name)
					)
				`,
				db.Params{},
			)
		},
	}
}
