package coreutil

import (
	"cmp"
	"database/sql"
	"encoding/json"
	"fmt"
	"os"
	"slices"
	"strings"
	"time"

	db "ucloud.dk/shared/pkg/database2"
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
