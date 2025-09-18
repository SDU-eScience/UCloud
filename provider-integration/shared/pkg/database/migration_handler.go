package database

import (
	_ "github.com/lib/pq"
	"os"
	"ucloud.dk/shared/pkg/log"
)

var scripts []MigrationScript

type MigrationScript struct {
	Id      string
	Execute func(tx *Transaction)
}

func Migrate() {
	createMigrationTables()
	scriptsToRun := findMissingMigrations()

	for _, migration := range scriptsToRun {
		NewTx0(func(tx *Transaction) {
			migration.Execute(tx)

			Exec(
				tx,
				`
					insert into completed_migrations (id, completed_at) values (:id, now())
				`,
				Params{
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
	NewTx0(func(tx *Transaction) {
		Exec(tx, `
			create table if not exists migrations(
				id text primary key
			);
		`, map[string]any{})

		Exec(tx, `
			create table if not exists completed_migrations(
				id text primary key references migrations,
				completed_at timestamp
			);
		`, map[string]any{})
	})
}

func findMissingMigrations() []MigrationScript {
	return NewTx[[]MigrationScript](func(tx *Transaction) []MigrationScript {
		for _, script := range scripts {
			Exec(tx, `
				insert into migrations(id) values (:id) on conflict (id) do nothing
			`, map[string]any{"id": script.Id})
		}

		type CompletedMigrationsRow struct {
			Id string
		}

		missingScriptIds := Select[CompletedMigrationsRow](tx, `
			select m.id
			from
				migrations m
				left join completed_migrations cm on m.id = cm.id
			where
				cm.id is null
		`, map[string]any{})

		var missingScripts []MigrationScript
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

func AddMigration(newScript MigrationScript) {
	for _, thisScript := range scripts {
		if thisScript.Id == newScript.Id {
			log.Error("Failed to add migration migration script %s: Already exists", newScript.Id)
			os.Exit(1)
			return
		}
	}

	scripts = append(scripts, newScript)
}
