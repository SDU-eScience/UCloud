package shared

import (
	"math"

	cfg "ucloud.dk/pkg/config"
	apm "ucloud.dk/shared/pkg/accounting"
)

var ServiceConfig *cfg.ServicesConfigurationKubernetes

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

func categoryFractionFactor(categoryName, groupName string) int {
	category, ok := ServiceConfig.Compute.Machines[categoryName]
	if !ok {
		return 1
	}
	group, ok := category.Groups[groupName]
	if !ok {
		return 1
	}

	fraction := group.Fraction.Normalize()
	return fraction.Numerator * (categoryFractionDenominator(categoryName) / fraction.Denominator)
}

func NormalizeForCategory(categoryName, groupName string, dims SchedulerDimensions, paymentUnit cfg.MachineResourceType) SchedulerDimensions {
	factor := categoryFractionFactor(categoryName, groupName)
	switch paymentUnit {
	case cfg.MachineResourceTypeCpu:
		dims.CpuMillis *= factor
	case cfg.MachineResourceTypeGpu:
		dims.Gpu *= factor
	}
	return dims
}

func NodeCpuMillisNormalizedWithReserved(product *apm.ProductV2) int {
	reservedPerCore := reservedCpuMillisPerCore(product)
	attachedVirtualCores := float64(product.Cpu)
	if product.Gpu == 0 {
		attachedVirtualCores *= fractionMultiplier(product.Fraction)
	}

	result := int(math.Floor(attachedVirtualCores * (1000 - reservedPerCore)))
	if result < 0 {
		return 0
	}
	return result
}

func NodeCpuMillisBaseWithReserved(product *apm.ProductV2) int {
	reservedPerCore := reservedCpuMillisPerCore(product)
	attachedVirtualCores := float64(product.Cpu)
	result := int(math.Floor(attachedVirtualCores * (1000 - reservedPerCore)))
	if result < 0 {
		return 0
	}
	return result
}

func reservedCpuMillisPerCore(product *apm.ProductV2) float64 {
	category, ok := ServiceConfig.Compute.Machines[product.Category.Name]
	if ok {
		if category.SystemReservedCpuMillisPerCore.Present {
			return float64(category.SystemReservedCpuMillisPerCore.Value)
		}

		maxVirtualCores := maxVirtualCoresForCategory(category)
		if maxVirtualCores > 0 {
			systemReservedCpuMillis := category.SystemReservedCpuMillis.GetOrDefault(500)
			return math.Ceil(float64(systemReservedCpuMillis) / maxVirtualCores)
		}
	}

	nodeCat, _ := NodeCategoryAndConfiguration(product)
	maxCpu := -1
	for _, config := range nodeCat.Configs {
		if config.AdvertisedCpu > maxCpu {
			maxCpu = config.AdvertisedCpu
		}
	}

	if maxCpu <= 0 {
		return 0
	}

	return math.Ceil(500.0 / float64(maxCpu))
}

func maxVirtualCoresForCategory(category cfg.K8sMachineCategory) float64 {
	maxVirtualCores := 0.0
	for _, group := range category.Groups {
		fraction := fractionMultiplier(group.Fraction)
		for _, config := range group.Configs {
			virtualCores := float64(config.AdvertisedCpu)
			if config.Gpu == 0 {
				virtualCores *= fraction
			}

			if virtualCores > maxVirtualCores {
				maxVirtualCores = virtualCores
			}
		}
	}
	return maxVirtualCores
}

func SystemReservedCpuMillisForCategory(categoryName string) int {
	category, ok := ServiceConfig.Compute.Machines[categoryName]
	if !ok {
		return 500
	}

	if category.SystemReservedCpuMillis.Present {
		return category.SystemReservedCpuMillis.Value
	}

	if category.SystemReservedCpuMillisPerCore.Present {
		maxVirtualCores := maxVirtualCoresForCategory(category)
		if maxVirtualCores > 0 {
			return int(math.Ceil(float64(category.SystemReservedCpuMillisPerCore.Value) * maxVirtualCores))
		}
	}

	return 500
}
