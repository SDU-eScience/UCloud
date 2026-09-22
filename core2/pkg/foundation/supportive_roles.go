package foundation

import (
	"net/http"
	"slices"
	"strings"
	"sync"

	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// Introduction
// =====================================================================================================================
// Supportive roles are duties which a project member holds alongside their regular project role (PI, Admin or
// User). They do not form a privilege hierarchy: holding a supportive role never changes what a member may do in
// the project by itself. Instead each role carries its own meaning, e.g. being responsible for the project's
// policies (the data manager).
//
// Invariants maintained by this file together with projects.go:
//
//   - A member holds exactly one regular project role but may additionally hold any supportive role.
//   - At most one member of a project holds a given supportive role
//   - The holder of a supportive role is always a current member of the project.
//   - Roles with FallbackToPi transfer back to the PI when their holder leaves
//
// Adding a new supportive role in the future:
//
//  1. Add the role constant to shared/pkg/foundation/projects and an entry in supportiveRoles below.
//  2. Decide on AssignableBy / AssignedToPiOnCreation / FallbackToPi for the new role.
//  3. Optionally gate functionality on the role via ActorIsSupportiveRoleHolder.

type supportiveRoleInfo struct {
	// AssignableBy is the regular project role required to transfer this supportive role to another member.
	AssignableBy fndapi.ProjectRole

	// AssignedToPiOnCreation grants this role to the PI when the project is created.
	AssignedToPiOnCreation bool

	// FallbackToPi transfers this role to the PI when its holder leaves the project. Roles without this flag
	// instead become vacant when their holder leaves.
	FallbackToPi bool

	// FollowsPiOffice transfers this role to the incoming PI when the PI office is handed over and the
	// outgoing PI is the current holder. The role follows the office rather than the person. Roles held by
	// any other member are explicit appointments and stay with their holder. Without this flag the role
	// lingers with the outgoing PI after they have been demoted to Admin.
	FollowsPiOffice bool
}

var supportiveRoles = map[fndapi.SupportiveRole]supportiveRoleInfo{
	fndapi.SupportiveRoleDataManager: {
		AssignableBy:           fndapi.ProjectRoleAdmin,
		AssignedToPiOnCreation: true,
		FallbackToPi:           true,
		FollowsPiOffice:        true,
	},
}

var supportiveRoleGlobals struct {
	Mu             sync.RWMutex
	TestingEnabled bool
	TestHolders    map[string]map[fndapi.SupportiveRole]string // project id -> role -> username
}

func supportiveRoleIsValid(role fndapi.SupportiveRole) bool {
	_, ok := supportiveRoles[role]
	return ok
}

// supportiveRoleHolder returns the index of the member currently holding the role. The caller must hold
// iproject.Mu.
func supportiveRoleHolder(status *fndapi.ProjectStatus, role fndapi.SupportiveRole) (int, bool) {
	for i := range status.Members {
		if slices.Contains(status.Members[i].SupportiveRoles, role) {
			return i, true
		}
	}
	return -1, false
}

// supportiveRoleGrant grants the role to username and removes it from any other member. The caller must
// hold iproject.Mu for writing.
func supportiveRoleGrant(status *fndapi.ProjectStatus, role fndapi.SupportiveRole, username string) {
	for i := range status.Members {
		member := &status.Members[i]
		if member.Username == username {
			if !slices.Contains(member.SupportiveRoles, role) {
				member.SupportiveRoles = append(member.SupportiveRoles, role)
				slices.SortFunc(member.SupportiveRoles, func(a, b fndapi.SupportiveRole) int {
					return strings.Compare(string(a), string(b))
				})
			}
		} else {
			member.SupportiveRoles = util.RemoveElementFunc(member.SupportiveRoles, func(r fndapi.SupportiveRole) bool {
				return r == role
			})
		}
	}
}

// supportiveRolesWithPiFallback lists every role which transfers to the PI when its holder leaves.
func supportiveRolesWithPiFallback() []string {
	var result []string
	for role, info := range supportiveRoles {
		if info.FallbackToPi {
			result = append(result, string(role))
		}
	}
	return result
}

// supportiveRolesWithoutPiFallback lists every role which becomes vacant when its holder leaves.
func supportiveRolesWithoutPiFallback() []string {
	var result []string
	for role, info := range supportiveRoles {
		if !info.FallbackToPi {
			result = append(result, string(role))
		}
	}
	return result
}

// supportiveRolesAssignedOnCreation lists every role granted to the PI on project creation.
func supportiveRolesAssignedOnCreation() []string {
	var result []string
	for role, info := range supportiveRoles {
		if info.AssignedToPiOnCreation {
			result = append(result, string(role))
		}
	}
	return result
}

// supportiveRolesFollowingPiOffice lists every role which follows the PI office when PI-ship is transferred.
func supportiveRolesFollowingPiOffice() []string {
	var result []string
	for role, info := range supportiveRoles {
		if info.FollowsPiOffice {
			result = append(result, string(role))
		}
	}
	return result
}

// supportiveRoleHandlePiTransfer transfers every supportive role which follows the PI office from the outgoing
// PI to the incoming PI. It runs as part of the PI transfer in ProjectChangeRole. Only roles currently held by
// the outgoing PI are transferred: roles held by another member are explicit appointments and stay with their
// holder. The names of the transferred roles are returned such that the caller can update its in-memory state.
func supportiveRoleHandlePiTransfer(tx *db.Transaction, projectId string, oldPi string, newPi string) []string {
	officeRoles := supportiveRolesFollowingPiOffice()
	if len(officeRoles) == 0 {
		return nil
	}

	if supportiveRoleGlobals.TestingEnabled {
		// Tests have no database, apply the transfer to the in-memory holders instead
		supportiveRoleGlobals.Mu.Lock()
		defer supportiveRoleGlobals.Mu.Unlock()

		holders := supportiveRoleGlobals.TestHolders[projectId]
		if holders == nil {
			return nil
		}

		var transferred []string
		for _, role := range officeRoles {
			if holders[fndapi.SupportiveRole(role)] == oldPi {
				holders[fndapi.SupportiveRole(role)] = newPi
				transferred = append(transferred, role)
			}
		}
		return transferred
	}

	rows := db.Select[struct{ Role string }](
		tx,
		`
			update project.supportive_roles sr
			set username = :new_pi, modified_at = now()
			where
				sr.project_id = :project
				and sr.username = :old_pi
				and sr.supportive_role = any(cast(:roles as text[]))
			returning sr.supportive_role as role
		`,
		db.Params{
			"project": projectId,
			"old_pi":  oldPi,
			"new_pi":  newPi,
			"roles":   officeRoles,
		},
	)

	var transferred []string
	for _, row := range rows {
		transferred = append(transferred, row.Role)
	}
	return transferred
}

// ActorIsSupportiveRoleHolder reports whether the actor currently holds the given supportive role in their active
// project.
func ActorIsSupportiveRoleHolder(actor rpc.Actor, role fndapi.SupportiveRole) bool {
	if !actor.Project.Present {
		return false
	}
	return IsSupportiveRoleHolder(string(actor.Project.Value), actor.Username, role)
}

// IsSupportiveRoleHolder reports whether the username currently holds the given supportive role in the project.
func IsSupportiveRoleHolder(projectId string, username string, role fndapi.SupportiveRole) bool {
	if supportiveRoleGlobals.TestingEnabled {
		supportiveRoleGlobals.Mu.RLock()
		defer supportiveRoleGlobals.Mu.RUnlock()
		holders := supportiveRoleGlobals.TestHolders[projectId]
		if holders == nil {
			return false
		}
		holder, ok := holders[role]
		return ok && holder == username
	}

	iproject, ok := projectRetrieveInternal(projectId)
	if !ok {
		return false
	}

	iproject.Mu.RLock()
	holds := false
	for _, member := range iproject.Project.Status.Members {
		if member.Username == username {
			if slices.Contains(member.SupportiveRoles, role) {
				holds = true
				break
			}
		}
	}
	iproject.Mu.RUnlock()
	return holds
}

// SupportiveRoleSetHolderForTesting overrides the holder of a supportive role in test mode (no database).
func SupportiveRoleSetHolderForTesting(projectId string, role fndapi.SupportiveRole, username string) {
	supportiveRoleGlobals.Mu.Lock()
	defer supportiveRoleGlobals.Mu.Unlock()

	if supportiveRoleGlobals.TestHolders == nil {
		supportiveRoleGlobals.TestHolders = make(map[string]map[fndapi.SupportiveRole]string)
	}

	holders := supportiveRoleGlobals.TestHolders[projectId]
	if holders == nil {
		holders = make(map[fndapi.SupportiveRole]string)
		supportiveRoleGlobals.TestHolders[projectId] = holders
	}
	holders[role] = username
}

// SupportiveRoleChange transfers a supportive role of the actor's active project to one of its members. The
// previous holder (if any) loses the role. Requires the regular project role configured for the supportive role
func SupportiveRoleChange(actor rpc.Actor, request fndapi.ProjectSupportiveRoleChangeRequest) *util.HttpError {
	if !actor.Project.Present {
		return util.HttpErr(http.StatusBadRequest, "This request requires an active project")
	}
	if !supportiveRoleIsValid(request.Role) {
		return util.HttpErr(http.StatusBadRequest, "Unknown supportive role")
	}

	projectId := string(actor.Project.Value)
	info := supportiveRoles[request.Role]

	project, iproject, err := projectRetrieve(actor, projectId, projectFlagsAll, info.AssignableBy)
	if err != nil {
		return err
	}

	isMember := slices.ContainsFunc(project.Status.Members, func(member fndapi.ProjectMember) bool {
		return member.Username == request.Username
	})
	if !isMember {
		return util.HttpErr(http.StatusBadRequest, "This user is not a member of the project. Try reloading the page.")
	}

	iproject.Mu.Lock()
	pStatus := &iproject.Project.Status
	if holderIdx, hasHolder := supportiveRoleHolder(pStatus, request.Role); hasHolder && pStatus.Members[holderIdx].Username == request.Username {
		iproject.Mu.Unlock()
		return util.HttpErr(http.StatusBadRequest, "This member already holds this role")
	}

	db.NewTx0(func(tx *db.Transaction) {
		// Re-check membership in the transaction so a concurrent removal cannot leave a non-member holding a role.
		_, stillMember := db.Get[struct{ Username string }](
			tx,
			`
				select username
				from project.project_members
				where username = :username and project_id = :project
			`,
			db.Params{
				"username": request.Username,
				"project":  projectId,
			},
		)
		if !stillMember {
			err = util.HttpErr(http.StatusBadRequest, "This user is not a member of the project. Try reloading the page.")
			return
		}

		db.Exec(
			tx,
			`
				insert into project.supportive_roles(project_id, supportive_role, username)
				values (:project, :supportiverole, :username)
				on conflict (project_id, supportive_role)
				do update set username = excluded.username, modified_at = now()
			`,
			db.Params{
				"project":        projectId,
				"supportiverole": string(request.Role),
				"username":       request.Username,
			},
		)

		db.Exec(
			tx,
			`
				update project.projects
				set modified_at = now()
				where id = :project
			`,
			db.Params{
				"project": projectId,
			},
		)
	})

	if err != nil {
		iproject.Mu.Unlock()
		return err
	}

	supportiveRoleGrant(pStatus, request.Role, request.Username)
	iproject.Mu.Unlock()

	for _, member := range project.Status.Members {
		projectsNotify(member.Username, projectId)
	}

	return nil
}
