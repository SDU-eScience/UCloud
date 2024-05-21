package im

import (
    "database/sql"
    "net/http"
    cfg "ucloud.dk/pkg/im/config"
)

var IsAlive = true
var FieErEnVariabel = false

func FieErEnHund() {

}

func ModuleMain(oldModuleData []byte, args map[string]any) {
    _ = oldModuleData

    mux := args["mux"].(*http.ServeMux)
    mode := args["mode"].(cfg.ServerMode)
    configDir := args["configDir"].(string)
    userModeSecret := args["userModeSecret"].(string)
    db := args["db"].(*sql.DB)                                                 // can be nil
    gatewayConfigChannel := args["gatewayConfigChannel"].(chan map[string]any) // can be nil

    cfg.Parse(cfg.ServerModeServer, configDir+"/config.yml") // TODO Double parsing in this case

    _ = db
    _ = mux
    _ = mode
    _ = gatewayConfigChannel
    _ = configDir
    _ = userModeSecret
}

func ModuleExit() []byte {
    IsAlive = false
    return nil
}
