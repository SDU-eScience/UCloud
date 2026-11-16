package maintenance

import (
	"context"
	"errors"
	"fmt"
	"slices"
	"time"

	"ucloud.dk/iapp/k8s/pkg/backup"
	"ucloud.dk/iapp/k8s/pkg/shared"
	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	"ucloud.dk/shared/pkg/log"
)

const backupClaimLease = 30 * time.Minute

const backupClaimRenewInterval = 5 * time.Minute

func backupStateRead(client shared.RpcGrantClient) (stackValue[backup.State], error) {
	return stackValueRead[backup.State](client, backup.StateKey, "backup state")
}

func backupRequestRead(client shared.RpcGrantClient) (stackValue[backup.Request], error) {
	return stackValueRead[backup.Request](client, backup.RequestKey, "backup request")
}

func backupStateWrite(client shared.RpcGrantClient, state backup.State, expectedRevision int64) (int64, error) {
	return stackValueWrite(client, backup.StateKey, state, expectedRevision)
}

func backupRequestWrite(client shared.RpcGrantClient, request backup.Request, expectedRevision int64) (int64, error) {
	return stackValueWrite(client, backup.RequestKey, request, expectedRevision)
}

func backupSweep(ctx context.Context, client shared.RpcGrantClient) error {
	record, found, err := readClusterRecord()
	if err != nil {
		return err
	}
	if !found || !shared.ClusterRecordPhaseOperational(record.Phase) {
		return nil
	}

	stateRecord, err := backupStateRead(client)
	if err != nil {
		return err
	}

	requestRecord, err := backupRequestRead(client)
	if err != nil {
		return err
	}

	now := time.Now().UTC()

	if !stateRecord.Found {
		state := backup.State{
			SchemaRevision: backup.SchemaRevision,
			DueAt:          backup.NextHour(now),
		}
		_, writeErr := backupStateWrite(client, state, 0)
		return writeErr
	}

	state := stateRecord.Value
	state.SchemaRevision = backup.SchemaRevision

	if state.ClaimUid != "" && now.Before(state.ClaimDeadline) {
		return nil
	}

	if state.ClaimUid != "" {
		state.LastAttempt = backup.Attempt{
			Trigger:    state.ClaimTrigger,
			Phase:      k8sapi.BackupAttemptFailed,
			StartedAt:  state.ClaimDeadline.Add(-backupClaimLease),
			FinishedAt: now,
			Error:      "the backup coordinator stopped before the attempt completed",
		}
		state.ClaimUid = ""
		state.ClaimTrigger = ""
		state.ClaimRequestUid = ""
		state.ClaimDeadline = time.Time{}

		revision, writeErr := backupStateWrite(client, state, stateRecord.Revision)
		if writeErr != nil {
			return writeErr
		}
		stateRecord.Revision = revision
	}

	request := requestRecord.Value
	manual := requestRecord.Found && request.Uid != "" &&
		request.Phase != k8sapi.BackupRequestCompleted && request.Phase != k8sapi.BackupRequestFailed

	due := !now.Before(state.DueAt)
	if !manual && !due {
		return nil
	}

	trigger := backup.TriggerScheduled
	if manual {
		trigger = backup.TriggerManual
	}

	attemptUid := shared.NewUid()

	state.ClaimUid = attemptUid
	state.ClaimTrigger = trigger
	state.ClaimRequestUid = ""
	state.ClaimDeadline = now.Add(backupClaimLease)
	if manual {
		state.ClaimRequestUid = request.Uid
	} else {
		state.DueAt = backup.NextHour(now)
	}

	revision, err := backupStateWrite(client, state, stateRecord.Revision)
	if err != nil {
		return err
	}

	requestRevision := requestRecord.Revision
	if manual {
		request.Phase = k8sapi.BackupRequestRunning
		request.AttemptUid = attemptUid
		request.Error = ""

		updatedRequestRevision, reqErr := backupRequestWrite(client, request, requestRevision)
		if reqErr != nil {
			log.Warn("k8s-app backup: could not mark the manual request as running: %s", reqErr)
			state.ClaimUid = ""
			state.ClaimTrigger = ""
			state.ClaimRequestUid = ""
			state.ClaimDeadline = time.Time{}
			_, releaseErr := backupStateWrite(client, state, revision)
			return releaseErr
		}

		requestRevision = updatedRequestRevision
	}

	startedAt := time.Now().UTC()

	node, nodeErr := backupSelectNode(ctx, record)
	if nodeErr != nil {
		attempt := backup.Attempt{
			Trigger:    trigger,
			Phase:      k8sapi.BackupAttemptFailed,
			StartedAt:  startedAt,
			FinishedAt: time.Now().UTC(),
			Error:      nodeErr.Error(),
		}
		return backupComplete(client, attemptUid, attempt, nil, nil, request, requestRevision, manual)
	}

	stopRenew := backupClaimRenew(client, attemptUid)
	result, backupErr := shared.NodeAgentClientBackup(ctx, node.Hostname, node.IpAddress, trigger, attemptUid)
	stopRenew()

	attempt := backup.Attempt{
		Trigger:    trigger,
		Phase:      k8sapi.BackupAttemptFailed,
		NodeName:   node.Hostname,
		StartedAt:  startedAt,
		FinishedAt: time.Now().UTC(),
	}
	var retained []backup.BackupInfo
	var pruneWarnings []string
	if backupErr != nil {
		attempt.Error = backupErr.Error()
	} else {
		attempt.Phase = k8sapi.BackupAttemptSuccess
		attempt.Id = result.Metadata.BackupId
		attempt.SizeBytes = result.Metadata.Snapshot.SizeBytes
		attempt.Sha256 = result.Metadata.Snapshot.Sha256
		retained = result.Retained
		pruneWarnings = result.PruneWarnings
	}

	return backupComplete(client, attemptUid, attempt, retained, pruneWarnings, request, requestRevision, manual)
}

func backupComplete(
	client shared.RpcGrantClient,
	attemptUid string,
	attempt backup.Attempt,
	retained []backup.BackupInfo,
	pruneWarnings []string,
	request backup.Request,
	requestRevision int64,
	manual bool,
) error {
	record, err := backupStateRead(client)
	if err != nil {
		return err
	}
	if !record.Found || record.Value.ClaimUid != attemptUid {
		return errors.New("the backup claim was lost before the attempt finished")
	}

	state := record.Value
	state.SchemaRevision = backup.SchemaRevision
	state.LastAttempt = attempt
	state.ClaimUid = ""
	state.ClaimTrigger = ""
	state.ClaimRequestUid = ""
	state.ClaimDeadline = time.Time{}
	if attempt.Phase == k8sapi.BackupAttemptSuccess {
		state.LastSuccess = attempt
		state.Retained = retained
		state.PruneWarnings = pruneWarnings
	}

	if _, writeErr := backupStateWrite(client, state, record.Revision); writeErr != nil {
		return writeErr
	}

	if !manual {
		return nil
	}

	request.AttemptUid = attemptUid
	request.CompletedAt = attempt.FinishedAt
	if attempt.Phase == k8sapi.BackupAttemptSuccess {
		request.Phase = k8sapi.BackupRequestCompleted
		request.Error = ""
	} else {
		request.Phase = k8sapi.BackupRequestFailed
		request.Error = attempt.Error
	}

	_, err = backupRequestWrite(client, request, requestRevision)
	if err != nil {
		return err
	}

	return nil
}

func backupClaimRenew(client shared.RpcGrantClient, claimUid string) func() {
	stop := make(chan struct{})
	done := make(chan struct{})

	go func() {
		defer close(done)
		ticker := time.NewTicker(backupClaimRenewInterval)
		defer ticker.Stop()

		for {
			select {
			case <-stop:
				return
			case <-ticker.C:
				record, err := backupStateRead(client)
				if err != nil || !record.Found || record.Value.ClaimUid != claimUid {
					continue
				}

				record.Value.ClaimDeadline = time.Now().UTC().Add(backupClaimLease)
				_, writeErr := backupStateWrite(client, record.Value, record.Revision)
				if writeErr != nil && !shared.IsConflict(writeErr) {
					log.Warn("k8s-app backup: could not extend the backup claim: %s", writeErr)
				}
			}
		}
	}()

	return func() {
		close(stop)
		<-done
	}
}

func backupSelectNode(ctx context.Context, record shared.ClusterRecord) (shared.ClusterNodeRecord, error) {
	operations, opsErr := Snapshot()
	if opsErr != nil {
		return shared.ClusterNodeRecord{}, fmt.Errorf("could not read the maintenance state: %s", opsErr)
	}

	busy := map[string]bool{}
	for nodeName, operation := range operations {
		if PhaseActive(operation.Phase) || operationBlockedByRecovery(operation) {
			busy[nodeName] = true
		}
	}

	var preferred []shared.ClusterNodeRecord
	for _, node := range record.Nodes {
		if node.Group != k8sapi.GroupControlPlane {
			continue
		}
		if busy[node.Hostname] {
			continue
		}
		preferred = append(preferred, node)
	}

	node, ok := backupProbeNodes(ctx, preferred)
	if ok {
		return node, nil
	}

	return shared.ClusterNodeRecord{}, errors.New("No control-plane node is available to create a backup. Check the node status and ongoing maintenance, then try again.")
}

func backupProbeNodes(ctx context.Context, nodes []shared.ClusterNodeRecord) (shared.ClusterNodeRecord, bool) {
	for _, node := range nodes {
		status, err := shared.NodeAgentClientStatus(ctx, node.Hostname, node.IpAddress)
		if err != nil {
			continue
		}
		if slices.Contains(shared.NodeAgentPhases, status.Phase) || status.NeedsStateRecovery {
			continue
		}
		return node, true
	}

	return shared.ClusterNodeRecord{}, false
}
