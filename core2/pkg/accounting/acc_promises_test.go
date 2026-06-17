package accounting

import (
	"testing"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
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

func (e *lowTestEnv) reconcile(at int, owner string, minimum util.Option[int64]) {
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
	head := promiseFindHead(e.tm(at), e.tree(), promise)
	if !head.Present {
		e.t.Fatalf("promise %d has no head", promiseId)
	}
	return e.tree().AllocationsById[head.Value]
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
			name: "sibling promises share overbooked root capacity",
			setup: func(e *lowTestEnv) (PromiseId, PromiseId) {
				e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
				e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
				first := e.addPromise("root", "a", 0, 10, 80)
				second := e.addPromise("root", "b", 0, 10, 80)
				return first, second
			},
			assert: func(e *lowTestEnv, first PromiseId, second PromiseId) {
				assertPromiseAllocation(t, e.promiseHead(first, 1), 80, 0, e.tm(1), e.tm(10))
				assertPromiseAllocation(t, e.promiseHead(second, 1), 20, 0, e.tm(1), e.tm(10))
				e.assertAllocation("root", 0, 100, 0, 100)
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
				e.reconcile(1, "child", util.OptValue[int64](60))
			} else {
				e.reconcile(1, "a", util.OptValue[int64](80))
				e.reconcile(1, "b", util.OptValue[int64](80))
			}
			tt.assert(e, first, second)
			e.assertValid()
		})
	}
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
				e.reconcile(1, "child", util.OptValue[int64](30))
				firstHead := e.promiseHead(promise, 1).Id
				e.reconcile(2, "child", util.OptValue[int64](90))
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
			name: "report reconciliation keeps allocation when lower target still has headroom",
			setup: func(e *lowTestEnv) PromiseId {
				e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
				e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
				return e.addPromise("root", "child", 0, 10, 100)
			},
			steps: func(e *lowTestEnv, promise PromiseId) {
				e.reconcile(1, "child", util.OptValue[int64](80))
				e.reconcile(2, "child", util.OptValue[int64](10))
			},
			assert: func(e *lowTestEnv, promise PromiseId) {
				assertPromiseAllocation(t, e.promiseHead(promise, 2), 80, 0, e.tm(1), e.tm(10))
				e.assertAllocation("root", 0, 100, 0, 80)
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

func TestPromiseReconcilePropagatesChildDemandThroughPeriodicPasses(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 20, Children: 80})
	parentPromise := e.addPromise("root", "parent", 0, 10, 100)
	childPromise := e.addPromise("parent", "child", 0, 10, 100)

	e.reconcile(1, "child", util.OptValue[int64](60))

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

	e.reconcile(1, "child", util.OptValue[int64](10))
	e.reconcile(2, "child", util.OptValue[int64](30))

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
				e.reconcile(1, "child", util.OptValue[int64](50))
				head := e.promiseHead(promise, 1)
				promiseSetPeriod(e.tm(1), e.tree(), head.Id, e.tm(0), e.tm(5))
				e.reconcile(6, "child", util.OptValue[int64](50))
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
			name: "report reconciliation keeps existing allocation when headroom is sufficient",
			setup: func(e *lowTestEnv) PromiseId {
				e.setPolicy(PromisePolicy{MinSlack: 20, TrendAlphaBasisPoints: 10000})
				e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
				return e.addPromise("root", "child", 0, 10, 100)
			},
			steps: func(e *lowTestEnv, promise PromiseId) {
				e.reconcile(1, "child", util.OptValue[int64](80))
				e.report(2, "child", 30)
			},
			assert: func(e *lowTestEnv, promise PromiseId) {
				assertPromiseAllocation(t, e.promiseHead(promise, 3), 100, 0, e.tm(1), e.tm(10))
				e.assertAllocation("root", 0, 100, 0, 100)
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

func TestPromiseReportUsageSkipsReconcileWhenHeadroomExists(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.setPolicy(PromisePolicy{MinSlack: 10, TrendAlphaBasisPoints: 10000})
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
	aPromise := e.addPromise("root", "a", 0, 10, 50)
	bPromise := e.addPromise("root", "b", 0, 10, 50)

	e.reconcile(1, "a", util.OptValue[int64](40))
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
	t.Run("usage can go over promise quota then shrink on later reconciliation", func(t *testing.T) {
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
		assertPromiseAllocation(t, head, 100, 0, e.tm(1), e.tm(10))
		if head.ConsumedSelf != 40 {
			t.Fatalf("lowered consumption = %d, want 40", head.ConsumedSelf)
		}
		e.assertAllocation("root", 0, 100, 0, 100)
	})

	t.Run("retired promise allocation releases parent and successor can grow", func(t *testing.T) {
		e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
		e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
		e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 20, Quota: 100, Self: 0, Children: 100})
		promise := e.addPromise("root", "child", 0, 20, 100)

		e.report(1, "child", 80)
		head := e.promiseHead(promise, 1)
		promiseSetPeriod(e.tm(1), e.tree(), head.Id, e.tm(0), e.tm(5))
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
		promiseSetPeriod(e.tm(1), e.tree(), first.Id, e.tm(0), e.tm(5))

		e.report(6, "child", 100)
		second := e.promiseHead(promise, 6)
		assertPromiseAllocation(t, second, 20, 0, e.tm(5), e.tm(20))
		if first.Id == second.Id {
			t.Fatalf("expected successor allocation after retirement")
		}
		if got := promiseMaterializedQuota(e.tm(6), e.tree(), e.tree().PromiseTree.PromisesById[promise], util.OptNone[AllocationId]()); got != 100 {
			t.Fatalf("materialized quota = %d, want 100", got)
		}
	})

	t.Run("non-capacity unused retired quota can be consumed later", func(t *testing.T) {
		e := newLowTestEnv(t, accapi.AccountingFrequencyPeriodicHour)
		e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
		e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 20, Quota: 200, Self: 0, Children: 200})
		promise := e.addPromise("root", "child", 0, 20, 100)

		e.reconcile(1, "child", util.OptValue[int64](80))
		first := e.promiseHead(promise, 1)
		assertPromiseAllocation(t, first, 80, 0, e.tm(1), e.tm(20))
		promiseSetPeriod(e.tm(1), e.tree(), first.Id, e.tm(0), e.tm(5))

		e.report(6, "child", 20)
		if first.QuotaSelf != 0 || first.ConsumedSelf != 0 {
			t.Fatalf("retired allocation = self:%d consumed:%d, want 0/0", first.QuotaSelf, first.ConsumedSelf)
		}

		e.reconcile(7, "child", util.OptValue[int64](100))
		second := e.promiseHead(promise, 7)
		assertPromiseAllocation(t, second, 100, 0, e.tm(5), e.tm(20))
		if got := promiseMaterializedQuota(e.tm(7), e.tree(), e.tree().PromiseTree.PromisesById[promise], util.OptNone[AllocationId]()); got != 100 {
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
		promiseSetPeriod(e.tm(1), e.tree(), first.Id, e.tm(0), e.tm(5))

		e.report(6, "child", 80)
		second := e.promiseHead(promise, 6)
		assertPromiseAllocation(t, second, 80, 0, e.tm(5), e.tm(20))
		if first.Id == second.Id {
			t.Fatalf("expected successor allocation after retirement")
		}
		if got := promiseMaterializedQuota(e.tm(6), e.tree(), e.tree().PromiseTree.PromisesById[promise], util.OptNone[AllocationId]()); got != 80 {
			t.Fatalf("materialized quota = %d, want 80", got)
		}
	})
}

func TestPromiseReconcileOwnerSkipUsesAccountingMode(t *testing.T) {
	t.Run("non-capacity retired usage contributes to request coverage", func(t *testing.T) {
		e := newLowTestEnv(t, accapi.AccountingFrequencyPeriodicHour)
		e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
		e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 20, Quota: 200, Self: 0, Children: 200})
		promise := e.addPromise("root", "child", 0, 20, 100)

		e.report(1, "child", 80)
		first := e.promiseHead(promise, 1)
		assertPromiseAllocation(t, first, 80, 0, e.tm(1), e.tm(20))
		promiseSetPeriod(e.tm(1), e.tree(), first.Id, e.tm(0), e.tm(5))

		e.report(6, "child", 80)
		if got := len(e.promiseAllocations(promise)); got != 1 {
			t.Fatalf("promise allocations = %d, want 1", got)
		}
		if got := promiseMaterializedQuota(e.tm(6), e.tree(), e.tree().PromiseTree.PromisesById[promise], util.OptNone[AllocationId]()); got != 80 {
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
		promiseSetPeriod(e.tm(1), e.tree(), first.Id, e.tm(0), e.tm(5))

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
