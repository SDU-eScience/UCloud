package apm

import (
	"ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
)

type SupportAssistRetrieveUserInfoRequest struct {
	Username string `json:"username"`
	Email    string `json:"email"`
}

type SupportAssistUserInfo struct {
	Username                 string
	FirstNames               string
	LastName                 string
	Email                    string
	EmailSettings            foundation.EmailSettings
	AssociatedProjects       []foundation.Project
	ActiveGrants             []GrantApplication
	PersonalProjectResources WalletV2
}

type SupportAssistRetrieveUserInfoResponse struct {
	Info []SupportAssistUserInfo
}

const SupportAssistAccContext = "support-assist-acc"

var SupportAssistRetrieveUserInfo = rpc.Call[SupportAssistRetrieveUserInfoRequest, SupportAssistRetrieveUserInfoResponse]{
	BaseContext: SupportAssistAccContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesAdmin,
	Operation:   "user_info",
}
