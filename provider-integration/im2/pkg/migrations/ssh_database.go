package migrations

import (
	db "ucloud.dk/shared/pkg/database"
)

func sshDatabaseV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "sshDatabaseV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table tracked_ssh_keys(
						owner text not null,
						key_id text not null,
						created_at timestamptz not null,
						fingerprint text not null,
						title text not null,
						key text not null,
						primary key(owner, key_id)
					)
				`,
				db.Params{},
			)
		},
	}
}
