package migrations

func LoadMigrations() {
	addScript(GenericLicensesV1())
}
