package migrations

import db "ucloud.dk/shared/pkg/database"

func kubevirtV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "kubevirtV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table k8s.vmagents(
						job_id text not null primary key,
						srv_token text not null,
						agent_token text not null
					)
			    `,
				db.Params{},
			)
		},
	}
}

func kubevirtV2() db.MigrationScript {
	return db.MigrationScript{
		Id: "kubevirtV2",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table if exists k8s.vmagents rename to job_introspection_tokens
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					alter table k8s.job_introspection_tokens rename column agent_token to token
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					alter table k8s.job_introspection_tokens rename column srv_token to vma_srv_token
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					alter table k8s.job_introspection_tokens alter column vma_srv_token drop not null
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					create unique index job_introspection_tokens_token_idx on k8s.job_introspection_tokens(token)
				`,
				db.Params{},
			)
		},
	}
}
