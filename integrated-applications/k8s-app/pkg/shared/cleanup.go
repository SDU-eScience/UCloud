package shared

import (
	"context"
	"errors"
	"net/http"
	"time"

	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"
)

const clusterCleanupInterval = 10 * time.Second

func ClusterCleanupRun(ctx context.Context) {
	ticker := time.NewTicker(clusterCleanupInterval)
	defer ticker.Stop()

	lastError := ""
	for {
		err := ClusterCleanupSweep()
		message := ""
		if err != nil {
			message = err.Error()
		}
		if message != lastError {
			if message != "" {
				log.Warn("k8s-app: cluster cleanup failed: %s", message)
			}
			lastError = message
		}

		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
		}
	}
}

func ClusterCleanupSweep() error {
	grantClient, err := RpcGrantClientNew()
	if err != nil {
		return err
	}

	return clusterCleanupSweep(
		ClusterStateClient{client: grantClient},
		clusterCleanupOpsHost(grantClient),
	)
}

func ClusterCleanupSweepSession(session *ucx.Session, stack *ucxsvc.Stack, stackId string) error {
	if session == nil || stack == nil || !stack.Ok || stackId == "" {
		return nil
	}

	stateClient, err := ClusterStateClientNewSession(session, stackId)
	if err != nil {
		return err
	}

	return clusterCleanupSweep(stateClient, clusterCleanupOpsSession(stack))
}

func clusterCleanupSweep(client ClusterStateClient, ops clusterCleanupOps) error {
	snapshot, err := ClusterStateRead(client)
	if err != nil {
		return err
	}
	if !snapshot.Found {
		return nil
	}

	if len(snapshot.Record.PendingCleanup) == 0 && snapshot.Record.Phase != ClusterRecordPhaseCleanup {
		return nil
	}

	recoveryActive, recoveryErr := ClusterRecoveryActive(client)
	if recoveryErr != nil {
		return recoveryErr
	}
	if recoveryActive {
		return nil
	}

	if len(snapshot.Record.PendingCleanup) == 0 {
		return ClusterRecordUpdate(client, func(record *ClusterRecord) bool {
			if record.Phase != ClusterRecordPhaseCleanup {
				return false
			}
			record.Phase = ClusterRecordPhaseCreated
			record.FailureReason = ""
			return true
		})
	}

	cleaned := map[ClusterPendingCleanup]bool{}
	for _, pending := range snapshot.Record.PendingCleanup {
		if clusterPendingCleanupReservationReused(snapshot.Record.Nodes, pending) {
			cleaned[pending] = true
			continue
		}

		done, cleanupErr := clusterCleanupPending(ops, pending)
		if cleanupErr != nil {
			log.Warn("k8s-app: could not clean up node %s: %s", pending.Hostname, cleanupErr)
			continue
		}
		if done {
			cleaned[pending] = true
		}
	}

	return ClusterRecordUpdate(client, func(record *ClusterRecord) bool {
		changed := false
		kept := make([]ClusterPendingCleanup, 0, len(record.PendingCleanup))
		for _, pending := range record.PendingCleanup {
			if cleaned[pending] {
				changed = true
				record.Nodes = clusterRemoveNodeForPending(record.Nodes, pending)
				continue
			}
			kept = append(kept, pending)
		}
		if changed {
			record.PendingCleanup = kept
		}

		if len(kept) > 0 && record.Phase == ClusterRecordPhaseCreated {
			record.Phase = ClusterRecordPhaseCleanup
			changed = true
		}
		if len(kept) == 0 && record.Phase == ClusterRecordPhaseCleanup {
			record.Phase = ClusterRecordPhaseCreated
			record.FailureReason = ""
			changed = true
		}

		return changed
	})
}

func clusterCleanupPending(ops clusterCleanupOps, pending ClusterPendingCleanup) (bool, error) {
	if pending.JobId != "" {
		job, found, err := ops.retrieveJob(pending.JobId)
		if err != nil {
			if clusterCleanupStatus(err) != http.StatusNotFound {
				return false, err
			}
		} else if found && !job.Status.State.IsFinal() {
			terminateErr := ops.terminateJob(pending.JobId)
			if terminateErr == nil {
				return false, nil
			}
			if clusterCleanupStatus(terminateErr) != http.StatusNotFound {
				return false, nil
			}
		}
	}

	if pending.ReservationId != "" {
		err := ops.deleteReservation(pending.ReservationId)
		if err != nil {
			status := clusterCleanupStatus(err)
			if status == http.StatusBadRequest {
				return false, nil
			}
			if status != http.StatusNotFound {
				return false, err
			}
		}
	}

	return true, nil
}

func clusterCleanupStatus(err error) int {
	var httpErr *util.HttpError
	if errors.As(err, &httpErr) {
		return httpErr.StatusCode
	}

	var callErr ucx.RpcCallError
	if errors.As(err, &callErr) {
		return ucx.HttpStatusFromRpcStatus(callErr.Status)
	}

	return 0
}

type clusterCleanupOps struct {
	retrieveJob       func(jobId string) (orcapi.Job, bool, error)
	terminateJob      func(jobId string) error
	deleteReservation func(reservationId string) error
}

func clusterCleanupOpsHost(client RpcGrantClient) clusterCleanupOps {
	return clusterCleanupOps{
		retrieveJob: func(jobId string) (orcapi.Job, bool, error) {
			job, found, err := RpcGrantRetrieveJob(client, jobId)
			if err != nil {
				return orcapi.Job{}, false, err
			}
			return job, found, nil
		},
		terminateJob: func(jobId string) error {
			if err := RpcGrantTerminateJob(client, jobId); err != nil {
				return err
			}
			return nil
		},
		deleteReservation: func(reservationId string) error {
			if err := RpcGrantDeletePrivateNetworkIp(client, reservationId); err != nil {
				return err
			}
			return nil
		},
	}
}

func clusterCleanupOpsSession(stack *ucxsvc.Stack) clusterCleanupOps {
	return clusterCleanupOps{
		retrieveJob: func(jobId string) (orcapi.Job, bool, error) {
			job, err := ucxsvc.JobRetrieve(stack, jobId)
			if err != nil {
				return orcapi.Job{}, false, err
			}
			return job, true, nil
		},
		terminateJob: func(jobId string) error {
			return ucxsvc.JobTerminate(stack, jobId)
		},
		deleteReservation: func(reservationId string) error {
			return ucxsvc.PrivateNetworkIpDelete(stack, reservationId)
		},
	}
}

func clusterRemoveNodeForPending(nodes []ClusterNodeRecord, pending ClusterPendingCleanup) []ClusterNodeRecord {
	if pending.JobId != "" {
		for i := range nodes {
			if nodes[i].JobId == pending.JobId {
				return append(nodes[:i], nodes[i+1:]...)
			}
		}
		return nodes
	}

	if pending.Hostname == "" {
		return nodes
	}

	for i := range nodes {
		if nodes[i].Hostname == pending.Hostname && nodes[i].JobId == "" {
			return append(nodes[:i], nodes[i+1:]...)
		}
	}
	return nodes
}

func clusterPendingCleanupReservationReused(nodes []ClusterNodeRecord, pending ClusterPendingCleanup) bool {
	if pending.ReservationId == "" {
		return false
	}

	for _, node := range nodes {
		if node.ReservationId == pending.ReservationId && node.JobId != "" && node.JobId != pending.JobId {
			return true
		}
	}
	return false
}

func clusterPendingCleanupSame(a ClusterPendingCleanup, b ClusterPendingCleanup) bool {
	return a.ReservationId == b.ReservationId && a.JobId == b.JobId && a.Hostname == b.Hostname
}
