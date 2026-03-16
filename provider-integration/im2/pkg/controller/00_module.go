package controller

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"

	ws "github.com/gorilla/websocket"
	cfg "ucloud.dk/pkg/config"

	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

var LaunchUserInstances = false
var UCloudUsername = ""

func RunsUserCode() bool {
	return cfg.Mode == cfg.ServerModeUser || (!LaunchUserInstances && cfg.Mode == cfg.ServerModeServer)
}

func RunsServerCode() bool {
	return cfg.Mode == cfg.ServerModeServer
}

var Mux *http.ServeMux

func Init(mux *http.ServeMux) {
	Mux = mux

	initFiles()
	initIdManagement()
	initJobs()
	initTasks()
	initSshKeys()
	initProviderBranding()

	initLiveness()
	if RunsServerCode() {
		initEvents()
	}
}

func InitLate() {
	initIntegratedApps()
}

type ApiHandler[T any] func(w http.ResponseWriter, r *http.Request, request T)

type HttpApiFlag int

const (
	HttpApiFlagNoAuth HttpApiFlag = 1 << iota
)

type commonErrorMessage struct {
	Why       string `json:"why,omitempty"`
	ErrorCode string `json:"errorCode,omitempty"`
}

type jwtPayload struct {
	Sub  string `json:"sub"`
	Role string `json:"role"`
}

func checkEnvoySecret(w http.ResponseWriter, r *http.Request) bool {
	if r.Header.Get("ucloud-secret") != cfg.OwnEnvoySecret {
		w.WriteHeader(http.StatusUnauthorized)
		return false
	}
	return true
}

func handleAuth(flags HttpApiFlag, w http.ResponseWriter, r *http.Request) bool {
	if flags&HttpApiFlagNoAuth != 0 {
		return true
	}

	if ok := checkEnvoySecret(w, r); !ok {
		return false
	}

	payloadHeader := r.Header.Get("x-jwt-payload")
	payloadDecoded, err := base64.RawURLEncoding.DecodeString(payloadHeader)
	if err != nil {
		w.WriteHeader(http.StatusUnauthorized)
		return false
	}
	var payload jwtPayload
	err = json.Unmarshal([]byte(payloadDecoded), &payload)
	if err != nil {
		w.WriteHeader(http.StatusUnauthorized)
		return false
	}
	if payload.Sub != "_UCloud" {
		w.WriteHeader(http.StatusUnauthorized)
		return false
	}
	if payload.Role != "SERVICE" {
		w.WriteHeader(http.StatusUnauthorized)
		return false
	}

	if !MaintenanceCheck(w, r) {
		return false
	}

	return true
}

func HttpUpgradeToWebSocketAuthenticated(w http.ResponseWriter, r *http.Request) (*ws.Conn, error) {
	if !handleAuth(0, w, r) {
		return nil, fmt.Errorf("failed authentication")
	}
	wsUpgrader := ws.Upgrader{
		ReadBufferSize:  1024 * 4,
		WriteBufferSize: 1024 * 4,
	}

	header := http.Header{}
	header.Set("ucloud-no-bearer", "true")
	return wsUpgrader.Upgrade(w, r, header)
}

func sendError(w http.ResponseWriter, err error) {
	if err == nil {
		msg, _ := json.Marshal(commonErrorMessage{
			Why: "Unexpected error occurred #1",
		})

		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		w.WriteHeader(http.StatusInternalServerError)

		_, _ = w.Write(msg)
	} else {
		var httpErr *util.HttpError
		if errors.As(err, &httpErr) {
			msg, _ := json.Marshal(commonErrorMessage{
				Why:       httpErr.Why,
				ErrorCode: httpErr.ErrorCode,
			})

			w.Header().Set("Content-Type", "application/json; charset=utf-8")
			w.WriteHeader(httpErr.StatusCode)
			_, _ = w.Write(msg)
		} else {
			log.Warn("Unexpected error occurred: %v", err.Error())

			msg, _ := json.Marshal(commonErrorMessage{
				Why: "Unexpected error occurred #2",
			})

			w.Header().Set("Content-Type", "application/json; charset=utf-8")
			w.WriteHeader(http.StatusInternalServerError)
			_, _ = w.Write(msg)
		}
	}
}
