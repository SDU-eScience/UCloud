package launcher

import (
	"database/sql"
	"fmt"
	"net/http"
	"ucloud.dk/pkg/im"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	svc "ucloud.dk/pkg/im/services"
)

var IsAlive = true

func ModuleMain(oldModuleData []byte, args *im.ModuleArgs) {
	fmt.Printf("Running module launcher with mode=%v\n", args.Mode)
	im.Args = args

	if !cfg.Parse(args.Mode, args.ConfigDir) {
		// TODO Double parsing in this case
		return
	}

	svc.Init(args)
	ctrl.Init(args.ServerMultiplexer)
}

func ModuleExit() []byte {
	IsAlive = false
	return nil
}

func ModuleMainStub(oldData []byte, args map[string]any) {
	// NOTE(Dan): This function must take untyped args since we cannot pass the ModuleArgs from the reload-er to the
	// reload-ee. Here we do some quick unmarshalling of the data to make it easier to use.

	mux := args["ServerMultiplexer"].(*http.ServeMux)
	mode := cfg.ServerMode(args["Mode"].(int))
	configDir := args["ConfigDir"].(string)
	userModeSecret := args["UserModeSecret"].(string)
	db := args["Database"].(*sql.DB)                                   // can be nil
	gatewayConfigChannel := args["GatewayConfigChannel"].(chan []byte) // can be nil
	ipcMux := args["IpcMultiplexer"].(*http.ServeMux)

	newArgs := im.ModuleArgs{
		Mode:                 mode,
		GatewayConfigChannel: gatewayConfigChannel,
		Database:             db,
		ConfigDir:            configDir,
		UserModeSecret:       userModeSecret,
		ServerMultiplexer:    mux,
		IpcMultiplexer:       ipcMux,
	}

	ModuleMain(oldData, &newArgs)
}

func ModuleExitStub() []byte {
	return ModuleExit()
}
