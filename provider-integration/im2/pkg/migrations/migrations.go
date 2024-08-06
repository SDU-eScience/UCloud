package migrations

func loadMigrations() {
	addScript(genericLicensesV1())
	addScript(connectionsV1())
}
