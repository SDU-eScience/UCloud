package im

import (
    "fmt"
    cfg "ucloud.dk/pkg/im/config"
)

var IsAlive = true
var FieErEnVariabel = false

func FieErEnHund() {

}

func ModuleMain(oldModuleData []byte, args *ModuleArgs) {
    for i := 0; i < 10; i++ {
        fmt.Printf("This is also reloading!\n")
    }
    cfg.Parse(cfg.ServerModeServer, args.ConfigDir+"/config.yml") // TODO Double parsing in this case
}

func ModuleExit() []byte {
    IsAlive = false
    return nil
}
