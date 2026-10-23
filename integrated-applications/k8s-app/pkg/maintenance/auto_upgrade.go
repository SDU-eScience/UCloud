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
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/util"
)

const autoBackupRequester = "automatic maintenance"

const autoManualWaitLimit = time.Hour

const autoAgentProbeConcurrency = 4

const (
	AutoCheckCluster    = "cluster"
	AutoCheckRecovery   = "recovery"
	AutoCheckOperations = "operations"
	AutoCheckNodes      = "nodes"
	AutoCheckQuorum     = "quorum"
	AutoCheckAgents     = "agents"
	AutoCheckVersions   = "versions"
	AutoCheckPath       = "path"
	AutoCheckRolling    = "rollingUpgrade"
	AutoCheckBackup     = "backup"
	AutoCheckOs         = "os"
)

type autoOutcome int

const (
	autoOutcomeProceed autoOutcome = iota
	autoOutcomeWait
	autoOutcomeStop
	autoOutcomeComplete
)

type autoCheckList struct {
	checks  []AutoCheck
	outcome autoOutcome
	detail  string
	target  shared.K3sRelease
}

func autoCheckRecord(list *autoCheckList, name string, outcome autoOutcome, detail string) {
	list.checks = append(list.checks, AutoCheck{
		Name:   name,
		Ok:     outcome == autoOutcomeProceed || outcome == autoOutcomeComplete,
		Detail: detail,
	})

	if outcome != autoOutcomeProceed {
		list.outcome = outcome
		list.detail = detail
	}
}

func autoChecksEqual(left []AutoCheck, right []AutoCheck) bool {
	if len(left) != len(right) {
		return false
	}
	for i := range left {
		if left[i] != right[i] {
			return false
		}
	}
	return true
}

func autoWindowOpenNow(now time.Time, window AutoWindow) (time.Time, time.Time, bool) {
	start, end, ok := AutoNextWindow(now, window)
	if !ok || now.Before(start) || !now.Before(end) {
		return time.Time{}, time.Time{}, false
	}
	return start, end, true
}

func AutoScheduleOpen(now time.Time, policy AutoPolicy) bool {
	if AutoTestWindowOpen(now, policy) {
		return true
	}
	if policy.DeferUntil.After(now) {
		return false
	}

	_, _, ok := autoWindowOpenNow(now, policy.Window)
	return ok
}

var errAutoSchedulePaused = errors.New("automatic Kubernetes upgrades are disabled or outside the maintenance window")

type autoScheduleGate struct {
	open      bool
	condition ucxapi.StackStateRevisionCondition
	deadline  time.Time
}

func autoUpgradeScheduleGate(client stackClient, now time.Time) (autoScheduleGate, error) {
	policyRecord, err := autoPolicyRead(client)
	if err != nil {
		return autoScheduleGate{}, err
	}
	policy := AutoPolicyNormalize(policyRecord.Value)
	if !policyRecord.Found || !policy.K8s.Enabled || !AutoScheduleOpen(now, policy) {
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

func autoDurationLabel(delta time.Duration) string {
	switch {
	case delta < time.Minute:
		return "less than a minute"
	case delta < time.Hour:
		return fmt.Sprintf("%d min", int(delta/time.Minute))
	case delta < 24*time.Hour:
		return fmt.Sprintf("%d h", int(delta/time.Hour))
	}
	return fmt.Sprintf("%d d", int(delta/(24*time.Hour)))
}

func autoUpgradeTarget(versions []string, now time.Time) (shared.K3sRelease, string) {
	target, ok := shared.RecommendedClusterTarget(versions, now)
	if !ok {
		return shared.K3sRelease{}, "No supported upgrade is available for every node. A node may be more than one minor behind, or every release may be past its end of support."
	}

	return target, ""
}

type autoAgentProbe struct {
	node   shared.ClusterNodeRecord
	status shared.NodeAgentStatus
	err    error
}

func autoUpgradeAgentProbes(ctx context.Context, nodes []shared.ClusterNodeRecord) []autoAgentProbe {
	probeCtx, cancel := context.WithTimeout(ctx, autoProbeBudget)
	defer cancel()

	probes := make([]autoAgentProbe, len(nodes))
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
				probes[index] = autoAgentProbe{node: node, err: probeCtx.Err()}
				return
			}
			defer func() { <-semaphore }()

			status, err := shared.NodeAgentClientStatus(probeCtx, node.Hostname, node.IpAddress)
			probes[index] = autoAgentProbe{node: node, status: status, err: err}
		}(i)
	}

	waitGroup.Wait()
	return probes
}

func autoUpgradeClusterChecks(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	record shared.ClusterRecord,
	now time.Time,
) (autoCheckList, error) {
	list := autoCheckList{outcome: autoOutcomeProceed}

	if record.Phase != "created" {
		autoCheckRecord(&list, AutoCheckCluster, autoOutcomeWait, fmt.Sprintf("The cluster is not ready for maintenance (state: %s).", record.Phase))
		return list, nil
	}
	autoCheckRecord(&list, AutoCheckCluster, autoOutcomeProceed, "")

	recovery, err := recoveryIntentActive()
	if err != nil {
		return list, err
	}
	if recovery {
		autoCheckRecord(&list, AutoCheckRecovery, autoOutcomeWait, "A recovery operation is in progress for this cluster.")
		return list, nil
	}
	autoCheckRecord(&list, AutoCheckRecovery, autoOutcomeProceed, "")

	snapshot, err := Snapshot()
	if err != nil {
		return list, fmt.Errorf("could not read the maintenance state: %s", err)
	}

	proceed, err := autoOperationsCheck(record, snapshot, &list)
	if err != nil {
		return list, err
	}
	if !proceed {
		return list, nil
	}

	listed, err := clientset.CoreV1().Nodes().List(ctx, metav1.ListOptions{})
	if err != nil {
		return list, fmt.Errorf("could not list the nodes of the cluster: %s", err)
	}

	liveNodes := make(map[string]*corev1.Node, len(listed.Items))
	for i := range listed.Items {
		liveNodes[listed.Items[i].Name] = &listed.Items[i]
	}

	versions := make([]string, 0, len(record.Nodes))
	versionsByNode := make(map[string]string, len(record.Nodes))
	controlPlaneVersions := []*version.Version{}
	workerVersions := []*version.Version{}

	for _, node := range record.Nodes {
		live, known := liveNodes[node.Hostname]
		if !known {
			autoCheckRecord(&list, AutoCheckNodes, autoOutcomeStop, fmt.Sprintf("Node %s is missing from Kubernetes.", node.Hostname))
			return list, nil
		}
		if !nodeIsReady(live) {
			autoCheckRecord(&list, AutoCheckNodes, autoOutcomeStop, fmt.Sprintf("Node %s is not ready.", node.Hostname))
			return list, nil
		}

		current := live.Status.NodeInfo.KubeletVersion
		parsed, parseErr := version.ParseSemantic(current)
		if parseErr != nil {
			autoCheckRecord(&list, AutoCheckNodes, autoOutcomeStop, fmt.Sprintf("The version %s of node %s could not be parsed.", current, node.Hostname))
			return list, nil
		}

		versions = append(versions, current)
		versionsByNode[node.Hostname] = current
		if node.Group == shared.GroupControlPlane {
			controlPlaneVersions = append(controlPlaneVersions, parsed)
		} else {
			workerVersions = append(workerVersions, parsed)
		}
	}
	autoCheckRecord(&list, AutoCheckNodes, autoOutcomeProceed, fmt.Sprintf("All %d nodes are ready.", len(record.Nodes)))

	proceed, err = autoQuorumCheck(ctx, clientset, record, &list, "an upgrade")
	if err != nil {
		return list, err
	}
	if !proceed {
		return list, nil
	}

	target, targetProblem := autoUpgradeTarget(versions, now)
	if targetProblem != "" {
		autoCheckRecord(&list, AutoCheckPath, autoOutcomeStop, targetProblem)
		return list, nil
	}
	list.target = target

	needsUpgrade := []shared.ClusterNodeRecord{}
	for _, node := range record.Nodes {
		current := versionsByNode[node.Hostname]
		if shared.SameRelease(current, target.Release) {
			continue
		}

		nodeTarget, ok := shared.RecommendedTarget(current, now)
		if !ok {
			autoCheckRecord(&list, AutoCheckPath, autoOutcomeStop, fmt.Sprintf(
				"Node %s runs %s and has no supported upgrade path. It needs a manual multi-step upgrade or a replacement.",
				node.Hostname,
				current,
			))
			return list, nil
		}
		if !shared.SameRelease(nodeTarget.Release, target.Release) {
			autoCheckRecord(&list, AutoCheckPath, autoOutcomeStop, fmt.Sprintf(
				"Node %s runs %s and cannot reach %s. Its next supported step is %s.",
				node.Hostname,
				current,
				target.Release,
				nodeTarget.Release,
			))
			return list, nil
		}

		needsUpgrade = append(needsUpgrade, node)
	}

	spreadErr := upgradeCheckControlPlaneSpread(controlPlaneVersions)
	if spreadErr != nil {
		autoCheckRecord(&list, AutoCheckVersions, autoOutcomeStop, spreadErr.Error())
		return list, nil
	}

	skewErr := upgradeCheckWorkerSkew(workerVersions, controlPlaneVersions)
	if skewErr != nil {
		autoCheckRecord(&list, AutoCheckVersions, autoOutcomeStop, skewErr.Error())
		return list, nil
	}
	autoCheckRecord(&list, AutoCheckVersions, autoOutcomeProceed, "")

	if len(needsUpgrade) == 0 {
		autoCheckRecord(&list, AutoCheckPath, autoOutcomeComplete, fmt.Sprintf("All nodes already run %s.", target.Release))
		return list, nil
	}

	probes := autoUpgradeAgentProbes(ctx, needsUpgrade)
	for _, probe := range probes {
		if probe.err != nil {
			autoCheckRecord(&list, AutoCheckAgents, autoOutcomeWait, fmt.Sprintf("The node agent of %s is not reachable.", probe.node.Hostname))
			return list, nil
		}
		if probe.status.NodeName != probe.node.Hostname {
			autoCheckRecord(&list, AutoCheckAgents, autoOutcomeStop, fmt.Sprintf(
				"The node agent at %s reports node %s, but %s was requested.",
				probe.node.IpAddress,
				probe.status.NodeName,
				probe.node.Hostname,
			))
			return list, nil
		}
		if shared.NodeAgentPhaseActive(probe.status.Phase) {
			autoCheckRecord(&list, AutoCheckAgents, autoOutcomeWait, fmt.Sprintf("The node agent of %s is busy with another operation.", probe.node.Hostname))
			return list, nil
		}
		if probe.status.Phase == shared.NodeAgentPhaseFailed && !shared.SameRelease(probe.status.Release, target.Release) {
			autoCheckRecord(&list, AutoCheckAgents, autoOutcomeStop, fmt.Sprintf(
				"The node agent of %s failed an upgrade to %s. The target does not match.",
				probe.node.Hostname,
				probe.status.Release,
			))
			return list, nil
		}
	}
	autoCheckRecord(&list, AutoCheckAgents, autoOutcomeProceed, fmt.Sprintf("All %d node agents are reachable and idle.", len(needsUpgrade)))

	autoCheckRecord(&list, AutoCheckPath, autoOutcomeProceed, fmt.Sprintf(
		"%d of %d nodes need an upgrade to %s.",
		len(needsUpgrade),
		len(record.Nodes),
		target.Release,
	))
	return list, nil
}

func autoOperationsCheck(
	record shared.ClusterRecord,
	snapshot map[string]Operation,
	list *autoCheckList,
) (bool, error) {
	for _, node := range record.Nodes {
		operation, present := snapshot[node.Hostname]
		if !present {
			continue
		}
		if PhaseActive(operation.Phase) {
			autoCheckRecord(list, AutoCheckOperations, autoOutcomeWait, fmt.Sprintf("Node %s has an active maintenance operation.", node.Hostname))
			return false, nil
		}
		if operationBlockedByRecovery(operation) {
			autoCheckRecord(list, AutoCheckOperations, autoOutcomeStop, autoRecoveryRequiredDetail(node.Hostname, operation))
			return false, nil
		}
		if operation.HostTerminationUnverified {
			autoCheckRecord(list, AutoCheckOperations, autoOutcomeStop, fmt.Sprintf(
				"Node %s has an unverified host termination from a previous force delete. Resolve it, then press Resume automatic maintenance.",
				node.Hostname,
			))
			return false, nil
		}
	}
	autoCheckRecord(list, AutoCheckOperations, autoOutcomeProceed, "")
	return true, nil
}

func autoRecoveryRequiredDetail(nodeName string, operation Operation) string {
	if operation.Kind == KindOsUpdate {
		return fmt.Sprintf(
			"Node %s requires operating-system update recovery. Retry the update or uncordon the node.",
			nodeName,
		)
	}
	return fmt.Sprintf(
		"Node %s requires upgrade recovery. Retry the failed upgrade or uncordon the node, then press Resume automatic maintenance.",
		nodeName,
	)
}

func autoQuorumCheck(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	record shared.ClusterRecord,
	list *autoCheckList,
	operationLabel string,
) (bool, error) {
	controlPlanes := controlPlaneNames(record)

	if len(controlPlanes) == 2 {
		autoCheckRecord(list, AutoCheckQuorum, autoOutcomeStop, fmt.Sprintf(
			"A cluster with two control plane nodes cannot preserve the etcd quorum during %s. Add or remove a control plane node first.",
			operationLabel,
		))
		return false, nil
	}

	if len(controlPlanes) > 2 {
		required := len(controlPlanes)/2 + 1
		remaining, remainingErr := controlPlaneRemainingCount(ctx, clientset, controlPlanes, "", map[string]bool{})
		if remainingErr != nil {
			return false, fmt.Errorf("could not count the control plane nodes: %s", remainingErr)
		}
		if remaining < required {
			autoCheckRecord(list, AutoCheckQuorum, autoOutcomeStop, fmt.Sprintf(
				"At least %d healthy, schedulable control plane nodes must remain during %s, but only %d were found.",
				required,
				operationLabel,
				remaining,
			))
			return false, nil
		}
	}

	autoCheckRecord(list, AutoCheckQuorum, autoOutcomeProceed, "")
	return true, nil
}

func autoUpgradeBackupGate(
	client stackClient,
	maxAge time.Duration,
	runStartedAt time.Time,
	now time.Time,
) (AutoCheck, autoOutcome, error) {
	check := AutoCheck{Name: AutoCheckBackup, Ok: true}

	if maxAge <= 0 {
		maxAge = autoDefaultMaxBackupAge
	}

	stateRecord, err := backupStateRead(client)
	if err != nil {
		return check, autoOutcomeProceed, err
	}

	fresh := stateRecord.Found &&
		stateRecord.Value.LastSuccess.Phase == backup.AttemptSuccess &&
		!stateRecord.Value.LastSuccess.FinishedAt.IsZero() &&
		now.Sub(stateRecord.Value.LastSuccess.FinishedAt) <= maxAge
	if fresh {
		check.Detail = fmt.Sprintf(
			"The last successful backup is %s old.",
			autoDurationLabel(now.Sub(stateRecord.Value.LastSuccess.FinishedAt)),
		)
		return check, autoOutcomeProceed, nil
	}

	if stateRecord.Found && stateRecord.Value.ClaimUid != "" && now.Before(stateRecord.Value.ClaimDeadline) {
		check.Ok = false
		check.Detail = "Waiting for a running backup to finish."
		return check, autoOutcomeWait, nil
	}

	requestRecord, err := backupRequestRead(client)
	if err != nil {
		return check, autoOutcomeProceed, err
	}

	request := requestRecord.Value
	if requestRecord.Found && request.Uid != "" {
		if request.Phase == backup.RequestPending || request.Phase == backup.RequestRunning {
			check.Ok = false
			check.Detail = "Waiting for the requested backup to finish."
			return check, autoOutcomeWait, nil
		}
		if request.Phase == backup.RequestFailed && request.Requester == autoBackupRequester && request.RequestedAt.After(runStartedAt) {
			check.Ok = false
			message := request.Error
			if message == "" {
				message = "no reason was recorded"
			}
			check.Detail = fmt.Sprintf("The fresh backup failed: %s.", message)
			return check, autoOutcomeStop, nil
		}
	}

	newRequest := backup.Request{
		SchemaRevision: backup.SchemaRevision,
		Uid:            shared.NewUid(),
		Requester:      autoBackupRequester,
		RequestedAt:    now.UTC(),
		Phase:          backup.RequestPending,
	}

	_, writeErr := backupRequestWrite(client, newRequest, requestRecord.Revision)
	if writeErr != nil && !shared.IsConflict(writeErr) {
		return check, autoOutcomeProceed, writeErr
	}

	check.Ok = false
	check.Detail = "Waiting for a fresh backup; a backup was requested."
	return check, autoOutcomeWait, nil
}

var errAutoRunSuperseded = errors.New("the automatic run state was already changed by another writer")

type autoRunWrite struct {
	client   stackClient
	state    autoStateRecord
	policy   AutoPolicy
	claimUid string
	now      time.Time
}

func autoRunPersist(write *autoRunWrite, mutate func(state *AutoState)) (autoStateRecord, error) {
	state := write.state.Value
	if write.claimUid != "" && state.ClaimUid != write.claimUid {
		return write.state, errAutoRunSuperseded
	}
	state.SchemaRevision = AutoSchemaRevision
	if start, _, ok := AutoEffectiveWindow(write.now, write.policy); ok {
		state.NextWindowAt = start
	}
	if write.claimUid != "" {
		state.ClaimDeadline = time.Now().UTC().Add(autoClaimLease)
	}
	mutate(&state)

	revision, err := autoStateWrite(write.client, state, write.state.Revision)
	if shared.IsConflict(err) {
		return write.state, errAutoRunSuperseded
	}
	if err != nil {
		return write.state, err
	}
	write.state = autoStateRecord{Found: true, Value: state, Revision: revision}
	return write.state, nil
}

func autoRunWaiting(write *autoRunWrite, phase string, checks []AutoCheck, target string) (autoStateRecord, error) {
	run := write.state.Value.LastRun
	if run.Phase == phase && autoChecksEqual(run.Checks, checks) && (target == "" || run.TargetRelease == target) {
		return write.state, nil
	}

	return autoRunPersist(write, func(state *AutoState) {
		run := &state.LastRun
		run.Phase = phase
		run.Checks = checks
		if target != "" {
			run.TargetRelease = target
			if run.Channel != AutoChannelOs {
				run.K8sRelease = target
			}
		}
	})
}

func autoRunWaitingOrBlocked(
	write *autoRunWrite,
	phase string,
	checks []AutoCheck,
	target string,
	detail string,
) (autoStateRecord, error) {
	if autoUpgradeRunWaitedTooLong(write.state.Value.LastRun, write.now) {
		message := detail
		if message == "" {
			message = "The run could not proceed."
		}

		return autoRunTerminal(
			write,
			AutoPhaseBlocked,
			checks,
			target,
			fmt.Sprintf(
				"%s The run waited longer than %s. Resolve the problem, then press Resume automatic maintenance.",
				message,
				autoDurationLabel(autoManualWaitLimit),
			),
		)
	}

	return autoRunWaiting(write, phase, checks, target)
}

func autoRunTerminal(
	write *autoRunWrite,
	phase string,
	checks []AutoCheck,
	target string,
	message string,
) (autoStateRecord, error) {
	return autoRunPersist(write, func(state *AutoState) {
		run := &state.LastRun
		run.Phase = phase
		run.Checks = checks
		if target != "" {
			run.TargetRelease = target
			if run.Channel != AutoChannelOs {
				run.K8sRelease = target
			}
		}
		if run.Channel != AutoChannelOs && phase == AutoPhaseCompleted {
			if run.K8sRelease == "" {
				run.K8sRelease = run.TargetRelease
			}
			run.K8sCompletedAt = write.now.UTC()
		}
		run.Error = message
		run.FinishedAt = write.now.UTC()
	})
}

func autoRunAdvance(write *autoRunWrite, ctx context.Context, kubeconfigPath string, record shared.ClusterRecord) error {
	run := write.state.Value.LastRun
	if run.Channel == AutoChannelOs {
		return autoOsAdvance(write, ctx, kubeconfigPath, record)
	}
	if run.Phase == AutoPhaseRunning {
		return autoRunWatch(write)
	}

	rolling, _, err := rollingUpgradeRead(write.client)
	if err != nil {
		return err
	}
	if run.Uid != "" && rolling.AutoRunUid == run.Uid {
		if _, err = autoRunWaiting(write, AutoPhaseRunning, run.Checks, rolling.TargetRelease); err != nil {
			return err
		}
		return autoRunWatch(write)
	}
	if run.Trigger == AutoTriggerWindow && (!write.policy.K8s.Enabled || !AutoScheduleOpen(time.Now().UTC(), write.policy)) {
		return nil
	}
	if run.Uid == "" {
		if _, err = autoRunPersist(write, func(state *AutoState) {
			state.LastRun.Uid = shared.NewUid()
		}); err != nil {
			return err
		}
	}
	return autoRunPrepare(write, ctx, kubeconfigPath, record)
}

func autoRunStart(
	write *autoRunWrite,
	ctx context.Context,
	kubeconfigPath string,
	record shared.ClusterRecord,
	trigger string,
	windowStart time.Time,
	windowEnd time.Time,
) error {
	_, err := autoRunPersist(write, func(state *AutoState) {
		state.LastRun = AutoRun{
			Uid:          shared.NewUid(),
			Channel:      AutoChannelK8s,
			Trigger:      trigger,
			Phase:        AutoPhasePending,
			WindowStart:  windowStart.UTC(),
			WindowEnd:    windowEnd.UTC(),
			StartedAt:    write.now.UTC(),
			ManualWindow: windowStart.Equal(write.policy.TestWindowStart) && AutoTestWindowOpen(write.now, write.policy),
		}
		state.LastRun.K8sRunUid = state.LastRun.Uid
	})
	if err != nil {
		return err
	}

	return autoRunPrepare(write, ctx, kubeconfigPath, record)
}

func autoRunPrepare(write *autoRunWrite, ctx context.Context, kubeconfigPath string, record shared.ClusterRecord) error {
	rolling, _, err := rollingUpgradeRead(write.client)
	if err != nil {
		return err
	}
	if PhaseActive(rolling.Phase) {
		detail := fmt.Sprintf("A rolling upgrade to %s is already active. The run waits for it to finish.", rolling.TargetRelease)
		checks := []AutoCheck{{Name: AutoCheckRolling, Ok: false, Detail: detail}}
		_, err = autoRunWaitingOrBlocked(write, AutoPhasePreflight, checks, "", detail)
		return err
	}

	clientset, err := newClient(kubeconfigPath)
	if err != nil {
		return err
	}

	list, err := autoUpgradeClusterChecks(ctx, clientset, record, write.now)
	if err != nil {
		return err
	}

	target := list.target.Release

	switch list.outcome {
	case autoOutcomeStop:
		_, err = autoRunTerminal(write, AutoPhaseBlocked, list.checks, target, list.detail)
		return err
	case autoOutcomeWait:
		_, err = autoRunWaitingOrBlocked(write, AutoPhasePreflight, list.checks, target, list.detail)
		return err
	case autoOutcomeComplete:
		_, err = autoRunTerminal(write, AutoPhaseCompleted, list.checks, target, "")
		return err
	}

	backupCheck, backupOutcome, err := autoUpgradeBackupGate(
		write.client,
		write.policy.K8s.MaxBackupAge,
		write.state.Value.LastRun.StartedAt,
		write.now,
	)
	if err != nil {
		return err
	}

	checks := append(list.checks, backupCheck)

	switch backupOutcome {
	case autoOutcomeStop:
		_, err = autoRunTerminal(write, AutoPhaseBlocked, checks, target, backupCheck.Detail)
		return err
	case autoOutcomeWait:
		_, err = autoRunWaitingOrBlocked(write, AutoPhaseBackup, checks, target, backupCheck.Detail)
		return err
	}

	startErr := rollingUpgradeStart(
		ctx,
		kubeconfigPath,
		target,
		autoUpgradeGroups(record),
		rollingUpgradeStartOptions{
			trigger:    write.state.Value.LastRun.Trigger,
			autoRunUid: write.state.Value.LastRun.Uid,
			conditions: []ucxapi.StackStateRevisionCondition{{
				Key:              AutoStateKey,
				ExpectedRevision: write.state.Revision,
			}},
			validUntil: util.OptValue(write.state.Value.ClaimDeadline.UnixMilli()),
		},
	)
	if errors.Is(startErr, errAutoSchedulePaused) {
		return nil
	}
	if startErr != nil {
		rolling, _, readErr := rollingUpgradeRead(write.client)
		if readErr == nil && rolling.AutoRunUid == write.state.Value.LastRun.Uid {
			_, err = autoRunWaiting(write, AutoPhaseRunning, checks, target)
			return err
		}
		detail := fmt.Sprintf("The rolling upgrade could not start yet: %s.", startErr)
		checks = append(checks, AutoCheck{Name: AutoCheckRolling, Ok: false, Detail: detail})
		_, err = autoRunWaitingOrBlocked(write, AutoPhasePreflight, checks, target, detail)
		return err
	}

	_, err = autoRunWaiting(write, AutoPhaseRunning, checks, target)
	return err
}

func autoRunWatch(write *autoRunWrite) error {
	upgrade, _, err := rollingUpgradeRead(write.client)
	if err != nil {
		return err
	}

	run := write.state.Value.LastRun

	if upgrade.Uid == "" {
		_, err = autoRunTerminal(
			write,
			AutoPhaseBlocked,
			run.Checks,
			run.TargetRelease,
			"The rolling upgrade record is missing. Check the rolling upgrade page, then resume automatic maintenance.",
		)
		return err
	}

	if run.Uid != "" && upgrade.AutoRunUid != run.Uid {
		_, err = autoRunTerminal(write, AutoPhaseBlocked, run.Checks, run.TargetRelease,
			"The rolling upgrade was replaced. Check the rolling upgrade page, then resume automatic maintenance.")
		return err
	}

	if run.TargetRelease != "" && !shared.SameRelease(upgrade.TargetRelease, run.TargetRelease) {
		_, err = autoRunTerminal(
			write,
			AutoPhaseBlocked,
			run.Checks,
			run.TargetRelease,
			fmt.Sprintf(
				"The rolling upgrade changed to %s, which is not the run's target. Check the rolling upgrade page, then resume automatic maintenance.",
				upgrade.TargetRelease,
			),
		)
		return err
	}

	if PhaseActive(upgrade.Phase) {
		return nil
	}

	if upgrade.Phase == PhaseCompleted {
		_, err = autoRunTerminal(write, AutoPhaseCompleted, run.Checks, run.TargetRelease, "")
		return err
	}

	message := upgrade.Error
	if message == "" {
		message = "The rolling upgrade stopped without recording a reason."
	}

	_, err = autoRunTerminal(write, AutoPhaseBlocked, run.Checks, run.TargetRelease, message)
	return err
}

func autoUpgradeRunWaitedTooLong(run AutoRun, now time.Time) bool {
	return run.Trigger == AutoTriggerManual &&
		!run.StartedAt.IsZero() &&
		now.After(run.StartedAt.Add(autoManualWaitLimit))
}

func autoUpgradeGroups(record shared.ClusterRecord) map[string]Options {
	groups := map[string]Options{}
	for _, node := range record.Nodes {
		groups[node.Group] = Options{TimeoutSeconds: defaultTimeoutSeconds}
	}
	return groups
}

func autoUpgradeSweep(ctx context.Context, kubeconfigPath string, client stackClient) error {
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
	policy := AutoPolicyNormalize(policyRecord.Value)

	stateRecord, err := autoStateRead(client)
	if err != nil {
		return err
	}

	now := time.Now().UTC()
	if autoStateClaimActive(stateRecord.Value, now) {
		return nil
	}

	run := stateRecord.Value.LastRun
	active := AutoRunActive(run)
	resumed := AutoStateResumed(stateRecord.Value)
	windowStart, windowEnd, _ := autoPolicyWindowOpenNow(now, policy)

	if !active {
		if AutoStateBlocked(stateRecord.Value) || !AutoScheduleOpen(now, policy) {
			return nil
		}
		if !autoChannelStartAllowed(run, policy, windowStart, resumed) {
			return nil
		}
	} else {
		if run.Channel == AutoChannelOs {
			if run.Phase == AutoPhaseRunning {
				snapshot, snapErr := Snapshot()
				if snapErr != nil {
					return nil
				}
				if operation, found := autoOsRunOperation(snapshot, run); found && PhaseActive(operation.Phase) {
					return nil
				}
			} else if run.Trigger == AutoTriggerWindow && !AutoScheduleOpen(now, policy) {
				return nil
			}
		} else {
			rolling, _, readErr := rollingUpgradeRead(client)
			if readErr != nil {
				return readErr
			}
			owned := run.Uid != "" && rolling.AutoRunUid == run.Uid
			if run.Phase == AutoPhaseRunning {
				if PhaseActive(rolling.Phase) && (owned || run.Uid == "") {
					return nil
				}
			} else if !owned && run.Trigger == AutoTriggerWindow && (!policy.K8s.Enabled || !AutoScheduleOpen(now, policy)) {
				return nil
			}
		}
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

	write := &autoRunWrite{
		client:   client,
		state:    stateRecord,
		policy:   policy,
		claimUid: claimUid,
		now:      now,
	}

	if active {
		return autoRunAdvance(write, ctx, kubeconfigPath, record)
	}

	if autoWindowChannel(run, policy, windowStart, resumed) == AutoChannelOs {
		return autoOsStart(write, ctx, kubeconfigPath, record, windowStart, windowEnd)
	}
	return autoRunStart(write, ctx, kubeconfigPath, record, AutoTriggerWindow, windowStart, windowEnd)
}

func autoUpgradeReleaseClaim(client stackClient, claimUid string) {
	record, err := autoStateRead(client)
	if err != nil {
		log.Warn("k8s-app automatic maintenance: could not read the state to release the claim: %s", err)
		return
	}
	_, err = autoStateRelease(client, record, claimUid)
	if err != nil && !shared.IsConflict(err) {
		log.Warn("k8s-app automatic maintenance: could not release the claim: %s", err)
	}
}

func AutoStopRun() error {
	client, err := stackClientNew()
	if err != nil {
		return err
	}

	record, err := autoStateRead(client)
	if err != nil {
		return err
	}

	run := record.Value.LastRun
	if !AutoRunActive(run) || run.Phase == AutoPhaseRunning {
		return errors.New("no waiting automatic run can be stopped")
	}

	conditions := []ucxapi.StackStateRevisionCondition{}
	if run.Channel != AutoChannelOs {
		rolling, rollingRevision, err := rollingUpgradeRead(client)
		if err != nil {
			return err
		}
		if PhaseActive(rolling.Phase) {
			return errors.New("a rolling upgrade already started and cannot be stopped; it will finish")
		}
		conditions = append(conditions, ucxapi.StackStateRevisionCondition{
			Key:              rollingUpgradeKey,
			ExpectedRevision: rollingRevision,
		})
	}

	state := record.Value
	state.SchemaRevision = AutoSchemaRevision
	state.LastRun.Phase = AutoPhaseBlocked
	state.LastRun.Error = "Stopped by the cluster owner. Press Resume automatic maintenance to let the schedule start a new run."
	state.LastRun.FinishedAt = time.Now().UTC()
	value, err := json.Marshal(state)
	if err != nil {
		return err
	}
	_, err = shared.RpcGrantStateWriteChecked(
		client,
		AutoStateKey,
		value,
		record.Revision,
		conditions,
		nil,
		util.OptNone[int64](),
	)
	if shared.IsConflict(err) {
		return errors.New("the automatic run or rolling upgrade changed while stopping it; please try again")
	}
	return err
}

func AutoResume() error {
	client, err := stackClientNew()
	if err != nil {
		return err
	}

	for attempt := 0; attempt < 3; attempt++ {
		record, err := autoStateRead(client)
		if err != nil {
			return err
		}
		if !AutoStateBlocked(record.Value) {
			return nil
		}
		state := record.Value
		state.SchemaRevision = AutoSchemaRevision
		state.ResumedAt = time.Now().UTC()
		_, err = autoStateWrite(client, state, record.Revision)
		if !shared.IsConflict(err) {
			if err == nil {
				autoMaintenanceWakeSignal()
			}
			return err
		}
	}

	return errors.New("the automatic maintenance state changed while resuming; please try again")
}
