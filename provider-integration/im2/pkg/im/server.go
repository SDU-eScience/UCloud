package im

import (
    "database/sql"
    "flag"
    "fmt"
    "log"
    "net"
    "net/http"
    "os"
    "plugin"
    "time"
    cfg "ucloud.dk/pkg/im/config"
    "ucloud.dk/pkg/im/gateway"
    "ucloud.dk/pkg/util"
)

type ReloadableModule struct {
    ModuleMain func(oldPluginData []byte, args map[string]any)
    ModuleExit func() []byte
}

func createTempCopy(path, tempdir string) string {
    newFile, err := os.CreateTemp(tempdir, "*.so")
    if err != nil {
        return ""
    }
    defer util.SilentClose(newFile)

    bytes, err := os.ReadFile(path)
    if err != nil {
        return ""
    }

    _, err = newFile.Write(bytes)
    if err != nil {
        return ""
    }

    return newFile.Name()
}

var _internalMux *http.ServeMux = nil
var _currentModule *ReloadableModule = nil

func reloadModule(
    newModule *ReloadableModule,
    mode cfg.ServerMode,
    gatewayConfigChannel chan map[string]any,
    db *sql.DB,
    configDir string,
    userModeSecret string,
) {
    var oldPluginData []byte = nil
    if _currentModule != nil {
        oldPluginData = _currentModule.ModuleExit()
    }

    _internalMux = http.NewServeMux()
    args := make(map[string]any)
    args["mux"] = _internalMux
    args["mode"] = mode
    args["db"] = db
    args["gatewayConfigChannel"] = gatewayConfigChannel
    args["configDir"] = configDir
    args["userModeSecret"] = userModeSecret

    newModule.ModuleMain(oldPluginData, args)
    _currentModule = newModule
}

func Launch() {
    var (
        configDir      = flag.String("config-dir", "/etc/ucloud", "Path to the configuration directory used by the IM")
        userModeSecret = flag.String("user-mode-secret", "", "User-mode secret which must be passed in all requests")
        reloadable     = flag.Bool("reloadable", false, "Whether to enable hot-reloading of the module")
    )

    flag.Parse()
    if !flag.Parsed() {
        os.Exit(1)
    }

    cfg.Parse(cfg.ServerModeServer, *configDir+"/config.yml")

    /*
       db, err := sql.Open("postgres", "postgres://postgres:postgrespassword@localhost/postgres")
       if err != nil {
           log.Fatalf("Could not open database %v", err)
       }
       _ = db
    */
    _ = cfg.ReadPublicKey(*configDir)

    mode := cfg.ServerModePlugin
    pluginName := ""
    switch flag.Arg(0) {
    case "user":
        mode = cfg.ServerModeUser
    case "server":
        mode = cfg.ServerModeServer
    case "proxy":
        mode = cfg.ServerModeProxy
    default:
        mode = cfg.ServerModePlugin
        pluginName = flag.Arg(1)
    }

    if mode == cfg.ServerModeUser && len(*userModeSecret) == 0 {
        fmt.Printf("No user-mode secret specified!\n")
        os.Exit(1)
    }

    _ = mode
    _ = pluginName

    var listener net.Listener = nil
    _ = listener
    gatewayConfigChannel := make(chan map[string]any)
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
        })

        // TODO
        _ = gatewayConfigChannel

        gateway.Resume()
    } else if mode == cfg.ServerModeUser {

    }

    // Once all critical parts are loaded, we can trigger the reloadable part of the code's main function
    if *reloadable {
        log.Printf("Hot-reloading modules enabled!\n")
        lastMod := time.Now()

        reload := func() {
            time.Sleep(time.Duration(1) * time.Second)
            info, err := os.Stat("./bin/module.so")
            if err == nil && info.ModTime() != lastMod {
                copied := createTempCopy("./bin/module.so", "")
                plug, err := plugin.Open(copied)
                if err != nil {
                    return
                }

                newPlugin := ReloadableModule{}

                sym, err := plug.Lookup("ModuleMain")
                if err != nil {
                    log.Printf("Error looking up plugin ModuleMain %v", err)
                    return
                }
                newPlugin.ModuleMain = sym.(func(old []byte, args map[string]any))

                sym, err = plug.Lookup("ModuleExit")
                if err != nil {
                    log.Printf("Error looking up plugin ModuleMain %v", err)
                    return
                }
                newPlugin.ModuleExit = sym.(func() []byte)

                reloadModule(&newPlugin, mode, gatewayConfigChannel, nil, *configDir, *userModeSecret)
            }
        }

        go func() {
            for {
                reload()
            }
        }()
    } else {
        module := ReloadableModule{
            ModuleMain: ModuleMain,
            ModuleExit: ModuleExit,
        }

        reloadModule(&module, mode, gatewayConfigChannel, nil, *configDir, *userModeSecret)
    }

    mux := http.NewServeMux()
    err := http.ListenAndServe(fmt.Sprintf(":%v", gateway.ServerClusterPort), mux)
    if err != nil {
        fmt.Printf("Failed to start listener on port %v\n", gateway.ServerClusterPort)
        os.Exit(1)
    }
}
