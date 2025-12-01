package migrations

import db "ucloud.dk/shared/pkg/database2"

func Init() {
	// TODO(Dan): This is assuming an already existing database created by an older Core. This will be inserted here
	//   near the end of the porting process.

	db.AddMigration(projectsV1())
	db.AddMigration(projectsV2())
	db.AddMigration(sharesV1())
	db.AddMigration(usageV1())
	db.AddMigration(usageV2())
	db.AddMigration(accountingV1())
	db.AddMigration(accountingV2())
	db.AddMigration(authV1())
	db.AddMigration(auditPostgresV1())
}
