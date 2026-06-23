package accounting

import (
	"testing"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func testActorForOwner(owner accapi.WalletOwner) rpc.Actor {
	if owner.ProjectId != "" {
		return rpc.Actor{Username: owner.ProjectId, Role: rpc.RoleUser, Project: util.OptValue(rpc.ProjectId(owner.ProjectId))}
	}
	return rpc.Actor{Username: owner.Username, Role: rpc.RoleUser}
}

func testRootAllocatorActor(e *lowTestEnv, project string) rpc.Actor {
	projectId := rpc.ProjectId(project)
	providerId := rpc.ProviderId(e.categoryId.Provider)
	return rpc.Actor{
		Username:         project,
		Role:             rpc.RoleUser,
		Project:          util.OptValue(projectId),
		ProviderProjects: rpc.ProviderProjects{providerId: projectId},
		Membership:       rpc.ProjectMembership{projectId: rpc.ProjectRoleAdmin},
	}
}

func TestLifecycleScanMarksActivationAndRetirementOnce(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.add(lowAllocSpec{Name: "future", Wallet: "wallet", Start: 5, End: 10, Quota: 100})
	wallet := e.tree().WalletsById[e.wallet("wallet")]

	createdAt := wallet.LastSignificantUpdateAt
	if !createdAt.Equal(e.tm(0)) {
		t.Fatalf("created significant update = %s, want %s", createdAt, e.tm(0))
	}

	lifecycleScan(e.tm(5), e.tree())
	if !wallet.LastSignificantUpdateAt.Equal(e.tm(5)) {
		t.Fatalf("activated significant update = %s, want %s", wallet.LastSignificantUpdateAt, e.tm(5))
	}
	if !e.tree().AllocationsById[e.allocs["future"]].Activated {
		t.Fatalf("allocation was not marked activated")
	}

	lifecycleScan(e.tm(6), e.tree())
	if !wallet.LastSignificantUpdateAt.Equal(e.tm(5)) {
		t.Fatalf("repeat scan changed significant update to %s", wallet.LastSignificantUpdateAt)
	}

	lifecycleScan(e.tm(10), e.tree())
	allocation := e.tree().AllocationsById[e.allocs["future"]]
	if !allocation.Retired {
		t.Fatalf("allocation was not marked retired")
	}
	if allocation.RetiredQuota != 100 {
		t.Fatalf("retired quota = %d, want 100", allocation.RetiredQuota)
	}
	if !wallet.LastSignificantUpdateAt.Equal(e.tm(10)) {
		t.Fatalf("retired significant update = %s, want %s", wallet.LastSignificantUpdateAt, e.tm(10))
	}
}

func TestWalletToApiCombinesPromisesAndLowLevelUsage(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
	promise := e.addPromise("root", "child", 0, 10, 100)

	e.report(1, "child", 40)

	tree := e.tree()
	promiseTree := &e.tree().PromiseTree
	childWallet := tree.WalletsById[e.wallet("child")]
	childApi := walletToApi(e.tm(1), tree, promiseTree, childWallet, false)

	if childApi.Owner.Reference() != "child" || childApi.PaysFor.ToId() != e.categoryId {
		t.Fatalf("unexpected wallet identity: %#v", childApi)
	}
	if childApi.TotalUsage != 40 || childApi.LocalUsage != 40 {
		t.Fatalf("usage = total:%d local:%d, want 40/40", childApi.TotalUsage, childApi.LocalUsage)
	}
	if childApi.Quota != 100 || childApi.MaxUsable != 60 {
		t.Fatalf("quota/max = %d/%d, want 100/60", childApi.Quota, childApi.MaxUsable)
	}
	if len(childApi.AllocationGroups) != 1 {
		t.Fatalf("allocation groups = %d, want 1", len(childApi.AllocationGroups))
	}
	group := childApi.AllocationGroups[0].Group
	if group.Quota != 100 || group.Usage != 40 || group.UiOnlyActiveQuota != 100 || group.UiOnlyActiveUsage != 40 {
		t.Fatalf("group = quota:%d usage:%d activeQuota:%d activeUsage:%d, want 100/40/100/40", group.Quota, group.Usage, group.UiOnlyActiveQuota, group.UiOnlyActiveUsage)
	}
	if len(group.Allocations) != 1 {
		t.Fatalf("allocations = %d, want 1", len(group.Allocations))
	}
	allocation := group.Allocations[0]
	if allocation.Id != int64(e.promiseHead(promise, 1).Id) || allocation.Quota != 100 || !allocation.Activated || allocation.Retired {
		t.Fatalf("allocation api = %#v", allocation)
	}

	rootWallet := tree.WalletsById[e.wallet("root")]
	rootApi := walletToApi(e.tm(1), tree, promiseTree, rootWallet, true)
	if rootApi.TotalAllocated != 100 || rootApi.TotalUsage != 40 {
		t.Fatalf("root totalAllocated/usage = %d/%d, want 100/40", rootApi.TotalAllocated, rootApi.TotalUsage)
	}
	if len(rootApi.Children) != 1 || rootApi.Children[0].Group.Usage != 40 {
		t.Fatalf("root children = %#v", rootApi.Children)
	}
}

func TestWalletsBrowseOverpromisedSubprojectReportsEffectiveMaxUsable(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.add(lowAllocSpec{Name: "a-root", Wallet: "A", Start: 0, End: 10, Quota: 1_000, Self: 0, Children: 1_000})
	e.addPromise("A", "B", 0, 10, 10_000)

	wallets := WalletsBrowseOwnerAt(e.tm(1), util.OptValue(e.owner("B")), WalletBrowseFilter{})
	if len(wallets) != 1 {
		t.Fatalf("wallets = %d, want 1", len(wallets))
	}
	if wallets[0].MaxUsable != 1_000 {
		t.Fatalf("B MaxUsable = %d, want 1000", wallets[0].MaxUsable)
	}
	if wallets[0].Quota != 10_000 {
		t.Fatalf("B compatibility quota = %d, want 10000", wallets[0].Quota)
	}
}

func TestWalletsBrowseOverpromisedSubprojectUsesUnreconciledParentSelfSlack(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 1_000, Self: 0, Children: 1_000})
	e.addPromise("root", "A", 0, 10, 1_000)
	e.reconcile(1, "A", 1_000)
	e.addPromise("A", "B", 0, 10, 10_000)

	wallets := WalletsBrowseOwnerAt(e.tm(1), util.OptValue(e.owner("B")), WalletBrowseFilter{})
	if len(wallets) != 1 {
		t.Fatalf("wallets = %d, want 1", len(wallets))
	}
	if wallets[0].MaxUsable != 1_000 {
		t.Fatalf("B MaxUsable = %d, want 1000", wallets[0].MaxUsable)
	}
}

func TestWalletsBrowseOverpromisedSubprojectUsesUnmaterializedAncestorPromise(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyPeriodicMinute)
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 600_000, Self: 0, Children: 600_000})
	e.addPromise("root", "A", 0, 10, 60_000)
	e.addPromise("A", "B", 0, 10, 600_000)

	aWallets := WalletsBrowseOwnerAt(e.tm(1), util.OptValue(e.owner("A")), WalletBrowseFilter{})
	if len(aWallets) != 1 {
		t.Fatalf("A wallets = %d, want 1", len(aWallets))
	}
	if aWallets[0].MaxUsable != 60_000 {
		t.Fatalf("A MaxUsable = %d, want 60000", aWallets[0].MaxUsable)
	}

	bWallets := WalletsBrowseOwnerAt(e.tm(1), util.OptValue(e.owner("B")), WalletBrowseFilter{})
	if len(bWallets) != 1 {
		t.Fatalf("B wallets = %d, want 1", len(bWallets))
	}
	if bWallets[0].MaxUsable != 60_000 {
		t.Fatalf("B MaxUsable = %d, want 60000", bWallets[0].MaxUsable)
	}
	if bWallets[0].Quota != 600_000 {
		t.Fatalf("B compatibility quota = %d, want 600000", bWallets[0].Quota)
	}
}

func TestWalletsBrowseOverpromisedSubprojectUsesPartialConcreteAndGrowableAncestorCapacity(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 1_000, Self: 0, Children: 1_000})
	rootToA := e.addPromise("root", "A", 0, 10, 1_000)
	e.reconcile(1, "A", 500)
	e.report(1, "A", 500)

	aAllocation := e.promiseHead(rootToA, 1)
	allocationMutate(e.tm(1), e.tree(), aAllocation.Id, func(allocation *Allocation, parent util.Option[*Allocation]) {
		allocation.QuotaChildren += 100
		if parent.Present {
			parent.Value.ReservedChildren += 100
		}
	})
	e.addPromise("A", "B", 0, 10, 800)

	wallets := WalletsBrowseOwnerAt(e.tm(1), util.OptValue(e.owner("B")), WalletBrowseFilter{})
	if len(wallets) != 1 {
		t.Fatalf("wallets = %d, want 1", len(wallets))
	}
	if wallets[0].MaxUsable != 500 {
		t.Fatalf("B MaxUsable = %d, want 500", wallets[0].MaxUsable)
	}
}

func TestWalletsBrowseParentUsageIncludesOverpromisedSubprojectConsumption(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.add(lowAllocSpec{Name: "a-root", Wallet: "A", Start: 0, End: 10, Quota: 1_000, Self: 0, Children: 1_000})
	e.addPromise("A", "B", 0, 10, 10_000)

	e.report(1, "B", 1_000)

	wallets := WalletsBrowseOwnerAt(e.tm(1), util.OptValue(e.owner("A")), WalletBrowseFilter{IncludeChildren: true})
	if len(wallets) != 1 {
		t.Fatalf("wallets = %d, want 1", len(wallets))
	}
	if wallets[0].TotalUsage != 1_000 {
		t.Fatalf("A TotalUsage = %d, want 1000", wallets[0].TotalUsage)
	}
	if wallets[0].LocalUsage != 0 {
		t.Fatalf("A LocalUsage = %d, want 0", wallets[0].LocalUsage)
	}
	if len(wallets[0].Children) != 1 || wallets[0].Children[0].Group.Usage != 1_000 {
		t.Fatalf("A child usage = %#v, want one child with usage 1000", wallets[0].Children)
	}
}

func TestWalletsBrowseActiveUsageIncludesActiveDescendantConsumption(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 1_000, Self: 0, Children: 1_000})
	e.addPromise("root", "A", 0, 10, 1_000)
	e.addPromise("A", "B", 0, 10, 10_000)

	e.report(1, "B", 1_049)

	wallets := WalletsBrowseOwnerAt(e.tm(1), util.OptValue(e.owner("A")), WalletBrowseFilter{})
	if len(wallets) != 1 {
		t.Fatalf("wallets = %d, want 1", len(wallets))
	}
	if wallets[0].TotalUsage != 1_049 {
		t.Fatalf("A TotalUsage = %d, want 1049", wallets[0].TotalUsage)
	}
	if wallets[0].LocalUsage != 0 {
		t.Fatalf("A LocalUsage = %d, want 0", wallets[0].LocalUsage)
	}
	if wallets[0].UiOnlyActiveUsage != 1_049 {
		t.Fatalf("A active usage = %d, want 1049", wallets[0].UiOnlyActiveUsage)
	}
}

func TestWalletsBrowseTotalUsageIncludesRecursiveDescendantConsumption(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 1_000, Self: 0, Children: 1_000})
	e.addPromise("root", "A", 0, 10, 1_000)
	e.addPromise("A", "B", 0, 10, 1_000)
	e.addPromise("B", "C", 0, 10, 1_000)

	e.report(1, "C", 300)

	wallets := WalletsBrowseOwnerAt(e.tm(1), util.OptValue(e.owner("A")), WalletBrowseFilter{})
	if len(wallets) != 1 {
		t.Fatalf("wallets = %d, want 1", len(wallets))
	}
	if wallets[0].TotalUsage != 300 {
		t.Fatalf("A TotalUsage = %d, want 300", wallets[0].TotalUsage)
	}
	if wallets[0].UiOnlyActiveUsage != 300 {
		t.Fatalf("A active usage = %d, want 300", wallets[0].UiOnlyActiveUsage)
	}
}

func TestWalletsBrowseUsageFollowsConcreteBackingInSharedDag(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.add(lowAllocSpec{Name: "root-early", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
	e.add(lowAllocSpec{Name: "root-late", Wallet: "root", Start: 10, End: 20, Quota: 100, Self: 0, Children: 100})
	rootToA := e.addPromise("root", "A", 0, 20, 100)
	aToB := e.addPromise("A", "B", 0, 20, 60)
	aToC := e.addPromise("A", "C", 0, 20, 40)
	bToL := e.addPromise("B", "L", 0, 20, 60)
	cToL := e.addPromise("C", "L", 0, 20, 40)
	assertParentChain := func(allocation *Allocation, wallets ...string) {
		t.Helper()
		current := allocation
		for _, wallet := range wallets {
			if !current.Parent.Present {
				t.Fatalf("allocation %d parent chain ended before wallet %s", current.Id, wallet)
			}
			parent := e.tree().AllocationsById[current.Parent.Value]
			if parent == nil {
				t.Fatalf("allocation %d has missing parent %d", current.Id, current.Parent.Value)
			}
			if parent.Wallet != e.wallet(wallet) {
				t.Fatalf("allocation %d parent wallet = %d, want %s (%d)", current.Id, parent.Wallet, wallet, e.wallet(wallet))
			}
			current = parent
		}
	}

	e.report(1, "L", 100)
	bToLEarly := e.promiseHead(bToL, 1)
	cToLEarly := e.promiseHead(cToL, 1)
	assertParentChain(bToLEarly, "B", "A", "root")
	assertParentChain(cToLEarly, "C", "A", "root")
	if bToLEarly.ConsumedSelf != 60 || cToLEarly.ConsumedSelf != 40 {
		t.Fatalf("early L split = %d/%d, want 60/40", bToLEarly.ConsumedSelf, cToLEarly.ConsumedSelf)
	}

	readWallet := func(at int, owner string, includeChildren bool) accapi.WalletV2 {
		t.Helper()
		wallets := WalletsBrowseOwnerAt(e.tm(at), util.OptValue(e.owner(owner)), WalletBrowseFilter{IncludeChildren: includeChildren})
		if len(wallets) != 1 {
			t.Fatalf("%s wallets = %d, want 1", owner, len(wallets))
		}
		return wallets[0]
	}

	assertWalletUsage := func(at int) {
		t.Helper()
		a := readWallet(at, "A", true)
		if a.TotalUsage != 100 || a.LocalUsage != 0 || a.UiOnlyActiveUsage != 100 {
			t.Fatalf("A usage at %d = total:%d local:%d active:%d, want 100/0/100", at, a.TotalUsage, a.LocalUsage, a.UiOnlyActiveUsage)
		}
		if len(a.Children) != 2 || a.Children[0].Group.Usage+a.Children[1].Group.Usage != 100 || a.Children[0].Group.UiOnlyActiveUsage+a.Children[1].Group.UiOnlyActiveUsage != 100 {
			t.Fatalf("A child groups at %d = %#v, want usage/active usage sum 100", at, a.Children)
		}

		b := readWallet(at, "B", true)
		if b.TotalUsage != 60 || b.LocalUsage != 0 || b.UiOnlyActiveUsage != 60 {
			t.Fatalf("B usage at %d = total:%d local:%d active:%d, want 60/0/60", at, b.TotalUsage, b.LocalUsage, b.UiOnlyActiveUsage)
		}
		if len(b.Children) != 1 || b.Children[0].Group.Usage != 60 || b.Children[0].Group.UiOnlyActiveUsage != 60 {
			t.Fatalf("B child groups at %d = %#v, want one child with usage/active usage 60", at, b.Children)
		}

		c := readWallet(at, "C", true)
		if c.TotalUsage != 40 || c.LocalUsage != 0 || c.UiOnlyActiveUsage != 40 {
			t.Fatalf("C usage at %d = total:%d local:%d active:%d, want 40/0/40", at, c.TotalUsage, c.LocalUsage, c.UiOnlyActiveUsage)
		}
		if len(c.Children) != 1 || c.Children[0].Group.Usage != 40 || c.Children[0].Group.UiOnlyActiveUsage != 40 {
			t.Fatalf("C child groups at %d = %#v, want one child with usage/active usage 40", at, c.Children)
		}

		l := readWallet(at, "L", false)
		if l.TotalUsage != 100 || l.LocalUsage != 100 || l.UiOnlyActiveUsage != 100 {
			t.Fatalf("L usage at %d = total:%d local:%d active:%d, want 100/100/100", at, l.TotalUsage, l.LocalUsage, l.UiOnlyActiveUsage)
		}
	}

	assertWalletUsage(1)

	e.report(11, "L", 100)
	if bToLEarly.ConsumedSelf != 0 || cToLEarly.ConsumedSelf != 0 {
		t.Fatalf("retired early L split = %d/%d, want 0/0", bToLEarly.ConsumedSelf, cToLEarly.ConsumedSelf)
	}
	if !bToLEarly.Retired || !cToLEarly.Retired {
		t.Fatalf("early L allocations retired = %v/%v, want true/true", bToLEarly.Retired, cToLEarly.Retired)
	}
	bToLLate := e.promiseHead(bToL, 11)
	cToLLate := e.promiseHead(cToL, 11)
	assertParentChain(bToLLate, "B", "A", "root")
	assertParentChain(cToLLate, "C", "A", "root")
	if bToLLate.ConsumedSelf != 60 || cToLLate.ConsumedSelf != 40 {
		t.Fatalf("late L split = %d/%d, want 60/40", bToLLate.ConsumedSelf, cToLLate.ConsumedSelf)
	}
	if bToLLate.Parent.Value == bToLEarly.Parent.Value || cToLLate.Parent.Value == cToLEarly.Parent.Value {
		t.Fatalf("late L allocations reused retired parents: B parent %d/%d C parent %d/%d", bToLLate.Parent.Value, bToLEarly.Parent.Value, cToLLate.Parent.Value, cToLEarly.Parent.Value)
	}

	for _, promise := range []PromiseId{rootToA, aToB, aToC, bToL, cToL} {
		if got := len(e.promiseAllocations(promise)); got < 2 {
			t.Fatalf("promise %d allocations = %d, want at least 2 across early/late backing periods", promise, got)
		}
	}
	assertWalletUsage(11)
}

func TestWalletsBrowseReadSidePromiseCycleTerminatesAndDoesNotDoubleCount(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
	e.addPromise("root", "A", 0, 10, 100)
	e.addPromise("A", "B", 0, 10, 100)
	e.addPromise("B", "A", 0, 10, 100)

	e.report(1, "B", 40)

	aWallets := WalletsBrowseOwnerAt(e.tm(1), util.OptValue(e.owner("A")), WalletBrowseFilter{})
	if len(aWallets) != 1 {
		t.Fatalf("A wallets = %d, want 1", len(aWallets))
	}
	if aWallets[0].MaxUsable > 100 {
		t.Fatalf("A MaxUsable = %d, want <= 100", aWallets[0].MaxUsable)
	}
	if aWallets[0].TotalUsage != 40 || aWallets[0].UiOnlyActiveUsage != 40 {
		t.Fatalf("A usage = total:%d active:%d, want 40/40", aWallets[0].TotalUsage, aWallets[0].UiOnlyActiveUsage)
	}

	bWallets := WalletsBrowseOwnerAt(e.tm(1), util.OptValue(e.owner("B")), WalletBrowseFilter{})
	if len(bWallets) != 1 {
		t.Fatalf("B wallets = %d, want 1", len(bWallets))
	}
	if bWallets[0].MaxUsable > 100 {
		t.Fatalf("B MaxUsable = %d, want <= 100", bWallets[0].MaxUsable)
	}
}

func TestWalletReevaluateLockMarksSignificantUpdates(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 200, Self: 0, Children: 200})
	e.addPromise("root", "child", 0, 10, 100)

	e.report(1, "child", 100)
	wallet := e.tree().WalletsById[e.wallet("child")]
	if !wallet.Locked {
		t.Fatalf("wallet was not locked after consuming all promised quota")
	}
	if !wallet.LastSignificantUpdateAt.Equal(e.tm(1)) {
		t.Fatalf("locked significant update = %s, want %s", wallet.LastSignificantUpdateAt, e.tm(1))
	}

	_, err := PromiseCreate(e.tm(2), e.categoryId, e.wallet("root"), e.wallet("child"), e.tm(0), e.tm(10), 100, util.OptNone[GrantId]())
	if err != nil {
		t.Fatalf("create second promise: %v", err)
	}
	walletReevaluateLock(e.tm(2), e.tree(), wallet)
	if wallet.Locked {
		t.Fatalf("wallet did not unlock after more promised quota became available")
	}
	if !wallet.LastSignificantUpdateAt.Equal(e.tm(2)) {
		t.Fatalf("unlocked significant update = %s, want %s", wallet.LastSignificantUpdateAt, e.tm(2))
	}
}

func TestAllocationToApiUsesLowLevelQuotaWhenRetired(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
	promise := e.addPromise("root", "child", 0, 10, 100)

	e.report(1, "child", 40)
	head := e.promiseHead(promise, 1)
	e.setAllocationPeriod(1, head.Id, 0, 5)
	lifecycleScan(e.tm(5), e.tree())

	wallet := e.tree().WalletsById[e.wallet("child")]
	apiWallet := walletToApi(e.tm(5), e.tree(), &e.tree().PromiseTree, wallet, false)
	allocation := apiWallet.AllocationGroups[0].Group.Allocations[0]
	if allocation.Quota != 40 || allocation.RetiredQuota != 40 || !allocation.Retired {
		t.Fatalf("retired allocation = quota:%d retiredQuota:%d retired:%v, want 40/40/true", allocation.Quota, allocation.RetiredQuota, allocation.Retired)
	}
}

func TestWalletsBrowseReturnsOwnerWallets(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.add(lowAllocSpec{Name: "wallet", Wallet: "wallet", Start: 0, End: 10, Quota: 100})

	wallets := WalletsBrowse(testActorForOwner(e.owner("wallet")), WalletBrowseFilter{})
	if len(wallets) != 1 {
		t.Fatalf("wallets = %d, want 1", len(wallets))
	}
	if wallets[0].Owner.Reference() != "wallet" {
		t.Fatalf("wallet owner = %s, want wallet", wallets[0].Owner.Reference())
	}
	if wallets[0].AllocationGroups == nil || wallets[0].Children == nil {
		t.Fatalf("wallet slices should be non-nil")
	}
}

func TestWalletToApiIncludesRootAllocationWithoutPromise(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	rootId := e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100})

	wallet := e.tree().WalletsById[e.wallet("root")]
	apiWallet := walletToApi(e.tm(1), e.tree(), &e.tree().PromiseTree, wallet, false)

	if apiWallet.Quota != 100 || apiWallet.UiOnlyActiveQuota != 100 || apiWallet.MaxUsable != 100 {
		t.Fatalf("wallet quota/active/max = %d/%d/%d, want 100/100/100", apiWallet.Quota, apiWallet.UiOnlyActiveQuota, apiWallet.MaxUsable)
	}
	if len(apiWallet.AllocationGroups) != 1 {
		t.Fatalf("allocation groups = %d, want 1", len(apiWallet.AllocationGroups))
	}
	groupWithParent := apiWallet.AllocationGroups[0]
	if groupWithParent.Parent.Present {
		t.Fatalf("root allocation parent = %#v, want none", groupWithParent.Parent)
	}
	if len(groupWithParent.Group.Allocations) != 1 || groupWithParent.Group.Allocations[0].Id != int64(rootId) {
		t.Fatalf("root allocations = %#v, want allocation %d", groupWithParent.Group.Allocations, rootId)
	}
	if groupWithParent.Group.Quota != 100 || groupWithParent.Group.UiOnlyActiveQuota != 100 {
		t.Fatalf("root group quota/active = %d/%d, want 100/100", groupWithParent.Group.Quota, groupWithParent.Group.UiOnlyActiveQuota)
	}
}

func TestWalletsBrowseAllAndPageFilterDeterministically(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.add(lowAllocSpec{Name: "b", Wallet: "b", Start: 0, End: 10, Quota: 100})
	e.add(lowAllocSpec{Name: "a", Wallet: "a", Start: 0, End: 10, Quota: 100})

	all := WalletsBrowseOwnerAt(e.tm(1), util.OptNone[accapi.WalletOwner](), WalletBrowseFilter{RequireActive: true})
	if len(all) != 2 || all[0].Owner.Reference() != "a" || all[1].Owner.Reference() != "b" {
		t.Fatalf("wallet order = %#v, want a,b", all)
	}

	page := WalletsBrowsePaginatedAt(
		e.tm(1),
		rpc.ActorSystem,
		accapi.WalletsBrowseRequest{ItemsPerPage: 1, RequireActive: util.OptValue(true)},
	)
	if len(page.Items) != 1 || page.Items[0].Owner.Reference() != "a" || !page.Next.Present {
		t.Fatalf("first page = %#v", page)
	}
	next := WalletsBrowsePaginatedAt(
		e.tm(1),
		rpc.ActorSystem,
		accapi.WalletsBrowseRequest{ItemsPerPage: 1, Next: page.Next, RequireActive: util.OptValue(true)},
	)
	if len(next.Items) != 1 || next.Items[0].Owner.Reference() != "b" || next.Next.Present {
		t.Fatalf("second page = %#v", next)
	}

	filtered := WalletsBrowseOwnerAt(e.tm(1), util.OptValue(e.owner("a")), WalletBrowseFilter{RequireActive: true})
	if len(filtered) != 1 || filtered[0].Owner.Reference() != "a" {
		t.Fatalf("filtered wallets = %#v", filtered)
	}
}

func TestUsageReportDeltaAndScopedUsage(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.add(lowAllocSpec{Name: "wallet", Wallet: "wallet", Start: 0, End: 10, Quota: 100})

	e.report(1, "wallet", 10)
	if got := e.tree().WalletsById[e.wallet("wallet")].Consumed; got != 10 {
		t.Fatalf("absolute usage = %d, want 10", got)
	}

	success, err := UsageReport(e.tm(2), accapi.ReportUsageRequest{Owner: e.owner("wallet"), CategoryIdV2: e.categoryId, Usage: 5, IsDeltaCharge: true})
	if err != nil || !success {
		t.Fatalf("delta report success=%v err=%v", success, err)
	}
	if got := e.tree().WalletsById[e.wallet("wallet")].Consumed; got != 15 {
		t.Fatalf("delta usage = %d, want 15", got)
	}

	success, err = UsageReport(e.tm(3), accapi.ReportUsageRequest{
		Owner:        e.owner("wallet"),
		CategoryIdV2: e.categoryId,
		Usage:        3,
		Description:  accapi.ChargeDescription{Scope: util.OptValue("scope")},
	})
	if err != nil || !success {
		t.Fatalf("scoped absolute report success=%v err=%v", success, err)
	}
	if got := e.tree().WalletsById[e.wallet("wallet")].Consumed; got != 18 {
		t.Fatalf("scoped absolute usage = %d, want 18", got)
	}

	success, err = UsageReport(e.tm(4), accapi.ReportUsageRequest{
		Owner:         e.owner("wallet"),
		CategoryIdV2:  e.categoryId,
		Usage:         -2,
		IsDeltaCharge: true,
		Description:   accapi.ChargeDescription{Scope: util.OptValue("scope")},
	})
	if err != nil || !success {
		t.Fatalf("scoped delta report success=%v err=%v", success, err)
	}
	if got := e.tree().WalletsById[e.wallet("wallet")].Consumed; got != 16 {
		t.Fatalf("scoped delta usage = %d, want 16", got)
	}
}

func TestAllocationUpdatePromiseBackedAllocationUpdatesPromise(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
	promiseId := e.addPromise("root", "child", 0, 10, 100)
	e.report(1, "child", 40)

	head := e.promiseHead(promiseId, 1)
	_, _, err := AllocationUpdate(e.tm(2), e.categoryId, head.Id, util.OptValue[int64](60), util.OptNone[time.Time](), util.OptNone[time.Time]())
	if err != nil {
		t.Fatalf("update promise-backed allocation: %v", err)
	}
	promise := e.tree().PromiseTree.PromisesById[promiseId]
	if promise.Quota != 60 {
		t.Fatalf("promise quota = %d, want 60", promise.Quota)
	}
	updatedHead := e.tree().AllocationsById[head.Id]
	if updatedHead.QuotaSelf+updatedHead.QuotaChildren > 60 {
		t.Fatalf("materialized quota = %d, want <= 60", updatedHead.QuotaSelf+updatedHead.QuotaChildren)
	}
	apiWallet := walletToApi(e.tm(2), e.tree(), &e.tree().PromiseTree, e.tree().WalletsById[e.wallet("child")], false)
	if got := apiWallet.AllocationGroups[0].Group.Allocations[0].Quota; got != 60 {
		t.Fatalf("api allocation quota = %d, want 60", got)
	}

	_, _, err = AllocationUpdate(e.tm(2), e.categoryId, head.Id, util.OptNone[int64](), util.OptNone[time.Time](), util.OptValue(e.tm(8)))
	if err != nil {
		t.Fatalf("update promise period: %v", err)
	}
	if !promise.End.Equal(e.tm(8)) {
		t.Fatalf("promise end = %s, want %s", promise.End, e.tm(8))
	}
	if !updatedHead.End.Equal(e.tm(8)) {
		t.Fatalf("materialized end = %s, want %s", updatedHead.End, e.tm(8))
	}

	_, _, err = AllocationUpdate(e.tm(3), e.categoryId, head.Id, util.OptValue[int64](30), util.OptNone[time.Time](), util.OptNone[time.Time]())
	if err == nil {
		t.Fatalf("expected error when lowering promise below consumption")
	}
}

func TestWalletV2ByAllocationID(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	allocId := e.add(lowAllocSpec{Name: "wallet", Wallet: "wallet", Start: 0, End: 10, Quota: 100})

	walletId, wallet, ok := WalletByAllocationIdAt(e.tm(1), allocId)
	if !ok {
		t.Fatalf("allocation was not found")
	}
	if walletId != e.wallet("wallet") || wallet.Owner.Reference() != "wallet" {
		t.Fatalf("wallet lookup = %d/%s, want %d/wallet", walletId, wallet.Owner.Reference(), e.wallet("wallet"))
	}
}

func TestPromiseCreateForGrantIsIdempotentAndPropagatesGrant(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})

	grant := util.OptValue(GrantId(42))
	promiseId, err := PromiseCreate(e.tm(1), e.categoryId, e.wallet("root"), e.wallet("child"), e.tm(0), e.tm(10), 100, grant)
	if err != nil {
		t.Fatalf("create grant promise: %v", err)
	}
	again, err := PromiseCreate(e.tm(1), e.categoryId, e.wallet("root"), e.wallet("child"), e.tm(0), e.tm(10), 100, grant)
	if err != nil {
		t.Fatalf("create duplicate grant promise: %v", err)
	}
	if again != promiseId {
		t.Fatalf("duplicate promise id = %d, want %d", again, promiseId)
	}
	if got := len(e.tree().PromiseTree.PromisesById); got != 1 {
		t.Fatalf("promises = %d, want 1", got)
	}

	e.report(2, "child", 10)
	head := e.promiseHead(promiseId, 2)
	if head == nil || !head.Grant.Present || head.Grant.Value != grant.Value {
		t.Fatalf("materialized grant = %#v, want %d", head, grant.Value)
	}

	apiWallet := walletToApi(e.tm(2), e.tree(), &e.tree().PromiseTree, e.tree().WalletsById[e.wallet("child")], false)
	apiGrant := apiWallet.AllocationGroups[0].Group.Allocations[0].GrantedIn
	if !apiGrant.Present || apiGrant.Value != int64(grant.Value) {
		t.Fatalf("api grantedIn = %#v, want %d", apiGrant, grant.Value)
	}
}

func TestWalletTotalQuotaContributingAtUsesPromisesAndRootAllocations(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100})

	quota, ok := WalletTotalQuotaContributingAt(e.tm(1), testActorForOwner(e.owner("root")), e.categoryId)
	if !ok || quota != 100 {
		t.Fatalf("root quota = %d ok=%v, want 100/true", quota, ok)
	}

	_, err := PromiseCreate(e.tm(1), e.categoryId, e.wallet("root"), e.wallet("child"), e.tm(0), e.tm(10), 60, util.OptValue(GrantId(7)))
	if err != nil {
		t.Fatalf("create grant promise: %v", err)
	}
	quota, ok = WalletTotalQuotaContributingAt(e.tm(1), testActorForOwner(e.owner("child")), e.categoryId)
	if !ok || quota != 60 {
		t.Fatalf("promise quota = %d ok=%v, want 60/true", quota, ok)
	}
}

func TestRootAllocateEnsuresWalletAndCreatesRootAllocation(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	resetProducts()
	createTestProduct(accapi.ProductV2{
		Type:        accapi.ProductTypeCStorage,
		Category:    e.category,
		Name:        e.category.Name,
		Description: e.category.Name,
		ProductType: e.category.ProductType,
	})
	owner := accapi.WalletOwnerProject("project")

	allocationId, err := RootAllocateAt(e.tm(1), testRootAllocatorActor(e, owner.ProjectId), e.categoryId, e.tm(1), e.tm(10), 100)
	if err != nil {
		t.Fatalf("root allocate: %v", err)
	}
	wallet := e.tree().WalletsByOwner[owner.Reference()]
	if wallet == nil {
		t.Fatalf("wallet was not created")
	}
	allocation := e.tree().AllocationsById[allocationId]
	if allocation == nil || allocation.Wallet != wallet.Id || allocation.Parent.Present || allocation.QuotaSelf != 100 {
		t.Fatalf("allocation = %#v", allocation)
	}
}

func TestWalletsBrowseInternalAndCheckProviderUsable(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.add(lowAllocSpec{Name: "wallet", Wallet: "wallet", Start: 0, End: 10, Quota: 100})

	internal := WalletsBrowseAt(e.tm(1), testActorForOwner(e.owner("wallet")), WalletBrowseFilter{})
	if len(internal) != 1 || internal[0].Owner.Reference() != "wallet" {
		t.Fatalf("internal wallets = %#v", internal)
	}

	provider := rpc.Actor{Username: fndapi.ProviderSubjectPrefix + e.categoryId.Provider, Role: rpc.RoleProvider}
	usable, err := WalletsCheckProviderUsableAt(e.tm(1), provider, e.owner("wallet"), e.categoryId)
	if err != nil || usable.MaxUsable != 100 {
		t.Fatalf("usable = %#v err=%v, want 100/nil", usable, err)
	}

	_, err = WalletsCheckProviderUsableAt(e.tm(1), provider, e.owner("missing"), e.categoryId)
	if err == nil {
		t.Fatalf("missing wallet should not be usable")
	}
}

func TestFindRelevantAndAllProviders(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.add(lowAllocSpec{Name: "wallet", Wallet: "wallet", Start: 0, End: 10, Quota: 100})

	relevant := FindRelevantProviders([]accapi.WalletOwner{e.owner("wallet")}, util.OptValue(accapi.ProductTypeStorage))
	if len(relevant.Providers) != 1 || relevant.Providers[0] != "provider" {
		t.Fatalf("relevant providers = %#v, want provider", relevant.Providers)
	}

	relevant = FindRelevantProviders([]accapi.WalletOwner{e.owner("wallet")}, util.OptValue(accapi.ProductTypeCompute))
	if len(relevant.Providers) != 0 {
		t.Fatalf("filtered relevant providers = %#v, want empty", relevant.Providers)
	}

	all := FindAllProviders([]accapi.ProductCategory{
		e.category,
		{Name: "free", Provider: "free-provider", ProductType: accapi.ProductTypeStorage, FreeToUse: true},
		{Name: "compute", Provider: "compute-provider", ProductType: accapi.ProductTypeCompute},
	}, util.OptValue(accapi.ProductTypeStorage), false)
	if len(all.Providers) != 1 || all.Providers[0] != "provider" {
		t.Fatalf("all providers = %#v, want provider", all.Providers)
	}

	all = FindAllProviders([]accapi.ProductCategory{
		e.category,
		{Name: "free", Provider: "free-provider", ProductType: accapi.ProductTypeStorage, FreeToUse: true},
	}, util.OptValue(accapi.ProductTypeStorage), true)
	if len(all.Providers) != 2 || all.Providers[0] != "free-provider" || all.Providers[1] != "provider" {
		t.Fatalf("all providers with free = %#v, want free-provider/provider", all.Providers)
	}
}
