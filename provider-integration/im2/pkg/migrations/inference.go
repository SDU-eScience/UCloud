package migrations

import db "ucloud.dk/shared/pkg/database"

func inferenceV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "inferenceV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`create table inference_api_keys(api_key text primary key, owner text not null)`,
				db.Params{},
			)
		},
	}
}
