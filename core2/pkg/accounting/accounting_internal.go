package accounting

import (
	"fmt"
	"math/big"
	"net/http"
	"regexp"
	"slices"
	"strings"
	"sync"
	"sync/atomic"
	"time"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
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
// This section contain the core-types as already introduced along with the global data-structure. From the global
// data-structure (accGlobals), it is possible to reach all other parts of the system.
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

type accGrantId int
type accGroupId int
type accWalletId int
type accOwnerId int
type accAllocId int

var accGlobals struct {
	Mu sync.RWMutex

	OwnersByReference map[string]*internalOwner
	OwnersById        map[accOwnerId]*internalOwner

	Usage             map[string]*scopedUsage // TODO(Dan): quite annoying that this has to be global
	BucketsByCategory map[accapi.ProductCategoryIdV2]*internalBucket

	OwnerIdAcc  atomic.Int64 // does not require mutex
	WalletIdAcc atomic.Int64 // does not require mutex
	GroupIdAcc  atomic.Int64 // does not require mutex
	AllocIdAcc  atomic.Int64 // does not require mutex
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
	Category accapi.ProductCategory // does not require any mutex

	SignificantUpdateAt time.Time

	WalletsById    map[accWalletId]*internalWallet
	WalletsByOwner map[accOwnerId]*internalWallet

	AllocationsById map[accAllocId]*internalAllocation

	disableEvaluation bool
}

func (b *internalBucket) IsCapacityBased() bool { // does not require any mutex
	switch b.Category.AccountingFrequency {
	case accapi.AccountingFrequencyOnce:
		return true
	default:
		return false
	}
}

type internalWallet struct {
	Id      accWalletId
	OwnedBy accOwnerId

	LocalUsage int64

	AllocationsByParent map[accWalletId]*internalGroup
	ChildrenUsage       map[accWalletId]int64

	Dirty                 bool
	WasLocked             bool
	LastSignificantUpdate time.Time
}

type internalGroup struct {
	Id               accGroupId
	AssociatedWallet accWalletId
	ParentWallet     accWalletId

	TreeUsage int64

	Allocations map[accAllocId]util.Empty // NOTE(Dan): This used to set if the allocation was active (now on allocation instead)

	Dirty bool
}

type internalAllocation struct {
	Id        accAllocId
	BelongsTo accWalletId
	Parent    accWalletId

	GrantedIn util.Option[accGrantId]

	Quota int64

	Start time.Time
	End   time.Time

	Retired      bool
	RetiredUsage int64
	RetiredQuota int64

	// NOTE(Dan): this used to be set in the group. This value remains true after activation and is not set to
	// false during retirement.
	Active    bool
	Dirty     bool
	Committed bool
}

type scopedUsage struct {
	Mu sync.RWMutex

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
// - internalAllocate
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

	owner := internalOwnerByReference(request.Owner.Reference())

	var scope *scopedUsage
	if request.Description.Scope.Present {
		scopeKey := fmt.Sprintf("%d\n%s", owner.Id, request.Description.Scope.Value)

		scope = util.ReadOrInsertBucket(&accGlobals.Mu, accGlobals.Usage, scopeKey, func() *scopedUsage {
			return &scopedUsage{
				Key:   scopeKey,
				Usage: 0,
				Dirty: true,
			}
		})
	}

	if scope != nil {
		scope.Mu.Lock()
		defer scope.Mu.Unlock()
	}

	b.Mu.Lock()
	defer b.Mu.Unlock()

	w := lInternalWalletByOwner(b, now, owner.Id)

	var currentUsage, delta int64
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

	_, visitedWallets := lInternalReportUsage(b, now, w, deltaToReport)
	w.LocalUsage += delta
	w.Dirty = true

	for visitedId, _ := range visitedWallets {
		visited := b.WalletsById[visitedId]
		lInternalReevaluate(b, now, visited, false)
	}

	return !w.WasLocked, nil
}

// If grantedIn is specified, then the allocations will not be committed to the database before
// internalCommitAllocations is invoked with the same ID.
func internalAllocate(
	now time.Time,
	b *internalBucket,
	start time.Time,
	end time.Time,
	quota int64,
	recipient accWalletId,
	parent accWalletId,
	grantedIn util.Option[accGrantId],
) (accAllocId, *util.HttpError) {
	// TODO check that we can do this. Might need to happen in public API instead.

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

		if parentWallet != nil {
			parentWallet.Dirty = true

			// NOTE(Dan): Insert a childrenUsage entry if we don't already have one. This is required to make
			// childrenUsage a valid tool for looking up children in the parent wallet.
			currentUsage, _ := parentWallet.ChildrenUsage[recipient]
			parentWallet.ChildrenUsage[recipient] = currentUsage
		}

		lInternalAttemptActivation(b, now, allocation)
		return allocationId, nil
	}
}

// internalCommitAllocations ensures that all allocations granted in grantId are committed together. If onPersist is
// specified, then it will be run when the data is persisted.
func internalCommitAllocations(grantId accGrantId, onPersist func(tx *db.Transaction)) {
	// TODO
}

func internalCompleteScan(now time.Time, persistence func(buckets []*internalBucket, scopes []*scopedUsage)) {
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
	}

	if persistence != nil {
		persistence(buckets, scopes)
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
}

// Entity lookup and initialization
// ---------------------------------------------------------------------------------------------------------------------
// Must data-structures in the accounting system are lazily initialized the first time they are requested. In is the
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
			WalletsById:     map[accWalletId]*internalWallet{},
			WalletsByOwner:  map[accOwnerId]*internalWallet{},
			AllocationsById: map[accAllocId]*internalAllocation{},
		}
	})
}

func internalWalletById(id accWalletId) (*internalBucket, *internalWallet, bool) {
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
	// TODO Reference must be check by caller
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

func internalWalletByOwner(b *internalBucket, now time.Time, owner accOwnerId) accWalletId {
	b.Mu.Lock()
	defer b.Mu.Unlock()
	return lInternalWalletByOwner(b, now, owner).Id
}

func lInternalWalletByOwner(b *internalBucket, now time.Time, owner accOwnerId) *internalWallet {
	return util.LReadOrInsertBucket(b.WalletsByOwner, owner, func() *internalWallet {
		result := &internalWallet{
			Id:                    accWalletId(accGlobals.WalletIdAcc.Add(1)),
			OwnedBy:               owner,
			LocalUsage:            0,
			AllocationsByParent:   map[accWalletId]*internalGroup{},
			ChildrenUsage:         map[accWalletId]int64{},
			Dirty:                 true,
			WasLocked:             false,
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

func lInternalReportUsage(b *internalBucket, now time.Time, w *internalWallet, delta int64) (int64, map[accWalletId]bool) {
	chargeGraph := lInternalBuildGraph(b, now, w, internalGraphWithOverAllocation)

	rootVertex := chargeGraph.WalletToVertex[internalGraphRoot]
	walletVertex := chargeGraph.WalletToVertex[w.Id]

	maxUsable := int64(0)
	if delta < 0 {
		maxUsable = chargeGraph.MinCostFlow(walletVertex, rootVertex, -delta)
	} else {
		maxUsable = chargeGraph.MinCostFlow(rootVertex, walletVertex, delta)
	}

	walletsUpdated := map[accWalletId]bool{}
	walletsUpdated[w.Id] = true

	if maxUsable != 0 {
		gSize := chargeGraph.VertexCount / 2

		for senderVertex := 0; senderVertex < gSize; senderVertex++ {
			senderWalletId := chargeGraph.VertexToWallet[senderVertex]
			senderWallet := b.WalletsById[senderWalletId]

			for receiverVertex := 0; receiverVertex < gSize; receiverVertex++ {
				if chargeGraph.Original[receiverVertex][senderVertex] {
					receiverWalletId := chargeGraph.VertexToWallet[receiverVertex]
					receiverWallet := b.WalletsById[receiverWalletId]
					amount := chargeGraph.Adjacent[senderVertex][receiverVertex]

					if senderWallet != nil {
						group := senderWallet.AllocationsByParent[receiverWalletId]
						group.TreeUsage = amount
						group.Dirty = true
					}

					if receiverWallet != nil {
						receiverWallet.ChildrenUsage[senderWalletId] = amount
						receiverWallet.Dirty = true
					}
				}
			}
		}
	}

	return maxUsable, walletsUpdated
}

func lInternalBuildGraph(b *internalBucket, now time.Time, leaf *internalWallet, flags internalGraphFlag) *Graph {
	vertexToWallet := []accWalletId{leaf.Id, internalGraphRoot}
	walletToVertex := map[accWalletId]int{leaf.Id: 0, internalGraphRoot: 1}
	rootVertex := walletToVertex[internalGraphRoot]

	{
		// Discover and add all nodes (wallets) to the graph
		// -------------------------------------------------
		queue := []accWalletId{leaf.Id}
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

				activeQuota := lInternalGroupTotalQuotaActive(b, allocationGroup)

				{
					// Capacity
					capacity := activeQuota - allocationGroup.TreeUsage
					g.AddEdge(sourceVertex, destinationVertex, capacity, allocationGroup.TreeUsage)
				}

				{
					// Cost
					// -------------------------------------------------------------------------------------------------
					// The cost variable is expected to be positive. The larger the number is, the more likely it is
					// that this edge will be chosen during charges.
					//
					// NOTE(Dan): The old code would have the cost be negative, but with identical semantics apart
					// from this. See also the negation we do at the end of this block.

					preferredBalance := lInternalGroupPreferredBalance(b, now, allocationGroup)

					balanceNotConsumed := max(1, preferredBalance-allocationGroup.TreeUsage)
					balanceFactor := balanceNotConsumed * internalGraphBalanceWeight

					earliestExpiration := lInternalEarliestExpiration(b, allocationGroup)
					timeFactor := (earliestExpiration.Sub(now).Milliseconds()) * internalGraphTimeWeight

					cost := (&big.Int{}).Mul(big.NewInt(balanceFactor), big.NewInt(timeFactor))

					if timeFactor < 0 || activeQuota < allocationGroup.TreeUsage {
						cost = internalGraphRetirementCost
					} else if cost.Cmp(big.NewInt(0)) == -1 {
						panic("expected cost to be >= 0")
					}

					// Multiply the cost with -1 because it has to be negative going into the graph function
					g.AddEdgeCost(sourceVertex, destinationVertex, (&big.Int{}).Neg(cost))
				}

				if flags&internalGraphWithOverAllocation != 0 {
					// This block augments the graph with an over-allocation node which overconsumption will flow
					// through. This edge is purposefully made very expensive such that this path will only be chosen
					// for a flow if there are no other ways of using up the charge.

					totalAllocated := lInternalWalletTotalAllocatedActive(b, wallet)
					activeQuota := lInternalWalletTotalQuotaActive(b, wallet)

					overAllocation := totalAllocated + wallet.LocalUsage - activeQuota
					if overAllocation > 0 {
						usageInNode := lInternalWalletTotalUsageInNode(b, wallet)
						propagatedUsage := lInternalWalletTotalPropagatedUsage(b, wallet)

						overAllocationUsed := usageInNode - propagatedUsage
						if overAllocationUsed < 0 {
							panic("assertion error")
						}

						overAllocationNode := vertexToOverAllocationRoot(vertexIndex)

						// Add edge between root and the over-allocation node
						g.AddEdge(rootVertex, overAllocationNode, overAllocationUsed-overAllocationUsed, overAllocationUsed)
						g.AddEdgeCost(rootVertex, overAllocationNode, internalGraphOverAllocationEdgeCost)

						// Add edge between our wallet node and the over-allocation node
						g.AddEdge(overAllocationNode, vertexIndex, overAllocationUsed-overAllocationUsed, overAllocationUsed)
						g.AddEdgeCost(overAllocationNode, vertexIndex, internalGraphOverAllocationEdgeCost)
					}
				}
			}
		}

		return g
	}
}

func lInternalReflowExcess(b *internalBucket, now time.Time, wallet *internalWallet) {
	// Similar to the re-balance operation, except this function will only attempt to increase usage by looking at the
	// local excess which is not being propagated.
	//
	// The main purpose of this function is to ensure that if an overconsumption node was taken at some point, then
	// this "excess" usage will eventually be propagated back into the system once there is room for it.

	for childId, _ := range wallet.ChildrenUsage {
		// TODO loops
		child := b.WalletsById[childId]
		lInternalReflowExcess(b, now, child)
	}

	if wallet.LocalUsage > 0 {
		propagated := lInternalWalletTotalPropagatedUsage(b, wallet)
		inNode := lInternalWalletTotalUsageInNode(b, wallet)
		excess := inNode - propagated

		if excess > 0 {
			usable := lInternalMaxUsable(b, now, wallet)
			toReport := min(usable, excess, wallet.LocalUsage)
			if toReport > 0 {
				lInternalReportUsage(b, now, wallet, toReport)
			}
		}
	}
}

func lInternalRebalance(b *internalBucket, now time.Time, wallet *internalWallet, deficit util.Option[int64]) {
	// The main purpose of this function is to ensure that retired flows are eventually moved to an active flow. This
	// operation is _only_ done for capacity-based product categories. For time-based products, this is not done (and
	// must not be done). Instead, time-based product utilizes a different retirement mechanism which locks usage in
	// place. This is needed, because in time-based products utilization is ever-increasing. Unlike in capacity based
	// products where utilization can go up and down.

	recharge := func(amount int64) {
		if amount > 0 {
			b.disableEvaluation = true
			lInternalReportUsage(b, now, wallet, -amount)
			b.disableEvaluation = false
			lInternalReportUsage(b, now, wallet, amount)
		}
	}

	if b.IsCapacityBased() {
		if !deficit.Present {
			sum := int64(0)
			for _, g := range wallet.AllocationsByParent {
				activeQuota := lInternalGroupTotalQuotaActive(b, g)
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

			// TODO loops
			for childId, usage := range wallet.ChildrenUsage {
				child := b.WalletsById[childId]
				lInternalRebalance(b, now, child, util.OptValue(min(usage, deficit.Value)))
			}

			maxUsable := lInternalMaxUsable(b, now, wallet)
			recharge(min(maxUsable, deficit.Value, wallet.LocalUsage))
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
	if rebalance {
		lInternalReflowExcess(b, now, wallet)
		lInternalRebalance(b, now, wallet, util.OptNone[int64]())
	}

	visited := map[accWalletId]util.Empty{}
	queue := []*internalWallet{wallet}
	for len(queue) > 0 {
		next := queue[0]
		queue = queue[1:]

		visited[next.Id] = util.Empty{}

		maxUsable := lInternalMaxUsable(b, now, next)
		if maxUsable <= 0 && !next.WasLocked {
			next.WasLocked = true
			lInternalMarkSignificantUpdate(b, now, next)
		} else if maxUsable > 0 && next.WasLocked {
			next.WasLocked = false
			lInternalMarkSignificantUpdate(b, now, next)
		}

		for childId, _ := range wallet.ChildrenUsage {
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
	for _, alloc := range b.AllocationsById {
		lInternalAttemptActivation(b, now, alloc)
		lInternalAttemptRetirement(b, now, alloc)
	}
}

func lInternalAttemptActivation(b *internalBucket, now time.Time, alloc *internalAllocation) {
	if !alloc.Active && now.Add(1*time.Second).After(alloc.Start) && now.Before(alloc.End) {
		wallet := b.WalletsById[alloc.BelongsTo]

		alloc.Active = true
		alloc.Dirty = true

		lInternalReevaluate(b, now, wallet, true)

		// NOTE(Dan): Always mark since reevaluate only marks if a wallet changes lock state
		lInternalMarkSignificantUpdate(b, now, wallet)
	}
}

func lInternalAttemptRetirement(b *internalBucket, now time.Time, alloc *internalAllocation) {
	if !alloc.Retired && now.Add(1*time.Second).After(alloc.End) {
		wallet := b.WalletsById[alloc.BelongsTo]
		group := wallet.AllocationsByParent[alloc.Parent]

		// TODO This value will be wrong if the retired amount was ever reflown
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
	}
}

// Wallet, allocation and group metrics
// ---------------------------------------------------------------------------------------------------------------------
// The following functions compute and measure various metrics that are needed for the internal functions of the
// accounting system. Some of these computed properties are also returned needed by the public API.

func lInternalMarkSignificantUpdate(b *internalBucket, now time.Time, wallet *internalWallet) {
	b.SignificantUpdateAt = now
	wallet.LastSignificantUpdate = now
	wallet.Dirty = true
}

func lInternalMaxUsable(b *internalBucket, now time.Time, wallet *internalWallet) int64 {
	graph := lInternalBuildGraph(b, now, wallet, 0)
	rootIndex := graph.WalletToVertex[internalGraphRoot]
	return graph.MaxFlow(rootIndex, 0)
}

func lInternalWalletTotalAllocatedActive(b *internalBucket, w *internalWallet) int64 {
	retiredAllocationsContribute := !b.IsCapacityBased()

	sum := int64(0)
	for childId, _ := range w.ChildrenUsage {
		child := b.WalletsById[childId]
		childGroup := child.AllocationsByParent[w.Id]

		for allocId, _ := range childGroup.Allocations {
			childAlloc := b.AllocationsById[allocId]
			if childAlloc.Active && (retiredAllocationsContribute || !childAlloc.Retired) {
				sum += childAlloc.Quota
			}
		}
	}
	return sum
}

func internalWalletTotalQuotaActive(b *internalBucket, wId accWalletId) (int64, bool) {
	b.Mu.RLock()
	owner, ok := b.WalletsById[wId]
	var result int64
	if ok {
		result = lInternalWalletTotalQuotaActive(b, owner)
	}
	b.Mu.RUnlock()
	return result, ok
}

func lInternalWalletTotalQuotaActive(b *internalBucket, w *internalWallet) int64 {
	sum := int64(0)
	for _, group := range w.AllocationsByParent {
		sum += lInternalGroupTotalQuotaActive(b, group)
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

func lInternalGroupTotalQuotaActive(b *internalBucket, group *internalGroup) int64 {
	retiredAllocationsContribute := !b.IsCapacityBased()

	sum := int64(0)
	for allocId, _ := range group.Allocations {
		alloc := b.AllocationsById[allocId]
		if alloc.Active && (retiredAllocationsContribute || !alloc.Retired) {
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
		if alloc.Active && (first || alloc.End.Before(earliest)) {
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

// Wallet read-only API
// ---------------------------------------------------------------------------------------------------------------------
// This API is needed for UIs and service providers. It mostly serves as a way to read information about the current
// state. All APIs in this section will tend to copy data out into a different format which can be consumed freely.

func internalFindRelevantProviders(
	reference string,
	filterProduct util.Option[accapi.ProductType],
	includeFreeToUse bool,
) []string {
	owner := internalOwnerByReference(reference)

	var potentialBuckets []*internalBucket
	providerSet := map[string]util.Empty{}

	accGlobals.Mu.RLock()

	for _, b := range accGlobals.BucketsByCategory {
		if b.Category.FreeToUse && !includeFreeToUse {
			continue
		} else if filterProduct.Present && filterProduct.Value != b.Category.ProductType {
			continue
		} else {
			potentialBuckets = append(potentialBuckets, b)
		}
	}

	accGlobals.Mu.RUnlock()

	now := time.Now()
	for _, b := range potentialBuckets {
		if _, alreadyPresent := providerSet[b.Category.Provider]; !alreadyPresent {
			b.Mu.RLock()
			w := lInternalWalletByOwner(b, now, owner.Id)
			if len(w.AllocationsByParent) > 0 {
				providerSet[b.Category.Provider] = util.Empty{}
			}
			b.Mu.RUnlock()
		}
	}

	var result []string
	for provider, _ := range providerSet {
		result = append(result, provider)
	}

	return result
}

type walletFilter struct {
	ProductType util.Option[accapi.ProductType]
	Provider    util.Option[string]
	Category    util.Option[string]

	IncludeChildren bool
	RequireActive   bool
}

func internalRetrieveWallets(
	now time.Time,
	reference string,
	filter walletFilter,
) []accapi.WalletV2 {
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
		b.Mu.RLock()

		w := lInternalWalletByOwner(b, now, owner.Id)

		groups := w.AllocationsByParent
		shouldInclude := !filter.RequireActive
		if filter.RequireActive {
		anyActive:
			for _, group := range groups {
				for allocId, _ := range group.Allocations {
					if b.AllocationsById[allocId].Active {
						shouldInclude = true
						break anyActive
					}
				}
			}
		}

		if shouldInclude {
			apiWallet := accapi.WalletV2{
				Owner:                   owner.WalletOwner(),
				PaysFor:                 b.Category,
				AllocationGroups:        nil,
				Children:                nil,
				TotalUsage:              lInternalWalletTotalUsageInNode(b, w),
				LocalUsage:              w.LocalUsage,
				MaxUsable:               lInternalMaxUsable(b, now, w),
				Quota:                   lInternalWalletTotalQuotaActive(b, w),
				TotalAllocated:          lInternalWalletTotalAllocatedActive(b, w),
				LastSignificantUpdateAt: fndapi.Timestamp(w.LastSignificantUpdate),
			}

			allocGroupApi := func(g *internalGroup) accapi.AllocationGroup {
				var apiAllocs []accapi.Allocation

				for allocId, _ := range g.Allocations {
					alloc := b.AllocationsById[allocId]
					apiAllocs = append(apiAllocs, accapi.Allocation{
						Id:        int64(allocId),
						StartDate: fndapi.Timestamp(alloc.Start),
						EndDate:   fndapi.Timestamp(alloc.End),
						Quota:     alloc.Quota,
						GrantedIn: util.OptDefaultOrMap(alloc.GrantedIn, util.OptNone[int64](), func(val accGrantId) util.Option[int64] {
							return util.OptValue(int64(val))
						}),
						RetiredUsage: alloc.RetiredUsage,
					})
				}

				return accapi.AllocationGroup{
					Id:          int(g.Id),
					Allocations: apiAllocs,
					Usage:       g.TreeUsage,
				}
			}

			for _, g := range groups {
				var parentWalletRef util.Option[accapi.ParentOrChildWallet]
				if g.ParentWallet != internalGraphRoot {
					pw := b.WalletsById[g.ParentWallet]
					po := accGlobals.OwnersById[pw.OwnedBy]
					parentWalletRef.Set(accapi.ParentOrChildWallet{
						ProjectId:    po.Reference,
						ProjectTitle: "", // NOTE(Dan)/TODO: This API will not do this
					})
				}

				apiWallet.AllocationGroups = append(apiWallet.AllocationGroups, accapi.AllocationGroupWithParent{
					Parent: parentWalletRef,
					Group:  allocGroupApi(g),
				})
			}

			if filter.IncludeChildren {
				for childId, _ := range w.ChildrenUsage {
					childWallet := b.WalletsById[childId]
					childOwner := accGlobals.OwnersById[childWallet.OwnedBy]

					g := childWallet.AllocationsByParent[w.Id]
					apiWallet.Children = append(apiWallet.Children, accapi.AllocationGroupWithChild{
						Child: util.OptValue(accapi.ParentOrChildWallet{
							ProjectId:    childOwner.Reference,
							ProjectTitle: "", // NOTE(Dan)/TODO: This API will not do this
						}),
						Group: allocGroupApi(g),
					})
				}
			}

			wallets = append(wallets, apiWallet)
		}

		b.Mu.RUnlock()
	}

	accGlobals.Mu.RUnlock() // need to be held for during owner lookups

	// TODO(Dan): Sorting is currently chosen for easy pagination, this is probably not the best one for display.
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

// Mermaid diagrams (for debugging)
// ---------------------------------------------------------------------------------------------------------------------

func lInternalMermaidGraph(bucket *internalBucket, now time.Time, root accWalletId) string {
	relevantWallets := map[accWalletId]*internalWallet{}

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
				body.WriteString(fmt.Sprintf("allocated: %d<br>", lInternalWalletTotalAllocatedActive(bucket, wallet)))
				body.WriteString(fmt.Sprintf("quota: %d<br>", lInternalWalletTotalQuotaActive(bucket, wallet)))
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

// Constants
// ---------------------------------------------------------------------------------------------------------------------

var projectRegex = regexp.MustCompile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

type internalGraphFlag int

const (
	internalGraphWithOverAllocation internalGraphFlag = 1 << iota
)

const (
	internalGraphRoot accWalletId = 0

	internalGraphBalanceWeight = int64(1 << 25)
	internalGraphTimeWeight    = int64(1)
)

// NOTE(Dan): Must be less than veryLargeNumber of accounting_graph.go
// NOTE(Dan): Must be (significantly) larger than any cost which can naturally be created from a normal node
var internalGraphOverAllocationEdgeCost = (&big.Int{}).Lsh(big.NewInt(1), 80)

// NOTE(Dan): This number must be more expensive than the over-allocation edge
var internalGraphRetirementCost = (&big.Int{}).Neg((&big.Int{}).Lsh(big.NewInt(1), 85))
