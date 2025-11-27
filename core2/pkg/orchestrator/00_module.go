package orchestrator

func Init() {
	initProviders()
	InitResources()
	initFeatures()
	initTasks()
	initProviderManagement()
	initProviderIntegration()

	initDrives()
	initFiles()
	initMetadata()
	initShares()
	initShareLinks()

	initAppSearchIndex()
	initAppLogos()
	initAppCatalog()
	initJobs()
	initLicenses()
	initPublicIps()
	initIngresses()
	initSupportAssistsOrc()
	initSsh()

	initSyncthing()
}
