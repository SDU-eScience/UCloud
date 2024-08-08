package migrations

import (
	_ "github.com/lib/pq"
	"os"
	db "ucloud.dk/pkg/database"
	"ucloud.dk/pkg/log"
)

var scripts []migrationScript

type migrationScript struct {
	Id      string
	Execute func(tx *db.Transaction)
}

func Migrate() {
	loadMigrations()
	createMigrationTables()
	scriptsToRun := findMissingMigrations()

	for _, migration := range scriptsToRun {
		db.NewTxV(func(tx *db.Transaction) {
			migration.Execute(tx)

			db.Exec(
				tx,
				`
					insert into completed_migrations (id, completed_at) values (:id, now())
				`,
				db.Params{
					"id": migration.Id,
				},
			)

			if !tx.Ok {
				log.Error("Failed to run migration: %s. %s", migration.Id, tx.ConsumeError().Error())
				os.Exit(1)
			}
		})
	}
}

func createMigrationTables() {
	db.NewTxV(func(tx *db.Transaction) {
		db.Exec(tx, `
			create table if not exists migrations(
				id text primary key
			);
		`, map[string]any{})

		db.Exec(tx, `
			create table if not exists completed_migrations(
				id text primary key references migrations,
				completed_at timestamp
			);
		`, map[string]any{})
	})
}

func findMissingMigrations() []migrationScript {
	return db.NewTx[[]migrationScript](func(tx *db.Transaction) []migrationScript {
		for _, script := range scripts {
			db.Exec(tx, `
				insert into migrations(id) values (:id) on conflict (id) do nothing
			`, map[string]any{"id": script.Id})
		}

		type CompletedMigrationsRow struct {
			Id string
		}

		missingScriptIds := db.Select[CompletedMigrationsRow](tx, `
			select m.id
			from
				migrations m
				left join completed_migrations cm on m.id = cm.id
			where
				cm.id is null
		`, map[string]any{})

		var missingScripts []migrationScript
		for _, script := range scripts {
			for _, id := range missingScriptIds {
				if script.Id == id.Id {
					missingScripts = append(missingScripts, script)
					break
				}
			}
		}
		return missingScripts
	})
}

func addScript(newScript migrationScript) {
	for _, thisScript := range scripts {
		if thisScript.Id == newScript.Id {
			log.Error("Failed to add migration migration script %s: Already exists", newScript.Id)
			os.Exit(1)
			return
		}
	}

	scripts = append(scripts, newScript)
}
