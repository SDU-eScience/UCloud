package migrations

func loadMigrations() {
	addScript(genericLicensesV1())
	addScript(connectionsV1())
	addScript(apmEventsV1())
	addScript(slurmV1())
}
