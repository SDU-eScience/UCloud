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

// walletBrowseKey returns the stable sort and pagination key used by wallet browse responses.
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

// walletToApi builds the compatibility-facing WalletV2 view for a concrete wallet.
//
// The returned quota fields intentionally mix promise-level and allocation-level concepts used by existing clients:
// Quota is contributing inbound quota, TotalAllocated is quota this wallet has allocated onward, and MaxUsable is the
// admission-control value from walletMaxUsable. Use the lower-level measurement helpers below when adding new fields so
// it is clear whether a value is promise-facing, concrete allocation-facing, active-only or UI-only.
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

// parentOrChildWallet returns the compact API identity for a known wallet id.
//
// It is used in allocation group parent/child links and delegates missing-wallet handling to optParentOrChildWallet.
func parentOrChildWallet(tree *AccountingTree, walletId WalletId) util.Option[accapi.ParentOrChildWallet] {
	return optParentOrChildWallet(tree, util.OptValue(walletId))
}

// optParentOrChildWallet returns a compact API wallet identity when the wallet id is present and resolvable.
//
// The API type carries project-related display fields even for user wallets; this helper mirrors the historical mapping
// used by wallet allocation groups.
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

// lowLevelAllocationGroupToApi converts concrete, non-promise allocations between two wallets into an API group.
//
// Its quota and usage measurements come only from the concrete allocations in the relationship. Use this for manually
// created allocation rows; promise-backed rows should use allocationGroupToApi so promise quota remains visible.
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

// allocationGroupToApi converts promises between two wallets into an API group.
//
// The group uses promise quota for Quota and active promise periods for UiOnlyActiveQuota, while usage is measured from
// the concrete materializations belonging to those promises. This preserves compatibility-facing promise visibility even
// when materialized parent-backed quota is lower.
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

// sortApiAllocations orders allocations for stable API output.
//
// Active and current/future allocations are shown before retired allocations, then newer starts and earlier expirations
// are preferred. The final id comparison makes the order deterministic.
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

// allocationToApi converts one concrete allocation row to the API allocation shape.
//
// For active promise-backed allocations, Quota is the promise quota rather than the materialized allocation quota. That
// is compatibility-facing and descriptive; callers that need concrete effective quota should inspect Allocation fields
// directly or use promiseWalletEffectiveReportQuota for wallet-level admission control.
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

// promiseRelationshipsByParent groups all promises that grant quota to child by their parent wallet.
//
// Use this when rendering or measuring the inbound allocation groups of a wallet. The returned relationships contain
// promise definitions only; concrete materializations are found later by relationshipAllocations.
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

// promiseRelationshipsByChild groups all promises made by parent by their child wallet.
//
// Use this when rendering or measuring the children of a wallet. The grouping is promise-level and does not imply that
// the promises are fully materialized in the concrete allocation tree.
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

// promiseRelationshipsFromMap builds sorted promise relationships from an already grouped promise map.
//
// walletIsParent controls whether the supplied wallet id is assigned to the Parent or Child side of each relationship.
// Promises and relationships are sorted so API output and aggregate measurements are deterministic.
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

// lowLevelRelationshipsByParent groups concrete non-promise allocations owned by child by their parent wallet.
//
// Root allocations are represented with no parent. Promise-backed allocations are deliberately excluded because promise
// groups use promiseRelationshipsByParent plus relationshipAllocations.
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

// lowLevelRelationshipsByChild groups concrete non-promise child allocations by child wallet for a parent wallet.
//
// Use this for manually created allocation children. Promise-backed children are excluded so they are measured through
// promise relationships instead of concrete materialization rows.
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

// sortLowLevelRelationships gives concrete allocation relationships deterministic parent/child order.
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

// walletTotalUsage measures wallet-local usage plus usage allocated onward to all children.
//
// LocalUsage in the API is wallet.Consumed only. TotalUsage uses this helper to add both promise-backed and low-level
// child relationship usage, making it a subtree-facing usage number rather than an admission-control number.
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

// walletUiOnlyActiveUsage measures inbound usage that should be shown as active in the UI.
//
// For non-capacity products, retired usage is subtracted because it remains contributing for accounting compatibility
// but should not inflate the UI's active usage display. This is a presentation helper, not a lock/admission helper.
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

// walletQuotaContributing measures compatibility-facing inbound quota for a wallet.
//
// Promise relationships contribute promise.Quota while they are contributing, even if less quota is currently
// materialized in the concrete allocation tree. Low-level relationships contribute active concrete allocation quota, and
// retired quota for non-capacity products. Do not use this alone for overbooking-sensitive admission control.
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

// walletQuotaAllocated measures compatibility-facing quota that this wallet has allocated to children.
//
// It mirrors walletQuotaContributing from the parent side: promise children contribute promise quota and low-level
// children contribute concrete allocation quota. Use it for descriptive API fields such as TotalAllocated.
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

// walletActiveQuota measures currently active inbound quota for UI filtering and active-quota fields.
//
// Promise quota is included only during the promise active interval. Low-level quota is included only from activated,
// non-retired concrete allocations. This is still a quota display metric, not a remaining-usable calculation.
func walletActiveQuota(now time.Time, tree *AccountingTree, promiseTree *PromiseTree, wallet *Wallet) int64 {
	quota := walletPromiseActiveQuota(now, promiseTree, wallet.Id)
	for _, relationship := range lowLevelRelationshipsByParent(tree, wallet.Id) {
		quota += lowLevelRelationshipActiveQuota(now, relationship)
	}
	return quota
}

// walletPromiseActiveQuota measures active promise quota granted to a wallet.
//
// It ignores low-level allocations and is used as the promise component of walletActiveQuota.
func walletPromiseActiveQuota(now time.Time, promiseTree *PromiseTree, walletId WalletId) int64 {
	quota := int64(0)
	for _, relationship := range promiseRelationshipsByParent(promiseTree, walletId) {
		quota += relationshipActiveQuota(now, relationship)
	}
	return quota
}

// relationshipUsage measures usage recorded on concrete materializations for a promise relationship.
//
// For capacity-based products this is current ConsumedSelf. For non-capacity products, retired allocations contribute
// RetiredUsage so historical consumption remains visible after retirement cleanup. The promise quota itself is not used
// to derive usage.
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

// relationshipUiOnlyActiveUsage measures promise relationship usage for active UI display.
//
// It starts from relationshipUsage, then removes retired usage for non-capacity products because that usage remains
// accounting-contributing but is no longer active in the UI sense.
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

// lowLevelRelationshipUsage measures usage on concrete non-promise allocations in a relationship.
//
// Its retirement semantics match relationshipUsage: capacity products use current ConsumedSelf, while non-capacity
// products keep RetiredUsage visible after retirement cleanup.
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

// lowLevelRelationshipUiOnlyActiveUsage measures concrete relationship usage for active UI display.
//
// It subtracts retired usage for non-capacity products, mirroring relationshipUiOnlyActiveUsage for non-promise
// allocation groups.
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

// lowLevelRelationshipQuotaContributing measures compatibility-facing quota on concrete non-promise allocations.
//
// Activated allocations contribute their total quota. Retired allocations keep contributing only for non-capacity
// products, where retired quota still represents historical entitlement for accounting reports.
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

// lowLevelRelationshipActiveQuota measures currently active concrete quota on non-promise allocations.
//
// Only activated, non-retired allocations are counted. Use this for active UI quota, not for promise compatibility quota.
func lowLevelRelationshipActiveQuota(now time.Time, relationship lowLevelRelationship) int64 {
	quota := int64(0)
	for _, allocation := range relationship.Allocations {
		if allocation.Activated && !allocation.Retired {
			quota += allocation.QuotaSelf + allocation.QuotaChildren
		}
	}
	return quota
}

// relationshipQuotaContributing measures compatibility-facing quota for a promise relationship.
//
// It sums promise.Quota for promises that currently contribute. This may exceed the concrete allocation-tree capacity in
// overbooked trees, so admission-control paths should combine it with effective concrete quota before allowing work.
func relationshipQuotaContributing(now time.Time, tree *AccountingTree, relationship promiseRelationship) int64 {
	quota := int64(0)
	for _, promise := range relationship.Promises {
		if promiseContributes(now, tree, promise) {
			quota += promise.Quota
		}
	}
	return quota
}

// relationshipActiveQuota measures promise quota whose promise interval is active at now.
//
// This is a promise-level UI/display metric. It does not check whether the promise has materialized concrete backing
// allocations.
func relationshipActiveQuota(now time.Time, relationship promiseRelationship) int64 {
	quota := int64(0)
	for _, promise := range relationship.Promises {
		if promiseActive(now, promise) {
			quota += promise.Quota
		}
	}
	return quota
}

// relationshipAllocations returns concrete allocations that materialize any promise in a promise relationship.
//
// The result is used for usage and allocation-list rendering. It may be empty even when the relationship has promise
// quota, because promise quota can exist before or without concrete materialization.
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

// promiseContributes reports whether a promise should count toward compatibility-facing contributing quota.
//
// Capacity-based products stop contributing at the promise end. Non-capacity products continue contributing after end so
// historical quota remains available for reporting semantics.
func promiseContributes(now time.Time, tree *AccountingTree, promise *Promise) bool {
	if promise == nil || now.Before(promise.Start) {
		return false
	}
	return !tree.IsCapacityBased() || now.Before(promise.End)
}

// promiseActive reports whether now is inside the promise's active interval.
//
// Active promises drive active quota and UI filtering. This is stricter than promiseContributes for non-capacity
// products, where a promise can keep contributing after it is no longer active.
func promiseActive(now time.Time, promise *Promise) bool {
	return promise != nil && !now.Before(promise.Start) && now.Before(promise.End)
}
