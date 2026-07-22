package accounting

import (
	"math"
	"net/http"
	"testing"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/util"
)

func TestRejectedScopedUsageDoesNotCreateScope(t *testing.T) {
	e := newEnv(t, capacityCategory)
	e.AllocateEx(0, 0, 100, 100, "user", "")
	e.ReportAbs(1, "user", 10)

	request := accapi.ReportUsageRequest{
		IsDeltaCharge: true,
		Owner:         e.Owner("user").WalletOwner(),
		CategoryIdV2:  e.Bucket.Category.ToId(),
		Usage:         -1,
		Description: accapi.ChargeDescription{
			Scope: util.OptValue("new-scope"),
		},
	}
	_, err := internalReportUsage(e.Tm(2), request)
	if err == nil || err.StatusCode != http.StatusBadRequest {
		t.Fatalf("scoped decrease error = %v, want HTTP 400", err)
	}
	if len(accGlobals.Usage) != 0 {
		t.Fatalf("rejected report created %d scoped usage entries", len(accGlobals.Usage))
	}
}

func TestRejectedUsageDoesNotCreateOwnerOrWallet(t *testing.T) {
	e := newEnv(t, capacityCategory)
	ownerCount := len(accGlobals.OwnersById)
	walletCount := len(e.Bucket.WalletsById)
	request := accapi.ReportUsageRequest{
		IsDeltaCharge: true,
		Owner:         accapi.WalletOwner{Type: accapi.WalletOwnerTypeUser, Username: "unknown"},
		CategoryIdV2:  e.Bucket.Category.ToId(),
		Usage:         -1,
	}
	_, err := internalReportUsage(e.Tm(1), request)
	if err == nil || err.StatusCode != http.StatusBadRequest {
		t.Fatalf("negative report error = %v, want HTTP 400", err)
	}
	if len(accGlobals.OwnersById) != ownerCount || len(e.Bucket.WalletsById) != walletCount {
		t.Fatal("rejected report created an owner or wallet")
	}
}

func TestFindAllProvidersIncludesPaidAndOptionalFreeProviders(t *testing.T) {
	resetProducts()
	createTestProduct(testProviderProduct("b", accapi.ProductTypeStorage, true))
	createTestProduct(testProviderProduct("a", accapi.ProductTypeStorage, false))
	duplicateProviderProduct := testProviderProduct("a", accapi.ProductTypeStorage, false)
	duplicateProviderProduct.Category.Name = "another-storage-category"
	createTestProduct(duplicateProviderProduct)
	createTestProduct(testProviderProduct("c", accapi.ProductTypeCompute, false))

	response := findAllProviders(fndapi.BulkRequest[accapi.FindAllProvidersRequest]{Items: []accapi.FindAllProvidersRequest{
		{FilterProductType: util.OptValue(accapi.ProductTypeStorage)},
		{FilterProductType: util.OptValue(accapi.ProductTypeStorage), IncludeFreeToUse: util.OptValue(true)},
		{FilterProductType: util.OptValue(accapi.ProductTypeCompute)},
	}})
	want := [][]string{{"a"}, {"a", "b"}, {"c"}}
	if len(response.Responses) != len(want) {
		t.Fatalf("response count = %d, want %d", len(response.Responses), len(want))
	}
	for i, providers := range want {
		if !slicesEqual(response.Responses[i].Providers, providers) {
			t.Errorf("response %d providers = %v, want %v", i, response.Responses[i].Providers, providers)
		}
	}
}

func TestUsageCollapsePreservesZeroUsageQuota(t *testing.T) {
	timestamp := time.Date(2026, time.January, 1, 0, 0, 0, 0, time.UTC)
	reports := []internalUsageReport{
		{UsageOverTime: internalUsageOverTime{Absolute: []internalUsageOverTimeAbsoluteDataPoint{{
			Timestamp: timestamp, Usage: 10, UtilizationPercent100: 100, Quota: util.OptValue[int64](10),
		}}}},
		{UsageOverTime: internalUsageOverTime{Absolute: []internalUsageOverTimeAbsoluteDataPoint{{
			Timestamp: timestamp, Usage: 0, UtilizationPercent100: 0, Quota: util.OptValue[int64](100),
		}}}},
	}

	collapsed := usageCollapseReports(reports)
	if len(collapsed.UsageOverTime.Absolute) != 1 {
		t.Fatalf("absolute point count = %d, want 1", len(collapsed.UsageOverTime.Absolute))
	}
	got := collapsed.UsageOverTime.Absolute[0].UtilizationPercent100
	want := float64(10) / 110 * 100
	if diff := got - want; diff < -0.000001 || diff > 0.000001 {
		t.Fatalf("utilization = %f, want %f", got, want)
	}
}

func TestOverAllocationEscapePreservesChildAllocation(t *testing.T) {
	runTable(t, []accapi.ProductCategory{capacityCategory, timeCategory}, func(e *env) {
		e.AllocateEx(0, 0, 100, 100, "parent", "")
		e.AllocateEx(0, 0, 100, 80, "child", "parent")
		e.ReportAbs(1, "parent", 30)
		e.ReportAbs(1, "child", 80)

		e.Expect("child", 80, true)
		e.Expect("parent", 100, true)
	})
}

func TestRoutingPrefersEarlierExpiration(t *testing.T) {
	e := newEnv(t, capacityCategory)
	e.AllocateEx(0, 0, 100, 100, "early-parent", "")
	e.AllocateEx(0, 0, 100, 100, "late-parent", "")
	e.AllocateEx(0, 0, 10, 100, "child", "early-parent")
	e.AllocateEx(0, 0, 20, 100, "child", "late-parent")
	e.ReportAbs(1, "child", 10)

	child := e.Bucket.WalletsById[e.Wallet(e.Owner("child"), e.Tm(0))]
	early := e.Wallet(e.Owner("early-parent"), e.Tm(0))
	late := e.Wallet(e.Owner("late-parent"), e.Tm(0))
	if got := child.AllocationsByParent[early].TreeUsage; got != 10 {
		t.Fatalf("early allocation usage = %d, want 10", got)
	}
	if got := child.AllocationsByParent[late].TreeUsage; got != 0 {
		t.Fatalf("late allocation usage = %d, want 0", got)
	}
}

func TestGraphCostSupportsLargeQuota(t *testing.T) {
	e := newEnv(t, capacityCategory)
	e.AllocateEx(0, 0, 100, math.MaxInt64, "user", "")
	wallet := e.Bucket.WalletsById[e.Wallet(e.Owner("user"), e.Tm(0))]
	_ = lInternalBuildGraph(e.Bucket, e.Tm(1), wallet, 0)
}

func TestRetiredAllocationDoesNotDetermineLiveExpiration(t *testing.T) {
	e := newEnv(t, capacityCategory)
	e.AllocateEx(0, 0, 10, 100, "user", "")
	e.AllocateEx(0, 5, 100, 100, "user", "")
	e.Scan(11)

	wallet := e.Bucket.WalletsById[e.Wallet(e.Owner("user"), e.Tm(0))]
	group := wallet.AllocationsByParent[internalGraphRoot]
	if got := lInternalEarliestExpiration(e.Bucket, group); !got.Equal(e.Tm(100)) {
		t.Fatalf("earliest live expiration = %s, want %s", got, e.Tm(100))
	}
}

func TestQuotaReductionAtStartCannotDropBelowUsage(t *testing.T) {
	e := newEnv(t, capacityCategory)
	e.AllocateEx(0, 0, 100, 100, "parent", "")
	allocationId := e.AllocateEx(0, 1, 100, 10, "child", "parent")
	e.ReportAbs(1, "child", 10)

	err := e.UpdateAllocation(t, e.Owner("parent"), 1, allocationId, util.OptValue[int64](0), util.OptNone[fndapi.Timestamp](), util.OptNone[fndapi.Timestamp]())
	if err == nil || err.StatusCode != http.StatusForbidden {
		t.Fatalf("quota reduction error = %v, want HTTP 403", err)
	}
	if got := e.Bucket.AllocationsById[allocationId].Quota; got != 10 {
		t.Fatalf("quota after rejected update = %d, want 10", got)
	}
}

func TestRequireActiveUsesCurrentValidity(t *testing.T) {
	t.Run("retired-only", func(t *testing.T) {
		e := newEnv(t, capacityCategory)
		e.AllocateEx(0, 0, 10, 100, "user", "")
		e.Scan(10)
		wallets := internalRetrieveWallets(e.Tm(10), "user", walletFilter{RequireActive: true})
		if len(wallets) != 0 {
			t.Fatalf("retired-only wallet count = %d, want 0", len(wallets))
		}
	})

	t.Run("start-boundary-without-scan", func(t *testing.T) {
		e := newEnv(t, capacityCategory)
		e.AllocateEx(0, 10, 20, 100, "user", "")
		wallets := internalRetrieveWallets(e.Tm(10), "user", walletFilter{RequireActive: true})
		if len(wallets) != 1 {
			t.Fatalf("current wallet count = %d, want 1", len(wallets))
		}
	})
}

func TestActivationReevaluatesSharedParentAndSibling(t *testing.T) {
	e := newEnv(t, capacityCategory)
	e.AllocateEx(0, 0, 100, 10, "parent", "")
	e.AllocateEx(0, 0, 100, 10, "sibling", "parent")
	e.AllocateEx(0, 10, 100, 10, "child", "parent")
	e.ReportAbs(1, "child", 10)

	previousMode := accountingInvariantChecks
	accountingInvariantChecks = accountingInvariantModeDisabled
	e.Scan(10)
	accountingInvariantChecks = previousMode

	parent := e.Bucket.WalletsById[e.Wallet(e.Owner("parent"), e.Tm(0))]
	sibling := e.Bucket.WalletsById[e.Wallet(e.Owner("sibling"), e.Tm(0))]
	if maxUsable := lInternalMaxUsable(e.Bucket, e.Tm(10), parent); maxUsable != 0 {
		t.Fatalf("parent MaxUsable = %d, want 0", maxUsable)
	}
	if !parent.WasLocked {
		t.Fatal("parent lock state was not reevaluated")
	}
	if maxUsable := lInternalMaxUsable(e.Bucket, e.Tm(10), sibling); maxUsable != 0 {
		t.Fatalf("sibling MaxUsable = %d, want 0", maxUsable)
	}
	if !sibling.WasLocked {
		t.Fatal("sibling lock state was not reevaluated")
	}
}

func TestUsageReportDoesNotDirtyUnchangedGroup(t *testing.T) {
	e := newEnv(t, capacityCategory)
	e.AllocateEx(0, 0, 100, 100, "usable", "")
	e.AllocateEx(0, 0, 100, 0, "unused", "")
	e.AllocateEx(0, 0, 100, 100, "child", "usable")
	e.AllocateEx(0, 0, 100, 0, "child", "unused")

	child := e.Bucket.WalletsById[e.Wallet(e.Owner("child"), e.Tm(0))]
	unused := e.Wallet(e.Owner("unused"), e.Tm(0))
	unchangedGroup := child.AllocationsByParent[unused]
	unchangedGroup.Dirty = false
	e.ReportAbs(1, "child", 10)
	if unchangedGroup.Dirty {
		t.Fatal("unchanged allocation group was marked dirty")
	}
}

func TestAllocationRejectsCyclesWithoutMutation(t *testing.T) {
	e := newEnv(t, capacityCategory)
	e.AllocateEx(0, 0, 100, 100, "a", "")
	e.AllocateEx(0, 0, 100, 100, "b", "a")

	a := e.Wallet(e.Owner("a"), e.Tm(0))
	b := e.Wallet(e.Owner("b"), e.Tm(0))
	allocationCount := len(e.Bucket.AllocationsById)
	groupCount := len(e.Bucket.WalletsById[a].AllocationsByParent)
	_, err := internalAllocateNoCommit(e.Tm(0), e.Bucket, e.Tm(0), e.Tm(100), 10, a, b, util.OptNone[accGrantId]())
	if err == nil || err.StatusCode != http.StatusBadRequest {
		t.Fatalf("cycle allocation error = %v, want HTTP 400", err)
	}
	if len(e.Bucket.AllocationsById) != allocationCount || len(e.Bucket.WalletsById[a].AllocationsByParent) != groupCount {
		t.Fatal("rejected cycle mutated allocation topology")
	}
}

func TestPendingGrantAllocationIsNotReadable(t *testing.T) {
	e := newEnv(t, capacityCategory)
	e.AllocateEx(0, 0, 100, 100, "parent", "")
	child := e.Wallet(e.Owner("child"), e.Tm(0))
	parent := e.Wallet(e.Owner("parent"), e.Tm(0))
	id, err := internalAllocateNoCommit(e.Tm(0), e.Bucket, e.Tm(0), e.Tm(100), 10, child, parent, util.OptValue[accGrantId](123))
	if err != nil {
		t.Fatalf("create pending allocation: %v", err)
	}

	_, _, found := internalRetrieveWalletByAllocationId(e.Tm(0), int(id))
	if found {
		t.Fatal("pending allocation was retrievable by ID")
	}
	wallet, ok := internalRetrieveWallet(e.Tm(0), child, false)
	if !ok {
		t.Fatal("child wallet not found")
	}
	if len(wallet.AllocationGroups) != 0 {
		t.Fatalf("pending allocation exposed %d allocation groups", len(wallet.AllocationGroups))
	}
	parentWallet, ok := internalRetrieveWallet(e.Tm(0), parent, true)
	if !ok {
		t.Fatal("parent wallet not found")
	}
	if len(parentWallet.Children) != 0 {
		t.Fatalf("pending allocation exposed %d children", len(parentWallet.Children))
	}
	if wallets := internalRetrieveWallets(e.Tm(0), "child", walletFilter{}); len(wallets) != 0 {
		t.Fatalf("pending allocation made %d wallets discoverable", len(wallets))
	}
}

func TestUsageReportRejectsExistingCycleWithoutMutation(t *testing.T) {
	e := newEnv(t, capacityCategory)
	rootAllocation := e.AllocateEx(0, 0, 100, 100, "a", "")
	e.AllocateEx(0, 0, 100, 100, "b", "a")
	a := e.Wallet(e.Owner("a"), e.Tm(0))
	b := e.Wallet(e.Owner("b"), e.Tm(0))

	rootGroup := e.Bucket.WalletsById[a].AllocationsByParent[internalGraphRoot]
	delete(e.Bucket.WalletsById[a].AllocationsByParent, internalGraphRoot)
	rootGroup.ParentWallet = b
	e.Bucket.WalletsById[a].AllocationsByParent[b] = rootGroup
	e.Bucket.WalletsById[b].ChildrenUsage[a] = 0
	e.Bucket.AllocationsById[rootAllocation].Parent = b

	request := accapi.ReportUsageRequest{
		Owner:        e.Owner("a").WalletOwner(),
		CategoryIdV2: e.Bucket.Category.ToId(),
		Usage:        1,
	}
	previousMode := accountingInvariantChecks
	accountingInvariantChecks = accountingInvariantModeDisabled
	_, err := internalReportUsage(e.Tm(1), request)
	accountingInvariantChecks = previousMode
	if err == nil || err.StatusCode != http.StatusInternalServerError {
		t.Fatalf("cyclic topology error = %v, want HTTP 500", err)
	}
	if got := e.Bucket.WalletsById[a].LocalUsage; got != 0 {
		t.Fatalf("local usage after rejected cyclic report = %d, want 0", got)
	}
}

func TestUsageReportRetrievalReturnsDeepSnapshot(t *testing.T) {
	walletId := AccWalletId(999)
	now := util.StartOfDayUTC(time.Now())
	delta := make([]internalUsageOverTimeDeltaDataPoint, 1, 2)
	delta[0] = internalUsageOverTimeDeltaDataPoint{Timestamp: now, Change: 1}
	report := &internalUsageReport{
		Wallet:    walletId,
		ValidFrom: now,
		UsageOverTime: internalUsageOverTime{
			Delta: delta,
			Absolute: []internalUsageOverTimeAbsoluteDataPoint{{
				Timestamp: now,
				Usage:     1,
				Quota:     util.OptValue[int64](10),
			}},
		},
	}
	reportGlobals.Mu.Lock()
	reportGlobals.Reports = map[AccWalletId]*internalUsageReport{walletId: report}
	reportGlobals.Mu.Unlock()

	snapshot := usageRetrieveHistoricReports(now, now, walletId)
	if len(snapshot) != 1 {
		t.Fatalf("snapshot count = %d, want 1", len(snapshot))
	}
	reportGlobals.Mu.Lock()
	report.UsageOverTime.Delta[0].Change = 2
	report.UsageOverTime.Absolute[0].Usage = 2
	reportGlobals.Mu.Unlock()

	if got := snapshot[0].UsageOverTime.Delta[0].Change; got != 1 {
		t.Fatalf("snapshot delta changed to %d", got)
	}
	if got := snapshot[0].UsageOverTime.Absolute[0].Usage; got != 1 {
		t.Fatalf("snapshot absolute usage changed to %d", got)
	}
}

func TestUsageReportRejectsFlowAboveQuotaWithoutMutation(t *testing.T) {
	e := newEnv(t, capacityCategory)
	e.AllocateEx(0, 0, 100, 10, "user", "")
	wallet := e.Bucket.WalletsById[e.Wallet(e.Owner("user"), e.Tm(0))]
	group := wallet.AllocationsByParent[internalGraphRoot]
	group.TreeUsage = 11

	request := accapi.ReportUsageRequest{
		Owner:        e.Owner("user").WalletOwner(),
		CategoryIdV2: e.Bucket.Category.ToId(),
		Usage:        1,
	}
	previousMode := accountingInvariantChecks
	accountingInvariantChecks = accountingInvariantModeDisabled
	_, err := internalReportUsage(e.Tm(1), request)
	accountingInvariantChecks = previousMode
	if err == nil || err.StatusCode != http.StatusInternalServerError {
		t.Fatalf("invalid flow error = %v, want HTTP 500", err)
	}
	if wallet.LocalUsage != 0 || group.TreeUsage != 11 {
		t.Fatal("rejected invalid-flow report mutated accounting state")
	}
}

func testProviderProduct(provider string, productType accapi.ProductType, free bool) accapi.ProductV2 {
	return accapi.ProductV2{
		Type: accapi.ProductTypeCCreate(productType),
		Name: string(productType),
		Category: accapi.ProductCategory{
			Name:                string(productType),
			Provider:            provider,
			ProductType:         productType,
			AccountingFrequency: accapi.AccountingFrequencyOnce,
			FreeToUse:           free,
		},
		ProductType: productType,
	}
}

func slicesEqual(a, b []string) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}
