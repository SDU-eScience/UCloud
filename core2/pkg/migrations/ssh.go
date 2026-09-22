package migrations

import db "ucloud.dk/shared/pkg/database"

func sshV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "sshV1",
		Execute: func(tx *db.Transaction) {
			statements := []string{
				// Rename all duplicates.
				// Given the titles currently in prod it seems efficient enough just to add _x
				`
					WITH duplicates AS (
						SELECT
							id,
							title,
							row_number() OVER (
								PARTITION BY owner, lower(title)
								ORDER BY id
							) AS rn
						FROM app_orchestrator.ssh_keys
					),
					renamed AS (
						SELECT
							id,
							title || '_' || rn AS new_title
						FROM duplicates
						WHERE rn > 1
					)
					UPDATE app_orchestrator.ssh_keys AS sk
					SET title = r.new_title
					FROM renamed AS r
					WHERE sk.id = r.id;			    
				`,
				// Create uniqueness index
				`
					CREATE UNIQUE INDEX ssh_keys_owner_lower_title_idx
					ON app_orchestrator.ssh_keys (owner, lower(title));
				`,
			}
			for _, statement := range statements {
				db.Exec(tx, statement, db.Params{})
			}
		},
	}
}
