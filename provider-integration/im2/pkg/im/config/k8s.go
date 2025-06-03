package config

import (
	"fmt"
	"gopkg.in/yaml.v3"
	"math"
	"net"
	"ucloud.dk/shared/pkg/cfgutil"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

type ServicesConfigurationKubernetes struct {
	FileSystem        KubernetesFileSystem
	Compute           KubernetesCompute
	SensitiveProjects []string
}

type KubernetesFileSystem struct {
	Name             string
	MountPoint       string
	TrashStagingArea string
	ClaimName        string
	ScanMethod       KubernetesFileSystemScanMethod
}

type KubernetesFileSystemScanMethod struct {
	Type              K8sScanMethodType
	ExtendedAttribute string
}

type K8sScanMethodType string

const (
	K8sScanMethodTypeWalk              K8sScanMethodType = "Walk"
	K8sScanMethodTypeExtendedAttribute K8sScanMethodType = "Xattr"
	K8sScanMethodTypeDevFile           K8sScanMethodType = "Development"
)

var K8sScanMethodTypeValues = []K8sScanMethodType{
	K8sScanMethodTypeWalk,
	K8sScanMethodTypeExtendedAttribute,
	K8sScanMethodTypeDevFile,
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

type KubernetesPublicLinkConfiguration struct {
	Enabled bool
	Name    string
	Prefix  string
	Suffix  string
}

type KubernetesIntegratedTerminal struct {
	Enabled bool
}

type KubernetesSshConfiguration struct {
	Enabled   bool
	IpAddress string
	Hostname  util.Option[string]
	PortMin   int
	PortMax   int
}

type KubernetesSyncthingConfiguration struct {
	Enabled               bool
	IpAddress             string
	PortMin               int
	PortMax               int
	DevelopmentSourceCode string
	RelaysEnabled         bool
}

type KubernetesCompute struct {
	Machines                        map[string]K8sMachineCategory
	EstimatedContainerDownloadSpeed float64 // MB/s
	Namespace                       string
	Web                             KubernetesWebConfiguration
	PublicIps                       KubernetesIpConfiguration
	PublicLinks                     KubernetesPublicLinkConfiguration
	Ssh                             KubernetesSshConfiguration
	Syncthing                       KubernetesSyncthingConfiguration
	IntegratedTerminal              KubernetesIntegratedTerminal
	VirtualMachineStorageClass      util.Option[string]
	ImSourceCode                    util.Option[string]
	Modules                         map[string]KubernetesModuleEntry
}

func (c *KubernetesCompute) ResolveMachine(name, category string) (K8sMachineCategory, K8sMachineCategoryGroup, K8sMachineConfiguration, bool) {
	cat, ok := c.Machines[category]
	if !ok {
		return K8sMachineCategory{}, K8sMachineCategoryGroup{}, K8sMachineConfiguration{}, false
	}

	for groupName, group := range cat.Groups {
		for _, machineConfig := range group.Configs {
			mgName := fmt.Sprintf("%v-%v", groupName, pickResource(group.NameSuffix, machineConfig))
			if mgName == name {
				return cat, group, machineConfig, true
			}
		}
	}

	return K8sMachineCategory{}, K8sMachineCategoryGroup{}, K8sMachineConfiguration{}, false
}

func pickResource(resource MachineResourceType, machineConfig K8sMachineConfiguration) int {
	switch resource {
	case MachineResourceTypeCpu:
		return machineConfig.AdvertisedCpu
	case MachineResourceTypeGpu:
		return machineConfig.Gpu
	case MachineResourceTypeMemory:
		return machineConfig.MemoryInGigabytes
	default:
		log.Warn("Unhandled machine resource type: %v", resource)
		return 0
	}
}

type KubernetesModuleEntry struct {
	Name       string              `json:"name"`
	VolSubPath string              `json:"claimSubPath"`
	HostPath   util.Option[string] `json:"hostPath"`
	ClaimName  util.Option[string] `json:"volumeClaim"`
}

type K8sMachineCategory struct {
	Payment PaymentInfo
	Groups  map[string]K8sMachineCategoryGroup
}

type K8sMachineCategoryGroup struct {
	GroupName               string
	NameSuffix              MachineResourceType
	Configs                 []K8sMachineConfiguration
	CpuModel                string
	GpuModel                string
	MemoryModel             string
	AllowVirtualMachines    bool
	AllowsContainers        bool
	GpuResourceType         string
	CustomRuntime           string
	SystemReservedCpuMillis int
}

type K8sMachineConfiguration struct {
	AdvertisedCpu     int
	ActualCpuMillis   int
	MemoryInGigabytes int
	Gpu               int
	Price             float64
}

func parseKubernetesServices(unmanaged bool, mode ServerMode, filePath string, services *yaml.Node) (bool, ServicesConfigurationKubernetes) {
	cfg := ServicesConfigurationKubernetes{}
	success := true

	sensitiveProjects, _ := cfgutil.GetChildOrNil(filePath, services, "sensitiveProjects")
	if sensitiveProjects != nil {
		cfgutil.Decode(filePath, sensitiveProjects, &cfg.SensitiveProjects, &success)
	}

	fsNode := cfgutil.RequireChild(filePath, services, "fileSystem", &success)
	{
		cfg.FileSystem.Name = cfgutil.RequireChildText(filePath, fsNode, "name", &success)
		cfg.FileSystem.MountPoint = cfgutil.RequireChildFolder(filePath, fsNode, "mountPoint", cfgutil.FileCheckReadWrite, &success)
		cfg.FileSystem.TrashStagingArea = cfgutil.RequireChildFolder(filePath, fsNode, "trashStagingArea", cfgutil.FileCheckReadWrite, &success)
		cfg.FileSystem.ClaimName = cfgutil.RequireChildText(filePath, fsNode, "claimName", &success)

		scanMethodNode, _ := cfgutil.GetChildOrNil(filePath, fsNode, "scanMethod")
		if scanMethodNode != nil {
			cfg.FileSystem.ScanMethod.Type = cfgutil.RequireChildEnum(filePath, scanMethodNode, "type",
				K8sScanMethodTypeValues, &success)

			switch cfg.FileSystem.ScanMethod.Type {
			case K8sScanMethodTypeWalk:
				// Do nothing

			case K8sScanMethodTypeExtendedAttribute:
				cfg.FileSystem.ScanMethod.ExtendedAttribute = cfgutil.RequireChildText(filePath, scanMethodNode,
					"xattr", &success)

			case K8sScanMethodTypeDevFile:
				// Do nothing
			}
		} else {
			cfg.FileSystem.ScanMethod.Type = K8sScanMethodTypeWalk
		}
	}

	computeNode := cfgutil.RequireChild(filePath, services, "compute", &success)
	cfg.Compute.Namespace = cfgutil.OptionalChildText(filePath, services, "namespace", &success)
	cfg.Compute.ImSourceCode = util.OptStringIfNotEmpty(cfgutil.OptionalChildText(filePath, computeNode, "imSourceCode", &success))

	// NOTE(Dan): Default value was based on several tests on the current production environment. Results were very
	// stable around 14.5MB/s. This result seems very low, but consistent. Thankfully, it is fairly rare that people
	// run containers that are not already present on the machine.
	cfg.Compute.EstimatedContainerDownloadSpeed = cfgutil.OptionalChildFloat(
		filePath,
		computeNode,
		"estimatedContainerDownloadSpeed",
		&success,
	).GetOrDefault(14.5)

	if cfg.Compute.Namespace == "" {
		cfg.Compute.Namespace = "ucloud-apps"
	}

	cfg.Compute.Modules = map[string]KubernetesModuleEntry{}
	modulesNode, _ := cfgutil.GetChildOrNil(filePath, computeNode, "modules")
	if modulesNode != nil && modulesNode.Kind != yaml.MappingNode {
		cfgutil.ReportError(filePath, computeNode, "expected 'modules' to be a dictionary")
		return false, cfg
	}

	if modulesNode != nil {
		for i := 0; i < len(modulesNode.Content); i += 2 {
			entry := KubernetesModuleEntry{}
			_ = modulesNode.Content[i].Decode(&entry.Name)

			entryNode := modulesNode.Content[i+1]
			entry.VolSubPath = cfgutil.RequireChildText(filePath, entryNode, "subPath", &success)
			entry.HostPath = util.OptStringIfNotEmpty(cfgutil.OptionalChildText(filePath, entryNode, "hostPath", &success))
			entry.ClaimName = util.OptStringIfNotEmpty(cfgutil.OptionalChildText(filePath, entryNode, "claimName", &success))

			claimSourceCount := 0
			if entry.HostPath.Present {
				claimSourceCount++
			}
			if entry.ClaimName.Present {
				claimSourceCount++
			}

			if claimSourceCount != 1 {
				cfgutil.ReportError(filePath, entryNode, "exactly one of 'hostPath' and 'volumeClaim' must be set!")
				return false, cfg
			}

			_, exists := cfg.Compute.Modules[entry.Name]
			if exists {
				cfgutil.ReportError(filePath, entryNode, "another module with this name already exists")
				return false, cfg
			}

			cfg.Compute.Modules[entry.Name] = entry
		}
	}

	cfg.Compute.Machines = make(map[string]K8sMachineCategory)
	machinesNode := cfgutil.RequireChild(filePath, computeNode, "machines", &success)
	if machinesNode.Kind != yaml.MappingNode {
		cfgutil.ReportError(filePath, computeNode, "expected machines to be a dictionary")
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
			cfgutil.RequireChild(filePath, machineNode, "payment", &success),
			[]string{"Cpu", "Memory", "Gpu"},
			false,
			&success,
		)

		if !cfgutil.HasChild(machineNode, "groups") {
			group := parseK8sMachineGroup(filePath, machineNode, &success)
			group.GroupName = machineCategoryName
			category.Groups[machineCategoryName] = group
		} else {
			groupsNode := cfgutil.RequireChild(filePath, machineNode, "groups", &success)
			if groupsNode.Kind != yaml.MappingNode {
				cfgutil.ReportError(filePath, groupsNode, "expected groups to be a dictionary")
				return false, cfg
			}

			for j := 0; j < len(groupsNode.Content); j += 2 {
				groupName := ""
				_ = groupsNode.Content[j].Decode(&groupName)
				groupNode := groupsNode.Content[j+1]
				group := parseK8sMachineGroup(filePath, groupNode, &success)
				group.GroupName = groupName
				category.Groups[groupName] = group
			}
		}

		if category.Payment.Type == PaymentTypeMoney {
			for key := range category.Groups {
				group, _ := category.Groups[key]
				for _, machineConfig := range group.Configs {
					if machineConfig.Price == 0 {
						cfgutil.ReportError(filePath, machineNode, "price must be specified for all machine groups when payment type is Money!")
						return false, cfg
					}
				}
			}
		} else {
			for key := range category.Groups {
				group, _ := category.Groups[key]
				for _, machineConfig := range group.Configs {
					if machineConfig.Price != 0 {
						cfgutil.ReportError(filePath, machineNode, "price must not be specified for all machine groups when payment type is Resource!")
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

	webNode, _ := cfgutil.GetChildOrNil(filePath, computeNode, "web")
	if webNode != nil {
		enabled, ok := cfgutil.OptionalChildBool(filePath, webNode, "enabled")
		cfg.Compute.Web.Enabled = enabled && ok

		if cfg.Compute.Web.Enabled {
			cfg.Compute.Web.Prefix = cfgutil.RequireChildText(filePath, webNode, "prefix", &success)
			cfg.Compute.Web.Suffix = cfgutil.RequireChildText(filePath, webNode, "suffix", &success)
		}
	}

	ipNode, _ := cfgutil.GetChildOrNil(filePath, computeNode, "publicIps")
	if ipNode != nil {
		enabled, ok := cfgutil.OptionalChildBool(filePath, ipNode, "enabled")
		cfg.Compute.PublicIps.Enabled = enabled && ok

		if cfg.Compute.PublicIps.Enabled {
			name := cfgutil.OptionalChildText(filePath, ipNode, "name", &success)
			if name != "" {
				cfg.Compute.PublicIps.Name = name
			} else {
				cfg.Compute.PublicIps.Name = "public-ip"
			}
		}
	}

	ingressNode, _ := cfgutil.GetChildOrNil(filePath, computeNode, "publicLinks")
	if ingressNode != nil {
		enabled, ok := cfgutil.OptionalChildBool(filePath, ingressNode, "enabled")
		cfg.Compute.PublicLinks.Enabled = enabled && ok

		if enabled {
			success = true
			prefix := cfgutil.RequireChildText(filePath, ingressNode, "prefix", &success)
			cfg.Compute.PublicLinks.Prefix = prefix

			success = true
			suffix := cfgutil.RequireChildText(filePath, ingressNode, "suffix", &success)
			cfg.Compute.PublicLinks.Suffix = suffix

			cfg.Compute.PublicLinks.Name = cfgutil.OptionalChildText(filePath, ingressNode, "name", &success)
			if cfg.Compute.PublicLinks.Name == "" {
				cfg.Compute.PublicLinks.Name = "public-links"
			}
		}
	}

	sshNode, _ := cfgutil.GetChildOrNil(filePath, computeNode, "ssh")
	if sshNode != nil {
		enabled, ok := cfgutil.OptionalChildBool(filePath, sshNode, "enabled")
		cfg.Compute.Ssh.Enabled = enabled && ok

		if cfg.Compute.Ssh.Enabled {
			ipAddr := cfgutil.RequireChildText(filePath, sshNode, "ipAddress", &success)
			if success {
				ip := net.ParseIP(ipAddr)
				if ip == nil {
					cfgutil.ReportError(filePath, sshNode, "Invalid IP address specified")
					success = false
				} else {
					cfg.Compute.Ssh.IpAddress = ipAddr
				}
			}

			portMin := cfgutil.RequireChildInt(filePath, sshNode, "portMin", &success)
			if success && (portMin <= 0 || portMin >= math.MaxUint16) {
				cfgutil.ReportError(filePath, sshNode, "portMin is invalid")
				success = false
			}

			portMax := cfgutil.RequireChildInt(filePath, sshNode, "portMax", &success)
			if success && (portMax <= 0 || portMax >= math.MaxUint16) {
				cfgutil.ReportError(filePath, sshNode, "portMax is invalid")
				success = false
			}

			if success && portMin < portMin {
				cfgutil.ReportError(filePath, sshNode, "portMax is less than portMin")
				success = false
			}

			cfg.Compute.Ssh.PortMin = int(portMin)
			cfg.Compute.Ssh.PortMax = int(portMax)

			hostname := cfgutil.OptionalChildText(filePath, sshNode, "hostname", &success)
			if hostname != "" {
				cfg.Compute.Ssh.Hostname.Set(hostname)
			}
		}
	}

	syncthingNode, _ := cfgutil.GetChildOrNil(filePath, computeNode, "syncthing")
	if syncthingNode != nil {
		enabled, ok := cfgutil.OptionalChildBool(filePath, syncthingNode, "enabled")
		cfg.Compute.Syncthing.Enabled = enabled && ok

		if cfg.Compute.Syncthing.Enabled {
			ipAddr := cfgutil.RequireChildText(filePath, syncthingNode, "ipAddress", &success)
			if success {
				ip := net.ParseIP(ipAddr)
				if ip == nil {
					cfgutil.ReportError(filePath, syncthingNode, "Invalid IP address specified")
					success = false
				} else {
					cfg.Compute.Syncthing.IpAddress = ipAddr
				}
			}

			portMin := cfgutil.RequireChildInt(filePath, syncthingNode, "portMin", &success)
			if success && (portMin <= 0 || portMin >= math.MaxInt16) {
				cfgutil.ReportError(filePath, syncthingNode, "portMin is invalid")
				success = false
			}

			portMax := cfgutil.RequireChildInt(filePath, syncthingNode, "portMax", &success)
			if success && (portMax <= 0 || portMax >= math.MaxInt16) {
				cfgutil.ReportError(filePath, syncthingNode, "portMax is invalid")
				success = false
			}

			if success && portMin < portMin {
				cfgutil.ReportError(filePath, syncthingNode, "portMax is less than portMin")
				success = false
			}

			cfg.Compute.Syncthing.PortMin = int(portMin)
			cfg.Compute.Syncthing.PortMax = int(portMax)

			cfg.Compute.Syncthing.DevelopmentSourceCode = cfgutil.OptionalChildText(filePath, syncthingNode, "developmentSourceCode", &success)

			relaysEnabled, ok := cfgutil.OptionalChildBool(filePath, syncthingNode, "relaysEnabled")
			cfg.Compute.Syncthing.RelaysEnabled = relaysEnabled && ok
			cfg.Compute.Syncthing.DevelopmentSourceCode = cfgutil.OptionalChildText(filePath, syncthingNode, "developmentSourceCode", &success)
		}
	}

	integratedTerminalNode, _ := cfgutil.GetChildOrNil(filePath, computeNode, "integratedTerminal")
	if integratedTerminalNode != nil {
		enabled, ok := cfgutil.OptionalChildBool(filePath, integratedTerminalNode, "enabled")
		cfg.Compute.IntegratedTerminal.Enabled = enabled && ok
	}

	vmStorageClass := cfgutil.OptionalChildText(filePath, computeNode, "virtualMachineStorageClass", &success)
	if vmStorageClass != "" {
		cfg.Compute.VirtualMachineStorageClass.Set(vmStorageClass)
	}

	return success, cfg
}

func parseK8sMachineGroup(filePath string, node *yaml.Node, success *bool) K8sMachineCategoryGroup {
	result := K8sMachineCategoryGroup{}
	result.CpuModel = cfgutil.OptionalChildText(filePath, node, "cpuModel", success)
	result.GpuModel = cfgutil.OptionalChildText(filePath, node, "gpuModel", success)
	result.MemoryModel = cfgutil.OptionalChildText(filePath, node, "memoryModel", success)

	allowVms, ok := cfgutil.OptionalChildBool(filePath, node, "allowVirtualMachines")
	result.AllowVirtualMachines = allowVms && ok

	allowContainers, ok := cfgutil.OptionalChildBool(filePath, node, "allowContainers")
	result.AllowsContainers = allowContainers || !ok

	// NOTE(Dan): The GPU resource type is set even if the machine doesn't have GPUs. This shouldn't matter in
	// practice since the machine will simply not report any usable GPUs and won't ever try to request any.
	result.GpuResourceType = cfgutil.OptionalChildText(filePath, node, "gpuType", success)
	if result.GpuResourceType == "" {
		result.GpuResourceType = "nvidia.com/gpu"
	}

	result.CustomRuntime = cfgutil.OptionalChildText(filePath, node, "customRuntime", success)
	result.SystemReservedCpuMillis = int(cfgutil.OptionalChildInt(filePath, node, "systemReservedCpuMillis", success).GetOrDefault(500))

	var cpu []int
	var actualCpuMillis []int
	var gpu []int
	var memory []int
	var price []float64

	{
		cfgutil.Decode(filePath, cfgutil.RequireChild(filePath, node, "cpu", success), &cpu, success)

		memoryNode := cfgutil.RequireChild(filePath, node, "memory", success)
		cfgutil.Decode(filePath, memoryNode, &memory, success)

		gpuNode, _ := cfgutil.GetChildOrNil(filePath, node, "gpu")
		if gpuNode != nil {
			cfgutil.Decode(filePath, gpuNode, &gpu, success)
		}

		priceNode, _ := cfgutil.GetChildOrNil(filePath, node, "price")
		if priceNode != nil {
			cfgutil.Decode(filePath, priceNode, &price, success)
		}

		actualCpuNode, _ := cfgutil.GetChildOrNil(filePath, node, "actualCpuMillis")
		if actualCpuNode != nil {
			cfgutil.Decode(filePath, actualCpuNode, &actualCpuMillis, success)
		} else {
			for i := 0; i < len(cpu); i++ {
				actualCpuMillis = append(actualCpuMillis, cpu[i]*1000)
			}
		}

		machineLength := len(cpu)

		if machineLength == 0 {
			cfgutil.ReportError(filePath, node, "You must specify at least one machine via cpu, memory (+ gpu/price)")
			*success = false
		}

		if gpu != nil && len(gpu) != machineLength {
			cfgutil.ReportError(filePath, gpuNode, "gpu must have the same length as cpu (%v != %v)", machineLength, len(gpu))
			*success = false
		}

		if price != nil && len(price) != machineLength {
			cfgutil.ReportError(filePath, gpuNode, "price must have the same length as cpu (%v != %v)", machineLength, len(price))
			*success = false
		}

		if len(memory) != machineLength {
			cfgutil.ReportError(filePath, memoryNode, "memory must have the same length as cpu (%v != %v)", machineLength, len(memory))
			*success = false
		}

		if len(actualCpuMillis) != machineLength {
			cfgutil.ReportError(filePath, memoryNode, "actualCpuMillis must have the same length as cpu (%v != %v)", machineLength, len(memory))
			*success = false
		}
	}

	for _, count := range cpu {
		if count <= 0 {
			cfgutil.ReportError(filePath, node, "cpu count must be greater than zero")
			*success = false
			break
		}
	}

	for _, count := range actualCpuMillis {
		if count <= 0 {
			cfgutil.ReportError(filePath, node, "actualCpuMillis must be greater than zero")
			*success = false
			break
		}
	}

	for _, count := range memory {
		if count <= 0 {
			cfgutil.ReportError(filePath, node, "memory must be greater than zero")
			*success = false
			break
		}
	}

	for _, count := range price {
		if count <= 0 {
			cfgutil.ReportError(filePath, node, "price must be greater than zero")
			*success = false
			break
		}
	}

	for _, count := range gpu {
		if count < 0 {
			cfgutil.ReportError(filePath, node, "gpu count must be positive")
			*success = false
			break
		}
	}

	if cfgutil.HasChild(node, "nameSuffix") {
		result.NameSuffix = cfgutil.RequireChildEnum(filePath, node, "nameSuffix", MachineResourceTypeOptions, success)
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
				AdvertisedCpu:     cpu[i],
				ActualCpuMillis:   actualCpuMillis[i],
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
