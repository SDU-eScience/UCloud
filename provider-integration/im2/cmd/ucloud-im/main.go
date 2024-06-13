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

		database.Exec(session, `
			create table if not exists test (
				id integer primary key,
				name varchar(40)
			)
		`, map[string]any{})

		if !session.Ok {
			log.Fatal("Something went wrong 1: %s", session.Error.Message)
		}

		database.Exec(session, `
			insert into test (id, name) values (1, 'Brian')
		`, map[string]any{})

		if !session.Ok {
			log.Fatal("Something went wrong 2: %s", session.Error.Message)
		}

		type Person struct {
			Name string
		}
		person := database.Get[Person](session, "select name from test where id = :id",
			map[string]any{
				"id": 1,
			},
		)

		if !session.Ok {
			log.Fatal("Something went wrong 3: %s", session.Error.Message)
		}

		fmt.Println(person.Name)

		rows := database.Select[Person](session, `select unnest(array['Dan', 'Brian', :fie]) as name`, map[string]any{
			"fie": "Fie",
		})

		for _, row := range rows {
			fmt.Println(row.Name)
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
