package ucxdelivery

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
	"time"

	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

func TestCacheRefreshPublishesVerifiedExecutable(t *testing.T) {
	server, request := testCacheServer(t, "hello", "hello", false)
	defer server.Close()

	providerFilesystem := t.TempDir()
	result, err := refreshTrackedApp(context.Background(), providerFilesystem, server.Client(), util.OptNone[int](), request)
	if err != nil {
		t.Fatalf("refresh cache: %s", err)
	}
	if !result.Updated {
		t.Fatalf("expected cache update")
	}

	current, err := os.ReadFile(result.CurrentPath)
	if err != nil {
		t.Fatalf("read current: %s", err)
	}
	if string(current) != "hello" {
		t.Fatalf("unexpected current contents: %q", string(current))
	}
	info, err := os.Stat(result.CurrentPath)
	if err != nil {
		t.Fatalf("stat current: %s", err)
	}
	if info.Mode()&0111 == 0 {
		t.Fatalf("expected current to be executable")
	}
	if result.CurrentPath != filepath.Join(providerFilesystem, "ucloud-ucx", "apps", "my-app", "1.0.0", "current") {
		t.Fatalf("unexpected current path: %s", result.CurrentPath)
	}
	if _, err := os.Stat(result.ManifestPath); err != nil {
		t.Fatalf("expected manifest file: %s", err)
	}
	if _, err := os.Stat(result.SignaturePath); err != nil {
		t.Fatalf("expected signature file: %s", err)
	}
}

func TestCacheRefreshRejectsInvalidSignatureAndKeepsCurrent(t *testing.T) {
	server, request := testCacheServer(t, "new", "new", true)
	defer server.Close()

	providerFilesystem := t.TempDir()
	currentPath := filepath.Join(providerFilesystem, "ucloud-ucx", "apps", "my-app", "1.0.0", "current")
	if err := os.MkdirAll(filepath.Dir(currentPath), 0755); err != nil {
		t.Fatalf("mkdir current dir: %s", err)
	}
	if err := os.WriteFile(currentPath, []byte("old"), 0755); err != nil {
		t.Fatalf("write current: %s", err)
	}

	if _, err := refreshTrackedApp(context.Background(), providerFilesystem, server.Client(), util.OptNone[int](), request); err == nil {
		t.Fatalf("expected invalid signature to fail")
	}
	current, err := os.ReadFile(currentPath)
	if err != nil {
		t.Fatalf("read current: %s", err)
	}
	if string(current) != "old" {
		t.Fatalf("expected existing current to remain, got %q", string(current))
	}
}

func TestCacheRefreshRejectsInvalidBinaryHashAndKeepsCurrent(t *testing.T) {
	server, request := testCacheServer(t, "expected", "changed", false)
	defer server.Close()

	providerFilesystem := t.TempDir()
	currentPath := filepath.Join(providerFilesystem, "ucloud-ucx", "apps", "my-app", "1.0.0", "current")
	if err := os.MkdirAll(filepath.Dir(currentPath), 0755); err != nil {
		t.Fatalf("mkdir current dir: %s", err)
	}
	if err := os.WriteFile(currentPath, []byte("old"), 0755); err != nil {
		t.Fatalf("write current: %s", err)
	}

	if _, err := refreshTrackedApp(context.Background(), providerFilesystem, server.Client(), util.OptNone[int](), request); err == nil {
		t.Fatalf("expected binary hash mismatch to fail")
	}
	current, err := os.ReadFile(currentPath)
	if err != nil {
		t.Fatalf("read current: %s", err)
	}
	if string(current) != "old" {
		t.Fatalf("expected existing current to remain, got %q", string(current))
	}
}

func TestCacheRefreshReportsNotUpdatedWhenCurrentMatches(t *testing.T) {
	server, request := testCacheServer(t, "hello", "hello", false)
	defer server.Close()

	providerFilesystem := t.TempDir()
	first, err := refreshTrackedApp(context.Background(), providerFilesystem, server.Client(), util.OptNone[int](), request)
	if err != nil {
		t.Fatalf("first refresh: %s", err)
	}
	if !first.Updated {
		t.Fatalf("expected first refresh to update")
	}
	second, err := refreshTrackedApp(context.Background(), providerFilesystem, server.Client(), util.OptNone[int](), request)
	if err != nil {
		t.Fatalf("second refresh: %s", err)
	}
	if second.Updated {
		t.Fatalf("expected second refresh to skip current update")
	}
}

func TestTrackedAppFromJob(t *testing.T) {
	job := orcapi.Job{
		Status: orcapi.JobStatus{
			ResolvedApplication: util.OptValue(orcapi.Application{
				WithAppMetadata: orcapi.WithAppMetadata{
					Metadata: orcapi.ApplicationMetadata{
						NameAndVersion: orcapi.NameAndVersion{Name: "my-app", Version: "1.0.0"},
					},
				},
				WithAppInvocation: orcapi.WithAppInvocation{
					Invocation: orcapi.ApplicationInvocationDescription{
						Tool: orcapi.ToolReference{
							Tool: util.OptValue(orcapi.Tool{
								Description: orcapi.ToolDescription{Backend: orcapi.ToolBackendUcx},
							}),
						},
						Ucx: util.OptValue(orcapi.UcxDescription{
							Executable: util.OptValue(orcapi.UcxExecutableDescription{
								ManifestUrl: "https://provider.example.org/ucx/my-app/1.0.0/manifest.json",
								PublicKey:   PublicKeyPrefix + "BASE64_PUBLIC_KEY",
								BinaryName:  "my-ucx-app",
							}),
						}),
					},
				},
			}),
		},
	}

	app, ok, err := trackedAppFromJob(job)
	if err != nil {
		t.Fatalf("tracked app from job: %s", err)
	}
	if !ok {
		t.Fatalf("expected UCX app to be tracked")
	}
	if app.AppName != "my-app" || app.AppVersion != "1.0.0" || app.BinaryName != "my-ucx-app" {
		t.Fatalf("unexpected tracked app: %#v", app)
	}
}

func TestTrackedBuiltInAppDoesNotRequireUcxToolBackend(t *testing.T) {
	app := &orcapi.Application{
		WithAppMetadata: orcapi.WithAppMetadata{
			Metadata: orcapi.ApplicationMetadata{
				NameAndVersion: orcapi.NameAndVersion{Name: "syncthing", Version: "1.0.0"},
			},
		},
		WithAppInvocation: orcapi.WithAppInvocation{
			Invocation: orcapi.ApplicationInvocationDescription{
				Ucx: util.OptValue(orcapi.UcxDescription{
					Executable: util.OptValue(orcapi.UcxExecutableDescription{ManifestUrl: "builtin://ucx-syncthing"}),
				}),
			},
		},
	}

	tracked, ok, err := trackedAppFromApp(app)
	if err != nil {
		t.Fatalf("track built-in app: %s", err)
	}
	if !ok {
		t.Fatalf("expected built-in app to be tracked")
	}
	if tracked.BinaryName != "ucx-syncthing" || tracked.PublicKey != "" {
		t.Fatalf("unexpected tracked app: %#v", tracked)
	}
}

func TestCacheRefreshPublishesBuiltInExecutable(t *testing.T) {
	providerFilesystem := t.TempDir()
	sharedDir := filepath.Join(providerFilesystem, sharedExecutablesDirectory)
	if err := os.MkdirAll(sharedDir, 0755); err != nil {
		t.Fatalf("mkdir shared executable dir: %s", err)
	}
	sourcePath := filepath.Join(sharedDir, "ucx-syncthing")
	if err := os.WriteFile(sourcePath, []byte("first"), 0755); err != nil {
		t.Fatalf("write built-in executable: %s", err)
	}
	app := trackedApp{
		AppName: "syncthing", AppVersion: "1.0.0",
		ManifestUrl: "builtin://ucx-syncthing", BinaryName: "ucx-syncthing",
	}

	first, err := refreshTrackedApp(context.Background(), providerFilesystem, nil, util.OptNone[int](), app)
	if err != nil {
		t.Fatalf("first refresh: %s", err)
	}
	if !first.Updated {
		t.Fatalf("expected first refresh to update")
	}
	if _, err := os.Stat(first.ManifestPath); !os.IsNotExist(err) {
		t.Fatalf("built-in refresh must not publish a manifest")
	}

	if err := os.WriteFile(sourcePath, []byte("second"), 0755); err != nil {
		t.Fatalf("update built-in executable: %s", err)
	}
	second, err := refreshTrackedApp(context.Background(), providerFilesystem, nil, util.OptNone[int](), app)
	if err != nil {
		t.Fatalf("second refresh: %s", err)
	}
	if !second.Updated {
		t.Fatalf("expected changed built-in executable to update")
	}
	current, err := os.ReadFile(second.CurrentPath)
	if err != nil {
		t.Fatalf("read current: %s", err)
	}
	if string(current) != "second" {
		t.Fatalf("unexpected current contents: %q", current)
	}
	info, err := os.Stat(second.CurrentPath)
	if err != nil || info.Mode()&0111 == 0 {
		t.Fatalf("expected executable current file: %v", err)
	}
}

func TestCacheRefreshRejectsInvalidBuiltInSourceAndKeepsCurrent(t *testing.T) {
	providerFilesystem := t.TempDir()
	sharedDir := filepath.Join(providerFilesystem, sharedExecutablesDirectory)
	if err := os.MkdirAll(sharedDir, 0755); err != nil {
		t.Fatalf("mkdir shared executable dir: %s", err)
	}
	if err := os.WriteFile(filepath.Join(sharedDir, "ucx-syncthing"), []byte("new"), 0644); err != nil {
		t.Fatalf("write built-in executable: %s", err)
	}
	currentPath := filepath.Join(providerFilesystem, "ucloud-ucx", "apps", "syncthing", "1.0.0", "current")
	if err := os.MkdirAll(filepath.Dir(currentPath), 0755); err != nil {
		t.Fatalf("mkdir current dir: %s", err)
	}
	if err := os.WriteFile(currentPath, []byte("old"), 0755); err != nil {
		t.Fatalf("write current: %s", err)
	}
	app := trackedApp{
		AppName: "syncthing", AppVersion: "1.0.0",
		ManifestUrl: "builtin://ucx-syncthing", BinaryName: "ucx-syncthing",
	}

	if _, err := refreshTrackedApp(context.Background(), providerFilesystem, nil, util.OptNone[int](), app); err == nil {
		t.Fatalf("expected non-executable built-in source to fail")
	}
	current, err := os.ReadFile(currentPath)
	if err != nil || string(current) != "old" {
		t.Fatalf("expected current to remain unchanged, got %q: %v", current, err)
	}
}

func testCacheServer(t *testing.T, manifestBinary string, servedBinary string, corruptSignature bool) (*httptest.Server, trackedApp) {
	t.Helper()

	keys, err := GenerateKeyPair()
	if err != nil {
		t.Fatalf("generate key pair: %s", err)
	}

	var manifestBytes []byte
	var signature []byte
	server := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/manifest.json":
			_, _ = w.Write(manifestBytes)
		case "/manifest.json.sig":
			_, _ = w.Write(signature)
		case "/my-ucx-app":
			_, _ = w.Write([]byte(servedBinary))
		default:
			http.NotFound(w, r)
		}
	}))

	sum := sha256.Sum256([]byte(manifestBinary))
	manifest := Manifest{
		BinaryUrl: server.URL + "/my-ucx-app",
		Sha256:    hex.EncodeToString(sum[:]),
		UpdatedAt: fndapi.Timestamp(time.Date(2026, 7, 7, 12, 0, 0, 0, time.UTC)),
	}
	manifestBytes, err = ManifestBytes(manifest)
	if err != nil {
		server.Close()
		t.Fatalf("manifest bytes: %s", err)
	}
	signature, _, err = SignManifest(manifestBytes, keys.PrivateKey)
	if err != nil {
		server.Close()
		t.Fatalf("sign manifest: %s", err)
	}
	if corruptSignature {
		signature[0] ^= 0xff
	}

	return server, trackedApp{
		AppName:     "my-app",
		AppVersion:  "1.0.0",
		ManifestUrl: server.URL + "/manifest.json",
		PublicKey:   keys.PublicKey,
		BinaryName:  "my-ucx-app",
	}
}
