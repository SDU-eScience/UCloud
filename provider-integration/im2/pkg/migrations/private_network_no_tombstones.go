package migrations

import (
	db "ucloud.dk/shared/pkg/database"
)

func privateNetworkDatabaseV4() db.MigrationScript {
	return db.MigrationScript{
		Id: "privateNetworkDatabaseV4",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					update private_network_ip_leases
					set
						state = 'pending'
					where
						state = 'attached'
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					alter table private_network_ip_leases drop constraint private_network_ip_leases_state_check
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					alter table private_network_ip_leases add constraint private_network_ip_leases_state_check
						check (state in ('pending', 'releasing'))
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					drop table private_network_tombstones
				`,
				db.Params{},
			)
		},
	}
}
