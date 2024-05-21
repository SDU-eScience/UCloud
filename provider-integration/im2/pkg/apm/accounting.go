package apm

import (
	c "ucloud.dk/pkg/client"
	fnd "ucloud.dk/pkg/foundation"
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
	Type      WalletOwnerType `json:"type,omitempty"`
	Username  string          `json:"username,omitempty"`
	ProjectId string          `json:"projectId,omitempty"`
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

type UsageReportItem struct {
	IsDeltaCharge bool
	Owner         WalletOwner
	CategoryIdV2  ProductCategoryIdV2
	Usage         int64
	Description   ChargeDescription
}

type ChargeDescription struct {
	Scope       string
	Description string
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

func ReportUsage(client *c.Client, request fnd.BulkRequest[UsageReportItem]) (c.HttpStatus, fnd.BulkResponse[bool]) {
	return c.ApiUpdate[fnd.BulkResponse[bool]](client, accountingNamespace+"reportUsage", accountingContext,
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
	client *c.Client,
	request fnd.BulkRequest[CheckProviderUsableReq],
) (c.HttpStatus, fnd.BulkResponse[CheckProviderUsableResp]) {
	return c.ApiUpdate[fnd.BulkResponse[CheckProviderUsableResp]](client, accountingNamespace+"checkProviderUsable",
		accountingContext, "checkProviderUsable", request)
}

type BrowseProviderAllocationsReq struct {
	FilterOwnerId        string
	FilterOwnerIsProject bool
	FilterCategory       string
}

type BrowseProviderAllocationsResp struct {
	Id       string          `json:"id"`
	Owner    WalletOwner     `json:"owner"`
	Category ProductCategory `json:"categoryId"`
	Start    fnd.Timestamp   `json:"notBefore"`
	End      fnd.Timestamp   `json:"notAfter"`
	Quota    int64           `json:"quota"`
}

func BrowseProviderAllocations(
	client *c.Client,
	next string,
	request BrowseProviderAllocationsReq,
) (c.HttpStatus, fnd.BulkResponse[BrowseProviderAllocationsResp]) {
	var payload struct {
		BrowseProviderAllocationsReq
		Next         string
		ItemsPerPage int
	}

	payload.BrowseProviderAllocationsReq = request
	payload.Next = next
	payload.ItemsPerPage = 250

	return c.ApiUpdate[fnd.BulkResponse[BrowseProviderAllocationsResp]](
		client,
		accountingNamespace+"browseProviderAllocations",
		accountingContext,
		"browseProviderAllocations",
		payload,
	)
}
