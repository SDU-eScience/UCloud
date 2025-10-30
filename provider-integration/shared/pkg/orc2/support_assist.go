package orchestrators

import (
	apm "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
)

type SupportAssistProjectInfoFlags struct {
	IncludeMembers        bool `json:"includeMembers"`
	IncludeAccountingInfo bool `json:"includeAccountingInfo"`
	IncludeJobsInfo       bool `json:"includeJobsInfo"`
}

type SupportAssistRetrieveProjectInfoRequest struct {
	ProjectId string `json:"projectId"`
}

type SupportAssistRetrieveProjectInfoResponse struct {
	Project          foundation.Project     `json:"project"`
	ProjectWallets   []apm.WalletV2         `json:"projectWallets"`
	ActiveGrants     []apm.GrantApplication `json:"activeGrants"`
	AccountingIssues []apm.WalletV2         `json:"accountingIssues"`
	Jobs             []Job                  `json:"jobs"`
}

type SupportAssistRetrieveJobInfoRequest struct {
	JobId string `json:"jobId"`
}

type SupportAssistRetrieveJobInfoResponse struct {
	JobInfo Job `json:"jobInfo"`
}
type SupportAssistWalletInfoFlags struct {
	IncludeAccountingGraph bool `json:"includeAccountingGraph"`
}
type SupportAssistRetrieveWalletInfoRequest struct {
	AllocationId string `json:"allocationId"`
}

type SupportAssistRetrieveWalletInfoResponse struct {
	Wallet          apm.WalletV2 `json:"wallet"`
	AccountingGraph string       `json:"accountingGraph"`
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
