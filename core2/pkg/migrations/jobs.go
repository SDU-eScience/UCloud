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
