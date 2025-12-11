package migrations

import db "ucloud.dk/shared/pkg/database2"

func authV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "authV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					insert into auth.identity_providers(id, title, configuration, counts_as_multi_factor) 
					values (1, 'wayf', '{"type": "wayf"}'::jsonb, true)
					on conflict (id) do nothing 
			    `,
				db.Params{},
			)
		},
	}
}

func authV2() db.MigrationScript {
	return db.MigrationScript{
		Id: "authV2",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`alter table auth.additional_user_info add column if not exists gender text;`,
				db.Params{},
			)
		},
	}
}
