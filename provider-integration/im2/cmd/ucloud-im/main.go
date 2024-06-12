package main

import (
	"fmt"

	embeddedpostgres "github.com/fergusstrange/embedded-postgres"
	"ucloud.dk/pkg/database"
	"ucloud.dk/pkg/im/launcher"
	"ucloud.dk/pkg/log"
)

func main() {

	if true {
		postgres := embeddedpostgres.NewDatabase()
		err := postgres.Start()

		if err != nil {
			log.Fatal("Postgres failed to start: %v", err)
		}
		defer postgres.Stop()

		log.Debug("Connecting")

		db := database.Connect()

		log.Debug("Opening")

		session := db.Open()
		defer session.Close()

		if !session.Ok {
			log.Fatal("Something went wrong 0: %s", session.Error.Message)
		}

		session.Exec(`
			create table if not exists test (
				id integer primary key,
				name varchar(40)
			)
		`)

		if !session.Ok {
			log.Fatal("Something went wrong 1: %s", session.Error.Message)
		}

		session.Exec(`
			insert into test (id, name) values (1, 'Brian')
		`)

		if !session.Ok {
			log.Fatal("Something went wrong 2: %s", session.Error.Message)
		}

		var name string
		session.Get(&name, "select name from test where id = $1",
			1,
		)

		if !session.Ok {
			log.Fatal("Something went wrong 3: %s", session.Error.Message)
		}

		fmt.Println(name)

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
