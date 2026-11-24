package accounting

import (
	"math"
	"testing"
)

func TestRecomputeSnapshotWalletValues(t *testing.T) {
	wallet := &internalWallet{
		Id:         1,
		LocalUsage: 20,
		ChildrenUsage: map[AccWalletId]int64{
			2: 15,
		},
		AllocationsByParent: map[AccWalletId]*internalGroup{
			0: {TreeUsage: 10},
		},
	}
	bucket := &internalBucket{
		AllocationsById: map[accAllocId]*internalAllocation{
			1: {Parent: 1, Quota: 100, Active: true, Committed: true},
			2: {Parent: 1, Quota: 500, Committed: true},
			3: {Parent: 1, Retired: true, RetiredQuota: 70, Active: true, Committed: true},
			4: {Parent: 2, Quota: 1_000, Active: true, Committed: true},
		},
	}

	values, overflowedFields := recomputeSnapshotWalletValues(bucket, wallet)
	if len(overflowedFields) != 0 {
		t.Fatalf("unexpected overflow: %v", overflowedFields)
	}
	if values.ExcessUsage != 25 {
		t.Errorf("excess usage: got %d, want 25", values.ExcessUsage)
	}
	if values.TotalAllocated != 100 {
		t.Errorf("total allocated: got %d, want 100", values.TotalAllocated)
	}
	if values.TotalRetiredAllocated != 70 {
		t.Errorf("total retired allocated: got %d, want 70", values.TotalRetiredAllocated)
	}
}

func TestRecomputeSnapshotWalletValuesReportsOverflow(t *testing.T) {
	wallet := &internalWallet{
		Id:                  1,
		LocalUsage:          math.MaxInt64,
		ChildrenUsage:       map[AccWalletId]int64{2: 1},
		AllocationsByParent: map[AccWalletId]*internalGroup{},
	}
	bucket := &internalBucket{
		AllocationsById: map[accAllocId]*internalAllocation{
			1: {Parent: 1, Quota: math.MaxInt64, Active: true, Committed: true},
			2: {Parent: 1, Quota: 1, Active: true, Committed: true},
			3: {Parent: 1, Retired: true, RetiredQuota: math.MaxInt64, Committed: true},
			4: {Parent: 1, Retired: true, RetiredQuota: 1, Committed: true},
		},
	}

	_, overflowedFields := recomputeSnapshotWalletValues(bucket, wallet)
	want := []string{"excess usage", "total allocated", "total retired allocated"}
	if len(overflowedFields) != len(want) {
		t.Fatalf("overflowed fields: got %v, want %v", overflowedFields, want)
	}
	for i := range want {
		if overflowedFields[i] != want[i] {
			t.Errorf("overflowed field %d: got %q, want %q", i, overflowedFields[i], want[i])
		}
	}
}

func TestAppendSnapshotValueDivergence(t *testing.T) {
	var findings []snapshotFinding
	affected := map[AccWalletId]bool{}
	appendSnapshotValueDivergence(&findings, affected, 42, "value-divergence", "value", 0, 9)

	if len(findings) != 1 {
		t.Fatalf("findings: got %d, want 1", len(findings))
	}
	if findings[0].PersistedValue == nil || *findings[0].PersistedValue != 0 {
		t.Errorf("persisted value: got %v, want 0", findings[0].PersistedValue)
	}
	if findings[0].RecomputedValue == nil || *findings[0].RecomputedValue != 9 {
		t.Errorf("recomputed value: got %v, want 9", findings[0].RecomputedValue)
	}
	if !affected[42] {
		t.Error("wallet 42 was not marked affected")
	}

	appendSnapshotValueDivergence(&findings, affected, 42, "value-divergence", "value", 9, 9)
	if len(findings) != 1 {
		t.Errorf("equal values added a finding")
	}
}
