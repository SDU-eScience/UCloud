package accounting

import (
	"net/http"
	"testing"
	"time"

	"ucloud.dk/core/pkg/coreutil"
	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func TestUsageBreakdownFlattensDescendantResources(t *testing.T) {
	e := newEnv(t, capacityCategory)
	root := "00000000-0000-0000-0000-000000000001"
	child := "00000000-0000-0000-0000-000000000002"
	grandchild := "00000000-0000-0000-0000-000000000003"

	e.AllocateEx(0, 0, 100, 1000, root, "")
	e.AllocateEx(0, 0, 100, 1000, child, root)
	e.AllocateEx(0, 0, 100, 1000, grandchild, child)
	e.ReportAbs(1, root, 5, "drive-"+e.Bucket.Category.Provider+"-1")
	e.ReportAbs(2, root, 7, "job-2")
	e.ReportAbs(3, root, 11, "unsupported-root")
	e.ReportAbs(4, child, 20, "drive-3")
	e.ReportAbs(5, grandchild, 30, "job-4")

	scopes := internalUsageBreakdown(root, e.Bucket.Category.ToId())
	if len(scopes) != 4 {
		t.Fatalf("scope count = %d, want 4: %#v", len(scopes), scopes)
	}

	usageById := map[string]int64{}
	for _, scope := range scopes {
		usageById[scope.Resource.Id] = scope.Usage
	}
	for id, want := range map[string]int64{"1": 5, "2": 7, "3": 20, "4": 30} {
		if got := usageById[id]; got != want {
			t.Errorf("usage for %s = %d, want %d", id, got, want)
		}
	}
}

func TestUsageBreakdownMarksExternalFunding(t *testing.T) {
	e := newEnv(t, capacityCategory)
	root := "00000000-0000-0000-0000-000000000001"
	child := "00000000-0000-0000-0000-000000000002"
	external := "00000000-0000-0000-0000-000000000003"

	e.AllocateEx(0, 0, 100, 1000, root, "")
	e.AllocateEx(0, 0, 100, 1000, external, "")
	e.AllocateEx(0, 0, 100, 500, child, root)
	e.AllocateEx(0, 0, 100, 500, child, external)
	e.AllocateEx(0, 0, 100, 500, child, "")
	e.ReportAbs(1, root, 5, "job-1")
	e.ReportAbs(2, child, 7, "job-2")

	scopes := internalUsageBreakdown(root, e.Bucket.Category.ToId())
	if len(scopes) != 2 {
		t.Fatalf("scope count = %d, want 2", len(scopes))
	}
	for _, scope := range scopes {
		if scope.Resource.Id == "1" && scope.IsExternallyFunded {
			t.Error("selected wallet was marked externally funded")
		}
		if scope.Resource.Id == "2" && !scope.IsExternallyFunded {
			t.Error("multiply funded descendant was not marked externally funded")
		}
	}
}

func TestUsageBreakdownSkipsUncommittedDescendants(t *testing.T) {
	e := newEnv(t, capacityCategory)
	root := "00000000-0000-0000-0000-000000000001"
	child := "00000000-0000-0000-0000-000000000002"
	rootWallet := e.Wallet(e.Owner(root), e.Tm(0))
	childWallet := e.Wallet(e.Owner(child), e.Tm(0))
	if _, err := internalAllocateNoCommit(e.Tm(0), e.Bucket, e.Tm(0), e.Tm(100), 1000, childWallet, rootWallet, util.OptNone[accGrantId]()); err != nil {
		t.Fatalf("allocate uncommitted descendant: %v", err)
	}
	e.Bucket.WalletsById[childWallet].ScopedUsage["job-1"] = &scopedUsage{Key: "job-1", Usage: 7, LastUpdatedAt: e.Tm(1)}

	if scopes := internalUsageBreakdown(root, e.Bucket.Category.ToId()); len(scopes) != 0 {
		t.Fatalf("uncommitted descendant scopes = %#v, want none", scopes)
	}
}

func TestUsageBreakdownEnrichment(t *testing.T) {
	scopes := []usageBreakdownScope{
		{Resource: accapi.UsageBreakdownResource{Type: accapi.UsageBreakdownResourceTypeJob, Id: "1"}, Usage: 4, LastUpdatedAt: testUsageBreakdownTime(8), Workspace: accapi.WalletOwnerProject("project"), IsExternallyFunded: true},
		{Resource: accapi.UsageBreakdownResource{Type: accapi.UsageBreakdownResourceTypeDrive, Id: "2"}, Usage: 8, LastUpdatedAt: testUsageBreakdownTime(8), Workspace: accapi.WalletOwnerUser("bob")},
		{Resource: accapi.UsageBreakdownResource{Type: accapi.UsageBreakdownResourceTypeJob, Id: "missing"}, Usage: 16, LastUpdatedAt: testUsageBreakdownTime(8), Workspace: accapi.WalletOwnerProject("project")},
		{Resource: accapi.UsageBreakdownResource{Type: accapi.UsageBreakdownResourceTypeJob, Id: "1"}, Usage: 2, LastUpdatedAt: testUsageBreakdownTime(9), Workspace: accapi.WalletOwnerProject("project")},
	}
	metadata := []coreutil.UsageBreakdownResource{
		{Type: "job", Id: "1", Title: "Simulation", CreatedBy: "alice", ProjectId: "project", ProjectTitle: "Research"},
		{Type: "drive", Id: "2", Title: "Home", CreatedBy: "bob"},
	}

	items := usageBreakdownEnrich(scopes, metadata)
	if len(items) != 3 {
		t.Fatalf("item count = %d, want 3", len(items))
	}
	itemsById := map[string]accapi.UsageBreakdownItem{}
	for _, item := range items {
		itemsById[item.Resource.Id] = item
	}
	if job := itemsById["1"]; job.Title != "Simulation" || job.CreatedBy != "alice" || job.Workspace.ProjectId != "project" || job.WorkspaceTitle != "Research" || job.Usage != 6 || job.LastUpdatedAt.Time() != testUsageBreakdownTime(9) || !job.IsExternallyFunded {
		t.Errorf("job = %#v", job)
	}
	if drive := itemsById["2"]; drive.Title != "Home" || drive.Workspace.Username != "bob" || drive.WorkspaceTitle != "bob's workspace" {
		t.Errorf("drive = %#v", drive)
	}
	if missing := itemsById["missing"]; missing.Usage != 16 || missing.Title != "" || missing.Workspace.ProjectId != "project" {
		t.Errorf("missing resource = %#v", missing)
	}
}

func TestUsageBreakdownFiltersAggregatesSortsAndPaginates(t *testing.T) {
	items := []accapi.UsageBreakdownItem{
		usageBreakdownTestItem("1", 10, 1, "alice", "project-a"),
		usageBreakdownTestItem("2", 30, 3, "alice", "project-a"),
		usageBreakdownTestItem("3", 20, 2, "bob", "project-b"),
	}
	request := accapi.UsageBreakdownBrowseRequest{
		ItemsPerPage:        1,
		FilterProject:       util.OptValue("project-a"),
		FilterCreatedBy:     util.OptValue("alice"),
		FilterUsageMin:      util.OptValue[int64](10),
		FilterUsageMax:      util.OptValue[int64](30),
		FilterReportedAtMin: util.OptValue(uint64(testUsageBreakdownTime(1).UnixMilli())),
		FilterReportedAtMax: util.OptValue(uint64(testUsageBreakdownTime(3).UnixMilli())),
		SortBy:              util.OptValue(accapi.UsageBreakdownSortByUsage),
		SortDirection:       util.OptValue(accapi.UsageBreakdownSortDescending),
	}

	first := usageBreakdownPage(items, request)
	if first.TotalCount != 2 || first.TotalUsage != 40 || len(first.Items) != 1 || first.Items[0].Resource.Id != "2" || !first.Next.Present {
		t.Fatalf("first page = %#v", first)
	}
	request.Next = first.Next
	second := usageBreakdownPage(items[0:1], request)
	if second.TotalCount != 1 || second.TotalUsage != 10 || len(second.Items) != 1 || second.Items[0].Resource.Id != "1" {
		t.Fatalf("page after prior item disappears = %#v", second)
	}
}

func TestUsageBreakdownAutocomplete(t *testing.T) {
	items := []accapi.UsageBreakdownItem{
		usageBreakdownTestItem("1", 10, 1, "alice", "project-a"),
		usageBreakdownTestItem("2", 20, 2, "bob", "project-b"),
		usageBreakdownTestItem("3", 30, 3, "alice", "project-a"),
		usageBreakdownTestItem("4", 40, 4, "malice", "project-c"),
	}
	items[0].WorkspaceTitle = "Alpha research"
	items[1].WorkspaceTitle = "Beta research"
	items[2].WorkspaceTitle = "Alpha research"
	items[3].WorkspaceTitle = "Research alpha"
	items[1].Resource.Type = accapi.UsageBreakdownResourceTypeDrive
	request := accapi.UsageBreakdownBrowseRequest{
		FilterProject: util.OptValue("project-b"), WorkspaceSearch: util.OptValue("alpha"),
		CreatedBySearch: util.OptValue("a"), FilterReportedAtMin: util.OptValue(uint64(testUsageBreakdownTime(1).UnixMilli())),
	}

	page := usageBreakdownPage(items, request)
	if len(page.WorkspaceAutocomplete) != 2 || page.WorkspaceAutocomplete[0].Value != "project-a" || page.WorkspaceAutocomplete[1].Value != "project-c" {
		t.Errorf("workspace autocomplete = %#v", page.WorkspaceAutocomplete)
	}
	if len(page.CreatedByAutocomplete) != 2 || page.CreatedByAutocomplete[0].Value != "alice" || page.CreatedByAutocomplete[1].Value != "malice" {
		t.Errorf("created-by autocomplete = %#v", page.CreatedByAutocomplete)
	}
	if page.TotalCount != 1 || page.Items[0].Resource.Id != "2" {
		t.Errorf("filtered page = %#v", page)
	}
}

func TestUsageBreakdownAuthorization(t *testing.T) {
	e := newEnv(t, capacityCategory)
	project := rpc.ProjectId("00000000-0000-0000-0000-000000000001")
	request := accapi.UsageBreakdownBrowseRequest{CategoryName: e.Bucket.Category.Name, CategoryProvider: e.Bucket.Category.Provider}
	actor := rpc.Actor{Username: "user", Project: util.OptValue(project), Membership: map[rpc.ProjectId]rpc.ProjectRole{project: rpc.ProjectRoleUser}}

	_, err := UsageBreakdownBrowse(actor, request)
	if err == nil || err.StatusCode != http.StatusForbidden {
		t.Fatalf("user error = %v, want HTTP 403", err)
	}
}

func TestUsageBreakdownBrowseEnrichesOutsideAccountingLocks(t *testing.T) {
	e := newEnv(t, capacityCategory)
	project := rpc.ProjectId("00000000-0000-0000-0000-000000000001")
	e.AllocateEx(0, 0, 100, 1000, string(project), "")
	e.ReportAbs(1, string(project), 12, "job-1")

	previousRetrieve := usageBreakdownRetrieveResources
	defer func() { usageBreakdownRetrieveResources = previousRetrieve }()
	usageBreakdownRetrieveResources = func(requested []coreutil.UsageBreakdownResource) []coreutil.UsageBreakdownResource {
		if !accGlobals.Mu.TryLock() {
			t.Fatal("resource enrichment ran while the accounting globals lock was held")
		}
		accGlobals.Mu.Unlock()
		return []coreutil.UsageBreakdownResource{{
			Type: "job", Id: requested[0].Id, Title: "Simulation", CreatedBy: "alice",
			ProjectId: string(project), ProjectTitle: "Research",
		}}
	}

	response, err := UsageBreakdownBrowse(rpc.Actor{
		Username: "alice", Project: util.OptValue(project),
		Membership: map[rpc.ProjectId]rpc.ProjectRole{project: rpc.ProjectRoleAdmin},
	}, accapi.UsageBreakdownBrowseRequest{
		CategoryName: e.Bucket.Category.Name, CategoryProvider: e.Bucket.Category.Provider,
	})
	if err != nil {
		t.Fatalf("browse error = %v", err)
	}
	if response.TotalCount != 1 || response.TotalUsage != 12 || response.Items[0].Title != "Simulation" || response.Items[0].WorkspaceTitle != "Research" {
		t.Fatalf("response = %#v", response)
	}
}

func usageBreakdownTestItem(id string, usage int64, reportedHour int, createdBy string, project string) accapi.UsageBreakdownItem {
	return accapi.UsageBreakdownItem{
		Resource: accapi.UsageBreakdownResource{Type: accapi.UsageBreakdownResourceTypeJob, Id: id},
		Usage:    usage, LastUpdatedAt: fndapi.Timestamp(testUsageBreakdownTime(reportedHour)), CreatedBy: createdBy,
		Workspace: accapi.WalletOwnerProject(project),
	}
}

func testUsageBreakdownTime(hour int) (result time.Time) {
	return time.Date(2025, time.January, 1, hour, 0, 0, 0, time.UTC)
}
