package ucxapi

import (
	"encoding/json"

	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/util"
)

type StackStateRecord struct {
	Key         string          `json:"key"`
	Value       json.RawMessage `json:"value"`
	Revision    int64           `json:"revision"`
}

func (record StackStateRecord) IsEmpty() bool {
	return record.Revision == 0 && string(record.Value) == "null"
}

type StackStateReadRequest struct {
	StackId string `json:"stackId"`
	Key     string `json:"key"`
}

type StackStateReadResponse struct {
	Found  bool             `json:"found"`
	Record StackStateRecord `json:"record"`
}

var StackStateRead = ucx.Rpc[StackStateReadRequest, StackStateReadResponse]{CallName: "stackStateRead"}

type StackStateListRequest struct {
	StackId      string              `json:"stackId"`
	Prefix       string              `json:"prefix"`
	Next         util.Option[string] `json:"next"`
	ItemsPerPage int                 `json:"itemsPerPage"`
}

var StackStateList = ucx.Rpc[StackStateListRequest, fnd.PageV2[StackStateRecord]]{CallName: "stackStateList"}

type StackStateWriteRequest struct {
	StackId          string          `json:"stackId"`
	Key              string          `json:"key"`
	Value            json.RawMessage `json:"value"`
	ExpectedRevision int64           `json:"expectedRevision"`
}

type StackStateWriteResponse struct {
	Revision int64 `json:"revision"`
}

var StackStateWrite = ucx.Rpc[StackStateWriteRequest, StackStateWriteResponse]{CallName: "stackStateWrite"}

type StackGrantStateReadRequest struct {
	StackGrantAuth
	Key string `json:"key"`
}

var StackGrantStateRead = rpc.Call[StackGrantStateReadRequest, StackStateReadResponse]{
	BaseContext: stackGrantBaseContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
	Operation:   "stateRead",
	Audit: rpc.AuditRules{
		Transformer: stackGrantAuditTransformer,
	},
}

type StackGrantStateListRequest struct {
	StackGrantAuth
	Prefix       string              `json:"prefix"`
	Next         util.Option[string] `json:"next"`
	ItemsPerPage int                 `json:"itemsPerPage"`
}

var StackGrantStateList = rpc.Call[StackGrantStateListRequest, fnd.PageV2[StackStateRecord]]{
	BaseContext: stackGrantBaseContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
	Operation:   "stateList",
	Audit: rpc.AuditRules{
		Transformer: stackGrantAuditTransformer,
	},
}

type StackGrantStateWriteRequest struct {
	StackGrantAuth
	Key              string          `json:"key"`
	Value            json.RawMessage `json:"value"`
	ExpectedRevision int64           `json:"expectedRevision"`
}

var StackGrantStateWrite = rpc.Call[StackGrantStateWriteRequest, StackStateWriteResponse]{
	BaseContext: stackGrantBaseContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
	Operation:   "stateWrite",
	Audit: rpc.AuditRules{
		Transformer: stackGrantAuditTransformer,
	},
}
