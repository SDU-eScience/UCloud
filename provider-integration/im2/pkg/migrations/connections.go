package migrations

import (
	db "ucloud.dk/pkg/database"
)

func connectionsV1() migrationScript {
	return migrationScript{
		Id: "connectionsV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(tx, `
				create table connections(
				    ucloud_username text not null primary key,
					uid int not null
				)
			`, db.Params{})

			db.Exec(tx, `
				create table project_connections(
				    ucloud_project_id text not null primary key,
					gid int not null
				)
			`, db.Params{})
		},
	}
}
