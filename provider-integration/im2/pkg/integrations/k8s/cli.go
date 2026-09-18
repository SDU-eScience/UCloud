package k8s

import (
	"os"

	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
	"ucloud.dk/pkg/integrations/k8s/inference"
	"ucloud.dk/pkg/ucxdelivery"
)

func HandleCliWithoutConfig(command string) bool {
	switch command {
	case "script-gen":
		HandleScriptGen()
	case "start-job-audit-log-server":
		JobAuditLogServerStart()
	case "task-processor":
		filesystem.TaskProcessor()
	case "ucx-keygen":
		os.Exit(ucxdelivery.KeygenCli(os.Args[2:], os.Stdout, os.Stderr))
	case "ucx-sign":
		os.Exit(ucxdelivery.SignCli(os.Args[2:], os.Stdout, os.Stderr))
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
	case "metadata":
		filesystem.MetadataCli(os.Args[2:])
	case "jobs":
		HandleJobsCommand()
	case "inference":
		inference.InferenceCli(os.Args[2:])
	case "ucx-publish":
		HandleUcxDevPublish(os.Args[2:])
	}
}
