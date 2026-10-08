package maintenance

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"ucloud.dk/iapp/k8s/pkg/backup"
	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/log"
)

const backupClaimLease = 30 * time.Minute

type backupStateRecord struct {
	Found    bool
	State    backup.State
	Revision int64
}

type backupRequestRecord struct {
	Found    bool
	Request  backup.Request
	Revision int64
}

func backupStateRead(client stackClient) (backupStateRecord, error) {
	record, found, err := shared.RpcGrantStateRead(client, backup.StateKey)
	if err != nil {
		return backupStateRecord{}, err
	}
	if !found {
		return backupStateRecord{}, nil
	}

	result := backupStateRecord{
		Found:    true,
		Revision: record.Revision,
	}

	if !record.IsEmpty() {
		err = json.Unmarshal(record.Value, &result.State)
		if err != nil {
			return backupStateRecord{}, fmt.Errorf("could not parse the backup state: %s", err)
		}
	}

	return result, nil
}

func backupRequestRead(client stackClient) (backupRequestRecord, error) {
	record, found, err := shared.RpcGrantStateRead(client, backup.RequestKey)
	if err != nil {
		return backupRequestRecord{}, err
	}
	if !found {
		return backupRequestRecord{}, nil
	}

	result := backupRequestRecord{
		Found:    true,
		Revision: record.Revision,
	}

	if !record.IsEmpty() {
		err = json.Unmarshal(record.Value, &result.Request)
		if err != nil {
			return backupRequestRecord{}, fmt.Errorf("could not parse the backup request: %s", err)
		}
	}

	return result, nil
}

func backupStateWrite(client stackClient, state backup.State, expectedRevision int64) (int64, error) {
	value, err := json.Marshal(state)
	if err != nil {
		return 0, err
	}

	return shared.RpcGrantStateWrite(client, backup.StateKey, value, expectedRevision)
}

func backupRequestWrite(client stackClient, request backup.Request, expectedRevision int64) (int64, error) {
	value, err := json.Marshal(request)
	if err != nil {
		return 0, err
	}

	return shared.RpcGrantStateWrite(client, backup.RequestKey, value, expectedRevision)
}

func backupRun(ctx context.Context) {
	ticker := time.NewTicker(runInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			client, err := stackClientNew()
			if err == nil {
				err = backupSweep(ctx, client)
			}
			if err != nil && !shared.IsConflict(err) && ctx.Err() == nil {
				log.Warn("k8s-app backup: %s", err)
			}
		}
	}
}

func backupSweep(ctx context.Context, client stackClient) error {
	record, err := readClusterRecord()
	if err != nil || record.Phase != "created" {
		return err
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

	state := stateRecord.State
	state.SchemaRevision = backup.SchemaRevision

	if state.ClaimUid != "" && now.Before(state.ClaimDeadline) {
		return nil
	}

	if state.ClaimUid != "" {
		state.LastAttempt = backup.Attempt{
			Trigger:    state.ClaimTrigger,
			Phase:      backup.AttemptFailed,
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

	request := requestRecord.Request
	manual := requestRecord.Found && request.Uid != "" &&
		request.Phase != backup.RequestCompleted && request.Phase != backup.RequestFailed

	due := !now.Before(state.DueAt)
	if !manual && !due {
		return nil
	}

	trigger := backup.TriggerScheduled
	if manual {
		trigger = backup.TriggerManual
	}

	attemptUid := newOperationUid()

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
		request.Phase = backup.RequestRunning
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
			Phase:      backup.AttemptFailed,
			StartedAt:  startedAt,
			FinishedAt: time.Now().UTC(),
			Error:      nodeErr.Error(),
		}
		return backupComplete(client, state, revision, request, requestRevision, manual, attemptUid, attempt, nil, nil)
	}

	result, backupErr := shared.NodeAgentClientBackup(ctx, node.Hostname, node.IpAddress, trigger, attemptUid)

	attempt := backup.Attempt{
		Trigger:    trigger,
		Phase:      backup.AttemptFailed,
		NodeName:   node.Hostname,
		StartedAt:  startedAt,
		FinishedAt: time.Now().UTC(),
	}
	var retained []backup.BackupInfo
	var pruneWarnings []string
	if backupErr != nil {
		attempt.Error = backupErr.Error()
	} else {
		attempt.Phase = backup.AttemptSuccess
		attempt.Id = result.Metadata.BackupId
		attempt.SizeBytes = result.Metadata.Snapshot.SizeBytes
		attempt.Sha256 = result.Metadata.Snapshot.Sha256
		retained = result.Retained
		pruneWarnings = result.PruneWarnings
	}

	return backupComplete(client, state, revision, request, requestRevision, manual, attemptUid, attempt, retained, pruneWarnings)
}

func backupComplete(
	client stackClient,
	state backup.State,
	stateRevision int64,
	request backup.Request,
	requestRevision int64,
	manual bool,
	attemptUid string,
	attempt backup.Attempt,
	retained []backup.BackupInfo,
	pruneWarnings []string,
) error {
	state.LastAttempt = attempt
	state.ClaimUid = ""
	state.ClaimTrigger = ""
	state.ClaimRequestUid = ""
	state.ClaimDeadline = time.Time{}
	if attempt.Phase == backup.AttemptSuccess {
		state.LastSuccess = attempt
		state.Retained = retained
		state.PruneWarnings = pruneWarnings
	}

	_, err := backupStateWrite(client, state, stateRevision)
	if err != nil {
		return err
	}

	if !manual {
		return nil
	}

	request.AttemptUid = attemptUid
	request.CompletedAt = attempt.FinishedAt
	if attempt.Phase == backup.AttemptSuccess {
		request.Phase = backup.RequestCompleted
		request.Error = ""
	} else {
		request.Phase = backup.RequestFailed
		request.Error = attempt.Error
	}

	_, err = backupRequestWrite(client, request, requestRevision)
	if err != nil {
		return err
	}

	return nil
}

func backupSelectNode(ctx context.Context, record shared.ClusterRecord) (shared.ClusterNodeRecord, error) {
	operations, opsErr := Snapshot()
	if opsErr != nil {
		return shared.ClusterNodeRecord{}, fmt.Errorf("could not read the maintenance state: %s", opsErr)
	}

	busy := map[string]bool{}
	for nodeName, operation := range operations {
		if PhaseActive(operation.Phase) || upgradeBlockedByRecovery(operation) || operation.HostTerminationUnverified {
			busy[nodeName] = true
		}
	}

	var preferred []shared.ClusterNodeRecord
	var fallback []shared.ClusterNodeRecord
	for _, node := range record.Nodes {
		if node.Group != shared.GroupControlPlane {
			continue
		}
		if busy[node.Hostname] {
			fallback = append(fallback, node)
		} else {
			preferred = append(preferred, node)
		}
	}

	node, ok := backupProbeNodes(ctx, preferred)
	if ok {
		return node, nil
	}

	node, ok = backupProbeNodes(ctx, fallback)
	if ok {
		return node, nil
	}

	return shared.ClusterNodeRecord{}, errors.New("no control-plane node is eligible for a backup")
}

func backupProbeNodes(ctx context.Context, nodes []shared.ClusterNodeRecord) (shared.ClusterNodeRecord, bool) {
	for _, node := range nodes {
		status, err := shared.NodeAgentClientStatus(ctx, node.Hostname, node.IpAddress)
		if err != nil {
			continue
		}
		if shared.NodeAgentPhaseActive(status.Phase) {
			continue
		}
		return node, true
	}

	return shared.ClusterNodeRecord{}, false
}
