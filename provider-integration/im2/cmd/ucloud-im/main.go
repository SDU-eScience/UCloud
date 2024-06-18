package main

import (
	"fmt"
	"time"

	embeddedpostgres "github.com/fergusstrange/embedded-postgres"
	"ucloud.dk/pkg/database"
	"ucloud.dk/pkg/im/launcher"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/migrations"
)

func main() {

	if true {
		postgres := embeddedpostgres.NewDatabase(
			embeddedpostgres.DefaultConfig().
				Database("ucloud-im").
				DataPath("/home/xirov/ucloud-data"), // TODO(Brian) Not working?
		)
		err := postgres.Start()

		if err != nil {
			log.Fatal("Postgres failed to start: %v", err)
		}
		defer postgres.Stop()

		db := database.Connect()
		session := db.Open()
		defer session.Close()

		// Migrations
		migrations.LoadMigrations()
		migrations.Migrate(session)

		if !session.Ok {
			log.Fatal("Something went wrong 1: %s", session.Error.Message)
		}

		type CompletedMigrationsRow struct {
			Id          string    `db:"id"`
			CompletedAt time.Time `db:"completed_at"`
		}
		completedRows := database.Select[CompletedMigrationsRow](session, `
			select * from completed_migrations
			`, map[string]any{},
		)

		if !session.Ok {
			log.Fatal("Something went wrong 3: %s", session.Error.Message)
		}

		fmt.Printf("FOUND %d MIGRATIONS\n", len(completedRows))
		for _, row := range completedRows {
			fmt.Printf("Got row: %s %v\n", row.Id, row.CompletedAt)
		}

		return
	}

	launcher.Launch()
}

// NOTE(Dan): For some reason, the module reloader can only find the Main and Exit symbols if they are placed in the
// launcher package. I really don't want to move all of that stuff in here, so instead we are just calling out to the real
// stubs from here. It is a silly workaround, but it takes less 10 lines, so I don't really care that much.

func ModuleMainStub(oldPluginData []byte, args map[string]any) {
	launcher.ModuleMainStub(oldPluginData, args)
}

func ModuleExitStub() []byte {
	return launcher.ModuleExitStub()
}
