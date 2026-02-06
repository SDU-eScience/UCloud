package migrations

import (
	"ucloud.dk/shared/pkg/audit"
	db "ucloud.dk/shared/pkg/database"
)

func Init() {
	db.AddMigration(genericLicensesV1())
	db.AddMigration(connectionsV1())
	db.AddMigration(apmEventsV1())
	db.AddMigration(slurmV1())
	db.AddMigration(connectionsV2())
	db.AddMigration(jobDatabaseV1())
	db.AddMigration(jobDatabaseV2())
	db.AddMigration(driveDatabaseV1())
	db.AddMigration(scriptLogV1())
	db.AddMigration(slurmV2())
	db.AddMigration(fileTransfersV1())
	db.AddMigration(uploadsV1())
	db.AddMigration(slurmV3())
	db.AddMigration(k8sV1())
	db.AddMigration(apmEventsV2())
	db.AddMigration(ipDatabaseV1())
	db.AddMigration(driveDatabaseV2())
	db.AddMigration(integratedAppsV1())
	db.AddMigration(connectionsV3())
	db.AddMigration(driveDatabaseV3())
	db.AddMigration(ingressDatabaseV1())
	db.AddMigration(licenseDatabaseV1())
	db.AddMigration(jobDatabaseV3())
	db.AddMigration(ipDatabaseV2())
	db.AddMigration(inferenceV1())
	db.AddMigration(audit.MigrationV1())
	db.AddMigration(audit.MigrationV2())
}
