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
	Username                 string                   `json:"username"`
	FirstNames               string                   `json:"firstNames"`
	LastName                 string                   `json:"lastName"`
	Email                    string                   `json:"email"`
	EmailSettings            foundation.EmailSettings `json:"emailSettings"`
	AssociatedProjects       []foundation.Project     `json:"associatedProjects"`
	ActiveGrants             []GrantApplication       `json:"activeGrants"`
	PersonalProjectResources []WalletV2               `json:"personalProjectResources"`
}

type SupportAssistRetrieveUserInfoResponse struct {
	Info []SupportAssistUserInfo `json:"info"`
}

const SupportAssistAccContext = "support-assist-acc"

var SupportAssistRetrieveUserInfo = rpc.Call[SupportAssistRetrieveUserInfoRequest, SupportAssistRetrieveUserInfoResponse]{
	BaseContext: SupportAssistAccContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesAdmin,
	Operation:   "user_info",
}
