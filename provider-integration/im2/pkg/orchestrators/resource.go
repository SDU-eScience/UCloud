package orchestrators

import (
    "ucloud.dk/pkg/apm"
    fnd "ucloud.dk/pkg/foundation"
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
    Product     apm.ProductReference `json:"product"`
    Maintenance Maintenance          `json:"maintenance,omitempty"`
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
    SortDirectionAscending  SortDirection = "ASCENDING"
    SortDirectionDescending SortDirection = "DESCENDING"
)

type ResourceBrowseRequest[Flags any] struct {
    Flags         Flags         `json:"flags,omitempty"`
    ItemsPerPage  int           `json:"itemsPerPage,omitempty"`
    Next          string        `json:"next,omitempty"`
    SortBy        string        `json:"sortBy,omitempty"`
    SortDirection SortDirection `json:"sortDirection,omitempty"`
}
