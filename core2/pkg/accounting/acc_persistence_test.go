package accounting

import (
	"testing"

	accapi "ucloud.dk/shared/pkg/accounting"
)

func TestAccountingRepairLoadedConsumptionDistributesMigratedWalletUsage(t *testing.T) {
	e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
	e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 10, Quota: 100})
	promise := e.addPromise("root", "child", 0, 10, 60)

	tree := e.tree()
	rootWallet := tree.WalletsById[e.wallet("root")]
	childWallet := tree.WalletsById[e.wallet("child")]
	rootWallet.Consumed = 40
	childWallet.Consumed = 50

	if got := tree.AllocationsById[e.allocs["root"]].ConsumedSelf; got != 0 {
		t.Fatalf("precondition root consumed self = %d, want 0", got)
	}
	if len(childWallet.Allocations) != 0 {
		t.Fatalf("precondition child allocations = %d, want 0", len(childWallet.Allocations))
	}

	accountingRepairLoadedConsumption(e.tm(1))

	e.assertAllocation("root", 50, 50, 40, 50)
	childHead := e.promiseHead(promise, 1)
	assertPromiseAllocation(t, childHead, 50, 0, e.tm(1), e.tm(10))
	if childHead.ConsumedSelf != 50 {
		t.Fatalf("child consumed self = %d, want 50", childHead.ConsumedSelf)
	}
	e.assertWalletConsumed("root", 40)
	e.assertWalletConsumed("child", 50)
	e.assertValid()
}
