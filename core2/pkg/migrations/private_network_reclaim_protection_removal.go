package migrations

import db "ucloud.dk/shared/pkg/database"

func privateNetworksV6() db.MigrationScript {
	return db.MigrationScript{
		Id: "privateNetworksV6",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table app_orchestrator.private_networks
					drop column reclaim_protection
				`,
				db.Params{},
			)
		},
	}
}
