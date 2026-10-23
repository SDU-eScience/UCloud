package migrations

import db "ucloud.dk/shared/pkg/database"

func privateNetworkIpsV3() db.MigrationScript {
	return db.MigrationScript{
		Id: "privateNetworkIpsV3",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create index if not exists private_network_ips_network_idx
					on app_orchestrator.private_network_ips(network)
			    `,
				db.Params{},
			)
		},
	}
}
