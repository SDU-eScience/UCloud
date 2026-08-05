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
				`
					insert into api_tokens(token_id, token_type, owner, permissions, token_hash, token_salt, expires_at, last_used_at)
					select token_id, 'inference', owner, '[{"name":"inference","action":"use"}]'::jsonb, token_hash, token_salt, expires_at, last_used_at
					from inference_api_keys
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`drop table inference_api_keys`,
				db.Params{},
			)
		},
	}
}
