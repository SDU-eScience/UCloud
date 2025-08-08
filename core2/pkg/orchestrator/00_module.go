package orchestrator

func Init() {
	initProviders()
	InitResources()
	initFeatures()

	initDrives()
	initFiles()
	initAppSearchIndex()
	initAppLogos()
	initAppCatalog()
}
