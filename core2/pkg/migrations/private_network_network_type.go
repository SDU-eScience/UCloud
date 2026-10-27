package migrations

import db "ucloud.dk/shared/pkg/database"

func privateNetworksV4() db.MigrationScript {
	return db.MigrationScript{
		Id: "privateNetworksV4",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table app_orchestrator.private_networks
					add column network_type text not null default 'ethernet'
					check (network_type in ('ethernet', 'infiniband'))
				`,
				db.Params{},
			)
		},
	}
}
