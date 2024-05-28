package ipc

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"ucloud.dk/pkg/im"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

type Request[T any] struct {
	Operation string
	Uid       uint32
	Payload   T
}

type Response struct {
	StatusCode int
	Payload    any
}

type Call[Req any, Resp any] struct {
	Handler func(handler func(r *Request[Req]) Response)
	Invoke  func(r Req) (resp Resp, err error)
}

func NewCall[Req any, Resp any](operation string) Call[Req, Resp] {
	return Call[Req, Resp]{
		RegisterServer: func(handler func(r *Request[Req]) Response) {
			RegisterServerHandler[Req](im.Args.IpcMultiplexer, operation, handler)
		},
		Invoke: func(r Req) (resp Resp, err error) {
			return Invoke[Resp](operation, r)
		},
	}
}

func RegisterServerHandler[T any](mux *http.ServeMux, operation string, handler func(r *Request[T]) Response) {
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

		var payload T
		err = json.Unmarshal(body, &payload)
		if err != nil {
			w.WriteHeader(http.StatusBadRequest)
			return
		}

		req := &Request[T]{
			Operation: operation,
			Uid:       uid,
			Payload:   payload,
		}

		resp := handler(req)
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
		return value, &ctrl.HttpError{
			StatusCode: http.StatusBadRequest,
			Why:        fmt.Sprintf("Failed to marshal JSON request: %v", err),
		}
	}

	resp, err := Client.Post("http://ignored.ignored/"+operation, "application/json", bytes.NewBuffer(jsonBytes))
	if err != nil {
		return value, &ctrl.HttpError{
			StatusCode: http.StatusBadGateway,
			Why:        fmt.Sprintf("Failed to contact IPC server: %v", err),
		}
	}

	if resp.StatusCode >= 200 && resp.StatusCode <= 299 {
		defer util.SilentClose(resp.Body)
		data, err := io.ReadAll(resp.Body)

		if err != nil {
			return value, &ctrl.HttpError{
				StatusCode: http.StatusBadGateway,
				Why:        fmt.Sprintf("Failed to read IPC body: %v", err),
			}
		}

		err = json.Unmarshal(data, &value)
		if err != nil {
			return value, &ctrl.HttpError{
				StatusCode: http.StatusBadGateway,
				Why:        fmt.Sprintf("Failed to read valid IPC body: %v", err),
			}
		}

		return value, nil
	} else {
		defer util.SilentClose(resp.Body)
		data, err := io.ReadAll(resp.Body)

		if err != nil {
			return value, &ctrl.HttpError{
				StatusCode: resp.StatusCode,
				Why:        fmt.Sprintf("Failed to read IPC body: %v", err),
			}
		}

		return value, &ctrl.HttpError{
			StatusCode: resp.StatusCode,
			Why:        string(data),
		}
	}
}
