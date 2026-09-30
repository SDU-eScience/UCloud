package maintenance

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"strings"
	"sync"
	"syscall"
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

const maintenanceKindCordonDrain = "cordon-drain"
const maintenanceKindCordonDrainRetry = "cordon-drain-retry"
const maintenanceKindCordon = "cordon"
const maintenanceKindUncordon = "uncordon"

const maintenanceUncordonTimeout = 2 * time.Minute

const maintenanceRunInterval = time.Second
const maintenanceApiRetryDelay = 5 * time.Second
const maintenanceDrainPollInterval = 2 * time.Second
const maintenanceCancelPollInterval = 200 * time.Millisecond
const maintenanceApiRequestTimeout = 15 * time.Second

const maintenanceControlPlaneLabel = "node-role.kubernetes.io/control-plane"
const maintenanceControlPlaneLabelLegacy = "node-role.kubernetes.io/master"

var maintenanceSubmits = make(chan string, 256)

var maintenanceRunning = map[string]bool{}

var maintenanceWorkers sync.WaitGroup

var maintenanceErrConcluded = errors.New("the operation was already concluded")

type maintenanceWorker struct {
	nodeName  string
	nodeUid   string
	startedAt time.Time
	ctx       context.Context
	cancel    context.CancelFunc
}

func MaintenanceRun(ctx context.Context, kubeconfigPath string) {
	resume := []string{}
	maintenanceMu.Lock()
	operations, err := maintenanceReadStore()
	if err != nil {
		log.Warn("k8s-app maintenance: could not load the operation store at startup: %s", err)
	} else {
		for nodeName, operation := range operations {
			if MaintenancePhaseActive(operation.Phase) {
				resume = append(resume, nodeName)
			}
		}
	}
	maintenanceMu.Unlock()

	for _, nodeName := range resume {
		maintenanceDispatch(ctx, kubeconfigPath, nodeName)
	}

	ticker := time.NewTicker(maintenanceRunInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			maintenanceWorkers.Wait()
			return
		case nodeName := <-maintenanceSubmits:
			maintenanceDispatch(ctx, kubeconfigPath, nodeName)
		case <-ticker.C:
			for _, nodeName := range maintenanceActiveNodeNames() {
				maintenanceDispatch(ctx, kubeconfigPath, nodeName)
			}
		}
	}
}

func maintenanceActiveNodeNames() []string {
	maintenanceMu.Lock()
	defer maintenanceMu.Unlock()

	operations, err := maintenanceReadStore()
	if err != nil {
		return nil
	}

	names := make([]string, 0, len(operations))
	for nodeName, operation := range operations {
		if MaintenancePhaseActive(operation.Phase) {
			names = append(names, nodeName)
		}
	}
	return names
}

func maintenanceDispatch(ctx context.Context, kubeconfigPath string, nodeName string) {
	maintenanceMu.Lock()
	if maintenanceRunning[nodeName] {
		maintenanceMu.Unlock()
		return
	}
	maintenanceRunning[nodeName] = true
	maintenanceMu.Unlock()

	maintenanceWorkers.Add(1)
	go func() {
		defer maintenanceWorkers.Done()
		defer func() {
			maintenanceMu.Lock()
			delete(maintenanceRunning, nodeName)
			maintenanceMu.Unlock()
		}()
		maintenanceExecuteOperation(ctx, kubeconfigPath, nodeName)
	}()
}

func maintenanceSignalSubmit(nodeName string) {
	select {
	case maintenanceSubmits <- nodeName:
	default:
	}
}

type maintenanceStepResult int

const (
	maintenanceStepAdvanced maintenanceStepResult = iota
	maintenanceStepRetry
	maintenanceStepTerminal
)

func maintenanceExecuteOperation(ctx context.Context, kubeconfigPath string, nodeName string) {
	operation, ok := maintenanceOperationLoad(nodeName)
	if !ok || !MaintenancePhaseActive(operation.Phase) {
		return
	}

	release, ok := maintenanceAcquireNodeLock(maintenanceOperationPath(nodeName))
	if !ok {
		return
	}
	defer release()

	operation, ok = maintenanceOperationLoad(nodeName)
	if !ok || !MaintenancePhaseActive(operation.Phase) {
		return
	}

	workerCtx, cancel := context.WithDeadline(ctx, operation.Deadline)
	defer cancel()

	worker := &maintenanceWorker{
		nodeName:  nodeName,
		nodeUid:   operation.NodeUid,
		startedAt: operation.StartedAt,
		ctx:       workerCtx,
		cancel:    cancel,
	}
	defer maintenanceFinishInterruptedWorker(worker)
	go maintenanceWatchWorker(worker)

	clientset, ok := maintenanceWorkerClient(workerCtx, kubeconfigPath, operation, worker)
	if !ok {
		return
	}

	for {
		operation, ok := maintenanceOperationLoad(nodeName)
		if !ok || !maintenanceWorkerMatches(operation, worker) {
			return
		}
		if !MaintenancePhaseActive(operation.Phase) {
			return
		}

		if operation.CancelRequested {
			maintenanceFinish(operation, worker, maintenancePhaseCancelled, maintenanceCancelMessage(operation.Kind))
			return
		}

		if workerCtx.Err() != nil {
			return
		}

		if time.Now().After(operation.Deadline) {
			maintenanceFinish(operation, worker, maintenancePhaseFailed, "the operation timed out")
			return
		}

		var result maintenanceStepResult
		switch operation.Kind {
		case maintenanceKindUncordon:
			result = maintenanceExecuteUncordon(workerCtx, clientset, operation, worker)
		case maintenanceKindCordon:
			result = maintenanceExecuteCordon(workerCtx, clientset, operation, worker)
		default:
			result = maintenanceExecuteCordonDrain(workerCtx, clientset, operation, worker)
		}

		if result == maintenanceStepTerminal {
			return
		}
	}
}

func maintenanceWatchWorker(worker *maintenanceWorker) {
	ticker := time.NewTicker(maintenanceCancelPollInterval)
	defer ticker.Stop()

	for {
		select {
		case <-worker.ctx.Done():
			return
		case <-ticker.C:
			operation, ok := maintenanceOperationLoad(worker.nodeName)
			if !ok || !maintenanceWorkerMatches(operation, worker) || !MaintenancePhaseActive(operation.Phase) || operation.CancelRequested {
				worker.cancel()
				return
			}
		}
	}
}

func maintenanceFinishInterruptedWorker(worker *maintenanceWorker) {
	operation, ok := maintenanceOperationLoad(worker.nodeName)
	if !ok || !maintenanceWorkerMatches(operation, worker) || !MaintenancePhaseActive(operation.Phase) {
		return
	}
	if operation.CancelRequested {
		maintenanceFinish(operation, worker, maintenancePhaseCancelled, maintenanceCancelMessage(operation.Kind))
	} else if time.Now().After(operation.Deadline) {
		maintenanceFinish(operation, worker, maintenancePhaseFailed, "the operation timed out")
	}
}

func maintenanceWorkerClient(
	parentCtx context.Context,
	kubeconfigPath string,
	operation MaintenanceOperation,
	worker *maintenanceWorker,
) (*kubernetes.Clientset, bool) {
	for {
		if parentCtx.Err() != nil {
			return nil, false
		}

		current, ok := maintenanceOperationLoad(operation.NodeName)
		if !ok || !maintenanceWorkerMatches(current, worker) || !MaintenancePhaseActive(current.Phase) {
			return nil, false
		}

		if current.CancelRequested {
			maintenanceFinish(current, worker, maintenancePhaseCancelled, maintenanceCancelMessage(current.Kind))
			return nil, false
		}

		if time.Now().After(current.Deadline) {
			maintenanceFinish(current, worker, maintenancePhaseFailed, "the operation timed out")
			return nil, false
		}

		clientset, err := maintenanceNewClient(kubeconfigPath)
		if err == nil {
			return clientset, true
		}

		log.Warn("k8s-app maintenance %s: could not build the Kubernetes client: %s", operation.NodeName, err)
		if !maintenanceWaitInterruptible(operation.NodeName, worker, maintenanceApiRetryDelay) {
			maintenanceCancelOnWait(operation.NodeName, worker)
			return nil, false
		}
	}
}

func maintenanceWorkerMatches(operation MaintenanceOperation, worker *maintenanceWorker) bool {
	return operation.NodeUid == worker.nodeUid && operation.StartedAt.Equal(worker.startedAt)
}

func maintenanceCancelMessage(kind string) string {
	if kind == maintenanceKindUncordon {
		return "cancelled by request"
	}
	return "cancelled by request; the node remains cordoned"
}
func maintenanceExecuteCordonDrain(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	operation MaintenanceOperation,
	worker *maintenanceWorker,
) maintenanceStepResult {
	nodeName := operation.NodeName

	switch operation.Phase {
	case maintenancePhaseRequested:
		maintenanceSetPhase(operation, worker, maintenancePhaseValidating)
		return maintenanceStepAdvanced

	case maintenancePhaseValidating:
		node, result := maintenanceGetNode(ctx, clientset, operation, worker)
		if result != maintenanceStepAdvanced {
			return result
		}

		record, ok := maintenanceReadClusterRecordWithRetry(operation, worker)
		if !ok {
			return maintenanceStepRetry
		}

		if record.Phase != "created" {
			maintenanceFinish(operation, worker, maintenancePhaseFailed, fmt.Sprintf("the cluster is not ready (state: %s)", record.Phase))
			return maintenanceStepTerminal
		}

		if !maintenanceKnownHostnames(record)[nodeName] {
			maintenanceFinish(operation, worker, maintenancePhaseFailed, "the node is not part of this cluster")
			return maintenanceStepTerminal
		}

		guardErr := maintenanceControlPlaneGuard(ctx, clientset, operation, worker, node)
		if guardErr != nil {
			if !errors.Is(guardErr, maintenanceErrConcluded) {
				maintenanceFinish(operation, worker, maintenancePhaseFailed, guardErr.Error())
			}
			return maintenanceStepTerminal
		}

		originalUnschedulable := node.Spec.Unschedulable
		maintenanceMutate(nodeName, operation.NodeUid, operation.StartedAt, func(op *MaintenanceOperation) bool {
			op.OriginalUnschedulable = originalUnschedulable
			op.Phase = maintenancePhaseCordoning
			return true
		})
		return maintenanceStepAdvanced

	case maintenancePhaseCordoning:
		node, result := maintenanceGetNode(ctx, clientset, operation, worker)
		if result != maintenanceStepAdvanced {
			return result
		}
		guardErr := maintenanceControlPlaneGuard(ctx, clientset, operation, worker, node)
		if guardErr != nil {
			if !errors.Is(guardErr, maintenanceErrConcluded) {
				maintenanceFinish(operation, worker, maintenancePhaseFailed, guardErr.Error())
			}
			return maintenanceStepTerminal
		}

		if !node.Spec.Unschedulable {
			_, err := maintenancePatchUnschedulable(ctx, clientset, node, true)
			if err != nil {
				return maintenancePatchStepResult(operation, worker, "cordon", err)
			}
		}

		maintenanceSetPhase(operation, worker, maintenancePhaseDraining)
		return maintenanceStepAdvanced

	case maintenancePhaseDraining:
		maintenanceDrainNode(ctx, clientset, operation, worker)
		return maintenanceStepTerminal

	default:
		return maintenanceStepTerminal
	}
}

func maintenanceExecuteCordon(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	operation MaintenanceOperation,
	worker *maintenanceWorker,
) maintenanceStepResult {
	switch operation.Phase {
	case maintenancePhaseRequested:
		maintenanceSetPhase(operation, worker, maintenancePhaseCordoning)
		return maintenanceStepAdvanced

	case maintenancePhaseCordoning:
		node, result := maintenanceGetNode(ctx, clientset, operation, worker)
		if result != maintenanceStepAdvanced {
			return result
		}

		originalUnschedulable := node.Spec.Unschedulable
		maintenanceMutate(operation.NodeName, operation.NodeUid, operation.StartedAt, func(op *MaintenanceOperation) bool {
			op.OriginalUnschedulable = originalUnschedulable
			return true
		})

		if !node.Spec.Unschedulable {
			_, err := maintenancePatchUnschedulable(ctx, clientset, node, true)
			if err != nil {
				return maintenancePatchStepResult(operation, worker, "cordon", err)
			}
		}

		maintenanceFinish(operation, worker, maintenancePhaseCompleted, "")
		return maintenanceStepTerminal

	default:
		return maintenanceStepTerminal
	}
}

func maintenanceExecuteUncordon(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	operation MaintenanceOperation,
	worker *maintenanceWorker,
) maintenanceStepResult {
	switch operation.Phase {
	case maintenancePhaseRequested, maintenancePhaseValidating:
		node, result := maintenanceGetNode(ctx, clientset, operation, worker)
		if result != maintenanceStepAdvanced {
			return result
		}

		originalUnschedulable := node.Spec.Unschedulable
		maintenanceMutate(operation.NodeName, operation.NodeUid, operation.StartedAt, func(op *MaintenanceOperation) bool {
			op.OriginalUnschedulable = originalUnschedulable
			op.Phase = maintenancePhaseCordoning
			return true
		})
		return maintenanceStepAdvanced

	case maintenancePhaseCordoning:
		node, result := maintenanceGetNode(ctx, clientset, operation, worker)
		if result != maintenanceStepAdvanced {
			return result
		}

		if node.Spec.Unschedulable {
			_, err := maintenancePatchUnschedulable(ctx, clientset, node, false)
			if err != nil {
				return maintenancePatchStepResult(operation, worker, "uncordon", err)
			}
		}

		maintenanceFinish(operation, worker, maintenancePhaseCompleted, "")
		return maintenanceStepTerminal

	default:
		return maintenanceStepTerminal
	}
}

func maintenanceDrainNode(ctx context.Context, clientset *kubernetes.Clientset, operation MaintenanceOperation, worker *maintenanceWorker) {
	nodeName := operation.NodeName
	resources := map[string][]metav1.APIResource{}

	for {
		current, ok := maintenanceOperationLoad(nodeName)
		if !ok || !maintenanceWorkerMatches(current, worker) || !MaintenancePhaseActive(current.Phase) {
			return
		}
		operation = current

		if operation.CancelRequested {
			maintenanceFinish(operation, worker, maintenancePhaseCancelled, maintenanceCancelMessage(operation.Kind))
			return
		}

		if ctx.Err() != nil {
			return
		}

		if time.Now().After(operation.Deadline) {
			maintenanceFinish(operation, worker, maintenancePhaseFailed, "the drain timed out with pods remaining on the node")
			return
		}

		node, result := maintenanceGetNode(ctx, clientset, operation, worker)
		if result == maintenanceStepTerminal {
			return
		}
		if result == maintenanceStepRetry {
			continue
		}
		if !node.Spec.Unschedulable {
			maintenanceFinish(operation, worker, maintenancePhaseFailed, "the node was uncordoned during the drain")
			return
		}

		pods, err := maintenanceListNodePods(ctx, clientset, nodeName)
		if err != nil {
			log.Warn("k8s-app maintenance %s: could not list the pods of the node: %s", nodeName, err)
			if !maintenanceWaitInterruptible(nodeName, worker, maintenanceApiRetryDelay) {
				maintenanceCancelOnWait(nodeName, worker)
				return
			}
			continue
		}

		blockers, deletable, terminating := maintenanceClassifyPods(ctx, clientset, operation, pods, resources)

		if len(blockers) > 0 && len(deletable) == 0 {
			if len(terminating) > 0 {
				if !maintenanceWaitInterruptible(nodeName, worker, maintenanceDrainPollInterval) {
					maintenanceCancelOnWait(nodeName, worker)
					return
				}
				continue
			}
			maintenanceFinish(operation, worker, maintenancePhaseBlocked,
				fmt.Sprintf("the drain is blocked by pods that were not approved for deletion: %s", strings.Join(blockers, "; ")))
			return
		}

		if len(deletable) == 0 && len(terminating) == 0 {
			confirmed, confirmErr := maintenanceListNodePods(ctx, clientset, nodeName)
			if confirmErr == nil {
				confirmBlockers, confirmDeletable, confirmTerminating := maintenanceClassifyPods(ctx, clientset, operation, confirmed, resources)
				if len(confirmBlockers) == 0 && len(confirmDeletable) == 0 && len(confirmTerminating) == 0 {
					node, result := maintenanceGetNode(ctx, clientset, operation, worker)
					if result == maintenanceStepTerminal {
						return
					}
					if result == maintenanceStepRetry {
						continue
					}
					if !node.Spec.Unschedulable {
						maintenanceFinish(operation, worker, maintenancePhaseFailed, "the node was uncordoned during the drain")
						return
					}
					maintenanceFinish(operation, worker, maintenancePhaseCompleted, "")
					return
				}
			}
		} else {
			for _, pod := range deletable {
				if ctx.Err() != nil || maintenanceStopRequested(nodeName, worker) {
					return
				}
				node, result := maintenanceGetNode(ctx, clientset, operation, worker)
				if result == maintenanceStepTerminal {
					return
				}
				if result == maintenanceStepRetry {
					break
				}
				if !node.Spec.Unschedulable {
					maintenanceFinish(operation, worker, maintenancePhaseFailed, "the node was uncordoned during the drain")
					return
				}
				maintenanceRemovePod(ctx, clientset, operation, pod)
			}
		}

		if !maintenanceWaitInterruptible(nodeName, worker, maintenanceDrainPollInterval) {
			maintenanceCancelOnWait(nodeName, worker)
			return
		}
	}
}

func maintenanceStopRequested(nodeName string, worker *maintenanceWorker) bool {
	operation, ok := maintenanceOperationLoad(nodeName)
	if !ok || !maintenanceWorkerMatches(operation, worker) || !MaintenancePhaseActive(operation.Phase) {
		return true
	}

	if operation.CancelRequested {
		worker.cancel()
		return true
	}

	return false
}

func maintenanceCancelOnWait(nodeName string, worker *maintenanceWorker) {
	worker.cancel()
	if operation, ok := maintenanceOperationLoad(nodeName); ok && maintenanceWorkerMatches(operation, worker) {
		if MaintenancePhaseActive(operation.Phase) && operation.CancelRequested {
			maintenanceFinish(operation, worker, maintenancePhaseCancelled, maintenanceCancelMessage(operation.Kind))
		}
	}
}

func maintenanceClassifyPods(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	operation MaintenanceOperation,
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
			terminating = append(terminating, maintenanceNamespacedName(pod.Namespace, pod.Name))
			continue
		}

		owner := maintenanceControllerOwnerOf(pod)
		unmanaged := owner == nil

		if owner != nil {
			live, known := controllers[owner.UID]
			if !known {
				var err error
				live, err = maintenanceControllerLive(ctx, clientset, pod, owner, resources)
				if err != nil {
					blockers = append(blockers, fmt.Sprintf("%s: could not verify its controller: %s", maintenanceNamespacedName(pod.Namespace, pod.Name), err))
					continue
				}
				controllers[owner.UID] = live
			}
			if owner.Kind == "DaemonSet" && live {
				continue
			}
			unmanaged = !live
		}

		namespaced := maintenanceNamespacedName(pod.Namespace, pod.Name)

		if (unmanaged || maintenancePodHasEmptyDir(pod)) && !operation.Options.DeleteVolatilePods {
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

func maintenanceControllerOwnerOf(pod *corev1.Pod) *metav1.OwnerReference {
	for i := range pod.OwnerReferences {
		owner := &pod.OwnerReferences[i]
		if owner.Controller != nil && *owner.Controller {
			return owner
		}
	}
	return nil
}

func maintenancePodHasEmptyDir(pod *corev1.Pod) bool {
	for i := range pod.Spec.Volumes {
		if pod.Spec.Volumes[i].EmptyDir != nil {
			return true
		}
	}
	return false
}

func maintenanceControllerLive(
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
		err = clientset.CoreV1().RESTClient().Get().AbsPath(apiPath).Do(ctx).Into(&listed)
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

func maintenanceRemovePod(ctx context.Context, clientset *kubernetes.Clientset, operation MaintenanceOperation, pod *corev1.Pod) {
	if operation.Options.BypassDisruptionBudgets {
		deleteOptions := metav1.DeleteOptions{
			Preconditions: &metav1.Preconditions{UID: &pod.UID},
		}
		if operation.Options.ForceDelete {
			grace := int64(0)
			deleteOptions.GracePeriodSeconds = &grace
		}

		err := clientset.CoreV1().Pods(pod.Namespace).Delete(ctx, pod.Name, deleteOptions)
		maintenanceLogRemoval(pod, "deleted", err)
		return
	}

	eviction := &policyv1.Eviction{
		ObjectMeta: metav1.ObjectMeta{
			Name:      pod.Name,
			Namespace: pod.Namespace,
		},
		DeleteOptions: &metav1.DeleteOptions{
			Preconditions: &metav1.Preconditions{UID: &pod.UID},
		},
	}

	err := clientset.PolicyV1().Evictions(pod.Namespace).Evict(ctx, eviction)
	maintenanceLogRemoval(pod, "evicted", err)
}

func maintenanceLogRemoval(pod *corev1.Pod, action string, err error) {
	if err == nil || apierrors.IsNotFound(err) || apierrors.IsConflict(err) {
		return
	}

	if apierrors.IsTooManyRequests(err) {
		log.Info("k8s-app maintenance: pod %s could not be %s yet, the disruption budget does not allow it", pod.Name, action)
		return
	}

	log.Warn("k8s-app maintenance: pod %s could not be %s: %s", pod.Name, action, err)
}

func maintenancePatchUnschedulable(ctx context.Context, clientset *kubernetes.Clientset, node *corev1.Node, unschedulable bool) (*corev1.Node, error) {
	patch := fmt.Sprintf(
		`[{"op":"test","path":"/metadata/uid","value":%q},{"op":"add","path":"/spec/unschedulable","value":%t}]`,
		string(node.UID),
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

func maintenancePatchStepResult(operation MaintenanceOperation, worker *maintenanceWorker, action string, err error) maintenanceStepResult {
	if apierrors.IsInvalid(err) {
		maintenanceFinish(operation, worker, maintenancePhaseFailed,
			fmt.Sprintf("the node changed while it was being patched (%s): %s", action, err))
		return maintenanceStepTerminal
	}

	log.Warn("k8s-app maintenance %s: could not %s the node: %s", operation.NodeName, action, err)
	if !maintenanceWaitInterruptible(operation.NodeName, worker, maintenanceApiRetryDelay) {
		maintenanceCancelOnWait(operation.NodeName, worker)
		return maintenanceStepTerminal
	}
	return maintenanceStepRetry
}

func maintenanceControlPlaneGuard(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	operation MaintenanceOperation,
	worker *maintenanceWorker,
	node *corev1.Node,
) error {
	record, ok := maintenanceReadClusterRecordWithRetry(operation, worker)
	if !ok {
		return maintenanceErrConcluded
	}

	controlPlanes := map[string]bool{}
	for _, recorded := range record.Nodes {
		if recorded.Group == shared.GroupControlPlane {
			controlPlanes[recorded.Hostname] = true
		}
	}
	if !controlPlanes[node.Name] && !maintenanceNodeIsControlPlane(node) {
		return nil
	}

	if len(controlPlanes) == 1 && controlPlanes[node.Name] {
		return nil
	}

	for {
		listed, listErr := clientset.CoreV1().Nodes().List(ctx, metav1.ListOptions{})
		if listErr != nil {
			log.Warn("k8s-app maintenance %s: could not list the control plane nodes: %s", operation.NodeName, listErr)
			if !maintenanceWaitInterruptible(operation.NodeName, worker, maintenanceApiRetryDelay) {
				return maintenanceErrConcluded
			}
			continue
		}
		pending, pendingErr := maintenancePendingCordons(operation.NodeName)
		if pendingErr != nil {
			log.Warn("k8s-app maintenance %s: could not check pending cordons: %s", operation.NodeName, pendingErr)
			if !maintenanceWaitInterruptible(operation.NodeName, worker, maintenanceApiRetryDelay) {
				return maintenanceErrConcluded
			}
			continue
		}
		for i := range listed.Items {
			candidate := &listed.Items[i]
			if candidate.Name == node.Name || !controlPlanes[candidate.Name] {
				continue
			}
			if !candidate.Spec.Unschedulable && !pending[candidate.Name] && maintenanceNodeIsReady(candidate) {
				return nil
			}
		}
		return errors.New("at least one other healthy, schedulable control plane node must remain")
	}
}

func maintenanceNodeIsControlPlane(node *corev1.Node) bool {
	_, current := node.Labels[maintenanceControlPlaneLabel]
	_, legacy := node.Labels[maintenanceControlPlaneLabelLegacy]
	return current || legacy
}

func maintenanceNodeIsReady(node *corev1.Node) bool {
	for _, condition := range node.Status.Conditions {
		if condition.Type == corev1.NodeReady {
			return condition.Status == corev1.ConditionTrue
		}
	}
	return false
}

func maintenanceGetNode(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	operation MaintenanceOperation,
	worker *maintenanceWorker,
) (*corev1.Node, maintenanceStepResult) {
	nodeName := operation.NodeName
	node, err := clientset.CoreV1().Nodes().Get(ctx, nodeName, metav1.GetOptions{})
	if err != nil {
		if apierrors.IsNotFound(err) {
			maintenanceFinish(operation, worker, maintenancePhaseFailed, "the node was not found in the cluster")
			return nil, maintenanceStepTerminal
		}

		log.Warn("k8s-app maintenance %s: could not read the node: %s", nodeName, err)
		if !maintenanceWaitInterruptible(nodeName, worker, maintenanceApiRetryDelay) {
			maintenanceCancelOnWait(nodeName, worker)
			return nil, maintenanceStepTerminal
		}
		return nil, maintenanceStepRetry
	}

	if string(node.UID) != operation.NodeUid {
		maintenanceFinish(operation, worker, maintenancePhaseFailed, "the node uid does not match the requested node")
		return nil, maintenanceStepTerminal
	}

	return node, maintenanceStepAdvanced
}

func maintenanceReadClusterRecordWithRetry(operation MaintenanceOperation, worker *maintenanceWorker) (shared.ClusterRecord, bool) {
	for {
		record, err := maintenanceReadClusterRecord()
		if err == nil {
			return record, true
		}

		if time.Now().After(operation.Deadline) {
			maintenanceFinish(operation, worker, maintenancePhaseFailed, fmt.Sprintf("could not read the cluster record: %s", err))
			return shared.ClusterRecord{}, false
		}

		log.Warn("k8s-app maintenance %s: could not read the cluster record: %s", operation.NodeName, err)
		if !maintenanceWaitInterruptible(operation.NodeName, worker, maintenanceApiRetryDelay) {
			maintenanceCancelOnWait(operation.NodeName, worker)
			return shared.ClusterRecord{}, false
		}
	}
}

func maintenanceListNodePods(ctx context.Context, clientset *kubernetes.Clientset, nodeName string) ([]corev1.Pod, error) {
	list, err := clientset.CoreV1().Pods("").List(ctx, metav1.ListOptions{
		FieldSelector: fmt.Sprintf("spec.nodeName=%s", nodeName),
	})
	if err != nil {
		return nil, err
	}
	return list.Items, nil
}

func maintenanceNewClient(kubeconfigPath string) (*kubernetes.Clientset, error) {
	config, err := clientcmd.BuildConfigFromFlags("", kubeconfigPath)
	if err != nil {
		return nil, err
	}

	config.QPS = 20
	config.Burst = 40
	config.Timeout = maintenanceApiRequestTimeout

	return kubernetes.NewForConfig(config)
}

func maintenanceAcquireNodeLock(path string) (func(), bool) {
	file, err := os.OpenFile(path, os.O_CREATE|os.O_RDWR, 0660)
	if err != nil {
		log.Warn("k8s-app maintenance: could not open the node lock %s: %s", path, err)
		return nil, false
	}

	err = syscall.Flock(int(file.Fd()), syscall.LOCK_EX|syscall.LOCK_NB)
	if err == nil {
		return func() {
			_ = syscall.Flock(int(file.Fd()), syscall.LOCK_UN)
			_ = file.Close()
		}, true
	}

	_ = file.Close()
	if !errors.Is(err, syscall.EWOULDBLOCK) {
		log.Warn("k8s-app maintenance: could not lock %s: %s", path, err)
		return nil, false
	}

	log.Info("k8s-app maintenance: the node lock %s is held by another worker; leaving the operation to that worker", path)
	return nil, false
}

func maintenanceWaitInterruptible(nodeName string, worker *maintenanceWorker, delay time.Duration) bool {
	deadline := time.Now().Add(delay)
	for {
		if worker.ctx.Err() != nil {
			return false
		}
		operation, ok := maintenanceOperationLoad(nodeName)
		if !ok || !maintenanceWorkerMatches(operation, worker) || !MaintenancePhaseActive(operation.Phase) {
			return false
		}

		if operation.CancelRequested {
			return false
		}

		remaining := time.Until(deadline)
		if remaining <= 0 {
			return true
		}

		step := maintenanceCancelPollInterval
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

func maintenancePendingCordons(exclude string) (map[string]bool, error) {
	maintenanceMu.Lock()
	defer maintenanceMu.Unlock()

	operations, err := maintenanceReadStore()
	if err != nil {
		return nil, err
	}

	pending := map[string]bool{}
	for nodeName, operation := range operations {
		if nodeName == exclude || operation.Kind == maintenanceKindUncordon {
			continue
		}
		if MaintenancePhaseActive(operation.Phase) {
			pending[nodeName] = true
		}
	}
	return pending, nil
}

func maintenanceOperationLoad(nodeName string) (MaintenanceOperation, bool) {
	maintenanceMu.Lock()
	defer maintenanceMu.Unlock()

	operations, err := maintenanceReadStore()
	if err != nil {
		return MaintenanceOperation{}, false
	}

	operation, exists := operations[nodeName]
	return operation, exists
}

func maintenanceSetPhase(operation MaintenanceOperation, worker *maintenanceWorker, phase string) {
	maintenanceMutate(operation.NodeName, operation.NodeUid, operation.StartedAt, func(op *MaintenanceOperation) bool {
		if op.Phase == phase {
			return false
		}
		op.Phase = phase
		return true
	})
}

func maintenanceFinish(operation MaintenanceOperation, worker *maintenanceWorker, phase string, message string) {
	maintenanceMutate(operation.NodeName, operation.NodeUid, operation.StartedAt, func(op *MaintenanceOperation) bool {
		op.Phase = phase
		op.Error = message
		op.CancelRequested = false
		return true
	})

	if phase == maintenancePhaseCompleted {
		log.Info("k8s-app maintenance %s: operation completed", operation.NodeName)
	} else if phase == maintenancePhaseFailed {
		log.Warn("k8s-app maintenance %s: operation failed: %s", operation.NodeName, message)
	} else {
		log.Info("k8s-app maintenance %s: operation %s: %s", operation.NodeName, phase, message)
	}
}
