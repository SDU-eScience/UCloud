package foundation

import (
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const SlaContext = "sla"

type ServiceAgreementText struct {
	Version int    `json:"version"`
	Text    string `json:"text"`
}

var SlaFind = rpc.Call[util.Empty, ServiceAgreementText]{
	BaseContext: SlaContext,
	Operation:   "",
	Convention:  rpc.ConventionQueryParameters,
	Roles:       rpc.RolesEndUser,
}

type SlaAcceptRequest struct {
	Version int `json:"version"`
}

var SlaAccept = rpc.Call[SlaAcceptRequest, util.Empty]{
	BaseContext: SlaContext,
	Operation:   "accept",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}
