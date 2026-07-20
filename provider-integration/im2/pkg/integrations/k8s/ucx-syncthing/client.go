package ucx_syncthing

import (
	"context"
	"encoding/json"
	"encoding/xml"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

const (
	maxConfigXMLBytes = 1 << 20
	maxJSONBytes      = 2 << 20
	maxMetricsBytes   = 4 << 20
	maxErrorBodyBytes = 4 << 10
)

type apiRuntime struct {
	baseURL     string
	stateDir    string
	shortClient *http.Client
	eventClient *http.Client
	mu          sync.RWMutex
	apiKey      string
}

type syncthingConfigXML struct {
	GUI struct {
		APIKey string `xml:"apikey"`
	} `xml:"gui"`
}

func newAPIRuntime(cfg runtimeConfig) *apiRuntime {
	return &apiRuntime{
		baseURL:     cfg.baseURL,
		stateDir:    cfg.stateDir,
		shortClient: &http.Client{Timeout: 5 * time.Second},
		eventClient: &http.Client{Timeout: 70 * time.Second},
	}
}

func (a *apiRuntime) loadAPIKey() error {
	path := filepath.Join(a.stateDir, "config.xml")
	fd, err := os.Open(path)
	if err != nil {
		return err
	}
	defer fd.Close()

	data, err := io.ReadAll(io.LimitReader(fd, maxConfigXMLBytes+1))
	if err != nil {
		return err
	}
	if len(data) > maxConfigXMLBytes {
		return fmt.Errorf("Syncthing config.xml exceeds %d bytes", maxConfigXMLBytes)
	}
	var config syncthingConfigXML
	if err := xml.Unmarshal(data, &config); err != nil {
		return err
	}
	key := strings.TrimSpace(config.GUI.APIKey)
	if key == "" {
		return fmt.Errorf("Syncthing API key is empty")
	}
	a.mu.Lock()
	a.apiKey = key
	a.mu.Unlock()
	return nil
}

func (a *apiRuntime) key() string {
	a.mu.RLock()
	defer a.mu.RUnlock()
	return a.apiKey
}

func apiFetchJson(ctx context.Context, api *apiRuntime, path string, query url.Values, out any) error {
	data, err := apiFetchBytes(ctx, api, api.shortClient, path, query, maxJSONBytes, true)
	if err != nil {
		return err
	}
	if err := json.Unmarshal(data, out); err != nil {
		return fmt.Errorf("decode %s: %w", path, err)
	}
	return nil
}

func apiFetchEvents(ctx context.Context, api *apiRuntime, query url.Values, out any) error {
	data, err := apiFetchBytes(ctx, api, api.eventClient, "/rest/events", query, maxJSONBytes, true)
	if err != nil {
		return err
	}
	if err := json.Unmarshal(data, out); err != nil {
		return fmt.Errorf("decode events: %w", err)
	}
	return nil
}

func apiFetchMetrics(ctx context.Context, api *apiRuntime) ([]byte, error) {
	return apiFetchBytes(ctx, api, api.shortClient, "/metrics", nil, maxMetricsBytes, true)
}

func apiProbeHealth(ctx context.Context, api *apiRuntime) error {
	var response struct {
		Status string `json:"status"`
	}
	data, err := apiFetchBytes(ctx, api, api.shortClient, "/rest/noauth/health", nil, 64<<10, false)
	if err != nil {
		return err
	}
	if err := json.Unmarshal(data, &response); err != nil {
		return err
	}
	if response.Status != "OK" {
		return fmt.Errorf("Syncthing health status is %q", response.Status)
	}
	return nil
}

func apiFetchBytes(ctx context.Context, api *apiRuntime, client *http.Client, path string, query url.Values, maxBytes int64, authenticated bool) ([]byte, error) {
	if !strings.HasPrefix(path, "/") {
		return nil, fmt.Errorf("invalid Syncthing API path")
	}
	endpoint, err := url.Parse(api.baseURL + path)
	if err != nil {
		return nil, err
	}
	endpoint.RawQuery = query.Encode()
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint.String(), nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Accept", "application/json")
	if authenticated {
		key := api.key()
		if key == "" {
			if err := api.loadAPIKey(); err != nil {
				return nil, err
			}
			key = api.key()
		}
		req.Header.Set("X-API-Key", key)
	}

	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, maxErrorBodyBytes))
		if authenticated && (resp.StatusCode == http.StatusUnauthorized || resp.StatusCode == http.StatusForbidden) {
			_ = api.loadAPIKey()
		}
		return nil, fmt.Errorf("Syncthing API %s returned %d: %s", path, resp.StatusCode, bounded(strings.TrimSpace(string(body)), maxErrorBodyBytes))
	}
	data, err := io.ReadAll(io.LimitReader(resp.Body, maxBytes+1))
	if err != nil {
		return nil, err
	}
	if int64(len(data)) > maxBytes {
		return nil, fmt.Errorf("Syncthing API %s response exceeds %d bytes", path, maxBytes)
	}
	return data, nil
}
