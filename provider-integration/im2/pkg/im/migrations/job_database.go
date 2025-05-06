package migrations

import (
	db "ucloud.dk/shared/pkg/database"
)

func jobDatabaseV1() db.MigrationScript {
	return db.MigrationScript{
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

func jobDatabaseV2() db.MigrationScript {
	return db.MigrationScript{
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

func jobDatabaseV3() db.MigrationScript {
	return db.MigrationScript{
		Id: "jobDatabaseV3",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table if not exists web_sessions(
						job_id text not null,
						rank int not null,
						target_address text not null,
						target_port int not null,
						address text not null,
						suffix text,
						auth_token text,
						flags int not null
					)
			    `,
				db.Params{},
			)
		},
	}
}
