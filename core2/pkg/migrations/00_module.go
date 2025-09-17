package migrations

import db "ucloud.dk/shared/pkg/database2"

func Init() {
	db.AddMigration(projectsV1())
}
