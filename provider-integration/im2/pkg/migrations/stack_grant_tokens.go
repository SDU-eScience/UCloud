package migrations

import db "ucloud.dk/shared/pkg/database"

func stackGrantTokensV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "stackGrantTokensV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table k8s.stack_grant_tokens(
						job_id text primary key,
						stack_instance text not null,
						owner_created_by text not null,
						owner_project text,
						provider text not null,
						token_hash bytea not null,
						token_salt bytea not null,
						created_at timestamptz not null default now()
					)
				`,
				db.Params{},
			)
		},
	}
}
