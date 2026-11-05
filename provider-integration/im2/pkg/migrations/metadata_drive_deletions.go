package migrations

import db "ucloud.dk/shared/pkg/database"

func metadataDriveDeletionsV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "metadataDriveDeletionsV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table fs_metadata_drive_deletions(
						drive_id text primary key,
						resource jsonb not null,
						source_path text not null,
						source_device text not null,
						source_inode text not null,
						drive_staging_name text not null,
						catalog_staging_name text not null,
						drive_staged boolean not null default false,
						catalog_observed boolean not null default false,
						catalog_device text not null default '',
						catalog_inode text not null default '',
						catalog_staged boolean not null default false,
						accounting_reported boolean not null default false,
						created_at timestamptz not null default now()
					)
				`,
				db.Params{},
			)
		},
	}
}
