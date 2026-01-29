package migrations

import db "ucloud.dk/shared/pkg/database2"

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
