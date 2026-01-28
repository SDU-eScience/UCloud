package migrations

import db "ucloud.dk/shared/pkg/database2"

func Init() {
	db.AddMigration(coreV1())
	db.AddMigration(projectsV1())
	db.AddMigration(projectsV2())
	db.AddMigration(sharesV1())
	db.AddMigration(usageV1())
	db.AddMigration(usageV2())
	db.AddMigration(accountingV1())
	db.AddMigration(accountingV2())
	db.AddMigration(authV1())
	db.AddMigration(auditPostgresV1())
	db.AddMigration(apiTokensV1())
	db.AddMigration(accountingV3())
	db.AddMigration(authV2())
	db.AddMigration(newsV1())
	db.AddMigration(coreV2())
	db.AddMigration(accountingV4())
	db.AddMigration(authV3())
	db.AddMigration(grantV1())
}
