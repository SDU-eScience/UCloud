package controller

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

var LaunchUserInstances = false

func Init(mux *http.ServeMux) {
	controllerFiles(mux)
	controllerConnection(mux)

	if cfg.Mode == cfg.ServerModeServer {
		initEvents()
	}
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
	if flags&HttpApiFlagNoAuth == 0 {
		// TODO Auth
	}

	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			sendStatusCode(w, http.StatusMethodNotAllowed)
			return
		}

		var request T
		// TODO This should read from query parameters but it is not
		handler(w, r, request)
	}
}

func HttpUpdateHandler[T any](flags HttpApiFlag, handler ApiHandler[T]) func(w http.ResponseWriter, r *http.Request) {
	if flags&HttpApiFlagNoAuth == 0 {
		// TODO Auth
	}

	return func(w http.ResponseWriter, r *http.Request) {
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
	}
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
