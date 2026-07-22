package accounting

import (
	"fmt"
	"slices"
	"strings"
	"testing"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
)

type environmentReference struct {
	capacity       bool
	allocations    map[int64]differentialAllocation
	reportCounters map[string]int64
}

func newEnvironmentReference(e *env) *environmentReference {
	GlobalRefAllocations = map[int64]*RefAllocation{}
	GlobalRefWallets = map[int64]*RefWallet{}
	return &environmentReference{
		capacity:       e.Bucket.IsCapacityBased(),
		allocations:    map[int64]differentialAllocation{},
		reportCounters: map[string]int64{},
	}
}

func (r *environmentReference) ensureWallet(walletId AccWalletId) {
	if walletId == internalGraphRoot || GetRefWalletFromId(int64(walletId)) != nil {
		return
	}
	if err := NewRefWallet(int64(walletId), r.capacity).Register(); err != nil {
		panic(err)
	}
}

func (r *environmentReference) allocate(e *env, allocationId accAllocId, now time.Time) {
	allocation := e.Bucket.AllocationsById[allocationId]
	r.ensureWallet(allocation.BelongsTo)
	r.ensureWallet(allocation.Parent)
	source := differentialAllocation{
		Id: int64(allocationId), Recipient: int64(allocation.BelongsTo), Parent: int64(allocation.Parent),
		Quota: allocation.Quota, Start: allocation.Start, End: allocation.End,
	}
	r.allocations[source.Id] = source
	referenceAllocation := &RefAllocation{
		Id: source.Id, BelongsTo: source.Recipient, ParentWallet: source.Parent,
		Quota: source.Quota, Start: source.Start, End: source.End,
	}
	if err := referenceAllocation.Register(); err != nil {
		e.t.Fatalf("register reference allocation %d: %v", allocationId, err)
	}
	wallet := GetRefWalletFromId(source.Recipient)
	group := wallet.AllocationsByParent[source.Parent]
	if group.allocations == nil {
		group = *NewRefAllocGroup()
	}
	group.allocations[source.Id] = false
	wallet.AllocationsByParent[source.Parent] = group
	if allocation.Committed && allocation.Active && !allocation.Retired {
		r.activate(source.Id, now)
	}
	r.compare(e, now, fmt.Sprintf("allocate %d", allocationId))
}

func (r *environmentReference) activate(allocationId int64, now time.Time) {
	allocation := GetRefAllocationFromId(allocationId)
	wallet := GetRefWalletFromId(allocation.BelongsTo)
	group := wallet.AllocationsByParent[allocation.ParentWallet]
	if group.allocations[allocationId] {
		return
	}
	group.allocations[allocationId] = true
	if group.earliestExpiration.IsZero() || allocation.End.Before(group.earliestExpiration) {
		group.earliestExpiration = allocation.End
	}
	wallet.AllocationsByParent[allocation.ParentWallet] = group
	if allocation.ParentWallet != 0 {
		GetRefWalletFromId(allocation.ParentWallet).TotalAllocated += allocation.Quota
	}
	if wallet.ExcessUsage > 0 {
		amount := wallet.ExcessUsage
		wallet.LocalUsage -= amount
		charged, changed := wallet.internalCharge(amount, now)
		wallet.LocalUsage += amount
		wallet.ExcessUsage = amount - charged
		_ = checkRefErrors(changed)
	}
}

func (r *environmentReference) report(e *env, request accapi.ReportUsageRequest, now time.Time) {
	r.sync(e, now)
	owner := accGlobals.OwnersByReference[request.Owner.Reference()]
	wallet := e.Bucket.WalletsByOwner[owner.Id]
	scope := ""
	if request.Description.Scope.Present {
		scope = request.Description.Scope.Value
	}
	key := fmt.Sprintf("%d\n%s", wallet.Id, scope)
	delta := request.Usage
	if request.IsDeltaCharge {
		r.reportCounters[key] += delta
	} else {
		delta = request.Usage - r.reportCounters[key]
		r.reportCounters[key] = request.Usage
	}
	if delta != 0 {
		err := (&RefProductCharge{WalletId: int64(wallet.Id), Amount: delta, Ts: now}).Process()
		if err != nil && !strings.Contains(err.Error(), "not enough credits") {
			e.t.Fatalf("reference report wallet %d delta %d: %v", wallet.Id, delta, err)
		}
	}
	r.compare(e, now, fmt.Sprintf("report wallet %d delta %d", wallet.Id, delta))
}

func (r *environmentReference) scan(e *env, now time.Time) {
	r.sync(e, now)
	r.compare(e, now, "scan")
}

func (r *environmentReference) sync(e *env, now time.Time) {
	allocations := make([]*internalAllocation, 0, len(r.allocations))
	for allocationId := range r.allocations {
		allocations = append(allocations, e.Bucket.AllocationsById[accAllocId(allocationId)])
	}
	slices.SortFunc(allocations, func(a, b *internalAllocation) int {
		if result := a.End.Compare(b.End); result != 0 {
			return result
		}
		if result := a.Start.Compare(b.Start); result != 0 {
			return result
		}
		return int(a.Id - b.Id)
	})
	for _, allocation := range allocations {
		referenceAllocation := GetRefAllocationFromId(int64(allocation.Id))
		group := GetRefWalletFromId(referenceAllocation.BelongsTo).AllocationsByParent[referenceAllocation.ParentWallet]
		if allocation.Retired && !referenceAllocation.Retired {
			if err := referenceAllocation.Retire(); err != nil {
				e.t.Fatalf("retire reference allocation %d: %v", allocation.Id, err)
			}
		} else if allocation.Active && !allocation.Retired && !group.allocations[referenceAllocation.Id] {
			r.activate(referenceAllocation.Id, now)
		}
	}
}

func (r *environmentReference) compare(e *env, now time.Time, operation string) {
	wallets := map[int64]AccWalletId{}
	reverseWallets := map[AccWalletId]int64{internalGraphRoot: 0}
	for walletId := range GlobalRefWallets {
		wallets[walletId] = AccWalletId(walletId)
		reverseWallets[AccWalletId(walletId)] = walletId
	}
	allocations := map[int64]accAllocId{}
	for allocationId := range r.allocations {
		allocations[allocationId] = accAllocId(allocationId)
	}
	core := (&coreDifferentialRunner{env: e, wallets: wallets, allocations: allocations, reverseWallet: reverseWallets}).Snapshot(now)
	reference := (&referenceDifferentialRunner{capacity: r.capacity, allocations: r.allocations, reportCounters: r.reportCounters}).Snapshot(now)
	if difference := compareDifferentialSnapshots(-1, core, reference); difference != nil {
		e.t.Fatalf("reference mismatch after %s: %s: Core=%v reference=%v", operation, difference.Path, difference.Core, difference.Reference)
	}
}

type referenceDifferentialRunner struct {
	capacity       bool
	allocations    map[int64]differentialAllocation
	reportCounters map[string]int64
}

func newReferenceDifferentialRunner(t *testing.T, scenario differentialScenario) *referenceDifferentialRunner {
	t.Helper()
	GlobalRefAllocations = map[int64]*RefAllocation{}
	GlobalRefWallets = map[int64]*RefWallet{}
	runner := &referenceDifferentialRunner{
		capacity:       scenario.Capacity,
		allocations:    map[int64]differentialAllocation{},
		reportCounters: map[string]int64{},
	}
	for _, walletId := range scenario.Wallets {
		if err := NewRefWallet(walletId, scenario.Capacity).Register(); err != nil {
			t.Fatalf("initialize reference wallet %d: %v", walletId, err)
		}
	}
	allocations := slices.Clone(scenario.Allocations)
	slices.SortFunc(allocations, func(a, b differentialAllocation) int { return int(a.Id - b.Id) })
	for _, allocation := range allocations {
		runner.allocations[allocation.Id] = allocation
		referenceAllocation := &RefAllocation{
			Id:           allocation.Id,
			BelongsTo:    allocation.Recipient,
			ParentWallet: allocation.Parent,
			Quota:        allocation.Quota,
			Start:        allocation.Start,
			End:          allocation.End,
		}
		if err := referenceAllocation.Register(); err != nil {
			t.Fatalf("initialize reference allocation %d: %v", allocation.Id, err)
		}
	}
	for _, allocation := range allocations {
		if allocation.Start.After(scenario.InitialTime) {
			continue
		}
		if err := GetRefWalletFromId(allocation.Recipient).AddAllocation(allocation.Id); err != nil {
			t.Fatalf("attach reference allocation %d: %v", allocation.Id, err)
		}
	}
	return runner
}

func (r *referenceDifferentialRunner) Apply(op differentialOperation) error {
	if op.Kind == differentialAdvance {
		retirements := slices.Clone(op.Retirements)
		slices.SortFunc(retirements, func(a, b int64) int {
			aAlloc, bAlloc := r.allocations[a], r.allocations[b]
			if result := aAlloc.End.Compare(bAlloc.End); result != 0 {
				return result
			}
			if result := aAlloc.Start.Compare(bAlloc.Start); result != 0 {
				return result
			}
			return int(a - b)
		})
		for _, allocationId := range retirements {
			if err := (&RefRetireAllocation{AllocationId: allocationId, Ts: op.At}).Process(); err != nil {
				return err
			}
		}
		activations := slices.Clone(op.Activations)
		slices.Sort(activations)
		for _, allocationId := range activations {
			allocation := r.allocations[allocationId]
			if err := GetRefWalletFromId(allocation.Recipient).AddAllocation(allocationId); err != nil {
				return err
			}
		}
		return nil
	}

	counterKey := fmt.Sprintf("%d\n%s", op.Wallet, op.Scope)
	delta := op.Usage
	if op.IsDelta {
		r.reportCounters[counterKey] += delta
	} else {
		delta = op.Usage - r.reportCounters[counterKey]
		r.reportCounters[counterKey] = op.Usage
	}
	if delta == 0 {
		return nil
	}
	err := (&RefProductCharge{WalletId: op.Wallet, Amount: delta, Ts: op.At}).Process()
	if err != nil && !strings.Contains(err.Error(), "not enough credits") {
		return err
	}
	return nil
}

func (r *referenceDifferentialRunner) Snapshot(now time.Time) differentialSnapshot {
	snapshot := differentialSnapshot{}
	for allocationId, source := range r.allocations {
		allocation := GetRefAllocationFromId(allocationId)
		group := GetRefWalletFromId(source.Recipient).AllocationsByParent[source.Parent]
		snapshot.Allocations = append(snapshot.Allocations, differentialAllocationState{
			Id:            allocationId,
			Recipient:     allocation.BelongsTo,
			Parent:        allocation.ParentWallet,
			OriginalQuota: allocation.Quota,
			Retired:       allocation.Retired,
			RetiredUsage:  allocation.RetiredUsage,
			Current:       group.allocations[allocationId],
		})
	}
	for walletId, wallet := range GlobalRefWallets {
		inNode := wallet.LocalUsage
		for childId, childUsage := range wallet.ChildrenUsage {
			if !r.capacity {
				childUsage += wallet.ChildrenRetiredUsage[childId]
			}
			inNode += childUsage
		}
		propagated := int64(0)
		for parentId, group := range wallet.AllocationsByParent {
			flow := group.treeUsage
			contributingQuota := group.totalActiveQuota()
			if !r.capacity {
				flow += group.retiredTreeUsage
				contributingQuota += group.retiredTreeUsage
			}
			propagated += flow
			snapshot.Groups = append(snapshot.Groups, differentialGroupState{
				Recipient:         walletId,
				Parent:            parentId,
				Flow:              flow,
				RetiredUsage:      group.retiredTreeUsage,
				ContributingQuota: contributingQuota,
			})
		}
		maxUsable, _ := wallet.MaxUsable(now)
		snapshot.Wallets = append(snapshot.Wallets, differentialWalletState{
			Id:         walletId,
			LocalUsage: wallet.LocalUsage,
			InNode:     inNode,
			Propagated: propagated,
			Excess:     inNode - propagated,
			MaxUsable:  maxUsable,
			Locked:     maxUsable <= 0,
		})
	}
	sortDifferentialSnapshot(&snapshot)
	return snapshot
}
