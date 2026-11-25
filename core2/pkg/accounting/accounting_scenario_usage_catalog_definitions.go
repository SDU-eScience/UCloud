package accounting

import (
	accapi "ucloud.dk/shared/pkg/accounting"
)

func accountingScenarioUnitDkk() accapi.AccountingUnit {
	return accapi.AccountingUnit{
		Name:                   "DKK",
		NamePlural:             "DKK",
		FloatingPoint:          true,
		DisplayFrequencySuffix: false,
	}
}

func accountingScenarioUsageProviderCatalog() accountingScenarioDefinition {
	products := []accountingScenarioProductSpec{
		{
			Key:             "gpu-minute-standard",
			Slug:            "gpu-minute-standard",
			ProductType:     accapi.ProductTypeCompute,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitGpu(),
			Frequency:       accapi.AccountingFrequencyPeriodicMinute,
			RootQuota:       2000000,
		},
		{
			Key:             "gpu-minute-large",
			Slug:            "gpu-minute-large",
			ProductType:     accapi.ProductTypeCompute,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitGpu(),
			Frequency:       accapi.AccountingFrequencyPeriodicMinute,
			RootQuota:       1200000,
		},
		{
			Key:             "gpu-minute-burst",
			Slug:            "gpu-minute-burst",
			ProductType:     accapi.ProductTypeCompute,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitGpu(),
			Frequency:       accapi.AccountingFrequencyPeriodicMinute,
			RootQuota:       800000,
		},
		{
			Key:             "gpu-minute-legacy",
			Slug:            "gpu-minute-legacy",
			ProductType:     accapi.ProductTypeCompute,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitGpu(),
			Frequency:       accapi.AccountingFrequencyPeriodicMinute,
			RootQuota:       600000,
		},
		{
			Key:             "gpu-hour-standard",
			Slug:            "gpu-hour-standard",
			ProductType:     accapi.ProductTypeCompute,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitGpu(),
			Frequency:       accapi.AccountingFrequencyPeriodicHour,
			RootQuota:       400000,
		},
		{
			Key:             "core-hour-standard",
			Slug:            "core-hour-standard",
			ProductType:     accapi.ProductTypeCompute,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitCore(),
			Frequency:       accapi.AccountingFrequencyPeriodicHour,
			RootQuota:       4000000,
		},
		{
			Key:             "core-minute-standard",
			Slug:            "core-minute-standard",
			ProductType:     accapi.ProductTypeCompute,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitCore(),
			Frequency:       accapi.AccountingFrequencyPeriodicMinute,
			RootQuota:       12000000,
		},
		{
			Key:             "storage-primary",
			Slug:            "storage-primary",
			ProductType:     accapi.ProductTypeStorage,
			AccountingModel: accapi.AccountingModelCapacity,
			Unit:            accountingScenarioUnitGb(),
			Frequency:       accapi.AccountingFrequencyOnce,
			RootQuota:       400000,
		},
		{
			Key:             "storage-archive",
			Slug:            "storage-archive",
			ProductType:     accapi.ProductTypeStorage,
			AccountingModel: accapi.AccountingModelCapacity,
			Unit:            accountingScenarioUnitGb(),
			Frequency:       accapi.AccountingFrequencyOnce,
			RootQuota:       400000,
		},
		{
			Key:             "license-hourly",
			Slug:            "license-hourly",
			ProductType:     accapi.ProductTypeLicense,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitDkk(),
			Frequency:       accapi.AccountingFrequencyPeriodicHour,
			RootQuota:       1000000000,
		},
		{
			Key:             "inference-credit",
			Slug:            "inference-credit",
			ProductType:     accapi.ProductTypeInference,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitCredit(),
			Frequency:       accapi.AccountingFrequencyOnce,
			RootQuota:       4000000000,
		},
		{
			Key:             "gpu-minute-alt",
			Slug:            "gpu-minute-alt",
			Provider:        "usagegen-alt",
			ProductType:     accapi.ProductTypeCompute,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitGpu(),
			Frequency:       accapi.AccountingFrequencyPeriodicMinute,
			RootQuota:       1000000,
		},
		{
			Key:             "core-hour-alt",
			Slug:            "core-hour-alt",
			Provider:        "usagegen-alt",
			ProductType:     accapi.ProductTypeCompute,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitCore(),
			Frequency:       accapi.AccountingFrequencyPeriodicHour,
			RootQuota:       2000000,
		},
		{
			Key:             "storage-alt",
			Slug:            "storage-alt",
			Provider:        "usagegen-alt",
			ProductType:     accapi.ProductTypeStorage,
			AccountingModel: accapi.AccountingModelCapacity,
			Unit:            accountingScenarioUnitGb(),
			Frequency:       accapi.AccountingFrequencyOnce,
			RootQuota:       200000,
		},
	}

	steps := []accountingScenarioStep{
		{Kind: "allocate", At: -90, Start: -90, End: 60, Quota: 180000, Owner: "team", Product: "gpu-minute-standard"},
		{Kind: "allocate", At: -90, Start: -90, End: 60, Quota: 120000, Owner: "team", Product: "gpu-minute-large"},
		{Kind: "allocate", At: -90, Start: -90, End: 45, Quota: 60000, Owner: "team", Product: "gpu-minute-burst"},
		{Kind: "allocate", At: -90, Start: -90, End: 60, Quota: 90000, Parent: "team", Owner: "analytics", Product: "gpu-minute-standard"},
		{Kind: "allocate", At: -90, Start: -90, End: 60, Quota: 45000, Parent: "team", Owner: "ml", Product: "gpu-minute-large"},
		{Kind: "allocate", At: -75, Start: -75, End: 60, Quota: 30000, Parent: "team", Owner: "student", Product: "gpu-minute-burst"},

		{Kind: "allocate", At: -120, Start: -120, End: -45, Quota: 90000, Owner: "team", Product: "gpu-minute-legacy"},
		{Kind: "allocate", At: -40, Start: -40, End: 60, Quota: 70000, Owner: "team", Product: "gpu-minute-legacy"},

		{Kind: "allocate", At: -90, Start: -90, End: 60, Quota: 3000, Owner: "team", Product: "gpu-hour-standard"},
		{Kind: "allocate", At: -90, Start: -90, End: 60, Quota: 2000, Parent: "team", Owner: "ml", Product: "gpu-hour-standard"},

		{Kind: "allocate", At: -90, Start: -90, End: 60, Quota: 150000, Owner: "team", Product: "core-hour-standard"},
		{Kind: "allocate", At: -90, Start: -90, End: 60, Quota: 100000, Parent: "team", Owner: "analytics", Product: "core-hour-standard"},
		{Kind: "allocate", At: -90, Start: -90, End: 45, Quota: 800000, Owner: "team", Product: "core-minute-standard"},

		{Kind: "allocate", At: -90, Start: -90, End: 60, Quota: 25000, Owner: "team", Product: "storage-primary"},
		{Kind: "allocate", At: -90, Start: -90, End: 30, Quota: 18000, Parent: "team", Owner: "archive", Product: "storage-primary"},
		{Kind: "allocate", At: -90, Start: -90, End: 60, Quota: 30000, Owner: "team", Product: "storage-archive"},

		{Kind: "allocate", At: -90, Start: -90, End: 60, Quota: 800000000, Owner: "team", Product: "inference-credit"},
		{Kind: "allocate", At: -90, Start: -90, End: 60, Quota: 400000000, Parent: "team", Owner: "ml", Product: "inference-credit"},

		{Kind: "allocate", At: -90, Start: -90, End: 60, Quota: 100000, Owner: "team", Product: "gpu-minute-alt"},
		{Kind: "allocate", At: -90, Start: -90, End: 60, Quota: 30000, Parent: "team", Owner: "analytics", Product: "gpu-minute-alt"},
		{Kind: "allocate", At: -90, Start: -90, End: 60, Quota: 80000, Owner: "team", Product: "core-hour-alt"},
		{Kind: "allocate", At: -90, Start: -90, End: 60, Quota: 15000, Owner: "team", Product: "storage-alt"},

		{Kind: "allocate", At: -90, Start: -90, End: 60, Quota: 200000, Owner: "platform", Product: "gpu-minute-standard"},
		{Kind: "allocate", At: -90, Start: -90, End: 60, Quota: 100000, Parent: "platform", Owner: "edge-a", Product: "gpu-minute-standard"},
		{Kind: "allocate", At: -90, Start: -90, End: 60, Quota: 70000, Parent: "platform", Owner: "edge-b", Product: "gpu-minute-standard"},
	}

	isWeekend := func(day int) bool {
		weekday := ((day % 7) + 7) % 7
		return weekday == 5 || weekday == 6
	}

	for day := -75; day <= -1; day += 3 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 5000 + int64((day+100)%4)*1000, Owner: "team", Product: "gpu-minute-standard",
		})
	}
	for day := -80; day <= -1; day += 2 {
		if isWeekend(day) {
			continue
		}
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 3000 + int64((day+100)%3)*700, Owner: "analytics", Product: "gpu-minute-standard",
		})
	}
	for day := -70; day <= -1; day += 4 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 1200 + int64((day+100)%3)*300, Owner: "edge-a", Product: "gpu-minute-standard",
		})
	}
	for day := -75; day <= -1; day += 2 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 2500 + int64((day+100)%5)*600, Owner: "ml", Product: "gpu-minute-large",
		})
	}
	for day := -60; day <= -1; day += 5 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 8000 + int64((day+100)%4)*2000, Owner: "team", Product: "gpu-minute-burst",
		})
	}
	for day := -115; day <= -50; day += 5 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 2500 + int64((day+100)%3)*500, Owner: "team", Product: "gpu-minute-legacy",
		})
	}
	for day := -35; day <= -1; day += 4 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 1500, Owner: "team", Product: "gpu-minute-legacy",
		})
	}
	for day := -70; day <= -1; day += 4 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 120 + int64((day+100)%4)*30, Owner: "team", Product: "gpu-hour-standard",
		})
	}
	for day := -70; day <= -1; day += 6 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 80, Owner: "ml", Product: "gpu-hour-standard",
		})
	}
	for day := -80; day <= -1; day++ {
		if isWeekend(day) {
			continue
		}
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 900 + int64((day+100)%5)*150, Owner: "analytics", Product: "core-hour-standard",
		})
		if day%2 == 0 {
			steps = append(steps, accountingScenarioStep{
				Kind: "charge", At: day, Quota: 600, Owner: "team", Product: "core-hour-standard",
			})
		}
	}
	for day := -75; day <= -1; day += 2 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 12000 + int64((day+100)%4)*3000, Owner: "team", Product: "core-minute-standard",
		})
	}

	steps = append(steps,
		accountingScenarioStep{Kind: "charge", At: -85, Quota: 4000, Owner: "team", Product: "storage-primary"},
		accountingScenarioStep{Kind: "charge", At: -65, Quota: 2500, Owner: "team", Product: "storage-primary"},
		accountingScenarioStep{Kind: "charge", At: -45, Quota: 3000, Owner: "team", Product: "storage-primary"},
		accountingScenarioStep{Kind: "charge", At: -25, Quota: -1200, Owner: "team", Product: "storage-primary"},
		accountingScenarioStep{Kind: "charge", At: -10, Quota: 2200, Owner: "team", Product: "storage-primary"},
		accountingScenarioStep{Kind: "charge", At: -3, Quota: -900, Owner: "team", Product: "storage-primary"},
		accountingScenarioStep{Kind: "charge", At: -70, Quota: 1800, Owner: "archive", Product: "storage-primary"},
		accountingScenarioStep{Kind: "charge", At: -40, Quota: 1200, Owner: "archive", Product: "storage-primary"},
		accountingScenarioStep{Kind: "charge", At: -15, Quota: -600, Owner: "archive", Product: "storage-primary"},
		accountingScenarioStep{Kind: "charge", At: -80, Quota: 8000, Owner: "team", Product: "storage-archive"},
		accountingScenarioStep{Kind: "charge", At: -50, Quota: 5000, Owner: "team", Product: "storage-archive"},
		accountingScenarioStep{Kind: "charge", At: -20, Quota: -2000, Owner: "team", Product: "storage-archive"},
		accountingScenarioStep{Kind: "charge", At: -60, Quota: 6000, Owner: "team", Product: "storage-alt"},
		accountingScenarioStep{Kind: "charge", At: -30, Quota: 2500, Owner: "team", Product: "storage-alt"},
		accountingScenarioStep{Kind: "charge", At: -10, Quota: -1000, Owner: "team", Product: "storage-alt"},
	)

	for day := -80; day <= -1; day += 3 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 5000000 + int64((day+100)%4)*1500000, Owner: "team", Product: "inference-credit",
		})
		if day%2 == 0 {
			steps = append(steps, accountingScenarioStep{
				Kind: "charge", At: day, Quota: 2500000 + int64((day+100)%3)*700000, Owner: "ml", Product: "inference-credit",
			})
		}
	}
	for day := -70; day <= -1; day += 4 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 2500 + int64((day+100)%3)*500, Owner: "analytics", Product: "gpu-minute-alt",
		})
	}
	for day := -75; day <= -1; day += 5 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 1000, Owner: "team", Product: "core-hour-alt",
		})
	}

	steps = append(steps, accountingScenarioStep{
		Kind:  "checkpoint",
		At:    0,
		Owner: "team",
		Label: "Multi-provider catalog final state",
	})

	return accountingScenarioDefinition{
		Name:                "usage-provider-catalog",
		Code:                "UCAT",
		Description:         "Multi-provider catalog with repeated compute units across hourly and minute frequencies, mixed consumption and capacity products, a retired baseline, idle products, and sub-allocator downstream projects.",
		SampleIntervalHours: 24,
		PrimaryProjectRole:  "team",
		Products:            products,
		SubAllocatorRoles:   []string{"root", "platform"},
		UserOwnedRoles:      []string{"root", "team", "analytics", "ml", "archive", "student", "platform"},
		Steps:               accountingScenarioSortSteps(steps),
	}
}

func accountingScenarioUsageInferenceCredits() accountingScenarioDefinition {
	products := []accountingScenarioProductSpec{
		{
			Key:             "inference-chat",
			Slug:            "inference-chat",
			ProductType:     accapi.ProductTypeInference,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitCredit(),
			Frequency:       accapi.AccountingFrequencyOnce,
			RootQuota:       6000000000,
		},
		{
			Key:             "inference-batch",
			Slug:            "inference-batch",
			ProductType:     accapi.ProductTypeInference,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitCredit(),
			Frequency:       accapi.AccountingFrequencyOnce,
			RootQuota:       10000000000,
		},
		{
			Key:             "inference-embed",
			Slug:            "inference-embed",
			Provider:        "usagegen-alt",
			ProductType:     accapi.ProductTypeInference,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitCredit(),
			Frequency:       accapi.AccountingFrequencyOnce,
			RootQuota:       4000000000,
		},
	}

	steps := []accountingScenarioStep{
		{Kind: "allocate", At: -120, Start: -120, End: -50, Quota: 500000000, Owner: "team", Product: "inference-chat"},
		{Kind: "allocate", At: -45, Start: -45, End: 60, Quota: 1500000000, Owner: "team", Product: "inference-chat"},
		{Kind: "allocate", At: -45, Start: -45, End: 60, Quota: 800000000, Owner: "research", Product: "inference-chat"},
		{Kind: "allocate", At: -45, Start: -45, End: 60, Quota: 600000000, Parent: "team", Owner: "child-a", Product: "inference-chat"},

		{Kind: "allocate", At: -60, Start: -60, End: 60, Quota: 2500000000, Owner: "platform", Product: "inference-chat"},
		{Kind: "allocate", At: -60, Start: -60, End: 60, Quota: 700000000, Parent: "platform", Owner: "edge-a", Product: "inference-chat"},
		{Kind: "allocate", At: -60, Start: -60, End: 60, Quota: 500000000, Parent: "platform", Owner: "edge-b", Product: "inference-chat"},

		{Kind: "allocate", At: -45, Start: -45, End: 60, Quota: 2000000000, Owner: "team", Product: "inference-batch"},
		{Kind: "allocate", At: -45, Start: -45, End: 60, Quota: 1500000000, Owner: "ops", Product: "inference-batch"},
		{Kind: "allocate", At: -45, Start: -45, End: 60, Quota: 900000000, Parent: "team", Owner: "child-b", Product: "inference-batch"},

		{Kind: "allocate", At: -45, Start: -45, End: 60, Quota: 1000000000, Owner: "research", Product: "inference-embed"},
	}

	for day := -115; day <= -55; day += 5 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 8000000, Owner: "team", Product: "inference-chat",
		})
	}
	for day := -40; day <= -1; day += 2 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 6000000 + int64((day+100)%4)*1500000, Owner: "team", Product: "inference-chat",
		})
	}
	for day := -40; day <= -1; day += 3 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 3000000 + int64((day+100)%3)*800000, Owner: "research", Product: "inference-chat",
		})
	}
	for day := -40; day <= -1; day += 4 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 2000000, Owner: "child-a", Product: "inference-chat",
		})
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 1500000, Owner: "edge-a", Product: "inference-chat",
		})
	}
	for day := -40; day <= -1; day += 3 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 20000000 + int64((day+100)%4)*5000000, Owner: "team", Product: "inference-batch",
		})
	}
	for day := -40; day <= -1; day += 4 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 12000000, Owner: "ops", Product: "inference-batch",
		})
	}
	for day := -40; day <= -1; day += 6 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 7000000, Owner: "child-b", Product: "inference-batch",
		})
	}
	for day := -25; day <= -1; day += 2 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 4000000 + int64((day+100)%3)*900000, Owner: "research", Product: "inference-embed",
		})
	}

	steps = append(steps, accountingScenarioStep{
		Kind:  "checkpoint",
		At:    0,
		Owner: "team",
		Label: "Inference credit portfolio final state",
	})

	return accountingScenarioDefinition{
		Name:                "usage-inference-credits",
		Code:                "UINF",
		Description:         "Inference credit portfolio with multiple providers, parent and child allocations, a sub-allocator with downstream projects, bursty usage, an idle downstream project, and a retired baseline.",
		SampleIntervalHours: 24,
		PrimaryProjectRole:  "team",
		Products:            products,
		SubAllocatorRoles:   []string{"root", "platform"},
		UserOwnedRoles:      []string{"root", "team", "research", "ops", "platform"},
		Steps:               accountingScenarioSortSteps(steps),
	}
}

func accountingScenarioUsageInferenceCoFunded() accountingScenarioDefinition {
	products := []accountingScenarioProductSpec{
		{
			Key:             "inference-credits",
			Slug:            "inference-credits",
			ProductType:     accapi.ProductTypeInference,
			AccountingModel: accapi.AccountingModelConsumption,
			Unit:            accountingScenarioUnitCredit(),
			Frequency:       accapi.AccountingFrequencyOnce,
			RootQuota:       8000000000,
		},
	}

	steps := []accountingScenarioStep{
		{Kind: "allocate", At: -85, Start: -85, End: 60, Quota: 2500000000, Owner: "allocator-a", Product: "inference-credits"},
		{Kind: "allocate", At: -85, Start: -85, End: 60, Quota: 2500000000, Owner: "allocator-b", Product: "inference-credits"},

		{Kind: "allocate", At: -55, Start: -55, End: 60, Quota: 600000000, Parent: "allocator-a", Owner: "alpha-lab", Product: "inference-credits"},
		{Kind: "allocate", At: -55, Start: -55, End: 60, Quota: 400000000, Parent: "allocator-a", Owner: "beta-lab", Product: "inference-credits"},
		{Kind: "allocate", At: -55, Start: -55, End: 60, Quota: 500000000, Parent: "allocator-a", Owner: "suballoc-a", Product: "inference-credits"},
		{Kind: "allocate", At: -55, Start: -55, End: 60, Quota: 350000000, Parent: "suballoc-a", Owner: "deep-lab", Product: "inference-credits"},

		{Kind: "allocate", At: -55, Start: -55, End: -18, Quota: 150000000, Parent: "allocator-a", Owner: "shared-lab", Product: "inference-credits"},
		{Kind: "allocate", At: -55, Start: -55, End: 60, Quota: 600000000, Parent: "allocator-b", Owner: "shared-lab", Product: "inference-credits"},

		{Kind: "allocate", At: -80, Start: -80, End: -40, Quota: 120000000, Parent: "allocator-a", Owner: "former-lab", Product: "inference-credits"},
		{Kind: "allocate", At: -45, Start: -45, End: 60, Quota: 700000000, Parent: "allocator-b", Owner: "former-lab", Product: "inference-credits"},
	}

	for day := -75; day <= -45; day += 3 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 7000000, Owner: "former-lab", Product: "inference-credits",
		})
	}
	for day := -38; day <= -1; day += 2 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 14000000 + int64((day+100)%4)*2000000, Owner: "former-lab", Product: "inference-credits",
		})
	}
	for day := -50; day <= -1; day += 2 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 10000000 + int64((day+100)%5)*1500000, Owner: "alpha-lab", Product: "inference-credits",
		})
	}
	for day := -50; day <= -1; day += 3 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 7000000 + int64((day+100)%3)*1200000, Owner: "beta-lab", Product: "inference-credits",
		})
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 6000000 + int64((day+100)%4)*900000, Owner: "deep-lab", Product: "inference-credits",
		})
	}
	for day := -50; day <= -1; day += 2 {
		steps = append(steps, accountingScenarioStep{
			Kind: "charge", At: day, Quota: 12000000 + int64((day+100)%3)*2500000, Owner: "shared-lab", Product: "inference-credits",
		})
	}

	steps = append(steps, accountingScenarioStep{
		Kind:  "checkpoint",
		At:    0,
		Owner: "allocator-a",
		Label: "Co-funded inference tree final state",
	})

	return accountingScenarioDefinition{
		Name:                "usage-inference-cofunded",
		Code:                "UICO",
		Description:         "Multi-parent inference tree with exclusive sub-projects, a 2-level sub-allocator, a co-funded sub-project with mid-period spillover, and an expired dormant funding edge.",
		SampleIntervalHours: 24,
		PrimaryProjectRole:  "allocator-a",
		Products:            products,
		SubAllocatorRoles:   []string{"root", "allocator-a", "allocator-b", "suballoc-a"},
		UserOwnedRoles:      []string{"root", "allocator-a", "allocator-b", "alpha-lab", "beta-lab", "suballoc-a", "shared-lab", "former-lab"},
		Steps:               accountingScenarioSortSteps(steps),
	}
}
