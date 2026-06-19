package accounting

import (
	"testing"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func (e *lowTestEnv) setPolicy(policy PromisePolicy) {
	e.t.Helper()
	e.tree().Policy = policy
}

func (e *lowTestEnv) addPromise(parent string, child string, start int, end int, quota int64) PromiseId {
	e.t.Helper()
	parentWallet := e.wallet(parent)
	childWallet := e.wallet(child)
	id := PromiseId(promiseGlobals.PromiseIdAcc.Add(1))
	promise := &Promise{
		Id:     id,
		Parent: parentWallet,
		Child:  childWallet,
		Start:  e.tm(start),
		End:    e.tm(end),
		Quota:  quota,
	}

	promiseTree := &e.tree().PromiseTree
	promiseTree.PromisesById[id] = promise
	promiseTree.PromisesByParent[parentWallet] = append(promiseTree.PromisesByParent[parentWallet], id)
	promiseTree.PromisesByChild[childWallet] = append(promiseTree.PromisesByChild[childWallet], id)
	return id
}

func (e *lowTestEnv) reconcile(at int, owner string, minimum int64) {
	e.t.Helper()
	PromiseReconcile(e.tm(at), e.categoryId, e.owner(owner), minimum)
	e.assertValid()
}

func (e *lowTestEnv) promiseAllocations(promiseId PromiseId) []*Allocation {
	e.t.Helper()
	allocations := []*Allocation{}
	for _, allocation := range e.tree().AllocationsById {
		if allocation.Promise.Present && allocation.Promise.Value == promiseId {
			allocations = append(allocations, allocation)
		}
	}
	return allocations
}

func (e *lowTestEnv) promiseHead(promiseId PromiseId, at int) *Allocation {
	e.t.Helper()
	promise := e.tree().PromiseTree.PromisesById[promiseId]
	heads := promiseFindMaterializationHeads(e.tm(at), e.tree(), promise)
	head := heads.UpdateFirst()
	if !head.Present {
		e.t.Fatalf("promise %d has no head", promiseId)
	}
	return head.Value
}

func (e *lowTestEnv) promiseMaterializedQuota(at int, promiseId PromiseId) int64 {
	e.t.Helper()
	promise := e.tree().PromiseTree.PromisesById[promiseId]
	wallet := e.tree().WalletsById[promise.Child]
	if wallet == nil {
		return 0
	}

	total := int64(0)
	for _, allocationId := range wallet.Allocations {
		allocation := e.tree().AllocationsById[allocationId]
		if allocation == nil || !allocation.Promise.Present || allocation.Promise.Value != promise.Id {
			continue
		}
		if e.tree().IsCapacityBased() && !e.tm(at).Before(allocation.End) {
			continue
		}
		total += allocation.QuotaSelf + allocation.QuotaChildren
	}
	return total
}

func (e *lowTestEnv) setAllocationPeriod(at int, allocationId AllocationId, start int, end int) {
	e.t.Helper()
	allocation := e.tree().AllocationsById[allocationId]
	if allocation == nil {
		e.t.Fatalf("unknown allocation %v", allocationId)
	}
	startTime := e.tm(start)
	endTime := e.tm(end)
	if startTime.After(endTime) {
		e.t.Fatalf("invalid allocation period")
	}
	if allocation.Parent.Present {
		parent := e.tree().AllocationsById[allocation.Parent.Value]
		if parent == nil || startTime.Before(parent.Start) || endTime.After(parent.End) {
			e.t.Fatalf("allocation period outside parent period")
		}
	}
	for _, childId := range allocation.Children {
		child := e.tree().AllocationsById[childId]
		if child != nil && (child.Start.Before(startTime) || child.End.After(endTime)) {
			e.t.Fatalf("allocation period outside child period")
		}
	}

	allocationMutate(e.tm(at), e.tree(), allocationId, func(alloc *Allocation, parent util.Option[*Allocation]) {
		alloc.Start = startTime
		alloc.End = endTime
	})
}

func assertPromiseAllocation(t *testing.T, allocation *Allocation, self int64, children int64, start time.Time, end time.Time) {
	t.Helper()
	if allocation.QuotaSelf != self || allocation.QuotaChildren != children || !allocation.Start.Equal(start) || !allocation.End.Equal(end) {
		t.Fatalf(
			"allocation = self:%d children:%d start:%s end:%s, want self:%d children:%d start:%s end:%s",
			allocation.QuotaSelf,
			allocation.QuotaChildren,
			allocation.Start,
			allocation.End,
			self,
			children,
			start,
			end,
		)
	}
}

func TestPromiseReconcileOverbookingMaterializesAvailableCapacity(t *testing.T) {
	tests := []struct {
		name   string
		setup  func(*lowTestEnv) (PromiseId, PromiseId)
		assert func(*lowTestEnv, PromiseId, PromiseId)
	}{
		{
			name: "minimum request steals unused sibling capacity",
			setup: func(e *lowTestEnv) (PromiseId, PromiseId) {
				e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
				e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
				first := e.addPromise("root", "a", 0, 10, 80)
				second := e.addPromise("root", "b", 0, 10, 80)
				return first, second
			},
			assert: func(e *lowTestEnv, first PromiseId, second PromiseId) {
				assertPromiseAllocation(t, e.promiseHead(first, 1), 0, 0, e.tm(1), e.tm(10))
				assertPromiseAllocation(t, e.promiseHead(second, 1), 80, 0, e.tm(1), e.tm(10))
				e.assertAllocation("root", 0, 100, 0, 80)
			},
		},
		{
			name: "single promise remains under-covered when root capacity is too small",
			setup: func(e *lowTestEnv) (PromiseId, PromiseId) {
				e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
				e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 40, Self: 0, Children: 40})
				promise := e.addPromise("root", "child", 0, 10, 100)
				return promise, 0
			},
			assert: func(e *lowTestEnv, promise PromiseId, _ PromiseId) {
				assertPromiseAllocation(t, e.promiseHead(promise, 1), 40, 0, e.tm(1), e.tm(10))
				e.assertAllocation("root", 0, 40, 0, 40)
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
			first, second := tt.setup(e)
			if second == 0 {
				e.reconcile(1, "child", 60)
			} else {
				e.reconcile(1, "a", 80)
				e.reconcile(1, "b", 80)
			}
			tt.assert(e, first, second)
			e.assertValid()
		})
	}
}

func TestPromiseReconcileMaterializesAcrossMultipleParentAllocations(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
	e.add(lowAllocSpec{Name: "root-a", Wallet: "root", Start: 0, End: 10, Quota: 50, Self: 0, Children: 50})
	e.add(lowAllocSpec{Name: "root-b", Wallet: "root", Start: 0, End: 10, Quota: 50, Self: 0, Children: 50})
	promise := e.addPromise("root", "child", 0, 10, 100)

	e.reconcile(1, "child", 100)

	allocations := e.promiseAllocations(promise)
	if len(allocations) != 2 {
		t.Fatalf("promise allocations = %d, want 2", len(allocations))
	}

	byParent := map[AllocationId]*Allocation{}
	total := int64(0)
	for _, allocation := range allocations {
		if !allocation.Parent.Present {
			t.Fatalf("promise allocation %d has no parent", allocation.Id)
		}
		byParent[allocation.Parent.Value] = allocation
		total += allocation.QuotaSelf + allocation.QuotaChildren
	}
	if total != 100 {
		t.Fatalf("promise materialized total = %d, want 100", total)
	}

	rootAChild := byParent[e.allocs["root-a"]]
	rootBChild := byParent[e.allocs["root-b"]]
	if rootAChild == nil || rootBChild == nil {
		t.Fatalf("promise allocation parents = %#v, want root-a and root-b", byParent)
	}
	assertPromiseAllocation(t, rootAChild, 50, 0, e.tm(1), e.tm(10))
	assertPromiseAllocation(t, rootBChild, 50, 0, e.tm(1), e.tm(10))
	e.assertAllocation("root-a", 0, 50, 0, 50)
	e.assertAllocation("root-b", 0, 50, 0, 50)

	if success := e.report(1, "child", 100); !success && walletMaxUsable(e.tm(1), e.tree(), e.tree().WalletsById[e.wallet("child")]) != 0 {
		t.Fatalf("usage report failed without leaving child exactly at zero remaining usable capacity")
	}
	e.assertWalletConsumed("child", 100)
	e.assertValid()
}

func TestPromiseReconcileUpdateFirstGrowthAndShrink(t *testing.T) {
	tests := []struct {
		name   string
		setup  func(*lowTestEnv) PromiseId
		steps  func(*lowTestEnv, PromiseId)
		assert func(*lowTestEnv, PromiseId)
	}{
		{
			name: "same active allocation grows in place through root slack",
			setup: func(e *lowTestEnv) PromiseId {
				e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
				e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 20, Children: 80})
				return e.addPromise("root", "child", 0, 10, 100)
			},
			steps: func(e *lowTestEnv, promise PromiseId) {
				e.reconcile(1, "child", 30)
				firstHead := e.promiseHead(promise, 1).Id
				e.reconcile(2, "child", 90)
				if e.promiseHead(promise, 2).Id != firstHead {
					t.Fatalf("promise created a successor while active head was editable")
				}
			},
			assert: func(e *lowTestEnv, promise PromiseId) {
				assertPromiseAllocation(t, e.promiseHead(promise, 2), 90, 0, e.tm(1), e.tm(10))
				e.assertAllocation("root", 10, 90, 0, 90)
			},
		},
		{
			name: "minimum reconciliation shrinks after meaningful decrease",
			setup: func(e *lowTestEnv) PromiseId {
				e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
				e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
				return e.addPromise("root", "child", 0, 10, 100)
			},
			steps: func(e *lowTestEnv, promise PromiseId) {
				e.reconcile(1, "child", 80)
				e.reconcile(2, "child", 10)
			},
			assert: func(e *lowTestEnv, promise PromiseId) {
				assertPromiseAllocation(t, e.promiseHead(promise, 2), 10, 0, e.tm(1), e.tm(10))
				e.assertAllocation("root", 0, 100, 0, 10)
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
			promise := tt.setup(e)
			tt.steps(e, promise)
			tt.assert(e, promise)
			e.assertValid()
		})
	}
}

func TestPromiseReconcileMinimumRequestStealsUnusedAncestorSelf(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
	parentPromise := e.addPromise("root", "parent", 0, 10, 100)
	childPromise := e.addPromise("parent", "child", 0, 10, 100)

	e.reconcile(1, "parent", 80)
	assertPromiseAllocation(t, e.promiseHead(parentPromise, 1), 80, 0, e.tm(1), e.tm(10))
	e.assertAllocation("root", 0, 100, 0, 80)

	e.reconcile(2, "child", 80)
	assertPromiseAllocation(t, e.promiseHead(parentPromise, 2), 0, 80, e.tm(1), e.tm(10))
	assertPromiseAllocation(t, e.promiseHead(childPromise, 2), 80, 0, e.tm(2), e.tm(10))
	e.assertAllocation("root", 0, 100, 0, 80)
}

func TestPromiseReconcileMinimumRequestStealsFromSiblingSubtree(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
	rootToA := e.addPromise("root", "root-a", 0, 10, 100)
	aToA1 := e.addPromise("root-a", "root-a-a1", 0, 10, 100)
	rootToB := e.addPromise("root", "root-b", 0, 10, 100)
	bToB1 := e.addPromise("root-b", "root-b-b1", 0, 10, 100)

	e.reconcile(1, "root-a-a1", 70)
	assertPromiseAllocation(t, e.promiseHead(rootToA, 1), 0, 70, e.tm(1), e.tm(10))
	assertPromiseAllocation(t, e.promiseHead(aToA1, 1), 70, 0, e.tm(1), e.tm(10))
	e.assertAllocation("root", 0, 100, 0, 70)

	e.reconcile(2, "root-b", 30)
	assertPromiseAllocation(t, e.promiseHead(rootToB, 2), 30, 0, e.tm(2), e.tm(10))
	e.assertAllocation("root", 0, 100, 0, 100)

	e.reconcile(3, "root-b-b1", 70)
	assertPromiseAllocation(t, e.promiseHead(rootToA, 3), 0, 0, e.tm(1), e.tm(10))
	assertPromiseAllocation(t, e.promiseHead(aToA1, 3), 0, 0, e.tm(1), e.tm(10))
	assertPromiseAllocation(t, e.promiseHead(rootToB, 3), 0, 70, e.tm(2), e.tm(10))
	assertPromiseAllocation(t, e.promiseHead(bToB1, 3), 70, 0, e.tm(3), e.tm(10))
	e.assertAllocation("root", 0, 100, 0, 70)
}

func TestPromiseCapacityDecreaseReleasesOnFullReconcile(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
	first := e.addPromise("root", "first", 0, 10, 100)
	second := e.addPromise("root", "second", 0, 10, 100)

	e.reconcile(1, "first", 80)
	if success := e.report(1, "first", 80); !success {
		t.Fatalf("first initial usage failed")
	}
	e.reconcile(1, "second", 80)
	assertPromiseAllocation(t, e.promiseHead(first, 1), 80, 0, e.tm(1), e.tm(10))
	assertPromiseAllocation(t, e.promiseHead(second, 1), 20, 0, e.tm(1), e.tm(10))
	e.assertAllocation("root", 0, 100, 0, 100)
	if success := e.report(1, "second", 20); !success {
		t.Fatalf("second initial usage failed")
	}

	if success := e.report(2, "first", 10); !success {
		t.Fatalf("first usage decrease failed")
	}
	assertPromiseAllocation(t, e.promiseHead(first, 2), 10, 0, e.tm(1), e.tm(10))
	e.assertAllocation("root", 0, 100, 0, 30)

	if success := e.report(2, "second", 80); !success {
		t.Fatalf("second usage increase failed")
	}
	assertPromiseAllocation(t, e.promiseHead(second, 2), 80, 0, e.tm(1), e.tm(10))
	if consumed := e.promiseHead(second, 2).ConsumedSelf; consumed != 80 {
		t.Fatalf("second consumed = %d, want 80", consumed)
	}
	e.assertAllocation("root", 0, 100, 0, 90)
}

func TestPromiseCapacityDecreaseUsesGrowthStepAsShrinkThreshold(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.setPolicy(PromisePolicy{GrowthStep: 25, TrendAlphaBasisPoints: 10000})
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
	promise := e.addPromise("root", "child", 0, 10, 100)

	if success := e.report(1, "child", 80); !success {
		t.Fatalf("initial usage failed")
	}
	assertPromiseAllocation(t, e.promiseHead(promise, 1), 100, 0, e.tm(1), e.tm(10))
	e.assertAllocation("root", 0, 100, 0, 100)

	if success := e.report(2, "child", 60); !success {
		t.Fatalf("small decrease failed")
	}
	assertPromiseAllocation(t, e.promiseHead(promise, 2), 100, 0, e.tm(1), e.tm(10))
	e.assertAllocation("root", 0, 100, 0, 100)

	if success := e.report(3, "child", 30); !success {
		t.Fatalf("meaningful decrease failed")
	}
	assertPromiseAllocation(t, e.promiseHead(promise, 3), 50, 0, e.tm(1), e.tm(10))
	e.assertAllocation("root", 0, 100, 0, 50)
}

func TestPromiseCycleReconcilePreservesInvariants(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
	rootToA := e.addPromise("root", "a", 0, 10, 100)
	aToB := e.addPromise("a", "b", 0, 10, 100)
	bToC := e.addPromise("b", "c", 0, 10, 100)
	cToA := e.addPromise("c", "a", 0, 10, 100)

	e.reconcile(1, "a", 50)
	e.reconcile(1, "b", 50)
	e.reconcile(1, "c", 50)
	PromiseReconcile(e.tm(2), e.categoryId, e.owner("root"), 0)
	e.assertValid()

	for _, promise := range []PromiseId{rootToA, aToB, bToC, cToA} {
		allocations := e.promiseAllocations(promise)
		if len(allocations) > 1 {
			t.Fatalf("promise %d allocations = %d, want at most one", promise, len(allocations))
		}
	}
}

func TestPromiseCycleUsageReportsPreserveInvariants(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.setPolicy(PromisePolicy{GrowthStep: 10, TrendAlphaBasisPoints: 10000})
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 20, Quota: 100, Self: 0, Children: 100})
	e.addPromise("root", "a", 0, 20, 100)
	e.addPromise("a", "b", 0, 20, 100)
	e.addPromise("b", "a", 0, 20, 100)

	for _, step := range []struct {
		at     int
		wallet string
		usage  int64
	}{
		{1, "a", 80},
		{2, "b", 60},
		{3, "a", 20},
		{4, "b", 10},
		{5, "a", 90},
	} {
		_, err := UsageReport(e.tm(step.at), accapi.ReportUsageRequest{
			Owner:        e.owner(step.wallet),
			CategoryIdV2: e.categoryId,
			Usage:        step.usage,
		})
		if err != nil {
			t.Fatalf("report usage for %s at %d: %v", step.wallet, step.at, err)
		}
		e.assertValid()
	}
}

func TestPromiseReconcilePropagatesChildDemandThroughPeriodicPasses(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 20, Children: 80})
	parentPromise := e.addPromise("root", "parent", 0, 10, 100)
	childPromise := e.addPromise("parent", "child", 0, 10, 100)

	e.reconcile(1, "child", 60)

	assertPromiseAllocation(t, e.promiseHead(parentPromise, 1), 0, 60, e.tm(1), e.tm(10))
	assertPromiseAllocation(t, e.promiseHead(childPromise, 1), 60, 0, e.tm(1), e.tm(10))
	e.assertAllocation("root", 20, 80, 0, 60)
	parentHead := e.promiseHead(parentPromise, 1)
	if parentHead.ReservedChildren != 60 {
		t.Fatalf("parent promise allocation reserved children = %d, want 60", parentHead.ReservedChildren)
	}
}

func TestPromiseCalculateTargetSplitUsesSlackAndRounding(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.setPolicy(PromisePolicy{MinSlack: 5, GrowthStep: 10, TrendAlphaBasisPoints: 10000})
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
	promise := e.addPromise("root", "child", 0, 10, 100)

	e.reconcile(1, "child", 10)
	e.reconcile(2, "child", 30)

	head := e.promiseHead(promise, 2)
	assertPromiseAllocation(t, head, 40, 0, e.tm(1), e.tm(10))
}

func TestPromiseReconcileSuccessorAndReportHeadroom(t *testing.T) {
	tests := []struct {
		name   string
		setup  func(*lowTestEnv) PromiseId
		steps  func(*lowTestEnv, PromiseId)
		assert func(*lowTestEnv, PromiseId)
	}{
		{
			name: "retired head creates successor without a gap",
			setup: func(e *lowTestEnv) PromiseId {
				e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
				e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
				return e.addPromise("root", "child", 0, 10, 100)
			},
			steps: func(e *lowTestEnv, promise PromiseId) {
				e.reconcile(1, "child", 50)
				head := e.promiseHead(promise, 1)
				e.setAllocationPeriod(1, head.Id, 0, 5)
				e.reconcile(6, "child", 50)
			},
			assert: func(e *lowTestEnv, promise PromiseId) {
				allocations := e.promiseAllocations(promise)
				if len(allocations) != 2 {
					t.Fatalf("promise allocations = %d, want 2", len(allocations))
				}
				head := e.promiseHead(promise, 6)
				assertPromiseAllocation(t, head, 50, 0, e.tm(5), e.tm(10))
			},
		},
		{
			name: "report reconciliation shrinks to lowered demand plus slack",
			setup: func(e *lowTestEnv) PromiseId {
				e.setPolicy(PromisePolicy{MinSlack: 20, TrendAlphaBasisPoints: 10000})
				e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
				return e.addPromise("root", "child", 0, 10, 100)
			},
			steps: func(e *lowTestEnv, promise PromiseId) {
				e.reconcile(1, "child", 80)
				e.report(2, "child", 30)
			},
			assert: func(e *lowTestEnv, promise PromiseId) {
				assertPromiseAllocation(t, e.promiseHead(promise, 3), 50, 0, e.tm(1), e.tm(10))
				e.assertAllocation("root", 0, 100, 0, 50)
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
			promise := tt.setup(e)
			tt.steps(e, promise)
			tt.assert(e, promise)
			e.assertValid()
		})
	}
}

func TestPromiseReportUsageMultiplePromisesForSameChild(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
	first := e.addPromise("root", "child", 0, 10, 50)
	second := e.addPromise("root", "child", 0, 10, 50)

	e.report(1, "child", 90)

	firstHead := e.promiseHead(first, 1)
	secondHead := e.promiseHead(second, 1)
	assertPromiseAllocation(t, firstHead, 50, 0, e.tm(1), e.tm(10))
	assertPromiseAllocation(t, secondHead, 50, 0, e.tm(1), e.tm(10))
	if firstHead.ConsumedSelf != 50 || secondHead.ConsumedSelf != 40 {
		t.Fatalf("consumption split = %d/%d, want 50/40", firstHead.ConsumedSelf, secondHead.ConsumedSelf)
	}
	e.assertAllocation("root", 0, 100, 0, 100)
	e.assertWalletConsumed("child", 90)
}

func TestPromiseReportUsageNewPromiseOnlyMaterializesResidualDemand(t *testing.T) {
	tests := []struct {
		name      string
		frequency accapi.AccountingFrequency
	}{
		{name: "capacity", frequency: accapi.AccountingFrequencyOnce},
		{name: "non-capacity", frequency: accapi.AccountingFrequencyPeriodicHour},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			e := newLowTestEnv(t, tt.frequency)
			e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
			e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 500_000, Self: 0, Children: 500_000})
			e.addPromise("root", "child", 0, 10, 150_000)
			e.addPromise("root", "child", 0, 10, 150_000)
			e.addPromise("root", "child", 0, 10, 150_000)

			e.report(1, "child", 450_000)
			newPromise := e.addPromise("root", "child", 0, 10, 50_000)
			e.report(2, "child", 451_000)

			newHead := e.promiseHead(newPromise, 2)
			assertPromiseAllocation(t, newHead, 1_000, 0, e.tm(2), e.tm(10))
			if newHead.ConsumedSelf != 1_000 {
				t.Fatalf("new promise consumed = %d, want 1000", newHead.ConsumedSelf)
			}
			e.assertAllocation("root", 0, 500_000, 0, 451_000)
			e.assertWalletConsumed("child", 451_000)
		})
	}
}

func TestPromiseReportUsageSkipsReconcileWhenHeadroomExists(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.setPolicy(PromisePolicy{MinSlack: 10, TrendAlphaBasisPoints: 10000})
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
	aPromise := e.addPromise("root", "a", 0, 10, 50)
	bPromise := e.addPromise("root", "b", 0, 10, 50)

	e.reconcile(1, "a", 40)
	e.report(2, "a", 40)

	assertPromiseAllocation(t, e.promiseHead(aPromise, 2), 50, 0, e.tm(1), e.tm(10))
	if got := len(e.promiseAllocations(bPromise)); got != 0 {
		t.Fatalf("unrelated promise allocations = %d, want 0", got)
	}
	e.assertAllocation("root", 0, 100, 0, 50)
}

func TestPromiseReportUsageSingleRootMultipleL1ChildrenOverbooked(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
	aPromise := e.addPromise("root", "a", 0, 10, 60)
	bPromise := e.addPromise("root", "b", 0, 10, 60)

	e.report(1, "a", 60)
	e.report(2, "b", 60)

	aHead := e.promiseHead(aPromise, 2)
	bHead := e.promiseHead(bPromise, 2)
	assertPromiseAllocation(t, aHead, 60, 0, e.tm(1), e.tm(10))
	assertPromiseAllocation(t, bHead, 40, 0, e.tm(2), e.tm(10))
	if bHead.ConsumedSelf != 60 {
		t.Fatalf("overbooked b consumption = %d, want 60", bHead.ConsumedSelf)
	}
	e.assertAllocation("root", 0, 100, 0, 100)
	e.assertWalletConsumed("a", 60)
	e.assertWalletConsumed("b", 60)
}

func TestPromiseReportUsageOverbookedBelowPromiseQuotaLocksWallet(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
	aPromise := e.addPromise("root", "a", 0, 10, 60)
	bPromise := e.addPromise("root", "b", 0, 10, 60)

	e.report(1, "a", 60)
	if success := e.report(2, "b", 50); success {
		t.Fatalf("b report succeeded, want wallet locked after exceeding materialized capacity")
	}

	aHead := e.promiseHead(aPromise, 2)
	bHead := e.promiseHead(bPromise, 2)
	assertPromiseAllocation(t, aHead, 60, 0, e.tm(1), e.tm(10))
	assertPromiseAllocation(t, bHead, 40, 0, e.tm(2), e.tm(10))
	if aHead.ConsumedSelf != 60 || bHead.ConsumedSelf != 50 {
		t.Fatalf("consumption split = %d/%d, want 60/50", aHead.ConsumedSelf, bHead.ConsumedSelf)
	}
	e.assertAllocation("root", 0, 100, 0, 100)
	e.assertWalletConsumed("a", 60)
	e.assertWalletConsumed("b", 50)

	bWallet := e.tree().WalletsById[e.wallet("b")]
	if bWallet == nil || !bWallet.Locked {
		t.Fatalf("b wallet locked = %v, want true", bWallet != nil && bWallet.Locked)
	}

	provider := rpc.Actor{Username: fndapi.ProviderSubjectPrefix + e.categoryId.Provider, Role: rpc.RoleProvider}
	usable, err := WalletsCheckProviderUsableAt(e.tm(2), provider, e.owner("b"), e.categoryId)
	if err != nil || usable.MaxUsable != 0 {
		t.Fatalf("provider usable = %#v err=%v, want 0/nil", usable, err)
	}
}

func TestPromiseReportUsageSingleL2ChildMultipleL1Parents(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
	l1aPromise := e.addPromise("root", "l1a", 0, 10, 50)
	l1bPromise := e.addPromise("root", "l1b", 0, 10, 50)
	l2FromA := e.addPromise("l1a", "l2", 0, 10, 50)
	l2FromB := e.addPromise("l1b", "l2", 0, 10, 50)

	e.report(1, "l2", 70)

	assertPromiseAllocation(t, e.promiseHead(l1aPromise, 1), 0, 50, e.tm(1), e.tm(10))
	assertPromiseAllocation(t, e.promiseHead(l1bPromise, 1), 0, 50, e.tm(1), e.tm(10))
	aHead := e.promiseHead(l2FromA, 1)
	bHead := e.promiseHead(l2FromB, 1)
	assertPromiseAllocation(t, aHead, 50, 0, e.tm(1), e.tm(10))
	assertPromiseAllocation(t, bHead, 50, 0, e.tm(1), e.tm(10))
	if aHead.ConsumedSelf != 50 || bHead.ConsumedSelf != 20 {
		t.Fatalf("l2 consumption split = %d/%d, want 50/20", aHead.ConsumedSelf, bHead.ConsumedSelf)
	}
	e.assertAllocation("root", 0, 100, 0, 100)
	e.assertWalletConsumed("l2", 70)
}

func TestPromiseReportUsageRetirementUpDownAndOverQuota(t *testing.T) {
	t.Run("usage can go over promise quota then shrink immediately on meaningful decrease", func(t *testing.T) {
		e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
		e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
		e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
		promise := e.addPromise("root", "child", 0, 10, 100)

		e.report(1, "child", 120)
		head := e.promiseHead(promise, 1)
		assertPromiseAllocation(t, head, 100, 0, e.tm(1), e.tm(10))
		if head.ConsumedSelf != 120 {
			t.Fatalf("over quota consumption = %d, want 120", head.ConsumedSelf)
		}

		e.report(2, "child", 40)
		head = e.promiseHead(promise, 2)
		assertPromiseAllocation(t, head, 40, 0, e.tm(1), e.tm(10))
		if head.ConsumedSelf != 40 {
			t.Fatalf("lowered consumption = %d, want 40", head.ConsumedSelf)
		}
		e.assertAllocation("root", 0, 100, 0, 40)
	})

	t.Run("retired promise allocation releases parent and successor can grow", func(t *testing.T) {
		e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
		e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
		e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 20, Quota: 100, Self: 0, Children: 100})
		promise := e.addPromise("root", "child", 0, 20, 100)

		e.report(1, "child", 80)
		head := e.promiseHead(promise, 1)
		e.setAllocationPeriod(1, head.Id, 0, 5)
		e.report(6, "child", 30)
		oldHead := e.tree().AllocationsById[head.Id]
		if oldHead.QuotaSelf != 0 || oldHead.ConsumedSelf != 0 {
			t.Fatalf("retired allocation = self:%d consumed:%d, want 0/0", oldHead.QuotaSelf, oldHead.ConsumedSelf)
		}

		successor := e.promiseHead(promise, 6)
		assertPromiseAllocation(t, successor, 30, 0, e.tm(5), e.tm(20))
		e.report(7, "child", 110)
		successor = e.promiseHead(promise, 7)
		assertPromiseAllocation(t, successor, 100, 0, e.tm(5), e.tm(20))
		if successor.ConsumedSelf != 110 {
			t.Fatalf("successor over quota consumption = %d, want 110", successor.ConsumedSelf)
		}
		e.assertAllocation("root", 0, 100, 0, 100)
	})
}

func TestPromiseReportUsagePromiseSpansGaplessYearlyRootAllocations(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})

	// Treat each test time unit as a calendar month. The root allocations cover two gapless calendar years.
	e.add(lowAllocSpec{Name: "root-year-0", Wallet: "root", Start: 0, End: 12, Quota: 100, Self: 0, Children: 100})
	e.add(lowAllocSpec{Name: "root-year-1", Wallet: "root", Start: 12, End: 24, Quota: 100, Self: 0, Children: 100})
	promise := e.addPromise("root", "sub-project", 5, 17, 100)

	e.report(5, "sub-project", 40)
	first := e.promiseHead(promise, 5)
	assertPromiseAllocation(t, first, 40, 0, e.tm(5), e.tm(12))
	if !first.Parent.Present || first.Parent.Value != e.allocs["root-year-0"] {
		t.Fatalf("first materialization parent = %#v, want root-year-0", first.Parent)
	}

	e.report(12, "sub-project", 40)
	second := e.promiseHead(promise, 12)
	assertPromiseAllocation(t, second, 40, 0, e.tm(12), e.tm(17))
	if !second.Parent.Present || second.Parent.Value != e.allocs["root-year-1"] {
		t.Fatalf("second materialization parent = %#v, want root-year-1", second.Parent)
	}
	if !first.End.Equal(second.Start) {
		t.Fatalf("promise materializations have a gap: first end %s, second start %s", first.End, second.Start)
	}

	e.assertAllocation("root-year-0", 0, 0, 0, 0)
	e.assertAllocation("root-year-1", 0, 100, 0, 40)
	e.assertWalletConsumed("sub-project", 40)
	e.assertValid()
}

func TestPromiseQuotaCountsRetiredMaterializationsByAccountingMode(t *testing.T) {
	t.Run("non-capacity quota is lifetime budget", func(t *testing.T) {
		e := newLowTestEnv(t, accapi.AccountingFrequencyPeriodicHour)
		e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
		e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 20, Quota: 200, Self: 0, Children: 200})
		promise := e.addPromise("root", "child", 0, 20, 100)

		e.report(1, "child", 80)
		first := e.promiseHead(promise, 1)
		assertPromiseAllocation(t, first, 80, 0, e.tm(1), e.tm(20))
		e.setAllocationPeriod(1, first.Id, 0, 5)

		e.report(6, "child", 100)
		second := e.promiseHead(promise, 6)
		assertPromiseAllocation(t, second, 20, 0, e.tm(5), e.tm(20))
		if first.Id == second.Id {
			t.Fatalf("expected successor allocation after retirement")
		}
		if got := e.promiseMaterializedQuota(6, promise); got != 100 {
			t.Fatalf("materialized quota = %d, want 100", got)
		}
	})

	t.Run("non-capacity unused retired quota can be consumed later", func(t *testing.T) {
		e := newLowTestEnv(t, accapi.AccountingFrequencyPeriodicHour)
		e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
		e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 20, Quota: 200, Self: 0, Children: 200})
		promise := e.addPromise("root", "child", 0, 20, 100)

		e.reconcile(1, "child", 80)
		first := e.promiseHead(promise, 1)
		assertPromiseAllocation(t, first, 80, 0, e.tm(1), e.tm(20))
		e.setAllocationPeriod(1, first.Id, 0, 5)

		e.report(6, "child", 20)
		if first.QuotaSelf != 0 || first.ConsumedSelf != 0 {
			t.Fatalf("retired allocation = self:%d consumed:%d, want 0/0", first.QuotaSelf, first.ConsumedSelf)
		}

		e.reconcile(7, "child", 100)
		second := e.promiseHead(promise, 7)
		assertPromiseAllocation(t, second, 100, 0, e.tm(5), e.tm(20))
		if got := e.promiseMaterializedQuota(7, promise); got != 100 {
			t.Fatalf("materialized quota = %d, want 100", got)
		}

		e.report(8, "child", 100)
		if second.ConsumedSelf != 100 {
			t.Fatalf("active consumption = %d, want 100", second.ConsumedSelf)
		}
	})

	t.Run("capacity quota is maximum concurrent exposure", func(t *testing.T) {
		e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
		e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
		e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 20, Quota: 200, Self: 0, Children: 200})
		promise := e.addPromise("root", "child", 0, 20, 100)

		e.report(1, "child", 80)
		first := e.promiseHead(promise, 1)
		assertPromiseAllocation(t, first, 80, 0, e.tm(1), e.tm(20))
		e.setAllocationPeriod(1, first.Id, 0, 5)

		e.report(6, "child", 80)
		second := e.promiseHead(promise, 6)
		assertPromiseAllocation(t, second, 80, 0, e.tm(5), e.tm(20))
		if first.Id == second.Id {
			t.Fatalf("expected successor allocation after retirement")
		}
		if got := e.promiseMaterializedQuota(6, promise); got != 80 {
			t.Fatalf("materialized quota = %d, want 80", got)
		}
	})
}

func TestPromiseReconcileOwnerSkipUsesAccountingMode(t *testing.T) {
	t.Run("non-capacity retired usage contributes to request coverage", func(t *testing.T) {
		e := newLowTestEnv(t, accapi.AccountingFrequencyPeriodicHour)
		e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
		e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 20, Quota: 200, Self: 0, Children: 200})
		promise := e.addPromise("root", "child", 0, 5, 100)

		e.report(1, "child", 80)
		first := e.promiseHead(promise, 1)
		assertPromiseAllocation(t, first, 80, 0, e.tm(1), e.tm(5))

		e.report(6, "child", 80)
		if got := len(e.promiseAllocations(promise)); got != 1 {
			t.Fatalf("promise allocations = %d, want 1", got)
		}
		if got := e.promiseMaterializedQuota(6, promise); got != 80 {
			t.Fatalf("materialized quota = %d, want 80", got)
		}
	})

	t.Run("capacity retired usage does not contribute to request coverage", func(t *testing.T) {
		e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
		e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
		e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 20, Quota: 200, Self: 0, Children: 200})
		promise := e.addPromise("root", "child", 0, 20, 100)

		e.report(1, "child", 80)
		first := e.promiseHead(promise, 1)
		assertPromiseAllocation(t, first, 80, 0, e.tm(1), e.tm(20))
		e.setAllocationPeriod(1, first.Id, 0, 5)

		e.report(6, "child", 80)
		if got := len(e.promiseAllocations(promise)); got != 2 {
			t.Fatalf("promise allocations = %d, want 2", got)
		}
		second := e.promiseHead(promise, 6)
		if first.Id == second.Id {
			t.Fatalf("expected successor allocation after capacity retirement")
		}
		assertPromiseAllocation(t, second, 80, 0, e.tm(5), e.tm(20))
	})
}
