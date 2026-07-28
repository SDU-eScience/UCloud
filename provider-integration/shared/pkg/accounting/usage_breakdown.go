package apm

import (
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const usageBreakdownContext = "accounting/v2/usageBreakdown"

type UsageBreakdownResourceType string

const (
	UsageBreakdownResourceTypeDrive UsageBreakdownResourceType = "drive"
	UsageBreakdownResourceTypeJob   UsageBreakdownResourceType = "job"
)

type UsageBreakdownResource struct {
	Type UsageBreakdownResourceType `json:"type"`
	Id   string                     `json:"id"`
}

type UsageBreakdownItem struct {
	Resource           UsageBreakdownResource `json:"resource"`
	Usage              int64                  `json:"usage"`
	LastUpdatedAt      fnd.Timestamp          `json:"lastUpdatedAt"`
	Title              string                 `json:"title"`
	CreatedBy          string                 `json:"createdBy"`
	Workspace          WalletOwner            `json:"workspace"`
	WorkspaceTitle     string                 `json:"workspaceTitle"`
	IsExternallyFunded bool                   `json:"isExternallyFunded"`
}

type UsageBreakdownSortBy string

const (
	UsageBreakdownSortByUsage      UsageBreakdownSortBy = "usage"
	UsageBreakdownSortByReportedAt UsageBreakdownSortBy = "reportedAt"
)

type UsageBreakdownSortDirection string

const (
	UsageBreakdownSortAscending  UsageBreakdownSortDirection = "ascending"
	UsageBreakdownSortDescending UsageBreakdownSortDirection = "descending"
)

type UsageBreakdownAutocompleteType string

const (
	UsageBreakdownAutocompleteProject   UsageBreakdownAutocompleteType = "project"
	UsageBreakdownAutocompleteCreatedBy UsageBreakdownAutocompleteType = "createdBy"
)

type UsageBreakdownAutocompleteSuggestion struct {
	Type  UsageBreakdownAutocompleteType `json:"type"`
	Value string                         `json:"value"`
	Label string                         `json:"label"`
}

type UsageBreakdownBrowseRequest struct {
	ItemsPerPage        int                                      `json:"itemsPerPage"`
	Next                util.Option[string]                      `json:"next"`
	CategoryName        string                                   `json:"categoryName"`
	CategoryProvider    string                                   `json:"categoryProvider"`
	FilterProject       util.Option[string]                      `json:"filterProject"`
	FilterCreatedBy     util.Option[string]                      `json:"filterCreatedBy"`
	FilterReportedAtMin util.Option[uint64]                      `json:"filterReportedAtMin"`
	FilterReportedAtMax util.Option[uint64]                      `json:"filterReportedAtMax"`
	FilterUsageMin      util.Option[int64]                       `json:"filterUsageMin"`
	FilterUsageMax      util.Option[int64]                       `json:"filterUsageMax"`
	WorkspaceSearch     util.Option[string]                      `json:"workspaceSearch"`
	CreatedBySearch     util.Option[string]                      `json:"createdBySearch"`
	SortBy              util.Option[UsageBreakdownSortBy]        `json:"sortBy"`
	SortDirection       util.Option[UsageBreakdownSortDirection] `json:"sortDirection"`
}

type UsageBreakdownBrowseResponse struct {
	Items                 []UsageBreakdownItem                   `json:"items"`
	ItemsPerPage          int                                    `json:"itemsPerPage"`
	Next                  util.Option[string]                    `json:"next"`
	TotalUsage            int64                                  `json:"totalUsage"`
	TotalCount            int                                    `json:"totalCount"`
	WorkspaceAutocomplete []UsageBreakdownAutocompleteSuggestion `json:"workspaceAutocomplete"`
	CreatedByAutocomplete []UsageBreakdownAutocompleteSuggestion `json:"createdByAutocomplete"`
}

// UsageBreakdownBrowse returns a current usage breakdown. It is not a time-bound statement or invoice.
var UsageBreakdownBrowse = rpc.Call[UsageBreakdownBrowseRequest, UsageBreakdownBrowseResponse]{
	BaseContext: usageBreakdownContext,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}
