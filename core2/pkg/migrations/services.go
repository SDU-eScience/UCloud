package migrations

import db "ucloud.dk/shared/pkg/database"

func servicesV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "servicesV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table app_orchestrator.services(
						name                    text not null,
						ports                   jsonb not null,
						internal_network        bigint references app_orchestrator.private_networks(resource),
						provisioning_state      text not null default 'PREPARING',
						status_message          text,
						internal_dns_name       text,
						backends                jsonb,
						resource                bigint primary key references provider.resource(id)
					)
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					create table app_orchestrator.service_members(
						service bigint not null references app_orchestrator.services(resource) on delete cascade,
						job     bigint not null references app_orchestrator.jobs(resource),
						primary key (service, job)
					)
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					alter table app_orchestrator.ingresses
						add column target_service bigint references app_orchestrator.services(resource),
						add column target_port text
				`,
				db.Params{},
			)
		},
	}
}
