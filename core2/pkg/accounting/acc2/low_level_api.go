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

	Quota            int64
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
	tree *AccountingTree,
	start time.Time,
	end time.Time,
	quota int64,
	recipient WalletId,
	parent util.Option[WalletId],
	grantedIn util.Option[GrantId],
) (AllocationId, *util.HttpError) {
	return 0, nil
}

func AllocationUpdate(
	now time.Time,
	tree *AccountingTree,
	id AllocationId,
	quota util.Option[int64],
	start util.Option[time.Time],
	end util.Option[time.Time],
) (GrantId, string, *util.HttpError) {
	return 0, "", nil
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
	treeMutate(category, func(tree *AccountingTree) {
		walletMutateEx(tree, owner, func(wallet *Wallet) {
			fn(tree, wallet)
		})
	})
}

func walletMutateEx(tree *AccountingTree, owner accapi.WalletOwner, fn func(wallet *Wallet)) {}

func treeMutate(category accapi.ProductCategoryIdV2, fn func(tree *AccountingTree)) {

}

func UsageReport(now time.Time, request accapi.ReportUsageRequest) (success bool, err *util.HttpError) {
	walletMutate(request.CategoryIdV2, request.Owner, func(tree *AccountingTree, wallet *Wallet) {
		// NOTE(Dan): We are going to start moving these scopes to their proper owners based on how the providers use
		// them. For this reason, we assume that we can actually retrieve the scope correctly from within a single
		// wallet.

		if len(wallet.Allocations) == 0 {
			success, err = false, util.HttpErr(http.StatusBadRequest, "this owner does not have any such resources")
			return
		}

		absoluteAmount := request.Usage // TODO Scopes and delta
		wallet.Consumed = absoluteAmount

		if tree.IsCapacityBased() {
			/*
				1. amountToDistribute = absoluteAmount
				2. Distribute amountToDistribute amongst active allocations in a weighted manner (taking into account fractions)
				3. Distribute excess on retired allocations that have consumed > 0 (the consumed value must not go up in each alloc)
				4. Distribute any remaining excess on active allocations
				5. Use retirement rules to release retired capacity reservations
			*/
		} else {
			/*
				1. retiredAmount = sum of retired ConsumedSelf
				2. amountToDistribute = absoluteAmount - retiredAmount
				3. Distribute amountToDistribute amongst active allocations in a weighted manner (taking into account fractions)
				4. Distribute any excess amountToDistribute after all active allocations were fully used
				6. Distribute (in a weighted manner) excess usage from retired allocations onto active allocations
				5. Use retirement rules to release retired capacity reservations
			*/

			retiredAmount := int64(0)

			var activeAllocationsReadOnly []*Allocation
			var retiredAllocations []AllocationId
			hasExcessUsageInRetired := false

			for _, allocId := range wallet.Allocations {
				alloc := tree.AllocationsById[allocId]
				if alloc.IsRetired(now) {
					retiredAmount += alloc.ConsumedSelf
					retiredAllocations = append(retiredAllocations, allocId)

					if alloc.ConsumedSelf > alloc.QuotaSelf {
						hasExcessUsageInRetired = true
					}
				} else if alloc.IsActive(now) {
					activeAllocationsReadOnly = append(activeAllocationsReadOnly, alloc)
				}
			}

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

			amountToDistribute := absoluteAmount - retiredAmount
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

			if hasExcessUsageInRetired && hadSpaceToDistribute && len(activeAllocationsReadOnly) > 0 {
				for _, retiredId := range retiredAllocations {
					retiredReadOnly := tree.AllocationsById[retiredId]
					if retiredReadOnly.ConsumedSelf > retiredReadOnly.QuotaSelf {
						amountToDistribute = retiredReadOnly.ConsumedSelf - retiredReadOnly.QuotaSelf

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

						removed := (retiredReadOnly.ConsumedSelf - retiredReadOnly.QuotaSelf) - amountToDistribute

						allocationMutate(now, tree, retiredId, func(retired *Allocation, parent util.Option[*Allocation]) {

						})
					}
				}
			}
		}

		/*
			When retirement occurs, the following applies:

			- We want to ReservedChildren and QuotaChildren to become equal
			- We want to ConsumedSelf and QuotaSelf to become equal
			- When these change, release resources to parent to hold invariants (and this may further propagate)
			- All of this only applies if the values are going down. They do not apply if they go up.
		*/
	})
	return
}

func WalletsBrowse(owner accapi.WalletOwner) []accapi.WalletV2 {
	return nil
}
