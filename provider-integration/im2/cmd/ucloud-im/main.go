package main

import (
	"os"

	embeddedpostgres "github.com/fergusstrange/embedded-postgres"
	"ucloud.dk/pkg/database"
	"ucloud.dk/pkg/im/gpfs"
	"ucloud.dk/pkg/im/launcher"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/migrations"
)

func main() {

	// Change to run migrations
	if false {
		postgres := embeddedpostgres.NewDatabase(
			embeddedpostgres.DefaultConfig().
				Database("ucloud-im"),
			//DataPath("/home/user/ucloud-data"),
			// TODO(Brian) Should be set for persistent storage, but not sure it's working?
		)
		err := postgres.Start()

		if err != nil {
			log.Fatal("Postgres failed to start: %v", err)
		}
		defer postgres.Stop()

		db := database.Connect()
		session := db.Open()
		defer session.Close()

		migrations.LoadMigrations()
		migrations.Migrate(session)

		return
	}

	exeName := os.Args[0]
	if exeName == "gpfs-mock" {
		gpfs.RunMockServer()
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
