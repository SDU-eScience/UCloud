package recovery

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"strings"
	"sync"
	"time"

	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"

	"ucloud.dk/iapp/k8s/pkg/backup"
	"ucloud.dk/iapp/k8s/pkg/shared"
)

const recoveryDefaultMountPath = "/etc/ucloud-stack"

type app struct {
	mu      sync.Mutex
	session *ucx.Session `ucx:"-"`

	Busy            bool
	AcknowledgeRisk bool

	Stack      *ucxsvc.Stack `ucx:"-"`
	EnvStackId string        `ucx:"-"`
	JobId      string        `ucx:"-"`
	MountPath  string        `ucx:"-"`

	watchStarted bool `ucx:"-"`

	Message      string `ucx:"-"`
	MessageError bool   `ucx:"-"`

	ClusterFound bool                 `ucx:"-"`
	Cluster      shared.ClusterRecord `ucx:"-"`

	RecoveryFound    bool                         `ucx:"-"`
	RecoveryRevision int64                        `ucx:"-"`
	Recovery         shared.ClusterRecoveryRecord `ucx:"-"`

	Backups          []backupEntry `ucx:"-"`
	SelectedBackupId string        `ucx:"-"`
	BackToSelect     bool          `ucx:"-"`
	ReviewBackup     bool          `ucx:"-"`

	CredentialsChecked bool `ucx:"-"`
	CredentialsOk      bool `ucx:"-"`

	Progress            string                `ucx:"-"`
	VerifyResults       []recoveryCheckResult `ucx:"-"`
	VerificationStarted bool                  `ucx:"-"`
	Verifying           bool                  `ucx:"-"`

	RestoreWaitSince time.Time `ucx:"-"`
	StoppedNodes     []string  `ucx:"-"`
}

type backupEntry struct {
	Id              string
	Release         string
	CreatedAt       time.Time
	SizeBytes       int64
	Sha256          string
	CreatedByNode   string
	ServerTokenHash string
	AgentTokenHash  string
}

type recoveryCheckResult struct {
	Name   string
	Status string
	Detail string
	Ok     bool
}

type recoveryLoadResult struct {
	ClusterFound bool
	Cluster      shared.ClusterRecord

	RecoveryFound    bool
	RecoveryRevision int64
	Recovery         shared.ClusterRecoveryRecord

	Backups []backupEntry

	Err error
}

func App() ucx.Application {
	return &app{
		EnvStackId: strings.TrimSpace(os.Getenv("UCLOUD_UCX_STACK_ID")),
		MountPath:  recoveryDefaultMountPath,
	}
}

func (app *app) Mutex() *sync.Mutex     { return &app.mu }
func (app *app) Session() **ucx.Session { return &app.session }

func (app *app) OnInit() {
}

func (app *app) OnMessage(message ucx.Frame) {
}

func (app *app) OnSysHello(payload string) {
	var request orcapi.AppUcxConnectJobProviderRequest
	if err := json.Unmarshal([]byte(payload), &request); err != nil {
		return
	}

	stack, ok := ucxsvc.StackFromJob(app, request.Job)
	if !ok {
		return
	}

	app.Stack = stack
	app.JobId = strings.TrimSpace(request.Job.Id)
	if trimmed := strings.TrimSpace(stack.MountPath); trimmed != "" {
		app.MountPath = trimmed
	}

	go app.refresh()
	go app.startRestoreWatcher()
}

func (app *app) stackId() string {
	if app.Stack != nil {
		if trimmed := strings.TrimSpace(app.Stack.InstanceId); trimmed != "" {
			return trimmed
		}
	}
	return app.EnvStackId
}

func (app *app) refresh() {
	app.mu.Lock()
	session := app.session
	stackId := app.stackId()
	mountPath := app.MountPath
	app.mu.Unlock()

	if session == nil {
		return
	}

	load := recoveryLoad(session, stackId, mountPath)

	app.mu.Lock()
	if load.Err == nil {
		app.applyLoad(load)
		app.verifyIfNeeded()
	}
	app.pushUi()
	app.mu.Unlock()
}

func recoveryLoad(session *ucx.Session, stackId string, mountPath string) recoveryLoadResult {
	result := recoveryLoadResult{}

	entries, backupsErr := recoveryListBackups(mountPath)
	if backupsErr == nil {
		result.Backups = entries
	}

	if stackId == "" {
		result.Err = errors.New("the stack id is not available")
		return result
	}

	client, err := shared.ClusterStateClientNewSession(session, stackId)
	if err != nil {
		result.Err = err
		return result
	}

	snapshot, err := shared.ClusterStateRead(client)
	if err != nil {
		result.Err = fmt.Errorf("could not read the cluster record: %s", err)
		return result
	}
	if snapshot.Found {
		record := snapshot.Record
		shared.ClusterStateRecordRevisionSet(&record, snapshot.Revision)
		result.ClusterFound = true
		result.Cluster = record
	}

	recoverySnapshot, err := shared.ClusterRecoveryRead(client)
	if err != nil {
		result.Err = fmt.Errorf("could not read the recovery state: %s", err)
		return result
	}
	if recoverySnapshot.Found {
		result.RecoveryFound = true
		result.Recovery = recoverySnapshot.Record
		result.RecoveryRevision = recoverySnapshot.Revision
	}

	return result
}

func (app *app) applyLoad(load recoveryLoadResult) {
	if load.Backups != nil {
		app.Backups = load.Backups
	}

	app.ClusterFound = load.ClusterFound
	if load.ClusterFound {
		app.Cluster = load.Cluster
	}

	app.RecoveryFound = load.RecoveryFound
	if load.RecoveryFound {
		app.Recovery = load.Recovery
		app.RecoveryRevision = load.RecoveryRevision
	} else {
		app.Recovery = shared.ClusterRecoveryRecord{}
		app.RecoveryRevision = 0
	}

	app.recomputeSelection()
	if app.RecoveryFound && app.Recovery.Phase == shared.ClusterRecoveryPhaseActive {
		app.ReviewBackup = false
	}
	if !app.RecoveryFound || app.Recovery.Phase != shared.ClusterRecoveryPhaseActive ||
		app.Recovery.Stage != shared.ClusterRecoveryStageRestore {
		app.StoppedNodes = nil
	}
	if app.currentStage() != shared.ClusterRecoveryStageVerify {
		app.VerificationStarted = false
		app.VerifyResults = nil
	}

	if !(app.RecoveryFound && app.Recovery.Phase == shared.ClusterRecoveryPhaseActive &&
		app.Recovery.Stage == shared.ClusterRecoveryStageRecreate) {
		app.BackToSelect = false
	}
}

func (app *app) recomputeSelection() {
	app.CredentialsChecked = false
	app.CredentialsOk = false

	entry, found := app.selectedBackup()
	if !found {
		return
	}

	serverHash, serverErr := recoveryTokenHash(app.MountPath, "server")
	agentHash, agentErr := recoveryTokenHash(app.MountPath, "agent")
	if serverErr != nil || agentErr != nil {
		return
	}

	app.CredentialsChecked = true
	app.CredentialsOk = serverHash == entry.ServerTokenHash && agentHash == entry.AgentTokenHash
}

func (app *app) selectedBackup() (backupEntry, bool) {
	for _, entry := range app.Backups {
		if entry.Id == app.SelectedBackupId {
			return entry, true
		}
	}
	return backupEntry{}, false
}

func (app *app) pushUi() {
	session := *app.Session()
	if session == nil || session.Context().Err() != nil {
		return
	}
	ucx.AppUpdateUi(app)
}

func (app *app) currentStage() string {
	if app.RecoveryFound && app.Recovery.Phase == shared.ClusterRecoveryPhaseActive && app.Recovery.Stage != "" {
		return app.Recovery.Stage
	}
	return shared.ClusterRecoveryStageStop
}

const recoveryWizardId = "recoveryWizard"

const recoveryStageCount = 4

func (app *app) UserInterface() ucx.UiNode {
	top := []ucx.UiNode{}

	if app.Stack == nil {
		top = append(top,
			ucx.Text("Connecting to the cluster…"),
		)

		return ucx.Box().Sx(
			ucx.SxDisplayFlex,
			ucx.SxFlexDirectionColumn,
			ucx.SxFlexGrow(1),
			ucx.SxMinHeight(0),
		).Children(recoveryTopBar(top...))
	}

	top = append(top, app.stageHeader())

	if app.Message != "" {
		if app.MessageError {
			top = append(top, ucx.Warning(app.Message))
		} else {
			top = append(top, ucx.Tip(app.Message))
		}
	}

	content := []ucx.UiNode{recoveryTopBar(top...)}
	content = append(content, app.stageContent()...)

	return ucx.Box().Sx(
		ucx.SxDisplayFlex,
		ucx.SxFlexDirectionColumn,
		ucx.SxFlexGrow(1),
		ucx.SxMinHeight(0),
	).Children(content...)
}

func recoveryTopBar(content ...ucx.UiNode) ucx.UiNode {
	return ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 16}).
		Sx(
			ucx.SxPx(24),
			ucx.SxPt(24),
			ucx.SxPb(4),
			ucx.SxWidthPercent(100),
			ucx.SxMaxWidth(1080),
			ucx.SxAlignSelf("center"),
			ucx.SxBoxSizing("border-box"),
		).
		Children(content...)
}

func (app *app) displayedStage() string {
	if app.currentStage() == shared.ClusterRecoveryStageRestore && len(app.StoppedNodes) > 0 {
		return shared.ClusterRecoveryStageRecreate
	}
	if app.ReviewBackup {
		return shared.ClusterRecoveryStageRecreate
	}
	if app.BackToSelect && app.currentStage() == shared.ClusterRecoveryStageRecreate {
		return shared.ClusterRecoveryStageStop
	}
	if app.RecoveryFound && app.Recovery.Phase == shared.ClusterRecoveryPhaseActive &&
		app.currentStage() == shared.ClusterRecoveryStageStop {
		return shared.ClusterRecoveryStageRecreate
	}
	return app.currentStage()
}

func recoveryStageIndex(stage string) int64 {
	switch stage {
	case shared.ClusterRecoveryStageRecreate:
		return 1
	case shared.ClusterRecoveryStageRestore:
		return 2
	case shared.ClusterRecoveryStageVerify:
		return 3
	default:
		return 0
	}
}

func (app *app) stageHeader() ucx.UiNode {
	steps := []struct {
		key   string
		label string
	}{
		{shared.ClusterRecoveryStageStop, "Select backup"},
		{shared.ClusterRecoveryStageRecreate, "Review recovery"},
		{shared.ClusterRecoveryStageRestore, "Restore cluster"},
		{shared.ClusterRecoveryStageVerify, "Verification"},
	}

	current := app.displayedStage()

	nodes := []ucx.UiNode{}
	for index, step := range steps {
		label := fmt.Sprintf("%d. %s", index+1, step.label)
		text := ucx.Text(label)
		if step.key == current {
			text = text.Sx(ucx.SxColor(ucx.ColorPrimaryMain), ucx.SxFontWeight("600"))
		} else {
			text = text.Sx(ucx.SxColor(ucx.ColorTextSecondary))
		}
		nodes = append(nodes, text)
	}

	return ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 16}).Sx(ucx.SxFlexWrapWrap, ucx.SxMb(8)).Children(nodes...)
}

func (app *app) stageContent() []ucx.UiNode {
	switch app.displayedStage() {
	case shared.ClusterRecoveryStageStop:
		return app.stageStop()
	case shared.ClusterRecoveryStageRecreate:
		return app.stageRecreate()
	case shared.ClusterRecoveryStageRestore:
		return app.stageRestore()
	default:
		return app.stageVerify()
	}
}

func (app *app) stageStop() []ucx.UiNode {
	return app.stageStopSelect()
}

func (app *app) stageStopSelect() []ucx.UiNode {
	content := []ucx.UiNode{
		ucx.Text("Recovery replaces the servers that manage your cluster and restores the cluster from a backup. Server names and IP addresses stay the same. Cluster changes made after the backup will be lost. After the restore, you can check that the cluster responds and its nodes are ready.").Sx(ucx.SxMb(12)),
		ucx.Text("Select a backup below, then review the recovery. Nothing changes until you confirm Start recovery in the next step.").Sx(ucx.SxMb(16)),
	}

	if len(app.Backups) == 0 {
		content = append(content, ucx.Warning("No backups were found on the application drive. Recovery requires a backup.").Sx(ucx.SxMb(16)))
	}

	rows := []ucx.UiNode{}
	for _, entry := range app.Backups {
		entry := entry
		trailing := ucx.Button("recoverySelect-"+entry.Id, "Select", ucx.ColorPrimaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
			if app.SelectedBackupId != entry.Id {
				app.AcknowledgeRisk = false
			}
			app.SelectedBackupId = entry.Id
			app.recomputeSelection()
			app.pushUi()
		})
		if entry.Id == app.SelectedBackupId {
			trailing = ucx.Text("Selected").Sx(ucx.SxColor(ucx.ColorSuccessMain), ucx.SxFontWeight("600"))
		}
		rows = append(rows, backup.UIBackupRow(entry.Id, entry.Release, entry.SizeBytes, entry.CreatedByNode, trailing))
	}
	if len(rows) > 0 {
		content = append(content, ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 12}).Sx(ucx.SxMb(16)).Children(rows...))
	}

	entry, found := app.selectedBackup()
	if found {
		_, releaseKnown := shared.ReleaseByExactVersion(entry.Release)
		if !releaseKnown {
			content = append(content, ucx.Warning("This backup uses an unavailable Kubernetes version. Select another backup.").Sx(ucx.SxMb(16)))
		}

		if !app.CredentialsChecked {
			content = append(content, ucx.Warning("Cluster credentials could not be checked. Recovery cannot continue. Try again later.").Sx(ucx.SxMb(16)))
		} else if !app.CredentialsOk {
			content = append(content, ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 12}).Sx(ucx.SxMb(16)).Children(
				ucx.Warning("The backup credentials differ from the current cluster credentials. Existing nodes may not reconnect after recovery."),
				ucx.Checkbox("recoveryAcknowledge", "I accept that existing nodes may not reconnect", "acknowledgeRisk", true),
			))
		}
	}

	wizard := ucx.TutorialWizard(recoveryWizardId, recoveryStageIndex(shared.ClusterRecoveryStageStop), recoveryStageCount).
		NextLabel("Review recovery").
		NextBusy("busy").
		NextDisabledWhen("busy").
		OnNext(func(ev ucx.UiEvent) {
			if app.Busy {
				return
			}

			entry, found := app.selectedBackup()
			if !found {
				app.Message = "Select a backup before you continue"
				app.MessageError = true
				app.pushUi()
				return
			}

			_, releaseKnown := shared.ReleaseByExactVersion(entry.Release)
			if message, ok := app.beginRecoveryValidation(entry, releaseKnown); !ok {
				app.Message = message
				app.MessageError = true
				app.pushUi()
				return
			}

			app.ReviewBackup = true
			app.Message = ""
			app.MessageError = false
			app.pushUi()
		})

	return []ucx.UiNode{wizard.Children(content...)}
}

func (app *app) stageRecreate() []ucx.UiNode {
	active := app.RecoveryFound && app.Recovery.Phase == shared.ClusterRecoveryPhaseActive
	interrupted := len(app.StoppedNodes) > 0
	canChange := !active || (app.currentStage() == shared.ClusterRecoveryStageRecreate &&
		app.ClusterFound && recoveryCanChangeBackup(app.MountPath, app.Cluster))
	content := []ucx.UiNode{
		recoverySectionTitle("Selected backup"),
		app.recoveryBackupRow(),
		ucx.Warning("Kubernetes changes made after this backup will be lost.").Sx(ucx.SxMb(24)),
		recoverySectionTitle("Control-plane nodes"),
		ucx.Text("Starting recovery stops and replaces these nodes. Their names and IP addresses stay the same.").Sx(ucx.SxColor(ucx.ColorTextSecondary), ucx.SxMb(12)),
	}
	if interrupted {
		content = append([]ucx.UiNode{
			ucx.Warning("Recovery cannot continue because these nodes stopped: " + strings.Join(app.StoppedNodes, ", ") + ". Restart recovery from the selected backup.").Sx(ucx.SxMb(16)),
		}, content...)
	}

	rows := recoveryNodeRows(app.Cluster)
	if len(rows) > 0 {
		content = append(content, ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 12}).Sx(ucx.SxMb(16)).Children(rows...))
	}

	if app.Progress != "" {
		content = append(content, progressLine(app.Progress))
	}

	wizard := ucx.TutorialWizard(recoveryWizardId, recoveryStageIndex(shared.ClusterRecoveryStageRecreate), recoveryStageCount).
		ShowPrevious(canChange).
		PreviousLabel("Change backup").
		OnPrevious(func(ev ucx.UiEvent) {
			if app.Busy {
				return
			}
			app.ReviewBackup = false
			app.BackToSelect = active
			app.Message = ""
			app.MessageError = false
			app.pushUi()
		}).
		NextLabel("Start recovery").
		NextBusy("busy").
		NextDisabledWhen("busy").
		OnNext(func(ev ucx.UiEvent) {
			if app.Busy {
				return
			}
			if interrupted {
				app.restartRecoveryAsync()
				return
			}
			if app.ReviewBackup {
				app.recomputeSelection()
				entry, found := app.selectedBackup()
				_, releaseKnown := shared.ReleaseByExactVersion(entry.Release)
				if !found {
					app.Message = "The selected backup is unavailable. Select another backup."
					app.MessageError = true
					app.pushUi()
					return
				}
				if message, ok := app.beginRecoveryValidation(entry, releaseKnown); !ok {
					app.Message = message
					app.MessageError = true
					app.pushUi()
					return
				}
				app.BackToSelect = false
				app.startRecoveryAsync(entry)
				return
			}
			if app.currentStage() == shared.ClusterRecoveryStageStop {
				entry, found := app.recoveryEntry()
				if !found {
					app.Message = "The recovery backup is unavailable. Restore access to the backup before continuing."
					app.MessageError = true
					app.pushUi()
					return
				}
				app.startRecoveryAsync(entry)
				return
			}
			app.recreateAsync()
		})

	if interrupted {
		wizard = wizard.NextLabel("Restart recovery").NextHoldToConfirm(true).NextColor(ucx.ColorErrorMain)
		if !app.Busy {
			content = append(content, ucx.Text("Press and hold Restart recovery to confirm. This stops any remaining control-plane nodes and restores the backup again.").Sx(ucx.SxColor(ucx.ColorTextSecondary), ucx.SxMt(16)))
		}
	} else if canChange {
		wizard = wizard.NextHoldToConfirm(true).NextColor(ucx.ColorErrorMain)
		if !app.Busy {
			content = append(content, ucx.Text("Press and hold Start recovery to confirm.").Sx(ucx.SxColor(ucx.ColorTextSecondary), ucx.SxMt(16)))
		}
	} else {
		wizard = wizard.NextLabel("Resume recovery")
	}
	if app.Busy {
		wizard = wizard.NextLabel("Recovery in progress").NextHoldToConfirm(false).NextColor(ucx.ColorPrimaryMain)
	}

	return []ucx.UiNode{wizard.Children(content...)}
}

func (app *app) recoveryEntry() (backupEntry, bool) {
	if app.ReviewBackup {
		return app.selectedBackup()
	}
	for _, entry := range app.Backups {
		if entry.Id == app.Recovery.BackupId {
			return entry, true
		}
	}
	return backupEntry{}, false
}

func (app *app) recoveryBackupRow() ucx.UiNode {
	entry, found := app.recoveryEntry()
	if !found {
		entry = backupEntry{Id: app.Recovery.BackupId, Release: app.Recovery.BackupRelease}
	}
	return ucx.Box().Sx(ucx.SxMb(16)).Children(
		backup.UIBackupRow(entry.Id, entry.Release, entry.SizeBytes, entry.CreatedByNode),
	)
}

func recoverySectionTitle(text string) ucx.UiNode {
	return ucx.Text(text).Sx(ucx.SxFontWeight("600"), ucx.SxMb(8))
}

func recoveryNodeRows(record shared.ClusterRecord) []ucx.UiNode {
	rows := []ucx.UiNode{}
	for _, node := range controlPlaneNodes(record) {
		rows = append(rows, ucx.Box().Sx(
			ucx.SxDisplayGrid,
			ucx.SxGridTemplateColumns("minmax(0, 1fr) minmax(0, 1fr)"),
			ucx.SxGap(12),
		).Children(
			ucx.Text(node.Hostname).Sx(ucx.SxFontWeight("600"), ucx.SxWordBreak("break-word")),
			ucx.Text(node.IpAddress).Sx(ucx.SxColor(ucx.ColorTextSecondary), ucx.SxWordBreak("break-word")),
		))
	}
	return rows
}

func (app *app) stageRestore() []ucx.UiNode {
	content := []ucx.UiNode{
		ucx.Text("Restoring the cluster from your backup. This step continues automatically when the cluster responds.").Sx(ucx.SxMb(16)),
	}
	if app.Progress != "" {
		content = append(content, ucx.Box().Sx(ucx.SxMb(16)).Children(progressLine(app.Progress)))
	}

	firstServer := shared.ClusterRecoveryFirstServerAllocation(&app.Cluster)
	firstServerJob := ""
	for _, node := range controlPlaneNodes(app.Cluster) {
		if node.AllocationId == firstServer {
			firstServerJob = node.JobId
		}
	}

	if firstServerJob == "" {
		content = append(content, ucx.Warning("The recovery server is unavailable. Check the replacement nodes before continuing.").Sx(ucx.SxMb(16)))
	} else {
		content = append(content,
			recoverySectionTitle("Recovery logs"),
			ucx.JobLogs("recoveryFirstServerLogs", firstServerJob).Sx(ucx.SxMb(16)),
		)
	}

	if !app.RestoreWaitSince.IsZero() && time.Since(app.RestoreWaitSince) >= recoveryRestoreManualAfter {
		content = append(content,
			ucx.Warning("The cluster has not responded for 15 minutes. Check the recovery logs or run the cluster checks.").Sx(ucx.SxMb(12)),
			ucx.Button("recoveryRestoreSkip", "Continue to cluster checks", ucx.ColorPrimaryMain).
				ButtonBusy("busy").
				ButtonDisabledWhen("busy").
				On(ucx.UiEventClick, func(ev ucx.UiEvent) {
					if app.Busy {
						return
					}
					app.restoreSkipAsync()
				}),
		)
	}

	wizard := ucx.TutorialWizard(recoveryWizardId, recoveryStageIndex(shared.ClusterRecoveryStageRestore), recoveryStageCount).
		ShowNext(false)

	return []ucx.UiNode{wizard.Children(content...)}
}

func (app *app) stageVerify() []ucx.UiNode {
	content := []ucx.UiNode{
		ucx.Text("These checks confirm that the cluster responds and its nodes are ready.").Sx(ucx.SxMb(16)),
	}

	if app.Progress != "" {
		content = append(content, ucx.Box().Sx(ucx.SxMb(16)).Children(progressLine(app.Progress)))
	}

	if len(app.VerifyResults) > 0 {
		rows := []ucx.UiNode{}
		failed := 0
		for _, result := range app.VerifyResults {
			if !result.Ok {
				failed++
				rows = append(rows, recoveryCheckRow(result))
			}
		}
		for _, result := range app.VerifyResults {
			if result.Ok {
				rows = append(rows, recoveryCheckRow(result))
			}
		}
		if failed == 0 {
			content = append(content, ucx.Tip("The cluster is ready. All checks passed. You can close recovery."))
		} else {
			content = append(content, ucx.Warning("Some checks need attention. Resolve the issues below or check the cluster manually before closing recovery."))
		}
		content = append(content, ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 16}).Sx(ucx.SxMt(24), ucx.SxMb(24)).Children(rows...))
	} else if !app.Verifying {
		content = append(content, ucx.Text("No check results are available. Try again before closing recovery.").Sx(ucx.SxMb(16)))
	}
	content = append(content, ucx.Box().Sx(ucx.SxDisplayFlex).Children(
		ucx.Button("recoveryVerifyRun", "Check again", ucx.ColorPrimaryMain).
			ButtonDisabledWhen("busy").
			On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				if app.Busy {
					return
				}
				app.verifyAsync()
			}),
	))

	wizard := ucx.TutorialWizard(recoveryWizardId, recoveryStageIndex(shared.ClusterRecoveryStageVerify), recoveryStageCount).
		NextLabel("Close recovery").
		NextBusy("busy").
		NextDisabledWhen("busy").
		OnNext(func(ev ucx.UiEvent) {
			if app.Busy {
				return
			}
			app.closeAsync()
		})

	return []ucx.UiNode{wizard.Children(content...)}
}

func recoveryCheckRow(result recoveryCheckResult) ucx.UiNode {
	color := ucx.ColorErrorMain
	if result.Ok {
		color = ucx.ColorSuccessMain
	}
	content := []ucx.UiNode{
		ucx.Box().Sx(
			ucx.SxDisplayGrid,
			ucx.SxGridTemplateColumns("minmax(0, 1fr) minmax(0, 1fr)"),
			ucx.SxGap(16),
		).Children(
			ucx.Text(result.Name).Sx(ucx.SxFontWeight("600"), ucx.SxWordBreak("break-word")),
			ucx.Text(result.Status).Sx(ucx.SxColor(color), ucx.SxFontWeight("600"), ucx.SxTextAlignRight),
		),
	}
	if result.Detail != "" {
		content = append(content, ucx.Text(result.Detail).Sx(ucx.SxColor(ucx.ColorTextSecondary), ucx.SxMt(4)))
	}
	return ucx.Box().Children(content...)
}

func progressLine(text string) ucx.UiNode {
	return ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 8}).
		Sx(ucx.SxAlignItemsCenter).
		Children(
			ucx.Spinner(16),
			ucx.Text(text).Sx(ucx.SxColor(ucx.ColorTextSecondary)),
		)
}

func controlPlaneNodes(record shared.ClusterRecord) []shared.ClusterNodeRecord {
	result := []shared.ClusterNodeRecord{}
	for _, node := range record.Nodes {
		if node.Group == shared.GroupControlPlane {
			result = append(result, node)
		}
	}
	for i := 0; i < len(result); i++ {
		for j := i + 1; j < len(result); j++ {
			if result[j].AllocationId < result[i].AllocationId {
				result[i], result[j] = result[j], result[i]
			}
		}
	}
	return result
}

func (app *app) beginRecoveryValidation(entry backupEntry, releaseKnown bool) (string, bool) {
	clusterId := app.stackId()
	if clusterId == "" {
		return "The cluster id is not available yet", false
	}
	if !app.ClusterFound {
		return "The cluster record could not be read", false
	}
	if app.Cluster.Phase != shared.ClusterRecordPhaseCreated && app.Cluster.Phase != shared.ClusterRecordPhaseRecovering {
		return "The cluster is not ready for recovery (state: " + app.Cluster.Phase + ")", false
	}
	if app.RecoveryFound && app.Recovery.Phase == shared.ClusterRecoveryPhaseActive &&
		!app.BackToSelect && app.Recovery.BackupId != "" && app.Recovery.BackupId != entry.Id {
		return "A recovery with a different backup is already active", false
	}
	if app.BackToSelect && !recoveryCanChangeBackup(app.MountPath, app.Cluster) {
		return "The replacement nodes already exist, so the backup cannot change", false
	}
	if !releaseKnown {
		return "The selected backup release is not in the catalog", false
	}
	if !app.CredentialsChecked {
		return "The live cluster tokens could not be read, so the credentials cannot be verified", false
	}
	if !app.CredentialsOk && !app.AcknowledgeRisk {
		return "The backup credentials differ from the live tokens. Acknowledge the mismatch to continue", false
	}
	if app.SelectedBackupId != entry.Id {
		return "The selected backup changed, select it again", false
	}
	return "", true
}

func (app *app) runAsync(work func(session *ucx.Session, progress func(string)) (string, error)) {
	session := *app.Session()
	if session == nil {
		return
	}

	app.Busy = true
	app.Message = ""
	app.MessageError = false
	app.Progress = "Starting"
	if app.Verifying {
		app.Progress = "Checking the cluster"
	}
	app.pushUi()

	stackId := app.stackId()
	mountPath := app.MountPath
	progress := func(text string) {
		app.mu.Lock()
		app.Progress = text
		app.pushUi()
		app.mu.Unlock()
	}

	go func() {
		message, err := work(session, progress)

		load := recoveryLoad(session, stackId, mountPath)

		app.mu.Lock()
		app.Busy = false
		app.Verifying = false
		app.Progress = ""
		if load.Err == nil {
			app.applyLoad(load)
		}
		if err != nil {
			app.Message = err.Error()
			app.MessageError = true
		} else {
			app.Message = message
			app.MessageError = false
		}
		app.verifyIfNeeded()
		app.pushUi()
		app.mu.Unlock()
	}()
}
