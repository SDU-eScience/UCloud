package shared

import (
	"fmt"
	"math"
	"ucloud.dk/pkg/apm"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
)

var MachineSupport []orc.JobSupport

var (
	Machines        []apm.ProductV2
	StorageProducts []apm.ProductV2
	LinkProducts    []apm.ProductV2
	IpProducts      []apm.ProductV2
	LicenseProducts []apm.ProductV2
)

func initProducts() {
	vms := map[string]bool{}
	containers := map[string]bool{}

	for categoryName, category := range ServiceConfig.Compute.Machines {
		productCategory := apm.ProductCategory{
			Name:                categoryName,
			Provider:            cfg.Provider.Id,
			ProductType:         apm.ProductTypeCompute,
			AccountingFrequency: apm.AccountingFrequencyPeriodicMinute,
			FreeToUse:           false,
			AllowSubAllocations: true,
		}

		usePrice := false
		switch category.Payment.Type {
		case cfg.PaymentTypeMoney:
			productCategory.AccountingUnit.Name = category.Payment.Currency
			productCategory.AccountingUnit.NamePlural = category.Payment.Currency
			productCategory.AccountingUnit.DisplayFrequencySuffix = false
			productCategory.AccountingUnit.FloatingPoint = true
			usePrice = true

		case cfg.PaymentTypeResource:
			switch category.Payment.Unit {
			case cfg.MachineResourceTypeCpu:
				productCategory.AccountingUnit.Name = "Core"
			case cfg.MachineResourceTypeGpu:
				productCategory.AccountingUnit.Name = "GPU"
			case cfg.MachineResourceTypeMemory:
				productCategory.AccountingUnit.Name = "GB"
			}

			productCategory.AccountingUnit.NamePlural = productCategory.AccountingUnit.Name
			productCategory.AccountingUnit.FloatingPoint = false
			productCategory.AccountingUnit.DisplayFrequencySuffix = true
		}

		switch category.Payment.Interval {
		case cfg.PaymentIntervalMinutely:
			productCategory.AccountingFrequency = apm.AccountingFrequencyPeriodicMinute
		case cfg.PaymentIntervalHourly:
			productCategory.AccountingFrequency = apm.AccountingFrequencyPeriodicHour
		case cfg.PaymentIntervalDaily:
			productCategory.AccountingFrequency = apm.AccountingFrequencyPeriodicDay
		}

		for groupName, group := range category.Groups {
			// TODO This property should br moved?
			vms[categoryName] = group.AllowVirtualMachines
			containers[categoryName] = group.AllowsContainers

			for _, machineConfig := range group.Configs {
				name := fmt.Sprintf("%v-%v", groupName, pickResource(group.NameSuffix, machineConfig))
				product := apm.ProductV2{
					Type:        apm.ProductTypeCCompute,
					Category:    productCategory,
					Name:        name,
					Description: "A compute product", // TODO

					ProductType:               apm.ProductTypeCompute,
					Price:                     int64(math.Floor(machineConfig.Price * 1_000_000)),
					HiddenInGrantApplications: false,

					Cpu:          machineConfig.Cpu,
					CpuModel:     group.CpuModel,
					MemoryInGigs: machineConfig.MemoryInGigabytes,
					MemoryModel:  group.MemoryModel,
					Gpu:          machineConfig.Gpu,
					GpuModel:     group.GpuModel,
				}

				if !usePrice {
					product.Price = int64(pickResource(category.Payment.Unit, machineConfig))
				}

				Machines = append(Machines, product)
			}
		}
	}

	for _, machine := range Machines {
		support := orc.JobSupport{
			Product: apm.ProductReference{
				Id:       machine.Name,
				Category: machine.Category.Name,
				Provider: cfg.Provider.Id,
			},
		}

		allowContainer := containers[machine.Category.Name]
		allowVirtualMachine := containers[machine.Category.Name]

		if allowContainer {
			support.Docker.Enabled = true
			support.Docker.Web = true
			support.Docker.Vnc = true
			support.Docker.Logs = true
			support.Docker.Terminal = true
			support.Docker.Peers = false
			support.Docker.TimeExtension = false
		}

		if allowVirtualMachine {
			support.VirtualMachine.Enabled = true
			support.VirtualMachine.Web = true
			support.VirtualMachine.Vnc = true
			support.VirtualMachine.Logs = false
			support.VirtualMachine.Terminal = true
			support.VirtualMachine.Peers = false
			support.VirtualMachine.TimeExtension = false
			support.VirtualMachine.Suspension = true
		}

		MachineSupport = append(MachineSupport, support)
	}
}

func pickResource(resource cfg.MachineResourceType, machineConfig cfg.K8sMachineConfiguration) int {
	switch resource {
	case cfg.MachineResourceTypeCpu:
		return machineConfig.Cpu
	case cfg.MachineResourceTypeGpu:
		return machineConfig.Gpu
	case cfg.MachineResourceTypeMemory:
		return machineConfig.MemoryInGigabytes
	default:
		log.Warn("Unhandled machine resource type: %v", resource)
		return 0
	}
}
