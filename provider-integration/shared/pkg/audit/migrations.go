package audit

import (
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/util"
)

func MigrationV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "auditPostgresV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`create schema audit_logs;`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					create table audit_logs.logs(
						request_name text not null,
						received_at timestamptz not null,
						
						request_size bigint not null,
						response_code smallint not null,
						response_time_nanos bigint not null,
						request_body jsonb,
						
						username text,
						user_agent text,
						token_reference text,
						remote_origin text
					) partition by range (received_at)
			    `,
				db.Params{},
			)
		},
	}
}

func MigrationV2() db.MigrationScript {
	return db.MigrationScript{
		Id: "auditPostgresV2",
		Execute: func(tx *db.Transaction) {
			if util.DeploymentName == "IM" {
				db.Exec(
					tx,
					`
					alter table audit_logs.logs 
						add column project_id text default null;
				`,
					db.Params{},
				)
			} else {
				db.Exec(
					tx,
					`
						alter table audit_logs.logs 
							add column project_id text references project.projects(id) default null;
					`,
					db.Params{},
				)
			}
		},
	}
}
