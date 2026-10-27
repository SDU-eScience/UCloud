package command

import (
	"context"
	"errors"
	"fmt"
	"sort"
	"time"

	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/ucx"
)

const (
	k8sWaitTimeout      = 30 * time.Minute
	k8sWaitPollInterval = 5 * time.Second
)

func k8sClusterFailure(response k8sapi.StatusResponse, name string) error {
	return k8sClusterFailureReason(response.Phase, response.FailureReason, name)
}

func k8sProvisioningFailure(response k8sapi.ProvisioningStatusResponse, name string) error {
	return k8sClusterFailureReason(response.Phase, response.FailureReason, name)
}

func k8sProvisioningNodeFailure(
	response k8sapi.ProvisioningStatusResponse,
	name string,
	include func(k8sapi.ProvisioningNodeStatus) bool,
) error {
	for _, node := range response.Nodes {
		if include != nil && !include(node) {
			continue
		}
		if node.Status != k8sapi.ProvisioningStatusFailed {
			continue
		}
		return fmt.Errorf(
			"the node %s failed to provision; use the dashboard at %s",
			node.Hostname,
			k8sValueOrDash(k8sDashboardURL(name)),
		)
	}
	return nil
}

func k8sClusterFailureReason(phase string, failureReason string, name string) error {
	if phase != k8sapi.ClusterPhaseError &&
		phase != k8sapi.ClusterPhaseCleanup &&
		failureReason == "" {
		return nil
	}

	reason := failureReason
	if reason == "" {
		reason = "the cluster entered the error phase"
	}
	return fmt.Errorf(
		"the cluster did not become ready: %s; use the dashboard at %s",
		reason,
		k8sValueOrDash(k8sDashboardURL(name)),
	)
}

func k8sWaitSleep(ctx context.Context) bool {
	timer := time.NewTimer(k8sWaitPollInterval)
	defer timer.Stop()

	select {
	case <-ctx.Done():
		return false
	case <-timer.C:
		return true
	}
}

func k8sWaitTimeoutError(name string) error {
	return fmt.Errorf(
		"the cluster did not become ready within %s; use the dashboard at %s",
		k8sWaitTimeout,
		k8sValueOrDash(k8sDashboardURL(name)),
	)
}

func k8sPoolWaitTimeoutError(name string, pool string) error {
	return fmt.Errorf(
		"the pool %s did not reach the target within %s; use the dashboard at %s",
		pool,
		k8sWaitTimeout,
		k8sValueOrDash(k8sDashboardURL(name)),
	)
}

func k8sWaitRpcNotFound(err error) bool {
	var rpcErr ucx.RpcCallError
	return errors.As(err, &rpcErr) && rpcErr.Status == ucx.RpcStatusNotFound
}

func k8sNodeStateLabel(state string) string {
	switch state {
	case k8sapi.NodeStateReady:
		return "ready"
	case k8sapi.NodeStateNotReady:
		return "not ready"
	case k8sapi.NodeStateUnknown:
		return "unknown"
	default:
		return "pending"
	}
}

func k8sWaitSetProgress(
	progress *termio.ProgressList,
	response k8sapi.ProvisioningStatusResponse,
	stages *k8sJobStages,
	include func(k8sapi.ProvisioningNodeStatus) bool,
) {
	nodes := make(map[string]k8sapi.ProvisioningNodeStatus, len(response.Nodes))
	for _, node := range response.Nodes {
		if include != nil && !include(node) {
			continue
		}
		nodes[node.Hostname] = node
	}

	stages.SetListener(func(hostname string, stage k8sJobStage) {
		node, ok := nodes[hostname]
		if !ok {
			return
		}
		progress.SetItem(k8sWaitItemWithStage(node.Hostname, node.Status, node.State, stage, true))
	})

	progress.SetItems(k8sWaitProgressItems(response, stages, include))
}

func k8sWaitSetJobProgress(
	progress *termio.ProgressList,
	stack orcapi.Stack,
	stages *k8sJobStages,
) {
	jobs := k8sWaitStackJobs(stack)
	byHostname := make(map[string]orcapi.Job, len(jobs))
	for _, job := range jobs {
		byHostname[k8sWaitJobName(job)] = job
	}

	stages.SetListener(func(hostname string, stage k8sJobStage) {
		job, ok := byHostname[hostname]
		if !ok {
			return
		}

		status := k8sapi.ProvisioningStatusFromJobState(job.Status.State)
		if status == "" {
			status = k8sapi.ProvisioningStatusProvisioning
		}
		progress.SetItem(k8sWaitItemWithStage(hostname, status, "", stage, true))
	})

	progress.SetItems(k8sWaitJobItems(jobs, stages))
}

func k8sWaitProgressItems(
	response k8sapi.ProvisioningStatusResponse,
	stages *k8sJobStages,
	include func(k8sapi.ProvisioningNodeStatus) bool,
) []termio.ProgressItem {
	items := make([]termio.ProgressItem, 0, len(response.Nodes))
	for _, node := range response.Nodes {
		if include != nil && !include(node) {
			continue
		}

		stages.Track(node.Hostname, node.JobId)
		items = append(items, k8sWaitItem(node.Hostname, node.Status, node.State, stages))
	}
	return items
}

func k8sWaitStackJobs(stack orcapi.Stack) []orcapi.Job {
	stackStatus := stack.Status.GetOrDefault(orcapi.StackStatus{})

	jobs := make([]orcapi.Job, 0, len(stackStatus.Jobs))
	for _, job := range stackStatus.Jobs {
		if job.Specification.Labels[k8sapi.LabelNodeGroup] == "" {
			continue
		}
		if k8sWaitJobName(job) == "" {
			continue
		}
		jobs = append(jobs, job)
	}

	sort.SliceStable(jobs, func(i, j int) bool {
		left := jobs[i]
		right := jobs[j]
		leftGroup := left.Specification.Labels[k8sapi.LabelNodeGroup]
		rightGroup := right.Specification.Labels[k8sapi.LabelNodeGroup]

		if leftGroup != rightGroup {
			if leftGroup == k8sapi.GroupControlPlane {
				return true
			}
			if rightGroup == k8sapi.GroupControlPlane {
				return false
			}
			return leftGroup < rightGroup
		}
		return k8sWaitJobName(left) < k8sWaitJobName(right)
	})

	return jobs
}

func k8sWaitJobItems(jobs []orcapi.Job, stages *k8sJobStages) []termio.ProgressItem {
	items := make([]termio.ProgressItem, 0, len(jobs))
	for _, job := range jobs {
		hostname := k8sWaitJobName(job)
		stages.Track(hostname, job.Id)

		status := k8sapi.ProvisioningStatusFromJobState(job.Status.State)
		if status == "" {
			status = k8sapi.ProvisioningStatusProvisioning
		}

		items = append(items, k8sWaitItem(hostname, status, "", stages))
	}
	return items
}

func k8sWaitItem(
	hostname string,
	status string,
	state string,
	stages *k8sJobStages,
) termio.ProgressItem {
	stage, hasStage := stages.Stage(hostname)
	return k8sWaitItemWithStage(hostname, status, state, stage, hasStage)
}

func k8sWaitItemWithStage(
	hostname string,
	status string,
	state string,
	stage k8sJobStage,
	hasStage bool,
) termio.ProgressItem {
	if hasStage && stage.Text != "" &&
		status != k8sapi.ProvisioningStatusFailed &&
		status != k8sapi.ProvisioningStatusPoweredOff {
		progressState := termio.ProgressRunning
		progress := stage.Progress
		known := stage.Known

		if state == k8sapi.NodeStateReady || (status == k8sapi.ProvisioningStatusReady && state == "") {
			progressState = termio.ProgressSuccess
			progress = 1
			known = true
		}

		return termio.ProgressItem{
			Key:      hostname,
			Label:    hostname,
			Status:   stage.Text,
			Progress: progress,
			Known:    known,
			State:    progressState,
		}
	}

	label, progressState, progress, known := k8sProvisioningPresentation(status, state)
	return termio.ProgressItem{
		Key:      hostname,
		Label:    hostname,
		Status:   label,
		Progress: progress,
		Known:    known,
		State:    progressState,
	}
}

func k8sWaitJobName(job orcapi.Job) string {
	if job.Specification.Name != "" {
		return job.Specification.Name
	}
	return job.Specification.Hostname.GetOrDefault("")
}

func k8sProvisioningPresentation(
	status string,
	state string,
) (string, termio.ProgressState, float64, bool) {
	if status == k8sapi.ProvisioningStatusReady {
		switch state {
		case k8sapi.NodeStateReady, "":
			return "ready", termio.ProgressSuccess, 1, true
		case k8sapi.NodeStateNotReady:
			return "not ready", termio.ProgressWarning, 0, false
		default:
			return k8sNodeStateLabel(state), termio.ProgressWarning, 0, false
		}
	}

	switch status {
	case k8sapi.ProvisioningStatusWaitingForResources:
		return status, termio.ProgressRunning, 0, false
	case k8sapi.ProvisioningStatusInitializing:
		return status, termio.ProgressRunning, 0, false
	case k8sapi.ProvisioningStatusPoweredOff:
		return status, termio.ProgressWarning, 0, false
	case k8sapi.ProvisioningStatusFailed:
		return status, termio.ProgressFailure, 1, true
	default:
		return k8sapi.ProvisioningStatusProvisioning, termio.ProgressRunning, 0, false
	}
}
