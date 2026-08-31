package migrations

import db "ucloud.dk/shared/pkg/database"

func apiTokensV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "apiTokensV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table api_tokens(
						token_id text primary key,
						token_type text not null,
						owner text not null,
						permissions jsonb not null,
						token_hash bytea not null,
						token_salt bytea not null,
						expires_at timestamptz not null,
						last_used_at timestamptz not null default now()
					)
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`drop table if exists inference_api_keys`,
				db.Params{},
			)
		},
	}
}

func apiTokensV2() db.MigrationScript {
	return db.MigrationScript{
		Id: "apiTokensV2",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`alter table api_tokens add column created_by text not null default ''`,
				db.Params{},
			)
		},
	}
}
