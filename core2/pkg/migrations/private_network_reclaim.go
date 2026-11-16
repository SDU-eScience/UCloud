package migrations

import db "ucloud.dk/shared/pkg/database"

func privateNetworksV5() db.MigrationScript {
	return db.MigrationScript{
		Id: "privateNetworksV5",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table app_orchestrator.private_networks
					add column last_used_at timestamptz not null default now(),
					add column reclaim_state text not null default 'active'
						check (reclaim_state in ('active', 'pending', 'reclaiming')),
					add column reclaim_after timestamptz,
					add column reclaim_protection boolean not null default false
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					create index private_networks_reclaim_idx
					on app_orchestrator.private_networks(network_type, reclaim_state, last_used_at)
				`,
				db.Params{},
			)
		},
	}
}
