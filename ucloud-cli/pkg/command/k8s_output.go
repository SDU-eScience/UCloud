package command

import (
	"fmt"
	"os"

	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
)

func k8sPrintProgressMessage(message string, success bool, level string) {
	if !success || message == "" {
		return
	}
	k8sPrintStatusLine(level, message)
}

func k8sPrintStatusLine(level string, message string) {
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

	fmt.Fprintln(os.Stderr, style("%s %s", icon, message).Render(termio.ColorEnabled(os.Stderr)))
}

func k8sPrintResultSpacer() {
	if termio.IsPtyWriter(os.Stdout) {
		fmt.Println()
	}
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
