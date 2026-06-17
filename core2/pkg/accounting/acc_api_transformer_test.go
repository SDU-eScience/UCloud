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
	e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
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

func TestWalletReevaluateLockMarksSignificantUpdates(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
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

	e.addPromise("root", "child", 0, 10, 100)
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
	e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100, Self: 0, Children: 100})
	promise := e.addPromise("root", "child", 0, 10, 100)

	e.report(1, "child", 40)
	head := e.promiseHead(promise, 1)
	promiseSetPeriod(e.tm(1), e.tree(), head.Id, e.tm(0), e.tm(5))
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
	e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
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
	if !updatedHead.End.Equal(e.tm(10)) {
		t.Fatalf("materialized end = %s, want unchanged %s", updatedHead.End, e.tm(10))
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
	e.setPolicy(PromisePolicy{TrendAlphaBasisPoints: 10000})
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

	relevant := FindRelevantProviders(e.tm(1), []accapi.WalletOwner{e.owner("wallet")}, util.OptValue(accapi.ProductTypeStorage))
	if len(relevant.Providers) != 1 || relevant.Providers[0] != "provider" {
		t.Fatalf("relevant providers = %#v, want provider", relevant.Providers)
	}

	relevant = FindRelevantProviders(e.tm(1), []accapi.WalletOwner{e.owner("wallet")}, util.OptValue(accapi.ProductTypeCompute))
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
