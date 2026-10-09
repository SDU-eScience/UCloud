package shared

import (
	"encoding/json"
	"errors"
	"time"
)

const ClusterRecoveryStateKey = "recovery/state"

const clusterRecoverySchemaRevision = 1

const (
	ClusterRecoveryPhaseActive = "active"
	ClusterRecoveryPhaseClosed = "closed"
)

const (
	ClusterRecoveryStageStop     = "stop"
	ClusterRecoveryStageRecreate = "recreate"
	ClusterRecoveryStageRestore  = "restore"
	ClusterRecoveryStageVerify   = "verify"
)

type ClusterRecoveryRecord struct {
	SchemaRevision int       `json:"schemaRevision"`
	Phase          string    `json:"phase"`
	Stage          string    `json:"stage,omitempty"`
	OperationUid   string    `json:"operationUid"`
	BackupId       string    `json:"backupId,omitempty"`
	BackupRelease  string    `json:"backupRelease,omitempty"`
	SnapshotSha256 string    `json:"snapshotSha256,omitempty"`
	StartedAt      time.Time `json:"startedAt,omitempty"`
	StartedBy      string    `json:"startedBy,omitempty"`
	ClosedAt       time.Time `json:"closedAt,omitempty"`
	LastError      string    `json:"lastError,omitempty"`
}

type ClusterRecoverySnapshot struct {
	Found    bool
	Record   ClusterRecoveryRecord
	Revision int64
}

func ClusterRecoveryRead(client ClusterStateClient) (ClusterRecoverySnapshot, error) {
	record, found, err := clusterStateReadRaw(client, ClusterRecoveryStateKey)
	if err != nil {
		return ClusterRecoverySnapshot{}, err
	}

	result := ClusterRecoverySnapshot{Revision: record.Revision}

	if !found || record.IsEmpty() {
		return result, nil
	}

	var parsed ClusterRecoveryRecord
	if err := json.Unmarshal(record.Value, &parsed); err != nil {
		return ClusterRecoverySnapshot{}, errors.New("could not parse the cluster recovery record")
	}

	result.Found = true
	result.Record = parsed
	return result, nil
}

func ClusterRecoveryRecordWrite(client ClusterStateClient, record *ClusterRecoveryRecord, expectedRevision int64) (int64, error) {
	if record == nil {
		return 0, errors.New("the cluster recovery record is nil")
	}
	if expectedRevision < 0 {
		return 0, errors.New("the expected cluster recovery record revision is negative")
	}
	if record.Phase != ClusterRecoveryPhaseActive && record.Phase != ClusterRecoveryPhaseClosed {
		return 0, errors.New("the cluster recovery record has an unknown phase")
	}
	if record.OperationUid == "" {
		return 0, errors.New("the cluster recovery record has no operation uid")
	}

	record.SchemaRevision = clusterRecoverySchemaRevision

	value, err := json.Marshal(record)
	if err != nil {
		return 0, err
	}

	return clusterStateWrite(client, ClusterRecoveryStateKey, value, expectedRevision)
}

func ClusterRecoveryActive(client ClusterStateClient) (bool, error) {
	snapshot, err := ClusterRecoveryRead(client)
	if err != nil {
		return false, err
	}

	return snapshot.Found && snapshot.Record.Phase == ClusterRecoveryPhaseActive, nil
}
