package k8s

import (
	"os"

	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
)

func HandleCliWithoutConfig(command string) bool {
	switch command {
	case "script-gen":
		HandleScriptGen()
	case "task-processor":
		filesystem.TaskProcessor()
	default:
		return false
	}
	return true
}

func HandleCli(command string) {
	switch command {
	case "ip":
		fallthrough
	case "ips":
		controller.IpPoolCliStub(os.Args[2:])
	case "license":
		controller.LicenseCli(os.Args[2:])
	case "storage-scan":
		StorageScanCli(os.Args[2:])
	}
}
