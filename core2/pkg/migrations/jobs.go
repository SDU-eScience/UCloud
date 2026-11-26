package migrations

import db "ucloud.dk/shared/pkg/database"

func jobsV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "jobsV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table app_orchestrator.jobs add column hostname text default null
			    `,
				db.Params{},
			)
		},
	}
}

func jobsV2() db.MigrationScript {
	return db.MigrationScript{
		Id: "jobsV2",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create index if not exists resource_jobs_project_created_idx
					on provider.resource (project, created_at)
					where type = 'job' and project is not null
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create index if not exists resource_jobs_user_created_idx
					on provider.resource (created_by, created_at)
					where type = 'job' and project is null
				`,
				db.Params{},
			)
		},
	}
}
