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
// The planner is chain-local and update-first. It tries to keep the current head allocation for a promise stable by
// changing its split in place. If the current materialization cannot be edited because of time boundaries, the planner
// creates a successor allocation under the same promise and under an existing parent allocation. Successors are ordered
// by timestamps. There is no separate linked-list field.

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

// promiseBasisPoints is the denominator for integer trend fractions. A value of 10,000 means 100%, 5,000 means 50%,
// and 1 means 0.01%. Promise policies use this representation instead of floats so accounting decisions remain stable
// and deterministic.
const promiseBasisPoints = int64(10000)

// Public reconciliation entry point
// ---------------------------------------------------------------------------------------------------------------------
// PromiseReconcile is invoked either periodically for a category or synchronously from UsageReport. Periodic
// reconciliation walks the whole promise tree. Report-triggered reconciliation only works on the reporting wallet's
// ancestor promise chain, and returns immediately if the wallet already has the rounded target plus slack available.
//
// Reconciliation proceeds bottom-up by sorting promises by child depth. This gives child demand a chance to influence
// parent targets on later passes and matches the operational model where periodic reconciliation can gradually expose
// capacity through a chain.

// PromiseReconcile is invoked to ensure that the low-level accounting tree is kept up-to-date with the
// promises given to projects.
//
// This function will be invoked in one of the following two cases:
//
// 1. Periodically by the promise system itself for all wallets
// 2. On-demand by the low-level accounting system in response to a charge which is unable to succeed
//
// The minimumRequest property is set only when invoked on-demand to ensure that the system can carry a charge. It is
// not guaranteed, that such a value is within what the promise would allow.
func PromiseReconcile(
	now time.Time,
	category accapi.ProductCategoryIdV2,
	owner accapi.WalletOwner,
	minimumRequest util.Option[int64],
) {
	_ = treeMutate(category, func(tree *AccountingTree) *util.HttpError {
		lifecycleScan(now, tree)
		promiseTree := &tree.PromiseTree

		if minimumRequest.Present {
			promiseReconcileOwner(now, tree, promiseTree, owner, minimumRequest.Value)
			return nil
		}

		promises := promiseTreePromises(promiseTree)
		for _, wallet := range tree.WalletsById {
			promiseUpdateWalletTrend(now, tree, wallet, promiseMeasuredWalletDemand(now, tree, wallet))
		}

		slices.SortFunc(promises, func(a, b *Promise) int {
			depthA := promiseWalletDepth(promiseTree, a.Child)
			depthB := promiseWalletDepth(promiseTree, b.Child)
			if depthA != depthB {
				return cmp.Compare(depthB, depthA)
			}
			return cmp.Compare(int(a.Id), int(b.Id))
		})

		for _, promise := range promises {
			promiseReconcileOne(now, tree, promise)
		}

		return nil
	})
}

func promiseReconcileOwner(now time.Time, tree *AccountingTree, promiseTree *PromiseTree, owner accapi.WalletOwner, minimumRequest int64) {
	wallet := tree.WalletsByOwner[owner.Reference()]
	if wallet == nil {
		return
	}

	// Skip reconciliation if we already have space in the wallet for the request
	requiredQuota := promiseRoundUp(minimumRequest+tree.Policy.MinSlack, tree.Policy.GrowthStep)
	if promiseWalletReportQuota(tree, wallet) >= requiredQuota {
		return
	}

	promiseUpdateWalletTrend(now, tree, wallet, minimumRequest)
	promises := promiseRelevantPromisesForWallet(promiseTree, wallet.Id)
	slices.SortFunc(promises, func(a, b *Promise) int {
		depthA := promiseWalletDepth(promiseTree, a.Child)
		depthB := promiseWalletDepth(promiseTree, b.Child)
		if depthA != depthB {
			return cmp.Compare(depthA, depthB)
		}
		return cmp.Compare(int(a.Id), int(b.Id))
	})

	for _, promise := range promises {
		promiseReconcileOne(now, tree, promise)
	}

	if promiseWalletReportQuota(tree, wallet) >= requiredQuota {
		return
	}

	problemRoots := map[AllocationId]util.Empty{}
	addProblemRootFromAllocation := func(allocationId AllocationId) {
		shortfall := requiredQuota - promiseWalletReportQuota(tree, wallet)
		if shortfall <= 0 {
			return
		}

		current := tree.AllocationsById[allocationId]
		for current != nil && current.Parent.Present {
			parent := tree.AllocationsById[current.Parent.Value]
			if parent == nil {
				return
			}

			if parent.QuotaChildren-parent.ReservedChildren < shortfall {
				problemRoots[parent.Id] = util.Empty{}
			}
			current = parent
		}
	}

	for _, allocationId := range wallet.Allocations {
		allocation := tree.AllocationsById[allocationId]
		if allocation == nil {
			continue
		}
		if promiseAllocationActive(now, allocation) || allocation.Start.After(now) {
			addProblemRootFromAllocation(allocation.Id)
		}
	}

	for _, promiseId := range promiseTree.PromisesByChild[wallet.Id] {
		promise := promiseTree.PromisesById[promiseId]
		if promise == nil || promise.Quota <= 0 || now.Before(promise.Start) || now.After(promise.End) {
			continue
		}

		head := promiseFindHead(now, tree, promise)
		if head.Present {
			addProblemRootFromAllocation(head.Value)
			continue
		}

		parentWallet := tree.WalletsById[promise.Parent]
		if parentWallet == nil {
			continue
		}
		for _, parentAllocationId := range parentWallet.Allocations {
			parentAllocation := tree.AllocationsById[parentAllocationId]
			if parentAllocation == nil || parentAllocation.End.Before(promise.Start) || parentAllocation.Start.After(promise.End) {
				continue
			}
			if promiseAllocationActive(now, parentAllocation) || parentAllocation.Start.After(now) {
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
		stack := []struct {
			id      AllocationId
			visited bool
		}{{id: rootId}}
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
			stack = append(stack, struct {
				id      AllocationId
				visited bool
			}{id: entry.id, visited: true})
			for _, childId := range allocation.Children {
				stack = append(stack, struct {
					id      AllocationId
					visited bool
				}{id: childId})
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
		promiseReconcileOne(now, tree, promises[i])
		if promiseWalletReportQuota(tree, wallet) >= requiredQuota {
			break
		}
	}
}

func promiseWalletReportQuota(tree *AccountingTree, wallet *Wallet) int64 {
	includeRetired := !tree.IsCapacityBased()
	quota := int64(0)
	for _, allocationId := range wallet.Allocations {
		allocation := tree.AllocationsById[allocationId]
		if allocation.Activated {
			if !allocation.Retired {
				quota += allocation.QuotaSelf
			} else if includeRetired {
				quota += allocation.ConsumedSelf
			}
		}
	}

	return quota
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
//
// The core helpers are:
//
// - promiseCalculateTargetSplit: Computes the full desired split for one wallet, including child promise demand.
// - promiseCalculateLocalTargetSplit: Computes direct self and already-reserved child demand for one wallet.

type promiseTargetSplit struct {
	QuotaSelf     int64
	QuotaChildren int64
}

// promiseCalculateTargetSplit will take the current state, policy and trend from the tree and determine what the
// split should be for a specific wallet. The returned split is not guaranteed to follow all invariants by the
// underlying tree, and it must be handled by the reconciliation function to attempt to reach the target. The target
// values can go both up and down or even remain unchanged.
func promiseCalculateTargetSplit(
	tree *AccountingTree,
	walletId WalletId,
) promiseTargetSplit {
	return promiseCalculateTargetSplitEx(tree, walletId, map[WalletId]bool{})
}

func promiseCalculateTargetSplitEx(tree *AccountingTree, walletId WalletId, seen map[WalletId]bool) promiseTargetSplit {
	if seen[walletId] {
		return promiseTargetSplit{}
	}
	seen[walletId] = true

	target := promiseTargetSplit{}
	if wallet, ok := tree.WalletsById[walletId]; ok {
		policy := tree.Policy
		currentSelf := promiseWalletConsumedSelf(tree, wallet)
		currentChildren := promiseWalletReservedChildren(tree, wallet)
		if wallet.PromiseDemandEwma > currentSelf {
			currentSelf = wallet.PromiseDemandEwma
		}

		targetSelf := currentSelf + policy.MinSlack
		targetChildren := currentChildren

		targetSelf = promiseRoundUp(targetSelf, policy.GrowthStep)
		targetChildren = promiseRoundUp(targetChildren, policy.GrowthStep)

		target = promiseTargetSplit{QuotaSelf: targetSelf, QuotaChildren: targetChildren}
	}

	target.QuotaChildren = max(target.QuotaChildren, promiseChildTargetDemand(tree, walletId, seen))
	target.QuotaChildren = promiseRoundUp(target.QuotaChildren, tree.Policy.GrowthStep)
	return target
}

// Promise tree indexes
// ---------------------------------------------------------------------------------------------------------------------
// AccountingTree construction initializes PromiseTree maps, so the planner can cheaply browse promises by parent, by
// child or by ID without additional checks.
//
// The core helpers are:
//
// - promiseTreePromises: Returns all promises in a tree as a slice for sorting/traversal.
// - promiseWalletDepth: Computes approximate wallet depth from promise parent links for bottom-up ordering.

func promiseTreePromises(tree *PromiseTree) []*Promise {
	result := make([]*Promise, 0, len(tree.PromisesById))
	for _, promise := range tree.PromisesById {
		result = append(result, promise)
	}
	return result
}

func promiseWalletDepth(tree *PromiseTree, walletId WalletId) int {
	depth := 0
	seen := map[WalletId]bool{}
	for {
		if seen[walletId] {
			return depth
		}
		seen[walletId] = true

		parents := tree.PromisesByChild[walletId]
		if len(parents) == 0 {
			return depth
		}

		promise := tree.PromisesById[parents[0]]
		if promise == nil {
			return depth
		}
		walletId = promise.Parent
		depth++
	}
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
// The policy fields ending in BasisPoints use promiseBasisPoints as their denominator. For example, an EWMA alpha of
// 2,500 applies 25% of the new sample and keeps 75% of the previous value.
//
// The core helpers are:
//
// - promiseUpdateWalletTrend: Updates EWMA demand and per-hour trend for a wallet.
// - promiseMeasuredWalletDemand: Reads current observed demand from wallet usage and active allocations.
// - promiseWalletConsumedSelf: Sums direct consumption for a wallet.
// - promiseWalletReservedChildren: Sums child reservations under a wallet's allocations.
// - promiseChildTargetDemand: Computes child promise demand for one wallet.
// - promiseChildTargetDemandEx: Recursive implementation over PromiseTree indexes.
// - promiseRoundUp: Rounds a target to policy-sized accounting steps.

func promiseUpdateWalletTrend(now time.Time, tree *AccountingTree, wallet *Wallet, measured int64) {
	if measured < 0 {
		measured = 0
	}

	alpha := tree.Policy.TrendAlphaBasisPoints
	if alpha <= 0 {
		alpha = 5000
	}
	if alpha > promiseBasisPoints {
		alpha = promiseBasisPoints
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
	wallet.PromiseDemandEwma = ((measured * alpha) + (wallet.PromiseDemandEwma * (promiseBasisPoints - alpha))) / promiseBasisPoints

	elapsed := now.Sub(wallet.PromiseTrendUpdatedAt)
	if elapsed > 0 {
		wallet.PromiseDemandTrend = ((wallet.PromiseDemandEwma - previous) * int64(time.Hour)) / int64(elapsed)
	}

	wallet.PromiseDemandObserved = measured
	wallet.PromiseTrendUpdatedAt = now
	wallet.Dirty = true
}

func promiseMeasuredWalletDemand(now time.Time, tree *AccountingTree, wallet *Wallet) int64 {
	demand := wallet.Consumed
	for _, allocationId := range wallet.Allocations {
		allocation := tree.AllocationsById[allocationId]
		if allocation != nil && !allocation.IsRetired(now) && allocation.ConsumedSelf > demand {
			demand = allocation.ConsumedSelf
		}
	}
	return demand
}

func promiseWalletConsumedSelf(tree *AccountingTree, wallet *Wallet) int64 {
	consumed := int64(0)
	for _, allocationId := range wallet.Allocations {
		allocation := tree.AllocationsById[allocationId]
		if allocation != nil {
			consumed += allocation.ConsumedSelf
		}
	}
	if wallet.Consumed > consumed {
		return wallet.Consumed
	}
	return consumed
}

func promiseWalletReservedChildren(tree *AccountingTree, wallet *Wallet) int64 {
	reserved := int64(0)
	for _, allocationId := range wallet.Allocations {
		allocation := tree.AllocationsById[allocationId]
		if allocation != nil {
			reserved += allocation.ReservedChildren
		}
	}
	return reserved
}

func promiseChildTargetDemand(tree *AccountingTree, walletId WalletId, seen map[WalletId]bool) int64 {
	promiseTree := &tree.PromiseTree
	demand := int64(0)
	for _, promiseId := range promiseTree.PromisesByParent[walletId] {
		promise := promiseTree.PromisesById[promiseId]
		if promise == nil {
			continue
		}

		childTarget := promiseCalculateTargetSplitEx(tree, promise.Child, seen)
		demand += childTarget.QuotaSelf + childTarget.QuotaChildren
	}
	return demand
}

func promiseRoundUp(value int64, step int64) int64 {
	if value <= 0 || step <= 0 {
		return value
	}
	return ((value + step - 1) / step) * step
}

// Promise-level reconciliation
// ---------------------------------------------------------------------------------------------------------------------
// promiseReconcileOne applies the target split to one promise. It first computes policy demand, then clamps demand to
// the remaining promise quota that is not already materialized. From there, the planner either creates the first head,
// creates a successor after retirement, or updates the current head in place.
func promiseReconcileOne(now time.Time, tree *AccountingTree, promise *Promise) {
	if promise == nil || promise.Quota <= 0 || now.Before(promise.Start) || now.After(promise.End) {
		return
	}

	target := promiseCalculateTargetSplit(tree, promise.Child)

	head := promiseFindHead(now, tree, promise)
	if !head.Present {
		remainingQuota := promise.Quota - promiseMaterializedQuota(now, tree, promise, util.OptNone[AllocationId]())
		if target.QuotaSelf+target.QuotaChildren > remainingQuota {
			target = promiseClampTargetToTotal(target, remainingQuota)
		}
		promiseCreateHead(now, tree, promise, target)
		return
	}

	allocation := tree.AllocationsById[head.Value]
	if allocation == nil {
		return
	}

	if !now.Before(allocation.End) {
		remainingQuota := promise.Quota - promiseMaterializedQuota(now, tree, promise, util.OptNone[AllocationId]())
		if target.QuotaSelf+target.QuotaChildren > remainingQuota {
			target = promiseClampTargetToTotal(target, remainingQuota)
		}
		promiseCreateSuccessor(now, tree, promise, allocation, target)
		return
	}

	remainingQuota := promise.Quota - promiseMaterializedQuota(now, tree, promise, head)
	if target.QuotaSelf+target.QuotaChildren > remainingQuota {
		target = promiseClampTargetToTotal(target, remainingQuota)
	}

	if allocation.Start.After(now) {
		promiseAdjustFutureHeadStart(now, tree, promise, allocation.Id)
	}

	promiseApplyTarget(now, tree, allocation.Id, target)
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
// - promiseMaterializedQuota: Counts promise quota already accounted for under capacity/non-capacity semantics.

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

func promiseMaterializedQuota(now time.Time, tree *AccountingTree, promise *Promise, exclude util.Option[AllocationId]) int64 {
	wallet := tree.WalletsById[promise.Child]
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
		if tree.IsCapacityBased() && promiseAllocationRetired(now, allocation) {
			continue
		}

		total += allocation.QuotaSelf + allocation.QuotaChildren
	}
	return total
}

// Head selection and time handling
// ---------------------------------------------------------------------------------------------------------------------
// A promise head is the active allocation for the promise, or if none is active, the next allocation becoming active.
// If neither exists, the newest retired allocation is used as the predecessor for successor creation.
//
// Allocation periods are Start-inclusive and End-exclusive. When a future head exists and no active head exists, the
// planner may move the future head's start backward to remove a gap, as long as low-level period invariants still hold.
//
// The core helpers are:
//
// - promiseFindHead: Selects active, next future or latest retired materialization for a promise.
// - promiseAllocationActive: Checks promise allocation activity using Start-inclusive, End-exclusive time.
// - promiseAllocationRetired: Checks whether an allocation has reached its end boundary.
// - promiseAdjustFutureHeadStart: Moves a future head backward when this removes a promise gap safely.
// - promiseSetPeriod: Applies a period change through low-level invariant checks.

func promiseFindHead(now time.Time, tree *AccountingTree, promise *Promise) util.Option[AllocationId] {
	var active *Allocation
	var next *Allocation
	var retired *Allocation

	wallet := tree.WalletsById[promise.Child]
	if wallet == nil {
		return util.OptNone[AllocationId]()
	}

	for _, allocationId := range wallet.Allocations {
		allocation := tree.AllocationsById[allocationId]
		if allocation == nil || !allocation.Promise.Present || allocation.Promise.Value != promise.Id {
			continue
		}

		if promiseAllocationActive(now, allocation) {
			if active == nil || allocation.End.Before(active.End) {
				active = allocation
			}
		} else if allocation.Start.After(now) {
			if next == nil || allocation.Start.Before(next.Start) {
				next = allocation
			}
		} else if promiseAllocationRetired(now, allocation) {
			if retired == nil || allocation.End.After(retired.End) {
				retired = allocation
			}
		}
	}

	if active != nil {
		return util.OptValue(active.Id)
	}
	if next != nil {
		return util.OptValue(next.Id)
	}
	if retired != nil {
		return util.OptValue(retired.Id)
	}
	return util.OptNone[AllocationId]()
}

func promiseAllocationActive(now time.Time, allocation *Allocation) bool {
	return !now.Before(allocation.Start) && now.Before(allocation.End)
}

func promiseAllocationRetired(now time.Time, allocation *Allocation) bool {
	return !now.Before(allocation.End)
}

func promiseAdjustFutureHeadStart(now time.Time, tree *AccountingTree, promise *Promise, allocationId AllocationId) {
	allocation := tree.AllocationsById[allocationId]
	if allocation == nil || !allocation.Start.After(now) {
		return
	}

	start := promise.Start
	wallet := tree.WalletsById[promise.Child]
	if wallet == nil {
		return
	}
	for _, candidateId := range wallet.Allocations {
		candidate := tree.AllocationsById[candidateId]
		if candidate == nil || candidate.Id == allocationId || !candidate.Promise.Present || candidate.Promise.Value != promise.Id {
			continue
		}
		if candidate.End.After(start) && !candidate.End.After(allocation.Start) {
			start = candidate.End
		}
	}

	if allocation.Parent.Present {
		parent := tree.AllocationsById[allocation.Parent.Value]
		if parent != nil && start.Before(parent.Start) {
			start = parent.Start
		}
	}
	if start.Before(promise.Start) {
		start = promise.Start
	}
	if !start.Before(allocation.End) || start.Equal(allocation.Start) {
		return
	}

	promiseSetPeriod(now, tree, allocationId, start, allocation.End)
}

func promiseSetPeriod(now time.Time, tree *AccountingTree, allocationId AllocationId, start time.Time, end time.Time) {
	allocation := tree.AllocationsById[allocationId]
	if allocation == nil {
		log.Fatal("Promise reconciliation attempted to update period for unknown allocation %v", allocationId)
	}
	if start.After(end) {
		log.Fatal("Promise reconciliation attempted to set invalid period for allocation %v", allocationId)
	}
	if allocation.Parent.Present {
		parent := tree.AllocationsById[allocation.Parent.Value]
		if parent == nil || start.Before(parent.Start) || end.After(parent.End) {
			log.Fatal("Promise reconciliation attempted to move allocation %v outside parent period", allocationId)
		}
	}
	for _, childId := range allocation.Children {
		child := tree.AllocationsById[childId]
		if child != nil && (child.Start.Before(start) || child.End.After(end)) {
			log.Fatal("Promise reconciliation attempted to move allocation %v outside child period", allocationId)
		}
	}

	allocationMutate(now, tree, allocationId, func(alloc *Allocation, parent util.Option[*Allocation]) {
		alloc.Start = start
		alloc.End = end
	})
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
//
// The core helpers are:
//
// - promiseApplyTarget: Applies a target split to one allocation, after clamping to reachable capacity.
// - promiseExposeChildCapacity: Recursively exposes child capacity through ancestors.
// - promiseSetSplit: Applies split changes through low-level mutation and reservation accounting.

func promiseApplyTarget(now time.Time, tree *AccountingTree, allocationId AllocationId, target promiseTargetSplit) {
	allocation := tree.AllocationsById[allocationId]
	if allocation == nil {
		return
	}

	if allocation.ConsumedSelf <= allocation.QuotaSelf {
		target.QuotaSelf = max(target.QuotaSelf, allocation.ConsumedSelf)
	} else {
		target.QuotaSelf = max(target.QuotaSelf, allocation.QuotaSelf)
	}
	target.QuotaChildren = max(target.QuotaChildren, allocation.ReservedChildren)

	currentTotal := allocation.QuotaSelf + allocation.QuotaChildren
	targetTotal := target.QuotaSelf + target.QuotaChildren
	if targetTotal > currentTotal && allocation.Parent.Present {
		provided := promiseExposeChildCapacity(now, tree, allocation.Parent.Value, targetTotal-currentTotal)
		if provided < targetTotal-currentTotal {
			target = promiseClampTargetToTotal(target, currentTotal+provided)
		}
	}

	if allocation.ConsumedSelf <= allocation.QuotaSelf {
		target.QuotaSelf = max(target.QuotaSelf, allocation.ConsumedSelf)
	} else {
		target.QuotaSelf = max(target.QuotaSelf, allocation.QuotaSelf)
	}
	target.QuotaChildren = max(target.QuotaChildren, allocation.ReservedChildren)

	targetTotal = target.QuotaSelf + target.QuotaChildren
	if targetTotal > currentTotal && allocation.Parent.Present {
		parent := tree.AllocationsById[allocation.Parent.Value]
		if parent == nil {
			return
		}
		available := max(parent.QuotaChildren-parent.ReservedChildren, 0)
		if targetTotal-currentTotal > available {
			target = promiseClampTargetToTotal(target, currentTotal+available)
			target.QuotaSelf = max(target.QuotaSelf, allocation.QuotaSelf)
			target.QuotaChildren = max(target.QuotaChildren, allocation.ReservedChildren)
		}
	}
	promiseSetSplit(now, tree, allocationId, target.QuotaSelf, target.QuotaChildren)
}

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
// When a promise has no editable head, the planner creates a promise-owned allocation under an existing parent
// allocation. The parent may be an active allocation or the next future allocation that covers the requested promise
// interval. Creation is partial if the parent can only expose part of the target.
//
// The promise layer never creates root allocations. If no parent-backed capacity exists, creation simply does nothing
// and the promise remains under-covered until a later reconciliation pass.
//
// The core helpers are:
//
// - promiseCreateHead: Creates the first materialization for a promise.
// - promiseCreateSuccessor: Creates the next materialization after a retired head.
// - promiseFindSupportingParent: Finds a parent allocation that can expose some requested capacity.
// - promiseFindSupportingParentEx: Implementation that also reports how much capacity was exposed.
// - promiseCreateAllocation: Creates a promise-owned low-level allocation and reserves it in the parent.

func promiseCreateHead(now time.Time, tree *AccountingTree, promise *Promise, target promiseTargetSplit) {
	parentId := promiseFindSupportingParent(now, tree, promise.Parent, target.QuotaSelf+target.QuotaChildren, promise.Start, promise.End)
	if !parentId.Present {
		return
	}
	promiseCreateAllocation(now, tree, promise, parentId.Value, maxTime(now, promise.Start), promise.End, target)
}

func promiseCreateSuccessor(now time.Time, tree *AccountingTree, promise *Promise, previous *Allocation, target promiseTargetSplit) {
	start := previous.End
	if start.Before(promise.Start) {
		start = promise.Start
	}
	parentId := promiseFindSupportingParent(now, tree, promise.Parent, target.QuotaSelf+target.QuotaChildren, start, promise.End)
	if !parentId.Present {
		return
	}
	promiseCreateAllocation(now, tree, promise, parentId.Value, start, promise.End, target)
}

func promiseFindSupportingParent(now time.Time, tree *AccountingTree, walletId WalletId, quota int64, start time.Time, end time.Time) util.Option[AllocationId] {
	parentId, _ := promiseFindSupportingParentEx(now, tree, walletId, quota, start, end)
	return parentId
}

func promiseFindSupportingParentEx(now time.Time, tree *AccountingTree, walletId WalletId, quota int64, start time.Time, end time.Time) (util.Option[AllocationId], int64) {
	wallet := tree.WalletsById[walletId]
	if wallet == nil {
		return util.OptNone[AllocationId](), 0
	}

	type candidate struct {
		allocation *Allocation
		active     bool
	}
	candidates := []candidate{}
	for _, allocationId := range wallet.Allocations {
		allocation := tree.AllocationsById[allocationId]
		if allocation == nil || allocation.End.Before(start) || allocation.Start.After(end) {
			continue
		}
		if promiseAllocationActive(now, allocation) || allocation.Start.After(now) {
			candidates = append(candidates, candidate{allocation: allocation, active: promiseAllocationActive(now, allocation)})
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

	for _, candidate := range candidates {
		allocation := candidate.allocation
		childStart := maxTime(start, allocation.Start)
		childEnd := minTime(end, allocation.End)
		if !childStart.Before(childEnd) {
			continue
		}
		provided := promiseExposeChildCapacity(now, tree, allocation.Id, quota)
		if provided > 0 {
			return util.OptValue(allocation.Id), provided
		}
	}

	return util.OptNone[AllocationId](), 0
}

func promiseCreateAllocation(now time.Time, tree *AccountingTree, promise *Promise, parentId AllocationId, start time.Time, end time.Time, target promiseTargetSplit) {
	parent := tree.AllocationsById[parentId]
	childWallet := tree.WalletsById[promise.Child]
	if parent == nil || childWallet == nil {
		return
	}

	start = maxTime(start, parent.Start)
	end = minTime(end, parent.End)
	if !start.Before(end) {
		return
	}

	total := target.QuotaSelf + target.QuotaChildren
	if total <= 0 {
		return
	}
	if parent.QuotaChildren-parent.ReservedChildren < total {
		provided := promiseExposeChildCapacity(now, tree, parentId, total)
		if provided < total {
			target = promiseClampTargetToTotal(target, provided)
			total = target.QuotaSelf + target.QuotaChildren
			if total <= 0 {
				return
			}
		}
	}

	allocationId := AllocationId(accGlobals.AllocIdAcc.Add(1))
	allocation := &Allocation{
		Id:               allocationId,
		Wallet:           promise.Child,
		Parent:           util.OptValue(parentId),
		Start:            start,
		End:              end,
		QuotaSelf:        target.QuotaSelf,
		QuotaChildren:    target.QuotaChildren,
		ConsumedSelf:     0,
		ReservedChildren: 0,
		Grant:            promise.Grant,
		Promise:          util.OptValue(promise.Id),
		Dirty:            true,
	}

	tree.AllocationsById[allocationId] = allocation
	childWallet.Allocations = append(childWallet.Allocations, allocationId)
	childWallet.Dirty = true
	allocationMutate(now, tree, parentId, func(parentAlloc *Allocation, parentParent util.Option[*Allocation]) {
		parentAlloc.ReservedChildren += total
		parentAlloc.Children = append(parentAlloc.Children, allocationId)
	})
	allocationMutate(now, tree, allocationId, func(alloc *Allocation, parent util.Option[*Allocation]) {})
	lifecycleScanAllocation(now, tree, allocation)
	walletMarkSignificantUpdate(now, tree, childWallet)
	walletReevaluateLock(now, tree, childWallet)
	parentWallet := tree.WalletsById[parent.Wallet]
	walletMarkSignificantUpdate(now, tree, parentWallet)
	walletReevaluateLock(now, tree, parentWallet)
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
