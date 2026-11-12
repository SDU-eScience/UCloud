package accounting

import (
	"math/rand"
	"testing"
	"time"
)

// These are deterministic differential ports of Accounting/wallet/wallet_test.go.
// They use a tree and retire at most one allocation per group because multi-parent
// routing and repeated capacity retirement are documented reference limitations.
func TestReferenceWalletHierarchy(t *testing.T) {
	runDifferentialScenario(t, referencePortedHierarchyScenario())
}

func TestReferenceLocalUsage(t *testing.T) {
	scenario := referencePortedHierarchyScenario()
	random := rand.New(rand.NewSource(1))
	at := scenario.InitialTime.Add(time.Minute)
	for i := 0; i < 1000; i++ {
		wallet := scenario.Wallets[random.Intn(len(scenario.Wallets))]
		scenario.Operations = append(scenario.Operations, differentialOperation{
			Kind:          differentialReport,
			At:            at,
			Wallet:        wallet,
			Usage:         1 + random.Int63n(5),
			IsDelta:       true,
			ReferenceNote: "seeded port of randomCharge",
		})
		at = at.Add(time.Second)
	}
	runDifferentialScenario(t, scenario)
}

func TestReferenceWalletTreeUsageHierarchy(t *testing.T) {
	runDifferentialScenario(t, referencePortedTreeUsageScenario())
}

func TestReferenceWalletTreeUsageLimit(t *testing.T) {
	// The hierarchy test compares group flow, contributing quota, propagated
	// usage, excess, capacity, and lock state after the same 100/retire/300
	// operation sequence used by the reference limit test.
	runDifferentialScenario(t, referencePortedTreeUsageScenario())
}

func referencePortedTreeUsageScenario() differentialScenario {
	scenario := referencePortedHierarchyScenario()
	random := rand.New(rand.NewSource(2))
	at := scenario.InitialTime.Add(time.Minute)
	for i := 0; i < 100; i++ {
		scenario.Operations = append(scenario.Operations, differentialOperation{
			Kind: differentialReport, At: at, Wallet: scenario.Wallets[random.Intn(len(scenario.Wallets))],
			Usage: 1 + random.Int63n(5), IsDelta: true, ReferenceNote: "seeded port of randomWalletCharge",
		})
		at = at.Add(time.Second)
	}
	for i := range scenario.Allocations {
		scenario.Allocations[i].End = at
	}

	// This is the reference test's retirement phase, constrained to distinct
	// groups so every snapshot remains comparable with the reference.
	scenario.Operations = append(scenario.Operations, differentialOperation{
		Kind: differentialAdvance, At: at,
		Retirements:   []int64{1, 2, 3},
		ReferenceNote: "seeded port of randomRetire",
	})
	at = at.Add(time.Second)
	for i := 0; i < 300; i++ {
		scenario.Operations = append(scenario.Operations, differentialOperation{
			Kind: differentialReport, At: at, Wallet: scenario.Wallets[random.Intn(len(scenario.Wallets))],
			Usage: 1 + random.Int63n(5), IsDelta: true, ReferenceNote: "seeded port of randomWalletCharge",
		})
		at = at.Add(time.Second)
	}
	return scenario
}

func referencePortedHierarchyScenario() differentialScenario {
	initial := time.Date(2024, time.January, 1, 0, 0, 0, 0, time.UTC)
	end := initial.Add(2 * time.Hour)
	return differentialScenario{
		Capacity:    true,
		InitialTime: initial,
		Wallets:     []int64{1, 2, 3},
		Allocations: []differentialAllocation{
			{Id: 1, Recipient: 1, Parent: 0, Quota: 100_000, Start: initial.Add(-time.Hour), End: end},
			{Id: 2, Recipient: 2, Parent: 1, Quota: 100_000, Start: initial.Add(-time.Hour), End: end},
			{Id: 3, Recipient: 3, Parent: 2, Quota: 100_000, Start: initial.Add(-time.Hour), End: end},
		},
	}
}
