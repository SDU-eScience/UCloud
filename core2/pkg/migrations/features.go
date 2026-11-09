package migrations

import db "ucloud.dk/shared/pkg/database"

func featuresV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "featuresV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create schema if not exists features
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create table features.project_grants(
						feature text not null,
						project_id varchar(255) not null
							references project.projects
							on delete cascade,
						granted_by varchar(255) not null
							references auth.principals,
						granted_at timestamptz not null default now(),
						primary key (feature, project_id)
					)
				`,
				db.Params{},
			)
		},
	}
}
