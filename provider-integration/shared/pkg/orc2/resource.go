package orchestrators

import (
	"slices"
	acc "ucloud.dk/shared/pkg/accounting"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/util"
)

type Resource struct {
	Id                  string              `json:"id,omitempty"`
	CreatedAt           fnd.Timestamp       `json:"createdAt"`
	Owner               ResourceOwner       `json:"owner"`
	Permissions         ResourcePermissions `json:"permissions"`
	ProviderGeneratedId string              `json:"providerGeneratedId,omitempty"`
}

type ResourceOwner struct {
	CreatedBy string `json:"createdBy,omitempty"`
	Project   string `json:"project,omitempty"`
}

type ResourcePermissions struct {
	Myself []Permission       `json:"myself,omitempty"`
	Others []ResourceAclEntry `json:"others,omitempty"`
}

type Permission string

const (
	PermissionRead     Permission = "READ"
	PermissionEdit     Permission = "EDIT"
	PermissionAdmin    Permission = "ADMIN"
	PermissionProvider Permission = "PROVIDER"
)

type ResourceAclEntry struct {
	Entity      AclEntity    `json:"entity"`
	Permissions []Permission `json:"permissions,omitempty"`
}

type AclEntity struct {
	Type      AclEntityType `json:"type,omitempty"`
	ProjectId string        `json:"projectId,omitempty"`
	Group     string        `json:"group,omitempty"`
	Username  string        `json:"username,omitempty"`
}

func PermissionsHas(list []Permission, perm Permission) bool {
	return slices.Contains(list, perm)
}

func PermissionsAdd(list []Permission, toAdd Permission) []Permission {
	result := list
	found := false
	for _, perm := range list {
		if perm == toAdd {
			found = true
			break
		}
	}

	if !found {
		result = append(result, toAdd)
	}
	return result
}

type AclEntityType string

const (
	AclEntityTypeProjectGroup AclEntityType = "project_group"
	AclEntityTypeUser         AclEntityType = "user"
)

func AclEntityProjectGroup(projectId, groupId string) AclEntity {
	return AclEntity{
		Type:      AclEntityTypeProjectGroup,
		ProjectId: projectId,
		Group:     groupId,
	}
}

func AclEntityUser(username string) AclEntity {
	return AclEntity{
		Type:     AclEntityTypeUser,
		Username: username,
	}
}

type ResourceUpdate struct {
	Timestamp fnd.Timestamp `json:"timestamp"`
	Status    string        `json:"status,omitempty"`
}

type ResourceStatus[S any] struct {
	ResolvedSupport util.Option[ResolvedSupport[S]] `json:"resolvedSupport"`
	ResolvedProduct util.Option[acc.ProductV2]      `json:"resolvedProduct"`
}

type ProductSupport struct {
	Product     acc.ProductReference     `json:"product"`
	Maintenance util.Option[Maintenance] `json:"maintenance,omitempty"`
}

type Maintenance struct {
	Description     string                  `json:"description,omitempty"`
	Availability    MaintenanceAvailability `json:"availability,omitempty"`
	StartsAt        fnd.Timestamp           `json:"startsAt"`
	EstimatedEndsAt fnd.Timestamp           `json:"estimatedEndsAt"`
}

type MaintenanceAvailability string

const (
	MaintenanceAvailabilityMinorDisruption MaintenanceAvailability = "MINOR_DISRUPTION"
	MaintenanceAvailabilityMajorDisruption MaintenanceAvailability = "MAJOR_DISRUPTION"
	MaintenanceAvailabilityNoService       MaintenanceAvailability = "NO_SERVICE"
)

type SortDirection string

const (
	SortDirectionDefault    SortDirection = ""
	SortDirectionAscending  SortDirection = "ascending"
	SortDirectionDescending SortDirection = "descending"
)

type ResourceBrowseRequest[Flags any] struct {
	Flags         Flags                      `json:"flags,omitempty"`
	ItemsPerPage  int                        `json:"itemsPerPage,omitempty"`
	Next          util.Option[string]        `json:"next,omitempty"`
	SortBy        util.Option[string]        `json:"sortBy,omitempty"`
	SortDirection util.Option[SortDirection] `json:"sortDirection,omitempty"`
}

type ResourceSpecification struct {
	Product acc.ProductReference `json:"product"`
}

type ResourceRetrieveRequest[Flags any] struct {
	Flags Flags  `json:"flags,omitempty"`
	Id    string `json:"id"`
}

type ProviderRegisteredResource[Spec any] struct {
	Spec                Spec                `json:"spec"`
	ProviderGeneratedId util.Option[string] `json:"providerGeneratedId"`
	CreatedBy           util.Option[string] `json:"createdBy"`
	Project             util.Option[string] `json:"project"`
	ProjectAllRead      bool                `json:"projectAllRead"`
	ProjectAllWrite     bool                `json:"projectAllWrite"`
}

func ResourceOwnerToWalletOwner(resource Resource) acc.WalletOwner {
	if resource.Owner.Project != "" {
		return acc.WalletOwnerProject(resource.Owner.Project)
	} else {
		return acc.WalletOwnerUser(resource.Owner.CreatedBy)
	}
}

type ResourceUpdateAndId[U any] struct {
	Id     string `json:"id"`
	Update U      `json:"update"`
}

type ResourceFlags struct {
	IncludeOthers         bool                       `json:"includeOthers"`
	IncludeUpdates        bool                       `json:"includeUpdates"`
	IncludeSupport        bool                       `json:"includeSupport"`
	IncludeProduct        bool                       `json:"includeProduct"`
	FilterCreatedBy       util.Option[string]        `json:"filterCreatedBy"`
	FilterCreatedAfter    util.Option[fnd.Timestamp] `json:"filterCreatedAfter"`
	FilterCreatedBefore   util.Option[fnd.Timestamp] `json:"filterCreatedBefore"`
	FilterProvider        util.Option[string]        `json:"filterProvider"`
	FilterProductId       util.Option[string]        `json:"filterProductId"`
	FilterProductCategory util.Option[string]        `json:"filterProductCategory"`
	HideProductId         util.Option[string]        `json:"hideProductId"`
	HideProductCategory   util.Option[string]        `json:"hideProductCategory"`
	HideProvider          util.Option[string]        `json:"hideProvider"`
	FilterProviderIds     util.Option[string]        `json:"filterProviderIds"`
	FilterIds             util.Option[string]        `json:"filterIds"`
}

func ResourceFlagsIncludeAll() ResourceFlags {
	return ResourceFlags{
		IncludeOthers:  true,
		IncludeUpdates: true,
		IncludeSupport: true,
		IncludeProduct: true,
	}
}

type UpdatedAcl struct {
	Id      string             `json:"id"`
	Added   []ResourceAclEntry `json:"added"`
	Deleted []AclEntity        `json:"deleted"`
}

type UpdatedAclWithResource[R any] struct {
	Resource R                  `json:"resource"`
	Added    []ResourceAclEntry `json:"added"`
	Deleted  []AclEntity        `json:"deleted"`
}

type SupportByProvider[S any] struct {
	ProductsByProvider map[string][]ResolvedSupport[S] `json:"productsByProvider"`
}

type ResolvedSupport[S any] struct {
	Product  acc.ProductV2 `json:"product"`
	Support  S             `json:"support"`  // being deprecated in favor of Features
	Features []string      `json:"features"` // use instead of Support
}
