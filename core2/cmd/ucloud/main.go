package main

import (
	"os"
	"slices"

	"ucloud.dk/core/pkg/launcher"
	"ucloud.dk/shared/pkg/util"
)

func main() {
	util.DeploymentName = "Core"
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
