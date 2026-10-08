package nodeagent

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"time"

	"ucloud.dk/iapp/k8s/pkg/backup"
	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/log"
)

const (
	stagingDir      = "/var/lib/ucloud-k8s/maintenance"
	downloadTimeout = 5 * time.Minute

	k3sBinary      = "/usr/local/bin/k3s"
	k3sReplacement = "/usr/local/bin/k3s.new"
	kubeconfig     = "/etc/rancher/k3s/k3s.yaml"

	serviceControlPlane = "k3s.service"
	serviceWorker       = "k3s-agent.service"

	etcdSnapshotName = "preupgrade"

	backupSnapshotName = "backup"
	backupPartPrefix   = ".part-"

	backupWorkDeadline = 20 * time.Minute
	backupPartStaleAge = 30 * time.Minute

	workDeadline       = 20 * time.Minute
	stopTimeout        = 60 * time.Second
	startTimeout       = 600 * time.Second
	readyzTimeout      = 10 * time.Second
	readyzPollInterval = 5 * time.Second
	stopPollInterval   = 2 * time.Second

	localServerUrl = "https://127.0.0.1:6443"

	progressPercentStart    = 5
	progressPercentDownload = 20
	progressPercentSnapshot = 40
	progressPercentInstall  = 60
	progressPercentRestart  = 75
	progressPercentVerify   = 85
	progressPercentDone     = 100
)

type StagedRelease struct {
	Release string
	Path    string
	Sha256  string
}

type upgradeState struct {
	SchemaRevision  int       `json:"schemaRevision"`
	NodeName        string    `json:"nodeName"`
	IpAddress       string    `json:"ipAddress"`
	Role            string    `json:"role"`
	Release         string    `json:"release"`
	PreviousRelease string    `json:"previousRelease"`
	Phase           string    `json:"phase"`
	Error           string    `json:"error"`
	NeedsRecovery   bool      `json:"needsRecovery"`
	UpdatedAt       time.Time `json:"updatedAt"`

	StateError         string `json:"stateError,omitempty"`
	NeedsStateRecovery bool   `json:"needsStateRecovery,omitempty"`
}

func runUpgrade(release string, previousState upgradeState) {
	workerCtx, cancel := context.WithTimeout(context.Background(), workDeadline)
	defer cancel()

	identityConfig := identitySnapshot()

	retry := previousState.Phase == shared.NodeAgentPhaseFailed && previousState.Release == release

	progressStage(
		fmt.Sprintf("Preparing the upgrade to %s", release),
		progressPercentStart,
	)

	binaryVersion := installedVersion(workerCtx)
	if binaryVersion == "" {
		finishFailure(release, previousState, "", "could not determine the currently installed Kubernetes version")
		return
	}

	if identityConfig.Role == "" {
		finishFailure(release, previousState, "", "the node identity is unknown")
		return
	}

	serviceName := serviceForRole(identityConfig.Role)
	if serviceName == "" {
		finishFailure(release, previousState, "", fmt.Sprintf("unknown node role: %s", identityConfig.Role))
		return
	}

	if !retry {
		if binaryVersion == release {
			previousRelease := binaryVersion
			if previousState.Release == release && previousState.PreviousRelease != "" {
				previousRelease = previousState.PreviousRelease
			}
			if !markVerifying(identityConfig, release, previousRelease) {
				finishFailure(release, previousState, "", "could not persist the phase verifying")
				return
			}
			progressStageLabel(fmt.Sprintf("%s is already installed. Verifying it", release))
			verifyErr := verifyUpgrade(workerCtx, release, identityConfig.Role, serviceName)
			if verifyErr != nil {
				finishFailure(release, previousState, "", verifyErr.Error())
				return
			}
			storeErr := storeCompleted(identityConfig, release, previousRelease)
			if storeErr != nil {
				finishFailure(release, previousState, previousState.PreviousRelease, fmt.Sprintf("could not persist the completed state: %s", storeErr))
				return
			}
			progressStage(
				fmt.Sprintf("The upgrade to %s completed", release),
				progressPercentDone,
			)
			log.Info("k8s-app node agent: %s is already installed and healthy", release)
			return
		}
	}
	if binaryVersion != release && !shared.NodeAgentUpgradeAllowed(binaryVersion, release) {
		finishFailure(release, previousState, binaryVersion, fmt.Sprintf(
			"the upgrade from %s to %s is not allowed. Only a forward patch or the next minor release is allowed",
			binaryVersion,
			release,
		))
		return
	}

	previousRelease := binaryVersion
	if retry {
		previousRelease = previousState.PreviousRelease
		if previousRelease == "" && binaryVersion != release {
			previousRelease = binaryVersion
		}
	}

	err := stateStore(&upgradeState{
		SchemaRevision:  stateSchemaRevision,
		NodeName:        identityConfig.NodeName,
		IpAddress:       identityConfig.IpAddress,
		Role:            identityConfig.Role,
		Release:         release,
		PreviousRelease: previousRelease,
		Phase:           shared.NodeAgentPhaseDownloading,
		UpdatedAt:       time.Now().UTC(),
	})
	if err != nil {
		finishFailure(release, previousState, previousRelease, fmt.Sprintf("could not persist the upgrade state: %s", err))
		return
	}

	staged, err := StageRelease(workerCtx, release)
	if err != nil {
		finishFailure(release, previousState, previousRelease, fmt.Sprintf("could not stage the release: %s", err))
		return
	}

	targetInstalled := fileMatchesSha256(k3sBinary, staged.Sha256)

	if identityConfig.Role == shared.GroupControlPlane && !targetInstalled {
		if !setPhase(shared.NodeAgentPhaseSnapshotting) {
			finishFailure(release, previousState, previousRelease, "could not persist the phase snapshotting")
			return
		}
		progressStage(
			"Taking a control-plane snapshot",
			progressPercentSnapshot-5,
		)
		snapshotPath, snapshotErr := snapshotEtcd(workerCtx)
		if snapshotErr != nil {
			finishFailure(release, previousState, previousRelease, fmt.Sprintf("the etcd snapshot failed: %s", snapshotErr))
			return
		}
		progressText(fmt.Sprintf("The control-plane snapshot was saved to %s", snapshotPath))
		log.Info("k8s-app node agent: the etcd snapshot was saved to %s", snapshotPath)
	}

	if !setPhase(shared.NodeAgentPhaseInstalling) {
		finishFailure(release, previousState, previousRelease, "could not persist the phase installing")
		return
	}

	if !targetInstalled {
		progressStage(
			fmt.Sprintf("Installing %s", release),
			progressPercentInstall,
		)
		backupPath, backupErr := backupBinary(k3sBinary, binaryVersion)
		if backupErr != nil {
			finishFailure(release, previousState, previousRelease, fmt.Sprintf("could not back up the server binary: %s", backupErr))
			return
		}
		progressText(fmt.Sprintf("The previous binary was retained at %s", backupPath))
		log.Info("k8s-app node agent: the previous binary was retained at %s", backupPath)

		prepareErr := prepareReplacement(staged.Path, k3sReplacement)
		if prepareErr != nil {
			finishFailure(release, previousState, previousRelease, fmt.Sprintf("could not prepare the new Kubernetes binary: %s", prepareErr))
			return
		}

		progressStage(
			fmt.Sprintf("Stopping %s", serviceName),
			progressPercentInstall+5,
		)
		stopErr := stopService(workerCtx, serviceName)
		if stopErr != nil {
			_ = os.Remove(k3sReplacement)
			finishFailure(release, previousState, previousRelease, fmt.Sprintf("could not stop %s: %s", serviceName, stopErr))
			return
		}

		swapErr := swapReplacement(k3sReplacement, k3sBinary)
		if swapErr != nil {
			startErr := startService(workerCtx, serviceName)
			if startErr != nil {
				log.Warn("k8s-app node agent: could not start %s after a failed swap: %s", serviceName, startErr)
			}
			finishFailure(release, previousState, previousRelease, fmt.Sprintf("could not install the new Kubernetes binary: %s", swapErr))
			return
		}
	}

	if !setPhase(shared.NodeAgentPhaseRestarting) {
		finishFailure(release, previousState, previousRelease, "could not persist the phase restarting")
		return
	}

	progressStage(
		fmt.Sprintf("Starting %s", serviceName),
		progressPercentRestart,
	)
	startErr := startService(workerCtx, serviceName)
	if startErr != nil {
		finishFailure(release, previousState, previousRelease, fmt.Sprintf("could not start %s: %s", serviceName, startErr))
		return
	}

	if identityConfig.Role == shared.GroupControlPlane {
		startTokenPublisher()
	}

	if !setPhase(shared.NodeAgentPhaseVerifying) {
		finishFailure(release, previousState, previousRelease, "could not persist the phase verifying")
		return
	}

	progressStage(
		"Verifying the version and the readiness",
		progressPercentVerify,
	)
	verifyErr := verifyUpgrade(workerCtx, release, identityConfig.Role, serviceName)
	if verifyErr != nil {
		finishFailure(release, previousState, previousRelease, verifyErr.Error())
		return
	}

	storeErr := storeCompleted(identityConfig, release, previousRelease)
	if storeErr != nil {
		finishFailure(release, previousState, previousRelease, fmt.Sprintf("could not persist the completed state: %s", storeErr))
		return
	}

	progressStage(
		fmt.Sprintf("The upgrade to %s completed", release),
		progressPercentDone,
	)
	log.Info("k8s-app node agent: the upgrade to %s completed", release)
}

func serviceForRole(role string) string {
	if role == shared.GroupControlPlane {
		return serviceControlPlane
	}
	if roleValid(role) {
		return serviceWorker
	}
	return ""
}

func startTokenPublisher() {
	commandCtx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	output, err := exec.CommandContext(commandCtx, "systemctl", "start", "ucloud-k8s-token-publisher").CombinedOutput()
	if err != nil {
		log.Warn("k8s-app node agent: could not start the token publisher: %s", strings.TrimSpace(string(output)))
	}
}

func roleValid(role string) bool {
	if len(role) > roleMaxLen {
		return false
	}
	if !roleRe.MatchString(role) {
		return false
	}
	return role != shared.GroupControlPlane
}

func finishFailure(release string, previousState upgradeState, observedRelease string, message string) {
	log.Warn("k8s-app node agent: the upgrade to %s failed: %s", release, message)
	progressStageLabel(fmt.Sprintf("The upgrade to %s failed", release))
	progressText(fmt.Sprintf("The upgrade to %s failed: %s", release, message))

	identityConfig := identitySnapshot()

	state := upgradeState{
		SchemaRevision:  stateSchemaRevision,
		NodeName:        identityConfig.NodeName,
		IpAddress:       identityConfig.IpAddress,
		Role:            identityConfig.Role,
		Release:         release,
		PreviousRelease: observedRelease,
		Phase:           shared.NodeAgentPhaseFailed,
		Error:           message,
		NeedsRecovery:   true,
		UpdatedAt:       time.Now().UTC(),
	}

	if state.NodeName == "" {
		state.NodeName = previousState.NodeName
		state.IpAddress = previousState.IpAddress
		state.Role = previousState.Role
	}

	if state.PreviousRelease == "" {
		state.PreviousRelease = previousState.PreviousRelease
	}

	err := stateStore(&state)
	if err != nil {
		log.Warn("k8s-app node agent: could not persist the failed state: %s", err)
	}
}

func setPhase(phase string) bool {
	globals.Mu.Lock()
	defer globals.Mu.Unlock()

	state := stateRead()
	if state.Release == "" {
		return false
	}

	state.Phase = phase
	state.Error = ""
	state.UpdatedAt = time.Now().UTC()

	err := stateStore(&state)
	if err != nil {
		log.Warn("k8s-app node agent: could not persist the phase %s: %s", phase, err)
		return false
	}
	return true
}

func markVerifying(identityConfig Config, release string, previousRelease string) bool {
	err := stateStore(&upgradeState{
		SchemaRevision:  stateSchemaRevision,
		NodeName:        identityConfig.NodeName,
		IpAddress:       identityConfig.IpAddress,
		Role:            identityConfig.Role,
		Release:         release,
		PreviousRelease: previousRelease,
		Phase:           shared.NodeAgentPhaseVerifying,
		UpdatedAt:       time.Now().UTC(),
	})
	if err != nil {
		log.Warn("k8s-app node agent: could not persist the phase verifying: %s", err)
		return false
	}
	return true
}

func storeCompleted(identityConfig Config, release string, previousRelease string) error {
	return stateStore(&upgradeState{
		SchemaRevision:  stateSchemaRevision,
		NodeName:        identityConfig.NodeName,
		IpAddress:       identityConfig.IpAddress,
		Role:            identityConfig.Role,
		Release:         release,
		PreviousRelease: previousRelease,
		Phase:           shared.NodeAgentPhaseCompleted,
		UpdatedAt:       time.Now().UTC(),
	})
}

func recoverInterrupted() {
	state := stateLoad()
	if state.Release == "" || !shared.NodeAgentPhaseActive(state.Phase) {
		return
	}

	interruptedPhase := state.Phase
	state.Phase = shared.NodeAgentPhaseFailed
	state.Error = fmt.Sprintf(
		"the upgrade to %s was interrupted at phase %s. Recovery is required",
		state.Release,
		interruptedPhase,
	)
	state.NeedsRecovery = true
	state.UpdatedAt = time.Now().UTC()

	err := stateStore(&state)
	if err != nil {
		log.Warn("k8s-app node agent: could not persist the interrupted upgrade state: %s", err)
		return
	}

	progressStageLabel(fmt.Sprintf("The upgrade to %s was interrupted", state.Release))
	progressText(fmt.Sprintf(
		"The upgrade to %s was interrupted at phase %s. An explicit retry is required",
		state.Release,
		interruptedPhase,
	))
	log.Warn(
		"k8s-app node agent: an interrupted upgrade to %s was marked as failed and needs recovery",
		state.Release,
	)
}

func statusFromState(identityConfig Config) (shared.NodeAgentStatus, error) {
	state := stateLoad()
	if state.NeedsStateRecovery {
		return shared.NodeAgentStatus{
			NodeName:  state.NodeName,
			Release:   state.Release,
			Phase:     shared.NodeAgentPhaseFailed,
			Error:     state.StateError,
			UpdatedAt: state.UpdatedAt,
		}, nil
	}
	if state.Phase == "" {
		state.Phase = shared.NodeAgentPhaseIdle
	}
	if state.NodeName == "" && identityConfig.NodeName != "" {
		state.NodeName = identityConfig.NodeName
		state.IpAddress = identityConfig.IpAddress
		state.Role = identityConfig.Role
	}

	return shared.NodeAgentStatus{
		NodeName:  state.NodeName,
		Release:   state.Release,
		Phase:     state.Phase,
		Error:     state.Error,
		UpdatedAt: state.UpdatedAt,
	}, nil
}

func stateLoad() upgradeState {
	globals.Mu.Lock()
	defer globals.Mu.Unlock()
	return stateRead()
}

func stateRead() upgradeState {
	data, err := os.ReadFile(statePath())
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return upgradeState{}
		}
		return upgradeState{
			SchemaRevision:     stateSchemaRevision,
			Phase:              shared.NodeAgentPhaseFailed,
			Error:              fmt.Sprintf("could not read the upgrade state: %s", err),
			NeedsRecovery:      true,
			StateError:         fmt.Sprintf("could not read the upgrade state: %s", err),
			NeedsStateRecovery: true,
		}
	}

	var state upgradeState
	err = json.Unmarshal(data, &state)
	if err != nil {
		return upgradeState{
			SchemaRevision:     stateSchemaRevision,
			Phase:              shared.NodeAgentPhaseFailed,
			Error:              fmt.Sprintf("could not parse the upgrade state: %s", err),
			NeedsRecovery:      true,
			StateError:         fmt.Sprintf("could not parse the upgrade state: %s", err),
			NeedsStateRecovery: true,
		}
	}

	if state.SchemaRevision != stateSchemaRevision {
		state.StateError = "the upgrade state was written by an incompatible version of the application"
		state.Phase = shared.NodeAgentPhaseFailed
		state.Error = state.StateError
		state.NeedsRecovery = true
		state.NeedsStateRecovery = true
	}

	return state
}

func stateStore(state *upgradeState) error {
	data, err := json.MarshalIndent(state, "", "  ")
	if err != nil {
		return err
	}

	return writeFileAtomic(statePath(), data, 0600)
}

func writeFileAtomic(path string, data []byte, mode os.FileMode) error {
	parent := filepath.Dir(path)
	err := os.MkdirAll(parent, 0700)
	if err != nil {
		return err
	}

	file, err := os.CreateTemp(parent, ".tmp-")
	if err != nil {
		return err
	}
	tempPath := file.Name()

	_, err = file.Write(data)
	if err == nil {
		err = file.Chmod(mode)
	}
	if err == nil {
		err = file.Sync()
	}
	if closeErr := file.Close(); err == nil {
		err = closeErr
	}
	if err != nil {
		_ = os.Remove(tempPath)
		return err
	}

	err = os.Rename(tempPath, path)
	if err != nil {
		_ = os.Remove(tempPath)
		return err
	}

	return syncDir(parent)
}

func statePath() string {
	return filepath.Join(workingDir(), stateName)
}

func workingDir() string {
	return stagingDir
}

func StageRelease(ctx context.Context, release string) (StagedRelease, error) {
	record, ok := shared.ReleaseByExactVersion(release)
	if !ok {
		return StagedRelease{}, fmt.Errorf("unknown Kubernetes release: %s", release)
	}

	arch := runtime.GOARCH
	if arch != "amd64" && arch != "arm64" {
		return StagedRelease{}, fmt.Errorf("unsupported architecture: %s", arch)
	}

	expectedSha := record.Sha256Amd64
	if arch == "arm64" {
		expectedSha = record.Sha256Arm64
	}

	err := os.MkdirAll(stagingDir, 0700)
	if err != nil {
		return StagedRelease{}, err
	}

	stagedPath := stagedPath(record.Release, arch)
	if fileMatchesSha256(stagedPath, expectedSha) {
		progressText(fmt.Sprintf("The release %s was already downloaded and its checksum matches", record.Release))
		return StagedRelease{
			Release: record.Release,
			Path:    stagedPath,
			Sha256:  expectedSha,
		}, nil
	}
	_ = os.Remove(stagedPath)

	progressStage(
		fmt.Sprintf("Downloading %s", record.Release),
		progressPercentDownload,
	)
	err = download(ctx, downloadUrl(record.Release, arch), stagedPath, expectedSha)
	if err != nil {
		return StagedRelease{}, err
	}
	progressText(fmt.Sprintf("The checksum of %s matches the pinned release", record.Release))

	return StagedRelease{
		Release: record.Release,
		Path:    stagedPath,
		Sha256:  expectedSha,
	}, nil
}

func stagedPath(release string, arch string) string {
	name := shared.SanitizeForPath(release)
	return filepath.Join(stagingDir, fmt.Sprintf("k3s-%s-%s", name, arch))
}

func downloadUrl(release string, arch string) string {
	if arch == "arm64" {
		return fmt.Sprintf("https://github.com/k3s-io/k3s/releases/download/%s/k3s-arm64", release)
	}
	return fmt.Sprintf("https://github.com/k3s-io/k3s/releases/download/%s/k3s", release)
}

func fileMatchesSha256(path string, expectedSha string) bool {
	file, err := os.Open(path)
	if err != nil {
		return false
	}
	defer file.Close()

	hasher := sha256.New()
	_, err = io.Copy(hasher, file)
	if err != nil {
		return false
	}

	return hex.EncodeToString(hasher.Sum(nil)) == expectedSha
}

func download(ctx context.Context, url string, stagedPath string, expectedSha string) error {
	downloadCtx, cancel := context.WithTimeout(ctx, downloadTimeout)
	defer cancel()

	request, err := http.NewRequestWithContext(downloadCtx, http.MethodGet, url, nil)
	if err != nil {
		return err
	}

	client := http.Client{
		Timeout: downloadTimeout,
		CheckRedirect: func(redirect *http.Request, via []*http.Request) error {
			if redirect.URL.Scheme != "https" {
				return errors.New("the download redirected away from HTTPS")
			}
			return nil
		},
	}

	response, err := client.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()

	if response.StatusCode != http.StatusOK {
		return fmt.Errorf("the download returned status %s", response.Status)
	}

	tempFile, err := os.CreateTemp(stagingDir, ".k3s-stage-")
	if err != nil {
		return err
	}
	tempPath := tempFile.Name()

	err = writeVerified(tempFile, response.Body, expectedSha)
	if closeErr := tempFile.Close(); err == nil {
		err = closeErr
	}
	if err != nil {
		_ = os.Remove(tempPath)
		return err
	}

	err = os.Chmod(tempPath, 0755)
	if err != nil {
		_ = os.Remove(tempPath)
		return err
	}

	err = os.Rename(tempPath, stagedPath)
	if err != nil {
		_ = os.Remove(tempPath)
		return err
	}

	return nil
}

func writeVerified(file *os.File, body io.Reader, expectedSha string) error {
	hasher := sha256.New()
	written, err := io.Copy(io.MultiWriter(file, hasher), body)
	if err != nil {
		return err
	}
	if written == 0 {
		return errors.New("the downloaded binary is empty")
	}

	if hex.EncodeToString(hasher.Sum(nil)) != expectedSha {
		return errors.New("the downloaded checksum does not match the pinned release")
	}

	return file.Sync()
}

func installedVersion(ctx context.Context) string {
	commandCtx, cancel := context.WithTimeout(ctx, stopTimeout)
	defer cancel()

	output, err := exec.CommandContext(commandCtx, k3sBinary, "--version").CombinedOutput()
	if err != nil {
		log.Warn("k8s-app node agent: could not read the installed Kubernetes version: %s", err)
		return ""
	}

	fields := strings.Fields(strings.TrimSpace(string(output)))
	if len(fields) < 3 || fields[0] != "k3s" || fields[1] != "version" {
		return ""
	}
	return fields[2]
}

func snapshotEtcd(ctx context.Context) (string, error) {
	return snapshotEtcdNamed(ctx, etcdSnapshotName)
}

func snapshotEtcdNamed(ctx context.Context, snapshotName string) (string, error) {
	dir := etcdSnapshotDir()
	err := os.MkdirAll(dir, 0700)
	if err != nil {
		return "", err
	}

	name := fmt.Sprintf("%s-%d", snapshotName, time.Now().Unix())

	output, err := exec.CommandContext(
		ctx,
		k3sBinary,
		"etcd-snapshot",
		"save",
		"--name",
		name,
		"--dir",
		dir,
	).CombinedOutput()
	if err != nil {
		progressCommandOutput("the etcd snapshot command failed with", output)
		return "", fmt.Errorf("the etcd snapshot command failed: %s: %s", err, strings.TrimSpace(string(output)))
	}
	progressCommandOutput("The etcd snapshot command reported", output)

	return latestSnapshot(dir, name)
}

func etcdSnapshotDir() string {
	return filepath.Join(stagingDir, "etcd-snapshots")
}

func latestSnapshot(dir string, name string) (string, error) {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return "", err
	}

	var latestPath string
	var latestModTime time.Time
	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		entryName := entry.Name()
		if strings.HasPrefix(entryName, ".") || strings.HasSuffix(entryName, ".part") ||
			strings.HasSuffix(entryName, ".tmp") {
			continue
		}
		if name != "" && !strings.HasPrefix(entryName, fmt.Sprintf("%s-", name)) {
			continue
		}

		info, infoErr := entry.Info()
		if infoErr != nil || !info.Mode().IsRegular() {
			continue
		}

		if latestPath == "" || !info.ModTime().Before(latestModTime) {
			latestPath = filepath.Join(dir, entryName)
			latestModTime = info.ModTime()
		}
	}

	if latestPath == "" {
		if name != "" {
			return "", fmt.Errorf("the etcd snapshot %s was not found in %s", name, dir)
		}
		return "", errors.New("the etcd snapshot produced no snapshot file")
	}

	return latestPath, nil
}

type backupCopyInfo struct {
	SizeBytes int64
	Sha256    string
}

func runBackup(ctx context.Context, trigger string, attemptUid string) (shared.NodeAgentBackupResult, error) {
	workerCtx, cancel := context.WithTimeout(ctx, backupWorkDeadline)
	defer cancel()

	identityConfig := identitySnapshot()
	if identityConfig.Role != shared.GroupControlPlane {
		return shared.NodeAgentBackupResult{}, errors.New("backups can only run on a control-plane node")
	}

	stackId, identityErr := readBackupStackIdentity()
	if identityErr != nil {
		return shared.NodeAgentBackupResult{}, identityErr
	}

	release := installedVersion(workerCtx)
	if release == "" {
		release = identityConfig.K8sVersion
	}
	if release == "" {
		return shared.NodeAgentBackupResult{}, errors.New("the installed Kubernetes version could not be determined")
	}

	createdAt := time.Now().UTC()
	backupId := backup.NewId(createdAt)

	_, prePruneWarnings := pruneBackups(createdAt)

	partDir := filepath.Join(shared.BackupsMountPath, backupPartPrefix+shared.SanitizeForPath(attemptUid))

	_ = os.RemoveAll(partDir)
	partErr := os.MkdirAll(partDir, 0700)
	if partErr != nil {
		return shared.NodeAgentBackupResult{}, fmt.Errorf("could not create the partial backup directory: %s", partErr)
	}
	if chownErr := backupChown(partDir); chownErr != nil {
		return shared.NodeAgentBackupResult{}, chownErr
	}

	committed := false
	defer func() {
		if !committed {
			_ = os.RemoveAll(partDir)
		}
	}()

	snapshotPath, snapshotErr := snapshotEtcdNamed(workerCtx, backupSnapshotName)
	if snapshotErr != nil {
		return shared.NodeAgentBackupResult{}, snapshotErr
	}
	defer func() {
		_ = os.Remove(snapshotPath)
	}()

	snapshotInfo, copyErr := copyBackupFile(snapshotPath, filepath.Join(partDir, "snapshot"))
	if copyErr != nil {
		return shared.NodeAgentBackupResult{}, fmt.Errorf("could not copy the etcd snapshot: %s", copyErr)
	}

	credentialsDir := filepath.Join(partDir, "credentials")
	credentialsErr := os.MkdirAll(credentialsDir, 0700)
	if credentialsErr != nil {
		return shared.NodeAgentBackupResult{}, fmt.Errorf("could not create the credentials directory: %s", credentialsErr)
	}
	if chownErr := backupChown(credentialsDir); chownErr != nil {
		return shared.NodeAgentBackupResult{}, chownErr
	}

	serverTokenInfo, serverTokenErr := copyBackupFile(
		filepath.Join(shared.ManagementMountPath, "tokens", "server"),
		filepath.Join(credentialsDir, "server-token"),
	)
	if serverTokenErr != nil {
		return shared.NodeAgentBackupResult{}, fmt.Errorf("could not copy the server token: %s", serverTokenErr)
	}

	agentTokenInfo, agentTokenErr := copyBackupFile(
		filepath.Join(shared.ManagementMountPath, "tokens", "agent"),
		filepath.Join(credentialsDir, "agent-token"),
	)
	if agentTokenErr != nil {
		return shared.NodeAgentBackupResult{}, fmt.Errorf("could not copy the agent token: %s", agentTokenErr)
	}

	maintenanceErr := copyBackupDir(
		shared.NodeAgentCoordinatorTokensDir,
		filepath.Join(credentialsDir, "maintenance-tokens"),
	)
	if maintenanceErr != nil {
		return shared.NodeAgentBackupResult{}, fmt.Errorf("could not copy the maintenance tokens: %s", maintenanceErr)
	}

	metadata := backup.Metadata{
		SchemaRevision: backup.SchemaRevision,
		BackupId:       backupId,
		ClusterId:      stackId,
		StackId:        stackId,
		Release:        release,
		CreatedAt:      createdAt,
		CreatedByNode:  identityConfig.NodeName,
		Trigger:        trigger,
		Snapshot: backup.SnapshotInfo{
			File:      "snapshot",
			SizeBytes: snapshotInfo.SizeBytes,
			Sha256:    snapshotInfo.Sha256,
		},
		Credentials: backup.CredentialsInfo{
			ServerTokenSha256: serverTokenInfo.Sha256,
			AgentTokenSha256:  agentTokenInfo.Sha256,
		},
		Restore: backup.RestoreInfo{
			ClusterResetRequired: true,
		},
	}

	metadataData, metadataErr := json.MarshalIndent(metadata, "", "  ")
	if metadataErr != nil {
		return shared.NodeAgentBackupResult{}, metadataErr
	}

	metadataPath := filepath.Join(partDir, "metadata.json")
	writeErr := writeFileAtomic(metadataPath, metadataData, 0600)
	if writeErr != nil {
		return shared.NodeAgentBackupResult{}, fmt.Errorf("could not write the backup metadata: %s", writeErr)
	}
	if chownErr := backupChown(metadataPath); chownErr != nil {
		return shared.NodeAgentBackupResult{}, chownErr
	}

	dirErr := syncDir(partDir)
	if dirErr != nil {
		return shared.NodeAgentBackupResult{}, fmt.Errorf("could not synchronize the backup directory: %s", dirErr)
	}

	finalDir := filepath.Join(shared.BackupsMountPath, backupId)
	renameErr := os.Rename(partDir, finalDir)
	if renameErr != nil {
		return shared.NodeAgentBackupResult{}, fmt.Errorf("could not commit the backup: %s", renameErr)
	}
	committed = true

	if syncErr := syncDir(shared.BackupsMountPath); syncErr != nil {
		log.Warn("k8s-app node agent: could not synchronize the backups directory: %s", syncErr)
	}

	retained, warnings := pruneBackups(time.Now().UTC())
	warnings = append(prePruneWarnings, warnings...)

	return shared.NodeAgentBackupResult{
		Metadata:      metadata,
		Retained:      retained,
		PruneWarnings: warnings,
	}, nil
}

func readBackupStackIdentity() (string, error) {
	data, err := os.ReadFile(filepath.Join(inputDir, "stack-identity.json"))
	if err != nil {
		return "", fmt.Errorf("could not read the stack identity: %s", err)
	}

	var identity struct {
		StackId string `json:"stackId"`
	}
	err = json.Unmarshal(data, &identity)
	if err != nil {
		return "", fmt.Errorf("the stack identity is invalid: %s", err)
	}

	stackId := strings.TrimSpace(identity.StackId)
	if stackId == "" {
		return "", errors.New("the stack identity has no stack id")
	}
	return stackId, nil
}

func copyBackupFile(source string, destination string) (backupCopyInfo, error) {
	sourceFile, err := os.Open(source)
	if err != nil {
		return backupCopyInfo{}, err
	}
	defer sourceFile.Close()

	destinationFile, err := os.OpenFile(destination, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0600)
	if err != nil {
		return backupCopyInfo{}, err
	}

	hasher := sha256.New()
	written, err := io.Copy(io.MultiWriter(destinationFile, hasher), sourceFile)
	if err == nil && written == 0 {
		err = errors.New("the source file is empty")
	}
	if err == nil {
		err = destinationFile.Sync()
	}
	if closeErr := destinationFile.Close(); err == nil {
		err = closeErr
	}
	if err != nil {
		_ = os.Remove(destination)
		return backupCopyInfo{}, err
	}

	chownErr := backupChown(destination)
	if chownErr != nil {
		return backupCopyInfo{}, chownErr
	}

	return backupCopyInfo{
		SizeBytes: written,
		Sha256:    hex.EncodeToString(hasher.Sum(nil)),
	}, nil
}

func copyBackupDir(sourceDir string, destinationDir string) error {
	entries, err := os.ReadDir(sourceDir)
	if err != nil {
		return err
	}

	err = os.MkdirAll(destinationDir, 0700)
	if err != nil {
		return err
	}
	if chownErr := backupChown(destinationDir); chownErr != nil {
		return chownErr
	}

	for _, entry := range entries {
		if !entry.Type().IsRegular() {
			continue
		}

		_, copyErr := copyBackupFile(
			filepath.Join(sourceDir, entry.Name()),
			filepath.Join(destinationDir, entry.Name()),
		)
		if copyErr != nil {
			return copyErr
		}
	}

	return syncDir(destinationDir)
}

func backupChown(path string) error {
	err := os.Chown(path, serviceUid, serviceGid)
	if err != nil {
		return fmt.Errorf("could not assign the backup data to the cluster service: %s", err)
	}
	return nil
}

func pruneBackups(now time.Time) ([]backup.BackupInfo, []string) {
	var warnings []string

	entries, err := os.ReadDir(shared.BackupsMountPath)
	if err != nil {
		return nil, []string{fmt.Sprintf("could not list the backups: %s", err)}
	}

	var ids []string
	for _, entry := range entries {
		entryName := entry.Name()
		if !entry.IsDir() {
			continue
		}
		if strings.HasPrefix(entryName, backupPartPrefix) {
			info, infoErr := entry.Info()
			if infoErr != nil {
				warnings = append(warnings, fmt.Sprintf("could not inspect the partial backup %s: %s", entryName, infoErr))
				continue
			}
			if now.Sub(info.ModTime()) < backupPartStaleAge {
				continue
			}
			removeErr := os.RemoveAll(filepath.Join(shared.BackupsMountPath, entryName))
			if removeErr != nil {
				warnings = append(warnings, fmt.Sprintf("could not remove the partial backup %s: %s", entryName, removeErr))
			}
			continue
		}
		if _, ok := backup.ParseId(entryName); ok {
			ids = append(ids, entryName)
		}
	}

	deletes, retentionErr := backup.RetentionDeleteIds(ids, now)
	if retentionErr != nil {
		return retainedBackups(ids), append(warnings, fmt.Sprintf("could not compute the retention: %s", retentionErr))
	}

	deleted := map[string]bool{}
	for _, id := range deletes {
		removeErr := os.RemoveAll(filepath.Join(shared.BackupsMountPath, id))
		if removeErr != nil {
			warnings = append(warnings, fmt.Sprintf("could not delete the backup %s: %s", id, removeErr))
			continue
		}
		deleted[id] = true
	}

	kept := make([]string, 0, len(ids))
	for _, id := range ids {
		if !deleted[id] {
			kept = append(kept, id)
		}
	}

	return retainedBackups(kept), warnings
}

func retainedBackups(ids []string) []backup.BackupInfo {
	retained := make([]backup.BackupInfo, 0, len(ids))
	for i := len(ids) - 1; i >= 0; i-- {
		id := ids[i]
		info := backup.BackupInfo{Id: id}
		if createdAt, ok := backup.ParseId(id); ok {
			info.CreatedAt = createdAt
		}
		metadata, err := readBackupMetadata(filepath.Join(shared.BackupsMountPath, id))
		if err == nil {
			info.Release = metadata.Release
			info.SizeBytes = metadata.Snapshot.SizeBytes
			info.Sha256 = metadata.Snapshot.Sha256
			info.Trigger = metadata.Trigger
			info.CreatedByNode = metadata.CreatedByNode
		}
		retained = append(retained, info)
	}
	return retained
}

func readBackupMetadata(dir string) (backup.Metadata, error) {
	data, err := os.ReadFile(filepath.Join(dir, "metadata.json"))
	if err != nil {
		return backup.Metadata{}, err
	}

	var metadata backup.Metadata
	err = json.Unmarshal(data, &metadata)
	if err != nil {
		return backup.Metadata{}, err
	}
	if metadata.SchemaRevision != backup.SchemaRevision {
		return backup.Metadata{}, errors.New("the backup metadata was written by an incompatible version of the application")
	}
	return metadata, nil
}

func backupBinary(binaryPath string, release string) (string, error) {
	source, err := os.Open(binaryPath)
	if err != nil {
		return "", err
	}
	defer source.Close()

	backupName := fmt.Sprintf("k3s-previous-%s", shared.SanitizeForPath(release))
	backupPath := filepath.Join(workingDir(), backupName)
	tempPath := fmt.Sprintf("%s.tmp", backupPath)

	destination, err := os.OpenFile(tempPath, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0755)
	if err != nil {
		return "", err
	}

	_, err = destination.ReadFrom(source)
	if err == nil {
		err = destination.Sync()
	}
	if closeErr := destination.Close(); err == nil {
		err = closeErr
	}
	if err != nil {
		_ = os.Remove(tempPath)
		return "", err
	}

	err = os.Rename(tempPath, backupPath)
	if err != nil {
		_ = os.Remove(tempPath)
		return "", err
	}

	err = syncDir(workingDir())
	if err != nil {
		return "", err
	}

	return backupPath, nil
}

func prepareReplacement(stagedPath string, replacementPath string) error {
	source, err := os.Open(stagedPath)
	if err != nil {
		return err
	}
	defer source.Close()

	destination, err := os.OpenFile(replacementPath, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0755)
	if err != nil {
		return err
	}

	_, err = destination.ReadFrom(source)
	if err == nil {
		err = destination.Sync()
	}
	if closeErr := destination.Close(); err == nil {
		err = closeErr
	}
	if err != nil {
		_ = os.Remove(replacementPath)
		return err
	}

	err = os.Chmod(replacementPath, 0755)
	if err != nil {
		_ = os.Remove(replacementPath)
		return err
	}

	return nil
}

func swapReplacement(replacementPath string, targetPath string) error {
	err := os.Rename(replacementPath, targetPath)
	if err != nil {
		return err
	}

	return syncDir(filepath.Dir(targetPath))
}

func syncDir(dir string) error {
	dirFile, err := os.Open(dir)
	if err != nil {
		return err
	}

	err = dirFile.Sync()
	if closeErr := dirFile.Close(); err == nil {
		err = closeErr
	}
	return err
}

func stopService(ctx context.Context, service string) error {
	commandCtx, cancel := context.WithTimeout(ctx, stopTimeout)
	defer cancel()

	output, err := exec.CommandContext(commandCtx, "systemctl", "stop", "--no-block", service).CombinedOutput()
	if err != nil {
		return fmt.Errorf("%s: %s", err, strings.TrimSpace(string(output)))
	}

	pollCtx, pollCancel := context.WithTimeout(ctx, stopTimeout)
	defer pollCancel()

	ticker := time.NewTicker(stopPollInterval)
	defer ticker.Stop()

	for {
		stateOutput, stateErr := exec.CommandContext(pollCtx, "systemctl", "show", service, "--property=ActiveState", "--value").CombinedOutput()
		if stateErr != nil {
			return fmt.Errorf("could not read the state of %s: %s: %s", service, stateErr, strings.TrimSpace(string(stateOutput)))
		}

		activeState := strings.TrimSpace(string(stateOutput))
		if activeState == "inactive" || activeState == "failed" {
			return nil
		}

		select {
		case <-pollCtx.Done():
			return fmt.Errorf(
				"the stop of %s did not reach a terminal state within %s. The last state was %s",
				service,
				stopTimeout,
				activeState,
			)
		case <-ticker.C:
		}
	}
}

func startService(ctx context.Context, service string) error {
	commandCtx, cancel := context.WithTimeout(ctx, startTimeout)
	defer cancel()

	output, err := exec.CommandContext(commandCtx, "systemctl", "start", service).CombinedOutput()
	if err != nil {
		return fmt.Errorf("%s: %s", err, strings.TrimSpace(string(output)))
	}
	return nil
}

func verifyUpgrade(ctx context.Context, release string, role string, service string) error {
	verifyCtx, cancel := context.WithTimeout(ctx, startTimeout)
	defer cancel()

	activeOutput, err := exec.CommandContext(verifyCtx, "systemctl", "is-active", service).CombinedOutput()
	if err != nil {
		return fmt.Errorf("%s is not active: %s", service, strings.TrimSpace(string(activeOutput)))
	}

	versionOutput, err := exec.CommandContext(verifyCtx, k3sBinary, "--version").CombinedOutput()
	if err != nil {
		return fmt.Errorf("could not read the installed Kubernetes version: %s", err)
	}

	fields := strings.Fields(strings.TrimSpace(string(versionOutput)))
	versionMatches := len(fields) >= 3 && fields[0] == "k3s" && fields[1] == "version" && fields[2] == release
	if !versionMatches {
		return fmt.Errorf(
			"the installed Kubernetes version does not match the target release: %s",
			strings.TrimSpace(string(versionOutput)),
		)
	}

	if role == shared.GroupControlPlane {
		pollCtx, pollCancel := context.WithTimeout(ctx, startTimeout)
		defer pollCancel()

		ticker := time.NewTicker(readyzPollInterval)
		defer ticker.Stop()

		var lastOutput string
		for {
			requestCtx, requestCancel := context.WithTimeout(pollCtx, readyzTimeout)
			readyzOutput, readyzErr := exec.CommandContext(
				requestCtx,
				k3sBinary,
				"kubectl",
				"--request-timeout=10s",
				"--server",
				localServerUrl,
				"--kubeconfig",
				kubeconfig,
				"get",
				"--raw",
				"/readyz",
			).CombinedOutput()
			requestCancel()

			if readyzErr == nil {
				return nil
			}
			lastOutput = strings.TrimSpace(string(readyzOutput))

			select {
			case <-pollCtx.Done():
				return fmt.Errorf(
					"the control plane did not report ready within %s: %s: %s",
					startTimeout,
					readyzErr,
					lastOutput,
				)
			case <-ticker.C:
			}
		}
	}

	return nil
}
