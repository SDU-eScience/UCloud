package migrations

import db "ucloud.dk/shared/pkg/database"

func servicesV2() db.MigrationScript {
	return db.MigrationScript{
		Id: "servicesV2",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table app_orchestrator.ingresses
						drop constraint if exists ingresses_target_service_fkey,
						add constraint ingresses_target_service_fkey
							foreign key (target_service)
							references app_orchestrator.services(resource)
							on delete set null
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					alter table app_orchestrator.services
						drop constraint if exists services_internal_network_fkey,
						add constraint services_internal_network_fkey
							foreign key (internal_network)
							references app_orchestrator.private_networks(resource)
							on delete set null
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					alter table app_orchestrator.service_members
						drop constraint if exists service_members_job_fkey,
						add constraint service_members_job_fkey
							foreign key (job)
							references app_orchestrator.jobs(resource)
							on delete cascade
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					create index if not exists services_internal_network_idx
						on app_orchestrator.services(internal_network)
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					create index if not exists ingresses_target_service_idx
						on app_orchestrator.ingresses(target_service)
						where target_service is not null
				`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					create index if not exists service_members_job_idx
						on app_orchestrator.service_members(job)
				`,
				db.Params{},
			)
		},
	}
}

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
