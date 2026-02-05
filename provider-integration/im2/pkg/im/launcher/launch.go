package launcher

import (
	"bufio"
	"flag"
	"fmt"
	"net"
	"net/http"
	_ "net/http/pprof"
	"os"
	"path/filepath"
	"runtime/debug"
	"strconv"
	"time"

	embeddedpostgres "github.com/fergusstrange/embedded-postgres"
	"ucloud.dk/pkg/im"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/im/external/user"
	"ucloud.dk/pkg/im/migrations"
	svc "ucloud.dk/pkg/im/services"
	"ucloud.dk/pkg/im/services/k8s"
	"ucloud.dk/pkg/im/services/slurm"
	"ucloud.dk/pkg/termio"
	"ucloud.dk/shared/pkg/client"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/util"

	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/gateway"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/shared/pkg/log"
)

func Launch() {
	if os.Getenv("UCLOUD_EARLY_DEBUG") != "" {
		fmt.Printf("Ready for debugger\n")
		keepWaiting := true

		//goland:noinspection GoBoolExpressions
		for keepWaiting {
			// Break this loop via the debugger (the debugger can change the value of keepWaiting).
			time.Sleep(10 * time.Millisecond)
		}
	}

	var (
		configDir = flag.String("config-dir", "/etc/ucloud", "Path to the configuration directory used by the IM")
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
		pluginName = flag.Arg(0)
	}

	if pluginName != "" {
		if k8s.HandleCliWithoutConfig(pluginName) {
			return
		}
	}

	if !cfg.Parse(mode, *configDir) {
		fmt.Printf("Failed to parse configuration!\n")
		return
	}

	if pluginName != "" {
		cfg.Mode = mode

		if gateway.HandleCli(pluginName) {
			return
		}

		switch cfg.Services.Type {
		case cfg.ServicesSlurm:
			slurm.HandleCli(pluginName)
		case cfg.ServicesKubernetes:
			k8s.HandleCli(pluginName)
		}

		return
	}

	fmt.Printf("UCloud/IM starting up... [1/4] Hello!\n")

	envoySecret, userModeSecretOk := os.LookupEnv("UCLOUD_USER_SECRET")
	if mode == cfg.ServerModeUser {
		if !userModeSecretOk || envoySecret == "" {
			termio.WriteLine("This command is used by IM/Server to spawn user instances. This is not intended for CLI use!")
			termio.WriteLine("The program is terminating because no user-mode secret was specified!")
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

	fmt.Printf("UCloud/IM starting up... [2/4] Getting things ready\n")

	var dbPool *db.Pool = nil

	gatewayConfigChannel := make(chan []byte)
	if mode == cfg.ServerModeServer {
		gateway.Initialize(gateway.Config{
			ListenAddress: "0.0.0.0",
			Port:          8889,
		}, gatewayConfigChannel)

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

	args := &im.ModuleArgs{
		Mode:                 mode,
		GatewayConfigChannel: gatewayConfigChannel,
		ConfigDir:            *configDir,
		UserModeSecret:       envoySecret,
		MetricsHandler:       &metricsServerHandler,
		ServerMultiplexer:    http.NewServeMux(),
		IpcMultiplexer:       http.NewServeMux(),
	}

	im.Args = args

	if dbPool != nil {
		args.Database = dbPool
	}

	fmt.Printf("UCloud/IM starting up... [3/4] Still working on it\n")

	if mode == cfg.ServerModeServer {
		client.DefaultClient = client.MakeClient(
			cfg.Server.RefreshToken,
			cfg.Provider.Hosts.UCloud.ToURL(),
		)

		go func() {
			ipc.InitIpc()
		}()
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

		db.Database = args.Database
		migrations.Init()
		db.Migrate()
	}

	ctrl.Init(args.ServerMultiplexer)
	svc.Init(args)
	ctrl.InitLate(args.ServerMultiplexer)
	svc.InitLater(args)

	if mode == cfg.ServerModeServer {
		launchMetricsServer()
		gateway.InitIpc()
	}

	fmt.Printf("UCloud/IM starting up... [4/4] Ready!\n")
	log.Info("UCloud is ready!")

	if mode == cfg.ServerModeServer && cfg.Provider.Profiler.Enabled {
		go func() {
			// NOTE(Dan): The pprof net handler is ridiculously designed and will, without doing anything other
			// than importing it, automatically attach itself to which http server happens to be available.
			// This is probably a very bad design, but it appears to be here to stay. To make sure that these endpoints
			// never become available on any of our public servers, we must make sure to _never_ use the
			// http.ListenAndServe utility function anywhere. Instead, we must create an http.Server manually with a
			// separate handler.
			//
			// NOTE(Dan): Usage example:
			//
			// curl http://localhost:$PORT/debug/pprof/heap -o heap.pprof
			// go tool pprof heap.pprof (common commands: top and list)
			//
			// curl http://localhost:$PORT/debug/pprof/profile -o profile.pprof # takes 30 seconds
			// go tool pprof profile.pprof
			log.Fatal("%s", http.ListenAndServe(fmt.Sprintf("127.0.0.1:%d", cfg.Provider.Profiler.Port), nil))
		}()
	}

	if mode == cfg.ServerModeServer || mode == cfg.ServerModeUser {
		serverPort := gateway.ServerClusterPort
		if mode == cfg.ServerModeUser {
			port, _ := strconv.Atoi(flag.Arg(1))
			serverPort = port
			ctrl.UCloudUsername = flag.Arg(2)
		}

		sMux := http.NewServeMux()
		sMux.HandleFunc("/", func(writer http.ResponseWriter, request *http.Request) {
			start := time.Now()
			defer func() {
				err := recover()
				if err != nil {
					log.Error("%v %v panic! %s %s", request.Method, request.RequestURI, err, string(debug.Stack()))
				}
			}()

			handler, _ := im.Args.ServerMultiplexer.Handler(request)
			newWriter := NewLoggingResponseWriter(writer)
			handler.ServeHTTP(newWriter, request)
			end := time.Now()
			duration := end.Sub(start)
			log.Info("%v %v %v %v", request.Method, request.URL.Path, newWriter.statusCode, duration)
		})
		s := &http.Server{Addr: fmt.Sprintf(":%v", serverPort), Handler: sMux}
		err := s.ListenAndServe()

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

func (lrw *loggingResponseWriter) Flush() {
	if flusher, ok := lrw.ResponseWriter.(http.Flusher); ok {
		flusher.Flush()
	}
}

var metricsServerHandler func(writer http.ResponseWriter, request *http.Request) = nil

func launchMetricsServer() {
	go func() {
		mux := http.NewServeMux()
		mux.HandleFunc("/metrics", func(writer http.ResponseWriter, request *http.Request) {
			if metricsServerHandler != nil {
				metricsServerHandler(writer, request)
			} else {
				writer.WriteHeader(http.StatusNotFound)
			}
		})

		s := &http.Server{Addr: ":7867", Handler: mux}
		err := s.ListenAndServe()
		if err != nil {
			log.Warn("Prometheus metrics server has failed unexpectedly! %v", err)
		}
	}()
}
