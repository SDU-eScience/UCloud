package config

import (
	"fmt"
	"gopkg.in/yaml.v3"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

type ServicesConfigurationSlurm struct {
	IdentityManagement IdentityManagement

	FileSystems map[string]SlurmFs
	Ssh         SlurmSsh
	Compute     SlurmCompute
}

type SlurmCompute struct {
	AccountManagement      SlurmAccountManagement
	Machines               map[string]SlurmMachineCategory
	Web                    SlurmWebConfiguration
	FakeResourceAllocation bool
	Applications           map[string][]SlurmApplicationConfiguration
	SystemLoadCommand      util.Option[string]
	SystemUnloadCommand    util.Option[string]
	Srun                   util.Option[SrunConfiguration]
	ModulesFile            util.Option[string]
	JobFolderName          string
}

type SrunConfiguration struct {
	Command string
	Flags   []string
}

type SlurmApplicationConfiguration struct {
	Versions []string
	Load     string
	Unload   string
	Srun     util.Option[SrunConfiguration]
	Readme   util.Option[string]
}

type SlurmWebConfiguration struct {
	Enabled bool
	Prefix  string
	Suffix  string
}

type SlurmMachineCategory struct {
	Payment   PaymentInfo
	Groups    map[string]SlurmMachineCategoryGroup
	Partition string
	Qos       util.Option[string]
}

type SlurmMachineCategoryGroup struct {
	Constraint  string
	NameSuffix  MachineResourceType
	Configs     []SlurmMachineConfiguration
	CpuModel    string
	GpuModel    string
	MemoryModel string
}

type SlurmMachineConfiguration struct {
	Cpu               int
	MemoryInGigabytes int
	Gpu               int
	Price             float64
}

type SlurmAccountManagement struct {
	Accounting    SlurmAccounting
	AccountMapper SlurmAccountMapper
}

type SlurmAccounting struct {
	Type          SlurmAccountingType
	Configuration any
}

func (m *SlurmAccounting) Automatic() *SlurmAccountingAutomatic {
	if m.Type == SlurmAccountingTypeAutomatic {
		return m.Configuration.(*SlurmAccountingAutomatic)
	}
	return nil
}

func (m *SlurmAccounting) Scripted() *SlurmAccountingScripted {
	if m.Type == SlurmAccountingTypeScripted {
		return m.Configuration.(*SlurmAccountingScripted)
	}
	return nil
}

type SlurmAccountingType string

const (
	SlurmAccountingTypeAutomatic SlurmAccountingType = "Automatic"
	SlurmAccountingTypeScripted  SlurmAccountingType = "Scripted"
	SlurmAccountingTypeNone      SlurmAccountingType = "None"
)

var SlurmAccountingTypeOptions = []SlurmAccountingType{
	SlurmAccountingTypeAutomatic,
	SlurmAccountingTypeScripted,
}

type SlurmAccountingScripted struct {
	OnQuotaUpdated   string
	OnUsageReporting string
	OnProjectUpdated string
}

type SlurmAccountingAutomatic struct {
}

type SlurmAccountMapper struct {
	Type          SlurmAccountMapperType
	Configuration any
}

func (m *SlurmAccountMapper) Scripted() *SlurmAccountMapperScripted {
	if m.Type == SlurmAccountMapperTypeScripted {
		return m.Configuration.(*SlurmAccountMapperScripted)
	}
	return nil
}

func (m *SlurmAccountMapper) Pattern() *SlurmAccountMapperPattern {
	if m.Type == SlurmAccountMapperTypePattern {
		return m.Configuration.(*SlurmAccountMapperPattern)
	}
	return nil
}

type SlurmAccountMapperType string

const (
	SlurmAccountMapperTypePattern  SlurmAccountMapperType = "Pattern"
	SlurmAccountMapperTypeScripted SlurmAccountMapperType = "Scripted"
	SlurmAccountMapperTypeNone     SlurmAccountMapperType = "None"
)

var SlurmAccountMapperTypeOptions = []SlurmAccountMapperType{
	SlurmAccountMapperTypePattern,
	SlurmAccountMapperTypeScripted,
}

type SlurmAccountMapperPattern struct {
	Users    string
	Projects string
}

type SlurmAccountMapperScripted struct {
	Script string
}

type SlurmSsh struct {
	Enabled     bool
	InstallKeys bool
	Host        HostInfo
}

type SlurmFs struct {
	Management    SlurmFsManagement
	Payment       PaymentInfo
	DriveLocators map[string]SlurmDriveLocator
}

type SlurmDriveLocatorEntityType string

const (
	SlurmDriveLocatorEntityTypeUser        SlurmDriveLocatorEntityType = "User"
	SlurmDriveLocatorEntityTypeProject     SlurmDriveLocatorEntityType = "Project"
	SlurmDriveLocatorEntityTypeMemberFiles SlurmDriveLocatorEntityType = "MemberFiles"
	SlurmDriveLocatorEntityTypeNone        SlurmDriveLocatorEntityType = "None"
)

var SlurmDriveLocatorEntityTypeOptions = []SlurmDriveLocatorEntityType{
	SlurmDriveLocatorEntityTypeUser,
	SlurmDriveLocatorEntityTypeProject,
	SlurmDriveLocatorEntityTypeMemberFiles,
}

type SlurmDriveLocator struct {
	Entity    SlurmDriveLocatorEntityType
	Pattern   string
	Script    string
	Title     string
	FreeQuota util.Option[int64] // valid only for users
}
type SlurmFsManagementType string

const (
	SlurmFsManagementTypeGpfs     SlurmFsManagementType = "GPFS"
	SlurmFsManagementTypeScripted SlurmFsManagementType = "Scripted"
	SlurmFsManagementTypeNone     SlurmFsManagementType = "None"
)

var SlurmFsManagementTypeOptions = []SlurmFsManagementType{
	SlurmFsManagementTypeGpfs,
	SlurmFsManagementTypeScripted,
}

type SlurmFsManagement struct {
	Type          SlurmFsManagementType
	Configuration any
}

func (m *SlurmFsManagement) GPFS() *SlurmFsManagementGpfs {
	if m.Type == SlurmFsManagementTypeGpfs {
		return m.Configuration.(*SlurmFsManagementGpfs)
	}
	return nil
}

func (m *SlurmFsManagement) Scripted() *SlurmFsManagementScripted {
	if m.Type == SlurmFsManagementTypeScripted {
		return m.Configuration.(*SlurmFsManagementScripted)
	}
	return nil
}

type SlurmFsManagementGpfs struct {
	Valid                  bool // Only true in server mode
	Username               string
	Password               string
	Server                 HostInfo
	VerifyTls              bool
	CaCertFile             util.Option[string]
	Mapping                map[string]GpfsMapping // Maps a locator name to a mapping
	UseStatFsForAccounting bool
}

type GpfsMapping struct {
	FileSystem     string `yaml:"fileSystem"`
	ParentFileSet  string `yaml:"parentFileSet"`
	FileSetPattern string `yaml:"fileSetPattern"`
}

type SlurmFsManagementScripted struct {
	OnQuotaUpdated   string
	OnUsageReporting string
}

func parseSlurmServices(unmanaged bool, serverMode ServerMode, filePath string, services *yaml.Node) (bool, ServicesConfigurationSlurm) {
	cfg := ServicesConfigurationSlurm{}
	success := true

	// Identity management
	identityManagement, _ := getChildOrNil(filePath, services, "identityManagement")
	if identityManagement != nil {
		ok, idm := parseIdentityManagement(filePath, identityManagement)
		if !ok {
			return false, cfg
		}

		cfg.IdentityManagement = idm
	} else {
		cfg.IdentityManagement.Type = IdentityManagementTypeNone
	}

	idmWorksInUnmanaged := true

	switch cfg.IdentityManagement.Type {
	case IdentityManagementTypeOidc:
		idmWorksInUnmanaged = true
	case IdentityManagementTypeNone:
		idmWorksInUnmanaged = true
	default:
		idmWorksInUnmanaged = false
	}

	if unmanaged && !idmWorksInUnmanaged {
		reportError(filePath, services, "identityManagement must be omitted when unmanaged is true")
	}

	if !unmanaged && idmWorksInUnmanaged {
		reportError(filePath, services, "identityManagement is required when managed")
	}

	{
		// File systems
		fileSystems := make(map[string]SlurmFs)
		cfg.FileSystems = fileSystems

		fsNode := requireChild(filePath, services, "fileSystems", &success)
		if !success {
			return false, cfg
		}

		if fsNode.Kind != yaml.MappingNode {
			reportError(filePath, fsNode, "expected fileSystems to be a dictionary")
			return false, cfg
		}

		for i := 0; i < len(fsNode.Content); i += 2 {
			fsNameNode := fsNode.Content[i]
			fsValueNode := fsNode.Content[i+1]

			var fileSystemName string
			decode(filePath, fsNameNode, &fileSystemName, &success)
			if !success {
				return false, cfg
			}

			// Parse the file system
			fs := SlurmFs{}
			fs.DriveLocators = make(map[string]SlurmDriveLocator)

			if !unmanaged {
				// Management
				managementNode := requireChild(filePath, fsValueNode, "management", &success)
				if !success {
					return false, cfg
				}

				fs.Management.Type = requireChildEnum(filePath, managementNode, "type", SlurmFsManagementTypeOptions,
					&success)

				switch fs.Management.Type {
				case SlurmFsManagementTypeGpfs:
					ess := SlurmFsManagementGpfs{}

					if serverMode == ServerModeServer {
						sPath, sNode := requireSecrets("GPFS management")
						gpfsTopLevel := requireChild(sPath, sNode, "gpfs", &success)
						gpfsNode := requireChild(sPath, gpfsTopLevel, fileSystemName, &success)

						ess.Username = requireChildText(sPath, gpfsNode, "username", &success)
						ess.Password = requireChildText(sPath, gpfsNode, "password", &success)
						essHost := requireChild(sPath, gpfsNode, "host", &success)
						decode(filePath, essHost, &ess.Server, &success)
						ess.Server.validate(sPath, essHost)

						verifyTls, hasVerifyTls := optionalChildBool(filePath, gpfsNode, "verifyTls")
						ess.VerifyTls = verifyTls || !hasVerifyTls

						caCertFile := optionalChildText(filePath, gpfsNode, "caCertFile", &success)
						if caCertFile != "" {
							requireChildFile(filePath, gpfsNode, "caCertFile", FileCheckRead, &success)
							ess.CaCertFile.Set(caCertFile)
						}

						mappingNode := requireChild(sPath, gpfsNode, "mapping", &success)
						decode(sPath, mappingNode, &ess.Mapping, &success)

						ess.UseStatFsForAccounting, _ = optionalChildBool(sPath, gpfsNode, "useStatFsForAccounting")

						ess.Valid = true

						if !success {
							return false, cfg
						}
					}

					fs.Management.Configuration = &ess

				case SlurmFsManagementTypeScripted:
					scripted := SlurmFsManagementScripted{}
					scripted.OnQuotaUpdated = requireChildText(filePath, managementNode, "onQuotaUpdated", &success)
					scripted.OnUsageReporting = requireChildText(filePath, managementNode, "onUsageReporting", &success)
					fs.Management.Configuration = &scripted

				}
			} else {
				managementNode, _ := getChildOrNil(filePath, fsValueNode, "management")
				fs.Management.Type = SlurmFsManagementTypeNone
				if managementNode != nil {
					reportError(filePath, managementNode, "management must be omitted when unmanaged is true")
					success = false
				}
			}

			fs.Payment = parsePaymentInfo(filePath, requireChild(filePath, fsValueNode, "payment", &success),
				[]string{"GB", "TB", "PB", "EB", "GiB", "TiB", "PiB", "EiB"}, true, &success)

			driveLocatorsNode := requireChild(filePath, fsValueNode, "driveLocators", &success)
			if driveLocatorsNode.Kind != yaml.MappingNode {
				reportError(filePath, driveLocatorsNode, "expected driveLocators to be a dictionary")
				return false, cfg
			}

			for i := 0; i < len(driveLocatorsNode.Content); i += 2 {
				locator := SlurmDriveLocator{}

				locatorName := ""
				_ = driveLocatorsNode.Content[i].Decode(&locatorName)
				locatorNode := driveLocatorsNode.Content[i+1]

				if !unmanaged {
					locator.Entity = requireChildEnum(filePath, locatorNode, "entity", SlurmDriveLocatorEntityTypeOptions, &success)
				} else {
					entityNode, _ := getChildOrNil(filePath, locatorNode, "entity")
					if entityNode != nil {
						reportError(filePath, entityNode, "entity must be omitted when unmanaged is true")
						success = false
					}
					locator.Entity = SlurmDriveLocatorEntityTypeNone
				}
				locator.Pattern = optionalChildText(filePath, locatorNode, "pattern", &success)
				locator.Script = optionalChildText(filePath, locatorNode, "script", &success)
				locator.Title = optionalChildText(filePath, locatorNode, "title", &success)

				if locator.Pattern == "" && locator.Script == "" {
					success = false
					reportError(filePath, locatorNode, "You must specify either a pattern or a script!")
				}

				freeQuota := optionalChildInt(filePath, locatorNode, "freeQuota", &success)
				if freeQuota.Present && locator.Entity != SlurmDriveLocatorEntityTypeUser {
					success = false
					reportError(filePath, locatorNode, "freeQuota can only be specified for user mappings")
				}

				if !freeQuota.Present && locator.Entity == SlurmDriveLocatorEntityTypeUser {
					success = false
					reportError(filePath, locatorNode, "freeQuota must be specified for user mappings")
				}

				locator.FreeQuota = freeQuota

				fs.DriveLocators[locatorName] = locator
			}

			if len(fs.DriveLocators) == 0 {
				success = false
				reportError(filePath, fsNode, "You must specify at least one driveLocator!")
			}

			if !success {
				return false, cfg
			}

			fileSystems[fileSystemName] = fs
		}
	}

	{
		// SSH
		sshNode, _ := getChildOrNil(filePath, services, "ssh")
		cfg.Ssh.Enabled = false
		if sshNode != nil {
			cfg.Ssh.Enabled = requireChildBool(filePath, sshNode, "enabled", &success)
			cfg.Ssh.InstallKeys = requireChildBool(filePath, sshNode, "installKeys", &success)
			hostNode := requireChild(filePath, sshNode, "host", &success)
			decode(filePath, hostNode, &cfg.Ssh.Host, &success)

			if !cfg.Ssh.Host.validate(filePath, hostNode) {
				success = false
			}
		}

		cfg.Ssh.InstallKeys = cfg.Ssh.Enabled && cfg.Ssh.InstallKeys

		if !success {
			return false, cfg
		}
	}

	{
		// Slurm
		slurmNode := requireChild(filePath, services, "slurm", &success)

		management := &cfg.Compute.AccountManagement
		if !unmanaged {
			managementNode := requireChild(filePath, slurmNode, "accountManagement", &success)

			accountingNode := requireChild(filePath, managementNode, "accounting", &success)
			management.Accounting.Type = requireChildEnum(filePath, accountingNode, "type", SlurmAccountingTypeOptions, &success)
			if !success {
				return false, cfg
			}

			switch management.Accounting.Type {
			case SlurmAccountingTypeAutomatic:
				res := parseAccountingAutomatic(filePath, accountingNode, &success)
				management.Accounting.Configuration = &res
			case SlurmAccountingTypeScripted:
				res := parseAccountingScripted(filePath, accountingNode, &success)
				management.Accounting.Configuration = &res
			}

			accountMapperNode := requireChild(filePath, managementNode, "accountMapper", &success)
			management.AccountMapper.Type = requireChildEnum(filePath, accountMapperNode, "type", SlurmAccountMapperTypeOptions, &success)
			if !success {
				return false, cfg
			}
			switch management.AccountMapper.Type {
			case SlurmAccountMapperTypePattern:
				res := parseAccountMapperPattern(filePath, accountMapperNode, &success)
				management.AccountMapper.Configuration = &res
			case SlurmAccountMapperTypeScripted:
				res := parseAccountMapperScripted(filePath, accountMapperNode, &success)
				management.AccountMapper.Configuration = &res
			}

			if !success {
				return false, cfg
			}
		} else {
			entityNode, _ := getChildOrNil(filePath, slurmNode, "accountManagement")
			if entityNode != nil {
				reportError(filePath, entityNode, "accountManagement must be omitted when unmanaged is true")
				success = false
			}
			management.Accounting.Type = SlurmAccountingTypeNone
			management.AccountMapper.Type = SlurmAccountMapperTypeNone
		}

		fakeResourceAllocation, ok := optionalChildBool(filePath, slurmNode, "fakeResourceAllocation")
		cfg.Compute.FakeResourceAllocation = fakeResourceAllocation && ok

		webNode, _ := getChildOrNil(filePath, slurmNode, "web")
		if webNode != nil {
			enabled, ok := optionalChildBool(filePath, webNode, "enabled")
			cfg.Compute.Web.Enabled = enabled && ok

			if cfg.Compute.Web.Enabled {
				cfg.Compute.Web.Prefix = requireChildText(filePath, webNode, "prefix", &success)
				cfg.Compute.Web.Suffix = requireChildText(filePath, webNode, "suffix", &success)
			}
		}

		srunChild, _ := getChildOrNil(filePath, slurmNode, "srun")
		if srunChild != nil {
			cfg.Compute.Srun.Set(parseSrunConfiguration(filePath, srunChild, &success))
		}

		globalLoad := optionalChildText(filePath, slurmNode, "systemLoad", &success)
		globalUnload := optionalChildText(filePath, slurmNode, "systemUnload", &success)
		if globalLoad != "" {
			cfg.Compute.SystemLoadCommand.Set(globalLoad)
		}

		if globalUnload != "" {
			cfg.Compute.SystemUnloadCommand.Set(globalUnload)
		}

		moduleFile := optionalChildText(filePath, slurmNode, "modulesFile", &success)
		if moduleFile != "" {
			cfg.Compute.ModulesFile.Set(moduleFile)
		}

		jobFolder := optionalChildText(filePath, slurmNode, "jobFolder", &success)
		if jobFolder == "" {
			jobFolder = "ucloud-jobs"
		}
		cfg.Compute.JobFolderName = jobFolder

		apps, ok := parseSlurmApplications(filePath)
		if !ok {
			return false, cfg
		}
		cfg.Compute.Applications = apps

		cfg.Compute.Machines = make(map[string]SlurmMachineCategory)
		machinesNode := requireChild(filePath, slurmNode, "machines", &success)
		if machinesNode.Kind != yaml.MappingNode {
			reportError(filePath, slurmNode, "expected machines to be a dictionary")
			return false, cfg
		}

		for i := 0; i < len(machinesNode.Content); i += 2 {
			machineCategoryName := ""
			_ = machinesNode.Content[i].Decode(&machineCategoryName)
			machineNode := machinesNode.Content[i+1]

			category := SlurmMachineCategory{}
			category.Groups = make(map[string]SlurmMachineCategoryGroup)
			category.Payment = parsePaymentInfo(
				filePath,
				requireChild(filePath, machineNode, "payment", &success),
				[]string{"Cpu", "Memory", "Gpu"},
				false,
				&success,
			)

			category.Partition = requireChildText(filePath, machineNode, "partition", &success)
			qos := optionalChildText(filePath, machineNode, "qos", &success)
			if qos != "" {
				category.Qos.Set(qos)
			}

			if !hasChild(machineNode, "groups") {
				group := parseSlurmMachineGroup(filePath, machineNode, &success)
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
					category.Groups[groupName] = parseSlurmMachineGroup(filePath, groupNode, &success)
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
	}

	return true, cfg
}

func parseSlurmMachineGroup(filePath string, node *yaml.Node, success *bool) SlurmMachineCategoryGroup {
	result := SlurmMachineCategoryGroup{}
	result.Constraint = optionalChildText(filePath, node, "constraint", success)
	result.CpuModel = optionalChildText(filePath, node, "cpuModel", success)
	result.GpuModel = optionalChildText(filePath, node, "gpuModel", success)
	result.MemoryModel = optionalChildText(filePath, node, "memoryModel", success)

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
			configuration := SlurmMachineConfiguration{
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

func parseAccountingAutomatic(filePath string, node *yaml.Node, success *bool) SlurmAccountingAutomatic {
	return SlurmAccountingAutomatic{}
}

func parseAccountingScripted(filePath string, node *yaml.Node, success *bool) SlurmAccountingScripted {
	result := SlurmAccountingScripted{}
	result.OnUsageReporting = requireChildFile(filePath, node, "onUsageReporting", FileCheckRead, success)
	result.OnQuotaUpdated = requireChildFile(filePath, node, "onQuotaUpdated", FileCheckRead, success)
	result.OnProjectUpdated = requireChildFile(filePath, node, "onProjectUpdated", FileCheckRead, success)
	return result
}

func parseAccountMapperPattern(filePath string, node *yaml.Node, success *bool) SlurmAccountMapperPattern {
	result := SlurmAccountMapperPattern{}
	result.Users = requireChildText(filePath, node, "users", success)
	result.Projects = requireChildText(filePath, node, "projects", success)
	return result
}

func parseAccountMapperScripted(filePath string, node *yaml.Node, success *bool) SlurmAccountMapperScripted {
	result := SlurmAccountMapperScripted{}
	result.Script = requireChildFile(filePath, node, "script", FileCheckRead, success)
	return result
}

func parseSlurmApplication(filePath string, name string, node *yaml.Node, success *bool) SlurmApplicationConfiguration {
	result := SlurmApplicationConfiguration{}
	versionsNode := requireChild(filePath, node, "versions", success)
	if versionsNode.Kind != yaml.SequenceNode {
		reportError(filePath, node, "expected versions to be a list")
		*success = false
		return result
	}

	decode(filePath, versionsNode, &result.Versions, success)

	result.Load = optionalChildText(filePath, node, "load", success)
	result.Unload = optionalChildText(filePath, node, "unload", success)
	result.Readme.Set(optionalChildText(filePath, node, "readme", success))
	if result.Readme.Value == "" {
		result.Readme.Present = false
	}

	srunChild, _ := getChildOrNil(filePath, node, "srun")
	if srunChild != nil {
		result.Srun.Set(parseSrunConfiguration(filePath, srunChild, success))
	}

	return result
}

func parseSrunConfiguration(filePath string, node *yaml.Node, success *bool) SrunConfiguration {
	result := SrunConfiguration{}
	decode(filePath, node, &result, success)
	return result
}

func parseSlurmApplications(filePath string) (map[string][]SlurmApplicationConfiguration, bool) {
	appDir := filepath.Join(filepath.Dir(filePath), "applications")

	var filesToParse []string

	_ = filepath.WalkDir(appDir, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}

		ext := filepath.Ext(path)
		if !d.IsDir() && (ext == ".yml" || ext == ".yaml") {
			info, err := d.Info()
			if err == nil {
				if info.Size() > 1024*1024*16 {
					log.Info("Skipping abnormally large YAML file (>16MB) at %v", path)
				} else {
					filesToParse = append(filesToParse, path)
				}
			}
		}

		return nil
	})

	apps := map[string][]SlurmApplicationConfiguration{}

	for _, filePath := range filesToParse {
		fileBytes, err := os.ReadFile(filePath)
		if err != nil {
			log.Warn("Failed to read YAML file at %s: %s", filePath, err)
		} else {
			fileText := string(fileBytes)
			documents := strings.Split(fileText, "\n---\n")

			for docIdx, doc := range documents {
				fakeFilePath := filePath
				if docIdx > 0 {
					fakeFilePath += fmt.Sprintf("[%d]", docIdx)
				}

				var document yaml.Node
				err = yaml.Unmarshal([]byte(doc), &document)
				if err != nil {
					reportError(
						fakeFilePath,
						nil,
						"Failed to parse this configuration file as valid YAML. Please check for errors. "+
							"Underlying error message: %v.",
						err,
					)
					return apps, false
				}

				success := true
				name := requireChildText(fakeFilePath, &document, "name", &success)
				configs := requireChild(fakeFilePath, &document, "configurations", &success)
				if !success || configs.Kind != yaml.SequenceNode {
					return apps, false
				}

				var appConfigs []SlurmApplicationConfiguration
				for j := 0; j < len(configs.Content); j++ {
					varApp := configs.Content[j]
					if varApp.Kind != yaml.MappingNode {
						reportError(filePath, varApp, "expected node to be a dictionary")
						success = false
					}

					appConfigs = append(appConfigs, parseSlurmApplication(fakeFilePath, name, varApp, &success))

					if !success {
						return apps, false
					}
				}

				apps[name] = appConfigs
			}
		}
	}

	return apps, true
}
