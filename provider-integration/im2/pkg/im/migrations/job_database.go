package migrations

import (
	db "ucloud.dk/pkg/database"
)

func jobDatabaseV1() migrationScript {
	return migrationScript{
		Id: "jobDatabaseV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table tracked_jobs(
						job_id text not null primary key,
						created_by text not null,
						project_id text,
						product_id text not null,
						product_category text not null,
						state text not null,
						resource jsonb not null -- Kept updated until terminal state is sent 
					)
			    `,
				db.Params{},
			)
		},
	}
}

func jobDatabaseV2() migrationScript {
	return migrationScript{
		Id: "jobDatabaseV2",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table tracked_jobs add column allocated_nodes text[] default null
			    `,
				db.Params{},
			)
		},
	}
}
