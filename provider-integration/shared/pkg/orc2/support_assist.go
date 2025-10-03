package orchestrators

import (
	apm "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
)

type SupportAssistProjectInfoFlags struct {
	IncludeMembers        bool
	IncludeAccountingInfo bool
	IncludeJobsInfo       bool
}

type SupportAssistRetrieveProjectInfoRequest struct {
	projectId string
	flags     SupportAssistProjectInfoFlags
}

type WalletIssue struct {
	associatedAllocation apm.Allocation
	problematicWallet    apm.WalletV2
	description          string
}

type SupportAssistRetrieveProjectInfoResponse struct {
	projectMembers   []foundation.ProjectMember
	projectWallet    apm.WalletV2
	accountingIssues []WalletIssue
	jobs             []orchestrators.Job
}

type SupportAssistRetrieveJobsInfoRequest struct {
	jobId string
}

type SupportAssistRetrieveJobsInfoResponse struct {
	jobInfo orchestrators.Job
}
type SupportAssistWalletInfoFlags struct {
	IncludeAccountingGraph bool
}
type SupportAssistRetrieveWalletInfoRequest struct {
	allocationId string
	walletId     string
	flags        SupportAssistWalletInfoFlags
}

type SupportAssistRetrieveWalletInfoResponse struct {
	wallet          apm.WalletV2
	accountingGraph string
}

const SupportAssistOrcContext = "support-assist-orc"

var SupportAssistRetrieveProjectInfo = rpc.Call[SupportAssistRetrieveProjectInfoRequest, SupportAssistRetrieveProjectInfoResponse]{
	BaseContext: SupportAssistOrcContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesAdmin,
}

var SupportAssistRetrieveJobsInfo = rpc.Call[SupportAssistRetrieveJobsInfoRequest, SupportAssistRetrieveJobsInfoResponse]{
	BaseContext: SupportAssistOrcContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesAdmin,
}

var SupportAssistRetrieveWalletsInfo = rpc.Call[SupportAssistRetrieveWalletInfoRequest, SupportAssistRetrieveWalletInfoResponse]{
	BaseContext: SupportAssistOrcContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesAdmin,
}
