package accounting

import (
	"errors"
	"fmt"
	"math"
	"time"

	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

type accountingInvariantMode int

const (
	accountingInvariantModeAutomatic accountingInvariantMode = iota
	accountingInvariantModeDisabled
	accountingInvariantModePanic
	accountingInvariantModeLog
)

// Set this to accountingInvariantModeLog to diagnose production state without terminating the process.
var accountingInvariantChecks = accountingInvariantModeAutomatic

func accountingInvariantModeCurrent() accountingInvariantMode {
	if accountingInvariantChecks != accountingInvariantModeAutomatic {
		return accountingInvariantChecks
	}
	if accGlobals.TestingEnabled || util.DevelopmentModeEnabled() {
		return accountingInvariantModePanic
	}
	return accountingInvariantModeDisabled
}

func handleAccountingInvariantError(operation string, err error) {
	if err == nil {
		return
	}

	message := fmt.Sprintf("accounting invariant violation after %s: %v", operation, err)
	switch accountingInvariantModeCurrent() {
	case accountingInvariantModePanic:
		panic(message)
	case accountingInvariantModeLog:
		log.Error("%s", message)
	}
}

// lCheckAccountingOperation validates only wallets which could have been changed by an operation.
// The caller must hold the bucket lock. A nil wallet set means that the operation can affect the entire bucket.
func lCheckAccountingOperation(operation string, b *internalBucket, now time.Time, walletIds map[AccWalletId]bool, scope *scopedUsage, additionalErrors ...error) {
	if accountingInvariantModeCurrent() == accountingInvariantModeDisabled {
		return
	}

	errs := append([]error(nil), additionalErrors...)
	if scope != nil && scope.Usage < 0 {
		errs = append(errs, fmt.Errorf("scoped usage %q is negative: %d", scope.Key, scope.Usage))
	}

	if walletIds == nil {
		walletIds = make(map[AccWalletId]bool, len(b.WalletsById))
		for walletId := range b.WalletsById {
			walletIds[walletId] = true
		}
	}

	for walletId, included := range walletIds {
		if !included {
			continue
		}
		wallet := b.WalletsById[walletId]
		if wallet == nil {
			errs = append(errs, fmt.Errorf("wallet %d does not exist in bucket %s/%s", walletId, b.Category.Provider, b.Category.Name))
			continue
		}
		errs = append(errs, lValidateAccountingWallet(b, now, wallet)...)
	}

	handleAccountingInvariantError(operation, errors.Join(errs...))
}

// internalValidateAccountingTree performs the non-local validation while obeying the bucket lock boundary.
// It returns a controlled error for malformed topology instead of constructing a residual graph from bad state.
func internalValidateAccountingTree(b *internalBucket, now time.Time) error {
	b.Mu.RLock()
	defer b.Mu.RUnlock()
	return lValidateAccountingTree(b, now)
}

func lValidateAccountingTree(b *internalBucket, now time.Time) error {
	var errs []error
	groupIds := map[accGroupId]*internalGroup{}
	allocationOccurrences := map[accAllocId]int{}

	for ownerId, wallet := range b.WalletsByOwner {
		if wallet == nil {
			errs = append(errs, fmt.Errorf("owner index %d contains a nil wallet", ownerId))
			continue
		}
		if wallet.OwnedBy != ownerId {
			errs = append(errs, fmt.Errorf("owner index %d points to wallet %d owned by %d", ownerId, wallet.Id, wallet.OwnedBy))
		}
		if b.WalletsById[wallet.Id] != wallet {
			errs = append(errs, fmt.Errorf("owner index wallet %d is missing or replaced in the ID index", wallet.Id))
		}
	}

	for walletId, wallet := range b.WalletsById {
		if wallet == nil {
			errs = append(errs, fmt.Errorf("wallet ID index %d contains nil", walletId))
			continue
		}
		if wallet.Id != walletId {
			errs = append(errs, fmt.Errorf("wallet ID index %d contains wallet %d", walletId, wallet.Id))
		}
		if b.WalletsByOwner[wallet.OwnedBy] != wallet {
			errs = append(errs, fmt.Errorf("wallet %d is missing or replaced in owner index %d", wallet.Id, wallet.OwnedBy))
		}

		for parentId, group := range wallet.AllocationsByParent {
			if group == nil {
				errs = append(errs, fmt.Errorf("wallet %d has a nil group for parent %d", wallet.Id, parentId))
				continue
			}
			if previous, duplicate := groupIds[group.Id]; duplicate && previous != group {
				errs = append(errs, fmt.Errorf("allocation group ID %d is reused", group.Id))
			} else {
				groupIds[group.Id] = group
			}
			for allocationId := range group.Allocations {
				allocationOccurrences[allocationId]++
			}
		}

		errs = append(errs, lValidateAccountingWallet(b, now, wallet)...)
	}

	for allocationId, allocation := range b.AllocationsById {
		if allocation == nil {
			errs = append(errs, fmt.Errorf("allocation ID index %d contains nil", allocationId))
			continue
		}
		if allocation.Id != allocationId {
			errs = append(errs, fmt.Errorf("allocation ID index %d contains allocation %d", allocationId, allocation.Id))
		}
		if count := allocationOccurrences[allocationId]; count != 1 {
			errs = append(errs, fmt.Errorf("allocation %d occurs in %d allocation groups", allocationId, count))
		}
	}
	for allocationId, count := range allocationOccurrences {
		if _, exists := b.AllocationsById[allocationId]; !exists {
			errs = append(errs, fmt.Errorf("allocation %d occurs in %d groups but not in the allocation index", allocationId, count))
		}
	}

	errs = append(errs, lValidateAccountingAcyclic(b)...)
	return errors.Join(errs...)
}

func lValidateAccountingWallet(b *internalBucket, now time.Time, wallet *internalWallet) []error {
	var errs []error
	if wallet.Id == internalGraphRoot {
		errs = append(errs, errors.New("synthetic graph root is stored as a wallet"))
	}
	if wallet.LocalUsage < 0 {
		errs = append(errs, fmt.Errorf("wallet %d has negative local usage: %d", wallet.Id, wallet.LocalUsage))
	}

	canCheckLock := true
	inNode := wallet.LocalUsage
	for childId, childUsage := range wallet.ChildrenUsage {
		if childUsage < 0 {
			errs = append(errs, fmt.Errorf("wallet %d has negative child usage for %d: %d", wallet.Id, childId, childUsage))
		}
		var overflow bool
		inNode, overflow = checkedAccountingAdd(inNode, childUsage)
		if overflow {
			errs = append(errs, fmt.Errorf("wallet %d local and child usage overflow int64", wallet.Id))
			canCheckLock = false
		}

		child := b.WalletsById[childId]
		if child == nil {
			errs = append(errs, fmt.Errorf("wallet %d references missing child %d", wallet.Id, childId))
			continue
		}
		group := child.AllocationsByParent[wallet.Id]
		if group == nil {
			errs = append(errs, fmt.Errorf("wallet %d has child usage for %d without an allocation group", wallet.Id, childId))
		} else if group.TreeUsage != childUsage {
			errs = append(errs, fmt.Errorf("wallet %d child usage for %d is %d, group tree usage is %d", wallet.Id, childId, childUsage, group.TreeUsage))
		}
	}

	propagated := int64(0)
	totalContributing := int64(0)
	for parentId, group := range wallet.AllocationsByParent {
		if group == nil {
			errs = append(errs, fmt.Errorf("wallet %d has nil allocation group for parent %d", wallet.Id, parentId))
			canCheckLock = false
			continue
		}
		if group.AssociatedWallet != wallet.Id || group.ParentWallet != parentId {
			errs = append(errs, fmt.Errorf("wallet %d group %d has endpoints %d -> %d instead of %d -> %d", wallet.Id, group.Id, group.ParentWallet, group.AssociatedWallet, parentId, wallet.Id))
			canCheckLock = false
		}
		if group.TreeUsage < 0 {
			errs = append(errs, fmt.Errorf("group %d has negative tree usage: %d", group.Id, group.TreeUsage))
			canCheckLock = false
		}
		var overflow bool
		propagated, overflow = checkedAccountingAdd(propagated, group.TreeUsage)
		if overflow {
			errs = append(errs, fmt.Errorf("wallet %d propagated usage overflows int64", wallet.Id))
			canCheckLock = false
		}

		if parentId != internalGraphRoot {
			parent := b.WalletsById[parentId]
			if parent == nil {
				errs = append(errs, fmt.Errorf("group %d references missing parent wallet %d", group.Id, parentId))
				canCheckLock = false
			} else if childUsage, exists := parent.ChildrenUsage[wallet.Id]; !exists || childUsage != group.TreeUsage {
				errs = append(errs, fmt.Errorf("group %d tree usage %d is not mirrored by parent wallet %d", group.Id, group.TreeUsage, parentId))
			}
		}

		contributing := int64(0)
		for allocationId := range group.Allocations {
			allocation := b.AllocationsById[allocationId]
			if allocation == nil {
				errs = append(errs, fmt.Errorf("group %d references missing allocation %d", group.Id, allocationId))
				canCheckLock = false
				continue
			}
			allocationErrors := lValidateAccountingAllocation(b, now, wallet, group, allocation)
			errs = append(errs, allocationErrors...)
			if len(allocationErrors) > 0 {
				canCheckLock = false
			}
			if allocation.Committed && allocation.Active && (!allocation.Retired || !b.IsCapacityBased()) {
				contributing, overflow = checkedAccountingAdd(contributing, allocation.Quota)
				if overflow {
					errs = append(errs, fmt.Errorf("group %d contributing quota overflows int64", group.Id))
					canCheckLock = false
				}
			}
		}
		if group.TreeUsage > contributing {
			errs = append(errs, fmt.Errorf("group %d tree usage %d exceeds contributing quota %d", group.Id, group.TreeUsage, contributing))
			canCheckLock = false
		}
		totalContributing, overflow = checkedAccountingAdd(totalContributing, contributing)
		if overflow {
			errs = append(errs, fmt.Errorf("wallet %d total contributing quota overflows int64", wallet.Id))
			canCheckLock = false
		}
	}

	if propagated > inNode {
		errs = append(errs, fmt.Errorf("wallet %d propagates %d but only has %d local and child usage", wallet.Id, propagated, inNode))
	}
	if canCheckLock {
		maxUsable, err := lAccountingMaxUsableSafely(b, now, wallet)
		if err != nil {
			errs = append(errs, fmt.Errorf("wallet %d cannot calculate MaxUsable: %w", wallet.Id, err))
		} else if wallet.WasLocked != (maxUsable <= 0) {
			errs = append(errs, fmt.Errorf("wallet %d WasLocked=%t but MaxUsable=%d", wallet.Id, wallet.WasLocked, maxUsable))
		}
	}
	return errs
}

// lValidateAccountingGraphState checks the operation-local ancestor graph without recursively calculating MaxUsable.
// The caller must hold the bucket lock.
func lValidateAccountingGraphState(b *internalBucket, leaf *internalWallet) error {
	visited := map[AccWalletId]bool{internalGraphRoot: true}
	queue := []*internalWallet{leaf}
	var errs []error

	for len(queue) > 0 {
		wallet := queue[0]
		queue = queue[1:]
		if wallet == nil || visited[wallet.Id] {
			continue
		}
		visited[wallet.Id] = true

		inNode := wallet.LocalUsage
		if inNode < 0 {
			errs = append(errs, fmt.Errorf("wallet %d has negative local usage %d", wallet.Id, inNode))
		}
		for childId, childUsage := range wallet.ChildrenUsage {
			if childUsage < 0 {
				errs = append(errs, fmt.Errorf("wallet %d has negative child usage %d for child %d", wallet.Id, childUsage, childId))
			}
			var overflow bool
			inNode, overflow = checkedAccountingAdd(inNode, childUsage)
			if overflow {
				errs = append(errs, fmt.Errorf("wallet %d usage in node overflows int64", wallet.Id))
			}
		}

		propagated := int64(0)
		totalContributing := int64(0)
		for parentId, group := range wallet.AllocationsByParent {
			if group == nil {
				errs = append(errs, fmt.Errorf("wallet %d has nil allocation group for parent %d", wallet.Id, parentId))
				continue
			}
			contributing := int64(0)
			for allocationId := range group.Allocations {
				allocation := b.AllocationsById[allocationId]
				if allocation == nil {
					errs = append(errs, fmt.Errorf("group %d references missing allocation %d", group.Id, allocationId))
					continue
				}
				if allocation.Committed && allocation.Active && (!allocation.Retired || !b.IsCapacityBased()) {
					var overflow bool
					contributing, overflow = checkedAccountingAdd(contributing, allocation.Quota)
					if overflow {
						errs = append(errs, fmt.Errorf("group %d contributing quota overflows int64", group.Id))
					}
				}
			}
			if group.TreeUsage < 0 || group.TreeUsage > contributing {
				errs = append(errs, fmt.Errorf("group %d tree usage %d is outside contributing quota [0,%d]", group.Id, group.TreeUsage, contributing))
			}
			var quotaOverflow bool
			totalContributing, quotaOverflow = checkedAccountingAdd(totalContributing, contributing)
			if quotaOverflow {
				errs = append(errs, fmt.Errorf("wallet %d contributing quota overflows int64", wallet.Id))
			}
			var overflow bool
			propagated, overflow = checkedAccountingAdd(propagated, group.TreeUsage)
			if overflow {
				errs = append(errs, fmt.Errorf("wallet %d propagated usage overflows int64", wallet.Id))
			}

			if parentId != internalGraphRoot {
				parent := b.WalletsById[parentId]
				if parent == nil {
					errs = append(errs, fmt.Errorf("group %d references missing parent %d", group.Id, parentId))
				} else {
					if mirrored, ok := parent.ChildrenUsage[wallet.Id]; !ok || mirrored != group.TreeUsage {
						errs = append(errs, fmt.Errorf("group %d usage %d is not mirrored by parent %d", group.Id, group.TreeUsage, parentId))
					}
					queue = append(queue, parent)
				}
			}
		}
		if propagated > inNode {
			errs = append(errs, fmt.Errorf("wallet %d propagates %d from only %d usage", wallet.Id, propagated, inNode))
		}

		totalAllocated := int64(0)
		for childId := range wallet.ChildrenUsage {
			child := b.WalletsById[childId]
			if child == nil {
				continue
			}
			group := child.AllocationsByParent[wallet.Id]
			if group == nil {
				continue
			}
			for allocationId := range group.Allocations {
				allocation := b.AllocationsById[allocationId]
				if allocation != nil && allocation.Committed && allocation.Active && (!allocation.Retired || !b.IsCapacityBased()) {
					var overflow bool
					totalAllocated, overflow = checkedAccountingAdd(totalAllocated, allocation.Quota)
					if overflow {
						errs = append(errs, fmt.Errorf("wallet %d allocated quota overflows int64", wallet.Id))
					}
				}
			}
		}
		totalWithLocal, totalOverflow := checkedAccountingAdd(totalAllocated, wallet.LocalUsage)
		overAllocation, subtractionOverflow := checkedAccountingSub(totalWithLocal, totalContributing)
		if totalOverflow || subtractionOverflow {
			errs = append(errs, fmt.Errorf("wallet %d over-allocation arithmetic overflows int64", wallet.Id))
		} else if overAllocation > 0 {
			overAllocationUsed := inNode - propagated
			if overAllocationUsed < 0 || overAllocationUsed > overAllocation {
				errs = append(errs, fmt.Errorf("wallet %d uses %d of over-allocation capacity %d", wallet.Id, overAllocationUsed, overAllocation))
			}
		}
	}

	return errors.Join(errs...)
}

func lValidateAccountingAllocation(b *internalBucket, now time.Time, wallet *internalWallet, group *internalGroup, allocation *internalAllocation) []error {
	var errs []error
	if allocation.BelongsTo != wallet.Id || allocation.Parent != group.ParentWallet || allocation.Group != group.Id {
		errs = append(errs, fmt.Errorf("allocation %d metadata does not match group %d", allocation.Id, group.Id))
	}
	if allocation.Quota < 0 || allocation.RetiredUsage < 0 || allocation.RetiredQuota < 0 {
		errs = append(errs, fmt.Errorf("allocation %d has negative quota state: quota=%d retiredUsage=%d retiredQuota=%d", allocation.Id, allocation.Quota, allocation.RetiredUsage, allocation.RetiredQuota))
	}
	if allocation.Start.After(allocation.End) {
		errs = append(errs, fmt.Errorf("allocation %d has invalid interval [%s, %s)", allocation.Id, allocation.Start, allocation.End))
	}
	if allocation.Retired && !allocation.Active {
		errs = append(errs, fmt.Errorf("allocation %d is retired but was never activated", allocation.Id))
	}
	if allocation.Committed && !allocation.Retired && !now.Before(allocation.End) {
		errs = append(errs, fmt.Errorf("allocation %d has passed its exclusive end but is not retired", allocation.Id))
	}
	if allocation.Committed && !allocation.Retired && !now.Before(allocation.Start) && !allocation.Active {
		errs = append(errs, fmt.Errorf("allocation %d is within its validity interval but inactive", allocation.Id))
	}
	if allocation.Retired {
		if b.IsCapacityBased() && allocation.RetiredUsage > allocation.RetiredQuota {
			errs = append(errs, fmt.Errorf("capacity allocation %d retired usage %d exceeds retired quota %d", allocation.Id, allocation.RetiredUsage, allocation.RetiredQuota))
		}
		if !b.IsCapacityBased() && allocation.Quota != allocation.RetiredUsage {
			errs = append(errs, fmt.Errorf("periodic allocation %d quota %d does not preserve retired usage %d", allocation.Id, allocation.Quota, allocation.RetiredUsage))
		}
	}
	return errs
}

func lValidateAccountingAcyclic(b *internalBucket) []error {
	const (
		unvisited = iota
		visiting
		visited
	)
	state := map[AccWalletId]int{}
	var errs []error
	type frame struct {
		walletId AccWalletId
		parents  []AccWalletId
		next     int
	}

	for walletId := range b.WalletsById {
		if state[walletId] != unvisited {
			continue
		}

		state[walletId] = visiting
		stack := []frame{{walletId: walletId, parents: lAccountingParentIds(b.WalletsById[walletId])}}
		for len(stack) > 0 {
			current := &stack[len(stack)-1]
			if current.next == len(current.parents) {
				state[current.walletId] = visited
				stack = stack[:len(stack)-1]
				continue
			}

			parentId := current.parents[current.next]
			current.next++
			switch state[parentId] {
			case visiting:
				errs = append(errs, fmt.Errorf("allocation graph contains a cycle through wallets %d and %d", current.walletId, parentId))
			case unvisited:
				state[parentId] = visiting
				stack = append(stack, frame{walletId: parentId, parents: lAccountingParentIds(b.WalletsById[parentId])})
			}
		}
	}
	return errs
}

func lAccountingParentIds(wallet *internalWallet) []AccWalletId {
	if wallet == nil {
		return nil
	}
	parents := make([]AccWalletId, 0, len(wallet.AllocationsByParent))
	for parentId := range wallet.AllocationsByParent {
		if parentId != internalGraphRoot {
			parents = append(parents, parentId)
		}
	}
	return parents
}

func checkedAccountingAdd(a, b int64) (int64, bool) {
	if (b > 0 && a > math.MaxInt64-b) || (b < 0 && a < math.MinInt64-b) {
		return 0, true
	}
	return a + b, false
}

func checkedAccountingSub(a, b int64) (int64, bool) {
	if (b > 0 && a < math.MinInt64+b) || (b < 0 && a > math.MaxInt64+b) {
		return 0, true
	}
	return a - b, false
}

func lAccountingMaxUsableSafely(b *internalBucket, now time.Time, wallet *internalWallet) (result int64, err error) {
	defer func() {
		if recovered := recover(); recovered != nil {
			err = fmt.Errorf("residual graph rejected malformed state: %v", recovered)
		}
	}()
	return lInternalMaxUsable(b, now, wallet), nil
}
