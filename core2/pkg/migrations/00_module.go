package migrations

import db "ucloud.dk/shared/pkg/database"

func Init() {
	db.AddMigration(projectsV1())
}
