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

	promiseTree := &tree.PromiseTree
	wallet := tree.WalletsByOwner[owner.Reference()]
	if wallet == nil {
		return
	}

	requiredQuota := minimumRequest
	currentQuota := promiseWalletReportQuota(tree, wallet)
	if currentQuota == requiredQuota {
		return
	}

	shrinkExisting := false
	if currentQuota > requiredQuota && wallet.Consumed <= minimumRequest {
		shrinkExisting = true
	}

	promiseUpdateWalletTrend(now, tree, wallet, minimumRequest)
	promises := promiseRelevantPromisesForWallet(promiseTree, wallet.Id)
	promiseSort(promiseTree, promises)

	for _, promise := range promises {
		promiseReconcileOne(now, tree, promise, !shrinkExisting)
	}

	if promiseWalletReportQuota(tree, wallet) >= requiredQuota {
		return
	}

	// NOTE(Dan): If we do not have enough to cover the request, we will actively start stealing from subtrees which
	// have any unused resources. This is done to ensure that the system can actually reach 100% utilization without
	// relying on eventual reconciliation which releases resources. In problemRoots we will track the roots that are
	// unable to deliver the required resources, these will be used for the search.

	problemRoots := map[AllocationId]util.Empty{}
	addProblemRootFromAllocation := func(allocationId AllocationId) {
		shortfall := requiredQuota - promiseWalletReportQuota(tree, wallet)
		if shortfall <= 0 {
			return
		}

		current := tree.AllocationsById[allocationId]
		for current != nil && current.Parent.Present {
			parent := tree.AllocationsById[current.Parent.Value]

			if parent.QuotaChildren-parent.ReservedChildren < shortfall {
				problemRoots[parent.Id] = util.Empty{}
			}
			current = parent
		}
	}

	for _, allocationId := range wallet.Allocations {
		allocation := tree.AllocationsById[allocationId]
		if allocation != nil && ((!now.Before(allocation.Start) && now.Before(allocation.End)) || allocation.Start.After(now)) {
			addProblemRootFromAllocation(allocation.Id)
		}
	}

	for _, promiseId := range promiseTree.PromisesByChild[wallet.Id] {
		promise := promiseTree.PromisesById[promiseId]
		if promise == nil || promise.Quota <= 0 || now.Before(promise.Start) || now.After(promise.End) {
			continue
		}

		heads := promiseFindMaterializationHeads(now, tree, promise)
		if len(heads.Active) > 0 {
			for _, active := range heads.Active {
				addProblemRootFromAllocation(active.Id)
			}
			continue
		}
		if heads.Next.Present {
			addProblemRootFromAllocation(heads.Next.Value.Id)
			continue
		}
		if heads.Retired.Present {
			addProblemRootFromAllocation(heads.Retired.Value.Id)
			continue
		}

		parentWallet := tree.WalletsById[promise.Parent]
		for _, parentAllocationId := range parentWallet.Allocations {
			parentAllocation := tree.AllocationsById[parentAllocationId]
			if parentAllocation == nil || parentAllocation.End.Before(promise.Start) || parentAllocation.Start.After(promise.End) {
				continue
			}
			if (!now.Before(parentAllocation.Start) && now.Before(parentAllocation.End)) || parentAllocation.Start.After(now) {
				problemRoots[parentAllocation.Id] = util.Empty{}
				addProblemRootFromAllocation(parentAllocation.Id)
			}
		}
	}

	if len(problemRoots) == 0 {
		return
	}

	for rootId := range problemRoots {
		postorder := []AllocationId{}
		type stackEntry struct {
			id      AllocationId
			visited bool
		}
		stack := []stackEntry{{id: rootId}}
		seen := map[AllocationId]bool{}
		for len(stack) > 0 {
			entry := stack[len(stack)-1]
			stack = stack[:len(stack)-1]
			if entry.visited {
				postorder = append(postorder, entry.id)
				continue
			}
			if seen[entry.id] {
				continue
			}
			seen[entry.id] = true
			allocation := tree.AllocationsById[entry.id]
			if allocation == nil {
				continue
			}
			stack = append(stack, stackEntry{id: entry.id, visited: true})
			for _, childId := range allocation.Children {
				stack = append(stack, stackEntry{id: childId})
			}
		}

		for _, allocationId := range postorder {
			allocation := tree.AllocationsById[allocationId]
			if allocation == nil || !allocation.Parent.Present {
				continue
			}

			quotaSelf := allocation.QuotaSelf
			if quotaSelf > allocation.ConsumedSelf {
				quotaSelf = allocation.ConsumedSelf
			}
			promiseSetSplit(now, tree, allocation.Id, quotaSelf, allocation.ReservedChildren)
		}
	}

	for i := len(promises) - 1; i >= 0; i-- {
		promiseReconcileOne(now, tree, promises[i], true)
		if promiseWalletReportQuota(tree, wallet) >= requiredQuota {
			break
		}
	}
	return
}

func promiseWalletReportQuota(tree *AccountingTree, wallet *Wallet) int64 {
	includeRetired := !tree.IsCapacityBased()
	quota := int64(0)
	for _, allocationId := range wallet.Allocations {
		allocation := tree.AllocationsById[allocationId]
		if allocation != nil && allocation.Activated {
			if !allocation.Retired {
				quota += allocation.QuotaSelf
			} else if includeRetired {
				quota += allocation.ConsumedSelf
			}
		}
	}

	return quota
}

func promiseWalletEffectiveReportQuota(tree *AccountingTree, wallet *Wallet) (int64, bool) {
	includeRetired := !tree.IsCapacityBased()
	quota := int64(0)
	hasConcreteQuota := false
	seenParents := map[AllocationId]bool{}

	for _, allocationId := range wallet.Allocations {
		allocation := tree.AllocationsById[allocationId]
		if allocation == nil || !allocation.Activated {
			continue
		}

		if !allocation.Retired {
			hasConcreteQuota = true
			quota += allocation.QuotaSelf
			if allocation.Parent.Present && !seenParents[allocation.Parent.Value] {
				seenParents[allocation.Parent.Value] = true
				parent := tree.AllocationsById[allocation.Parent.Value]
				if parent != nil {
					quota += max(parent.QuotaChildren-parent.ReservedChildren, 0)
				}
			}
		} else if includeRetired {
			hasConcreteQuota = true
			quota += allocation.ConsumedSelf
		}
	}

	return quota, hasConcreteQuota
}

func promiseRelevantPromisesForWallet(tree *PromiseTree, walletId WalletId) []*Promise {
	result := []*Promise{}
	seenPromises := map[PromiseId]bool{}
	seenWallets := map[WalletId]bool{}
	queue := []WalletId{walletId}

	for len(queue) > 0 {
		current := queue[0]
		queue = queue[1:]
		if seenWallets[current] {
			continue
		}
		seenWallets[current] = true

		for _, promiseId := range tree.PromisesByChild[current] {
			if seenPromises[promiseId] {
				continue
			}
			promise := tree.PromisesById[promiseId]
			if promise == nil {
				continue
			}
			seenPromises[promiseId] = true
			result = append(result, promise)
			queue = append(queue, promise.Parent)
		}
	}

	return result
}

// Target split calculation
// ---------------------------------------------------------------------------------------------------------------------
// A promise target is a policy decision, not a guarantee. It describes the desired split for a wallet if enough promise
// quota and parent-backed capacity exist. The reconciliation code later clamps this target to what can actually be
// materialized without violating low-level invariants.
//
// The target has two parts:
//
// - QuotaSelf: current self consumption, EWMA demand and optional slack.
// - QuotaChildren: the larger of current child reservations and target demand derived from child promises.
//
// This is intentionally not a global flow solver. Child targets are recursively summarized so the planner can propagate
// demand through one allocation family while still staying local to the existing tree.
type promiseTargetSplit struct {
	QuotaSelf     int64
	QuotaChildren int64
}

// Trend and demand helpers
// ---------------------------------------------------------------------------------------------------------------------
// Promise policy uses current consumption plus a small amount of trend information. The trend is deliberately simple:
// each reconciliation pass updates an EWMA and uses it as the current demand estimate.
//
// Child demand is derived from children's targets rather than historical child reservations alone. This lets a child
// ask its parent for headroom before the child allocation actually exists, which is the mechanism that lets periodic
// reconciliation propagate capacity down through multiple layers.
//
// promiseUpdateWalletTrend updates EWMA demand and per-hour trend for a wallet.

func promiseUpdateWalletTrend(now time.Time, tree *AccountingTree, wallet *Wallet, measured int64) {
	if measured < 0 {
		measured = 0
	}

	if wallet.PromiseTrendUpdatedAt.IsZero() {
		wallet.PromiseDemandEwma = measured
		wallet.PromiseDemandObserved = measured
		wallet.PromiseDemandTrend = 0
		wallet.PromiseTrendUpdatedAt = now
		wallet.Dirty = true
		return
	}

	previous := wallet.PromiseDemandEwma
	wallet.PromiseDemandEwma = measured

	elapsed := now.Sub(wallet.PromiseTrendUpdatedAt)
	if elapsed > 0 {
		wallet.PromiseDemandTrend = ((wallet.PromiseDemandEwma - previous) * int64(time.Hour)) / int64(elapsed)
	}

	wallet.PromiseDemandObserved = measured
	wallet.PromiseTrendUpdatedAt = now
	wallet.Dirty = true
}

// Promise-level reconciliation
// ---------------------------------------------------------------------------------------------------------------------
// promiseReconcileOne applies the target split to one promise. It first computes policy demand, then clamps demand to
// the remaining promise quota that is not already materialized. From there, the planner either creates missing
// materializations, creates successors after retirement, or updates an existing materialization in place.

func promiseReconcileOne(now time.Time, tree *AccountingTree, promise *Promise, preserveExisting bool) {
	if promise == nil || promise.Quota <= 0 || now.Before(promise.Start) || now.After(promise.End) {
		return
	}

	var targetForWallet func(walletId WalletId, seen map[WalletId]bool) promiseTargetSplit
	targetForWallet = func(walletId WalletId, seen map[WalletId]bool) promiseTargetSplit {
		if seen[walletId] {
			return promiseTargetSplit{}
		}
		seen[walletId] = true

		target := promiseTargetSplit{}
		if wallet, ok := tree.WalletsById[walletId]; ok {
			currentSelf := int64(0)
			currentChildren := int64(0)
			for _, allocationId := range wallet.Allocations {
				allocation := tree.AllocationsById[allocationId]
				if allocation == nil {
					continue
				}
				currentSelf += allocation.ConsumedSelf
				currentChildren += allocation.ReservedChildren
			}
			currentSelf = max(currentSelf, wallet.Consumed)
			currentSelf = max(currentSelf, wallet.PromiseDemandEwma)

			target = promiseTargetSplit{
				QuotaSelf:     currentSelf,
				QuotaChildren: currentChildren,
			}
		}

		childDemand := int64(0)
		promiseTree := &tree.PromiseTree
		for _, promiseId := range promiseTree.PromisesByParent[walletId] {
			childPromise := promiseTree.PromisesById[promiseId]
			if childPromise == nil {
				continue
			}
			childTarget := targetForWallet(childPromise.Child, seen)
			childDemand += childTarget.QuotaSelf + childTarget.QuotaChildren
		}
		target.QuotaChildren = max(target.QuotaChildren, childDemand)
		return target
	}

	target := targetForWallet(promise.Child, map[WalletId]bool{})
	wallet := tree.WalletsById[promise.Child]
	if wallet != nil {
		hasObservedConsumption := wallet.Consumed > 0
		for _, allocationId := range wallet.Allocations {
			allocation := tree.AllocationsById[allocationId]
			if allocation != nil && allocation.ConsumedSelf > 0 {
				hasObservedConsumption = true
				break
			}
		}
		if hasObservedConsumption {
			includeRetired := !tree.IsCapacityBased()
			covered := promiseTargetSplit{}
			for _, allocationId := range wallet.Allocations {
				allocation := tree.AllocationsById[allocationId]
				if allocation == nil || allocation.Promise.Present && allocation.Promise.Value == promise.Id {
					continue
				}

				if allocation.Activated && !allocation.Retired {
					covered.QuotaSelf += allocation.QuotaSelf
					covered.QuotaChildren += allocation.QuotaChildren
				} else if includeRetired && allocation.Activated && allocation.Retired {
					covered.QuotaSelf += allocation.ConsumedSelf
				}
			}
			target.QuotaSelf = max(target.QuotaSelf-covered.QuotaSelf, 0)
			target.QuotaChildren = max(target.QuotaChildren-covered.QuotaChildren, 0)
		}
	}
	materializedQuota := func(exclude util.Option[AllocationId]) int64 {
		if wallet == nil {
			return 0
		}

		total := int64(0)
		for _, allocationId := range wallet.Allocations {
			if exclude.Present && exclude.Value == allocationId {
				continue
			}

			allocation := tree.AllocationsById[allocationId]
			if allocation == nil || !allocation.Promise.Present || allocation.Promise.Value != promise.Id {
				continue
			}
			if tree.IsCapacityBased() && !now.Before(allocation.End) {
				continue
			}

			total += allocation.QuotaSelf + allocation.QuotaChildren
		}
		return total
	}

	heads := promiseFindMaterializationHeads(now, tree, promise)
	wallet = tree.WalletsById[promise.Child]
	allocation := heads.UpdateFirst()
	if !allocation.Present {
		remainingQuota := promise.Quota - materializedQuota(util.OptNone[AllocationId]())
		if target.QuotaSelf+target.QuotaChildren > remainingQuota {
			target = promiseClampTargetToTotal(target, remainingQuota)
		}
		promiseCreateMaterializations(now, tree, promise, maxTime(now, promise.Start), promise.End, target, materializedQuota)
		return
	}
	allocationValue := allocation.Value
	if preserveExisting && now.Before(allocationValue.End) {
		target.QuotaSelf = max(target.QuotaSelf, allocationValue.QuotaSelf)
		target.QuotaChildren = max(target.QuotaChildren, allocationValue.QuotaChildren)
	}

	if !now.Before(allocationValue.End) {
		remainingQuota := promise.Quota - materializedQuota(util.OptNone[AllocationId]())
		if target.QuotaSelf+target.QuotaChildren > remainingQuota {
			target = promiseClampTargetToTotal(target, remainingQuota)
		}
		start := allocationValue.End
		if start.Before(promise.Start) {
			start = promise.Start
		}
		promiseCreateMaterializations(now, tree, promise, start, promise.End, target, materializedQuota)
		return
	}

	remainingQuota := promise.Quota - materializedQuota(util.OptValue(allocationValue.Id))
	if target.QuotaSelf+target.QuotaChildren > remainingQuota {
		target = promiseClampTargetToTotal(target, remainingQuota)
	}

	if allocationValue.Start.After(now) {
		start := promise.Start
		if wallet == nil {
			return
		}
		for _, candidateId := range wallet.Allocations {
			candidate := tree.AllocationsById[candidateId]
			if candidate == nil || candidate.Id == allocationValue.Id || !candidate.Promise.Present || candidate.Promise.Value != promise.Id {
				continue
			}
			if candidate.End.After(start) && !candidate.End.After(allocationValue.Start) {
				start = candidate.End
			}
		}
		if allocationValue.Parent.Present {
			parent := tree.AllocationsById[allocationValue.Parent.Value]
			if parent != nil && start.Before(parent.Start) {
				start = parent.Start
			}
		}
		if start.Before(promise.Start) {
			start = promise.Start
		}
		if start.Before(allocationValue.End) && !start.Equal(allocationValue.Start) {
			if start.After(allocationValue.End) {
				log.Fatal("Promise reconciliation attempted to set invalid period for allocation %v", allocationValue.Id)
			}
			if allocationValue.Parent.Present {
				parent := tree.AllocationsById[allocationValue.Parent.Value]
				if parent == nil || start.Before(parent.Start) || allocationValue.End.After(parent.End) {
					log.Fatal("Promise reconciliation attempted to move allocation %v outside parent period", allocationValue.Id)
				}
			}
			for _, childId := range allocationValue.Children {
				child := tree.AllocationsById[childId]
				if child != nil && (child.Start.Before(start) || child.End.After(allocationValue.End)) {
					log.Fatal("Promise reconciliation attempted to move allocation %v outside child period", allocationValue.Id)
				}
			}

			allocationMutate(now, tree, allocationValue.Id, func(alloc *Allocation, parent util.Option[*Allocation]) {
				alloc.Start = start
				alloc.End = allocationValue.End
			})
		}
	}

	if allocationValue.ConsumedSelf <= allocationValue.QuotaSelf {
		target.QuotaSelf = max(target.QuotaSelf, allocationValue.ConsumedSelf)
	} else {
		target.QuotaSelf = max(target.QuotaSelf, allocationValue.QuotaSelf)
	}
	target.QuotaChildren = max(target.QuotaChildren, allocationValue.ReservedChildren)

	currentTotal := allocationValue.QuotaSelf + allocationValue.QuotaChildren
	targetTotal := target.QuotaSelf + target.QuotaChildren
	if targetTotal > currentTotal && allocationValue.Parent.Present {
		provided := promiseExposeChildCapacity(now, tree, allocationValue.Parent.Value, targetTotal-currentTotal)
		if provided < targetTotal-currentTotal {
			target = promiseClampTargetToTotal(target, currentTotal+provided)
		}
	}

	if allocationValue.ConsumedSelf <= allocationValue.QuotaSelf {
		target.QuotaSelf = max(target.QuotaSelf, allocationValue.ConsumedSelf)
	} else {
		target.QuotaSelf = max(target.QuotaSelf, allocationValue.QuotaSelf)
	}
	target.QuotaChildren = max(target.QuotaChildren, allocationValue.ReservedChildren)

	targetTotal = target.QuotaSelf + target.QuotaChildren
	if targetTotal > currentTotal && allocationValue.Parent.Present {
		parent := tree.AllocationsById[allocationValue.Parent.Value]
		if parent == nil {
			return
		}
		available := max(parent.QuotaChildren-parent.ReservedChildren, 0)
		if targetTotal-currentTotal > available {
			target = promiseClampTargetToTotal(target, currentTotal+available)
			target.QuotaSelf = max(target.QuotaSelf, allocationValue.QuotaSelf)
			target.QuotaChildren = max(target.QuotaChildren, allocationValue.ReservedChildren)
		}
	}
	promiseSetSplit(now, tree, allocationValue.Id, target.QuotaSelf, target.QuotaChildren)
	promiseCreateMaterializations(now, tree, promise, maxTime(now, promise.Start), promise.End, target, materializedQuota)
}

// Target clamping and materialized quota accounting
// ---------------------------------------------------------------------------------------------------------------------
// These helpers keep the planner honest about the two independent caps it must respect:
//
// - Promise quota: a promise cannot materialize more total quota than it owns.
// - Parent-backed capacity: an allocation cannot grow unless its parent can expose the requested child capacity.
//
// Promise quota has different meaning depending on accounting frequency. Capacity-based products use promise quota as
// a maximum concurrent exposure, so retired materializations no longer count against the promise. Non-capacity products
// use promise quota as a maximum total consumption budget for the promise period. Retired materializations still count,
// but low-level retirement cleanup lowers unused retired quota to retained consumption, so unused non-capacity quota can
// be materialized later in the promise period.
//
// The functions in this section only calculate or clamp. They do not mutate the low-level tree.
//
// The core helpers are:
//
// - promiseClampTargetToTotal: Reduces a target split so its total does not exceed a cap.
func promiseClampTargetToTotal(target promiseTargetSplit, total int64) promiseTargetSplit {
	if total < 0 {
		total = 0
	}
	currentTotal := target.QuotaSelf + target.QuotaChildren
	if currentTotal <= total {
		return target
	}

	reduction := currentTotal - total
	childrenReduction := min(reduction, target.QuotaChildren)
	target.QuotaChildren -= childrenReduction
	reduction -= childrenReduction
	target.QuotaSelf -= min(reduction, target.QuotaSelf)
	return target
}

type promiseMaterializationHeads struct {
	Active  []*Allocation
	Next    util.Option[*Allocation]
	Retired util.Option[*Allocation]
}

func (h promiseMaterializationHeads) UpdateFirst() util.Option[*Allocation] {
	if len(h.Active) > 0 {
		return util.OptValue(h.Active[0])
	}
	if h.Next.Present {
		return h.Next
	}
	return h.Retired
}

// promiseFindMaterializationHeads classifies a promise's materializations around now. A promise can have multiple
// active heads because it may be backed by several parent allocations at the same time; callers that update only one
// allocation should use Active[0], while callers that reason about capacity roots should consider every active head.
func promiseFindMaterializationHeads(now time.Time, tree *AccountingTree, promise *Promise) promiseMaterializationHeads {
	heads := promiseMaterializationHeads{}
	wallet := tree.WalletsById[promise.Child]
	if wallet == nil {
		return heads
	}

	for _, allocationId := range wallet.Allocations {
		allocation := tree.AllocationsById[allocationId]
		if allocation == nil || !allocation.Promise.Present || allocation.Promise.Value != promise.Id {
			continue
		}

		if !now.Before(allocation.Start) && now.Before(allocation.End) {
			heads.Active = append(heads.Active, allocation)
		} else if allocation.Start.After(now) {
			if !heads.Next.Present || allocation.Start.Before(heads.Next.Value.Start) || allocation.Start.Equal(heads.Next.Value.Start) && allocation.Id < heads.Next.Value.Id {
				heads.Next = util.OptValue(allocation)
			}
		} else if !now.Before(allocation.End) {
			if !heads.Retired.Present || allocation.End.After(heads.Retired.Value.End) || allocation.End.Equal(heads.Retired.Value.End) && allocation.Id < heads.Retired.Value.Id {
				heads.Retired = util.OptValue(allocation)
			}
		}
	}

	slices.SortFunc(heads.Active, func(a, b *Allocation) int {
		if a.End.Before(b.End) {
			return -1
		}
		if a.End.After(b.End) {
			return 1
		}
		return cmp.Compare(int(a.Id), int(b.Id))
	})
	return heads
}

// Split application and capacity propagation
// ---------------------------------------------------------------------------------------------------------------------
// This section is where target policy becomes concrete allocation state. The planner always uses low-level mutation
// helpers and never writes through the tree without invariant checks.
//
// Growth starts at the child and asks ancestors for child capacity. The parent first uses existing QuotaChildren slack.
// If that is not enough, the request propagates upward. At the root, available self quota may be converted into child
// quota, but no new root quota is ever created. If the chain cannot expose enough capacity, the target is clamped and
// the promise remains under-covered.
func promiseExposeChildCapacity(now time.Time, tree *AccountingTree, allocationId AllocationId, need int64) int64 {
	if need <= 0 {
		return 0
	}

	allocation := tree.AllocationsById[allocationId]
	if allocation == nil {
		return 0
	}

	available := allocation.QuotaChildren - allocation.ReservedChildren
	if available >= need {
		return need
	}

	shortage := need - max(available, 0)
	provided := int64(0)
	if allocation.Parent.Present {
		provided = promiseExposeChildCapacity(now, tree, allocation.Parent.Value, shortage)
	} else {
		selfSlack := allocation.QuotaSelf - allocation.ConsumedSelf
		provided = min(shortage, max(selfSlack, 0))
	}

	if provided > 0 {
		if allocation.Parent.Present {
			parent := tree.AllocationsById[allocation.Parent.Value]
			if parent == nil {
				return max(available, 0)
			}
			provided = min(provided, max(parent.QuotaChildren-parent.ReservedChildren, 0))
			if provided <= 0 {
				return max(available, 0)
			}
		}

		quotaSelf := allocation.QuotaSelf
		if !allocation.Parent.Present {
			quotaSelf -= provided
		}
		promiseSetSplit(now, tree, allocationId, quotaSelf, allocation.QuotaChildren+provided)
	}

	return max(available, 0) + provided
}

func promiseSetSplit(now time.Time, tree *AccountingTree, allocationId AllocationId, quotaSelf int64, quotaChildren int64) {
	allocation := tree.AllocationsById[allocationId]
	if allocation == nil {
		log.Fatal("Promise reconciliation attempted to update unknown allocation %v", allocationId)
		return
	}

	if quotaSelf != allocation.QuotaSelf && quotaSelf < allocation.ConsumedSelf {
		return
	}
	if quotaChildren < allocation.ReservedChildren {
		log.Fatal("Promise reconciliation attempted to lower QuotaChildren below child reservations for allocation %v", allocationId)
	}

	currentTotal := allocation.QuotaSelf + allocation.QuotaChildren
	proposedTotal := quotaSelf + quotaChildren
	delta := proposedTotal - currentTotal
	changed := allocation.QuotaSelf != quotaSelf || allocation.QuotaChildren != quotaChildren
	if delta > 0 {
		if !allocation.Parent.Present {
			log.Fatal("Promise reconciliation attempted to grow root allocation %v", allocationId)
		}
		parent := tree.AllocationsById[allocation.Parent.Value]
		if parent == nil {
			log.Fatal("Promise reconciliation attempted to grow allocation %v without parent", allocationId)
			return
		}

		available := max(parent.QuotaChildren-parent.ReservedChildren, 0)
		if delta > available {
			clamped := promiseClampTargetToTotal(promiseTargetSplit{QuotaSelf: quotaSelf, QuotaChildren: quotaChildren}, currentTotal+available)
			if clamped.QuotaSelf < allocation.ConsumedSelf || clamped.QuotaChildren < allocation.ReservedChildren {
				return
			}
			quotaSelf = clamped.QuotaSelf
			quotaChildren = clamped.QuotaChildren
			proposedTotal = quotaSelf + quotaChildren
			delta = proposedTotal - currentTotal
		}
	}

	allocationMutate(now, tree, allocationId, func(alloc *Allocation, parent util.Option[*Allocation]) {
		alloc.QuotaSelf = quotaSelf
		alloc.QuotaChildren = quotaChildren
		if parent.Present {
			parent.Value.ReservedChildren += delta
		}
	})
	if changed {
		wallet := tree.WalletsById[allocation.Wallet]
		walletMarkSignificantUpdate(now, tree, wallet)
		walletReevaluateLock(now, tree, wallet)
		if allocation.Parent.Present {
			parent := tree.AllocationsById[allocation.Parent.Value]
			if parent != nil {
				parentWallet := tree.WalletsById[parent.Wallet]
				walletMarkSignificantUpdate(now, tree, parentWallet)
				walletReevaluateLock(now, tree, parentWallet)
			}
		}
	}
}

// Materialization creation
// ---------------------------------------------------------------------------------------------------------------------
// The planner creates promise-owned allocations under existing backing parent allocations. The backing allocation may be
// active or the next future allocation that covers the requested promise interval. Creation is partial if the backing
// allocation can only expose part of the target. A promise may create one materialization per backing allocation over an
// overlapping interval.
//
// The promise layer never creates root allocations. If no parent-backed capacity exists, creation simply does nothing
// and the promise remains under-covered until a later reconciliation pass.

func promiseCreateMaterializations(
	now time.Time,
	tree *AccountingTree,
	promise *Promise,
	start time.Time,
	end time.Time,
	target promiseTargetSplit,
	materializedQuota func(util.Option[AllocationId]) int64,
) {
	parentWallet := tree.WalletsById[promise.Parent]
	childWallet := tree.WalletsById[promise.Child]
	if parentWallet == nil || childWallet == nil {
		return
	}

	type candidate struct {
		allocation *Allocation
		active     bool
	}

	for range parentWallet.Allocations {
		remaining := target
		usedBackingAllocations := map[AllocationId]bool{}
		for _, allocationId := range childWallet.Allocations {
			allocation := tree.AllocationsById[allocationId]
			if allocation == nil || !allocation.Promise.Present || allocation.Promise.Value != promise.Id {
				continue
			}
			if !allocation.Parent.Present || !allocation.End.After(start) || !allocation.Start.Before(end) {
				continue
			}
			usedBackingAllocations[allocation.Parent.Value] = true
			if now.Before(allocation.Start) || !now.Before(allocation.End) {
				continue
			}

			self := min(remaining.QuotaSelf, allocation.QuotaSelf)
			remaining.QuotaSelf -= self
			children := min(remaining.QuotaChildren, allocation.QuotaChildren)
			remaining.QuotaChildren -= children
		}

		remainingQuota := promise.Quota - materializedQuota(util.OptNone[AllocationId]())
		if remaining.QuotaSelf+remaining.QuotaChildren > remainingQuota {
			remaining = promiseClampTargetToTotal(remaining, remainingQuota)
		}
		if remaining.QuotaSelf+remaining.QuotaChildren <= 0 {
			return
		}

		candidates := []candidate{}
		for _, allocationId := range parentWallet.Allocations {
			allocation := tree.AllocationsById[allocationId]
			if allocation == nil || usedBackingAllocations[allocationId] || allocation.End.Before(start) || allocation.Start.After(end) {
				continue
			}
			active := !now.Before(allocation.Start) && now.Before(allocation.End)
			if active || allocation.Start.After(now) {
				candidates = append(candidates, candidate{allocation: allocation, active: active})
			}
		}

		slices.SortFunc(candidates, func(a, b candidate) int {
			if a.active != b.active {
				if a.active {
					return -1
				}
				return 1
			}
			if a.allocation.End.Before(b.allocation.End) {
				return -1
			}
			if a.allocation.End.After(b.allocation.End) {
				return 1
			}
			return cmp.Compare(int(a.allocation.Id), int(b.allocation.Id))
		})

		var parent *Allocation
		for _, candidate := range candidates {
			allocation := candidate.allocation
			childStart := maxTime(start, allocation.Start)
			childEnd := minTime(end, allocation.End)
			if !childStart.Before(childEnd) {
				continue
			}
			provided := promiseExposeChildCapacity(now, tree, allocation.Id, remaining.QuotaSelf+remaining.QuotaChildren)
			if provided > 0 {
				parent = allocation
				break
			}
		}
		if parent == nil {
			return
		}

		before := materializedQuota(util.OptNone[AllocationId]())
		allocationStart := maxTime(start, parent.Start)
		allocationEnd := minTime(end, parent.End)
		if !allocationStart.Before(allocationEnd) {
			return
		}

		total := remaining.QuotaSelf + remaining.QuotaChildren
		if total <= 0 {
			return
		}
		if parent.QuotaChildren-parent.ReservedChildren < total {
			provided := promiseExposeChildCapacity(now, tree, parent.Id, total)
			if provided < total {
				remaining = promiseClampTargetToTotal(remaining, provided)
				total = remaining.QuotaSelf + remaining.QuotaChildren
				if total <= 0 {
					return
				}
			}
		}

		allocationId := AllocationId(accGlobals.AllocIdAcc.Add(1))
		allocation := &Allocation{
			Id:               allocationId,
			Wallet:           promise.Child,
			Parent:           util.OptValue(parent.Id),
			Start:            allocationStart,
			End:              allocationEnd,
			QuotaSelf:        remaining.QuotaSelf,
			QuotaChildren:    remaining.QuotaChildren,
			ConsumedSelf:     0,
			ReservedChildren: 0,
			Grant:            promise.Grant,
			Promise:          util.OptValue(promise.Id),
			Dirty:            true,
		}

		tree.AllocationsById[allocationId] = allocation
		childWallet.Allocations = append(childWallet.Allocations, allocationId)
		childWallet.Dirty = true
		allocationMutate(now, tree, parent.Id, func(parentAlloc *Allocation, parentParent util.Option[*Allocation]) {
			parentAlloc.ReservedChildren += total
			parentAlloc.Children = append(parentAlloc.Children, allocationId)
		})
		allocationMutate(now, tree, allocationId, func(alloc *Allocation, parent util.Option[*Allocation]) {})
		lifecycleScanEx(now, tree, []AllocationId{allocation.Id})
		walletMarkSignificantUpdate(now, tree, childWallet)
		walletReevaluateLock(now, tree, childWallet)
		parentWallet := tree.WalletsById[parent.Wallet]
		walletMarkSignificantUpdate(now, tree, parentWallet)
		walletReevaluateLock(now, tree, parentWallet)

		after := materializedQuota(util.OptNone[AllocationId]())
		if after <= before {
			return
		}
	}
}

func promiseSort(promiseTree *PromiseTree, promises []*Promise) {
	depth := func(walletId WalletId) int {
		depth := 0
		seen := map[WalletId]bool{}
		for {
			if seen[walletId] {
				return depth
			}
			seen[walletId] = true

			parents := promiseTree.PromisesByChild[walletId]
			if len(parents) == 0 {
				return depth
			}

			promise := promiseTree.PromisesById[parents[0]]
			if promise == nil {
				return depth
			}
			walletId = promise.Parent
			depth++
		}
	}
	slices.SortFunc(promises, func(a, b *Promise) int {
		depthA := depth(a.Child)
		depthB := depth(b.Child)
		if depthA != depthB {
			return cmp.Compare(depthA, depthB)
		}
		return cmp.Compare(int(a.Id), int(b.Id))
	})
}

// Small time helpers
// ---------------------------------------------------------------------------------------------------------------------
// - minTime: Returns the earlier of two times.
// - maxTime: Returns the later of two times.

func minTime(a, b time.Time) time.Time {
	if a.Before(b) {
		return a
	}
	return b
}

func maxTime(a, b time.Time) time.Time {
	if a.After(b) {
		return a
	}
	return b
}
