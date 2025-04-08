package migrations

import (
	db "ucloud.dk/shared/pkg/database"
)

func scriptLogV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "scriptLogV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table if not exists script_log(
						timestamp timestamptz not null,
						id bigserial primary key,
						request text not null,
						script_path text not null,
						stdout text not null,
						stderr text not null,
						status_code int not null,
						success bool not null,
						uid int not null
					)
			    `,
				db.Params{},
			)
		},
	}
}
