package main

import (
	"ucloud.dk/core/pkg/launcher"
	"ucloud.dk/shared/pkg/util"
)

func main() {
	util.DeploymentName = "Core"
	launcher.Launch()
}
