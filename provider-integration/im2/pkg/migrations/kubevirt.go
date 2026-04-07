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
