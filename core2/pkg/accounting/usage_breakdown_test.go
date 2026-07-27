package accounting

import (
	"net/http"
	"testing"

	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func TestUsageBreakdownContainsLocalResourcesAndBilledChildren(t *testing.T) {
	e := newEnv(t, capacityCategory)
	root := "00000000-0000-0000-0000-000000000001"
	child := "00000000-0000-0000-0000-000000000002"
	grandchild := "00000000-0000-0000-0000-000000000003"

	e.AllocateEx(0, 0, 100, 1000, root, "")
	e.AllocateEx(0, 0, 100, 1000, child, root)
	e.AllocateEx(0, 0, 100, 1000, grandchild, child)
	e.ReportAbs(1, root, 5, "drive-root")
	e.ReportAbs(2, root, 7, "job-root")
	e.ReportAbs(3, root, 11, "unsupported-root")
	e.ReportAbs(4, child, 20, "drive-child")
	e.ReportAbs(5, grandchild, 30, "job-grandchild")

	items := internalUsageBreakdown(e.Tm(6), root, e.Bucket.Category.ToId())
	if len(items) != 3 {
		t.Fatalf("item count = %d, want 3: %#v", len(items), items)
	}

	resources := map[accapi.UsageBreakdownResourceType]accapi.UsageBreakdownItem{}
	var workspace accapi.UsageBreakdownItem
	for _, item := range items {
		if item.Resource.Present {
			resources[item.Resource.Value.Type] = item
		} else if item.Workspace.Present {
			workspace = item
		}
	}

	if drive := resources[accapi.UsageBreakdownResourceTypeDrive]; drive.Usage != 5 || drive.Resource.Value.Id != "root" || !drive.LastUpdatedAt.Present {
		t.Errorf("drive item = %#v", drive)
	}
	if job := resources[accapi.UsageBreakdownResourceTypeJob]; job.Usage != 7 || job.Resource.Value.Id != "root" || !job.LastUpdatedAt.Present {
		t.Errorf("job item = %#v", job)
	}
	if !workspace.Workspace.Present || workspace.Workspace.Value.ProjectId != child || workspace.Usage != 50 {
		t.Errorf("workspace item = %#v, want child usage 50", workspace)
	}
}

func TestUsageBreakdownAuthorization(t *testing.T) {
	e := newEnv(t, capacityCategory)
	project := rpc.ProjectId("00000000-0000-0000-0000-000000000001")
	request := accapi.UsageBreakdownBrowseRequest{Category: e.Bucket.Category.ToId()}
	actor := rpc.Actor{
		Username:   "user",
		Project:    util.OptValue(project),
		Membership: map[rpc.ProjectId]rpc.ProjectRole{project: rpc.ProjectRoleUser},
	}

	_, err := UsageBreakdownBrowse(actor, request)
	if err == nil || err.StatusCode != http.StatusForbidden {
		t.Fatalf("user error = %v, want HTTP 403", err)
	}

	actor.Membership[project] = rpc.ProjectRoleAdmin
	if _, err = UsageBreakdownBrowse(actor, request); err != nil {
		t.Fatalf("admin error = %v", err)
	}

	actor.Membership[project] = rpc.ProjectRolePI
	if _, err = UsageBreakdownBrowse(actor, request); err != nil {
		t.Fatalf("PI error = %v", err)
	}

	actor.Project = util.OptNone[rpc.ProjectId]()
	if _, err = UsageBreakdownBrowse(actor, request); err != nil {
		t.Fatalf("personal workspace error = %v", err)
	}
}

func TestUsageBreakdownTransitionsDescendantAllocations(t *testing.T) {
	e := newEnv(t, capacityCategory)
	root := "00000000-0000-0000-0000-000000000001"
	child := "00000000-0000-0000-0000-000000000002"

	e.AllocateEx(0, 0, 100, 1000, root, "")
	e.AllocateEx(0, 0, 5, 1000, child, root)
	e.ReportAbs(1, child, 20, "drive-child")

	items := internalUsageBreakdown(e.Tm(6), root, e.Bucket.Category.ToId())
	if len(items) != 0 {
		t.Fatalf("items after child allocation expiry = %#v, want none", items)
	}
}

func TestUsageBreakdownPagination(t *testing.T) {
	items := []accapi.UsageBreakdownItem{
		{Type: accapi.UsageBreakdownItemTypeResource, Resource: util.OptValue(accapi.UsageBreakdownResource{Type: accapi.UsageBreakdownResourceTypeDrive, Id: "1"})},
		{Type: accapi.UsageBreakdownItemTypeResource, Resource: util.OptValue(accapi.UsageBreakdownResource{Type: accapi.UsageBreakdownResourceTypeJob, Id: "2"})},
	}

	first := usageBreakdownPage(items, 1, util.OptNone[string]())
	if len(first.Items) != 1 || !first.Next.Present {
		t.Fatalf("first page = %#v", first)
	}
	second := usageBreakdownPage(items, 1, first.Next)
	if len(second.Items) != 1 || second.Next.Present || second.Items[0].Resource.Value.Id != "2" {
		t.Fatalf("second page = %#v", second)
	}

	secondAfterFirstDisappears := usageBreakdownPage(items[1:], 1, first.Next)
	if len(secondAfterFirstDisappears.Items) != 1 || secondAfterFirstDisappears.Items[0].Resource.Value.Id != "2" {
		t.Fatalf("second page after first item disappears = %#v", secondAfterFirstDisappears)
	}
}
