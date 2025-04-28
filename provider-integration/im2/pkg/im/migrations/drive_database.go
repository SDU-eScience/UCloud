package migrations

import (
	db "ucloud.dk/shared/pkg/database"
)

func driveDatabaseV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "driveDatabaseV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table tracked_drives(
						drive_id text not null primary key,
						product_id text not null,
						product_category text not null,
						created_by text not null,
						project_id text,
						provider_generated_id text,
						resource jsonb not null
					)
			    `,
				db.Params{},
			)
		},
	}
}

func driveDatabaseV2() db.MigrationScript {
	return db.MigrationScript{
		Id: "driveDatabaseV2",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table tracked_drives
					add column last_scan_completed_at timestamptz not null default now() - cast('24 hours' as interval)
			    `,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					alter table tracked_drives
					add column last_scan_submitted_at timestamptz not null default now() - cast('24 hours' as interval)
			    `,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					create table tracked_drives_scan_timer(
					    zero int primary key,
						time timestamptz not null
					)
			    `,
				db.Params{},
			)
		},
	}
}

func driveDatabaseV3() db.MigrationScript {
	return db.MigrationScript{
		Id: "driveDatabaseV3",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table tracked_drives
					add column search_index bytea default null
			    `,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					alter table tracked_drives
					add column search_next_bucket_count int not null default 4096
			    `,
				db.Params{},
			)
		},
	}
}
