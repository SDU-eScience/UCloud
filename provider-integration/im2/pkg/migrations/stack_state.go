package migrations

import db "ucloud.dk/shared/pkg/database"

func stackStateV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "stackStateV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table k8s.stack_state_records(
						stack_scope_hash bytea not null,
						stack_id text not null,
						record_key text not null,
						value jsonb not null,
						revision bigint not null default 0 check (revision >= 0),
						lease_principal_hash bytea,
						lease_holder text,
						lease_token_hash bytea,
						lease_expires_at timestamptz,
						lease_generation bigint not null default 0 check (lease_generation >= 0),
						primary key (stack_scope_hash, stack_id, record_key),
						check (
							(lease_principal_hash is null and lease_token_hash is null and lease_expires_at is null)
							or (lease_principal_hash is not null and lease_token_hash is not null and lease_expires_at is not null)
						)
					)
				`,
				db.Params{},
			)
		},
	}
}
