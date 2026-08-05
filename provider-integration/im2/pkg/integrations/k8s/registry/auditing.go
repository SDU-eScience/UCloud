package registry

import (
	"context"
	"net/http"
	"time"

	ocidauth "github.com/distribution/distribution/v3/registry/auth"
)

func auditingMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		state := &requestState{}
		r = r.WithContext(contextWithRequestState(r, state))
		response := &auditResponseWriter{ResponseWriter: w}
		startedAt := time.Now()

		next.ServeHTTP(response, r)
		auditRegistryRequest(r, state.grant, state.access, response.statusCode(), response.written, time.Since(startedAt))
	})
}

func contextWithRequestState(r *http.Request, state *requestState) context.Context {
	return context.WithValue(r.Context(), requestStateKey{}, state)
}

func auditRegistryRequest(r *http.Request, grant *ocidauth.Grant, access []ocidauth.Access, status int, responseBytes int64, duration time.Duration) {
	panic("TODO audit registry request")
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
