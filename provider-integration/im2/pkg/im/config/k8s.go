package config

import (
	"math"
	"net"

	"gopkg.in/yaml.v3"
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

type KubernetesIngressConfiguration struct {
	Enabled bool
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
	Ingresses                       KubernetesIngressConfiguration
	Ssh                             KubernetesSshConfiguration
	Syncthing                       KubernetesSyncthingConfiguration
	IntegratedTerminal              KubernetesIntegratedTerminal
	VirtualMachineStorageClass      util.Option[string]
	ImSourceCode                    util.Option[string]
	Modules                         map[string]KubernetesModuleEntry
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

		scanMethodNode, _ := getChildOrNil(filePath, fsNode, "scanMethod")
		if scanMethodNode != nil {
			cfg.FileSystem.ScanMethod.Type = requireChildEnum(filePath, scanMethodNode, "type",
				K8sScanMethodTypeValues, &success)

			switch cfg.FileSystem.ScanMethod.Type {
			case K8sScanMethodTypeWalk:
				// Do nothing

			case K8sScanMethodTypeExtendedAttribute:
				cfg.FileSystem.ScanMethod.ExtendedAttribute = requireChildText(filePath, scanMethodNode,
					"xattr", &success)

			case K8sScanMethodTypeDevFile:
				// Do nothing
			}
		} else {
			cfg.FileSystem.ScanMethod.Type = K8sScanMethodTypeWalk
		}
	}

	computeNode := requireChild(filePath, services, "compute", &success)
	cfg.Compute.Namespace = optionalChildText(filePath, services, "namespace", &success)
	cfg.Compute.ImSourceCode = util.OptStringIfNotEmpty(optionalChildText(filePath, computeNode, "imSourceCode", &success))

	// NOTE(Dan): Default value was based on several tests on the current production environment. Results were very
	// stable around 14.5MB/s. This result seems very low, but consistent. Thankfully, it is fairly rare that people
	// run containers that are not already present on the machine.
	cfg.Compute.EstimatedContainerDownloadSpeed = optionalChildFloat(
		filePath,
		computeNode,
		"estimatedContainerDownloadSpeed",
		&success,
	).GetOrDefault(14.5)

	if cfg.Compute.Namespace == "" {
		cfg.Compute.Namespace = "ucloud-apps"
	}

	cfg.Compute.Modules = map[string]KubernetesModuleEntry{}
	modulesNode, _ := getChildOrNil(filePath, computeNode, "modules")
	if modulesNode != nil && modulesNode.Kind != yaml.MappingNode {
		reportError(filePath, computeNode, "expected 'modules' to be a dictionary")
		return false, cfg
	}

	if modulesNode != nil {
		for i := 0; i < len(modulesNode.Content); i += 2 {
			entry := KubernetesModuleEntry{}
			_ = modulesNode.Content[i].Decode(&entry.Name)

			entryNode := modulesNode.Content[i+1]
			entry.VolSubPath = requireChildText(filePath, entryNode, "subPath", &success)
			entry.HostPath = util.OptStringIfNotEmpty(optionalChildText(filePath, entryNode, "hostPath", &success))
			entry.ClaimName = util.OptStringIfNotEmpty(optionalChildText(filePath, entryNode, "claimName", &success))

			claimSourceCount := 0
			if entry.HostPath.Present {
				claimSourceCount++
			}
			if entry.ClaimName.Present {
				claimSourceCount++
			}

			if claimSourceCount != 1 {
				reportError(filePath, entryNode, "exactly one of 'hostPath' and 'volumeClaim' must be set!")
				return false, cfg
			}

			_, exists := cfg.Compute.Modules[entry.Name]
			if exists {
				reportError(filePath, entryNode, "another module with this name already exists")
				return false, cfg
			}

			cfg.Compute.Modules[entry.Name] = entry
		}
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

	ingressNode, _ := getChildOrNil(filePath, computeNode, "publicLinks")
	if ingressNode != nil {
		enabled, ok := optionalChildBool(filePath, ingressNode, "enabled")
		cfg.Compute.Ingresses.Enabled = enabled && ok

		success = true
		prefix := optionalChildText(filePath, ingressNode, "prefix", &success)

		if success && prefix != "" {
			cfg.Compute.Ingresses.Prefix = prefix
		} else {
			cfg.Compute.Ingresses.Prefix = "app-"
		}

		success = true
		suffix := optionalChildText(filePath, ingressNode, "suffix", &success)

		if success && suffix != "" {
			cfg.Compute.Ingresses.Suffix = suffix
		} else {
			cfg.Compute.Ingresses.Prefix = ".example.com"
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

	syncthingNode, _ := getChildOrNil(filePath, computeNode, "syncthing")
	if syncthingNode != nil {
		enabled, ok := optionalChildBool(filePath, syncthingNode, "enabled")
		cfg.Compute.Syncthing.Enabled = enabled && ok

		if cfg.Compute.Syncthing.Enabled {
			ipAddr := requireChildText(filePath, syncthingNode, "ipAddress", &success)
			if success {
				ip := net.ParseIP(ipAddr)
				if ip == nil {
					reportError(filePath, syncthingNode, "Invalid IP address specified")
					success = false
				} else {
					cfg.Compute.Syncthing.IpAddress = ipAddr
				}
			}

			portMin := requireChildInt(filePath, syncthingNode, "portMin", &success)
			if success && (portMin <= 0 || portMin >= math.MaxInt16) {
				reportError(filePath, syncthingNode, "portMin is invalid")
				success = false
			}

			portMax := requireChildInt(filePath, syncthingNode, "portMax", &success)
			if success && (portMax <= 0 || portMax >= math.MaxInt16) {
				reportError(filePath, syncthingNode, "portMax is invalid")
				success = false
			}

			if success && portMin < portMin {
				reportError(filePath, syncthingNode, "portMax is less than portMin")
				success = false
			}

			cfg.Compute.Syncthing.PortMin = int(portMin)
			cfg.Compute.Syncthing.PortMax = int(portMax)

			cfg.Compute.Syncthing.DevelopmentSourceCode = optionalChildText(filePath, syncthingNode, "developmentSourceCode", &success)

			relaysEnabled, ok := optionalChildBool(filePath, syncthingNode, "relaysEnabled")
			cfg.Compute.Syncthing.RelaysEnabled = relaysEnabled && ok
			cfg.Compute.Syncthing.DevelopmentSourceCode = optionalChildText(filePath, syncthingNode, "developmentSourceCode", &success)
		}
	}

	integratedTerminalNode, _ := getChildOrNil(filePath, computeNode, "integratedTerminal")
	if integratedTerminalNode != nil {
		enabled, ok := optionalChildBool(filePath, integratedTerminalNode, "enabled")
		cfg.Compute.IntegratedTerminal.Enabled = enabled && ok
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
