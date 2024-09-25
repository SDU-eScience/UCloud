package migrations

func loadMigrations() {
	addScript(genericLicensesV1())
	addScript(connectionsV1())
	addScript(apmEventsV1())
	addScript(slurmV1())
	addScript(connectionsV2())
	addScript(jobDatabaseV1())
	addScript(jobDatabaseV2())
	addScript(driveDatabaseV1())
	addScript(scriptLogV1())
	addScript(slurmV2())
	addScript(fileTransfersV1())
}
