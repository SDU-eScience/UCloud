package controller

import (
	"fmt"
	"net/http"
	"os"
	"sync"
	"time"
	fnd "ucloud.dk/pkg/foundation"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

var usersToTerminateMutex = sync.Mutex{}
var usersToTerminate = map[uint32]fnd.Timestamp{}
var livenessVersion = util.RandomToken(16)

func RequestUserTermination(uid uint32) {
	usersToTerminateMutex.Lock()
	defer usersToTerminateMutex.Unlock()

	usersToTerminate[uid] = fnd.Timestamp(time.Now())
}

func initLiveness() {
	if cfg.Mode == cfg.ServerModeServer {
		requestLiveness.Handler(func(r *ipc.Request[util.Empty]) ipc.Response[LivenessResponse] {
			usersToTerminateMutex.Lock()
			restartBefore, ok := usersToTerminate[r.Uid]
			usersToTerminateMutex.Unlock()
			if !ok {
				restartBefore = fnd.TimeFromUnixMilli(0)
			}

			return ipc.Response[LivenessResponse]{
				StatusCode: http.StatusOK,
				Payload: LivenessResponse{
					Version:                livenessVersion,
					RestartIfStartedBefore: restartBefore,
				},
			}
		})
	} else if cfg.Mode == cfg.ServerModeUser {
		startedAt := time.Now()
		knownVersion := ""

		livenessExit := func() {
			ppid := os.Getppid()

			link, _ := os.Readlink(fmt.Sprintf("/proc/%d/exe", ppid))
			if link == "/usr/bin/dlv" {
				process, err := os.FindProcess(ppid)
				if err == nil {
					_ = process.Kill()
				}
			}

			os.Exit(0)
		}

		go func() {
			for util.IsAlive {
				resp, err := requestLiveness.Invoke(util.Empty{})
				if err != nil {
					log.Info("Shutting down due to liveness check. Cause: %v", err)
					livenessExit()
				}

				if knownVersion == "" {
					knownVersion = resp.Version
				} else if knownVersion != resp.Version {
					log.Info("Shutting down due to liveness check. Cause: UCloud/IM server has restarted.")
					livenessExit()
				}

				if startedAt.Before(resp.RestartIfStartedBefore.Time()) {
					log.Info("Shutting down due to liveness check. Cause: UCloud/IM server has requested a restart.")
					livenessExit()
				}

				time.Sleep(time.Second)
			}
		}()
	}
}

type LivenessResponse struct {
	Version                string
	RestartIfStartedBefore fnd.Timestamp
}

var (
	requestLiveness = ipc.NewCall[util.Empty, LivenessResponse]("ctrl.liveness.requestLiveness")
)
