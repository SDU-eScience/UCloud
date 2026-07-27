package accounting

import (
	"bytes"
	"slices"
	"testing"
	"time"
)

func auditTestCapture(wallet AccountingAuditWallet) AccountingAuditCapture {
	return AccountingAuditCapture{
		SchemaVersion: accountingAuditSchemaVersion,
		CapturedAt:    time.Date(2026, time.July, 23, 12, 0, 0, 0, time.UTC),
		Database:      "ucloud",
		Totals:        snapshotTotals{Wallets: 1},
		Wallets:       []AccountingAuditWallet{wallet},
		Findings:      []AccountingAuditFinding{},
		Groups:        []AccountingAuditGroup{},
		Allocations:   []AccountingAuditAllocation{},
		Scopes:        []AccountingAuditScope{},
	}
}

func TestAccountingAuditNoAllocationLockCorrectionIsOmitted(t *testing.T) {
	wallet := AccountingAuditWallet{
		Id: 1, OwnerKind: "project", OwnerReference: "project-1", ProjectTitle: "Project One",
		Provider: "provider", Category: "cpu", WasLocked: false, AllocationCount: 0,
	}
	before := auditTestCapture(wallet)
	wallet.WasLocked = true
	after := auditTestCapture(wallet)
	after.CapturedAt = before.CapturedAt.Add(time.Minute)

	report, err := CompareAccountingAudits(before, after)
	if err != nil {
		t.Fatal(err)
	}
	if len(report.Changes) != 0 {
		t.Fatalf("changes = %+v", report.Changes)
	}
}

func TestAccountingAuditAllocatedWalletLockCorrectionIsHigh(t *testing.T) {
	wallet := AccountingAuditWallet{
		Id: 1, OwnerKind: "project", OwnerReference: "project-1",
		Provider: "provider", Category: "cpu", WasLocked: false, AllocationCount: 1,
	}
	before := auditTestCapture(wallet)
	before.Allocations = []AccountingAuditAllocation{{Id: 1, AssociatedWallet: 1}}
	before.Totals.Allocations = 1
	wallet.WasLocked = true
	after := auditTestCapture(wallet)
	after.Allocations = []AccountingAuditAllocation{{Id: 1, AssociatedWallet: 1}}
	after.Totals.Allocations = 1
	after.CapturedAt = before.CapturedAt.Add(time.Minute)

	report, err := CompareAccountingAudits(before, after)
	if err != nil {
		t.Fatal(err)
	}
	if len(report.Changes) != 1 || report.Changes[0].Severity != "high" {
		t.Fatalf("changes = %+v", report.Changes)
	}
}

func TestAccountingAuditComparesRawScopeKeysAndRendersHTML(t *testing.T) {
	wallet := AccountingAuditWallet{Id: 1, OwnerKind: "user", OwnerReference: "user", Provider: "provider", Category: "cpu"}
	before := auditTestCapture(wallet)
	after := auditTestCapture(wallet)
	after.CapturedAt = before.CapturedAt.Add(time.Minute)
	before.Scopes = []AccountingAuditScope{{WalletId: 1, Key: "job/<one>", Usage: 10, LastUpdatedAt: before.CapturedAt}}
	after.Scopes = []AccountingAuditScope{{WalletId: 1, Key: "job/<one>", Usage: 12, LastUpdatedAt: before.CapturedAt}}
	before.Totals.Scopes = 1
	after.Totals.Scopes = 1

	report, err := CompareAccountingAudits(before, after)
	if err != nil {
		t.Fatal(err)
	}
	if len(report.Changes) != 1 || report.Changes[0].EntityId != "job/<one>" || report.Changes[0].Severity != "critical" {
		t.Fatalf("changes = %+v", report.Changes)
	}
	html, err := RenderAccountingAuditHTML(report)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Contains(html, []byte(`job/\u003cone\u003e`)) {
		t.Fatalf("HTML did not contain safely encoded raw scope key")
	}
	if bytes.Contains(html, []byte(`<td`)) || !bytes.Contains(html, []byte(`function virtualTable`)) {
		t.Fatalf("HTML did not use JSON-backed virtualized rows")
	}
	if !bytes.Contains(html, []byte(`a critical row can legitimately show zero`)) || !bytes.Contains(html, []byte(`Why flagged`)) {
		t.Fatalf("HTML did not explain summary severity")
	}
	if !bytes.Contains(html, []byte(`id="project-sort"`)) || !bytes.Contains(html, []byte(`function sortProjects()`)) {
		t.Fatalf("HTML did not include changed-wallet sorting")
	}
	if !bytes.Contains(html, []byte(`placeholder="Filter owner or title"`)) || !bytes.Contains(html, []byte(`id="change-sort"`)) || !bytes.Contains(html, []byte(`[change.ownerReference,change.projectTitle]`)) {
		t.Fatalf("HTML did not include owner-only filtering and detailed-change sorting")
	}
}

func TestAccountingAuditSortsWalletsByCategoryThenTitle(t *testing.T) {
	changes := []AccountingAuditChange{
		{Severity: "critical", Bucket: "z-provider/storage", ProjectTitle: "Zulu", OwnerReference: "owner-3", WalletId: 3, Entity: "wallet", EntityId: "3", Field: "loaded"},
		{Severity: "info", Bucket: "a-provider/compute", ProjectTitle: "Zulu", OwnerReference: "owner-2", WalletId: 2, Entity: "wallet", EntityId: "2", Field: "wasLocked"},
		{Severity: "high", Bucket: "z-provider/compute", ProjectTitle: "Alpha", OwnerReference: "owner-1", WalletId: 1, Entity: "wallet", EntityId: "1", Field: "localUsage"},
	}
	wallets := map[AccWalletId]AccountingAuditWallet{
		1: {Id: 1, Provider: "z-provider", Category: "compute", ProjectTitle: "Alpha", OwnerReference: "owner-1"},
		2: {Id: 2, Provider: "a-provider", Category: "compute", ProjectTitle: "Zulu", OwnerReference: "owner-2"},
		3: {Id: 3, Provider: "z-provider", Category: "storage", ProjectTitle: "Zulu", OwnerReference: "owner-3"},
	}

	slices.SortFunc(changes, compareAccountingAuditChanges)
	projects := summarizeAccountingAuditProjects(changes, wallets, wallets)
	if changes[0].WalletId != 1 || changes[1].WalletId != 2 || changes[2].WalletId != 3 {
		t.Fatalf("change order = %v, want wallets 1, 2, 3", []AccWalletId{changes[0].WalletId, changes[1].WalletId, changes[2].WalletId})
	}
	if len(projects) != 2 || projects[0].WalletId != 1 || projects[1].WalletId != 3 {
		t.Fatalf("project order = %+v, want wallets 1, 3", projects)
	}
}

func TestAccountingAuditOmitsInfoOnlyWalletsFromOverview(t *testing.T) {
	wallets := map[AccWalletId]AccountingAuditWallet{
		1: {Id: 1, Provider: "provider", Category: "compute"},
		2: {Id: 2, Provider: "provider", Category: "compute"},
	}
	changes := []AccountingAuditChange{
		{Severity: "info", WalletId: 1},
		{Severity: "info", WalletId: 2},
		{Severity: "high", WalletId: 2},
	}

	projects := summarizeAccountingAuditProjects(changes, wallets, wallets)
	if len(projects) != 1 || projects[0].WalletId != 2 || projects[0].Changes != 2 {
		t.Fatalf("projects = %+v", projects)
	}
	report := AccountingAuditComparison{Projects: []AccountingAuditProjectSummary{{Severity: "info", WalletId: 1}}, Changes: changes}
	html, err := RenderAccountingAuditHTML(report)
	if err != nil {
		t.Fatal(err)
	}
	if bytes.Contains(html, []byte(`"projects":[{"severity":"info"`)) {
		t.Fatal("HTML retained an info-only wallet overview row")
	}
	if !bytes.Contains(html, []byte(`"severity":"info"`)) {
		t.Fatal("HTML removed detailed info changes")
	}
}

func TestAccountingAuditIncludesBeforeAndAfterWalletMetrics(t *testing.T) {
	beforeWallet := AccountingAuditWallet{
		Id: 1, Loaded: true, LocalUsage: 10, TotalUsage: 15,
		IncomingContributingQuota: 100, OutgoingContributingQuota: 30,
		MaxUsable: 85, RecomputedExcessUsage: 2, WasLocked: false,
	}
	afterWallet := beforeWallet
	afterWallet.LocalUsage = 20
	afterWallet.TotalUsage = 25
	afterWallet.MaxUsable = 75
	changes := []AccountingAuditChange{{Severity: "high", WalletId: 1}}

	projects := summarizeAccountingAuditProjects(
		changes,
		map[AccWalletId]AccountingAuditWallet{1: beforeWallet},
		map[AccWalletId]AccountingAuditWallet{1: afterWallet},
	)
	if len(projects) != 1 {
		t.Fatalf("projects = %+v", projects)
	}
	project := projects[0]
	if project.Before.LocalUsage != 10 || project.After.LocalUsage != 20 || project.Before.Quota != 100 || project.After.MaxUsable != 75 {
		t.Fatalf("wallet metrics = before %+v after %+v", project.Before, project.After)
	}

	report := AccountingAuditComparison{Projects: projects, Changes: changes}
	html, err := RenderAccountingAuditHTML(report)
	if err != nil {
		t.Fatal(err)
	}
	for _, expected := range [][]byte{[]byte(`id="wallet-detail"`), []byte(`function showWalletDetails`), []byte(`Incoming quota`), []byte(`const ownerName=item=>item.projectTitle||item.ownerReference`)} {
		if !bytes.Contains(html, expected) {
			t.Fatalf("HTML missing %q", expected)
		}
	}
}

func TestAccountingAuditCaptureRoundTrip(t *testing.T) {
	wallet := AccountingAuditWallet{Id: 1, OwnerKind: "project", OwnerReference: "project-1", Provider: "provider", Category: "cpu"}
	want := auditTestCapture(wallet)
	want.Scopes = []AccountingAuditScope{{WalletId: 1, Key: "raw-scope-key", Usage: 42, LastUpdatedAt: want.CapturedAt}}
	want.Totals.Scopes = 1
	want.JobUsage = []AccountingAuditJobUsage{{WalletId: 1, Key: "job-123", JobId: 123, ScopedUsage: 42, RuntimeMinutes: 42, RuntimeSource: "job timestamps", ExpectedUsageMin: 42, ExpectedUsageMax: 42, AcceptedUsageMin: 41, AcceptedUsageMax: 43, UsagePerMinute: 1}}

	encoded, err := EncodeAccountingAuditCapture(want)
	if err != nil {
		t.Fatal(err)
	}
	got, err := DecodeAccountingAuditCapture(bytes.NewReader(encoded))
	if err != nil {
		t.Fatal(err)
	}
	if len(got.Scopes) != 1 || got.Scopes[0].Key != "raw-scope-key" || got.Scopes[0].Usage != 42 || len(got.JobUsage) != 1 || got.JobUsage[0].ExpectedUsageMin != 42 {
		t.Fatalf("decoded capture = %+v", got)
	}
}

func TestAccountingAuditReportsJobUsageMismatch(t *testing.T) {
	wallet := AccountingAuditWallet{Id: 1, OwnerKind: "user", OwnerReference: "user", Provider: "provider", Category: "cpu"}
	before := auditTestCapture(wallet)
	after := auditTestCapture(wallet)
	after.CapturedAt = before.CapturedAt.Add(time.Minute)
	after.JobUsage = []AccountingAuditJobUsage{
		{WalletId: 1, Key: "job-123", ScopedUsage: 116, RuntimeMinutes: 60, RuntimeSource: "state updates", ExpectedUsageMin: 120, ExpectedUsageMax: 121, AcceptedUsageMin: 118, AcceptedUsageMax: 123, UsagePerMinute: 2},
		{WalletId: 1, Key: "job-124", ScopedUsage: 59, RuntimeMinutes: 30, RuntimeSource: "state updates", ExpectedUsageMin: 60, ExpectedUsageMax: 61, AcceptedUsageMin: 58, AcceptedUsageMax: 63, UsagePerMinute: 2},
		{WalletId: 1, Key: "job-126", ScopedUsage: 61, RuntimeMinutes: 30, RuntimeSource: "state updates", ExpectedUsageMin: 60, ExpectedUsageMax: 61, AcceptedUsageMin: 58, AcceptedUsageMax: 63, UsagePerMinute: 2},
		{WalletId: 1, Key: "job-125", ScopedUsage: 0, Error: "job does not exist"},
	}

	report, err := CompareAccountingAudits(before, after)
	if err != nil {
		t.Fatal(err)
	}
	if report.JobUsage.Checked != 4 || report.JobUsage.Matched != 1 || report.JobUsage.Tolerated != 1 || report.JobUsage.Mismatch != 1 || report.JobUsage.Skipped != 1 {
		t.Fatalf("job usage summary = %+v", report.JobUsage)
	}
	if len(report.Changes) != 2 || report.Changes[0].Entity != "job-usage" || report.Changes[0].Severity != "critical" {
		t.Fatalf("changes = %+v", report.Changes)
	}
}

func TestAccountingAuditExpectedJobUsageRangeHandlesCoarseUnits(t *testing.T) {
	minimum, maximum := accountingAuditExpectedJobUsageRange(59, 60, 1)
	if minimum != 0 || maximum != 0 {
		t.Fatalf("59 hourly minutes = %d..%d, want 0..0", minimum, maximum)
	}
	minimum, maximum = accountingAuditExpectedJobUsageRange(1, 1, 2)
	if minimum != 2 || maximum != 3 {
		t.Fatalf("one minute at scale 2 = %d..%d, want 2..3", minimum, maximum)
	}
}
