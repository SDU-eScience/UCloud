package controller

import (
    "encoding/json"
    "io"
    "net/http"
    "ucloud.dk/pkg/util"
)

type OpCode int

type ProviderMessage struct {
    Op   OpCode
    Data any
}

type ProviderMessageResponse struct {
    StatusCode int
    Payload    any
}

func HandleMessage(message ProviderMessage) ProviderMessageResponse {
    return ProviderMessageResponse{
        StatusCode: http.StatusInternalServerError,
    }
}

const (
    OpCodeBaseFiles OpCode = 1000 * (iota + 1)
    OpCodeBaseDrives
    OpCodeBaseJobs
    OpCodeBaseIps
    OpCodeBaseLinks
)

type ApiHandler[T any] func(w http.ResponseWriter, r *http.Request, request T)

type HttpApiFlag int

const (
    HttpApiFlagNoAuth HttpApiFlag = 1 << iota
)

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
