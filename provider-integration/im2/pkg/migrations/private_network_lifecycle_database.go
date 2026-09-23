package migrations

import (
	db "ucloud.dk/shared/pkg/database"
)

func privateNetworkDatabaseV2() db.MigrationScript {
	return db.MigrationScript{
		Id: "privateNetworkDatabaseV2",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table tracked_private_networks add column cidr_block inet
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					alter table tracked_private_networks add column state text not null default 'ready'
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					alter table tracked_private_networks add constraint tracked_private_networks_state_check
						check (state in ('provisioning', 'ready', 'deleting'))
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					alter table tracked_private_networks add column workspace_id text
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					update tracked_private_networks
					set
						workspace_id = case
							when project_id is not null and project_id != '' then 'project:' || project_id
							else 'user:' || created_by
						end
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					alter table tracked_private_networks alter column workspace_id set not null
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					create table private_network_ip_reservations(
						network_id text not null references tracked_private_networks(resource_id) on delete restrict,
						ip inet not null,
						reservation_id text not null unique,
						workspace_id text not null,
						created_by text not null,
						project_id text,
						resource jsonb not null,
						notified boolean not null default false,
						primary key(network_id, ip)
					)
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					create index private_network_ip_reservations_reservation_id_idx on private_network_ip_reservations(reservation_id)
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					create table private_network_ip_leases(
						network_id text not null references tracked_private_networks(resource_id) on delete restrict,
						ip inet not null,
						mac_address macaddr,
						job_id text not null,
						rank int not null,
						pinned boolean not null default false,
						reservation_id text references private_network_ip_reservations(reservation_id) on delete restrict,
						state text not null,
						workspace_id text not null,
						created_at timestamptz not null default now(),
						primary key(network_id, ip),
						unique(network_id, job_id, rank),
						unique(network_id, mac_address),
						check (state in ('pending', 'releasing'))
					)
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					create index private_network_ip_leases_job_id_idx on private_network_ip_leases(job_id)
				`,
				db.Params{},
			)
		},
	}
}
