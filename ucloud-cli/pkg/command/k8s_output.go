package command

import (
	"fmt"
	"io"
	"os"
	"strings"
	"sync"

	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/ucloud_cli/pkg/iapp"
)

type k8sMessageState struct {
	output   string
	mu       sync.Mutex
	failures []string
	closed   bool
}

func k8sStartMessages(client *iapp.Client, output string) *k8sMessageState {
	messages := &k8sMessageState{output: output}
	client.SetMessageHandler(k8sMessageHandler(messages))
	return messages
}

func k8sStopMessages(client *iapp.Client, messages *k8sMessageState, result error) error {
	client.SetMessageHandler(nil)
	return k8sFinishMessages(messages, result)
}

func k8sStartWaitProgress(
	client *iapp.Client,
	messages *k8sMessageState,
	output io.Writer,
	title string,
) *termio.ProgressList {
	progress := termio.NewProgressList(output, title)
	progress.Start()
	client.SetMessageHandler(k8sWaitMessageHandler(messages, progress))
	return progress
}

func k8sStopWaitProgress(client *iapp.Client, progress *termio.ProgressList) {
	defer progress.Stop()
	client.SetMessageHandler(nil)
}

func k8sStartOperationProgress(
	client *iapp.Client,
	messages *k8sMessageState,
	title string,
) *termio.ProgressList {
	progress := termio.NewProgressList(outputProgressWriter(messages.output == "json"), title)
	progress.Start()
	client.SetMessageHandler(k8sProgressMessageHandler(messages, progress))
	return progress
}

func k8sProgressMessageHandler(
	messages *k8sMessageState,
	progress *termio.ProgressList,
) func(string, bool, string) {
	return func(message string, success bool, level string) {
		if message == "" || k8sCaptureFailure(messages, message, success, level) {
			return
		}
		if level == ucxapi.UiMessageLevelWarning {
			progress.MilestoneWarning(message)
			return
		}
		if level == ucxapi.UiMessageLevelSuccess {
			return
		}
		progress.SetItem(termio.ProgressItem{
			Key:    "operation",
			Status: message,
			State:  termio.ProgressRunning,
		})
	}
}

func k8sStopOperationProgress(client *iapp.Client, progress *termio.ProgressList) {
	client.SetMessageHandler(nil)
	progress.SetItems(nil)
	progress.Stop()
}

func k8sClusterFrame(cluster string) termio.Frame {
	frame := termio.Frame{}
	frame.Title("Cluster " + termio.QuoteName(cluster))
	return frame
}

func k8sSummaryFrame(message string) termio.Frame {
	fmt.Fprintln(os.Stdout, outputSuccessText(os.Stdout, message))
	fmt.Println()
	frame := termio.Frame{}
	frame.Title("Summary")
	return frame
}

func k8sMessageHandler(state *k8sMessageState) func(string, bool, string) {
	return func(message string, success bool, level string) {
		if message == "" || k8sCaptureFailure(state, message, success, level) {
			return
		}
		state.mu.Lock()
		defer state.mu.Unlock()
		if state.closed {
			return
		}
		k8sPrintStatusLine(state.output, level, message)
	}
}

func k8sWaitMessageHandler(state *k8sMessageState, progress *termio.ProgressList) func(string, bool, string) {
	warnings := map[string]bool{}
	return func(message string, success bool, level string) {
		if message == "" || k8sCaptureFailure(state, message, success, level) || level != ucxapi.UiMessageLevelWarning {
			return
		}
		state.mu.Lock()
		defer state.mu.Unlock()
		if state.closed || warnings[message] {
			return
		}
		warnings[message] = true
		progress.MilestoneWarning(message)
	}
}

func k8sCaptureFailure(state *k8sMessageState, message string, success bool, level string) bool {
	if success && level != ucxapi.UiMessageLevelFailure {
		return false
	}

	state.mu.Lock()
	defer state.mu.Unlock()
	if state.closed || message == "" {
		return true
	}
	message = k8sClusterMessage(message)
	for _, previous := range state.failures {
		if previous == message {
			return true
		}
	}
	state.failures = append(state.failures, message)
	return true
}

func k8sFinishMessages(state *k8sMessageState, result error) error {
	state.mu.Lock()
	state.closed = true
	failures := append([]string(nil), state.failures...)
	state.mu.Unlock()

	if result == nil {
		for _, message := range failures {
			k8sPrintStatusLine(state.output, ucxapi.UiMessageLevelFailure, message)
		}
		return nil
	}

	var additional []string
	for _, message := range failures {
		if !strings.Contains(result.Error(), message) {
			additional = append(additional, message)
		}
	}
	if len(additional) == 0 {
		return result
	}
	return fmt.Errorf("%w; %s", result, strings.Join(additional, "; "))
}

func k8sPrintStatusLine(output string, level string, message string) {
	if message == "" {
		return
	}

	icon := "…"
	style := termio.Subtle
	switch level {
	case ucxapi.UiMessageLevelSuccess:
		icon = "✓"
		style = termio.Success
	case ucxapi.UiMessageLevelWarning:
		icon = "!"
		style = termio.Warning
	case ucxapi.UiMessageLevelFailure:
		icon = "✗"
		style = termio.Danger
	}

	writer := outputProgressWriter(output == "json")
	if level == ucxapi.UiMessageLevelFailure {
		writer = os.Stderr
	}
	fmt.Fprintln(writer, style("%s %s", icon, message).Render(termio.ColorEnabled(writer)))
}

func k8sMaintenanceState(phase string) termio.ProgressState {
	switch phase {
	case k8sapi.MaintenancePhaseCompleted:
		return termio.ProgressSuccess
	case k8sapi.MaintenancePhaseFailed,
		k8sapi.MaintenancePhaseBlocked,
		k8sapi.MaintenancePhaseCancelled:
		return termio.ProgressFailure
	case k8sapi.MaintenancePhaseRunning:
		return termio.ProgressRunning
	default:
		return termio.ProgressPending
	}
}

func k8sBackupState(phase string) termio.ProgressState {
	switch phase {
	case k8sapi.BackupRequestCompleted:
		return termio.ProgressSuccess
	case k8sapi.BackupRequestFailed:
		return termio.ProgressFailure
	case k8sapi.BackupRequestRunning:
		return termio.ProgressRunning
	default:
		return termio.ProgressPending
	}
}
