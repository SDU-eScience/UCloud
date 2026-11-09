package migrations

import db "ucloud.dk/shared/pkg/database"

func customApplicationsV4() db.MigrationScript {
	return db.MigrationScript{
		Id: "customApplicationsV4",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create extension if not exists btree_gist
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					alter table app_store.custom_applications
						add constraint custom_workspace_flavor_names exclude using gist (
							coalesce(project_id, created_by) with =,
							custom_group_id with =,
							lower(flavor_name) with =,
							name with <>
						)
				`,
				db.Params{},
			)
		},
	}
}
