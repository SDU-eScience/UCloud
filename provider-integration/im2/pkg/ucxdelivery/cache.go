package ucxdelivery

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
)

const DefaultPollInterval = 30 * time.Second

var cacheState = struct {
	mu                 sync.RWMutex
	providerFilesystem string
	client             *http.Client
	initialized        bool
	cancel             context.CancelFunc
}{}

type trackedApp struct {
	AppName     string
	AppVersion  string
	ManifestUrl string
	PublicKey   string
	BinaryName  string
}

type refreshResult struct {
	AppDirectory  string
	CurrentPath   string
	ManifestPath  string
	SignaturePath string
	Updated       bool
	Manifest      Manifest
}

func Initialize(providerFilesystem string, client *http.Client) error {
	if strings.TrimSpace(providerFilesystem) == "" {
		return fmt.Errorf("provider filesystem is required")
	}
	ctx, cancel := context.WithCancel(context.Background())

	cacheState.mu.Lock()
	if cacheState.cancel != nil {
		cacheState.cancel()
	}
	cacheState.providerFilesystem = providerFilesystem
	cacheState.client = client
	cacheState.initialized = true
	cacheState.cancel = cancel
	cacheState.mu.Unlock()

	go pollTrackedApps(ctx)
	return nil
}

func TrackJob(job orcapi.Job) error {
	app, ok, err := trackedAppFromJob(job)
	if err != nil || !ok {
		return err
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into ucx_executable_cache_apps(app_name, app_version, manifest_url, public_key, binary_name)
				values (:app_name, :app_version, :manifest_url, :public_key, :binary_name)
				on conflict (app_name, app_version) do update set
					manifest_url = excluded.manifest_url,
					public_key = excluded.public_key,
					binary_name = excluded.binary_name
			`,
			db.Params{
				"app_name":     app.AppName,
				"app_version":  app.AppVersion,
				"manifest_url": app.ManifestUrl,
				"public_key":   app.PublicKey,
				"binary_name":  app.BinaryName,
			},
		)
	})

	if cfg, ok := currentConfig(); ok {
		go func() {
			if _, err := refreshTrackedApp(context.Background(), cfg.providerFilesystem, cfg.client, app); err != nil {
				recordRefreshError(app, err)
				log.Warn("UCX delivery: failed to refresh tracked app %s@%s: %v", app.AppName, app.AppVersion, err)
			}
		}()
	}

	return nil
}

func ExecutablePath(appName string, appVersion string) (string, error) {
	cfg, ok := currentConfig()
	if !ok {
		return "", fmt.Errorf("UCX executable cache is not initialized")
	}
	app := trackedApp{AppName: appName, AppVersion: appVersion, BinaryName: "current"}
	paths, err := cachePaths(cfg.providerFilesystem, app)
	if err != nil {
		return "", err
	}
	return paths.CurrentPath, nil
}

func trackedAppFromJob(job orcapi.Job) (trackedApp, bool, error) {
	if !job.Status.ResolvedApplication.Present {
		return trackedApp{}, false, nil
	}
	app := job.Status.ResolvedApplication.Value
	if !app.Invocation.Tool.Tool.Present || app.Invocation.Tool.Tool.Value.Description.Backend != orcapi.ToolBackendUcx {
		return trackedApp{}, false, nil
	}
	if !app.Invocation.Ucx.Present || !app.Invocation.Ucx.Value.Executable.Present {
		return trackedApp{}, false, nil
	}
	executable := app.Invocation.Ucx.Value.Executable.Value
	if err := requireHttpsUrl(executable.ManifestUrl, "manifestUrl"); err != nil {
		return trackedApp{}, false, err
	}
	if !strings.HasPrefix(executable.PublicKey, PublicKeyPrefix) {
		return trackedApp{}, false, fmt.Errorf("public key must use the ed25519: prefix")
	}
	if err := requireCachePathComponent(executable.BinaryName, "binary name"); err != nil {
		return trackedApp{}, false, err
	}

	return trackedApp{
		AppName:     app.Metadata.Name,
		AppVersion:  app.Metadata.Version,
		ManifestUrl: executable.ManifestUrl,
		PublicKey:   executable.PublicKey,
		BinaryName:  executable.BinaryName,
	}, true, nil
}

type cacheConfig struct {
	providerFilesystem string
	client             *http.Client
}

func currentConfig() (cacheConfig, bool) {
	cacheState.mu.RLock()
	defer cacheState.mu.RUnlock()
	return cacheConfig{
		providerFilesystem: cacheState.providerFilesystem,
		client:             cacheState.client,
	}, cacheState.initialized
}

func pollTrackedApps(ctx context.Context) {
	refreshAllTrackedApps(ctx)

	for {
		ticker := time.NewTicker(DefaultPollInterval)
		select {
		case <-ctx.Done():
			ticker.Stop()
			return
		case <-ticker.C:
			ticker.Stop()
			refreshAllTrackedApps(ctx)
		}
	}
}

func refreshAllTrackedApps(ctx context.Context) {
	cfg, ok := currentConfig()
	if !ok {
		return
	}
	apps := loadTrackedApps()
	for _, app := range apps {
		if ctx.Err() != nil {
			return
		}
		if _, err := refreshTrackedApp(ctx, cfg.providerFilesystem, cfg.client, app); err != nil {
			recordRefreshError(app, err)
			log.Warn("UCX delivery: failed to refresh tracked app %s@%s: %v", app.AppName, app.AppVersion, err)
		} else {
			recordRefreshSuccess(app)
		}
	}
}

func loadTrackedApps() []trackedApp {
	return db.NewTx(func(tx *db.Transaction) []trackedApp {
		return db.Select[trackedApp](
			tx,
			`
				select app_name, app_version, manifest_url, public_key, binary_name
				from ucx_executable_cache_apps
			`,
			db.Params{},
		)
	})
}

func recordRefreshSuccess(app trackedApp) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(tx, `
			update ucx_executable_cache_apps
			set last_checked_at = now(), last_error = null
			where app_name = :app_name and app_version = :app_version
		`, db.Params{"app_name": app.AppName, "app_version": app.AppVersion})
	})
}

func recordRefreshError(app trackedApp, err error) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(tx, `
			update ucx_executable_cache_apps
			set last_checked_at = now(), last_error = :last_error
			where app_name = :app_name and app_version = :app_version
		`, db.Params{"app_name": app.AppName, "app_version": app.AppVersion, "last_error": err.Error()})
	})
}

func refreshTrackedApp(ctx context.Context, providerFilesystem string, client *http.Client, app trackedApp) (refreshResult, error) {
	result, err := cachePaths(providerFilesystem, app)
	if err != nil {
		return refreshResult{}, err
	}

	manifestBytes, err := fetchBytes(ctx, client, app.ManifestUrl)
	if err != nil {
		return refreshResult{}, err
	}
	signatureBytes, err := fetchBytes(ctx, client, SignatureUrl(app.ManifestUrl))
	if err != nil {
		return refreshResult{}, err
	}
	manifest, err := VerifyManifest(manifestBytes, signatureBytes, app.PublicKey)
	if err != nil {
		return refreshResult{}, err
	}
	if err := requireManifestBinaryName(manifest.BinaryUrl, app.BinaryName); err != nil {
		return refreshResult{}, err
	}

	binaryBytes, err := FetchAndVerifyBinary(ctx, client, manifest)
	if err != nil {
		return refreshResult{}, err
	}

	updated, err := currentNeedsUpdate(result.CurrentPath, manifest.Sha256)
	if err != nil {
		return refreshResult{}, err
	}
	result.Manifest = manifest
	result.Updated = updated

	if err := os.MkdirAll(result.AppDirectory, 0755); err != nil {
		return refreshResult{}, err
	}
	if err := publishFileAtomically(result.ManifestPath, manifestBytes, 0644); err != nil {
		return refreshResult{}, err
	}
	if err := publishFileAtomically(result.SignaturePath, signatureBytes, 0644); err != nil {
		return refreshResult{}, err
	}
	if !updated {
		return result, nil
	}
	if err := publishFileAtomically(result.CurrentPath, binaryBytes, 0755); err != nil {
		return refreshResult{}, err
	}

	return result, nil
}

func cachePaths(providerFilesystem string, app trackedApp) (refreshResult, error) {
	if strings.TrimSpace(providerFilesystem) == "" {
		return refreshResult{}, fmt.Errorf("provider filesystem is required")
	}
	if err := requireCachePathComponent(app.AppName, "app name"); err != nil {
		return refreshResult{}, err
	}
	if err := requireCachePathComponent(app.AppVersion, "app version"); err != nil {
		return refreshResult{}, err
	}
	if err := requireCachePathComponent(app.BinaryName, "binary name"); err != nil {
		return refreshResult{}, err
	}

	appDir := filepath.Join(providerFilesystem, "ucloud-ucx", "apps", app.AppName, app.AppVersion)
	return refreshResult{
		AppDirectory:  appDir,
		CurrentPath:   filepath.Join(appDir, "current"),
		ManifestPath:  filepath.Join(appDir, "manifest.json"),
		SignaturePath: filepath.Join(appDir, "manifest.json.sig"),
	}, nil
}

func requireCachePathComponent(value string, field string) error {
	if strings.TrimSpace(value) == "" {
		return fmt.Errorf("%s is required", field)
	}
	if value != filepath.Base(value) || value == "." || value == ".." {
		return fmt.Errorf("%s must be a single path component", field)
	}
	return nil
}

func requireManifestBinaryName(binaryUrl string, binaryName string) error {
	parsed, err := url.Parse(binaryUrl)
	if err != nil {
		return err
	}
	unescaped, err := url.PathUnescape(filepath.Base(parsed.Path))
	if err != nil {
		return err
	}
	if unescaped != binaryName {
		return fmt.Errorf("manifest binaryUrl does not match catalog binaryName")
	}
	return nil
}

func currentNeedsUpdate(currentPath string, expectedSha256 string) (bool, error) {
	current, err := os.ReadFile(currentPath)
	if os.IsNotExist(err) {
		return true, nil
	}
	if err != nil {
		return false, err
	}
	sum := sha256.Sum256(current)
	return hex.EncodeToString(sum[:]) != strings.ToLower(expectedSha256), nil
}

func publishFileAtomically(destination string, contents []byte, mode os.FileMode) error {
	dir := filepath.Dir(destination)
	tmp, err := os.CreateTemp(dir, ".tmp-*")
	if err != nil {
		return err
	}
	tmpName := tmp.Name()
	defer func() { _ = os.Remove(tmpName) }()

	if _, err := tmp.Write(contents); err != nil {
		_ = tmp.Close()
		return err
	}
	if err := tmp.Chmod(mode); err != nil {
		_ = tmp.Close()
		return err
	}
	if err := tmp.Close(); err != nil {
		return err
	}
	return os.Rename(tmpName, destination)
}
