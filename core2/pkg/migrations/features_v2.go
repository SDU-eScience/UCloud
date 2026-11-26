package migrations

import db "ucloud.dk/shared/pkg/database"

func featuresV2() db.MigrationScript {
	return db.MigrationScript{
		Id: "featuresV2",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table features.global_enabled(
						feature text primary key,
						enabled_by text not null
							references auth.principals(id),
						enabled_at timestamptz not null default now()
					)
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create table features.user_overrides(
						username text not null
							references auth.principals(id),
						feature text not null,
						enabled boolean not null,
						updated_at timestamptz not null default now(),
						primary key (username, feature)
					)
				`,
				db.Params{},
			)
		},
	}
}
