package accounting

import (
	"database/sql"
	"testing"
	"time"
)

func TestPlanFindingsRepairCyclesDeletesHighestAllocationUntilAGroupIsEmpty(t *testing.T) {
	groups := []findingsRepairGroupRow{
		{Id: 10, ParentWallet: sql.NullInt64{Int64: 1, Valid: true}, AssociatedWallet: 2},
		{Id: 11, ParentWallet: sql.NullInt64{Int64: 2, Valid: true}, AssociatedWallet: 1},
	}
	allocations := []findingsRepairAllocationRow{
		{Id: 5, GroupId: 10},
		{Id: 9, GroupId: 10},
		{Id: 8, GroupId: 11},
	}

	plan := planFindingsRepairCycles(groups, allocations)
	if len(plan.AllocationDeletions) != 2 || plan.AllocationDeletions[0].Id != 9 || plan.AllocationDeletions[1].Id != 8 {
		t.Fatalf("allocation deletions = %+v", plan.AllocationDeletions)
	}
	if len(plan.GroupDeletions) != 1 || plan.GroupDeletions[0].Id != 11 {
		t.Fatalf("group deletions = %+v", plan.GroupDeletions)
	}
}

func TestPlanFindingsRepairCyclesDeletesHighestEmptyGroup(t *testing.T) {
	groups := []findingsRepairGroupRow{
		{Id: 10, ParentWallet: sql.NullInt64{Int64: 1, Valid: true}, AssociatedWallet: 2},
		{Id: 12, ParentWallet: sql.NullInt64{Int64: 2, Valid: true}, AssociatedWallet: 1},
	}
	allocations := []findingsRepairAllocationRow{{Id: 20, GroupId: 10}}

	plan := planFindingsRepairCycles(groups, allocations)
	if len(plan.AllocationDeletions) != 0 {
		t.Fatalf("allocation deletions = %+v", plan.AllocationDeletions)
	}
	if len(plan.GroupDeletions) != 1 || plan.GroupDeletions[0].Id != 12 {
		t.Fatalf("group deletions = %+v", plan.GroupDeletions)
	}
}

func TestPlanFindingsRepairClampsAndRetiresAllocations(t *testing.T) {
	now := time.Date(2026, time.July, 23, 12, 0, 0, 0, time.UTC)
	groups := []findingsRepairGroupRow{
		{Id: 10, AssociatedWallet: 1, TreeUsage: 100},
		{Id: 11, AssociatedWallet: 2, TreeUsage: 50},
	}
	allocations := []findingsRepairAllocationRow{
		{
			Id:                  20,
			GroupId:             10,
			Quota:               -1,
			AllocationStartTime: now.Add(time.Hour),
			AllocationEndTime:   now,
			RetiredUsage:        -2,
			RetiredQuota:        -3,
			AccountingFrequency: "ONCE",
		},
		{
			Id:                  21,
			GroupId:             11,
			Quota:               80,
			AllocationStartTime: now.Add(-2 * time.Hour),
			AllocationEndTime:   now.Add(-time.Hour),
			AccountingFrequency: "PERIODIC_HOUR",
		},
	}

	plan := planFindingsRepair(groups, allocations, now)
	if len(plan.Clamps) != 3 {
		t.Fatalf("clamps = %+v", plan.Clamps)
	}
	if len(plan.AllocationUpdates) != 2 {
		t.Fatalf("allocation updates = %+v", plan.AllocationUpdates)
	}
	invalid := plan.AllocationUpdates[0]
	if !invalid.End.Equal(invalid.Start) || invalid.Quota != 0 || invalid.RetiredUsage != 0 || invalid.RetiredQuota != 0 || invalid.Retired {
		t.Fatalf("invalid interval update = %+v", invalid)
	}
	periodic := plan.AllocationUpdates[1]
	if !periodic.Retired || periodic.Quota != 50 || periodic.RetiredUsage != 50 || periodic.RetiredQuota != 80 {
		t.Fatalf("periodic retirement update = %+v", periodic)
	}
}

func TestPlanFindingsRepairUsesCapacityRetirementSemantics(t *testing.T) {
	now := time.Date(2026, time.July, 23, 12, 0, 0, 0, time.UTC)
	groups := []findingsRepairGroupRow{{Id: 10, AssociatedWallet: 1, TreeUsage: 60}}
	allocations := []findingsRepairAllocationRow{
		{
			Id:                  20,
			GroupId:             10,
			Quota:               100,
			AllocationStartTime: now.Add(-2 * time.Hour),
			AllocationEndTime:   now.Add(-time.Hour),
			AccountingFrequency: "ONCE",
		},
	}

	plan := planFindingsRepair(groups, allocations, now)
	if len(plan.AllocationUpdates) != 1 {
		t.Fatalf("allocation updates = %+v", plan.AllocationUpdates)
	}
	update := plan.AllocationUpdates[0]
	if !update.Retired || update.Quota != 100 || update.RetiredUsage != 60 || update.RetiredQuota != 100 {
		t.Fatalf("capacity retirement update = %+v", update)
	}
}

func TestPlanFindingsRepairRecomputesPrematureRetirement(t *testing.T) {
	now := time.Date(2026, time.July, 23, 12, 0, 0, 0, time.UTC)
	groups := []findingsRepairGroupRow{{Id: 10, AssociatedWallet: 1}}
	allocations := []findingsRepairAllocationRow{{
		Id: 20, GroupId: 10, Quota: 30,
		AllocationStartTime: now.Add(-time.Hour), AllocationEndTime: now.Add(time.Hour),
		Retired: true, RetiredUsage: 30, RetiredQuota: 100, AccountingFrequency: "PERIODIC_HOUR",
	}}

	plan := planFindingsRepair(groups, allocations, now)
	if len(plan.AllocationUpdates) != 1 {
		t.Fatalf("allocation updates = %+v", plan.AllocationUpdates)
	}
	update := plan.AllocationUpdates[0]
	if update.Retired || update.Quota != 100 || update.RetiredUsage != 0 || update.RetiredQuota != 0 {
		t.Fatalf("premature retirement update = %+v", update)
	}
}

func TestPlanFindingsRepairNormalizesRetirementState(t *testing.T) {
	now := time.Date(2026, time.July, 23, 12, 0, 0, 0, time.UTC)
	groups := []findingsRepairGroupRow{
		{Id: 10, AssociatedWallet: 1, TreeUsage: 100},
		{Id: 11, AssociatedWallet: 2, TreeUsage: 100},
		{Id: 12, AssociatedWallet: 3, TreeUsage: 100},
	}
	allocations := []findingsRepairAllocationRow{
		{Id: 20, GroupId: 10, Quota: 80, AllocationStartTime: now.Add(-2 * time.Hour), AllocationEndTime: now.Add(-time.Hour), RetiredUsage: 4, RetiredQuota: 9, AccountingFrequency: "ONCE"},
		{Id: 21, GroupId: 11, Quota: 70, AllocationStartTime: now.Add(-2 * time.Hour), AllocationEndTime: now.Add(-time.Hour), Retired: true, RetiredUsage: 60, RetiredQuota: 50, AccountingFrequency: "ONCE"},
		{Id: 22, GroupId: 12, Quota: 70, AllocationStartTime: now.Add(-2 * time.Hour), AllocationEndTime: now.Add(-time.Hour), Retired: true, RetiredUsage: 40, RetiredQuota: 70, AccountingFrequency: "PERIODIC_HOUR"},
	}

	plan := planFindingsRepair(groups, allocations, now)
	if len(plan.AllocationUpdates) != 3 {
		t.Fatalf("allocation updates = %+v", plan.AllocationUpdates)
	}
	if update := plan.AllocationUpdates[0]; !update.Retired || update.RetiredUsage != 80 || update.RetiredQuota != 80 {
		t.Errorf("expired allocation update = %+v", update)
	}
	if update := plan.AllocationUpdates[1]; update.RetiredUsage != 50 {
		t.Errorf("capacity retirement update = %+v", update)
	}
	if update := plan.AllocationUpdates[2]; update.Quota != 40 {
		t.Errorf("periodic retirement update = %+v", update)
	}
}
