package launcher

import (
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"ucloud.dk/pkg/im/external/user"
	"ucloud.dk/pkg/im/migrations"

	"github.com/jmoiron/sqlx"
	"ucloud.dk/pkg/im"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	svc "ucloud.dk/pkg/im/services"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

func ModuleMain(oldModuleData []byte, args *im.ModuleArgs) {
	im.Args = args

	if !cfg.Parse(args.Mode, args.ConfigDir) {
		// TODO Double parsing in this case
		return
	}

	cfg.OwnEnvoySecret = args.UserModeSecret

	{
		logCfg := &cfg.Provider.Logs

		var logFileName string
		switch cfg.Mode {
		case cfg.ServerModeServer:
			logFileName = "server"
		case cfg.ServerModeProxy:
			logFileName = "proxy"
		case cfg.ServerModePlugin:
			logFileName = ""
		case cfg.ServerModeUser:
			uinfo, err := user.Current()
			if err == nil {
				logFileName = uinfo.Username
			} else {
				logFileName = fmt.Sprintf("uid-%d", os.Getuid())
			}
		}

		if cfg.Mode != cfg.ServerModeServer || !cfg.Provider.Logs.ServerStdout {
			log.SetLogConsole(false)
			err := log.SetLogFile(filepath.Join(logCfg.Directory, logFileName+".log"))
			if err != nil {
				panic("Unable to open log file: " + err.Error())
			}

			if logCfg.Rotation.Enabled {
				log.SetRotation(log.RotateDaily, logCfg.Rotation.RetentionPeriodInDays, true)
			}
		}
	}

	if args.Mode == cfg.ServerModeServer {
		*args.MetricsHandler = ctrl.InitMetrics()

		args.Database.MapperFunc(util.ToSnakeCase)
		db.Database = &db.Pool{Connection: args.Database}
		migrations.Init()
		db.Migrate()
	}

	ctrl.Init(args.ServerMultiplexer)
	svc.Init(args)
	ctrl.InitLate(args.ServerMultiplexer)
	svc.InitLater(args)
}

func ModuleExit() []byte {
	util.IsAlive = false
	return nil
}

func ModuleMainStub(oldData []byte, args map[string]any) {
	// NOTE(Dan): This function must take untyped args since we cannot pass the ModuleArgs from the reload-er to the
	// reload-ee. Here we do some quick unmarshalling of the data to make it easier to use.

	mux := args["ServerMultiplexer"].(*http.ServeMux)
	mode := cfg.ServerMode(args["Mode"].(int))
	configDir := args["ConfigDir"].(string)
	userModeSecret := args["UserModeSecret"].(string)
	dbPool := args["Database"].(*sqlx.DB)                              // can be nil
	gatewayConfigChannel := args["GatewayConfigChannel"].(chan []byte) // can be nil
	ipcMux := args["IpcMultiplexer"].(*http.ServeMux)
	metricsHandler := args["MetricsHandler"].(*func(writer http.ResponseWriter, request *http.Request))

	newArgs := im.ModuleArgs{
		Mode:                 mode,
		GatewayConfigChannel: gatewayConfigChannel,
		Database:             dbPool,
		ConfigDir:            configDir,
		UserModeSecret:       userModeSecret,
		ServerMultiplexer:    mux,
		IpcMultiplexer:       ipcMux,
		MetricsHandler:       metricsHandler,
	}

	ModuleMain(oldData, &newArgs)
}

func ModuleExitStub() []byte {
	return ModuleExit()
}
