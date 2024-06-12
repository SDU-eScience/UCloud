package database

import (
	"fmt"
	embeddedpostgres "github.com/fergusstrange/embedded-postgres"
	"testing"
	"ucloud.dk/pkg/log"
)

func TestName(t *testing.T) {
	postgres := embeddedpostgres.NewDatabase()
	err := postgres.Start()

	if err != nil {
		log.Fatal("Postgres failed to start: %v", err)
	}
	defer postgres.Stop()

	log.Debug("Connecting")

	db := Connect()

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
	`, map[string]any{})

	if !session.Ok {
		log.Fatal("Something went wrong 1: %s", session.Error.Message)
	}

	session.Exec(`
		insert into test (id, name) values (1, 'Brian')
	`, map[string]any{})

	if !session.Ok {
		log.Fatal("Something went wrong 2: %s", session.Error.Message)
	}

	type Row struct {
		Name string
	}
	name := &Row{}
	session.Get(&name, "select name from test where id = :id",
		map[string]any{
			"id": 1,
		},
	)

	if !session.Ok {
		log.Fatal("Something went wrong 3: %s", session.Error.Message)
	}

	rows := Select[Row](session, `select unnest(array['Dan', 'Brian', :fie]) as name`, map[string]any{
		"fie": "Fie",
	})

	for _, row := range rows {
		fmt.Println(row)
	}

	// db.Select[struct { Name string }](transaction, "select :fie", map[string]any{ "fie": "fie" })

	//fmt.Println(name.Name)
}
