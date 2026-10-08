package dashboard

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"strings"
	"time"

	"ucloud.dk/iapp/k8s/pkg/backup"
	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"
)

type dashboardBackupStatus struct {
	StateFound      bool
	State           backup.State
	RequestFound    bool
	RequestRevision int64
	Request         backup.Request
}

func (app *stackUiApp) clusterStateValueClient() (shared.ClusterStateClient, error) {
	session := app.session
	stack := app.Stack
	if session != nil && stack != nil && stack.Ok {
		client, err := shared.ClusterStateClientNewSession(session, stack.InstanceId)
		if err == nil {
			return client, nil
		}
	}
	return shared.ClusterStateClientNewHost()
}

func (app *stackUiApp) backupStatusRead() (dashboardBackupStatus, error) {
	client, err := app.clusterStateValueClient()
	if err != nil {
		return dashboardBackupStatus{}, err
	}

	stateValue, err := shared.ClusterStateValueRead(client, backup.StateKey)
	if err != nil {
		return dashboardBackupStatus{}, fmt.Errorf("could not read the backup state: %s", err)
	}

	result := dashboardBackupStatus{
		StateFound: stateValue.Found && !stateValue.Empty,
	}

	if result.StateFound {
		err = json.Unmarshal(stateValue.Value, &result.State)
		if err != nil {
			return dashboardBackupStatus{}, fmt.Errorf("could not parse the backup state: %s", err)
		}
	}

	requestValue, err := shared.ClusterStateValueRead(client, backup.RequestKey)
	if err != nil {
		return dashboardBackupStatus{}, fmt.Errorf("could not read the backup request: %s", err)
	}

	result.RequestFound = requestValue.Found && !requestValue.Empty
	result.RequestRevision = requestValue.Revision

	if result.RequestFound {
		err = json.Unmarshal(requestValue.Value, &result.Request)
		if err != nil {
			return dashboardBackupStatus{}, fmt.Errorf("could not parse the backup request: %s", err)
		}
	}

	return result, nil
}

func backupRequestActive(request backup.Request) bool {
	return request.Phase == backup.RequestPending || request.Phase == backup.RequestRunning
}

func backupActive(status dashboardBackupStatus) bool {
	if status.StateFound && status.State.ClaimUid != "" {
		return true
	}
	return backupRequestActive(status.Request)
}

func backupRequestSubmit(app *stackUiApp) error {
	status, err := app.backupStatusRead()
	if err != nil {
		return err
	}

	if backupActive(status) {
		return errors.New("a backup is already pending or running")
	}

	client, err := app.clusterStateValueClient()
	if err != nil {
		return err
	}

	request := backup.Request{
		SchemaRevision: backup.SchemaRevision,
		Uid:            backupRequestUid(),
		Requester:      app.backupsRequester,
		RequestedAt:    time.Now().UTC(),
		Phase:          backup.RequestPending,
	}

	value, err := json.Marshal(request)
	if err != nil {
		return err
	}

	_, err = shared.ClusterStateValueWrite(client, backup.RequestKey, value, status.RequestRevision)
	if err == nil {
		return nil
	}

	latest, latestErr := shared.ClusterStateValueRead(client, backup.RequestKey)
	if latestErr == nil && latest.Found && !latest.Empty {
		var current backup.Request
		if json.Unmarshal(latest.Value, &current) == nil && backupRequestActive(current) {
			return errors.New("a backup is already pending or running")
		}
	}

	if shared.IsConflict(err) {
		return errors.New("the backup request changed while submitting; please try again")
	}

	return fmt.Errorf("could not submit the backup request: %s", err)
}

func backupRequestUid() string {
	holder := ""
	if hostname, err := os.Hostname(); err == nil {
		holder = strings.TrimSpace(hostname)
	}
	return fmt.Sprintf("%s-%d-%s", shared.SanitizeForPath(holder), time.Now().UnixNano(), util.SecureToken())
}

func backupSubmitAsync(app *stackUiApp) {
	if app.BackupsBusy || app.BackupsBlocked {
		return
	}

	session := *app.Session()
	if session == nil {
		return
	}

	app.BackupsBusy = true
	ucx.AppUpdateModelPatch(session, map[string]ucx.Value{
		"backupsBusy": ucx.VBool(true),
	})

	go func() {
		err := backupRequestSubmit(app)

		app.mu.Lock()
		app.BackupsBusy = false
		updatedUi := app.UserInterface()
		updatedModel := ucx.AppSnapshot(app)
		app.mu.Unlock()

		if session.Context().Err() == nil {
			ucx.AppUpdateUiLocked(session, updatedUi, updatedModel)
			if err != nil {
				maintenanceSendUiMessage(session, err.Error(), false)
			} else {
				maintenanceSendUiMessage(session, "A backup was requested. It starts within a few seconds.", true)
			}
		}
	}()
}

func (app *stackUiApp) homeBackupsMetric() []ucx.UiNode {
	status, err := app.backupStatusRead()

	link := ucx.LinkButton("homeBackups", "(View)", ucx.ColorLinkColor).Sx(
		ucx.SxFontSize(12),
		ucx.SxFontWeight("400"),
	).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
		ucxsvc.RouterPushPage(app, "backups")
	})

	valueLabel := "Unknown"
	valueColor := ucx.ColorTextSecondary

	if err == nil && status.StateFound {
		now := time.Now().UTC()
		hasSuccess := status.State.LastSuccess.Phase == backup.AttemptSuccess &&
			!status.State.LastSuccess.FinishedAt.IsZero()
		failed := status.State.LastAttempt.Phase == backup.AttemptFailed

		if hasSuccess {
			valueLabel = backupAgeLabel(status.State.LastSuccess.FinishedAt, now)
			valueColor = ucx.ColorTextPrimary
		} else {
			valueLabel = "Never"
		}
		if failed {
			valueLabel = "Failed"
			valueColor = ucx.ColorErrorMain
		}
	} else if err == nil {
		valueLabel = "Not yet"
	}

	return app.homeMetricEx("Backups", []ucx.UiNode{link}, homeMetricText(valueLabel, valueColor))
}

func backupPage(app *stackUiApp) []ucx.UiNode {
	content := []ucx.UiNode{
		ucx.Text("Control-plane backups").Sx(ucx.SxFontSize(20), ucx.SxFontWeight("600")),
		ucx.Text("An hourly etcd backup is written to the cluster state drive. The backups survive the loss of all control-plane VMs.").Sx(
			ucx.SxColor(ucx.ColorTextSecondary),
		),
		ucx.Text("Retention keeps the newest 48 hourly backups and one backup per day for the last 14 days. If the drive or the project is deleted, the backups are deleted with it.").Sx(
			ucx.SxColor(ucx.ColorTextSecondary),
		),
	}

	status, err := app.backupStatusRead()
	if err != nil {
		content = append(content, ucx.Warning(fmt.Sprintf("Could not read the backup state: %s", err)))
		return backupPageShell(app, content)
	}

	now := time.Now().UTC()
	app.BackupsBlocked = backupActive(status)

	if !status.StateFound && !status.RequestFound {
		content = append(content, ucx.Text(
			"No backup has run yet. The first backup starts at the top of the next hour, or you can start one now.",
		))
	}

	content = append(content, backupStatusNodes(app, status, now)...)
	content = append(content, backupRequestNodes(app, status, now)...)
	content = append(content, backupRetainedNodes(status)...)

	return backupPageShell(app, content)
}

func backupPageShell(app *stackUiApp, content []ucx.UiNode) []ucx.UiNode {
	return []ucx.UiNode{app.appShell(appShellProps{
		Content: []ucx.UiNode{shellContentBox(1100,
			ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 16}).Children(content...),
		)},
		EscapePath: "browse/home",
	})}
}

func backupStatusNodes(app *stackUiApp, status dashboardBackupStatus, now time.Time) []ucx.UiNode {
	nodes := []ucx.UiNode{}

	if status.StateFound && status.State.LastAttempt.Phase == backup.AttemptFailed {
		attempt := status.State.LastAttempt
		message := "The last backup failed"
		if !attempt.FinishedAt.IsZero() {
			message += " " + backupAgeLabel(attempt.FinishedAt, now)
		}
		if attempt.NodeName != "" {
			message += " on " + attempt.NodeName
		}
		if attempt.Error != "" {
			message += ": " + attempt.Error
		}
		nodes = append(nodes, ucx.Warning(message))
	}

	for _, warning := range status.State.PruneWarnings {
		nodes = append(nodes, ucx.Warning("Retention warning: "+warning))
	}

	if status.StateFound {
		lastSuccess := "Never"
		if status.State.LastSuccess.Phase == backup.AttemptSuccess && !status.State.LastSuccess.FinishedAt.IsZero() {
			lastSuccess = backupTimestampLabel(status.State.LastSuccess.FinishedAt)
		}

		nextBackup := "-"
		if !status.State.DueAt.IsZero() {
			nextBackup = backupTimestampLabel(status.State.DueAt)
		}

		nodes = append(nodes, app.homeMetricsRow(
			app.homeMetric("Last success", homeMetricText(lastSuccess, ucx.ColorTextPrimary)),
			app.homeMetric("Next backup", homeMetricText(nextBackup, ucx.ColorTextPrimary)),
			app.homeMetric("Retained", homeMetricText(fmt.Sprint(len(status.State.Retained)), ucx.ColorTextPrimary)),
		))
	}

	return nodes
}

func backupRequestNodes(app *stackUiApp, status dashboardBackupStatus, now time.Time) []ucx.UiNode {
	row := []ucx.UiNode{}

	running := status.RequestFound && status.Request.Phase == backup.RequestRunning
	pending := status.RequestFound && status.Request.Phase == backup.RequestPending
	claimActive := status.StateFound && status.State.ClaimUid != ""

	switch {
	case running:
		row = append(row, backupProgressLine("A manual backup is running."))
	case claimActive:
		row = append(row, backupProgressLine("A scheduled backup is running."))
	case pending:
		message := "A backup request is waiting for the coordinator."
		if !status.Request.RequestedAt.IsZero() {
			message = fmt.Sprintf(
				"A backup request is waiting for the coordinator (requested %s).",
				backupAgeLabel(status.Request.RequestedAt, now),
			)
		}
		row = append(row, backupProgressLine(message))
	}

	submit := ucx.SubmitButton("backupSubmit", "Start backup", ucx.ColorPrimaryMain).
		ButtonBusy("backupsBusy").
		ButtonDisabledWhen("backupsBlocked").
		WithTooltip("Starts a backup now. The hourly schedule does not change.").
		Sx(ucx.SxWhiteSpace("nowrap"))

	form := ucx.Form("backupForm").
		Sx(ucx.SxFlexShrink(0), ucx.SxAlignSelf("center")).
		On(ucx.UiEventSubmit, func(ev ucx.UiEvent) {
			if app.BackupsBusy || app.BackupsBlocked {
				return
			}
			backupSubmitAsync(app)
		}).Children(submit)

	row = append(row, ucx.Box().Sx(ucx.SxFlexGrow(1)), form)

	return []ucx.UiNode{ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 12}).
		Sx(ucx.SxAlignItemsCenter).
		Children(row...)}
}

func backupRetainedNodes(status dashboardBackupStatus) []ucx.UiNode {
	nodes := []ucx.UiNode{
		ucx.Text("Retained backups").Sx(ucx.SxFontSize(16), ucx.SxFontWeight("600")),
	}

	if !status.StateFound || len(status.State.Retained) == 0 {
		nodes = append(nodes, ucx.Text("No backups are stored yet.").Sx(ucx.SxColor(ucx.ColorTextSecondary)))
		return nodes
	}

	rows := make([]ucx.UiNode, 0, len(status.State.Retained))
	for _, info := range status.State.Retained {
		rows = append(rows, backupRetainedRow(info))
	}
	nodes = append(nodes, ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 6}).Children(rows...))
	return nodes
}

func backupRetainedRow(info backup.BackupInfo) ucx.UiNode {
	children := []ucx.UiNode{
		ucx.Text(info.Id),
	}

	details := []string{}
	if info.Release != "" {
		details = append(details, info.Release)
	}
	if size := backupSizeLabel(info.SizeBytes); size != "" {
		details = append(details, size)
	}
	if info.CreatedByNode != "" {
		details = append(details, "backed up by "+info.CreatedByNode)
	}
	if len(details) > 0 {
		children = append(children, ucx.Text(strings.Join(details, " — ")).Sx(
			ucx.SxColor(ucx.ColorTextSecondary),
		))
	}

	children = append(children,
		ucx.Box().Sx(ucx.SxFlexGrow(1)),
		ucx.Text("ok").Sx(ucx.SxColor(ucx.ColorSuccessMain)),
	)

	return ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 12}).
		Sx(ucx.SxAlignItemsCenter).
		Children(children...)
}

func backupProgressLine(text string) ucx.UiNode {
	return ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 8}).
		Sx(ucx.SxAlignItemsCenter).
		Children(
			ucx.Spinner(16),
			ucx.Text(text).Sx(ucx.SxColor(ucx.ColorTextSecondary)),
		)
}

func backupTimestampLabel(t time.Time) string {
	return t.UTC().Format("Jan 2, 2006 15:04 UTC")
}

func backupAgeLabel(t time.Time, now time.Time) string {
	delta := now.Sub(t)
	if delta < time.Minute {
		return "just now"
	}
	if delta < time.Hour {
		return fmt.Sprintf("%d min ago", int(delta/time.Minute))
	}
	if delta < 24*time.Hour {
		return fmt.Sprintf("%d h ago", int(delta/time.Hour))
	}
	days := int(delta / (24 * time.Hour))
	if days == 1 {
		return "1 day ago"
	}
	return fmt.Sprintf("%d days ago", days)
}

func backupSizeLabel(sizeBytes int64) string {
	if sizeBytes <= 0 {
		return ""
	}

	const (
		kilo = int64(1000)
		mega = 1000 * kilo
		giga = 1000 * mega
	)

	switch {
	case sizeBytes >= giga:
		return fmt.Sprintf("%.1f GB", float64(sizeBytes)/float64(giga))
	case sizeBytes >= mega:
		return fmt.Sprintf("%.0f MB", float64(sizeBytes)/float64(mega))
	case sizeBytes >= kilo:
		return fmt.Sprintf("%.0f KB", float64(sizeBytes)/float64(kilo))
	default:
		return fmt.Sprintf("%d B", sizeBytes)
	}
}
