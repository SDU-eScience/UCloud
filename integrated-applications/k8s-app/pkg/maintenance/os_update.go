package maintenance

import (
	"context"
	"errors"
	"fmt"
	"time"

	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"

	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/log"
)

const osUpdateStatusPollInterval = 5 * time.Second

// Operating-system updates
// =====================================================================================================================

func osUpdateFailureRecoveryMessage() string {
	return "Retry the update when the node is healthy. The node stays cordoned until then."
}

func runOsUpdate(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	operation Operation,
	worker *nodeWorker,
) error {
	if !operation.ExecutorSubmitted {
		preflightErr := osUpdatePreflight(ctx, clientset, operation)
		if preflightErr != nil {
			return preflightErr
		}
		staged, stageErr := osUpdateStage(ctx, operation, worker)
		if stageErr != nil {
			return stageErr
		}
		if !staged {
			return nil
		}
		if err := worker.mutate(func(op *Operation) bool {
			if op.OsPackagesStaged {
				return false
			}
			op.OsPackagesStaged = true
			return true
		}); err != nil {
			return err
		}
		if err := osUpdatePreflight(ctx, clientset, operation); err != nil {
			return err
		}

		result := runCordonDrain(ctx, clientset, operation, worker, false)
		if result.err != nil {
			return result.err
		}
		if result.outcome != drainDone {
			return nil
		}

		operationLogStage(worker, "submitting")

		current, ok := worker.checkpoint()
		if !ok {
			return nil
		}
		operation = current
	}

	for {
		done := osUpdateDriveRemote(ctx, clientset, operation, worker)
		if done {
			return nil
		}

		current, ok := worker.checkpoint()
		if !ok {
			return nil
		}
		operation = current

		if !waitInterruptible(worker, osUpdateStatusPollInterval) {
			return nil
		}
	}
}

func osUpdatePreflight(ctx context.Context, clientset *kubernetes.Clientset, operation Operation) error {
	nodeName := operation.NodeName

	if operation.Options.ForceDelete {
		return errors.New("the operating-system update cannot delete pods forcefully")
	}

	if operation.Options.BypassDisruptionBudgets {
		return errors.New("the operating-system update cannot bypass the disruption budgets")
	}

	if operation.HostTerminationUnverified {
		return errors.New("pods may still be running on this node after a previous force delete. The node cannot be updated")
	}

	record, found, err := readClusterRecord()
	if err != nil {
		return fmt.Errorf("could not read the cluster record: %s", err)
	}
	if !found {
		return errors.New("the cluster record does not exist")
	}

	if record.Phase != "created" {
		return fmt.Errorf("the cluster is not ready (state: %s)", record.Phase)
	}

	nodeRecord, nodeKnown := upgradeNodeRecord(record, nodeName)
	if !nodeKnown {
		return errors.New("the node is not part of this cluster")
	}

	node, err := clientset.CoreV1().Nodes().Get(ctx, nodeName, metav1.GetOptions{})
	if err != nil {
		return fmt.Errorf("could not read the node: %s", err)
	}

	if string(node.UID) != operation.NodeUid {
		return errors.New("the node uid does not match the requested node")
	}

	if !nodeIsReady(node) {
		return errors.New("the node is not ready")
	}

	guardErr := upgradeControlPlaneGuard(ctx, clientset, operation)
	if guardErr != nil {
		return guardErr
	}

	status, statusErr := shared.NodeAgentClientOsStatus(ctx, nodeName, nodeRecord.IpAddress)
	if statusErr != nil {
		return fmt.Errorf("could not check the update status of node %s: %s", nodeName, statusErr)
	}

	if status.NodeName != nodeName {
		return fmt.Errorf(
			"the maintenance service at %s reports node %s, but %s was requested",
			nodeRecord.IpAddress,
			status.NodeName,
			nodeName,
		)
	}

	if status.NeedsStateRecovery {
		return fmt.Errorf(
			"the operating-system update status of node %s cannot be read. Contact support before retrying",
			nodeName,
		)
	}

	if !status.StagingSupported {
		return errors.New("the node cannot prepare updates before draining. Contact support before retrying")
	}
	upgradeStatus, upgradeStatusErr := shared.NodeAgentClientStatus(ctx, nodeName, nodeRecord.IpAddress)
	if upgradeStatusErr != nil {
		return fmt.Errorf("could not check the upgrade status of node %s: %s", nodeName, upgradeStatusErr)
	}
	if shared.NodeAgentPhaseActive(upgradeStatus.Phase) {
		return fmt.Errorf("an upgrade is running on %s", nodeName)
	}

	return nil
}

func osUpdateStage(ctx context.Context, operation Operation, worker *nodeWorker) (bool, error) {
	for {
		if _, ok := worker.checkpoint(); !ok {
			return false, nil
		}
		nodeIp, err := upgradeNodeIp(worker, operation)
		if err != nil {
			return false, err
		}
		status, err := shared.NodeAgentClientOsStatus(ctx, operation.NodeName, nodeIp)
		if err == nil {
			if status.NodeName != operation.NodeName || status.NeedsStateRecovery {
				return false, errors.New(
					"the node could not confirm which update it is running. Check the node and retry the update",
				)
			}
			operationExecutorProgress(worker, operation.Uid, status.Progress)
			owned := operationSawStatus(operation, status.OperationUid)
			if owned && status.Phase == shared.NodeAgentOsPhaseStaged {
				return true, nil
			}
			if owned && status.Phase == shared.NodeAgentOsPhaseFailed {
				return false, fmt.Errorf("the OS update could not be downloaded on %s: %s", operation.NodeName, status.Error)
			}
			if owned && status.Phase != shared.NodeAgentOsPhaseStaging {
				return false, errors.New(
					"the node already installed the update. Retry the update to run it again",
				)
			}
			if shared.NodeAgentOsPhaseActive(status.Phase) {
				if !owned || status.Phase != shared.NodeAgentOsPhaseStaging {
					return false, errors.New("an OS update is already running on the node")
				}
			} else {
				command := shared.NodeAgentOsCommandStage
				if status.Phase == shared.NodeAgentOsPhaseFailed {
					command = shared.NodeAgentOsCommandStageRetry
				}
				_, err = shared.NodeAgentClientOsCommand(ctx, operation.NodeName, nodeIp,
					shared.NodeAgentOsRequest{Command: command}, operation.Uid)
			}
		}
		if err != nil {
			operationLogStage(worker, "download-retry", fmt.Sprintf("Waiting to download OS updates: %s", err))
		} else {
			operationLogStage(worker, "executor-staging")
		}
		if !waitInterruptible(worker, osUpdateStatusPollInterval) {
			return false, nil
		}
	}
}

func osUpdateDriveRemote(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	operation Operation,
	worker *nodeWorker,
) bool {
	nodeName := operation.NodeName

	nodeIp, ipErr := upgradeNodeIp(worker, operation)
	if ipErr != nil {
		log.Warn("k8s-app maintenance %s: could not resolve the address of the node executor: %s", nodeName, ipErr)
		operationLogStage(
			worker,
			"submit-retry-address",
			fmt.Sprintf("Waiting to find the address of the node: %s", ipErr),
		)
		return false
	}

	status, statusErr := shared.NodeAgentClientOsStatus(ctx, nodeName, nodeIp)
	if statusErr != nil {
		log.Warn("k8s-app maintenance %s: could not read the node executor status: %s", nodeName, statusErr)
		operationLogStage(
			worker,
			"submit-retry-status",
			fmt.Sprintf("Waiting for the node to respond. It may be restarting: %s", statusErr),
		)
		return false
	}

	if status.NodeName != nodeName {
		osUpdateFail(operation, worker, fmt.Sprintf(
			"the node answered as %s, but %s was requested. Check the node and retry the update",
			status.NodeName,
			nodeName,
		))
		return true
	}

	if shared.NodeAgentOsPhaseActive(status.Phase) {
		operationExecutorProgress(worker, operation.Uid, status.Progress)
		operationLogStage(
			worker,
			fmt.Sprintf("executor-%s", status.Phase),
		)
		return false
	}

	if status.NeedsStateRecovery {
		osUpdateFail(operation, worker, fmt.Sprintf(
			"the operating-system update status on node %s cannot be read. Contact support before retrying the update",
			nodeName,
		))
		return true
	}
	operationExecutorProgress(worker, operation.Uid, status.Progress)

	switch status.Phase {
	case shared.NodeAgentOsPhaseStaged:
		return osUpdateSubmit(ctx, operation, worker, nodeIp, status)
	case shared.NodeAgentOsPhaseCompleted:
		if !operationSawStatus(operation, status.OperationUid) {
			return osUpdateSubmit(ctx, operation, worker, nodeIp, status)
		}
		osUpdateComplete(ctx, clientset, operation, worker)
		return true

	case shared.NodeAgentOsPhaseFailed:
		if !operationSawStatus(operation, status.OperationUid) {
			return osUpdateSubmit(ctx, operation, worker, nodeIp, status)
		}

		executorMessage := status.Error
		if executorMessage == "" {
			executorMessage = "the node did not report a reason"
		}
		osUpdateFail(operation, worker, fmt.Sprintf(
			"%s. %s",
			executorMessage,
			osUpdateFailureRecoveryMessage(),
		))
		return true

	case shared.NodeAgentOsPhaseIdle:
		return osUpdateSubmit(ctx, operation, worker, nodeIp, status)

	default:
		osUpdateFail(operation, worker, fmt.Sprintf(
			"the node reported the unexpected state %s. Check the node, then retry the update",
			status.Phase,
		))
		return true
	}
}

func operationSawStatus(operation Operation, operationUid string) bool {
	return operation.Uid == operationUid
}

func osUpdateSubmit(
	ctx context.Context,
	operation Operation,
	worker *nodeWorker,
	nodeIp string,
	status shared.NodeAgentOsStatus,
) bool {
	nodeName := operation.NodeName

	if operation.OsPackagesStaged && (!operationSawStatus(operation, status.OperationUid) || status.Phase != shared.NodeAgentOsPhaseStaged) {
		osUpdateFail(operation, worker, "the downloaded packages were replaced. Retry the update")
		return true
	}

	trafficErr := upgradeSuspendTraffic(operation, worker)
	if trafficErr != nil {
		log.Warn("k8s-app maintenance %s: could not suspend traffic before the operating-system update: %s", nodeName, trafficErr)
		operationLogStage(
			worker,
			"submit-retry-traffic",
			fmt.Sprintf("Waiting to move network traffic away from the node: %s", trafficErr),
		)
		return false
	}

	persistErr := worker.mutate(func(op *Operation) bool {
		if op.ExecutorSubmitted && !op.SubmittedAt.IsZero() {
			return false
		}
		op.ExecutorSubmitted = true
		if op.SubmittedAt.IsZero() {
			op.SubmittedAt = time.Now().UTC()
		}
		return true
	})
	if persistErr != nil {
		log.Warn("k8s-app maintenance %s: could not record the submission to the node executor: %s", nodeName, persistErr)
		operationLogStage(
			worker,
			"submit-retry-persist",
			fmt.Sprintf("Waiting to save the update request: %s", persistErr),
		)
		return false
	}

	command := shared.NodeAgentOsCommandApply
	if status.Phase == shared.NodeAgentOsPhaseFailed {
		command = shared.NodeAgentOsCommandRetry
	}

	_, postErr := shared.NodeAgentClientOsCommand(
		ctx,
		nodeName,
		nodeIp,
		shared.NodeAgentOsRequest{Command: command},
		operation.Uid,
	)
	if postErr != nil {
		log.Warn("k8s-app maintenance %s: could not submit the operating-system update to the node executor: %s", nodeName, postErr)
		operationLogStage(
			worker,
			"submit-retry-post",
			fmt.Sprintf("Waiting to send the update to the node: %s", postErr),
		)
		return false
	}

	operationLogStage(
		worker,
		"submitted",
	)
	return false
}

func osUpdateComplete(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	operation Operation,
	worker *nodeWorker,
) {
	nodeName := operation.NodeName

	log.Info("k8s-app maintenance %s: the node executor completed the operating-system update", nodeName)

	for {
		if worker.ctx.Err() != nil {
			return
		}

		if _, ok := worker.checkpoint(); !ok {
			return
		}

		node, err := upgradeGetNode(worker.ctx, worker, operation, clientset)
		if err != nil {
			log.Warn("k8s-app maintenance %s: could not read the node after the operating-system update: %s", nodeName, err)
			if !waitInterruptible(worker, apiRetryDelay) {
				return
			}
			continue
		}

		if nodeIsReady(node) {
			break
		}

		operationLogStage(
			worker,
			"waiting-ready",
			fmt.Sprintf("Waiting for %s to come back online", nodeName),
		)
		if !waitInterruptible(worker, osUpdateStatusPollInterval) {
			return
		}
	}

	for {
		current, ok := worker.checkpoint()
		if !ok {
			return
		}
		operation = current

		trafficErr := upgradeRestoreTraffic(operation, worker)
		if trafficErr != nil {
			log.Warn("k8s-app maintenance %s: could not restore traffic after the operating-system update: %s", nodeName, trafficErr)
			if !waitInterruptible(worker, apiRetryDelay) {
				return
			}
			continue
		}

		if operation.OriginalUnschedulable {
			log.Info(
				"k8s-app maintenance %s: the node remains cordoned because it was cordoned before the operating-system update",
				nodeName,
			)
			osUpdateFinish(operation, worker)
			return
		}

		if !operation.OriginalSchedulingCaptured {
			log.Warn(
				"k8s-app maintenance %s: the node remains cordoned because the original scheduling state was not recorded",
				nodeName,
			)
			operationLogLines(worker, fmt.Sprintf(
				"The node %s remains cordoned because its original scheduling state was not recorded",
				nodeName,
			))
			osUpdateFinish(operation, worker)
			return
		}

		node, err := upgradeGetNode(worker.ctx, worker, operation, clientset)
		if err != nil {
			log.Warn("k8s-app maintenance %s: could not read the node after the operating-system update: %s", nodeName, err)
			if !waitInterruptible(worker, apiRetryDelay) {
				return
			}
			continue
		}

		if node.Spec.Unschedulable {
			_, patchErr := patchUnschedulable(worker.ctx, clientset, node, false)
			if patchErr != nil {
				log.Warn("k8s-app maintenance %s: could not uncordon the node: %s", nodeName, patchErr)
				if !waitInterruptible(worker, apiRetryDelay) {
					return
				}
				continue
			}
			operationLogLines(worker, fmt.Sprintf("The node %s was uncordoned. It accepts workloads again", nodeName))
		}

		osUpdateFinish(operation, worker)
		return
	}
}

func osUpdateFail(operation Operation, worker *nodeWorker, message string) {
	operationLogStage(
		worker,
		"failed",
		fmt.Sprintf("The OS update of %s stopped: %s", operation.NodeName, message),
	)
	failOperation(worker, message)
}

func osUpdateFinish(operation Operation, worker *nodeWorker) {
	finishErr := finishQuiet(worker, PhaseCompleted, "")
	if errors.Is(finishErr, errConcluded) {
		return
	}
	if finishErr != nil {
		osUpdateFail(operation, worker, fmt.Sprintf(
			"the OS update finished on the node, but the result could not be saved: %s. Retry the update to record the result without draining the node again",
			finishErr,
		))
		return
	}

	operationLogStage(
		worker,
		"completed",
		fmt.Sprintf("The OS update of %s completed", operation.NodeName),
	)

	log.Info("k8s-app maintenance %s: the operating-system update completed", operation.NodeName)
	worker.cancel()
}
