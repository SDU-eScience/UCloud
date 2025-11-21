package apm

import (
	"encoding/json"

	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type WalletV2 struct {
	Owner            WalletOwner                 `json:"owner"`
	PaysFor          ProductCategory             `json:"paysFor"`
	AllocationGroups []AllocationGroupWithParent `json:"allocationGroups"`
	Children         []AllocationGroupWithChild  `json:"children"`

	TotalUsage     int64 `json:"totalUsage"`
	LocalUsage     int64 `json:"localUsage"`
	MaxUsable      int64 `json:"maxUsable"`
	Quota          int64 `json:"quota"`
	TotalAllocated int64 `json:"totalAllocated"`

	LastSignificantUpdateAt fnd.Timestamp `json:"lastSignificantUpdateAt"`
}

type AllocationGroup struct {
	Id          int          `json:"id"`
	Allocations []Allocation `json:"allocations"`
	Usage       int64        `json:"usage"`
	Quota       int64        `json:"quota"`
}

type Allocation struct {
	Id           int64              `json:"id,omitempty"`
	StartDate    fnd.Timestamp      `json:"startDate"`
	EndDate      fnd.Timestamp      `json:"endDate"`
	Quota        int64              `json:"quota"`
	GrantedIn    util.Option[int64] `json:"grantedIn"`
	RetiredQuota int64              `json:"retiredQuota"`
	Activated    bool               `json:"actived"`
	Retired      bool               `json:"retired"`

	// deprecated - do not use
	RetiredUsage int64 `json:"retiredUsage"`
}

type AllocationGroupWithParent struct {
	Parent util.Option[ParentOrChildWallet] `json:"parent"`
	Group  AllocationGroup                  `json:"group"`
}

type AllocationGroupWithChild struct {
	Child util.Option[ParentOrChildWallet] `json:"child"`
	Group AllocationGroup                  `json:"group"`
}

type ParentOrChildWallet struct {
	ProjectId    string `json:"projectId"`
	ProjectTitle string `json:"projectTitle"`
}

type WalletOwner struct {
	Type      WalletOwnerType `json:"type"`
	Username  string          `json:"username"`
	ProjectId string          `json:"projectId"`
}

func (o *WalletOwner) Reference() string {
	if o.ProjectId != "" {
		return o.ProjectId
	} else {
		return o.Username
	}
}

type WalletOwnerType string

const (
	WalletOwnerTypeUser    WalletOwnerType = "user"
	WalletOwnerTypeProject WalletOwnerType = "project"
)

func WalletOwnerUser(username string) WalletOwner {
	return WalletOwner{
		Type:     WalletOwnerTypeUser,
		Username: username,
	}
}

func WalletOwnerProject(projectId string) WalletOwner {
	return WalletOwner{
		Type:      WalletOwnerTypeProject,
		ProjectId: projectId,
	}
}

func WalletOwnerFromIds(username, projectId string) WalletOwner {
	result := WalletOwner{}
	if projectId != "" {
		result.Type = WalletOwnerTypeProject
		result.ProjectId = projectId
	} else {
		result.Type = WalletOwnerTypeUser
		result.Username = username
	}
	return result
}

// API
// =====================================================================================================================

const AccountingNamespace = "accounting/v2"

type RootAllocateRequest struct {
	Category ProductCategoryIdV2 `json:"category"`
	Quota    int64               `json:"quota"`
	Start    fnd.Timestamp       `json:"start"`
	End      fnd.Timestamp       `json:"end"`
}

var RootAllocate = rpc.Call[fnd.BulkRequest[RootAllocateRequest], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: AccountingNamespace,
	Operation:   "rootAllocate",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}

type UpdateAllocationRequest struct {
	AllocationId int64                      `json:"allocationId"`
	NewQuota     util.Option[int64]         `json:"newQuota"`
	NewStart     util.Option[fnd.Timestamp] `json:"newStart"`
	NewEnd       util.Option[fnd.Timestamp] `json:"newEnd"`
	Reason       string                     `json:"reason"`
}

var UpdateAllocation = rpc.Call[fnd.BulkRequest[UpdateAllocationRequest], util.Empty]{
	BaseContext: AccountingNamespace,
	Operation:   "updateAllocation",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}

type WalletsBrowseRequest struct {
	ItemsPerPage    int                      `json:"itemsPerPage"`
	Next            util.Option[string]      `json:"next"`
	FilterType      util.Option[ProductType] `json:"filterType"`
	IncludeChildren bool                     `json:"includeChildren"`
	ChildrenQuery   util.Option[string]      `json:"childrenQuery"`
}

var WalletsBrowse = rpc.Call[WalletsBrowseRequest, fnd.PageV2[WalletV2]]{
	BaseContext: AccountingNamespace,
	Convention:  rpc.ConventionBrowse,
	Operation:   "wallets",
	Roles:       rpc.RolesEndUser,
}

type RetrieveAccountingInfoForProjectRequest struct {
	ProjectId  string `json:"projectId"`
	PiUsername string `json:"piUsername"`
}

type RetrieveAccountingInfoForProjectResponse struct {
	Wallets   []WalletV2            `json:"wallets"`
	Grants    []GrantApplication    `json:"grants"`
	Ancestors map[string][]WalletV2 `json:"ancestors"`
}

var RetrieveAccountingInfoForProject = rpc.Call[RetrieveAccountingInfoForProjectRequest, RetrieveAccountingInfoForProjectResponse]{
	BaseContext: AccountingNamespace,
	Convention:  rpc.ConventionRetrieve,
	Operation:   "accountingInfoForProject",
	Roles:       rpc.RoleService,
}

type RetrieveWalletByAllocationRequest struct {
	AllocationId string `json:"allocationId"`
}

type RetrieveWalletByAllocationResponse struct {
	Id     string   `json:"id"`
	Wallet WalletV2 `json:"wallet"`
}

var RetrieveWalletByAllocationId = rpc.Call[RetrieveWalletByAllocationRequest, RetrieveWalletByAllocationResponse]{
	BaseContext: AccountingNamespace,
	Convention:  rpc.ConventionRetrieve,
	Operation:   "walletByAllocationId",
	Roles:       rpc.RoleService,
}

type RetrieveAccountingGraphRequest struct {
	WalletId string `json:"walletId"`
}

type RetrieveAccountingGraphResponse struct {
	Graph string `json:"graph"`
}

var RetrieveAccountingGraph = rpc.Call[RetrieveAccountingGraphRequest, RetrieveAccountingGraphResponse]{
	BaseContext: AccountingNamespace,
	Convention:  rpc.ConventionRetrieve,
	Operation:   "accountingGraph",
	Roles:       rpc.RoleService,
}

type WalletsBrowseInternalRequest struct {
	Owner WalletOwner `json:"owner"`
}

type WalletsBrowseInternalResponse struct {
	Wallets []WalletV2 `json:"wallets"`
}

var WalletsBrowseInternal = rpc.Call[WalletsBrowseInternalRequest, WalletsBrowseInternalResponse]{
	BaseContext: AccountingNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "browseWalletsInternal",
	Roles:       rpc.RolesPrivileged,
}

type ReportUsageRequest struct {
	IsDeltaCharge bool                `json:"isDeltaCharge"`
	Owner         WalletOwner         `json:"owner"`
	CategoryIdV2  ProductCategoryIdV2 `json:"categoryIdV2"`
	Usage         int64               `json:"usage"`
	Description   ChargeDescription   `json:"description"`
}

type ChargeDescription struct {
	Scope       util.Option[string] `json:"scope"`
	Description util.Option[string] `json:"description"`
}

var ReportUsage = rpc.Call[fnd.BulkRequest[ReportUsageRequest], fnd.BulkResponse[bool]]{
	BaseContext: AccountingNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "reportUsage",
	Roles:       rpc.RolesProvider,
}

type CheckProviderUsableRequest struct {
	Owner    WalletOwner         `json:"owner"`
	Category ProductCategoryIdV2 `json:"category"`
}

type CheckProviderUsableResponse struct {
	MaxUsable int64 `json:"maxUsable"`
}

var CheckProviderUsable = rpc.Call[fnd.BulkRequest[CheckProviderUsableRequest], fnd.BulkResponse[CheckProviderUsableResponse]]{
	BaseContext: AccountingNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "checkProviderUsable",
	Roles:       rpc.RolesProvider,
}

type RegisterProviderGiftRequest struct {
	OwnerUsername string              `json:"ownerUsername"`
	Category      ProductCategoryIdV2 `json:"category"`
	Quota         int64               `json:"quota"`
	ExpiresAt     util.Option[int64]  `json:"expiresAt"`
}

var RegisterProviderGift = rpc.Call[fnd.BulkRequest[RegisterProviderGiftRequest], util.Empty]{
	BaseContext: AccountingNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "registerProviderGift",
	Roles:       rpc.RolesProvider,
}

type FindRelevantProvidersRequest struct {
	Username          string                   `json:"username"`
	Project           util.Option[string]      `json:"project"`
	UseProject        bool                     `json:"useProject"`
	FilterProductType util.Option[ProductType] `json:"filterProductType"`
	IncludeFreeToUse  util.Option[bool]        `json:"includeFreeToUse"`
}

type FindRelevantProvidersResponse struct {
	Providers []string `json:"providers"`
}

var FindRelevantProviders = rpc.Call[fnd.BulkRequest[FindRelevantProvidersRequest], fnd.BulkResponse[FindRelevantProvidersResponse]]{
	BaseContext: AccountingNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "findRelevantProviders",
	Roles:       rpc.RolesPrivileged,
}

type FindAllProvidersRequest struct {
	FilterProductType util.Option[ProductType] `json:"filterProductType"`
	IncludeFreeToUse  util.Option[bool]        `json:"includeFreeToUse"`
}

type FindAllProvidersResponse struct {
	Providers []string `json:"providers"`
}

var FindAllProviders = rpc.Call[fnd.BulkRequest[FindAllProvidersRequest], fnd.BulkResponse[FindAllProvidersResponse]]{
	BaseContext: AccountingNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "findAllProviders",
	Roles:       rpc.RolesPrivileged,
}

type WalletDebugRequest struct {
	WalletId int `json:"walletId"`
}

type WalletDebugResponse struct {
	MermaidGraph string          `json:"mermaidGraph"`
	StateDump    json.RawMessage `json:"stateDump"`
}

var WalletsAdminDebug = rpc.Call[WalletDebugRequest, WalletDebugResponse]{
	BaseContext: AccountingNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "adminDebug",
	Roles:       rpc.RolesAdmin,
}

type WalletsAdminChargeRequest struct {
	WalletId      int               `json:"walletId"`
	Amount        int64             `json:"amount"`
	IsDeltaCharge util.Option[bool] `json:"isDeltaCharge"`
}

type WalletsAdminChargeResponse struct {
	ErrorIfAny util.Option[string] `json:"errorIfAny"`
}

var WalletsAdminCharge = rpc.Call[WalletsAdminChargeRequest, WalletsAdminChargeResponse]{
	BaseContext: AccountingNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "adminCharge",
	Roles:       rpc.RolesAdmin,
}

type WalletsAdminResetRequest struct {
	Category ProductCategoryIdV2 `json:"category"`
}

var WalletsAdminReset = rpc.Call[WalletsAdminResetRequest, util.Empty]{
	BaseContext: AccountingNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "adminReset",
	Roles:       rpc.RolesAdmin,
}

var ProviderNotificationStream = rpc.Call[util.Empty, util.Empty]{
	BaseContext: "accounting",
	Operation:   "notifications",
	Convention:  rpc.ConventionWebSocket,
}
