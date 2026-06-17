package accounting

import (
	"net/http"
	"testing"

	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func testUpdateAllocationActor(project string, role rpc.ProjectRole) rpc.Actor {
	projectId := rpc.ProjectId(project)
	return rpc.Actor{
		Username:   project,
		Role:       rpc.RoleUser,
		Project:    util.OptValue(projectId),
		Membership: rpc.ProjectMembership{projectId: role},
	}
}

func TestUpdateAllocationVerifyActorRequiresActiveProjectAdmin(t *testing.T) {
	if err := updateAllocationVerifyActor(testUpdateAllocationActor("project", rpc.ProjectRoleAdmin)); err != nil {
		t.Fatalf("admin actor rejected: %v", err)
	}

	actor := testUpdateAllocationActor("project", rpc.ProjectRoleUser)
	if err := updateAllocationVerifyActor(actor); err == nil || err.StatusCode != http.StatusForbidden {
		t.Fatalf("non-admin actor err = %v, want forbidden", err)
	}

	actor = testUpdateAllocationActor("project", rpc.ProjectRoleAdmin)
	actor.Project.Clear()
	if err := updateAllocationVerifyActor(actor); err == nil || err.StatusCode != http.StatusForbidden {
		t.Fatalf("actor without active project err = %v, want forbidden", err)
	}
}

func TestUpdateAllocationVerifyOwnerRequiresParentOwnedByActiveProject(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)

	parentProject := "11111111-1111-1111-1111-111111111111"
	childProject := "22222222-2222-2222-2222-222222222222"
	parentWallet, err := WalletEnsure(e.categoryId, accapi.WalletOwnerProject(parentProject))
	if err != nil {
		t.Fatalf("parent wallet: %v", err)
	}
	parentAllocation, err := AllocationCreate(e.tm(0), e.categoryId, e.tm(0), e.tm(10), 100, parentWallet, util.OptNone[AllocationId](), util.OptNone[GrantId]())
	if err != nil {
		t.Fatalf("parent allocation: %v", err)
	}
	e.allocs["parent"] = parentAllocation
	e.setSplit("parent", 0, 100)

	childWallet, err := WalletEnsure(e.categoryId, accapi.WalletOwnerProject(childProject))
	if err != nil {
		t.Fatalf("child wallet: %v", err)
	}
	childAllocation, err := AllocationCreate(e.tm(0), e.categoryId, e.tm(0), e.tm(10), 100, childWallet, util.OptValue(parentAllocation), util.OptNone[GrantId]())
	if err != nil {
		t.Fatalf("child allocation: %v", err)
	}

	category, verifyErr := updateAllocationVerifyOwner(testUpdateAllocationActor(parentProject, rpc.ProjectRoleAdmin), childAllocation)
	if verifyErr != nil || category != e.categoryId {
		t.Fatalf("parent admin verify = %v category=%#v, want success/%#v", verifyErr, category, e.categoryId)
	}

	_, verifyErr = updateAllocationVerifyOwner(testUpdateAllocationActor(childProject, rpc.ProjectRoleAdmin), childAllocation)
	if verifyErr == nil || verifyErr.StatusCode != http.StatusForbidden {
		t.Fatalf("child admin verify err = %v, want forbidden", verifyErr)
	}

	_, verifyErr = updateAllocationVerifyOwner(testUpdateAllocationActor(parentProject, rpc.ProjectRoleAdmin), parentAllocation)
	if verifyErr == nil || verifyErr.StatusCode != http.StatusForbidden {
		t.Fatalf("root allocation verify err = %v, want forbidden", verifyErr)
	}
}
