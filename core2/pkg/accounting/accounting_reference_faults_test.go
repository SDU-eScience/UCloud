package accounting

import (
	"math"
	"testing"
	"time"
)

func assertKnownReferenceFault[T comparable](t *testing.T, description string, expected, knownWrong, actual T) {
	t.Helper()
	if actual == expected {
		t.Fatalf("reference unexpectedly produced the correct %s (%v); remove this fault test and its documentation", description, expected)
	}
	if actual != knownWrong {
		t.Fatalf("reference %s changed: correct value is %v, known wrong value is %v, got %v", description, expected, knownWrong, actual)
	}
	t.Logf("reference fault demonstrated: %s should be %v, but reference produced %v", description, expected, actual)
}

func newReferenceFaultHierarchy(t *testing.T, capacity bool, allocations ...RefAllocation) {
	t.Helper()
	GlobalRefAllocations = map[int64]*RefAllocation{}
	GlobalRefWallets = map[int64]*RefWallet{}
	for _, allocation := range allocations {
		for _, walletId := range []int64{allocation.BelongsTo, allocation.ParentWallet} {
			if walletId != 0 && GetRefWalletFromId(walletId) == nil {
				if err := NewRefWallet(walletId, capacity).Register(); err != nil {
					t.Fatalf("register wallet %d: %v", walletId, err)
				}
			}
		}
		allocationCopy := allocation
		if err := allocationCopy.Register(); err != nil {
			t.Fatalf("register allocation %d: %v", allocation.Id, err)
		}
	}
	for _, allocation := range allocations {
		if err := GetRefWalletFromId(allocation.BelongsTo).AddAllocation(allocation.Id); err != nil {
			t.Fatalf("add allocation %d: %v", allocation.Id, err)
		}
	}
	t.Cleanup(DestroyRefWalletHierarchy)
}

func TestReferenceFaultRepeatedCapacityRetirementCountsPriorFlow(t *testing.T) {
	now := time.Now()
	newReferenceFaultHierarchy(t, true,
		RefAllocation{Id: 1, BelongsTo: 1, Quota: 100, Start: now.Add(-time.Hour), End: now.Add(time.Hour)},
		RefAllocation{Id: 2, BelongsTo: 1, Quota: 100, Start: now.Add(-time.Hour), End: now.Add(2 * time.Hour)},
	)
	if err := (&RefProductCharge{WalletId: 1, Amount: 7, Ts: now}).Process(); err != nil {
		t.Fatalf("initial charge: %v", err)
	}
	if err := GetRefAllocationFromId(1).Retire(); err != nil {
		t.Fatalf("first retirement: %v", err)
	}
	if err := (&RefProductCharge{WalletId: 1, Amount: 2, Ts: now}).Process(); err != nil {
		t.Fatalf("second charge: %v", err)
	}
	if err := GetRefAllocationFromId(2).Retire(); err != nil {
		t.Fatalf("second retirement: %v", err)
	}

	// Seven units were attributed to allocation 1. Only the two units consumed
	// afterward can be newly attributed to allocation 2.
	assertKnownReferenceFault(t, "second allocation retired usage", int64(2), int64(9), GetRefAllocationFromId(2).RetiredUsage)
}

func TestReferenceFaultAddAllocationIgnoresOperationTime(t *testing.T) {
	GlobalRefAllocations = map[int64]*RefAllocation{}
	GlobalRefWallets = map[int64]*RefWallet{}
	t.Cleanup(DestroyRefWalletHierarchy)
	if err := NewRefWallet(1, true).Register(); err != nil {
		t.Fatal(err)
	}
	start := time.Now().Add(time.Hour)
	allocation := &RefAllocation{Id: 1, BelongsTo: 1, Quota: 10, Start: start, End: start.Add(time.Hour)}
	if err := allocation.Register(); err != nil {
		t.Fatal(err)
	}
	if err := (&RefAddAllocation{AllocationId: 1, Ts: start}).Process(); err != nil {
		t.Fatal(err)
	}

	actual := GetRefWalletFromId(1).AllocationsByParent[0].allocations[1]
	assertKnownReferenceFault(t, "allocation activity at the operation timestamp", true, false, actual)
}

func TestReferenceFaultAllocationValidityInterval(t *testing.T) {
	start := time.Unix(1_000, 0)
	allocation := RefAllocation{Start: start, End: start.Add(time.Hour)}

	t.Run("inclusive-start", func(t *testing.T) {
		assertKnownReferenceFault(t, "activity at the inclusive start", true, false, allocation.IsActive(start))
	})
	t.Run("exclusive-end", func(t *testing.T) {
		assertKnownReferenceFault(t, "activity at the exclusive end", false, true, allocation.IsActive(allocation.End))
	})
}

func TestReferenceFaultZeroQuotaRetirementLeavesAllocationActive(t *testing.T) {
	now := time.Now()
	newReferenceFaultHierarchy(t, true, RefAllocation{
		Id: 1, BelongsTo: 1, Quota: 0, Start: now.Add(-time.Hour), End: now.Add(time.Hour),
	})
	if err := GetRefAllocationFromId(1).Retire(); err != nil {
		t.Fatal(err)
	}

	actual := GetRefWalletFromId(1).AllocationsByParent[0].allocations[1]
	assertKnownReferenceFault(t, "zero-quota group membership after retirement", false, true, actual)
}

func TestReferenceFaultPeriodicUsageCanDecrease(t *testing.T) {
	now := time.Now()
	newReferenceFaultHierarchy(t, false, RefAllocation{
		Id: 1, BelongsTo: 1, Quota: 100, Start: now.Add(-time.Hour), End: now.Add(time.Hour),
	})
	if err := (&RefProductCharge{WalletId: 1, Amount: 10, Ts: now}).Process(); err != nil {
		t.Fatal(err)
	}
	if err := (&RefProductCharge{WalletId: 1, Amount: -5, Ts: now}).Process(); err != nil {
		t.Fatalf("reference unexpectedly rejected its known-invalid periodic decrease: %v", err)
	}

	// Lifetime periodic usage must remain monotonic without an explicit historic
	// correction operation, so the decrease should have left usage at ten.
	assertKnownReferenceFault(t, "periodic local usage after a decrease", int64(10), int64(5), GetRefWalletFromId(1).LocalUsage)
}

func TestReferenceFaultRootlessOverAllocationPropagatesUpstream(t *testing.T) {
	now := time.Now()
	newReferenceFaultHierarchy(t, true,
		RefAllocation{Id: 1, BelongsTo: 2, ParentWallet: 1, Quota: 10, Start: now.Add(-time.Hour), End: now.Add(time.Hour)},
		RefAllocation{Id: 2, BelongsTo: 3, ParentWallet: 2, Quota: 10, Start: now.Add(-time.Hour), End: now.Add(time.Hour)},
	)
	_ = (&RefProductCharge{WalletId: 3, Amount: 10, Ts: now}).Process()

	// Wallet 1 has no root entitlement. The synthetic excess path must not make
	// wallet 2 appear funded by wallet 1.
	actual := GetRefWalletFromId(2).AllocationsByParent[1].treeUsage
	assertKnownReferenceFault(t, "flow through a parent with no root entitlement", int64(0), int64(10), actual)
}

func TestReferenceFaultExpiredAllocationIsAddedAsActive(t *testing.T) {
	now := time.Now()
	newReferenceFaultHierarchy(t, true, RefAllocation{
		Id: 1, BelongsTo: 1, Quota: 10, Start: now.Add(-2 * time.Hour), End: now.Add(-time.Hour),
	})

	actual := GetRefWalletFromId(1).AllocationsByParent[0].allocations[1]
	assertKnownReferenceFault(t, "activity of an allocation created after its end", false, true, actual)
}

func TestReferenceFaultPreferredBalanceOverflows(t *testing.T) {
	start := time.Unix(1_000, 0)
	allocation := RefAllocation{Quota: math.MaxInt64, Start: start, End: start.Add(4 * time.Second)}
	expected := int64(math.MaxInt64 / 2)
	actual := allocation.PreferredBalance(start.Add(2 * time.Second))

	assertKnownReferenceFault(t, "preferred balance with a large quota", expected, int64(0), actual)
}
