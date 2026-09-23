package migrations

import db "ucloud.dk/shared/pkg/database"

func applicationEditorV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "applicationEditorV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table app_store.applications
					add column source_application text
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create table app_store.application_render_attempts(
						username text not null references auth.principals(id) on delete cascade,
						attempted_at timestamptz not null default now()
					)
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create index application_render_attempts_user_time
					on app_store.application_render_attempts(username, attempted_at)
				`,
				db.Params{},
			)
		},
	}
}
