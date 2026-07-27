package main

import (
	"os"
	"slices"

	"ucloud.dk/core/pkg/launcher"
	"ucloud.dk/shared/pkg/util"
)

func main() {
	util.DeploymentName = "Core"
	if index := slices.Index(os.Args, "accounting-audit"); index >= 0 {
		util.DeploymentName = "Core/Accounting audit"
		launcher.LaunchAccountingAudit(os.Args[index+1:])
		return
	}
	if index := slices.Index(os.Args, "accounting-repair"); index >= 0 {
		util.DeploymentName = "Core/Accounting repair"
		launcher.LaunchAccountingRepair(os.Args[index+1:])
		return
	}
	if slices.Contains(os.Args, "accounting-snapshot") {
		util.DeploymentName = "Core/Accounting snapshot"
		launcher.LaunchAccountingSnapshot()
		return
	}
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
