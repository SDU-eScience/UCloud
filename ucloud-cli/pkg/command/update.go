package command

import (
	"archive/tar"
	"compress/gzip"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"time"

	"github.com/charmbracelet/x/term"
	"ucloud.dk/ucloud_cli/pkg/shared"
	"ucloud.dk/ucloud_cli/pkg/version"
)

const (
	updateCheckInterval   = 15 * time.Minute
	updateManifestTimeout = 5 * time.Second
	updateDownloadTimeout = 5 * time.Minute
	updateMaxManifestSize = 1 << 20
)

type UpdateCommand struct {
	Check bool `flag:"check" usage:"Check for updates without installing"`
}

var UpdateCommands = map[string]CommandFunc{
	"update": func() Command { return &UpdateCommand{} },
}

func (c UpdateCommand) Execute() error {
	return performUpdate(c.Check)
}

func AutoUpdate(commandName string) {
	switch commandName {
	case "update", "completion", "version":
		return
	}
	if version.Version == "" || version.Version == "dev" {
		return
	}
	if !isTerminal(os.Stdin) || !isTerminal(os.Stdout) {
		return
	}

	if _, err := os.Stat(shared.GetConfigPath()); err != nil {
		return
	}

	cfg, err := shared.ReadConfig()
	if err != nil {
		return
	}

	environment, ok := cfg.Environments[cfg.DefaultEnvironment]
	if !ok || isLauncherEnvironment(environment.URL) {
		return
	}

	cachePath, err := updateCachePath()
	if err != nil {
		return
	}

	baseURL := updateBaseURL(environment.URL)
	if cachedAt, cachedURL, err := readUpdateCheck(cachePath); err == nil &&
		cachedURL == baseURL &&
		time.Since(cachedAt) < updateCheckInterval {
		return
	}
	_ = writeUpdateCheck(cachePath, baseURL)

	manifest, err := fetchUpdateManifest(baseURL, updateManifestTimeout)
	if err != nil {
		return
	}
	if compareVersions(manifest.Version, version.Version) <= 0 {
		return
	}

	platform, ok := manifest.platformFor(runtime.GOOS, runtime.GOARCH)
	if !ok {
		return
	}

	if err := installUpdate(baseURL, platform); err != nil {
		fmt.Fprintf(os.Stderr, "UCloud CLI update failed: %v\n", err)
		return
	}
	fmt.Printf("Updated UCloud CLI %s -> %s\n", version.Version, manifest.Version)
}

func performUpdate(checkOnly bool) error {
	if version.Version == "" || version.Version == "dev" {
		return fmt.Errorf("update is disabled for development builds")
	}

	cfg, err := shared.ReadConfig()
	if err != nil {
		return err
	}

	environment, ok := cfg.Environments[cfg.DefaultEnvironment]
	if !ok {
		return fmt.Errorf("environment %s was not found", cfg.DefaultEnvironment)
	}

	baseURL := updateBaseURL(environment.URL)
	fmt.Printf("Checking %s\n", updateManifestURL(baseURL))

	manifest, err := fetchUpdateManifest(baseURL, 30*time.Second)
	if err != nil {
		return err
	}

	if compareVersions(manifest.Version, version.Version) <= 0 {
		fmt.Printf("UCloud CLI %s is up to date\n", version.Version)
		return nil
	}

	fmt.Printf("Update available: %s (current %s)\n", manifest.Version, version.Version)
	if checkOnly {
		return nil
	}

	platform, ok := manifest.platformFor(runtime.GOOS, runtime.GOARCH)
	if !ok {
		return fmt.Errorf("no download available for %s/%s", runtime.GOOS, runtime.GOARCH)
	}

	if err := installUpdate(baseURL, platform); err != nil {
		return err
	}
	fmt.Printf("Updated UCloud CLI %s -> %s\n", version.Version, manifest.Version)
	return nil
}

type updateManifest struct {
	Version   string           `json:"version"`
	Platforms []updatePlatform `json:"platforms"`
}

type updatePlatform struct {
	OS           string `json:"os"`
	Architecture string `json:"architecture"`
	File         string `json:"file"`
	SHA256       string `json:"sha256"`
}

func (m updateManifest) platformFor(osName string, architecture string) (updatePlatform, bool) {
	for _, platform := range m.Platforms {
		if platform.OS == osName && platform.Architecture == architecture {
			return platform, true
		}
	}
	return updatePlatform{}, false
}

func fetchUpdateManifest(baseURL string, timeout time.Duration) (updateManifest, error) {
	client := &http.Client{Timeout: timeout}
	response, err := client.Get(updateManifestURL(baseURL))
	if err != nil {
		return updateManifest{}, err
	}
	defer response.Body.Close()

	if response.StatusCode != http.StatusOK {
		return updateManifest{}, fmt.Errorf("manifest request failed: %s", response.Status)
	}

	var manifest updateManifest
	if err := json.NewDecoder(io.LimitReader(response.Body, updateMaxManifestSize)).Decode(&manifest); err != nil {
		return updateManifest{}, err
	}
	return manifest, nil
}

func installUpdate(baseURL string, platform updatePlatform) error {
	tmpDir, err := os.MkdirTemp("", "ucloud-update-*")
	if err != nil {
		return err
	}
	defer os.RemoveAll(tmpDir)

	filename := filepath.Base(platform.File)
	archivePath := filepath.Join(tmpDir, filename)
	archiveURL := updateBaseURL(baseURL) + "/cli/current/" + url.PathEscape(filename)

	if err := downloadFile(archiveURL, archivePath); err != nil {
		return err
	}
	if err := verifyFileChecksum(archivePath, platform.SHA256); err != nil {
		return err
	}

	binaryPath := filepath.Join(tmpDir, "ucloud")
	if err := extractUCloudBinary(archivePath, binaryPath); err != nil {
		return err
	}

	executablePath, err := replaceExecutable(binaryPath)
	if err != nil {
		return err
	}

	if runtime.GOOS == "darwin" {
		_ = exec.Command("xattr", "-d", "com.apple.quarantine", executablePath).Run()
	}
	return nil
}

func downloadFile(remoteURL string, destination string) error {
	client := &http.Client{Timeout: updateDownloadTimeout}
	response, err := client.Get(remoteURL)
	if err != nil {
		return err
	}
	defer response.Body.Close()

	if response.StatusCode != http.StatusOK {
		return fmt.Errorf("download failed: %s", response.Status)
	}

	output, err := os.Create(destination)
	if err != nil {
		return err
	}

	_, copyErr := io.Copy(output, response.Body)
	closeErr := output.Close()
	if copyErr != nil {
		return copyErr
	}
	return closeErr
}

func verifyFileChecksum(path string, expected string) error {
	file, err := os.Open(path)
	if err != nil {
		return err
	}
	defer file.Close()

	hash := sha256.New()
	if _, err := io.Copy(hash, file); err != nil {
		return err
	}

	actual := hex.EncodeToString(hash.Sum(nil))
	if !strings.EqualFold(actual, expected) {
		return fmt.Errorf("checksum verification failed")
	}
	return nil
}

func extractUCloudBinary(archivePath string, destination string) error {
	file, err := os.Open(archivePath)
	if err != nil {
		return err
	}
	defer file.Close()

	gzipReader, err := gzip.NewReader(file)
	if err != nil {
		return err
	}
	defer gzipReader.Close()

	tarReader := tar.NewReader(gzipReader)
	for {
		header, err := tarReader.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			return err
		}
		if header.Typeflag != tar.TypeReg || filepath.Base(header.Name) != "ucloud" {
			continue
		}

		output, err := os.OpenFile(destination, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0755)
		if err != nil {
			return err
		}

		_, copyErr := io.Copy(output, tarReader)
		closeErr := output.Close()
		if copyErr != nil {
			return copyErr
		}
		return closeErr
	}

	return fmt.Errorf("archive does not contain ucloud")
}

func replaceExecutable(source string) (string, error) {
	executablePath, err := os.Executable()
	if err != nil {
		return "", err
	}
	if resolved, err := filepath.EvalSymlinks(executablePath); err == nil {
		executablePath = resolved
	}

	directory := filepath.Dir(executablePath)
	temporary, err := os.CreateTemp(directory, ".ucloud-update-*")
	if err != nil {
		return "", fmt.Errorf("cannot write to %s: %w", directory, err)
	}
	temporaryPath := temporary.Name()
	defer os.Remove(temporaryPath)

	sourceFile, err := os.Open(source)
	if err != nil {
		_ = temporary.Close()
		return "", err
	}

	_, copyErr := io.Copy(temporary, sourceFile)
	closeSourceErr := sourceFile.Close()
	chmodErr := temporary.Chmod(0755)
	closeErr := temporary.Close()

	if copyErr != nil {
		return "", copyErr
	}
	if closeSourceErr != nil {
		return "", closeSourceErr
	}
	if chmodErr != nil {
		return "", chmodErr
	}
	if closeErr != nil {
		return "", closeErr
	}

	if err := os.Rename(temporaryPath, executablePath); err != nil {
		return "", err
	}
	return executablePath, nil
}

func updateManifestURL(baseURL string) string {
	return updateBaseURL(baseURL) + "/cli/current/manifest.json"
}

func updateBaseURL(baseURL string) string {
	return strings.TrimRight(baseURL, "/")
}

func isTerminal(file *os.File) bool {
	return term.IsTerminal(file.Fd())
}

func isLauncherEnvironment(rawURL string) bool {
	parsed, err := url.Parse(rawURL)
	if err != nil {
		return false
	}

	host := parsed.Hostname()
	switch host {
	case "localhost", "127.0.0.1", "::1", "ucloud.localhost.direct":
		return true
	}
	return strings.HasSuffix(host, ".localhost")
}

func updateCachePath() (string, error) {
	directory, err := os.UserCacheDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(directory, "ucloud", "update-check"), nil
}

func readUpdateCheck(path string) (time.Time, string, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return time.Time{}, "", err
	}

	lines := strings.SplitN(strings.TrimSpace(string(data)), "\n", 2)
	if len(lines) != 2 {
		return time.Time{}, "", fmt.Errorf("invalid update cache")
	}

	seconds, err := strconv.ParseInt(strings.TrimSpace(lines[0]), 10, 64)
	if err != nil {
		return time.Time{}, "", err
	}
	return time.Unix(seconds, 0), strings.TrimSpace(lines[1]), nil
}

func writeUpdateCheck(path string, baseURL string) error {
	if err := os.MkdirAll(filepath.Dir(path), 0700); err != nil {
		return err
	}
	data := fmt.Sprintf("%d\n%s", time.Now().Unix(), baseURL)
	return os.WriteFile(path, []byte(data), 0600)
}

func compareVersions(first string, second string) int {
	firstParts := versionParts(first)
	secondParts := versionParts(second)
	count := max(len(firstParts), len(secondParts))

	for i := 0; i < count; i++ {
		var firstValue int
		var secondValue int
		if i < len(firstParts) {
			firstValue, _ = strconv.Atoi(firstParts[i])
		}
		if i < len(secondParts) {
			secondValue, _ = strconv.Atoi(secondParts[i])
		}

		if firstValue < secondValue {
			return -1
		}
		if firstValue > secondValue {
			return 1
		}
	}
	return 0
}

func versionParts(value string) []string {
	value = strings.TrimPrefix(value, "v")
	if index := strings.IndexAny(value, "-+"); index >= 0 {
		value = value[:index]
	}
	return strings.Split(value, ".")
}
