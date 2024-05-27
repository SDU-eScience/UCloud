package im

import (
    "flag"
    "fmt"
    "net/http"
    "os"
    "strconv"
    cfg "ucloud.dk/pkg/im/config"
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

    /*
       database, err := sql.Open("postgres", "postgres://postgres:postgrespassword@localhost/postgres")
       if err != nil {
           log.Fatalf("Could not open database %v", err)
       }
       _ = Database
    */
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

    cfg.Parse(mode, *configDir+"/config.yml")

    userModeSecret, userModeSecretOk := os.LookupEnv("UCLOUD_USER_SECRET")
    fmt.Printf("env=%v\n", os.Environ())
    if mode == cfg.ServerModeUser && (!userModeSecretOk || userModeSecret == "") {
        fmt.Printf("No user-Mode secret specified!\n")
        os.Exit(1)
    }

    _ = pluginName

    gatewayConfigChannel := make(chan []byte)
    if mode == cfg.ServerModeServer {
        // TODO(Dan): Update the gateway to perform JWT validation. This should not (and must not) pass the JWT to the
        //   upstreams. Gateway must be configured to send the user secret to all user instances. For the server
        //  instance it must select a secret at random and use it for all subsequent requests. The secret must be
        //  captured by all relevant requests and validated. The secret is used as a signal that Envoy has successfully
        //  validated a JWT without passing on the JWT.
        gateway.Initialize(gateway.Config{
            ListenAddress:   "0.0.0.0",
            Port:            8889,
            InitialClusters: nil,
            InitialRoutes:   nil,
        }, gatewayConfigChannel)

        gateway.Resume()
    } else if mode == cfg.ServerModeUser {

    }

    moduleArgs := ModuleArgs{
        Mode:                 mode,
        GatewayConfigChannel: gatewayConfigChannel,
        Database:             nil,
        ConfigDir:            *configDir,
        UserModeSecret:       userModeSecret,
    }

    // Once all critical parts are loaded, we can trigger the reloadable part of the code's main function
    if *reloadable {
        watchForReload(&moduleArgs)
    } else {
        module := ReloadableModule{
            ModuleMain: ModuleMainStub,
            ModuleExit: ModuleExitStub,
        }

        reloadModule(&module, &moduleArgs)
    }

    log.Info("UCloud is ready!")

    if mode == cfg.ServerModeServer {
        // NOTE(Dan): The initial setup is _not_ reloadable. This is similar to how the HTTP server setup is also not
        // reloadable.

        go func() {
            ipc.InitIpc()
        }()
    }

    if mode == cfg.ServerModeServer || mode == cfg.ServerModeUser {
        serverPort := gateway.ServerClusterPort
        if mode == cfg.ServerModeUser {
            port, _ := strconv.Atoi(flag.Arg(1))
            serverPort = port
        }

        log.Info("Starting up on %v. 0=%v 1=%v 2=%v", serverPort, flag.Arg(0), flag.Arg(1), flag.Arg(2))

        err := http.ListenAndServe(
            fmt.Sprintf(":%v", serverPort),
            http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
                handler, _ := _internalMux.Handler(request)
                handler.ServeHTTP(writer, request)
            }),
        )

        if err != nil {
            fmt.Printf("Failed to start listener on port %v\n", gateway.ServerClusterPort)
            os.Exit(1)
        }
    }
}
