package recovery

import (
	"cmp"
	"encoding/json"
	"fmt"
	"os"
	"slices"
	"strings"
	"sync"
	"time"

	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"

	"ucloud.dk/iapp/k8s/pkg/shared"
)

const (
	recoveryDefaultMountPath = "/etc/ucloud-stack"

	recoveryCredentialsUnchecked recoveryCredentialsStatus = ""

	recoveryWizardId = "recoveryWizard"
)

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

	Message         string `ucx:"-"`
	MessageError    bool   `ucx:"-"`
	MessageFromLoad bool   `ucx:"-"`

	recoveryLoadResult `ucx:"-"`

	SelectedBackupId string `ucx:"-"`
	BackToSelect     bool   `ucx:"-"`
	ReviewBackup     bool   `ucx:"-"`

	CredentialsStatus recoveryCredentialsStatus `ucx:"-"`

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

type recoveryCredentialsStatus string

type recoveryLoadResult struct {
	ClusterFound bool
	Cluster      shared.ClusterRecord

	RecoveryFound    bool
	RecoveryRevision int64
	Recovery         shared.ClusterRecoveryRecord

	Backups []backupEntry

	Messages []string
}

func App() ucx.Application {
	return &app{
		EnvStackId: strings.TrimSpace(os.Getenv("UCLOUD_UCX_STACK_ID")),
		MountPath:  recoveryDefaultMountPath,
	}
}

func (app *app) Mutex() *sync.Mutex          { return &app.mu }
func (app *app) Session() **ucx.Session      { return &app.session }
func (app *app) OnMessage(message ucx.Frame) {}

func (app *app) OnInit() {
	shared.RegisterRpcHandlers(app, k8sapi.AppModeRecovery)
	app.registerRpcHandlers()
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
	app.JobId = request.Job.Id
	if stack.MountPath != "" {
		app.MountPath = stack.MountPath
	}

	if ucx.AppHeadless(app) {
		return
	}

	go app.refresh()
	go app.startRestoreWatcher()
}

func (app *app) stackId() string {
	if app.Stack != nil {
		if app.Stack.InstanceId != "" {
			return app.Stack.InstanceId
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
	app.applyLoad(load)
	app.verifyIfNeeded()
	app.clearLoadMessage()
	app.applyLoadMessages(load)
	app.pushUi()
	app.mu.Unlock()
}

func recoveryLoad(session *ucx.Session, stackId string, mountPath string) recoveryLoadResult {
	result := recoveryLoadResult{}

	entries, backupsErr := recoveryListBackups(mountPath)
	if backupsErr != nil {
		result.Messages = append(result.Messages, fmt.Sprintf("Could not list the backups: %s", backupsErr))
	} else {
		result.Backups = entries
	}

	client := shared.ClusterStateClientNewSession(session, stackId)

	snapshot, err := shared.ClusterStateRead(client)
	if err != nil {
		result.Messages = append(result.Messages, fmt.Sprintf("Could not read the cluster record: %s", err))
		return result
	}
	if snapshot.Found {
		result.ClusterFound = true
		result.Cluster = snapshot.Record
	}

	recoverySnapshot, err := shared.ClusterRecoveryRead(client)
	if err != nil {
		result.Messages = append(result.Messages, fmt.Sprintf("Could not read the recovery state: %s", err))
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
	if load.Backups == nil {
		load.Backups = app.Backups
	}
	if !load.ClusterFound {
		load.Cluster = app.Cluster
	}
	if !load.RecoveryFound {
		load.RecoveryFound = app.RecoveryFound
		load.RecoveryRevision = app.RecoveryRevision
		load.Recovery = app.Recovery
	}

	app.recoveryLoadResult = load

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

func (app *app) applyLoadMessages(load recoveryLoadResult) {
	if len(load.Messages) == 0 {
		return
	}

	app.Message = strings.Join(load.Messages, " ")
	app.MessageError = true
	app.MessageFromLoad = true
}

func (app *app) clearLoadMessage() {
	if !app.MessageFromLoad {
		return
	}
	app.Message = ""
	app.MessageError = false
	app.MessageFromLoad = false
}

func (app *app) recomputeSelection() {
	app.CredentialsStatus = recoveryCredentialsUnchecked

	entry, found := app.selectedBackup()
	if !found {
		return
	}

	app.CredentialsStatus = recoveryCheckCredentials(app.MountPath, entry)
}

func recoveryCheckCredentials(mountPath string, entry backupEntry) recoveryCredentialsStatus {
	if entry.ServerTokenHash == "" || entry.AgentTokenHash == "" {
		return recoveryCredentialsUnchecked
	}

	serverHash, serverErr := recoveryTokenHash(mountPath, "server")
	agentHash, agentErr := recoveryTokenHash(mountPath, "agent")
	if serverErr != nil || agentErr != nil {
		return recoveryCredentialsUnchecked
	}

	if serverHash == entry.ServerTokenHash && agentHash == entry.AgentTokenHash {
		return k8sapi.RecoveryCredentialsOk
	}
	return k8sapi.RecoveryCredentialsMismatch
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

var recoveryStages = []struct {
	Key   string
	Label string
}{
	{shared.ClusterRecoveryStageStop, "Select backup"},
	{shared.ClusterRecoveryStageRecreate, "Review recovery"},
	{shared.ClusterRecoveryStageRestore, "Restore cluster"},
	{shared.ClusterRecoveryStageVerify, "Verification"},
}

func recoveryStageCount() int64 {
	return int64(len(recoveryStages))
}

func controlPlaneNodes(record shared.ClusterRecord) []shared.ClusterNodeRecord {
	result := shared.NodesInGroup(record, k8sapi.GroupControlPlane)
	slices.SortFunc(result, func(a, b shared.ClusterNodeRecord) int {
		return cmp.Compare(a.InternalId, b.InternalId)
	})
	return result
}

func (app *app) beginRecoveryValidation(entry backupEntry, releaseKnown bool) (string, bool) {
	clusterId := app.stackId()
	if clusterId == "" {
		return "The cluster name is not available yet", false
	}
	if !app.ClusterFound {
		return "The cluster record could not be read", false
	}
	if !shared.ClusterRecordPhaseAllowsRecovery(app.Cluster.Phase) {
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
		return "The selected backup uses an unavailable Kubernetes version", false
	}
	if app.CredentialsStatus == recoveryCredentialsUnchecked {
		return "The live cluster tokens could not be read, so the credentials cannot be verified", false
	}
	if app.CredentialsStatus == k8sapi.RecoveryCredentialsMismatch && !app.AcknowledgeRisk {
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
	app.MessageFromLoad = false
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
		app.clearLoadMessage()
		app.applyLoad(load)
		if err != nil {
			app.Message = err.Error()
			app.MessageError = true
		} else if message != "" {
			app.Message = message
			app.MessageError = false
		} else {
			app.applyLoadMessages(load)
		}
		app.verifyIfNeeded()
		app.pushUi()
		app.mu.Unlock()
	}()
}
