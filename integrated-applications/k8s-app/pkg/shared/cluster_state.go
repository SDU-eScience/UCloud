package shared

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"

	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/util"
)

const ClusterStateRecordKey = "cluster/record"

var ClusterStateErrConflict = RpcGrantErrConflict

func IsConflict(err error) bool {
	if err == nil {
		return false
	}
	if errors.Is(err, ClusterStateErrConflict) || errors.Is(err, RpcGrantErrConflict) {
		return true
	}

	var callErr ucx.RpcCallError
	return errors.As(err, &callErr) && callErr.Status == ucx.RpcStatusConflict
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

func ClusterStateClientNewSession(session *ucx.Session, stackId string) ClusterStateClient {
	if session == nil {
		log.Fatal("k8s-app: the cluster state session is nil")
	}
	if stackId == "" {
		log.Fatal("k8s-app: the cluster state stack id is empty")
	}

	return ClusterStateClient{session: session, stackId: stackId}
}

func ClusterStateClientNewHost() (ClusterStateClient, error) {
	client, err := RpcGrantClientNew()
	if err != nil {
		return ClusterStateClient{}, err
	}
	return ClusterStateClient{client: client}, nil
}

func ClusterStateRead(client ClusterStateClient) (ClusterStateSnapshot, error) {
	return ClusterStateReadEx(context.Background(), client)
}

func ClusterStateReadEx(ctx context.Context, client ClusterStateClient) (ClusterStateSnapshot, error) {
	record, found, err := clusterStateReadRawEx(ctx, client, ClusterStateRecordKey)
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
	if parsed.SchemaRevision != clusterRecordSchemaRevision {
		return ClusterStateSnapshot{}, errors.New("the cluster record was written by an incompatible version of the application")
	}

	parsed.stateRevision = record.Revision
	result.Found = true
	result.Record = parsed
	return result, nil
}

func clusterRecordReplaceAt(client ClusterStateClient, record *ClusterRecord, expectedRevision int64) (int64, error) {
	value := util.MustJsonMarshal(*record)

	newRevision, err := clusterStateWrite(client, ClusterStateRecordKey, value, expectedRevision)
	if err != nil {
		return 0, err
	}

	record.stateRevision = newRevision
	return newRevision, nil
}

type ClusterStateValue struct {
	Value    json.RawMessage
	Revision int64
	Found    bool
}

func ClusterStateValueReadEx(ctx context.Context, client ClusterStateClient, key string) (ClusterStateValue, error) {
	record, found, err := clusterStateReadRawEx(ctx, client, key)
	if err != nil {
		return ClusterStateValue{}, err
	}
	if !found {
		return ClusterStateValue{}, nil
	}

	return ClusterStateValue{
		Value:    record.Value,
		Revision: record.Revision,
		Found:    !record.IsEmpty(),
	}, nil
}

func ClusterStateValueWrite(client ClusterStateClient, key string, value json.RawMessage, expectedRevision int64) (int64, error) {
	return clusterStateWrite(client, key, value, expectedRevision)
}

func clusterStateReadRaw(client ClusterStateClient, key string) (ucxapi.StackStateRecord, bool, error) {
	return clusterStateReadRawEx(context.Background(), client, key)
}

func clusterStateReadRawEx(ctx context.Context, client ClusterStateClient, key string) (ucxapi.StackStateRecord, bool, error) {
	if client.session != nil {
		response, err := ucxapi.StackStateRead.InvokeEx(ctx, client.session, ucxapi.StackStateReadRequest{
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
	if expectedRevision < 0 {
		log.Fatal("k8s-app: the expected revision is negative")
	}

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

	return snapshot.Record, true, nil
}
