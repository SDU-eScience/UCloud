package migrations

import (
	"ucloud.dk/shared/pkg/audit"
	db "ucloud.dk/shared/pkg/database"
)

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
	db.AddMigration(audit.MigrationV1())
	db.AddMigration(apiTokensV1())
	db.AddMigration(accountingV3())
	db.AddMigration(authV2())
	db.AddMigration(newsV1())
	db.AddMigration(coreV2())
	db.AddMigration(accountingV4())
	db.AddMigration(projectsV3())
	db.AddMigration(audit.MigrationV2())
	db.AddMigration(authV3())
	db.AddMigration(grantV1())
	db.AddMigration(projectsV4())
	db.AddMigration(grantV2())
	db.AddMigration(privateNetworksV1())
	db.AddMigration(jobsV1())
}
