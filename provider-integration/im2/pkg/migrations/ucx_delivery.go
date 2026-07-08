package migrations

import db "ucloud.dk/shared/pkg/database"

func ucxDeliveryV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "ucxDeliveryV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table ucx_executable_cache_apps(
						app_name text not null,
						app_version text not null,
						manifest_url text not null,
						public_key text not null,
						binary_name text not null,
						last_checked_at timestamptz,
						last_error text,
						primary key (app_name, app_version)
					)
				`,
				db.Params{},
			)
		},
	}
}
