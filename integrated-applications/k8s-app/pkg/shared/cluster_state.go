package shared

import (
	"encoding/json"
	"errors"
	"fmt"
	"strings"

	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
)

const ClusterStateRecordKey = "cluster/record"

var ClusterStateErrConflict = RpcGrantErrConflict

func IsConflict(err error) bool {
	return err != nil && (errors.Is(err, ClusterStateErrConflict) || errors.Is(err, RpcGrantErrConflict))
}

type ClusterStateClient struct {
	session *ucx.Session
	stackId string
	client  RpcGrantClient
}

type ClusterStateSnapshot struct {
	Found    bool
	Record   ClusterRecord
	Revision int64
}

func ClusterStateClientNewSession(session *ucx.Session, stackId string) (ClusterStateClient, error) {
	if session == nil {
		return ClusterStateClient{}, errors.New("the cluster state session is nil")
	}

	stackId = strings.TrimSpace(stackId)
	if stackId == "" {
		return ClusterStateClient{}, errors.New("the cluster state stack id is empty")
	}

	return ClusterStateClient{session: session, stackId: stackId}, nil
}

func ClusterStateClientNewHost() (ClusterStateClient, error) {
	client, err := RpcGrantClientNew()
	if err != nil {
		return ClusterStateClient{}, err
	}
	return ClusterStateClient{client: client}, nil
}

func ClusterStateRead(client ClusterStateClient) (ClusterStateSnapshot, error) {
	record, found, err := clusterStateReadRaw(client, ClusterStateRecordKey)
	if err != nil {
		return ClusterStateSnapshot{}, err
	}

	result := ClusterStateSnapshot{Revision: record.Revision}

	if !found || record.IsEmpty() {
		return result, nil
	}

	var parsed ClusterRecord
	if err := json.Unmarshal(record.Value, &parsed); err != nil {
		return ClusterStateSnapshot{}, fmt.Errorf("could not parse the cluster record: %s", err)
	}

	result.Found = true
	result.Record = parsed
	return result, nil
}

// ClusterStateRecordWrite replaces the cluster record. The write fails with a
// conflict if the record changed since it was read.
func ClusterStateRecordWrite(client ClusterStateClient, record *ClusterRecord, expectedRevision int64) (int64, error) {
	if record == nil {
		return 0, errors.New("the cluster record is nil")
	}
	if expectedRevision < 0 {
		return 0, errors.New("the expected cluster record revision is negative")
	}

	value, err := json.Marshal(record)
	if err != nil {
		return 0, err
	}

	return clusterStateWrite(client, ClusterStateRecordKey, value, expectedRevision)
}

func clusterStateReadRaw(client ClusterStateClient, key string) (ucxapi.StackStateRecord, bool, error) {
	if client.session != nil {
		response, err := ucxapi.StackStateRead.Invoke(client.session, ucxapi.StackStateReadRequest{
			StackId: client.stackId,
			Key:     key,
		})
		if err != nil {
			return ucxapi.StackStateRecord{}, false, err
		}
		if !response.Found {
			return ucxapi.StackStateRecord{}, false, nil
		}
		return response.Record, true, nil
	}

	record, found, err := RpcGrantStateRead(client.client, key)
	if err != nil {
		return ucxapi.StackStateRecord{}, false, err
	}
	return record, found, nil
}

func clusterStateWrite(client ClusterStateClient, key string, value json.RawMessage, expectedRevision int64) (int64, error) {
	if client.session != nil {
		response, err := ucxapi.StackStateWrite.Invoke(client.session, ucxapi.StackStateWriteRequest{
			StackId:          client.stackId,
			Key:              key,
			Value:            value,
			ExpectedRevision: expectedRevision,
		})
		if err != nil {
			return 0, err
		}
		return response.Revision, nil
	}

	return RpcGrantStateWrite(client.client, key, value, expectedRevision)
}

func ClusterStateRecordRevision(record *ClusterRecord) (int64, error) {
	if record == nil {
		return 0, errors.New("the cluster record is nil")
	}
	if record.stateRevision < 0 {
		return 0, errors.New("the cluster record revision is negative")
	}
	return record.stateRevision, nil
}

func ClusterStateRecordRevisionSet(record *ClusterRecord, revision int64) {
	if record != nil && revision >= 0 {
		record.stateRevision = revision
	}
}

func ClusterStateRecordRead() (ClusterRecord, bool, error) {
	client, err := ClusterStateClientNewHost()
	if err != nil {
		return ClusterRecord{}, false, err
	}

	snapshot, err := ClusterStateRead(client)
	if err != nil {
		return ClusterRecord{}, false, err
	}
	if !snapshot.Found {
		return ClusterRecord{}, false, nil
	}

	record := snapshot.Record
	record.stateRevision = snapshot.Revision
	return record, true, nil
}
