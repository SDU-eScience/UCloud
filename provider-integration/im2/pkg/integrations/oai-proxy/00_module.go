package oai_proxy

import (
	"context"
	"crypto/sha256"
	"crypto/subtle"
	"errors"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
	"os/signal"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
	"time"

	"ucloud.dk/shared/pkg/log"
)

const (
	oaiProxyEnvListenAddress        = "UCLOUD_OAI_PROXY_LISTEN_ADDRESS"
	oaiProxyEnvUpstreamURL          = "UCLOUD_OAI_PROXY_UPSTREAM_URL"
	oaiProxyEnvUpstreamHost         = "UCLOUD_OAI_PROXY_UPSTREAM_HOST"
	oaiProxyEnvBearerToken          = "UCLOUD_OAI_PROXY_BEARER_TOKEN"
	oaiProxyEnvUpstreamBearerToken  = "UCLOUD_OAI_PROXY_UPSTREAM_BEARER_TOKEN"
	oaiProxyEnvPublicURL            = "UCLOUD_OAI_PROXY_PUBLIC_URL"
	oaiProxyEnvFileRoot             = "UCLOUD_OAI_PROXY_FILE_ROOT"
	oaiProxyEnvSpoolDir             = "UCLOUD_OAI_PROXY_SPOOL_DIR"
	oaiProxyEnvInlineLimitBytes     = "UCLOUD_OAI_PROXY_INLINE_LIMIT_BYTES"
	oaiProxyEnvMaxFileBytes         = "UCLOUD_OAI_PROXY_MAX_FILE_BYTES"
	oaiProxyEnvMaxJSONResponseBytes = "UCLOUD_OAI_PROXY_MAX_JSON_RESPONSE_BYTES"
	oaiProxyEnvCacheMaxBytes        = "UCLOUD_OAI_PROXY_CACHE_MAX_BYTES"
	oaiProxyEnvFileTTL              = "UCLOUD_OAI_PROXY_FILE_TTL"
	oaiProxyDefaultListenAddress    = "0.0.0.0:8080"
	oaiProxyDefaultInlineLimitBytes = int64(16 << 20)
	oaiProxyDefaultMaxFileBytes     = int64(16 << 30)
	oaiProxyDefaultMaxJSONBytes     = int64(256 << 20)
	oaiProxyDefaultCacheMaxBytes    = int64(256 << 20)
	oaiProxyDefaultFileTTL          = time.Hour
)

type oaiProxyConfig struct {
	listenAddress        string
	upstream             *url.URL
	upstreamHost         string
	bearerToken          string
	upstreamBearerToken  string
	publicURL            *url.URL
	fileRoot             string
	spoolRoot            string
	inlineLimit          int64
	maxFileBytes         int64
	maxJSONResponseBytes int64
	cacheMaxBytes        int64
	fileTTL              time.Duration
}

func Launch() {
	cfg := oaiProxyConfigLoad()

	files, err := oaiProxyFileStoreNew(cfg)
	if err != nil {
		log.Fatal("Could not initialize the OAI proxy file store: %v", err)
	}
	defer files.Close()

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	go files.RunJanitor(ctx)

	proxy := oaiProxyNewReverseProxy(cfg, files)

	mux := http.NewServeMux()
	mux.HandleFunc("/health", oaiProxyHealth)
	mux.HandleFunc("/files/", files.HandleDownload)
	if publicPath := strings.TrimRight(cfg.publicURL.Path, "/"); publicPath != "" {
		mux.HandleFunc(publicPath+"/files/", files.HandleDownload)
	}
	mux.Handle("/v1/", oaiProxyRequireBearer(cfg.bearerToken, proxy))

	server := &http.Server{
		Addr:              cfg.listenAddress,
		Handler:           mux,
		ReadHeaderTimeout: 10 * time.Second,
		IdleTimeout:       90 * time.Second,
	}

	go func() {
		<-ctx.Done()
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		_ = server.Shutdown(shutdownCtx)
	}()

	log.Info("OAI proxy listening on %s with upstream %s", cfg.listenAddress, cfg.upstream.String())
	err = server.ListenAndServe()
	if err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatal("OAI proxy server failed: %v", err)
	}
}

func oaiProxyConfigLoad() *oaiProxyConfig {
	cfg := &oaiProxyConfig{
		listenAddress: strings.TrimSpace(oaiProxyEnvOrDefault(oaiProxyEnvListenAddress, oaiProxyDefaultListenAddress)),
		upstreamHost:  strings.TrimSpace(os.Getenv(oaiProxyEnvUpstreamHost)),
		bearerToken:   strings.TrimSpace(oaiProxyEnvRequired(oaiProxyEnvBearerToken)),
		upstreamBearerToken: strings.TrimSpace(
			os.Getenv(oaiProxyEnvUpstreamBearerToken),
		),
		fileRoot:             filepath.Clean(strings.TrimSpace(oaiProxyEnvRequired(oaiProxyEnvFileRoot))),
		spoolRoot:            filepath.Clean(strings.TrimSpace(oaiProxyEnvOrDefault(oaiProxyEnvSpoolDir, filepath.Join(os.TempDir(), "ucloud-oai-proxy")))),
		inlineLimit:          oaiProxyEnvInt64(oaiProxyEnvInlineLimitBytes, oaiProxyDefaultInlineLimitBytes),
		maxFileBytes:         oaiProxyEnvInt64(oaiProxyEnvMaxFileBytes, oaiProxyDefaultMaxFileBytes),
		maxJSONResponseBytes: oaiProxyEnvInt64(oaiProxyEnvMaxJSONResponseBytes, oaiProxyDefaultMaxJSONBytes),
		cacheMaxBytes:        oaiProxyEnvInt64(oaiProxyEnvCacheMaxBytes, oaiProxyDefaultCacheMaxBytes),
		fileTTL:              oaiProxyEnvDuration(oaiProxyEnvFileTTL, oaiProxyDefaultFileTTL),
	}

	if cfg.listenAddress == "" {
		log.Fatal("The environment variable %s must not be empty", oaiProxyEnvListenAddress)
	}
	if cfg.bearerToken == "" {
		log.Fatal("The environment variable %s must not be empty", oaiProxyEnvBearerToken)
	}
	if cfg.upstreamHost != "" && strings.ContainsAny(cfg.upstreamHost, "/\\ \t\r\n") {
		log.Fatal("The environment variable %s must be a host name with an optional port", oaiProxyEnvUpstreamHost)
	}
	if !filepath.IsAbs(cfg.fileRoot) {
		log.Fatal("The environment variable %s must be an absolute path", oaiProxyEnvFileRoot)
	}
	if !filepath.IsAbs(cfg.spoolRoot) {
		log.Fatal("The environment variable %s must be an absolute path", oaiProxyEnvSpoolDir)
	}
	if cfg.inlineLimit <= 0 || cfg.maxFileBytes <= 0 || cfg.inlineLimit > cfg.maxFileBytes {
		log.Fatal("The OAI proxy file size limits are invalid")
	}
	if cfg.maxJSONResponseBytes <= 0 || cfg.cacheMaxBytes <= 0 {
		log.Fatal("The OAI proxy response and cache limits are invalid")
	}
	if cfg.fileTTL <= 0 {
		log.Fatal("The environment variable %s must be a positive duration", oaiProxyEnvFileTTL)
	}

	info, err := os.Stat(cfg.fileRoot)
	if err != nil || !info.IsDir() {
		log.Fatal("The environment variable %s must point at an existing directory", oaiProxyEnvFileRoot)
	}

	cfg.upstream = oaiProxyParseURL(oaiProxyEnvRequired(oaiProxyEnvUpstreamURL), oaiProxyEnvUpstreamURL)
	cfg.publicURL = oaiProxyParseURL(oaiProxyEnvRequired(oaiProxyEnvPublicURL), oaiProxyEnvPublicURL)

	return cfg
}

func oaiProxyParseURL(raw string, envName string) *url.URL {
	parsed, err := url.Parse(strings.TrimSpace(raw))
	if err != nil || parsed.Scheme == "" || parsed.Host == "" || parsed.User != nil ||
		parsed.RawQuery != "" || parsed.Fragment != "" ||
		(parsed.Scheme != "http" && parsed.Scheme != "https") {
		log.Fatal("The environment variable %s must be an absolute HTTP or HTTPS URL without a query or fragment", envName)
		return nil
	}

	parsed.Path = strings.TrimRight(parsed.Path, "/")
	parsed.RawPath = ""
	parsed.ForceQuery = false
	return parsed
}

func oaiProxyEnvRequired(name string) string {
	value := strings.TrimSpace(os.Getenv(name))
	if value == "" {
		log.Fatal("The environment variable %s is required", name)
	}
	return value
}

func oaiProxyEnvOrDefault(name string, fallback string) string {
	value := strings.TrimSpace(os.Getenv(name))
	if value == "" {
		return fallback
	}
	return value
}

func oaiProxyEnvInt64(name string, fallback int64) int64 {
	raw := strings.TrimSpace(os.Getenv(name))
	if raw == "" {
		return fallback
	}

	value, err := strconv.ParseInt(raw, 10, 64)
	if err != nil || value <= 0 {
		log.Fatal("The environment variable %s must be a positive integer", name)
	}
	return value
}

func oaiProxyEnvDuration(name string, fallback time.Duration) time.Duration {
	raw := strings.TrimSpace(os.Getenv(name))
	if raw == "" {
		return fallback
	}

	value, err := time.ParseDuration(raw)
	if err != nil || value <= 0 {
		log.Fatal("The environment variable %s must be a positive duration", name)
	}
	return value
}

func oaiProxyHealth(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet && r.Method != http.MethodHead {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}

	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	w.WriteHeader(http.StatusOK)
	if r.Method != http.MethodHead {
		_, _ = w.Write([]byte("ok\n"))
	}
}

func oaiProxyRequireBearer(expected string, next http.Handler) http.Handler {
	expectedHash := sha256.Sum256([]byte(expected))

	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		authorization := strings.TrimSpace(r.Header.Get("Authorization"))
		scheme, token, ok := strings.Cut(authorization, " ")
		token = strings.TrimSpace(token)
		tokenHash := sha256.Sum256([]byte(token))

		if !ok || !strings.EqualFold(scheme, "Bearer") || subtle.ConstantTimeCompare(expectedHash[:], tokenHash[:]) != 1 {
			w.Header().Set("WWW-Authenticate", `Bearer realm="oai-proxy"`)
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}

		next.ServeHTTP(w, r)
	})
}

func oaiProxyNewReverseProxy(cfg *oaiProxyConfig, files *oaiProxyFileStore) *httputil.ReverseProxy {
	return &httputil.ReverseProxy{
		Rewrite: func(pr *httputil.ProxyRequest) {
			pr.Out = pr.Out.WithContext(context.WithValue(pr.Out.Context(), oaiProxyRequestPathKey{}, pr.In.URL.Path))
			pr.Out.URL.Scheme = cfg.upstream.Scheme
			pr.Out.URL.Host = cfg.upstream.Host
			pr.Out.URL.Path = oaiProxyUpstreamPath(cfg.upstream.Path, pr.In.URL.Path)
			pr.Out.URL.RawPath = ""
			pr.Out.URL.RawQuery = pr.In.URL.RawQuery
			if cfg.upstreamHost != "" {
				pr.Out.Host = cfg.upstreamHost
			} else {
				pr.Out.Host = cfg.upstream.Host
			}
			pr.Out.Header.Set("Accept-Encoding", "identity")
			pr.Out.Header.Del("Authorization")

			if cfg.upstreamBearerToken != "" {
				pr.Out.Header.Set("Authorization", "Bearer "+cfg.upstreamBearerToken)
			}
		},
		ModifyResponse: func(resp *http.Response) error {
			return oaiProxyRewriteResponse(resp, files, cfg.maxJSONResponseBytes)
		},
		ErrorHandler: func(w http.ResponseWriter, r *http.Request, err error) {
			if r.Context().Err() != nil {
				return
			}
			log.Warn("OAI proxy upstream request failed: method=%s path=%s err=%v", r.Method, r.URL.Path, err)
			http.Error(w, "bad gateway", http.StatusBadGateway)
		},
		FlushInterval: -1,
	}
}

func oaiProxyUpstreamPath(basePath string, requestPath string) string {
	if basePath == "" {
		return requestPath
	}

	if strings.HasSuffix(basePath, "/v1") {
		suffix := strings.TrimPrefix(requestPath, "/v1")
		if suffix == "" {
			suffix = "/"
		}
		return strings.TrimRight(basePath, "/") + suffix
	}

	return strings.TrimRight(basePath, "/") + requestPath
}
