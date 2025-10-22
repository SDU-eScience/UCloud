package orchestrators

import (
	apm "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
)

type SupportAssistProjectInfoFlags struct {
	IncludeMembers        bool
	IncludeAccountingInfo bool
	IncludeJobsInfo       bool
}

type SupportAssistRetrieveProjectInfoRequest struct {
	ProjectId string
	Flags     SupportAssistProjectInfoFlags
}

type WalletIssue struct {
	AssociatedAllocation apm.Allocation
	ProblematicWallet    apm.WalletV2
	Description          string
}

type SupportAssistRetrieveProjectInfoResponse struct {
	Project          foundation.Project
	ProjectWallets   []apm.WalletV2
	AccountingIssues []WalletIssue
	Jobs             []Job
}

type SupportAssistRetrieveJobInfoRequest struct {
	JobId string
}

type SupportAssistRetrieveJobInfoResponse struct {
	JobInfo Job
}
type SupportAssistWalletInfoFlags struct {
	IncludeAccountingGraph bool
}
type SupportAssistRetrieveWalletInfoRequest struct {
	AllocationId string
	Flags        SupportAssistWalletInfoFlags
}

type SupportAssistRetrieveWalletInfoResponse struct {
	Wallet          apm.WalletV2
	AccountingGraph string
}

const SupportAssistOrcContext = "support-assist-orc"

var SupportAssistRetrieveProjectInfo = rpc.Call[SupportAssistRetrieveProjectInfoRequest, SupportAssistRetrieveProjectInfoResponse]{
	BaseContext: SupportAssistOrcContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesAdmin,
	Operation:   "project_info",
}

var SupportAssistRetrieveJobsInfo = rpc.Call[SupportAssistRetrieveJobInfoRequest, SupportAssistRetrieveJobInfoResponse]{
	BaseContext: SupportAssistOrcContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesAdmin,
	Operation:   "job_info",
}

var SupportAssistRetrieveWalletsInfo = rpc.Call[SupportAssistRetrieveWalletInfoRequest, SupportAssistRetrieveWalletInfoResponse]{
	BaseContext: SupportAssistOrcContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesAdmin,
	Operation:   "wallets_info",
}
