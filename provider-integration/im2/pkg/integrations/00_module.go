package integrations

import (
	cfg "ucloud.dk/pkg/config"
	ctrl "ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/gateway"
	"ucloud.dk/pkg/integrations/k8s"
	"ucloud.dk/pkg/integrations/slurm"
	"ucloud.dk/shared/pkg/rpc"
)

func Init() {
	slurmCfg := cfg.Services.Slurm()
	k8sCfg := cfg.Services.Kubernetes()
	if slurmCfg != nil {
		slurm.Init(slurmCfg, rpc.DefaultServer.Mux)
	} else if k8sCfg != nil {
		k8s.Init(k8sCfg)
	}

	if ctrl.RunsServerCode() {
		gateway.SendMessage(gateway.ConfigurationMessage{LaunchingUserInstances: &ctrl.LaunchUserInstances})
	}
}

func InitLater() {
	slurmCfg := cfg.Services.Slurm()
	k8sCfg := cfg.Services.Kubernetes()
	if slurmCfg != nil {
		slurm.InitLater(slurmCfg)
	} else if k8sCfg != nil {
		k8s.InitLater(k8sCfg)
	}
}
