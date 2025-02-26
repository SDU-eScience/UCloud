package k8s

import "ucloud.dk/pkg/im/services/k8s/containers"

func HandleCliWithoutConfig(command string) bool {
	switch command {
	case "script-gen":
		HandleScriptGen()
	case "nix-process":
		containers.HandleNixProcessCli()
	default:
		return false
	}
	return true
}
