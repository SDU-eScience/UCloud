package apm

import (
	c "ucloud.dk/pkg/client"
	fnd "ucloud.dk/pkg/foundation"
	"ucloud.dk/pkg/util"
)

type AllocationGroup struct {
	Id          int
	Allocations []Allocation
	Usage       int64
}

type Allocation struct {
	Id           int64
	StartDate    fnd.Timestamp
	EndDate      fnd.Timestamp
	Quota        int64
	GrantedIn    int64
	RetiredUsage int64
}

type AllocationGroupWithParent struct {
	Parent ParentOrChildWallet
	Group  AllocationGroup
}

type AllocationGroupWithChild struct {
	Child ParentOrChildWallet
	Group AllocationGroup
}

type ParentOrChildWallet struct {
	ProjectId    string
	ProjectTitle string
}

type WalletOwner struct {
	Type      WalletOwnerType `json:"type"`
	Username  string          `json:"username"`
	ProjectId string          `json:"projectId"`
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
		result.ProjectId = username
	}
	return result
}

type UsageReportItem struct {
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

type WalletV2 struct {
	Owner            WalletOwner
	PaysFor          ProductCategory
	AllocationGroups []AllocationGroupWithParent
	Children         []AllocationGroupWithChild

	TotalUsage     int64
	LocalUsage     int64
	MaxUsable      int64
	Quota          int64
	TotalAllocated int64

	LastSignificantUpdateAt fnd.Timestamp
}

// API
// =====================================================================================================================
const accountingContext = "/api/accounting/v2"
const accountingNamespace = "accounting.v2."

func ReportUsage(request fnd.BulkRequest[UsageReportItem]) (fnd.BulkResponse[bool], error) {
	return c.ApiUpdate[fnd.BulkResponse[bool]](accountingNamespace+"reportUsage", accountingContext,
		"reportUsage", request)
}

type CheckProviderUsableReq struct {
	Owner    WalletOwner
	Category ProductCategoryIdV2
}

type CheckProviderUsableResp struct {
	MaxUsable int64
}

func CheckProviderUsable(
	request fnd.BulkRequest[CheckProviderUsableReq],
) (fnd.BulkResponse[CheckProviderUsableResp], error) {
	return c.ApiUpdate[fnd.BulkResponse[CheckProviderUsableResp]](accountingNamespace+"checkProviderUsable",
		accountingContext, "checkProviderUsable", request)
}

type BrowseProviderAllocationsReq struct {
	FilterOwnerId        string `json:"filterOwnerId"`
	FilterOwnerIsProject bool   `json:"filterOwnerIsProject"`
	FilterCategory       string `json:"filterCategory"`
	Next                 string `json:"next,omitempty"`
	ItemsPerPage         int    `json:"itemsPerPage"`
}

type BrowseProviderAllocationsResp struct {
	Id       string                        `json:"id"`
	Owner    WalletOwner                   `json:"owner"`
	Category ProductCategory               `json:"categoryId"`
	Start    fnd.Timestamp                 `json:"notBefore"`
	End      fnd.Timestamp                 `json:"notAfter"`
	Quota    int64                         `json:"quota"`
	Grant    util.Option[GrantInformation] `json:"grant"`
}

type GrantInformation struct {
	GrantId        uint64   `json:"grantId"`
	Approver       []string `json:"approver"`
	ApproverTitles []string `json:"approverTitles"`
	ReferenceIds   []string `json:"referenceIds"`
}

func BrowseProviderAllocations(
	next string,
	request BrowseProviderAllocationsReq,
) (fnd.PageV2[BrowseProviderAllocationsResp], error) {
	request.Next = next
	request.ItemsPerPage = 250

	return c.ApiUpdate[fnd.PageV2[BrowseProviderAllocationsResp]](
		accountingNamespace+"browseProviderAllocations",
		accountingContext,
		"browseProviderAllocations",
		request,
	)
}
