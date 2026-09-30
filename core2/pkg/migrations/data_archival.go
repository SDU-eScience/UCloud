package migrations

import db "ucloud.dk/shared/pkg/database"

func dataArchivalV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "dataArchivalV1",
		Execute: func(tx *db.Transaction) {
			statements := []string{
				`
					create schema if not exists data_archival
				`,
				`
					create table data_archival.datasets (
					    id text not null primary key,
					    drive text not null,
					    created_at timestamp with time zone not null default now(),
					    created_by text not null references auth.principals(id),
					    source_project_id text not null references project.project(id),
					);
				`,
				`
					create table data_archival.publications (
					    id text not null primary key,
					    doi text,
					    ark_id text,
					    dataset text not null references data_archival.datasets(id),
					    metadata jsonb not null,
   					    current_state text not null default 'DRAFT'
					)
				`,
			}
			for _, statement := range statements {
				db.Exec(tx, statement, db.Params{})
			}
		},
	}
}
