package migrations

import db "ucloud.dk/shared/pkg/database"

func sharesV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "sharesV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`alter table file_orchestrator.shares_links drop constraint shares_links_pkey`,
				db.Params{},
			)

			db.Exec(
				tx,
				`alter table file_orchestrator.shares_links alter column token type text using token::text`,
				db.Params{},
			)

			db.Exec(
				tx,
				`alter table file_orchestrator.shares_links add primary key (token)`,
				db.Params{},
			)
		},
	}
}
