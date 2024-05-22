package im

import (
    "database/sql"
    "fmt"
    "io"
    "log"
    "net/http"
    "os"
    "os/exec"
    "os/signal"
    "path/filepath"
    "plugin"
    "strings"
    "sync"
    "syscall"
    "time"
    cfg "ucloud.dk/pkg/im/config"
    "ucloud.dk/pkg/util"
)

var _internalMux *http.ServeMux = nil
var _currentModule *ReloadableModule = nil

type ModuleArgs struct {
    Mode                 cfg.ServerMode
    GatewayConfigChannel chan map[string]any
    Database             *sql.DB
    ConfigDir            string
    UserModeSecret       string
    ServerMultiplexer    *http.ServeMux
}

func ModuleMainStub(oldData []byte, args map[string]any) {
    // NOTE(Dan): This function must take untyped args since we cannot pass the ModuleArgs from the reload-er to the
    // reload-ee. Here we do some quick unmarshalling of the data to make it easier to use.

    mux := args["ServerMultiplexer"].(*http.ServeMux)
    mode := cfg.ServerMode(args["Mode"].(int))
    configDir := args["ConfigDir"].(string)
    userModeSecret := args["UserModeSecret"].(string)
    db := args["Database"].(*sql.DB)                                           // can be nil
    gatewayConfigChannel := args["GatewayConfigChannel"].(chan map[string]any) // can be nil

    newArgs := ModuleArgs{
        Mode:                 mode,
        GatewayConfigChannel: gatewayConfigChannel,
        Database:             db,
        ConfigDir:            configDir,
        UserModeSecret:       userModeSecret,
        ServerMultiplexer:    mux,
    }

    ModuleMain(oldData, &newArgs)
}

func ModuleExitStub() []byte {
    return ModuleExit()
}

type ReloadableModule struct {
    ModuleMain func(oldPluginData []byte, args map[string]any)
    ModuleExit func() []byte
}

func reloadModule(
    newModule *ReloadableModule,
    moduleArgs *ModuleArgs,
) {
    var oldPluginData []byte = nil
    if _currentModule != nil {
        oldPluginData = _currentModule.ModuleExit()
    }

    _internalMux = http.NewServeMux()
    moduleArgs.ServerMultiplexer = _internalMux
    args := make(map[string]any)
    args["ServerMultiplexer"] = _internalMux
    args["Mode"] = int(moduleArgs.Mode)
    args["Database"] = moduleArgs.Database
    args["GatewayConfigChannel"] = moduleArgs.GatewayConfigChannel
    args["ConfigDir"] = moduleArgs.ConfigDir
    args["UserModeSecret"] = moduleArgs.UserModeSecret

    newModule.ModuleMain(oldPluginData, args)
    _currentModule = newModule
}

func watchForReload(moduleArgs *ModuleArgs) {
    log.Printf("Hot-reloading modules enabled! pid = %v\n", os.Getpid())
    signals := make(chan os.Signal, 1)
    signal.Notify(signals, syscall.SIGHUP)

    go func() {
        for {
            <-signals

            p1Start := time.Now()
            projectDir, modName := createProjectCopy([]string{"cmd", "pkg"})
            libraryPath := filepath.Join(projectDir, "module.so")

            log.Printf("ProjectDir=%v\n", projectDir)

            p2Start := time.Now()
            var args []string
            args = append(args, "build")
            args = append(args, "-buildmode=plugin")
            args = append(args, "-trimpath")
            args = append(args, "-o", libraryPath)
            args = append(args, fmt.Sprintf("%v/cmd/ucloud-im", modName))

            goBuild := exec.Command("go", args...)
            goBuild.Dir = projectDir
            goBuild.Env = append(os.Environ(), "CGO_ENABLED=1")
            goBuild.Stdout = os.Stdout
            goBuild.Stderr = os.Stderr

            if err := goBuild.Run(); err != nil {
                log.Printf("Failed to build module. You can retry with another SIGHUP!\nError: %v\n", err)
            } else {
                p3Start := time.Now()
                plug, err := plugin.Open(libraryPath)
                if err != nil {
                    return
                }

                newPlugin := ReloadableModule{}

                sym, err := plug.Lookup("ModuleMainStub")
                if err != nil {
                    log.Printf("Error looking up plugin ModuleMainStub %v", err)
                    return
                }
                newPlugin.ModuleMain = sym.(func(oldPluginData []byte, args map[string]any))

                sym, err = plug.Lookup("ModuleExitStub")
                if err != nil {
                    log.Printf("Error looking up plugin ModuleMainStub %v", err)
                    return
                }
                newPlugin.ModuleExit = sym.(func() []byte)

                p4Start := time.Now()

                log.Printf(
                    "Reload complete: Copy=%v Compile=%v Load=%v Total=%v\n",
                    p2Start.Sub(p1Start),
                    p3Start.Sub(p2Start),
                    p4Start.Sub(p3Start),
                    p4Start.Sub(p1Start),
                )
                reloadModule(&newPlugin, moduleArgs)
            }
        }
    }()

    signals <- syscall.SIGHUP
}

// Utility code
// =====================================================================================================================
// Hot-reloading in Go is quite annoying since you aren't allowed to load (Go) plugins which have already been loaded.
// This is obviously something required for hot-reloading. Our workaround for this is to instead build a completely new
// copy of our code but with a different module name. This tricks Go into being willing to load our module since it
// now appears as a completely different plugin.
//
// createProjectCopy:
//     Creates a completely new project and performs import replacement. Returns the directory and new module name.
//
// copyPackage/copyDirectory/copyRegularFile/copyGoFile:
//     Utility functions needed by createProjectCopy. These will copy the files and perform any required modifications
//     to their contents. Should not be called by any other function than createProjectCopy.

func createProjectCopy(rootDirs []string) (string, string) {
    destination, err := os.MkdirTemp("", "*")
    if err != nil {
        log.Fatalf("Failed to create temp dir for project copy: %v\n", err)
    }

    oldModuleName := "ucloud.dk"
    newModuleName := fmt.Sprintf("hot-reload-%v.ucloud.dk", time.Now().UnixNano())

    moduleReplacements := make(map[string]string, len(rootDirs))
    for _, rootDir := range rootDirs {
        oldPath := fmt.Sprintf("%v/%v", oldModuleName, rootDir)
        newPath := fmt.Sprintf("%v/%v", newModuleName, rootDir)
        moduleReplacements[oldPath] = newPath
    }

    pwd, _ := os.Getwd()
    for _, rootDir := range rootDirs {
        copyDirectory(filepath.Join(pwd, rootDir), filepath.Join(destination, rootDir), moduleReplacements)
    }

    copyPackage(filepath.Join(pwd, "go.mod"), filepath.Join(destination, "go.mod"), oldModuleName, newModuleName)
    copyRegularFile(filepath.Join(pwd, "go.sum"), filepath.Join(destination, "go.sum"))
    return destination, newModuleName
}

func copyPackage(src, dest string, oldModuleName, newModuleName string) {
    sourceBytes, err := os.ReadFile(src)
    if err != nil {
        return
    }

    sourceString := string(sourceBytes)
    sourceString = strings.ReplaceAll(sourceString, oldModuleName, newModuleName)

    _ = os.WriteFile(dest, []byte(sourceString), 0644)
}

func copyDirectory(src, dest string, moduleReplacements map[string]string) {
    var wg sync.WaitGroup

    _ = os.MkdirAll(dest, 0700)

    entries, _ := os.ReadDir(src)
    for _, entry := range entries {
        entryName := entry.Name()
        sourceFile := filepath.Join(src, entryName)
        destFile := filepath.Join(dest, entryName)

        sourceInfo, err := os.Stat(sourceFile)
        if err != nil {
            continue
        }

        wg.Add(1)

        go func() {
            defer wg.Done()

            if sourceInfo.IsDir() {
                _ = os.MkdirAll(destFile, sourceInfo.Mode())
                copyDirectory(sourceFile, destFile, moduleReplacements)
            } else {
                if strings.HasSuffix(entryName, ".go") {
                    copyGoFile(sourceFile, destFile, moduleReplacements)
                } else {
                    copyRegularFile(sourceFile, destFile)
                }
            }
        }()
    }

    wg.Wait()
}

func copyRegularFile(sourceFile, destFile string) {
    out, err := os.Create(destFile)
    if err != nil {
        return
    }

    defer util.SilentClose(out)

    in, err := os.Open(sourceFile)
    if err != nil {
        return
    }

    defer util.SilentClose(in)

    _, _ = io.Copy(out, in)
}

func copyGoFile(sourceFile, destFile string, moduleReplacements map[string]string) {
    sourceBytes, err := os.ReadFile(sourceFile)
    if err != nil {
        return
    }

    sourceString := string(sourceBytes)
    for k, v := range moduleReplacements {
        sourceString = strings.ReplaceAll(sourceString, k, v)
    }

    _ = os.WriteFile(destFile, []byte(sourceString), 0644)
}
