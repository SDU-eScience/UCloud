package controller

import (
	"fmt"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"sync"
	"ucloud.dk/pkg/client"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/im/gateway"
	"ucloud.dk/pkg/kvdb"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

type Manifest struct {
	Enabled                bool                `json:"enabled"`
	ExpiresAfterMs         util.Option[uint64] `json:"expiresAfterMs"`
	RequiresMessageSigning bool                `json:"requiresMessageSigning"`
}

var Connections ConnectionService

type ConnectionService struct {
	Initiate         func(username string, signingKey util.Option[int]) (redirectToUrl string)
	RetrieveManifest func() Manifest
}

func ConnectionError(error string) string {
	return "TODO" // TODO
}

const uidMapPrefix = "uid-map-"
const uidInvMapPrefix = "uid-inv-map-"

func RegisterConnectionComplete(username string, uid uint32) error {
	type Req struct {
		Username string `json:"username"`
	}
	type Resp struct{}

	log.Info("Registering connection complete %v -> %v", username, uid)
	_, err := client.ApiUpdate[Resp](
		"providers.im.control.approveConnection",
		"/api/providers/integration/control",
		"approveConnection",
		Req{username},
	)

	kvdb.Set(fmt.Sprintf("%v%v", uidInvMapPrefix, uid), username)
	kvdb.Set(fmt.Sprintf("%v%v", uidMapPrefix, username), uid)

	return err
}

func MapUCloudToLocal(username string) (uint32, bool) {
	val, ok := kvdb.Get(fmt.Sprintf("%v%v", uidMapPrefix, username))
	if !ok {
		return 11400, false
	}

	return val.(uint32), true
}

func MapLocalToUCloud(uid uint32) (string, bool) {
	val, ok := kvdb.Get(fmt.Sprintf("%v%v", uidInvMapPrefix, uid))
	if !ok {
		return "_guest", false
	}

	return val.(string), true
}

func RegisterSigningKey(username string, key string) int {
	return 0
}

func ActivateSigningKey(keyId int) {

}

func GetSigningKeys(username string) []string {
	return nil
}

func GetSigningKeyById(id int) util.Option[string] {
	return util.OptNone[string]()
}

type redirectEntry struct {
	Username string
}

var redirectMutex = sync.Mutex{}
var redirectStore = make(map[string]redirectEntry)

func controllerConnection(mux *http.ServeMux) {
	providerId := cfg.Provider.Id
	if cfg.Mode == cfg.ServerModeServer {
		baseContext := fmt.Sprintf("/ucloud/%v/integration/", providerId)

		type connectRequest struct {
			Username string `json:"username"`
		}
		type connectResponse struct {
			RedirectTo string `json:"redirectTo"`
		}
		mux.HandleFunc(
			baseContext+"connect",
			HttpUpdateHandler[connectRequest](0, func(w http.ResponseWriter, r *http.Request, request connectRequest) {
				manifest := Connections.RetrieveManifest()
				if manifest.RequiresMessageSigning {
					tok := util.RandomToken(32)
					redirectMutex.Lock()
					defer redirectMutex.Unlock()
					redirectStore[tok] = redirectEntry{request.Username}
					sendResponseOrError(
						w,
						connectResponse{
							cfg.Provider.Hosts.SelfPublic.ToURL() + "/" + baseContext + "keyUpload?session=" + tok,
						},
						nil,
					)
				} else {
					redirectTo := Connections.Initiate(request.Username, util.OptNone[int]())
					sendResponseOrError(w, connectResponse{redirectTo}, nil)
				}
			}),
		)

		// NOTE(Dan): Renamed from redirect in the Kotlin version to keyUpload since this is more accurate.
		type keyUploadRequest struct {
			PublicKey string `json:"publicKey"`
		}
		mux.HandleFunc(
			baseContext+"keyUpload",
			HttpUpdateHandler[keyUploadRequest](0, func(w http.ResponseWriter, r *http.Request, request keyUploadRequest) {
				session := r.URL.Query().Get("session")
				redirectMutex.Lock()
				defer redirectMutex.Unlock()
				info, ok := redirectStore[session]
				if !ok {
					sendStatusCode(w, http.StatusBadRequest)
					return
				}

				keyId := RegisterSigningKey(info.Username, request.PublicKey)

				redirectTo := Connections.Initiate(info.Username, util.OptValue(keyId))
				sendResponseOrError(w, connectResponse{redirectTo}, nil)
			}),
		)

		type retrieveManifestRequest struct{}
		mux.HandleFunc(
			baseContext+"retrieveManifest",
			HttpRetrieveHandler[retrieveManifestRequest](0, func(w http.ResponseWriter, r *http.Request, _ retrieveManifestRequest) {
				sendResponseOrError(w, Connections.RetrieveManifest(), nil)
			}),
		)

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

				uid, ok := MapUCloudToLocal(request.Username)
				if uid <= 0 || !ok {
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

var runningInstances = map[uint32]bool{}
var instanceMutex = sync.Mutex{}
var portAllocator = gateway.ServerClusterPort + 1

func allocatePortIfNeeded(uid uint32) (port int, valid bool) {
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
