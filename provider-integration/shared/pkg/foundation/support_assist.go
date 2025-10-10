package foundation

import (
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type ResetMFARequest struct {
	Username string
}

const SupportAssistFndContext = "support-assist-fnd"

var SupportAssistResetMFA = rpc.Call[ResetMFARequest, util.Empty]{
	BaseContext: SupportAssistFndContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesAdmin,
	Operation:   "reset_mfa",
}
