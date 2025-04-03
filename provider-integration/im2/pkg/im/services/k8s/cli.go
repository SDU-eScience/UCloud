package k8s

import (
	"os"
	ctrl "ucloud.dk/pkg/im/controller"
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
		ctrl.IpPoolCliStub(os.Args[2:])
	}
}
