package registry

import (
	"context"
	"encoding/json"
	"net/http"
	"slices"
	"strconv"
	"strings"
	"time"

	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func auditingMiddleware(next http.Handler, tokenEndpoint bool) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		state := &requestState{}
		r = r.WithContext(contextWithRequestState(r, state))
		response := &auditResponseWriter{ResponseWriter: w}
		startedAt := time.Now()

		next.ServeHTTP(response, r)
		auditRegistryRequest(r, state, tokenEndpoint, response.statusCode(), response.written, startedAt, time.Since(startedAt))
	})
}

func contextWithRequestState(r *http.Request, state *requestState) context.Context {
	return context.WithValue(r.Context(), requestStateKey{}, state)
}

func auditRegistryRequest(r *http.Request, state *requestState, tokenEndpoint bool, status int, responseBytes int64, receivedAt time.Time, duration time.Duration) {
	if rpc.AuditConsumer == nil {
		return
	}

	actions := make([]string, 0, len(state.access))
	actionSeen := map[string]bool{}
	type auditAccess struct {
		Type   string `json:"type"`
		Name   string `json:"name"`
		Action string `json:"action"`
	}
	access := make([]auditAccess, 0, len(state.access))
	for _, item := range state.access {
		access = append(access, auditAccess{Type: item.Type, Name: item.Name, Action: item.Action})
		if !actionSeen[item.Action] {
			actionSeen[item.Action] = true
			actions = append(actions, item.Action)
		}
	}
	slices.Sort(actions)

	requestName := "containerRegistry."
	if tokenEndpoint {
		requestName += "token"
		if len(actions) > 0 {
			requestName += "."
		}
	}
	if len(actions) > 0 {
		requestName += strings.Join(actions, "+")
	} else if !tokenEndpoint {
		requestName += "request"
	}

	requestData, _ := json.Marshal(struct {
		Method        string        `json:"method"`
		Path          string        `json:"path"`
		Access        []auditAccess `json:"access"`
		ResponseBytes int64         `json:"responseBytes"`
	}{
		Method:        r.Method,
		Path:          r.URL.Path,
		Access:        access,
		ResponseBytes: responseBytes,
	})

	event := rpc.HttpCallLogEntry{
		JobId:             util.OptStringIfNotEmpty(r.Header.Get("Job-Id")).GetOrDefault(util.RandomTokenNoTs(8)),
		HandledBy:         rpc.ServiceInstance{Definition: rpc.ServiceDefinition{Name: "ucloud", Version: "ucloud"}, Hostname: "hostname", Port: 8080},
		CausedBy:          util.OptNone[string](),
		RequestName:       requestName,
		UserAgent:         util.OptStringIfNotEmpty(r.Header.Get("User-Agent")),
		RemoteOrigin:      util.ClientIP(r).String(),
		ResponseCode:      status,
		ResponseTime:      uint64(duration.Milliseconds()),
		ResponseTimeNanos: uint64(duration.Nanoseconds()),
		Expiry:            uint64(receivedAt.Add(180 * 24 * time.Hour).UnixMilli()),
		ReceivedAt:        receivedAt,
	}
	event.RequestJson.Set(requestData)
	if contentLength, err := strconv.ParseUint(r.Header.Get("Content-Length"), 10, 64); err == nil {
		event.RequestSize = contentLength
	}
	if state.owner.ProjectId != "" {
		event.Project.Set(state.owner.ProjectId)
	}
	if state.grant != nil {
		token := rpc.SecurityPrincipalToken{
			Principal: rpc.SecurityPrincipal{
				Username: state.grant.User.Name,
				Role:     rpc.RoleUser.String(),
			},
			Scopes:          actions,
			ExtendedByChain: nil,
		}
		if state.apiTokenId != "" {
			token.PublicSessionReference.Set(state.apiTokenId)
		}
		event.Token.Set(token)
	}

	rpc.AuditConsumer(event)
}

type auditResponseWriter struct {
	http.ResponseWriter
	status  int
	written int64
}

func (w *auditResponseWriter) WriteHeader(status int) {
	if w.status == 0 {
		w.status = status
	}
	w.ResponseWriter.WriteHeader(status)
}

func (w *auditResponseWriter) Write(payload []byte) (int, error) {
	if w.status == 0 {
		w.status = http.StatusOK
	}
	n, err := w.ResponseWriter.Write(payload)
	w.written += int64(n)
	return n, err
}

func (w *auditResponseWriter) Flush() {
	if w.status == 0 {
		w.status = http.StatusOK
	}
	if flusher, ok := w.ResponseWriter.(http.Flusher); ok {
		flusher.Flush()
	}
}

func (w *auditResponseWriter) Unwrap() http.ResponseWriter {
	return w.ResponseWriter
}

func (w *auditResponseWriter) statusCode() int {
	if w.status == 0 {
		return http.StatusOK
	}
	return w.status
}
