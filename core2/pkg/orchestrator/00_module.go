package orchestrator

import (
	"time"

	"ucloud.dk/core/pkg/coreutil"
	"ucloud.dk/shared/pkg/util"
)

func Init() {
	t := util.NewTimer()
	times := map[string]time.Duration{}

	// Orchestration and utilities
	//==================================================================================================================

	initProviders()
	times["Providers"] = t.Mark()

	initProviderBrandings()
	times["ProviderBrandings"] = t.Mark()

	InitResources()
	times["Resources"] = t.Mark()

	initFeatures()
	times["Features"] = t.Mark()

	initTasks()
	times["Tasks"] = t.Mark()

	initProviderManagement()
	times["ProviderManagement"] = t.Mark()

	initProviderIntegration()
	times["ProviderIntegration"] = t.Mark()

	initApiTokens()
	times["ApiTokens"] = t.Mark()

	// Storage
	//==================================================================================================================

	initDrives()
	times["Drives"] = t.Mark()

	initFiles()
	times["Files"] = t.Mark()

	initMetadata()
	times["Metadata"] = t.Mark()

	initShares()
	times["Shares"] = t.Mark()

	initShareLinks()
	times["ShareLinks"] = t.Mark()

	// Compute
	//==================================================================================================================

	initAppSearchIndex()
	times["AppSearchIndex"] = t.Mark()

	initAppLogos()
	times["AppLogos"] = t.Mark()

	initAppCatalog()
	times["AppCatalog"] = t.Mark()

	initJobs()
	times["Jobs"] = t.Mark()

	initLicenses()
	times["Licenses"] = t.Mark()

	initPublicIps()
	times["PublicIps"] = t.Mark()

	initIngresses()
	times["Ingresses"] = t.Mark()

	initSupportAssistsOrc()
	times["SupportAssistsOrc"] = t.Mark()

	initSsh()
	times["Ssh"] = t.Mark()

	initScripts()
	times["Scripts"] = t.Mark()

	initSyncthing()
	times["Syncthing"] = t.Mark()

	coreutil.PrintStartupTimes("Orchestrator", times)
}
