package accounting

import (
	"embed"
	"encoding/csv"
	"fmt"
	"strconv"
	"strings"
	"testing"
	"time"
)

//go:embed testdata/reference/*.csv
var referenceCSVFiles embed.FS

// TestReferenceCSVFixtures imports every fixture through the allocation model
// in tmp/Accounting/wallet/importdata.go, replays positive local usage, and
// compares both the initialized and final states.
func TestReferenceCSVFixtures(t *testing.T) {
	ref, _ := referenceCSVFiles.ReadDir("testdata/reference")
	for _, entry := range ref {
		name := entry.Name()
		t.Run(strings.TrimSuffix(name, ".csv"), func(t *testing.T) {
			runReferenceCSVScenario(t, referenceCSVScenario(t, name))
		})
	}
}

func runReferenceCSVScenario(t *testing.T, scenario differentialScenario) {
	t.Helper()
	core := newCoreDifferentialRunner(t, scenario)
	reference := newReferenceDifferentialRunner(t, scenario)
	defer DestroyRefWalletHierarchy()

	if difference := compareDifferentialSnapshots(-1, core.Snapshot(scenario.InitialTime), reference.Snapshot(scenario.InitialTime)); difference != nil {
		failDifferentialScenario(t, scenario, difference)
	}
	last := scenario.InitialTime
	for operationIndex, operation := range scenario.Operations {
		if err := core.Apply(operation); err != nil {
			t.Fatalf("Core rejected operation %d (%s): %v", operationIndex, operation, err)
		}
		if err := reference.Apply(operation); err != nil {
			t.Fatalf("reference rejected operation %d (%s): %v", operationIndex, operation, err)
		}
		last = operation.At
	}
	if difference := compareDifferentialSnapshots(len(scenario.Operations)-1, core.Snapshot(last), reference.Snapshot(last)); difference != nil {
		failDifferentialScenario(t, scenario, difference)
	}
}

func referenceCSVScenario(t *testing.T, name string) differentialScenario {
	t.Helper()
	file, err := referenceCSVFiles.Open("testdata/reference/" + name)
	if err != nil {
		t.Fatal(err)
	}
	defer file.Close()

	records, err := csv.NewReader(file).ReadAll()
	if err != nil {
		t.Fatalf("read %s: %v", name, err)
	}
	initial := time.Date(2024, time.January, 1, 0, 0, 0, 0, time.UTC)
	scenario := differentialScenario{Capacity: true, InitialTime: initial}
	wallets := map[int64]struct{}{}
	allocations := map[int64]struct{}{}
	initialUsage := []differentialOperation{}
	for line, record := range records {
		if len(record) < 7 {
			t.Fatalf("%s:%d: expected at least 7 columns, got %d", name, line+1, len(record))
		}
		id := referenceCSVImportedInt(record[0])
		if _, exists := allocations[id]; exists {
			t.Fatalf("%s:%d: duplicate allocation id %d", name, line+1, id)
		}
		allocations[id] = struct{}{}
		recipient := referenceCSVImportedInt(record[1])
		parent := int64(0)
		if strings.TrimSpace(record[2]) != "" {
			parent = referenceCSVImportedInt(record[2])
		}
		quota := referenceCSVImportedInt(record[4])
		start := time.UnixMilli(referenceCSVImportedInt(record[5]))
		// AddAllocation only evaluates Start and ignores End. Use the same
		// open-ended validity that the reference importer's resulting state has.
		end := time.UnixMilli(4_891_363_200_000)
		scenario.Allocations = append(scenario.Allocations, differentialAllocation{
			Id: id, Recipient: recipient, Parent: parent, Quota: quota, Start: start, End: end,
		})
		if len(record) > 7 {
			if usage := referenceCSVImportedInt(record[7]); usage > 0 {
				initialUsage = append(initialUsage, differentialOperation{
					Kind: differentialReport, At: initial, Wallet: recipient, Usage: usage, IsDelta: true,
					ReferenceNote: fmt.Sprintf("CSV allocation %d local usage", id),
				})
			}
		}
		wallets[recipient] = struct{}{}
	}
	for _, allocation := range scenario.Allocations {
		if allocation.Parent != 0 {
			if _, exists := wallets[allocation.Parent]; !exists {
				t.Fatalf("%s: allocation %d has parent wallet %d which the reference importer cannot create", name, allocation.Id, allocation.Parent)
			}
		}
	}
	for wallet := range wallets {
		scenario.Wallets = append(scenario.Wallets, wallet)
	}
	scenario.Operations = initialUsage
	return scenario
}

func referenceCSVImportedInt(value string) int64 {
	// importdata.go intentionally discards ParseInt errors; decimal timestamp
	// fields therefore become zero in the reference state.
	result, _ := strconv.ParseInt(strings.TrimSpace(value), 10, 64)
	return result
}
