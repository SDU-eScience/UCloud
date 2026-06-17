package accounting

import (
	"cmp"
	"net/http"
	"slices"
	"strings"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type promiseRelationship struct {
	Parent   WalletId
	Child    WalletId
	Promises []*Promise
}

type lowLevelRelationship struct {
	Parent      util.Option[WalletId]
	Child       WalletId
	Allocations []*Allocation
}

type WalletBrowseFilter struct {
	ProductType     util.Option[accapi.ProductType]
	Provider        util.Option[string]
	Category        util.Option[string]
	RequireActive   bool
	IncludeChildren bool
}

func actorToWalletOwner(actor rpc.Actor) accapi.WalletOwner {
	if actor.Project.Present {
		return accapi.WalletOwnerProject(string(actor.Project.Value))
	} else {
		return accapi.WalletOwnerUser(actor.Username)
	}
}

func RootAllocate(
	actor rpc.Actor,
	catId accapi.ProductCategoryIdV2,
	start time.Time,
	end time.Time,
	quota int64,
) (AllocationId, *util.HttpError) {
	return RootAllocateAt(time.Now(), actor, catId, start, end, quota)
}

func RootAllocateAt(
	now time.Time,
	actor rpc.Actor,
	catId accapi.ProductCategoryIdV2,
	start time.Time,
	end time.Time,
	quota int64,
) (AllocationId, *util.HttpError) {
	if actor.Role&rpc.RolesEndUser == 0 {
		return 0, util.HttpErr(http.StatusForbidden, "You are not allowed to create a root allocation!")
	}

	if !actor.Project.Present || actor.Project.Value == "" {
		return 0, util.HttpErr(http.StatusForbidden, "Cannot perform a root allocation in a personal workspace!")
	}

	projectId, ok := actor.ProviderProjects[rpc.ProviderId(catId.Provider)]

	if !ok || projectId != actor.Project.Value {
		return 0, util.HttpErr(http.StatusForbidden, "You are not allowed to create a root allocation!")
	}

	role := fndapi.ProjectRole(actor.Membership[projectId])
	if !role.Satisfies(fndapi.ProjectRoleAdmin) {
		return 0, util.HttpErr(http.StatusForbidden, "You are not allowed to create a root allocation!")
	}

	_, err := ProductCategoryRetrieve(actor, catId.Name, catId.Provider)

	if err != nil {
		return 0, util.HttpErr(http.StatusForbidden, "This category does not exist")
	}

	walletId, err := WalletEnsure(catId, actorToWalletOwner(actor))
	if err != nil {
		return 0, err
	}
	return AllocationCreate(now, catId, start, end, quota, walletId, util.OptNone[AllocationId](), util.OptNone[GrantId]())
}

func walletBrowseKey(wallet accapi.WalletV2) string {
	return wallet.Owner.Reference() + "@" + wallet.PaysFor.Provider + "@" + wallet.PaysFor.Name
}

func WalletsBrowse(actor rpc.Actor, filter WalletBrowseFilter) []accapi.WalletV2 {
	return WalletsBrowseAt(time.Now(), actor, filter)
}

func WalletsBrowseAt(
	now time.Time,
	actor rpc.Actor,
	filter WalletBrowseFilter,
) []accapi.WalletV2 {
	owner := util.OptNone[accapi.WalletOwner]()
	if !actor.IsSystem() {
		owner.Set(actorToWalletOwner(actor))
	}
	return WalletsBrowseOwnerAt(now, owner, filter)
}

func WalletsBrowseOwnerAt(
	now time.Time,
	owner util.Option[accapi.WalletOwner],
	filter WalletBrowseFilter,
) []accapi.WalletV2 {
	result := []accapi.WalletV2{}

	accGlobals.Mu.RLock()
	trees := make([]*AccountingTree, 0, len(accGlobals.Trees))
	for _, tree := range accGlobals.Trees {
		trees = append(trees, tree)
	}
	accGlobals.Mu.RUnlock()

	for _, tree := range trees {
		tree.Mu.RLock()

		matches := true
		if filter.ProductType.Present && filter.ProductType.Value != tree.Category.ProductType {
			matches = false
		}
		if filter.Provider.Present && filter.Provider.Value != tree.Category.Provider {
			matches = false
		}
		if filter.Category.Present && filter.Category.Value != tree.Category.Name {
			matches = false
		}

		if matches {
			promiseTree := &tree.PromiseTree
			for _, wallet := range tree.WalletsById {
				if owner.Present && owner.Value.Reference() != wallet.Owner.Reference() {
					continue
				}
				if filter.RequireActive && walletActiveQuota(now, tree, promiseTree, wallet) <= 0 {
					continue
				}

				result = append(result, walletToApi(now, tree, promiseTree, wallet, filter.IncludeChildren))
			}
		}
		tree.Mu.RUnlock()
	}

	slices.SortFunc(result, func(a, b accapi.WalletV2) int {
		return cmp.Compare(walletBrowseKey(a), walletBrowseKey(b))
	})

	return result
}

func WalletsBrowsePaginated(
	actor rpc.Actor,
	request accapi.WalletsBrowseRequest,
) fndapi.PageV2[accapi.WalletV2] {
	return WalletsBrowsePaginatedAt(time.Now(), actor, request)
}

func WalletsBrowsePaginatedAt(
	now time.Time,
	actor rpc.Actor,
	request accapi.WalletsBrowseRequest,
) fndapi.PageV2[accapi.WalletV2] {
	filter := WalletBrowseFilter{
		ProductType:     request.FilterType,
		IncludeChildren: request.IncludeChildren,
		RequireActive:   request.RequireActive.Present && request.RequireActive.Value,
	}

	items := WalletsBrowseAt(now, actor, filter)
	itemsPerPage := fndapi.ItemsPerPage(request.ItemsPerPage)
	result := fndapi.PageV2[accapi.WalletV2]{Items: []accapi.WalletV2{}, ItemsPerPage: itemsPerPage}
	start := !request.Next.Present
	for _, item := range items {
		if !start {
			start = walletBrowseKey(item) == request.Next.Value
			continue
		}
		if len(result.Items) == itemsPerPage {
			result.Next.Set(walletBrowseKey(result.Items[len(result.Items)-1]))
			break
		}
		result.Items = append(result.Items, item)
	}
	return result
}

func WalletsCheckProviderUsable(
	actor rpc.Actor,
	owner accapi.WalletOwner,
	category accapi.ProductCategoryIdV2,
) (accapi.CheckProviderUsableResponse, *util.HttpError) {
	return WalletsCheckProviderUsableAt(time.Now(), actor, owner, category)
}

func WalletsCheckProviderUsableAt(
	now time.Time,
	actor rpc.Actor,
	owner accapi.WalletOwner,
	category accapi.ProductCategoryIdV2,
) (accapi.CheckProviderUsableResponse, *util.HttpError) {
	providerId, ok := strings.CutPrefix(actor.Username, fndapi.ProviderSubjectPrefix)
	if !ok || providerId != category.Provider {
		return accapi.CheckProviderUsableResponse{}, util.HttpErr(http.StatusForbidden, "forbidden")
	}

	accGlobals.Mu.RLock()
	tree := accGlobals.Trees[category]
	accGlobals.Mu.RUnlock()
	if tree == nil {
		return accapi.CheckProviderUsableResponse{}, util.HttpErr(http.StatusNotFound, "unknown category")
	}

	tree.Mu.RLock()
	defer tree.Mu.RUnlock()
	wallet := tree.WalletsByOwner[owner.Reference()]
	if wallet == nil {
		return accapi.CheckProviderUsableResponse{}, util.HttpErr(http.StatusNotFound, "unknown wallet")
	}
	return accapi.CheckProviderUsableResponse{MaxUsable: walletMaxUsable(now, tree, wallet)}, nil
}

func WalletTotalQuotaContributing(
	actor rpc.Actor,
	category accapi.ProductCategoryIdV2,
) (int64, bool) {
	return WalletTotalQuotaContributingAt(time.Now(), actor, category)
}

func WalletTotalQuotaContributingAt(
	at time.Time,
	actor rpc.Actor,
	category accapi.ProductCategoryIdV2,
) (int64, bool) {
	owner := actorToWalletOwner(actor)
	return WalletTotalQuotaContributingOwnerAt(at, owner, category)
}

func WalletTotalQuotaContributingOwnerAt(
	at time.Time,
	owner accapi.WalletOwner,
	category accapi.ProductCategoryIdV2,
) (int64, bool) {

	accGlobals.Mu.RLock()
	tree := accGlobals.Trees[category]
	accGlobals.Mu.RUnlock()
	if tree == nil {
		return 0, false
	}

	tree.Mu.RLock()
	defer tree.Mu.RUnlock()

	wallet := tree.WalletsByOwner[owner.Reference()]
	if wallet == nil {
		return 0, false
	}
	promiseTree := &tree.PromiseTree
	return walletQuotaContributing(at, tree, promiseTree, wallet), true
}

func FindRelevantProviders(
	owners []accapi.WalletOwner,
	productType util.Option[accapi.ProductType],
) accapi.FindRelevantProvidersResponse {
	return FindRelevantProvidersAt(time.Now(), owners, productType)
}

func FindRelevantProvidersAt(
	now time.Time,
	owners []accapi.WalletOwner,
	productType util.Option[accapi.ProductType],
) accapi.FindRelevantProvidersResponse {
	providers := map[string]util.Empty{}
	for _, owner := range owners {
		wallets := WalletsBrowseOwnerAt(now, util.OptValue(owner), WalletBrowseFilter{
			ProductType:   productType,
			RequireActive: true,
		})

		for _, wallet := range wallets {
			providers[wallet.PaysFor.Provider] = util.Empty{}
		}
	}

	return accapi.FindRelevantProvidersResponse{Providers: sortedProviderList(providers)}
}

func FindAllProviders(categories []accapi.ProductCategory, productType util.Option[accapi.ProductType], includeFreeToUse bool) accapi.FindAllProvidersResponse {
	providers := map[string]util.Empty{}
	for _, category := range categories {
		if category.FreeToUse && !includeFreeToUse {
			continue
		}
		if productType.Present && productType.Value != category.ProductType {
			continue
		}
		providers[category.Provider] = util.Empty{}
	}
	return accapi.FindAllProvidersResponse{Providers: sortedProviderList(providers)}
}

func sortedProviderList(providers map[string]util.Empty) []string {
	result := make([]string, 0, len(providers))
	for provider := range providers {
		result = append(result, provider)
	}
	slices.Sort(result)
	return result
}

func WalletByAllocationId(allocationId AllocationId) (WalletId, accapi.WalletV2, bool) {
	return WalletByAllocationIdAt(time.Now(), allocationId)
}

func WalletByAllocationIdAt(now time.Time, allocationId AllocationId) (WalletId, accapi.WalletV2, bool) {
	accGlobals.Mu.RLock()
	trees := make([]*AccountingTree, 0, len(accGlobals.Trees))
	for _, tree := range accGlobals.Trees {
		trees = append(trees, tree)
	}
	accGlobals.Mu.RUnlock()

	for _, tree := range trees {
		tree.Mu.RLock()
		allocation := tree.AllocationsById[allocationId]
		if allocation != nil {
			wallet := tree.WalletsById[allocation.Wallet]
			if wallet != nil {
				promiseTree := &tree.PromiseTree
				apiWallet := walletToApi(now, tree, promiseTree, wallet, false)
				tree.Mu.RUnlock()
				return wallet.Id, apiWallet, true
			}
		}
		tree.Mu.RUnlock()
	}

	return 0, accapi.WalletV2{}, false
}

func WalletById(walletId WalletId) (accapi.WalletV2, bool) {
	return WalletByIdAt(time.Now(), walletId)
}

func WalletByIdAt(now time.Time, walletId WalletId) (accapi.WalletV2, bool) {
	accGlobals.Mu.RLock()
	trees := make([]*AccountingTree, 0, len(accGlobals.Trees))
	for _, tree := range accGlobals.Trees {
		trees = append(trees, tree)
	}
	accGlobals.Mu.RUnlock()

	for _, tree := range trees {
		tree.Mu.RLock()
		wallet := tree.WalletsById[walletId]
		if wallet != nil {
			apiWallet := walletToApi(now, tree, &tree.PromiseTree, wallet, false)
			tree.Mu.RUnlock()
			return apiWallet, true
		}
		tree.Mu.RUnlock()
	}

	return accapi.WalletV2{}, false
}

func WalletsUpdatedAfter(now time.Time, replayFrom time.Time, providerId string) []accapi.WalletV2 {
	wallets := WalletsBrowseOwnerAt(
		now,
		util.OptNone[accapi.WalletOwner](),
		WalletBrowseFilter{Provider: util.OptValue(providerId), RequireActive: true},
	)

	result := []accapi.WalletV2{}
	for _, wallet := range wallets {
		if wallet.LastSignificantUpdateAt.Time().After(replayFrom) {
			result = append(result, wallet)
		}
	}
	return result
}

func walletToApi(now time.Time, tree *AccountingTree, promiseTree *PromiseTree, wallet *Wallet, includeChildren bool) accapi.WalletV2 {
	inbound := promiseRelationshipsByParent(promiseTree, wallet.Id)
	children := promiseRelationshipsByChild(promiseTree, wallet.Id)
	lowLevelInbound := lowLevelRelationshipsByParent(tree, wallet.Id)
	lowLevelChildren := lowLevelRelationshipsByChild(tree, wallet.Id)

	apiWallet := accapi.WalletV2{
		Owner:                   wallet.Owner,
		PaysFor:                 tree.Category,
		AllocationGroups:        []accapi.AllocationGroupWithParent{},
		Children:                []accapi.AllocationGroupWithChild{},
		TotalUsage:              walletTotalUsage(now, tree, promiseTree, wallet),
		LocalUsage:              wallet.Consumed,
		MaxUsable:               walletMaxUsable(now, tree, wallet),
		Quota:                   walletQuotaContributing(now, tree, promiseTree, wallet),
		TotalAllocated:          walletQuotaAllocated(now, tree, promiseTree, wallet),
		UiOnlyActiveUsage:       walletUiOnlyActiveUsage(now, tree, promiseTree, wallet),
		UiOnlyActiveQuota:       walletActiveQuota(now, tree, promiseTree, wallet),
		LastSignificantUpdateAt: fndapi.Timestamp(wallet.LastSignificantUpdateAt),
	}

	for _, relationship := range inbound {
		apiWallet.AllocationGroups = append(apiWallet.AllocationGroups, accapi.AllocationGroupWithParent{
			Parent: parentOrChildWallet(tree, relationship.Parent),
			Group:  allocationGroupToApi(now, tree, promiseTree, relationship),
		})
	}
	for _, relationship := range lowLevelInbound {
		apiWallet.AllocationGroups = append(apiWallet.AllocationGroups, accapi.AllocationGroupWithParent{
			Parent: optParentOrChildWallet(tree, relationship.Parent),
			Group:  lowLevelAllocationGroupToApi(now, tree, promiseTree, relationship),
		})
	}

	if includeChildren {
		for _, relationship := range children {
			apiWallet.Children = append(apiWallet.Children, accapi.AllocationGroupWithChild{
				Child: parentOrChildWallet(tree, relationship.Child),
				Group: allocationGroupToApi(now, tree, promiseTree, relationship),
			})
		}
		for _, relationship := range lowLevelChildren {
			apiWallet.Children = append(apiWallet.Children, accapi.AllocationGroupWithChild{
				Child: parentOrChildWallet(tree, relationship.Child),
				Group: lowLevelAllocationGroupToApi(now, tree, promiseTree, relationship),
			})
		}
	}

	return apiWallet
}

func parentOrChildWallet(tree *AccountingTree, walletId WalletId) util.Option[accapi.ParentOrChildWallet] {
	return optParentOrChildWallet(tree, util.OptValue(walletId))
}

func optParentOrChildWallet(tree *AccountingTree, walletId util.Option[WalletId]) util.Option[accapi.ParentOrChildWallet] {
	if !walletId.Present {
		return util.OptNone[accapi.ParentOrChildWallet]()
	}

	wallet := tree.WalletsById[walletId.Value]
	if wallet == nil {
		return util.OptNone[accapi.ParentOrChildWallet]()
	}
	return util.OptValue(accapi.ParentOrChildWallet{
		ProjectId:    wallet.Owner.ProjectId,
		ProjectTitle: wallet.Owner.Username,
	})
}

func lowLevelAllocationGroupToApi(now time.Time, tree *AccountingTree, promiseTree *PromiseTree, relationship lowLevelRelationship) accapi.AllocationGroup {
	allocations := make([]accapi.Allocation, 0, len(relationship.Allocations))
	for _, allocation := range relationship.Allocations {
		allocations = append(allocations, allocationToApi(promiseTree, allocation))
	}
	sortApiAllocations(allocations)

	return accapi.AllocationGroup{
		Id:                0,
		Allocations:       util.NonNilSlice(allocations),
		Usage:             lowLevelRelationshipUsage(now, tree, relationship),
		Quota:             lowLevelRelationshipQuotaContributing(now, tree, relationship),
		UiOnlyActiveUsage: lowLevelRelationshipUiOnlyActiveUsage(now, tree, relationship),
		UiOnlyActiveQuota: lowLevelRelationshipActiveQuota(now, relationship),
	}
}

func allocationGroupToApi(now time.Time, tree *AccountingTree, promiseTree *PromiseTree, relationship promiseRelationship) accapi.AllocationGroup {
	promiseIds := map[PromiseId]bool{}
	for _, promise := range relationship.Promises {
		promiseIds[promise.Id] = true
	}

	allocations := []accapi.Allocation{}
	child := tree.WalletsById[relationship.Child]
	if child != nil {
		for _, allocationId := range child.Allocations {
			allocation := tree.AllocationsById[allocationId]
			if allocation == nil || !allocation.Promise.Present || !promiseIds[allocation.Promise.Value] {
				continue
			}
			allocations = append(allocations, allocationToApi(promiseTree, allocation))
		}
	}

	sortApiAllocations(allocations)

	return accapi.AllocationGroup{
		Id:                0,
		Allocations:       util.NonNilSlice(allocations),
		Usage:             relationshipUsage(now, tree, relationship),
		Quota:             relationshipQuotaContributing(now, tree, relationship),
		UiOnlyActiveUsage: relationshipUiOnlyActiveUsage(now, tree, relationship),
		UiOnlyActiveQuota: relationshipActiveQuota(now, relationship),
	}
}

func sortApiAllocations(allocations []accapi.Allocation) {
	slices.SortFunc(allocations, func(a, b accapi.Allocation) int {
		if a.Retired != b.Retired {
			if a.Retired {
				return 1
			}
			return -1
		}
		if a.Activated != b.Activated {
			if a.Activated {
				return 1
			}
			return -1
		}
		if !a.StartDate.Time().Equal(b.StartDate.Time()) {
			if a.StartDate.Time().After(b.StartDate.Time()) {
				return -1
			}
			return 1
		}
		if !a.EndDate.Time().Equal(b.EndDate.Time()) {
			if a.EndDate.Time().Before(b.EndDate.Time()) {
				return -1
			}
			return 1
		}
		return cmp.Compare(a.Id, b.Id)
	})
}

func allocationToApi(promiseTree *PromiseTree, allocation *Allocation) accapi.Allocation {
	quota := allocation.QuotaSelf + allocation.QuotaChildren
	grant := allocation.Grant
	if !allocation.Retired && allocation.Promise.Present {
		promise := promiseTree.PromisesById[allocation.Promise.Value]
		if promise != nil {
			quota = promise.Quota
			if promise.Grant.Present {
				grant = promise.Grant
			}
		}
	}

	retiredQuota := allocation.RetiredQuota
	if retiredQuota == 0 && allocation.Retired {
		retiredQuota = allocation.QuotaSelf + allocation.QuotaChildren
	}

	return accapi.Allocation{
		Id:           int64(allocation.Id),
		StartDate:    fndapi.Timestamp(allocation.Start),
		EndDate:      fndapi.Timestamp(allocation.End),
		Quota:        quota,
		GrantedIn:    util.OptMap(grant, func(grant GrantId) int64 { return int64(grant) }),
		RetiredQuota: retiredQuota,
		Activated:    allocation.Activated,
		Retired:      allocation.Retired,
		RetiredUsage: allocation.RetiredUsage,
	}
}

func promiseRelationshipsByParent(promiseTree *PromiseTree, child WalletId) []promiseRelationship {
	byParent := map[WalletId][]*Promise{}
	for _, promiseId := range promiseTree.PromisesByChild[child] {
		promise := promiseTree.PromisesById[promiseId]
		if promise != nil {
			byParent[promise.Parent] = append(byParent[promise.Parent], promise)
		}
	}
	return promiseRelationshipsFromMap(child, byParent, false)
}

func promiseRelationshipsByChild(promiseTree *PromiseTree, parent WalletId) []promiseRelationship {
	byChild := map[WalletId][]*Promise{}
	for _, promiseId := range promiseTree.PromisesByParent[parent] {
		promise := promiseTree.PromisesById[promiseId]
		if promise != nil {
			byChild[promise.Child] = append(byChild[promise.Child], promise)
		}
	}
	return promiseRelationshipsFromMap(parent, byChild, true)
}

func promiseRelationshipsFromMap(wallet WalletId, grouped map[WalletId][]*Promise, walletIsParent bool) []promiseRelationship {
	relationships := []promiseRelationship{}
	for other, promises := range grouped {
		relationship := promiseRelationship{Promises: promises}
		if walletIsParent {
			relationship.Parent = wallet
			relationship.Child = other
		} else {
			relationship.Parent = other
			relationship.Child = wallet
		}
		slices.SortFunc(relationship.Promises, func(a, b *Promise) int {
			return cmp.Compare(int(a.Id), int(b.Id))
		})
		relationships = append(relationships, relationship)
	}
	slices.SortFunc(relationships, func(a, b promiseRelationship) int {
		if a.Parent != b.Parent {
			return cmp.Compare(int(a.Parent), int(b.Parent))
		}
		return cmp.Compare(int(a.Child), int(b.Child))
	})
	return relationships
}

func lowLevelRelationshipsByParent(tree *AccountingTree, child WalletId) []lowLevelRelationship {
	grouped := map[WalletId][]*Allocation{}
	root := []*Allocation{}
	wallet := tree.WalletsById[child]
	if wallet == nil {
		return nil
	}

	for _, allocationId := range wallet.Allocations {
		allocation := tree.AllocationsById[allocationId]
		if allocation == nil || allocation.Promise.Present {
			continue
		}
		if !allocation.Parent.Present {
			root = append(root, allocation)
			continue
		}
		parent := tree.AllocationsById[allocation.Parent.Value]
		if parent != nil {
			grouped[parent.Wallet] = append(grouped[parent.Wallet], allocation)
		}
	}

	result := []lowLevelRelationship{}
	if len(root) > 0 {
		result = append(result, lowLevelRelationship{Parent: util.OptNone[WalletId](), Child: child, Allocations: root})
	}
	for parent, allocations := range grouped {
		result = append(result, lowLevelRelationship{Parent: util.OptValue(parent), Child: child, Allocations: allocations})
	}
	sortLowLevelRelationships(result)
	return result
}

func lowLevelRelationshipsByChild(tree *AccountingTree, parent WalletId) []lowLevelRelationship {
	grouped := map[WalletId][]*Allocation{}
	for _, allocation := range tree.AllocationsById {
		if allocation == nil || allocation.Promise.Present || !allocation.Parent.Present {
			continue
		}
		parentAllocation := tree.AllocationsById[allocation.Parent.Value]
		if parentAllocation != nil && parentAllocation.Wallet == parent {
			grouped[allocation.Wallet] = append(grouped[allocation.Wallet], allocation)
		}
	}

	result := []lowLevelRelationship{}
	for child, allocations := range grouped {
		result = append(result, lowLevelRelationship{Parent: util.OptValue(parent), Child: child, Allocations: allocations})
	}
	sortLowLevelRelationships(result)
	return result
}

func sortLowLevelRelationships(relationships []lowLevelRelationship) {
	slices.SortFunc(relationships, func(a, b lowLevelRelationship) int {
		parentA := 0
		parentB := 0
		if a.Parent.Present {
			parentA = int(a.Parent.Value)
		}
		if b.Parent.Present {
			parentB = int(b.Parent.Value)
		}
		if parentA != parentB {
			return cmp.Compare(parentA, parentB)
		}
		return cmp.Compare(int(a.Child), int(b.Child))
	})
}

func walletTotalUsage(now time.Time, tree *AccountingTree, promiseTree *PromiseTree, wallet *Wallet) int64 {
	usage := wallet.Consumed
	for _, relationship := range promiseRelationshipsByChild(promiseTree, wallet.Id) {
		usage += relationshipUsage(now, tree, relationship)
	}
	for _, relationship := range lowLevelRelationshipsByChild(tree, wallet.Id) {
		usage += lowLevelRelationshipUsage(now, tree, relationship)
	}
	return usage
}

func walletUiOnlyActiveUsage(now time.Time, tree *AccountingTree, promiseTree *PromiseTree, wallet *Wallet) int64 {
	usage := int64(0)
	for _, relationship := range promiseRelationshipsByParent(promiseTree, wallet.Id) {
		usage += relationshipUiOnlyActiveUsage(now, tree, relationship)
	}
	for _, relationship := range lowLevelRelationshipsByParent(tree, wallet.Id) {
		usage += lowLevelRelationshipUiOnlyActiveUsage(now, tree, relationship)
	}
	return usage
}

func walletQuotaContributing(now time.Time, tree *AccountingTree, promiseTree *PromiseTree, wallet *Wallet) int64 {
	quota := int64(0)
	for _, relationship := range promiseRelationshipsByParent(promiseTree, wallet.Id) {
		quota += relationshipQuotaContributing(now, tree, relationship)
	}
	for _, relationship := range lowLevelRelationshipsByParent(tree, wallet.Id) {
		quota += lowLevelRelationshipQuotaContributing(now, tree, relationship)
	}
	return quota
}

func walletQuotaAllocated(now time.Time, tree *AccountingTree, promiseTree *PromiseTree, wallet *Wallet) int64 {
	quota := int64(0)
	for _, relationship := range promiseRelationshipsByChild(promiseTree, wallet.Id) {
		quota += relationshipQuotaContributing(now, tree, relationship)
	}
	for _, relationship := range lowLevelRelationshipsByChild(tree, wallet.Id) {
		quota += lowLevelRelationshipQuotaContributing(now, tree, relationship)
	}
	return quota
}

func walletActiveQuota(now time.Time, tree *AccountingTree, promiseTree *PromiseTree, wallet *Wallet) int64 {
	quota := walletPromiseActiveQuota(now, promiseTree, wallet.Id)
	for _, relationship := range lowLevelRelationshipsByParent(tree, wallet.Id) {
		quota += lowLevelRelationshipActiveQuota(now, relationship)
	}
	return quota
}

func walletPromiseActiveQuota(now time.Time, promiseTree *PromiseTree, walletId WalletId) int64 {
	quota := int64(0)
	for _, relationship := range promiseRelationshipsByParent(promiseTree, walletId) {
		quota += relationshipActiveQuota(now, relationship)
	}
	return quota
}

func relationshipUsage(now time.Time, tree *AccountingTree, relationship promiseRelationship) int64 {
	usage := int64(0)
	for _, allocation := range relationshipAllocations(tree, relationship) {
		if !tree.IsCapacityBased() && allocation.Retired {
			usage += allocation.RetiredUsage
		} else {
			usage += allocation.ConsumedSelf
		}
	}
	return usage
}

func relationshipUiOnlyActiveUsage(now time.Time, tree *AccountingTree, relationship promiseRelationship) int64 {
	usage := relationshipUsage(now, tree, relationship)
	if !tree.IsCapacityBased() {
		for _, allocation := range relationshipAllocations(tree, relationship) {
			if allocation.Activated && allocation.Retired {
				usage -= allocation.RetiredUsage
			}
		}
	}
	return max(usage, 0)
}

func lowLevelRelationshipUsage(now time.Time, tree *AccountingTree, relationship lowLevelRelationship) int64 {
	usage := int64(0)
	for _, allocation := range relationship.Allocations {
		if !tree.IsCapacityBased() && allocation.Retired {
			usage += allocation.RetiredUsage
		} else {
			usage += allocation.ConsumedSelf
		}
	}
	return usage
}

func lowLevelRelationshipUiOnlyActiveUsage(now time.Time, tree *AccountingTree, relationship lowLevelRelationship) int64 {
	usage := lowLevelRelationshipUsage(now, tree, relationship)
	if !tree.IsCapacityBased() {
		for _, allocation := range relationship.Allocations {
			if allocation.Activated && allocation.Retired {
				usage -= allocation.RetiredUsage
			}
		}
	}
	return max(usage, 0)
}

func lowLevelRelationshipQuotaContributing(now time.Time, tree *AccountingTree, relationship lowLevelRelationship) int64 {
	retiredContributes := !tree.IsCapacityBased()
	quota := int64(0)
	for _, allocation := range relationship.Allocations {
		if allocation.Activated && (retiredContributes || !allocation.Retired) {
			quota += allocation.QuotaSelf + allocation.QuotaChildren
		}
	}
	return quota
}

func lowLevelRelationshipActiveQuota(now time.Time, relationship lowLevelRelationship) int64 {
	quota := int64(0)
	for _, allocation := range relationship.Allocations {
		if allocation.Activated && !allocation.Retired {
			quota += allocation.QuotaSelf + allocation.QuotaChildren
		}
	}
	return quota
}

func relationshipQuotaContributing(now time.Time, tree *AccountingTree, relationship promiseRelationship) int64 {
	quota := int64(0)
	for _, promise := range relationship.Promises {
		if promiseContributes(now, tree, promise) {
			quota += promise.Quota
		}
	}
	return quota
}

func relationshipActiveQuota(now time.Time, relationship promiseRelationship) int64 {
	quota := int64(0)
	for _, promise := range relationship.Promises {
		if promiseActive(now, promise) {
			quota += promise.Quota
		}
	}
	return quota
}

func relationshipAllocations(tree *AccountingTree, relationship promiseRelationship) []*Allocation {
	promiseIds := map[PromiseId]bool{}
	for _, promise := range relationship.Promises {
		promiseIds[promise.Id] = true
	}

	wallet := tree.WalletsById[relationship.Child]
	if wallet == nil {
		return nil
	}

	allocations := []*Allocation{}
	for _, allocationId := range wallet.Allocations {
		allocation := tree.AllocationsById[allocationId]
		if allocation != nil && allocation.Promise.Present && promiseIds[allocation.Promise.Value] {
			allocations = append(allocations, allocation)
		}
	}
	return allocations
}

func promiseContributes(now time.Time, tree *AccountingTree, promise *Promise) bool {
	if promise == nil || now.Before(promise.Start) {
		return false
	}
	return !tree.IsCapacityBased() || now.Before(promise.End)
}

func promiseActive(now time.Time, promise *Promise) bool {
	return promise != nil && !now.Before(promise.Start) && now.Before(promise.End)
}
