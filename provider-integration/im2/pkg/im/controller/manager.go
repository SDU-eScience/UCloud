package controller

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"time"

	ws "github.com/gorilla/websocket"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"

	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

var LaunchUserInstances = false
var UCloudUsername = ""

var (
	metricRequestCounter = promauto.NewCounter(prometheus.CounterOpts{
		Namespace: "ucloud_im",
		Subsystem: "server",
		Name:      "requests_started",
		Help:      "Number of requests received",
	})

	metricRequestClientErrorCounter = promauto.NewCounter(prometheus.CounterOpts{
		Namespace: "ucloud_im",
		Subsystem: "server",
		Name:      "request_client_errors",
		Help:      "Number of requests that ended in client error (4xx).",
	})

	metricRequestServerErrorCounter = promauto.NewCounter(prometheus.CounterOpts{
		Namespace: "ucloud_im",
		Subsystem: "server",
		Name:      "request_server_errors",
		Help:      "Number of requests that ended in server error (5xx).",
	})

	metricRequestInFlight = promauto.NewGauge(prometheus.GaugeOpts{
		Namespace: "ucloud_im",
		Subsystem: "server",
		Name:      "requests_in_flight",
		Help:      "Number of requests currently in-flight",
	})

	metricRequestDuration = promauto.NewSummary(prometheus.SummaryOpts{
		Namespace: "ucloud_im",
		Subsystem: "server",
		Name:      "requests_duration_seconds",
		Help:      "Summary of the duration (in seconds) it takes to complete requests",
		Objectives: map[float64]float64{
			0.5:  0.01,
			0.75: 0.01,
			0.95: 0.01,
			0.99: 0.01,
		},
	})
)

func RunsUserCode() bool {
	return cfg.Mode == cfg.ServerModeUser || (!LaunchUserInstances && cfg.Mode == cfg.ServerModeServer)
}

func RunsServerCode() bool {
	return cfg.Mode == cfg.ServerModeServer
}

var Mux *http.ServeMux

func Init(mux *http.ServeMux) {
	Mux = mux

	controllerFiles(mux)
	controllerConnection(mux)
	controllerJobs(mux)
	controllerTasks(mux)
	controllerSshKeys(mux)

	initLiveness()
	if RunsServerCode() {
		initEvents()
	}
}

func InitLate(mux *http.ServeMux) {
	controllerIntegratedApps(mux)
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

func HttpRetrieveHandler[T any](flags HttpApiFlag, handler ApiHandler[T]) func(w http.ResponseWriter, r *http.Request) {
	return func(w http.ResponseWriter, r *http.Request) {
		metricRequestCounter.Inc()
		metricRequestInFlight.Inc()
		defer metricRequestInFlight.Dec()

		start := time.Now()

		if r.Method != http.MethodGet {
			sendStatusCode(w, http.StatusMethodNotAllowed)
			return
		}

		if !handleAuth(flags, w, r) {
			return
		}

		var request T
		// TODO This should read from query parameters but it is not
		handler(w, r, request)

		metricRequestDuration.Observe(float64(time.Now().Sub(start).Seconds()))
	}
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

func HttpUpdateHandler[T any](flags HttpApiFlag, handler ApiHandler[T]) func(w http.ResponseWriter, r *http.Request) {
	return func(w http.ResponseWriter, r *http.Request) {
		metricRequestCounter.Inc()
		metricRequestInFlight.Inc()
		defer metricRequestInFlight.Dec()
		start := time.Now()

		if !handleAuth(flags, w, r) {
			return
		}

		if r.Body == nil {
			sendStatusCode(w, http.StatusBadRequest)
			return
		}

		defer util.SilentClose(r.Body)
		body, err := io.ReadAll(r.Body)

		if err != nil {
			sendStatusCode(w, http.StatusBadRequest)
			return
		}

		var request T
		err = json.Unmarshal(body, &request)
		if err != nil {
			sendStatusCode(w, http.StatusBadRequest)
			return
		}

		handler(w, r, request)

		metricRequestDuration.Observe(float64(time.Now().Sub(start).Seconds()))
	}
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

func sendStatusCode(w http.ResponseWriter, status int) {
	w.WriteHeader(status)
	_, _ = w.Write([]byte{})
}

func sendOkOrError(w http.ResponseWriter, err error) {
	sendStaticJsonOrError(w, "{}", err)
}

func sendStaticJsonOrError(w http.ResponseWriter, json string, err error) {
	if err != nil {
		sendError(w, err)
	} else {
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(json))
	}
}

func sendResponseOrError(w http.ResponseWriter, data any, err error) {
	if err != nil {
		sendError(w, err)
	} else {
		data, err := json.Marshal(data)
		if err != nil {
			sendError(w, err)
		} else {
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write(data)
		}
	}
}

func sendError(w http.ResponseWriter, err error) {
	if err == nil {
		msg, _ := json.Marshal(commonErrorMessage{
			Why: "Unexpected error occurred #1",
		})

		metricRequestServerErrorCounter.Inc()

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

			if httpErr.StatusCode < 500 {
				metricRequestClientErrorCounter.Inc()
			} else {
				metricRequestServerErrorCounter.Inc()
			}

			w.Header().Set("Content-Type", "application/json; charset=utf-8")
			w.WriteHeader(httpErr.StatusCode)
			_, _ = w.Write(msg)
		} else {
			log.Warn("Unexpected error occurred: %v", err.Error())

			msg, _ := json.Marshal(commonErrorMessage{
				Why: "Unexpected error occurred #2",
			})

			metricRequestServerErrorCounter.Inc()

			w.Header().Set("Content-Type", "application/json; charset=utf-8")
			w.WriteHeader(http.StatusInternalServerError)
			_, _ = w.Write(msg)
		}
	}
}
