package controller

import (
	"context"
	"fmt"
	"net/http"
	"sync"
	"time"

	ws "github.com/gorilla/websocket"
	cfg "ucloud.dk/pkg/config"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/util"
)

type inferencePlaygroundSession struct {
	Owner     orcapi.ResourceOwner
	ExpiresAt time.Time
}

var inferencePlaygroundSessions = struct {
	Mu       sync.RWMutex
	Sessions map[string]inferencePlaygroundSession
}{
	Sessions: map[string]inferencePlaygroundSession{},
}

func initInference() {
	if RunsServerCode() {
		providerPath := fmt.Sprintf("/ucloud/%s/inference/playground", cfg.Provider.Id)
		upgrader := ws.Upgrader{CheckOrigin: func(r *http.Request) bool { return true }}

		Mux.HandleFunc(providerPath, func(w http.ResponseWriter, r *http.Request) {
			conn, err := upgrader.Upgrade(w, r, nil)
			if err != nil {
				return
			}
			defer util.SilentClose(conn)

			token, ok := inferencePlaygroundReadAuthToken(conn)
			if !ok {
				return
			}

			session, ok := inferencePlaygroundSessionLookup(token)
			if !ok {
				_ = conn.WriteMessage(ws.TextMessage, []byte("Forbidden"))
				return
			}

			factory := UcxApplications.InferencePlaygroundFactory
			if factory == nil {
				_ = conn.WriteMessage(ws.TextMessage, []byte("Forbidden"))
				return
			}

			app := factory(session.Owner, token)
			if app == nil {
				_ = conn.WriteMessage(ws.TextMessage, []byte("Forbidden"))
				return
			}

			ucx.RunAppWebSocketApplication(conn, context.Background(), app)
		})
	}

	orcapi.InferenceOpenPlaygroundProvider.Handler(func(info rpc.RequestInfo, request orcapi.InferenceOpenPlaygroundProviderRequest) (orcapi.InferenceOpenPlaygroundProviderResponse, *util.HttpError) {
		if !RunsServerCode() {
			return orcapi.InferenceOpenPlaygroundProviderResponse{}, util.HttpErr(http.StatusNotFound, "not found")
		}

		sessionId := util.SecureToken()
		inferencePlaygroundSessionStore(sessionId, request.Owner)

		return orcapi.InferenceOpenPlaygroundProviderResponse{
			ConnectTo:    fmt.Sprintf("%s/ucloud/%s/inference/playground", cfg.Provider.Hosts.SelfPublic.ToURL(), cfg.Provider.Id),
			SessionToken: sessionId,
		}, nil
	})
}

func inferencePlaygroundReadAuthToken(conn *ws.Conn) (string, bool) {
	messageType, rawMessage, err := conn.ReadMessage()
	if err != nil || messageType != ws.TextMessage {
		_ = conn.WriteMessage(ws.TextMessage, []byte("Forbidden"))
		return "", false
	}

	return string(rawMessage), true
}

func inferencePlaygroundSessionStore(token string, owner orcapi.ResourceOwner) {
	inferencePlaygroundSessions.Mu.Lock()
	inferencePlaygroundSessions.Sessions[token] = inferencePlaygroundSession{
		Owner:     owner,
		ExpiresAt: time.Now().Add(24 * time.Hour),
	}
	inferencePlaygroundSessions.Mu.Unlock()
}

func inferencePlaygroundSessionLookup(token string) (inferencePlaygroundSession, bool) {
	inferencePlaygroundSessions.Mu.RLock()
	session, ok := inferencePlaygroundSessions.Sessions[token]
	inferencePlaygroundSessions.Mu.RUnlock()
	if !ok {
		return inferencePlaygroundSession{}, false
	}

	if time.Now().After(session.ExpiresAt) {
		inferencePlaygroundSessions.Mu.Lock()
		delete(inferencePlaygroundSessions.Sessions, token)
		inferencePlaygroundSessions.Mu.Unlock()
		return inferencePlaygroundSession{}, false
	}

	return session, true
}
