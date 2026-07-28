package accounting

import "testing"

func TestAccountingOverflowAnalysisReconcilesRootAndLeafUsage(t *testing.T) {
	e := newEnv(t, capacityCategory, false)
	e.AllocateEx(0, 0, 100, 100, "00000000-0000-0000-0000-000000000001", "")
	e.AllocateEx(0, 0, 100, 150, "00000000-0000-0000-0000-000000000002", "00000000-0000-0000-0000-000000000001")
	e.ReportDelta(1, "00000000-0000-0000-0000-000000000002", 120)

	report := analyzeAccountingOverflowBucket(e.Bucket, nil)
	if len(report.Components) != 1 {
		t.Fatalf("components = %+v", report.Components)
	}
	component := report.Components[0]
	if component.DirectLocalUsage != 120 || component.LeafLocalUsage != 120 {
		t.Fatalf("local usage = direct %d leaf %d, want 120", component.DirectLocalUsage, component.LeafLocalUsage)
	}
	if component.PersistedRootFlow != 100 || component.SyntheticOverflowFlow != 20 || component.ReconciliationDifference != 0 {
		t.Fatalf(
			"reconciliation = root %d + overflow %d, difference %d; want 100 + 20, difference 0",
			component.PersistedRootFlow,
			component.SyntheticOverflowFlow,
			component.ReconciliationDifference,
		)
	}
	if len(component.OverflowWallets) != 1 || component.OverflowWallets[0].OverflowFlow != 20 || component.OverflowWallets[0].OverflowCapacity != 50 {
		t.Fatalf("overflow wallets = %+v", component.OverflowWallets)
	}
}

func TestAccountingOverflowAnalysisAllowsUnusedWalletWithoutRootAllocation(t *testing.T) {
	e := newEnv(t, capacityCategory, false)
	walletId := e.Wallet(e.Owner("unused"), e.Tm(0))

	report := analyzeAccountingOverflowBucket(e.Bucket, nil)
	if len(report.Components) != 0 {
		t.Fatalf("unused rootless wallet errors = %+v", report.Components)
	}

	e.Bucket.WalletsById[walletId].LocalUsage = 1
	report = analyzeAccountingOverflowBucket(e.Bucket, nil)
	if len(report.Components) != 0 || len(report.UnfundedUsage) != 1 || report.UnfundedUsage[0].WalletId != walletId || report.UnfundedUsage[0].LocalUsage != 1 {
		t.Fatalf("used rootless wallet output = components %+v, unfunded usage %+v", report.Components, report.UnfundedUsage)
	}
}

func TestAccountingOverflowAnalysisIncludesTopLevelFlowTotals(t *testing.T) {
	e := newEnv(t, capacityCategory, false)
	e.AllocateEx(0, 0, 100, 100, "00000000-0000-0000-0000-000000000001", "")
	e.AllocateEx(0, 0, 100, 150, "00000000-0000-0000-0000-000000000002", "00000000-0000-0000-0000-000000000001")
	e.ReportDelta(1, "00000000-0000-0000-0000-000000000002", 120)

	report := analyzeLoadedAccountingOverflow(e.Tm(1), capacityCategory.Provider, capacityCategory.Name, nil)
	if len(report.OverflowRootTotals) != 1 {
		t.Fatalf("overflow root totals = %+v", report.OverflowRootTotals)
	}
	total := report.OverflowRootTotals[0]
	if total.FlowedThroughOverflowRoots != 20 || total.OverflowRoots != 1 {
		t.Fatalf("overflow root total = %+v", total)
	}
}
