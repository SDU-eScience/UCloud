package foundation

import (
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type CreateTicketRequest struct {
	Subject string `json:"subject"`
	Message string `json:"message"`
}

var SupportCreateTicket = rpc.Call[CreateTicketRequest, util.Empty]{
	BaseContext: MailContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "ticket",
}
