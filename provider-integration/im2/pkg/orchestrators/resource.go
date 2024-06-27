package orchestrators

import (
	"ucloud.dk/pkg/apm"
	fnd "ucloud.dk/pkg/foundation"
	"ucloud.dk/pkg/util"
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

type ProductSupport struct {
	Product     apm.ProductReference     `json:"product"`
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
	Flags         Flags         `json:"flags,omitempty"`
	ItemsPerPage  int           `json:"itemsPerPage,omitempty"`
	Next          string        `json:"next,omitempty"`
	SortBy        string        `json:"sortBy,omitempty"`
	SortDirection SortDirection `json:"sortDirection,omitempty"`
}

type ResourceSpecification struct {
	Product apm.ProductReference `json:"product"`
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

func ResourceOwnerToWalletOwner(resource Resource) apm.WalletOwner {
	if resource.Owner.Project != "" {
		return apm.WalletOwnerProject(resource.Owner.Project)
	} else {
		return apm.WalletOwnerUser(resource.Owner.CreatedBy)
	}
}

type ResourceUpdateAndId[U any] struct {
	Id     string `json:"id"`
	Update U      `json:"update"`
}
