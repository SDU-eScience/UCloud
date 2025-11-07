package apm

import (
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type Gift struct {
	Title            string              `json:"title"`
	Description      string              `json:"description"`
	Resources        []AllocationRequest `json:"resources"`
	ResourcesOwnedBy string              `json:"resourcesOwnedBy"`
	RenewEvery       int                 `json:"renewEvery"`
}

type GiftWithCriteria struct {
	Gift
	Id       int            `json:"id"`
	Criteria []UserCriteria `json:"criteria"`
}

const giftsBaseContext = "gifts"

type GiftsFindById struct {
	GiftId int `json:"giftId"`
}

var GiftsClaim = rpc.Call[GiftsFindById, util.Empty]{
	BaseContext: giftsBaseContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "claim",
}

type GiftsAvailableResponse struct {
	Gifts []fnd.FindByIntId `json:"gifts"`
}

var GiftsAvailable = rpc.Call[util.Empty, GiftsAvailableResponse]{
	BaseContext: giftsBaseContext,
	Convention:  rpc.ConventionQueryParameters,
	Roles:       rpc.RolesEndUser,
	Operation:   "available",
}

var GiftsCreate = rpc.Call[GiftWithCriteria, fnd.FindByIntId]{
	BaseContext: giftsBaseContext,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

var GiftsDelete = rpc.Call[GiftsFindById, util.Empty]{
	BaseContext: giftsBaseContext,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesEndUser,
}

type GiftsBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`
}

var GiftsBrowse = rpc.Call[GiftsBrowseRequest, fnd.PageV2[GiftWithCriteria]]{
	BaseContext: giftsBaseContext,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}
