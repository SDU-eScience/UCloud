package controller

import (
	"fmt"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"slices"
	"sync"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	"ucloud.dk/shared/pkg/apm"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/pkg/im/ipc"

	"ucloud.dk/shared/pkg/client"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/im/gateway"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

type Manifest struct {
	Enabled                bool                `json:"enabled"`
	ExpireAfterMs          util.Option[uint64] `json:"expireAfterMs"`
	RequiresMessageSigning bool                `json:"requiresMessageSigning"`
	UnmanagedConnections   bool                `json:"unmanagedConnections"`
}

var Connections ConnectionService

type ConnectionService struct {
	Initiate          func(username string, signingKey util.Option[int]) (redirectToUrl string, err error)
	Unlink            func(username string, uid uint32) error
	RetrieveManifest  func() Manifest
	RetrieveCondition func() Condition
}

type IdentityManagementService struct {
	InitiateConnection        func(username string) (string, error)
	HandleAuthentication      func(username string) (uint32, error)
	HandleProjectNotification func(updated *NotificationProjectUpdated) bool
	UnmanagedConnections      bool
	ExpiresAfter              util.Option[uint64]
}

var IdentityManagement IdentityManagementService

func ConnectionError(error string) string {
	return "TODO" // TODO
}

var connectionCompleteCallbacks []func(username string, uid uint32)
var projectNotificationCallbacks []func(updated *NotificationProjectUpdated)

func OnConnectionComplete(callback func(username string, uid uint32)) {
	connectionCompleteCallbacks = append(connectionCompleteCallbacks, callback)
}

func OnProjectNotification(callback func(updated *NotificationProjectUpdated)) {
	projectNotificationCallbacks = append(projectNotificationCallbacks, callback)
}

func CreatePersonalProviderProject(username string) (string, error) {
	type Req struct {
		Username string `json:"username"`
	}
	type Resp struct {
		ProjectId string `json:"projectId"`
	}

	resp, err := client.ApiUpdate[Resp](
		"projects.v2.createPersonalProviderProject",
		"/api/projects/v2",
		"createPersonalProviderProject",
		Req{
			Username: username,
		},
	)

	if err != nil {
		return "", err
	} else {
		return resp.ProjectId, err
	}
}

func RegisterConnectionComplete(username string, uid uint32, notifyUCloud bool) error {
	return RegisterConnectionCompleteEx(username, uid, notifyUCloud, util.OptNone[uint64]())
}

func RegisterConnectionCompleteEx(username string, uid uint32, notifyUCloud bool, expiresAfterOverride util.Option[uint64]) error {
	type Req struct {
		Username string `json:"username"`
	}
	type Resp struct{}

	if LaunchUserInstances {
		existing, ok, _ := MapUCloudToLocal(username)
		if ok && existing != uid {
			log.Info("Wanted to perform registration which would change the UID for %s from %v to %v! "+
				"This is not possible. Please clean up manually if this is intended!", username, existing, uid)

			return util.UserHttpError("Unable to switch to a different user. Please login with the original "+
				"account (from %v to %v).", existing, uid)
		}
	}

	if notifyUCloud {
		log.Info("Registering connection complete %v -> %v", username, uid)
		_, err := client.ApiUpdate[Resp](
			"providers.im.control.approveConnection",
			"/api/providers/integration/control",
			"approveConnection",
			Req{username},
		)

		if err != nil {
			return err
		}
	}

	if uid == UnknownUser {
		return nil
	}

	expiresAfter := IdentityManagement.ExpiresAfter
	if !expiresAfter.Present {
		expiresAfter = expiresAfterOverride
	}

	var expiresAfterStringPtr *string
	if expiresAfter.Present {
		expiresAfterStringPtr = util.Pointer(fmt.Sprintf("%v milliseconds", expiresAfter.Value))
	}

	err := db.NewTx[error](func(tx *db.Transaction) error {
		db.Exec(
			tx,
			`
				insert into connections(ucloud_username, uid, expires_at)
				values (:username, :uid, now() + cast(:expires_after as interval))
				on conflict (ucloud_username) do update set
					expires_at = excluded.expires_at
			`,
			db.Params{
				"username":      username,
				"uid":           uid,
				"expires_after": expiresAfterStringPtr,
			},
		)

		err := tx.ConsumeError()
		if err != nil {
			log.Warn("Failed to register connection. Underlying error is: %v", err)
			return &util.HttpError{
				StatusCode: http.StatusInternalServerError,
				Why:        "Failed to register connection.",
			}
		}
		return nil
	})

	if err != nil {
		return err
	}

	userReplayChannel <- username

	for _, callback := range connectionCompleteCallbacks {
		callback(username, uid)
	}
	return err
}

func RemoveConnection(uid uint32, notifyUCloud bool) error {
	ucloud, ok, _ := MapLocalToUCloud(uid)
	if !ok {
		return fmt.Errorf("unknown user supplied: %v", uid)
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update connections
				set expires_at = now()
				where uid = :uid
		    `,
			db.Params{
				"uid": uid,
			},
		)
	})

	if notifyUCloud {
		type Req struct {
			Username string `json:"username"`
		}
		_, err := client.ApiUpdate[util.Empty](
			"providers.im.control.clearConnection",
			"/api/providers/integration/control",
			"clearConnection",
			Req{Username: ucloud},
		)

		if err != nil {
			_ = RegisterConnectionComplete(ucloud, uid, false)
			return fmt.Errorf("failed to clear connection in UCloud: %v", err)
		}
	}

	return nil
}

func getDebugPort(ucloudUsername string) (int, bool) {
	// TODO add this to config make it clear that this is insecure and for development _only_
	if util.DevelopmentModeEnabled() {
		if ucloudUsername == "user" {
			return 51234, true
		}
		if ucloudUsername == "user2" {
			return 51235, true
		}
	}
	return 0, false
}

func MapUCloudToLocal(username string) (uint32, bool, bool) {
	return db.NewTx3[uint32, bool, bool](func(tx *db.Transaction) (uint32, bool, bool) {
		val, ok := db.Get[struct {
			Uid      uint32
			IsActive bool
		}](
			tx,
			`
				select uid, expires_at is null or now() < expires_at as is_active
				from connections
				where
					ucloud_username = :username
			`,
			db.Params{
				"username": username,
			},
		)
		if !ok {
			return UnknownUser, false, false
		}

		return val.Uid, true, val.IsActive
	})
}

func MapUCloudUsersToLocalUsers(usernames []string) map[string]uint32 {
	return db.NewTx[map[string]uint32](func(tx *db.Transaction) map[string]uint32 {
		rows := db.Select[struct {
			Username string
			Uid      uint32
		}](
			tx,
			`
				with data as (
					select unnest(cast(:usernames as text[])) as username
				)
				select
					c.ucloud_username as username,
					c.uid
				from
					data d
					join connections c on d.username = c.ucloud_username
			`,
			db.Params{
				"usernames": usernames,
			},
		)

		result := map[string]uint32{}
		for _, row := range rows {
			result[row.Username] = row.Uid
		}
		return result
	})
}

func MapLocalToUCloud(uid uint32) (string, bool, bool) {
	return db.NewTx3[string, bool, bool](func(tx *db.Transaction) (string, bool, bool) {
		val, ok := db.Get[struct {
			UCloudUsername string
			IsActive       bool
		}](
			tx,
			`
				select ucloud_username, expires_at is null or now() < expires_at as is_active
				from connections
				where
					uid = :uid
			`,
			db.Params{
				"uid": uid,
			},
		)

		if !ok {
			return "_guest", false, false
		}

		return val.UCloudUsername, true, val.IsActive
	})
}

func RegisterProjectMapping(projectId string, gid uint32) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into project_connections(ucloud_project_id, gid)
				values (:project_id, :group_id)
			`,
			db.Params{
				"project_id": projectId,
				"group_id":   gid,
			},
		)
	})
}

func MapLocalProjectToUCloud(gid uint32) (string, bool) {
	return db.NewTx2(func(tx *db.Transaction) (string, bool) {
		val, ok := db.Get[struct{ UCloudProjectId string }](
			tx,
			`
				select ucloud_project_id
				from project_connections
				where
					gid = :gid
			`,
			db.Params{
				"gid": gid,
			},
		)
		return val.UCloudProjectId, ok
	})
}

func MapUCloudProjectToLocal(projectId string) (uint32, bool) {
	return db.NewTx2(func(tx *db.Transaction) (uint32, bool) {
		val, ok := db.Get[struct{ Gid uint32 }](
			tx,
			`
				select gid
				from project_connections
				where
					ucloud_project_id = :project_id
			`,
			db.Params{
				"project_id": projectId,
			},
		)
		if !ok {
			return UnknownUser, false
		}

		return val.Gid, true
	})
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

	if RunsServerCode() {
		go monitorInstances()
	}

	if RunsServerCode() {
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
					redirectTo, err := Connections.Initiate(request.Username, util.OptNone[int]())
					sendResponseOrError(w, connectResponse{redirectTo}, err)
				}
			}),
		)

		type unlinkedRequest struct {
			Username string `json:"username"`
		}
		mux.HandleFunc(
			baseContext+"unlinked",
			HttpUpdateHandler[unlinkedRequest](0, func(w http.ResponseWriter, r *http.Request, request unlinkedRequest) {
				local, ok, _ := MapUCloudToLocal(request.Username)
				if !ok {
					sendError(w, util.UserHttpError("Unknown user is being unlinked"))
					return
				}

				unlinkFn := Connections.Unlink
				if unlinkFn != nil {
					err := unlinkFn(request.Username, local)
					if err != nil {
						sendError(w, err)
						return
					}
				}

				err := RemoveConnection(local, false)
				sendResponseOrError(w, util.EmptyValue, err)
			}),
		)

		type retrieveConditionRequest struct{}
		mux.HandleFunc(
			baseContext+"retrieveCondition",
			HttpRetrieveHandler[retrieveConditionRequest](0, func(w http.ResponseWriter, r *http.Request, _ retrieveConditionRequest) {
				fn := Connections.RetrieveCondition
				if fn != nil {
					sendResponseOrError(w, Connections.RetrieveCondition(), nil)
				} else {
					sendResponseOrError(w, nil, util.HttpErr(404, "Not found"))
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

				redirectTo, _ := Connections.Initiate(info.Username, util.OptValue(keyId))
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
					log.Info("Attempting to launch user instance, but this IM will not launch user instances.")
					sendStatusCode(w, http.StatusNotFound)
					return
				}

				uid, ok, _ := MapUCloudToLocal(request.Username)
				if uid <= 0 || !ok {
					log.Warn("Could not map UCloud to local identity: %v -> %v (%v)", request.Username, uid, ok)
					sendStatusCode(w, http.StatusBadRequest)
					return
				}

				err := LaunchUserInstance(uid)
				sendResponseOrError(w, util.EmptyValue, err)
			}),
		)

		InitiateReverseConnectionFromUser.Handler(func(req *ipc.Request[util.Empty]) ipc.Response[string] {
			if !cfg.Services.Unmanaged {
				return ipc.Response[string]{
					StatusCode:   http.StatusForbidden,
					ErrorMessage: "This provider does not support the 'ucloud connect' command",
				}
			}

			_, exists, _ := MapLocalToUCloud(req.Uid)
			if exists {
				return ipc.Response[string]{
					StatusCode:   http.StatusConflict,
					ErrorMessage: "You have already connected to UCloud with this user",
				}
			}

			resp, err := apm.InitiateReverseConnection()
			if err != nil {
				return ipc.Response[string]{
					StatusCode:   http.StatusInternalServerError,
					ErrorMessage: err.Error(),
				}
			}

			db.NewTx0(func(tx *db.Transaction) {
				db.Exec(
					tx,
					`
						insert into reverse_connections(uid, token)
						values (:uid, :token)
					`,
					db.Params{
						"uid":   req.Uid,
						"token": resp.Token,
					},
				)
			})

			ucloudUrl := cfg.Provider.Hosts.UCloudPublic.ToURL()
			return ipc.Response[string]{
				StatusCode: http.StatusOK,
				Payload:    fmt.Sprintf("%s/app/connection?token=%s", ucloudUrl, resp.Token),
			}
		})

		Whoami.Handler(func(req *ipc.Request[util.Empty]) ipc.Response[string] {
			username, exists, _ := MapLocalToUCloud(req.Uid)
			if exists {
				return ipc.Response[string]{
					StatusCode: http.StatusOK,
					Payload:    username,
				}
			} else {
				return ipc.Response[string]{
					StatusCode: http.StatusNotFound,
				}
			}
		})

		type reverseConnectionClaimedRequest struct {
			Token    string `json:"token"`
			Username string `json:"username"`
		}
		mux.HandleFunc(
			baseContext+"reverseConnectionClaimed",
			HttpUpdateHandler(0, func(w http.ResponseWriter, r *http.Request, req reverseConnectionClaimedRequest) {
				uid, ok := db.NewTx2(func(tx *db.Transaction) (uint32, bool) {
					uidWrapper, ok := db.Get[struct {
						Uid uint32
					}](
						tx,
						`
							delete from reverse_connections
							where token = :token
							returning uid
					    `,
						db.Params{
							"token": req.Token,
						},
					)

					return uidWrapper.Uid, ok
				})

				if !ok {
					sendError(w, &util.HttpError{
						StatusCode: http.StatusNotFound,
						Why:        "Unknown token supplied. Try again.",
					})
					return
				}

				err := RegisterConnectionCompleteEx(req.Username, uid, false, util.OptValue[uint64](1000*60*60*24*7))
				if err != nil {
					sendError(w, err)
					return
				}

				_, err = CreatePersonalProviderProject(req.Username)
				sendResponseOrError(w, util.EmptyValue, err)
			}),
		)
	}
}

func monitorInstances() {
	for util.IsAlive {
		if LaunchUserInstances {
			instanceMutex.Lock()
			for uid, _ := range runningInstances {
				_, ok, isActive := MapLocalToUCloud(uid)
				if !ok || !isActive {
					RequestUserTermination(uid)
				}
			}
			instanceMutex.Unlock()
		}

		time.Sleep(5 * time.Second)
	}
}

func LaunchUserInstance(uid uint32) error {
	return LaunchUserInstanceEx(uid, true)
}

func LaunchUserInstanceEx(uid uint32, tryAgain bool) error {
	if !LaunchUserInstances {
		return fmt.Errorf("attempting to launch user instance, but this IM will not launch user instances")
	}

	ucloudUsername, ok, isActive := MapLocalToUCloud(uid)
	if !ok {
		return fmt.Errorf("unknown user")
	}

	if !isActive {
		type clearReq struct {
			Username string `json:"username"`
			Provider string `json:"provider"`
		}
		_, err := client.ApiUpdate[util.Empty](
			"providers.im.control.clearConnection",
			"/api/providers/integration/control",
			"clearConnection",
			clearReq{
				Username: ucloudUsername,
				Provider: cfg.Provider.Id,
			},
		)

		if err != nil {
			log.Warn("Failed to clear connection at UCloud for %s: %v", ucloudUsername, err)
		}
		return util.UserHttpError("You need to reauthenticate with the system before continuing to use it.")
	}

	allowList := cfg.Provider.Maintenance.UserAllowList
	if len(allowList) > 0 {
		if !slices.Contains(allowList, ucloudUsername) {
			return util.ServerHttpError("System is currently undergoing maintenance")
		}
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
			return fmt.Errorf("could not create startup log file for user at %v", logFile)
		}

		secret := util.RandomToken(16)

		var args []string
		args = append(args, "--preserve-env=UCLOUD_USER_SECRET")
		args = append(args, "-u")
		args = append(args, fmt.Sprintf("#%v", uid))
		debugPort, shouldDebug := getDebugPort(ucloudUsername)
		if shouldDebug {
			args = append(args, "/usr/bin/dlv")
			args = append(args, "exec")
		}
		args = append(args, exe)
		if shouldDebug {
			args = append(args, "--headless", "--api-version=2", "--continue", "--accept-multiclient")
			args = append(args, fmt.Sprintf("--listen=0.0.0.0:%v", debugPort))
			args = append(args, "--")
		}
		args = append(args, "user")
		args = append(args, fmt.Sprint(port))
		args = append(args, ucloudUsername)

		child := exec.Command("sudo", args...)
		child.Stdout = logFile
		child.Stderr = logFile
		child.Env = append(os.Environ(), fmt.Sprintf("UCLOUD_USER_SECRET=%v", secret))

		err = child.Start()
		if err != nil {
			util.SilentClose(logFile)
			return fmt.Errorf("failed to start UCloud/User process for uid=%v see %v", uid, startupLogFile)
		}
		userInstancesLaunched.Inc()

		cluster := &gateway.EnvoyCluster{
			Name:    ucloudUsername,
			Address: "127.0.0.1",
			Port:    port,
			UseDNS:  false,
		}
		route := &gateway.EnvoyRoute{
			Cluster:        ucloudUsername,
			Identifier:     ucloudUsername,
			Type:           gateway.RouteTypeUser,
			EnvoySecretKey: secret,
		}

		go func() {
			err = child.Wait()
			if err != nil {
				log.Warn("IM/User for uid=%v terminated unexpectedly with error: %v", uid, err)
				log.Warn("You might be able to find more information in the log file: %v", startupLogFile)
				log.Warn("The instance will be automatically in a few seconds.")
			}

			gateway.SendMessage(gateway.ConfigurationMessage{
				RouteDown:   route,
				ClusterDown: cluster,
			})

			instanceMutex.Lock()
			delete(runningInstances, uid)
			instanceMutex.Unlock()

			util.SilentClose(logFile)

			if tryAgain {
				time.Sleep(5 * time.Second)
				_ = LaunchUserInstanceEx(uid, false)
			}
		}()

		gateway.SendMessage(gateway.ConfigurationMessage{
			ClusterUp: cluster,
			RouteUp:   route,
		})
	}
	return nil
}

var InitiateReverseConnectionFromUser = ipc.NewCall[util.Empty, string]("connection.initiateReverseConnectionFromUser")
var Whoami = ipc.NewCall[util.Empty, string]("connection.whoami")

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

var (
	userInstancesLaunched = promauto.NewCounter(prometheus.CounterOpts{
		Namespace: "ucloud_im",
		Name:      "launched_user_instances",
		Help:      "The total number of UCloud/IM (User) instances launched",
	})
)

const UnknownUser = 11400
