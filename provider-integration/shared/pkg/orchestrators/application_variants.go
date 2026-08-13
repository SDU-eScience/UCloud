package orchestrators

import (
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type ApplicationVariantState string

const (
	ApplicationVariantStatePending ApplicationVariantState = "PENDING"
	ApplicationVariantStateActive  ApplicationVariantState = "ACTIVE"
	ApplicationVariantStateFailed  ApplicationVariantState = "FAILED"
	ApplicationVariantStateDeleted ApplicationVariantState = "DELETED"
)

type ApplicationVariant struct {
	Id                 int64                   `json:"id"`
	RevisionId         int64                   `json:"revisionId"`
	BaseApplication    NameAndVersion          `json:"baseApplication"`
	CreatedBy          string                  `json:"createdBy"`
	Project            util.Option[string]     `json:"project"`
	Image              string                  `json:"image"`
	ImageDigest        string                  `json:"imageDigest"`
	Provider           string                  `json:"provider"`
	Title              string                  `json:"title"`
	PublishedToProject bool                    `json:"publishedToProject"`
	State              ApplicationVariantState `json:"state"`
	Failure            util.Option[string]     `json:"failure"`
	CreatedAt          fnd.Timestamp           `json:"createdAt"`
}

type ApplicationVariantCreateRequest struct {
	BaseApplication    NameAndVersion `json:"baseApplication"`
	Image              string         `json:"image"`
	Provider           string         `json:"provider"`
	Title              string         `json:"title"`
	PublishedToProject bool           `json:"publishedToProject"`
}

var ApplicationVariantsCreate = rpc.Call[ApplicationVariantCreateRequest, ApplicationVariant]{
	BaseContext: "hpc/apps/variants",
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

type ApplicationVariantUpdateRequest struct {
	Id                 int64               `json:"id"`
	Title              util.Option[string] `json:"title"`
	PublishedToProject util.Option[bool]   `json:"publishedToProject"`
	Image              util.Option[string] `json:"image"`
}

var ApplicationVariantsUpdate = rpc.Call[ApplicationVariantUpdateRequest, ApplicationVariant]{
	BaseContext: "hpc/apps/variants",
	Convention:  rpc.ConventionUpdate,
	Operation:   "update",
	Roles:       rpc.RolesEndUser,
}

type ApplicationVariantBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`
}

var ApplicationVariantsBrowse = rpc.Call[ApplicationVariantBrowseRequest, fnd.PageV2[ApplicationVariant]]{
	BaseContext: "hpc/apps/variants",
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

type FindApplicationVariant struct {
	Id int64 `json:"id"`
}

var ApplicationVariantsRetrieve = rpc.Call[FindApplicationVariant, ApplicationVariant]{
	BaseContext: "hpc/apps/variants",
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

var ApplicationVariantsDelete = rpc.Call[FindApplicationVariant, util.Empty]{
	BaseContext: "hpc/apps/variants",
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesEndUser,
}

type ApplicationVariantValidateImageRequest struct {
	Owner                ResourceOwner `json:"owner"`
	Image                string        `json:"image"`
	RequireProjectAccess bool          `json:"requireProjectAccess"`
}

type ApplicationVariantValidateImageResponse struct {
	Image       string `json:"image"`
	ImageDigest string `json:"imageDigest"`
}

var ApplicationVariantsProviderValidateImage = rpc.Call[ApplicationVariantValidateImageRequest, ApplicationVariantValidateImageResponse]{
	BaseContext: "ucloud/" + rpc.ProviderPlaceholder + "/applicationVariants",
	Convention:  rpc.ConventionUpdate,
	Operation:   "validateImage",
	Roles:       rpc.RolesPrivileged,
}

type ApplicationVariantCompleteSnapshotRequest struct {
	VariantId       int64               `json:"variantId"`
	TaskId          int                 `json:"taskId"`
	BaseApplication NameAndVersion      `json:"baseApplication"`
	RequestedBy     string              `json:"requestedBy"`
	Image           string              `json:"image"`
	ImageDigest     string              `json:"imageDigest"`
	Failure         util.Option[string] `json:"failure"`
}

var ApplicationVariantsControlCompleteSnapshot = rpc.Call[ApplicationVariantCompleteSnapshotRequest, util.Empty]{
	BaseContext: "hpc/apps/variants/control",
	Convention:  rpc.ConventionUpdate,
	Operation:   "completeSnapshot",
	Roles:       rpc.RolesProvider,
}
