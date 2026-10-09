package recovery

import (
	"errors"
	"fmt"
	"path/filepath"
	"strings"
	"time"

	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"

	"ucloud.dk/iapp/k8s/pkg/shared"
)

func (app *app) startRecoveryAsync(entry backupEntry) {
	app.runAsync(func(session *ucx.Session, progress func(string)) (string, error) {
		if _, err := app.beginRecoveryWork(session, entry, progress); err != nil {
			return "", err
		}
		if _, err := app.stopWork(session, progress); err != nil {
			return "", err
		}
		return app.recreateWork(session, progress)
	})
}

func (app *app) beginRecoveryWork(session *ucx.Session, entry backupEntry, progress func(string)) (string, error) {
	client, err := recoveryClient(session, app.stackId())
	if err != nil {
		return "", err
	}

	progress("Checking the cluster")
	existing, existingRevision, existingFound, err := recoveryReadRecovery(client)
	if err != nil {
		return "", err
	}
	cluster, err := recoveryReadCluster(client)
	if err != nil {
		return "", err
	}

	if existingFound && existing.Phase == shared.ClusterRecoveryPhaseActive {
		if existing.Stage == shared.ClusterRecoveryStageRecreate && !recoveryCanChangeBackup(app.MountPath, cluster) {
			return "", errors.New("the replacement nodes already exist, so the recovery continues from the recreate step")
		}
		if existing.BackupId != "" && existing.BackupId != entry.Id {
			progress("Updating the selected backup")
			updated := existing
			updated.BackupId = entry.Id
			updated.BackupRelease = entry.Release
			updated.SnapshotSha256 = entry.Sha256
			updated.LastError = ""
			if _, err := shared.ClusterRecoveryRecordWrite(client, &updated, existingRevision); err != nil {
				return "", fmt.Errorf("could not update the selected backup: %s", err)
			}
		}
	}

	if cluster.Phase != shared.ClusterRecordPhaseCreated && cluster.Phase != shared.ClusterRecordPhaseRecovering {
		return "", fmt.Errorf("the cluster is not ready for recovery (state: %s)", cluster.Phase)
	}

	if !(existingFound && existing.Phase == shared.ClusterRecoveryPhaseActive) {
		progress("Preparing recovery")
		record := shared.ClusterRecoveryRecord{
			Phase:          shared.ClusterRecoveryPhaseActive,
			Stage:          shared.ClusterRecoveryStageStop,
			OperationUid:   util.SecureToken(),
			BackupId:       entry.Id,
			BackupRelease:  entry.Release,
			SnapshotSha256: entry.Sha256,
			StartedAt:      time.Now().UTC(),
		}
		if _, err := shared.ClusterRecoveryRecordWrite(client, &record, existingRevision); err != nil {
			return "", fmt.Errorf("could not record the recovery intent: %s", err)
		}
	}

	if cluster.Phase != shared.ClusterRecordPhaseRecovering {
		progress("Preparing the cluster for recovery")
		cluster.Phase = shared.ClusterRecordPhaseRecovering
		if err := shared.ClusterRecordWrite(client, &cluster); err != nil {
			return "", fmt.Errorf("could not mark the cluster as recovering: %s", err)
		}
	}

	return "The recovery intent is recorded.", nil
}

func (app *app) stopWork(session *ucx.Session, progress func(string)) (string, error) {
	client, err := recoveryClient(session, app.stackId())
	if err != nil {
		return "", err
	}

	stack := app.Stack
	if stack == nil || !stack.Ok {
		return "", errors.New("the cluster stack is not available")
	}

	cluster, err := recoveryReadCluster(client)
	if err != nil {
		return "", err
	}
	if cluster.Phase != shared.ClusterRecordPhaseRecovering {
		return "", fmt.Errorf("the cluster is not recovering (state: %s)", cluster.Phase)
	}

	record, _, found, err := recoveryReadRecovery(client)
	if err != nil {
		return "", err
	}
	if !found || record.Phase != shared.ClusterRecoveryPhaseActive {
		return "", errors.New("the recovery intent is not active")
	}

	nodes := controlPlaneNodes(cluster)
	if len(nodes) == 0 {
		return "", errors.New("the cluster record has no control-plane nodes")
	}

	for _, node := range nodes {
		generation := recoveryReadGeneration(app.MountPath, node.AllocationId)
		if err := recoveryWriteMarker(app.MountPath, node.AllocationId, generation); err != nil {
			return "", fmt.Errorf("could not write the decommissioned marker of node %s: %s", node.Hostname, err)
		}
	}

	progress("Stopping the current control-plane nodes")
	for _, node := range nodes {
		if node.JobId == "" {
			continue
		}
		job, retrieveErr := ucxsvc.JobRetrieve(stack, node.JobId)
		if retrieveErr == nil && job.Status.State.IsFinal() {
			continue
		}
		_ = ucxsvc.JobTerminate(stack, node.JobId)
	}

	deadline := time.Now().Add(10 * time.Minute)
	for {
		pending := []string{}
		for _, node := range nodes {
			if node.JobId == "" {
				continue
			}
			job, retrieveErr := ucxsvc.JobRetrieve(stack, node.JobId)
			if retrieveErr != nil || !job.Status.State.IsFinal() {
				pending = append(pending, node.Hostname)
			}
		}
		if len(pending) == 0 {
			break
		}
		if time.Now().After(deadline) {
			return "", fmt.Errorf("the old control-plane jobs did not reach a final state in time: %s", strings.Join(pending, ", "))
		}
		progress("Waiting for " + strings.Join(pending, ", "))
		time.Sleep(5 * time.Second)
	}

	if err := recoverySetStage(client, shared.ClusterRecoveryStageRecreate); err != nil {
		return "", err
	}

	return "", nil
}

func (app *app) recreateAsync() {
	app.runAsync(func(session *ucx.Session, progress func(string)) (string, error) {
		return app.recreateWork(session, progress)
	})
}

func (app *app) restartRecoveryAsync() {
	app.runAsync(func(session *ucx.Session, progress func(string)) (string, error) {
		client, err := recoveryClient(session, app.stackId())
		if err != nil {
			return "", err
		}
		record, _, found, err := recoveryReadRecovery(client)
		if err != nil {
			return "", err
		}
		if !found || record.Phase != shared.ClusterRecoveryPhaseActive || record.Stage != shared.ClusterRecoveryStageRestore {
			return "", errors.New("the recovery state changed. Review the current step before continuing")
		}
		if err := recoverySetStage(client, shared.ClusterRecoveryStageStop); err != nil {
			return "", err
		}
		if _, err := app.stopWork(session, progress); err != nil {
			return "", err
		}
		return app.recreateWork(session, progress)
	})
}

func (app *app) recreateWork(session *ucx.Session, progress func(string)) (string, error) {
	client, err := recoveryClient(session, app.stackId())
	if err != nil {
		return "", err
	}

	stack := app.Stack
	if stack == nil || !stack.Ok {
		return "", errors.New("the cluster stack is not available")
	}

	record, _, found, err := recoveryReadRecovery(client)
	if err != nil {
		return "", err
	}
	if !found || record.Phase != shared.ClusterRecoveryPhaseActive {
		return "", errors.New("the recovery intent is not active")
	}

	cluster, err := recoveryReadCluster(client)
	if err != nil {
		return "", err
	}

	release, known := shared.ReleaseByExactVersion(record.BackupRelease)
	if !known {
		return "", fmt.Errorf("the backup release %s is not in the catalog", record.BackupRelease)
	}

	backupDir := filepath.Join(app.MountPath, "backups", record.BackupId)
	serverToken, err := recoveryReadText(filepath.Join(backupDir, "credentials", "server-token"))
	if err != nil {
		return "", fmt.Errorf("could not read the backup server token: %s", err)
	}
	agentToken, err := recoveryReadText(filepath.Join(backupDir, "credentials", "agent-token"))
	if err != nil {
		return "", fmt.Errorf("could not read the backup agent token: %s", err)
	}

	progress("Preparing replacement nodes")
	customUi := shared.ClusterNodeCustomUi(stack)
	if !stack.Ok {
		return "", errors.New("could not prepare the custom UI service for the replacement nodes")
	}

	firstServer := shared.ClusterRecoveryFirstServerAllocation(&cluster)
	nodes := controlPlaneNodes(cluster)
	for _, node := range nodes {
		if node.JobId != "" {
			job, retrieveErr := ucxsvc.JobRetrieve(stack, node.JobId)
			if retrieveErr == nil && !job.Status.State.IsFinal() {
				progress("Replacement node " + node.Hostname + " is already running")
				continue
			}
		}

		progress("Creating the replacement node " + node.Hostname)
		spec := shared.ClusterRecoveryNodeSpec{
			AllocationId:   node.AllocationId,
			FirstServer:    node.AllocationId == firstServer,
			Release:        release,
			BackupId:       record.BackupId,
			SnapshotSha256: record.SnapshotSha256,
			ServerToken:    serverToken,
			AgentToken:     agentToken,
			CustomUi:       customUi,
			Session:        session,
			Progress:       progress,
		}
		if err := shared.ClusterRecoveryCreateNode(stack, client, &cluster, spec); err != nil {
			_ = recoverySetLastError(client, err.Error())
			return "", fmt.Errorf("could not create the replacement node %s: %s", node.Hostname, err)
		}
	}

	if err := recoverySetStage(client, shared.ClusterRecoveryStageRestore); err != nil {
		return "", err
	}

	return "", nil
}

const recoveryRestorePollInterval = 10 * time.Second

const recoveryRestoreManualAfter = 15 * time.Minute

func (app *app) restoreSkipAsync() {
	app.runAsync(func(session *ucx.Session, progress func(string)) (string, error) {
		client, err := recoveryClient(session, app.stackId())
		if err != nil {
			return "", err
		}
		if err := recoverySetStage(client, shared.ClusterRecoveryStageVerify); err != nil {
			return "", err
		}
		return "", nil
	})
}

func (app *app) startRestoreWatcher() {
	app.mu.Lock()
	if app.watchStarted {
		app.mu.Unlock()
		return
	}
	app.watchStarted = true
	app.mu.Unlock()

	go func() {
		for {
			time.Sleep(recoveryRestorePollInterval)
			app.pollRestore()
		}
	}()
}

func (app *app) pollRestore() {
	app.mu.Lock()
	session := app.session
	stack := app.Stack
	stackId := app.stackId()
	mountPath := app.MountPath
	active := app.RecoveryFound && app.Recovery.Phase == shared.ClusterRecoveryPhaseActive
	stage := app.currentStage()
	busy := app.Busy
	app.mu.Unlock()

	if session == nil || !active || stage != shared.ClusterRecoveryStageRestore || busy {
		app.mu.Lock()
		if !app.RestoreWaitSince.IsZero() {
			app.RestoreWaitSince = time.Time{}
		}
		app.mu.Unlock()
		return
	}

	client, err := recoveryClient(session, stackId)
	if err != nil {
		log.Warn("k8s-app: recovery could not connect to cluster state: %s", err)
		return
	}
	cluster, err := recoveryReadCluster(client)
	if err != nil {
		log.Warn("k8s-app: recovery could not read cluster state: %s", err)
		return
	}
	stopped := []string{}
	for _, node := range controlPlaneNodes(cluster) {
		if node.JobId == "" {
			stopped = append(stopped, node.Hostname)
			continue
		}
		job, err := ucxsvc.JobRetrieve(stack, node.JobId)
		if err != nil {
			log.Warn("k8s-app: recovery could not read node %s: %s", node.Hostname, err)
			return
		}
		if job.Status.State.IsFinal() {
			stopped = append(stopped, node.Hostname)
		}
	}
	app.mu.Lock()
	if app.Busy || app.currentStage() != shared.ClusterRecoveryStageRestore {
		app.mu.Unlock()
		return
	}
	app.Cluster = cluster
	app.StoppedNodes = stopped
	if len(stopped) > 0 {
		app.Progress = ""
		app.RestoreWaitSince = time.Time{}
		app.pushUi()
		app.mu.Unlock()
		return
	}
	app.mu.Unlock()

	if err := recoveryApiProbe(mountPath, cluster); err != nil {
		log.Info("k8s-app: recovery cluster API probe failed, retrying: %s", err)
		app.mu.Lock()
		if app.RestoreWaitSince.IsZero() {
			app.RestoreWaitSince = time.Now()
		}
		app.Progress = "Waiting for the cluster to respond"
		app.pushUi()
		app.mu.Unlock()
		return
	}

	if err := recoverySetStage(client, shared.ClusterRecoveryStageVerify); err != nil {
		log.Warn("k8s-app: recovery could not advance to verification: %s", err)
		return
	}

	app.mu.Lock()
	app.Recovery.Stage = shared.ClusterRecoveryStageVerify
	app.Progress = ""
	app.RestoreWaitSince = time.Time{}
	app.Message = "The cluster responds. Check that its nodes are ready."
	app.MessageError = false
	app.verifyIfNeeded()
	app.pushUi()
	app.mu.Unlock()
}

func (app *app) verifyAsync() {
	app.VerificationStarted = true
	app.Verifying = true
	app.VerifyResults = nil
	app.runAsync(func(session *ucx.Session, progress func(string)) (string, error) {
		return app.verifyWork(session, progress)
	})
}

func (app *app) verifyIfNeeded() {
	if app.Busy || app.VerificationStarted || !app.RecoveryFound ||
		app.Recovery.Phase != shared.ClusterRecoveryPhaseActive || app.currentStage() != shared.ClusterRecoveryStageVerify {
		return
	}
	app.verifyAsync()
}

func (app *app) verifyWork(session *ucx.Session, progress func(string)) (string, error) {
	client, err := recoveryClient(session, app.stackId())
	if err != nil {
		return "", err
	}

	stack := app.Stack
	if stack == nil || !stack.Ok {
		return "", errors.New("the cluster stack is not available")
	}

	cluster, err := recoveryReadCluster(client)
	if err != nil {
		return "", err
	}
	record, _, _, err := recoveryReadRecovery(client)
	if err != nil {
		return "", err
	}

	results := []recoveryCheckResult{}
	progress("Checking control-plane nodes")
	for _, node := range controlPlaneNodes(cluster) {
		if node.JobId == "" {
			results = append(results, recoveryCheckResult{
				Name: node.Hostname, Status: "Not started", Detail: "The replacement node has not started.",
			})
			continue
		}
		job, jobErr := ucxsvc.JobRetrieve(stack, node.JobId)
		if jobErr != nil {
			results = append(results, recoveryCheckResult{
				Name: node.Hostname, Status: "Unavailable", Detail: "Could not check node status. Try again.",
			})
			continue
		}
		if job.Status.State != orcapi.JobStateRunning {
			results = append(results, recoveryCheckResult{
				Name: node.Hostname, Status: string(job.Status.State), Detail: "The replacement node is not running.",
			})
		}
	}

	progress("Checking the restored cluster API")
	reported := map[string]bool{}
	for _, result := range results {
		reported[result.Name] = true
	}
	for _, result := range recoveryApiChecks(app.MountPath, cluster, record) {
		if !reported[result.Name] {
			results = append(results, result)
		}
	}

	app.mu.Lock()
	app.VerifyResults = results
	app.mu.Unlock()

	return "", nil
}

func (app *app) closeAsync() {
	app.runAsync(func(session *ucx.Session, progress func(string)) (string, error) {
		return app.closeWork(session, progress)
	})
}

func (app *app) closeWork(session *ucx.Session, progress func(string)) (string, error) {
	client, err := recoveryClient(session, app.stackId())
	if err != nil {
		return "", err
	}

	progress("Closing recovery")
	snapshot, err := shared.ClusterRecoveryRead(client)
	if err != nil {
		return "", fmt.Errorf("could not read the recovery state: %s", err)
	}
	if snapshot.Found && snapshot.Record.Phase == shared.ClusterRecoveryPhaseActive {
		record := snapshot.Record
		record.Phase = shared.ClusterRecoveryPhaseClosed
		record.ClosedAt = time.Now().UTC()
		if record.Stage == "" {
			record.Stage = shared.ClusterRecoveryStageVerify
		}
		if _, err := shared.ClusterRecoveryRecordWrite(client, &record, snapshot.Revision); err != nil {
			return "", fmt.Errorf("could not close the recovery intent: %s", err)
		}
	}

	progress("Returning the cluster to normal operation")
	cluster, err := recoveryReadCluster(client)
	if err != nil {
		return "", err
	}
	if cluster.Phase != shared.ClusterRecordPhaseCreated {
		cluster.Phase = shared.ClusterRecordPhaseCreated
		if err := shared.ClusterRecordWrite(client, &cluster); err != nil {
			return "", fmt.Errorf("could not set the cluster phase back to created: %s", err)
		}
	}

	progress("Stopping the recovery job")
	stack := app.Stack
	jobId := strings.TrimSpace(app.JobId)
	if stack != nil && stack.Ok && jobId != "" {
		_ = ucxsvc.JobTerminate(stack, jobId)
	}

	return "The recovery is complete. The cluster is back in normal operation.", nil
}

func recoverySetStage(client shared.ClusterStateClient, stage string) error {
	snapshot, err := shared.ClusterRecoveryRead(client)
	if err != nil {
		return fmt.Errorf("could not read the recovery state: %s", err)
	}
	if !snapshot.Found || snapshot.Record.Phase != shared.ClusterRecoveryPhaseActive {
		return errors.New("the recovery intent is not active")
	}

	record := snapshot.Record
	record.Stage = stage
	record.LastError = ""
	if _, err := shared.ClusterRecoveryRecordWrite(client, &record, snapshot.Revision); err != nil {
		return fmt.Errorf("could not update the recovery state: %s", err)
	}
	return nil
}

func recoverySetLastError(client shared.ClusterStateClient, message string) error {
	snapshot, err := shared.ClusterRecoveryRead(client)
	if err != nil {
		return err
	}
	if !snapshot.Found || snapshot.Record.Phase != shared.ClusterRecoveryPhaseActive {
		return nil
	}

	record := snapshot.Record
	record.LastError = message
	_, err = shared.ClusterRecoveryRecordWrite(client, &record, snapshot.Revision)
	return err
}
