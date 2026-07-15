package migrations

import db "ucloud.dk/shared/pkg/database"

func activityCatalogV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "activityCatalogV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(tx, `
				create table fs_activity_events(
				    event_id bigserial primary key,
					kind smallint not null,
					operation text not null,
					started_at timestamptz not null default now(),
					read_only boolean not null default false,
					performed_by text,
					job_id text,
					pod text default null,
					node text default null
				);

				create table fs_activity_event_targets(
					event_id bigint not null references fs_activity_events(event_id) on delete cascade,
					target_role text not null default '',
					drive_id text not null,
					ucloud_path text not null
				);

				create index fs_activity_events_completed_at_idx
					on fs_activity_events(started_at);
				create index fs_activity_event_targets_drive_id
					on fs_activity_event_targets(drive_id);
				create index fs_activity_event_targets_path
					on fs_activity_event_targets(ucloud_path);
			`, db.Params{})
		},
	}
}

func activityCatalogV2() db.MigrationScript {
	return db.MigrationScript{
		Id: "activityCatalogV2",
		Execute: func(tx *db.Transaction) {
			db.Exec(tx, `
				create index fs_activity_event_targets_event_id
					on fs_activity_event_targets(event_id);

				create table fs_metadata_scan_state(
					drive_id text not null references tracked_drives(drive_id) on delete cascade,
					ucloud_path text not null,
					last_submitted_at timestamptz not null default timestamp 'epoch',
					last_completed_at timestamptz not null default timestamp 'epoch',
					primary key (drive_id, ucloud_path)
				);
			`, db.Params{})
		},
	}
}
