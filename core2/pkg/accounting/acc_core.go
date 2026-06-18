package accounting

import (
	"cmp"
	"fmt"
	"net/http"
	"regexp"
	"slices"
	"sync"
	"sync/atomic"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
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
// PromisePolicy uses integer basis points for trend configuration. 10,000 basis points means 100%, 5,000 means 50%,
// and so on. This avoids floating point behavior in accounting code while still allowing policy fractions.
//
// ---------------------------------------------------------------------------------------------------------------------
// MUTEX LOCK ORDER: globals > tree > scopedUsage
// AccountingTree requires its mutex for mutation. Types inside a tree are protected by the tree mutex.
// ---------------------------------------------------------------------------------------------------------------------

type AllocationId int
type WalletId int
type GrantId int
type OwnerId int

var accGlobals struct {
	Mu sync.RWMutex

	TestingEnabled bool

	Usage map[string]*ScopedUsage // NOTE(Dan): quite annoying that this has to be global
	Trees map[accapi.ProductCategoryIdV2]*AccountingTree

	OwnersByReference map[string]*walletOwner
	OwnersById        map[OwnerId]*walletOwner

	OwnerIdAcc  atomic.Int64
	WalletIdAcc atomic.Int64
	AllocIdAcc  atomic.Int64
}

type walletOwner struct {
	Id        OwnerId
	Reference string
	Dirty     bool
}

func (o *walletOwner) WalletOwner() accapi.WalletOwner {
	if projectRegex.MatchString(o.Reference) {
		return accapi.WalletOwnerProject(o.Reference)
	} else {
		return accapi.WalletOwnerUser(o.Reference)
	}
}

var projectRegex = regexp.MustCompile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

type AccountingTree struct {
	Mu       sync.RWMutex
	Category accapi.ProductCategory // does not require any mutex

	SignificantUpdateAt time.Time

	WalletsById    map[WalletId]*Wallet
	WalletsByOwner map[string]*Wallet

	AllocationsById map[AllocationId]*Allocation
	PromiseTree     PromiseTree

	disableEvaluation bool

	Policy PromisePolicy
}

type PromisePolicy struct {
	MinSlack int64

	// GrowthStep rounds policy targets up to stable accounting-sized chunks. A zero value disables rounding.
	GrowthStep int64

	// TrendAlphaBasisPoints controls the EWMA update weight. A zero value defaults to 50%.
	TrendAlphaBasisPoints int64
}

// IsCapacityBased reports whether the category represents point-in-time capacity rather than accumulated usage.
//
// Capacity-based products can move usage away from retired allocations and retired promise materializations stop
// contributing to promise quota. Periodic products keep retired usage meaningful for accounting history.
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
	Dirty bool
}

type Wallet struct {
	Id          WalletId
	Allocations []AllocationId
	Consumed    int64
	Locked      bool
	Owner       accapi.WalletOwner
	Category    accapi.ProductCategoryIdV2
	Dirty       bool

	LastSignificantUpdateAt time.Time

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
	RetiredQuota     int64
	RetiredUsage     int64
	Activated        bool
	Retired          bool

	Children []AllocationId

	Grant   util.Option[GrantId]
	Promise util.Option[PromiseId]
	Dirty   bool
}

func CategoryEnsure(category accapi.ProductCategory) {
	categoryId := category.ToId()
	accGlobals.Mu.Lock()
	defer accGlobals.Mu.Unlock()

	if accGlobals.Trees == nil {
		accGlobals.Trees = map[accapi.ProductCategoryIdV2]*AccountingTree{}
	}
	if accGlobals.Trees[categoryId] != nil {
		return
	}

	accGlobals.Trees[categoryId] = &AccountingTree{
		Category:        category,
		WalletsById:     map[WalletId]*Wallet{},
		WalletsByOwner:  map[string]*Wallet{},
		AllocationsById: map[AllocationId]*Allocation{},
		PromiseTree: PromiseTree{
			PromisesById:     map[PromiseId]*Promise{},
			PromisesByParent: map[WalletId][]PromiseId{},
			PromisesByChild:  map[WalletId][]PromiseId{},
		},
		Policy: PromisePolicy{TrendAlphaBasisPoints: 10000},
	}
}

// IsActive reports whether now is inside the allocation's inclusive-start, exclusive-end interval.
//
// This checks the time interval only. It does not require the allocation.Activated flag to have been advanced by a
// lifecycle scan.
func (a *Allocation) IsActive(now time.Time) bool {
	return !now.Before(a.Start) && now.Before(a.End)
}

// IsRetired reports whether now is at or after the allocation's exclusive end boundary.
//
// This checks time only. It does not require allocation.Retired to have been set by lifecycleScanAllocation.
func (a *Allocation) IsRetired(now time.Time) bool {
	return !now.Before(a.End)
}

// shouldActivate reports whether lifecycleScanAllocation should flip allocation.Activated at now.
//
// The one-second tolerance preserves existing behavior around boundary sampling. Activation is persisted state and is
// what quota aggregation helpers use when deciding whether an allocation contributes.
func (a *Allocation) shouldActivate(now time.Time) bool {
	return !a.Activated && now.Add(time.Second).After(a.Start) && now.Before(a.End)
}

// shouldRetire reports whether lifecycleScanAllocation should flip allocation.Retired at now.
//
// The one-second tolerance matches activation behavior. Retirement snapshots quota and usage for later reporting and
// cleanup.
func (a *Allocation) shouldRetire(now time.Time) bool {
	return !a.Retired && now.Add(time.Second).After(a.End)
}

// WalletEnsure creates or returns the wallet id for an owner in a category.
//
// This is a public low-level creation helper. It only ensures wallet existence; it does not grant quota or make the
// wallet usable.
func WalletEnsure(category accapi.ProductCategoryIdV2, owner accapi.WalletOwner) (WalletId, *util.HttpError) {
	var walletId WalletId
	err := treeMutate(category, func(tree *AccountingTree) *util.HttpError {
		wallet := walletEnsureEx(tree, owner)
		walletId = wallet.Id
		return nil
	})
	return walletId, err
}

// walletEnsureEx creates or returns a wallet while the caller already holds the tree lock.
//
// New wallets start empty, unlocked and dirty. Lock state becomes meaningful once allocations or promises are added and
// walletReevaluateLock has been run.
func walletEnsureEx(tree *AccountingTree, owner accapi.WalletOwner) *Wallet {
	ref := owner.Reference()
	wallet := tree.WalletsByOwner[ref]
	if wallet != nil {
		return wallet
	}

	wallet = &Wallet{
		Id:          WalletId(accGlobals.WalletIdAcc.Add(1)),
		Allocations: []AllocationId{},
		Owner:       owner,
		Category:    tree.Category.ToId(),
		Dirty:       true,
	}
	tree.WalletsById[wallet.Id] = wallet
	tree.WalletsByOwner[ref] = wallet
	return wallet
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
		lifecycleScan(now, tree)

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
			Dirty:            true,
		}

		tree.AllocationsById[allocationId] = allocation
		recipientWallet.Allocations = append(recipientWallet.Allocations, allocationId)
		recipientWallet.Dirty = true

		if parentAllocation.Present {
			allocationMutate(now, tree, parentAllocation.Value, func(parentAlloc *Allocation, parent util.Option[*Allocation]) {
				parentAlloc.ReservedChildren += quota
				parentAlloc.Children = append(parentAlloc.Children, allocationId)
			})
			parent := tree.AllocationsById[parentAllocation.Value]
			if parent != nil {
				walletMarkSignificantUpdate(now, tree, tree.WalletsById[parent.Wallet])
			}
		}

		allocationMutate(now, tree, allocationId, func(alloc *Allocation, parent util.Option[*Allocation]) {})
		lifecycleScanAllocation(now, tree, allocation)
		walletMarkSignificantUpdate(now, tree, recipientWallet)
		walletReevaluateLock(now, tree, recipientWallet)

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
		lifecycleScan(now, tree)

		alloc, ok := tree.AllocationsById[id]
		if !ok {
			return util.HttpErr(http.StatusNotFound, "unknown allocation")
		}
		if alloc.Promise.Present {
			promiseTree := &tree.PromiseTree
			promise := promiseTree.PromisesById[alloc.Promise.Value]
			if promise == nil {
				return util.HttpErr(http.StatusNotFound, "unknown promise")
			}
			return allocationUpdatePromiseBacked(now, tree, promise, alloc.Grant, quota, start, end, &grantedIn, &changelog)
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

		walletMarkSignificantUpdate(now, tree, tree.WalletsById[alloc.Wallet])
		if alloc.Parent.Present {
			parent := tree.AllocationsById[alloc.Parent.Value]
			if parent != nil {
				walletMarkSignificantUpdate(now, tree, tree.WalletsById[parent.Wallet])
			}
		}
		lifecycleScanAllocation(now, tree, alloc)
		walletReevaluateLock(now, tree, tree.WalletsById[alloc.Wallet])

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

// allocationUpdatePromiseBacked applies a manual allocation update to the backing promise rather than one materialized row.
//
// Promise-backed allocations expose promise-level quota and period to users. Updating one of those allocations therefore
// edits the promise, reconciles materializations, records significant updates on both sides and emits grant changelog
// text when relevant.
func allocationUpdatePromiseBacked(
	now time.Time,
	tree *AccountingTree,
	promise *Promise,
	allocationGrant util.Option[GrantId],
	quota util.Option[int64],
	start util.Option[time.Time],
	end util.Option[time.Time],
	grantedIn *GrantId,
	changelog *string,
) *util.HttpError {
	proposedStart := promise.Start
	if start.Present {
		proposedStart = start.Value
	}

	proposedEnd := promise.End
	if end.Present {
		proposedEnd = end.Value
	}

	proposedQuota := promise.Quota
	if quota.Present {
		proposedQuota = quota.Value
	}

	if proposedStart.After(proposedEnd) {
		return util.HttpErr(http.StatusBadRequest, "start must occur before the end of a promise")
	}
	if quota.Present && proposedQuota < 0 {
		return util.HttpErr(http.StatusBadRequest, "quota must not be negative")
	}
	if start.Present && !proposedStart.Equal(promise.Start) && now.After(promise.Start) {
		return util.HttpErr(http.StatusForbidden, "cannot change the starting time of a promise which has already started")
	}
	if end.Present && !proposedEnd.Equal(promise.End) && now.After(promise.End) {
		return util.HttpErr(http.StatusForbidden, "cannot change the ending time of a promise which has already ended")
	}
	if proposedQuota < promiseRequiredQuota(now, tree, promise) {
		return util.HttpErr(http.StatusForbidden, "quota cannot be lowered below existing consumption and child reservations")
	}

	promise.Start = proposedStart
	promise.End = proposedEnd
	promise.Quota = proposedQuota
	promise.Dirty = true
	promiseReconcileOne(now, tree, promise)

	walletMarkSignificantUpdate(now, tree, tree.WalletsById[promise.Child])
	walletMarkSignificantUpdate(now, tree, tree.WalletsById[promise.Parent])

	grant := allocationGrant
	if promise.Grant.Present {
		grant = promise.Grant
	}
	if grant.Present {
		*grantedIn = grant.Value
		*changelog += allocationUpdateChangelog(tree, proposedQuota, proposedStart, proposedEnd, quota, start, end)
	}

	return nil
}

// promiseRequiredQuota returns the concrete promise-backed quota that must remain allocated for a promise.
//
// It is used when manually editing a promise-backed allocation through AllocationUpdate. The value is intentionally
// based on already-materialized allocation state, not promise quota: lowering a promise below existing consumption or
// child reservations would make the allocation tree invalid. For capacity-based products, retired allocations no longer
// require quota from the promise.
func promiseRequiredQuota(now time.Time, tree *AccountingTree, promise *Promise) int64 {
	wallet := tree.WalletsById[promise.Child]
	if wallet == nil {
		return 0
	}
	required := int64(0)
	for _, allocationId := range wallet.Allocations {
		allocation := tree.AllocationsById[allocationId]
		if allocation == nil || !allocation.Promise.Present || allocation.Promise.Value != promise.Id {
			continue
		}
		if tree.IsCapacityBased() && allocation.Retired {
			continue
		}
		required += allocation.ConsumedSelf + allocation.ReservedChildren
	}
	return required
}

// allocationUpdateChangelog formats the grant-facing audit text for manual promise allocation edits.
//
// The amount is converted to the same display convention used by normal allocation updates. This helper does not inspect
// or mutate accounting state; callers pass the proposed values and the options that were actually changed.
func allocationUpdateChangelog(
	tree *AccountingTree,
	proposedQuota int64,
	proposedStart time.Time,
	proposedEnd time.Time,
	quota util.Option[int64],
	start util.Option[time.Time],
	end util.Option[time.Time],
) string {
	changelog := ""
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
	return changelog
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

// walletMarkSignificantUpdate records that a wallet changed in a way providers or UI clients should observe.
//
// It updates both the category tree timestamp and the wallet timestamp. Use this for changes to availability,
// allocations, lock state or other externally visible state; routine dirty persistence alone should only set Dirty.
func walletMarkSignificantUpdate(now time.Time, tree *AccountingTree, wallet *Wallet) {
	if wallet == nil {
		return
	}
	tree.SignificantUpdateAt = now
	wallet.LastSignificantUpdateAt = now
	wallet.Dirty = true

	if tree.Category.Provider != "usagegen" {
		// TODO(Dan): If more than one million updates is ever made in a single lock-cycle, then this function will
		//   indefinitely stall the system. Please refactor the code, before the system reaches such a size.
		providerWalletNotifications <- wallet.Id
	}
}

// walletReevaluateLock updates wallet.Locked from the current admission-control view of remaining usable quota.
//
// A wallet is locked when walletRemainingUsable is zero. This is the post-mutation gate used after usage reports,
// lifecycle scans and allocation changes. Lock transitions are significant updates because providers use them to decide
// whether new work may be admitted.
func walletReevaluateLock(now time.Time, tree *AccountingTree, wallet *Wallet) {
	if wallet == nil {
		return
	}
	maxUsable := walletRemainingUsable(now, tree, wallet)
	if maxUsable <= 0 && !wallet.Locked {
		wallet.Locked = true
		walletMarkSignificantUpdate(now, tree, wallet)
	} else if maxUsable > 0 && wallet.Locked {
		wallet.Locked = false
		walletMarkSignificantUpdate(now, tree, wallet)
	}
}

// walletMaxUsable returns the externally advertised remaining usable amount for a wallet.
//
// This is the value exposed as WalletV2.MaxUsable and CheckProviderUsable.MaxUsable. It must always be zero for locked
// wallets, even if quota fields still describe promised or historical capacity. For unlocked wallets it delegates to
// walletRemainingUsable.
func walletMaxUsable(now time.Time, tree *AccountingTree, wallet *Wallet) int64 {
	if wallet == nil || wallet.Locked {
		return 0
	}
	return walletRemainingUsable(now, tree, wallet)
}

// walletRemainingUsable returns the non-negative amount that should currently be admitted for additional wallet usage.
//
// The base quota is compatibility-facing contributing quota: active/contributing promises plus low-level allocations.
// If the wallet's measured demand has already exceeded the effective concrete allocation-tree quota, the quota is
// clamped to that effective quota so overbooked promise trees do not keep admitting work solely because promise quota is
// larger than parent-backed capacity. This helper ignores wallet.Locked; use walletMaxUsable for API responses.
func walletRemainingUsable(now time.Time, tree *AccountingTree, wallet *Wallet) int64 {
	if wallet == nil {
		return 0
	}
	quota := int64(0)
	quota = walletQuotaContributing(now, tree, &tree.PromiseTree, wallet)
	effectiveQuota, hasEffectiveQuota := promiseWalletEffectiveReportQuota(tree, wallet)
	demand := promiseMeasuredWalletDemand(now, tree, wallet)
	if hasEffectiveQuota {
		if quota == 0 {
			quota = effectiveQuota
		} else if demand > effectiveQuota {
			quota = min(quota, effectiveQuota)
		}
	} else if quota == 0 {
		quota = effectiveQuota
	}
	return max(quota-demand, 0)
}

// lifecycleScan advances activation and retirement state for every allocation in a tree.
//
// It also cleans up retired allocations and reevaluates wallet locks. Call this at the start of public operations that
// interpret time-sensitive quota or usage state.
func lifecycleScan(now time.Time, tree *AccountingTree) {
	retiredCleanupQueue := make([]AllocationId, 0, len(tree.AllocationsById))
	for _, allocation := range tree.AllocationsById {
		lifecycleScanAllocation(now, tree, allocation)
		retiredCleanupQueue = append(retiredCleanupQueue, allocation.Id)
	}
	lifecycleCleanupRetiredAllocations(now, tree, retiredCleanupQueue)
	for _, wallet := range tree.WalletsById {
		walletReevaluateLock(now, tree, wallet)
	}
}

// lifecycleScanAllocation applies time-based activation and retirement transitions to one allocation.
//
// Activation makes an allocation count toward active/contributing quota. Retirement snapshots retired quota and usage so
// reporting and API code can still describe what happened after the active interval ends.
func lifecycleScanAllocation(now time.Time, tree *AccountingTree, allocation *Allocation) {
	if allocation == nil {
		return
	}
	wallet := tree.WalletsById[allocation.Wallet]
	if allocation.shouldActivate(now) {
		allocation.Activated = true
		allocation.Dirty = true
		walletMarkSignificantUpdate(now, tree, wallet)
	}
	if allocation.shouldRetire(now) {
		allocation.Retired = true
		allocation.Activated = true
		allocation.RetiredQuota = allocation.QuotaSelf + allocation.QuotaChildren
		allocation.RetiredUsage = allocation.ConsumedSelf
		allocation.Dirty = true
		walletMarkSignificantUpdate(now, tree, wallet)
	}
}

// lifecycleCleanupRetiredAllocations releases unused quota from retired allocations back up the allocation tree.
//
// The cleanup keeps enough retired quota to cover retained self-consumption and child reservations, then propagates any
// released child reservation to parents. Use this after lifecycle scans or usage redistribution, not as a standalone
// quota planning mechanism.
func lifecycleCleanupRetiredAllocations(now time.Time, tree *AccountingTree, initialQueue []AllocationId) {
	retirementCleanupQueue := slices.Clone(initialQueue)
	for len(retirementCleanupQueue) > 0 {
		retiredId := retirementCleanupQueue[0]
		retirementCleanupQueue = retirementCleanupQueue[1:]

		retiredReadOnly := tree.AllocationsById[retiredId]
		if retiredReadOnly == nil || !retiredReadOnly.IsRetired(now) {
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

			if released > 0 {
				walletMarkSignificantUpdate(now, tree, tree.WalletsById[retired.Wallet])
			}

			if released > 0 && parent.Present {
				parent.Value.ReservedChildren -= released
				retirementCleanupQueue = append(retirementCleanupQueue, parent.Value.Id)
				walletMarkSignificantUpdate(now, tree, tree.WalletsById[parent.Value.Wallet])
				walletReevaluateLock(now, tree, tree.WalletsById[parent.Value.Wallet])
			}
		})
		walletReevaluateLock(now, tree, tree.WalletsById[retiredReadOnly.Wallet])
	}
}

// allocationMutate is the low-level invariant boundary for modifying a concrete allocation.
//
// The callback may edit the allocation and, when present, its parent. After the callback this helper marks changed rows
// dirty and checks allocation-local invariants, parent/child reservation consistency and parent period containment. It
// should be used for all direct allocation state changes that can affect quota, consumption, parentage or period bounds.
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
	a.Dirty = true
	if parent.Present {
		parent.Value.Dirty = true
	}

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

// walletMutate locks a category tree, finds a wallet by owner, and runs a wallet-level mutation.
//
// Use this for public operations that need exclusive access to one wallet and may also inspect or mutate nearby tree
// state. The callback receives the locked tree so related allocations can be updated through allocationMutate.
func walletMutate(category accapi.ProductCategoryIdV2, owner accapi.WalletOwner, fn func(tree *AccountingTree, wallet *Wallet)) {
	treeMutate(category, func(tree *AccountingTree) *util.HttpError {
		walletMutateEx(tree, owner, func(wallet *Wallet) {
			fn(tree, wallet)
		})
		return nil
	})
}

// walletMutateEx runs a wallet mutation when the caller already holds the tree lock.
//
// It marks the wallet dirty and verifies that wallet.Consumed still equals the sum of ConsumedSelf across the wallet's
// allocations. Missing wallets are ignored for historical callers; new public entry points should generally validate the
// wallet before reaching this helper.
func walletMutateEx(tree *AccountingTree, owner accapi.WalletOwner, fn func(wallet *Wallet)) {
	ref := owner.Reference()
	wallet, ok := tree.WalletsByOwner[ref]
	if !ok {
		return
	}

	fn(wallet)
	wallet.Dirty = true
	walletAssertConsumptionMatchesAllocations(tree, wallet)
}

// walletAssertConsumptionMatchesAllocations checks the wallet consumption accounting invariant.
//
// wallet.Consumed is the descriptive total reported for the wallet, while allocation.ConsumedSelf is the concrete
// distribution of that total over active or retired allocations. They must sum exactly after every wallet mutation.
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

// treeMutate locates a category tree and runs a mutation while holding its exclusive lock.
//
// This is the standard lock boundary for public low-level accounting operations. Callers should not hold another tree
// lock when entering this helper.
func treeMutate(category accapi.ProductCategoryIdV2, fn func(tree *AccountingTree) *util.HttpError) *util.HttpError {
	accGlobals.Mu.RLock()
	tree, ok := accGlobals.Trees[category]
	accGlobals.Mu.RUnlock()

	if !ok {
		cat, err := ProductCategoryRetrieve(rpc.ActorSystem, category.Name, category.Provider)
		if err == nil {
			log.Info("Looking up category. Found it.")
			CategoryEnsure(cat)
			log.Info("Ensured")
			return treeMutate(category, fn)
		}

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
	scope := usageReportScope(request)
	absoluteAmount, resolveErr := usageReportResolveAbsoluteUsage(now, request, scope)
	if resolveErr != nil {
		return false, resolveErr
	}

	PromiseReconcile(now, request.CategoryIdV2, request.Owner, util.OptValue(absoluteAmount))

	shouldShrinkReconcile := false
	walletMutate(request.CategoryIdV2, request.Owner, func(tree *AccountingTree, wallet *Wallet) {
		if tree.Category.FreeToUse {
			return
		}

		lifecycleScan(now, tree)

		if len(wallet.Allocations) == 0 {
			success, err = false, util.HttpErr(http.StatusBadRequest, "this owner does not have any such resources")
			return
		}

		previousConsumed := wallet.Consumed
		meaningfulShrink := tree.Policy.GrowthStep
		if meaningfulShrink <= 0 {
			meaningfulShrink = 1
		}
		shouldShrinkReconcile = tree.IsCapacityBased() && previousConsumed-absoluteAmount >= meaningfulShrink

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

		lifecycleCleanupRetiredAllocations(now, tree, retiredAllocations)

		walletReevaluateLock(now, tree, wallet)
		success = !wallet.Locked
	})

	if err == nil {
		if shouldShrinkReconcile {
			PromiseReconcile(now, request.CategoryIdV2, request.Owner, util.OptNone[int64]())
		} else {
			PromiseReconcile(now, request.CategoryIdV2, request.Owner, util.OptValue(absoluteAmount))
		}
	}
	return
}

// usageReportScope returns the mutable scoped usage counter for delta reports with a scope key.
//
// Scoped usage lets a provider report deltas for a sub-resource while UsageReport still mutates wallet-level absolute
// usage. A nil return means the request is unscoped and should be resolved directly from wallet.Consumed.
func usageReportScope(request accapi.ReportUsageRequest) *ScopedUsage {
	if !request.Description.Scope.Present {
		return nil
	}

	key := request.Owner.Reference() + "\n" + request.Description.Scope.Value
	accGlobals.Mu.Lock()
	defer accGlobals.Mu.Unlock()
	if accGlobals.Usage == nil {
		accGlobals.Usage = map[string]*ScopedUsage{}
	}
	scope := accGlobals.Usage[key]
	if scope == nil {
		scope = &ScopedUsage{Key: key, Dirty: true}
		accGlobals.Usage[key] = scope
	}
	return scope
}

// usageReportResolveAbsoluteUsage converts an absolute or delta usage request into the wallet total to record.
//
// For unscoped requests, deltas are relative to wallet.Consumed. For scoped requests, deltas are relative to the scoped
// counter but are still applied to wallet.Consumed. This helper validates that absolute wallet usage never becomes
// negative and performs the scoped counter update under its own lock.
func usageReportResolveAbsoluteUsage(now time.Time, request accapi.ReportUsageRequest, scope *ScopedUsage) (int64, *util.HttpError) {
	if !request.IsDeltaCharge && request.Usage < 0 {
		return 0, util.HttpErr(http.StatusForbidden, "absolute usage cannot be negative")
	}

	absoluteAmount := int64(0)
	err := treeMutate(request.CategoryIdV2, func(tree *AccountingTree) *util.HttpError {
		if tree.Category.FreeToUse {
			return nil
		}

		lifecycleScan(now, tree)
		wallet := tree.WalletsByOwner[request.Owner.Reference()]
		if wallet == nil {
			return util.HttpErr(http.StatusNotFound, "unknown wallet")
		}

		currentUsage := wallet.Consumed
		if scope != nil {
			scope.Mu.Lock()
			defer scope.Mu.Unlock()
			currentUsage = scope.Usage
		}

		delta := request.Usage
		if !request.IsDeltaCharge {
			delta = request.Usage - currentUsage
		}

		absoluteAmount = wallet.Consumed + delta
		if absoluteAmount < 0 {
			return util.HttpErr(http.StatusForbidden, "usage cannot become negative")
		}

		if scope != nil {
			scope.Usage = currentUsage + delta
			scope.Dirty = true
		}
		return nil
	})

	return absoluteAmount, err
}
