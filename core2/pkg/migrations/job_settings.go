package migrations

import db "ucloud.dk/shared/pkg/database"

func jobSettingsV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "jobSettingsV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`create table app_orchestrator.job_settings (
    				username text not null references auth.principals(id),
    				toggled boolean not null,
    				sample_rate_value text,
    				primary key (username)
    			);
				`,
				db.Params{},
			)
		},
	}
}
