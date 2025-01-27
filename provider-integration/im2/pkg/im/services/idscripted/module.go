package idscripted

import (
	"errors"
	"slices"
	"ucloud.dk/pkg/apm"
	fnd "ucloud.dk/pkg/foundation"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

func Init(config *cfg.IdentityManagementScripted) {
	onUserConnected.Script = config.OnUserConnected
	onProjectUpdated.Script = config.OnProjectUpdated

	ctrl.IdentityManagement.HandleAuthentication = func(username string) (uint32, error) {
		parsed := fnd.ParseUCloudUsername(username)

		request := createUserRequest{}
		request.UCloudUsername = username
		request.FirstName = parsed.FirstName
		request.LastName = parsed.LastName
		request.SuggestedUsername = parsed.SuggestedUsername

		resp, ok := onUserConnected.Invoke(request)
		if !ok {
			return 11400, errors.New("failed to create a new user (internal error)")
		}

		return resp.Uid, nil
	}

	ctrl.IdentityManagement.HandleProjectNotification = func(updated *ctrl.NotificationProjectUpdated) bool {
		project := &updated.Project
		comparison := &updated.ProjectComparison

		membersAddedToProject := comparison.MembersAddedToProject
		membersRemovedFromProject := comparison.MembersRemovedFromProject

		req := syncProjectRequest{}
		req.UCloudProjectId = project.Id
		req.ProjectTitle = project.Specification.Title
		req.SuggestedGroupName, _ = fnd.GenerateProjectName(project.Id, project.Specification.Title, fnd.ProjectTitleDefault)

		gid, hasGid := ctrl.MapUCloudProjectToLocal(updated.Project.Id)
		if hasGid {
			req.UnixGid.Set(gid)
		}

		for _, member := range project.Status.Members {
			wasAdded := slices.Contains(membersAddedToProject, member.Username)
			uid, ok := ctrl.MapUCloudToLocal(member.Username)
			if !ok {
				continue
			}

			projectMember := scriptMember{
				Uid:            uid,
				UCloudUsername: member.Username,
				Role:           util.OptValue(member.Role),
			}

			req.AllMembers = append(req.AllMembers, projectMember)
			if wasAdded {
				req.MembersAddedToProject = append(req.MembersAddedToProject, projectMember)
			}
		}

		for _, member := range membersRemovedFromProject {
			uid, ok := ctrl.MapUCloudToLocal(member)
			if !ok {
				continue
			}

			req.MembersRemovedFromProject = append(req.MembersRemovedFromProject, scriptMember{
				Uid:            uid,
				UCloudUsername: member,
			})
		}

		resp, ok := onProjectUpdated.Invoke(req)
		if !ok {
			return false
		}

		if hasGid && resp.Gid != req.UnixGid.Get() {
			log.Warn(
				"GID returned from project script returned an unexpected gid. Expected %v but got %v for project %v",
				req.UnixGid.Get(),
				resp.Gid,
				project.Id,
			)
		} else if !hasGid {
			ctrl.RegisterProjectMapping(updated.Project.Id, resp.Gid)
		}
		return true
	}
}

type createUserRequest struct {
	UCloudUsername    string `json:"ucloudUsername"`
	FirstName         string `json:"firstName"`
	LastName          string `json:"lastName"`
	SuggestedUsername string `json:"suggestedUsername"`
}

type createUserResponse struct {
	Uid uint32 `json:"uid"`
}

var onUserConnected = ctrl.NewScript[createUserRequest, createUserResponse]()

type scriptMember struct {
	UCloudUsername string                       `json:"UCloudUsername"`
	Uid            uint32                       `json:"uid"`
	Role           util.Option[apm.ProjectRole] `json:"role"`
}

type syncProjectRequest struct {
	UCloudProjectId           string              `json:"UCloudProjectId"`
	ProjectTitle              string              `json:"projectTitle"`
	SuggestedGroupName        string              `json:"suggestedGroupName"`
	UnixGid                   util.Option[uint32] `json:"unixGid"`
	AllMembers                []scriptMember      `json:"allMembers"`
	MembersAddedToProject     []scriptMember      `json:"membersAddedToProject"`
	MembersRemovedFromProject []scriptMember      `json:"membersRemovedFromProject"`
}

type syncProjectResponse struct {
	Gid uint32 `json:"gid"`
}

var onProjectUpdated = ctrl.NewScript[syncProjectRequest, syncProjectResponse]()
