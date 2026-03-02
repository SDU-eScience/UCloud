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
	cfg "ucloud.dk/pkg/config"
	"ucloud.dk/pkg/gateway"
	"ucloud.dk/pkg/ipc"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var Connections ConnectionService

type ConnectionService struct {
	Initiate         func(username string, signingKey util.Option[int]) (redirectToUrl string, err *util.HttpError)
	Unlink           func(username string, uid uint32) *util.HttpError
	RetrieveManifest func() orcapi.ProviderIntegrationManifest
}

type IdentityManagementService struct {
	InitiateConnection        func(username string) (string, *util.HttpError)
	HandleAuthentication      func(username string) (uint32, error)
	HandleProjectNotification func(updated *EventProjectUpdated) bool
	ExpiresAfter              util.Option[int]
}

var IdentityManagement IdentityManagementService

var idmConnectionCompleteCallbacks []func(username string, uid uint32)
var idmProjectNotificationCallbacks []func(updated *EventProjectUpdated)

func initIdManagement() {
	if RunsServerCode() {
		go idmMonitorInstances()
	}

	if RunsServerCode() {
		orcapi.ProviderIntegrationPConnect.Handler(func(info rpc.RequestInfo, request orcapi.ProviderIntegrationPFindByUser) (orcapi.ProviderIntegrationPConnectResponse, *util.HttpError) {
			redirectTo, err := Connections.Initiate(request.Username, util.OptNone[int]())
			return orcapi.ProviderIntegrationPConnectResponse{RedirectTo: redirectTo}, err
		})

		orcapi.ProviderIntegrationPDisconnect.Handler(func(info rpc.RequestInfo, request orcapi.ProviderIntegrationPFindByUser) (util.Empty, *util.HttpError) {
			local, ok, _ := IdmMapUCloudToLocal(request.Username)
			if !ok {
				return util.Empty{}, util.UserHttpError("Unknown user is being unlinked")
			}

			unlinkFn := Connections.Unlink
			if unlinkFn != nil {
				err := unlinkFn(request.Username, local)
				if err != nil {
					return util.Empty{}, err
				}
			}

			err := IdmConnectionRemove(local, 0)
			return util.Empty{}, err
		})

		orcapi.ProviderIntegrationPRetrieveManifest.Handler(func(info rpc.RequestInfo, request util.Empty) (orcapi.ProviderIntegrationManifest, *util.HttpError) {
			return Connections.RetrieveManifest(), nil
		})

		orcapi.ProviderIntegrationPInit.Handler(func(info rpc.RequestInfo, request orcapi.ProviderIntegrationPFindByUser) (util.Empty, *util.HttpError) {
			if !LaunchUserInstances {
				log.Info("Attempting to launch user instance, but this IM will not launch user instances.")
				return util.Empty{}, util.HttpErr(http.StatusNotFound, "not found")
			}

			uid, ok, _ := IdmMapUCloudToLocal(request.Username)
			if uid <= 0 || !ok {
				log.Warn("Could not map UCloud to local identity: %v -> %v (%v)", request.Username, uid, ok)
				return util.Empty{}, util.HttpErr(http.StatusBadRequest, "bad request")
			}

			err := LaunchUserInstance(uid)
			return util.Empty{}, err
		})

		Whoami.Handler(func(req *ipc.Request[util.Empty]) ipc.Response[string] {
			username, exists, _ := IdmMapLocalToUCloud(req.Uid)
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
	}
}

func IdmAddOnCompleteHandler(callback func(username string, uid uint32)) {
	idmConnectionCompleteCallbacks = append(idmConnectionCompleteCallbacks, callback)
}

func IdmAddProjectEvHandler(callback func(updated *EventProjectUpdated)) {
	idmProjectNotificationCallbacks = append(idmProjectNotificationCallbacks, callback)
}

func IdmRegisterCompleted(username string, uid uint32, notifyUCloud bool) *util.HttpError {
	return IdmRegisterCompletedEx(username, uid, notifyUCloud, util.OptNone[int]())
}

func IdmRegisterCompletedEx(username string, uid uint32, notifyUCloud bool, expiresAfterOverride util.Option[int]) *util.HttpError {
	if LaunchUserInstances {
		existing, ok, _ := IdmMapUCloudToLocal(username)
		if ok && existing != uid {
			log.Info("Wanted to perform registration which would change the UID for %s from %v to %v! "+
				"This is not possible. Please clean up manually if this is intended!", username, existing, uid)

			return util.UserHttpError("Unable to switch to a different user. Please login with the original "+
				"account (from %v to %v).", existing, uid)
		}
	}

	if notifyUCloud {
		log.Info("Registering connection complete %v -> %v", username, uid)
		_, err := orcapi.ProviderIntegrationCtrlApproveConnection.Invoke(orcapi.ProviderIntegrationCtrlFindByUser{Username: username})

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

	err := db.NewTx[*util.HttpError](func(tx *db.Transaction) *util.HttpError {
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

	eventUserReplayChannel <- username

	for _, callback := range idmConnectionCompleteCallbacks {
		callback(username, uid)
	}
	return err
}

type IdmConnectionRemoveFlag int

const (
	IdmConnectionRemoveNotify IdmConnectionRemoveFlag = 1 << iota
	IdmConnectionRemoveTrulyRemove
)

func IdmConnectionRemove(uid uint32, flags IdmConnectionRemoveFlag) *util.HttpError {
	ucloud, ok, _ := IdmMapLocalToUCloud(uid)
	if !ok {
		return util.UserHttpError("unknown user supplied: %v", uid)
	}

	db.NewTx0(func(tx *db.Transaction) {
		if flags&IdmConnectionRemoveTrulyRemove == 0 {
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
		} else {
			db.Exec(
				tx,
				`
					delete from connections
					where uid = :uid
				`,
				db.Params{
					"uid": uid,
				},
			)
		}
	})

	if flags&IdmConnectionRemoveNotify != 0 {
		_, err := orcapi.ProviderIntegrationCtrlClearConnection.Invoke(orcapi.ProviderIntegrationCtrlFindByUser{Username: ucloud})

		if err != nil {
			_ = IdmRegisterCompleted(ucloud, uid, false)
			return util.UserHttpError("failed to clear connection in UCloud: %v", err)
		}
	}

	return nil
}

func idmGetDebugPort(ucloudUsername string) (int, bool) {
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

func IdmMapUCloudToLocal(username string) (uint32, bool, bool) {
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

func IdmMapUCloudUsersToLocalUsers(usernames []string) map[string]uint32 {
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

func IdmMapLocalToUCloud(uid uint32) (string, bool, bool) {
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

func IdmRegisterProjectMapping(projectId string, gid uint32) {
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

func IdmMapLocalProjectToUCloud(gid uint32) (string, bool) {
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

func IdmMapUCloudProjectToLocal(projectId string) (uint32, bool) {
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

func idmMonitorInstances() {
	for util.IsAlive {
		if LaunchUserInstances {
			instanceMutex.Lock()
			for uid, _ := range runningInstances {
				_, ok, isActive := IdmMapLocalToUCloud(uid)
				if !ok || !isActive {
					RequestUserTermination(uid)
				}
			}
			instanceMutex.Unlock()
		}

		time.Sleep(5 * time.Second)
	}
}

func LaunchUserInstance(uid uint32) *util.HttpError {
	return LaunchUserInstanceEx(uid, true)
}

func LaunchUserInstanceEx(uid uint32, tryAgain bool) *util.HttpError {
	if !LaunchUserInstances {
		return util.UserHttpError("attempting to launch user instance, but this IM will not launch user instances")
	}

	ucloudUsername, ok, isActive := IdmMapLocalToUCloud(uid)
	if !ok {
		return util.UserHttpError("unknown user")
	}

	if !isActive {
		_, err := orcapi.ProviderIntegrationCtrlClearConnection.Invoke(
			orcapi.ProviderIntegrationCtrlFindByUser{Username: ucloudUsername})

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
			return util.UserHttpError("could not create startup log file for user at %v", logFile)
		}

		secret := util.RandomToken(16)

		var args []string
		args = append(args, "--preserve-env=UCLOUD_USER_SECRET")
		args = append(args, "-u")
		args = append(args, fmt.Sprintf("#%v", uid))
		debugPort, shouldDebug := idmGetDebugPort(ucloudUsername)
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
			return util.UserHttpError("failed to start UCloud/User process for uid=%v see %v", uid, startupLogFile)
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
