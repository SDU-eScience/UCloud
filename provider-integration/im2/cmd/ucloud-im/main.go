package main

import _ "ucloud.dk/pkg/silentlog"

import (
	"fmt"
	"os"
	"strconv"

	"ucloud.dk/pkg/im/controller/fsearch"
	"ucloud.dk/pkg/im/external/gpfs"
	"ucloud.dk/shared/pkg/util"

	"ucloud.dk/pkg/im/launcher"
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

	util.DeploymentName = "IM"
	launcher.Launch()
}

// NOTE(Dan): For some reason, the module reloader can only find the Main and Exit symbols if they are placed in the
// launcher package. I really don't want to move all of that stuff in here, so instead we are just calling out to the real
// stubs from here. It is a silly workaround, but it takes less 10 lines, so I don't really care that much.

func ModuleMainStub(oldPluginData []byte, args map[string]any) {
	launcher.ModuleMainStub(oldPluginData, args)
}

func ModuleExitStub() []byte {
	return launcher.ModuleExitStub()
}
