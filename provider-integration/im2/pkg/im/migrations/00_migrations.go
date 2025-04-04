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
	addScript(uploadsV1())
	addScript(slurmV3())
	addScript(k8sV1())
	addScript(apmEventsV2())
	addScript(ipDatabaseV1())
	addScript(driveDatabaseV2())
	addScript(integratedAppsV1())
	addScript(connectionsV3())
	addScript(ingressDatabaseV1())
}
