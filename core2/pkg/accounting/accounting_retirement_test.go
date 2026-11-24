package accounting

import (
	"net/http"
	"testing"

	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/util"
)

func TestAccountingRetirementAcrossPeriods(t *testing.T) {
	for _, category := range []accapi.ProductCategory{capacityCategory, timeCategory} {
		t.Run(category.Name, func(t *testing.T) {
			e := newEnv(t, category, false)
			first := e.AllocateEx(0, 0, 10, 100, "user", "")
			second := e.AllocateEx(0, 10, 20, 100, "user", "")
			e.ReportAbs(1, "user", 40)
			e.Scan(10)

			if got := e.Bucket.AllocationsById[first].RetiredUsage; got != 40 {
				t.Fatalf("first retired usage = %d, want 40", got)
			}
			wallet := e.Bucket.WalletsByOwner[e.Owner("user").Id]
			if got := wallet.AllocationsByParent[internalGraphRoot].TreeUsage; got != 40 {
				t.Fatalf("flow after renewal = %d, want 40", got)
			}
			e.Scan(20)

			wantRetired, wantFlow := int64(0), int64(40)
			if e.Bucket.IsCapacityBased() {
				wantRetired, wantFlow = 40, 0
			}
			if got := e.Bucket.AllocationsById[second].RetiredUsage; got != wantRetired {
				t.Fatalf("second retired usage = %d, want %d", got, wantRetired)
			}
			if got := e.Bucket.AllocationsById[first].RetiredUsage; got != 40 {
				t.Fatalf("first retired usage after second retirement = %d, want 40", got)
			}
			if wallet.LocalUsage != 40 {
				t.Fatalf("local usage = %d, want 40", wallet.LocalUsage)
			}
			if got := wallet.AllocationsByParent[internalGraphRoot].TreeUsage; got != wantFlow {
				t.Fatalf("final group flow = %d, want %d", got, wantFlow)
			}
			for _, allocationId := range []accAllocId{first, second} {
				allocation := e.Bucket.AllocationsById[allocationId]
				if !allocation.Retired || allocation.RetiredQuota != 100 {
					t.Fatalf("allocation %d retired=%t quota=%d, want true/100", allocationId, allocation.Retired, allocation.RetiredQuota)
				}
			}
			if err := internalValidateAccountingTree(e.Bucket, e.Tm(20)); err != nil {
				t.Fatal(err)
			}
		})
	}
}

func TestAccountingCapacityRetirementSharesBatchBudget(t *testing.T) {
	for _, trigger := range []string{"scan", "report", "read"} {
		t.Run(trigger, func(t *testing.T) {
			e := newEnv(t, capacityCategory, false)
			previous := e.AllocateEx(0, 0, 5, 100, "user", "")
			first := e.AllocateEx(0, 5, 10, 100, "user", "")
			second := e.AllocateEx(0, 5, 10, 100, "user", "")
			e.ReportAbs(1, "user", 40)
			e.Scan(5)
			e.ReportAbs(6, "user", 150)
			wallet := e.Bucket.WalletsByOwner[e.Owner("user").Id]

			switch trigger {
			case "scan":
				e.Scan(10)
			case "report":
				e.ReportAbs(10, "user", 150)
			case "read":
				if _, ok := internalRetrieveWallet(e.Tm(10), wallet.Id, false); !ok {
					t.Fatal("wallet not found")
				}
			}

			for allocationId, want := range map[accAllocId]int64{previous: 40, first: 100, second: 50} {
				if got := e.Bucket.AllocationsById[allocationId].RetiredUsage; got != want {
					t.Errorf("allocation %d retired usage = %d, want %d", allocationId, got, want)
				}
			}
			if wallet.LocalUsage != 150 {
				t.Errorf("local usage = %d, want 150", wallet.LocalUsage)
			}
			if got := wallet.AllocationsByParent[internalGraphRoot].TreeUsage; got != 0 {
				t.Errorf("retired capacity flow = %d, want 0", got)
			}
			if err := internalValidateAccountingTree(e.Bucket, e.Tm(10)); err != nil {
				t.Fatal(err)
			}
		})
	}
}

func TestAccountingCapacityRetirementOnCommit(t *testing.T) {
	e := newEnv(t, capacityCategory, false)
	previous := e.AllocateEx(0, 0, 5, 100, "user", "")
	e.AllocateEx(0, 5, 20, 100, "user", "")
	e.ReportAbs(1, "user", 40)
	e.Scan(5)
	allocationId := e.AllocateEx(6, 0, 6, 100, "user", "")

	for _, id := range []accAllocId{previous, allocationId} {
		if got := e.Bucket.AllocationsById[id].RetiredUsage; got != 40 {
			t.Errorf("allocation %d retired usage = %d, want 40", id, got)
		}
	}
	wallet := e.Bucket.WalletsByOwner[e.Owner("user").Id]
	if wallet.LocalUsage != 40 || wallet.AllocationsByParent[internalGraphRoot].TreeUsage != 40 {
		t.Fatalf("local usage/flow = %d/%d, want 40/40", wallet.LocalUsage, wallet.AllocationsByParent[internalGraphRoot].TreeUsage)
	}
}

func TestAccountingCapacityRetirementOnUpdate(t *testing.T) {
	e := newEnv(t, capacityCategory, false)
	e.AllocateEx(0, 0, 100, 1_000, "parent", "")
	previous := e.AllocateEx(0, 0, 5, 100, "user", "parent")
	current := e.AllocateEx(0, 5, 20, 100, "user", "parent")
	e.ReportAbs(1, "user", 40)
	e.Scan(5)
	err := e.UpdateAllocation(
		t,
		e.Owner("parent"),
		6,
		current,
		util.OptNone[int64](),
		util.OptNone[fndapi.Timestamp](),
		util.OptValue(fndapi.Timestamp(e.Tm(6))),
	)
	if err != nil {
		t.Fatal(err)
	}
	for _, id := range []accAllocId{previous, current} {
		if got := e.Bucket.AllocationsById[id].RetiredUsage; got != 40 {
			t.Errorf("allocation %d retired usage = %d, want 40", id, got)
		}
	}
	wallet := e.Bucket.WalletsByOwner[e.Owner("user").Id]
	parent := e.Bucket.WalletsByOwner[e.Owner("parent").Id]
	if wallet.LocalUsage != 40 || wallet.AllocationsByParent[parent.Id].TreeUsage != 0 || parent.ChildrenUsage[wallet.Id] != 0 {
		t.Fatalf("local usage/group flow/parent mirror = %d/%d/%d, want 40/0/0", wallet.LocalUsage, wallet.AllocationsByParent[parent.Id].TreeUsage, parent.ChildrenUsage[wallet.Id])
	}
	if err := internalValidateAccountingTree(e.Bucket, e.Tm(6)); err != nil {
		t.Fatal(err)
	}
}

func TestAccountingAllocationStartUpdateBoundary(t *testing.T) {
	for _, category := range []accapi.ProductCategory{capacityCategory, timeCategory} {
		t.Run(category.Name, func(t *testing.T) {
			for _, tc := range []struct {
				name     string
				now      int
				newStart int
				allowed  bool
			}{
				{name: "before", now: 9, newStart: 15, allowed: true},
				{name: "at-start", now: 10, newStart: 15},
				{name: "after", now: 11, newStart: 15},
				{name: "unchanged", now: 10, newStart: 10, allowed: true},
			} {
				t.Run(tc.name, func(t *testing.T) {
					e := newEnv(t, category, false)
					e.AllocateEx(0, 0, 100, 1_000, "parent", "")
					allocationId := e.AllocateEx(0, 10, 30, 100, "user", "parent")
					e.ReportAbs(9, "user", 40)
					err := e.UpdateAllocation(
						t,
						e.Owner("parent"),
						tc.now,
						allocationId,
						util.OptNone[int64](),
						util.OptValue(fndapi.Timestamp(e.Tm(tc.newStart))),
						util.OptNone[fndapi.Timestamp](),
					)
					wantStart := 10
					if tc.allowed {
						if err != nil {
							t.Fatal(err)
						}
						wantStart = tc.newStart
					} else if err == nil || err.StatusCode != http.StatusForbidden {
						t.Fatalf("start update error = %v, want HTTP 403", err)
					}
					allocation := e.Bucket.AllocationsById[allocationId]
					if !allocation.Start.Equal(e.Tm(wantStart)) {
						t.Fatalf("start = %s, want %s", allocation.Start, e.Tm(wantStart))
					}
					wallet := e.Bucket.WalletsByOwner[e.Owner("user").Id]
					if wallet.LocalUsage != 40 {
						t.Fatalf("local usage = %d, want 40", wallet.LocalUsage)
					}
					wantHeadroom := int64(60)
					if tc.now < wantStart {
						wantHeadroom = 0
					}
					if got := lInternalMaxUsable(e.Bucket, e.Tm(tc.now), wallet); got != wantHeadroom {
						t.Fatalf("headroom = %d, want %d", got, wantHeadroom)
					}
					if err := internalValidateAccountingTree(e.Bucket, e.Tm(tc.now)); err != nil {
						t.Fatal(err)
					}
				})
			}
		})
	}
}
