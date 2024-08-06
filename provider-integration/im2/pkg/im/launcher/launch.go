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

	_ = cfg.ReadPublicKey(*configDir)

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

	userModeSecret, userModeSecretOk := os.LookupEnv("UCLOUD_USER_SECRET")
	if mode == cfg.ServerModeUser && (!userModeSecretOk || userModeSecret == "") {
		fmt.Printf("No user-Mode secret specified!\n")
		os.Exit(1)
	}

	_ = pluginName

	var dbPool *db.Pool = nil

	gatewayConfigChannel := make(chan []byte)
	if mode == cfg.ServerModeServer {
		// TODO(Dan): Update the gateway to perform JWT validation. This should not (and must not) pass the JWT to the
		//   upstreams. Gateway must be configured to send the user secret to all user instances. For the server
		//  instance it must select a secret at random and use it for all subsequent requests. The secret must be
		//  captured by all relevant requests and validated. The secret is used as a signal that Envoy has successfully
		//  validated a JWT without passing on the JWT.
		// TODO(Dan): Websockets are problematic here since they pass the bearer in the request message and not the
		//   header.
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
		UserModeSecret:       userModeSecret,
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
