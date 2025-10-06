package apm

import (
	"ucloud.dk/shared/pkg/apm"
	"ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
)

type SupportAssistRetrieveUserInfoRequest struct {
	Username string
	Email    string
}

type SupportAssistUserInfo struct {
	Username                 string
	FirstNames               string
	LastName                 string
	Email                    string
	EmailSettings            foundation.EmailSettings
	AssociatedProjects       []apm.Project
	ActiveGrants             []GrantApplication
	PersonalProjectResources WalletV2
}

type SupportAssistRetrieveUserInfoResponse struct {
	Info []SupportAssistUserInfo
}

const SupportAssistAccContext = "support-assist-acc"

var SupportAssistRetrieveUserInfo = rpc.Call[SupportAssistRetrieveUserInfoRequest, SupportAssistRetrieveUserInfoResponse]{
	BaseContext: SupportAssistAccContext,
	Convention:  rpc.ConventionCustom,
	Roles:       rpc.RolesAdmin,
	Operation:   "retrieve_user_info",
}
