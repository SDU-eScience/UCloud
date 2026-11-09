package shared

import (
	"maps"
	"slices"

	cfg "ucloud.dk/pkg/config"
	apm "ucloud.dk/shared/pkg/accounting"
)

var ServiceConfig *cfg.ServicesConfigurationKubernetes

const DefaultGpuResourceType = "nvidia.com/gpu"

func GpuResourceTypeOrDefault(resourceType string) string {
	if resourceType == "" {
		return DefaultGpuResourceType
	}
	return resourceType
}

func GpuResourceTypesForCategory(categoryName string) []string {
	result := map[string]bool{DefaultGpuResourceType: true}
	category, ok := ServiceConfig.Compute.Machines[categoryName]
	if ok {
		for _, group := range category.Groups {
			result[GpuResourceTypeOrDefault(group.GpuResourceType)] = true
		}
	}

	return slices.Sorted(maps.Keys(result))
}

func NodeCategoryAndConfiguration(product *apm.ProductV2) (cfg.K8sMachineCategoryGroup, cfg.K8sMachineConfiguration) {
	category, ok := ServiceConfig.Compute.Machines[product.Category.Name]
	if ok {
		productFraction := product.Fraction.Normalize()
		for _, group := range category.Groups {
			groupFraction := group.Fraction.Normalize()
			if groupFraction != productFraction {
				continue
			}

			for _, config := range group.Configs {
				if config.AdvertisedCpu == product.Cpu &&
					config.MemoryInGigabytes == product.MemoryInGigs &&
					config.Gpu == product.Gpu {
					return group, config
				}
			}
		}
	}

	_, group, config, ok := ServiceConfig.Compute.ResolveMachine(product.Name, product.Category.Name)
	if ok {
		return group, config
	}
	defaultConfig := cfg.K8sMachineConfiguration{
		AdvertisedCpu:     product.Cpu,
		MemoryInGigabytes: product.MemoryInGigs,
		Gpu:               product.Gpu,
		Price:             float64(product.Cpu),
	}

	group = cfg.K8sMachineCategoryGroup{
		GroupName:        product.Category.Name,
		NameSuffix:       "Cpu",
		Configs:          []cfg.K8sMachineConfiguration{defaultConfig},
		AllowsContainers: true,
	}

	return group, defaultConfig
}

func fractionMultiplier(f apm.Fraction) float64 {
	value := f.Normalize()
	return float64(value.Numerator) / float64(value.Denominator)
}

func gcd(a, b int) int {
	if a < 0 {
		a = -a
	}
	if b < 0 {
		b = -b
	}
	for b != 0 {
		a, b = b, a%b
	}
	if a == 0 {
		return 1
	}
	return a
}

func lcm(a, b int) int {
	if a == 0 || b == 0 {
		return 0
	}
	return a / gcd(a, b) * b
}

func categoryFractionDenominator(categoryName string) int {
	category, ok := ServiceConfig.Compute.Machines[categoryName]
	if !ok {
		return 1
	}

	denominator := 1
	for _, group := range category.Groups {
		fraction := group.Fraction.Normalize()
		denominator = lcm(denominator, fraction.Denominator)
	}
	if denominator <= 0 {
		return 1
	}
	return denominator
}

func NormalizationDenominatorForCategory(categoryName string) int {
	return categoryFractionDenominator(categoryName)
}

func NodeCpuMillisNormalizedWithoutReserved(product *apm.ProductV2) int64 {
	attachedVirtualCores := float64(product.Cpu)
	if product.Gpu == 0 {
		attachedVirtualCores *= fractionMultiplier(product.Fraction)
	}

	return int64(attachedVirtualCores * 1000)
}

func NodeCpuMillisWithBudget(cpuMillis, capacityMillis, budgetMillis int) int {
	if cpuMillis <= 0 || capacityMillis <= 0 || budgetMillis <= 0 {
		return 0
	}
	return int(int64(cpuMillis) * int64(min(capacityMillis, budgetMillis)) / int64(capacityMillis))
}

func SystemReservedCpuMillisForCategory(categoryName string) int {
	category, ok := ServiceConfig.Compute.Machines[categoryName]
	if !ok {
		return 500
	}

	return category.SystemReservedCpuMillis.GetOrDefault(500)
}
