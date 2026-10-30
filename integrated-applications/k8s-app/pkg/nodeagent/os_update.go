// Introduction
// =====================================================================================================================
// This file updates the operating system of one node. The update uses apt and runs in three stages. A check reports
// the pending packages and the kernel state. A stage downloads the packages. An apply installs them and verifies the
// node.
//
// A kernel update needs a reboot. The agent writes the current boot id to the state before it reboots. After the boot
// a new boot id continues at the verification stage. The same boot id means that the reboot did not happen. The
// update then needs an explicit retry.
//
// The state file records the phase, the error, the package counts and the kernel state. The coordinator polls the
// state to follow the update. The worker sends the package output to the progress log.
//
// The agent removes old kernels after a successful apply. It keeps the running kernel and the newest other kernel.
//
// The sections below follow the update. The state helpers keep the record. Startup recovery handles a reboot. The
// check reports. The worker stages, applies, reboots and verifies. The last sections hold the package commands and
// the kernel helpers.

package nodeagent

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"io"
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

// Constants and types
// =====================================================================================================================
// The constants set the work deadline and the timeout of each command. They also set the minimum free space on the
// root and boot file systems. osState is the record on disk.

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
	nodeState
	OperationUid   string    `json:"operationUid,omitempty"`
	NodeName       string    `json:"nodeName"`
	IpAddress      string    `json:"ipAddress"`
	Role           string    `json:"role"`
	Pending        int       `json:"pending,omitempty"`
	Security       int       `json:"security,omitempty"`
	RebootRequired bool      `json:"rebootRequired,omitempty"`
	KernelRunning  string    `json:"kernelRunning,omitempty"`
	KernelPending  string    `json:"kernelPending,omitempty"`
	CheckedAt      time.Time `json:"checkedAt,omitempty"`
	CheckError     string    `json:"checkError,omitempty"`
	BootId         string    `json:"bootId,omitempty"`
}

// The operating-system state
// =====================================================================================================================
// The state helpers read and write os-state.json. The lock in globals protects each transition. A state from an
// incompatible version is marked as a recovery case. statusFromState converts the file to the API status. The
// conversion adds the progress record and the identity of the node.

func osStatePath() string {
	return filepath.Join(stagingDir, osStateName)
}

func osStateLoad() osState {
	globals.Mu.Lock()
	defer globals.Mu.Unlock()
	return osStateRead()
}

func osStateRead() osState {
	return nodeStateRead(osStatePath(), "operating-system update", osStateSchemaRevision, shared.NodeAgentOsPhaseFailed, func(state *osState) *nodeState {
		return &state.nodeState
	})
}

func osStateStore(state *osState) error {
	return nodeStateStore(osStatePath(), state)
}

func osSetPhase(phase string, mutate func(state *osState)) error {
	globals.Mu.Lock()
	defer globals.Mu.Unlock()

	state := osStateRead()
	if state.NeedsStateRecovery {
		return errors.New("the operating-system update state requires recovery")
	}

	err := nodeStateSetPhase(osStatePath(), &state, &state.nodeState, phase, func() {
		if mutate != nil {
			mutate(&state)
		}
	})
	if err != nil {
		return err
	}
	return nil
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
			Error:              state.Error,
			NeedsRecovery:      true,
			NeedsStateRecovery: true,
			UpdatedAt:          state.UpdatedAt,
		}
	}

	phase := nodeStateDefaultPhase(state.Phase, shared.NodeAgentOsPhaseIdle)
	nodeName := nodeStateDefaultNodeName(state.NodeName, identityConfig)

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
// A reboot is an expected part of an update. When the agent starts and the state says rebooting, the boot id decides.
// A new boot id continues at the verification. The same boot id means that the reboot never happened, and the update
// needs recovery. Every other interrupted phase needs recovery too.

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
			if err := osSetPhase(shared.NodeAgentOsPhaseVerifying, nil); err != nil {
				osFinishFailure(fmt.Sprintf("could not persist the phase verifying after the reboot: %s", err))
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
	err := nodeStateFail(osStatePath(), &state, &state.nodeState, shared.NodeAgentOsPhaseFailed, message)
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
// The check refreshes the package lists and simulates a full upgrade. It does not change packages. It counts the
// packages that would be installed and the security packages among them. It also records the running kernel, the
// pending kernel and the reboot flag. A failed check is recorded in the report and needs no recovery.

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
// The worker runs in a background goroutine. A full update stages the packages first and applies them next. A stage
// command only downloads. An apply command installs packages that are already staged. Each stage writes its phase
// before it does the work. A failure writes the failed phase and the message for the operator.

func runOsUpdate() {
	workerCtx, cancel := context.WithTimeout(context.Background(), osWorkDeadline)
	defer cancel()
	if osStagePackages(workerCtx) {
		osApplyPackages(workerCtx)
	}
}

func runOsStage() {
	workerCtx, cancel := context.WithTimeout(context.Background(), osWorkDeadline)
	defer cancel()
	if !osStagePackages(workerCtx) {
		return
	}
	if err := osSetPhase(shared.NodeAgentOsPhaseStaged, nil); err != nil {
		osFinishFailure(fmt.Sprintf("could not persist the downloaded operating-system update: %s", err))
		return
	}
	progressStage("Packages downloaded. Preparing to move workloads", osProgressStaged)
}

func runOsApply() {
	workerCtx, cancel := context.WithTimeout(context.Background(), osWorkDeadline)
	defer cancel()
	osApplyPackages(workerCtx)
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
		osFinishFailure(state.Error)
		return false
	}

	if err := osSetPhase(shared.NodeAgentOsPhaseStaging, nil); err != nil {
		osFinishFailure(fmt.Sprintf("could not persist the phase staging: %s", err))
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

func osApplyPackages(workerCtx context.Context) {
	serviceName := serviceForRole(identitySnapshot().Role)
	if serviceName == "" {
		osFinishFailure("the node identity is unknown")
		return
	}
	if err := osSetPhase(shared.NodeAgentOsPhaseApplying, nil); err != nil {
		osFinishFailure(fmt.Sprintf("could not persist the phase applying: %s", err))
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
			"Retry the update on this node. Contact support if it fails again",
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
		osFileExists(osRebootRequiredPath)

	if err := osSetPhase(shared.NodeAgentOsPhaseApplying, func(state *osState) {
		state.KernelRunning = kernelRunning
		state.KernelPending = kernelPending
		state.RebootRequired = rebootRequired
	}); err != nil {
		osFinishFailure(fmt.Sprintf("could not persist the kernel state: %s", err))
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
	if err := osSetPhase(shared.NodeAgentOsPhaseVerifying, nil); err != nil {
		osFinishFailure(fmt.Sprintf("could not persist the phase verifying: %s", err))
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

	err := nodeStateFail(osStatePath(), &state, &state.nodeState, shared.NodeAgentOsPhaseFailed, message)
	if err != nil {
		log.Warn("k8s-app node agent: could not persist the failed operating-system update state: %s", err)
	}
}

// Reboot
// =====================================================================================================================
// The agent writes the boot id and the rebooting phase before it asks systemd to reboot. It then waits for the system
// to leave the running state. A command error during shutdown is not a failure. The boot id after the next boot is
// the proof that the reboot happened.

func osRebootForUpdate(ctx context.Context) error {
	bootId := osBootId()
	if bootId == "" {
		return errors.New("the boot id of the node could not be read")
	}

	if err := osSetPhase(shared.NodeAgentOsPhaseRebooting, func(state *osState) {
		state.BootId = bootId
	}); err != nil {
		return fmt.Errorf("could not persist the phase rebooting: %s", err)
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

// User messages
// =====================================================================================================================
// A user message has two parts. The first part states what happened. The second part tells the operator what to do.
// The helper adds a final period to both parts. The dashboard shows the result.

func userMessage(action string, format string, args ...any) string {
	details := fmt.Sprintf(format, args...)
	if !strings.HasSuffix(details, ".") {
		details += "."
	}
	return fmt.Sprintf("%s %s.", details, action)
}

// Commands
// =====================================================================================================================
// The command helpers run apt and other system tools with a timeout. They return the combined output as a string.
// osRunPackageCommand streams the output of apt to the progress log and returns the last line as the error. A reader
// thread prevents the pipe from blocking.

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

func osRunPackageCommand(ctx context.Context, timeout time.Duration, env []string, args ...string) error {
	commandCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	reader, writer := io.Pipe()
	command := exec.CommandContext(commandCtx, "apt-get", args...)
	command.Env = append(os.Environ(), env...)
	command.Env = append(command.Env, "LC_ALL=C")
	command.Stdout = writer
	command.Stderr = writer
	command.WaitDelay = 5 * time.Second
	lastLine := ""
	done := make(chan struct{})
	go func() {
		defer close(done)
		scanner := bufio.NewScanner(reader)
		scanner.Buffer(make([]byte, 4096), 256*1024)
		for scanner.Scan() {
			for _, part := range strings.Split(scanner.Text(), "\r") {
				line := strings.TrimSpace(part)
				if line == "" {
					continue
				}
				line = shared.NodeAgentTruncateLine(line)
				progressText(line)
				lastLine = line
			}
		}
		if scanner.Err() != nil {
			progressText("Some package output could not be displayed")
			_, _ = io.Copy(io.Discard, reader)
		}
	}()
	err := command.Run()
	_ = writer.Close()
	<-done
	_ = reader.Close()
	if err != nil {
		if lastLine == "" {
			lastLine = "the package manager did not report a reason"
		}
		return errors.New(lastLine)
	}
	return nil
}

// System facts
// =====================================================================================================================
// These helpers read the boot id, the running kernel and the free space. The disk check requires free space on the
// root file system and on /boot. A failed read is an error. A missing reboot flag file is not.

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

// Kernels
// =====================================================================================================================
// The kernel helpers read the installed kernel packages. A kernel is newer when its numeric parts compare higher.
// The pending kernel is the newest installed kernel when it is newer than the running kernel. Cleanup keeps the
// running kernel and the newest other kernel.

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
		if !strings.HasPrefix(fields[1], "ii") {
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
