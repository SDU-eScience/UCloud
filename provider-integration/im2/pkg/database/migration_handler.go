package database

import (
	"log"

	_ "github.com/lib/pq"
)

type MigrationHandler struct {
	DB      DBSession
	scripts []MigrationScript
}

type MigrationScript struct {
	Id      string
	Execute func(connection *DBContext)
}

func (handler MigrationHandler) migrate() {
	var missingMigrations []string

	session := handler.DB.Open()

	session.Exec(`
		create table if not exists migrations(
			id text primary key
		);
	`)

	session.Exec(`
		create table if not exists completed_migrations(
			id text primary key references migrations,
			completed_at timestamp
		);
	`)

	if !session.Ok {
		log.Fatalf("Failed to create migrations table")
	}

	for _, script := range handler.scripts {
		handler.DB.Exec(`
			insert into migrations(id) values ($1) on conflict ($1) do nothing
		`, script.Id)

		if !handler.DB.Ok {
			log.Fatalf("Failed to register migration %s", script.Id)
		}

	}

	var notCompleted = handler.DB.Query(`
		select m.id
		from migrations m left join completed_migrations cm on m.id = cm.id
		where cm.id is null
	`)

	if !handler.DB.Ok {
		log.Fatalf("Failed to fetch missing migrations")
	} else {
		for notCompleted.Next() {
			var missing string
			notCompleted.Scan(&missing)
			missingMigrations = append(missingMigrations, missing)
		}
		defer notCompleted.Close()
	}

	groupedScripts := make(map[string]MigrationScript)

	for _, script := range handler.scripts {
		groupedScripts[script.Id] = script
	}

	for _, migrationId := range missingMigrations {
		migration := groupedScripts[migrationId]

		migration.Execute(handler.DB)

		// NOTE(Dan): This needs to be prepared everytime because of schema changes made by the migrations.
		handler.DB.Exec(`
			insert into completed_migrations (id, completed_at) values ($1, now())
		`, migrationId)

		if !handler.DB.Ok {
			log.Fatalf("Failed to run migration: %s", migrationId)
		}
	}
}

func (handler *MigrationHandler) addScript(script MigrationScript) {
	for _, thisScript := range handler.scripts {
		if thisScript.Id == script.Id {
			log.Fatalf("Failed to add migration script %s: Already exists", script.Id)
			return
		}
	}

	handler.scripts = append(handler.scripts, script)
}
