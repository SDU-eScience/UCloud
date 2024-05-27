package services

import (
    cfg "ucloud.dk/pkg/im/config"
    ctrl "ucloud.dk/pkg/im/controller"
    "ucloud.dk/pkg/im/gateway"
    "ucloud.dk/pkg/im/services/slurm"
)

func Init() {
    slurmCfg := cfg.Services.Slurm()
    if slurmCfg != nil {
        slurm.Init(slurmCfg)
    }

    if cfg.Mode == cfg.ServerModeServer {
        gateway.SendMessage(gateway.ConfigurationMessage{LaunchingUserInstances: &ctrl.LaunchUserInstances})
    }
}
