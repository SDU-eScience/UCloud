package migrations

import db "ucloud.dk/shared/pkg/database"

func privateNetworkIpsV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "privateNetworkIpsV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table app_orchestrator.private_network_ips(
						network     bigint not null references app_orchestrator.private_networks(resource),
						ip_address  text,
						resource    bigint primary key references provider.resource(id)
				    )
			    `,
				db.Params{},
			)
		},
	}
}
