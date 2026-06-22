package accounting

import (
	"cmp"
	"net/http"
	"slices"
	"sync/atomic"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

// Promise system
// =====================================================================================================================
// This file implements the promise layer above the low-level accounting tree. The low-level tree owns concrete wallet
// and allocation state. The promise layer owns intent: a parent wallet promises quota to a child wallet over a period,
// and reconciliation decides how that intent becomes concrete allocations over time.
//
// Promises deliberately allow overbooking. A set of promises may describe more quota than the current root-backed
// allocations can expose. Reconciliation must therefore be best-effort: it materializes as much as the existing
// allocation tree can support, never creates new root capacity, and leaves the remaining promise under-covered.
//
// The planner is chain-local and update-first. It tries to keep current promise materializations stable by changing
// their split in place. A promise may have multiple simultaneous materializations, but at most one for each backing
// parent allocation. If a materialization cannot be edited because of time boundaries, the planner creates a successor
// allocation under the same promise and under an existing parent allocation. Successors are ordered by timestamps. There
// is no separate linked-list field.

// Core promise types and globals
// ---------------------------------------------------------------------------------------------------------------------
// PromiseTree is the in-memory registry for promise intent inside an AccountingTree. The accounting tree mutex protects
// these indexes together with the low-level wallet and allocation state.

type PromiseId int

type Promise struct {
	Id     PromiseId
	Parent WalletId
	Child  WalletId

	Start time.Time
	End   time.Time

	Quota int64
	Grant util.Option[GrantId]
	Dirty bool
}

type PromiseTree struct {
	PromisesById     map[PromiseId]*Promise
	PromisesByParent map[WalletId][]PromiseId
	PromisesByChild  map[WalletId][]PromiseId
}

func PromiseCreate(
	now time.Time,
	category accapi.ProductCategoryIdV2,
	parentWallet WalletId,
	childWallet WalletId,
	start time.Time,
	end time.Time,
	quota int64,
	grant util.Option[GrantId],
) (PromiseId, *util.HttpError) {
	var promiseId PromiseId
	err := treeMutate(category, func(tree *AccountingTree) *util.HttpError {
		if _, ok := tree.WalletsById[parentWallet]; !ok {
			return util.HttpErr(http.StatusNotFound, "unknown parent wallet")
		}
		child := tree.WalletsById[childWallet]
		if child == nil {
			return util.HttpErr(http.StatusNotFound, "unknown child wallet")
		}
		if parentWallet == childWallet {
			return util.HttpErr(http.StatusBadRequest, "parent and child wallet must be different")
		}
		if start.After(end) {
			return util.HttpErr(http.StatusBadRequest, "start must occur before the end of a promise")
		}
		if quota < 0 {
			return util.HttpErr(http.StatusBadRequest, "quota must not be negative")
		}

		promiseTree := &tree.PromiseTree
		if grant.Present {
			for _, existingId := range promiseTree.PromisesByParent[parentWallet] {
				existing := promiseTree.PromisesById[existingId]
				if existing == nil || !existing.Grant.Present {
					continue
				}
				if existing.Grant.Value == grant.Value && existing.Child == childWallet && existing.Start.Equal(start) && existing.End.Equal(end) && existing.Quota == quota {
					promiseId = existing.Id
					return nil
				}
			}
		}

		promiseId = PromiseId(promiseGlobals.PromiseIdAcc.Add(1))
		promise := &Promise{
			Id:     promiseId,
			Parent: parentWallet,
			Child:  childWallet,
			Start:  start,
			End:    end,
			Quota:  quota,
			Grant:  grant,
			Dirty:  true,
		}

		promiseTree.PromisesById[promiseId] = promise
		promiseTree.PromisesByParent[parentWallet] = append(promiseTree.PromisesByParent[parentWallet], promiseId)
		promiseTree.PromisesByChild[childWallet] = append(promiseTree.PromisesByChild[childWallet], promiseId)

		walletMarkSignificantUpdate(now, tree, tree.WalletsById[parentWallet])
		walletMarkSignificantUpdate(now, tree, child)
		walletReevaluateLock(now, tree, child)
		return nil
	})
	return promiseId, err
}

var promiseGlobals struct {
	PromiseIdAcc atomic.Int64
}

// Public reconciliation entry point
// ---------------------------------------------------------------------------------------------------------------------
// PromiseReconcile is invoked for one wallet owner with a concrete minimum request. Reconciliation only works on that
// wallet's ancestor promise chain. If observed usage drops below current quota, the same path can shrink existing
// materializations.
//
// Reconciliation proceeds bottom-up by sorting promises by child depth. This gives child demand a chance to influence
// parent targets on later passes and matches the operational model where periodic reconciliation can gradually expose
// capacity through a chain.

func PromiseReconcile(
	now time.Time,
	tree *AccountingTree,
	owner accapi.WalletOwner,
	minimumRequest int64,
) {
	lifecycleScanEx(now, tree, nil)

	wallet := tree.WalletsByOwner[owner.Reference()]
	if wallet == nil {
		return
	}
	minimumRequest = max(minimumRequest, 0)
	if minimumRequest == 0 && promiseWalletCoverage(now, tree, wallet) == 0 {
		return
	}

	promiseReconcileWallet(now, tree, wallet, minimumRequest, promiseWalletRequiredChildren(now, tree, wallet), map[WalletId]bool{})
	walletReevaluateLock(now, tree, wallet)
	walletMarkSignificantUpdate(now, tree, wallet)

	return
}

func promiseReconcileWallet(
	now time.Time,
	tree *AccountingTree,
	wallet *Wallet,
	requiredSelf int64,
	requiredChildren int64,
	seen map[WalletId]bool,
) int64 {
	if wallet == nil || seen[wallet.Id] {
		return 0
	}
	seen[wallet.Id] = true
	defer delete(seen, wallet.Id)

	requiredSelf = max(requiredSelf, 0)
	requiredChildren = max(requiredChildren, 0)
	promises := promiseActiveIncoming(now, tree, wallet.Id)

	if requiredSelf < promiseWalletCoverage(now, tree, wallet) {
		promiseShrinkWallet(now, tree, wallet, requiredSelf)
	}

	selfNeed := max(requiredSelf-promiseWalletCoverage(now, tree, wallet), 0)
	childrenNeed := max(requiredChildren-promiseWalletChildrenCoverage(now, tree, wallet), 0)
	if selfNeed > 0 || childrenNeed > 0 {
		for _, promise := range promises {
			promiseAssertMaterializations(now, tree, promise)
			selfNeed, childrenNeed = promiseGrowExisting(now, tree, promise, selfNeed, childrenNeed, seen)
			if selfNeed == 0 && childrenNeed == 0 {
				break
			}
		}
	}
	if selfNeed > 0 || childrenNeed > 0 {
		for _, promise := range promises {
			promiseAssertMaterializations(now, tree, promise)
			selfNeed, childrenNeed = promiseCreateAllocations(now, tree, promise, selfNeed, childrenNeed, seen)
			if selfNeed == 0 && childrenNeed == 0 {
				break
			}
		}
	}

	walletReevaluateLock(now, tree, wallet)
	return requiredSelf - selfNeed + requiredChildren - childrenNeed
}

func promiseActiveIncoming(now time.Time, tree *AccountingTree, child WalletId) []*Promise {
	promises := []*Promise{}
	for _, id := range tree.PromiseTree.PromisesByChild[child] {
		promise := tree.PromiseTree.PromisesById[id]
		if promise != nil && promise.Quota > 0 && !now.Before(promise.Start) && now.Before(promise.End) {
			promises = append(promises, promise)
		}
	}
	slices.SortFunc(promises, func(a, b *Promise) int { return cmp.Compare(int(a.Id), int(b.Id)) })
	return promises
}

func promiseAllocationsFor(now time.Time, tree *AccountingTree, promise *Promise, activeOnly bool) []*Allocation {
	child := tree.WalletsById[promise.Child]
	if child == nil {
		return nil
	}
	allocations := []*Allocation{}
	for _, id := range child.Allocations {
		allocation := tree.AllocationsById[id]
		if allocation == nil || !allocation.Promise.Present || allocation.Promise.Value != promise.Id {
			continue
		}
		if activeOnly && !allocation.IsActive(now) {
			continue
		}
		allocations = append(allocations, allocation)
	}
	slices.SortFunc(allocations, func(a, b *Allocation) int {
		if !a.End.Equal(b.End) {
			if a.End.Before(b.End) {
				return -1
			}
			return 1
		}
		return cmp.Compare(int(a.Id), int(b.Id))
	})
	return allocations
}

func promiseWalletCoverage(now time.Time, tree *AccountingTree, wallet *Wallet) int64 {
	coverage := int64(0)
	for _, id := range wallet.Allocations {
		allocation := tree.AllocationsById[id]
		if allocation == nil || !allocation.Promise.Present {
			continue
		}
		if allocation.IsActive(now) && (!tree.IsCapacityBased() || !allocation.Retired) {
			coverage += allocation.QuotaSelf
		} else if !tree.IsCapacityBased() && allocation.Retired {
			coverage += allocation.ConsumedSelf
		}
	}
	return coverage
}

func promiseWalletChildrenCoverage(now time.Time, tree *AccountingTree, wallet *Wallet) int64 {
	coverage := int64(0)
	for _, id := range wallet.Allocations {
		allocation := tree.AllocationsById[id]
		if allocation != nil && allocation.Promise.Present && allocation.IsActive(now) && (!tree.IsCapacityBased() || !allocation.Retired) {
			coverage += allocation.QuotaChildren
		}
	}
	return coverage
}

func promiseWalletRequiredChildren(now time.Time, tree *AccountingTree, wallet *Wallet) int64 {
	required := int64(0)
	for _, id := range wallet.Allocations {
		allocation := tree.AllocationsById[id]
		if allocation != nil && allocation.IsActive(now) && !allocation.Retired {
			required += allocation.ReservedChildren
		}
	}
	return required
}

func promiseDelivered(now time.Time, tree *AccountingTree, promise *Promise) int64 {
	delivered := int64(0)
	for _, allocation := range promiseAllocationsFor(now, tree, promise, false) {
		if allocation.IsActive(now) && (!tree.IsCapacityBased() || !allocation.Retired) {
			delivered += allocation.QuotaSelf + allocation.QuotaChildren
		} else if !tree.IsCapacityBased() && allocation.Retired {
			delivered += allocation.ConsumedSelf + allocation.ReservedChildren
		}
	}
	return delivered
}

func promiseShrinkWallet(now time.Time, tree *AccountingTree, wallet *Wallet, requiredSelf int64) {
	allocations := []*Allocation{}
	for _, id := range wallet.Allocations {
		allocation := tree.AllocationsById[id]
		if allocation != nil && allocation.Promise.Present && allocation.IsActive(now) && !allocation.Retired {
			allocations = append(allocations, allocation)
		}
	}
	slices.SortFunc(allocations, func(a, b *Allocation) int {
		if !a.End.Equal(b.End) {
			if a.End.After(b.End) {
				return -1
			}
			return 1
		}
		return cmp.Compare(int(b.Id), int(a.Id))
	})

	coverage := promiseWalletCoverage(now, tree, wallet)
	for _, allocation := range allocations {
		excess := coverage - requiredSelf
		if excess <= 0 {
			break
		}
		oldTotal := allocation.QuotaSelf + allocation.QuotaChildren
		oldSelf := allocation.QuotaSelf
		newSelf := max(allocation.ConsumedSelf, allocation.QuotaSelf-min(excess, max(allocation.QuotaSelf-allocation.ConsumedSelf, 0)))
		allocationMutate(now, tree, allocation.Id, func(alloc *Allocation, parent util.Option[*Allocation]) {
			alloc.QuotaSelf = newSelf
			alloc.QuotaChildren = alloc.ReservedChildren
			if parent.Present {
				parent.Value.ReservedChildren += alloc.QuotaSelf + alloc.QuotaChildren - oldTotal
			}
		})
		coverage -= oldSelf - newSelf
		promiseShrinkParent(now, tree, allocation)
	}
}

func promiseShrinkParent(now time.Time, tree *AccountingTree, child *Allocation) {
	seenParents := map[AllocationId]bool{}
	current := child
	for current.Parent.Present {
		parent := tree.AllocationsById[current.Parent.Value]
		if parent == nil {
			log.Fatal("allocation %d has missing parent %d", current.Id, current.Parent.Value)
		}
		if seenParents[parent.Id] {
			log.Fatal("allocation parent cycle at %d", parent.Id)
		}
		seenParents[parent.Id] = true
		if !parent.Promise.Present {
			break
		}
		oldTotal := parent.QuotaSelf + parent.QuotaChildren
		allocationMutate(now, tree, parent.Id, func(alloc *Allocation, grandparent util.Option[*Allocation]) {
			alloc.QuotaSelf = max(alloc.ConsumedSelf, alloc.QuotaSelf)
			alloc.QuotaChildren = alloc.ReservedChildren
			if grandparent.Present {
				grandparent.Value.ReservedChildren += alloc.QuotaSelf + alloc.QuotaChildren - oldTotal
			}
		})
		current = parent
	}
}

func promiseGrowExisting(now time.Time, tree *AccountingTree, promise *Promise, selfNeed int64, childrenNeed int64, seen map[WalletId]bool) (int64, int64) {
	for _, allocation := range promiseAllocationsFor(now, tree, promise, true) {
		if selfNeed == 0 && childrenNeed == 0 {
			break
		}
		available := promise.Quota - promiseDelivered(now, tree, promise) + allocation.QuotaSelf + allocation.QuotaChildren
		if available <= allocation.QuotaSelf+allocation.QuotaChildren {
			continue
		}
		growSelf, growChildren := promiseSplitNeed(min(selfNeed+childrenNeed, available-allocation.QuotaSelf-allocation.QuotaChildren), selfNeed, childrenNeed)
		grown := promiseGrowAllocation(now, tree, allocation, growSelf, growChildren, seen)
		selfGrown := min(growSelf, grown)
		selfNeed -= selfGrown
		childrenNeed -= grown - selfGrown
	}
	return selfNeed, childrenNeed
}

func promiseCreateAllocations(now time.Time, tree *AccountingTree, promise *Promise, selfNeed int64, childrenNeed int64, seen map[WalletId]bool) (int64, int64) {
	parents := promiseParentAllocations(now, tree, promise)
	if len(parents) == 0 {
		if parentWallet := tree.WalletsById[promise.Parent]; parentWallet != nil {
			promiseReconcileWallet(now, tree, parentWallet, parentWallet.Consumed, selfNeed+childrenNeed, seen)
			parents = promiseParentAllocations(now, tree, promise)
		}
	}
	for _, parent := range parents {
		if selfNeed == 0 && childrenNeed == 0 {
			break
		}
		if promiseHasActiveAllocationUnder(now, tree, promise, parent.Id) {
			continue
		}
		available := promise.Quota - promiseDelivered(now, tree, promise)
		if available <= 0 {
			break
		}
		growSelf, growChildren := promiseSplitNeed(min(selfNeed+childrenNeed, available), selfNeed, childrenNeed)
		grown := promiseExposeChildCapacity(now, tree, parent, growSelf+growChildren, seen)
		if grown <= 0 {
			continue
		}
		growSelf, growChildren = promiseSplitNeed(min(grown, growSelf+growChildren), growSelf, growChildren)
		start := maxTime(now, promise.Start, parent.Start)
		end := minTime(promise.End, parent.End)
		if !start.Before(end) {
			continue
		}
		allocation := &Allocation{
			Id:               AllocationId(accGlobals.AllocIdAcc.Add(1)),
			Wallet:           promise.Child,
			Parent:           util.OptValue(parent.Id),
			Start:            start,
			End:              end,
			QuotaSelf:        growSelf,
			QuotaChildren:    growChildren,
			ConsumedSelf:     0,
			ReservedChildren: 0,
			Grant:            promise.Grant,
			Promise:          util.OptValue(promise.Id),
			Dirty:            true,
		}
		tree.AllocationsById[allocation.Id] = allocation
		child := tree.WalletsById[promise.Child]
		child.Allocations = append(child.Allocations, allocation.Id)
		child.Dirty = true
		allocationMutate(now, tree, parent.Id, func(parent *Allocation, grandparent util.Option[*Allocation]) {
			parent.Children = append(parent.Children, allocation.Id)
			parent.ReservedChildren += allocation.QuotaSelf + allocation.QuotaChildren
		})
		allocationMutate(now, tree, allocation.Id, func(alloc *Allocation, parent util.Option[*Allocation]) {})
		lifecycleScanEx(now, tree, []AllocationId{allocation.Id})
		walletMarkSignificantUpdate(now, tree, child)
		walletMarkSignificantUpdate(now, tree, tree.WalletsById[parent.Wallet])
		walletReevaluateLock(now, tree, child)
		walletReevaluateLock(now, tree, tree.WalletsById[parent.Wallet])
		selfNeed -= growSelf
		childrenNeed -= growChildren
	}
	return selfNeed, childrenNeed
}

func promiseGrowAllocation(now time.Time, tree *AccountingTree, allocation *Allocation, self int64, children int64, seen map[WalletId]bool) int64 {
	if self+children <= 0 || !allocation.Parent.Present {
		return 0
	}
	parent := tree.AllocationsById[allocation.Parent.Value]
	if parent == nil {
		log.Fatal("promise allocation %d has missing parent", allocation.Id)
	}
	grown := promiseExposeChildCapacity(now, tree, parent, self+children, seen)
	if grown <= 0 {
		return 0
	}
	self, children = promiseSplitNeed(grown, self, children)
	allocationMutate(now, tree, allocation.Id, func(alloc *Allocation, parent util.Option[*Allocation]) {
		alloc.QuotaSelf += self
		alloc.QuotaChildren += children
		if parent.Present {
			parent.Value.ReservedChildren += self + children
		}
	})
	walletMarkSignificantUpdate(now, tree, tree.WalletsById[allocation.Wallet])
	walletMarkSignificantUpdate(now, tree, tree.WalletsById[parent.Wallet])
	return self + children
}

func promiseExposeChildCapacity(now time.Time, tree *AccountingTree, parent *Allocation, need int64, seen map[WalletId]bool) int64 {
	if need <= 0 {
		return 0
	}
	free := max(parent.QuotaChildren-parent.ReservedChildren, 0)
	if free < need && parent.Promise.Present {
		parentWallet := tree.WalletsById[parent.Wallet]
		promiseReconcileWallet(now, tree, parentWallet, parentWallet.Consumed, parent.ReservedChildren+need, seen)
		free = max(parent.QuotaChildren-parent.ReservedChildren, 0)
	}
	if free < need && !parent.Parent.Present {
		convert := min(need-free, max(parent.QuotaSelf-parent.ConsumedSelf, 0))
		if convert > 0 {
			allocationMutate(now, tree, parent.Id, func(alloc *Allocation, parent util.Option[*Allocation]) {
				alloc.QuotaSelf -= convert
				alloc.QuotaChildren += convert
			})
			free += convert
		}
	}
	return min(need, free)
}

func promiseParentAllocations(now time.Time, tree *AccountingTree, promise *Promise) []*Allocation {
	parentWallet := tree.WalletsById[promise.Parent]
	if parentWallet == nil {
		return nil
	}
	allocations := []*Allocation{}
	for _, id := range parentWallet.Allocations {
		allocation := tree.AllocationsById[id]
		if allocation != nil && allocation.IsActive(now) && !allocation.Retired && allocation.Start.Before(promise.End) && promise.Start.Before(allocation.End) {
			allocations = append(allocations, allocation)
		}
	}
	slices.SortFunc(allocations, func(a, b *Allocation) int {
		if !a.End.Equal(b.End) {
			if a.End.Before(b.End) {
				return -1
			}
			return 1
		}
		return cmp.Compare(int(a.Id), int(b.Id))
	})
	return allocations
}

func promiseHasActiveAllocationUnder(now time.Time, tree *AccountingTree, promise *Promise, parent AllocationId) bool {
	for _, allocation := range promiseAllocationsFor(now, tree, promise, true) {
		if allocation.Parent.Present && allocation.Parent.Value == parent {
			return true
		}
	}
	return false
}

func promiseSplitNeed(amount int64, selfNeed int64, childrenNeed int64) (int64, int64) {
	self := min(amount, selfNeed)
	return self, min(amount-self, childrenNeed)
}

func promiseAssertMaterializations(now time.Time, tree *AccountingTree, promise *Promise) {
	seenParent := map[AllocationId]AllocationId{}
	for _, allocation := range promiseAllocationsFor(now, tree, promise, false) {
		if allocation.Wallet != promise.Child {
			log.Fatal("promise %d allocation %d has wallet %d, want %d", promise.Id, allocation.Id, allocation.Wallet, promise.Child)
		}
		if !allocation.Parent.Present {
			log.Fatal("promise %d allocation %d has no parent", promise.Id, allocation.Id)
		}
		parent := tree.AllocationsById[allocation.Parent.Value]
		if parent == nil || parent.Wallet != promise.Parent {
			log.Fatal("promise %d allocation %d has invalid parent", promise.Id, allocation.Id)
		}
		if allocation.Start.Before(promise.Start) || allocation.End.After(promise.End) || allocation.Start.Before(parent.Start) || allocation.End.After(parent.End) {
			log.Fatal("promise %d allocation %d period is outside promise or parent", promise.Id, allocation.Id)
		}
		if !allocation.IsActive(now) {
			continue
		}
		if previous, ok := seenParent[parent.Id]; ok {
			log.Fatal("promise %d has multiple active allocations for parent %d: %d and %d", promise.Id, parent.Id, previous, allocation.Id)
		}
		seenParent[parent.Id] = allocation.Id
	}
}

func promiseWalletEffectiveReportQuota(now time.Time, tree *AccountingTree, wallet *Wallet) (int64, bool) {
	return promiseWalletEffectiveReportQuotaSeen(now, tree, wallet, map[WalletId]bool{})
}

func promiseWalletEffectiveReportQuotaSeen(now time.Time, tree *AccountingTree, wallet *Wallet, seen map[WalletId]bool) (int64, bool) {
	if wallet == nil {
		return 0, false
	}
	if seen[wallet.Id] {
		return 0, true
	}
	seen[wallet.Id] = true
	defer delete(seen, wallet.Id)

	quota := int64(0)
	promises := promiseActiveIncoming(now, tree, wallet.Id)
	for _, promise := range promises {
		direct := int64(0)
		delivered := int64(0)
		for _, allocation := range promiseAllocationsFor(now, tree, promise, false) {
			if allocation.IsActive(now) && (!tree.IsCapacityBased() || !allocation.Retired) {
				direct += allocation.QuotaSelf
				delivered += allocation.QuotaSelf + allocation.QuotaChildren
			} else if !tree.IsCapacityBased() && allocation.Retired {
				delivered += allocation.ConsumedSelf + allocation.ReservedChildren
			}
		}
		remaining := max(promise.Quota-delivered, 0)
		if remaining > 0 {
			direct += min(remaining, promiseReadableParentCapacitySeen(now, tree, promise, seen))
		}
		quota += direct
	}
	return quota, len(promises) > 0
}

func promiseReadableParentCapacity(now time.Time, tree *AccountingTree, promise *Promise) int64 {
	return promiseReadableParentCapacitySeen(now, tree, promise, map[WalletId]bool{})
}

func promiseReadableParentCapacitySeen(now time.Time, tree *AccountingTree, promise *Promise, seen map[WalletId]bool) int64 {
	capacity := int64(0)
	for _, parent := range promiseParentAllocations(now, tree, promise) {
		capacity += promiseAllocationChildCapacityPotentialSeen(now, tree, parent, seen)
	}
	if capacity == 0 {
		parentWallet := tree.WalletsById[promise.Parent]
		parentQuota, ok := promiseWalletEffectiveReportQuotaSeen(now, tree, parentWallet, seen)
		if ok && parentWallet != nil {
			capacity = max(parentQuota-parentWallet.Consumed-promiseWalletRequiredChildren(now, tree, parentWallet), 0)
		}
	}
	return capacity
}

func promiseAllocationChildCapacityPotentialSeen(now time.Time, tree *AccountingTree, allocation *Allocation, seen map[WalletId]bool) int64 {
	if allocation == nil || seen[allocation.Wallet] {
		return 0
	}
	capacity := max(allocation.QuotaChildren-allocation.ReservedChildren, 0)
	capacity += max(allocation.QuotaSelf-allocation.ConsumedSelf, 0)
	capacity += promiseAllocationGrowableTotalSeen(now, tree, allocation, seen)
	return capacity
}

func promiseAllocationGrowableTotalSeen(now time.Time, tree *AccountingTree, allocation *Allocation, seen map[WalletId]bool) int64 {
	if allocation == nil || !allocation.Promise.Present || !allocation.Parent.Present {
		return 0
	}
	promise := tree.PromiseTree.PromisesById[allocation.Promise.Value]
	if promise == nil {
		return 0
	}
	remaining := promise.Quota - promiseDelivered(now, tree, promise)
	if remaining <= 0 {
		return 0
	}
	parent := tree.AllocationsById[allocation.Parent.Value]
	return min(remaining, promiseAllocationChildCapacityPotentialSeen(now, tree, parent, seen))
}

func maxTime(values ...time.Time) time.Time {
	result := values[0]
	for _, value := range values[1:] {
		if value.After(result) {
			result = value
		}
	}
	return result
}

func minTime(values ...time.Time) time.Time {
	result := values[0]
	for _, value := range values[1:] {
		if value.Before(result) {
			result = value
		}
	}
	return result
}
