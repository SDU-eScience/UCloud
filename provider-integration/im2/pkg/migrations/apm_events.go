package migrations

import (
	db "ucloud.dk/pkg/database"
)

func apmEventsV1() migrationScript {
	return migrationScript{
		Id: "apmEventsV1",
		Execute: func(ctx *db.Transaction) {
			db.Exec(
				ctx,
				`
					create table apm_events_replay_from(
					    provider_id text not null primary key,
						last_update timestamptz not null default now()
					)
				`,
				db.Params{},
			)

			db.Exec(
				ctx,
				`
					create table tracked_allocations(
						owner_username text not null default '',
						owner_project text not null default '',
						category text not null,
						combined_quota int8 not null,
						locked bool not null,
						last_update timestamptz not null,
						primary key (category, owner_username, owner_project)
					)
				`,
				db.Params{},
			)

			db.Exec(
				ctx,
				`
					create table tracked_projects(
						project_id text primary key,
						ucloud_project jsonb not null,
						last_update timestamptz not null default now()
					)
				`,
				db.Params{},
			)
		},
	}
}
