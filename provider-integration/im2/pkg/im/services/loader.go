package services

import (
	"ucloud.dk/pkg/im"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/gateway"
	"ucloud.dk/pkg/im/services/k8s"
	"ucloud.dk/pkg/im/services/slurm"
)

func Init(args *im.ModuleArgs) {
	slurmCfg := cfg.Services.Slurm()
	k8sCfg := cfg.Services.Kubernetes()
	if slurmCfg != nil {
		slurm.Init(slurmCfg)
	} else if k8sCfg != nil {
		k8s.Init(k8sCfg)
	}

	if ctrl.RunsServerCode() {
		gateway.SendMessage(gateway.ConfigurationMessage{LaunchingUserInstances: &ctrl.LaunchUserInstances})
	}
}
