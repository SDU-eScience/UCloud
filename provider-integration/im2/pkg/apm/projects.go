package apm

import (
	c "ucloud.dk/pkg/client"
	"ucloud.dk/pkg/util"
)
import fnd "ucloud.dk/pkg/foundation"

const projectsContext = "/api/projects/v2"
const projectsNamespace = "projects.v2."

type Project struct {
	Id            string               `json:"id,omitempty"`
	CreatedAt     fnd.Timestamp        `json:"createdAt"`
	ModifiedAt    fnd.Timestamp        `json:"modifiedAt"`
	Specification ProjectSpecification `json:"specification"`
	Status        ProjectStatus        `json:"status"`
}

type ProjectSpecification struct {
	Parent              string `json:"parent,omitempty"`
	Title               string `json:"title,omitempty"`
	CanConsumeResources bool   `json:"canConsumeResources,omitempty"`
}

type ProjectStatus struct {
	Archived                   bool                `json:"archived,omitempty"`
	IsFavorite                 bool                `json:"isFavorite,omitempty"`
	Members                    []ProjectMember     `json:"members,omitempty"`
	Groups                     []ProjectGroup      `json:"groups,omitempty"`
	Settings                   ProjectSettings     `json:"settings"`
	MyRole                     ProjectRole         `json:"myRole,omitempty"`
	Path                       string              `json:"path,omitempty"`
	PersonalProviderProjectFor util.Option[string] `json:"personalProviderProjectFor"`
}

type ProjectSettings struct {
	SubProjects struct {
		AllowRenaming bool `json:"allowRenaming,omitempty"`
	} `json:"subProjects,omitempty"`
}

type ProjectRole string

const (
	ProjectRoleUser  ProjectRole = "USER"
	ProjectRoleAdmin ProjectRole = "ADMIN"
	ProjectRolePI    ProjectRole = "PI"
)

type ProjectMember struct {
	Username string      `json:"username,omitempty"`
	Role     ProjectRole `json:"role,omitempty"`
	Email    string      `json:"email,omitempty"`
}

type ProjectGroup struct {
	Id            string                    `json:"id,omitempty"`
	CreatedAt     fnd.Timestamp             `json:"createdAt"`
	Specification ProjectGroupSpecification `json:"specification"`
	Status        ProjectGroupStatus        `json:"status"`
}

type ProjectGroupSpecification struct {
	Project string `json:"project,omitempty"`
	Title   string `json:"title,omitempty"`
}

type ProjectGroupStatus struct {
	Members []string `json:"members,omitempty"`
}

type ProjectFlags struct {
	IncludeMembers  bool
	IncludeGroups   bool
	IncludeFavorite bool
	IncludeArchived bool
	IncludeSettings bool
	IncludePath     bool
}

func RetrieveProject(id string, flags ProjectFlags) (project Project, err error) {
	return c.ApiRetrieve[Project](
		projectsNamespace+"retrieve",
		projectsContext,
		"",
		append(c.StructToParameters(flags), "id", id),
	)
}

type ProjectBrowseFlags struct {
	ProjectFlags
	SortBy        string
	SortDirection string
}

func BrowseProjects(next string, flags ProjectBrowseFlags) (fnd.PageV2[Project], error) {
	return c.ApiBrowse[fnd.PageV2[Project]](
		projectsNamespace+"browse",
		projectsContext,
		"",
		append(c.StructToParameters(flags), "next", next, "itemsPerPage", "250"),
	)
}

func IsMemberOfGroup(project Project, groupId string, username string) bool {
	for _, group := range project.Status.Groups {
		if group.Id == groupId {
			for _, member := range group.Status.Members {
				if member == username {
					return true
				}
			}
		}
	}

	return false
}
