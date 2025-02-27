package config

import (
	"gopkg.in/yaml.v3"
	"math"
	"net"
	"ucloud.dk/pkg/util"
)

type ServicesConfigurationKubernetes struct {
	FileSystem KubernetesFileSystem
	Compute    KubernetesCompute
}

type KubernetesFileSystem struct {
	Name             string
	MountPoint       string
	TrashStagingArea string
	ClaimName        string
}

type KubernetesWebConfiguration struct {
	Enabled bool
	Prefix  string
	Suffix  string
}

type KubernetesIpConfiguration struct {
	Enabled bool
	Name    string
}

type KubernetesSshConfiguration struct {
	Enabled   bool
	IpAddress string
	Hostname  util.Option[string]
	PortMin   int
	PortMax   int
}

type KubernetesCompute struct {
	Machines                   map[string]K8sMachineCategory
	Namespace                  string
	Web                        KubernetesWebConfiguration
	PublicIps                  KubernetesIpConfiguration
	Ssh                        KubernetesSshConfiguration
	VirtualMachineStorageClass util.Option[string]
}

type K8sMachineCategory struct {
	Payment PaymentInfo
	Groups  map[string]K8sMachineCategoryGroup
}

type K8sMachineCategoryGroup struct {
	NameSuffix           MachineResourceType
	Configs              []K8sMachineConfiguration
	CpuModel             string
	GpuModel             string
	MemoryModel          string
	AllowVirtualMachines bool
	AllowsContainers     bool
}

type K8sMachineConfiguration struct {
	Cpu               int
	MemoryInGigabytes int
	Gpu               int
	Price             float64
}

func parseKubernetesServices(unmanaged bool, mode ServerMode, filePath string, services *yaml.Node) (bool, ServicesConfigurationKubernetes) {
	cfg := ServicesConfigurationKubernetes{}
	success := true

	fsNode := requireChild(filePath, services, "fileSystem", &success)
	{
		cfg.FileSystem.Name = requireChildText(filePath, fsNode, "name", &success)
		cfg.FileSystem.MountPoint = requireChildFolder(filePath, fsNode, "mountPoint", FileCheckReadWrite, &success)
		cfg.FileSystem.TrashStagingArea = requireChildFolder(filePath, fsNode, "trashStagingArea", FileCheckReadWrite, &success)
		cfg.FileSystem.ClaimName = requireChildText(filePath, fsNode, "claimName", &success)
	}

	computeNode := requireChild(filePath, services, "compute", &success)
	cfg.Compute.Namespace = optionalChildText(filePath, services, "namespace", &success)
	if cfg.Compute.Namespace == "" {
		cfg.Compute.Namespace = "ucloud-apps"
	}

	cfg.Compute.Machines = make(map[string]K8sMachineCategory)
	machinesNode := requireChild(filePath, computeNode, "machines", &success)
	if machinesNode.Kind != yaml.MappingNode {
		reportError(filePath, computeNode, "expected machines to be a dictionary")
		return false, cfg
	}

	for i := 0; i < len(machinesNode.Content); i += 2 {
		machineCategoryName := ""
		_ = machinesNode.Content[i].Decode(&machineCategoryName)
		machineNode := machinesNode.Content[i+1]

		category := K8sMachineCategory{}
		category.Groups = make(map[string]K8sMachineCategoryGroup)
		category.Payment = parsePaymentInfo(
			filePath,
			requireChild(filePath, machineNode, "payment", &success),
			[]string{"Cpu", "Memory", "Gpu"},
			false,
			&success,
		)

		if !hasChild(machineNode, "groups") {
			group := parseK8sMachineGroup(filePath, machineNode, &success)
			category.Groups[machineCategoryName] = group
		} else {
			groupsNode := requireChild(filePath, machineNode, "groups", &success)
			if groupsNode.Kind != yaml.MappingNode {
				reportError(filePath, groupsNode, "expected groups to be a dictionary")
				return false, cfg
			}

			for j := 0; j < len(groupsNode.Content); j += 2 {
				groupName := ""
				_ = groupsNode.Content[j].Decode(&groupName)
				groupNode := groupsNode.Content[j+1]
				category.Groups[groupName] = parseK8sMachineGroup(filePath, groupNode, &success)
			}
		}

		if category.Payment.Type == PaymentTypeMoney {
			for key := range category.Groups {
				group, _ := category.Groups[key]
				for _, machineConfig := range group.Configs {
					if machineConfig.Price == 0 {
						reportError(filePath, machineNode, "price must be specified for all machine groups when payment type is Money!")
						return false, cfg
					}
				}
			}
		} else {
			for key := range category.Groups {
				group, _ := category.Groups[key]
				for _, machineConfig := range group.Configs {
					if machineConfig.Price != 0 {
						reportError(filePath, machineNode, "price must not be specified for all machine groups when payment type is Resource!")
						return false, cfg
					}
				}
			}
		}

		if !success {
			return false, cfg
		}

		cfg.Compute.Machines[machineCategoryName] = category
	}

	webNode, _ := getChildOrNil(filePath, computeNode, "web")
	if webNode != nil {
		enabled, ok := optionalChildBool(filePath, webNode, "enabled")
		cfg.Compute.Web.Enabled = enabled && ok

		if cfg.Compute.Web.Enabled {
			cfg.Compute.Web.Prefix = requireChildText(filePath, webNode, "prefix", &success)
			cfg.Compute.Web.Suffix = requireChildText(filePath, webNode, "suffix", &success)
		}
	}

	ipNode, _ := getChildOrNil(filePath, computeNode, "publicIps")
	if ipNode != nil {
		enabled, ok := optionalChildBool(filePath, ipNode, "enabled")
		cfg.Compute.PublicIps.Enabled = enabled && ok

		if cfg.Compute.PublicIps.Enabled {
			name := optionalChildText(filePath, ipNode, "name", &success)
			if name != "" {
				cfg.Compute.PublicIps.Name = name
			} else {
				cfg.Compute.PublicIps.Name = "public-ip"
			}
		}
	}

	sshNode, _ := getChildOrNil(filePath, computeNode, "ssh")
	if sshNode != nil {
		enabled, ok := optionalChildBool(filePath, sshNode, "enabled")
		cfg.Compute.Ssh.Enabled = enabled && ok

		if cfg.Compute.Ssh.Enabled {
			ipAddr := requireChildText(filePath, sshNode, "ipAddress", &success)
			if success {
				ip := net.ParseIP(ipAddr)
				if ip == nil {
					reportError(filePath, sshNode, "Invalid IP address specified")
					success = false
				} else {
					cfg.Compute.Ssh.IpAddress = ipAddr
				}
			}

			portMin := requireChildInt(filePath, sshNode, "portMin", &success)
			if success && (portMin <= 0 || portMin >= math.MaxInt16) {
				reportError(filePath, sshNode, "portMin is invalid")
				success = false
			}

			portMax := requireChildInt(filePath, sshNode, "portMax", &success)
			if success && (portMax <= 0 || portMax >= math.MaxInt16) {
				reportError(filePath, sshNode, "portMax is invalid")
				success = false
			}

			if success && portMin < portMin {
				reportError(filePath, sshNode, "portMax is less than portMin")
				success = false
			}

			cfg.Compute.Ssh.PortMin = int(portMin)
			cfg.Compute.Ssh.PortMax = int(portMax)

			hostname := optionalChildText(filePath, sshNode, "hostname", &success)
			if hostname != "" {
				cfg.Compute.Ssh.Hostname.Set(hostname)
			}
		}
	}

	vmStorageClass := optionalChildText(filePath, computeNode, "virtualMachineStorageClass", &success)
	if vmStorageClass != "" {
		cfg.Compute.VirtualMachineStorageClass.Set(vmStorageClass)
	}

	return success, cfg
}

func parseK8sMachineGroup(filePath string, node *yaml.Node, success *bool) K8sMachineCategoryGroup {
	result := K8sMachineCategoryGroup{}
	result.CpuModel = optionalChildText(filePath, node, "cpuModel", success)
	result.GpuModel = optionalChildText(filePath, node, "gpuModel", success)
	result.MemoryModel = optionalChildText(filePath, node, "memoryModel", success)

	allowVms, ok := optionalChildBool(filePath, node, "allowVirtualMachines")
	result.AllowVirtualMachines = allowVms && ok

	allowContainers, ok := optionalChildBool(filePath, node, "allowContainers")
	result.AllowsContainers = allowContainers || !ok

	var cpu []int
	var gpu []int
	var memory []int
	var price []float64

	{
		decode(filePath, requireChild(filePath, node, "cpu", success), &cpu, success)

		memoryNode := requireChild(filePath, node, "memory", success)
		decode(filePath, memoryNode, &memory, success)

		gpuNode, _ := getChildOrNil(filePath, node, "gpu")
		if gpuNode != nil {
			decode(filePath, gpuNode, &gpu, success)
		}

		priceNode, _ := getChildOrNil(filePath, node, "price")
		if priceNode != nil {
			decode(filePath, priceNode, &price, success)
		}

		machineLength := len(cpu)

		if machineLength == 0 {
			reportError(filePath, node, "You must specify at least one machine via cpu, memory (+ gpu/price)")
			*success = false
		}

		if gpu != nil && len(gpu) != machineLength {
			reportError(filePath, gpuNode, "gpu must have the same length as cpu (%v != %v)", machineLength, len(gpu))
			*success = false
		}

		if price != nil && len(price) != machineLength {
			reportError(filePath, gpuNode, "price must have the same length as cpu (%v != %v)", machineLength, len(price))
			*success = false
		}

		if len(memory) != machineLength {
			reportError(filePath, memoryNode, "memory must have the same length as cpu (%v != %v)", machineLength, len(memory))
			*success = false
		}
	}

	for _, count := range cpu {
		if count <= 0 {
			reportError(filePath, node, "cpu count must be greater than zero")
			*success = false
			break
		}
	}

	for _, count := range memory {
		if count <= 0 {
			reportError(filePath, node, "cpu count must be greater than zero")
			*success = false
			break
		}
	}

	for _, count := range price {
		if count <= 0 {
			reportError(filePath, node, "price must be greater than zero")
			*success = false
			break
		}
	}

	for _, count := range gpu {
		if count < 0 {
			reportError(filePath, node, "gpu count must be positive")
			*success = false
			break
		}
	}

	if hasChild(node, "nameSuffix") {
		result.NameSuffix = requireChildEnum(filePath, node, "nameSuffix", MachineResourceTypeOptions, success)
	} else {
		if gpu != nil {
			result.NameSuffix = MachineResourceTypeGpu
		} else {
			result.NameSuffix = MachineResourceTypeCpu
		}
	}

	if *success {
		for i := 0; i < len(cpu); i++ {
			gpuCount := 0
			if gpu != nil {
				gpuCount = gpu[i]
			}
			configuration := K8sMachineConfiguration{
				Cpu:               cpu[i],
				MemoryInGigabytes: memory[i],
				Gpu:               gpuCount,
			}
			if price != nil {
				configuration.Price = price[i]
			}
			result.Configs = append(result.Configs, configuration)
		}
	}

	return result
}
