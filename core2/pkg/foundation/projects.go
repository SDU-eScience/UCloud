package foundation

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"hash/fnv"
	"math/rand"
	"net/http"
	"runtime"
	"slices"
	"strings"
	"sync"
	"time"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// TODO(Dan): This service is not completed yet, this is a rough list of things missing:
//   - Invites
//   - Provider access (when relevant
//   - Unmanaged provider projects
//   - Project creation (we might want to do this purely through grants moving forward and make this a purely internal
//     call)
//   - Notifications for accounting (not obvious this can be done right now)

// NOTE(Dan): Locks are always acquired in this order: bucket -> project -> link -> userinfo.
//
// It is not allowed to hold two of the same lock type (e.g. two bucket locks).
//
// For example, this means that if you want to acquire a project lock then you are allowed to hold 0 or 1 bucket
// locks and _no other locks_.
//
// Another example, if you want to acquire a link lock, then you are allowed to hold 0-1 bucket locks and 0-1 project
// locks, but no other locks can be held at the time of acquisition.
//
// Not all locks need to be acquired for a change to be made. But any locks required by a function must be done in this
// order.
//
// You are not allowed to perform any read or write operation without first acquiring the lock.

var projectBuckets []internalProjectBucket

type internalProjectBucket struct {
	Mu          sync.RWMutex
	Users       map[string]*internalProjectUserInfo
	Projects    map[string]*internalProject
	InviteLinks map[string]*internalInviteLink
}

func projectBucket(id string) *internalProjectBucket {
	h := fnv.New32a()
	_, err := h.Write([]byte(id))
	if err != nil {
		panic("hash fail: " + err.Error())
	}

	return &projectBuckets[int(h.Sum32())%len(projectBuckets)]
}

type internalProjectUserInfo struct {
	Username  string
	Mu        sync.RWMutex
	Projects  map[string]util.Empty
	Groups    map[string]string // group to project
	Favorites map[string]util.Empty
}

type internalProject struct {
	Id          string
	Mu          sync.RWMutex
	Project     fndapi.Project
	InviteLinks map[string]util.Empty
}

type internalInviteLink struct {
	Project string
	Mu      sync.RWMutex
	Link    fndapi.ProjectInviteLink
}

func initProjects() {
	projectBuckets = make([]internalProjectBucket, runtime.NumCPU())
	for i := range projectBuckets {
		projectBuckets[i] = internalProjectBucket{
			Mu:          sync.RWMutex{},
			Users:       make(map[string]*internalProjectUserInfo),
			Projects:    make(map[string]*internalProject),
			InviteLinks: make(map[string]*internalInviteLink),
		}
	}

	fndapi.ProjectRetrieve.Handler(func(info rpc.RequestInfo, request fndapi.ProjectRetrieveRequest) (fndapi.Project, *util.HttpError) {
		return ProjectRetrieve(info.Actor, request.Id, request.ProjectFlags, fndapi.ProjectRoleUser)
	})

	fndapi.ProjectRetrieveMetadata.Handler(func(info rpc.RequestInfo, request fndapi.FindByStringId) (fndapi.ProjectMetadata, *util.HttpError) {
		return ProjectRetrieveMetadata(request.Id)
	})

	fndapi.ProjectBrowse.Handler(func(info rpc.RequestInfo, request fndapi.ProjectBrowseRequest) (fndapi.PageV2[fndapi.Project], *util.HttpError) {
		return ProjectBrowse(info.Actor, request)
	})

	fndapi.ProjectRename.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.ProjectRenameRequest]) (util.Empty, *util.HttpError) {
		for _, reqItem := range request.Items {
			err := ProjectRename(info.Actor, reqItem)
			if err != nil {
				return util.Empty{}, err
			}
		}
		return util.Empty{}, nil
	})

	fndapi.ProjectRenamable.Handler(func(info rpc.RequestInfo, request fndapi.ProjectRenamableRequest) (fndapi.ProjectRenamableResponse, *util.HttpError) {
		allowed := ProjectRenamingAllowed(info.Actor, request.ProjectId)
		return fndapi.ProjectRenamableResponse{Allowed: allowed}, nil
	})

	fndapi.ProjectToggleFavorite.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (util.Empty, *util.HttpError) {
		for _, reqItem := range request.Items {
			err := ProjectToggleFavorite(info.Actor, reqItem.Id)
			if err != nil {
				return util.Empty{}, err
			}
		}
		return util.Empty{}, nil
	})

	fndapi.ProjectRemoveMember.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.ProjectRemoveMemberRequest]) (util.Empty, *util.HttpError) {
		if !info.Actor.Project.Present {
			return util.Empty{}, util.HttpErr(http.StatusBadRequest, "This request requires an active project")
		}

		for _, reqItem := range request.Items {
			err := projectRemoveMember(info.Actor, info.Actor.Project.Value, reqItem.Username, nil)
			if err != nil {
				return util.Empty{}, err
			}
		}

		return util.Empty{}, nil
	})

	fndapi.ProjectUpdateSettings.Handler(func(info rpc.RequestInfo, request fndapi.ProjectSettings) (util.Empty, *util.HttpError) {
		return util.Empty{}, ProjectUpdateSettings(info.Actor, request)
	})

	fndapi.ProjectMemberChangeRole.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.ProjectMemberChangeRoleRequest]) (util.Empty, *util.HttpError) {
		for _, reqItem := range request.Items {
			err := ProjectChangeRole(info.Actor, reqItem)
			if err != nil {
				return util.Empty{}, err
			}
		}
		return util.Empty{}, nil
	})

	fndapi.ProjectInternalCreate.Handler(func(info rpc.RequestInfo, request fndapi.ProjectInternalCreateRequest) (fndapi.FindByStringId, *util.HttpError) {
		id, err := ProjectCreateInternal(info.Actor, request)
		if err != nil {
			return fndapi.FindByStringId{}, err
		} else {
			return fndapi.FindByStringId{Id: id}, nil
		}
	})

	fndapi.ProjectCreateGroup.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.ProjectGroupSpecification]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		var resp fndapi.BulkResponse[fndapi.FindByStringId]
		for _, reqItem := range request.Items {
			id, err := ProjectCreateGroup(info.Actor, reqItem)
			if err != nil {
				return resp, err
			} else {
				resp.Responses = append(resp.Responses, fndapi.FindByStringId{Id: id})
			}
		}
		return resp, nil
	})

	fndapi.ProjectRenameGroup.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.ProjectRenameGroupRequest]) (util.Empty, *util.HttpError) {
		for _, reqItem := range request.Items {
			err := ProjectRenameGroup(info.Actor, reqItem.Group, reqItem.NewTitle)
			if err != nil {
				return util.Empty{}, err
			}
		}
		return util.Empty{}, nil
	})

	fndapi.ProjectDeleteGroup.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (util.Empty, *util.HttpError) {
		for _, reqItem := range request.Items {
			err := ProjectDeleteGroup(info.Actor, reqItem.Id)
			if err != nil {
				return util.Empty{}, err
			}
		}
		return util.Empty{}, nil
	})

	fndapi.ProjectRetrieveGroup.Handler(func(info rpc.RequestInfo, request fndapi.ProjectRetrieveGroupRequest) (fndapi.ProjectGroup, *util.HttpError) {
		group, err := ProjectRetrieveGroup(info.Actor, request.Id)
		if err != nil {
			return group, err
		} else {
			if !request.IncludeMembers {
				group.Status.Members = make([]string, 0)
			}

			return group, err
		}
	})

	fndapi.ProjectCreateGroupMember.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.ProjectGroupMember]) (util.Empty, *util.HttpError) {
		for _, reqItem := range request.Items {
			err := ProjectCreateGroupMember(info.Actor, reqItem.Group, reqItem.Username)
			if err != nil {
				return util.Empty{}, err
			}
		}
		return util.Empty{}, nil
	})

	fndapi.ProjectDeleteGroupMember.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.ProjectGroupMember]) (util.Empty, *util.HttpError) {
		for _, reqItem := range request.Items {
			err := ProjectDeleteGroupMember(info.Actor, reqItem.Group, reqItem.Username)
			if err != nil {
				return util.Empty{}, err
			}
		}
		return util.Empty{}, nil
	})

	fndapi.ProjectCreateInviteLink.Handler(func(info rpc.RequestInfo, request util.Empty) (fndapi.ProjectInviteLink, *util.HttpError) {
		return ProjectCreateInviteLink(info.Actor)
	})

	fndapi.ProjectUpdateInviteLink.Handler(func(info rpc.RequestInfo, request fndapi.ProjectUpdateInviteLinkRequest) (util.Empty, *util.HttpError) {
		return util.Empty{}, ProjectUpdateInviteLink(info.Actor, request)
	})

	fndapi.ProjectBrowseInviteLinks.Handler(func(info rpc.RequestInfo, request fndapi.ProjectBrowseInviteLinksRequest) (fndapi.PageV2[fndapi.ProjectInviteLink], *util.HttpError) {
		return ProjectBrowseInviteLinks(info.Actor, request)
	})

	fndapi.ProjectDeleteInviteLink.Handler(func(info rpc.RequestInfo, request fndapi.FindByInviteLink) (util.Empty, *util.HttpError) {
		return util.Empty{}, ProjectDeleteInviteLink(info.Actor, request.Token)
	})

	fndapi.ProjectRetrieveAllUsersGroup.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByProjectId]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		var result fndapi.BulkResponse[fndapi.FindByStringId]
		for _, reqItem := range request.Items {
			project, err := ProjectRetrieve(info.Actor, reqItem.Project, projectFlagsAll, fndapi.ProjectRoleUser)
			if err != nil {
				return result, err
			} else {
				found := false
				for _, g := range project.Status.Groups {
					if g.Specification.Title == ProjectGroupAllUsers {
						result.Responses = append(result.Responses, fndapi.FindByStringId{Id: g.Id})
						found = true
						break
					}
				}

				if !found {
					// TODO Make sure that we do not have any remaining projects like this
					return result, util.HttpErr(http.StatusInternalServerError, "Could not find 'all users' group")
				}
			}
		}
		return result, nil
	})

	fndapi.ProjectRetrieveInviteLink.Handler(func(info rpc.RequestInfo, request fndapi.FindByInviteLink) (fndapi.ProjectInviteLinkInfo, *util.HttpError) {
		linkInfo, _, err := ProjectRetrieveInviteLink(info.Actor, request.Token)
		return linkInfo, err
	})

	fndapi.ProjectAcceptInviteLink.Handler(func(info rpc.RequestInfo, request fndapi.FindByInviteLink) (fndapi.ProjectAcceptInviteLinkResponse, *util.HttpError) {
		linkInfo, err := ProjectAcceptInviteLink(info.Actor, request.Token)
		return fndapi.ProjectAcceptInviteLinkResponse{Project: linkInfo.Project.Id}, err
	})

	fndapi.ProjectBrowseInvites.Handler(func(info rpc.RequestInfo, request util.Empty) (fndapi.PageV2[util.Empty], *util.HttpError) {
		return fndapi.PageV2[util.Empty]{Items: make([]util.Empty, 0)}, nil
	})
}

var projectFlagsAll = fndapi.ProjectFlags{
	IncludeMembers:  true,
	IncludeGroups:   true,
	IncludeFavorite: true,
	IncludeArchived: true,
	IncludeSettings: true,
	IncludePath:     true,
}

func ProjectRetrieveMetadata(id string) (fndapi.ProjectMetadata, *util.HttpError) {
	p, ok := projectRetrieveInternal(id)
	if ok {
		p.Mu.RLock()

		piUsername := "_ucloud"
		for _, user := range p.Project.Status.Members {
			if user.Role == fndapi.ProjectRolePI {
				piUsername = user.Username
				break
			}
		}

		result := fndapi.ProjectMetadata{
			Id:         p.Id,
			Title:      p.Project.Specification.Title,
			PiUsername: piUsername,
		}

		p.Mu.RUnlock()

		return result, nil
	}
	return fndapi.ProjectMetadata{}, util.HttpErr(http.StatusNotFound, "unknown project")
}

func ProjectRetrieve(
	actor rpc.Actor,
	id string,
	flags fndapi.ProjectFlags,
	roleRequirement fndapi.ProjectRole,
) (fndapi.Project, *util.HttpError) {
	res, _, err := projectRetrieve(actor, id, flags, roleRequirement)
	return res, err
}

func projectRetrieve(
	actor rpc.Actor,
	id string,
	flags fndapi.ProjectFlags,
	roleRequirement fndapi.ProjectRole,
) (fndapi.Project, *internalProject, *util.HttpError) {
	if id == "" {
		return fndapi.Project{}, nil, util.HttpErr(http.StatusNotFound, "This action only works in a non-personal project")
	}

	// TODO providers need to take a special path

	var isMember bool
	var isFavorite bool

	isSystem := actor.Username == rpc.ActorSystem.Username
	if !isSystem {
		userInfo := projectRetrieveUserInfo(actor.Username)
		userInfo.Mu.RLock()
		_, isMember = userInfo.Projects[id]
		_, isFavorite = userInfo.Favorites[id]
		userInfo.Mu.RUnlock()

		if !isMember {
			return fndapi.Project{}, nil, util.HttpErr(http.StatusNotFound, "Unknown project or permission error")
		}
	}

	p, ok := projectRetrieveInternal(id)
	if !ok {
		return fndapi.Project{}, nil, util.HttpErr(http.StatusNotFound, "Unknown project or permission error")
	}

	p.Mu.RLock()
	result := p.Project
	p.Mu.RUnlock()

	result, resultFlags := projectProcessFlags(result, actor.Username, isFavorite, flags)
	if resultFlags&projectResultIsMember == 0 && !isSystem {
		// NOTE(Dan): An unlikely and mostly harmless race-condition means that the user info might momentarily
		// list a project for which we are no longer a member. We detect it regardless, but it probably wouldn't
		// be a hard requirement.
		return fndapi.Project{}, nil, util.HttpErr(http.StatusNotFound, "Unknown project or permission error")
	}

	if !result.Status.MyRole.Satisfies(roleRequirement) && !isSystem {
		return fndapi.Project{}, nil, util.HttpErr(http.StatusForbidden, "You do not have permissions for this action")
	}

	return result, p, nil
}

type ProjectClaimsInfo struct {
	Membership       rpc.ProjectMembership
	Groups           rpc.GroupMembership
	ProviderProjects rpc.ProviderProjects
}

func ProjectRetrieveClaimsInfo(username string) ProjectClaimsInfo {
	// NOTE(Dan): Be very careful that none of these functions accidentally rely on actor/principal information as
	// this could cause an infinite loop. This function is used as part of building the actor/principal.

	result := ProjectClaimsInfo{
		Membership: make(rpc.ProjectMembership),
		Groups:     make(rpc.GroupMembership),
	}

	userInfo := projectRetrieveUserInfo(username)
	userInfo.Mu.RLock()
	for groupId, projectId := range userInfo.Groups {
		result.Groups[rpc.GroupId(groupId)] = rpc.ProjectId(projectId)
	}
	for projectId, _ := range userInfo.Projects {
		result.Membership[rpc.ProjectId(projectId)] = rpc.ProjectRoleUser
	}
	userInfo.Mu.RUnlock()

	for projectId, _ := range result.Membership {
		project, ok := projectRetrieveInternal(string(projectId))
		if ok {
			role := util.OptNone[rpc.ProjectRole]()
			project.Mu.RLock()
			for _, member := range project.Project.Status.Members {
				if member.Username == username {
					role.Set(rpc.ProjectRole(member.Role))
				}
			}
			project.Mu.RUnlock()

			if role.Present {
				result.Membership[projectId] = role.Value
			} else {
				delete(result.Membership, projectId)
			}
		} else {
			delete(result.Membership, projectId)
		}
	}

	result.ProviderProjects = db.NewTx(func(tx *db.Transaction) rpc.ProviderProjects {
		// TODO(Dan): Quite annoying that we have to go into another deployment's implementation details on top of
		//  doing a DB transaction here when it is otherwise not needed. On the bright side, this function only runs
		//  every 5-10 minutes for a given user.
		projects := rpc.ProviderProjects{}

		rows := db.Select[struct {
			ProviderId string
			Project    string
		}](
			tx,
			`
				select p.unique_name as provider_id, r.project
				from
					project.project_members pm
					join provider.resource r on pm.project_id = r.project
					join provider.providers p on r.id = p.resource
				where
					pm.username = :username
		    `,
			db.Params{
				"username": username,
			},
		)

		for _, row := range rows {
			projects[rpc.ProviderId(row.ProviderId)] = rpc.ProjectId(row.Project)
		}

		return projects
	})

	return result
}

func ProjectBrowse(actor rpc.Actor, request fndapi.ProjectBrowseRequest) (fndapi.PageV2[fndapi.Project], *util.HttpError) {
	var projectIds []string
	favorites := map[string]bool{}

	// TODO providers need to take a special path

	userInfo := projectRetrieveUserInfo(actor.Username)
	userInfo.Mu.RLock()
	for p := range userInfo.Projects {
		projectIds = append(projectIds, p)
	}
	for f := range userInfo.Favorites {
		favorites[f] = true
	}
	userInfo.Mu.RUnlock()

	var projects []fndapi.Project

	for _, projectId := range projectIds {
		p, ok := projectRetrieveInternal(projectId)
		if ok {
			p.Mu.RLock()
			result := p.Project
			p.Mu.RUnlock()

			result, resultFlags := projectProcessFlags(result, actor.Username, favorites[p.Id], request.ProjectFlags)

			isMember := resultFlags&projectResultIsMember != 0
			isWantedByFilter := resultFlags&projectResultNotWantedByFilter == 0

			if isMember && isWantedByFilter {
				projects = append(projects, result)
			}
		}
	}

	sortBy := request.SortBy.Value.Normalize()
	direction := request.SortDirection.Value.Normalize()

	slices.SortFunc(projects, func(a, b fndapi.Project) int {
		cmpResult := 0

		if sortBy == fndapi.ProjectSortByFavorite {
			if a.Status.IsFavorite && !b.Status.IsFavorite {
				cmpResult = -1
			} else if !a.Status.IsFavorite && b.Status.IsFavorite {
				cmpResult = 1
			}
		}

		if cmpResult == 0 {
			if sortBy == fndapi.ProjectSortByParent {
				cmpResult = strings.Compare(a.Specification.Parent.GetOrDefault(""), b.Specification.Parent.GetOrDefault(""))
			}

			if cmpResult == 0 {
				cmpResult = strings.Compare(a.Specification.Title, b.Specification.Title)
			}

			if cmpResult == 0 {
				cmpResult = strings.Compare(a.Id, b.Id)
			}
		}

		if direction == fndapi.ProjectSortDescending {
			cmpResult *= -1
		}

		return cmpResult
	})

	result := fndapi.PageV2[fndapi.Project]{
		Items: projects,
	}
	result.Prepare(request.ItemsPerPage, request.Next, func(item fndapi.Project) string {
		return item.Id
	})

	return result, nil
}

func ProjectRename(actor rpc.Actor, request fndapi.ProjectRenameRequest) *util.HttpError {
	_, p, err := projectRetrieve(actor, request.Id, projectFlagsAll, fndapi.ProjectRoleAdmin)
	if err != nil {
		return err
	}

	p.Mu.Lock()
	db.NewTx0(func(tx *db.Transaction) {
		renameRow, _ := db.Get[struct{ CanRename bool }](
			tx,
			`
				select coalesce(parent.subprojects_renameable, true) as can_rename
				from
					project.projects p
					left join project.projects parent on p.parent = parent.id
				where
				    p.id = :id
		    `,
			db.Params{
				"id": request.Id,
			},
		)

		if !renameRow.CanRename {
			err = util.HttpErr(http.StatusForbidden, "This project is not allowed to rename itself")
		} else {
			db.Exec(
				tx,
				`
					update project.projects
					set title = :new_title
					where id = :id
				`,
				db.Params{
					"id":        request.Id,
					"new_title": request.NewTitle,
				},
			)

			if tx.ConsumeError() != nil {
				err = util.HttpErr(http.StatusForbidden, "This name is already in use, please use a different one.")
			}
		}
	})

	if err == nil {
		p.Project.Specification.Title = request.NewTitle
	}
	p.Mu.Unlock()

	return err
}

func ProjectRenamingAllowed(actor rpc.Actor, projectId string) bool {
	child, _, err := projectRetrieve(actor, projectId, projectFlagsAll, fndapi.ProjectRoleUser)
	if err != nil {
		return false
	}

	if !child.Specification.Parent.Present {
		return true
	} else {
		parent, _, err := projectRetrieve(rpc.ActorSystem, child.Specification.Parent.GetOrDefault(""),
			projectFlagsAll, fndapi.ProjectRoleUser)
		if err != nil {
			return false
		}

		return parent.Status.Settings.SubProjects.AllowRenaming
	}
}

func ProjectCreateInternal(actor rpc.Actor, req fndapi.ProjectInternalCreateRequest) (string, *util.HttpError) {
	_, ok := rpc.LookupActor(req.PiUsername)
	if !ok {
		return "", util.HttpErr(http.StatusNotFound, "unknown user")
	}

	resultId := util.UUid()
	suffix := ""

	for {
		id, ok := db.NewTx2(func(tx *db.Transaction) (string, bool) {
			row, ok := db.Get[struct{ Id string }](
				tx,
				`
					select id
					from project.projects where backend_id = :backend
			    `,
				db.Params{
					"backend": req.BackendId,
				},
			)

			if ok {
				return row.Id, true
			}

			db.Exec(
				tx,
				`
					insert into project.projects(id, created_at, modified_at, title, archived, parent, dmp, 
						subprojects_renameable, can_consume_resources, provider_project_for, backend_id) 
					values (:id, now(), now(), :title, false, null, null, false, true, null, :backend_id)
				`,
				db.Params{
					"id":         resultId,
					"title":      req.Title + suffix,
					"backend_id": req.BackendId,
				},
			)

			if tx.ConsumeError() != nil {
				suffix = fmt.Sprintf("-%d", rand.Intn(9000)+1000)
				return "", false
			} else {
				db.Exec(
					tx,
					`
						insert into project.project_members(created_at, modified_at, role, username, project_id) 
						values (now(), now(), 'PI', :username, :project)
				    `,
					db.Params{
						"username": req.PiUsername,
						"project":  resultId,
					},
				)
			}

			return resultId, true
		})

		if ok {
			b := projectBucket(req.PiUsername)
			b.Mu.Lock()
			delete(b.Users, req.PiUsername) // invalidate cache
			b.Mu.Unlock()

			return id, nil
		}
	}
}

func ProjectToggleFavorite(actor rpc.Actor, projectId string) *util.HttpError {
	var err *util.HttpError
	uinfo := projectRetrieveUserInfo(actor.Username)
	uinfo.Mu.Lock()
	if _, ok := uinfo.Projects[projectId]; !ok {
		err = util.HttpErr(http.StatusForbidden, "You are not a member of this project!")
	} else {
		wasFavorite := false
		db.NewTx0(func(tx *db.Transaction) {
			_, wasFavorite = db.Get[struct{ ProjectId string }](
				tx,
				`
					delete from project.project_favorite
					where username = :username and project_id = :project
					returning project_id
			    `,
				db.Params{
					"username": actor.Username,
					"project":  projectId,
				},
			)

			if !wasFavorite {
				db.Exec(
					tx,
					`
						insert into project.project_favorite(project_id, username) 
						values (:project, :username)
					`,
					db.Params{
						"username": actor.Username,
						"project":  projectId,
					},
				)
			}
		})

		if wasFavorite {
			delete(uinfo.Favorites, projectId)
		} else {
			uinfo.Favorites[projectId] = util.Empty{}
		}
	}
	uinfo.Mu.Unlock()
	return err
}

func ProjectUpdateSettings(actor rpc.Actor, settings fndapi.ProjectSettings) *util.HttpError {
	_, iproject, err := projectRetrieve(actor, actor.Project.Value, projectFlagsAll, fndapi.ProjectRoleAdmin)
	if err != nil {
		return err
	}

	iproject.Mu.Lock()
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update project.projects
				set subprojects_renameable = :renamable
				where id = :project
		    `,
			db.Params{
				"renamable": settings.SubProjects.AllowRenaming,
				"project":   actor.Project.Value,
			},
		)
	})
	iproject.Project.Status.Settings.SubProjects.AllowRenaming = settings.SubProjects.AllowRenaming
	iproject.Mu.Unlock()
	return nil
}

func ProjectChangeRole(actor rpc.Actor, request fndapi.ProjectMemberChangeRoleRequest) *util.HttpError {
	if actor.Username == request.Username {
		return util.HttpErr(http.StatusForbidden, "You cannot change your own role")
	}

	piTransfer := false
	requiredRole := fndapi.ProjectRoleAdmin
	if request.Role == fndapi.ProjectRolePI {
		requiredRole = fndapi.ProjectRolePI
		piTransfer = true
	}

	_, iproject, err := projectRetrieve(actor, actor.Project.Value, projectFlagsAll, requiredRole)
	if err != nil {
		return err
	}

	iproject.Mu.Lock()
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update project.project_members
				set role = :new_role
				where
					username = :username
					and project_id = :project
		    `,
			db.Params{
				"new_role": string(request.Role),
				"username": request.Username,
				"project":  actor.Project.Value,
			},
		)

		if piTransfer {
			db.Exec(
				tx,
				`
				update project.project_members
				set role = :new_role
				where
					username = :username
					and project_id = :project
		    `,
				db.Params{
					"new_role": string(fndapi.ProjectRoleAdmin),
					"username": actor.Username,
					"project":  actor.Project.Value,
				},
			)
		}
	})

	pStatus := &iproject.Project.Status
	for i := 0; i < len(pStatus.Members); i++ {
		member := &pStatus.Members[i]
		if member.Username == request.Username {
			member.Role = request.Role
		} else if piTransfer && member.Username == actor.Username {
			member.Role = fndapi.ProjectRoleAdmin
		}
	}

	iproject.Mu.Unlock()
	return nil
}

func ProjectCreateGroup(actor rpc.Actor, spec fndapi.ProjectGroupSpecification) (string, *util.HttpError) {
	_, iproject, err := projectRetrieve(actor, spec.Project, projectFlagsAll, fndapi.ProjectRoleAdmin)
	if err != nil {
		return "", err
	}

	iproject.Mu.Lock()
	groupId := util.UUid()
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into project.groups(title, project, id) 
				values (:title, :project, :group_id)
		    `,
			db.Params{
				"title":    spec.Title,
				"project":  spec.Project,
				"group_id": groupId,
			},
		)

		if tx.ConsumeError() != nil {
			err = util.HttpErr(http.StatusBadRequest, "A group with this name already exists")
		}
	})
	if err == nil {
		pStatus := &iproject.Project.Status
		pStatus.Groups = append(pStatus.Groups, fndapi.ProjectGroup{
			Id:            groupId,
			Specification: spec,
			Status: fndapi.ProjectGroupStatus{
				Members: make([]string, 0),
			},
		})
	}
	iproject.Mu.Unlock()

	if err != nil {
		return "", err
	} else {
		return groupId, nil
	}
}

func ProjectRenameGroup(actor rpc.Actor, id string, newTitle string) *util.HttpError {
	_, iproject, err := projectRetrieve(actor, actor.Project.Value, projectFlagsAll, fndapi.ProjectRoleAdmin)
	if err != nil {
		return err
	}

	iproject.Mu.Lock()
	idx := slices.IndexFunc(iproject.Project.Status.Groups, func(group fndapi.ProjectGroup) bool {
		return group.Id == id
	})

	if idx == -1 {
		err = util.HttpErr(http.StatusNotFound, "This group does not exist in this project")
	} else {
		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					update project.groups
					set title = :new_title
					where id = :id
			    `,
				db.Params{
					"id":        id,
					"new_title": newTitle,
				},
			)

			if tx.ConsumeError() != nil {
				err = util.HttpErr(http.StatusBadRequest, "A group with this name already exists!")
			}
		})

		if err == nil {
			iproject.Project.Status.Groups[idx].Specification.Title = newTitle
		}
	}

	iproject.Mu.Unlock()
	return err
}

func ProjectDeleteGroup(actor rpc.Actor, id string) *util.HttpError {
	_, iproject, err := projectRetrieve(actor, actor.Project.Value, projectFlagsAll, fndapi.ProjectRoleAdmin)
	if err != nil {
		return err
	}

	iproject.Mu.Lock()
	idx := slices.IndexFunc(iproject.Project.Status.Groups, func(group fndapi.ProjectGroup) bool {
		return group.Id == id
	})

	if idx == -1 {
		err = util.HttpErr(http.StatusNotFound, "This group does not exist in this project")
	} else if iproject.Project.Status.Groups[idx].Specification.Title == ProjectGroupAllUsers {
		err = util.HttpErr(http.StatusForbidden, "You cannot delete this group")
	} else {
		var groupMembersRemoved []struct{ Username string }
		var linksInvalidated []struct{ LinkToken string }

		db.NewTx0(func(tx *db.Transaction) {
			groupMembersRemoved = db.Select[struct{ Username string }](
				tx,
				`
					delete from project.group_members gm
					where gm.group_id = :group_id
					returning gm.username
			    `,
				db.Params{
					"group_id": id,
				},
			)

			linksInvalidated = db.Select[struct{ LinkToken string }](
				tx,
				`
					delete from project.invite_link_group_assignments
					where group_id = :group_id
					returning link_token
			    `,
				db.Params{
					"group_id": id,
				},
			)

			db.Exec(
				tx,
				`
					delete from project.groups
					where id = :group_id
			    `,
				db.Params{
					"group_id": id,
				},
			)
		})

		iproject.Project.Status.Groups = util.RemoveAtIndex(iproject.Project.Status.Groups, idx)
		for _, member := range groupMembersRemoved {
			uinfo := projectRetrieveUserInfo(member.Username)
			uinfo.Mu.Lock()
			delete(uinfo.Groups, id)
			uinfo.Mu.Unlock()
		}

		for _, token := range linksInvalidated {
			link, ok := projectRetrieveLink(token.LinkToken)
			if ok {
				link.Mu.Lock()
				link.Link.GroupAssignment = util.RemoveElementFunc(link.Link.GroupAssignment, func(element string) bool {
					return element == id
				})
				link.Mu.Unlock()
			}
		}
	}
	iproject.Mu.Unlock()
	return err
}

func ProjectRetrieveGroup(actor rpc.Actor, groupId string) (fndapi.ProjectGroup, *util.HttpError) {
	var result fndapi.ProjectGroup
	_, iproject, err := projectRetrieve(actor, actor.Project.Value, projectFlagsAll, fndapi.ProjectRoleUser)
	if err != nil {
		return result, err
	}

	iproject.Mu.RLock()
	idx := slices.IndexFunc(iproject.Project.Status.Groups, func(group fndapi.ProjectGroup) bool {
		return group.Id == groupId
	})
	if idx == -1 {
		err = util.HttpErr(http.StatusNotFound, "Unknown group")
	} else {
		result = iproject.Project.Status.Groups[idx]
	}
	iproject.Mu.RUnlock()
	return result, err
}

func ProjectCreateGroupMember(actor rpc.Actor, groupId string, memberToAdd string) *util.HttpError {
	_, iproject, err := projectRetrieve(actor, actor.Project.Value, projectFlagsAll, fndapi.ProjectRoleAdmin)
	if err != nil {
		return err
	}

	iproject.Mu.Lock()
	idx := slices.IndexFunc(iproject.Project.Status.Groups, func(group fndapi.ProjectGroup) bool {
		return group.Id == groupId
	})
	isMember := slices.ContainsFunc(iproject.Project.Status.Members, func(member fndapi.ProjectMember) bool {
		return member.Username == memberToAdd
	})
	if idx == -1 {
		err = util.HttpErr(http.StatusNotFound, "Unknown group")
	} else if !isMember {
		err = util.HttpErr(http.StatusBadRequest, "This user is not a member of the project. Try reloading the page.")
	} else {
		group := &iproject.Project.Status.Groups[idx]
		isInGroup := slices.Contains(group.Status.Members, memberToAdd)
		if !isInGroup {
			db.NewTx0(func(tx *db.Transaction) {
				db.Exec(
					tx,
					`
						insert into project.group_members(username, group_id) 
						values (:username, :group)
				    `,
					db.Params{
						"username": memberToAdd,
						"group":    groupId,
					},
				)
			})

			group.Status.Members = append(group.Status.Members, memberToAdd)
		}
	}
	iproject.Mu.Unlock()
	return err
}

func ProjectDeleteGroupMember(actor rpc.Actor, groupId string, memberToRemove string) *util.HttpError {
	_, iproject, err := projectRetrieve(actor, actor.Project.Value, projectFlagsAll, fndapi.ProjectRoleAdmin)
	if err != nil {
		return err
	}

	iproject.Mu.Lock()
	idx := slices.IndexFunc(iproject.Project.Status.Groups, func(group fndapi.ProjectGroup) bool {
		return group.Id == groupId
	})
	isMember := slices.ContainsFunc(iproject.Project.Status.Members, func(member fndapi.ProjectMember) bool {
		return member.Username == memberToRemove
	})
	if idx == -1 {
		err = util.HttpErr(http.StatusNotFound, "Unknown group")
	} else if !isMember {
		err = util.HttpErr(http.StatusBadRequest, "This user is not a member of the project. Try reloading the page.")
	} else {
		group := &iproject.Project.Status.Groups[idx]
		memberIdx := slices.Index(group.Status.Members, memberToRemove)
		if memberIdx != -1 {
			db.NewTx0(func(tx *db.Transaction) {
				db.Exec(
					tx,
					`
						delete from project.group_members
						where username = :username and group_id = :group
				    `,
					db.Params{
						"username": memberToRemove,
						"group":    groupId,
					},
				)
			})

			group.Status.Members = util.RemoveAtIndex(group.Status.Members, memberIdx)
		}
	}
	iproject.Mu.Unlock()
	return err
}

func ProjectCreateInviteLink(actor rpc.Actor) (fndapi.ProjectInviteLink, *util.HttpError) {
	projectId := actor.Project.GetOrDefault("")
	_, p, err := projectRetrieve(actor, projectId, projectFlagsAll, fndapi.ProjectRoleAdmin)
	if err != nil {
		return fndapi.ProjectInviteLink{}, err
	}

	token := util.UUid() // required by db schema
	bucket := projectBucket(token)

	bucket.Mu.Lock()
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into project.invite_links(token, project_id, expires) 
				values (:token, :project_id, now() + cast('30 days' as interval))
		    `,
			db.Params{
				"token":      token,
				"project_id": projectId,
			},
		)
	})

	result := fndapi.ProjectInviteLink{
		Token:          token,
		Expires:        fndapi.Timestamp(time.Now().Add(30 * 24 * time.Hour)),
		RoleAssignment: fndapi.ProjectRoleUser,
	}

	bucket.InviteLinks[token] = &internalInviteLink{
		Project: projectId,
		Link:    result,
	}
	bucket.Mu.Unlock()

	p.Mu.Lock()
	p.InviteLinks[token] = util.Empty{}
	p.Mu.Unlock()

	return result, nil
}

func ProjectUpdateInviteLink(actor rpc.Actor, request fndapi.ProjectUpdateInviteLinkRequest) *util.HttpError {
	token := request.Token
	link, ok := projectRetrieveLink(token)
	if !ok {
		return util.HttpErr(http.StatusNotFound, "Unknown link or permission error")
	}

	_, _, err := projectRetrieve(actor, link.Project, projectFlagsAll, fndapi.ProjectRoleAdmin)
	if err != nil {
		return util.HttpErr(http.StatusNotFound, "Unknown link or permission error")
	}

	link.Mu.Lock()
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from project.invite_link_group_assignments
				where link_token = :token
		    `,
			db.Params{
				"token": token,
			},
		)

		db.Exec(
			tx,
			`
				insert into project.invite_link_group_assignments (link_token, group_id) 
				values (:token, unnest(cast(:groups as text[])))
		    `,
			db.Params{
				"token":  token,
				"groups": request.Groups,
			},
		)

		if tx.ConsumeError() != nil {
			err = util.HttpErr(http.StatusForbidden, "One or more of the referenced groups do not exist. Try reloading the page.")
		}

		db.Exec(
			tx,
			`
				update project.invite_links
				set role_assignment = :assignment
				where token = :token
		    `,
			db.Params{
				"token":      token,
				"assignment": request.Role.Normalize(),
			},
		)
	})
	if err == nil {
		link.Link.GroupAssignment = request.Groups
		link.Link.RoleAssignment = request.Role.Normalize()
	}
	link.Mu.Unlock()
	return err
}

func ProjectDeleteInviteLink(actor rpc.Actor, token string) *util.HttpError {
	linkBucket := projectBucket(token)
	link, ok := projectRetrieveLink(token)
	if !ok {
		return util.HttpErr(http.StatusNotFound, "Unknown link or permission error")
	}

	projectId := link.Project
	_, p, err := projectRetrieve(actor, projectId, projectFlagsAll, fndapi.ProjectRoleAdmin)
	if err != nil {
		return util.HttpErr(http.StatusNotFound, "Unknown link or permission error")
	}

	linkBucket.Mu.Lock()
	link.Mu.Lock()
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from project.invite_link_group_assignments
				where link_token = :token
		    `,
			db.Params{"token": token},
		)

		db.Exec(
			tx,
			`
				delete from project.invite_links
				where token = :token
		    `,
			db.Params{"token": token},
		)
	})

	delete(linkBucket.InviteLinks, token)

	link.Mu.Unlock()
	linkBucket.Mu.Unlock()

	p.Mu.Lock()
	delete(p.InviteLinks, token)
	p.Mu.Unlock()
	return nil
}

func ProjectBrowseInviteLinks(
	actor rpc.Actor,
	request fndapi.ProjectBrowseInviteLinksRequest,
) (fndapi.PageV2[fndapi.ProjectInviteLink], *util.HttpError) {
	var result fndapi.PageV2[fndapi.ProjectInviteLink]
	_, p, err := projectRetrieve(actor, actor.Project.GetOrDefault(""), projectFlagsAll, fndapi.ProjectRoleAdmin)

	if err != nil {
		return result, err
	}

	var linkTokens []string

	p.Mu.RLock()
	for tok := range p.InviteLinks {
		linkTokens = append(linkTokens, tok)
	}
	p.Mu.RUnlock()

	now := time.Now()
	for _, id := range linkTokens {
		link, ok := projectRetrieveLink(id)
		if ok && link.Link.Expires.Time().After(now) {
			link.Mu.RLock()
			result.Items = append(result.Items, link.Link)
			link.Mu.RUnlock()
		}
	}

	slices.SortFunc(result.Items, func(a, b fndapi.ProjectInviteLink) int {
		if a.Expires.Time().Before(b.Expires.Time()) {
			return -1
		} else if a.Expires.Time().After(b.Expires.Time()) {
			return 1
		}

		return strings.Compare(a.Token, b.Token)
	})

	result.Prepare(request.ItemsPerPage, request.Next, func(item fndapi.ProjectInviteLink) string {
		return item.Token
	})

	return result, nil
}

func ProjectRetrieveInviteLink(actor rpc.Actor, token string) (fndapi.ProjectInviteLinkInfo, fndapi.ProjectInviteLink, *util.HttpError) {
	ilink, ok := projectRetrieveLink(token)
	if !ok {
		return fndapi.ProjectInviteLinkInfo{}, fndapi.ProjectInviteLink{}, util.HttpErr(http.StatusNotFound, "Expired or invalid link")
	}

	ilink.Mu.RLock()
	link := ilink.Link
	projectId := ilink.Project
	ilink.Mu.RUnlock()

	if time.Now().After(link.Expires.Time()) {
		return fndapi.ProjectInviteLinkInfo{}, fndapi.ProjectInviteLink{}, util.HttpErr(http.StatusNotFound, "Expired or invalid link")
	}

	iproject, ok := projectRetrieveInternal(projectId)
	if !ok {
		return fndapi.ProjectInviteLinkInfo{}, fndapi.ProjectInviteLink{}, util.HttpErr(http.StatusNotFound, "Expired or invalid link")
	}

	iproject.Mu.RLock()
	project := iproject.Project
	iproject.Mu.RUnlock()

	project, projectFlags := projectProcessFlags(project, actor.Username, false, fndapi.ProjectFlags{})
	isMember := projectFlags&projectResultIsMember != 0

	return fndapi.ProjectInviteLinkInfo{
		Token:    token,
		IsMember: isMember,
		Project:  project,
	}, link, nil
}

func ProjectAcceptInviteLink(actor rpc.Actor, token string) (fndapi.ProjectInviteLinkInfo, *util.HttpError) {
	linkInfo, link, err := ProjectRetrieveInviteLink(actor, token)
	if err != nil {
		return fndapi.ProjectInviteLinkInfo{}, err
	}

	if linkInfo.IsMember {
		return fndapi.ProjectInviteLinkInfo{}, util.HttpErr(http.StatusBadRequest, "You are already a member of this project!")
	}

	err = projectAddUserToProjectAndGroups(linkInfo.Project.Id, actor.Username, link.RoleAssignment,
		link.GroupAssignment, nil)
	return linkInfo, err
}

func projectRetrieveLink(id string) (*internalInviteLink, bool) {
	bucket := projectBucket(id)
	bucket.Mu.RLock()
	link, ok := bucket.InviteLinks[id]
	bucket.Mu.RUnlock()

	if !ok {
		bucket.Mu.Lock()
		link, ok = db.NewTx2(func(tx *db.Transaction) (*internalInviteLink, bool) {
			row, ok := db.Get[struct {
				Token           string
				ProjectId       string
				Expires         time.Time
				RoleAssignment  string
				GroupAssignment string
			}](
				tx,
				`
					select
						l.token, l.project_id, l.expires, l.role_assignment, jsonb_agg(g.group_id) as group_assignment
					from
						project.invite_links l
						left join project.invite_link_group_assignments g on l.token = g.link_token
					where
						l.token = :token
					group by l.token, l.project_id, l.expires, l.role_assignment
				`,
				db.Params{
					"token": id,
				},
			)

			if ok {
				link = &internalInviteLink{
					Project: row.ProjectId,
					Link: fndapi.ProjectInviteLink{
						Token:          row.Token,
						Expires:        fndapi.Timestamp(row.Expires),
						RoleAssignment: fndapi.ProjectRole(row.RoleAssignment).Normalize(),
					},
				}

				_ = json.Unmarshal([]byte(row.GroupAssignment), &link.Link.GroupAssignment)
				if len(link.Link.GroupAssignment) == 1 && link.Link.GroupAssignment[0] == "" {
					link.Link.GroupAssignment = make([]string, 0)
				}
			}

			return link, ok
		})
		if ok {
			bucket.InviteLinks[id] = link
		}
		bucket.Mu.Unlock()
	}

	return link, ok
}

func projectAddUserToProjectAndGroups(
	projectId string,
	userToAdd string,
	role fndapi.ProjectRole,
	groupIds []string,
	sideEffects func(tx *db.Transaction) *util.HttpError,
) *util.HttpError {
	var err *util.HttpError
	iproject, ok := projectRetrieveInternal(projectId)
	if !ok {
		return util.HttpErr(http.StatusInternalServerError, "Could not add user to project")
	}

	uinfo := projectRetrieveUserInfo(userToAdd)

	iproject.Mu.Lock()
	uinfo.Mu.Lock()

	clonedGroupIds := append([]string(nil), groupIds...)
	for _, g := range iproject.Project.Status.Groups {
		if g.Specification.Title == ProjectGroupAllUsers {
			clonedGroupIds = append(clonedGroupIds, g.Id)
			break
		}
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into project.project_members(created_at, modified_at, role, username, project_id) 
				values (now(), now(), :role, :username, :project)
		    `,
			db.Params{
				"role":     string(role),
				"username": userToAdd,
				"project":  projectId,
			},
		)

		if tx.ConsumeError() != nil {
			err = util.HttpErr(http.StatusBadRequest, "Already a member of this project")
		}

		if len(clonedGroupIds) > 0 {
			db.Exec(
				tx,
				`
					insert into project.group_members(username, group_id) 
					values (:username, unnest(cast(:group_ids as text[])))
				`,
				db.Params{
					"username":  userToAdd,
					"group_ids": clonedGroupIds,
				},
			)
		}

		if sideEffects != nil {
			err = sideEffects(tx)
		}
	})

	if err == nil {
		pStatus := &iproject.Project.Status
		pStatus.Members = append(pStatus.Members, fndapi.ProjectMember{
			Username: userToAdd,
			Role:     role,
		})
		uinfo.Projects[projectId] = util.Empty{}

		for _, gid := range clonedGroupIds {
			for i := 0; i < len(pStatus.Groups); i++ {
				group := &pStatus.Groups[i]
				if group.Id == gid {
					group.Status.Members = append(group.Status.Members, userToAdd)
					break
				}
			}

			uinfo.Groups[gid] = projectId
		}
	}

	uinfo.Mu.Unlock()
	iproject.Mu.Unlock()
	return err
}

func projectRemoveMember(actor rpc.Actor, projectId string, memberToRemove string, sideEffects func(tx *db.Transaction) *util.HttpError) *util.HttpError {
	isLeaving := actor.Username == memberToRemove
	roleRequired := fndapi.ProjectRoleAdmin
	if isLeaving {
		roleRequired = fndapi.ProjectRoleUser
	}

	uToRemove := projectRetrieveUserInfo(memberToRemove)
	project, iproject, err := projectRetrieve(actor, projectId, projectFlagsAll, roleRequired)
	if err != nil {
		return err
	}

	if isLeaving && project.Status.MyRole == fndapi.ProjectRolePI {
		return util.HttpErr(http.StatusForbidden, "You cannot leave the project as a PI")
	}

	iproject.Mu.Lock()
	uToRemove.Mu.Lock()

	var removedFromGroups []string

	db.NewTx0(func(tx *db.Transaction) {
		groupRows := db.Select[struct{ GroupId string }](
			tx,
			`
				delete from project.group_members gm
				using project.groups g
				where
					gm.username = :username
					and gm.group_id = g.id
					and g.project = :project
				returning gm.group_id
		    `,
			db.Params{
				"username": memberToRemove,
				"project":  projectId,
			},
		)

		for _, row := range groupRows {
			removedFromGroups = append(removedFromGroups, row.GroupId)
		}

		db.Exec(
			tx,
			`
				delete from project.project_members pm
				where
					pm.username = :username
					and pm.project_id = :project
		    `,
			db.Params{
				"username": memberToRemove,
				"project":  projectId,
			},
		)

		_, hasPi := db.Get[struct{ Username string }](
			tx,
			`
				select pm.username
				from project.project_members pm
				where
					pm.role = 'PI'
					and pm.project_id = :project
		    `,
			db.Params{
				"project": projectId,
			},
		)

		if !hasPi {
			db.RequestRollback(tx)
			err = util.HttpErr(http.StatusInternalServerError, "Project has no PI after member removal")
		} else if sideEffects != nil {
			err = sideEffects(tx)
		}
	})

	if err == nil {
		pStatus := &iproject.Project.Status

		pStatus.Members = util.RemoveElementFunc(pStatus.Members, func(element fndapi.ProjectMember) bool {
			return element.Username == memberToRemove
		})

		delete(uToRemove.Projects, projectId)
		for _, gid := range removedFromGroups {
			delete(uToRemove.Groups, gid)

			for i := 0; i < len(pStatus.Groups); i++ {
				g := &pStatus.Groups[i]
				if g.Id == gid {
					g.Status.Members = util.RemoveElementFunc(g.Status.Members, func(element string) bool {
						return element == memberToRemove
					})
				}
			}
		}
	}

	uToRemove.Mu.Unlock()
	iproject.Mu.Unlock()
	return err
}

type projectProcessResult int

const (
	projectResultNotWantedByFilter projectProcessResult = 1 << iota
	projectResultIsMember
)

func projectProcessFlags(project fndapi.Project, username string, isFavorite bool, flags fndapi.ProjectFlags) (fndapi.Project, projectProcessResult) {
	result := project
	resultFlags := projectProcessResult(0)

	result.Status.IsFavorite = isFavorite

	role := fndapi.ProjectRoleUser
	for _, member := range project.Status.Members {
		if member.Username == username {
			role = member.Role
			resultFlags |= projectResultIsMember
		}
	}
	result.Status.MyRole = role

	if !flags.IncludeGroups || result.Status.Groups == nil {
		result.Status.Groups = make([]fndapi.ProjectGroup, 0)
	}

	if !flags.IncludeMembers || result.Status.Members == nil {
		result.Status.Members = make([]fndapi.ProjectMember, 0)
	}

	if !flags.IncludeSettings {
		result.Status.Settings = fndapi.ProjectSettings{}
	}

	if !flags.IncludePath {
		result.Status.Path = ""
	}

	if !flags.IncludeArchived && project.Status.Archived {
		resultFlags |= projectResultNotWantedByFilter
	}

	return result, resultFlags
}

func projectRetrieveUserInfo(username string) *internalProjectUserInfo {
	bucket := projectBucket(username)
	bucket.Mu.RLock()
	userInfo, ok := bucket.Users[username]
	bucket.Mu.RUnlock()

	if !ok {
		bucket.Mu.Lock()
		userInfo, ok = bucket.Users[username]
		if !ok {
			userInfo = db.NewTx(func(tx *db.Transaction) *internalProjectUserInfo {
				result := &internalProjectUserInfo{
					Username:  username,
					Projects:  map[string]util.Empty{},
					Groups:    map[string]string{},
					Favorites: map[string]util.Empty{},
				}

				projectRows := db.Select[struct{ ProjectId string }](
					tx,
					`
						select pm.project_id
						from project.project_members pm
						where pm.username = :username
				    `,
					db.Params{
						"username": username,
					},
				)

				groupRows := db.Select[struct {
					GroupId   string
					ProjectId string
				}](
					tx,
					`
						select gm.group_id, g.id as project_id
						from
							project.group_members gm
							join project.groups g on gm.group_id = g.id
						where gm.username = :username
				    `,
					db.Params{
						"username": username,
					},
				)

				favoriteRows := db.Select[struct{ ProjectId string }](
					tx,
					`
						select project_id
						from project.project_favorite
						where username = :username
				    `,
					db.Params{
						"username": username,
					},
				)

				for _, project := range projectRows {
					result.Projects[project.ProjectId] = util.Empty{}
				}

				for _, group := range groupRows {
					result.Groups[group.GroupId] = group.ProjectId
				}

				for _, favorite := range favoriteRows {
					result.Favorites[favorite.ProjectId] = util.Empty{}
				}

				return result
			})

			bucket.Users[username] = userInfo
		}
		bucket.Mu.Unlock()
		return userInfo
	} else {
		return userInfo
	}
}

func projectRetrieveInternal(id string) (*internalProject, bool) {
	bucket := projectBucket(id)
	bucket.Mu.RLock()
	project, ok := bucket.Projects[id]
	bucket.Mu.RUnlock()

	if !ok {
		bucket.Mu.Lock()
		project, ok = bucket.Projects[id]
		if !ok {
			project, ok = db.NewTx2(func(tx *db.Transaction) (*internalProject, bool) {
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
					return nil, false
				} else {
					result := &internalProject{
						Id: id,
						Project: fndapi.Project{
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
						},
						InviteLinks: make(map[string]util.Empty),
					}

					p := &result.Project

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

					links := db.Select[struct{ Token string }](
						tx,
						`
							select token
							from project.invite_links
							where project_id = :id
					    `,
						db.Params{
							"id": id,
						},
					)

					for _, link := range links {
						result.InviteLinks[link.Token] = util.Empty{}
					}

					return result, true
				}
			})

			if ok {
				bucket.Projects[id] = project
			}
		}
		bucket.Mu.Unlock()

		return project, ok
	} else {
		return project, ok
	}
}

const ProjectGroupAllUsers = "All users"
