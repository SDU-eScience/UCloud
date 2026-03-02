package shared

import (
	"math"

	cfg "ucloud.dk/pkg/config"
	apm "ucloud.dk/shared/pkg/accounting"
)

var ServiceConfig *cfg.ServicesConfigurationKubernetes

func NodeCategoryAndConfiguration(product *apm.ProductV2) (cfg.K8sMachineCategoryGroup, cfg.K8sMachineConfiguration) {
	machineCategory, ok := ServiceConfig.Compute.Machines[product.Category.Name]
	if ok {
		nodeCat, ok := machineCategory.Groups[product.Category.Name]
		if ok {
			for _, config := range nodeCat.Configs {
				if config.AdvertisedCpu == product.Cpu {
					return nodeCat, config
				}
			}
		}
	}
	defaultConfig := cfg.K8sMachineConfiguration{
		AdvertisedCpu:     product.Cpu,
		MemoryInGigabytes: product.MemoryInGigs,
		Gpu:               product.Gpu,
		Price:             float64(product.Cpu),
	}

	group := cfg.K8sMachineCategoryGroup{
		GroupName:        product.Category.Name,
		NameSuffix:       "Cpu",
		Configs:          []cfg.K8sMachineConfiguration{defaultConfig},
		AllowsContainers: true,
	}

	return group, defaultConfig
}

func NodeCpuMillisReserved(product *apm.ProductV2) int {
	nodeCat, _ := NodeCategoryAndConfiguration(product)

	maxCpu := -1
	for _, config := range nodeCat.Configs {
		if config.AdvertisedCpu > maxCpu {
			maxCpu = config.AdvertisedCpu
		}
	}

	reservedPerCore := math.Ceil(float64(nodeCat.SystemReservedCpuMillis) / float64(maxCpu))
	return product.Cpu*1000 - int(float64(product.Cpu)*reservedPerCore)
}
