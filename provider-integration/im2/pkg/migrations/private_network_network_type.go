package migrations

import db "ucloud.dk/shared/pkg/database"

func privateNetworkDatabaseV3() db.MigrationScript {
	return db.MigrationScript{
		Id: "privateNetworkDatabaseV3",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table tracked_private_networks
					add column network_type text not null default 'ethernet'
					check (network_type in ('ethernet', 'infiniband'))
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					create index tracked_private_networks_type_cidr_idx
					on tracked_private_networks(network_type, cidr_block)
				`,
				db.Params{},
			)
		},
	}
}
