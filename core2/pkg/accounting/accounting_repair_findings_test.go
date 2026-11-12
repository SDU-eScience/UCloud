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
		{
			Id:                  20,
			GroupId:             10,
			Quota:               80,
			AllocationStartTime: now.Add(-2 * time.Hour),
			AllocationEndTime:   now.Add(-time.Hour),
			RetiredUsage:        4,
			RetiredQuota:        9,
			AccountingFrequency: "ONCE",
		},
		{
			Id:                  21,
			GroupId:             11,
			Quota:               70,
			AllocationStartTime: now.Add(-2 * time.Hour),
			AllocationEndTime:   now.Add(-time.Hour),
			Retired:             true,
			RetiredUsage:        60,
			RetiredQuota:        50,
			AccountingFrequency: "ONCE",
		},
		{
			Id:                  22,
			GroupId:             12,
			Quota:               70,
			AllocationStartTime: now.Add(-2 * time.Hour),
			AllocationEndTime:   now.Add(-time.Hour),
			Retired:             true,
			RetiredUsage:        40,
			RetiredQuota:        70,
			AccountingFrequency: "PERIODIC_HOUR",
		},
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

func TestAccountingFindingsRepairRetirementBatch(t *testing.T) {
	for _, tc := range []struct {
		name      string
		frequency string
		product   string
		capacity  bool
	}{
		{name: "capacity", frequency: "ONCE", product: "STORAGE", capacity: true},
		{name: "consumption", frequency: "PERIODIC_HOUR", product: "COMPUTE"},
		{name: "inference", frequency: "ONCE", product: "INFERENCE"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			now := time.Date(2026, time.November, 10, 12, 0, 0, 0, time.UTC)
			groups := []findingsRepairGroupRow{
				{Id: 10, AssociatedWallet: 1, TreeUsage: 150},
				{Id: 11, AssociatedWallet: 2, TreeUsage: 70},
			}
			allocations := []findingsRepairAllocationRow{
				{Id: 20, GroupId: 10, Quota: 100, Retired: true, RetiredUsage: 40, RetiredQuota: 100},
				{Id: 21, GroupId: 10, Quota: 100},
				{Id: 22, GroupId: 10, Quota: 100},
				{Id: 23, GroupId: 11, Quota: 100, Retired: true, RetiredUsage: 30, RetiredQuota: 100},
				{Id: 24, GroupId: 11, Quota: 100},
			}
			for index := range allocations {
				allocation := &allocations[index]
				allocation.AccountingFrequency = tc.frequency
				allocation.ProductType = tc.product
				allocation.AllocationStartTime = now.Add(-2 * time.Hour)
				allocation.AllocationEndTime = now
				if allocation.Retired {
					allocation.AllocationStartTime = now.Add(-4 * time.Hour)
					allocation.AllocationEndTime = now.Add(-3 * time.Hour)
					if !tc.capacity {
						allocation.Quota = allocation.RetiredUsage
					}
				}
			}
			allocations[1].AllocationStartTime = now.Add(-time.Hour)

			plan := planFindingsRepair(groups, allocations, now)
			if len(plan.AllocationUpdates) != 3 {
				t.Fatalf("allocation updates = %+v, want three newly retired allocations", plan.AllocationUpdates)
			}
			wantUsage := map[int64]int64{21: 50, 22: 100, 24: 70}
			if !tc.capacity {
				wantUsage[21] = 10
				wantUsage[24] = 40
			}
			for _, update := range plan.AllocationUpdates {
				want, ok := wantUsage[update.Id]
				if !ok {
					t.Fatalf("repair rewrote historical allocation %d", update.Id)
				}
				wantQuota := int64(100)
				if !tc.capacity {
					wantQuota = want
				}
				if !update.Retired || update.RetiredUsage != want || update.Quota != wantQuota || update.RetiredQuota != 100 {
					t.Errorf("allocation %d retirement = %+v, want usage/quota/original %d/%d/100", update.Id, update, want, wantQuota)
				}
				for index := range allocations {
					allocation := &allocations[index]
					if allocation.Id == update.Id {
						allocation.Retired = update.Retired
						allocation.RetiredUsage = update.RetiredUsage
						allocation.RetiredQuota = update.RetiredQuota
						allocation.Quota = update.Quota
					}
				}
			}
			if repeated := planFindingsRepair(groups, allocations, now); len(repeated.AllocationUpdates) != 0 {
				t.Fatalf("repeated repair changed retired allocations: %+v", repeated.AllocationUpdates)
			}
		})
	}
}
