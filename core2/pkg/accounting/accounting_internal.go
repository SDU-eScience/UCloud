package accounting

import (
	"cmp"
	"fmt"
	"math"
	"math/big"
	"net/http"
	"os"
	"regexp"
	"slices"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
	"ucloud.dk/shared/pkg/util/mermaid"
)

// Internal accounting system
// =====================================================================================================================
// This file implements the internal parts of UCloud's accounting system which contain a graph of "wallets". The
// internal accounting system is invoked by the publicly facing API in `accounting.go`. The internal accounting system
// does not implement any authorization, this is done by the publicly facing API. The internal API is fully
// self-contained and does not directly depend on outside concepts making it independently testable.
//
// These are the core concepts of the accounting system:
//
// - Owners: Users and projects.
// - Wallets: One per owner *per product category*.
// - Allocations: Hierarchical grants of quotas. All allocations have a start & end date.
//
// At run-time the system keeps all state in memory. The core operations in the accounting system are as follows:
//
// 1. Allocate: Create a new allocation (root or sub).
// 2. ReportUsage: Normally invoked by a provider (through the public API). This reports usage to a single wallet.
// 3. Scan: Trigger a time-driven sweep that activates or retires allocations. Normally done automatically by the
//    public API.
//
// APIs in the internal API are prefixed by either "internal" or "lInternal". "internal" functions require the
// caller to have released all locks prior to calling. "lInternal" functions require the caller to have all appropriate
// locks already (invoked only by the internal API).
//
// All functions in the internal API accept the current time as a parameter, if time is relevant to the function. This
// time is expected to be sampled exactly once by the public API and used for all subsequent functions within the
// public API. In other words, it is expected that time _does not_ change during the invocation of a single public API
// function. Most public API functions have an ex(tended) function which accepts the current time as a parameter for
// testing purposes. Note that despite accepting time as a parameter, this does not imply that the system supports time
// travel. Time is assumed to always be monotonically increasing.

// Core types and globals
// ---------------------------------------------------------------------------------------------------------------------
// This section contains the core types and global data structure. From accGlobals, it is possible to reach all other
// parts of the accounting system.
//
// References in the internal accounting system are generally done through numeric IDs. These IDs are all integers, but
// are separate Go types to reduce chance of accidental misuse. New numeric IDs are generated through the XXXIdAcc
// atomics stored in the accGlobals. As this might imply, IDs are global to the system and are not namespaced by their
// bucket.
//
// From accGlobals, it is possible to reach a bucket. There is one bucket per product category in the system. All data
// relevant for a wallet graph is stored within a single bucket, there are _never_ links between buckets.
//
// ---------------------------------------------------------------------------------------------------------------------
// accGlobals & internalBucket REQUIRE A MUTEX FOR ANY READ OR WRITE OPERATION.
// MUTEX LOCK ORDER: globals > bucket > scopedUsage
//
// (Note that all other data structures are locked by the bucket.)
// ---------------------------------------------------------------------------------------------------------------------

// Accounting state has two distinct kinds of usage which must not be conflated:
//
//   - LocalUsage is the usage reported directly for one wallet. It is the external accounting fact and is not changed
//     by routing, allocation activation, or allocation retirement.
//   - TreeUsage and ChildrenUsage describe how much of that usage is currently routed through allocation groups. They
//     are internal flow bookkeeping and may change while LocalUsage remains unchanged.
//
// For a wallet in a valid stable state:
//
//   usage available at the node = LocalUsage + sum(ChildrenUsage)
//   propagated usage            = sum(TreeUsage for the wallet's parent groups)
//   excess usage                = usage available at the node - propagated usage
//
// Excess is deliberately not propagated to a parent which has not allocated capacity for it. It must remain visible
// through LocalUsage, lock state, and reporting, and must be routed again if suitable quota later becomes available.
// The Core does not store excess as a separate field; it is derived from the values above.
//
// Capacity and non-capacity categories differ at retirement:
//
//   - Capacity usage is a current gauge. Retiring an allocation removes its quota from the graph. Current usage stays
//     in LocalUsage and must move to another active allocation or become excess. Retired capacity quota must not keep
//     funding current flow.
//   - Non-capacity usage is lifetime consumption. Retirement preserves the portion committed to the allocation by
//     replacing its operational Quota with RetiredUsage. The retained quota supports the retained historic TreeUsage;
//     a later allocation adds new entitlement and does not reset LocalUsage.
//
// Allocation validity uses [Start, End): active at Start and no longer current at End. Active is historical state; the
// current contributing predicate also considers Retired and the category type. See the individual field comments below.

type accGrantId int
type accGroupId int
type AccWalletId int
type accOwnerId int
type accAllocId int

var accGlobals struct {
	// Mu protects every non-atomic field in accGlobals. Locks must be acquired in the order globals, bucket, scope.
	Mu sync.RWMutex

	// TestingEnabled selects testing behavior for background work and invariant checks. Configure it before concurrent
	// accounting work starts.
	TestingEnabled bool

	// OwnersByReference and OwnersById are two indexes of the same owner objects. Reference is the persisted username or
	// project ID. The legacy model does not retain owner type separately and currently infers it from Reference.
	OwnersByReference map[string]*internalOwner
	OwnersById        map[accOwnerId]*internalOwner

	// Usage contains absolute-report baselines for individual scopes. It is global because its legacy persisted key is
	// owner ID plus scope, rather than being stored in the category bucket. See scopedUsage for important limitations.
	Usage map[string]*scopedUsage

	// BucketsByCategory contains one independent wallet graph per provider/product-category pair.
	BucketsByCategory map[accapi.ProductCategoryIdV2]*internalBucket

	// OnPersistHandlers ties grant synchronization callbacks to the persistence transaction which commits allocations.
	OnPersistHandlers []internalOnPersistHandler

	// These are process-wide high-water marks. IDs are global, not bucket-local, and must never be reused. Atomics are
	// used so allocating an ID does not require accGlobals.Mu.
	OwnerIdAcc  atomic.Int64
	WalletIdAcc atomic.Int64
	GroupIdAcc  atomic.Int64
	AllocIdAcc  atomic.Int64
}

type internalOwner struct {
	Id        accOwnerId
	Reference string
	Dirty     bool
}

func (o *internalOwner) WalletOwner() accapi.WalletOwner {
	if projectRegex.MatchString(o.Reference) {
		return accapi.WalletOwnerProject(o.Reference)
	} else {
		return accapi.WalletOwnerUser(o.Reference)
	}
}

type internalBucket struct {
	Mu       sync.RWMutex
	Category accapi.ProductCategory

	// SignificantUpdateAt is the newest LastSignificantUpdate among wallets in this bucket. It is an index used to skip
	// buckets during provider-notification scans; it is not updated for every internal flow or dirty-state change.
	SignificantUpdateAt time.Time

	// WalletsById and WalletsByOwner are two indexes of the same wallet objects. A bucket contains at most one wallet for
	// each owner, and a wallet ID must occur in only one bucket.
	WalletsById    map[AccWalletId]*internalWallet
	WalletsByOwner map[accOwnerId]*internalWallet

	// AllocationsById owns every allocation referenced by groups in this bucket. Allocation IDs are process-wide.
	AllocationsById map[accAllocId]*internalAllocation

	// disableEvaluation is a recursion guard used while rebalance temporarily reverses and reapplies usage. It is only
	// valid while Mu is held and must be restored before an operation completes.
	disableEvaluation bool
}

func (b *internalBucket) IsCapacityBased() bool { // does not require any mutex
	if b.Category.ProductType == accapi.ProductTypeInference {
		return false
	}

	switch b.Category.AccountingFrequency {
	case accapi.AccountingFrequencyOnce:
		return true
	default:
		return false
	}
}

type internalWallet struct {
	Id      AccWalletId
	OwnedBy accOwnerId

	// LocalUsage is usage reported directly for this owner and category. It excludes usage received from children and is
	// independent of how usage is routed to parents. For capacity categories it is a current gauge and may decrease. For
	// non-capacity categories it is lifetime consumption and should only increase without an explicit correction.
	LocalUsage int64

	// AllocationsByParent contains this wallet's incoming quota, grouped by funding parent. The key is a real parent
	// wallet ID or internalGraphRoot for root allocations. All allocations from one parent share one internalGroup.
	AllocationsByParent map[AccWalletId]*internalGroup

	// ChildrenUsage is both the reverse child index and the flow received from each child. For child C, this value must
	// equal C.AllocationsByParent[this wallet].TreeUsage. A zero entry is still meaningful because it records topology.
	ChildrenUsage map[AccWalletId]int64

	// Dirty means LocalUsage, WasLocked, or other persisted wallet state must be written in the next persistence batch.
	Dirty bool

	// WasLocked caches whether the wallet had no additional usable quota after the latest completed accounting operation.
	// In stable state it is equivalent to MaxUsable <= 0. It drives externally visible state-change notifications.
	WasLocked bool

	// LastSignificantUpdate is the last externally relevant wallet transition, such as lock state or allocation changes.
	// Rebalancing or reevaluating a wallet does not by itself make the update significant.
	LastSignificantUpdate time.Time
}

type internalGroup struct {
	Id               accGroupId
	AssociatedWallet AccWalletId
	ParentWallet     AccWalletId

	// TreeUsage is the recipient's usage currently charged through this parent. It is the reverse residual capacity of
	// the group's graph edge; contributing quota minus TreeUsage is its forward residual capacity. The parent mirror is
	// ParentWallet.ChildrenUsage[AssociatedWallet]. Unsupported usage belongs in derived excess, not stable TreeUsage.
	TreeUsage int64

	// Allocations is membership only and includes future, active, and retired allocations. Lifecycle state is stored on
	// each internalAllocation; the map value intentionally carries no active flag.
	Allocations map[accAllocId]util.Empty

	// Dirty means group endpoints or TreeUsage must be included in the next persistence batch.
	Dirty bool
}

type internalAllocation struct {
	Id        accAllocId
	BelongsTo AccWalletId
	Parent    AccWalletId
	Group     accGroupId

	// GrantedIn links grant-awarded allocations to the logical grant commit. Root/manual allocations have no grant ID.
	GrantedIn util.Option[accGrantId]

	// Quota is the amount this allocation contributes while current. Capacity retirement leaves the original value here
	// but excludes the allocation from contributing quota. Non-capacity retirement replaces it with RetiredUsage so the
	// allocation retains exactly enough operational quota to support its committed historic flow.
	Quota int64

	// Start and End define the allocation's [Start, End) validity interval.
	Start time.Time
	End   time.Time

	// Retired records that the end transition has run. A retired allocation is never currently available for new usage.
	Retired bool

	// RetiredUsage is the portion of group flow attributed to this allocation when it retired. For non-capacity products
	// it remains committed lifetime usage and becomes the retained operational Quota. For capacity products it is
	// historical metadata only and must not continue funding current usage.
	RetiredUsage int64

	// RetiredQuota preserves the allocation's quota immediately before retirement for history and reporting. It is not
	// contributing quota.
	RetiredQuota int64

	// Active means activation has happened at least once. It is historical and intentionally remains true after
	// retirement. Current availability is Active && !Retired. Category-specific contributing rules additionally decide
	// whether retired non-capacity quota supports existing flow.
	Active bool

	// Dirty means allocation state must be considered by the next persistence batch.
	Dirty bool

	// Committed controls logical visibility of a grant allocation. Uncommitted grant allocations must not become usable
	// or independently persistable before the grant synchronization marker is committed. Loaded allocations are committed.
	Committed bool
}

type scopedUsage struct {
	Mu    sync.RWMutex
	Key   string
	Usage int64
	Dirty bool
}

// Public to internal adapter
// ---------------------------------------------------------------------------------------------------------------------
// These APIs are intended as entry points for the public API and cover the core concepts as mentioned in the
// introduction at the top of this file.
//
// The core APIs are (see the introduction for more info):
//
// - internalReportUsage
// - internalAllocateNoCommit
// - internalScanAllocations

// internalReportUsage performs a report to a target wallet. The function always returns
// successfully. If the target wallet becomes locked, then this information can be read back from the target wallet.
// As always, locking a wallet is considered a significant update and thus triggers a message to relevant service
// providers.
func internalReportUsage(now time.Time, request accapi.ReportUsageRequest) (bool, *util.HttpError) {
	accGlobals.Mu.RLock()
	b, ok := accGlobals.BucketsByCategory[request.CategoryIdV2]
	accGlobals.Mu.RUnlock()

	if !ok {
		return false, util.HttpErr(http.StatusNotFound, "unknown category")
	}

	reference := request.Owner.Reference()
	if reference == "" {
		return false, util.HttpErr(http.StatusNotFound, "invalid owner specified")
	}

	accGlobals.Mu.RLock()
	owner := accGlobals.OwnersByReference[reference]
	accGlobals.Mu.RUnlock()
	if owner == nil && request.IsDeltaCharge && request.Usage < 0 {
		return false, util.HttpErr(http.StatusBadRequest, "usage report cannot make usage negative")
	}
	if owner == nil {
		owner = internalOwnerByReference(reference)
	}
	if request.IsDeltaCharge && request.Usage < 0 {
		b.Mu.RLock()
		wallet := b.WalletsByOwner[owner.Id]
		b.Mu.RUnlock()
		if wallet == nil {
			return false, util.HttpErr(http.StatusBadRequest, "usage report cannot make usage negative")
		}
	}

	var scope *scopedUsage
	if request.Description.Scope.Present {
		scopeKey := fmt.Sprintf("%d\n%s", owner.Id, request.Description.Scope.Value)
		accGlobals.Mu.RLock()
		scope = accGlobals.Usage[scopeKey]
		accGlobals.Mu.RUnlock()
		if scope == nil && request.IsDeltaCharge && request.Usage < 0 {
			return false, util.HttpErr(http.StatusBadRequest, "usage report cannot make usage negative")
		}

		if scope == nil {
			scope = util.ReadOrInsertBucket(&accGlobals.Mu, accGlobals.Usage, scopeKey, func() *scopedUsage {
				return &scopedUsage{
					Key:   scopeKey,
					Usage: 0,
					Dirty: true,
				}
			})
		}
	}

	b.Mu.Lock()
	defer b.Mu.Unlock()
	if topologyErrors := lValidateAccountingAcyclic(b); len(topologyErrors) > 0 {
		log.Error("Rejecting usage report for malformed accounting topology in %s/%s: %v", b.Category.Provider, b.Category.Name, topologyErrors[0])
		return false, util.HttpErr(http.StatusInternalServerError, "accounting topology is invalid")
	}

	if scope != nil {
		scope.Mu.Lock()
		defer scope.Mu.Unlock()
	}

	w := lInternalWalletByOwner(b, now, owner.Id)
	if err := lValidateAccountingGraphState(b, w); err != nil {
		log.Error("Rejecting usage report for invalid accounting flow in %s/%s: %v", b.Category.Provider, b.Category.Name, err)
		return false, util.HttpErr(http.StatusInternalServerError, "accounting flow state is invalid")
	}
	var visitedWallets map[AccWalletId]bool
	var delta int64
	accepted := false
	localUsageBefore := w.LocalUsage
	scopeUsageBefore := int64(0)
	if scope != nil {
		scopeUsageBefore = scope.Usage
	}
	defer func() {
		if !accepted {
			return
		}
		var invariantErrors []error
		if !request.IsDeltaCharge {
			absoluteUsage := w.LocalUsage
			if scope != nil {
				absoluteUsage = scope.Usage
			}
			if absoluteUsage != request.Usage {
				invariantErrors = append(invariantErrors, fmt.Errorf("absolute usage report requested %d but applicable usage is %d", request.Usage, absoluteUsage))
			}
		}
		expectedLocalUsage, overflow := checkedAccountingAdd(localUsageBefore, delta)
		if overflow {
			invariantErrors = append(invariantErrors, fmt.Errorf("accepted usage delta %d overflows wallet %d local usage %d", delta, w.Id, localUsageBefore))
		} else if w.LocalUsage != expectedLocalUsage {
			invariantErrors = append(invariantErrors, fmt.Errorf("wallet %d local usage changed from %d to %d for accepted delta %d", w.Id, localUsageBefore, w.LocalUsage, delta))
		}
		if scope != nil {
			expectedScopeUsage, scopeOverflow := checkedAccountingAdd(scopeUsageBefore, delta)
			if scopeOverflow {
				invariantErrors = append(invariantErrors, fmt.Errorf("accepted usage delta %d overflows scoped usage %q value %d", delta, scope.Key, scopeUsageBefore))
			} else if scope.Usage != expectedScopeUsage {
				invariantErrors = append(invariantErrors, fmt.Errorf("scoped usage %q changed from %d to %d for accepted delta %d", scope.Key, scopeUsageBefore, scope.Usage, delta))
			}
		}
		lCheckAccountingOperation("report usage", b, now, visitedWallets, scope, invariantErrors...)
	}()

	var currentUsage int64
	if scope == nil {
		currentUsage = w.LocalUsage
	} else {
		currentUsage = scope.Usage
	}

	if request.IsDeltaCharge {
		delta = request.Usage
	} else {
		delta = request.Usage - currentUsage
	}
	applicableUsage, overflow := checkedAccountingAdd(currentUsage, delta)
	localUsage, localOverflow := checkedAccountingAdd(w.LocalUsage, delta)
	if overflow || localOverflow {
		return false, util.HttpErr(http.StatusBadRequest, "usage report exceeds the supported numeric range")
	}
	if applicableUsage < 0 || localUsage < 0 {
		return false, util.HttpErr(http.StatusBadRequest, "usage report cannot make usage negative")
	}
	if !b.IsCapacityBased() && delta < 0 {
		return false, util.HttpErr(http.StatusBadRequest, "non-capacity usage cannot decrease")
	}
	accepted = true
	lInternalTransitionWallets(b, now, false, w.Id)

	if scope != nil {
		scope.Usage = currentUsage + delta
		scope.Dirty = true
	}

	deltaToReport := delta
	if delta < 0 {
		// Check if there is local excess which should counter-act the decrease
		propagated := lInternalWalletTotalPropagatedUsage(b, w)
		inNode := lInternalWalletTotalUsageInNode(b, w)
		excess := min(inNode-propagated, w.LocalUsage)

		if excess > 0 {
			deltaToReport = min(0, deltaToReport+excess)
		}
	}

	_, visitedWallets = lInternalReportUsage(b, now, w, deltaToReport)
	w.LocalUsage += delta
	w.Dirty = true
	if delta < 0 {
		for walletId := range visitedWallets {
			wallet := b.WalletsById[walletId]
			if wallet == nil {
				continue
			}
			for changedId := range lInternalReflowExcess(b, now, wallet) {
				visitedWallets[changedId] = true
			}
		}
	}

	lInternalReevaluateAffected(b, now, visitedWallets)

	return !w.WasLocked, nil
}

// If grantedIn is specified, then the allocations will not be committed to the database before
// internalCommitGrantAllocations is invoked with the same ID.
func internalAllocateNoCommit(
	now time.Time,
	b *internalBucket,
	start time.Time,
	end time.Time,
	quota int64,
	recipient AccWalletId,
	parent AccWalletId,
	grantedIn util.Option[accGrantId],
) (accAllocId, *util.HttpError) {
	if start.After(end) {
		return 0, util.HttpErr(http.StatusBadRequest, "start must occur before the end of an allocation!")
	} else if quota < 0 {
		return 0, util.HttpErr(http.StatusBadRequest, "quota must not be negative")
	} else if recipient == parent {
		return 0, util.HttpErr(http.StatusBadRequest, "cannot allocate to yourself")
	} else {
		// TODO check that if an allocation is root, then all allocations in the wallet are root

		// NOTE(Dan): Request at this point is semantically valid and was authorized via the public API.
		// The call is expected to succeed from this point.

		b.Mu.Lock()
		defer b.Mu.Unlock()

		recipientWallet := b.WalletsById[recipient]
		var parentWallet *internalWallet
		if parent != internalGraphRoot {
			parentWallet = b.WalletsById[parent]
		}
		lInternalTransitionWallets(b, now, false, recipient, parent)
		if lInternalAllocationWouldCreateCycle(b, recipient, parent) {
			return 0, util.HttpErr(http.StatusBadRequest, "allocation would create a cycle")
		}

		allocationId := accAllocId(accGlobals.AllocIdAcc.Add(1))
		allocation := &internalAllocation{
			Id:           allocationId,
			BelongsTo:    recipient,
			Parent:       parent,
			GrantedIn:    grantedIn,
			Quota:        quota,
			Start:        start,
			End:          end,
			Retired:      false,
			RetiredUsage: 0,
			Active:       false,
			Dirty:        true,
			Committed:    false,
		}

		b.AllocationsById[allocationId] = allocation

		group := util.LReadOrInsertBucket(recipientWallet.AllocationsByParent, parent, func() *internalGroup {
			return &internalGroup{
				Id:               accGroupId(accGlobals.GroupIdAcc.Add(1)),
				AssociatedWallet: recipient,
				ParentWallet:     parent,
				TreeUsage:        0,
				Allocations:      map[accAllocId]util.Empty{},
				Dirty:            true,
			}
		})

		group.Dirty = true
		group.Allocations[allocationId] = util.Empty{}

		allocation.Group = group.Id

		if parentWallet != nil {
			parentWallet.Dirty = true

			// NOTE(Dan): Insert a childrenUsage entry if we don't already have one. This is required to make
			// childrenUsage a valid tool for looking up children in the parent wallet.
			currentUsage, _ := parentWallet.ChildrenUsage[recipient]
			parentWallet.ChildrenUsage[recipient] = currentUsage
		}

		lInternalAttemptActivation(b, now, allocation, false)
		lInternalAttemptRetirement(b, now, allocation, false)
		lCheckAccountingOperation("allocate", b, now, map[AccWalletId]bool{recipient: true, parent: parent != internalGraphRoot}, nil)
		return allocationId, nil
	}
}

func lInternalAllocationWouldCreateCycle(b *internalBucket, recipient, parent AccWalletId) bool {
	if parent == internalGraphRoot {
		return false
	}
	visited := map[AccWalletId]bool{}
	queue := []AccWalletId{parent}
	for len(queue) > 0 {
		walletId := queue[0]
		queue = queue[1:]
		if walletId == recipient {
			return true
		}
		if visited[walletId] {
			continue
		}
		visited[walletId] = true
		if wallet := b.WalletsById[walletId]; wallet != nil {
			for parentId := range wallet.AllocationsByParent {
				if parentId != internalGraphRoot {
					queue = append(queue, parentId)
				}
			}
		}
	}
	return false
}

type internalOnPersistHandler struct {
	// GrantId identifies the set of uncommitted allocations which must become visible with this callback.
	GrantId accGrantId

	// OnPersist runs in the same database transaction which persists the grant allocations. It writes the external
	// synchronization marker which makes the logical grant commit atomic to readers.
	OnPersist func(tx *db.Transaction)
}

// internalCommitGrantAllocations ensures that all allocations granted in grantId are committed together. If onPersist is
// specified, then it will be run when the data is persisted.
func internalCommitGrantAllocations(grantId accGrantId, onPersist func(tx *db.Transaction)) {
	accGlobals.Mu.Lock()
	accGlobals.OnPersistHandlers = append(accGlobals.OnPersistHandlers, internalOnPersistHandler{
		GrantId:   grantId,
		OnPersist: onPersist,
	})
	accGlobals.Mu.Unlock()
}

func internalCommitAllocation(b *internalBucket, now time.Time, allocId accAllocId) {
	b.Mu.Lock()
	alloc, ok := b.AllocationsById[allocId]
	if ok {
		alloc.Committed = true
		lInternalAttemptActivation(b, now, alloc, false)
		lInternalAttemptRetirement(b, now, alloc, false)
		lCheckAccountingOperation("commit allocation", b, now, map[AccWalletId]bool{alloc.BelongsTo: true, alloc.Parent: alloc.Parent != internalGraphRoot}, nil)
	}
	b.Mu.Unlock()
}

func lValidateUpdate(now time.Time, alloc *internalAllocation, newQuota util.Option[int64], proposedNewStart time.Time, proposedNewEnd time.Time) (bool, *util.HttpError) {
	if alloc.Retired {
		return false, util.HttpErr(http.StatusForbidden, "You cannot update a retired allocation, it has already expired!")
	}
	if alloc.Start.Before(now) && !alloc.Start.Equal(proposedNewStart) {
		return false, util.HttpErr(http.StatusForbidden, "You cannot change the starting time of an allocation which has already started")
	}
	if proposedNewStart.After(proposedNewEnd) {
		return false, util.HttpErr(http.StatusForbidden, "This update would make the allocation invalid. An allocation cannot start after it has ended.")
	}
	if newQuota.Present && newQuota.Value < 0 {
		return false, util.HttpErr(http.StatusForbidden, "You cannot set a negative quota for an allocation (%d)", newQuota.Value)
	}
	return true, nil
}

// internalUpdateAllocation Updates an allocation and returns the grantId and the changelog. This can be used
// to notify the wallet owner about changes by commenting on the related grant if possible.
func internalUpdateAllocation(
	parentOwner *internalOwner,
	now time.Time,
	b *internalBucket,
	allocationId accAllocId,
	newQuota util.Option[int64],
	newStart util.Option[fndapi.Timestamp],
	newEnd util.Option[fndapi.Timestamp],
	reason string,
) (accGrantId, string, *util.HttpError) {
	var iAlloc *internalAllocation
	var iWallet *internalWallet
	var iParent *internalWallet
	grantedIn := accGrantId(0)
	changelog := ""

	b.Mu.Lock()

	iAlloc = b.AllocationsById[allocationId]
	if iAlloc == nil || iAlloc.Parent == internalGraphRoot {
		b.Mu.Unlock()
		return grantedIn, changelog, util.HttpErr(http.StatusNotFound, "Unknown allocation or bad Parent")
	}
	iWallet = b.WalletsById[iAlloc.BelongsTo]
	iParent = b.WalletsById[iAlloc.Parent]

	// Check if you have granted the allocation
	if iParent == nil || iParent.OwnedBy != parentOwner.Id {
		b.Mu.Unlock()
		return grantedIn, changelog, util.HttpErr(http.StatusForbidden, "You are not allowed to modify this allocation")
	}
	lInternalTransitionWallets(b, now, false, iWallet.Id, iParent.Id)

	proposedNewStart := iAlloc.Start
	if newStart.Present {
		proposedNewStart = newStart.Value.Time()
	}
	proposedNewEnd := iAlloc.End
	if newEnd.Present {
		proposedNewEnd = newEnd.Value.Time()
	}

	proposedNewQuota := iAlloc.Quota
	if newQuota.Present {
		iAllocGroup, _ := iWallet.AllocationsByParent[iAlloc.Parent]
		proposedNewQuota = newQuota.Value
		delta := newQuota.Value - iAlloc.Quota
		activeQuota := lInternalGroupTotalQuotaContributing(b, iAllocGroup)
		activeUsage := iAllocGroup.TreeUsage

		proposedActiveQuota, overflow := checkedAccountingAdd(activeQuota, delta)
		if overflow {
			b.Mu.Unlock()
			return grantedIn, changelog, util.HttpErr(http.StatusForbidden, "The quota update exceeds the supported numeric range")
		}
		if iAlloc.Committed && iAlloc.Active && !iAlloc.Retired && proposedActiveQuota < activeUsage {
			b.Mu.Unlock()
			return grantedIn, changelog, util.HttpErr(http.StatusForbidden, "You cannot decrease the quota below the current usage!")
		}
	}

	valid, errorResponse := lValidateUpdate(now, iAlloc, newQuota, proposedNewStart, proposedNewEnd)
	if !valid {
		b.Mu.Unlock()
		return grantedIn, changelog, errorResponse
	}

	iAlloc.Dirty = true
	iAlloc.Start = proposedNewStart
	iAlloc.End = proposedNewEnd
	iAlloc.Quota = proposedNewQuota

	lInternalAttemptActivation(b, now, iAlloc, true)
	lInternalAttemptRetirement(b, now, iAlloc, true)
	lInternalReevaluate(b, now, iWallet, true)
	lInternalMarkSignificantUpdate(b, now, iWallet)
	lCheckAccountingOperation("update allocation", b, now, map[AccWalletId]bool{iWallet.Id: true, iParent.Id: true}, nil)

	category := b.Category
	if iAlloc.GrantedIn.Present {
		grantedIn = iAlloc.GrantedIn.Value
	}

	b.Mu.Unlock()

	if grantedIn != accGrantId(0) {
		if newQuota.Present {
			amount := int64(0)
			// Converting to readable format instead of raw format
			switch category.AccountingFrequency {
			case accapi.AccountingFrequencyOnce:
				amount = proposedNewQuota
			case accapi.AccountingFrequencyPeriodicMinute:
				amount = proposedNewQuota / 60
			case accapi.AccountingFrequencyPeriodicHour:
				amount = proposedNewQuota
			case accapi.AccountingFrequencyPeriodicDay:
				amount = proposedNewQuota * 24
			default:
				log.Warn("Invalid accounting frequency passed: '%v'\n", category.AccountingFrequency)
			}
			changelog += fmt.Sprintf("The quota for %s (%s) has manually been updated to %d.\n", category.Name, category.Provider, amount)
		}
		if newStart.Present {
			changelog += fmt.Sprintf("The start date for the granted %s (%s) allocation has manually been updated to %s.\n", category.Name, category.Provider, proposedNewStart.String())
		}
		if newEnd.Present {
			changelog += fmt.Sprintf("The end date for the granted %s (%s) allocation has manually been updated to %s.\n", category.Name, category.Provider, proposedNewEnd.String())
		}
		changelog += fmt.Sprintf("Reason: %s", reason)
	}
	return grantedIn, changelog, nil
}

func internalCompleteScan(now time.Time, persistence func(buckets []*internalBucket, scopes []*scopedUsage, onPersistHandlers []internalOnPersistHandler)) {
	var buckets []*internalBucket
	var scopes []*scopedUsage
	accGlobals.Mu.Lock()
	for _, b := range accGlobals.BucketsByCategory {
		buckets = append(buckets, b)
	}

	for _, s := range accGlobals.Usage {
		scopes = append(scopes, s)
	}

	slices.SortFunc(buckets, func(a, b *internalBucket) int {
		if a.Category.Provider < b.Category.Provider {
			return -1
		} else if a.Category.Provider > b.Category.Provider {
			return 1
		} else if a.Category.Name < b.Category.Name {
			return -1
		} else if a.Category.Name > b.Category.Name {
			return 1
		} else {
			return 0
		}
	})

	slices.SortFunc(scopes, func(a, b *scopedUsage) int {
		if a.Key < b.Key {
			return -1
		} else if a.Key > b.Key {
			return 1
		} else {
			return 0
		}
	})

	for _, b := range buckets {
		b.Mu.Lock()
	}

	for _, s := range scopes {
		s.Mu.Lock()
	}

	for _, b := range buckets {
		lInternalScanAllocations(b, now)
		lCheckAccountingOperation("complete scan", b, now, nil, nil)
	}

	if persistence != nil {
		persistence(buckets, scopes, accGlobals.OnPersistHandlers)
	}
	for _, b := range buckets {
		lCheckAccountingOperation("persist accounting state", b, now, nil, nil)
	}

	for _, s := range scopes {
		s.Mu.Unlock()
	}

	for _, b := range buckets {
		b.Mu.Unlock()
	}

	accGlobals.Mu.Unlock()
}

func internalScanAllocations(b *internalBucket, now time.Time) {
	b.Mu.Lock()
	defer b.Mu.Unlock()
	lInternalScanAllocations(b, now)
	lCheckAccountingOperation("scan allocations", b, now, nil, nil)
}

// Entity lookup and initialization
// ---------------------------------------------------------------------------------------------------------------------
// Most data-structures in the accounting system are lazily initialized the first time they are requested. In is the
// responsibility of the caller (i.e. the public API) to ensure requests are only made to resources that exist in the
// rest of UCloud. Users, projects and product references are _NOT_ checked in the internal API.
//
// Lookup functions exist for each type of entity that exist in the system.

func internalBucketOrInit(category accapi.ProductCategory) *internalBucket {
	id := accapi.ProductCategoryIdV2{
		Name:     category.Name,
		Provider: category.Provider,
	}

	return util.ReadOrInsertBucket(&accGlobals.Mu, accGlobals.BucketsByCategory, id, func() *internalBucket {
		return &internalBucket{
			Mu:              sync.RWMutex{},
			Category:        category,
			WalletsById:     map[AccWalletId]*internalWallet{},
			WalletsByOwner:  map[accOwnerId]*internalWallet{},
			AllocationsById: map[accAllocId]*internalAllocation{},
		}
	})
}

func internalWalletByAllocationId(id accAllocId) (*internalBucket, *internalWallet, bool) {
	if id == 0 {
		return nil, nil, false
	}
	var resultBucket *internalBucket
	var resultAllocation *internalAllocation
	var resultWallet *internalWallet
	ok := false

	accGlobals.Mu.RLock()
	for _, b := range accGlobals.BucketsByCategory {
		b.Mu.RLock()
		resultAllocation, ok = b.AllocationsById[id]
		if ok {
			resultWallet, ok = b.WalletsById[resultAllocation.BelongsTo]
		}
		b.Mu.RUnlock()
		if ok {
			resultBucket = b
			break
		}
	}
	accGlobals.Mu.RUnlock()
	return resultBucket, resultWallet, ok
}

func internalWalletById(id AccWalletId) (*internalBucket, *internalWallet, bool) {
	if id == 0 {
		return nil, nil, false
	}

	var resultBucket *internalBucket
	var resultWallet *internalWallet
	ok := false

	accGlobals.Mu.RLock()
	for _, b := range accGlobals.BucketsByCategory {
		b.Mu.RLock()
		resultWallet, ok = b.WalletsById[id]
		b.Mu.RUnlock()

		if ok {
			resultBucket = b
			break
		}
	}
	accGlobals.Mu.RUnlock()

	return resultBucket, resultWallet, ok
}

func internalOwnerByReference(reference string) *internalOwner {
	if reference == "" {
		log.Fatal("internalOwnerByReference called with an empty reference")
	}

	return util.ReadOrInsertBucket(&accGlobals.Mu, accGlobals.OwnersByReference, reference, func() *internalOwner {
		ow := &internalOwner{
			Id:        accOwnerId(accGlobals.OwnerIdAcc.Add(1)),
			Reference: reference,
			Dirty:     true,
		}

		accGlobals.OwnersById[ow.Id] = ow
		return ow
	})
}

func internalWalletByOwner(b *internalBucket, now time.Time, owner accOwnerId) AccWalletId {
	b.Mu.Lock()
	defer b.Mu.Unlock()
	return lInternalWalletByOwner(b, now, owner).Id
}

func internalWalletByReferenceAndCategory(now time.Time, reference string, category accapi.ProductCategoryIdV2) (AccWalletId, bool) {
	if reference == "" {
		return 0, false
	}

	owner := internalOwnerByReference(reference)
	cat, err := ProductCategoryRetrieve(rpc.ActorSystem, category.Name, category.Provider)
	if err != nil {
		return 0, false
	} else {
		b := internalBucketOrInit(cat)
		w := internalWalletByOwner(b, now, owner.Id)
		return w, true
	}
}

func lInternalWalletByOwner(b *internalBucket, now time.Time, owner accOwnerId) *internalWallet {
	return util.LReadOrInsertBucket(b.WalletsByOwner, owner, func() *internalWallet {
		result := &internalWallet{
			Id:                    AccWalletId(accGlobals.WalletIdAcc.Add(1)),
			OwnedBy:               owner,
			LocalUsage:            0,
			AllocationsByParent:   map[AccWalletId]*internalGroup{},
			ChildrenUsage:         map[AccWalletId]int64{},
			Dirty:                 true,
			WasLocked:             true,
			LastSignificantUpdate: now,
		}

		b.WalletsById[result.Id] = result
		return result
	})
}

// Usage reporting and wallet re-balancing
// ---------------------------------------------------------------------------------------------------------------------
// This section contains the 'meat' of the accounting system. It is responsible for modifying the system state in
// response to a charge (usage report).
//
// The following description is a bit more theoretical that most of the other source code, but here goes.
//
// When a charge arrives, the bucket turns the wallet tree into a min-cost residual graph where:
//
// - capacity edges are the unused quota
// - costs prefer less imbalanced allocations
// - a *very* expensive escape edge models over-consumption
//
// Edmonds-Karp gives *max flow* (how much of the charge can propagate) and the resulting residual capacities
// become the new usage.
//
// The graph is constructed from the other core concepts mentioned in this file. The mapping is as follows:
//
// - Bucket -> Graph
// - Wallet -> Node
// - Allocation group -> Edges between nodes
//
// The existing flow on the edges comes from the LocalUsage of a wallet and the TreeUsage of a group. The capacity of
// an edge is constructed from the active quota of a group.
//
// The graph algorithms themselves are implemented in `accounting_graph.go`.

func lInternalReportUsage(b *internalBucket, now time.Time, w *internalWallet, delta int64) (int64, map[AccWalletId]bool) {
	flags := internalGraphWithOverAllocation
	if delta < 0 {
		// The caller already removes the reporting wallet's local excess from a decrease. Ancestor escape flow is still
		// required so funded child flow can be reversed when an ancestor has no persisted upstream flow.
		flags |= internalGraphWithoutLeafOverAllocation
	}
	chargeGraph := lInternalBuildGraph(b, now, w, flags)

	rootVertex := chargeGraph.WalletToVertex[internalGraphRoot]
	walletVertex := chargeGraph.WalletToVertex[w.Id]

	maxUsable := int64(0)
	if delta < 0 {
		maxUsable = chargeGraph.MinCostFlow(walletVertex, rootVertex, -delta)
	} else {
		maxUsable = chargeGraph.MinCostFlow(rootVertex, walletVertex, delta)
	}

	walletsUpdated := map[AccWalletId]bool{}
	walletsUpdated[w.Id] = true

	if maxUsable != 0 {
		gSize := len(chargeGraph.VertexToWallet)

		for senderVertex := 0; senderVertex < gSize; senderVertex++ {
			for receiverVertex := 0; receiverVertex < gSize; receiverVertex++ {
				if chargeGraph.Original[receiverVertex][senderVertex] {
					senderWalletId := chargeGraph.VertexToWallet[senderVertex]
					senderWallet := b.WalletsById[senderWalletId]
					receiverWalletId := chargeGraph.VertexToWallet[receiverVertex]
					receiverWallet := b.WalletsById[receiverWalletId]
					amount := chargeGraph.Adjacent[senderVertex][receiverVertex]

					if senderWallet == nil {
						continue
					}
					group := senderWallet.AllocationsByParent[receiverWalletId]
					if group.TreeUsage == amount {
						continue
					}
					group.TreeUsage = amount
					group.Dirty = true
					walletsUpdated[senderWalletId] = true

					if receiverWallet != nil {
						receiverWallet.ChildrenUsage[senderWalletId] = amount
						receiverWallet.Dirty = true
						walletsUpdated[receiverWalletId] = true
					}
				}
			}
		}
	}

	return maxUsable, walletsUpdated
}

func lInternalBuildGraph(b *internalBucket, now time.Time, leaf *internalWallet, flags internalGraphFlag) *Graph {
	vertexToWallet := []AccWalletId{leaf.Id, internalGraphRoot}
	walletToVertex := map[AccWalletId]int{leaf.Id: 0, internalGraphRoot: 1}
	rootVertex := walletToVertex[internalGraphRoot]

	{
		// Discover and add all nodes (wallets) to the graph
		// -------------------------------------------------
		queue := []AccWalletId{leaf.Id}
		for len(queue) > 0 {
			wallet := b.WalletsById[queue[0]]
			queue = queue[1:]

			for parentId, _ := range wallet.AllocationsByParent {
				if _, added := walletToVertex[parentId]; !added {
					if parentId != internalGraphRoot {
						queue = append(queue, parentId)
					}

					walletToVertex[parentId] = len(vertexToWallet)
					vertexToWallet = append(vertexToWallet, parentId)
				}
			}
		}

		if len(vertexToWallet) != len(walletToVertex) {
			panic("assertion error")
		}
	}

	{
		// Add edges to the graph (allocation groups)
		// ------------------------------------------

		gSize := len(vertexToWallet)
		graphSize := gSize
		if flags&internalGraphWithOverAllocation != 0 {
			graphSize *= 2
		}

		vertexToOverAllocationRoot := func(vertex int) int {
			return vertex + gSize
		}

		g := NewGraph(graphSize)
		g.VertexToWallet = vertexToWallet
		g.WalletToVertex = walletToVertex

		for walletId, vertexIndex := range walletToVertex {
			if walletId == internalGraphRoot {
				continue
			}

			wallet := b.WalletsById[walletId]

			for parentId, allocationGroup := range wallet.AllocationsByParent {
				sourceVertex := walletToVertex[parentId]
				destinationVertex := vertexIndex

				activeQuota := lInternalGroupTotalQuotaContributing(b, allocationGroup)

				{
					// Capacity
					capacity := max(0, activeQuota-allocationGroup.TreeUsage)
					g.AddEdge(sourceVertex, destinationVertex, capacity, allocationGroup.TreeUsage)
					g.Original[sourceVertex][destinationVertex] = true
				}

				{
					// Cost
					// -------------------------------------------------------------------------------------------------
					// The cost variable is expected to be positive. The larger the number is, the more likely it is
					// that this edge will be chosen during charges.
					//
					// NOTE(Dan): The old code would have the cost be negative, but with identical semantics apart
					// from this. See also the negation we do at the end of this block.

					earliestExpiration := lInternalEarliestExpiration(b, allocationGroup)
					if !earliestExpiration.After(now) || activeQuota < allocationGroup.TreeUsage {
						g.AddEdgeCost(sourceVertex, destinationVertex, (&big.Int{}).Neg(internalGraphRetirementCost))
					} else {
						preferredBalance := lInternalGroupPreferredBalance(b, now, allocationGroup)
						balanceGap := (&big.Int{}).Sub(big.NewInt(preferredBalance), big.NewInt(allocationGroup.TreeUsage))
						if balanceGap.Sign() <= 0 {
							balanceGap.SetInt64(1)
						}

						// Balance is the primary ordering key. Expiration is a 64-bit secondary key where an earlier
						// expiration receives the larger score. Min-cost flow then prefers the negated larger score.
						score := (&big.Int{}).Lsh(balanceGap, 64)
						remainingMillis := max(int64(0), earliestExpiration.Sub(now).Milliseconds())
						expirationScore := (&big.Int{}).Sub(big.NewInt(math.MaxInt64), big.NewInt(remainingMillis))
						score.Add(score, expirationScore)
						g.AddEdgeCost(sourceVertex, destinationVertex, (&big.Int{}).Neg(score))
					}
				}

				if flags&internalGraphWithOverAllocation != 0 && !(flags&internalGraphWithoutLeafOverAllocation != 0 && walletId == leaf.Id) {
					// This block augments the graph with an over-allocation node which overconsumption will flow
					// through. This edge is purposefully made very expensive such that this path will only be chosen
					// for a flow if there are no other ways of using up the charge.

					totalAllocated := lInternalWalletTotalAllocatedContributing(b, wallet)
					activeQuota := lInternalWalletTotalQuotaContributing(b, wallet)

					totalWithLocal, totalOverflow := checkedAccountingAdd(totalAllocated, wallet.LocalUsage)
					overAllocation, subtractionOverflow := checkedAccountingSub(totalWithLocal, activeQuota)
					if totalOverflow || subtractionOverflow {
						log.Error("over-allocation arithmetic overflow in %s/%s for wallet %d", b.Category.Provider, b.Category.Name, wallet.Id)
						continue
					}
					if overAllocation > 0 {
						usageInNode := lInternalWalletTotalUsageInNode(b, wallet)
						propagatedUsage := lInternalWalletTotalPropagatedUsage(b, wallet)

						overAllocationUsed := usageInNode - propagatedUsage
						if overAllocationUsed < 0 || overAllocationUsed > overAllocation {
							log.Error("invalid over-allocation usage %d of %d in %s/%s for wallet %d", overAllocationUsed, overAllocation, b.Category.Provider, b.Category.Name, wallet.Id)
							continue
						}
						available := overAllocation - overAllocationUsed

						overAllocationNode := vertexToOverAllocationRoot(vertexIndex)

						// Add edge between root and the over-allocation node
						g.AddEdge(rootVertex, overAllocationNode, available, overAllocationUsed)
						g.AddEdgeCost(rootVertex, overAllocationNode, internalGraphOverAllocationEdgeCost)

						// Add edge between our wallet node and the over-allocation node
						g.AddEdge(overAllocationNode, vertexIndex, available, overAllocationUsed)
						g.AddEdgeCost(overAllocationNode, vertexIndex, internalGraphOverAllocationEdgeCost)
					}
				}
			}
		}

		return g
	}
}

func lInternalReflowExcess(b *internalBucket, now time.Time, wallet *internalWallet) map[AccWalletId]bool {
	changed := map[AccWalletId]bool{}
	lInternalReflowExcessEx(b, now, wallet, map[AccWalletId]util.Empty{}, changed)
	return changed
}

func lInternalReflowExcessEx(b *internalBucket, now time.Time, wallet *internalWallet, handled map[AccWalletId]util.Empty, changed map[AccWalletId]bool) {
	// Similar to the re-balance operation, except this function will only attempt to increase usage by looking at the
	// local excess which is not being propagated.
	//
	// The main purpose of this function is to ensure that if an overconsumption node was taken at some point, then
	// this "excess" usage will eventually be propagated back into the system once there is room for it.

	for childId, _ := range wallet.ChildrenUsage {
		_, hasHandled := handled[childId]
		if !hasHandled {
			handled[childId] = util.Empty{}

			child := b.WalletsById[childId]
			lInternalReflowExcessEx(b, now, child, handled, changed)
		}
	}

	if lInternalWalletTotalUsageInNode(b, wallet) > 0 {
		propagated := lInternalWalletTotalPropagatedUsage(b, wallet)
		inNode := lInternalWalletTotalUsageInNode(b, wallet)
		excess := inNode - propagated

		if excess > 0 {
			usable := lInternalMaxUsable(b, now, wallet)
			toReport := min(usable, excess)
			if toReport > 0 {
				_, updated := lInternalReportUsage(b, now, wallet, toReport)
				for walletId := range updated {
					changed[walletId] = true
				}
			}
		}
	}
}

func lInternalRebalance(b *internalBucket, now time.Time, wallet *internalWallet, deficit util.Option[int64]) map[AccWalletId]bool {
	changed := map[AccWalletId]bool{}
	lInternalRebalanceEx(b, now, wallet, deficit, map[AccWalletId]util.Empty{}, changed)
	return changed
}

func lInternalRebalanceEx(b *internalBucket, now time.Time, wallet *internalWallet, deficit util.Option[int64], handled map[AccWalletId]util.Empty, changed map[AccWalletId]bool) {
	// The main purpose of this function is to ensure that retired flows are eventually moved to an active flow. This
	// operation is _only_ done for capacity-based product categories. For time-based products, this is not done (and
	// must not be done). Instead, time-based product utilizes a different retirement mechanism which locks usage in
	// place. This is needed, because in time-based products utilization is ever-increasing. Unlike in capacity based
	// products where utilization can go up and down.

	recharge := func(amount int64) {
		if amount > 0 {
			b.disableEvaluation = true
			_, updated := lInternalReportUsage(b, now, wallet, -amount)
			b.disableEvaluation = false
			for walletId := range updated {
				changed[walletId] = true
			}
			_, updated = lInternalReportUsage(b, now, wallet, amount)
			for walletId := range updated {
				changed[walletId] = true
			}
		}
	}

	if b.IsCapacityBased() {
		if !deficit.Present {
			sum := int64(0)
			for _, g := range wallet.AllocationsByParent {
				activeQuota := lInternalGroupTotalQuotaContributing(b, g)
				propagated := g.TreeUsage

				if propagated > activeQuota {
					sum += propagated - activeQuota
				}
			}

			if sum > 0 {
				deficit.Set(sum)
			}
		}
		if deficit.Present {
			// NOTE(Dan): We should attempt to rebalance because we are propagating more than we have. We start by
			// attempting to rebalance each of our children.

			for childId, usage := range wallet.ChildrenUsage {
				_, hasHandled := handled[childId]
				if !hasHandled {
					handled[childId] = util.Empty{}
					child := b.WalletsById[childId]
					lInternalRebalanceEx(b, now, child, util.OptValue(min(usage, deficit.Value)), handled, changed)
				}
			}

			recharge(min(deficit.Value, wallet.LocalUsage))
		}
	}
}

// lInternalReevaluate will re-evaluate the state of a wallet following a significant change to its state. This will
// ensure that the lock flag is correctly set. If rebalance is true, then excess usage is reflown and retired balance
// is balanced to other parts of the graph.
func lInternalReevaluate(b *internalBucket, now time.Time, wallet *internalWallet, rebalance bool) {
	if b.disableEvaluation {
		return
	}

	// NOTE(Dan): rebalance cannot be done unconditionally since this will also be triggered by a normal report
	// which could lead to infinite recursion. The value should be true for all calls not coming from a usage report.
	changed := map[AccWalletId]bool{wallet.Id: true}
	if rebalance {
		for walletId := range lInternalReflowExcess(b, now, wallet) {
			changed[walletId] = true
		}
		for walletId := range lInternalRebalance(b, now, wallet, util.OptNone[int64]()) {
			changed[walletId] = true
		}
	}
	lInternalReevaluateAffected(b, now, changed)
}

func lInternalReevaluateAffected(b *internalBucket, now time.Time, seeds map[AccWalletId]bool) {
	visited := map[AccWalletId]util.Empty{}
	queue := make([]*internalWallet, 0, len(seeds))
	for walletId := range seeds {
		if wallet := b.WalletsById[walletId]; wallet != nil {
			queue = append(queue, wallet)
		}
	}
	for len(queue) > 0 {
		next := queue[0]
		queue = queue[1:]
		if _, alreadyVisited := visited[next.Id]; alreadyVisited {
			continue
		}

		visited[next.Id] = util.Empty{}

		maxUsable := lInternalMaxUsable(b, now, next)
		if maxUsable <= 0 && !next.WasLocked {
			next.WasLocked = true
			lInternalMarkSignificantUpdate(b, now, next)
		} else if maxUsable > 0 && next.WasLocked {
			next.WasLocked = false
			lInternalMarkSignificantUpdate(b, now, next)
		}

		for childId, _ := range next.ChildrenUsage {
			if _, hasVisited := visited[childId]; !hasVisited {
				child := b.WalletsById[childId]
				queue = append(queue, child)
			}
		}
	}
}

// Allocation life-cycle
// ---------------------------------------------------------------------------------------------------------------------
// The following function manage the activation and retirement of allocations. Activation sets the Active property of
// an allocation. This flag will then never be turned off. Retirement will turn on the Retired flag and will also turn
// on the Active flag (in the rare cases where retirement happens before activation).

func lInternalScanAllocations(b *internalBucket, now time.Time) {
	lInternalTransitionAllocations(b, now, true)
}

func lInternalTransitionAllocations(b *internalBucket, now time.Time, logTransitions bool) {
	allocations := make([]*internalAllocation, 0, len(b.AllocationsById))
	for _, allocation := range b.AllocationsById {
		allocations = append(allocations, allocation)
	}
	lInternalTransitionAllocationSet(b, now, logTransitions, allocations)
}

func lInternalTransitionWallets(b *internalBucket, now time.Time, logTransitions bool, walletIds ...AccWalletId) {
	visited := map[AccWalletId]bool{internalGraphRoot: true}
	allocationsById := map[accAllocId]*internalAllocation{}
	for len(walletIds) > 0 {
		walletId := walletIds[0]
		walletIds = walletIds[1:]
		if visited[walletId] {
			continue
		}
		visited[walletId] = true
		wallet := b.WalletsById[walletId]
		if wallet == nil {
			continue
		}
		for parentId, group := range wallet.AllocationsByParent {
			walletIds = append(walletIds, parentId)
			for allocationId := range group.Allocations {
				allocationsById[allocationId] = b.AllocationsById[allocationId]
			}
		}
	}

	allocations := make([]*internalAllocation, 0, len(allocationsById))
	for _, allocation := range allocationsById {
		allocations = append(allocations, allocation)
	}
	lInternalTransitionAllocationSet(b, now, logTransitions, allocations)
}

func lInternalTransitionAllocationSet(b *internalBucket, now time.Time, logTransitions bool, allocations []*internalAllocation) {
	slices.SortFunc(allocations, func(a, b *internalAllocation) int {
		if result := a.End.Compare(b.End); result != 0 {
			return result
		}
		if result := a.Start.Compare(b.Start); result != 0 {
			return result
		}
		return cmp.Compare(a.Id, b.Id)
	})

	for _, alloc := range allocations {
		lInternalAttemptActivation(b, now, alloc, logTransitions)
		lInternalAttemptRetirement(b, now, alloc, logTransitions)
	}
}

func lInternalAttemptActivation(b *internalBucket, now time.Time, alloc *internalAllocation, logActivation bool) {
	if alloc.Committed && !alloc.Active && !now.Before(alloc.Start) && now.Before(alloc.End) {
		wallet := b.WalletsById[alloc.BelongsTo]

		alloc.Active = true
		alloc.Dirty = true

		lInternalReevaluate(b, now, wallet, true)

		// NOTE(Dan): Always mark since reevaluate only marks if a wallet changes lock state
		lInternalMarkSignificantUpdate(b, now, wallet)

		if logActivation {
			log.Info("Activating allocation: %v", alloc.Id)
		}
	}
}

func lInternalAttemptRetirement(b *internalBucket, now time.Time, alloc *internalAllocation, logRetirement bool) {
	if alloc.Committed && !alloc.Retired && !now.Before(alloc.End) {
		wallet := b.WalletsById[alloc.BelongsTo]
		group := wallet.AllocationsByParent[alloc.Parent]

		// NOTE(Dan): This value will be wrong if the retired amount was ever reflown. This should obviously be avoided.
		groupRetired := lInternalGroupTotalRetired(b, group)
		toRetire := min(alloc.Quota, group.TreeUsage-groupRetired)
		toRetire = max(0, toRetire)

		alloc.Dirty = true
		alloc.RetiredUsage = toRetire
		alloc.RetiredQuota = alloc.Quota
		alloc.Retired = true
		alloc.Active = true // retired allocations are always active (even if it was never activated)

		if !b.IsCapacityBased() {
			// Non-capacity based (i.e. time-based) allocations will have their quota set to retired usage immediately
			// to prevent further use. The quota continues to count in the wallet.
			alloc.Quota = alloc.RetiredUsage
		} else {
			// In capacity based wallets, the quota immediately stops counting from the wallet once the allocation
			// retires. Thus, there is no further action required.
		}

		lInternalReevaluate(b, now, wallet, true)
		lInternalMarkSignificantUpdate(b, now, wallet)

		if logRetirement {
			log.Info("Retiring allocation: %v", alloc.Id)
		}
	}
}

// Wallet, allocation and group metrics
// ---------------------------------------------------------------------------------------------------------------------
// The following functions compute and measure various metrics that are needed for the internal functions of the
// accounting system. Some of these computed properties are also returned needed by the public API.

func internalWalletsUpdatedAfter(timestamp time.Time, providerId string) []AccWalletId {
	var buckets []*internalBucket
	var wallets []AccWalletId

	accGlobals.Mu.RLock()
	for cat, b := range accGlobals.BucketsByCategory {
		b.Mu.RLock()
		if cat.Provider == providerId {
			if b.SignificantUpdateAt.Equal(timestamp) || b.SignificantUpdateAt.After(timestamp) {
				buckets = append(buckets, b)
			}
		}
		b.Mu.RUnlock()
	}
	accGlobals.Mu.RUnlock()

	for _, b := range buckets {
		b.Mu.RLock()
		for wid, wallet := range b.WalletsById {
			wUpdate := wallet.LastSignificantUpdate
			if wUpdate.Equal(timestamp) || wUpdate.After(timestamp) {
				wallets = append(wallets, wid)
			}
		}
		b.Mu.RUnlock()
	}

	return wallets
}

func lInternalMarkSignificantUpdate(b *internalBucket, now time.Time, wallet *internalWallet) {
	b.SignificantUpdateAt = now
	wallet.LastSignificantUpdate = now
	wallet.Dirty = true
	if b.Category.Provider != "usagegen" {
		// TODO(Dan): If more than one million updates is ever made in a single lock-cycle, then this function will
		//   indefinitely stall the system. Please refactor the code, before the system reaches such a size.
		providerWalletNotifications <- wallet.Id
	}
}

func internalGetMermaidGraph(now time.Time, walletId AccWalletId) (string, bool) {
	b, _, ok := internalWalletById(walletId)
	if ok {
		accGlobals.Mu.RLock()
		b.Mu.RLock()
		graph := lInternalMermaidGraph(b, now, walletId)
		b.Mu.RUnlock()
		accGlobals.Mu.RUnlock()
		return graph, true
	}
	return "", false
}

func internalMaxUsable(now time.Time, wallet AccWalletId) (int64, bool) {
	b, w, ok := internalWalletById(wallet)
	if ok {
		b.Mu.RLock()
		result := lInternalMaxUsable(b, now, w)
		b.Mu.RUnlock()
		return result, true
	} else {
		return 0, false
	}
}

func lInternalMaxUsable(b *internalBucket, now time.Time, wallet *internalWallet) int64 {
	graph := lInternalBuildGraph(b, now, wallet, 0)
	rootIndex := graph.WalletToVertex[internalGraphRoot]
	return graph.MaxFlow(rootIndex, 0)
}

func lInternalWalletTotalAllocatedContributing(b *internalBucket, w *internalWallet) int64 {
	retiredAllocationsContribute := !b.IsCapacityBased()

	sum := int64(0)
	for childId, _ := range w.ChildrenUsage {
		child := b.WalletsById[childId]
		childGroup := child.AllocationsByParent[w.Id]

		for allocId, _ := range childGroup.Allocations {
			childAlloc := b.AllocationsById[allocId]
			if childAlloc.Committed && childAlloc.Active && (retiredAllocationsContribute || !childAlloc.Retired) {
				sum += childAlloc.Quota
			}
		}
	}
	return sum
}

// NOTE(Dan): Do NOT use for internal accounting operations. Use this ONLY for understanding the data.
func internalWalletTotalQuotaFromActiveAllocations(b *internalBucket, wId AccWalletId) (int64, bool) {
	b.Mu.RLock()
	owner, ok := b.WalletsById[wId]
	var result int64
	if ok {
		result = lInternalWalletTotalQuotaFromActiveAllocations(b, owner)
	}
	b.Mu.RUnlock()
	return result, ok
}

// NOTE(Dan): Do NOT use for internal accounting operations. Use this ONLY for understanding the data.
func lInternalWalletTotalQuotaFromActiveAllocations(b *internalBucket, w *internalWallet) int64 {
	sum := int64(0)
	for _, group := range w.AllocationsByParent {
		sum += lInternalGroupTotalQuotaFromActiveAllocations(b, group)
	}
	return sum
}

func lInternalGroupTotalUsageFromActiveAllocationsUiOnly(b *internalBucket, group *internalGroup) int64 {
	retiredUsage := int64(0)
	for allocId, _ := range group.Allocations {
		alloc := b.AllocationsById[allocId]
		if !b.IsCapacityBased() && alloc.Active && alloc.Retired {
			retiredUsage += alloc.RetiredUsage
		}
	}

	return group.TreeUsage - retiredUsage
}

func lInternalWalletTotalUsageFromActiveAllocationsUiOnly(b *internalBucket, w *internalWallet) int64 {
	usage := int64(0)
	for _, group := range w.AllocationsByParent {
		usage += lInternalGroupTotalUsageFromActiveAllocationsUiOnly(b, group)
	}

	return usage
}

// NOTE(Dan): Do NOT use for internal accounting operations. Use this ONLY for understanding the data.
func lInternalGroupTotalQuotaFromActiveAllocations(b *internalBucket, group *internalGroup) int64 {
	sum := int64(0)
	for allocId, _ := range group.Allocations {
		alloc := b.AllocationsById[allocId]
		if alloc.Active && !alloc.Retired {
			sum += alloc.Quota
		}
	}
	return sum
}

func internalWalletTotalQuotaContributing(b *internalBucket, wId AccWalletId) (int64, bool) {
	b.Mu.RLock()
	owner, ok := b.WalletsById[wId]
	var result int64
	if ok {
		result = lInternalWalletTotalQuotaContributing(b, owner)
	}
	b.Mu.RUnlock()
	return result, ok
}

func internalWalletTotalQuotaContributingAt(b *internalBucket, wId AccWalletId, at time.Time) (int64, bool) {
	b.Mu.RLock()
	owner, ok := b.WalletsById[wId]
	var result int64
	if ok {
		result = lInternalWalletTotalQuotaContributingAt(b, owner, at)
	}
	b.Mu.RUnlock()
	return result, ok
}

func lInternalWalletTotalQuotaContributing(b *internalBucket, w *internalWallet) int64 {
	sum := int64(0)
	for _, group := range w.AllocationsByParent {
		sum += lInternalGroupTotalQuotaContributing(b, group)
	}
	return sum
}

func lInternalWalletTotalQuotaContributingAt(b *internalBucket, w *internalWallet, at time.Time) int64 {
	sum := int64(0)
	for _, group := range w.AllocationsByParent {
		for allocId := range group.Allocations {
			alloc := b.AllocationsById[allocId]
			if alloc.Committed && !alloc.Retired && (alloc.Start.Before(at) || alloc.Start.Equal(at)) && at.Before(alloc.End) {
				sum += alloc.Quota
			}
		}
	}
	return sum
}

// lInternalWalletTotalUsageInNode returns the sum of the local usage and the usage which has propagated from children
// to this node. This value is guaranteed to be greater than or equal to lInternalWalletTotalPropagatedUsage.
func lInternalWalletTotalUsageInNode(b *internalBucket, w *internalWallet) int64 {
	sum := w.LocalUsage
	for _, childUsage := range w.ChildrenUsage {
		sum += childUsage
	}
	return sum
}

// lInternalWalletTotalPropagatedUsage returns the sum of usage propagated to parents. This value is guaranteed to be
// less than or equal to lInternalWalletTotalUsageInNode. Note that neither of these functions are guaranteed to return
// the effective usage. The only way to get the effective usage is by summing the local usages of all relevant wallets.
// This is due to the way the accounting system caps consumption, such that each child can never propagate more than
// their own quota to their parent.
func lInternalWalletTotalPropagatedUsage(b *internalBucket, w *internalWallet) int64 {
	sum := int64(0)
	for _, group := range w.AllocationsByParent {
		sum += group.TreeUsage
	}
	return sum
}

func lInternalGroupTotalQuotaContributing(b *internalBucket, group *internalGroup) int64 {
	retiredAllocationsContribute := !b.IsCapacityBased()

	sum := int64(0)
	for allocId, _ := range group.Allocations {
		alloc := b.AllocationsById[allocId]
		if alloc.Committed && alloc.Active && (retiredAllocationsContribute || !alloc.Retired) {
			sum += alloc.Quota
		}
	}
	return sum
}

func lInternalEarliestExpiration(b *internalBucket, g *internalGroup) time.Time {
	first := true
	earliest := time.UnixMilli(0)

	for allocId, _ := range g.Allocations {
		alloc := b.AllocationsById[allocId]
		if alloc.Committed && alloc.Active && !alloc.Retired && alloc.Quota > 0 && (first || alloc.End.Before(earliest)) {
			first = false
			earliest = alloc.End
		}
	}

	return earliest
}

func lInternalGroupPreferredBalance(b *internalBucket, now time.Time, g *internalGroup) int64 {
	sum := int64(0)
	for childId, _ := range g.Allocations {
		sum += lInternalAllocPreferredBalance(b, now, b.AllocationsById[childId])
	}
	return sum
}

func lInternalAllocPreferredBalance(b *internalBucket, now time.Time, a *internalAllocation) int64 {
	if !a.Active {
		return 0
	} else if a.Retired {
		if b.IsCapacityBased() {
			return 0
		} else {
			return a.RetiredUsage
		}
	} else {
		if b.IsCapacityBased() {
			return a.Quota
		} else {
			duration := a.End.Sub(a.Start)
			timeSinceStart := now.Sub(a.Start)

			percentUsed := float64(timeSinceStart) / float64(duration)
			result := int64(float64(a.Quota) * percentUsed)

			result = max(0, result)
			result = min(a.Quota, result)
			return result
		}
	}
}

func lInternalGroupTotalRetired(b *internalBucket, group *internalGroup) int64 {
	sum := int64(0)
	for allocId, _ := range group.Allocations {
		alloc := b.AllocationsById[allocId]
		sum += alloc.RetiredUsage
	}
	return sum
}

func lInternalGroupHasCommittedAllocation(b *internalBucket, group *internalGroup) bool {
	for allocationId := range group.Allocations {
		if allocation := b.AllocationsById[allocationId]; allocation != nil && allocation.Committed {
			return true
		}
	}
	return false
}

// Wallet read-only API
// ---------------------------------------------------------------------------------------------------------------------
// This API is needed for UIs and service providers. It mostly serves as a way to read information about the current
// state. All APIs in this section will tend to copy data out into a different format which can be consumed freely.
//
// The similarly named API values have deliberately different meanings:
//
//   - LocalUsage is the provider-reported usage belonging directly to this wallet.
//   - TotalUsage is LocalUsage plus usage received from direct children. It is usage present at this node, not the sum
//     of usage in the entire descendant tree; child usage has already been aggregated at each edge.
//   - Quota is incoming contributing quota. Capacity allocations stop contributing when retired; retired non-capacity
//     allocations retain quota equal to their committed RetiredUsage.
//   - TotalAllocated is contributing quota granted from this wallet to direct children. It is not incoming quota.
//   - MaxUsable is additional usage which can currently be routed from the synthetic root to this wallet. It is
//     headroom, not total quota or current balance. A value of zero means the wallet is locked.
//   - UiOnlyActiveQuota and UiOnlyActiveUsage intentionally describe only currently active allocation periods for UI
//     presentation. They are not safe inputs to routing or accounting decisions because they omit retired attribution.
//
// Allocation-group Usage is TreeUsage, while allocation-group Quota is contributing quota for that one parent edge.
// Neither value should be interpreted as the wallet's total local usage.

func internalRetrieveWallet(
	now time.Time,
	id AccWalletId,
	includeChildren bool,
) (accapi.WalletV2, bool) {
	b, w, ok := internalWalletById(id)

	if !ok {
		return accapi.WalletV2{}, false
	} else {
		accGlobals.Mu.RLock()
		b.Mu.RLock()

		ownerId := w.OwnedBy
		owner := accGlobals.OwnersById[ownerId].WalletOwner()
		apiWallet := lInternalWalletToApi(now, b, w, owner, includeChildren, util.OptNone[int]())

		b.Mu.RUnlock()
		accGlobals.Mu.RUnlock()

		return apiWallet, true
	}
}

func lInternalWalletToApi(
	now time.Time,
	b *internalBucket,
	w *internalWallet,
	owner accapi.WalletOwner,
	includeChildren bool,
	filterChildrenByIdleTimeInDays util.Option[int],
) accapi.WalletV2 {
	groups := w.AllocationsByParent
	apiWallet := accapi.WalletV2{
		Owner:                   owner,
		PaysFor:                 b.Category,
		AllocationGroups:        []accapi.AllocationGroupWithParent{},
		Children:                []accapi.AllocationGroupWithChild{},
		TotalUsage:              lInternalWalletTotalUsageInNode(b, w),
		LocalUsage:              w.LocalUsage,
		MaxUsable:               lInternalMaxUsable(b, now, w),
		Quota:                   lInternalWalletTotalQuotaContributing(b, w),
		TotalAllocated:          lInternalWalletTotalAllocatedContributing(b, w),
		UiOnlyActiveUsage:       lInternalWalletTotalUsageFromActiveAllocationsUiOnly(b, w),
		UiOnlyActiveQuota:       lInternalWalletTotalQuotaFromActiveAllocations(b, w),
		LastSignificantUpdateAt: fndapi.Timestamp(w.LastSignificantUpdate),
	}

	allocGroupApi := func(g *internalGroup) accapi.AllocationGroup {
		var apiAllocs []accapi.Allocation

		contributingQuota := int64(0)
		retiredContributes := !b.IsCapacityBased()

		for allocId, _ := range g.Allocations {
			alloc := b.AllocationsById[allocId]
			if !alloc.Committed {
				continue
			}
			apiAllocs = append(apiAllocs, accapi.Allocation{
				Id:        int64(allocId),
				StartDate: fndapi.Timestamp(alloc.Start),
				EndDate:   fndapi.Timestamp(alloc.End),
				Quota:     alloc.Quota,
				GrantedIn: util.OptDefaultOrMap(alloc.GrantedIn, util.OptNone[int64](), func(val accGrantId) util.Option[int64] {
					return util.OptValue(int64(val))
				}),
				RetiredUsage: alloc.RetiredUsage,
				RetiredQuota: alloc.RetiredQuota,
				Retired:      alloc.Retired,
				Activated:    alloc.Active,
			})

			if alloc.Active && (!alloc.Retired || retiredContributes) {
				contributingQuota += alloc.Quota
			}
		}

		slices.SortFunc(apiAllocs, func(a, b accapi.Allocation) int {
			// Retired last
			if a.Retired && !b.Retired {
				return 1
			} else if !a.Retired && b.Retired {
				return -1
			}

			// Not activated first
			if a.Activated && !b.Activated {
				return 1
			} else if !a.Activated && b.Activated {
				return -1
			}

			// Newest start date first
			if a.StartDate.Time().After(b.StartDate.Time()) {
				return -1
			} else if a.StartDate.Time().Before(b.StartDate.Time()) {
				return 1
			}

			// Latest end date first
			if a.EndDate.Time().Before(b.EndDate.Time()) {
				return -1
			} else if a.EndDate.Time().After(b.EndDate.Time()) {
				return 1
			}

			// Tiebreaker by ID
			if a.Id > b.Id {
				return 1
			} else if a.Id < b.Id {
				return -1
			} else {
				return 0
			}
		})

		return accapi.AllocationGroup{
			Id:                int(g.Id),
			Allocations:       util.NonNilSlice(apiAllocs),
			Usage:             g.TreeUsage,
			Quota:             contributingQuota,
			UiOnlyActiveQuota: lInternalGroupTotalQuotaFromActiveAllocations(b, g),
			UiOnlyActiveUsage: lInternalGroupTotalUsageFromActiveAllocationsUiOnly(b, g),
		}
	}

	for _, g := range groups {
		if !lInternalGroupHasCommittedAllocation(b, g) {
			continue
		}
		var parentWalletRef util.Option[accapi.ParentOrChildWallet]
		if g.ParentWallet != internalGraphRoot {
			pw := b.WalletsById[g.ParentWallet]
			po := accGlobals.OwnersById[pw.OwnedBy]
			wo := po.WalletOwner()
			parentWalletRef.Set(accapi.ParentOrChildWallet{
				ProjectId:    wo.ProjectId,
				ProjectTitle: wo.Username, // NOTE(Dan): Stupid backwards compatible design
			})
		}

		apiWallet.AllocationGroups = append(apiWallet.AllocationGroups, accapi.AllocationGroupWithParent{
			Parent: parentWalletRef,
			Group:  allocGroupApi(g),
		})
	}

	if includeChildren {
		activeChildren := map[AccWalletId]util.Empty{}
		if filterChildrenByIdleTimeInDays.Present && filterChildrenByIdleTimeInDays.Value > 0 {
			from := now.AddDate(0, 0, -filterChildrenByIdleTimeInDays.Value)
			activeChildren = lUsageActiveChildrenInWindow(from, now, w.Id)
		}

		for childId, _ := range w.ChildrenUsage {
			if len(activeChildren) > 0 {
				if _, isActive := activeChildren[childId]; isActive {
					continue
				}
			}

			childWallet := b.WalletsById[childId]
			childOwner := accGlobals.OwnersById[childWallet.OwnedBy]
			g := childWallet.AllocationsByParent[w.Id]
			if !lInternalGroupHasCommittedAllocation(b, g) {
				continue
			}

			wo := childOwner.WalletOwner()
			child := accapi.ParentOrChildWallet{
				ProjectId:    wo.ProjectId,
				ProjectTitle: wo.Username, // NOTE(Dan): Stupid backwards compatible design
			}

			apiWallet.Children = append(apiWallet.Children, accapi.AllocationGroupWithChild{
				Child: util.OptValue(child),
				Group: allocGroupApi(g),
			})
		}
	}

	return apiWallet
}

func internalRetrieveWalletByAllocationId(
	now time.Time,
	allocationId int,
) (AccWalletId, accapi.WalletV2, bool) {
	accGlobals.Mu.RLock()

	var wallet accapi.WalletV2
	var found = false
	var wId AccWalletId
	for _, bucket := range accGlobals.BucketsByCategory {
		bucket.Mu.RLock()
		locatedAllocation := bucket.AllocationsById[accAllocId(allocationId)]
		if locatedAllocation != nil && locatedAllocation.Committed {
			walletId := locatedAllocation.BelongsTo
			iWallet := bucket.WalletsById[walletId]
			if iWallet != nil {
				owner := accGlobals.OwnersById[iWallet.OwnedBy]
				if owner != nil {
					wallet = lInternalWalletToApi(now, bucket, iWallet, owner.WalletOwner(), false, util.OptNone[int]())
					wId = walletId
					found = true
					bucket.Mu.RUnlock()
					break
				}
			}
		}
		bucket.Mu.RUnlock()
	}

	accGlobals.Mu.RUnlock()
	return wId, wallet, found
}

type walletFilter struct {
	ProductType util.Option[accapi.ProductType]
	Provider    util.Option[string]
	Category    util.Option[string]

	IncludeChildren bool
	RequireActive   bool

	FilterChildrenByIdleTimeInDays util.Option[int]
}

func lUsageActiveChildrenInWindow(from time.Time, until time.Time, parentWallet AccWalletId) map[AccWalletId]util.Empty {
	result := map[AccWalletId]util.Empty{}

	reports := usageRetrieveHistoricReports(from, until, parentWallet)
	for _, report := range reports {
		for _, item := range report.UsageOverTime.Delta {
			if item.Child.Present && item.Change != 0 {
				result[item.Child.Value] = util.Empty{}
			}
		}
	}

	return result
}

func internalRetrieveWallets(
	now time.Time,
	reference string,
	filter walletFilter,
) []accapi.WalletV2 {
	if reference == "" {
		return nil
	}

	owner := internalOwnerByReference(reference)
	var potentialBuckets []*internalBucket

	accGlobals.Mu.RLock()
	for _, b := range accGlobals.BucketsByCategory {
		if filter.ProductType.Present && filter.ProductType.Value != b.Category.ProductType {
			continue
		} else if filter.Provider.Present && filter.Provider.Value != b.Category.Provider {
			continue
		} else if filter.Category.Present && filter.Category.Value != b.Category.Name {
			continue
		} else {
			potentialBuckets = append(potentialBuckets, b)
		}
	}

	var wallets []accapi.WalletV2

	for _, b := range potentialBuckets {
		wId := internalWalletByOwner(b, now, owner.Id)
		b.Mu.RLock()

		w := b.WalletsById[wId]
		groups := w.AllocationsByParent
		shouldInclude := false
		if !filter.RequireActive {
			for _, group := range groups {
				if lInternalGroupHasCommittedAllocation(b, group) {
					shouldInclude = true
					break
				}
			}
		}
		if filter.RequireActive {
		anyActive:
			for _, group := range groups {
				for allocId, _ := range group.Allocations {
					allocation := b.AllocationsById[allocId]
					if allocation.Committed && !allocation.Retired && !now.Before(allocation.Start) && now.Before(allocation.End) {
						shouldInclude = true
						break anyActive
					}
				}
			}
		}

		if shouldInclude {
			apiWallet := lInternalWalletToApi(
				now,
				b,
				w,
				owner.WalletOwner(),
				filter.IncludeChildren,
				filter.FilterChildrenByIdleTimeInDays,
			)
			wallets = append(wallets, apiWallet)
		}

		b.Mu.RUnlock()
	}

	accGlobals.Mu.RUnlock() // need to be held for during owner lookups

	// NOTE(Dan): Sorting is currently chosen for easy pagination, this is probably not the best one for display.
	slices.SortFunc(wallets, func(a, b accapi.WalletV2) int {
		if a.PaysFor.Provider < b.PaysFor.Provider {
			return -1
		} else if a.PaysFor.Provider > b.PaysFor.Provider {
			return 1
		} else if a.PaysFor.Name < b.PaysFor.Name {
			return -1
		} else if a.PaysFor.Name > b.PaysFor.Name {
			return 1
		} else {
			return 0
		}
	})

	return wallets
}

func internalRetrieveAncestors(now time.Time, category accapi.ProductCategoryIdV2, owner accapi.WalletOwner) []accapi.WalletV2 {
	accGlobals.Mu.RLock()

	bucket := accGlobals.BucketsByCategory[category]
	iOwner := accGlobals.OwnersByReference[owner.Reference()]
	iWalletId := internalWalletByOwner(bucket, now, iOwner.Id)

	bucket.Mu.RLock()
	ancestors := lRetrieveAncestorWallets(bucket, now, iWalletId)
	bucket.Mu.RUnlock()

	accGlobals.Mu.RUnlock()
	return ancestors
}

func lRetrieveAncestorWallets(bucket *internalBucket, now time.Time, root AccWalletId) []accapi.WalletV2 {
	relevantWallets := map[AccWalletId]*internalWallet{}

	queue := []*internalWallet{bucket.WalletsById[root]}
	for len(queue) > 0 {
		next := queue[0]
		queue = queue[1:]

		relevantWallets[next.Id] = next
		for parentId, _ := range next.AllocationsByParent {
			if _, hasVisited := relevantWallets[parentId]; !hasVisited && parentId != internalGraphRoot {
				queue = append(queue, bucket.WalletsById[parentId])
			}
		}
	}

	var wallets []accapi.WalletV2
	for _, w := range relevantWallets {
		owner := accGlobals.OwnersById[w.OwnedBy].WalletOwner()
		wallets = append(wallets, lInternalWalletToApi(now, bucket, w, owner, false, util.OptNone[int]()))
	}

	return wallets
}

// Admin endpoints
// ---------------------------------------------------------------------------------------------------------------------

func internalHierarchyReset(bucket *internalBucket) {
	bucket.Mu.Lock()
	for _, alloc := range bucket.AllocationsById {
		alloc.RetiredUsage = 0
		alloc.Dirty = true
	}
	for _, wallet := range bucket.WalletsById {
		wallet.LocalUsage = 0
		for child := range wallet.ChildrenUsage {
			wallet.ChildrenUsage[child] = 0
		}
		for _, group := range wallet.AllocationsByParent {
			group.TreeUsage = 0
			group.Dirty = true
		}
		wallet.Dirty = true
	}
	bucket.Mu.Unlock()
}

// Mermaid diagrams (for debugging)
// ---------------------------------------------------------------------------------------------------------------------

func lInternalMermaidGraph(bucket *internalBucket, now time.Time, root AccWalletId) string {
	relevantWallets := map[AccWalletId]*internalWallet{}

	queue := []*internalWallet{bucket.WalletsById[root]}
	for len(queue) > 0 {
		next := queue[0]
		queue = queue[1:]

		relevantWallets[next.Id] = next
		for parentId, _ := range next.AllocationsByParent {
			if _, hasVisited := relevantWallets[parentId]; !hasVisited && parentId != internalGraphRoot {
				queue = append(queue, bucket.WalletsById[parentId])
			}
		}
	}

	return mermaid.Mermaid(func(b *mermaid.Builder) {
		b.Node("W0", "Root", mermaid.ShapeRound, "")

		for _, wallet := range relevantWallets {
			walletName := fmt.Sprintf("W%d", wallet.Id)
			b.Subgraph(walletName, walletName, func(g *mermaid.Builder) {
				body := strings.Builder{}
				body.WriteString("<b>Info</b><br>")
				body.WriteString(fmt.Sprintf("local: %d<br>", wallet.LocalUsage))
				body.WriteString(fmt.Sprintf("allocated: %d<br>", lInternalWalletTotalAllocatedContributing(bucket, wallet)))
				body.WriteString(fmt.Sprintf("quota: %d<br>", lInternalWalletTotalQuotaContributing(bucket, wallet)))
				body.WriteString(fmt.Sprintf("usable: %d<br>", lInternalMaxUsable(bucket, now, wallet)))
				body.WriteString(fmt.Sprintf("usage: %d<br>", lInternalWalletTotalUsageInNode(bucket, wallet)))
				body.WriteString(fmt.Sprintf("..propagated: %d<br>", lInternalWalletTotalPropagatedUsage(bucket, wallet)))
				body.WriteString(fmt.Sprintf("locked: %v", wallet.WasLocked))

				if len(wallet.ChildrenUsage) > 0 {
					body.WriteString("<br>children:<br>")

					for childId, usage := range wallet.ChildrenUsage {
						if _, isRelevant := relevantWallets[childId]; isRelevant {
							body.WriteString(fmt.Sprintf("+ W%d = %d<br>", childId, usage))
						}
					}
				}

				g.Node(walletName+"Info", body.String(), mermaid.ShapeRound, "text-align:left")

				var groups []*internalGroup
				for _, group := range wallet.AllocationsByParent {
					groups = append(groups, group)
				}

				slices.SortFunc(groups, func(a, b *internalGroup) int {
					if a.ParentWallet < b.ParentWallet {
						return -1
					} else if a.ParentWallet > b.ParentWallet {
						return 1
					} else {
						return 0
					}
				})

				for _, group := range groups {
					parentId := group.ParentWallet
					groupBody := strings.Builder{}

					groupBody.WriteString(fmt.Sprintf("<b>parent:</b> %d<br>", parentId))
					groupBody.WriteString(fmt.Sprintf("<b>usage:</b> %d<br>", group.TreeUsage))
					groupBody.WriteString(fmt.Sprintf("<b>preferred:</b> %d<br>", lInternalGroupPreferredBalance(bucket, now, group)))

					groupName := fmt.Sprintf("W%dW%d", wallet.Id, parentId)
					groupGraph := g.Subgraph(groupName, fmt.Sprintf("Group -> W%d", parentId), func(gb *mermaid.Builder) {
						gb.Node(groupName+"Info", groupBody.String(), mermaid.ShapeRound, "text-align:left")

						for allocId, _ := range group.Allocations {
							alloc := bucket.AllocationsById[allocId]
							gb.Node(
								fmt.Sprintf("A%d", allocId),
								fmt.Sprintf("<b>id:</b> %d<br><b>quota:</b> %d<br> <b>active:</b> %v<br><b>retired:</b> %v<br><b>retired usage:</b> %v", allocId, alloc.Quota, alloc.Active, alloc.Retired, alloc.RetiredUsage),
								mermaid.ShapeRound,
								"text-align:left",
							)
						}
					})

					b.LinkTo(groupGraph, fmt.Sprintf("W%d", parentId), fmt.Sprint(group.TreeUsage), mermaid.LineNormal, nil, nil)
				}
			})
		}
	})
}

func internalAccountingDump() {
	var buckets []*internalBucket
	now := time.Now()

	type line struct {
		Id   AccWalletId
		Data string
	}

	var lines []line

	g := &accGlobals
	g.Mu.RLock()
	for _, bucket := range accGlobals.BucketsByCategory {
		buckets = append(buckets, bucket)
	}
	g.Mu.RUnlock()

	for _, b := range buckets {
		b.Mu.RLock()
		for _, wallet := range b.WalletsById {
			maxUsable := lInternalMaxUsable(b, now, wallet)
			treeUsage := lInternalWalletTotalPropagatedUsage(b, wallet)
			quota := lInternalWalletTotalQuotaContributing(b, wallet)

			lines = append(lines, line{
				Id:   wallet.Id,
				Data: fmt.Sprintf("%d,%d,%d,%d", wallet.Id, maxUsable, treeUsage, quota),
			})
		}
		b.Mu.RUnlock()
	}

	slices.SortFunc(lines, func(a, b line) int {
		return cmp.Compare(a.Id, b.Id)
	})

	w := &strings.Builder{}
	w.WriteString("id,maxUsable,treeUsage,quota\n")
	for _, l := range lines {
		w.WriteString(l.Data)
		w.WriteString("\n")
	}

	_ = os.WriteFile("/tmp/dump.csv", []byte(w.String()), 0660)
}

// Constants
// ---------------------------------------------------------------------------------------------------------------------

var projectRegex = regexp.MustCompile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

type internalGraphFlag int

const (
	internalGraphWithOverAllocation internalGraphFlag = 1 << iota
	internalGraphWithoutLeafOverAllocation
)

const (
	internalGraphRoot AccWalletId = 0

	internalGraphBalanceWeight = int64(1 << 25)
	internalGraphTimeWeight    = int64(1)
)

// NOTE(Dan): Must be less than veryLargeNumber of accounting_graph.go
// NOTE(Dan): Must be (significantly) larger than any cost which can naturally be created from a normal node
var internalGraphOverAllocationEdgeCost = (&big.Int{}).Lsh(big.NewInt(1), 80)

// NOTE(Dan): This number must be more expensive than the over-allocation edge
var internalGraphRetirementCost = (&big.Int{}).Neg((&big.Int{}).Lsh(big.NewInt(1), 85))
