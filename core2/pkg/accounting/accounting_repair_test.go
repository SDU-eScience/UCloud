package accounting

import (
	"database/sql"
	"slices"
	"testing"
	"time"
)

func TestPlanDuplicateRepairNormalizesWalletsBeforeGroups(t *testing.T) {
	wallets := []duplicateRepairWalletRow{
		{Id: 1, WalletOwner: 10, ProductCategory: 20, LocalUsage: 5},
		{Id: 2, WalletOwner: 10, ProductCategory: 20, LocalUsage: 9},
		{Id: 3, WalletOwner: 11, ProductCategory: 20, LocalUsage: 1},
	}
	groups := []duplicateRepairGroupRow{
		{Id: 10, AssociatedWallet: 3, ParentWallet: sql.NullInt64{Int64: 1, Valid: true}},
		{Id: 11, AssociatedWallet: 3, ParentWallet: sql.NullInt64{Int64: 2, Valid: true}},
		{Id: 12, AssociatedWallet: 2},
		{Id: 13, AssociatedWallet: 1},
	}

	plan := planDuplicateRepair(wallets, groups)
	if plan.Blocked {
		t.Fatalf("unexpected blockers: %v", plan.Blockers)
	}
	if len(plan.Wallets) != 1 || plan.Wallets[0] != (duplicateRepairWallet{RemoveId: 2, KeepId: 1}) {
		t.Fatalf("wallet changes = %+v", plan.Wallets)
	}
	wantGroups := []duplicateRepairGroup{{RemoveId: 11, KeepId: 10}, {RemoveId: 13, KeepId: 12}}
	if len(plan.Groups) != len(wantGroups) {
		t.Fatalf("group changes = %+v, want %+v", plan.Groups, wantGroups)
	}
	for i := range wantGroups {
		if plan.Groups[i] != wantGroups[i] {
			t.Errorf("group change %d = %+v, want %+v", i, plan.Groups[i], wantGroups[i])
		}
	}

	ids, usage := duplicateRepairCanonicalUsage(wallets)
	if len(ids) != 2 || ids[0] != 1 || usage[0] != 9 || ids[1] != 3 || usage[1] != 1 {
		t.Fatalf("canonical usage = ids %v usage %v", ids, usage)
	}
}

func TestPlanDuplicateRepairRejectsNormalizedSelfAllocation(t *testing.T) {
	wallets := []duplicateRepairWalletRow{
		{Id: 1, WalletOwner: 10, ProductCategory: 20},
		{Id: 2, WalletOwner: 10, ProductCategory: 20},
	}
	groups := []duplicateRepairGroupRow{
		{Id: 10, AssociatedWallet: 2, ParentWallet: sql.NullInt64{Int64: 1, Valid: true}},
	}

	plan := planDuplicateRepair(wallets, groups)
	if !plan.Blocked || len(plan.Blockers) != 1 {
		t.Fatalf("plan should be blocked: %+v", plan)
	}
}

func TestResetAccountingBucketForReplay(t *testing.T) {
	wallet := &internalWallet{
		Id:                  1,
		LocalUsage:          50,
		WasLocked:           false,
		ChildrenUsage:       map[AccWalletId]int64{2: 7},
		AllocationsByParent: map[AccWalletId]*internalGroup{0: {TreeUsage: 8}},
	}
	allocation := &internalAllocation{
		Id:           1,
		Quota:        4,
		Active:       true,
		Retired:      true,
		RetiredUsage: 4,
		RetiredQuota: 100,
	}
	bucket := &internalBucket{
		WalletsById:     map[AccWalletId]*internalWallet{1: wallet},
		AllocationsById: map[accAllocId]*internalAllocation{1: allocation},
	}

	resetAccountingBucketForReplay(bucket)
	if wallet.LocalUsage != 0 || wallet.ChildrenUsage[2] != 0 || wallet.AllocationsByParent[0].TreeUsage != 0 || !wallet.WasLocked {
		t.Fatalf("wallet was not reset: %+v", wallet)
	}
	if allocation.Quota != 100 || allocation.Active || allocation.Retired || allocation.RetiredUsage != 0 || allocation.RetiredQuota != 0 {
		t.Fatalf("allocation was not rewound: %+v", allocation)
	}
}

func TestReplayAccountingBucketUsesScopeTimestamps(t *testing.T) {
	e := newEnv(t, timeCategory)
	e.AllocateEx(0, 0, 10, 100, "user", "")
	walletId := e.Wallet(e.Owner("user"), e.Tm(0))

	replayAccountingBucket(e.Bucket, []accountingReflowEvent{
		{At: e.Tm(5), WalletId: walletId, Key: "before-expiration", Usage: 40},
		{At: e.Tm(15), WalletId: walletId, Key: "after-expiration", Usage: 10},
	}, e.Tm(20))

	wallet := e.Bucket.WalletsById[walletId]
	group := wallet.AllocationsByParent[internalGraphRoot]
	if wallet.LocalUsage != 50 {
		t.Fatalf("local usage = %d, want 50", wallet.LocalUsage)
	}
	if group.TreeUsage != 40 {
		t.Fatalf("tree usage = %d, want 40", group.TreeUsage)
	}
	for allocationId := range group.Allocations {
		allocation := e.Bucket.AllocationsById[allocationId]
		if !allocation.Retired || allocation.RetiredUsage != 40 || allocation.RetiredQuota != 100 {
			t.Fatalf("retired allocation = %+v", allocation)
		}
	}
}

type accountingReplayTestState struct {
	localUsage    int64
	treeUsage     int64
	childrenUsage int64
	allocations   [][5]int64
}

func captureAccountingReplayTestState(e *env, ownerRef string) accountingReplayTestState {
	walletId := e.Wallet(e.Owner(ownerRef), e.Tm(0))
	wallet := e.Bucket.WalletsById[walletId]
	state := accountingReplayTestState{localUsage: wallet.LocalUsage}
	if group := wallet.AllocationsByParent[internalGraphRoot]; group != nil {
		state.treeUsage = group.TreeUsage
	}
	for _, parent := range e.Bucket.WalletsById {
		state.childrenUsage += parent.ChildrenUsage[walletId]
	}
	allocationIds := make([]accAllocId, 0, len(e.Bucket.AllocationsById))
	for allocationId := range e.Bucket.AllocationsById {
		allocationIds = append(allocationIds, allocationId)
	}
	slices.Sort(allocationIds)
	for _, allocationId := range allocationIds {
		allocation := e.Bucket.AllocationsById[allocationId]
		state.allocations = append(state.allocations, [5]int64{
			allocation.Quota,
			boolToInt64(allocation.Active),
			boolToInt64(allocation.Retired),
			allocation.RetiredUsage,
			allocation.RetiredQuota,
		})
	}
	return state
}

func boolToInt64(value bool) int64 {
	if value {
		return 1
	}
	return 0
}

func replayAccountingBucketWithAllocationScanPerEvent(bucket *internalBucket, events []accountingReflowEvent, now time.Time) {
	resetAccountingBucketForReplay(bucket)
	for _, event := range events {
		lInternalTransitionAllocations(bucket, event.At, false)
		wallet := bucket.WalletsById[event.WalletId]
		if event.Usage != 0 {
			lInternalReportUsage(bucket, event.At, wallet, event.Usage)
			wallet.LocalUsage += event.Usage
		}
	}
	lInternalTransitionAllocations(bucket, now, false)
}

func TestReplayAccountingBucketCachedTransitionsMatchPerEventScanning(t *testing.T) {
	setup := func() (*env, []accountingReflowEvent) {
		e := newEnv(t, capacityCategory, false)
		e.AllocateEx(0, 0, 10, 100, "user", "")
		e.AllocateEx(0, 10, 30, 100, "user", "")
		walletId := e.Wallet(e.Owner("user"), e.Tm(0))
		return e, []accountingReflowEvent{
			{At: e.Tm(5), WalletId: walletId, Key: "a", Usage: 40},
			{At: e.Tm(5), WalletId: walletId, Key: "b", Usage: 5},
			{At: e.Tm(6), WalletId: walletId, Key: "c", Usage: 5},
			{At: e.Tm(15), WalletId: walletId, Key: "d", Usage: 10},
			{At: e.Tm(16), WalletId: walletId, Key: "e", Usage: 5},
		}
	}

	optimized, optimizedEvents := setup()
	replayAccountingBucket(optimized.Bucket, optimizedEvents, optimized.Tm(40))
	optimizedState := captureAccountingReplayTestState(optimized, "user")

	baseline, baselineEvents := setup()
	replayAccountingBucketWithAllocationScanPerEvent(baseline.Bucket, baselineEvents, baseline.Tm(40))
	baselineState := captureAccountingReplayTestState(baseline, "user")

	if optimizedState.localUsage != baselineState.localUsage ||
		optimizedState.treeUsage != baselineState.treeUsage ||
		optimizedState.childrenUsage != baselineState.childrenUsage ||
		!slices.Equal(optimizedState.allocations, baselineState.allocations) {
		t.Fatalf("cached replay state = %+v, per-event replay state = %+v", optimizedState, baselineState)
	}
}

func BenchmarkAccountingReplayAllocationTransitions(b *testing.B) {
	const allocationCount = 1_000
	const eventCount = 43_000
	base := time.Date(2000, time.January, 1, 0, 0, 0, 0, time.UTC)
	newBucket := func() *internalBucket {
		bucket := &internalBucket{
			Category:        capacityCategory,
			WalletsById:     map[AccWalletId]*internalWallet{},
			AllocationsById: make(map[accAllocId]*internalAllocation, allocationCount),
		}
		for i := 0; i < allocationCount; i++ {
			id := accAllocId(i + 1)
			bucket.AllocationsById[id] = &internalAllocation{
				Id:        id,
				Start:     base.AddDate(100, 0, 0),
				End:       base.AddDate(101, 0, 0),
				Committed: true,
			}
		}
		return bucket
	}

	b.Run("cached-order-and-boundaries", func(b *testing.B) {
		for range b.N {
			bucket := newBucket()
			transitions := newAccountingReplayAllocationTransitions(bucket)
			for i := 0; i < eventCount; i++ {
				transitions.transition(bucket, base.Add(time.Duration(i)*time.Second))
			}
		}
	})
	b.Run("sort-and-scan-per-event", func(b *testing.B) {
		for range b.N {
			bucket := newBucket()
			for i := 0; i < eventCount; i++ {
				lInternalTransitionAllocations(bucket, base.Add(time.Duration(i)*time.Second), false)
			}
		}
	})
}

func TestResolveAccountingReflowGrantQuotaUsesLatestMatchingRevision(t *testing.T) {
	candidates := []accountingReflowGrantQuota{
		{GrantId: 7, GrantGiver: "giver-a", RevisionNumber: 1, Quota: 10},
		{GrantId: 7, GrantGiver: "giver-a", RevisionNumber: 2, Quota: 20},
		{GrantId: 7, GrantGiver: "giver-b", RevisionNumber: 3, Quota: 30},
	}

	got, ok := resolveAccountingReflowGrantQuota(candidates, "giver-a")
	if !ok || got.Quota != 20 || got.RevisionNumber != 2 {
		t.Fatalf("resolved quota = %+v, ok=%t", got, ok)
	}
	if _, ok := resolveAccountingReflowGrantQuota([]accountingReflowGrantQuota{
		{GrantGiver: "giver-a", RevisionNumber: 2, Quota: 20},
		{GrantGiver: "giver-a", RevisionNumber: 2, Quota: 30},
	}, "giver-a"); ok {
		t.Fatal("ambiguous latest revision should not resolve")
	}
}

func TestRecoverAccountingReflowRetiredQuotaFromGrant(t *testing.T) {
	allocation := &internalAllocation{Id: 1, Retired: true}
	allocation.GrantedIn.Set(7)
	bucket := &internalBucket{
		Category:        timeCategory,
		AllocationsById: map[accAllocId]*internalAllocation{1: allocation},
	}
	grantQuotas := map[int64][]accountingReflowGrantQuota{
		7: {{GrantId: 7, RevisionNumber: 4, Quota: 100}},
	}

	recoveries, ignored, blockers := recoverAccountingReflowRetiredQuotas(bucket, grantQuotas)
	if len(blockers) != 0 || len(ignored) != 0 || len(recoveries) != 1 {
		t.Fatalf("recoveries=%+v ignored=%v blockers=%v", recoveries, ignored, blockers)
	}
	if allocation.RetiredQuota != 100 || recoveries[0].GrantId != 7 || recoveries[0].Revision != 4 {
		t.Fatalf("allocation=%+v recovery=%+v", allocation, recoveries[0])
	}
}

func TestRecoverAccountingReflowIgnoresMissingCapacityRetiredQuota(t *testing.T) {
	allocation := &internalAllocation{Id: 1, Retired: true}
	bucket := &internalBucket{
		Category:        capacityCategory,
		AllocationsById: map[accAllocId]*internalAllocation{1: allocation},
	}

	recoveries, ignored, blockers := recoverAccountingReflowRetiredQuotas(bucket, nil)
	if len(recoveries) != 0 || len(blockers) != 0 || len(ignored) != 1 || ignored[0] != 1 {
		t.Fatalf("recoveries=%+v ignored=%v blockers=%v", recoveries, ignored, blockers)
	}
	bucket.Category = timeCategory
	_, ignored, blockers = recoverAccountingReflowRetiredQuotas(bucket, nil)
	if len(ignored) != 0 || len(blockers) != 1 {
		t.Fatalf("non-capacity ignored=%v blockers=%v", ignored, blockers)
	}
}
