package migrations

import (
	db "ucloud.dk/pkg/database"
)

func integratedAppsV1() migrationScript {
	return migrationScript{
		Id: "integratedAppsV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table integrated_application_config(
						username text not null,
						project text not null,
						application_name text not null,
						configuration jsonb not null,
						job_id text not null,
						version text not null,
						primary key (username, project, application_name)
					)
			    `,
				db.Params{},
			)
		},
	}
}
