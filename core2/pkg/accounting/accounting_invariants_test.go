package accounting

import (
	"testing"

	accapi "ucloud.dk/shared/pkg/accounting"
)

func TestAccountingInvariantModeAutomaticOnlyEnablesChecksInTests(t *testing.T) {
	previousMode := accountingInvariantChecks
	previousTestingEnabled := accGlobals.TestingEnabled
	t.Cleanup(func() {
		accountingInvariantChecks = previousMode
		accGlobals.TestingEnabled = previousTestingEnabled
	})

	accountingInvariantChecks = accountingInvariantModeAutomatic
	accGlobals.TestingEnabled = false
	if mode := accountingInvariantModeCurrent(); mode != accountingInvariantModeDisabled {
		t.Fatalf("automatic mode outside tests = %v, want disabled", mode)
	}

	accGlobals.TestingEnabled = true
	if mode := accountingInvariantModeCurrent(); mode != accountingInvariantModePanic {
		t.Fatalf("automatic mode in tests = %v, want panic", mode)
	}
}

func TestAccountingInvariantExplicitModeOverridesTestingDefault(t *testing.T) {
	previousMode := accountingInvariantChecks
	previousTestingEnabled := accGlobals.TestingEnabled
	t.Cleanup(func() {
		accountingInvariantChecks = previousMode
		accGlobals.TestingEnabled = previousTestingEnabled
	})

	accGlobals.TestingEnabled = true
	for _, mode := range []accountingInvariantMode{
		accountingInvariantModeDisabled,
		accountingInvariantModePanic,
		accountingInvariantModeLog,
	} {
		accountingInvariantChecks = mode
		if current := accountingInvariantModeCurrent(); current != mode {
			t.Fatalf("explicit mode %v resolved to %v", mode, current)
		}
	}
}

func TestUsageReportSkipsPreflightWhenAccountingInvariantsAreDisabled(t *testing.T) {
	e := newEnv(t, capacityCategory)
	e.AllocateEx(0, 0, 100, 10, "user", "")
	wallet := e.Bucket.WalletsById[e.Wallet(e.Owner("user"), e.Tm(0))]
	wallet.AllocationsByParent[internalGraphRoot].TreeUsage = 11

	previousMode := accountingInvariantChecks
	accountingInvariantChecks = accountingInvariantModeDisabled
	t.Cleanup(func() { accountingInvariantChecks = previousMode })

	_, err := internalReportUsage(e.Tm(1), accapi.ReportUsageRequest{
		Owner:        e.Owner("user").WalletOwner(),
		CategoryIdV2: e.Bucket.Category.ToId(),
		Usage:        1,
	})
	if err != nil {
		t.Fatalf("disabled invariant preflight rejected usage report: %v", err)
	}
}
