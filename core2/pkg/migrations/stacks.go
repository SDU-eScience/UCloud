package migrations

import db "ucloud.dk/shared/pkg/database"

func stacksV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "stacksV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table app_orchestrator.stack_deletion_requests(
					    request_id bigserial primary key,
						stack_id text not null,
						provider_filter text,
						activation_time timestamptz,
						owner_created_by text not null,
						owner_project text
					)
			    `,
				db.Params{},
			)
		},
	}
}
