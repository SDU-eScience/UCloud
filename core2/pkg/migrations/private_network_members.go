package migrations

import db "ucloud.dk/shared/pkg/database"

func privateNetworksV3() db.MigrationScript {
	return db.MigrationScript{
		Id: "privateNetworksV3",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table app_orchestrator.private_networks
					add column members bigint[] not null default '{}'
			    `,
				db.Params{},
			)
		},
	}
}
