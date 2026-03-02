package main

import (
	"fmt"
	"os"
	"strconv"

	"ucloud.dk/pkg/controller/fsearch"
	"ucloud.dk/pkg/external/gpfs"
	"ucloud.dk/pkg/integrations/k8s/kubevirt"
	"ucloud.dk/pkg/launcher"
	_ "ucloud.dk/shared/pkg/silentlog"
	"ucloud.dk/shared/pkg/util"
)

func main() {
	exeName := util.FileName(os.Args[0])
	if exeName == "gpfs-mock" {
		gpfs.RunMockServer()
		return
	}

	if exeName == "search" {
		if len(os.Args) < 4 {
			fmt.Printf("search <query> <load> <bucketCount>")
			return
		}

		bucketCount, _ := strconv.ParseInt(os.Args[3], 10, 64)
		fsearch.CliMain(os.Args[1], os.Args[2] == "true", int(bucketCount))
		return
	}

	if len(os.Args) >= 2 && os.Args[1] == "vmi-mutator" {
		kubevirt.VmiStandaloneMutator()
		return
	}

	util.DeploymentName = "IM"
	launcher.Launch()
}
