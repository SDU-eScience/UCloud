package main

import (
	"os"
	"slices"

	"ucloud.dk/core/pkg/accounting"
	"ucloud.dk/core/pkg/launcher"
	"ucloud.dk/shared/pkg/util"
)

func main() {
	if len(os.Args) > 1 && os.Args[1] == "accounting-scenarios" {
		accounting.AccountingScenariosMain(os.Args[2:])
		return
	}
	util.DeploymentName = "Core"
	if slices.Contains(os.Args, "migrate") {
		util.DeploymentName = "Core/Migrate"
		launcher.LaunchMigrationsOnly()
		return
	}
	if slices.Contains(os.Args, "foundation") {
		util.DeploymentName = "Core/Foundation"
	}
	if slices.Contains(os.Args, "apm") || slices.Contains(os.Args, "accounting") {
		util.DeploymentName = "Core/Accounting"
	}
	if slices.Contains(os.Args, "orchestrator") {
		util.DeploymentName = "Core/Orchestrator"
	}
	launcher.Launch()
}
