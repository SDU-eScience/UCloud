package migrations

import db "ucloud.dk/shared/pkg/database"

func customApplicationsV3() db.MigrationScript {
	return db.MigrationScript{
		Id: "customApplicationsV3",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table app_store.custom_application_groups
						add column logo jsonb
				`,
				db.Params{},
			)
		},
	}
}
