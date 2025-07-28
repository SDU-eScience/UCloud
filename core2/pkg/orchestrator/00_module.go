package orchestrator

func Init() {
	initProviders()
	InitResources()
	initFeatures()

	initDrives()
}
