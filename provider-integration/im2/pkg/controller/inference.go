package controller

import (
	"bytes"
	"context"
	"fmt"
	"net/http"
	"net/url"
	"path/filepath"
	"strings"
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

var InferencePlaygroundImageRead func(owner orcapi.ResourceOwner, path string) ([]byte, *util.HttpError)

type inferencePlaygroundImageLink struct {
	Owner     orcapi.ResourceOwner
	Path      string
	ExpiresAt time.Time
}

var inferencePlaygroundImageLinks = struct {
	sync.Mutex
	Links map[string]inferencePlaygroundImageLink
}{
	Links: map[string]inferencePlaygroundImageLink{},
}

func InferencePlaygroundImageURL(owner orcapi.ResourceOwner, path string) string {
	now := time.Now()
	token := ""
	inferencePlaygroundImageLinks.Lock()
	for key, link := range inferencePlaygroundImageLinks.Links {
		if now.After(link.ExpiresAt) {
			delete(inferencePlaygroundImageLinks.Links, key)
			continue
		}
		if token == "" && link.Owner == owner && link.Path == path && link.ExpiresAt.Sub(now) > time.Minute {
			token = key
		}
	}
	if token == "" {
		token = util.SecureToken()
		inferencePlaygroundImageLinks.Links[token] = inferencePlaygroundImageLink{
			Owner: owner, Path: path, ExpiresAt: now.Add(10 * time.Minute),
		}
	}
	inferencePlaygroundImageLinks.Unlock()
	return fmt.Sprintf("%s/ucloud/%s/inference/playground/images?token=%s",
		cfg.Provider.Hosts.SelfPublic.ToURL(), cfg.Provider.Id, url.QueryEscape(token))
}

func inferencePlaygroundServeImage(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Access-Control-Allow-Origin", cfg.Provider.Hosts.UCloudPublic.ToURL())
	w.Header().Set("Access-Control-Allow-Headers", "Authorization")
	w.Header().Set("Access-Control-Allow-Methods", "GET, OPTIONS")
	w.Header().Set("Cache-Control", "no-store")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.Header().Set("Cross-Origin-Resource-Policy", "cross-origin")
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusNoContent)
		return
	}
	if r.Method != http.MethodGet {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var owner orcapi.ResourceOwner
	path := r.URL.Query().Get("path")
	if token := r.URL.Query().Get("token"); token != "" {
		inferencePlaygroundImageLinks.Lock()
		link, ok := inferencePlaygroundImageLinks.Links[token]
		inferencePlaygroundImageLinks.Unlock()
		if !ok || time.Now().After(link.ExpiresAt) {
			http.Error(w, "forbidden", http.StatusForbidden)
			return
		}
		owner, path = link.Owner, link.Path
	} else {
		token, bearer := strings.CutPrefix(r.Header.Get("Authorization"), "Bearer ")
		session, ok := inferencePlaygroundSessionLookup(token)
		if !bearer || !ok {
			http.Error(w, "forbidden", http.StatusForbidden)
			return
		}
		owner = session.Owner
	}
	if InferencePlaygroundImageRead == nil {
		http.NotFound(w, r)
		return
	}
	data, err := InferencePlaygroundImageRead(owner, path)
	if err != nil {
		http.Error(w, err.Why, err.StatusCode)
		return
	}
	w.Header().Set("Content-Type", http.DetectContentType(data))
	http.ServeContent(w, r, filepath.Base(path), time.Time{}, bytes.NewReader(data))
}

func initInference() {
	if RunsServerCode() {
		providerPath := fmt.Sprintf("/ucloud/%s/inference/playground", cfg.Provider.Id)
		Mux.HandleFunc(providerPath+"/images", inferencePlaygroundServeImage)
		upgrader := ws.Upgrader{CheckOrigin: func(r *http.Request) bool { return true }}

		go func() {
			ticker := time.NewTicker(time.Hour)
			defer ticker.Stop()
			for range ticker.C {
				inferencePlaygroundSessionSweep()
			}
		}()

		Mux.HandleFunc(providerPath, func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("ucloud-generated-by-ai-model", "true")

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

func inferencePlaygroundSessionSweep() {
	now := time.Now()
	inferencePlaygroundSessions.Mu.Lock()
	for token, session := range inferencePlaygroundSessions.Sessions {
		if now.After(session.ExpiresAt) {
			delete(inferencePlaygroundSessions.Sessions, token)
		}
	}
	inferencePlaygroundSessions.Mu.Unlock()
}
