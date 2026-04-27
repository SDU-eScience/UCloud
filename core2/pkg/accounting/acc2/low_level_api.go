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

type AllocationId int
type WalletId int
type GrantId int
type PromiseId int

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
	return now.After(a.Start) && now.Before(a.End)
}

func (a *Allocation) IsRetired(now time.Time) bool {
	return now.After(a.End)
}

// =====================================================================================================================

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

func UsageReport(now time.Time, request accapi.ReportUsageRequest) (success bool, err *util.HttpError) {
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
	return
}

func WalletsBrowse(owner accapi.WalletOwner) []accapi.WalletV2 {
	return nil
}
