package migrations

import db "ucloud.dk/shared/pkg/database"

func tasksV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "tasksV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table task.tasks_v2
					add column meta jsonb
				`,
				db.Params{},
			)
		},
	}
}
