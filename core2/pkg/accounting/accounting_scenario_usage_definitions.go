package accounting

import (
	"cmp"
	"fmt"
	"slices"

	accapi "ucloud.dk/shared/pkg/accounting"
)

func accountingScenarioUsageDefinitions() []accountingScenarioDefinition {
	return []accountingScenarioDefinition{
		accountingScenarioUsageResearchMultiProduct(),
		accountingScenarioUsageSmallTeam(),
		accountingScenarioUsageDeepHierarchy(),
		accountingScenarioUsageCapacityLifecycle(),
		accountingScenarioUsageGrantAllocator(),
		accountingScenarioUsageProviderCatalog(),
		accountingScenarioUsageInferenceCredits(),
		accountingScenarioUsageInferenceCoFunded(),
	}
}

func accountingScenarioUnitCore() accapi.AccountingUnit {
	return accapi.AccountingUnit{
		Name:                   "Core",
		NamePlural:             "Cores",
		FloatingPoint:          false,
		DisplayFrequencySuffix: true,
	}
}

func accountingScenarioUnitGpu() accapi.AccountingUnit {
	return accapi.AccountingUnit{
		Name:                   "GPU",
		NamePlural:             "GPUs",
		FloatingPoint:          false,
		DisplayFrequencySuffix: true,
	}
}

func accountingScenarioUnitGb() accapi.AccountingUnit {
	return accapi.AccountingUnit{
		Name:                   "GB",
		NamePlural:             "GB",
		FloatingPoint:          false,
		DisplayFrequencySuffix: false,
	}
}

func accountingScenarioUnitCredit() accapi.AccountingUnit {
	return accapi.AccountingUnit{
		Name:                   "Credit",
		NamePlural:             "Credits",
		FloatingPoint:          true,
		DisplayFrequencySuffix: false,
	}
}

func accountingScenarioUnitIp() accapi.AccountingUnit {
	return accapi.AccountingUnit{
		Name:                   "IP",
		NamePlural:             "IPs",
		FloatingPoint:          false,
		DisplayFrequencySuffix: false,
	}
}

func accountingScenarioSortSteps(steps []accountingScenarioStep) []accountingScenarioStep {
	slices.SortStableFunc(steps, func(a, b accountingScenarioStep) int {
		if c := cmp.Compare(a.At, b.At); c != 0 {
			return c
		}
		if c := cmp.Compare(a.AtMillis, b.AtMillis); c != 0 {
			return c
		}
		kindOrder := func(k string) int {
			switch k {
			case "allocate":
				return 0
			case "charge":
				return 1
			case "checkpoint":
				return 2
			case "sample":
				return 3
			default:
				return 4
			}
		}
		return cmp.Compare(kindOrder(a.Kind), kindOrder(b.Kind))
	})
	return steps
}

func accountingScenarioUsageResearchMultiProduct() accountingScenarioDefinition {
	products := []accountingScenarioProductSpec{
		{
			Key:             "cpu-legacy",
			Slug:            "u1-standard",
			ProductType:     accapi.ProductTypeCompute,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitCore(),
			Frequency:       accapi.AccountingFrequencyPeriodicHour,
			RootQuota:       500000,
		},
		{
			Key:             "cpu-zen5",
			Slug:            "cpu-amd-zen5",
			ProductType:     accapi.ProductTypeCompute,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitCore(),
			Frequency:       accapi.AccountingFrequencyPeriodicMinute,
			RootQuota:       30000000,
		},
		{
			Key:             "gpu-b200",
			Slug:            "gpu-nvidia-b200",
			ProductType:     accapi.ProductTypeCompute,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitGpu(),
			Frequency:       accapi.AccountingFrequencyPeriodicHour,
			RootQuota:       100000,
		},
		{
			Key:             "storage-ceph",
			Slug:            "cephfs",
			ProductType:     accapi.ProductTypeStorage,
			AccountingModel: accapi.AccountingModelCapacity,
			Unit:            accountingScenarioUnitGb(),
			Frequency:       accapi.AccountingFrequencyOnce,
			RootQuota:       200000,
		},
		{
			Key:             "storage-scratch",
			Slug:            "nvme-scratch",
			ProductType:     accapi.ProductTypeStorage,
			AccountingModel: accapi.AccountingModelCapacity,
			Unit:            accountingScenarioUnitGb(),
			Frequency:       accapi.AccountingFrequencyOnce,
			RootQuota:       100000,
		},
		{
			Key:             "inference-llm",
			Slug:            "llm-credits",
			ProductType:     accapi.ProductTypeInference,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitCredit(),
			Frequency:       accapi.AccountingFrequencyOnce,
			RootQuota:       2000000000,
		},
	}

	steps := []accountingScenarioStep{
		{Kind: "allocate", At: -75, Start: -75, End: -20, Quota: 8000, Owner: "research", Product: "cpu-legacy"},
		{Kind: "allocate", At: -35, Start: -35, End: 45, Quota: 10000, Owner: "research", Product: "cpu-legacy"},
		{Kind: "allocate", At: -30, Start: -30, End: 60, Quota: 1200000, Owner: "research", Product: "cpu-zen5"},
		{Kind: "allocate", At: -90, Start: -90, End: -25, Quota: 4000, Owner: "research", Product: "gpu-b200"},
		{Kind: "allocate", At: -25, Start: -25, End: 35, Quota: 1200, Owner: "research", Product: "gpu-b200"},
		{Kind: "allocate", At: -60, Start: -60, End: 90, Quota: 20000, Owner: "research", Product: "storage-ceph"},
		{Kind: "allocate", At: -60, Start: -60, End: 10, Quota: 8000, Owner: "research", Product: "storage-scratch"},
		{Kind: "allocate", At: -60, Start: -60, End: 90, Quota: 4000, Owner: "research", Product: "storage-scratch"},
		{Kind: "allocate", At: -45, Start: -45, End: 45, Quota: 500000000, Owner: "research", Product: "inference-llm"},
	}

	for day := -70; day <= -1; day++ {
		weekday := ((day % 7) + 7) % 7
		factor := int64(1)
		if weekday == 5 || weekday == 6 {
			factor = 0
		}
		if day <= -21 {
			charge := 90 + int64((day+100)%4)*30
			if factor > 0 {
				steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: charge, Owner: "research", Product: "cpu-legacy"})
			}
		} else {
			legacyCharge := 40 + int64((day+100)%3)*15
			zen5Charge := (220 + int64((day+100)%5)*60) * 60
			if factor > 0 {
				steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: legacyCharge, Owner: "research", Product: "cpu-legacy"})
				steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: zen5Charge, Owner: "research", Product: "cpu-zen5"})
			}
		}
	}

	for day := -85; day <= -26; day += 3 {
		steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: 185, Owner: "research", Product: "gpu-b200"})
	}
	for day := -24; day <= -1; day += 4 {
		steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: 55, Owner: "research", Product: "gpu-b200"})
	}

	cephChanges := []struct {
		Day   int
		Delta int64
	}{
		{Day: -58, Delta: 4500},
		{Day: -48, Delta: 3200},
		{Day: -36, Delta: 4100},
		{Day: -25, Delta: -1800},
		{Day: -14, Delta: 3600},
		{Day: -5, Delta: 1200},
	}
	for _, ch := range cephChanges {
		steps = append(steps, accountingScenarioStep{Kind: "charge", At: ch.Day, Quota: ch.Delta, Owner: "research", Product: "storage-ceph"})
	}

	scratchChanges := []struct {
		Day   int
		Delta int64
	}{
		{Day: -55, Delta: 3000},
		{Day: -40, Delta: 4500},
		{Day: -22, Delta: -2000},
		{Day: -10, Delta: 4200},
	}
	for _, ch := range scratchChanges {
		steps = append(steps, accountingScenarioStep{Kind: "charge", At: ch.Day, Quota: ch.Delta, Owner: "research", Product: "storage-scratch"})
	}

	for day := -42; day <= -1; day += 2 {
		burst := int64(4500000 + ((day+100)%6)*1800000)
		steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: burst, Owner: "research", Product: "inference-llm"})
	}

	steps = append(steps, accountingScenarioStep{
		Kind:  "checkpoint",
		At:    0,
		Owner: "research",
		Label: "Research leaf project final state",
	})

	return accountingScenarioDefinition{
		Name:                "usage-research-multi-product",
		Code:                "URMP",
		Description:         "Leaf research project with 6 products across 4 units (Core-hours migration, GPU retired baseline, Storage upcoming quota drop, Inference credits) sampled daily.",
		SampleIntervalHours: 24,
		PrimaryProjectRole:  "research",
		Products:            products,
		UserOwnedRoles:      []string{"research", "root"},
		Steps:               accountingScenarioSortSteps(steps),
	}
}

func accountingScenarioUsageSmallTeam() accountingScenarioDefinition {
	products := []accountingScenarioProductSpec{
		{
			Key:             "cpu",
			Slug:            "cpu-zen5",
			ProductType:     accapi.ProductTypeCompute,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitCore(),
			Frequency:       accapi.AccountingFrequencyPeriodicHour,
			RootQuota:       250000,
		},
		{
			Key:             "storage",
			Slug:            "cephfs",
			ProductType:     accapi.ProductTypeStorage,
			AccountingModel: accapi.AccountingModelCapacity,
			Unit:            accountingScenarioUnitGb(),
			Frequency:       accapi.AccountingFrequencyOnce,
			RootQuota:       100000,
		},
	}

	steps := []accountingScenarioStep{
		{Kind: "allocate", At: -50, Start: -50, End: 40, Quota: 16000, Owner: "team", Product: "cpu"},
		{Kind: "allocate", At: -50, Start: -50, End: 40, Quota: 30000, Owner: "cofunder", Product: "cpu"},
		{Kind: "allocate", At: -50, Start: -50, End: 40, Quota: 15000, Owner: "team", Product: "storage"},

		{Kind: "allocate", At: -48, Start: -48, End: 40, Quota: 6000, Parent: "team", Owner: "ml-lab", Product: "cpu"},
		{Kind: "allocate", At: -48, Start: -48, End: 40, Quota: 5000, Parent: "team", Owner: "bio-sim", Product: "cpu"},
		{Kind: "allocate", At: -48, Start: -48, End: 20, Quota: 5000, Parent: "team", Owner: "shared-child", Product: "cpu"},
		{Kind: "allocate", At: -48, Start: -48, End: 40, Quota: 4000, Parent: "team", Owner: "nlp-exp", Product: "cpu"},
		{Kind: "allocate", At: -48, Start: -48, End: 40, Quota: 8000, Parent: "team", Owner: "capped-child", Product: "cpu"},
		{Kind: "allocate", At: -48, Start: -48, End: 40, Quota: 3000, Parent: "team", Owner: "idle-student", Product: "cpu"},

		{Kind: "allocate", At: -22, Start: -22, End: 10, Quota: 8000, Parent: "cofunder", Owner: "shared-child", Product: "cpu"},

		{Kind: "allocate", At: -48, Start: -48, End: 40, Quota: 6000, Parent: "team", Owner: "ml-lab", Product: "storage"},
		{Kind: "allocate", At: -48, Start: -48, End: 40, Quota: 4500, Parent: "team", Owner: "bio-sim", Product: "storage"},
		{Kind: "allocate", At: -48, Start: -48, End: 40, Quota: 3000, Parent: "team", Owner: "nlp-exp", Product: "storage"},
	}

	for day := -45; day <= -1; day++ {
		if day%2 == 0 {
			steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: 75, Owner: "team", Product: "cpu"})
		}
		steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: 110 + int64((day+100)%4)*25, Owner: "ml-lab", Product: "cpu"})
		if day%2 != 0 {
			steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: 95, Owner: "bio-sim", Product: "cpu"})
		}
		if day <= -23 {
			steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: 85, Owner: "shared-child", Product: "cpu"})
		} else {
			steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: 140, Owner: "shared-child", Product: "cpu"})
		}
		if day%3 == 0 {
			steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: 120, Owner: "nlp-exp", Product: "cpu"})
		}
		if day >= -30 && day%2 == 0 {
			steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: 150, Owner: "capped-child", Product: "cpu"})
		}
	}

	storageSteps := []accountingScenarioStep{
		{Kind: "charge", At: -44, Quota: 1800, Owner: "team", Product: "storage"},
		{Kind: "charge", At: -40, Quota: 2500, Owner: "ml-lab", Product: "storage"},
		{Kind: "charge", At: -35, Quota: 1900, Owner: "bio-sim", Product: "storage"},
		{Kind: "charge", At: -28, Quota: 2100, Owner: "ml-lab", Product: "storage"},
		{Kind: "charge", At: -20, Quota: 1500, Owner: "nlp-exp", Product: "storage"},
		{Kind: "charge", At: -12, Quota: -700, Owner: "ml-lab", Product: "storage"},
		{Kind: "charge", At: -6, Quota: 1400, Owner: "bio-sim", Product: "storage"},
	}
	steps = append(steps, storageSteps...)

	steps = append(steps, accountingScenarioStep{
		Kind:  "checkpoint",
		At:    0,
		Owner: "team",
		Label: "Small team with local usage, sub-projects, and multi-parent rebalancing",
	})

	return accountingScenarioDefinition{
		Name:                "usage-small-team",
		Code:                "USTM",
		Description:         "Research project with local usage, 6 sub-projects, multi-parent flow rebalancing, and an upstream-capped child across Compute and Storage.",
		SampleIntervalHours: 24,
		PrimaryProjectRole:  "team",
		Products:            products,
		UserOwnedRoles:      []string{"team", "cofunder", "capped-child", "shared-child"},
		Steps:               accountingScenarioSortSteps(steps),
	}
}

func accountingScenarioUsageDeepHierarchy() accountingScenarioDefinition {
	products := []accountingScenarioProductSpec{
		{
			Key:             "cpu",
			Slug:            "cpu-zen5",
			ProductType:     accapi.ProductTypeCompute,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitCore(),
			Frequency:       accapi.AccountingFrequencyPeriodicHour,
			RootQuota:       500000,
		},
		{
			Key:             "gpu",
			Slug:            "gpu-b200",
			ProductType:     accapi.ProductTypeCompute,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitGpu(),
			Frequency:       accapi.AccountingFrequencyPeriodicHour,
			RootQuota:       80000,
		},
	}

	steps := []accountingScenarioStep{
		{Kind: "allocate", At: -55, Start: -55, End: 45, Quota: 120000, Owner: "faculty-sci", Product: "cpu"},
		{Kind: "allocate", At: -55, Start: -55, End: 45, Quota: 100000, Owner: "faculty-eng", Product: "cpu"},
		{Kind: "allocate", At: -55, Start: -55, End: 45, Quota: 80000, Owner: "faculty-med", Product: "cpu"},
		{Kind: "allocate", At: -55, Start: -55, End: 45, Quota: 30000, Owner: "direct-flagship", Product: "cpu"},
		{Kind: "allocate", At: -55, Start: -55, End: 45, Quota: 15000, Owner: "direct-pilot", Product: "cpu"},

		{Kind: "allocate", At: -55, Start: -55, End: 45, Quota: 20000, Owner: "faculty-sci", Product: "gpu"},
		{Kind: "allocate", At: -55, Start: -55, End: 45, Quota: 15000, Owner: "faculty-med", Product: "gpu"},
		{Kind: "allocate", At: -55, Start: -55, End: 45, Quota: 8000, Owner: "direct-flagship", Product: "gpu"},
	}

	for i := 1; i <= 6; i++ {
		sciChild := fmt.Sprintf("sci-lab-%d", i)
		steps = append(steps, accountingScenarioStep{Kind: "allocate", At: -52, Start: -52, End: 45, Quota: 19000, Parent: "faculty-sci", Owner: sciChild, Product: "cpu"})
		if i <= 3 {
			steps = append(steps, accountingScenarioStep{Kind: "allocate", At: -52, Start: -52, End: 45, Quota: 5500, Parent: "faculty-sci", Owner: sciChild, Product: "gpu"})
		}

		engChild := fmt.Sprintf("eng-lab-%d", i)
		steps = append(steps, accountingScenarioStep{Kind: "allocate", At: -52, Start: -52, End: 45, Quota: 6000, Parent: "faculty-eng", Owner: engChild, Product: "cpu"})

		medChild := fmt.Sprintf("med-lab-%d", i)
		steps = append(steps, accountingScenarioStep{Kind: "allocate", At: -52, Start: -52, End: 20, Quota: 16000, Parent: "faculty-med", Owner: medChild, Product: "cpu"})
		if i <= 3 {
			steps = append(steps, accountingScenarioStep{Kind: "allocate", At: -52, Start: -52, End: 20, Quota: 6000, Parent: "faculty-med", Owner: medChild, Product: "gpu"})
		}
	}

	for day := -48; day <= -1; day += 2 {
		steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: 420, Owner: "direct-flagship", Product: "cpu"})
		steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: 110, Owner: "direct-flagship", Product: "gpu"})
		if day >= -20 {
			steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: 90, Owner: "direct-pilot", Product: "cpu"})
		}

		for i := 1; i <= 6; i++ {
			sciChild := fmt.Sprintf("sci-lab-%d", i)
			steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: 340 + int64(i*20), Owner: sciChild, Product: "cpu"})
			if i <= 3 {
				steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: 95 + int64(i*10), Owner: sciChild, Product: "gpu"})
			}

			engChild := fmt.Sprintf("eng-lab-%d", i)
			if i <= 3 {
				steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: 65, Owner: engChild, Product: "cpu"})
			}

			medChild := fmt.Sprintf("med-lab-%d", i)
			medBurn := int64(580)
			if i <= 2 {
				medBurn = 640
			}
			steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: medBurn, Owner: medChild, Product: "cpu"})
			if i <= 3 {
				steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: 210, Owner: medChild, Product: "gpu"})
			}
		}
	}

	steps = append(steps, accountingScenarioStep{
		Kind:  "checkpoint",
		At:    0,
		Owner: "faculty-sci",
		Label: "Deep hierarchy final state",
	})

	return accountingScenarioDefinition{
		Name:                "usage-deep-hierarchy",
		Code:                "UDHP",
		Description:         "3-level funding hierarchy (National allocator -> 3 Faculty sub-allocators + 2 direct projects -> 18 downstream labs) exercising hierarchy and sub-allocator aggregation.",
		SampleIntervalHours: 24,
		PrimaryProjectRole:  "root",
		Products:            products,
		SubAllocatorRoles:   []string{"root", "faculty-sci", "faculty-eng", "faculty-med"},
		UserOwnedRoles:      []string{"root", "faculty-sci", "faculty-eng", "faculty-med"},
		Steps:               accountingScenarioSortSteps(steps),
	}
}

func accountingScenarioUsageCapacityLifecycle() accountingScenarioDefinition {
	products := []accountingScenarioProductSpec{
		{
			Key:             "storage-main",
			Slug:            "cephfs-main",
			ProductType:     accapi.ProductTypeStorage,
			AccountingModel: accapi.AccountingModelCapacity,
			Unit:            accountingScenarioUnitGb(),
			Frequency:       accapi.AccountingFrequencyOnce,
			RootQuota:       200000,
		},
		{
			Key:             "storage-archive",
			Slug:            "tape-archive",
			ProductType:     accapi.ProductTypeStorage,
			AccountingModel: accapi.AccountingModelCapacity,
			Unit:            accountingScenarioUnitGb(),
			Frequency:       accapi.AccountingFrequencyOnce,
			RootQuota:       200000,
		},
		{
			Key:             "public-ip",
			Slug:            "public-ip",
			ProductType:     accapi.ProductTypeNetworkIp,
			AccountingModel: accapi.AccountingModelCapacity,
			Unit:            accountingScenarioUnitIp(),
			Frequency:       accapi.AccountingFrequencyOnce,
			RootQuota:       500,
		},
	}

	steps := []accountingScenarioStep{
		{Kind: "allocate", At: -60, Start: -60, End: -25, Quota: 10000, Owner: "project", Product: "storage-main"},
		{Kind: "allocate", At: -60, Start: -18, End: 12, Quota: 15000, Owner: "project", Product: "storage-main"},
		{Kind: "allocate", At: -60, Start: -18, End: 90, Quota: 6000, Owner: "project", Product: "storage-main"},

		{Kind: "allocate", At: -60, Start: -60, End: 90, Quota: 25000, Owner: "project", Product: "storage-archive"},
		{Kind: "allocate", At: -60, Start: -60, End: 40, Quota: 8, Owner: "project", Product: "public-ip"},

		{Kind: "charge", At: -58, Quota: 4000, Owner: "project", Product: "storage-main"},
		{Kind: "charge", At: -45, Quota: 4200, Owner: "project", Product: "storage-main"},
		{Kind: "charge", At: -32, Quota: 1200, Owner: "project", Product: "storage-main"},
		{Kind: "charge", At: -22, Quota: -1500, Owner: "project", Product: "storage-main"},
		{Kind: "charge", At: -12, Quota: 4800, Owner: "project", Product: "storage-main"},
		{Kind: "charge", At: -4, Quota: -900, Owner: "project", Product: "storage-main"},

		{Kind: "charge", At: -55, Quota: 9000, Owner: "project", Product: "storage-archive"},
		{Kind: "charge", At: -30, Quota: 7500, Owner: "project", Product: "storage-archive"},
		{Kind: "charge", At: -10, Quota: 3200, Owner: "project", Product: "storage-archive"},

		{Kind: "charge", At: -50, Quota: 3, Owner: "project", Product: "public-ip"},
		{Kind: "charge", At: -28, Quota: 4, Owner: "project", Product: "public-ip"},
		{Kind: "charge", At: -12, Quota: -2, Owner: "project", Product: "public-ip"},

		{Kind: "checkpoint", At: 0, Owner: "project", Label: "Capacity excess recovery and upcoming drop"},
	}

	return accountingScenarioDefinition{
		Name:                "usage-capacity-lifecycle",
		Code:                "UCAP",
		Description:         "Capacity-focused scenario with storage growth/deletions, a 7-day unfunded over-quota excess window, renewal recovery, upcoming quota drop warning, and Public IPs.",
		SampleIntervalHours: 24,
		PrimaryProjectRole:  "project",
		Products:            products,
		UserOwnedRoles:      []string{"project", "root"},
		Steps:               accountingScenarioSortSteps(steps),
	}
}

func accountingScenarioUsageGrantAllocator() accountingScenarioDefinition {
	products := []accountingScenarioProductSpec{
		{
			Key:             "cpu-zen5",
			Slug:            "cpu-amd-zen5",
			ProductType:     accapi.ProductTypeCompute,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitCore(),
			Frequency:       accapi.AccountingFrequencyPeriodicHour,
			RootQuota:       12000000,
		},
		{
			Key:             "cpu-legacy",
			Slug:            "u1-standard",
			ProductType:     accapi.ProductTypeCompute,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitCore(),
			Frequency:       accapi.AccountingFrequencyPeriodicHour,
			RootQuota:       6000000,
		},
		{
			Key:             "storage",
			Slug:            "cephfs",
			ProductType:     accapi.ProductTypeStorage,
			AccountingModel: accapi.AccountingModelCapacity,
			Unit:            accountingScenarioUnitGb(),
			Frequency:       accapi.AccountingFrequencyOnce,
			RootQuota:       4000000,
		},
		{
			Key:             "inference-llm",
			Slug:            "inference-credits",
			ProductType:     accapi.ProductTypeInference,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitCredit(),
			Frequency:       accapi.AccountingFrequencyOnce,
			RootQuota:       40000000000,
		},
	}

	var steps []accountingScenarioStep
	subAllocatorRoles := []string{"root"}

	const directCount = 160
	for i := 1; i <= directCount; i++ {
		owner := fmt.Sprintf("grant-%03d", i)
		isSubAlloc := i <= 8
		if isSubAlloc {
			owner = fmt.Sprintf("suballoc-%02d", i)
			subAllocatorRoles = append(subAllocatorRoles, owner)
		}

		var quotaCpu int64
		var burnPerTick int64
		var quotaInf int64
		var burnInfPerTick int64
		endDay := 60
		retiredQuota := int64(0)
		retiredCharge := int64(0)

		switch {
		case i <= 8:
			quotaCpu = 240000
			quotaInf = 400000000
			retiredQuota = 80000
			retiredCharge = 65000
		case i <= 18:
			quotaCpu = 180000
			burnPerTick = 5800
			quotaInf = 300000000
			burnInfPerTick = 9500000
			retiredQuota = 60000
			retiredCharge = 55000
		case i <= 42:
			quotaCpu = 50000
			burnPerTick = 1850
			quotaInf = 120000000
			burnInfPerTick = 4200000
			endDay = 18
		case i <= 95:
			quotaCpu = 40000
			burnPerTick = 750
			quotaInf = 80000000
			burnInfPerTick = 1800000
		case i <= 125:
			quotaCpu = 30000
			burnPerTick = 140
			quotaInf = 50000000
			burnInfPerTick = 500000
		default:
			quotaCpu = 25000
			burnPerTick = 0
			quotaInf = 40000000
			burnInfPerTick = 0
		}

		if retiredQuota > 0 {
			steps = append(steps, accountingScenarioStep{Kind: "allocate", At: -75, Start: -75, End: -30, Quota: retiredQuota, Owner: owner, Product: "cpu-legacy"})
			if !isSubAlloc && retiredCharge > 0 {
				steps = append(steps, accountingScenarioStep{Kind: "charge", At: -50, Quota: retiredCharge, Owner: owner, Product: "cpu-legacy"})
			}
		}

		steps = append(steps, accountingScenarioStep{Kind: "allocate", At: -45, Start: -45, End: endDay, Quota: quotaCpu, Owner: owner, Product: "cpu-zen5"})
		steps = append(steps, accountingScenarioStep{Kind: "allocate", At: -45, Start: -45, End: endDay, Quota: quotaInf, Owner: owner, Product: "inference-llm"})
		if i%3 == 0 {
			steps = append(steps, accountingScenarioStep{Kind: "allocate", At: -45, Start: -45, End: 60, Quota: quotaCpu / 2, Owner: owner, Product: "cpu-legacy"})
		}
		if i%2 == 0 {
			storageQuota := int64(12000 + (i%5)*4000)
			steps = append(steps, accountingScenarioStep{Kind: "allocate", At: -45, Start: -45, End: 60, Quota: storageQuota, Owner: owner, Product: "storage"})
			if !isSubAlloc && i <= 125 {
				occ := (storageQuota * int64(25+(i*13)%70)) / 100
				steps = append(steps, accountingScenarioStep{Kind: "charge", At: -38, Quota: occ, Owner: owner, Product: "storage"})
			}
		}

		if isSubAlloc {
			for g := 1; g <= 4; g++ {
				grand := fmt.Sprintf("suballoc-%02d-g%d", i, g)
				downQuota := int64(45000)
				downInfQuota := int64(75000000)
				if i%2 == 0 {
					downQuota = 22000
					downInfQuota = 45000000
				}
				steps = append(steps, accountingScenarioStep{Kind: "allocate", At: -42, Start: -42, End: 60, Quota: downQuota, Parent: owner, Owner: grand, Product: "cpu-zen5"})
				steps = append(steps, accountingScenarioStep{Kind: "allocate", At: -42, Start: -42, End: 60, Quota: downInfQuota, Parent: owner, Owner: grand, Product: "inference-llm"})
				if g <= 3 {
					for day := -36; day <= -2; day += 4 {
						steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: 1600 + int64(i*120), Owner: grand, Product: "cpu-zen5"})
						steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: 2200000 + int64(i*250000), Owner: grand, Product: "inference-llm"})
					}
				}
			}
		} else if burnPerTick > 0 {
			for day := -40; day <= -2; day += 2 {
				steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: burnPerTick, Owner: owner, Product: "cpu-zen5"})
				if burnInfPerTick > 0 && (day+i)%3 == 0 {
					steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: burnInfPerTick, Owner: owner, Product: "inference-llm"})
				}
				if i%3 == 0 && day%4 == 0 {
					steps = append(steps, accountingScenarioStep{Kind: "charge", At: day, Quota: burnPerTick / 3, Owner: owner, Product: "cpu-legacy"})
				}
			}
		}
	}

	steps = append(steps, accountingScenarioStep{
		Kind:  "checkpoint",
		At:    0,
		Owner: "grant-009",
		Label: "Large grant allocator final state",
	})

	return accountingScenarioDefinition{
		Name:                accountingScenarioUsageAllocatorName,
		Code:                "UGRA",
		Description:         "Grant allocator (canConsumeResources=false) with 160 direct recipients (including 8 sub-allocators with 32 downstream projects), Pareto burn distribution, idle/at-risk cohorts, and 3 products over 60 days. Run by name only.",
		ExplicitRun:         true,
		ExtendedTimeout:     true,
		SkipGrantRecords:    true,
		SampleIntervalHours: 24,
		PrimaryProjectRole:  "root",
		Products:            products,
		SubAllocatorRoles:   subAllocatorRoles,
		UserOwnedRoles:      []string{"root"},
		Steps:               accountingScenarioSortSteps(steps),
	}
}
