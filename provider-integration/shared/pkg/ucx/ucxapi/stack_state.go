package ucxapi

import (
	"encoding/json"

	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/util"
)

type StackStateRecord struct {
	Key      string          `json:"key"`
	Value    json.RawMessage `json:"value"`
	Revision int64           `json:"revision"`
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

func stackControlStateCall[Req StackControlRequest, Resp any](operation string) rpc.Call[Req, Resp] {
	return rpc.Call[Req, Resp]{
		BaseContext: StackControlBaseContext,
		Convention:  rpc.ConventionUpdate,
		Roles:       rpc.RolesPublic,
		Operation:   operation,
		Audit: rpc.AuditRules{
			Transformer: stackControlAuditTransformer,
		},
	}
}

type StackControlStateReadRequest struct {
	StackCredentialAuth
	Key string `json:"key"`
}

var StackControlStateRead = stackControlStateCall[StackControlStateReadRequest, StackStateReadResponse]("stateRead")

type StackControlStateListRequest struct {
	StackCredentialAuth
	Prefix       string              `json:"prefix"`
	Next         util.Option[string] `json:"next"`
	ItemsPerPage int                 `json:"itemsPerPage"`
}

var StackControlStateList = stackControlStateCall[StackControlStateListRequest, fnd.PageV2[StackStateRecord]]("stateList")

type StackControlStateWriteRequest struct {
	StackCredentialAuth
	Key              string          `json:"key"`
	Value            json.RawMessage `json:"value"`
	ExpectedRevision int64           `json:"expectedRevision"`
}

var StackControlStateWrite = stackControlStateCall[StackControlStateWriteRequest, StackStateWriteResponse]("stateWrite")
