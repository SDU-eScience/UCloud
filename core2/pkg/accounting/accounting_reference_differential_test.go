package accounting

import (
	"fmt"
	"slices"
	"strings"
	"testing"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/util"
)

type differentialOperationKind byte

const (
	differentialReport differentialOperationKind = iota
	differentialAdvance
)

type differentialAllocation struct {
	Id        int64
	Recipient int64
	Parent    int64
	Quota     int64
	Start     time.Time
	End       time.Time
}

type differentialOperation struct {
	Kind          differentialOperationKind
	At            time.Time
	Wallet        int64
	Usage         int64
	IsDelta       bool
	Scope         string
	Activations   []int64
	Retirements   []int64
	ReferenceNote string
}

func (op differentialOperation) String() string {
	if op.Kind == differentialAdvance {
		return fmt.Sprintf("advance to %s; activate allocations %v; retire allocations %v", op.At.Format(time.RFC3339), op.Activations, op.Retirements)
	}
	mode := "absolute"
	if op.IsDelta {
		mode = "delta"
	}
	scope := "unscoped"
	if op.Scope != "" {
		scope = fmt.Sprintf("scope=%q", op.Scope)
	}
	result := fmt.Sprintf("%s report wallet=%d %s usage=%d at %s", mode, op.Wallet, scope, op.Usage, op.At.Format(time.RFC3339))
	if op.ReferenceNote != "" {
		result += "; reference: " + op.ReferenceNote
	}
	return result
}

type differentialScenario struct {
	Capacity    bool
	InitialTime time.Time
	Wallets     []int64
	Allocations []differentialAllocation
	Operations  []differentialOperation
}

type differentialAllocationState struct {
	Id            int64
	Recipient     int64
	Parent        int64
	OriginalQuota int64
	Retired       bool
	RetiredUsage  int64
	Current       bool
}

type differentialGroupState struct {
	Recipient         int64
	Parent            int64
	Flow              int64
	RetiredUsage      int64
	ContributingQuota int64
}

type differentialWalletState struct {
	Id         int64
	LocalUsage int64
	InNode     int64
	Propagated int64
	Excess     int64
	MaxUsable  int64
	Locked     bool
}

type differentialSnapshot struct {
	Allocations []differentialAllocationState
	Groups      []differentialGroupState
	Wallets     []differentialWalletState
}

type accountingDifference struct {
	Operation int
	Path      string
	Core      any
	Reference any
}

type coreDifferentialRunner struct {
	env           *env
	wallets       map[int64]AccWalletId
	allocations   map[int64]accAllocId
	reverseWallet map[AccWalletId]int64
}

func newCoreDifferentialRunner(t *testing.T, scenario differentialScenario) *coreDifferentialRunner {
	t.Helper()
	category := timeCategory
	if scenario.Capacity {
		category = capacityCategory
	}
	e := newEnv(t, category)
	_ = e.diagram.Close()
	runner := &coreDifferentialRunner{
		env:           e,
		wallets:       map[int64]AccWalletId{},
		allocations:   map[int64]accAllocId{},
		reverseWallet: map[AccWalletId]int64{},
	}
	for _, walletId := range scenario.Wallets {
		owner := internalOwnerByReference(fmt.Sprintf("differential-wallet-%d", walletId))
		coreId := internalWalletByOwner(e.Bucket, scenario.InitialTime, owner.Id)
		runner.wallets[walletId] = coreId
		runner.reverseWallet[coreId] = walletId
	}
	for _, allocation := range scenario.Allocations {
		coreId, err := internalAllocateNoCommit(
			scenario.InitialTime,
			e.Bucket,
			allocation.Start,
			allocation.End,
			allocation.Quota,
			runner.wallets[allocation.Recipient],
			runner.wallets[allocation.Parent],
			util.OptNone[accGrantId](),
		)
		if err != nil {
			t.Fatalf("initialize Core allocation %d: %v", allocation.Id, err)
		}
		internalCommitAllocation(e.Bucket, scenario.InitialTime, coreId)
		runner.allocations[allocation.Id] = coreId
	}
	return runner
}

func (r *coreDifferentialRunner) Apply(op differentialOperation) error {
	if op.Kind == differentialAdvance {
		internalScanAllocations(r.env.Bucket, op.At)
		return nil
	}
	owner := accGlobals.OwnersById[r.env.Bucket.WalletsById[r.wallets[op.Wallet]].OwnedBy]
	request := accapi.ReportUsageRequest{
		IsDeltaCharge: op.IsDelta,
		Owner:         owner.WalletOwner(),
		CategoryIdV2:  r.env.Bucket.Category.ToId(),
		Usage:         op.Usage,
	}
	if op.Scope != "" {
		request.Description.Scope = util.OptValue(op.Scope)
	}
	_, err := internalReportUsage(op.At, request)
	if err != nil {
		return err
	}
	return nil
}

func (r *coreDifferentialRunner) Snapshot(now time.Time) differentialSnapshot {
	b := r.env.Bucket
	b.Mu.RLock()
	defer b.Mu.RUnlock()
	snapshot := differentialSnapshot{}
	for scenarioId, coreId := range r.allocations {
		allocation := b.AllocationsById[coreId]
		originalQuota := allocation.Quota
		if allocation.Retired {
			originalQuota = allocation.RetiredQuota
		}
		snapshot.Allocations = append(snapshot.Allocations, differentialAllocationState{
			Id:            scenarioId,
			Recipient:     r.reverseWallet[allocation.BelongsTo],
			Parent:        r.reverseWallet[allocation.Parent],
			OriginalQuota: originalQuota,
			Retired:       allocation.Retired,
			RetiredUsage:  allocation.RetiredUsage,
			Current:       allocation.Committed && allocation.Active && !allocation.Retired,
		})
	}
	for scenarioId, coreId := range r.wallets {
		wallet := b.WalletsById[coreId]
		inNode := lInternalWalletTotalUsageInNode(b, wallet)
		propagated := lInternalWalletTotalPropagatedUsage(b, wallet)
		maxUsable := lInternalMaxUsable(b, now, wallet)
		snapshot.Wallets = append(snapshot.Wallets, differentialWalletState{
			Id:         scenarioId,
			LocalUsage: wallet.LocalUsage,
			InNode:     inNode,
			Propagated: propagated,
			Excess:     inNode - propagated,
			MaxUsable:  maxUsable,
			Locked:     wallet.WasLocked,
		})
		for parentId, group := range wallet.AllocationsByParent {
			retiredUsage := int64(0)
			for allocationId := range group.Allocations {
				retiredUsage += b.AllocationsById[allocationId].RetiredUsage
			}
			snapshot.Groups = append(snapshot.Groups, differentialGroupState{
				Recipient:         scenarioId,
				Parent:            r.reverseWallet[parentId],
				Flow:              group.TreeUsage,
				RetiredUsage:      retiredUsage,
				ContributingQuota: lInternalGroupTotalQuotaContributing(b, group),
			})
		}
	}
	sortDifferentialSnapshot(&snapshot)
	return snapshot
}

func sortDifferentialSnapshot(snapshot *differentialSnapshot) {
	slices.SortFunc(snapshot.Allocations, func(a, b differentialAllocationState) int { return int(a.Id - b.Id) })
	slices.SortFunc(snapshot.Wallets, func(a, b differentialWalletState) int { return int(a.Id - b.Id) })
	slices.SortFunc(snapshot.Groups, func(a, b differentialGroupState) int {
		if a.Recipient != b.Recipient {
			return int(a.Recipient - b.Recipient)
		}
		return int(a.Parent - b.Parent)
	})
}

func compareDifferentialSnapshots(operation int, core, reference differentialSnapshot) *accountingDifference {
	if len(core.Allocations) != len(reference.Allocations) {
		return &accountingDifference{operation, "allocations.length", len(core.Allocations), len(reference.Allocations)}
	}
	for i := range core.Allocations {
		a, b := core.Allocations[i], reference.Allocations[i]
		path := fmt.Sprintf("allocations[%d]", a.Id)
		for _, field := range []struct {
			name string
			a    any
			b    any
		}{{"identity", [3]int64{a.Id, a.Recipient, a.Parent}, [3]int64{b.Id, b.Recipient, b.Parent}}, {"originalQuota", a.OriginalQuota, b.OriginalQuota}, {"retired", a.Retired, b.Retired}, {"retiredUsage", a.RetiredUsage, b.RetiredUsage}, {"current", a.Current, b.Current}} {
			if fmt.Sprint(field.a) != fmt.Sprint(field.b) {
				return &accountingDifference{operation, path + "." + field.name, field.a, field.b}
			}
		}
	}
	if len(core.Groups) != len(reference.Groups) {
		return &accountingDifference{operation, "groups.length", len(core.Groups), len(reference.Groups)}
	}
	for i := range core.Groups {
		a, b := core.Groups[i], reference.Groups[i]
		path := fmt.Sprintf("groups[recipient=%d,parent=%d]", a.Recipient, a.Parent)
		for _, field := range []struct {
			name string
			a    int64
			b    int64
		}{{"recipient", a.Recipient, b.Recipient}, {"parent", a.Parent, b.Parent}, {"flow", a.Flow, b.Flow}, {"retiredUsage", a.RetiredUsage, b.RetiredUsage}, {"contributingQuota", a.ContributingQuota, b.ContributingQuota}} {
			if field.a != field.b {
				return &accountingDifference{operation, path + "." + field.name, field.a, field.b}
			}
		}
	}
	if len(core.Wallets) != len(reference.Wallets) {
		return &accountingDifference{operation, "wallets.length", len(core.Wallets), len(reference.Wallets)}
	}
	for i := range core.Wallets {
		a, b := core.Wallets[i], reference.Wallets[i]
		path := fmt.Sprintf("wallets[%d]", a.Id)
		for _, field := range []struct {
			name string
			a    int64
			b    int64
		}{{"id", a.Id, b.Id}, {"localUsage", a.LocalUsage, b.LocalUsage}, {"inNode", a.InNode, b.InNode}, {"propagated", a.Propagated, b.Propagated}, {"excess", a.Excess, b.Excess}, {"maxUsable", a.MaxUsable, b.MaxUsable}} {
			if field.a != field.b {
				return &accountingDifference{operation, path + "." + field.name, field.a, field.b}
			}
		}
		if a.Locked != b.Locked {
			return &accountingDifference{operation, path + ".locked", a.Locked, b.Locked}
		}
	}
	return nil
}

func runDifferentialScenario(t *testing.T, scenario differentialScenario) {
	t.Helper()
	core := newCoreDifferentialRunner(t, scenario)
	reference := newReferenceDifferentialRunner(t, scenario)
	defer DestroyRefWalletHierarchy()

	if difference := compareDifferentialSnapshots(-1, core.Snapshot(scenario.InitialTime), reference.Snapshot(scenario.InitialTime)); difference != nil {
		failDifferentialScenario(t, scenario, difference)
	}
	for operationIndex, operation := range scenario.Operations {
		if err := core.Apply(operation); err != nil {
			t.Fatalf("Core rejected operation %d (%s): %v", operationIndex, operation, err)
		}
		if err := reference.Apply(operation); err != nil {
			t.Fatalf("reference rejected operation %d (%s): %v", operationIndex, operation, err)
		}
		if difference := compareDifferentialSnapshots(operationIndex, core.Snapshot(operation.At), reference.Snapshot(operation.At)); difference != nil {
			failDifferentialScenario(t, scenario, difference)
		}
	}
}

func failDifferentialScenario(t *testing.T, scenario differentialScenario, difference *accountingDifference) {
	t.Helper()
	trace := strings.Builder{}
	for i, operation := range scenario.Operations {
		if difference.Operation >= 0 && i > difference.Operation {
			break
		}
		fmt.Fprintf(&trace, "\n  %d. %s", i, operation)
	}
	t.Fatalf("accounting implementations diverged after operation %d\npath: %s\nCore: %v\nreference: %v\ncapacity: %t\noperation trace:%s", difference.Operation, difference.Path, difference.Core, difference.Reference, scenario.Capacity, trace.String())
}

func FuzzAccountingReferenceAgreement(f *testing.F) {
	// The strict profile uses trees, where routing has one path, and retires at
	// most one allocation per group. Multi-parent cost choices and repeated
	// capacity retirement are known reference limitations and remain covered by
	// FuzzAccountingGraphOperations until differences can be classified.
	for _, capacity := range []bool{false, true} {
		for _, scoped := range []bool{false, true} {
			for shape := byte(0); shape < 3; shape++ {
				f.Add(capacity, scoped, shape, []byte{3 + shape, 7, 11, 17, 23, 31, 41, 53})
			}
		}
	}

	f.Fuzz(func(t *testing.T, capacity, scoped bool, shape byte, input []byte) {
		runDifferentialScenario(t, makeDifferentialScenario(capacity, scoped, shape, input))
	})
}

func makeDifferentialScenario(capacity, scoped bool, shape byte, input []byte) differentialScenario {
	data := fuzzAccountingBytes{data: input}
	base := time.Now().UTC().Truncate(time.Second)
	initialTime := base.Add(-2 * time.Hour)
	earlyEnd := base.Add(-time.Hour)
	lateEnd := base.Add(time.Hour)
	start := base.Add(-3 * time.Hour)

	walletCount := 1
	switch shape % 3 {
	case 1:
		walletCount = 2 + int(data.next()%3)
	case 2:
		walletCount = 4 + int(data.next()%3)
	}
	scenario := differentialScenario{Capacity: capacity, InitialTime: initialTime}
	for walletId := int64(1); walletId <= int64(walletCount); walletId++ {
		scenario.Wallets = append(scenario.Wallets, walletId)
	}

	earlyIds := []int64{}
	lateIds := []int64{}
	allocationId := int64(1)
	for walletId := int64(1); walletId <= int64(walletCount); walletId++ {
		parent := int64(0)
		if walletId > 1 {
			if shape%3 == 1 {
				parent = walletId - 1
			} else {
				parent = walletId / 2
			}
		}
		quota := int64(500)
		if parent != 0 {
			quota = 30 + int64(data.next()%51)
		}
		scenario.Allocations = append(scenario.Allocations,
			differentialAllocation{Id: allocationId, Recipient: walletId, Parent: parent, Quota: quota, Start: start, End: earlyEnd},
			differentialAllocation{Id: allocationId + 1, Recipient: walletId, Parent: parent, Quota: quota, Start: earlyEnd, End: lateEnd},
		)
		earlyIds = append(earlyIds, allocationId)
		lateIds = append(lateIds, allocationId+1)
		allocationId += 2
	}

	reportTime := initialTime.Add(10 * time.Minute)
	for walletId := int64(1); walletId <= int64(walletCount); walletId++ {
		first := int64(1 + data.next()%5)
		second := int64(1 + data.next()%5)
		firstScope := ""
		secondScope := ""
		if scoped {
			firstScope = "scope-a"
			secondScope = "scope-b"
		}
		scenario.Operations = append(scenario.Operations,
			differentialOperation{Kind: differentialReport, At: reportTime, Wallet: walletId, Usage: first, Scope: firstScope, ReferenceNote: fmt.Sprintf("local delta=%d", first)},
			differentialOperation{Kind: differentialReport, At: reportTime.Add(time.Minute), Wallet: walletId, Usage: second, IsDelta: true, Scope: secondScope, ReferenceNote: fmt.Sprintf("local delta=%d", second)},
		)
	}
	scenario.Operations = append(scenario.Operations, differentialOperation{Kind: differentialAdvance, At: earlyEnd, Activations: lateIds, Retirements: earlyIds})

	for walletId := int64(1); walletId <= int64(walletCount); walletId++ {
		increase := int64(1 + data.next()%5)
		scope := ""
		current := int64(0)
		if scoped {
			scope = "scope-a"
		}
		for _, operation := range scenario.Operations {
			if operation.Kind == differentialReport && operation.Wallet == walletId && operation.Scope == scope {
				if operation.IsDelta {
					current += operation.Usage
				} else {
					current = operation.Usage
				}
			}
		}
		scenario.Operations = append(scenario.Operations, differentialOperation{
			Kind: differentialReport, At: earlyEnd.Add(time.Minute), Wallet: walletId, Usage: current + increase, Scope: scope,
			ReferenceNote: fmt.Sprintf("local delta=%d", increase),
		})
	}
	return scenario
}
