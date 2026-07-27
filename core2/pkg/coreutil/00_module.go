package coreutil

import (
	"cmp"
	"database/sql"
	"encoding/json"
	"fmt"
	"os"
	"slices"
	"strconv"
	"strings"
	"sync"
	"time"

	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// NOTE(Dan): The purpose of this package is to have a place to put code which is shared amongst all deployments of the
// Core. Any deployment is free to call anything from this package. Put code here sparringly and only when needed.

// ProjectRetrieveFromDatabase will retrieve a project directly from the database. No authentication or authorization
// will be performed. The project is guaranteed to be up-to-date because the foundation deployment only does
// write-through updates. The result of this function is always fetched from the database with no caching. Callers of
// this function are recommended to introduce their own caching if needed.
//
// NOTE(Dan): This function is placed here to avoid a dependency in the accounting notification stream to providers.
// With this function, the accounting stream can listen for project updates purely through a trigger at the database
// level and fetch information directly from the database. The alternative to this would be to re-invent the exact
// same functionality in the code, but this was deemed unnecessary.
func ProjectRetrieveFromDatabase(tx *db.Transaction, id string) (fndapi.Project, bool) {
	projectInfo, ok := db.Get[struct {
		Id                   string
		CreatedAt            time.Time
		ModifiedAt           time.Time
		Title                string
		Archived             bool
		Parent               sql.NullString
		SubProjectsCanRename bool
		Pid                  int
		ProviderProjectFor   sql.NullString
		CanConsumeResources  bool
	}](
		tx,
		`
			select
				id, created_at, modified_at, title, archived, parent, subprojects_renameable as sub_projects_can_rename,
				pid, provider_project_for, can_consume_resources
			from
				project.projects
			where
				id = :id
		`,
		db.Params{
			"id": id,
		},
	)

	if !ok {
		return fndapi.Project{}, false
	}

	p := fndapi.Project{
		Id:         id,
		CreatedAt:  fndapi.Timestamp(projectInfo.CreatedAt),
		ModifiedAt: fndapi.Timestamp(projectInfo.ModifiedAt),
		Specification: fndapi.ProjectSpecification{
			Parent:              util.OptStringIfNotEmpty(projectInfo.Parent.String),
			Title:               projectInfo.Title,
			CanConsumeResources: projectInfo.CanConsumeResources,
		},
		Status: fndapi.ProjectStatus{
			Archived:                   projectInfo.Archived,
			PersonalProviderProjectFor: util.OptStringIfNotEmpty(projectInfo.ProviderProjectFor.String),
			Members:                    make([]fndapi.ProjectMember, 0),
			Groups:                     make([]fndapi.ProjectGroup, 0),
		},
	}

	p.Status.Settings.SubProjects.AllowRenaming = projectInfo.SubProjectsCanRename

	projectMembers := db.Select[struct {
		Username string
		Role     string
	}](
		tx,
		`
			select pm.username, pm.role
			from project.project_members pm
			where project_id = :id
		`,
		db.Params{
			"id": id,
		},
	)

	for _, pm := range projectMembers {
		p.Status.Members = append(p.Status.Members, fndapi.ProjectMember{
			Username: pm.Username,
			Role:     fndapi.ProjectRole(pm.Role),
		})
	}

	groups := db.Select[struct {
		Id           string
		Gid          string
		Title        string
		GroupMembers string // json
	}](
		tx,
		`
			select
				g.id, g.gid, g.title, jsonb_agg(gm.username) as group_members
			from
				project.groups g
				left join project.group_members gm on g.id = gm.group_id
			where
				g.project = :id
			group by
				g.id, g.gid, g.title
			order by lower(g.title)
		`,
		db.Params{
			"id": id,
		},
	)

	for _, g := range groups {
		var memberNames []string
		_ = json.Unmarshal([]byte(g.GroupMembers), &memberNames)

		if len(memberNames) == 1 && memberNames[0] == "" {
			memberNames = make([]string, 0)
		}

		p.Status.Groups = append(p.Status.Groups, fndapi.ProjectGroup{
			Id: g.Id,
			Specification: fndapi.ProjectGroupSpecification{
				Project: p.Id,
				Title:   g.Title,
			},
			Status: fndapi.ProjectGroupStatus{
				Members: memberNames,
			},
		})

		newGroup := &p.Status.Groups[len(p.Status.Groups)-1]
		slices.SortFunc(newGroup.Status.Members, func(a, b string) int {
			return cmp.Compare(strings.ToLower(a), strings.ToLower(b))
		})
	}

	return p, true
}

func ProjectRetrieveFromDatabaseViaGroupId(tx *db.Transaction, groupId string) (fndapi.Project, bool) {
	row, ok := db.Get[struct{ Project string }](
		tx,
		`select project from project.groups where id = :id`,
		db.Params{"id": groupId},
	)

	if !ok {
		return fndapi.Project{}, false
	} else {
		return ProjectRetrieveFromDatabase(tx, row.Project)
	}
}

func ProjectsListUpdatedAfter(timestamp time.Time) []rpc.ProjectId {
	return db.NewTx(func(tx *db.Transaction) []rpc.ProjectId {
		rows := db.Select[struct{ Id string }](
			tx,
			`
				select id
				from project.projects
				where
					modified_at > :timestamp
		    `,
			db.Params{
				"timestamp": timestamp,
			},
		)

		var result []rpc.ProjectId
		for _, row := range rows {
			result = append(result, rpc.ProjectId(row.Id))
		}

		return result
	})
}

type TaskOwner struct {
	User     string
	Provider string
}

func TaskRetrieveOwner(taskId int) (TaskOwner, bool) {
	return db.NewTx2(func(tx *db.Transaction) (TaskOwner, bool) {
		row, ok := db.Get[struct {
			OwnedBy   string
			CreatedBy string
		}](
			tx,
			`
				select owned_by, created_by
				from task.tasks_v2
				where id = :id
		    `,
			db.Params{
				"id": taskId,
			},
		)

		if ok {
			return TaskOwner{Provider: row.OwnedBy, User: row.CreatedBy}, true
		} else {
			return TaskOwner{}, false
		}
	})
}

type UsageBreakdownResource struct {
	Type         string
	Id           string
	Title        string
	CreatedBy    string
	ProjectId    string
	ProjectTitle string
	Exists       bool
}

var usageBreakdownResourceCache = struct {
	mu          sync.Mutex
	initialized bool
	resources   map[string]UsageBreakdownResource
}{resources: map[string]UsageBreakdownResource{}}

// UsageBreakdownRetrieveResources performs a privileged bulk lookup of resource and workspace metadata. It deliberately
// bypasses resource authorization and must only be called after the caller has authorized the complete usage breakdown.
// Resource metadata is immutable, so the first call eagerly caches all jobs and drives for the lifetime of the process.
// Later calls only retrieve uncached resources. Unknown and deleted resources retain the workspace metadata supplied by
// the caller and are not cached, allowing a later call to discover resources created after the initial cache population.
func UsageBreakdownRetrieveResources(requested []UsageBreakdownResource) []UsageBreakdownResource {
	if len(requested) == 0 {
		return []UsageBreakdownResource{}
	}

	usageBreakdownResourceCache.mu.Lock()
	if !usageBreakdownResourceCache.initialized {
		for _, resource := range usageBreakdownRetrieveAllResourcesFromDatabase() {
			usageBreakdownResourceCache.resources[usageBreakdownResourceKey(resource)] = resource
		}
		usageBreakdownResourceCache.initialized = true
	}

	result := make([]UsageBreakdownResource, 0, len(requested))
	missing := make([]UsageBreakdownResource, 0)
	for _, resource := range requested {
		if cached, ok := usageBreakdownResourceCache.resources[usageBreakdownResourceKey(resource)]; ok {
			result = append(result, cached)
		} else {
			missing = append(missing, resource)
		}
	}
	usageBreakdownResourceCache.mu.Unlock()

	retrieved := usageBreakdownRetrieveRequestedResourcesFromDatabase(missing)
	result = append(result, retrieved...)
	usageBreakdownResourceCache.mu.Lock()
	for _, resource := range retrieved {
		if resource.Exists {
			usageBreakdownResourceCache.resources[usageBreakdownResourceKey(resource)] = resource
		}
	}
	usageBreakdownResourceCache.mu.Unlock()
	return result
}

func usageBreakdownResourceKey(resource UsageBreakdownResource) string {
	return resource.Type + "\x00" + resource.Id
}

func usageBreakdownRetrieveAllResourcesFromDatabase() []UsageBreakdownResource {
	return db.NewTx(func(tx *db.Transaction) []UsageBreakdownResource {
		return db.Select[UsageBreakdownResource](
			tx,
			`
				select
					case r.type when 'file_collection' then 'drive' else 'job' end as type,
					r.id::text as id,
					case
						when r.type = 'file_collection' then coalesce(d.title, '')
						when r.type = 'job' then coalesce(nullif(j.name, ''), a.title, j.application_name, '')
						else ''
					end as title,
					r.created_by,
					coalesce(r.project, '') as project_id,
					coalesce(p.title, '') as project_title,
					true as exists
				from
					provider.resource r
					left join file_orchestrator.file_collections d on r.type = 'file_collection' and d.resource = r.id
					left join app_orchestrator.jobs j on r.type = 'job' and j.resource = r.id
					left join app_store.applications a on a.name = j.application_name and a.version = j.application_version
					left join project.projects p on p.id = r.project
				where
					r.type in ('file_collection', 'job')
			`,
			db.Params{},
		)
	})
}

func usageBreakdownRetrieveRequestedResourcesFromDatabase(requested []UsageBreakdownResource) []UsageBreakdownResource {
	if len(requested) == 0 {
		return []UsageBreakdownResource{}
	}

	types := make([]string, 0, len(requested))
	ids := make([]int64, 0, len(requested))
	createdBy := make([]string, 0, len(requested))
	projectIds := make([]string, 0, len(requested))
	for _, resource := range requested {
		id, err := strconv.ParseInt(resource.Id, 10, 64)
		if err != nil {
			continue
		}
		types = append(types, resource.Type)
		ids = append(ids, id)
		createdBy = append(createdBy, resource.CreatedBy)
		projectIds = append(projectIds, resource.ProjectId)
	}
	if len(ids) == 0 {
		return []UsageBreakdownResource{}
	}

	return db.NewTx(func(tx *db.Transaction) []UsageBreakdownResource {
		return db.Select[UsageBreakdownResource](
			tx,
			`
				with requested as (
					select
						unnest(cast(:types as text[])) as type,
						unnest(cast(:ids as bigint[])) as id,
						unnest(cast(:created_by as text[])) as created_by,
						unnest(cast(:project_ids as text[])) as project_id
				)
				select
					requested.type,
					requested.id::text as id,
					case
						when requested.type = 'drive' then coalesce(d.title, '')
						when requested.type = 'job' then coalesce(nullif(j.name, ''), a.title, j.application_name, '')
						else ''
					end as title,
					coalesce(r.created_by, requested.created_by) as created_by,
					coalesce(r.project, requested.project_id) as project_id,
					coalesce(p.title, '') as project_title,
					r.id is not null as exists
				from
					requested
					left join provider.resource r on
						r.id = requested.id
						and r.type = case when requested.type = 'drive' then 'file_collection' else requested.type end
					left join file_orchestrator.file_collections d on requested.type = 'drive' and d.resource = r.id
					left join app_orchestrator.jobs j on requested.type = 'job' and j.resource = r.id
					left join app_store.applications a on a.name = j.application_name and a.version = j.application_version
					left join project.projects p on p.id = coalesce(r.project, nullif(requested.project_id, ''))
				where
					requested.type in ('drive', 'job')
			`,
			db.Params{"types": types, "ids": ids, "created_by": createdBy, "project_ids": projectIds},
		)
	})
}

func PrintStartupTimes(name string, times map[string]time.Duration) {
	if util.DevelopmentModeEnabled() || os.Getenv("UCLOUD_STARTUP_TIMES") != "" {
		b := strings.Builder{}

		b.WriteString(fmt.Sprintf("%s startup compete!\n", name))
		var entries []util.Tuple2[string, time.Duration]
		for stage, t := range times {
			entries = append(entries, util.Tuple2[string, time.Duration]{stage, t})
		}

		slices.SortFunc(entries, func(a, b util.Tuple2[string, time.Duration]) int {
			return cmp.Compare(a.Second, b.Second) * -1
		})

		for _, entry := range entries {
			b.WriteString(fmt.Sprintf("----- %v: %v\n", entry.First, entry.Second))
		}

		log.Info("%s", b.String())
	}
}
