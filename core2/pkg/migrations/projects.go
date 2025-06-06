package migrations

import db "ucloud.dk/shared/pkg/database"

func projectsV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "projectsV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table project.projects add column backend_id text unique default null
			    `,
				db.Params{},
			)
		},
	}
}
