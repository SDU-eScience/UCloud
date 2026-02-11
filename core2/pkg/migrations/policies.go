package migrations

import db "ucloud.dk/shared/pkg/database"

func policiesV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "policiesV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table if not exists project.policies (
					    policy_name text not null,
					    policy_property jsonb not null,
					    project_id text not null references project.projects(id) on delete cascade,
					    primary key (project_id, policy_name)
					)
			    `,
				db.Params{},
			)
		},
	}
}
