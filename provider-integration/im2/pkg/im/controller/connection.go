package controller

import (
    "fmt"
    "net/http"
    "os"
    "os/exec"
    "path/filepath"
    "sync"
    cfg "ucloud.dk/pkg/im/config"
    "ucloud.dk/pkg/im/gateway"
    "ucloud.dk/pkg/log"
    "ucloud.dk/pkg/util"
)

func controllerConnection(mux *http.ServeMux) {
    providerId := cfg.Provider.Id
    if cfg.Mode == cfg.ServerModeServer {
        baseContext := fmt.Sprintf("/ucloud/%v/integration/", providerId)

        type initRequest struct {
            Username string `json:"username,omitempty"`
        }
        mux.HandleFunc(
            baseContext+"init",
            HttpUpdateHandler[initRequest](0, func(w http.ResponseWriter, r *http.Request, request initRequest) {
                if !LaunchUserInstances {
                    sendStatusCode(w, http.StatusNotFound)
                    return
                }

                uid := mapUserToUid(request.Username)
                if uid <= 0 {
                    sendStatusCode(w, http.StatusBadRequest)
                    return
                }

                // Try in a few different ways to get the most reliable exe which we will pass to `sudo`
                exe, err := os.Executable()
                if err != nil {
                    exe = "ucloud"
                } else {
                    abs, err := filepath.Abs(exe)
                    if err == nil {
                        exe = abs
                    }
                }

                port, valid := allocatePortIfNeeded(uid)
                if valid {
                    startupLogFile := filepath.Join(cfg.Provider.Logs.Directory, fmt.Sprintf("user-startup-%v.log", uid))
                    logFile, err := os.OpenFile(
                        startupLogFile,
                        os.O_RDONLY|os.O_WRONLY|os.O_CREATE,
                        0600,
                    )

                    if err != nil {
                        log.Warn("Could not create startup log file for user at %v", logFile)
                        sendStatusCode(w, http.StatusInternalServerError)
                        return
                    }

                    secret := util.RandomToken(16)

                    var args []string
                    args = append(args, "--preserve-env=UCLOUD_USER_SECRET")
                    args = append(args, "-u")
                    args = append(args, fmt.Sprintf("#%v", uid))
                    args = append(args, exe)
                    args = append(args, "user")
                    args = append(args, fmt.Sprint(port))

                    child := exec.Command("sudo", args...)
                    child.Stdout = logFile
                    child.Stderr = logFile
                    child.Env = append(os.Environ(), fmt.Sprintf("UCLOUD_USER_SECRET=%v", secret))

                    err = child.Start()
                    if err != nil {
                        log.Warn("Failed to start UCloud/User process for uid=%v. There might be information in %v", uid, startupLogFile)
                        util.SilentClose(logFile)
                        sendStatusCode(w, http.StatusInternalServerError)
                        return
                    }

                    go func() {
                        err = child.Wait()
                        log.Warn("IM/User for uid=%v terminated unexpectedly with error: %v", uid, err)
                        log.Warn("You might be able to find more information in the log file: %v", startupLogFile)
                        log.Warn("The instance will be automatically restarted when the user makes another request.")

                        instanceMutex.Lock()
                        delete(runningInstances, uid)
                        instanceMutex.Unlock()

                        util.SilentClose(logFile)
                    }()

                    gateway.SendMessage(gateway.ConfigurationMessage{
                        ClusterUp: &gateway.EnvoyCluster{
                            Name:    request.Username,
                            Address: "127.0.0.1",
                            Port:    port,
                            UseDNS:  false,
                        },
                        RouteUp: &gateway.EnvoyRoute{
                            Cluster:    request.Username,
                            Identifier: request.Username,
                            Type:       gateway.RouteTypeUser,
                        },
                    })
                }

                sendStatusCode(w, http.StatusOK)
            }),
        )
    }
}

func mapUserToUid(username string) int64 {
    if username == "user" {
        return 11043
    }
    return -1
}

var runningInstances = map[int64]bool{}
var instanceMutex = sync.Mutex{}
var portAllocator = gateway.ServerClusterPort + 1

func allocatePortIfNeeded(uid int64) (port int, valid bool) {
    instanceMutex.Lock()
    defer instanceMutex.Unlock()

    _, ok := runningInstances[uid]
    if ok {
        return 0, false
    } else {
        allocatedPort := portAllocator
        portAllocator += 1
        runningInstances[uid] = true
        return allocatedPort, true
    }
}
