package migrations

import (
	db "ucloud.dk/pkg/database"
)

func uploadsV1() migrationScript {
	return migrationScript{
		Id: "uploadsV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table upload_sessions(
						session text primary key,
						owner_uid int not null,
						conflict_policy text not null,
						path text not null,
						user_data text not null,
						created_at timestamptz not null default now()
					);
			    `,
				db.Params{},
			)
		},
	}
}
