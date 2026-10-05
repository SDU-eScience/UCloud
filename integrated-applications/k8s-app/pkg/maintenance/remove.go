package maintenance

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	corev1 "k8s.io/api/core/v1"
	apierrors "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"

	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/log"
)

// Node removal
// =====================================================================================================================
// A removal runs in three stages. The first stage suspends the traffic of control plane nodes, drains the node when the
// operator asked for it, and deletes the Kubernetes Node object, which makes k3s remove the etcd member on control
// plane nodes. The second stage verifies that the control plane still answers. The third stage removes the
// infrastructure: the job, the private network address, the node record and the input files. The stages are split by
// the NodeDeleted and ResourcesRemoved flags, so an interrupted removal resumes where it stopped. Once the Kubernetes
// Node object is deleted, the removal no longer accepts cancellation.

const removeVerifyInterval = 5 * time.Second

func removePastPointOfNoReturn(operation Operation) bool {
	return operation.Kind == KindRemove && (operation.NodeDeleted || operation.ResourcesRemoved)
}

func RemovePastPointOfNoReturn(operation Operation) bool {
	return removePastPointOfNoReturn(operation)
}

func runRemove(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	operation Operation,
	worker *nodeWorker,
) error {
	record, err := worker.clusterRecord()
	if err != nil {
		return fmt.Errorf("could not read the cluster record: %s", err)
	}

	node, known := removeNodeRecordOrReconstruct(record, operation.NodeName)
	if !known {
		return fmt.Errorf("the node %s is not part of this cluster", operation.NodeName)
	}

	if !operation.NodeDeleted {
		if node.Group == shared.GroupControlPlane && node.JobId != "" {
			trafficErr := upgradeSuspendTraffic(operation, worker)
			if trafficErr != nil {
				return fmt.Errorf("could not suspend the traffic of the node: %s", trafficErr)
			}
		}

		live, exists, liveErr := removeLiveNode(ctx, clientset, operation)
		if liveErr != nil {
			return liveErr
		}

		if exists && operation.Options.Drain {
			outcome, cordonErr := runCordonDrain(ctx, clientset, operation, worker, false, true)
			if cordonErr != nil {
				return cordonErr
			}
			if outcome != drainDone {
				return nil
			}

			current, ok := worker.checkpoint()
			if !ok {
				return nil
			}
			operation = current
		}

		if exists {
			if deleteErr := removeDeleteNode(ctx, clientset, operation, worker, live); deleteErr != nil {
				return deleteErr
			}
		}

		persistErr := worker.mutate(func(op *Operation) bool {
			op.NodeDeleted = true
			return true
		})
		if persistErr != nil {
			return fmt.Errorf("could not record the node deletion: %s", persistErr)
		}
		operation.NodeDeleted = true
	}

	if node.Group == shared.GroupControlPlane {
		if verifyErr := removeVerifyControlPlane(ctx, clientset, worker); verifyErr != nil {
			return verifyErr
		}
	}

	if !operation.ResourcesRemoved {
		if resourcesErr := removeInfrastructure(worker, node); resourcesErr != nil {
			return resourcesErr
		}

		persistErr := worker.mutate(func(op *Operation) bool {
			op.ResourcesRemoved = true
			return true
		})
		if persistErr != nil {
			return fmt.Errorf("could not record the infrastructure removal: %s", persistErr)
		}
	}

	finish(worker, PhaseCompleted, "")
	removeTombstoneOperation(worker)
	return nil
}

func removeNodeRecordOrReconstruct(record shared.ClusterRecord, nodeName string) (shared.ClusterNodeRecord, bool) {
	for _, node := range record.Nodes {
		if node.Hostname == nodeName {
			return node, true
		}
	}

	dash := strings.LastIndex(nodeName, "-")
	if dash <= 0 || dash == len(nodeName)-1 {
		return shared.ClusterNodeRecord{}, false
	}

	allocationId, err := strconv.Atoi(nodeName[dash+1:])
	if err != nil {
		return shared.ClusterNodeRecord{}, false
	}

	return shared.ClusterNodeRecord{
		AllocationId: allocationId,
		Group:        nodeName[:dash],
		Hostname:     nodeName,
	}, true
}

func removeLiveNode(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	operation Operation,
) (*corev1.Node, bool, error) {
	live, err := clientset.CoreV1().Nodes().Get(ctx, operation.NodeName, metav1.GetOptions{})
	if apierrors.IsNotFound(err) {
		return nil, false, nil
	}
	if err != nil {
		return nil, false, fmt.Errorf("could not read the node %s: %s", operation.NodeName, err)
	}

	if operation.NodeUid != "" && string(live.UID) != operation.NodeUid {
		return nil, false, errors.New("the node uid does not match the requested node")
	}

	return live, true, nil
}

func removeDeleteNode(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	operation Operation,
	worker *nodeWorker,
	live *corev1.Node,
) error {
	operationLogStage(
		worker,
		"deleting-node",
		fmt.Sprintf("The Kubernetes node %s is deleted, which also removes it from etcd", operation.NodeName),
	)

	deleteErr := clientset.CoreV1().Nodes().Delete(ctx, operation.NodeName, metav1.DeleteOptions{
		Preconditions: &metav1.Preconditions{UID: &live.UID},
	})
	if deleteErr != nil && !apierrors.IsNotFound(deleteErr) {
		return fmt.Errorf("could not delete the Kubernetes node %s: %s", operation.NodeName, deleteErr)
	}

	return nil
}

func removeVerifyControlPlane(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	worker *nodeWorker,
) error {
	for {
		if worker.ctx.Err() != nil {
			return nil
		}
		if _, ok := worker.checkpoint(); !ok {
			return nil
		}

		checkCtx, cancel := context.WithTimeout(ctx, apiRequestTimeout)
		_, listErr := clientset.CoreV1().Nodes().List(checkCtx, metav1.ListOptions{})
		cancel()
		if listErr == nil {
			return nil
		}

		log.Info(
			"k8s-app maintenance %s: the control plane did not answer after the removal: %s",
			worker.nodeName,
			listErr,
		)
		operationLogStage(
			worker,
			"verifying-quorum",
			"Waiting for the control plane to confirm that the etcd quorum is intact",
		)
		if !waitInterruptible(worker, removeVerifyInterval) {
			return nil
		}
	}
}

func removeInfrastructure(worker *nodeWorker, node shared.ClusterNodeRecord) error {
	client, err := shared.RpcGrantClientNew()
	if err != nil {
		return fmt.Errorf("could not open the stack control client: %s", err)
	}

	if node.JobId != "" {
		terminateErr := shared.RpcGrantTerminateJob(client, node.JobId)
		if terminateErr != nil && terminateErr.StatusCode != 404 {
			return fmt.Errorf("could not terminate the job of the node: %s", terminateErr)
		}

		if waitErr := removeWaitJobFinal(worker, client, node.JobId); waitErr != nil {
			return waitErr
		}
	}

	if node.ReservationId != "" {
		if waitErr := removeWaitReservationFree(worker, client, node.ReservationId); waitErr != nil {
			return waitErr
		}
	}

	if recordErr := removeNodeRecordFromCluster(node.Hostname); recordErr != nil {
		return recordErr
	}

	os.RemoveAll(shared.NodeDirForAllocation(node.AllocationId))
	os.Remove(filepath.Join(shared.NodeAgentCoordinatorTokensDir, shared.SanitizeForPath(node.Hostname)))
	return nil
}

func removeWaitJobFinal(worker *nodeWorker, client shared.RpcGrantClient, jobId string) error {
	for {
		if worker.ctx.Err() != nil {
			return nil
		}
		if _, ok := worker.checkpoint(); !ok {
			return nil
		}

		job, found, herr := shared.RpcGrantRetrieveJob(client, jobId)
		if herr != nil && herr.StatusCode != 404 {
			log.Info(
				"k8s-app maintenance %s: could not read the job %s during the removal: %s",
				worker.nodeName,
				jobId,
				herr,
			)
		}
		if herr == nil && found && !job.Status.State.IsFinal() {
			operationLogStage(
				worker,
				"waiting-job",
				fmt.Sprintf("Waiting for the job %s of the node to stop", jobId),
			)
			if !waitInterruptible(worker, removeVerifyInterval) {
				return nil
			}
			continue
		}

		return nil
	}
}

func removeWaitReservationFree(worker *nodeWorker, client shared.RpcGrantClient, reservationId string) error {
	for attempt := 0; ; attempt++ {
		if worker.ctx.Err() != nil {
			return nil
		}
		if _, ok := worker.checkpoint(); !ok {
			return nil
		}

		deleteErr := shared.RpcGrantDeletePrivateNetworkIp(client, reservationId)
		if deleteErr == nil || deleteErr.StatusCode == 404 {
			return nil
		}
		if deleteErr.StatusCode != 400 {
			return fmt.Errorf("could not release the private network address: %s", deleteErr)
		}

		if attempt == 0 {
			operationLogStage(
				worker,
				"waiting-reservation",
				"Waiting for the private network address of the node to be released",
			)
		}
		if !waitInterruptible(worker, removeVerifyInterval) {
			return nil
		}
	}
}

func removeNodeRecordFromCluster(nodeName string) error {
	client, err := shared.ClusterStateClientNewHost()
	if err != nil {
		return err
	}

	for attempt := 0; ; attempt++ {
		snapshot, readErr := shared.ClusterStateRead(client)
		if readErr != nil {
			return readErr
		}
		if !snapshot.Found {
			return errors.New("the cluster record does not exist")
		}

		record := snapshot.Record
		found := false
		for i := range record.Nodes {
			if record.Nodes[i].Hostname == nodeName {
				record.Nodes = append(record.Nodes[:i], record.Nodes[i+1:]...)
				found = true
				break
			}
		}
		if !found {
			return nil
		}

		_, writeErr := shared.ClusterStateRecordWrite(client, &record, snapshot.Revision)
		if writeErr != nil {
			if shared.IsConflict(writeErr) && attempt < 8 {
				time.Sleep(2 * time.Second)
				continue
			}
			return fmt.Errorf("could not update the cluster record: %s", writeErr)
		}

		return nil
	}
}

func removeTombstoneOperation(worker *nodeWorker) {
	client, err := stackClientNew()
	if err != nil {
		log.Warn("k8s-app maintenance %s: could not clear the maintenance record: %s", worker.nodeName, err)
		return
	}

	record, readErr := stackRead(client, stackNodeKey(worker.nodeName))
	if readErr != nil {
		log.Warn("k8s-app maintenance %s: could not clear the maintenance record: %s", worker.nodeName, readErr)
		return
	}
	if !record.Found || record.Operation.NodeName == "" || record.Operation.Phase != PhaseCompleted {
		return
	}

	_, writeErr := stackWrite(client, stackNodeKey(worker.nodeName), json.RawMessage("null"), record.Revision)
	if writeErr != nil {
		log.Warn("k8s-app maintenance %s: could not clear the maintenance record: %s", worker.nodeName, writeErr)
	}
}
