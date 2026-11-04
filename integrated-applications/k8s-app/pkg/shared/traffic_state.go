package shared

import (
	"encoding/json"
	"errors"

	"ucloud.dk/shared/pkg/util"
)

const ClusterTrafficRecordKey = "traffic/state"

const clusterTrafficSchemaRevision = 2

type ClusterTrafficRecord struct {
	SchemaRevision int                     `json:"schemaRevision"`
	SuspendedJobs  []ClusterTrafficSuspend `json:"suspendedJobs,omitempty"`
}

type ClusterTrafficSuspend struct {
	JobId        string `json:"jobId"`
	NodeName     string `json:"nodeName"`
	OperationUid string `json:"operationUid"`
}

func ClusterTrafficSuspendFind(record ClusterTrafficRecord, jobId string) (ClusterTrafficSuspend, bool) {
	for _, suspended := range record.SuspendedJobs {
		if suspended.JobId == jobId {
			return suspended, true
		}
	}
	return ClusterTrafficSuspend{}, false
}

func ClusterTrafficSuspendUpsert(record *ClusterTrafficRecord, suspend ClusterTrafficSuspend) bool {
	if record == nil || suspend.JobId == "" {
		return false
	}

	for i := range record.SuspendedJobs {
		if record.SuspendedJobs[i].JobId == suspend.JobId {
			record.SuspendedJobs[i] = suspend
			return true
		}
	}

	record.SuspendedJobs = append(record.SuspendedJobs, suspend)
	return true
}

func ClusterTrafficSuspendRemove(record *ClusterTrafficRecord, jobId string) bool {
	if record == nil {
		return false
	}

	kept := record.SuspendedJobs[:0]
	removed := false
	for _, suspended := range record.SuspendedJobs {
		if suspended.JobId == jobId {
			removed = true
			continue
		}
		kept = append(kept, suspended)
	}
	record.SuspendedJobs = kept
	return removed
}

func ClusterTrafficSuspendedJobIds(record ClusterTrafficRecord) []string {
	result := make([]string, 0, len(record.SuspendedJobs))
	for _, suspended := range record.SuspendedJobs {
		if suspended.JobId != "" {
			result = append(result, suspended.JobId)
		}
	}
	return result
}

func ClusterTrafficRead(client ClusterStateClient) (ClusterTrafficSnapshot, error) {
	record, found, err := clusterStateReadRaw(client, ClusterTrafficRecordKey)
	if err != nil {
		return ClusterTrafficSnapshot{}, err
	}

	result := ClusterTrafficSnapshot{Revision: record.Revision}

	if !found || record.IsEmpty() {
		return result, nil
	}

	var parsed ClusterTrafficRecord
	if err := json.Unmarshal(record.Value, &parsed); err != nil {
		return ClusterTrafficSnapshot{}, errors.New("could not parse the cluster traffic record")
	}
	if parsed.SchemaRevision != clusterTrafficSchemaRevision {
		return ClusterTrafficSnapshot{}, errors.New("the cluster traffic record was written by an incompatible version of the application")
	}

	result.Found = true
	result.Record = parsed
	return result, nil
}

type ClusterTrafficSnapshot struct {
	Found    bool
	Record   ClusterTrafficRecord
	Revision int64
}

func ClusterTrafficRecordWrite(client ClusterStateClient, record *ClusterTrafficRecord, expectedRevision int64) (int64, error) {
	record.SchemaRevision = clusterTrafficSchemaRevision

	value := util.MustJsonMarshal(record)

	return clusterStateWrite(client, ClusterTrafficRecordKey, value, expectedRevision)
}
