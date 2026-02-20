package migrations

import db "ucloud.dk/shared/pkg/database"

func grantV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "grantsV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`create table "grant"."user_application_visits" (
							application_id bigint not null references "grant". "applications" on delete cascade, 
							username text not null references auth.principals,
							last_visited_at timestamp not null, 
							primary key(application_id, username)
						);
			`, db.Params{},
			)
		},
	}
}

func grantV2() db.MigrationScript {
	return db.MigrationScript{
		Id: "grantsV2",
		Execute: func(tx *db.Transaction) {
			statements := []string{
				`
					alter table "grant".applications
					add column project_id varchar(255);
				`,
				`
					alter table "grant".applications
					add constraint application_project_id_fkey
					foreign key (project_id)
					references "project".projects(id)
					on delete set null;
				`,
			}
			for _, statement := range statements {
				db.Exec(tx, statement, db.Params{})
			}
		},
	}
}
