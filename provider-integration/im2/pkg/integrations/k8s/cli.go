package k8s

import (
	"os"

	"ucloud.dk/pkg/controller"
)

func HandleCliWithoutConfig(command string) bool {
	switch command {
	case "script-gen":
		HandleScriptGen()
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
