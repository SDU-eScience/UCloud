package k8s

import (
	"os"

	ctrl "ucloud.dk/pkg/im/controller"
)

func HandleCliWithoutConfig(command string) bool {
	switch command {
	case "script-gen":
		HandleScriptGen()
	case "im2-k8s-import":
		HandleDataImport(os.Args[2:])
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
		ctrl.IpPoolCliStub(os.Args[2:])
	case "license":
		ctrl.LicenseCli(os.Args[2:])
	case "storage-scan":
		StorageScanCli(os.Args[2:])
	}
}
