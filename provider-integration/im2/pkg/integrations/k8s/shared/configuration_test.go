package shared

import (
	"testing"

	cfg "ucloud.dk/pkg/config"
	apm "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/util"
)

func TestBuildMachineNameV2(t *testing.T) {
	group := cfg.K8sMachineCategoryGroup{
		NameSuffix:      cfg.MachineResourceTypeGpuV2,
		Fraction:        apm.Fraction{Numerator: 1, Denominator: 7},
		GpuResourceType: "nvidia.com/mig-1g.5gb",
	}
	conf := cfg.K8sMachineConfiguration{Gpu: 2}

	got := cfg.BuildMachineName("gpu-cat", "full", group, conf)
	want := "gpu-cat-2-mig.1g"
	if got != want {
		t.Fatalf("expected %q, got %q", want, got)
	}
}

func TestGpuResourceTypesForCategory(t *testing.T) {
	ServiceConfig = &cfg.ServicesConfigurationKubernetes{}
	ServiceConfig.Compute.Machines = map[string]cfg.K8sMachineCategory{
		"gpu-cat": {
			Payment: cfg.PaymentInfo{Unit: cfg.MachineResourceTypeGpu},
			Groups: map[string]cfg.K8sMachineCategoryGroup{
				"full": {
					NameSuffix:      cfg.MachineResourceTypeGpuV2,
					Fraction:        apm.Fraction{Numerator: 1, Denominator: 1},
					GpuResourceType: "nvidia.com/gpu",
				},
				"mig": {
					NameSuffix:      cfg.MachineResourceTypeGpuV2,
					Fraction:        apm.Fraction{Numerator: 1, Denominator: 7},
					GpuResourceType: "nvidia.com/mig-1g.10gb",
				},
			},
		},
	}

	resourceTypes := GpuResourceTypesForCategory("gpu-cat")
	if len(resourceTypes) != 2 {
		t.Fatalf("expected two configured resource types, got %v", resourceTypes)
	}
	if resourceTypes[0] != "nvidia.com/gpu" || resourceTypes[1] != "nvidia.com/mig-1g.10gb" {
		t.Fatalf("unexpected resource types: %v", resourceTypes)
	}
}

func TestNodeCpuMillisReservedAppliesFraction(t *testing.T) {
	ServiceConfig = &cfg.ServicesConfigurationKubernetes{}
	ServiceConfig.Compute.Machines = map[string]cfg.K8sMachineCategory{
		"cpu-cat": {
			Payment:                 cfg.PaymentInfo{Unit: cfg.MachineResourceTypeCpu},
			SystemReservedCpuMillis: util.OptValue(0),
			Groups: map[string]cfg.K8sMachineCategoryGroup{
				"cpu": {
					NameSuffix: cfg.MachineResourceTypeCpuV2,
					Fraction:   apm.Fraction{Numerator: 1, Denominator: 10},
					Configs: []cfg.K8sMachineConfiguration{{
						AdvertisedCpu: 2,
					}},
				},
			},
		},
	}

	product := apm.ProductV2{
		Name:         "cpu-cat-2-vcpu",
		Category:     apm.ProductCategory{Name: "cpu-cat"},
		Cpu:          2,
		MemoryInGigs: 1,
		Fraction:     apm.Fraction{Numerator: 1, Denominator: 10},
	}

	if got := NodeCpuMillisNormalizedWithReserved(&product); got != 200 {
		t.Fatalf("expected 200 millicpu, got %d", got)
	}
}

func TestJobDimensionsCpuFractionAppliedOnce(t *testing.T) {
	ServiceConfig = &cfg.ServicesConfigurationKubernetes{}
	ServiceConfig.Compute.Machines = map[string]cfg.K8sMachineCategory{
		"cpu-cat": {
			Payment:                 cfg.PaymentInfo{Unit: cfg.MachineResourceTypeCpu},
			SystemReservedCpuMillis: util.OptValue(0),
			Groups: map[string]cfg.K8sMachineCategoryGroup{
				"full": {
					GroupName:  "full",
					NameSuffix: cfg.MachineResourceTypeCpuV2,
					Fraction:   apm.Fraction{Numerator: 1, Denominator: 1},
					Configs: []cfg.K8sMachineConfiguration{{
						AdvertisedCpu: 2,
					}},
				},
				"frac": {
					GroupName:  "frac",
					NameSuffix: cfg.MachineResourceTypeCpuV2,
					Fraction:   apm.Fraction{Numerator: 1, Denominator: 4},
					Configs: []cfg.K8sMachineConfiguration{{
						AdvertisedCpu: 1,
					}},
				},
			},
		},
	}

	fullProduct := apm.ProductV2{
		Name:         "cpu-cat-2-vcpu",
		Category:     apm.ProductCategory{Name: "cpu-cat"},
		Cpu:          2,
		MemoryInGigs: 1,
		Fraction:     apm.Fraction{Numerator: 1, Denominator: 1},
	}
	fullDims := JobDimensionsFromProductOnly(&fullProduct)
	if fullDims.CpuMillis != 2000 {
		t.Fatalf("expected full CPU dims to be 2000, got %d", fullDims.CpuMillis)
	}

	fracProduct := apm.ProductV2{
		Name:         "cpu-cat-1-vcpu",
		Category:     apm.ProductCategory{Name: "cpu-cat"},
		Cpu:          1,
		MemoryInGigs: 1,
		Fraction:     apm.Fraction{Numerator: 1, Denominator: 4},
	}
	fracDims := JobDimensionsFromProductOnly(&fracProduct)
	if fracDims.CpuMillis != 250 {
		t.Fatalf("expected fractional CPU dims to be 250, got %d", fracDims.CpuMillis)
	}
}

func TestCpuReservationScenarioValues(t *testing.T) {
	ServiceConfig = &cfg.ServicesConfigurationKubernetes{}
	ServiceConfig.Compute.Machines = map[string]cfg.K8sMachineCategory{
		"cpu-standard": {
			Payment:                 cfg.PaymentInfo{Unit: cfg.MachineResourceTypeCpu},
			SystemReservedCpuMillis: util.OptValue(500),
			Groups: map[string]cfg.K8sMachineCategoryGroup{
				"full": {
					GroupName:  "full",
					NameSuffix: cfg.MachineResourceTypeCpuV2,
					Fraction:   apm.Fraction{Numerator: 1, Denominator: 1},
					Configs: []cfg.K8sMachineConfiguration{
						{AdvertisedCpu: 1, MemoryInGigabytes: 2},
						{AdvertisedCpu: 2, MemoryInGigabytes: 4},
						{AdvertisedCpu: 4, MemoryInGigabytes: 8},
						{AdvertisedCpu: 12, MemoryInGigabytes: 24},
					},
				},
				"fractional": {
					GroupName:  "fractional",
					NameSuffix: cfg.MachineResourceTypeCpuV2,
					Fraction:   apm.Fraction{Numerator: 1, Denominator: 4},
					Configs: []cfg.K8sMachineConfiguration{
						{AdvertisedCpu: 1, MemoryInGigabytes: 1},
						{AdvertisedCpu: 2, MemoryInGigabytes: 2},
						{AdvertisedCpu: 3, MemoryInGigabytes: 3},
					},
				},
			},
		},
	}

	full := apm.ProductV2{
		Name:         "cpu-standard-1-vcpu",
		Category:     apm.ProductCategory{Name: "cpu-standard"},
		Cpu:          1,
		MemoryInGigs: 2,
		Fraction:     apm.Fraction{Numerator: 1, Denominator: 1},
	}
	if got := NodeCpuMillisNormalizedWithReserved(&full); got != 958 {
		t.Fatalf("expected full product to reserve 958 millicpu, got %d", got)
	}

	fractional := apm.ProductV2{
		Name:         "cpu-standard-2-vcpu",
		Category:     apm.ProductCategory{Name: "cpu-standard"},
		Cpu:          2,
		MemoryInGigs: 2,
		Fraction:     apm.Fraction{Numerator: 1, Denominator: 4},
	}
	if got := NodeCpuMillisNormalizedWithReserved(&fractional); got != 479 {
		t.Fatalf("expected fractional product to reserve 479 millicpu, got %d", got)
	}
}

func TestJobDimensionsFromProductOnlyUsesConfiguredGpuResourceType(t *testing.T) {
	ServiceConfig = &cfg.ServicesConfigurationKubernetes{}
	ServiceConfig.Compute.Machines = map[string]cfg.K8sMachineCategory{
		"gpu-cat": {
			Payment: cfg.PaymentInfo{Unit: cfg.MachineResourceTypeGpu},
			Groups: map[string]cfg.K8sMachineCategoryGroup{
				"full": {
					GroupName:       "full",
					NameSuffix:      cfg.MachineResourceTypeGpuV2,
					Fraction:        apm.Fraction{Numerator: 1, Denominator: 1},
					GpuResourceType: "nvidia.com/gpu",
					Configs: []cfg.K8sMachineConfiguration{{
						AdvertisedCpu:     16,
						MemoryInGigabytes: 64,
						Gpu:               2,
					}},
				},
				"mig": {
					GroupName:       "mig",
					NameSuffix:      cfg.MachineResourceTypeGpuV2,
					Fraction:        apm.Fraction{Numerator: 1, Denominator: 7},
					GpuResourceType: "nvidia.com/mig-1g.10gb",
					Configs: []cfg.K8sMachineConfiguration{{
						AdvertisedCpu:     4,
						MemoryInGigabytes: 16,
						Gpu:               2,
					}},
				},
			},
		},
	}

	full := apm.ProductV2{
		Name:         "gpu-cat-2-gpu",
		Category:     apm.ProductCategory{Name: "gpu-cat"},
		Cpu:          16,
		MemoryInGigs: 64,
		Gpu:          2,
		Fraction:     apm.Fraction{Numerator: 1, Denominator: 1},
	}

	dims := JobDimensionsFromProductOnly(&full)
	if dims.Resources["nvidia.com/gpu"] != 2 {
		t.Fatalf("expected full GPU request to use nvidia.com/gpu, got %v", dims.Resources)
	}

	mig := apm.ProductV2{
		Name:         "gpu-cat-2-mig-1g",
		Category:     apm.ProductCategory{Name: "gpu-cat"},
		Cpu:          4,
		MemoryInGigs: 16,
		Gpu:          2,
		Fraction:     apm.Fraction{Numerator: 1, Denominator: 7},
	}

	dims = JobDimensionsFromProductOnly(&mig)
	if dims.Resources["nvidia.com/mig-1g.10gb"] != 2 {
		t.Fatalf("expected MIG request to use nvidia.com/mig-1g.10gb, got %v", dims.Resources)
	}
}
