package launcher

import (
	"bufio"
	"flag"
	"fmt"
	embeddedpostgres "github.com/fergusstrange/embedded-postgres"
	"net"
	"net/http"
	"os"
	"strconv"
	"time"
	"ucloud.dk/pkg/client"
	db "ucloud.dk/pkg/database"
	"ucloud.dk/pkg/im"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/util"

	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/gateway"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/pkg/log"
)

func Launch() {
	var (
		configDir  = flag.String("config-dir", "/etc/ucloud", "Path to the configuration directory used by the IM")
		reloadable = flag.Bool("reloadable", false, "Whether to enable hot-reloading of the module")
	)

	flag.Parse()
	if !flag.Parsed() {
		os.Exit(1)
	}

	mode := cfg.ServerModePlugin
	pluginName := ""
	switch flag.Arg(0) {
	case "user":
		mode = cfg.ServerModeUser
	case "":
		mode = cfg.ServerModeServer
	case "server":
		mode = cfg.ServerModeServer
	case "proxy":
		mode = cfg.ServerModeProxy
	default:
		mode = cfg.ServerModePlugin
		pluginName = flag.Arg(1)
	}

	if !cfg.Parse(mode, *configDir) {
		fmt.Printf("Failed to parse configuration!\n")
		return
	}

	envoySecret, userModeSecretOk := os.LookupEnv("UCLOUD_USER_SECRET")
	if mode == cfg.ServerModeUser {
		if !userModeSecretOk || envoySecret == "" {
			fmt.Printf("No user-Mode secret specified!\n")
			os.Exit(1)
		}

		// NOTE(Dan): Not security related, it just looks weird in the output of `env` on a user job. This is mostly
		// done to avoid tickets.
		envKeysToRemove := []string{
			"SUDO_COMMAND",
			"SUDO_GID",
			"SUDO_USER",
			"SUDO_UID",
			"UCLOUD_USER_SECRET",
		}
		for _, key := range envKeysToRemove {
			_ = os.Unsetenv(key)
		}
	}

	if mode == cfg.ServerModeServer {
		envoySecret = util.RandomToken(16)
	}
	cfg.OwnEnvoySecret = envoySecret

	_ = pluginName

	var dbPool *db.Pool = nil

	gatewayConfigChannel := make(chan []byte)
	if mode == cfg.ServerModeServer {
		gateway.Initialize(gateway.Config{
			ListenAddress:   "0.0.0.0",
			Port:            8889,
			InitialClusters: nil,
			InitialRoutes:   nil,
		}, gatewayConfigChannel)

		gateway.Resume()

		dbConfig := &cfg.Server.Database
		if dbConfig.Embedded {
			embeddedDb := embeddedpostgres.NewDatabase(
				embeddedpostgres.
					DefaultConfig().
					StartTimeout(30 * time.Second).
					Username(dbConfig.Username).
					Password(dbConfig.Password).
					Database(dbConfig.Database).
					DataPath(dbConfig.EmbeddedDataDirectory),
			)

			err := embeddedDb.Start()
			if err != nil {
				fmt.Printf("Failed to start embedded database! %v\n", err)
				os.Exit(1)
			}
		}

		dbPool = db.Connect(
			dbConfig.Username,
			dbConfig.Password,
			dbConfig.Host.Address,
			dbConfig.Host.Port,
			dbConfig.Database,
			dbConfig.Ssl,
		)
	} else if mode == cfg.ServerModeUser {

	}

	moduleArgs := im.ModuleArgs{
		Mode:                 mode,
		GatewayConfigChannel: gatewayConfigChannel,
		ConfigDir:            *configDir,
		UserModeSecret:       envoySecret,
		MetricsHandler:       &metricsServerHandler,
	}

	if dbPool != nil {
		moduleArgs.Database = dbPool.Connection
	}

	if mode == cfg.ServerModeServer {
		// NOTE(Dan): The initial setup is _not_ reloadable. This is similar to how the HTTP server setup is also not
		// reloadable.
		client.DefaultClient = client.MakeClient(
			cfg.Server.RefreshToken,
			cfg.Provider.Hosts.UCloud.ToURL(),
		)

		go func() {
			ipc.InitIpc()
		}()
	}

	// Once all critical parts are loaded, we can trigger the reloadable part of the code's launcher function
	if *reloadable {
		im.WatchForReload(&moduleArgs)
	} else {
		module := im.ReloadableModule{
			ModuleMain: ModuleMainStub,
			ModuleExit: ModuleExitStub,
		}

		im.ReloadModule(&module, &moduleArgs)
	}

	if mode == cfg.ServerModeServer {
		launchMetricsServer()
	}

	log.Info("UCloud is ready!")

	if mode == cfg.ServerModeServer || mode == cfg.ServerModeUser {
		serverPort := gateway.ServerClusterPort
		if mode == cfg.ServerModeUser {
			port, _ := strconv.Atoi(flag.Arg(1))
			serverPort = port
			ctrl.UCloudUsername = flag.Arg(2)
		}

		err := http.ListenAndServe(
			fmt.Sprintf(":%v", serverPort),
			http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
				handler, _ := im.Args.ServerMultiplexer.Handler(request)
				newWriter := NewLoggingResponseWriter(writer)
				handler.ServeHTTP(newWriter, request)
				log.Info("%v %v %v", request.Method, request.RequestURI, newWriter.statusCode)
			}),
		)

		if err != nil {
			fmt.Printf("Failed to start listener on port %v\n", gateway.ServerClusterPort)
			os.Exit(1)
		}
	}
}

type loggingResponseWriter struct {
	http.ResponseWriter
	statusCode int
}

func NewLoggingResponseWriter(w http.ResponseWriter) *loggingResponseWriter {
	return &loggingResponseWriter{w, http.StatusOK}
}

func (lrw *loggingResponseWriter) WriteHeader(code int) {
	lrw.statusCode = code
	lrw.ResponseWriter.WriteHeader(code)
}

func (lrw *loggingResponseWriter) Hijack() (net.Conn, *bufio.ReadWriter, error) {
	hijacker, ok := lrw.ResponseWriter.(http.Hijacker)
	if !ok {
		return nil, nil, fmt.Errorf("hijack not supported")
	}

	return hijacker.Hijack()
}

var metricsServerHandler func(writer http.ResponseWriter, request *http.Request) = nil

func launchMetricsServer() {
	go func() {
		http.HandleFunc("/metrics", func(writer http.ResponseWriter, request *http.Request) {
			if metricsServerHandler != nil {
				metricsServerHandler(writer, request)
			} else {
				writer.WriteHeader(http.StatusNotFound)
			}
		})
		err := http.ListenAndServe(":7867", nil)
		if err != nil {
			log.Warn("Prometheus metrics server has failed unexpectedly! %v", err)
		}
	}()
}
