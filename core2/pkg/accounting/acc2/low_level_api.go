package acc2

import (
	"cmp"
	"fmt"
	"net/http"
	"slices"
	"sync"
	"sync/atomic"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

// Low-level accounting system
// =====================================================================================================================
// This file implements the low-level accounting tree used by the promise system and by direct usage reporting. The
// low-level system owns the concrete state: wallets, allocations, quota split, consumption and lock state. Higher
// layers may decide what should exist, but this layer decides whether an allocation tree is valid and how reported
// usage is distributed across concrete allocations.
//
// The core concepts are:
//
// - Wallets: One per owner per product category. A wallet stores the total consumed amount reported for that owner.
// - Allocations: Time-bounded quota rows owned by a wallet. Allocations may form a parent/child tree.
// - QuotaSelf: Quota available for direct consumption in the allocation's wallet.
// - QuotaChildren: Quota exposed for child allocations.
// - ReservedChildren: The amount of QuotaChildren already consumed by child allocation totals.
// - ConsumedSelf: Usage currently assigned to this allocation.
//
// The important invariant is that every child allocation reserves its full total quota in its parent. This is why most
// mutations go through allocationMutate: it lets the caller change state, then checks that the allocation and its parent
// still agree about reservations and time bounds.
//
// Time is supplied by callers and is assumed to be sampled once per public operation. Allocation Start is inclusive and
// End is exclusive. At End, an allocation is retired and the cleanup rules near UsageReport may release unused quota
// back to its parent.

// Core types and globals
// ---------------------------------------------------------------------------------------------------------------------
// The entire low-level tree is reachable from accGlobals. There is one AccountingTree per product category, and all
// wallets and allocations for that category live inside that tree. IDs are process-global integers, matching the older
// accounting implementation.
//
// PromisePolicy uses integer basis points for fractional configuration. 10,000 basis points means 100%, 5,000 means
// 50%, and so on. This avoids floating point behavior in accounting code while still allowing policy fractions.
//
// ---------------------------------------------------------------------------------------------------------------------
// MUTEX LOCK ORDER: globals > tree > scopedUsage
// AccountingTree requires its mutex for mutation. Types inside a tree are protected by the tree mutex.
// ---------------------------------------------------------------------------------------------------------------------

type AllocationId int
type WalletId int
type GrantId int

var accGlobals struct {
	Mu sync.RWMutex

	TestingEnabled bool

	Usage map[string]*ScopedUsage // NOTE(Dan): quite annoying that this has to be global
	Trees map[accapi.ProductCategoryIdV2]*AccountingTree

	OwnerIdAcc  atomic.Int64
	WalletIdAcc atomic.Int64
	GroupIdAcc  atomic.Int64
	AllocIdAcc  atomic.Int64
}

type AccountingTree struct {
	Mu       sync.RWMutex
	Category accapi.ProductCategory // does not require any mutex

	SignificantUpdateAt time.Time

	WalletsById    map[WalletId]*Wallet
	WalletsByOwner map[string]*Wallet

	AllocationsById map[AllocationId]*Allocation

	disableEvaluation bool

	Policy PromisePolicy
}

type ReservationMode int

const (
	// ReservationModeMinimal will only reserve what is needed right now plus a small slack. This will maximize the
	// utilization but causes more future edits in the promise reconciliation step.
	ReservationModeMinimal ReservationMode = iota

	// ReservationModeBuffered will reserve enough to make it very likely that there is enough space to survive until
	// the next periodic reconciliation loop.
	ReservationModeBuffered

	// ReservationModeCommitted will reserve a large amount of the promise up front.
	ReservationModeCommitted
)

type PromisePolicy struct {
	Mode ReservationMode

	MinSlack int64

	// GrowthStep rounds policy targets up to stable accounting-sized chunks. A zero value disables rounding.
	GrowthStep int64

	// ForecastWindow controls how far buffered reservations project the current EWMA trend. A zero value means no
	// forecast beyond the latest measured demand.
	ForecastWindow time.Duration

	// CommittedFractionBasisPoints controls how much of a promise should be reserved in committed mode. A zero value
	// defaults to 100%.
	CommittedFractionBasisPoints int64

	// TrendAlphaBasisPoints controls the EWMA update weight. A zero value defaults to 50%.
	TrendAlphaBasisPoints int64

	// TightReservationThresholdBasisPoints controls when reconciliation starts reducing buffers. A zero value defaults
	// to 85%.
	TightReservationThresholdBasisPoints int64
}

func (t *AccountingTree) IsCapacityBased() bool {
	switch t.Category.AccountingFrequency {
	case accapi.AccountingFrequencyOnce:
		return true
	default:
		return false
	}
}

type ScopedUsage struct {
	Mu    sync.RWMutex
	Key   string
	Usage int64
}

type Wallet struct {
	Id          WalletId
	Allocations []AllocationId
	Consumed    int64
	Locked      bool
	Owner       accapi.WalletOwner
	Category    accapi.ProductCategoryIdV2

	PromiseDemandEwma     int64
	PromiseDemandObserved int64
	PromiseDemandTrend    int64
	PromiseTrendUpdatedAt time.Time
}

type Allocation struct {
	Id AllocationId

	Wallet WalletId
	Parent util.Option[AllocationId]

	Start time.Time
	End   time.Time

	QuotaSelf        int64
	QuotaChildren    int64
	ConsumedSelf     int64
	ReservedChildren int64

	Children []AllocationId

	Grant   util.Option[GrantId]
	Promise util.Option[PromiseId]
}

func (a *Allocation) IsActive(now time.Time) bool {
	return !now.Before(a.Start) && now.Before(a.End)
}

func (a *Allocation) IsRetired(now time.Time) bool {
	return !now.Before(a.End)
}

// Allocation lifecycle API
// ---------------------------------------------------------------------------------------------------------------------
// AllocationCreate and AllocationUpdate are the low-level entry points for manually materializing allocation rows. They
// intentionally operate on concrete allocations rather than promises. The promise layer may call the non-exported
// mutation helpers below when it needs split-aware updates, but these exported functions keep the simple public shape:
// create a total quota and update a total quota or period.
//
// These functions validate everything that can be checked before mutation. The final invariant checks still happen in
// allocationMutate, so callers cannot accidentally leave the tree inconsistent after a successful update.
//
// The core APIs are:
//
// - AllocationCreate: Creates a root or child allocation and reserves child quota in the parent when needed.
// - AllocationUpdate: Changes total quota or period while preserving consumption, child reservations and parent bounds.

func AllocationCreate(
	now time.Time,
	category accapi.ProductCategoryIdV2,
	start time.Time,
	end time.Time,
	quota int64,
	recipient WalletId,
	parentAllocation util.Option[AllocationId],
	grantedIn util.Option[GrantId],
) (AllocationId, *util.HttpError) {
	var allocationId AllocationId
	err := treeMutate(category, func(tree *AccountingTree) *util.HttpError {
		if start.After(end) {
			return util.HttpErr(http.StatusBadRequest, "start must occur before the end of an allocation")
		}

		if quota < 0 {
			return util.HttpErr(http.StatusBadRequest, "quota must not be negative")
		}

		recipientWallet, ok := tree.WalletsById[recipient]
		if !ok {
			return util.HttpErr(http.StatusNotFound, "unknown recipient wallet")
		}

		if parentAllocation.Present {
			alloc, ok := tree.AllocationsById[parentAllocation.Value]
			if !ok {
				return util.HttpErr(http.StatusNotFound, "unknown parent wallet")
			}

			if alloc.Start.After(start) || alloc.End.Before(end) {
				return util.HttpErr(http.StatusBadRequest, "child allocation must occur within the parent allocation's duration")
			}

			if alloc.QuotaChildren-alloc.ReservedChildren < quota {
				return util.HttpErr(http.StatusBadRequest, "parent allocation is not capable of supporting the child allocation")
			}
		}

		allocationId = AllocationId(accGlobals.AllocIdAcc.Add(1))
		allocation := &Allocation{
			Id:               allocationId,
			Wallet:           recipient,
			Parent:           parentAllocation,
			Start:            start,
			End:              end,
			QuotaSelf:        quota,
			QuotaChildren:    0,
			ConsumedSelf:     0,
			ReservedChildren: 0,
			Grant:            grantedIn,
			Promise:          util.OptNone[PromiseId](),
		}

		tree.AllocationsById[allocationId] = allocation
		recipientWallet.Allocations = append(recipientWallet.Allocations, allocationId)

		if parentAllocation.Present {
			allocationMutate(now, tree, parentAllocation.Value, func(parentAlloc *Allocation, parent util.Option[*Allocation]) {
				parentAlloc.ReservedChildren += quota
				parentAlloc.Children = append(parentAlloc.Children, allocationId)
			})
		}

		allocationMutate(now, tree, allocationId, func(alloc *Allocation, parent util.Option[*Allocation]) {})

		tree.SignificantUpdateAt = now
		return nil
	})
	return allocationId, err
}

func AllocationUpdate(
	now time.Time,
	category accapi.ProductCategoryIdV2,
	id AllocationId,
	quota util.Option[int64],
	start util.Option[time.Time],
	end util.Option[time.Time],
) (GrantId, string, *util.HttpError) {
	var grantedIn GrantId
	var changelog string
	err := treeMutate(category, func(tree *AccountingTree) *util.HttpError {
		alloc, ok := tree.AllocationsById[id]
		if !ok {
			return util.HttpErr(http.StatusNotFound, "unknown allocation")
		}

		proposedStart := alloc.Start
		if start.Present {
			proposedStart = start.Value
		}

		proposedEnd := alloc.End
		if end.Present {
			proposedEnd = end.Value
		}

		proposedQuota := alloc.QuotaSelf + alloc.QuotaChildren
		if quota.Present {
			proposedQuota = quota.Value
		}

		if proposedStart.After(proposedEnd) {
			return util.HttpErr(http.StatusBadRequest, "start must occur before the end of an allocation")
		}

		if quota.Present && proposedQuota < 0 {
			return util.HttpErr(http.StatusBadRequest, "quota must not be negative")
		}

		if start.Present && !proposedStart.Equal(alloc.Start) && now.After(alloc.Start) {
			return util.HttpErr(http.StatusForbidden, "cannot change the starting time of an allocation which has already started")
		}

		if end.Present && !proposedEnd.Equal(alloc.End) && now.After(alloc.End) {
			return util.HttpErr(http.StatusForbidden, "cannot change the ending time of an allocation which has already ended")
		}

		if proposedQuota < alloc.QuotaChildren+alloc.ConsumedSelf {
			return util.HttpErr(http.StatusForbidden, "quota cannot be lowered below existing consumption and child reservations")
		}

		for _, childId := range alloc.Children {
			child := tree.AllocationsById[childId]
			if child.Start.Before(proposedStart) || child.End.After(proposedEnd) {
				return util.HttpErr(http.StatusForbidden, "allocation period cannot exclude an existing child allocation")
			}
		}

		currentQuota := alloc.QuotaSelf + alloc.QuotaChildren
		delta := proposedQuota - currentQuota
		if alloc.Parent.Present {
			parentAlloc, ok := tree.AllocationsById[alloc.Parent.Value]
			if !ok {
				return util.HttpErr(http.StatusNotFound, "unknown parent allocation")
			}

			if proposedStart.Before(parentAlloc.Start) || proposedEnd.After(parentAlloc.End) {
				return util.HttpErr(http.StatusForbidden, "allocation period must fit inside the parent allocation")
			}

			if delta > 0 && parentAlloc.QuotaChildren-parentAlloc.ReservedChildren < delta {
				return util.HttpErr(http.StatusForbidden, "the parent allocation does not have enough available quota for this update")
			}
		}

		allocationMutate(now, tree, id, func(alloc *Allocation, parent util.Option[*Allocation]) {
			alloc.Start = proposedStart
			alloc.End = proposedEnd

			if quota.Present {
				alloc.QuotaSelf = proposedQuota - alloc.QuotaChildren

				if parent.Present {
					parent.Value.ReservedChildren += delta
				}
			}
		})

		tree.SignificantUpdateAt = now

		if alloc.Grant.Present {
			grantedIn = alloc.Grant.Value

			if quota.Present {
				amount := proposedQuota
				switch tree.Category.AccountingFrequency {
				case accapi.AccountingFrequencyPeriodicMinute:
					amount = proposedQuota / 60
				case accapi.AccountingFrequencyPeriodicDay:
					amount = proposedQuota * 24
				}
				changelog += fmt.Sprintf("The quota for %s (%s) has manually been updated to %d.\n", tree.Category.Name, tree.Category.Provider, amount)
			}

			if start.Present {
				changelog += fmt.Sprintf("The start date for the granted %s (%s) allocation has manually been updated to %s.\n", tree.Category.Name, tree.Category.Provider, proposedStart.String())
			}

			if end.Present {
				changelog += fmt.Sprintf("The end date for the granted %s (%s) allocation has manually been updated to %s.\n", tree.Category.Name, tree.Category.Provider, proposedEnd.String())
			}
		}

		return nil
	})

	return grantedIn, changelog, err
}

// Mutation and invariant helpers
// ---------------------------------------------------------------------------------------------------------------------
// The low-level accounting tree is deliberately strict: mutation functions are allowed to change several related fields
// at once, but every mutation immediately re-checks the local invariants. This keeps bugs close to the code that
// introduced them and makes the promise layer safe to implement as a planner over the same data structures.
//
// allocationMutate checks allocation-local invariants and parent/child reservation consistency. walletMutate checks
// that the wallet's total consumption matches the sum of all ConsumedSelf fields in the wallet's allocations.
// treeMutate is the lock boundary used by public entry points.
//
// The core helpers are:
//
// - allocationMutate: Applies a mutation to one allocation and checks local and parent/child invariants.
// - walletMutate: Locks a tree by category, finds a wallet by owner and runs a wallet mutation.
// - walletMutateEx: Runs a wallet mutation when the caller already holds the tree lock.
// - walletAssertConsumptionMatchesAllocations: Verifies wallet.Consumed equals the sum of allocation consumption.
// - treeMutate: Locates and locks the accounting tree for a category.

func allocationMutate(
	now time.Time,
	tree *AccountingTree,
	aId AllocationId,
	fn func(alloc *Allocation, parent util.Option[*Allocation]),
) {
	a, ok := tree.AllocationsById[aId]
	if !ok {
		log.Fatal("Allocation %v does not exist", aId)
	}

	reportError := func(format string, args ...any) {
		log.Fatal("Assertion error %#v: %s", a, fmt.Sprintf(format, args...))
	}

	initialStart := a.Start
	initialEnd := a.End
	initialQuotaSelf := a.QuotaSelf

	parent := util.OptNone[*Allocation]()
	if a.Parent.Present {
		p, ok := tree.AllocationsById[a.Parent.Value]
		if !ok {
			reportError("Allocation has invalid parent")
		}

		parent.Set(p)
	}

	fn(a, parent)

	if a.Id <= 0 {
		reportError("Id <= 0")
	}

	if a.Wallet <= 0 {
		reportError("Wallet <= 0")
	}

	if !a.Parent.Present && a.Promise.Present {
		reportError("Root allocations should not have promises")
	}

	if a.Start.After(a.End) {
		reportError("Start > End")
	}

	if a.Start != initialStart && now.After(initialStart) {
		reportError("Start cannot be mutated after start of allocation")
	}

	if a.End != initialEnd && now.After(initialEnd) {
		reportError("End cannot be mutated after end of allocation")
	}

	if a.QuotaChildren < 0 {
		reportError("QuotaChildren < 0")
	}

	if a.QuotaSelf < 0 {
		reportError("QuotaSelf < 0")
	}

	if a.ConsumedSelf < 0 {
		reportError("ConsumedSelf < 0")
	}

	if a.ReservedChildren < 0 {
		reportError("ReservedChildren < 0")
	}

	if a.QuotaSelf != initialQuotaSelf && a.QuotaSelf < a.ConsumedSelf {
		reportError("QuotaSelf must not be lowered below ConsumedSelf by an update")
	}

	if a.QuotaChildren < a.ReservedChildren {
		reportError("QuotaChildren < ReservedChildren")
	}

	if parent.Present {
		p := parent.Value

		if a.Parent.Value != p.Id {
			reportError("Data corruption in parent allocation")
		}

		if a.Start.Before(p.Start) || a.End.After(p.End) {
			reportError("Allocation period must fit inside parent")
		}

		if p.ReservedChildren < a.QuotaSelf {
			reportError("parent.ReservedChildren < a.QuotaSelf")
		}

		foundSelf := false
		expectedChildReservation := int64(0)
		for _, childId := range p.Children {
			if a.Id == childId {
				foundSelf = true
			}

			child, ok := tree.AllocationsById[childId]
			if !ok {
				reportError("child %v is not valid but still in children slice", childId)
			}

			expectedChildReservation += child.QuotaSelf + child.QuotaChildren
		}

		if !foundSelf {
			reportError("!foundSelf")
		}

		if p.ReservedChildren != expectedChildReservation {
			reportError("Unexpected parent reservation %#v", p)
		}
	}
}

func walletMutate(category accapi.ProductCategoryIdV2, owner accapi.WalletOwner, fn func(tree *AccountingTree, wallet *Wallet)) {
	treeMutate(category, func(tree *AccountingTree) *util.HttpError {
		walletMutateEx(tree, owner, func(wallet *Wallet) {
			fn(tree, wallet)
		})
		return nil
	})
}

func walletMutateEx(tree *AccountingTree, owner accapi.WalletOwner, fn func(wallet *Wallet)) {
	ref := owner.Reference()
	wallet, ok := tree.WalletsByOwner[ref]
	if !ok {
		return // TODO
	}

	fn(wallet)
	walletAssertConsumptionMatchesAllocations(tree, wallet)
}

func walletAssertConsumptionMatchesAllocations(tree *AccountingTree, wallet *Wallet) {
	reportError := func(format string, args ...any) {
		log.Fatal("Assertion error %#v: %s", wallet, fmt.Sprintf(format, args...))
	}

	consumed := int64(0)
	for _, allocId := range wallet.Allocations {
		allocReadOnly := tree.AllocationsById[allocId]
		consumed += allocReadOnly.ConsumedSelf
	}

	if wallet.Consumed != consumed {
		reportError("wallet consumed was expected to be %v but it wasn't", consumed)
	}
}

func treeMutate(category accapi.ProductCategoryIdV2, fn func(tree *AccountingTree) *util.HttpError) *util.HttpError {
	accGlobals.Mu.RLock()
	tree, ok := accGlobals.Trees[category]
	accGlobals.Mu.RUnlock()

	if !ok {
		return util.HttpErr(http.StatusInternalServerError, "unknown category: %#v", category)
	}

	tree.Mu.Lock()
	defer tree.Mu.Unlock()
	return fn(tree)
}

// Usage reporting
// ---------------------------------------------------------------------------------------------------------------------
// UsageReport is the provider-facing low-level operation. It receives an absolute usage number for one wallet and then
// redistributes that number across active and retired allocations. Providers may report usage beyond the current quota;
// in that case the excess remains assigned to an allocation so the wallet's total still reflects reality.
//
// Before and after the redistribution, the promise system is asked to reconcile. The first reconciliation gives the
// promise layer a chance to materialize enough capacity for the incoming report. The second reconciliation sees the
// final consumption distribution and may shrink, grow, or advance materializations based on the updated state.
//
// Capacity-based products and time-based products differ in retirement behavior. Capacity usage can move away from
// retired allocations once active capacity exists. Time-based usage keeps retired quota meaningful, but excess retired
// usage can be moved forward when an active allocation can absorb it.

func UsageReport(now time.Time, request accapi.ReportUsageRequest) (success bool, err *util.HttpError) {
	PromiseReconcile(now, request.CategoryIdV2, request.Owner, util.OptValue(request.Usage))

	walletMutate(request.CategoryIdV2, request.Owner, func(tree *AccountingTree, wallet *Wallet) {
		if len(wallet.Allocations) == 0 {
			success, err = false, util.HttpErr(http.StatusBadRequest, "this owner does not have any such resources")
			return
		}

		// NOTE(Dan): We are going to start moving these scopes to their proper owners based on how the providers use
		// them. For this reason, we assume that we can actually retrieve the scope correctly from within a single
		// wallet.
		absoluteAmount := request.Usage // TODO Scopes and delta

		retiredAmount := int64(0)

		var activeAllocationsReadOnly []*Allocation
		var retiredAllocations []AllocationId
		hasExcessUsageInRetired := false

		for _, allocId := range wallet.Allocations {
			alloc := tree.AllocationsById[allocId]
			if alloc.IsRetired(now) {
				retiredAmount += alloc.ConsumedSelf
				retiredAllocations = append(retiredAllocations, allocId)

				if tree.IsCapacityBased() {
					if alloc.ConsumedSelf > 0 {
						hasExcessUsageInRetired = true
					}
				} else {
					if alloc.ConsumedSelf > alloc.QuotaSelf {
						hasExcessUsageInRetired = true
					}
				}
			} else if alloc.IsActive(now) {
				activeAllocationsReadOnly = append(activeAllocationsReadOnly, alloc)
			}
		}

		if len(activeAllocationsReadOnly) == 0 && len(retiredAllocations) == 0 {
			success, err = false, util.HttpErr(http.StatusBadRequest, "this owner does not have any active or retired resources")
			return
		}

		wallet.Consumed = absoluteAmount

		slices.SortFunc(activeAllocationsReadOnly, func(a, b *Allocation) int {
			if a.End.Before(b.End) {
				return -1
			} else if a.End.After(b.End) {
				return 1
			} else if a.Start.Before(b.Start) {
				return -1
			} else if a.Start.After(b.Start) {
				return 1
			}
			return cmp.Compare(int(a.Id), int(b.Id))
		})

		amountToDistribute := int64(0)
		if tree.IsCapacityBased() {
			amountToDistribute = absoluteAmount
		} else {
			amountToDistribute = absoluteAmount - retiredAmount
		}

		if amountToDistribute < 0 {
			amountToDistribute = 0
		}

		for _, aReadOnly := range activeAllocationsReadOnly {
			allocationMutate(now, tree, aReadOnly.Id, func(alloc *Allocation, parent util.Option[*Allocation]) {
				newConsumption := min(alloc.QuotaSelf, amountToDistribute)
				alloc.ConsumedSelf = newConsumption
				amountToDistribute -= newConsumption
			})
		}

		hadSpaceToDistribute := amountToDistribute == 0

		if len(activeAllocationsReadOnly) > 0 && amountToDistribute > 0 {
			allocationMutate(now, tree, activeAllocationsReadOnly[0].Id, func(alloc *Allocation, parent util.Option[*Allocation]) {
				alloc.ConsumedSelf += amountToDistribute
				amountToDistribute = 0
			})
		}

		if len(retiredAllocations) > 0 && amountToDistribute > 0 {
			allocationMutate(now, tree, retiredAllocations[0], func(alloc *Allocation, parent util.Option[*Allocation]) {
				alloc.ConsumedSelf += amountToDistribute
			})
		}

		if tree.IsCapacityBased() {
			// Retain existing retired usage only for consumption that could not be placed on active allocations.
			// Do not increase retired ConsumedSelf unless there is no retired usage left to retain.
			for _, retiredId := range retiredAllocations {
				retiredReadOnly := tree.AllocationsById[retiredId]
				retiredUsageToKeep := min(retiredReadOnly.ConsumedSelf, amountToDistribute)
				retiredUsageToRemove := retiredReadOnly.ConsumedSelf - retiredUsageToKeep
				amountToDistribute -= retiredUsageToKeep

				if retiredUsageToRemove > 0 {
					allocationMutate(now, tree, retiredId, func(retired *Allocation, parent util.Option[*Allocation]) {
						retired.ConsumedSelf -= retiredUsageToRemove
					})
				}
			}

			if len(retiredAllocations) > 0 && amountToDistribute > 0 {
				allocationMutate(now, tree, retiredAllocations[0], func(retired *Allocation, parent util.Option[*Allocation]) {
					retired.ConsumedSelf += amountToDistribute
					amountToDistribute = 0
				})
			}
		} else {
			if hasExcessUsageInRetired && hadSpaceToDistribute && len(activeAllocationsReadOnly) > 0 {
				for _, retiredId := range retiredAllocations {
					retiredReadOnly := tree.AllocationsById[retiredId]
					if retiredReadOnly.ConsumedSelf > retiredReadOnly.QuotaSelf {
						excess := retiredReadOnly.ConsumedSelf - retiredReadOnly.QuotaSelf
						amountToDistribute = excess

						for _, aReadOnly := range activeAllocationsReadOnly {
							allocationMutate(now, tree, aReadOnly.Id, func(alloc *Allocation, parent util.Option[*Allocation]) {
								consumption := min(alloc.QuotaSelf-alloc.ConsumedSelf, amountToDistribute)
								alloc.ConsumedSelf += consumption
								amountToDistribute -= consumption
							})

							if amountToDistribute == 0 {
								break
							}
						}

						removed := excess - amountToDistribute

						if removed > 0 {
							allocationMutate(now, tree, retiredId, func(retired *Allocation, parent util.Option[*Allocation]) {
								retired.ConsumedSelf -= removed
							})
						}
					}
				}
			}
		}

		consumedInAllocations := int64(0)
		for _, allocId := range wallet.Allocations {
			alloc := tree.AllocationsById[allocId]
			consumedInAllocations += alloc.ConsumedSelf
		}

		consumedToRemove := consumedInAllocations - wallet.Consumed
		if consumedToRemove > 0 {
			for _, retiredId := range retiredAllocations {
				retiredReadOnly := tree.AllocationsById[retiredId]
				removed := min(retiredReadOnly.ConsumedSelf, consumedToRemove)
				if removed > 0 {
					allocationMutate(now, tree, retiredId, func(retired *Allocation, parent util.Option[*Allocation]) {
						retired.ConsumedSelf -= removed
					})
					consumedToRemove -= removed
				}

				if consumedToRemove == 0 {
					break
				}
			}
		}

		if consumedToRemove > 0 {
			for _, active := range activeAllocationsReadOnly {
				activeReadOnly := tree.AllocationsById[active.Id]
				excess := max(activeReadOnly.ConsumedSelf-activeReadOnly.QuotaSelf, 0)
				removed := min(excess, consumedToRemove)
				if removed > 0 {
					allocationMutate(now, tree, active.Id, func(alloc *Allocation, parent util.Option[*Allocation]) {
						alloc.ConsumedSelf -= removed
					})
					consumedToRemove -= removed
				}

				if consumedToRemove == 0 {
					break
				}
			}
		}

		if consumedToRemove > 0 {
			for _, allocId := range wallet.Allocations {
				activeReadOnly := tree.AllocationsById[allocId]
				removed := min(activeReadOnly.ConsumedSelf, consumedToRemove)
				if removed > 0 {
					allocationMutate(now, tree, allocId, func(alloc *Allocation, parent util.Option[*Allocation]) {
						alloc.ConsumedSelf -= removed
					})
					consumedToRemove -= removed
				}

				if consumedToRemove == 0 {
					break
				}
			}
		}

		/*
			When retirement occurs, the following applies:

			- Lower QuotaChildren to ReservedChildren.
			- Lower QuotaSelf to ConsumedSelf.
			- Do not lower ConsumedSelf here unless the same usage was already moved to another allocation in this wallet.
			- When QuotaSelf or QuotaChildren goes down, release the same amount from parent.ReservedChildren.
			- Parent releases may enable the same cleanup recursively up the tree.
			- These rules only lower values. They must not increase retired allocation quota or usage.
		*/
		retirementCleanupQueue := slices.Clone(retiredAllocations)
		for len(retirementCleanupQueue) > 0 {
			retiredId := retirementCleanupQueue[0]
			retirementCleanupQueue = retirementCleanupQueue[1:]

			retiredReadOnly := tree.AllocationsById[retiredId]
			if !retiredReadOnly.IsRetired(now) {
				continue
			}

			allocationMutate(now, tree, retiredId, func(retired *Allocation, parent util.Option[*Allocation]) {
				released := int64(0)

				if retired.QuotaChildren > retired.ReservedChildren {
					released += retired.QuotaChildren - retired.ReservedChildren
					retired.QuotaChildren = retired.ReservedChildren
				}

				if retired.QuotaSelf > retired.ConsumedSelf {
					released += retired.QuotaSelf - retired.ConsumedSelf
					retired.QuotaSelf = retired.ConsumedSelf
				}

				if released > 0 && parent.Present {
					parent.Value.ReservedChildren -= released
					retirementCleanupQueue = append(retirementCleanupQueue, parent.Value.Id)
				}
			})
		}
	})

	if err == nil {
		PromiseReconcile(now, request.CategoryIdV2, request.Owner, util.OptValue(request.Usage))
	}
	return
}

func WalletsBrowse(owner accapi.WalletOwner) []accapi.WalletV2 {
	return nil
}
