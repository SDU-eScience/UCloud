package migrations

import db "ucloud.dk/shared/pkg/database"

func stacksDeletionV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "stacksDeletionV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table app_orchestrator.stack_deletion_requests
						add column delete_state_drive boolean not null default false,
						add column entity_resource bigint
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create unique index stack_deletion_one_enduser_request
					on app_orchestrator.stack_deletion_requests(entity_resource)
					where provider_filter is null and entity_resource is not null
				`,
				db.Params{},
			)
		},
	}
}
