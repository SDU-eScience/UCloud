package maintenance

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"sync"
	"time"

	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/util/version"
	"k8s.io/client-go/kubernetes"

	"ucloud.dk/iapp/k8s/pkg/backup"
	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/log"
)

const (
	runAgentProbeConcurrency = 8
	runOsProbeConcurrency    = 8
	runProbeBudget           = 30 * time.Minute
	runBackupRequesterPrefix = "maintenance run "
	runClaimLease            = 15 * time.Minute
	runClaimRenewInterval    = runClaimLease / 3
)

type runOutcome int

const (
	runProceed runOutcome = iota
	runWait
	runStop
)

type runTaskWaitError struct {
	detail string
}

func (err *runTaskWaitError) Error() string {
	return err.detail
}

type runAgentProbe struct {
	node   shared.ClusterNodeRecord
	status shared.NodeAgentStatus
	err    error
}

func runSweep(ctx context.Context, kubeconfigPath string, client stackClient) error {
	if err := maybeStartScheduledRun(client); err != nil {
		return err
	}

	updateRecord, err := runUpdateRead(client)
	if err != nil {
		return err
	}
	if updateRecord.Found {
		err = advanceRun(ctx, kubeconfigPath, client, updateRecord.Value, updateRecord.Revision)
		if err != nil && !shared.IsConflict(err) {
			return err
		}
	}

	nodeRecords, err := runNodeRecords(client)
	if err != nil {
		return err
	}
	for _, record := range nodeRecords {
		err = advanceRun(ctx, kubeconfigPath, client, record.Value, record.Revision)
		if err != nil && !shared.IsConflict(err) {
			return err
		}
	}

	return nil
}

func maybeStartScheduledRun(client stackClient) error {
	now := time.Now().UTC()

	record, found, err := readClusterRecord()
	if err != nil {
		return err
	}
	if !found || !shared.ClusterRecordPhaseOperational(record.Phase) {
		return nil
	}

	scheduleRecord, err := ScheduleRead(client)
	if err != nil {
		return err
	}
	schedule := scheduleRecord.Value

	policyRecord, err := PolicyRead(client)
	if err != nil {
		return err
	}
	if !policyRecord.Found {
		return nil
	}
	policy := policyRecord.Value
	if !policy.Kubernetes.Enabled && !policy.Os.Enabled {
		return nil
	}

	windowStart, windowEnd, open := WindowOpen(now, policy.Window)
	if !open || schedule.LastWindowStart.Equal(windowStart) {
		return nil
	}

	runSubmitMu.Lock()
	defer runSubmitMu.Unlock()

	admissionUid, admissionErr := runAdmissionAcquire(client)
	if admissionErr != nil {
		if errors.Is(admissionErr, errRunAdmissionBusy) {
			return nil
		}
		return admissionErr
	}
	defer runAdmissionRelease(client, admissionUid)

	updateRecord, err := runUpdateRead(client)
	if err != nil {
		return err
	}
	if updateRecord.Found {
		return nil
	}

	nodeRuns, err := runNodeList(client)
	if err != nil {
		return err
	}
	if len(nodeRuns) > 0 {
		return nil
	}

	run, err := createScheduledRun(client, policy, windowStart, windowEnd)
	if err != nil {
		if shared.IsConflict(err) {
			return nil
		}
		return err
	}
	if err := setLastWindowStart(client, windowStart); err != nil {
		log.Warn("k8s-app maintenance: started run %s but could not record the window: %s", run.Uid, err)
	}
	return nil
}

func setLastWindowStart(client stackClient, windowStart time.Time) error {
	for attempt := 0; attempt < 3; attempt++ {
		record, err := ScheduleRead(client)
		if err != nil {
			return err
		}
		state := record.Value
		state.SchemaRevision = 1
		state.LastWindowStart = windowStart.UTC()
		_, err = ScheduleWrite(client, state, record.Revision)
		if err == nil {
			return nil
		}
		if !shared.IsConflict(err) {
			return err
		}
	}
	return errors.New("the maintenance schedule changed while recording the window")
}

type runLease struct {
	mu              sync.Mutex
	key             string
	claimUid        string
	revision        int64
	cancelRequested bool
	stopOnce        sync.Once
	stopCh          chan struct{}
	doneCh          chan struct{}
}

func newRunLease(run MaintenanceRun, revision int64) *runLease {
	return &runLease{
		key:      runKey(run),
		claimUid: run.ClaimUid,
		revision: revision,
		stopCh:   make(chan struct{}),
		doneCh:   make(chan struct{}),
	}
}

func (lease *runLease) start(client stackClient) {
	go func() {
		defer close(lease.doneCh)
		ticker := time.NewTicker(runClaimRenewInterval)
		defer ticker.Stop()

		for {
			select {
			case <-lease.stopCh:
				return
			case <-ticker.C:
				lease.mu.Lock()
				record, err := stackValueRead[MaintenanceRun](client, lease.key, "maintenance run")
				if err == nil && record.Found && record.Value.ClaimUid == lease.claimUid {
					if record.Value.CancelRequested {
						lease.cancelRequested = true
					}
					record.Value.ClaimDeadline = time.Now().UTC().Add(runClaimLease)
					revision, writeErr := runWrite(client, record.Value, record.Revision)
					if writeErr == nil {
						lease.revision = revision
					}
				}
				lease.mu.Unlock()
			}
		}
	}()
}

func (lease *runLease) stop() {
	lease.stopOnce.Do(func() {
		close(lease.stopCh)
		<-lease.doneCh
	})
}

func (lease *runLease) cancellationRequested(client stackClient) bool {
	lease.mu.Lock()
	if lease.cancelRequested {
		lease.mu.Unlock()
		return true
	}
	lease.mu.Unlock()

	record, err := stackValueRead[MaintenanceRun](client, lease.key, "maintenance run")
	if err != nil || !record.Found || !record.Value.CancelRequested {
		return false
	}

	lease.mu.Lock()
	lease.cancelRequested = true
	lease.mu.Unlock()
	return true
}

func (lease *runLease) write(client stackClient, run MaintenanceRun) error {
	lease.mu.Lock()
	defer lease.mu.Unlock()

	if lease.cancelRequested {
		run.CancelRequested = true
	}

	revision, err := runWrite(client, run, lease.revision)
	if err == nil {
		lease.revision = revision
	}
	return err
}

func (lease *runLease) finish(client stackClient, run MaintenanceRun) error {
	lease.mu.Lock()
	defer lease.mu.Unlock()

	if lease.cancelRequested {
		run.CancelRequested = true
	}

	revision, err := lease.finishLocked(client, run)
	lease.revision = revision
	return err
}

func (lease *runLease) finishLocked(client stackClient, run MaintenanceRun) (int64, error) {
	run.FinishedAt = time.Now().UTC()
	if run.Kind == RunKindUpdate && run.Trigger == TriggerWindow {
		if err := setLastWindowStart(client, run.WindowStart); err != nil {
			log.Warn("k8s-app maintenance: could not record the maintenance window: %s", err)
		}
	}

	value, err := marshalRun(run)
	if err != nil {
		return lease.revision, err
	}

	if err := runHistoryAdd(client, run); err != nil {
		log.Warn("k8s-app maintenance: could not record the run history: %s", err)
	}

	lastRecord, err := runLastRead(client)
	if err != nil {
		return lease.revision, err
	}
	_, err = shared.RpcGrantStateWrite(client, runLastKey, value, lastRecord.Revision)
	if err != nil {
		return lease.revision, err
	}

	err = runDelete(client, run, lease.revision)
	if err != nil && !shared.IsConflict(err) {
		return lease.revision, err
	}
	return lease.revision, nil
}

func advanceRun(ctx context.Context, kubeconfigPath string, client stackClient, run MaintenanceRun, revision int64) error {
	if RunTerminal(run) {
		return finishRun(client, run, revision)
	}
	if (run.Phase == RunBlocked || run.Phase == RunFailed) && !run.CancelRequested {
		return nil
	}

	run, revision, claimed, err := claimRun(client, run, revision)
	if err != nil || !claimed {
		return err
	}
	defer releaseRunClaim(client, run)

	lease := newRunLease(run, revision)
	lease.start(client)
	defer lease.stop()

	if run.StartedAt.IsZero() {
		run.StartedAt = time.Now().UTC()
	}
	if run.CancelRequested {
		return cancelRunExecution(ctx, client, &run, lease)
	}

	switch run.Phase {
	case RunPending, RunPreflight:
		outcome, detail, preflightErr := preflightRun(ctx, kubeconfigPath, client, &run)
		if ctx.Err() != nil {
			return ctx.Err()
		}
		if lease.cancellationRequested(client) {
			run.CancelRequested = true
			return cancelRunExecution(ctx, client, &run, lease)
		}
		if preflightErr != nil {
			runFail(&run, preflightErr.Error())
			break
		}
		switch outcome {
		case runProceed:
			run.Phase = RunRunning
			run.Message = ""
			run.Error = ""
			completed, taskErr := advanceRunTasks(ctx, kubeconfigPath, client, lease, &run)
			if ctx.Err() != nil {
				return ctx.Err()
			}
			if lease.cancellationRequested(client) && !completed {
				run.CancelRequested = true
				return cancelRunExecution(ctx, client, &run, lease)
			}
			if taskErr != nil {
				runFail(&run, taskErr.Error())
			} else if completed {
				run.Phase = RunCompleted
			}
		case runWait:
			run.Message = detail
		case runStop:
			run.Phase = RunFailed
			run.Message = ""
			run.Error = detail
		}

	case RunBackup:
		outcome, detail, backupErr := runBackupEnsure(client, &run)
		if ctx.Err() != nil {
			return ctx.Err()
		}
		if lease.cancellationRequested(client) {
			run.CancelRequested = true
			return cancelRunExecution(ctx, client, &run, lease)
		}
		if backupErr != nil {
			runFail(&run, backupErr.Error())
			break
		}
		switch outcome {
		case runProceed:
			run.Phase = RunPreflight
			run.Message = ""
			run.Error = ""
		case runWait:
			run.Message = detail
		case runStop:
			run.Phase = RunFailed
			run.Message = ""
			run.Error = detail
		}

	case RunRunning:
		completed, taskErr := advanceRunTasks(ctx, kubeconfigPath, client, lease, &run)
		if ctx.Err() != nil {
			return ctx.Err()
		}
		if lease.cancellationRequested(client) && !completed {
			run.CancelRequested = true
			return cancelRunExecution(ctx, client, &run, lease)
		}
		if taskErr != nil {
			runFail(&run, taskErr.Error())
		} else if completed {
			run.Phase = RunCompleted
		}

	case RunBlocked, RunFailed:
		// The run waits for an operator retry or cancel.

	default:
		runFail(&run, fmt.Sprintf("the maintenance run entered the unknown phase %s", run.Phase))
	}

	if ctx.Err() != nil {
		return ctx.Err()
	}

	if run.Phase == RunCompleted || run.Phase == RunCancelled {
		lease.stop()
		return lease.finish(client, run)
	}

	return lease.write(client, run)
}

func claimRun(client stackClient, run MaintenanceRun, revision int64) (MaintenanceRun, int64, bool, error) {
	now := time.Now().UTC()
	if run.ClaimUid != "" && now.Before(run.ClaimDeadline) {
		return run, revision, false, nil
	}

	run.ClaimUid = shared.NewUid()
	run.ClaimDeadline = now.Add(runClaimLease)
	newRevision, err := runWrite(client, run, revision)
	if err != nil {
		if shared.IsConflict(err) {
			return run, revision, false, nil
		}
		return run, revision, false, err
	}
	return run, newRevision, true, nil
}

func releaseRunClaim(client stackClient, run MaintenanceRun) {
	key := runKey(run)

	record, err := stackValueRead[MaintenanceRun](client, key, "maintenance run")
	if err != nil || !record.Found || record.Value.ClaimUid != run.ClaimUid {
		return
	}
	record.Value.ClaimUid = ""
	record.Value.ClaimDeadline = time.Time{}
	_, err = runWrite(client, record.Value, record.Revision)
	if err != nil && !shared.IsConflict(err) {
		log.Warn("k8s-app maintenance: could not release the run claim: %s", err)
	}
}

func finishRun(client stackClient, run MaintenanceRun, revision int64) error {
	run.FinishedAt = time.Now().UTC()
	if run.Kind == RunKindUpdate && run.Trigger == TriggerWindow {
		if err := setLastWindowStart(client, run.WindowStart); err != nil {
			log.Warn("k8s-app maintenance: could not record the maintenance window: %s", err)
		}
	}

	value, err := marshalRun(run)
	if err != nil {
		return err
	}

	if err := runHistoryAdd(client, run); err != nil {
		log.Warn("k8s-app maintenance: could not record the run history: %s", err)
	}

	lastRecord, err := runLastRead(client)
	if err != nil {
		return err
	}
	_, err = shared.RpcGrantStateWrite(client, runLastKey, value, lastRecord.Revision)
	if err != nil {
		return err
	}

	err = runDelete(client, run, revision)
	if err != nil && !shared.IsConflict(err) {
		return err
	}
	return nil
}

func marshalRun(run MaintenanceRun) ([]byte, error) {
	return json.Marshal(run)
}

func cancelRunExecution(
	ctx context.Context,
	client stackClient,
	run *MaintenanceRun,
	lease *runLease,
) error {
	snapshot, err := Snapshot()
	if err != nil {
		return err
	}

	for taskIndex := range run.Tasks {
		task := &run.Tasks[taskIndex]
		taskActive := false
		for itemIndex := range task.Items {
			item := &task.Items[itemIndex]
			if item.Phase != RunPending && item.Phase != RunRunning {
				continue
			}

			operation, found := snapshot[item.NodeName]
			if found && operation.RunUid == run.Uid && PhaseActive(operation.Phase) {
				item.OperationUid = operation.Uid
				cancelErr := cancelActiveOperation(item.NodeName)
				if cancelErr != nil {
					item.Error = cancelErr.Error()
				}
				item.Phase = RunRunning
				taskActive = true
				continue
			}

			item.Phase = RunCancelled
		}
		if task.Phase != RunCompleted {
			if taskActive {
				task.Phase = RunRunning
			} else {
				task.Phase = RunCancelled
			}
		}
	}

	active := false
	for _, task := range run.Tasks {
		for _, item := range task.Items {
			if item.Phase == RunRunning {
				active = true
			}
		}
	}

	if !active {
		run.Phase = RunCancelled
		run.Message = ""
		lease.stop()
		return lease.finish(client, *run)
	}

	run.Message = "cancellation requested. Waiting for the active node operation to stop"
	return lease.write(client, *run)
}

func runFail(run *MaintenanceRun, message string) {
	run.Phase = RunFailed
	run.Message = ""
	run.Error = message
}

func runCheck(run *MaintenanceRun, name string, ok bool, detail string) {
	run.Checks = append(run.Checks, RunCheck{Name: name, Ok: ok, Detail: detail})
}

func runCheckReplace(run *MaintenanceRun, name string, ok bool, detail string) {
	checks := run.Checks[:0]
	for _, check := range run.Checks {
		if check.Name != name {
			checks = append(checks, check)
		}
	}
	run.Checks = append(checks, RunCheck{Name: name, Ok: ok, Detail: detail})
}

func ensureRunTasks(run *MaintenanceRun) {
	if len(run.Tasks) > 0 {
		return
	}
	for _, spec := range run.Effective.Tasks {
		run.Tasks = append(run.Tasks, RunTask{
			Kind:          spec.Kind,
			TargetRelease: spec.TargetRelease,
			Phase:         RunPending,
			Options:       spec.Options,
		})
	}
}

func preflightRun(
	ctx context.Context,
	kubeconfigPath string,
	client stackClient,
	run *MaintenanceRun,
) (runOutcome, string, error) {
	run.Checks = nil

	record, found, err := readClusterRecord()
	if err != nil {
		runCheck(run, "cluster", false, err.Error())
		return runStop, "could not read the cluster record", nil
	}
	if !found {
		runCheck(run, "cluster", false, "The cluster record does not exist.")
		return runStop, "the cluster record does not exist", nil
	}
	if !shared.ClusterRecordPhaseOperational(record.Phase) {
		runCheck(run, "cluster", false, fmt.Sprintf("The cluster is in state %s.", record.Phase))
		return runWait, fmt.Sprintf("the cluster is not ready for maintenance (state: %s)", record.Phase), nil
	}
	runCheck(run, "cluster", true, "")

	recovery, err := recoveryIntentActive()
	if err != nil {
		runCheck(run, "recovery", false, err.Error())
		return runStop, "could not read the recovery state", nil
	}
	if recovery {
		runCheck(run, "recovery", false, "A recovery operation is in progress.")
		return runWait, "a recovery operation is in progress", nil
	}
	runCheck(run, "recovery", true, "")

	ensureRunTasks(run)

	targets, targetErr := runTargetNodes(record, run)
	if targetErr != nil {
		runCheck(run, "nodes", false, targetErr.Error())
		return runStop, targetErr.Error(), nil
	}
	if len(targets) == 0 {
		runCheck(run, "nodes", false, "The maintenance target does not contain any nodes.")
		return runStop, "the maintenance target does not contain any nodes", nil
	}

	snapshot, err := Snapshot()
	if err != nil {
		runCheck(run, "operations", false, err.Error())
		return runStop, "could not read the maintenance state", nil
	}
	for _, node := range targets {
		operation, present := snapshot[node.Hostname]
		if present && PhaseActive(operation.Phase) && operation.RunUid != run.Uid {
			runCheck(run, "operations", false, fmt.Sprintf("Node %s has an active maintenance operation.", node.Hostname))
			return runWait, fmt.Sprintf("node %s has an active maintenance operation", node.Hostname), nil
		}
	}
	runCheck(run, "operations", true, "")

	if run.Kind == RunKindNodeAction {
		return runProceed, "", nil
	}

	clientset, err := newClient(kubeconfigPath)
	if err != nil {
		runCheck(run, "nodes", false, err.Error())
		return runStop, "could not connect to Kubernetes", nil
	}

	live, err := runLiveNodes(ctx, clientset)
	if err != nil {
		runCheck(run, "nodes", false, err.Error())
		return runStop, "could not list the nodes of the cluster", nil
	}

	for _, node := range targets {
		liveNode, known := live[node.Hostname]
		if !known {
			runCheck(run, "nodes", false, fmt.Sprintf("Node %s is missing from Kubernetes.", node.Hostname))
			return runStop, fmt.Sprintf("node %s is missing from Kubernetes", node.Hostname), nil
		}
		if !nodeIsReady(liveNode) {
			runCheck(run, "nodes", false, fmt.Sprintf("Node %s is not ready.", node.Hostname))
			return runWait, fmt.Sprintf("node %s is not ready", node.Hostname), nil
		}
	}
	runCheck(run, "nodes", true, fmt.Sprintf("All %d target nodes are ready.", len(targets)))

	outcome, detail := runQuorumCheck(ctx, clientset, record, run, targets)
	if outcome != runProceed {
		return outcome, detail, nil
	}

	for taskIndex := range run.Tasks {
		task := &run.Tasks[taskIndex]
		if task.Kind != TaskK8sUpgrade {
			continue
		}
		outcome, detail, err = runPreflightKubernetes(ctx, clientset, client, record, live, run, task, targets)
		if err != nil {
			runCheck(run, "upgrade", false, err.Error())
			return runStop, "the Kubernetes preflight failed", nil
		}
		if outcome != runProceed {
			return outcome, detail, nil
		}
	}

	for taskIndex := range run.Tasks {
		task := &run.Tasks[taskIndex]
		if task.Kind != TaskOsUpdate {
			continue
		}
		outcome, detail = runPreflightOs(ctx, run, targets)
		if outcome != runProceed {
			return outcome, detail, nil
		}
	}

	return runProceed, "", nil
}

type runOsProbe struct {
	node   shared.ClusterNodeRecord
	status shared.NodeAgentOsStatus
	err    error
}

func runProbeOsNodes(ctx context.Context, nodes []shared.ClusterNodeRecord) []runOsProbe {
	probeCtx, cancel := context.WithTimeout(ctx, runProbeBudget)
	defer cancel()

	probes := make([]runOsProbe, len(nodes))
	semaphore := make(chan struct{}, runOsProbeConcurrency)
	var waitGroup sync.WaitGroup

	for i := range nodes {
		waitGroup.Add(1)
		go func(index int) {
			defer waitGroup.Done()

			node := nodes[index]
			select {
			case semaphore <- struct{}{}:
			case <-probeCtx.Done():
				probes[index] = runOsProbe{node: node, err: probeCtx.Err()}
				return
			}
			defer func() { <-semaphore }()

			status, err := shared.NodeAgentClientOsCommand(
				probeCtx,
				node.Hostname,
				node.IpAddress,
				shared.NodeAgentOsRequest{Command: shared.NodeAgentOsCommandCheck},
				shared.NewUid(),
			)
			probes[index] = runOsProbe{node: node, status: status, err: err}
		}(i)
	}

	waitGroup.Wait()
	return probes
}

func runPreflightOs(
	ctx context.Context,
	run *MaintenanceRun,
	targets []shared.ClusterNodeRecord,
) (runOutcome, string) {
	probes := runProbeOsNodes(ctx, targets)
	run.osProbes = make(map[string]shared.NodeAgentOsStatus, len(probes))

	for _, probe := range probes {
		if probe.err != nil {
			detail := fmt.Sprintf("The maintenance service on node %s is not responding.", probe.node.Hostname)
			runCheck(run, "os", false, detail)
			return runWait, detail
		}
		if probe.status.NeedsStateRecovery {
			detail := fmt.Sprintf("The operating-system update on node %s requires recovery.", probe.node.Hostname)
			runCheck(run, "os", false, detail)
			return runStop, detail
		}
		if probe.status.NodeName != probe.node.Hostname {
			detail := fmt.Sprintf(
				"The maintenance service at %s reports node %s, but %s was requested.",
				probe.node.IpAddress,
				probe.status.NodeName,
				probe.node.Hostname,
			)
			runCheck(run, "os", false, detail)
			return runStop, detail
		}
		if probe.status.CheckError != "" {
			detail := fmt.Sprintf(
				"The operating-system update check on node %s failed: %s.",
				probe.node.Hostname,
				probe.status.CheckError,
			)
			runCheck(run, "os", false, detail)
			return runStop, detail
		}
		run.osProbes[probe.node.Hostname] = probe.status
	}
	runCheck(run, "os", true, fmt.Sprintf("All %d target nodes were checked for updates.", len(targets)))
	return runProceed, ""
}

func runQuorumCheck(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	record shared.ClusterRecord,
	run *MaintenanceRun,
	targets []shared.ClusterNodeRecord,
) (runOutcome, string) {
	controlPlanes := controlPlaneNames(record)
	targetsControlPlane := false
	for _, node := range targets {
		if controlPlanes[node.Hostname] {
			targetsControlPlane = true
			break
		}
	}
	if !targetsControlPlane {
		runCheck(run, "quorum", true, "")
		return runProceed, ""
	}
	if len(controlPlanes) == 2 {
		detail := "A cluster with two control-plane nodes cannot preserve the etcd quorum during an update. Add additional control-plane nodes first."
		runCheck(run, "quorum", false, detail)
		return runStop, detail
	}
	if len(controlPlanes) > 2 {
		required := len(controlPlanes)/2 + 1
		busy := map[string]bool{}
		for _, node := range targets {
			if controlPlanes[node.Hostname] {
				busy[node.Hostname] = true
				break
			}
		}
		remaining, err := controlPlaneRemainingCount(ctx, clientset, controlPlanes, "", busy)
		if err != nil {
			detail := fmt.Sprintf("Could not count the control-plane nodes: %s.", err)
			runCheck(run, "quorum", false, detail)
			return runStop, detail
		}
		if remaining < required {
			detail := fmt.Sprintf(
				"At least %d healthy, schedulable control-plane nodes must remain during the update, but only %d were found.",
				required,
				remaining,
			)
			runCheck(run, "quorum", false, detail)
			return runStop, detail
		}
	}
	runCheck(run, "quorum", true, "")
	return runProceed, ""
}

func runPreflightKubernetes(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	client stackClient,
	record shared.ClusterRecord,
	live map[string]*corev1.Node,
	run *MaintenanceRun,
	task *RunTask,
	targets []shared.ClusterNodeRecord,
) (runOutcome, string, error) {
	now := time.Now().UTC()
	versionsByNode := map[string]string{}
	targetSet := map[string]bool{}

	for _, node := range targets {
		liveNode, known := live[node.Hostname]
		if !known {
			detail := fmt.Sprintf("Node %s is missing from Kubernetes.", node.Hostname)
			runCheck(run, "versions", false, detail)
			return runStop, detail, nil
		}
		current := liveNode.Status.NodeInfo.KubeletVersion
		if _, err := version.ParseSemantic(current); err != nil {
			detail := fmt.Sprintf("The version %s of node %s could not be parsed.", current, node.Hostname)
			runCheck(run, "versions", false, detail)
			return runStop, detail, nil
		}
		versionsByNode[node.Hostname] = current
		targetSet[node.Hostname] = true
	}

	targetRelease := task.TargetRelease
	if targetRelease == "" {
		if len(targets) == 1 {
			target, ok := shared.RecommendedTarget(versionsByNode[targets[0].Hostname], now)
			if !ok {
				guidance := shared.ClusterUpgradePathGuidance(versionsByNode, now)
				detail := "No supported upgrade is available for this node."
				if len(guidance) > 0 {
					detail = guidance[0]
				}
				runCheck(run, "path", false, detail)
				return runStop, detail, nil
			}
			targetRelease = target.Release
		} else {
			target, problem := runRecommendedClusterTarget(versionsByNode, now)
			if problem != "" {
				runCheck(run, "path", false, problem)
				return runStop, problem, nil
			}
			targetRelease = target
		}
		task.TargetRelease = targetRelease
	}

	if _, known := shared.ReleaseByExactVersion(targetRelease); !known {
		detail := fmt.Sprintf("The Kubernetes version %s is not known.", targetRelease)
		runCheck(run, "path", false, detail)
		return runStop, detail, nil
	}

	needsUpgrade := []shared.ClusterNodeRecord{}
	singleNode := len(targets) == 1
	for _, node := range targets {
		current := versionsByNode[node.Hostname]
		if shared.SameRelease(current, targetRelease) {
			continue
		}
		if !shared.NodeAgentUpgradeAllowed(current, targetRelease) {
			detail := fmt.Sprintf("Node %s cannot upgrade from %s to %s.", node.Hostname, current, targetRelease)
			runCheck(run, "path", false, detail)
			return runStop, detail, nil
		}
		if !singleNode {
			next, ok := shared.RecommendedTarget(current, now)
			if !ok {
				detail := fmt.Sprintf("Node %s has no supported upgrade path.", node.Hostname)
				runCheck(run, "path", false, detail)
				return runStop, detail, nil
			}
			if !shared.SameRelease(next.Release, targetRelease) {
				detail := fmt.Sprintf(
					"Node %s should upgrade to %s, but the run targets %s.",
					node.Hostname,
					next.Release,
					targetRelease,
				)
				runCheck(run, "path", false, detail)
				return runStop, detail, nil
			}
		}
		needsUpgrade = append(needsUpgrade, node)
	}

	projectedControlPlanes := []*version.Version{}
	projectedWorkers := []*version.Version{}
	for _, node := range record.Nodes {
		liveNode, known := live[node.Hostname]
		if !known {
			if targetSet[node.Hostname] {
				detail := fmt.Sprintf("Node %s is missing from Kubernetes.", node.Hostname)
				runCheck(run, "versions", false, detail)
				return runStop, detail, nil
			}
			continue
		}

		resulting := liveNode.Status.NodeInfo.KubeletVersion
		if targetSet[node.Hostname] {
			resulting = targetRelease
		}
		parsed, err := version.ParseSemantic(resulting)
		if err != nil {
			detail := fmt.Sprintf("The version %s of node %s could not be parsed.", resulting, node.Hostname)
			runCheck(run, "versions", false, detail)
			return runStop, detail, nil
		}
		if node.Group == shared.GroupControlPlane {
			projectedControlPlanes = append(projectedControlPlanes, parsed)
		} else {
			projectedWorkers = append(projectedWorkers, parsed)
		}
	}

	if err := upgradeCheckControlPlaneSpread(projectedControlPlanes); err != nil {
		runCheck(run, "versions", false, err.Error())
		return runStop, err.Error(), nil
	}
	if err := upgradeCheckWorkerSkew(projectedWorkers, projectedControlPlanes); err != nil {
		runCheck(run, "versions", false, err.Error())
		return runStop, err.Error(), nil
	}
	runCheck(run, "versions", true, "")

	if len(needsUpgrade) == 0 {
		runCheck(run, "path", true, fmt.Sprintf("All target nodes already run Kubernetes %s.", targetRelease))
		runCheck(run, "agents", true, "")
		return runProceed, "", nil
	}

	probes := runProbeAgents(ctx, needsUpgrade)
	for _, probe := range probes {
		if probe.err != nil {
			detail := fmt.Sprintf("Node %s is not responding to maintenance requests.", probe.node.Hostname)
			runCheck(run, "agents", false, detail)
			return runWait, detail, nil
		}
		if probe.status.NodeName != probe.node.Hostname {
			detail := fmt.Sprintf(
				"The maintenance service at %s reports node %s, but %s was requested.",
				probe.node.IpAddress,
				probe.status.NodeName,
				probe.node.Hostname,
			)
			runCheck(run, "agents", false, detail)
			return runStop, detail, nil
		}
		if shared.NodeAgentPhaseActive(probe.status.Phase) {
			detail := fmt.Sprintf("Node %s is busy with another maintenance operation.", probe.node.Hostname)
			runCheck(run, "agents", false, detail)
			return runWait, detail, nil
		}
		if probe.status.Phase == shared.NodeAgentPhaseFailed && !shared.SameRelease(probe.status.Release, targetRelease) {
			detail := fmt.Sprintf("An upgrade to %s failed on node %s. The target does not match.", probe.status.Release, probe.node.Hostname)
			runCheck(run, "agents", false, detail)
			return runStop, detail, nil
		}
	}
	runCheck(run, "agents", true, fmt.Sprintf("%d nodes are ready for the upgrade.", len(needsUpgrade)))
	runCheck(run, "path", true, fmt.Sprintf("Nodes needing an upgrade to %s: %d of %d.", targetRelease, len(needsUpgrade), len(targets)))

	outcome, detail, err := runBackupEnsure(client, run)
	if err != nil {
		return runStop, "the backup could not be checked", err
	}
	if outcome != runProceed {
		return outcome, detail, nil
	}
	return runProceed, "", nil
}

func runRecommendedClusterTarget(versions map[string]string, now time.Time) (string, string) {
	values := make([]string, 0, len(versions))
	for _, value := range versions {
		values = append(values, value)
	}
	target, ok := shared.RecommendedClusterTarget(values, now)
	if !ok {
		guidance := shared.ClusterUpgradePathGuidance(versions, now)
		if len(guidance) > 0 {
			return "", guidance[0]
		}
		return "", "No supported upgrade is available for every node. Check node maintenance or contact your service provider."
	}
	return target.Release, ""
}

func runProbeAgents(ctx context.Context, nodes []shared.ClusterNodeRecord) []runAgentProbe {
	probeCtx, cancel := context.WithTimeout(ctx, runProbeBudget)
	defer cancel()

	probes := make([]runAgentProbe, len(nodes))
	semaphore := make(chan struct{}, runAgentProbeConcurrency)
	var waitGroup sync.WaitGroup

	for i := range nodes {
		waitGroup.Add(1)
		go func(index int) {
			defer waitGroup.Done()

			node := nodes[index]
			select {
			case semaphore <- struct{}{}:
			case <-probeCtx.Done():
				probes[index] = runAgentProbe{node: node, err: probeCtx.Err()}
				return
			}
			defer func() { <-semaphore }()

			status, err := shared.NodeAgentClientStatus(probeCtx, node.Hostname, node.IpAddress)
			probes[index] = runAgentProbe{node: node, status: status, err: err}
		}(i)
	}

	waitGroup.Wait()
	return probes
}

func runLiveNodes(ctx context.Context, clientset *kubernetes.Clientset) (map[string]*corev1.Node, error) {
	listed, err := clientset.CoreV1().Nodes().List(ctx, metav1.ListOptions{})
	if err != nil {
		return nil, err
	}
	result := make(map[string]*corev1.Node, len(listed.Items))
	for i := range listed.Items {
		result[listed.Items[i].Name] = &listed.Items[i]
	}
	return result, nil
}

func runTargetNodes(record shared.ClusterRecord, run *MaintenanceRun) ([]shared.ClusterNodeRecord, error) {
	strict := true
	if run.Kind == RunKindNodeAction &&
		len(run.Effective.Tasks) > 0 &&
		run.Effective.Tasks[0].Kind == TaskRemove {
		strict = false
	}
	return targetNodes(record, run.Effective, strict)
}

func runBackupEnsure(client stackClient, run *MaintenanceRun) (runOutcome, string, error) {
	if run.StartedAt.IsZero() {
		run.StartedAt = time.Now().UTC()
	}

	maxAge := run.Effective.MaxBackupAge
	if maxAge <= 0 {
		maxAge = defaultMaxBackupAge
	}

	stateRecord, err := backupStateRead(client)
	if err != nil {
		return runStop, "could not read the backup state", err
	}
	requestRecord, err := backupRequestRead(client)
	if err != nil {
		return runStop, "could not read the backup request", err
	}

	now := time.Now().UTC()
	state := stateRecord.Value
	request := requestRecord.Value

	if run.BackupConfirmed {
		runCheckReplace(run, "backup", true, "A backup from this run is available.")
		if run.Phase == RunBackup {
			run.Phase = RunPreflight
		}
		return runProceed, "", nil
	}

	scheduledFresh := run.Trigger == TriggerWindow &&
		stateRecord.Found &&
		state.LastSuccess.Phase == backup.AttemptSuccess &&
		!state.LastSuccess.FinishedAt.IsZero() &&
		now.Sub(state.LastSuccess.FinishedAt) <= maxAge
	if scheduledFresh {
		run.BackupConfirmed = true
		runCheckReplace(run, "backup", true, "A recent backup is available.")
		if run.Phase == RunBackup {
			run.Phase = RunPreflight
		}
		return runProceed, "", nil
	}

	ownByUid := run.BackupRequestUid != "" && requestRecord.Found && request.Uid == run.BackupRequestUid
	ownRequest := ownByUid || (run.BackupRequestUid == "" &&
		requestRecord.Found &&
		request.Requester == runBackupRequester(run) &&
		request.Phase != backup.RequestFailed)
	if run.BackupRequestUid != "" && !ownByUid {
		run.BackupRequestUid = ""
	}

	if ownRequest {
		if run.BackupRequestUid == "" {
			run.BackupRequestUid = request.Uid
		}
		switch request.Phase {
		case backup.RequestPending, backup.RequestRunning:
			detail := "Waiting for the requested backup to finish."
			runCheckReplace(run, "backup", false, detail)
			run.Phase = RunBackup
			return runWait, detail, nil

		case backup.RequestCompleted:
			run.BackupConfirmed = true
			runCheckReplace(run, "backup", true, "The requested backup is available.")
			if run.Phase == RunBackup {
				run.Phase = RunPreflight
			}
			return runProceed, "", nil

		case backup.RequestFailed:
			message := request.Error
			if message == "" {
				message = "no reason was recorded"
			}
			detail := fmt.Sprintf("The backup failed: %s.", message)
			runCheckReplace(run, "backup", false, detail)
			return runStop, detail, nil
		}
	}

	if requestRecord.Found && request.Uid != "" &&
		(request.Phase == backup.RequestPending || request.Phase == backup.RequestRunning) {
		detail := "Waiting for another backup request to finish before requesting a new backup."
		runCheckReplace(run, "backup", false, detail)
		run.Phase = RunBackup
		return runWait, detail, nil
	}

	if stateRecord.Found && state.ClaimUid != "" && now.Before(state.ClaimDeadline) {
		detail := "Waiting for a running backup to finish."
		runCheckReplace(run, "backup", false, detail)
		run.Phase = RunBackup
		return runWait, detail, nil
	}

	newRequest := backup.Request{
		SchemaRevision: backup.SchemaRevision,
		Uid:            shared.NewUid(),
		Requester:      runBackupRequester(run),
		RequestedAt:    now,
		Phase:          backup.RequestPending,
	}
	_, writeErr := backupRequestWrite(client, newRequest, requestRecord.Revision)
	if writeErr != nil {
		if shared.IsConflict(writeErr) {
			detail := "The backup state changed while requesting a backup. Retrying."
			runCheckReplace(run, "backup", false, detail)
			run.Phase = RunBackup
			return runWait, detail, nil
		}
		return runStop, "could not request a backup", writeErr
	}

	run.BackupRequestUid = newRequest.Uid
	detail := "A backup was requested. Waiting for it to finish."
	runCheckReplace(run, "backup", false, detail)
	run.Phase = RunBackup
	return runWait, detail, nil
}

func runBackupRequester(run *MaintenanceRun) string {
	return runBackupRequesterPrefix + run.Uid
}

func advanceRunTasks(
	ctx context.Context,
	kubeconfigPath string,
	client stackClient,
	lease *runLease,
	run *MaintenanceRun,
) (bool, error) {
	ensureRunTasks(run)

	for taskIndex := range run.Tasks {
		task := &run.Tasks[taskIndex]
		if task.Phase == RunCompleted {
			continue
		}
		if lease.cancellationRequested(client) {
			run.CancelRequested = true
			return false, nil
		}
		if task.Phase == RunFailed || task.Phase == RunBlocked {
			run.Phase = task.Phase
			run.Message = ""
			if run.Error == "" {
				run.Error = "a maintenance task stopped"
			}
			return false, nil
		}
		if task.Phase == RunCancelled {
			if run.CancelRequested {
				return false, nil
			}
			runFail(run, "a maintenance task was cancelled outside of the maintenance run")
			return false, nil
		}

		if task.Phase == RunPending {
			if err := startRunTask(ctx, kubeconfigPath, run, task); err != nil {
				var waitErr *runTaskWaitError
				if errors.As(err, &waitErr) {
					run.Message = waitErr.detail
					return false, nil
				}
				return false, err
			}
			if task.Phase == RunFailed {
				run.Phase = RunFailed
				run.Message = ""
				if run.Error == "" {
					run.Error = "a maintenance task could not start"
				}
				return false, nil
			}
		}

		if task.Phase == RunRunning {
			if err := advanceRunTask(ctx, kubeconfigPath, client, lease, run, task); err != nil {
				return false, err
			}
			if task.Phase == RunFailed {
				run.Phase = RunFailed
				return false, nil
			}
			if task.Phase != RunCompleted {
				return false, nil
			}
		}
	}

	return true, nil
}

func runItemKey(item RunItem, targetRelease string) string {
	return item.NodeName + "|" + item.NodeUid + "|" + targetRelease
}

func startRunTask(ctx context.Context, kubeconfigPath string, run *MaintenanceRun, task *RunTask) error {
	record, found, err := readClusterRecord()
	if err != nil {
		return err
	}
	if !found {
		return errors.New("the cluster record does not exist")
	}

	clientset, err := newClient(kubeconfigPath)
	if err != nil {
		return err
	}
	live, err := runLiveNodes(ctx, clientset)
	if err != nil {
		return err
	}

	targets, targetErr := runTargetNodes(record, run)
	if targetErr != nil {
		return targetErr
	}

	if task.Kind == TaskOsUpdate && len(run.osProbes) == 0 && len(targets) > 0 {
		probes := runProbeOsNodes(ctx, targets)
		for _, probe := range probes {
			if probe.err != nil {
				return &runTaskWaitError{detail: fmt.Sprintf(
					"Waiting for the maintenance service on node %s: %s",
					probe.node.Hostname,
					probe.err,
				)}
			}
		}
		run.osProbes = make(map[string]shared.NodeAgentOsStatus, len(probes))
		for _, probe := range probes {
			run.osProbes[probe.node.Hostname] = probe.status
		}
	}

	completedItems := map[string]RunItem{}
	for _, item := range task.Items {
		if item.Phase == RunCompleted {
			completedItems[runItemKey(item, task.TargetRelease)] = item
		}
	}
	task.Items = task.Items[:0]
	appendItem := func(item RunItem) {
		if existing, ok := completedItems[runItemKey(item, task.TargetRelease)]; ok {
			task.Items = append(task.Items, existing)
			return
		}
		task.Items = append(task.Items, item)
	}

	switch task.Kind {
	case TaskK8sUpgrade:
		for _, node := range targets {
			item := RunItem{NodeName: node.Hostname, Group: node.Group, Phase: RunPending}
			liveNode, known := live[node.Hostname]
			if !known {
				item.Phase = RunFailed
				item.Error = "the node is missing from Kubernetes"
			} else {
				item.NodeUid = string(liveNode.UID)
				if shared.SameRelease(liveNode.Status.NodeInfo.KubeletVersion, task.TargetRelease) {
					item.Phase = RunCompleted
				}
			}
			appendItem(item)
		}

	case TaskOsUpdate:
		for _, node := range targets {
			item := RunItem{NodeName: node.Hostname, Group: node.Group, Phase: RunPending}
			liveNode, known := live[node.Hostname]
			if !known {
				item.Phase = RunFailed
				item.Error = "the node is missing from Kubernetes"
			} else {
				item.NodeUid = string(liveNode.UID)
				status, checked := run.osProbes[node.Hostname]
				if !checked {
					item.Phase = RunFailed
					item.Error = "the operating-system update check did not complete"
				} else if status.NeedsStateRecovery {
					item.Phase = RunFailed
					item.Error = "the operating-system update state requires recovery"
				} else if status.NodeName != node.Hostname {
					item.Phase = RunFailed
					item.Error = "the maintenance service reported a different node name"
				} else if status.CheckError != "" {
					item.Phase = RunFailed
					item.Error = status.CheckError
				} else if status.Pending == 0 && !status.RebootRequired {
					item.Phase = RunCompleted
				}
			}
			appendItem(item)
		}

	case TaskCordon, TaskUncordon, TaskRemove:
		if len(targets) != 1 {
			return errors.New("a node action requires exactly one node")
		}
		node := targets[0]
		item := RunItem{NodeName: node.Hostname, Group: node.Group, Phase: RunPending}
		if liveNode, known := live[node.Hostname]; known {
			item.NodeUid = string(liveNode.UID)
		} else if task.Kind != TaskRemove {
			item.Phase = RunFailed
			item.Error = "the node is missing from Kubernetes"
		}
		appendItem(item)

	default:
		return fmt.Errorf("the maintenance task %s is not supported", task.Kind)
	}

	task.Phase = RunRunning
	for _, item := range task.Items {
		if item.Phase == RunFailed {
			task.Phase = RunFailed
			run.Phase = RunFailed
			run.Message = ""
			run.Error = item.Error
			return nil
		}
	}
	allCompleted := true
	for _, item := range task.Items {
		if item.Phase != RunCompleted {
			allCompleted = false
			break
		}
	}
	if allCompleted {
		task.Phase = RunCompleted
	}
	return nil
}

func advanceRunTask(
	ctx context.Context,
	kubeconfigPath string,
	client stackClient,
	lease *runLease,
	run *MaintenanceRun,
	task *RunTask,
) error {
	snapshot, err := Snapshot()
	if err != nil {
		return err
	}

	for itemIndex := range task.Items {
		item := &task.Items[itemIndex]
		if item.Phase != RunRunning {
			continue
		}

		operation, found := snapshot[item.NodeName]
		if found && operation.Uid == item.OperationUid {
			switch operation.Phase {
			case PhaseCompleted:
				item.Phase = RunCompleted
			case PhaseFailed, PhaseBlocked:
				item.Phase = RunFailed
				item.Error = operation.Error
				task.Phase = RunFailed
				run.Phase = RunFailed
				run.Message = ""
				run.Error = fmt.Sprintf("%s of %s failed: %s", task.Kind, item.NodeName, operation.Error)
				return nil
			case PhaseCancelled:
				item.Phase = RunCancelled
			}
			continue
		}

		if task.Kind == TaskRemove {
			record, recordFound, recordErr := readClusterRecord()
			if recordErr != nil {
				return recordErr
			}
			if recordFound && !clusterRecordHasNode(record, item.NodeName) {
				item.Phase = RunCompleted
				continue
			}
		}

		if item.OperationUid != "" {
			if item.StartedAt.IsZero() || time.Since(item.StartedAt) < 30*time.Second {
				continue
			}
			item.Phase = RunFailed
			item.Error = "the maintenance operation record is missing"
			task.Phase = RunFailed
			run.Phase = RunFailed
			run.Message = ""
			run.Error = item.Error
			return nil
		}
	}

	for _, item := range task.Items {
		if item.Phase == RunCancelled && !run.CancelRequested {
			runFail(run, "a maintenance operation was cancelled outside of the maintenance run")
			return nil
		}
	}

	if runTaskFailed(task) {
		run.Phase = RunFailed
		run.Message = ""
		return nil
	}

	groupActive := map[string]int{}
	controlPlaneRunning := false
	for _, item := range task.Items {
		if item.Phase == RunRunning {
			groupActive[item.Group]++
			if item.Group == shared.GroupControlPlane {
				controlPlaneRunning = true
			}
		}
	}

	controlPlaneDone := true
	for _, item := range task.Items {
		if item.Group == shared.GroupControlPlane && item.Phase != RunCompleted && item.Phase != RunCancelled {
			controlPlaneDone = false
			break
		}
	}

	for itemIndex := range task.Items {
		item := &task.Items[itemIndex]
		if item.Phase != RunPending {
			continue
		}
		if item.Group != shared.GroupControlPlane && !controlPlaneDone {
			continue
		}
		limit := run.Effective.MaxParallelPerGroup
		if limit <= 0 {
			limit = 1
		}
		if item.Group == shared.GroupControlPlane {
			limit = 1
			if controlPlaneRunning {
				continue
			}
		}
		if groupActive[item.Group] >= limit {
			continue
		}

		if lease.cancellationRequested(client) {
			run.CancelRequested = true
			return nil
		}

		if err := startRunItem(ctx, kubeconfigPath, client, lease, run, task, item); err != nil {
			return err
		}
		groupActive[item.Group]++
		if item.Group == shared.GroupControlPlane {
			controlPlaneRunning = true
		}
	}

	if runTaskFailed(task) {
		run.Phase = RunFailed
		run.Message = ""
		return nil
	}

	terminal := true
	allCompleted := true
	for _, item := range task.Items {
		switch item.Phase {
		case RunCompleted:
		case RunCancelled:
		default:
			terminal = false
		}
		if item.Phase != RunCompleted {
			allCompleted = false
		}
	}
	if allCompleted {
		task.Phase = RunCompleted
	} else if terminal {
		if run.CancelRequested {
			task.Phase = RunCancelled
		} else {
			runFail(run, "a maintenance operation was cancelled outside of the maintenance run")
		}
	}
	return nil
}

func runTaskFailed(task *RunTask) bool {
	for _, item := range task.Items {
		if item.Phase == RunFailed {
			return true
		}
	}
	return false
}

func startRunItem(
	ctx context.Context,
	kubeconfigPath string,
	client stackClient,
	lease *runLease,
	run *MaintenanceRun,
	task *RunTask,
	item *RunItem,
) error {
	kind, kindErr := runTaskOperationKind(task.Kind)
	if kindErr != nil {
		return kindErr
	}

	adopted, adoptErr := runAdoptOperation(client, run, task, item, kind)
	if adoptErr != nil {
		return adoptErr
	}
	if adopted {
		return lease.write(client, *run)
	}

	nodeUid := item.NodeUid
	if nodeUid == "" && task.Kind != TaskRemove {
		clientset, clientErr := newClient(kubeconfigPath)
		if clientErr != nil {
			return clientErr
		}
		node, getErr := clientset.CoreV1().Nodes().Get(ctx, item.NodeName, metav1.GetOptions{})
		if getErr != nil {
			return getErr
		}
		nodeUid = string(node.UID)
	}

	if kind == KindRemove {
		if guardErr := removeSubmitGuard(item.NodeName); guardErr != nil {
			return guardErr
		}
	}

	operationUid, submitErr := submitOperation(
		item.NodeName,
		nodeUid,
		kind,
		task.Options,
		task.TargetRelease,
		run.Uid,
	)
	if submitErr != nil {
		return submitErr
	}
	item.OperationUid = operationUid
	item.Phase = RunRunning
	item.StartedAt = time.Now().UTC()
	return lease.write(client, *run)
}

func runTaskOperationKind(taskKind string) (string, error) {
	switch taskKind {
	case TaskK8sUpgrade:
		return KindUpgrade, nil
	case TaskOsUpdate:
		return KindOsUpdate, nil
	case TaskCordon:
		return KindCordon, nil
	case TaskUncordon:
		return KindUncordon, nil
	case TaskRemove:
		return KindRemove, nil
	default:
		return "", fmt.Errorf("the maintenance task %s is not supported", taskKind)
	}
}

func runAdoptOperation(
	client stackClient,
	run *MaintenanceRun,
	task *RunTask,
	item *RunItem,
	kind string,
) (bool, error) {
	record, err := stackValueRead[Operation](client, stackNodeKey(item.NodeName), "maintenance record "+stackNodeKey(item.NodeName))
	if err != nil {
		return false, err
	}
	if !record.Found || record.Value.RunUid != run.Uid || record.Value.Kind != kind {
		return false, nil
	}
	if item.NodeUid != "" && record.Value.NodeUid != "" && record.Value.NodeUid != item.NodeUid {
		return false, nil
	}
	if task.Kind == TaskK8sUpgrade && record.Value.TargetRelease != task.TargetRelease {
		return false, nil
	}

	operation := record.Value
	switch operation.Phase {
	case PhaseCompleted:
		item.OperationUid = operation.Uid
		item.StartedAt = operation.StartedAt
		item.Phase = RunCompleted
		return true, nil
	case PhasePending, PhaseRunning:
		item.OperationUid = operation.Uid
		item.StartedAt = operation.StartedAt
		item.Phase = RunRunning
		return true, nil
	default:
		return false, nil
	}
}

func clusterRecordHasNode(record shared.ClusterRecord, nodeName string) bool {
	for _, node := range record.Nodes {
		if node.Hostname == nodeName {
			return true
		}
	}
	return false
}
