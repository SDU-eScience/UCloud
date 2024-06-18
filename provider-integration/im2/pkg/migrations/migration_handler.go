package migrations

import (
	"fmt"
	"log"
	"time"

	_ "github.com/lib/pq"
	"ucloud.dk/pkg/database"
)

var scripts []MigrationScript

type MigrationScript struct {
	Id      string
	Execute func(ctx *database.Transaction)
}

func Migrate(ctx *database.Transaction) {
	database.Exec(ctx, `
		create table if not exists migrations(
			id text primary key
		);
	`, map[string]any{})

	database.Exec(ctx, `
		create table if not exists completed_migrations(
			id text primary key references migrations,
			completed_at timestamp
		);
	`, map[string]any{})

	// TODO TEST
	type CompletedMigrationsRowOne struct {
		Id          string    `db:"id"`
		CompletedAt time.Time `db:"completed_at"`
	}
	completedRows := database.Select[CompletedMigrationsRowOne](ctx, `
		select * from completed_migrations
		`, map[string]any{},
	)

	fmt.Printf("FOUND %d MIGRATIONS\n", len(completedRows))
	// TODO TEST END

	if !ctx.Ok {
		log.Fatalf("Failed to create migrations table")
	}

	for _, script := range scripts {
		database.Exec(ctx, `
			insert into migrations(id) values (:id) on conflict (id) do nothing
		`, map[string]any{"id": script.Id})

		if !ctx.Ok {
			log.Fatalf("Failed to register migration: %s\n%v\n", script.Id, ctx.Error.Message)
		}

	}

	type CompletedMigrationsRow struct {
		Id string
	}

	var missingMigrations = database.Select[CompletedMigrationsRow](ctx, `
		select m.id
		from migrations m left join completed_migrations cm on m.id = cm.id
		where cm.id is null
	`, map[string]any{})

	if !ctx.Ok {
		log.Fatalf("Failed to fetch missing migrations")
	}

	groupedScripts := make(map[string]MigrationScript)

	for _, script := range scripts {
		groupedScripts[script.Id] = script
	}

	for _, migrationId := range missingMigrations {
		migration := groupedScripts[migrationId.Id]

		migration.Execute(ctx)

		// NOTE(Dan): This needs to be prepared everytime because of schema changes made by the migrations.
		database.Exec(ctx, `
			insert into completed_migrations (id, completed_at) values (:id, now())
		`, map[string]any{"id": migrationId.Id})

		if !ctx.Ok {
			log.Fatalf("Failed to run migration: %s", migrationId)
		}
	}
}

func addScript(script MigrationScript) {
	for _, thisScript := range scripts {
		if thisScript.Id == script.Id {
			log.Fatalf("Failed to add migration script %s: Already exists", script.Id)
			return
		}
	}

	scripts = append(scripts, script)
}
