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

type UsageBreakdownItemType string

const (
	UsageBreakdownItemTypeResource  UsageBreakdownItemType = "resource"
	UsageBreakdownItemTypeWorkspace UsageBreakdownItemType = "workspace"
)

type UsageBreakdownResource struct {
	Type UsageBreakdownResourceType `json:"type"`
	Id   string                     `json:"id"`
}

type UsageBreakdownItem struct {
	Type          UsageBreakdownItemType              `json:"type"`
	Usage         int64                               `json:"usage"`
	Resource      util.Option[UsageBreakdownResource] `json:"resource"`
	Workspace     util.Option[WalletOwner]            `json:"workspace"`
	LastUpdatedAt util.Option[fnd.Timestamp]          `json:"lastUpdatedAt"`
}

type UsageBreakdownBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`
	Category     ProductCategoryIdV2 `json:"category"`
}

// UsageBreakdownBrowse returns a current usage breakdown. It is not a time-bound statement or invoice.
var UsageBreakdownBrowse = rpc.Call[UsageBreakdownBrowseRequest, fnd.PageV2[UsageBreakdownItem]]{
	BaseContext: usageBreakdownContext,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}
