package k8s

import (
	"os"

	cfg "ucloud.dk/pkg/config"
	"ucloud.dk/pkg/ucxdelivery"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/util"
)

func HandleUcxDevPublish(args []string) {
	if !util.DevelopmentModeEnabled() {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "ucx-publish (dev) is only available in development mode")
		os.Exit(1)
	}

	if len(args) != 3 {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Usage: ucloud ucx-publish <appName> <appVersion> <directory>")
		os.Exit(1)
	}

	k8sCfg := cfg.Services.Kubernetes()
	if k8sCfg == nil {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "This IM is not configured for Kubernetes")
		os.Exit(1)
	}

	result, err := ucxdelivery.PublishVersion(k8sCfg.FileSystem.MountPoint, args[0], args[1], args[2])
	if err != nil {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Publish failed: %v", err)
		os.Exit(1)
	}

	f := termio.Frame{}
	f.AppendTitle("UCX application published")
	f.AppendField("Path", result.Path)
	f.AppendField("Binary", result.BinaryName)
	f.Print()
}
