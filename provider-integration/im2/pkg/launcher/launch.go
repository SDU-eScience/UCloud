package launcher

import (
	"encoding/base64"
	"encoding/json"
	"flag"
	"fmt"
	"net/http"
	_ "net/http/pprof"
	"os"
	"path/filepath"
	"slices"
	"strconv"
	"strings"
	"time"

	cfg "ucloud.dk/pkg/config"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/external/user"
	"ucloud.dk/pkg/gateway"
	svc "ucloud.dk/pkg/integrations"
	"ucloud.dk/pkg/integrations/k8s"
	"ucloud.dk/pkg/integrations/slurm"
	"ucloud.dk/pkg/ipc"
	"ucloud.dk/pkg/migrations"
	"ucloud.dk/shared/pkg/audit"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/util"

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
	rpc.ServerProviderId = cfg.Provider.Id

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

	gatewayConfigChannel := make(chan []byte)
	if mode == cfg.ServerModeServer {
		gateway.Initialize(gateway.Config{
			ListenAddress: "0.0.0.0",
			Port:          8889,
		}, gatewayConfigChannel)

		dbConfig := &cfg.Server.Database

		db.Database = db.Connect(
			dbConfig.Username,
			dbConfig.Password,
			dbConfig.Host.Address,
			dbConfig.Host.Port,
			dbConfig.Database,
			dbConfig.Ssl,
		)
	} else if mode == cfg.ServerModeUser {

	}

	ipc.IpcMultiplexer = http.NewServeMux()

	fmt.Printf("UCloud/IM starting up... [3/4] Still working on it\n")

	rpc.ServerAuthenticator = func(r *http.Request) (rpc.Actor, *util.HttpError) {
		type jwtPayload struct {
			Sub  string `json:"sub"`
			Role string `json:"role"`
		}

		checkEnvoySecret := func(r *http.Request) bool {
			if r.Header.Get("ucloud-secret") != cfg.OwnEnvoySecret {
				return false
			}
			return true
		}

		if ok := checkEnvoySecret(r); !ok {
			return rpc.Actor{}, util.HttpErr(http.StatusUnauthorized, "unauthorized")
		}

		payloadHeader := r.Header.Get("x-jwt-payload")
		payloadDecoded, err := base64.RawURLEncoding.DecodeString(payloadHeader)
		if err != nil {
			return rpc.Actor{}, util.HttpErr(http.StatusUnauthorized, "unauthorized")
		}
		var payload jwtPayload
		err = json.Unmarshal(payloadDecoded, &payload)
		if err != nil {
			return rpc.Actor{}, util.HttpErr(http.StatusUnauthorized, "unauthorized")
		}
		if payload.Sub != "_UCloud" {
			return rpc.Actor{}, util.HttpErr(http.StatusUnauthorized, "unauthorized")
		}
		if payload.Role != "SERVICE" {
			return rpc.Actor{}, util.HttpErr(http.StatusUnauthorized, "unauthorized")
		}

		GetUCloudUsername := func(r *http.Request) string {
			base64Encoded := r.Header.Get("ucloud-username")
			if len(base64Encoded) == 0 {
				if cfg.Mode == cfg.ServerModeServer {
					return rpc.ActorSystem.Username
				} else {
					return "_guest"
				}
			}

			bytes, err := base64.StdEncoding.DecodeString(base64Encoded)
			if err != nil {
				return "_guest"
			}

			return string(bytes)
		}
		username := GetUCloudUsername(r)

		if cfg.Provider.Maintenance.Enabled {
			if username != "_guest" && username != "" && !slices.Contains(cfg.Provider.Maintenance.UserAllowList, username) {
				// NOTE(Dan): The Core currently refuse to show any of the 5XX results, so we use a 4XX return code instead.
				return rpc.Actor{}, util.HttpErr(http.StatusNotFound, "Service is currently undergoing maintenance.")
			}
		}

		if username == rpc.ActorSystem.Username {
			return rpc.ActorSystem, nil
		} else {
			return rpc.Actor{
				Username: username,
				Role:     rpc.RoleService, // needed to make rpc layer happy
			}, nil
		}
	}

	rpc.DefaultServer.Mux = http.NewServeMux()

	if mode == cfg.ServerModeServer {
		rpc.DefaultClient = &rpc.Client{
			RefreshToken: cfg.Server.RefreshToken,
			BasePath:     cfg.Provider.Hosts.UCloud.ToURL(),
			Client: &http.Client{
				Timeout: 10 * time.Second,
			},
		}

		go func() {
			ipc.InitIpc()
		}()
	}

	cfg.OwnEnvoySecret = envoySecret

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

	if mode == cfg.ServerModeServer {
		metricsServerHandler = controller.InitMetrics()
		migrations.Init()
		db.Migrate()
	}

	controller.Init(rpc.DefaultServer.Mux)
	svc.Init()
	controller.InitLate()
	svc.InitLater()

	if mode == cfg.ServerModeServer {
		launchMetricsServer()
		gateway.InitIpc()
	}

	if mode == cfg.ServerModeServer {
		audit.Init()
	} else {
		rpc.AuditConsumer = func(event rpc.HttpCallLogEntry) {
			log.Info("%v/%v %v", event.RequestName, event.ResponseCode, time.Duration(event.ResponseTimeNanos)*time.Nanosecond)
		}
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
			controller.UCloudUsername = flag.Arg(2)
		}

		s := &http.Server{
			Addr: fmt.Sprintf(":%v", serverPort),
			Handler: collapseServerSlashes(
				http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
					handler, _ := rpc.DefaultServer.Mux.Handler(request)
					handler.ServeHTTP(writer, request)
				}),
			),
		}
		err := s.ListenAndServe()

		if err != nil {
			fmt.Printf("Failed to start listener on port %v\n", gateway.ServerClusterPort)
			os.Exit(1)
		}
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

func collapseServerSlashes(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		p := r.URL.Path
		if p == "" {
			next.ServeHTTP(w, r)
			return
		}

		// Preserve a trailing slash (except for root)
		trailing := strings.HasSuffix(p, "/") && p != "/"

		// Replace until stable
		clean := p
		for {
			newp := strings.ReplaceAll(clean, "//", "/")
			if newp == clean {
				break
			}
			clean = newp
		}
		if trailing && clean != "/" && !strings.HasSuffix(clean, "/") {
			clean += "/"
		}

		if clean == p {
			next.ServeHTTP(w, r)
			return
		}

		// Clone request and update path (leave query untouched)
		r2 := r.Clone(r.Context())
		u := *r.URL
		u.Path = clean
		r2.URL = &u
		next.ServeHTTP(w, r2)
	})
}
