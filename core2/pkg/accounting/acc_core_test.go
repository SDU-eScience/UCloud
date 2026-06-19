package accounting

import (
	"fmt"
	"net/http"
	"testing"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/util"
)

type lowTestEnv struct {
	t          *testing.T
	category   accapi.ProductCategory
	categoryId accapi.ProductCategoryIdV2
	base       time.Time
	wallets    map[string]WalletId
	allocs     map[string]AllocationId
}

func newLowTestEnv(t *testing.T, frequency accapi.AccountingFrequency) *lowTestEnv {
	t.Helper()

	accGlobals.Mu.Lock()
	accGlobals.TestingEnabled = true
	accGlobals.Usage = map[string]*ScopedUsage{}
	accGlobals.Trees = map[accapi.ProductCategoryIdV2]*AccountingTree{}
	accGlobals.OwnersByReference = map[string]*walletOwner{}
	accGlobals.OwnersById = map[OwnerId]*walletOwner{}
	accGlobals.OwnerIdAcc.Store(0)
	accGlobals.WalletIdAcc.Store(0)
	accGlobals.AllocIdAcc.Store(0)
	accGlobals.Mu.Unlock()

	promiseGlobals.PromiseIdAcc.Store(0)

	category := accapi.ProductCategory{
		Name:        fmt.Sprintf("acc2-%s", t.Name()),
		Provider:    "provider",
		ProductType: accapi.ProductTypeStorage,
		AccountingUnit: accapi.AccountingUnit{
			Name:                   "unit",
			NamePlural:             "units",
			FloatingPoint:          false,
			DisplayFrequencySuffix: false,
		},
		AccountingFrequency: frequency,
		FreeToUse:           false,
		AllowSubAllocations: true,
	}
	categoryId := category.ToId()

	tree := &AccountingTree{
		Category:        category,
		WalletsById:     map[WalletId]*Wallet{},
		WalletsByOwner:  map[string]*Wallet{},
		AllocationsById: map[AllocationId]*Allocation{},
		PromiseTree: PromiseTree{
			PromisesById:     map[PromiseId]*Promise{},
			PromisesByParent: map[WalletId][]PromiseId{},
			PromisesByChild:  map[WalletId][]PromiseId{},
		},
	}

	accGlobals.Mu.Lock()
	accGlobals.Trees[categoryId] = tree
	accGlobals.Mu.Unlock()

	return &lowTestEnv{
		t:          t,
		category:   category,
		categoryId: categoryId,
		base:       time.Date(2024, time.January, 1, 0, 0, 0, 0, time.UTC),
		wallets:    map[string]WalletId{},
		allocs:     map[string]AllocationId{},
	}
}

func (e *lowTestEnv) tm(hours int) time.Time {
	return e.base.Add(time.Duration(hours) * time.Hour)
}

func (e *lowTestEnv) owner(ref string) accapi.WalletOwner {
	return accapi.WalletOwnerUser(ref)
}

func (e *lowTestEnv) wallet(ref string) WalletId {
	e.t.Helper()
	if id, ok := e.wallets[ref]; ok {
		return id
	}

	owner := e.owner(ref)
	walletOwner := walletOwnerEnsure(owner)
	id := WalletId(accGlobals.WalletIdAcc.Add(1))
	wallet := &Wallet{
		Id:          id,
		Allocations: []AllocationId{},
		Owner:       owner,
		OwnerId:     walletOwner.Id,
		Category:    e.categoryId,
	}

	tree := e.tree()
	tree.WalletsById[id] = wallet
	tree.WalletsByOwner[owner.Reference()] = wallet
	e.wallets[ref] = id
	return id
}

func (e *lowTestEnv) tree() *AccountingTree {
	e.t.Helper()
	accGlobals.Mu.RLock()
	tree := accGlobals.Trees[e.categoryId]
	accGlobals.Mu.RUnlock()
	if tree == nil {
		e.t.Fatalf("missing accounting tree")
	}
	return tree
}

type lowAllocSpec struct {
	Name     string
	Wallet   string
	Parent   string
	Start    int
	End      int
	Quota    int64
	Self     int64
	Children int64
}

func (e *lowTestEnv) add(spec lowAllocSpec) AllocationId {
	e.t.Helper()
	recipient := e.wallet(spec.Wallet)
	parent := util.OptNone[AllocationId]()
	if spec.Parent != "" {
		parent = util.OptValue(e.allocs[spec.Parent])
	}

	id, err := AllocationCreate(
		e.tm(0),
		e.categoryId,
		e.tm(spec.Start),
		e.tm(spec.End),
		spec.Quota,
		recipient,
		parent,
		util.OptNone[GrantId](),
	)
	if err != nil {
		e.t.Fatalf("create allocation %s: %v", spec.Name, err)
	}

	e.allocs[spec.Name] = id
	if spec.Self != 0 || spec.Children != 0 {
		e.setSplit(spec.Name, spec.Self, spec.Children)
	}
	e.assertValid()
	return id
}

func (e *lowTestEnv) setSplit(name string, self int64, children int64) {
	e.t.Helper()
	tree := e.tree()
	id := e.allocs[name]
	allocationMutate(e.tm(0), tree, id, func(alloc *Allocation, parent util.Option[*Allocation]) {
		oldTotal := alloc.QuotaSelf + alloc.QuotaChildren
		newTotal := self + children
		alloc.QuotaSelf = self
		alloc.QuotaChildren = children
		if parent.Present {
			parent.Value.ReservedChildren += newTotal - oldTotal
		}
	})
}

func (e *lowTestEnv) report(at int, wallet string, usage int64) bool {
	e.t.Helper()
	success, err := UsageReport(e.tm(at), accapi.ReportUsageRequest{
		Owner:        e.owner(wallet),
		CategoryIdV2: e.categoryId,
		Usage:        usage,
	})
	if err != nil {
		e.t.Fatalf("report usage for %s: %v", wallet, err)
	}
	e.assertValid()
	return success
}

func (e *lowTestEnv) tryReport(at int, wallet string, usage int64, wantStatus int) bool {
	e.t.Helper()
	success, err := UsageReport(e.tm(at), accapi.ReportUsageRequest{
		Owner:        e.owner(wallet),
		CategoryIdV2: e.categoryId,
		Usage:        usage,
	})
	if wantStatus == 0 && err != nil {
		e.t.Fatalf("report usage for %s: %v", wallet, err)
	}
	if wantStatus != 0 {
		if err == nil {
			e.t.Fatalf("report usage for %s succeeded, want status %d", wallet, wantStatus)
		}
		if err.StatusCode != wantStatus {
			e.t.Fatalf("report usage status = %d, want %d", err.StatusCode, wantStatus)
		}
	}
	e.assertValid()
	return success
}

func (e *lowTestEnv) update(at int, name string, quota util.Option[int64], start util.Option[int], end util.Option[int], wantStatus int) {
	e.t.Helper()
	startTime := util.OptNone[time.Time]()
	if start.Present {
		startTime.Set(e.tm(start.Value))
	}
	endTime := util.OptNone[time.Time]()
	if end.Present {
		endTime.Set(e.tm(end.Value))
	}

	_, _, err := AllocationUpdate(e.tm(at), e.categoryId, e.allocs[name], quota, startTime, endTime)
	if wantStatus == 0 && err != nil {
		e.t.Fatalf("update %s: %v", name, err)
	}
	if wantStatus != 0 {
		if err == nil {
			e.t.Fatalf("update %s succeeded, want status %d", name, wantStatus)
		}
		if err.StatusCode != wantStatus {
			e.t.Fatalf("update %s status = %d, want %d", name, err.StatusCode, wantStatus)
		}
	}
	e.assertValid()
}

func (e *lowTestEnv) alloc(name string) *Allocation {
	e.t.Helper()
	allocation := e.tree().AllocationsById[e.allocs[name]]
	if allocation == nil {
		e.t.Fatalf("missing allocation %s", name)
	}
	return allocation
}

func (e *lowTestEnv) assertAllocation(name string, self int64, children int64, consumed int64, reserved int64) {
	e.t.Helper()
	allocation := e.alloc(name)
	if allocation.QuotaSelf != self || allocation.QuotaChildren != children || allocation.ConsumedSelf != consumed || allocation.ReservedChildren != reserved {
		e.t.Fatalf(
			"%s = self:%d children:%d consumed:%d reserved:%d, want self:%d children:%d consumed:%d reserved:%d",
			name,
			allocation.QuotaSelf,
			allocation.QuotaChildren,
			allocation.ConsumedSelf,
			allocation.ReservedChildren,
			self,
			children,
			consumed,
			reserved,
		)
	}
}

func (e *lowTestEnv) assertWalletConsumed(wallet string, consumed int64) {
	e.t.Helper()
	got := e.tree().WalletsById[e.wallet(wallet)].Consumed
	if got != consumed {
		e.t.Fatalf("wallet %s consumed = %d, want %d", wallet, got, consumed)
	}
}

func (e *lowTestEnv) assertValid() {
	e.t.Helper()
	tree := e.tree()
	for _, wallet := range tree.WalletsById {
		consumed := int64(0)
		for _, allocationId := range wallet.Allocations {
			allocation := tree.AllocationsById[allocationId]
			if allocation == nil {
				e.t.Fatalf("wallet %d references missing allocation %d", wallet.Id, allocationId)
			}
			if allocation.Wallet != wallet.Id {
				e.t.Fatalf("allocation %d wallet = %d, want %d", allocation.Id, allocation.Wallet, wallet.Id)
			}
			consumed += allocation.ConsumedSelf
		}
		if wallet.Consumed != consumed {
			e.t.Fatalf("wallet %d consumed = %d, want %d", wallet.Id, wallet.Consumed, consumed)
		}
	}

	for _, allocation := range tree.AllocationsById {
		if allocation.Id <= 0 || allocation.Wallet <= 0 {
			e.t.Fatalf("invalid allocation identity: %#v", allocation)
		}
		if allocation.Start.After(allocation.End) {
			e.t.Fatalf("allocation %d start after end", allocation.Id)
		}
		if allocation.QuotaSelf < 0 || allocation.QuotaChildren < 0 || allocation.ConsumedSelf < 0 || allocation.ReservedChildren < 0 {
			e.t.Fatalf("allocation %d has negative values: %#v", allocation.Id, allocation)
		}
		if allocation.QuotaSelf < allocation.ConsumedSelf && !allocation.IsRetired(e.tm(1000)) {
			e.t.Fatalf("allocation %d self quota below consumption: %#v", allocation.Id, allocation)
		}
		if allocation.QuotaChildren < allocation.ReservedChildren {
			e.t.Fatalf("allocation %d child quota below reservation: %#v", allocation.Id, allocation)
		}
		if !allocation.Parent.Present && allocation.Promise.Present {
			e.t.Fatalf("root allocation %d has promise", allocation.Id)
		}
		if allocation.Parent.Present {
			parent := tree.AllocationsById[allocation.Parent.Value]
			if parent == nil {
				e.t.Fatalf("allocation %d missing parent %d", allocation.Id, allocation.Parent.Value)
			}
			if allocation.Start.Before(parent.Start) || allocation.End.After(parent.End) {
				e.t.Fatalf("allocation %d period outside parent", allocation.Id)
			}
		}
	}

	for _, parent := range tree.AllocationsById {
		expectedReserved := int64(0)
		seen := map[AllocationId]bool{}
		for _, childId := range parent.Children {
			child := tree.AllocationsById[childId]
			if child == nil {
				e.t.Fatalf("parent %d references missing child %d", parent.Id, childId)
			}
			if !child.Parent.Present || child.Parent.Value != parent.Id {
				e.t.Fatalf("parent %d child %d does not point back", parent.Id, child.Id)
			}
			if seen[childId] {
				e.t.Fatalf("parent %d lists child %d twice", parent.Id, childId)
			}
			seen[childId] = true
			expectedReserved += child.QuotaSelf + child.QuotaChildren
		}
		if parent.ReservedChildren != expectedReserved {
			e.t.Fatalf("parent %d reserved = %d, want %d", parent.Id, parent.ReservedChildren, expectedReserved)
		}
	}

	promiseTree := &tree.PromiseTree
	for promiseId, promise := range promiseTree.PromisesById {
		if promise == nil {
			e.t.Fatalf("promise %d is nil", promiseId)
		}
		if promise.Id != promiseId {
			e.t.Fatalf("promise map key %d has id %d", promiseId, promise.Id)
		}
		if tree.WalletsById[promise.Parent] == nil {
			e.t.Fatalf("promise %d missing parent wallet %d", promise.Id, promise.Parent)
		}
		if tree.WalletsById[promise.Child] == nil {
			e.t.Fatalf("promise %d missing child wallet %d", promise.Id, promise.Child)
		}
		if promise.Parent == promise.Child {
			e.t.Fatalf("promise %d has same parent and child wallet %d", promise.Id, promise.Parent)
		}
		if promise.Start.After(promise.End) {
			e.t.Fatalf("promise %d start after end", promise.Id)
		}
		if promise.Quota < 0 {
			e.t.Fatalf("promise %d has negative quota %d", promise.Id, promise.Quota)
		}
	}

	for parentWallet, promiseIds := range promiseTree.PromisesByParent {
		if tree.WalletsById[parentWallet] == nil {
			e.t.Fatalf("promise parent index references missing wallet %d", parentWallet)
		}
		seen := map[PromiseId]bool{}
		for _, promiseId := range promiseIds {
			promise := promiseTree.PromisesById[promiseId]
			if promise == nil {
				e.t.Fatalf("promise parent index references missing promise %d", promiseId)
			}
			if promise.Parent != parentWallet {
				e.t.Fatalf("promise parent index %d contains promise %d with parent %d", parentWallet, promise.Id, promise.Parent)
			}
			if seen[promiseId] {
				e.t.Fatalf("promise parent index %d lists promise %d twice", parentWallet, promiseId)
			}
			seen[promiseId] = true
		}
	}

	for childWallet, promiseIds := range promiseTree.PromisesByChild {
		if tree.WalletsById[childWallet] == nil {
			e.t.Fatalf("promise child index references missing wallet %d", childWallet)
		}
		seen := map[PromiseId]bool{}
		for _, promiseId := range promiseIds {
			promise := promiseTree.PromisesById[promiseId]
			if promise == nil {
				e.t.Fatalf("promise child index references missing promise %d", promiseId)
			}
			if promise.Child != childWallet {
				e.t.Fatalf("promise child index %d contains promise %d with child %d", childWallet, promise.Id, promise.Child)
			}
			if seen[promiseId] {
				e.t.Fatalf("promise child index %d lists promise %d twice", childWallet, promiseId)
			}
			seen[promiseId] = true
		}
	}

	for _, allocation := range tree.AllocationsById {
		if !allocation.Promise.Present {
			continue
		}
		promise := promiseTree.PromisesById[allocation.Promise.Value]
		if promise == nil {
			e.t.Fatalf("allocation %d references missing promise %d", allocation.Id, allocation.Promise.Value)
		}
		if allocation.Wallet != promise.Child {
			e.t.Fatalf("promise allocation %d wallet = %d, want promise child %d", allocation.Id, allocation.Wallet, promise.Child)
		}
		if !allocation.Parent.Present {
			e.t.Fatalf("promise allocation %d has no parent allocation", allocation.Id)
		}
		parent := tree.AllocationsById[allocation.Parent.Value]
		if parent == nil {
			e.t.Fatalf("promise allocation %d missing parent allocation %d", allocation.Id, allocation.Parent.Value)
		}
		if parent.Wallet != promise.Parent {
			e.t.Fatalf("promise allocation %d parent wallet = %d, want promise parent %d", allocation.Id, parent.Wallet, promise.Parent)
		}
	}
}

func TestLowLevelAllocationCreateValidationAndReservations(t *testing.T) {
	tests := []struct {
		name       string
		setup      func(*lowTestEnv)
		create     func(*lowTestEnv) (AllocationId, *util.HttpError)
		wantStatus int
	}{
		{
			name: "negative quota rejected",
			create: func(e *lowTestEnv) (AllocationId, *util.HttpError) {
				return AllocationCreate(e.tm(0), e.categoryId, e.tm(0), e.tm(10), -1, e.wallet("user"), util.OptNone[AllocationId](), util.OptNone[GrantId]())
			},
			wantStatus: http.StatusBadRequest,
		},
		{
			name: "start after end rejected",
			create: func(e *lowTestEnv) (AllocationId, *util.HttpError) {
				return AllocationCreate(e.tm(0), e.categoryId, e.tm(10), e.tm(0), 1, e.wallet("user"), util.OptNone[AllocationId](), util.OptNone[GrantId]())
			},
			wantStatus: http.StatusBadRequest,
		},
		{
			name: "unknown recipient rejected",
			create: func(e *lowTestEnv) (AllocationId, *util.HttpError) {
				return AllocationCreate(e.tm(0), e.categoryId, e.tm(0), e.tm(10), 1, WalletId(999), util.OptNone[AllocationId](), util.OptNone[GrantId]())
			},
			wantStatus: http.StatusNotFound,
		},
		{
			name: "unknown parent rejected",
			create: func(e *lowTestEnv) (AllocationId, *util.HttpError) {
				return AllocationCreate(e.tm(0), e.categoryId, e.tm(0), e.tm(10), 1, e.wallet("child"), util.OptValue(AllocationId(999)), util.OptNone[GrantId]())
			},
			wantStatus: http.StatusNotFound,
		},
		{
			name: "child outside parent rejected",
			setup: func(e *lowTestEnv) {
				e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 50, Children: 50})
			},
			create: func(e *lowTestEnv) (AllocationId, *util.HttpError) {
				return AllocationCreate(e.tm(0), e.categoryId, e.tm(0), e.tm(11), 1, e.wallet("child"), util.OptValue(e.allocs["root"]), util.OptNone[GrantId]())
			},
			wantStatus: http.StatusBadRequest,
		},
		{
			name: "parent capacity enforced",
			setup: func(e *lowTestEnv) {
				e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 90, Children: 10})
			},
			create: func(e *lowTestEnv) (AllocationId, *util.HttpError) {
				return AllocationCreate(e.tm(0), e.categoryId, e.tm(0), e.tm(10), 11, e.wallet("child"), util.OptValue(e.allocs["root"]), util.OptNone[GrantId]())
			},
			wantStatus: http.StatusBadRequest,
		},
		{
			name: "valid child reserves parent",
			setup: func(e *lowTestEnv) {
				e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 40, Children: 60})
			},
			create: func(e *lowTestEnv) (AllocationId, *util.HttpError) {
				return AllocationCreate(e.tm(0), e.categoryId, e.tm(1), e.tm(9), 25, e.wallet("child"), util.OptValue(e.allocs["root"]), util.OptNone[GrantId]())
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
			if tt.setup != nil {
				tt.setup(e)
			}
			id, err := tt.create(e)
			if tt.wantStatus == 0 && err != nil {
				t.Fatalf("create failed: %v", err)
			}
			if tt.wantStatus != 0 {
				if err == nil {
					t.Fatalf("create succeeded with id %d, want status %d", id, tt.wantStatus)
				}
				if err.StatusCode != tt.wantStatus {
					t.Fatalf("status = %d, want %d", err.StatusCode, tt.wantStatus)
				}
			}
			e.assertValid()
			if tt.name == "valid child reserves parent" {
				e.assertAllocation("root", 40, 60, 0, 25)
			}
		})
	}
}

func TestLowLevelAllocationUpdateValidationAndReservations(t *testing.T) {
	tests := []struct {
		name       string
		setup      func(*lowTestEnv)
		update     func(*lowTestEnv)
		wantStatus int
		assert     func(*lowTestEnv)
	}{
		{
			name:  "unknown allocation rejected",
			setup: func(e *lowTestEnv) {},
			update: func(e *lowTestEnv) {
				e.allocs["missing"] = AllocationId(999)
				e.update(0, "missing", util.OptValue[int64](1), util.OptNone[int](), util.OptNone[int](), http.StatusNotFound)
			},
		},
		{
			name:  "negative quota rejected",
			setup: func(e *lowTestEnv) { e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100}) },
			update: func(e *lowTestEnv) {
				e.update(0, "root", util.OptValue[int64](-1), util.OptNone[int](), util.OptNone[int](), http.StatusBadRequest)
			},
		},
		{
			name:  "start after end rejected",
			setup: func(e *lowTestEnv) { e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100}) },
			update: func(e *lowTestEnv) {
				e.update(0, "root", util.OptNone[int64](), util.OptValue(9), util.OptValue(8), http.StatusBadRequest)
			},
		},
		{
			name:  "move started start rejected",
			setup: func(e *lowTestEnv) { e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100}) },
			update: func(e *lowTestEnv) {
				e.update(1, "root", util.OptNone[int64](), util.OptValue(2), util.OptNone[int](), http.StatusForbidden)
			},
		},
		{
			name:  "move retired end rejected",
			setup: func(e *lowTestEnv) { e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100}) },
			update: func(e *lowTestEnv) {
				e.update(11, "root", util.OptNone[int64](), util.OptNone[int](), util.OptValue(12), http.StatusForbidden)
			},
		},
		{
			name: "parent period cannot exclude child",
			setup: func(e *lowTestEnv) {
				e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 50, Children: 50})
				e.add(lowAllocSpec{Name: "child", Wallet: "child", Parent: "root", Start: 2, End: 8, Quota: 10})
			},
			update: func(e *lowTestEnv) {
				e.update(0, "root", util.OptNone[int64](), util.OptNone[int](), util.OptValue(7), http.StatusForbidden)
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
			tt.setup(e)
			tt.update(e)
			if tt.assert != nil {
				tt.assert(e)
			}
			e.assertValid()
		})
	}
}

func TestLowLevelUsageReportCapacityStatesAndRetirementCleanup(t *testing.T) {
	tests := []struct {
		name   string
		setup  func(*lowTestEnv)
		report func(*lowTestEnv)
		assert func(*lowTestEnv)
	}{
		{
			name: "inactive allocations are not charged before start",
			setup: func(e *lowTestEnv) {
				e.add(lowAllocSpec{Name: "future", Wallet: "user", Start: 5, End: 10, Quota: 100})
			},
			report: func(e *lowTestEnv) { e.tryReport(1, "user", 10, http.StatusBadRequest) },
			assert: func(e *lowTestEnv) { e.assertWalletConsumed("user", 0) },
		},
		{
			name: "active allocations fill by earliest end and may overconsume first active allocation",
			setup: func(e *lowTestEnv) {
				e.add(lowAllocSpec{Name: "late", Wallet: "user", Start: 0, End: 20, Quota: 50})
				e.add(lowAllocSpec{Name: "early", Wallet: "user", Start: 0, End: 10, Quota: 50})
			},
			report: func(e *lowTestEnv) { e.report(1, "user", 130) },
			assert: func(e *lowTestEnv) {
				e.assertAllocation("early", 50, 0, 80, 0)
				e.assertAllocation("late", 50, 0, 50, 0)
				e.assertWalletConsumed("user", 130)
			},
		},
		{
			name: "capacity retired usage is removed when active capacity covers it",
			setup: func(e *lowTestEnv) {
				e.add(lowAllocSpec{Name: "old", Wallet: "user", Start: 0, End: 5, Quota: 100})
				e.report(1, "user", 80)
				e.add(lowAllocSpec{Name: "new", Wallet: "user", Start: 5, End: 20, Quota: 100})
			},
			report: func(e *lowTestEnv) { e.report(6, "user", 60) },
			assert: func(e *lowTestEnv) {
				e.assertAllocation("old", 0, 0, 0, 0)
				e.assertAllocation("new", 100, 0, 60, 0)
				e.assertWalletConsumed("user", 60)
			},
		},
		{
			name: "capacity current usage beyond active capacity overconsumes active allocation",
			setup: func(e *lowTestEnv) {
				e.add(lowAllocSpec{Name: "old", Wallet: "user", Start: 0, End: 5, Quota: 100})
				e.report(1, "user", 80)
				e.add(lowAllocSpec{Name: "new", Wallet: "user", Start: 5, End: 20, Quota: 50})
			},
			report: func(e *lowTestEnv) { e.report(6, "user", 80) },
			assert: func(e *lowTestEnv) {
				e.assertAllocation("old", 0, 0, 0, 0)
				e.assertAllocation("new", 50, 0, 80, 0)
				e.assertWalletConsumed("user", 80)
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
			tt.setup(e)
			tt.report(e)
			tt.assert(e)
			e.assertValid()
		})
	}
}

func TestLowLevelUsageReportTimeBasedRetiredExcessMovesForward(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyPeriodicHour)
	e.add(lowAllocSpec{Name: "old", Wallet: "user", Start: 0, End: 5, Quota: 50})
	e.report(1, "user", 80)
	e.add(lowAllocSpec{Name: "new", Wallet: "user", Start: 5, End: 20, Quota: 100})

	e.report(6, "user", 80)

	e.assertAllocation("old", 50, 0, 50, 0)
	e.assertAllocation("new", 100, 0, 30, 0)
	e.assertWalletConsumed("user", 80)
	e.assertValid()
}

func TestLowLevelRetiredAllocationReleasesResourcesToParents(t *testing.T) {
	tests := []struct {
		name   string
		setup  func(*lowTestEnv)
		report func(*lowTestEnv)
		assert func(*lowTestEnv)
	}{
		{
			name: "retired leaf releases parent reservation",
			setup: func(e *lowTestEnv) {
				e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 20, Quota: 100, Self: 20, Children: 80})
				e.add(lowAllocSpec{Name: "child", Wallet: "child", Parent: "root", Start: 0, End: 5, Quota: 50})
			},
			report: func(e *lowTestEnv) { e.report(6, "child", 0) },
			assert: func(e *lowTestEnv) {
				e.assertAllocation("child", 0, 0, 0, 0)
				e.assertAllocation("root", 20, 80, 0, 0)
			},
		},
		{
			name: "retired parent release propagates to grandparent",
			setup: func(e *lowTestEnv) {
				e.add(lowAllocSpec{Name: "grand", Wallet: "grand", Start: 0, End: 20, Quota: 200, Self: 50, Children: 150})
				e.add(lowAllocSpec{Name: "parent", Wallet: "parent", Parent: "grand", Start: 0, End: 10, Quota: 100, Self: 40, Children: 60})
				e.add(lowAllocSpec{Name: "child", Wallet: "child", Parent: "parent", Start: 0, End: 5, Quota: 30})
			},
			report: func(e *lowTestEnv) {
				e.report(11, "child", 0)
				e.report(11, "parent", 0)
			},
			assert: func(e *lowTestEnv) {
				e.assertAllocation("child", 0, 0, 0, 0)
				e.assertAllocation("parent", 0, 0, 0, 0)
				e.assertAllocation("grand", 50, 150, 0, 0)
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
			tt.setup(e)
			tt.report(e)
			tt.assert(e)
			e.assertValid()
		})
	}
}

func TestLowLevelLifecycleRetiredTimeBasedAllocationReleasesResourcesToParents(t *testing.T) {
	t.Run("retired leaf releases parent reservation", func(t *testing.T) {
		e := newLowTestEnv(t, accapi.AccountingFrequencyPeriodicHour)
		e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 20, Quota: 100, Self: 0, Children: 100})
		e.add(lowAllocSpec{Name: "child", Wallet: "child", Parent: "root", Start: 0, End: 5, Quota: 80})

		lifecycleScan(e.tm(6), e.tree())

		child := e.alloc("child")
		if !child.Retired {
			t.Fatalf("child allocation was not marked retired")
		}
		e.assertAllocation("child", 0, 0, 0, 0)
		e.assertAllocation("root", 0, 100, 0, 0)
		e.assertValid()
	})

	t.Run("retired parent release propagates to grandparent", func(t *testing.T) {
		e := newLowTestEnv(t, accapi.AccountingFrequencyPeriodicHour)
		e.add(lowAllocSpec{Name: "grand", Wallet: "grand", Start: 0, End: 20, Quota: 200, Self: 0, Children: 200})
		e.add(lowAllocSpec{Name: "parent", Wallet: "parent", Parent: "grand", Start: 0, End: 10, Quota: 100, Self: 40, Children: 60})
		e.add(lowAllocSpec{Name: "child", Wallet: "child", Parent: "parent", Start: 0, End: 5, Quota: 30})

		lifecycleScan(e.tm(11), e.tree())

		if !e.alloc("child").Retired {
			t.Fatalf("child allocation was not marked retired")
		}
		if !e.alloc("parent").Retired {
			t.Fatalf("parent allocation was not marked retired")
		}
		e.assertAllocation("child", 0, 0, 0, 0)
		e.assertAllocation("parent", 0, 0, 0, 0)
		e.assertAllocation("grand", 0, 200, 0, 0)
		e.assertValid()
	})
}
