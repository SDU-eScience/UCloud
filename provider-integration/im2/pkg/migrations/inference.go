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
					drop table if exists inference_api_keys
			    `,
				db.Params{},
			)

			db.Exec(
				tx,
				`
				create table inference_api_keys (
				    token_id text primary key,
				    owner text not null,
				    token_hash bytea not null,
				    token_salt bytea not null,
				    expires_at timestamptz not null,
				    last_used_at timestamptz not null default now()
				)
			    `,
				db.Params{},
			)
		},
	}
}

func inferenceV3() db.MigrationScript {
	return db.MigrationScript{
		Id: "inferenceV3",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table inference_model(
						name text primary key,
						title text not null,
						capabilities jsonb not null,
						price_cached_input int not null,
						price_input int not null,
						price_output int not null,
						inference_endpoint_path text not null,
						inference_endpoint_model text not null,
						public bool not null,
						available_to jsonb not null
					)
				`,
				db.Params{},
			)
		},
	}
}

func inferenceV4() db.MigrationScript {
	return db.MigrationScript{
		Id: "inferenceV4",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table inference_model add column context_window int
				`,
				db.Params{},
			)
		},
	}
}

func inferenceV5() db.MigrationScript {
	return db.MigrationScript{
		Id: "inferenceV5",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table inference_model add column temperature double precision not null default 0.8
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					alter table inference_model add column top_p double precision not null default 0.1
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					alter table inference_model add column max_completion_tokens int not null default 65536
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					alter table inference_model add column system_prompt text
				`,
				db.Params{},
			)
		},
	}
}
