package migrations

import db "ucloud.dk/shared/pkg/database"

func privateNetworkIpsV2() db.MigrationScript {
	return db.MigrationScript{
		Id: "privateNetworkIpsV2",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table app_orchestrator.private_network_ips
					add column requested_ip text
			    `,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					update app_orchestrator.private_network_ips
					set requested_ip = ip_address
					where ip_address is not null
			    `,
				db.Params{},
			)
		},
	}
}
