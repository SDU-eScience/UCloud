package slurm

import (
    cfg "ucloud.dk/pkg/im/config"
    ctrl "ucloud.dk/pkg/im/controller"
)

var ServiceConfig *cfg.ServicesConfigurationSlurm

func Init(config *cfg.ServicesConfigurationSlurm) {
    ServiceConfig = config

    ctrl.LaunchUserInstances = true
    ctrl.Files = InitializeFiles()
}
