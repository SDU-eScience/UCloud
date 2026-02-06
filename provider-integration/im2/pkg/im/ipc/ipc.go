package ipc

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"

	"ucloud.dk/pkg/im"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

type Request[T any] struct {
	Operation string
	Uid       uint32
	Payload   T
}

type Response[T any] struct {
	StatusCode   int
	ErrorMessage string
	Payload      T
}

type Call[Req any, Resp any] struct {
	Handler func(handler func(r *Request[Req]) Response[Resp])
	Invoke  func(r Req) (resp Resp, err error)
}

func NewCall[Req any, Resp any](operation string) Call[Req, Resp] {
	return Call[Req, Resp]{
		Handler: func(handler func(r *Request[Req]) Response[Resp]) {
			RegisterServerHandler[Req](im.Args.IpcMultiplexer, operation, handler)
		},
		Invoke: func(r Req) (resp Resp, err error) {
			return Invoke[Resp](operation, r)
		},
	}
}

func RegisterServerHandler[Req any, Resp any](mux *http.ServeMux, operation string, handler func(r *Request[Req]) Response[Resp]) {
	mux.HandleFunc("/"+operation, func(w http.ResponseWriter, r *http.Request) {
		uid, ok := GetConnectionUid(r)
		if !ok {
			w.WriteHeader(http.StatusForbidden)
			return
		}

		if r.Body == nil {
			w.WriteHeader(http.StatusBadRequest)
			return
		}

		defer util.SilentClose(r.Body)
		body, err := io.ReadAll(r.Body)

		if err != nil {
			w.WriteHeader(http.StatusBadRequest)
			return
		}

		var payload Req
		err = json.Unmarshal(body, &payload)
		if err != nil {
			w.WriteHeader(http.StatusBadRequest)
			return
		}

		req := &Request[Req]{
			Operation: operation,
			Uid:       uid,
			Payload:   payload,
		}

		resp := handler(req)
		if resp.ErrorMessage != "" {
			w.Header().Add("ucloud-ipc-error", resp.ErrorMessage)
		}
		w.WriteHeader(resp.StatusCode)
		jsonBytes, err := json.Marshal(resp.Payload)
		if err != nil {
			w.WriteHeader(http.StatusInternalServerError)
			log.Warn("Failed IPC request %v: %v/%v", operation, resp.Payload, err)
			return
		}

		_, err = w.Write(jsonBytes)
		if err != nil {
			log.Warn("Failed IPC request %v: %v/%v", operation, resp.Payload, err)
		}
	})
}

func Invoke[Resp any](operation string, payload any) (Resp, error) {
	var value Resp
	jsonBytes, err := json.Marshal(payload)
	if err != nil {
		return value, (&util.HttpError{
			StatusCode: http.StatusBadRequest,
			Why:        fmt.Sprintf("Failed to marshal JSON request: %v", err),
		}).AsError()
	}

	resp, err := Client.Post("http://ucloud.internal/"+operation, "application/json", bytes.NewBuffer(jsonBytes))
	if err != nil {
		return value, (&util.HttpError{
			StatusCode: http.StatusBadGateway,
			Why:        fmt.Sprintf("Could not talk to the running service. Are you sure it is running? (IPC unreachable: %v)", err),
		}).AsError()
	}

	if resp.StatusCode >= 200 && resp.StatusCode <= 299 {
		defer util.SilentClose(resp.Body)
		data, err := io.ReadAll(resp.Body)

		if err != nil {
			return value, (&util.HttpError{
				StatusCode: http.StatusBadGateway,
				Why:        fmt.Sprintf("Failed to read IPC body: %v", err),
			}).AsError()
		}

		err = json.Unmarshal(data, &value)
		if err != nil {
			return value, (&util.HttpError{
				StatusCode: http.StatusBadGateway,
				Why:        fmt.Sprintf("Failed to read valid IPC body: %v", err),
			}).AsError()
		}

		return value, nil
	} else {
		defer util.SilentClose(resp.Body)
		why := resp.Header.Get("ucloud-ipc-error")

		return value, (&util.HttpError{
			StatusCode: resp.StatusCode,
			Why:        why,
		}).AsError()
	}
}
