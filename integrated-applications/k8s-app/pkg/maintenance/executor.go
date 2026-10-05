package maintenance

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"strings"
	"sync"
	"time"

	corev1 "k8s.io/api/core/v1"
	policyv1 "k8s.io/api/policy/v1"
	apierrors "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime/schema"
	"k8s.io/apimachinery/pkg/types"
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/tools/clientcmd"

	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/log"
)

func namespacedName(namespace string, name string) string {
	if namespace == "" {
		return name
	}
	return fmt.Sprintf("%s/%s", namespace, name)
}

func localHostname() string {
	hostname, _ := os.Hostname()
	return hostname
}

// Worker state
// =====================================================================================================================
// A node worker owns one operation from the moment the sweep adopts it until the operation concludes. The worker
// holds its own context with the deadline of the operation, the stack client, and the log relay. The cluster record
// is read once and cached, which is safe because every worker runs on its own goroutine.

const (
	runInterval       = 5 * time.Second
	apiRetryDelay     = 5 * time.Second
	drainPollInterval = 2 * time.Second
	apiRequestTimeout = 15 * time.Second
	heartbeatInterval = 15 * time.Second

	controlPlaneLabel       = "node-role.kubernetes.io/control-plane"
	controlPlaneLabelLegacy = "node-role.kubernetes.io/master"
)

var workers sync.WaitGroup

var errConcluded = errors.New("the operation was already concluded")

type nodeWorker struct {
	nodeName  string
	uid       string
	ctx       context.Context
	cancel    context.CancelFunc
	client    stackClient
	logs      *logRelay
	lastTouch time.Time

	record       shared.ClusterRecord
	recordLoaded bool
}

type drainOutcome int

const (
	drainConcluded drainOutcome = iota
	drainDone
	drainBlocked
)

func (worker *nodeWorker) load() (stackRecord, error) {
	return stackRead(worker.client, stackNodeKey(worker.nodeName))
}

func (worker *nodeWorker) clusterRecord() (shared.ClusterRecord, error) {
	if worker.recordLoaded {
		return worker.record, nil
	}

	record, err := readClusterRecord()
	if err != nil {
		return shared.ClusterRecord{}, err
	}

	worker.record = record
	worker.recordLoaded = true
	return record, nil
}

func (worker *nodeWorker) mutate(mutate func(*Operation) bool) error {
	record, err := worker.load()
	if err != nil {
		return err
	}

	if !record.Found || record.Operation.NodeName == "" || record.Operation.Uid != worker.uid {
		return errConcluded
	}

	operation := record.Operation
	operation.UpdatedAt = time.Now().UTC()
	operation.HeartbeatAt = operation.UpdatedAt
	changed := mutate(&operation)

	if !changed && time.Since(worker.lastTouch) < heartbeatInterval {
		return nil
	}

	value, marshalErr := json.Marshal(operation)
	if marshalErr != nil {
		return marshalErr
	}

	_, writeErr := stackWrite(worker.client, stackNodeKey(worker.nodeName), value, record.Revision)
	if writeErr != nil {
		if shared.IsConflict(writeErr) {
			return errConcluded
		}
		return writeErr
	}

	worker.lastTouch = time.Now()
	return nil
}

func (worker *nodeWorker) checkpoint() (Operation, bool) {
	record, err := worker.load()
	if err != nil {
		log.Warn("k8s-app maintenance %s: could not reload the operation: %s", worker.nodeName, err)
		return Operation{}, false
	}
	if !record.Found || record.Operation.NodeName == "" || record.Operation.Uid != worker.uid {
		return Operation{}, false
	}

	operation := record.Operation
	if !PhaseActive(operation.Phase) {
		return Operation{}, false
	}
	if operation.CancelRequested && !removePastPointOfNoReturn(operation) {
		finish(worker, PhaseCancelled, cancelMessage(operation))
		return Operation{}, false
	}
	if worker.ctx.Err() != nil {
		return Operation{}, false
	}
	if time.Now().After(operation.Deadline) {
		finish(worker, PhaseFailed, "the operation timed out")
		return Operation{}, false
	}

	return operation, true
}

// Dispatch loop
// =====================================================================================================================
// The loop scans the stack state on a fixed interval. It claims every operation whose worker stopped touching its
// heartbeat and starts a new worker for it. The claim is a compare-and-swap write that stamps the heartbeat, so only
// one coordinator instance adopts a given operation. The worker revalidates the recorded uid before every mutation.

func Run(ctx context.Context, kubeconfigPath string) {
	ticker := time.NewTicker(runInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			workers.Wait()
			return
		case <-ticker.C:
			sweep(ctx, kubeconfigPath)
		}
	}
}

func sweep(ctx context.Context, kubeconfigPath string) {
	client, err := stackClientNew()
	if err != nil {
		return
	}

	records, err := stackList(client, stackKeyPrefix)
	if err != nil {
		return
	}

	for _, record := range records {
		if record.IsEmpty() {
			continue
		}

		var operation Operation
		if err := json.Unmarshal(record.Value, &operation); err != nil {
			log.Warn("k8s-app maintenance: could not parse the maintenance record %s during the sweep: %s", record.Key, err)
			continue
		}

		if operation.NodeName == "" || !PhaseActive(operation.Phase) {
			continue
		}

		if !operationStale(operation, time.Now()) {
			continue
		}

		if operation.Kind == KindRemove && operation.NodeName == localHostname() {
			continue
		}

		if !claimOperation(client, operation, record.Revision) {
			continue
		}

		workers.Add(1)
		go func() {
			defer workers.Done()
			runOperation(ctx, kubeconfigPath, client, operation)
		}()
	}
}

func claimOperation(client stackClient, operation Operation, revision int64) bool {
	claimed := operation
	claimed.UpdatedAt = time.Now().UTC()
	claimed.HeartbeatAt = claimed.UpdatedAt

	value, marshalErr := json.Marshal(claimed)
	if marshalErr != nil {
		return false
	}

	_, writeErr := stackWrite(client, stackNodeKey(operation.NodeName), value, revision)
	if writeErr != nil {
		if !shared.IsConflict(writeErr) {
			log.Warn(
				"k8s-app maintenance %s: could not claim the stale operation: %s",
				operation.NodeName,
				writeErr,
			)
		}
		return false
	}

	return true
}

func runOperation(
	ctx context.Context,
	kubeconfigPath string,
	client stackClient,
	operation Operation,
) {
	nodeName := operation.NodeName

	workerCtx, cancel := context.WithDeadline(ctx, operation.Deadline)
	defer cancel()

	worker := &nodeWorker{
		nodeName: nodeName,
		uid:      operation.Uid,
		ctx:      workerCtx,
		cancel:   cancel,
		client:   client,
	}
	worker.logs = logRelayStart(worker)

	clientset, err := newClient(kubeconfigPath)
	if err != nil {
		log.Warn("k8s-app maintenance %s: could not build the Kubernetes client: %s", nodeName, err)
		return
	}

	current, ok := worker.checkpoint()
	if !ok {
		return
	}

	var runErr error
	switch current.Kind {
	case KindUncordon:
		runErr = runUncordon(workerCtx, clientset, current, worker)
	case KindCordon:
		runErr = runCordon(workerCtx, clientset, current, worker)
	case KindCordonDrain:
		outcome, cordonErr := runCordonDrain(workerCtx, clientset, current, worker, false, true)
		runErr = cordonErr
		if runErr == nil && outcome == drainDone {
			finish(worker, PhaseCompleted, "")
		}
	case KindUpgrade:
		runErr = runUpgrade(workerCtx, clientset, current, worker)
	case KindRemove:
		runErr = runRemove(workerCtx, clientset, current, worker)
	default:
		runErr = fmt.Errorf("the operation kind %s is not supported", current.Kind)
	}

	if runErr != nil {
		failOperation(worker, runErr.Error())
	}
}

// Operations
// =====================================================================================================================
// The functions in this section implement the four operation kinds. They all revalidate the node before they act,
// because the node may have been replaced between submission and execution.

func cancelMessage(operation Operation) string {
	if operation.Kind == KindUncordon {
		return "cancelled by request"
	}
	if operation.Kind == KindRemove {
		if operation.NodeDeleted {
			return "cancelled by request. The Kubernetes node was already deleted and the removal must be retried"
		}
		return "cancelled by request. The node remains part of the cluster"
	}
	if KindIsUpgrade(operation.Kind) && !UpgradeCordons(operation) {
		return "cancelled by request"
	}
	return "cancelled by request. The node remains cordoned"
}

func runCordonDrain(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	operation Operation,
	worker *nodeWorker,
	verified bool,
	drain bool,
) (drainOutcome, error) {
	record, err := worker.clusterRecord()
	if err != nil {
		return drainConcluded, fmt.Errorf("could not read the cluster record: %s", err)
	}

	if record.Phase != "created" {
		return drainConcluded, fmt.Errorf("the cluster is not ready (state: %s)", record.Phase)
	}

	if !verified && !knownHostnames(record)[operation.NodeName] {
		return drainConcluded, errors.New("the node is not part of this cluster")
	}

	node, err := getNode(ctx, clientset, operation.NodeName)
	if err != nil {
		return drainConcluded, err
	}

	if string(node.UID) != operation.NodeUid {
		return drainConcluded, errors.New("the node uid does not match the requested node")
	}

	if !verified {
		if guardErr := controlPlaneGuard(ctx, clientset, operation, node); guardErr != nil {
			return drainConcluded, guardErr
		}
	}

	if cordonErr := cordonNode(ctx, clientset, worker, node); cordonErr != nil {
		return drainConcluded, cordonErr
	}

	if !drain {
		return drainDone, nil
	}

	operationLogStage(
		worker,
		"draining",
		fmt.Sprintf("The node %s is cordoned. Its pods are drained", operation.NodeName),
	)

	return drainNode(ctx, clientset, operation, worker), nil
}

func runCordon(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	operation Operation,
	worker *nodeWorker,
) error {
	node, err := getNode(ctx, clientset, operation.NodeName)
	if err != nil {
		return err
	}

	if string(node.UID) != operation.NodeUid {
		return errors.New("the node uid does not match the requested node")
	}

	if cordonErr := cordonNode(ctx, clientset, worker, node); cordonErr != nil {
		return cordonErr
	}

	finish(worker, PhaseCompleted, "")
	return nil
}

func cordonNode(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	worker *nodeWorker,
	node *corev1.Node,
) error {
	originalUnschedulable := node.Spec.Unschedulable
	worker.mutate(func(op *Operation) bool {
		if op.OriginalSchedulingCaptured {
			return false
		}
		op.OriginalUnschedulable = originalUnschedulable
		op.OriginalSchedulingCaptured = true
		return true
	})

	if !node.Spec.Unschedulable {
		if _, patchErr := patchUnschedulable(ctx, clientset, node, true); patchErr != nil {
			return patchErr
		}
	}

	return nil
}

func runUncordon(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	operation Operation,
	worker *nodeWorker,
) error {
	if restoreErr := upgradeRestoreTraffic(operation, worker); restoreErr != nil {
		return fmt.Errorf("could not release the traffic suspension: %s", restoreErr)
	}

	node, err := getNode(ctx, clientset, operation.NodeName)
	if err != nil {
		return err
	}

	if string(node.UID) != operation.NodeUid {
		return errors.New("the node uid does not match the requested node")
	}

	if node.Spec.Unschedulable {
		if _, patchErr := patchUnschedulable(ctx, clientset, node, false); patchErr != nil {
			return patchErr
		}
	}

	finish(worker, PhaseCompleted, "")
	return nil
}

func drainNode(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	operation Operation,
	worker *nodeWorker,
) drainOutcome {
	nodeName := operation.NodeName
	resources := map[string][]metav1.APIResource{}

	drainLog := drainLogger{worker: worker}

	for {
		current, ok := worker.checkpoint()
		if !ok {
			return drainConcluded
		}

		if time.Now().After(current.Deadline) {
			finish(worker, PhaseFailed, "the drain timed out with pods remaining on the node")
			return drainConcluded
		}

		node, nodeErr := getNode(ctx, clientset, nodeName)
		if nodeErr != nil {
			log.Warn("k8s-app maintenance %s: could not read the node during the drain: %s", nodeName, nodeErr)
			if !waitInterruptible(worker, apiRetryDelay) {
				return drainConcluded
			}
			continue
		}

		if string(node.UID) != operation.NodeUid {
			finish(worker, PhaseFailed, "the node was replaced during the drain")
			return drainConcluded
		}

		if !node.Spec.Unschedulable {
			finish(worker, PhaseFailed, "the node was uncordoned during the drain")
			return drainConcluded
		}

		pods, err := listNodePods(ctx, clientset, nodeName)
		if err != nil {
			log.Warn("k8s-app maintenance %s: could not list the pods of the node: %s", nodeName, err)
			if !waitInterruptible(worker, apiRetryDelay) {
				return drainConcluded
			}
			continue
		}

		blockers, deletable, terminating := classifyPods(ctx, clientset, operation, pods, resources)

		drainLog.report(len(deletable), len(terminating), len(blockers))

		if len(blockers) > 0 && len(deletable) == 0 {
			if len(terminating) > 0 {
				if !waitInterruptible(worker, drainPollInterval) {
					return drainConcluded
				}
				continue
			}
			message := fmt.Sprintf(
				"the drain is blocked by pods that were not approved for deletion: %s",
				strings.Join(blockers, ", "),
			)
			operationLogLines(worker, fmt.Sprintf("The drain is blocked: %s", message))
			finish(worker, PhaseBlocked, message)
			return drainBlocked
		}

		if len(deletable) == 0 && len(terminating) == 0 {
			return drainDone
		}

		for _, pod := range deletable {
			if ctx.Err() != nil {
				return drainConcluded
			}
			if _, ok := worker.checkpoint(); !ok {
				return drainConcluded
			}
			removePod(ctx, clientset, operation, worker, pod)
		}

		if !waitInterruptible(worker, drainPollInterval) {
			return drainConcluded
		}
	}
}

// Kubernetes helpers
// =====================================================================================================================
// The functions in this section wrap the Kubernetes API. They return plain errors so that callers can log them
// without knowing about the API machinery.

func getNode(ctx context.Context, clientset *kubernetes.Clientset, nodeName string) (*corev1.Node, error) {
	node, err := clientset.CoreV1().Nodes().Get(ctx, nodeName, metav1.GetOptions{})
	if err != nil {
		if apierrors.IsNotFound(err) {
			return nil, fmt.Errorf("the node %s was not found in the cluster", nodeName)
		}
		return nil, err
	}
	return node, nil
}

func classifyPods(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	operation Operation,
	pods []corev1.Pod,
	resources map[string][]metav1.APIResource,
) (blockers []string, deletable []*corev1.Pod, terminating []string) {
	controllers := map[types.UID]bool{}
	for i := range pods {
		pod := &pods[i]

		if pod.Annotations[corev1.MirrorPodAnnotationKey] != "" {
			continue
		}

		if pod.Status.Phase == corev1.PodSucceeded || pod.Status.Phase == corev1.PodFailed {
			continue
		}

		if pod.DeletionTimestamp != nil {
			terminating = append(terminating, namespacedName(pod.Namespace, pod.Name))
			continue
		}

		owner := controllerOwnerOf(pod)
		unmanaged := owner == nil

		if owner != nil {
			live, known := controllers[owner.UID]
			if !known {
				var err error
				live, err = controllerLive(ctx, clientset, pod, owner, resources)
				if err != nil {
					blockers = append(blockers, fmt.Sprintf("%s: could not verify its controller: %s", namespacedName(pod.Namespace, pod.Name), err))
					continue
				}
				controllers[owner.UID] = live
			}
			if owner.Kind == "DaemonSet" && live {
				continue
			}
			unmanaged = !live
		}

		namespaced := namespacedName(pod.Namespace, pod.Name)

		if (unmanaged || podHasEmptyDir(pod)) && !operation.Options.DeleteVolatilePods {
			blocker := "pod with local emptyDir storage"
			if unmanaged {
				blocker = "unmanaged pod without a live controller"
			}
			blockers = append(blockers, fmt.Sprintf("%s: %s", namespaced, blocker))
			continue
		}

		deletable = append(deletable, pod)
	}
	return blockers, deletable, terminating
}

func controllerOwnerOf(pod *corev1.Pod) *metav1.OwnerReference {
	for i := range pod.OwnerReferences {
		owner := &pod.OwnerReferences[i]
		if owner.Controller != nil && *owner.Controller {
			return owner
		}
	}
	return nil
}

func podHasEmptyDir(pod *corev1.Pod) bool {
	for i := range pod.Spec.Volumes {
		if pod.Spec.Volumes[i].EmptyDir != nil {
			return true
		}
	}
	return false
}

func controllerLive(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	pod *corev1.Pod,
	owner *metav1.OwnerReference,
	resources map[string][]metav1.APIResource,
) (bool, error) {
	groupVersion, err := schema.ParseGroupVersion(owner.APIVersion)
	if err != nil {
		return false, err
	}

	apiPath := fmt.Sprintf("/api/%s", groupVersion.Version)
	if groupVersion.Group != "" {
		apiPath = fmt.Sprintf("/apis/%s/%s", groupVersion.Group, groupVersion.Version)
	}
	available, known := resources[owner.APIVersion]
	if !known {
		var listed metav1.APIResourceList
		err := clientset.CoreV1().RESTClient().Get().AbsPath(apiPath).Do(ctx).Into(&listed)
		if apierrors.IsNotFound(err) {
			return false, nil
		}
		if err != nil {
			return false, err
		}
		available = listed.APIResources
		resources[owner.APIVersion] = available
	}

	for _, resource := range available {
		if resource.Kind != owner.Kind || strings.Contains(resource.Name, "/") {
			continue
		}
		path := apiPath
		if resource.Namespaced {
			path = fmt.Sprintf("%s/namespaces/%s", path, pod.Namespace)
		}
		path = fmt.Sprintf("%s/%s/%s", path, resource.Name, owner.Name)
		data, err := clientset.CoreV1().RESTClient().Get().AbsPath(path).SetHeader("Accept", "application/json").DoRaw(ctx)
		if apierrors.IsNotFound(err) {
			return false, nil
		}
		if err != nil {
			return false, err
		}
		var controller metav1.PartialObjectMetadata
		err = json.Unmarshal(data, &controller)
		if err != nil {
			return false, err
		}
		return controller.UID == owner.UID && controller.DeletionTimestamp == nil, nil
	}

	return false, nil
}

func removePod(ctx context.Context, clientset *kubernetes.Clientset, operation Operation, worker *nodeWorker, pod *corev1.Pod) {
	if operation.Options.BypassDisruptionBudgets {
		deleteOptions := metav1.DeleteOptions{
			Preconditions: &metav1.Preconditions{UID: &pod.UID, ResourceVersion: &pod.ResourceVersion},
		}
		if operation.Options.ForceDelete {
			err := markHostTerminationUnverified(operation, worker)
			if err != nil {
				log.Warn("k8s-app maintenance: could not record the force deletion of pod %s: %s", pod.Name, err)
				return
			}
			grace := int64(0)
			deleteOptions.GracePeriodSeconds = &grace
		}
		err := clientset.CoreV1().Pods(pod.Namespace).Delete(ctx, pod.Name, deleteOptions)
		logRemoval(pod, "deleted", err)
		return
	}

	eviction := &policyv1.Eviction{
		ObjectMeta: metav1.ObjectMeta{
			Name:      pod.Name,
			Namespace: pod.Namespace,
		},
		DeleteOptions: &metav1.DeleteOptions{
			Preconditions: &metav1.Preconditions{UID: &pod.UID, ResourceVersion: &pod.ResourceVersion},
		},
	}

	err := clientset.PolicyV1().Evictions(pod.Namespace).Evict(ctx, eviction)
	logRemoval(pod, "evicted", err)
}

func logRemoval(pod *corev1.Pod, action string, err error) {
	if err == nil || apierrors.IsNotFound(err) || apierrors.IsConflict(err) {
		return
	}

	if apierrors.IsTooManyRequests(err) {
		log.Info("k8s-app maintenance: pod %s could not be %s yet, the disruption budget does not allow it", pod.Name, action)
		return
	}

	log.Warn("k8s-app maintenance: pod %s could not be %s: %s", pod.Name, action, err)
}

func markHostTerminationUnverified(operation Operation, worker *nodeWorker) error {
	return worker.mutate(func(op *Operation) bool {
		if op.HostTerminationUnverified {
			return false
		}
		op.HostTerminationUnverified = true
		return true
	})
}

func patchUnschedulable(ctx context.Context, clientset *kubernetes.Clientset, node *corev1.Node, unschedulable bool) (*corev1.Node, error) {
	patch := fmt.Sprintf(
		`[{"op":"test","path":"/metadata/uid","value":%q},{"op":"test","path":"/metadata/resourceVersion","value":%q},{"op":"add","path":"/spec/unschedulable","value":%t}]`,
		string(node.UID),
		node.ResourceVersion,
		unschedulable,
	)

	return clientset.CoreV1().Nodes().Patch(
		ctx,
		node.Name,
		types.JSONPatchType,
		[]byte(patch),
		metav1.PatchOptions{},
	)
}

func controlPlaneGuard(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	operation Operation,
	node *corev1.Node,
) error {
	record, err := readClusterRecord()
	if err != nil {
		return err
	}

	controlPlanes := controlPlaneNames(record)
	if !controlPlanes[node.Name] && !nodeIsControlPlane(node) {
		return nil
	}

	if len(controlPlanes) == 1 && controlPlanes[node.Name] {
		return nil
	}

	pending, pendingErr := pendingCordons(operation.NodeName)
	if pendingErr != nil {
		return pendingErr
	}

	remaining, remainingErr := controlPlaneRemaining(ctx, clientset, controlPlanes, node.Name, pending)
	if remainingErr != nil {
		return remainingErr
	}
	if !remaining {
		return errors.New("at least one other healthy, schedulable control plane node must remain")
	}

	return nil
}

func controlPlaneNames(record shared.ClusterRecord) map[string]bool {
	controlPlanes := map[string]bool{}
	for _, recorded := range record.Nodes {
		if recorded.Group == shared.GroupControlPlane {
			controlPlanes[recorded.Hostname] = true
		}
	}
	return controlPlanes
}

func controlPlaneRemaining(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	controlPlanes map[string]bool,
	exclude string,
	busy map[string]bool,
) (bool, error) {
	remaining, err := controlPlaneRemainingCount(ctx, clientset, controlPlanes, exclude, busy)
	if err != nil {
		return false, err
	}
	return remaining > 0, nil
}

func controlPlaneRemainingCount(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	controlPlanes map[string]bool,
	exclude string,
	busy map[string]bool,
) (int, error) {
	listed, listErr := clientset.CoreV1().Nodes().List(ctx, metav1.ListOptions{})
	if listErr != nil {
		return 0, listErr
	}

	remaining := 0
	for i := range listed.Items {
		candidate := &listed.Items[i]
		if candidate.Name == exclude || !controlPlanes[candidate.Name] || busy[candidate.Name] {
			continue
		}
		if !candidate.Spec.Unschedulable && nodeIsReady(candidate) {
			remaining++
		}
	}
	return remaining, nil
}

func nodeIsControlPlane(node *corev1.Node) bool {
	_, current := node.Labels[controlPlaneLabel]
	_, legacy := node.Labels[controlPlaneLabelLegacy]
	return current || legacy
}

func nodeIsReady(node *corev1.Node) bool {
	for _, condition := range node.Status.Conditions {
		if condition.Type == corev1.NodeReady {
			return condition.Status == corev1.ConditionTrue
		}
	}
	return false
}

func knownHostnames(record shared.ClusterRecord) map[string]bool {
	known := map[string]bool{}
	for _, node := range record.Nodes {
		known[node.Hostname] = true
	}
	return known
}

func listNodePods(ctx context.Context, clientset *kubernetes.Clientset, nodeName string) ([]corev1.Pod, error) {
	list, err := clientset.CoreV1().Pods("").List(ctx, metav1.ListOptions{
		FieldSelector: fmt.Sprintf("spec.nodeName=%s", nodeName),
	})
	if err != nil {
		return nil, err
	}
	return list.Items, nil
}

func newClient(kubeconfigPath string) (*kubernetes.Clientset, error) {
	config, err := clientcmd.BuildConfigFromFlags("", kubeconfigPath)
	if err != nil {
		return nil, err
	}

	config.QPS = 20
	config.Burst = 40
	config.Timeout = apiRequestTimeout

	return kubernetes.NewForConfig(config)
}

func waitInterruptible(worker *nodeWorker, delay time.Duration) bool {
	deadline := time.Now().Add(delay)
	for {
		if worker.ctx.Err() != nil {
			return false
		}

		if time.Since(worker.lastTouch) > heartbeatInterval {
			heartbeatErr := worker.mutate(func(op *Operation) bool { return false })
			if heartbeatErr != nil && errors.Is(heartbeatErr, errConcluded) {
				return false
			}
		}

		remaining := time.Until(deadline)
		if remaining <= 0 {
			return true
		}

		step := drainPollInterval
		if remaining < step {
			step = remaining
		}
		select {
		case <-worker.ctx.Done():
			return false
		case <-time.After(step):
		}
	}
}

func pendingCordons(exclude string) (map[string]bool, error) {
	snapshot, err := Snapshot()
	if err != nil {
		return nil, err
	}

	pending := map[string]bool{}
	for nodeName, operation := range snapshot {
		if nodeName == exclude || operation.Kind == KindUncordon {
			continue
		}
		if PhaseActive(operation.Phase) {
			pending[nodeName] = true
		}
	}
	return pending, nil
}

// Conclusion
// =====================================================================================================================
// Every operation ends through finish. A failed upgrade that reached the node is marked for recovery, which blocks new
// operations on the node until the upgrade is retried or the node is uncordoned.

func finish(worker *nodeWorker, phase string, message string) {
	err := finishQuiet(worker, phase, message)
	if err != nil {
		logMutateFailure(worker.nodeName, err)
	}

	if phase == PhaseCompleted {
		log.Info("k8s-app maintenance %s: operation completed", worker.nodeName)
	} else if phase == PhaseFailed {
		log.Warn("k8s-app maintenance %s: operation failed: %s", worker.nodeName, message)
	} else {
		log.Info("k8s-app maintenance %s: operation %s: %s", worker.nodeName, phase, message)
	}
}

func finishQuiet(worker *nodeWorker, phase string, message string) error {
	return worker.mutate(func(op *Operation) bool {
		op.Phase = phase
		op.Error = message
		if KindIsUpgrade(op.Kind) && op.ExecutorSubmitted && phase != PhaseCompleted {
			op.RecoveryRequired = true
		}
		op.CancelRequested = false
		return true
	})
}

func failOperation(worker *nodeWorker, message string) {
	finish(worker, PhaseFailed, message)
	worker.cancel()
}

func logMutateFailure(nodeName string, err error) {
	if errors.Is(err, errConcluded) {
		return
	}
	log.Warn("k8s-app maintenance %s: could not persist the operation update: %s", nodeName, err)
}
