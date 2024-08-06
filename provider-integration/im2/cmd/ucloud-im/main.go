package main

import (
	"os"

	"ucloud.dk/pkg/im/gpfs"
	"ucloud.dk/pkg/im/launcher"
)

func main() {
	exeName := os.Args[0]
	if exeName == "gpfs-mock" {
		gpfs.RunMockServer()
		return
	}

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
