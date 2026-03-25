package orchestrators

import (
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type Stack struct {
	Id          string                   `json:"id"`
	Type        string                   `json:"name"`
	CreatedAt   fnd.Timestamp            `json:"createdAt"`
	Permissions ResourcePermissions      `json:"permissions"`
	Status      util.Option[StackStatus] `json:"status"`
}

type UcxUiMode string

const (
	UcxUiReplacement UcxUiMode = "Replacement"
	UcxUiPartial     UcxUiMode = "Partial"
	UcxUiNone        UcxUiMode = "None"
)

type StackStatus struct {
	UcxUiMode   UcxUiMode        `json:"ucxUiMode"`
	Jobs        []Job            `json:"jobs"`
	Licenses    []License        `json:"licenses"`
	PublicIps   []PublicIp       `json:"publicIps"`
	PublicLinks []Ingress        `json:"publicLinks"`
	Networks    []PrivateNetwork `json:"networks"`
}

const stacksContext = "jobs/stacks"

type StacksBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`
}

var StacksBrowse = rpc.Call[StacksBrowseRequest, fnd.PageV2[Stack]]{
	BaseContext: stacksContext,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

var StacksRetrieve = rpc.Call[fnd.FindByStringId, Stack]{
	BaseContext: stacksContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

var StacksDelete = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], util.Empty]{
	BaseContext: stacksContext,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesEndUser,
}

var StacksUpdateAcl = rpc.Call[fnd.BulkRequest[UpdatedAcl], util.Empty]{
	BaseContext: stacksContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateAcl",
}

type StacksControlRequestDeletionRequest struct {
	Id             string                     `json:"id"`
	ActivationTime util.Option[fnd.Timestamp] `json:"activationTime"`
	Owner          ResourceOwner              `json:"owner"`
}

var StacksControlRequestDeletion = rpc.Call[StacksControlRequestDeletionRequest, fnd.FindByIntId]{
	BaseContext: stacksContext + "/control",
	Convention:  rpc.ConventionUpdate,
	Operation:   "requestDeletion",
	Roles:       rpc.RolesProvider,
}

var StacksControlCancelDeletion = rpc.Call[fnd.FindByIntId, util.Empty]{
	BaseContext: stacksContext + "/control",
	Convention:  rpc.ConventionUpdate,
	Operation:   "cancelDeletion",
	Roles:       rpc.RolesProvider,
}
