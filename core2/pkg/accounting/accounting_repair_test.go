package accounting

import (
	"database/sql"
	"testing"
)

func TestPlanDuplicateRepairNormalizesWalletsBeforeGroups(t *testing.T) {
	wallets := []duplicateRepairWalletRow{
		{Id: 1, WalletOwner: 10, ProductCategory: 20, LocalUsage: 5},
		{Id: 2, WalletOwner: 10, ProductCategory: 20, LocalUsage: 9},
		{Id: 3, WalletOwner: 11, ProductCategory: 20, LocalUsage: 1},
	}
	groups := []duplicateRepairGroupRow{
		{Id: 10, AssociatedWallet: 3, ParentWallet: sql.NullInt64{Int64: 1, Valid: true}},
		{Id: 11, AssociatedWallet: 3, ParentWallet: sql.NullInt64{Int64: 2, Valid: true}},
		{Id: 12, AssociatedWallet: 2},
		{Id: 13, AssociatedWallet: 1},
	}

	plan := planDuplicateRepair(wallets, groups)
	if plan.Blocked {
		t.Fatalf("unexpected blockers: %v", plan.Blockers)
	}
	if len(plan.Wallets) != 1 || plan.Wallets[0] != (duplicateRepairWallet{RemoveId: 2, KeepId: 1}) {
		t.Fatalf("wallet changes = %+v", plan.Wallets)
	}
	wantGroups := []duplicateRepairGroup{{RemoveId: 11, KeepId: 10}, {RemoveId: 13, KeepId: 12}}
	if len(plan.Groups) != len(wantGroups) {
		t.Fatalf("group changes = %+v, want %+v", plan.Groups, wantGroups)
	}
	for i := range wantGroups {
		if plan.Groups[i] != wantGroups[i] {
			t.Errorf("group change %d = %+v, want %+v", i, plan.Groups[i], wantGroups[i])
		}
	}

	ids, usage := duplicateRepairCanonicalUsage(wallets)
	if len(ids) != 2 || ids[0] != 1 || usage[0] != 9 || ids[1] != 3 || usage[1] != 1 {
		t.Fatalf("canonical usage = ids %v usage %v", ids, usage)
	}
}

func TestPlanDuplicateRepairRejectsNormalizedSelfAllocation(t *testing.T) {
	wallets := []duplicateRepairWalletRow{
		{Id: 1, WalletOwner: 10, ProductCategory: 20},
		{Id: 2, WalletOwner: 10, ProductCategory: 20},
	}
	groups := []duplicateRepairGroupRow{
		{Id: 10, AssociatedWallet: 2, ParentWallet: sql.NullInt64{Int64: 1, Valid: true}},
	}

	plan := planDuplicateRepair(wallets, groups)
	if !plan.Blocked || len(plan.Blockers) != 1 {
		t.Fatalf("plan should be blocked: %+v", plan)
	}
}
