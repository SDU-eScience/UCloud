package im

import (
    "fmt"
    cfg "ucloud.dk/pkg/im/config"
    ctrl "ucloud.dk/pkg/im/controller"
    svc "ucloud.dk/pkg/im/services"
)

var IsAlive = true

func ModuleMain(oldModuleData []byte, args *ModuleArgs) {
    fmt.Printf("Running module main with mode=%v\n", args.Mode)
    success := cfg.Parse(args.Mode, args.ConfigDir+"/config.yml") // TODO Double parsing in this case
    if !success {
        // Do not start any services if the configuration is invalid
        return
    }

    svc.Init()
    ctrl.Init(args.ServerMultiplexer)
}

func ModuleExit() []byte {
    IsAlive = false
    return nil
}
