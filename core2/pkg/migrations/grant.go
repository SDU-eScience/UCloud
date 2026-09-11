package migrations

import db "ucloud.dk/shared/pkg/database"

func grantV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "grantsV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`create table "grant"."user_application_visits" (
							application_id bigint not null references "grant". "applications" on delete cascade, 
							username text not null references auth.principals,
							last_visited_at timestamp not null, 
							primary key(application_id, username)
						);
			`, db.Params{},
			)
		},
	}
}

func grantV2() db.MigrationScript {
	return db.MigrationScript{
		Id: "grantsV2",
		Execute: func(tx *db.Transaction) {
			statements := []string{
				`
					alter table "grant".applications
					add column project_id varchar(255);
				`,
				`
					alter table "grant".applications
					add constraint application_project_id_fkey
					foreign key (project_id)
					references "project".projects(id)
					on delete set null;
				`,
			}
			for _, statement := range statements {
				db.Exec(tx, statement, db.Params{})
			}
		},
	}
}

func grantV3() db.MigrationScript {
	return db.MigrationScript{
		Id: "grantsV3",
		Execute: func(tx *db.Transaction) {
			statement := `
				alter table "grant".forms
				add column form_type text not null default 'plain_text';
			`
			db.Exec(tx, statement, db.Params{})
		},
	}
}

func grantV4() db.MigrationScript {
	return db.MigrationScript{
		Id: "grantsV4",
		Execute: func(tx *db.Transaction) {
			statements := []string{
				`
				    alter table "grant".templates
				    drop constraint templates_pkey;`,
				`
					alter table "grant".templates
					add column revision_number integer not null default 1;
				`,
				`
					alter table "grant".templates
					add constraint templates_project_id_revision_number_key
					primary key (project_id, revision_number);
				`,
			}
			for _, statement := range statements {
				db.Exec(tx, statement, db.Params{})
			}
		},
	}
}

func grantV5() db.MigrationScript {
	return db.MigrationScript{
		Id: "grantsV5",
		Execute: func(tx *db.Transaction) {
			statements := []string{
				`
					create table "grant".gifts_exclude_criteria
					(
						gift_id      bigint not null
							references "grant".gifts,
						type         text   not null,
						applicant_id text
					);
				`,
				`
					create unique index gifts_exclude_criteria_uniq
					on "grant".gifts_exclude_criteria (gift_id, type, COALESCE(applicant_id, ''::text));
				`,
			}
			for _, statement := range statements {
				db.Exec(tx, statement, db.Params{})
			}
		},
	}
}
