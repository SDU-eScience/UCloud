package nodeagent

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
	"time"

	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/log"
)

const (
	osStateSchemaRevision = 1
	osStateName           = "os-state.json"

	osWorkDeadline        = 70 * time.Minute
	osCheckTimeout        = 5 * time.Minute
	osStageTimeout        = 20 * time.Minute
	osApplyTimeout        = 30 * time.Minute
	osVerifyTimeout       = 15 * time.Minute
	osCleanupTimeout      = 10 * time.Minute
	osRebootWait          = 2 * time.Minute
	osRebootPollInterval  = 2 * time.Second
	osCommandShortTimeout = 30 * time.Second

	osRootFreeMinBytes = 512 * 1024 * 1024
	osBootFreeMinBytes = 256 * 1024 * 1024

	osRebootRequiredPath = "/var/run/reboot-required"
	osBootIdPath         = "/proc/sys/kernel/random/boot_id"
	osKernelReleasePath  = "/proc/sys/kernel/osrelease"

	osProgressStart     = 5
	osProgressStaged    = 35
	osProgressApplied   = 60
	osProgressReboot    = 75
	osProgressVerifying = 85
	osProgressDone      = 100
)

type osState struct {
	SchemaRevision int       `json:"schemaRevision"`
	OperationUid   string    `json:"operationUid,omitempty"`
	NodeName       string    `json:"nodeName"`
	IpAddress      string    `json:"ipAddress"`
	Role           string    `json:"role"`
	Phase          string    `json:"phase"`
	Error          string    `json:"error"`
	NeedsRecovery  bool      `json:"needsRecovery"`
	UpdatedAt      time.Time `json:"updatedAt"`

	Pending        int       `json:"pending,omitempty"`
	Security       int       `json:"security,omitempty"`
	RebootRequired bool      `json:"rebootRequired,omitempty"`
	KernelRunning  string    `json:"kernelRunning,omitempty"`
	KernelPending  string    `json:"kernelPending,omitempty"`
	CheckedAt      time.Time `json:"checkedAt,omitempty"`
	CheckError     string    `json:"checkError,omitempty"`
	BootId         string    `json:"bootId,omitempty"`

	StateError         string `json:"stateError,omitempty"`
	NeedsStateRecovery bool   `json:"needsStateRecovery,omitempty"`
}

func osStatePath() string {
	return filepath.Join(workingDir(), osStateName)
}

func osStateLoad() osState {
	globals.Mu.Lock()
	defer globals.Mu.Unlock()
	return osStateRead()
}

func osStateRead() osState {
	data, err := os.ReadFile(osStatePath())
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return osState{}
		}
		return osState{
			SchemaRevision:     osStateSchemaRevision,
			Phase:              shared.NodeAgentOsPhaseFailed,
			Error:              fmt.Sprintf("could not read the operating-system update state: %s", err),
			NeedsRecovery:      true,
			StateError:         fmt.Sprintf("could not read the operating-system update state: %s", err),
			NeedsStateRecovery: true,
		}
	}

	var state osState
	err = json.Unmarshal(data, &state)
	if err != nil {
		return osState{
			SchemaRevision:     osStateSchemaRevision,
			Phase:              shared.NodeAgentOsPhaseFailed,
			Error:              fmt.Sprintf("could not parse the operating-system update state: %s", err),
			NeedsRecovery:      true,
			StateError:         fmt.Sprintf("could not parse the operating-system update state: %s", err),
			NeedsStateRecovery: true,
		}
	}

	if state.SchemaRevision != osStateSchemaRevision {
		state.StateError = "the operating-system update state was written by an incompatible version of the application"
		state.Phase = shared.NodeAgentOsPhaseFailed
		state.Error = state.StateError
		state.NeedsRecovery = true
		state.NeedsStateRecovery = true
	}

	return state
}

func osStateStore(state *osState) error {
	data, err := json.MarshalIndent(state, "", "  ")
	if err != nil {
		return err
	}

	return writeFileAtomic(osStatePath(), data, 0600)
}

func osSetPhase(phase string, mutate func(state *osState)) bool {
	globals.Mu.Lock()
	defer globals.Mu.Unlock()

	state := osStateRead()
	if state.NeedsStateRecovery {
		return false
	}

	state.Phase = phase
	state.Error = ""
	state.UpdatedAt = time.Now().UTC()
	if mutate != nil {
		mutate(&state)
	}

	err := osStateStore(&state)
	if err != nil {
		log.Warn("k8s-app node agent: could not persist the operating-system update phase %s: %s", phase, err)
		return false
	}
	return true
}

func osStatusFromState(identityConfig Config) shared.NodeAgentOsStatus {
	state := osStateLoad()

	if state.NeedsStateRecovery {
		return shared.NodeAgentOsStatus{
			Progress:           progressLoad(),
			StagingSupported:   true,
			OperationUid:       state.OperationUid,
			NodeName:           state.NodeName,
			Phase:              shared.NodeAgentOsPhaseFailed,
			Error:              state.StateError,
			NeedsRecovery:      true,
			NeedsStateRecovery: true,
			UpdatedAt:          state.UpdatedAt,
		}
	}

	phase := state.Phase
	if phase == "" {
		phase = shared.NodeAgentOsPhaseIdle
	}
	nodeName := state.NodeName
	if nodeName == "" && identityConfig.NodeName != "" {
		nodeName = identityConfig.NodeName
	}

	return shared.NodeAgentOsStatus{
		Progress:         progressLoad(),
		StagingSupported: true,
		OperationUid:     state.OperationUid,
		NodeName:         nodeName,
		Phase:            phase,
		Error:            state.Error,
		NeedsRecovery:    state.NeedsRecovery,
		Pending:          state.Pending,
		Security:         state.Security,
		RebootRequired:   state.RebootRequired,
		KernelRunning:    state.KernelRunning,
		KernelPending:    state.KernelPending,
		CheckedAt:        state.CheckedAt,
		CheckError:       state.CheckError,
		BootId:           state.BootId,
		UpdatedAt:        state.UpdatedAt,
	}
}

// Startup recovery
// =====================================================================================================================
// A reboot is an expected part of an operating-system update. When the agent starts and the state says rebooting, the
// boot id decides: a new boot id continues at the verification, while the same boot id means that the reboot never
// happened and the update needs recovery. Every other interrupted phase needs recovery.

func recoverInterruptedOs() {
	state := osStateLoad()
	if state.NeedsStateRecovery {
		return
	}

	switch {
	case state.Phase == shared.NodeAgentOsPhaseRebooting:
		bootId := osBootId()
		if bootId != "" && state.BootId != "" && bootId != state.BootId {
			progressStageLabel("The node rebooted. The operating-system update continues with the verification")
			if !osSetPhase(shared.NodeAgentOsPhaseVerifying, nil) {
				osFinishFailure("could not persist the phase verifying after the reboot")
				return
			}
			go osResumeVerify()
			return
		}

		osFailInterrupted(state, "the node did not restart after the update. Retry the update")
	case shared.NodeAgentOsPhaseActive(state.Phase):
		osFailInterrupted(state, fmt.Sprintf(
			"the update was interrupted while %s. Retry the update",
			osPhaseUserLabel(state.Phase),
		))
	}
}

func osFailInterrupted(state osState, message string) {
	state.Phase = shared.NodeAgentOsPhaseFailed
	state.Error = message
	state.NeedsRecovery = true
	state.UpdatedAt = time.Now().UTC()

	err := osStateStore(&state)
	if err != nil {
		log.Warn("k8s-app node agent: could not persist the failed operating-system update state: %s", err)
		return
	}

	progressStageLabel("The OS update stopped")
	progressText(fmt.Sprintf("The OS update stopped: %s", state.Error))
	log.Warn("k8s-app node agent: the operating-system update failed: %s", state.Error)
}

func osPhaseUserLabel(phase string) string {
	switch phase {
	case shared.NodeAgentOsPhaseStaging:
		return "downloading updates"
	case shared.NodeAgentOsPhaseStaged:
		return "waiting to install updates"
	case shared.NodeAgentOsPhaseApplying:
		return "installing updates"
	case shared.NodeAgentOsPhaseRebooting:
		return "restarting the node"
	case shared.NodeAgentOsPhaseVerifying:
		return "checking node health"
	}
	return phase
}

// The update check
// =====================================================================================================================
// The check refreshes the package lists and reports what a full upgrade would install. It does not change packages. A
// failed check is recorded in the report and does not mark the node as requiring recovery.

func runOsCheck(ctx context.Context) shared.NodeAgentOsStatus {
	identityConfig := identitySnapshot()

	pending := 0
	security := 0
	checkError := ""

	progressStageLabel("Checking for operating-system updates")
	_, updateErr := osRunCommand(ctx, osCheckTimeout, nil, "apt-get", "update", "-q")
	if updateErr != nil {
		checkError = fmt.Sprintf("the package lists could not be refreshed: %s", updateErr)
	} else {
		simulated, simulatedErr := osRunCommand(ctx, osCheckTimeout, nil, "apt-get", "-s", "-q", "-y", "full-upgrade")
		if simulatedErr != nil {
			checkError = fmt.Sprintf("the pending updates could not be computed: %s", simulatedErr)
		} else {
			for _, line := range strings.Split(simulated, "\n") {
				if !strings.HasPrefix(line, "Inst ") {
					continue
				}
				pending++
				if strings.Contains(line, "-security") {
					security++
				}
			}
		}
	}

	kernelRunning := osRunningKernel()
	kernelPending := osPendingKernel(ctx, kernelRunning)
	rebootRequired := kernelPending != "" || osFileExists(osRebootRequiredPath)

	state := osStateLoad()
	if !state.NeedsStateRecovery {
		state.SchemaRevision = osStateSchemaRevision
		state.NodeName = identityConfig.NodeName
		state.IpAddress = identityConfig.IpAddress
		state.Role = identityConfig.Role
		state.Pending = pending
		state.Security = security
		state.RebootRequired = rebootRequired
		state.KernelRunning = kernelRunning
		state.KernelPending = kernelPending
		state.CheckedAt = time.Now().UTC()
		state.CheckError = checkError
		storeErr := osStateStore(&state)
		if storeErr != nil {
			log.Warn("k8s-app node agent: could not persist the update check: %s", storeErr)
		}
	}

	if checkError != "" {
		progressText(fmt.Sprintf("The update check failed: %s", checkError))
	} else {
		progressText(fmt.Sprintf("The update check found %d pending packages, %d of them security", pending, security))
	}
	if rebootRequired {
		progressText("The node requires a reboot to finish its updates")
	}

	return osStatusFromState(identityConfig)
}

// The update worker
// =====================================================================================================================
// The worker stages the packages, applies them, decides whether the update needs a reboot and verifies the node. The
// phases are persisted so that the coordinator and the dashboard follow the update. A reboot is a normal transition:
// the phase rebooting is written with the boot id before the reboot command runs.

func runOsUpdate(rebootPolicy string) {
	workerCtx, cancel := context.WithTimeout(context.Background(), osWorkDeadline)
	defer cancel()
	if osStagePackages(workerCtx) {
		osApplyPackages(workerCtx, rebootPolicy)
	}
}

func runOsStage() {
	workerCtx, cancel := context.WithTimeout(context.Background(), osWorkDeadline)
	defer cancel()
	if !osStagePackages(workerCtx) {
		return
	}
	if !osSetPhase(shared.NodeAgentOsPhaseStaged, nil) {
		osFinishFailure("could not persist the downloaded operating-system update")
		return
	}
	progressStage("Packages downloaded. Preparing to move workloads", osProgressStaged)
}

func runOsApply(rebootPolicy string) {
	workerCtx, cancel := context.WithTimeout(context.Background(), osWorkDeadline)
	defer cancel()
	osApplyPackages(workerCtx, rebootPolicy)
}

func osStagePackages(workerCtx context.Context) bool {
	identityConfig := identitySnapshot()
	if identityConfig.Role == "" {
		osFinishFailure("the node identity is unknown")
		return false
	}

	serviceName := serviceForRole(identityConfig.Role)
	if serviceName == "" {
		osFinishFailure(fmt.Sprintf("unknown node role: %s", identityConfig.Role))
		return false
	}

	state := osStateLoad()
	if state.NeedsStateRecovery {
		osFinishFailure(state.StateError)
		return false
	}

	if !osSetPhase(shared.NodeAgentOsPhaseStaging, nil) {
		osFinishFailure("could not persist the phase staging")
		return false
	}

	progressStage("Checking package repositories", osProgressStart)
	updateErr := osRunPackageCommand(workerCtx, osCheckTimeout, nil, "update", "-q")
	if updateErr != nil {
		osFinishFailure(userMessage(
			"Check that the node can reach its package mirror, then retry the update",
			"the package lists could not be refreshed: %s", updateErr,
		))
		return false
	}

	diskErr := osCheckDiskSpace()
	if diskErr != nil {
		osFinishFailure(userMessage(
			"Free some disk space on the node, then retry the update",
			"%s", diskErr,
		))
		return false
	}

	progressStage("Downloading OS updates", osProgressStaged-10)
	stageErr := osRunPackageCommand(workerCtx, osStageTimeout, nil, "-d", "-q", "-y", "full-upgrade")
	if stageErr != nil {
		osFinishFailure(userMessage(
			"Check the disk space and the package mirror on the node, then retry the update",
			"OS updates could not be downloaded: %s", stageErr,
		))
		return false
	}
	if err := osCheckDiskSpace(); err != nil {
		osFinishFailure(userMessage(
			"Free some disk space on the node, then retry the update",
			"%s", err,
		))
		return false
	}
	return true
}

func osApplyPackages(workerCtx context.Context, rebootPolicy string) {
	serviceName := serviceForRole(identitySnapshot().Role)
	if serviceName == "" {
		osFinishFailure("the node identity is unknown")
		return
	}
	if !osSetPhase(shared.NodeAgentOsPhaseApplying, nil) {
		osFinishFailure("could not persist the phase applying")
		return
	}

	progressStage("Installing OS updates", osProgressApplied)
	applyErr := osRunPackageCommand(
		workerCtx,
		osApplyTimeout,
		[]string{"DEBIAN_FRONTEND=noninteractive", "NEEDRESTART_MODE=a"},
		"-q",
		"-y",
		"--no-download",
		"-o", "Dpkg::Use-Pty=0",
		"-o", "Dpkg::Progress-Fancy=0",
		"-o", "Dpkg::Options::=--force-confdef",
		"-o", "Dpkg::Options::=--force-confold",
		"full-upgrade",
	)
	if applyErr != nil {
		osFinishFailure(userMessage(
			"Retry the update on this node; contact support if it fails again",
			"OS updates could not be installed: %s", applyErr,
		))
		return
	}

	_, cleanErr := osRunCommand(workerCtx, osCleanupTimeout, nil, "apt-get", "-q", "-y", "clean")
	if cleanErr != nil {
		log.Warn("k8s-app node agent: could not clean the package cache: %s", cleanErr)
	}

	kernelRunning := osRunningKernel()
	kernelPending := osPendingKernel(workerCtx, kernelRunning)
	rebootRequired := kernelPending != "" ||
		osFileExists(osRebootRequiredPath) ||
		rebootPolicy == shared.NodeAgentOsRebootAlways

	if !osSetPhase(shared.NodeAgentOsPhaseApplying, func(state *osState) {
		state.KernelRunning = kernelRunning
		state.KernelPending = kernelPending
		state.RebootRequired = rebootRequired
	}) {
		osFinishFailure("could not persist the kernel state")
		return
	}

	for _, warning := range osCleanupKernels(workerCtx, kernelRunning, osNewestOtherKernel(workerCtx, kernelRunning)) {
		progressText(warning)
	}

	if rebootRequired {
		rebootErr := osRebootForUpdate(workerCtx)
		if rebootErr != nil {
			osFinishFailure(rebootErr.Error())
			return
		}
		return
	}

	osRunVerification(serviceName, kernelPending)
}

func osResumeVerify() {
	verifyCtx, cancel := context.WithTimeout(context.Background(), osVerifyTimeout)
	defer cancel()

	identityConfig := identitySnapshot()
	serviceName := serviceForRole(identityConfig.Role)
	if serviceName == "" {
		osFinishFailure("the node identity is unknown")
		return
	}

	state := osStateLoad()
	osRunVerificationEx(verifyCtx, serviceName, state.KernelPending)
}

func osRunVerification(serviceName string, kernelPending string) {
	verifyCtx, cancel := context.WithTimeout(context.Background(), osVerifyTimeout)
	defer cancel()
	osRunVerificationEx(verifyCtx, serviceName, kernelPending)
}

func osRunVerificationEx(ctx context.Context, serviceName string, kernelPending string) {
	if !osSetPhase(shared.NodeAgentOsPhaseVerifying, nil) {
		osFinishFailure("could not persist the phase verifying")
		return
	}

	progressStage("Checking node health after the update", osProgressVerifying)
	verifyErr := osVerifyUpdate(ctx, serviceName, kernelPending)
	if verifyErr != nil {
		osFinishFailure(verifyErr.Error())
		return
	}

	osCompleteUpdate()
}

func osVerifyUpdate(ctx context.Context, serviceName string, kernelPending string) error {
	ticker := time.NewTicker(readyzPollInterval)
	defer ticker.Stop()

	for {
		_, activeErr := osRunCommand(ctx, stopTimeout, nil, "systemctl", "is-active", serviceName)
		if activeErr == nil {
			break
		}

		select {
		case <-ctx.Done():
			return fmt.Errorf("%s did not become active after the operating-system update: %w: %s", serviceName, ctx.Err(), activeErr)
		case <-ticker.C:
		}
	}

	running := osRunningKernel()
	if running == "" {
		return errors.New("the running kernel could not be determined")
	}
	if kernelPending != "" && running != kernelPending {
		return fmt.Errorf(
			"the planned kernel %s is not running. The node runs %s",
			kernelPending,
			running,
		)
	}

	if identitySnapshot().Role == shared.GroupControlPlane {
		return verifyControlPlaneReadyz(ctx)
	}

	return nil
}

func osCompleteUpdate() {
	state := osStateLoad()
	state.Phase = shared.NodeAgentOsPhaseCompleted
	state.Error = ""
	state.NeedsRecovery = false
	state.RebootRequired = false
	state.Pending = 0
	state.Security = 0
	state.CheckedAt = time.Time{}
	state.UpdatedAt = time.Now().UTC()

	err := osStateStore(&state)
	if err != nil {
		osFinishFailure(fmt.Sprintf("could not persist the completed operating-system update: %s", err))
		return
	}

	progressStage("OS updates installed and node health checked", osProgressDone)
	log.Info("k8s-app node agent: the OS update completed on %s", state.NodeName)
}

func osFinishFailure(message string) {
	log.Warn("k8s-app node agent: the operating-system update failed: %s", message)
	progressStageLabel("The OS update stopped")
	progressText(fmt.Sprintf("The OS update stopped: %s", message))

	identityConfig := identitySnapshot()
	state := osStateLoad()
	state.SchemaRevision = osStateSchemaRevision
	if identityConfig.NodeName != "" {
		state.NodeName = identityConfig.NodeName
		state.IpAddress = identityConfig.IpAddress
		state.Role = identityConfig.Role
	}
	state.Phase = shared.NodeAgentOsPhaseFailed
	state.Error = message
	state.NeedsRecovery = true
	state.UpdatedAt = time.Now().UTC()

	err := osStateStore(&state)
	if err != nil {
		log.Warn("k8s-app node agent: could not persist the failed operating-system update state: %s", err)
	}
}

func osRebootForUpdate(ctx context.Context) error {
	bootId := osBootId()
	if bootId == "" {
		return errors.New("the boot id of the node could not be read")
	}

	if !osSetPhase(shared.NodeAgentOsPhaseRebooting, func(state *osState) {
		state.BootId = bootId
	}) {
		return errors.New("could not persist the phase rebooting")
	}

	progressStage("Rebooting the node to finish the update", osProgressReboot)
	progressText("The node reboots to activate the new kernel. The update continues after the boot")

	_, rebootErr := osRunCommand(ctx, osCommandShortTimeout, nil, "systemctl", "reboot")
	if rebootErr != nil {
		systemState, readable := osSystemState(ctx)
		switch systemState {
		case "running", "degraded", "starting", "initializing", "maintenance":
			if readable {
				return fmt.Errorf("the reboot command failed: %s", rebootErr)
			}
		}

		// The system is going down even though the command reported an error. The boot id proof decides.
		log.Warn("k8s-app node agent: the reboot command reported an error, but the node is shutting down: %s", rebootErr)
		return nil
	}

	deadline := time.Now().Add(osRebootWait)
	for {
		if ctx.Err() != nil {
			// The update is out of time, but the reboot may already be on its way. The boot id proof decides after
			// the next boot.
			return nil
		}

		systemState, readable := osSystemState(ctx)
		if !readable {
			return nil
		}

		switch systemState {
		case "running", "degraded", "starting", "initializing", "maintenance":
			if time.Now().After(deadline) {
				// The reboot is scheduled but the shutdown has not started yet. The phase stays rebooting, and the
				// boot id proof and the startup recovery decide the outcome.
				log.Warn(
					"k8s-app node agent: the node did not start shutting down within %s. The update stays in the rebooting phase",
					osRebootWait,
				)
				return nil
			}
		default:
			return nil
		}

		select {
		case <-ctx.Done():
			return nil
		case <-time.After(osRebootPollInterval):
		}
	}
}

func osSystemState(ctx context.Context) (string, bool) {
	output, err := osRunCommand(ctx, osCommandShortTimeout, nil, "systemctl", "is-system-running")
	state := strings.TrimSpace(output)
	if state == "" && err != nil {
		return "", false
	}
	return state, true
}

// Command and package helpers
// =====================================================================================================================

func userMessage(action string, format string, args ...any) string {
	details := fmt.Sprintf(format, args...)
	if !strings.HasSuffix(details, ".") {
		details += "."
	}
	return fmt.Sprintf("%s %s.", strings.TrimSpace(details), action)
}

func osRunCommand(ctx context.Context, timeout time.Duration, env []string, name string, args ...string) (string, error) {
	commandCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	command := exec.CommandContext(commandCtx, name, args...)
	if len(env) > 0 {
		command.Env = append(os.Environ(), env...)
	}

	output, err := command.CombinedOutput()
	trimmed := strings.TrimSpace(string(output))
	if err != nil {
		if trimmed == "" {
			return "", fmt.Errorf("%s: %s", err, name)
		}
		return trimmed, fmt.Errorf("%s: %s", err, trimmed)
	}
	return trimmed, nil
}

func osBootId() string {
	data, err := os.ReadFile(osBootIdPath)
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(data))
}

func osRunningKernel() string {
	data, err := os.ReadFile(osKernelReleasePath)
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(data))
}

func osFileExists(path string) bool {
	_, err := os.Stat(path)
	return err == nil
}

func osCheckDiskSpace() error {
	rootFree, err := osFreeBytes("/")
	if err != nil {
		return fmt.Errorf("could not read the free space of /: %s", err)
	}
	if rootFree < osRootFreeMinBytes {
		return fmt.Errorf(
			"the root file system has %d MB free, which is below the required %d MB",
			rootFree/(1024*1024),
			osRootFreeMinBytes/(1024*1024),
		)
	}

	bootFree, err := osFreeBytes("/boot")
	if err != nil {
		return fmt.Errorf("could not read the free space of /boot: %s", err)
	}
	if bootFree < osBootFreeMinBytes {
		return fmt.Errorf(
			"the boot file system has %d MB free, which is below the required %d MB",
			bootFree/(1024*1024),
			osBootFreeMinBytes/(1024*1024),
		)
	}

	return nil
}

func osFreeBytes(path string) (uint64, error) {
	var stat syscall.Statfs_t
	err := syscall.Statfs(path, &stat)
	if err != nil {
		return 0, err
	}
	return stat.Bavail * uint64(stat.Bsize), nil
}

func osInstalledKernels(ctx context.Context) []string {
	output, err := osRunCommand(ctx, osCommandShortTimeout, nil, "dpkg-query", "-W", "-f=${Package} ${db:Status-Abbrev}\\n", "linux-image-*")
	if err != nil && output == "" {
		return nil
	}

	kernels := []string{}
	for _, line := range strings.Split(output, "\n") {
		fields := strings.Fields(line)
		if len(fields) < 2 {
			continue
		}

		pkg := fields[0]
		if !strings.HasPrefix(strings.TrimSpace(fields[1]), "ii") {
			continue
		}
		if !strings.HasPrefix(pkg, "linux-image-") {
			continue
		}
		release := strings.TrimPrefix(pkg, "linux-image-")
		if release == "" || release[0] < '0' || release[0] > '9' {
			continue
		}
		kernels = append(kernels, release)
	}
	return kernels
}

func osNewestOtherKernel(ctx context.Context, running string) string {
	newest := ""
	for _, kernel := range osInstalledKernels(ctx) {
		if kernel == running {
			continue
		}
		if newest == "" || osKernelNewer(kernel, newest) {
			newest = kernel
		}
	}
	return newest
}

func osPendingKernel(ctx context.Context, running string) string {
	if running == "" {
		return ""
	}

	newest := running
	for _, kernel := range osInstalledKernels(ctx) {
		if osKernelNewer(kernel, newest) {
			newest = kernel
		}
	}
	if newest == running {
		return ""
	}
	return newest
}

func osCleanupKernels(ctx context.Context, running string, keep string) []string {
	removals := []string{}
	for _, kernel := range osInstalledKernels(ctx) {
		if kernel == running || kernel == keep {
			continue
		}

		candidates := []string{
			"linux-image-" + kernel,
			"linux-modules-" + kernel,
			"linux-modules-extra-" + kernel,
			"linux-headers-" + kernel,
		}
		for _, candidate := range candidates {
			installed, err := osPackageInstalled(ctx, candidate)
			if err != nil {
				continue
			}
			if installed {
				removals = append(removals, candidate)
			}
		}
	}

	if len(removals) == 0 {
		return nil
	}

	args := append([]string{"-q", "-y", "purge"}, removals...)
	_, purgeErr := osRunCommand(ctx, osCleanupTimeout, nil, "apt-get", args...)
	if purgeErr != nil {
		return []string{fmt.Sprintf("The old kernels %s could not be removed: %s", strings.Join(removals, ", "), purgeErr)}
	}

	return []string{fmt.Sprintf("The old kernels %s were removed", strings.Join(removals, ", "))}
}

func osPackageInstalled(ctx context.Context, name string) (bool, error) {
	output, err := osRunCommand(ctx, osCommandShortTimeout, nil, "dpkg-query", "-W", "-f=${db:Status-Abbrev}", name)
	if err != nil && output == "" {
		return false, err
	}
	return strings.HasPrefix(strings.TrimSpace(output), "ii"), nil
}

func osKernelNewer(left string, right string) bool {
	leftParts := osKernelParts(left)
	rightParts := osKernelParts(right)
	if len(leftParts) == 0 || len(rightParts) == 0 {
		return left > right
	}

	for i := 0; i < len(leftParts) && i < len(rightParts); i++ {
		if leftParts[i] != rightParts[i] {
			return leftParts[i] > rightParts[i]
		}
	}
	if len(leftParts) != len(rightParts) {
		return len(leftParts) > len(rightParts)
	}
	return left > right
}

func osKernelParts(release string) []int {
	parts := []int{}
	for _, field := range strings.Split(release, "-") {
		for _, sub := range strings.Split(field, ".") {
			digits := ""
			for _, char := range sub {
				if char < '0' || char > '9' {
					break
				}
				digits += string(char)
			}
			if digits == "" {
				return parts
			}
			value, err := strconv.Atoi(digits)
			if err != nil {
				return parts
			}
			parts = append(parts, value)
		}
	}
	if len(parts) == 0 {
		return nil
	}
	return parts
}
