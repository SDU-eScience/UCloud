package maintenance

import (
	"context"
	"errors"
	"fmt"
	"sort"
	"strings"
	"sync"
	"time"

	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"

	"ucloud.dk/iapp/k8s/pkg/backup"
	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
)

const (
	autoOsCheckInterval        = 6 * time.Hour
	autoOsCheckRetryInterval   = 15 * time.Minute
	autoOsCheckLeadTime        = 30 * time.Minute
	autoOsReportFreshLimit     = 90 * time.Minute
	autoOsOperationTimeoutSecs = 1800

	autoProbeBudget = autoClaimLease / 2
)

var errAutoOsSchedulePaused = errors.New("automatic operating-system updates are disabled or outside the maintenance window")

func autoOsScheduleGate(client stackClient, now time.Time) (autoScheduleGate, error) {
	policyRecord, err := autoPolicyRead(client)
	if err != nil {
		return autoScheduleGate{}, err
	}
	policy := AutoPolicyNormalize(policyRecord.Value)
	if !policyRecord.Found || !policy.Os.Enabled || !AutoScheduleOpen(now, policy) {
		return autoScheduleGate{}, nil
	}

	_, end, open := autoPolicyWindowOpenNow(now, policy)
	return autoScheduleGate{
		open: open,
		condition: ucxapi.StackStateRevisionCondition{
			Key:              AutoPolicyKey,
			ExpectedRevision: policyRecord.Revision,
		},
		deadline: end,
	}, nil
}

// Update checks
// =====================================================================================================================
// The coordinator asks every node for an operating-system update check every six hours, before a window, and on
// demand. The reports live on the automatic maintenance state, so the dashboard shows them after any restart, even
// while a node is unreachable.

type autoOsCheckResult struct {
	node   shared.ClusterNodeRecord
	status shared.NodeAgentOsStatus
	err    error
}

func autoOsCheckNodes(ctx context.Context, nodes []shared.ClusterNodeRecord) []autoOsCheckResult {
	probeCtx, cancel := context.WithTimeout(ctx, autoProbeBudget)
	defer cancel()

	results := make([]autoOsCheckResult, len(nodes))
	semaphore := make(chan struct{}, autoAgentProbeConcurrency)
	var waitGroup sync.WaitGroup

	for i := range nodes {
		waitGroup.Add(1)
		go func(index int) {
			defer waitGroup.Done()

			node := nodes[index]
			select {
			case semaphore <- struct{}{}:
			case <-probeCtx.Done():
				results[index] = autoOsCheckResult{node: node, err: probeCtx.Err()}
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
			results[index] = autoOsCheckResult{node: node, status: status, err: err}
		}(i)
	}

	waitGroup.Wait()
	return results
}

func autoOsFindReport(reports []AutoUpdateReport, nodeName string) (AutoUpdateReport, bool) {
	for _, report := range reports {
		if report.NodeName == nodeName {
			return report, true
		}
	}
	return AutoUpdateReport{}, false
}

func autoOsReportsMerge(
	reports []AutoUpdateReport,
	known map[string]bool,
	results []autoOsCheckResult,
	now time.Time,
) []AutoUpdateReport {
	merged := map[string]AutoUpdateReport{}
	for _, report := range reports {
		if known[report.NodeName] {
			merged[report.NodeName] = report
		}
	}

	for _, result := range results {
		report := AutoUpdateReport{
			NodeName:  result.node.Hostname,
			CheckedAt: now.UTC(),
		}
		if result.err != nil {
			report.Error = result.err.Error()
			if previous, ok := merged[result.node.Hostname]; ok {
				report.Pending = previous.Pending
				report.Security = previous.Security
				report.RebootRequired = previous.RebootRequired
			}
		} else {
			report.Pending = result.status.Pending
			report.Security = result.status.Security
			report.RebootRequired = result.status.RebootRequired
			report.Error = strings.TrimSpace(result.status.CheckError)
		}
		merged[result.node.Hostname] = report
	}

	names := make([]string, 0, len(merged))
	for name := range merged {
		names = append(names, name)
	}
	sort.Strings(names)

	sorted := make([]AutoUpdateReport, 0, len(names))
	for _, name := range names {
		sorted = append(sorted, merged[name])
	}
	return sorted
}

func autoOsNodeNames(record shared.ClusterRecord) map[string]bool {
	names := map[string]bool{}
	for _, node := range record.Nodes {
		names[node.Hostname] = true
	}
	return names
}

func autoOsCheckDue(
	report AutoUpdateReport,
	found bool,
	now time.Time,
	windowStart time.Time,
	windowOpen bool,
) bool {
	if !found || report.CheckedAt.IsZero() {
		return true
	}
	if report.Error != "" {
		return now.Sub(report.CheckedAt) >= autoOsCheckRetryInterval
	}
	if !windowOpen && !windowStart.IsZero() {
		untilWindow := windowStart.Sub(now)
		if untilWindow > 0 && untilWindow <= autoOsCheckLeadTime && now.Sub(report.CheckedAt) >= autoOsCheckLeadTime {
			return true
		}
	}
	return now.Sub(report.CheckedAt) >= autoOsCheckInterval
}

func autoOsCheckDueNodes(
	record shared.ClusterRecord,
	state AutoState,
	policy AutoPolicy,
	now time.Time,
) []shared.ClusterNodeRecord {
	windowStart, _, windowOpen := autoPolicyWindowOpenNow(now, policy)
	if !windowOpen {
		if effective, _, ok := AutoEffectiveWindow(now, policy); ok {
			windowStart = effective
		}
	}

	due := []shared.ClusterNodeRecord{}
	for _, node := range record.Nodes {
		report, found := autoOsFindReport(state.OsReports, node.Hostname)
		if autoOsCheckDue(report, found, now, windowStart, windowOpen) {
			due = append(due, node)
		}
	}
	return due
}

func autoOsCheckSweep(ctx context.Context, client stackClient) error {
	record, err := readClusterRecord()
	if err != nil {
		return err
	}
	if record.Phase != "created" {
		return nil
	}

	policyRecord, err := autoPolicyRead(client)
	if err != nil {
		return err
	}
	if !policyRecord.Found {
		return nil
	}
	policy := policyRecord.Value

	stateRecord, err := autoStateRead(client)
	if err != nil {
		return err
	}
	if !stateRecord.Found {
		return nil
	}

	now := time.Now().UTC()
	if autoStateClaimActive(stateRecord.Value, now) {
		return nil
	}
	if AutoRunActive(stateRecord.Value.LastRun) {
		// The run refreshes the reports it needs. The periodic check does not compete with it.
		return nil
	}

	due := autoOsCheckDueNodes(record, stateRecord.Value, policy, now)
	if len(due) == 0 {
		return nil
	}

	claimUid := shared.NewUid()
	stateRecord, err = autoStateClaim(client, stateRecord, claimUid, now)
	if err != nil {
		if shared.IsConflict(err) {
			return nil
		}
		return err
	}
	defer autoUpgradeReleaseClaim(client, claimUid)

	results := autoOsCheckNodes(ctx, due)

	write := &autoRunWrite{
		client:   client,
		state:    stateRecord,
		policy:   policy,
		claimUid: claimUid,
		now:      now,
	}

	_, err = autoRunPersist(write, func(state *AutoState) {
		state.OsReports = autoOsReportsMerge(state.OsReports, autoOsNodeNames(record), results, time.Now().UTC())
	})
	return err
}

func AutoOsRecoveryNodes() ([]string, error) {
	snapshot, err := Snapshot()
	if err != nil {
		return nil, err
	}

	nodes := []string{}
	for _, operation := range snapshot {
		if KindIsOsUpdate(operation.Kind) && operationBlockedByRecovery(operation) {
			nodes = append(nodes, operation.NodeName)
		}
	}
	sort.Strings(nodes)
	return nodes, nil
}

func AutoRetryOsUpdate(ctx context.Context, kubeconfigPath string, nodeName string) error {
	nodeName = strings.TrimSpace(nodeName)
	if nodeName == "" {
		return errors.New("no node was selected for the retry")
	}

	record, err := readClusterRecord()
	if err != nil {
		return err
	}
	if record.Phase != "created" {
		return errors.New("the cluster is not ready for maintenance")
	}

	client, err := stackClientNew()
	if err != nil {
		return err
	}

	operationRecord, err := stackRead(client, stackNodeKey(nodeName))
	if err != nil {
		return err
	}
	if !operationRecord.Found || !KindIsOsUpdate(operationRecord.Value.Kind) ||
		!operationBlockedByRecovery(operationRecord.Value) {
		return errors.New("no failed operating-system update is waiting on " + nodeName)
	}

	clientset, err := newClient(kubeconfigPath)
	if err != nil {
		return err
	}

	nodeObject, err := clientset.CoreV1().Nodes().Get(ctx, nodeName, metav1.GetOptions{})
	if err != nil {
		return fmt.Errorf("could not read the node %s: %s", nodeName, err)
	}

	runUid := ""
	stateRecord, stateErr := autoStateRead(client)
	if stateErr == nil && AutoStateBlocked(stateRecord.Value) {
		runUid = stateRecord.Value.LastRun.Uid
	}

	return OsUpdateStart(nodeName, string(nodeObject.UID), Options{TimeoutSeconds: autoOsOperationTimeoutSecs}, runUid)
}

// The scheduled operating-system run
// =====================================================================================================================
// The operating-system channel shares the window with the Kubernetes channel: Kubernetes goes first, and an
// operating-system run starts only when no Kubernetes run or node operation is active. Nodes run one at a time,
// control planes first, and the next node starts only after the previous node is verified healthy.

func autoOsStart(write *autoRunWrite, ctx context.Context, kubeconfigPath string, record shared.ClusterRecord, windowStart time.Time, windowEnd time.Time) error {
	previous := write.state.Value.LastRun
	inherited := AutoRun{}
	if previous.Channel != AutoChannelOs && previous.Phase == AutoPhaseCompleted &&
		!previous.WindowStart.IsZero() && previous.WindowStart.Equal(windowStart.UTC()) {
		inherited.K8sRelease = previous.K8sRelease
		if inherited.K8sRelease == "" {
			inherited.K8sRelease = previous.TargetRelease
		}
		inherited.K8sCompletedAt = previous.K8sCompletedAt
		inherited.K8sRunUid = previous.K8sRunUid
		if inherited.K8sRunUid == "" {
			inherited.K8sRunUid = previous.Uid
		}
	}

	_, err := autoRunPersist(write, func(state *AutoState) {
		state.LastRun = AutoRun{
			Uid:            shared.NewUid(),
			Channel:        AutoChannelOs,
			Trigger:        AutoTriggerWindow,
			Phase:          AutoPhasePending,
			WindowStart:    windowStart.UTC(),
			WindowEnd:      windowEnd.UTC(),
			StartedAt:      write.now.UTC(),
			ManualWindow:   windowStart.Equal(write.policy.TestWindowStart) && AutoTestWindowOpen(write.now, write.policy),
			K8sRelease:     inherited.K8sRelease,
			K8sRunUid:      inherited.K8sRunUid,
			K8sCompletedAt: inherited.K8sCompletedAt,
		}
	})
	if err != nil {
		return err
	}

	return autoOsPrepare(write, ctx, kubeconfigPath, record)
}

func autoOsAdvance(write *autoRunWrite, ctx context.Context, kubeconfigPath string, record shared.ClusterRecord) error {
	run := write.state.Value.LastRun

	snapshot, err := Snapshot()
	if err != nil {
		return fmt.Errorf("could not read the maintenance state: %s", err)
	}

	operation, found := autoOsRunOperation(snapshot, run)
	if found && PhaseActive(operation.Phase) {
		_, err = autoRunWaiting(write, AutoPhaseRunning, run.Checks, "")
		return err
	}

	if found {
		return autoOsConcludeNode(write, ctx, kubeconfigPath, record, operation)
	}

	return autoOsPrepare(write, ctx, kubeconfigPath, record)
}

func autoOsRunOperation(snapshot map[string]Operation, run AutoRun) (Operation, bool) {
	var found Operation
	for _, operation := range snapshot {
		if !KindIsOsUpdate(operation.Kind) {
			continue
		}
		if operation.AutoRunUid != run.Uid {
			continue
		}
		if found.NodeName == "" || operation.UpdatedAt.After(found.UpdatedAt) {
			found = operation
		}
	}
	return found, found.NodeName != ""
}

func autoOsConcludeNode(
	write *autoRunWrite,
	ctx context.Context,
	kubeconfigPath string,
	record shared.ClusterRecord,
	operation Operation,
) error {
	run := write.state.Value.LastRun

	if operation.Phase == PhaseCompleted {
		node, known := upgradeNodeRecord(record, operation.NodeName)
		if known {
			results := autoOsCheckNodes(ctx, []shared.ClusterNodeRecord{node})
			_, err := autoRunPersist(write, func(state *AutoState) {
				state.OsReports = autoOsReportsMerge(state.OsReports, autoOsNodeNames(record), results, time.Now().UTC())
			})
			if err != nil {
				return err
			}
		}
		return autoOsPrepare(write, ctx, kubeconfigPath, record)
	}

	message := operation.Error
	if message == "" {
		message = "the operation stopped without recording a reason"
	}

	_, err := autoRunTerminal(
		write,
		AutoPhaseBlocked,
		run.Checks,
		"",
		fmt.Sprintf(
			"The operating-system update of %s stopped: %s. Resolve the problem, then press Resume automatic maintenance.",
			operation.NodeName,
			message,
		),
	)
	return err
}

func autoOsPrepare(write *autoRunWrite, ctx context.Context, kubeconfigPath string, record shared.ClusterRecord) error {
	run := write.state.Value.LastRun

	if run.Trigger == AutoTriggerWindow && (!write.policy.Os.Enabled || !AutoScheduleOpen(time.Now().UTC(), write.policy)) {
		return nil
	}

	clientset, err := newClient(kubeconfigPath)
	if err != nil {
		return err
	}

	list := autoCheckList{outcome: autoOutcomeProceed}
	proceed, err := autoOsClusterChecks(ctx, clientset, write.client, record, &list)
	if err != nil {
		return err
	}
	if !proceed {
		switch list.outcome {
		case autoOutcomeStop:
			_, err = autoRunTerminal(write, AutoPhaseBlocked, list.checks, "", list.detail)
		case autoOutcomeWait:
			_, err = autoRunWaitingOrBlocked(write, AutoPhasePreflight, list.checks, "", list.detail)
		}
		return err
	}

	if _, err = autoRunWaiting(write, AutoPhasePreflight, list.checks, ""); err != nil {
		return err
	}

	reports, err := autoOsRefreshReports(ctx, write, record)
	if err != nil {
		return err
	}

	snapshot, err := Snapshot()
	if err != nil {
		return fmt.Errorf("could not read the maintenance state: %s", err)
	}

	plan, skipped, complete, detail := autoOsPlan(record, reports, snapshot, run)

	var checks []AutoCheck
	if complete {
		checks = append(list.checks, AutoCheck{Name: AutoCheckOs, Ok: true, Detail: detail})
	} else {
		checks = append(list.checks, AutoCheck{
			Name:   AutoCheckOs,
			Ok:     true,
			Detail: fmt.Sprintf("%d of %d nodes need an operating-system update.", len(plan), len(record.Nodes)),
		})
	}
	for _, skip := range skipped {
		checks = append(checks, AutoCheck{Name: AutoCheckOs, Ok: false, Detail: skip})
	}

	if complete {
		_, err = autoRunTerminal(write, AutoPhaseCompleted, checks, "", "")
		return err
	}

	node := plan[0]
	if submitErr := autoOsSubmitNode(ctx, clientset, node, run); submitErr != nil {
		if errors.Is(submitErr, errAutoOsSchedulePaused) {
			return nil
		}
		detail := fmt.Sprintf("The operating-system update of %s could not start: %s", node.Hostname, submitErr)
		checks = append(checks, AutoCheck{Name: AutoCheckOs, Ok: false, Detail: detail})
		_, err = autoRunWaitingOrBlocked(write, AutoPhasePreflight, checks, "", detail)
		return err
	}

	_, err = autoRunWaiting(write, AutoPhaseRunning, checks, "")
	return err
}

func autoOsClusterChecks(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	client stackClient,
	record shared.ClusterRecord,
	list *autoCheckList,
) (bool, error) {
	if record.Phase != "created" {
		autoCheckRecord(list, AutoCheckCluster, autoOutcomeWait, fmt.Sprintf("The cluster is not ready for maintenance (state: %s).", record.Phase))
		return false, nil
	}
	autoCheckRecord(list, AutoCheckCluster, autoOutcomeProceed, "")

	recovery, err := recoveryIntentActive()
	if err != nil {
		return false, err
	}
	if recovery {
		autoCheckRecord(list, AutoCheckRecovery, autoOutcomeWait, "A recovery operation is in progress for this cluster.")
		return false, nil
	}
	autoCheckRecord(list, AutoCheckRecovery, autoOutcomeProceed, "")

	snapshot, err := Snapshot()
	if err != nil {
		return false, fmt.Errorf("could not read the maintenance state: %s", err)
	}

	proceed, err := autoOperationsCheck(record, snapshot, list)
	if err != nil || !proceed {
		return false, err
	}

	proceed, err = autoOsBackupWait(client, list)
	if err != nil || !proceed {
		return false, err
	}

	listed, err := clientset.CoreV1().Nodes().List(ctx, metav1.ListOptions{})
	if err != nil {
		return false, fmt.Errorf("could not list the nodes of the cluster: %s", err)
	}

	liveNodes := make(map[string]*corev1.Node, len(listed.Items))
	for i := range listed.Items {
		liveNodes[listed.Items[i].Name] = &listed.Items[i]
	}

	for _, node := range record.Nodes {
		live, known := liveNodes[node.Hostname]
		if !known {
			autoCheckRecord(list, AutoCheckNodes, autoOutcomeStop, fmt.Sprintf("Node %s is missing from Kubernetes.", node.Hostname))
			return false, nil
		}
		if !nodeIsReady(live) {
			autoCheckRecord(list, AutoCheckNodes, autoOutcomeStop, fmt.Sprintf("Node %s is not ready.", node.Hostname))
			return false, nil
		}
	}
	autoCheckRecord(list, AutoCheckNodes, autoOutcomeProceed, fmt.Sprintf("All %d nodes are ready.", len(record.Nodes)))

	proceed, err = autoQuorumCheck(ctx, clientset, record, list, "an operating-system update")
	if err != nil || !proceed {
		return false, err
	}

	rolling, _, err := rollingUpgradeRead(client)
	if err != nil {
		return false, err
	}
	if PhaseActive(rolling.Phase) {
		autoCheckRecord(list, AutoCheckRolling, autoOutcomeWait, fmt.Sprintf(
			"A rolling upgrade to %s is active. The operating-system run waits for it to finish.",
			rolling.TargetRelease,
		))
		return false, nil
	}
	autoCheckRecord(list, AutoCheckRolling, autoOutcomeProceed, "")

	return true, nil
}

func autoOsBackupWait(client stackClient, list *autoCheckList) (bool, error) {
	stateRecord, err := backupStateRead(client)
	if err != nil {
		return false, err
	}

	now := time.Now().UTC()
	running := stateRecord.Found &&
		((stateRecord.Value.ClaimUid != "" && now.Before(stateRecord.Value.ClaimDeadline)) ||
			stateRecord.Value.LastAttempt.Phase == backup.AttemptRunning)
	if running {
		autoCheckRecord(list, AutoCheckBackup, autoOutcomeWait, "A backup is running. The operating-system run waits for it to finish.")
		return false, nil
	}

	autoCheckRecord(list, AutoCheckBackup, autoOutcomeProceed, "")
	return true, nil
}

func autoOsRefreshReports(ctx context.Context, write *autoRunWrite, record shared.ClusterRecord) ([]AutoUpdateReport, error) {
	reports := write.state.Value.OsReports
	run := write.state.Value.LastRun

	due := []shared.ClusterNodeRecord{}
	for _, node := range record.Nodes {
		report, found := autoOsFindReport(reports, node.Hostname)
		if found {
			if report.Error != "" {
				if write.now.Sub(report.CheckedAt) < autoOsCheckRetryInterval {
					continue
				}
			} else if report.CheckedAt.After(run.StartedAt) || write.now.Sub(report.CheckedAt) <= autoOsReportFreshLimit {
				continue
			}
		}
		due = append(due, node)
	}
	if len(due) == 0 {
		return reports, nil
	}

	results := autoOsCheckNodes(ctx, due)

	merged := reports
	_, err := autoRunPersist(write, func(state *AutoState) {
		merged = autoOsReportsMerge(state.OsReports, autoOsNodeNames(record), results, time.Now().UTC())
		state.OsReports = merged
	})
	if err != nil {
		return nil, err
	}
	return merged, nil
}

func autoOsPlan(
	record shared.ClusterRecord,
	reports []AutoUpdateReport,
	snapshot map[string]Operation,
	run AutoRun,
) ([]shared.ClusterNodeRecord, []string, bool, string) {
	nodes := append([]shared.ClusterNodeRecord(nil), record.Nodes...)
	sort.SliceStable(nodes, func(i int, j int) bool {
		left := nodes[i]
		right := nodes[j]
		leftControlPlane := left.Group == shared.GroupControlPlane
		rightControlPlane := right.Group == shared.GroupControlPlane
		if leftControlPlane != rightControlPlane {
			return leftControlPlane
		}
		if left.Group != right.Group {
			return left.Group < right.Group
		}
		return left.AllocationId < right.AllocationId
	})

	plan := []shared.ClusterNodeRecord{}
	skipped := []string{}
	processed := 0
	for _, node := range nodes {
		if autoOsNodeProcessed(snapshot, node.Hostname, run) {
			processed++
			continue
		}

		report, found := autoOsFindReport(reports, node.Hostname)
		if !found || report.CheckedAt.IsZero() {
			skipped = append(skipped, fmt.Sprintf("%s was skipped because it has no update check yet.", node.Hostname))
			continue
		}
		if report.Error != "" {
			skipped = append(skipped, fmt.Sprintf("%s was skipped because its update check failed: %s.", node.Hostname, report.Error))
			continue
		}
		if report.Pending == 0 && !report.RebootRequired {
			continue
		}

		plan = append(plan, node)
	}

	if len(plan) == 0 {
		switch {
		case processed > 0 && len(skipped) > 0:
			return nil, skipped, true, fmt.Sprintf(
				"The planned nodes were updated (%s). The update check failed on %s.",
				autoOsNodeCount(processed),
				autoOsNodeCount(len(skipped)),
			)
		case processed > 0:
			return nil, skipped, true, fmt.Sprintf("The planned nodes were updated (%s).", autoOsNodeCount(processed))
		case len(skipped) > 0:
			return nil, skipped, true, fmt.Sprintf(
				"The update check failed on %s. Those nodes are updated in a later window.",
				autoOsNodeCount(len(skipped)),
			)
		default:
			return nil, skipped, true, "All nodes are up to date."
		}
	}

	return plan, skipped, false, ""
}

func autoOsNodeCount(count int) string {
	if count == 1 {
		return "1 node"
	}
	return fmt.Sprintf("%d nodes", count)
}

func autoOsNodeProcessed(snapshot map[string]Operation, nodeName string, run AutoRun) bool {
	for _, operation := range snapshot {
		if operation.NodeName != nodeName || !KindIsOsUpdate(operation.Kind) {
			continue
		}
		if operation.AutoRunUid != run.Uid {
			continue
		}
		if operation.Phase == PhaseCompleted && operation.UpdatedAt.After(run.StartedAt) {
			return true
		}
	}
	return false
}

func autoOsSubmitNode(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	node shared.ClusterNodeRecord,
	run AutoRun,
) error {
	nodeObject, err := clientset.CoreV1().Nodes().Get(ctx, node.Hostname, metav1.GetOptions{})
	if err != nil {
		return fmt.Errorf("could not read the node %s: %s", node.Hostname, err)
	}

	return OsUpdateStart(
		node.Hostname,
		string(nodeObject.UID),
		Options{TimeoutSeconds: autoOsOperationTimeoutSecs},
		run.Uid,
	)
}

func autoChannelStartAllowed(run AutoRun, policy AutoPolicy, windowStart time.Time, resumed bool) bool {
	ranThisWindow := !run.WindowStart.IsZero() && run.WindowStart.Equal(windowStart)
	switch {
	case !ranThisWindow:
		return policy.K8s.Enabled || policy.Os.Enabled
	case resumed:
		// The operator resolved the problem that stopped the run. The same channel continues in the window.
		if autoWindowChannel(run, policy, windowStart, true) == AutoChannelOs {
			return policy.Os.Enabled
		}
		return policy.K8s.Enabled
	case run.Channel == AutoChannelK8s && run.Phase == AutoPhaseCompleted && policy.Os.Enabled:
		// The Kubernetes channel takes the window first. The operating-system channel uses the free time of the
		// window after a completed Kubernetes run.
		return true
	}
	return false
}

func autoWindowChannel(run AutoRun, policy AutoPolicy, windowStart time.Time, resumed bool) string {
	ranThisWindow := !run.WindowStart.IsZero() && run.WindowStart.Equal(windowStart)
	if ranThisWindow && resumed && run.Channel != "" {
		// The operator resolved the problem that stopped the run. The channel continues in the window.
		return run.Channel
	}
	if !ranThisWindow && policy.K8s.Enabled {
		return AutoChannelK8s
	}
	return AutoChannelOs
}
