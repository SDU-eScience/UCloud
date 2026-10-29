// Introduction
// =====================================================================================================================
// This file serves the maintenance API of the node agent. The coordinator is the only client. It polls the status
// endpoints, submits operations and reads the result. Every endpoint first authenticates the peer with the
// maintenance token. The upgrade, operating-system and log endpoints also check the operation uid against the fence.
//
// The agent accepts a command only when the node is in a state where the command makes sense. It refuses a second
// upgrade while one runs. It refuses an apply before the packages are staged. The checks also compare the state that
// the handler read with the state on disk. A difference means that another command won the race. The agent then
// rejects the command and starts no work.
//
// The endpoints are:
//
// - GET /status reports the state of the Kubernetes upgrade
// - POST /upgrade starts an upgrade
// - POST /retry retries a failed upgrade
// - GET /os/status reports the state of the operating-system update
// - POST /os/update checks, stages, applies or retries an operating-system update
// - POST /backup starts a control-plane backup
// - POST /log appends log lines
// - POST /log/reset resets the node log
//
// The log endpoints live in logs.go. The backup worker lives in upgrade.go.

package nodeagent

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"ucloud.dk/iapp/k8s/pkg/backup"
	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/log"
)

// Response helpers
// =====================================================================================================================
// Every response is JSON. An error response has one field named error. The status helper encodes the agent state.

func writeStatus(writer http.ResponseWriter, statusCode int) {
	identityConfig := identitySnapshot()

	status := statusFromState(identityConfig)

	writer.Header().Set("Content-Type", "application/json")
	writer.WriteHeader(statusCode)
	_ = json.NewEncoder(writer).Encode(status)
}

func writeError(writer http.ResponseWriter, statusCode int, message string) {
	writer.Header().Set("Content-Type", "application/json")
	writer.WriteHeader(statusCode)
	_ = json.NewEncoder(writer).Encode(map[string]string{"error": message})
}

func writeJson(writer http.ResponseWriter, statusCode int, value any) {
	writer.Header().Set("Content-Type", "application/json")
	writer.WriteHeader(statusCode)
	_ = json.NewEncoder(writer).Encode(value)
}

// Status endpoints
// =====================================================================================================================
// The status endpoints read the state without the command mutex. They do not change the state.

func handleStatus(writer http.ResponseWriter, request *http.Request) {
	writeStatus(writer, http.StatusOK)
}

// Upgrade commands
// =====================================================================================================================
// The upgrade endpoint starts a new upgrade. The retry endpoint accepts the target of a failed upgrade again. Both
// share one handler. The handler validates the release, the fence and the current phase before it starts the worker.
// The worker runs in a background goroutine. The handler returns after the new phase is on disk.

func handleUpgrade(writer http.ResponseWriter, request *http.Request) {
	handleUpgradeOrRetry(writer, request, false)
}

func handleRetry(writer http.ResponseWriter, request *http.Request) {
	handleUpgradeOrRetry(writer, request, true)
}

func handleUpgradeOrRetry(writer http.ResponseWriter, request *http.Request, retry bool) {
	release, ok := decodeRelease(writer, request)
	if !ok {
		return
	}

	globals.CommandMu.Lock()
	defer globals.CommandMu.Unlock()

	operationUid, ok := readCommandAuthorization(writer, request)
	if !ok {
		return
	}

	state := stateLoad()
	if state.NeedsStateRecovery {
		writeError(writer, http.StatusInternalServerError, state.Error)
		return
	}

	osActive := shared.NodeAgentOsPhaseActive(osStateLoad().Phase)

	fenceErr := validateFence(operationUid, shared.NodeAgentPhaseActive(state.Phase) || osActive)
	if fenceErr != nil {
		log.Warn("k8s-app node agent: the command was rejected because its ownership could not be verified: %s", fenceErr)
		writeError(writer, http.StatusForbidden, fenceErr.Error())
		return
	}

	if osActive {
		writeError(writer, http.StatusConflict, "an operating-system update is running on the node")
		return
	}

	if globals.BackupActive {
		writeError(writer, http.StatusConflict, "a backup is running on the node")
		return
	}

	if globals.OsCheckActive {
		writeError(writer, http.StatusConflict, "an operating-system update check is running")
		return
	}

	if shared.NodeAgentPhaseActive(state.Phase) {
		if state.Release == release {
			if err := acceptOperation(operationUid); err != nil {
				writeError(writer, http.StatusInternalServerError, err.Error())
				return
			}
			writeStatus(writer, http.StatusOK)
			return
		}
		writeError(writer, http.StatusConflict, fmt.Sprintf(
			"an upgrade to %s is already running. A different target was rejected",
			state.Release,
		))
		return
	}

	if state.Phase == shared.NodeAgentPhaseFailed {
		if state.Release == "" {
			writeError(writer, http.StatusInternalServerError, "the upgrade state is corrupted. Manual recovery is required")
			return
		}
		if !retry {
			writeError(writer, http.StatusConflict, "the failed upgrade requires an explicit retry")
			return
		}
		if state.Release != release {
			writeError(writer, http.StatusConflict, fmt.Sprintf(
				"the failed upgrade targets %s. The retry does not match",
				state.Release,
			))
			return
		}
		startUpgrade(writer, release, state, operationUid)
		return
	}

	if state.Phase == shared.NodeAgentPhaseCompleted {
		if state.Release != release {
			if retry {
				writeError(writer, http.StatusConflict, "there is no failed upgrade to retry")
				return
			}
			startUpgrade(writer, release, state, operationUid)
			return
		}
		if state.PreviousRelease == "" {
			writeError(writer, http.StatusInternalServerError, "the completed upgrade has no recorded previous release. Manual verification is required")
			return
		}
		startUpgrade(writer, release, state, operationUid)
		return
	}

	if state.Release == "" {
		if retry {
			writeError(writer, http.StatusConflict, "there is no failed upgrade to retry")
			return
		}
		startUpgrade(writer, release, state, operationUid)
		return
	}

	writeError(writer, http.StatusInternalServerError, fmt.Sprintf(
		"the upgrade state reports phase %s without a target Kubernetes version. Manual recovery is required",
		state.Phase,
	))
}

func decodeJsonBody[T any](writer http.ResponseWriter, request *http.Request) (T, bool) {
	var parsed T

	body := request.Body
	if request.ContentLength > maxRequestBody {
		writeError(writer, http.StatusRequestEntityTooLarge, "the request body is too large")
		return parsed, false
	}
	body = http.MaxBytesReader(writer, body, maxRequestBody)

	decoder := json.NewDecoder(body)
	decoder.DisallowUnknownFields()
	err := decoder.Decode(&parsed)
	if err != nil {
		writeError(writer, http.StatusBadRequest, fmt.Sprintf("invalid request body: %s", err))
		return parsed, false
	}
	if decoder.Decode(&struct{}{}) != io.EOF {
		writeError(writer, http.StatusBadRequest, "the request body must contain a single JSON object")
		return parsed, false
	}
	return parsed, true
}

type releaseRequest struct {
	Release string `json:"release"`
}

func decodeRelease(writer http.ResponseWriter, request *http.Request) (string, bool) {
	parsed, ok := decodeJsonBody[releaseRequest](writer, request)
	if !ok {
		return "", false
	}
	if parsed.Release == "" {
		writeError(writer, http.StatusBadRequest, "a release is required")
		return "", false
	}
	if _, known := shared.ReleaseByExactVersion(parsed.Release); !known {
		writeError(writer, http.StatusBadRequest, fmt.Sprintf("unknown release: %s", parsed.Release))
		return "", false
	}
	return parsed.Release, true
}

func beginFencedOperation(
	writer http.ResponseWriter,
	operationUid string,
	needsStateRecovery bool,
	stateChanged bool,
	busy bool,
	conflictMessage string,
) bool {
	if needsStateRecovery || stateChanged {
		writeError(writer, http.StatusConflict, conflictMessage)
		return false
	}

	fence, fenceErr := fenceLoad()
	if fenceErr != nil {
		writeError(writer, http.StatusInternalServerError, fmt.Sprintf("could not read the fence state: %s", fenceErr))
		return false
	}
	if busy && fence.LastOperation == "" {
		writeError(writer, http.StatusConflict, "the node has no recorded operation owner, so the command cannot be verified")
		return false
	}
	if fence.LastOperation != "" && fence.LastOperation != operationUid && busy {
		writeError(writer, http.StatusConflict, "the command comes from obsolete node ownership and was rejected")
		return false
	}

	fenceErr = fenceStore(operationUid)
	if fenceErr != nil {
		writeError(writer, http.StatusInternalServerError, fmt.Sprintf("could not persist the fence state: %s", fenceErr))
		return false
	}

	return true
}

func startUpgrade(writer http.ResponseWriter, release string, state upgradeState, operationUid string) {
	current := stateLoad()
	stateChanged := current.Phase != state.Phase || current.Release != state.Release || !current.UpdatedAt.Equal(state.UpdatedAt)
	if !beginFencedOperation(
		writer,
		operationUid,
		current.NeedsStateRecovery,
		stateChanged,
		shared.NodeAgentPhaseActive(current.Phase),
		"the upgrade state changed. Read its status before submitting again",
	) {
		return
	}

	identity := identitySnapshot()
	progressBegin(operationUid)
	previousRelease := ""
	if state.Release == release {
		previousRelease = state.PreviousRelease
	}
	persistErr := stateStore(&upgradeState{
		nodeState: nodeState{
			SchemaRevision: stateSchemaRevision,
			Phase:          shared.NodeAgentPhaseDownloading,
			UpdatedAt:      time.Now().UTC(),
		},
		OperationUid:    operationUid,
		NodeName:        identity.NodeName,
		IpAddress:       identity.IpAddress,
		Role:            identity.Role,
		Release:         release,
		PreviousRelease: previousRelease,
	})
	if persistErr != nil {
		writeError(writer, http.StatusInternalServerError, fmt.Sprintf("could not persist the accepted upgrade: %s", persistErr))
		return
	}

	go runUpgrade(release, state)

	writeStatus(writer, http.StatusAccepted)
}

// Operating-system commands
// =====================================================================================================================
// The operating-system endpoint accepts five commands. A check reports what an update would install and does not
// change packages. A stage downloads the packages. A stage-retry downloads them again after a failure. An apply
// installs the staged packages. A retry starts the update again after a failure.
//
// The handler refuses an apply before a stage has completed. It reports the current status when the command repeats
// an accepted operation. It rejects a command from obsolete ownership.

type osUpdateRequest struct {
	Command string `json:"command"`
}

func writeOsStatus(writer http.ResponseWriter, statusCode int) {
	writeJson(writer, statusCode, osStatusFromState(identitySnapshot()))
}

func handleOsStatus(writer http.ResponseWriter, request *http.Request) {
	writeOsStatus(writer, http.StatusOK)
}

func handleOsUpdate(writer http.ResponseWriter, request *http.Request) {
	parsed, ok := decodeOsUpdate(writer, request)
	if !ok {
		return
	}

	globals.CommandMu.Lock()
	commandLocked := true
	defer func() {
		if commandLocked {
			globals.CommandMu.Unlock()
		}
	}()

	operationUid, ok := readCommandAuthorization(writer, request)
	if !ok {
		return
	}

	state := osStateLoad()
	if state.NeedsStateRecovery {
		if parsed.Command == shared.NodeAgentOsCommandCheck {
			writeJson(writer, http.StatusOK, osStatusFromState(identitySnapshot()))
			return
		}
		writeError(writer, http.StatusInternalServerError, state.Error)
		return
	}

	upgradeActive := shared.NodeAgentPhaseActive(stateLoad().Phase)
	osActive := shared.NodeAgentOsPhaseActive(state.Phase)

	if parsed.Command == shared.NodeAgentOsCommandCheck {
		if upgradeActive {
			writeError(writer, http.StatusConflict, "an upgrade is running on the node")
			return
		}
		if osActive {
			writeError(writer, http.StatusConflict, "an operating-system update is running on the node")
			return
		}
		if globals.OsCheckActive {
			writeError(writer, http.StatusConflict, "an operating-system update check is running")
			return
		}
		if globals.BackupActive {
			writeError(writer, http.StatusConflict, "a backup is running on the node")
			return
		}
	}

	fenceErr := validateFence(operationUid, upgradeActive || osActive)
	if fenceErr != nil {
		log.Warn("k8s-app node agent: the operating-system command was rejected because its ownership could not be verified: %s", fenceErr)
		writeError(writer, http.StatusForbidden, fenceErr.Error())
		return
	}

	if parsed.Command == shared.NodeAgentOsCommandCheck {
		if err := acceptOperation(operationUid); err != nil {
			writeError(writer, http.StatusInternalServerError, err.Error())
			return
		}

		globals.OsCheckActive = true
		globals.CommandMu.Unlock()
		commandLocked = false
		defer func() {
			globals.CommandMu.Lock()
			globals.OsCheckActive = false
			globals.CommandMu.Unlock()
		}()

		controller := http.NewResponseController(writer)
		_ = controller.SetWriteDeadline(time.Now().Add(2*osCheckTimeout + osCommandShortTimeout + writeTimeout))

		status := runOsCheck(request.Context())

		globals.CommandMu.Lock()
		globals.OsCheckActive = false
		globals.CommandMu.Unlock()

		writeJson(writer, http.StatusOK, status)
		return
	}

	if globals.BackupActive {
		writeError(writer, http.StatusConflict, "a backup is running on the node")
		return
	}

	if globals.OsCheckActive {
		writeError(writer, http.StatusConflict, "an operating-system update check is running")
		return
	}

	if upgradeActive {
		writeError(writer, http.StatusConflict, "an upgrade is running on the node")
		return
	}

	if osActive {
		if err := acceptOperation(operationUid); err != nil {
			writeError(writer, http.StatusInternalServerError, err.Error())
			return
		}
		writeOsStatus(writer, http.StatusAccepted)
		return
	}

	staging := parsed.Command == shared.NodeAgentOsCommandStage || parsed.Command == shared.NodeAgentOsCommandStageRetry
	if state.OperationUid == operationUid && (state.Phase == shared.NodeAgentOsPhaseCompleted ||
		(staging && state.Phase == shared.NodeAgentOsPhaseStaged)) {
		writeOsStatus(writer, http.StatusOK)
		return
	}
	if parsed.Command == shared.NodeAgentOsCommandApply && state.Phase == shared.NodeAgentOsPhaseStaged && state.OperationUid == operationUid {
		if err := acceptOperation(operationUid); err != nil {
			writeError(writer, http.StatusInternalServerError, err.Error())
			return
		}
		if err := osSetPhase(shared.NodeAgentOsPhaseApplying, nil); err != nil {
			writeError(writer, http.StatusInternalServerError, fmt.Sprintf("could not persist the accepted operating-system installation: %s", err))
			return
		}
		go runOsApply()
		writeOsStatus(writer, http.StatusAccepted)
		return
	}

	if state.Phase == shared.NodeAgentOsPhaseFailed && parsed.Command != shared.NodeAgentOsCommandRetry && parsed.Command != shared.NodeAgentOsCommandStageRetry {
		writeError(writer, http.StatusConflict, "the failed operating-system update requires an explicit retry")
		return
	}

	startOsUpdate(writer, parsed, state, operationUid)
}

func decodeOsUpdate(writer http.ResponseWriter, request *http.Request) (osUpdateRequest, bool) {
	parsed, ok := decodeJsonBody[osUpdateRequest](writer, request)
	if !ok {
		return osUpdateRequest{}, false
	}

	switch parsed.Command {
	case shared.NodeAgentOsCommandCheck, shared.NodeAgentOsCommandStage, shared.NodeAgentOsCommandStageRetry,
		shared.NodeAgentOsCommandApply, shared.NodeAgentOsCommandRetry:
	default:
		writeError(writer, http.StatusBadRequest, fmt.Sprintf("unknown operating-system command: %s", parsed.Command))
		return osUpdateRequest{}, false
	}

	return parsed, true
}

func startOsUpdate(writer http.ResponseWriter, parsed osUpdateRequest, state osState, operationUid string) {
	current := osStateLoad()
	stateChanged := current.Phase != state.Phase || !current.UpdatedAt.Equal(state.UpdatedAt)
	if !beginFencedOperation(
		writer,
		operationUid,
		current.NeedsStateRecovery,
		stateChanged,
		shared.NodeAgentOsPhaseActive(current.Phase),
		"the operating-system update state changed. Read its status before submitting again",
	) {
		return
	}

	identity := identitySnapshot()
	progressBegin(operationUid)
	persistErr := osStateStore(&osState{
		nodeState: nodeState{
			SchemaRevision: osStateSchemaRevision,
			Phase:          shared.NodeAgentOsPhaseStaging,
			UpdatedAt:      time.Now().UTC(),
		},
		OperationUid:   operationUid,
		NodeName:       identity.NodeName,
		IpAddress:      identity.IpAddress,
		Role:           identity.Role,
		KernelRunning:  current.KernelRunning,
		KernelPending:  current.KernelPending,
		Pending:        current.Pending,
		Security:       current.Security,
		RebootRequired: current.RebootRequired,
		CheckedAt:      current.CheckedAt,
		CheckError:     current.CheckError,
	})
	if persistErr != nil {
		writeError(writer, http.StatusInternalServerError, fmt.Sprintf("could not persist the accepted operating-system update: %s", persistErr))
		return
	}

	if parsed.Command == shared.NodeAgentOsCommandStage || parsed.Command == shared.NodeAgentOsCommandStageRetry {
		go runOsStage()
	} else {
		go runOsUpdate()
	}

	writeOsStatus(writer, http.StatusAccepted)
}

// Backup command
// =====================================================================================================================
// A backup is only available on a control plane node. The handler holds the backup mutex for the duration, so two
// backups cannot run at once. The response waits for the backup to finish.

func handleBackup(writer http.ResponseWriter, request *http.Request) {
	trigger, ok := decodeBackupTrigger(writer, request)
	if !ok {
		return
	}

	attemptUid, ok := readCommandAuthorization(writer, request)
	if !ok {
		return
	}

	controller := http.NewResponseController(writer)
	_ = controller.SetWriteDeadline(time.Now().Add(backupWorkDeadline + writeTimeout))

	globals.CommandMu.Lock()
	if globals.BackupActive {
		globals.CommandMu.Unlock()
		writeError(writer, http.StatusConflict, "a backup is already running")
		return
	}
	if globals.OsCheckActive {
		globals.CommandMu.Unlock()
		writeError(writer, http.StatusConflict, "an operating-system update check is running")
		return
	}

	state := stateLoad()
	if state.NeedsStateRecovery {
		globals.CommandMu.Unlock()
		writeError(writer, http.StatusInternalServerError, "the upgrade state of the node is unreadable")
		return
	}
	if shared.NodeAgentPhaseActive(state.Phase) {
		globals.CommandMu.Unlock()
		writeError(writer, http.StatusConflict, "an upgrade is running on the node")
		return
	}
	if shared.NodeAgentOsPhaseActive(osStateLoad().Phase) {
		globals.CommandMu.Unlock()
		writeError(writer, http.StatusConflict, "an operating-system update is running on the node")
		return
	}

	if !globals.BackupMu.TryLock() {
		globals.CommandMu.Unlock()
		writeError(writer, http.StatusConflict, "a backup is already running")
		return
	}

	globals.BackupActive = true
	globals.CommandMu.Unlock()
	defer func() {
		globals.CommandMu.Lock()
		globals.BackupActive = false
		globals.CommandMu.Unlock()
		globals.BackupMu.Unlock()
	}()

	result, err := runBackup(request.Context(), trigger, attemptUid)
	if err != nil {
		log.Warn("k8s-app node agent: the backup failed: %s", err)
		writeError(writer, http.StatusInternalServerError, err.Error())
		return
	}

	writeJson(writer, http.StatusOK, result)
}

type backupTriggerRequest struct {
	Trigger string `json:"trigger"`
}

func decodeBackupTrigger(writer http.ResponseWriter, request *http.Request) (string, bool) {
	parsed, ok := decodeJsonBody[backupTriggerRequest](writer, request)
	if !ok {
		return "", false
	}
	if parsed.Trigger != backup.TriggerScheduled && parsed.Trigger != backup.TriggerManual {
		writeError(writer, http.StatusBadRequest, fmt.Sprintf("unknown backup trigger: %s", parsed.Trigger))
		return "", false
	}
	return parsed.Trigger, true
}
