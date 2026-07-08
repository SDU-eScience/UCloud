package ucxdelivery

import (
	"crypto/ed25519"
	"encoding/json"
	"fmt"
	"io"
	"mime"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"

	"ucloud.dk/shared/pkg/util"
)

const PublishedDirectory = "ucloud-ucx-published"

func RegisterStaticHandler(mux *http.ServeMux, hostingRoot string) error {
	if mux == nil {
		return fmt.Errorf("HTTP mux is required")
	}
	handler, err := StaticHandler(hostingRoot)
	if err != nil {
		return err
	}
	mux.Handle("/ucx/", handler)
	return nil
}

func StaticHandler(hostingRoot string) (http.Handler, error) {
	if strings.TrimSpace(hostingRoot) == "" {
		return nil, fmt.Errorf("hosting root is required")
	}
	root, err := filepath.Abs(hostingRoot)
	if err != nil {
		return nil, err
	}

	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet && r.Method != http.MethodHead {
			w.WriteHeader(http.StatusMethodNotAllowed)
			return
		}

		filePath, err := publishedFilePath(root, r.URL.Path)
		if err != nil {
			http.NotFound(w, r)
			return
		}

		if contentType := contentTypeForPublishedFile(filePath); contentType != "" {
			w.Header().Set("Content-Type", contentType)
		}
		http.ServeFile(w, r, filePath)
	}), nil
}

func publishedFilePath(root string, requestPath string) (string, error) {
	parts := strings.Split(strings.TrimPrefix(requestPath, "/"), "/")
	if len(parts) != 4 || parts[0] != "ucx" {
		return "", fmt.Errorf("invalid UCX path")
	}
	for _, part := range parts[1:] {
		if part == "" || part == "." || part == ".." || strings.Contains(part, "\\") {
			return "", fmt.Errorf("invalid path component")
		}
	}

	candidate := filepath.Join(root, PublishedDirectory, "apps", parts[1], parts[2], "current", parts[3])
	resolved, err := filepath.EvalSymlinks(candidate)
	if err != nil {
		return "", err
	}
	if !pathIsInside(root, resolved) {
		return "", fmt.Errorf("path escapes hosting root")
	}
	info, err := os.Stat(resolved)
	if err != nil {
		return "", err
	}
	if info.IsDir() {
		return "", fmt.Errorf("directories are not served")
	}

	return resolved, nil
}

func pathIsInside(root string, path string) bool {
	rel, err := filepath.Rel(root, path)
	if err != nil {
		return false
	}
	return rel == "." || (!strings.HasPrefix(rel, ".."+string(filepath.Separator)) && rel != "..")
}

func contentTypeForPublishedFile(path string) string {
	switch filepath.Base(path) {
	case "manifest.json":
		return "application/json"
	case "manifest.json.sig":
		return "application/octet-stream"
	default:
		return mime.TypeByExtension(filepath.Ext(path))
	}
}

type PublishResult struct {
	Manifest   Manifest
	BinaryName string
	Path       string
}

func PublishVersion(hostingRoot string, appName string, appVersion string, sourceDirectory string) (PublishResult, error) {
	root, err := filepath.Abs(hostingRoot)
	if err != nil {
		return PublishResult{}, err
	}
	source, err := filepath.Abs(sourceDirectory)
	if err != nil {
		return PublishResult{}, err
	}
	if err := requireCachePathComponent(appName, "app name"); err != nil {
		return PublishResult{}, err
	}
	if err := requireCachePathComponent(appVersion, "app version"); err != nil {
		return PublishResult{}, err
	}

	manifest, binaryName, err := validatePublishSource(source, appName, appVersion)
	if err != nil {
		return PublishResult{}, err
	}

	versionRoot := filepath.Join(root, PublishedDirectory, "apps", appName, appVersion)
	releasesRoot := filepath.Join(versionRoot, "releases")
	releaseName := util.SecureToken()
	releasePath := filepath.Join(releasesRoot, releaseName)
	if err := os.MkdirAll(releasePath, 0755); err != nil {
		return PublishResult{}, err
	}
	cleanupRelease := true
	defer func() {
		if cleanupRelease {
			_ = os.RemoveAll(releasePath)
		}
	}()

	if err := copyPublishFiles(source, releasePath, binaryName); err != nil {
		return PublishResult{}, err
	}
	if _, _, err := validatePublishSource(releasePath, appName, appVersion); err != nil {
		return PublishResult{}, err
	}

	currentLink := filepath.Join(versionRoot, "current")
	nextLink := filepath.Join(versionRoot, "next")
	oldTarget, oldTargetErr := os.Readlink(currentLink)
	_ = os.Remove(nextLink)
	if err := os.Symlink(filepath.Join("releases", releaseName), nextLink); err != nil {
		return PublishResult{}, err
	}
	if err := os.Rename(nextLink, currentLink); err != nil {
		_ = os.Remove(nextLink)
		return PublishResult{}, err
	}

	cleanupRelease = false
	if oldTargetErr == nil && oldTarget != "" && oldTarget != filepath.Join("releases", releaseName) {
		oldPath := filepath.Clean(filepath.Join(versionRoot, oldTarget))
		if pathIsInside(releasesRoot, oldPath) {
			_ = os.RemoveAll(oldPath)
		}
	}

	return PublishResult{
		Manifest:   manifest,
		BinaryName: binaryName,
		Path:       releasePath,
	}, nil
}

func validatePublishSource(sourceDirectory string, appName string, appVersion string) (Manifest, string, error) {
	info, err := os.Stat(sourceDirectory)
	if err != nil {
		return Manifest{}, "", err
	}
	if !info.IsDir() {
		return Manifest{}, "", fmt.Errorf("publish source must be a directory")
	}

	manifestPath := filepath.Join(sourceDirectory, "manifest.json")
	manifestBytes, err := os.ReadFile(manifestPath)
	if err != nil {
		return Manifest{}, "", err
	}
	var manifest Manifest
	if err := json.Unmarshal(manifestBytes, &manifest); err != nil {
		return Manifest{}, "", fmt.Errorf("invalid manifest JSON: %w", err)
	}
	if err := ValidateManifest(manifest); err != nil {
		return Manifest{}, "", err
	}

	signature, err := os.ReadFile(filepath.Join(sourceDirectory, "manifest.json.sig"))
	if err != nil {
		return Manifest{}, "", err
	}
	if len(signature) != ed25519.SignatureSize {
		return Manifest{}, "", fmt.Errorf("manifest signature must be %d bytes", ed25519.SignatureSize)
	}

	binaryName, err := validateManifestPath(manifest.BinaryUrl, appName, appVersion)
	if err != nil {
		return Manifest{}, "", err
	}
	binaryPath := filepath.Join(sourceDirectory, binaryName)
	binaryInfo, err := os.Stat(binaryPath)
	if err != nil {
		return Manifest{}, "", err
	}
	if !binaryInfo.Mode().IsRegular() {
		return Manifest{}, "", fmt.Errorf("binary must be a regular file")
	}
	if binaryInfo.Mode().Perm()&0111 == 0 {
		return Manifest{}, "", fmt.Errorf("binary must be executable")
	}
	binary, err := os.ReadFile(binaryPath)
	if err != nil {
		return Manifest{}, "", err
	}
	if err := VerifyBinary(binary, manifest.Sha256); err != nil {
		return Manifest{}, "", err
	}

	return manifest, binaryName, nil
}

func validateManifestPath(binaryUrl string, appName string, appVersion string) (string, error) {
	parsed, err := url.Parse(binaryUrl)
	if err != nil {
		return "", err
	}
	parts := strings.Split(strings.TrimPrefix(parsed.EscapedPath(), "/"), "/")
	if len(parts) != 4 || parts[0] != "ucx" {
		return "", fmt.Errorf("manifest binaryUrl must point to /ucx/<app>/<version>/<binary>")
	}
	pathAppName, err := url.PathUnescape(parts[1])
	if err != nil {
		return "", err
	}
	pathAppVersion, err := url.PathUnescape(parts[2])
	if err != nil {
		return "", err
	}
	binaryName, err := url.PathUnescape(parts[3])
	if err != nil {
		return "", err
	}
	if pathAppName != appName || pathAppVersion != appVersion {
		return "", fmt.Errorf("manifest binaryUrl app/version does not match publish request")
	}
	if err := requireCachePathComponent(binaryName, "binary name"); err != nil {
		return "", err
	}
	return binaryName, nil
}

func copyPublishFiles(source string, destination string, binaryName string) error {
	files := []string{"manifest.json", "manifest.json.sig", binaryName}
	for _, name := range files {
		if name != filepath.Base(name) || name == "." || name == ".." {
			return fmt.Errorf("invalid publish file name: %s", name)
		}

		sourcePath := filepath.Join(source, name)
		info, err := os.Lstat(sourcePath)
		if err != nil {
			return err
		}
		if info.Mode()&os.ModeSymlink != 0 {
			return fmt.Errorf("publish file must not be a symbolic link: %s", name)
		}
		if !info.Mode().IsRegular() {
			return fmt.Errorf("publish file must be a regular file: %s", name)
		}

		if err := copyFile(sourcePath, filepath.Join(destination, name), info.Mode().Perm()); err != nil {
			return err
		}
	}
	return nil
}

func copyFile(source string, destination string, mode os.FileMode) error {
	if err := os.MkdirAll(filepath.Dir(destination), 0755); err != nil {
		return err
	}
	in, err := os.Open(source)
	if err != nil {
		return err
	}
	defer in.Close()
	out, err := os.OpenFile(destination, os.O_WRONLY|os.O_CREATE|os.O_EXCL, mode)
	if err != nil {
		return err
	}
	_, copyErr := io.Copy(out, in)
	closeErr := out.Close()
	if copyErr != nil {
		return copyErr
	}
	return closeErr
}
