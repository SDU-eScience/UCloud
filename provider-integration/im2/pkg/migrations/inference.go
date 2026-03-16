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

func inferenceV2() db.MigrationScript {
	return db.MigrationScript{
		Id: "inferenceV2",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
				drop table inference_api_keys
			    `,
				db.Params{},
			)

			db.Exec(
				tx,
				`
				create table inference_api_keys (
				    owner text not null,
				    token_hash bytea not null,
				    token_salt bytea not null,
				    expires_at timestamptz not null
				)
			    `,
				db.Params{},
			)
		},
	}
}
