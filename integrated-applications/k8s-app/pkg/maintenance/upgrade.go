package maintenance

import (
	"context"
	"errors"
	"fmt"
	"net"
	"path/filepath"
	"slices"
	"time"

	corev1 "k8s.io/api/core/v1"
	apierrors "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/util/version"
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/tools/clientcmd"

	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
)

const (
	upgradeStatusPollInterval = 5 * time.Second

	trafficServiceName = "k8s-ingress"
)

// Upgrades
// =====================================================================================================================
// An upgrade runs in two stages. The first stage is local only: preflight checks, followed by an optional cordon and
// drain. The second stage drives the node agent remotely and polls its status until the upgrade completes or fails.
// The two stages are split by the ExecutorSubmitted flag, which makes an interrupted upgrade resume at the remote
// stage.

func upgradeSubmitMessage(operation Operation) string {
	if operation.Options.Drain {
		return fmt.Sprintf(
			"The drain of %s completed. The upgrade to %s is being submitted to the node executor",
			operation.NodeName,
			operation.TargetRelease,
		)
	}
	if operation.Options.Cordon {
		return fmt.Sprintf(
			"The node %s is cordoned. The upgrade to %s is being submitted to the node executor",
			operation.NodeName,
			operation.TargetRelease,
		)
	}
	return fmt.Sprintf(
		"The upgrade to %s is being submitted to the node executor",
		operation.TargetRelease,
	)
}

func upgradeFailureRecoveryMessage(operation Operation) string {
	if UpgradeCordons(operation) {
		return "An explicit retry is required and the node remains cordoned"
	}
	return "An explicit retry is required"
}

func upgradeNodeRecord(record shared.ClusterRecord, nodeName string) (shared.ClusterNodeRecord, bool) {
	for _, recorded := range record.Nodes {
		if recorded.Hostname == nodeName {
			return recorded, true
		}
	}
	return shared.ClusterNodeRecord{}, false
}

func runUpgrade(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	operation Operation,
	worker *nodeWorker,
) error {
	if !operation.ExecutorSubmitted {
		preflightErr := upgradePreflight(ctx, clientset, operation)
		if preflightErr != nil {
			return preflightErr
		}

		if UpgradeCordons(operation) {
			outcome, cordonErr := runCordonDrain(ctx, clientset, operation, worker, true, operation.Options.Drain)
			if cordonErr != nil {
				return cordonErr
			}
			if outcome != drainDone {
				return nil
			}
		}

		operationLogStage(
			worker,
			"submitting",
			upgradeSubmitMessage(operation),
		)

		current, ok := worker.checkpoint()
		if !ok {
			return nil
		}
		operation = current
	}

	for {
		done := upgradeDriveRemote(ctx, clientset, operation, worker)
		if done {
			return nil
		}

		current, ok := worker.checkpoint()
		if !ok {
			return nil
		}
		operation = current

		if !waitInterruptible(worker, upgradeStatusPollInterval) {
			return nil
		}
	}
}

func upgradePreflight(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	operation Operation,
) error {
	nodeName := operation.NodeName

	if operation.Options.ForceDelete {
		return errors.New("zero grace deletion cannot be used with node upgrades because it is not possible to prove that the workloads stopped")
	}

	if operation.HostTerminationUnverified {
		return errors.New("the node has unverified host terminations from a previous force delete and cannot be upgraded")
	}

	_, releaseKnown := shared.ReleaseByExactVersion(operation.TargetRelease)
	if !releaseKnown {
		return fmt.Errorf("the release %s is not a known Kubernetes release", operation.TargetRelease)
	}

	record, err := readClusterRecord()
	if err != nil {
		return fmt.Errorf("could not read the cluster record: %s", err)
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

	listed, err := clientset.CoreV1().Nodes().List(ctx, metav1.ListOptions{})
	if err != nil {
		return fmt.Errorf("could not list the nodes of the cluster: %s", err)
	}

	liveNodes := make(map[string]*corev1.Node, len(listed.Items))
	for i := range listed.Items {
		liveNodes[listed.Items[i].Name] = &listed.Items[i]
	}

	snapshot, err := Snapshot()
	if err != nil {
		return fmt.Errorf("could not read the maintenance state: %s", err)
	}

	currentVersion := node.Status.NodeInfo.KubeletVersion
	_, err = version.ParseSemantic(currentVersion)
	if err != nil {
		return fmt.Errorf("the version %s of node %s could not be parsed", currentVersion, nodeName)
	}

	_, err = version.ParseSemantic(operation.TargetRelease)
	if err != nil {
		return fmt.Errorf("the release %s could not be parsed", operation.TargetRelease)
	}

	if currentVersion == operation.TargetRelease {
		return errors.New("the node already runs the target release")
	} else if !shared.NodeAgentUpgradeAllowed(currentVersion, operation.TargetRelease) {
		return fmt.Errorf(
			"the upgrade from %s to %s is not allowed. Only a forward patch or the next minor release is allowed",
			currentVersion,
			operation.TargetRelease,
		)
	}

	controlPlaneVersions := []*version.Version{}
	workerVersions := []*version.Version{}

	for i := range record.Nodes {
		recorded := &record.Nodes[i]

		live, liveKnown := liveNodes[recorded.Hostname]
		if !liveKnown {
			return fmt.Errorf("the node %s of the cluster is missing from Kubernetes", recorded.Hostname)
		}

		resulting := live.Status.NodeInfo.KubeletVersion
		if recorded.Hostname == nodeName {
			resulting = operation.TargetRelease
		}

		other, otherKnown := snapshot[recorded.Hostname]
		otherUpgrading := otherKnown && recorded.Hostname != nodeName &&
			KindIsUpgrade(other.Kind) &&
			PhaseActive(other.Phase) &&
			other.TargetRelease != ""
		if otherUpgrading {
			resulting = other.TargetRelease
		}

		parsed, err := version.ParseSemantic(resulting)
		if err != nil {
			return fmt.Errorf("the version %s of node %s could not be parsed", resulting, recorded.Hostname)
		}

		if recorded.Group == shared.GroupControlPlane {
			controlPlaneVersions = append(controlPlaneVersions, parsed)
		} else {
			workerVersions = append(workerVersions, parsed)
		}
	}

	spreadErr := upgradeCheckControlPlaneSpread(controlPlaneVersions)
	if spreadErr != nil {
		return spreadErr
	}

	skewErr := upgradeCheckWorkerSkew(workerVersions, controlPlaneVersions)
	if skewErr != nil {
		return skewErr
	}

	guardErr := upgradeControlPlaneGuard(ctx, clientset, operation)
	if guardErr != nil {
		return guardErr
	}

	status, err := shared.NodeAgentClientStatus(ctx, nodeName, nodeRecord.IpAddress)
	if err != nil {
		return fmt.Errorf("could not read the node executor status of %s: %s", nodeName, err)
	}

	if status.NodeName != nodeName {
		return fmt.Errorf(
			"the node executor at %s reports node %s, but %s was requested",
			nodeRecord.IpAddress,
			status.NodeName,
			nodeName,
		)
	}

	if shared.NodeAgentPhaseActive(status.Phase) {
		if status.Release != operation.TargetRelease {
			return fmt.Errorf(
				"the node executor of %s is already upgrading to %s",
				nodeName,
				status.Release,
			)
		}
	}

	if status.Phase == shared.NodeAgentPhaseFailed && status.Release != operation.TargetRelease {
		return fmt.Errorf(
			"the node executor of %s failed an upgrade to %s. The target release does not match",
			nodeName,
			status.Release,
		)
	}

	return nil
}

func upgradeControlPlaneGuard(ctx context.Context, clientset *kubernetes.Clientset, operation Operation) error {
	record, err := readClusterRecord()
	if err != nil {
		return fmt.Errorf("could not read the cluster record: %s", err)
	}

	controlPlanes := controlPlaneNames(record)

	if !controlPlanes[operation.NodeName] {
		return nil
	}

	total := len(controlPlanes)

	if total == 1 {
		return nil
	}

	if total == 2 {
		return errors.New("a cluster with two control plane nodes cannot preserve the etcd quorum during an upgrade")
	}

	snapshot, err := Snapshot()
	if err != nil {
		return fmt.Errorf("could not read the maintenance state: %s", err)
	}

	upgrading := map[string]bool{}
	for otherName, other := range snapshot {
		otherControlPlaneUpgrading := otherName != operation.NodeName && controlPlanes[otherName] &&
			other.Kind != KindUncordon &&
			(PhaseActive(other.Phase) || upgradeBlockedByRecovery(other))
		if otherControlPlaneUpgrading {
			upgrading[otherName] = true
		}
	}

	required := total/2 + 1
	remaining, remainingErr := controlPlaneRemainingCount(ctx, clientset, controlPlanes, operation.NodeName, upgrading)
	if remainingErr != nil {
		return fmt.Errorf("could not list the control plane nodes: %s", remainingErr)
	}
	if remaining < required {
		return fmt.Errorf(
			"at least %d healthy, schedulable control plane nodes must remain during the upgrade, but only %d were found",
			required,
			remaining,
		)
	}

	return nil
}

func upgradeCheckControlPlaneSpread(controlPlanes []*version.Version) error {
	if len(controlPlanes) == 0 {
		return nil
	}

	major := controlPlanes[0].Major()
	oldestMinor := controlPlanes[0].Minor()
	newestMinor := controlPlanes[0].Minor()

	for _, parsed := range controlPlanes[1:] {
		if parsed.Major() != major {
			return errors.New("the control plane nodes must stay within one minor version of each other")
		}

		if parsed.Minor() < oldestMinor {
			oldestMinor = parsed.Minor()
		}

		if parsed.Minor() > newestMinor {
			newestMinor = parsed.Minor()
		}
	}

	if newestMinor > oldestMinor+1 {
		return errors.New("the upgrade would leave the control plane nodes more than one minor version apart")
	}

	return nil
}

func upgradeCheckWorkerSkew(
	workers []*version.Version,
	controlPlanes []*version.Version,
) error {
	if len(workers) == 0 || len(controlPlanes) == 0 {
		return nil
	}

	major := controlPlanes[0].Major()
	oldestMinor := controlPlanes[0].Minor()
	newestMinor := controlPlanes[0].Minor()

	for _, parsed := range controlPlanes[1:] {
		if parsed.Minor() < oldestMinor {
			oldestMinor = parsed.Minor()
		}

		if parsed.Minor() > newestMinor {
			newestMinor = parsed.Minor()
		}
	}

	for _, parsed := range workers {
		if parsed.Major() != major {
			return errors.New("the worker nodes must run the same major version as the control plane")
		}

		if parsed.Minor() > oldestMinor {
			return fmt.Errorf(
				"a worker node may not run a newer minor version than the oldest control plane node (%d.%d)",
				major,
				oldestMinor,
			)
		}

		if newestMinor > parsed.Minor()+3 {
			return errors.New("a worker node may not be more than three minor versions behind the newest control plane node")
		}
	}

	return nil
}

func upgradeDriveRemote(
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
			fmt.Sprintf("Waiting to resolve the address of the node executor: %s", ipErr),
		)
		return false
	}

	status, statusErr := shared.NodeAgentClientStatus(ctx, nodeName, nodeIp)
	if statusErr != nil {
		log.Warn("k8s-app maintenance %s: could not read the node executor status: %s", nodeName, statusErr)
		operationLogStage(
			worker,
			"submit-retry-status",
			fmt.Sprintf("Waiting to read the status of the node executor: %s", statusErr),
		)
		return false
	}

	if status.NodeName != nodeName {
		upgradeFail(operation, worker, fmt.Sprintf(
			"the node executor reports node %s, but %s was requested",
			status.NodeName,
			nodeName,
		))
		return true
	}

	if shared.NodeAgentPhaseActive(status.Phase) {
		if status.Release != operation.TargetRelease {
			upgradeFail(operation, worker, fmt.Sprintf(
				"the node executor of %s is already upgrading to %s",
				nodeName,
				status.Release,
			))
			return true
		}
		operationLogStage(
			worker,
			fmt.Sprintf("executor-%s", status.Phase),
			fmt.Sprintf("The node executor is %s", status.Phase),
		)
		return false
	}

	switch status.Phase {
	case shared.NodeAgentPhaseCompleted:
		if status.Release != operation.TargetRelease {
			upgradeFail(operation, worker, fmt.Sprintf(
				"the node executor completed an upgrade to %s, but %s was requested",
				status.Release,
				operation.TargetRelease,
			))
			return true
		}
		upgradeComplete(ctx, clientset, operation, worker)
		return true

	case shared.NodeAgentPhaseFailed:
		if status.Release != operation.TargetRelease {
			upgradeFail(operation, worker, fmt.Sprintf(
				"the node executor of %s failed an upgrade to %s. The target release does not match",
				nodeName,
				status.Release,
			))
			return true
		}

		executorMessage := status.Error
		if executorMessage == "" {
			executorMessage = "the node executor did not report an error"
		}
		upgradeFail(operation, worker, fmt.Sprintf(
			"the upgrade to %s failed on the node: %s. %s",
			operation.TargetRelease,
			executorMessage,
			upgradeFailureRecoveryMessage(operation),
		))
		return true

	case shared.NodeAgentPhaseIdle:
		if !operation.ExecutorSubmitted && UpgradeCordons(operation) {
			node, nodeErr := getNode(ctx, clientset, nodeName)
			if nodeErr != nil {
				log.Warn("k8s-app maintenance %s: could not read the node before the submission: %s", nodeName, nodeErr)
				operationLogStage(
					worker,
					"submit-retry-node",
					fmt.Sprintf("Waiting to read the node before the submission: %s", nodeErr),
				)
				return false
			}

			if string(node.UID) != operation.NodeUid {
				upgradeFail(operation, worker, "the node was replaced during the upgrade")
				return true
			}

			if !node.Spec.Unschedulable {
				upgradeFail(operation, worker, "the node was uncordoned before the upgrade was submitted")
				return true
			}
		}

		return upgradeSubmit(ctx, operation, worker, nodeIp, status)

	default:
		upgradeFail(operation, worker, fmt.Sprintf(
			"the node executor of %s reported phase %s, which cannot be driven",
			nodeName,
			status.Phase,
		))
		return true
	}
}

func upgradeSubmit(
	ctx context.Context,
	operation Operation,
	worker *nodeWorker,
	nodeIp string,
	status shared.NodeAgentStatus,
) bool {
	nodeName := operation.NodeName

	if operation.Options.Drain {
		trafficErr := upgradeSuspendTraffic(operation, worker)
		if trafficErr != nil {
			log.Warn("k8s-app maintenance %s: could not suspend traffic before the upgrade: %s", nodeName, trafficErr)
			operationLogStage(
				worker,
				"submit-retry-traffic",
				fmt.Sprintf("Waiting to suspend the traffic of the node before the upgrade: %s", trafficErr),
			)
			return false
		}
	}

	persistErr := worker.mutate(func(op *Operation) bool {
		op.ExecutorSubmitted = true
		return true
	})
	if persistErr != nil {
		log.Warn("k8s-app maintenance %s: could not record the submission to the node executor: %s", nodeName, persistErr)
		operationLogStage(
			worker,
			"submit-retry-persist",
			fmt.Sprintf("Waiting to record the submission to the node executor: %s", persistErr),
		)
		return false
	}

	resubmit := status.Phase == shared.NodeAgentPhaseFailed && status.Release == operation.TargetRelease
	_, postErr := shared.NodeAgentClientUpgrade(ctx, nodeName, nodeIp, operation.TargetRelease, resubmit, operation.Uid)
	if postErr != nil {
		log.Warn("k8s-app maintenance %s: could not submit the upgrade to the node executor: %s", nodeName, postErr)
		operationLogStage(
			worker,
			"submit-retry-post",
			fmt.Sprintf("Waiting to submit the upgrade to the node executor: %s", postErr),
		)
		return false
	}

	operationLogStage(
		worker,
		"submitted",
		fmt.Sprintf("The upgrade to %s was submitted to the node executor", operation.TargetRelease),
	)
	return false
}

func upgradeComplete(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	operation Operation,
	worker *nodeWorker,
) {
	nodeName := operation.NodeName

	log.Info(
		"k8s-app maintenance %s: the node executor completed the upgrade to %s",
		nodeName,
		operation.TargetRelease,
	)

	for {
		if worker.ctx.Err() != nil {
			return
		}

		if _, ok := worker.checkpoint(); !ok {
			return
		}

		node, err := upgradeGetNode(worker.ctx, worker, operation, clientset)
		if err != nil {
			log.Warn("k8s-app maintenance %s: could not read the node after the upgrade: %s", nodeName, err)
			if !waitInterruptible(worker, apiRetryDelay) {
				return
			}
			continue
		}

		if nodeIsReady(node) && node.Status.NodeInfo.KubeletVersion == operation.TargetRelease {
			break
		}

		operationLogStage(
			worker,
			"waiting-ready",
			fmt.Sprintf("Waiting for %s to become ready with %s", nodeName, operation.TargetRelease),
		)
		if !waitInterruptible(worker, upgradeStatusPollInterval) {
			return
		}
	}

	recordErr := upgradeRecordDesiredVersion(operation, worker)
	if recordErr != nil {
		log.Warn("k8s-app maintenance %s: could not record the desired version of the node: %s", nodeName, recordErr)
		if !waitInterruptible(worker, apiRetryDelay) {
			return
		}
	}

	trafficErr := upgradeRestoreTraffic(operation, worker)
	if trafficErr != nil {
		log.Warn("k8s-app maintenance %s: could not restore traffic after the upgrade: %s", nodeName, trafficErr)
		if !waitInterruptible(worker, apiRetryDelay) {
			return
		}
	}

	if operation.OriginalUnschedulable {
		log.Info(
			"k8s-app maintenance %s: the node remains cordoned because it was cordoned before the upgrade",
			nodeName,
		)
		upgradeFinish(operation, worker)
		return
	}

	if !UpgradeCordons(operation) {
		upgradeFinish(operation, worker)
		return
	}

	node, err := upgradeGetNode(worker.ctx, worker, operation, clientset)
	if err != nil {
		log.Warn("k8s-app maintenance %s: could not read the node after the upgrade: %s", nodeName, err)
		if !waitInterruptible(worker, apiRetryDelay) {
			return
		}
		upgradeFinish(operation, worker)
		return
	}

	if node.Spec.Unschedulable {
		_, patchErr := patchUnschedulable(worker.ctx, clientset, node, false)
		if patchErr != nil {
			log.Warn("k8s-app maintenance %s: could not uncordon the node: %s", nodeName, patchErr)
			if !waitInterruptible(worker, apiRetryDelay) {
				return
			}
		} else {
			operationLogLines(worker, fmt.Sprintf("The node %s was uncordoned. It accepts workloads again", nodeName))
		}
	}

	upgradeFinish(operation, worker)
}

func upgradeGetNode(
	ctx context.Context,
	worker *nodeWorker,
	operation Operation,
	clientset *kubernetes.Clientset,
) (*corev1.Node, error) {
	healthClient, clientErr := upgradeHealthClient(worker, operation, clientset)
	if clientErr != nil {
		return nil, clientErr
	}

	node, err := healthClient.CoreV1().Nodes().Get(ctx, operation.NodeName, metav1.GetOptions{})
	if err != nil {
		if apierrors.IsNotFound(err) {
			return nil, fmt.Errorf("the node %s was not found in the cluster", operation.NodeName)
		}
		return nil, err
	}

	if string(node.UID) != operation.NodeUid {
		return nil, errors.New("the node was replaced during the upgrade")
	}

	return node, nil
}

func upgradeHealthClient(worker *nodeWorker, operation Operation, clientset *kubernetes.Clientset) (*kubernetes.Clientset, error) {
	record, err := worker.clusterRecord()
	if err != nil {
		return nil, err
	}
	node, known := upgradeNodeRecord(record, operation.NodeName)
	if !known {
		return nil, fmt.Errorf("the node %s is not part of the cluster", operation.NodeName)
	}
	if node.Group != shared.GroupControlPlane {
		return clientset, nil
	}
	config, err := clientcmd.BuildConfigFromFlags("", filepath.Join(shared.ManagementMountPath, "kubeconfig-internal"))
	if err != nil {
		return nil, err
	}
	config.Host = fmt.Sprintf("https://%s", net.JoinHostPort(node.IpAddress, fmt.Sprintf("%d", shared.ApiPort)))
	config.TLSClientConfig.ServerName = ""
	config.Timeout = apiRequestTimeout
	return kubernetes.NewForConfig(config)
}

func upgradeFail(operation Operation, worker *nodeWorker, message string) {
	operationLogStage(
		worker,
		"failed",
		fmt.Sprintf("The upgrade of %s failed: %s", operation.NodeName, message),
	)
	failOperation(worker, message)
}

func upgradeFinish(operation Operation, worker *nodeWorker) {
	finishErr := finishQuiet(worker, PhaseCompleted, "")
	if finishErr != nil {
		upgradeFail(operation, worker, fmt.Sprintf(
			"the upgrade to %s completed on the node, but the result could not be recorded: %s. An explicit retry recovers it without draining the node again",
			operation.TargetRelease,
			finishErr,
		))
		return
	}

	operationLogStage(
		worker,
		"completed",
		fmt.Sprintf(
			"The upgrade of %s to %s completed",
			operation.NodeName,
			operation.TargetRelease,
		),
	)

	log.Info(
		"k8s-app maintenance %s: the upgrade to %s completed",
		operation.NodeName,
		operation.TargetRelease,
	)
	worker.cancel()
}

func upgradeNodeIp(worker *nodeWorker, operation Operation) (string, error) {
	record, err := worker.clusterRecord()
	if err != nil {
		return "", fmt.Errorf("could not read the cluster record: %s", err)
	}

	nodeRecord, known := upgradeNodeRecord(record, operation.NodeName)
	if !known {
		return "", errors.New("the node is not part of this cluster")
	}

	return nodeRecord.IpAddress, nil
}

func upgradeRecordDesiredVersion(operation Operation, worker *nodeWorker) error {
	nodeName := operation.NodeName
	release := operation.TargetRelease

	client, clientErr := shared.ClusterStateClientNewHost()
	if clientErr != nil {
		return fmt.Errorf("could not open the cluster state client: %s", clientErr)
	}

	snapshot, readErr := shared.ClusterStateRead(client)
	if readErr != nil {
		return fmt.Errorf("could not read the cluster record: %s", readErr)
	}
	if !snapshot.Found {
		return errors.New("the cluster record does not exist")
	}

	record := snapshot.Record
	nodeIndex := -1
	for i := range record.Nodes {
		if record.Nodes[i].Hostname == nodeName {
			nodeIndex = i
			break
		}
	}
	if nodeIndex == -1 {
		return fmt.Errorf("the node %s is not part of this cluster", nodeName)
	}

	if record.Nodes[nodeIndex].DesiredVersion == release {
		return nil
	}

	record.Nodes[nodeIndex].DesiredVersion = release
	shared.ClusterStateRecordRevisionSet(&record, snapshot.Revision)

	_, writeErr := shared.ClusterStateRecordWrite(client, &record, snapshot.Revision)
	if writeErr != nil {
		return fmt.Errorf("could not update the cluster record: %s", writeErr)
	}
	return nil
}

// Traffic suspension
// =====================================================================================================================
// An upgrade suspends the traffic to the node before it stops workloads. The suspension edits service membership in
// the IM integration so that the node stops receiving new jobs. Membership updates are synchronous, so a single
// verification pass is enough. The suspended records are kept on the operation and restored on completion.

func trafficNodeJobId(worker *nodeWorker, operation Operation) (string, error) {
	record, err := worker.clusterRecord()
	if err != nil {
		return "", fmt.Errorf("could not read the cluster record: %s", err)
	}

	nodeRecord, known := upgradeNodeRecord(record, operation.NodeName)
	if !known {
		return "", fmt.Errorf("the node %s is not part of this cluster", operation.NodeName)
	}

	if nodeRecord.JobId == "" {
		return "", fmt.Errorf("the node %s has no job id in the cluster record", operation.NodeName)
	}

	return nodeRecord.JobId, nil
}

func trafficServices(worker *nodeWorker) ([]orcapi.Service, error) {
	record, err := worker.clusterRecord()
	if err != nil {
		return nil, fmt.Errorf("could not read the cluster record: %s", err)
	}

	client, clientErr := shared.RpcGrantClientNew()
	if clientErr != nil {
		return nil, clientErr
	}

	services, herr := shared.RpcGrantBrowseServices(client.Rpc, client.Token)
	if herr != nil {
		return nil, fmt.Errorf("could not browse the stack services: %s", herr)
	}

	affected := []orcapi.Service{}
	for _, service := range services {
		if service.Specification.Name == trafficServiceName || service.Id == record.ServiceId {
			affected = append(affected, service)
		}
	}
	return affected, nil
}

func trafficSuspendRecords(worker *nodeWorker, operation Operation) (string, error) {
	jobId, jobErr := trafficNodeJobId(worker, operation)
	if jobErr != nil {
		return "", jobErr
	}

	client, clientErr := shared.ClusterStateClientNewHost()
	if clientErr != nil {
		return "", clientErr
	}

	for attempt := 0; ; attempt++ {
		snapshot, readErr := shared.ClusterTrafficRead(client)
		if readErr != nil {
			return "", readErr
		}

		if _, exists := shared.ClusterTrafficSuspendFind(snapshot.Record, jobId); exists {
			return jobId, nil
		}

		record := snapshot.Record
		if !shared.ClusterTrafficSuspendUpsert(&record, shared.ClusterTrafficSuspend{
			JobId:        jobId,
			NodeName:     operation.NodeName,
			OperationUid: operation.Uid,
		}) {
			return "", fmt.Errorf("could not record the traffic suspension for the node %s", operation.NodeName)
		}

		_, writeErr := shared.ClusterTrafficRecordWrite(client, &record, snapshot.Revision)
		if writeErr != nil {
			if shared.IsConflict(writeErr) && attempt < 8 {
				time.Sleep(2 * time.Second)
				continue
			}
			return "", writeErr
		}

		return jobId, nil
	}
}

func trafficRestoreRecords(worker *nodeWorker, operation Operation) (bool, error) {
	jobId, jobErr := trafficNodeJobId(worker, operation)
	if jobErr != nil {
		return false, jobErr
	}

	client, clientErr := shared.ClusterStateClientNewHost()
	if clientErr != nil {
		return false, clientErr
	}

	for attempt := 0; ; attempt++ {
		snapshot, readErr := shared.ClusterTrafficRead(client)
		if readErr != nil {
			return false, readErr
		}

		if _, exists := shared.ClusterTrafficSuspendFind(snapshot.Record, jobId); !exists {
			return false, nil
		}

		record := snapshot.Record
		if !shared.ClusterTrafficSuspendRemove(&record, jobId) {
			return false, nil
		}

		_, writeErr := shared.ClusterTrafficRecordWrite(client, &record, snapshot.Revision)
		if writeErr != nil {
			if shared.IsConflict(writeErr) && attempt < 8 {
				time.Sleep(2 * time.Second)
				continue
			}
			return false, writeErr
		}

		return true, nil
	}
}

func trafficMoveMember(worker *nodeWorker, jobId string, remove bool) error {
	services, servicesErr := trafficServices(worker)
	if servicesErr != nil {
		return servicesErr
	}

	client, clientErr := shared.RpcGrantClientNew()
	if clientErr != nil {
		return clientErr
	}

	for _, service := range services {
		member := slices.Contains(service.Status.Members, jobId)
		if member != remove {
			continue
		}

		request := ucxapi.StackControlRequestOf[orcapi.ServicesControlUpdateMembersRequest]{
			StackCredentialAuth: ucxapi.StackCredentialAuth{Token: client.Token},
			Request: orcapi.ServicesControlUpdateMembersRequest{
				Id: service.Id,
			},
		}
		if remove {
			request.Request.RemovedJobIds = []string{jobId}
		} else {
			request.Request.AddedJobIds = []string{jobId}
		}

		_, herr := ucxapi.StackControlUpdateMembers.InvokeEx(client.Rpc, request, rpc.InvokeOpts{})
		if herr != nil {
			return fmt.Errorf(
				"could not update the membership of the job %s in the service %s: %s",
				jobId,
				service.Id,
				herr,
			)
		}
	}

	return nil
}

func trafficMemberActive(worker *nodeWorker, jobId string) (bool, error) {
	services, servicesErr := trafficServices(worker)
	if servicesErr != nil {
		return false, servicesErr
	}

	for _, service := range services {
		if slices.Contains(service.Status.Members, jobId) {
			return true, nil
		}
	}
	return false, nil
}

func upgradeSuspendTraffic(operation Operation, worker *nodeWorker) error {
	jobId, err := trafficSuspendRecords(worker, operation)
	if err != nil {
		return fmt.Errorf("could not record the traffic suspension: %s", err)
	}
	if jobId == "" {
		return nil
	}

	persistErr := worker.mutate(func(op *Operation) bool {
		if slices.Contains(op.SuspendedJobIds, jobId) {
			return false
		}
		op.SuspendedJobIds = append(op.SuspendedJobIds, jobId)
		return true
	})
	if persistErr != nil && shared.IsConflict(persistErr) {
		return persistErr
	}

	if removeErr := trafficMoveMember(worker, jobId, true); removeErr != nil {
		return fmt.Errorf("could not remove the job %s from the traffic services: %s", jobId, removeErr)
	}

	active, activeErr := trafficMemberActive(worker, jobId)
	if activeErr != nil {
		return fmt.Errorf("could not verify the traffic services after the removal: %s", activeErr)
	}
	if active {
		return fmt.Errorf(
			"the traffic services did not accept the removal of the job %s of the node %s",
			jobId,
			operation.NodeName,
		)
	}

	operationLogLines(worker, fmt.Sprintf(
		"The traffic of %s was suspended. The job %s no longer receives traffic",
		operation.NodeName,
		jobId,
	))

	return nil
}

func upgradeRestoreTraffic(operation Operation, worker *nodeWorker) error {
	restored, err := trafficRestoreRecords(worker, operation)
	if err != nil {
		return err
	}

	persistErr := worker.mutate(func(op *Operation) bool {
		if len(op.SuspendedJobIds) == 0 {
			return false
		}
		op.SuspendedJobIds = nil
		return true
	})
	if persistErr != nil {
		return persistErr
	}

	if !restored && len(operation.SuspendedJobIds) == 0 {
		return nil
	}

	jobId, jobErr := trafficNodeJobId(worker, operation)
	if jobErr != nil {
		return jobErr
	}

	if addErr := trafficMoveMember(worker, jobId, false); addErr != nil {
		return addErr
	}

	log.Info(
		"k8s-app maintenance %s: the traffic suspension was removed and the job %s was restored to the traffic services",
		operation.NodeName,
		jobId,
	)
	return nil
}
