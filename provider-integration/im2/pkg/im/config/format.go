package config

import (
    "crypto/rand"
    "encoding/hex"
    "errors"
    "fmt"
    "gopkg.in/yaml.v3"
    "net"
    "os"
    "path"
    "regexp"
    "strings"
    "time"
    "ucloud.dk/pkg/log"
)

type ServerMode int

const (
    ServerModeUser ServerMode = iota
    ServerModeServer
    ServerModeProxy
    ServerModePlugin
)

func reportError(path string, node *yaml.Node, format string, args ...any) {
    if node != &dummyNode {
        if node != nil {
            combinedArgs := []any{path, node.Line, node.Column}
            combinedArgs = append(combinedArgs, args...)

            log.Error("%v at line %v (column %v): "+format, combinedArgs...)
        } else {
            combinedArgs := []any{path}
            combinedArgs = append(combinedArgs, args...)
            log.Error("%v: "+format, combinedArgs...)
        }
    }
}

func getChildOrNil(path string, node *yaml.Node, child string) (*yaml.Node, error) {
    if node == nil {
        return nil, errors.New("node is nil")
    }

    if node.Kind == yaml.DocumentNode {
        if node.Content == nil {
            return nil, errors.New("document is empty")
        }

        return getChildOrNil(path, node.Content[0], child)
    }

    if node.Kind != yaml.MappingNode {
        return nil, fmt.Errorf("expected a dictionary but got %v", node.Kind)
    }

    length := len(node.Content)
    for i := 0; i < length; i += 2 {
        key := node.Content[i]
        value := node.Content[i+1]
        if key.Tag == "!!str" && key.Value == child {
            return value, nil
        }
    }

    return nil, fmt.Errorf("could not find property '%v' but it is mandatory", child)
}

func hasChild(node *yaml.Node, child string) bool {
    node, _ = getChildOrNil("", node, child)
    return node != nil
}

var dummyNode = yaml.Node{}

func requireChild(path string, node *yaml.Node, child string, success *bool) *yaml.Node {
    result, err := getChildOrNil(path, node, child)
    if err != nil {
        *success = false
        reportError(path, node, err.Error())
        return &dummyNode
    }

    return result
}

func optionalChildText(path string, node *yaml.Node, child string, success *bool) string {
    n, err := getChildOrNil(path, node, child)
    if n == nil {
        return ""
    }

    var result string
    err = n.Decode(&result)
    if err != nil {
        reportError(path, n, "Expected a string here")
        *success = false
        return ""
    }

    return result
}

func requireChildText(path string, node *yaml.Node, child string, success *bool) string {
    n := requireChild(path, node, child, success)
    if !*success {
        return ""
    }

    var result string
    err := n.Decode(&result)
    if err != nil {
        reportError(path, n, "Expected a string here")
        *success = false
        return ""
    }

    return result
}

func requireChildFloat(path string, node *yaml.Node, child string, success *bool) float64 {
    n := requireChild(path, node, child, success)
    if !*success {
        return 0
    }

    var result float64
    err := n.Decode(&result)
    if err != nil {
        reportError(path, n, "Expected a float here")
        *success = false
        return 0
    }

    return result
}

func requireChildBool(path string, node *yaml.Node, child string, success *bool) bool {
    n := requireChild(path, node, child, success)
    if !*success {
        return false
    }

    var result bool
    err := n.Decode(&result)
    if err != nil {
        reportError(path, n, "Expected a string here")
        *success = false
        return false
    }

    return result
}

func requireChildEnum[T any](filePath string, node *yaml.Node, child string, options []T, success *bool) T {
    var result T
    text := requireChildText(filePath, node, child, success)
    if !*success {
        return result
    }

    for _, option := range options {
        if fmt.Sprint(option) == text {
            return option
        }
    }

    reportError(filePath, node, "expected '%v' to be one of %v", text, options)
    *success = false
    return result
}

func randomToken() string {
    bytes := make([]byte, 16)
    _, _ = rand.Read(bytes)
    return fmt.Sprintf("%v-%v", time.Now().UnixNano(), hex.EncodeToString(bytes))
}

type FileCheckFlags int

const (
    FileCheckNone  FileCheckFlags = 0
    FileCheckRead  FileCheckFlags = 1 << 0
    FileCheckWrite FileCheckFlags = 1 << 1

    FileCheckReadWrite = FileCheckRead | FileCheckWrite
)

func requireChildFolder(filePath string, node *yaml.Node, child string, flags FileCheckFlags, success *bool) string {
    text := requireChildText(filePath, node, child, success)
    if !*success {
        return ""
    }

    info, err := os.Stat(text)
    if err != nil || !info.IsDir() {
        reportError(filePath, node, "Expected this value to point to a valid directory, but %v is not a valid directory!", text)
        *success = false
        return ""
    }

    if flags&FileCheckRead != 0 {
        _, err = os.ReadDir(text)
        if err != nil {
            reportError(
                filePath,
                node,
                "Expected '%v' to be readable but failed to read a directory listing [UID=%v GID=%v] (%v).",
                text,
                os.Getuid(),
                os.Getgid(),
                err.Error(),
            )
            *success = false
            return ""
        }
    }

    if flags&FileCheckWrite != 0 {
        temporaryFile := path.Join(text, "."+randomToken())
        err = os.WriteFile(temporaryFile, []byte("UCloud/IM test file"), 0700)
        if err != nil {
            reportError(
                filePath,
                node,
                "Expected '%v' to be writeable but failed to create a file [UID=%v GID=%v] (%v).",
                text,
                os.Getuid(),
                os.Getgid(),
                err.Error(),
            )
            *success = false
            return ""
        }

        _ = os.Remove(temporaryFile)
    }

    return text
}

func requireChildFile(filePath string, node *yaml.Node, child string, flags FileCheckFlags, success *bool) string {
    text := requireChildText(filePath, node, child, success)
    if !*success {
        return ""
    }

    info, err := os.Stat(text)
    if err != nil || !info.Mode().IsRegular() {
        reportError(filePath, node, "Expected this value to point to a valid regular file, but %v is not a regular file!", text)
        *success = false
        return ""
    }

    if flags&FileCheckRead != 0 {
        handle, err := os.OpenFile(text, os.O_RDONLY, 0)
        if err != nil {
            reportError(
                filePath,
                node,
                "Expected '%v' to be readable but failed to open the file [UID=%v GID=%v] (%v).",
                text,
                os.Getuid(),
                os.Getgid(),
                err.Error(),
            )
            *success = false
            return ""
        } else {
            _ = handle.Close()
        }
    }

    if flags&FileCheckWrite != 0 {
        handle, err := os.OpenFile(text, os.O_WRONLY, 0600)
        if err != nil {
            reportError(
                filePath,
                node,
                "Expected '%v' to be writeable but failed to create a file [UID=%v GID=%v] (%v).",
                text,
                os.Getuid(),
                os.Getgid(),
                err.Error(),
            )
            *success = false
            return ""
        } else {
            _ = handle.Close()
        }
    }

    return text
}

func decode(filePath string, node *yaml.Node, result any, success *bool) {
    err := node.Decode(result)
    if err != nil {
        cleanedError := err.Error()
        cleanedError = strings.ReplaceAll(cleanedError, "yaml: unmarshal errors:\n", "")
        cleanedError = strings.ReplaceAll(cleanedError, "unmarshal", "convert")
        cleanedError = strings.ReplaceAll(cleanedError, "!!str", "text")
        cleanedError = strings.TrimSpace(cleanedError)
        regex := regexp.MustCompile("line \\d+: ")
        cleanedError = regex.ReplaceAllString(cleanedError, "")
        reportError(filePath, node, "Failed to parse value %v", cleanedError)
        *success = false
    }
}

func Parse(serverMode ServerMode, filePath string) bool {
    success := true
    fileBytes, err := os.ReadFile(filePath)
    if err != nil {
        reportError(
            filePath,
            nil,
            "Failed to read file. Does it exist/is it readable by the UCloud process? Current user is uid=%v gid=%v. "+
                "Underlying error message: %v.",
            os.Getuid(),
            os.Getgid(),
            err.Error(),
        )
        return false
    }

    var document yaml.Node

    err = yaml.Unmarshal(fileBytes, &document)
    if err != nil {
        reportError(
            filePath,
            nil,
            "Failed to parse this configuration file as valid YAML. Please check for errors. "+
                "Underlying error message: %v.",
            err.Error(),
        )
        return false
    }

    providerNode := requireChild(filePath, &document, "provider", &success)
    if !success {
        return false
    }
    success, providerConfig := parseProvider(filePath, providerNode)
    if !success {
        return false
    }

    _ = providerConfig

    servicesNode := requireChild(filePath, &document, "services", &success)
    if !success {
        return false
    }
    success, servicesConfig := parseServices(serverMode, filePath, servicesNode)
    if !success {
        return false
    }
    _ = servicesConfig

    return true
}

type HostInfo struct {
    Address string `yaml:"address"`
    Port    int    `yaml:"port"`
}

func (h HostInfo) validate(filePath string, node *yaml.Node) bool {
    if h.Port <= 0 || h.Port >= 1024*64 {
        if h.Port == 0 {
            reportError(filePath, node, "Port 0 is not a valid choice. Did you remember to specify a port?")
        } else {
            reportError(filePath, node, "Invalid TCP/IP port specified %v", h.Port)
        }
        return false
    }

    _, err := net.LookupHost(h.Address)

    if err != nil {
        reportError(
            filePath,
            node,
            "The host '%v:%v' appears to be invalid. Make sure that the hostname is valid and "+
                "that you are able to connect to it.",
            h.Address,
            h.Port,
        )
    }

    return err == nil
}

type ProviderConfiguration struct {
    Id string

    Hosts struct {
        UCloud HostInfo
        Self   HostInfo
    }

    Ipc struct {
        Directory string
    }

    Logs struct {
        Directory string
        Rotation  struct {
            Enabled               bool `yaml:"enabled"`
            RetentionPeriodInDays int  `yaml:"retentionPeriodInDays"`
        }
    }
}

func parseProvider(filePath string, provider *yaml.Node) (bool, ProviderConfiguration) {
    cfg := ProviderConfiguration{}
    success := true

    {
        // Hosts section
        hosts := requireChild(filePath, provider, "hosts", &success)
        ucloudHost := requireChild(filePath, hosts, "ucloud", &success)
        selfHost := requireChild(filePath, hosts, "self", &success)
        decode(filePath, ucloudHost, &cfg.Hosts.UCloud, &success)
        decode(filePath, selfHost, &cfg.Hosts.Self, &success)

        if !success {
            return false, cfg
        }

        if !cfg.Hosts.UCloud.validate(filePath, ucloudHost) || !cfg.Hosts.Self.validate(filePath, selfHost) {
            return false, cfg
        }
    }

    {
        // IPC section
        ipc := requireChild(filePath, provider, "ipc", &success)
        directory := requireChildFolder(filePath, ipc, "directory", FileCheckReadWrite, &success)
        cfg.Ipc.Directory = directory
        if !success {
            return false, cfg
        }
    }

    {
        // Logs section
        logs := requireChild(filePath, provider, "logs", &success)
        directory := requireChildFolder(filePath, logs, "directory", FileCheckReadWrite, &success)
        cfg.Logs.Directory = directory
        if !success {
            return false, cfg
        }

        rotationNode, _ := getChildOrNil(filePath, logs, "rotation")
        if rotationNode != nil {
            decode(filePath, rotationNode, &cfg.Logs.Rotation, &success)
            if !success {
                return false, cfg
            }

            rotation := &cfg.Logs.Rotation
            if rotation.Enabled {
                if rotation.RetentionPeriodInDays <= 0 {
                    reportError(filePath, rotationNode, "retentionPeriodInDays must be specified and must be greater than zero")
                    return false, cfg
                }
            }
        }
    }

    return true, cfg
}

type ServicesType string

const (
    ServicesSlurm      ServicesType = "Slurm"
    ServicesKubernetes ServicesType = "Kubernetes"
    ServicesPuhuri     ServicesType = "Puhuri"
)

var ServicesTypeOptions = []ServicesType{
    ServicesSlurm,
    ServicesKubernetes,
    ServicesPuhuri,
}

type ServicesConfiguration struct {
    Type          ServicesType
    Configuration any
}

func (cfg *ServicesConfiguration) Slurm() *ServicesConfigurationSlurm {
    if cfg.Type == ServicesSlurm {
        return cfg.Configuration.(*ServicesConfigurationSlurm)
    }
    return nil
}

func (cfg *ServicesConfiguration) Kubernetes() *ServicesConfigurationKubernetes {
    if cfg.Type == ServicesKubernetes {
        return cfg.Configuration.(*ServicesConfigurationKubernetes)
    }
    return nil
}

func (cfg *ServicesConfiguration) Puhuri() *ServicesConfigurationPuhuri {
    if cfg.Type == ServicesPuhuri {
        return cfg.Configuration.(*ServicesConfigurationPuhuri)
    }
    return nil
}

type ServicesConfigurationKubernetes struct{}
type ServicesConfigurationPuhuri struct{}

type ServicesConfigurationSlurm struct {
    IdentityManagement struct {
        Type   string
        Config string
    }

    FileSystems map[string]SlurmFs
    Ssh         SlurmSsh
    Compute     SlurmCompute
}

type SlurmCompute struct {
    AccountManagement SlurmComputeAccountManagement
    Machines          map[string]SlurmMachineCategory
}

type SlurmMachineCategory struct {
    Payment PaymentInfo
    Groups  map[string]SlurmMachineCategoryGroup
}

type SlurmMachineCategoryGroup struct {
    Partition   string
    Constraint  string
    NameSuffix  MachineResourceType
    Configs     []SlurmMachineConfiguration
    CpuModel    string
    GpuModel    string
    MemoryModel string
    Price       []float64
}

type SlurmMachineConfiguration struct {
    Cpu               int
    MemoryInGigabytes int
    Gpu               int
}

type MachineResourceType = string

const (
    MachineResourceTypeCpu    MachineResourceType = "Cpu"
    MachineResourceTypeGpu    MachineResourceType = "Gpu"
    MachineResourceTypeMemory MachineResourceType = "Memory"
)

var MachineResourceTypeOptions = []MachineResourceType{
    MachineResourceTypeCpu,
    MachineResourceTypeGpu,
    MachineResourceTypeMemory,
}

type SlurmComputeAccountManagement struct {
    IsScripted    bool
    WalletUpdated string
    FetchUsage    string
    AccountMapper string
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
    SlurmDriveLocatorEntityTypeCollection  SlurmDriveLocatorEntityType = "Collection"
    SlurmDriveLocatorEntityTypeMemberFiles SlurmDriveLocatorEntityType = "MemberFiles"
)

var SlurmDriveLocatorEntityTypeOptions = []SlurmDriveLocatorEntityType{
    SlurmDriveLocatorEntityTypeUser,
    SlurmDriveLocatorEntityTypeProject,
    SlurmDriveLocatorEntityTypeCollection,
    SlurmDriveLocatorEntityTypeMemberFiles,
}

type SlurmDriveLocator struct {
    Entity  SlurmDriveLocatorEntityType
    Pattern string
    Script  string
}

type PaymentInterval string

const (
    PaymentIntervalMinutely PaymentInterval = "Minutely"
    PaymentIntervalHourly   PaymentInterval = "Hourly"
    PaymentIntervalDaily    PaymentInterval = "Daily"
    PaymentIntervalMonthly  PaymentInterval = "Monthly"
)

var PaymentIntervalOptions = []PaymentInterval{
    PaymentIntervalMinutely,
    PaymentIntervalHourly,
    PaymentIntervalDaily,
    PaymentIntervalMonthly,
}

type PaymentType string

const (
    PaymentTypeResource PaymentType = "Resource"
    PaymentTypeMoney    PaymentType = "Money"
)

var PaymentTypeOptions = []PaymentType{
    PaymentTypeResource,
    PaymentTypeMoney,
}

type PaymentInfo struct {
    Type     PaymentType
    Unit     string
    Currency string
    Interval PaymentInterval
    Price    float64
}

func parsePaymentInfo(filePath string, node *yaml.Node, validUnits []string, withPrice bool, success *bool) PaymentInfo {
    result := PaymentInfo{}

    result.Type = requireChildEnum(filePath, node, "type", PaymentTypeOptions, success)

    if hasChild(node, "unit") {
        result.Unit = requireChildEnum(filePath, node, "unit", validUnits, success)
    }

    if hasChild(node, "interval") {
        result.Interval = requireChildEnum(filePath, node, "interval", PaymentIntervalOptions, success)
    }

    if result.Type == PaymentTypeMoney {
        if withPrice {
            result.Price = requireChildFloat(filePath, node, "price", success)
        }
        result.Currency = requireChildText(filePath, node, "currency", success)
        if result.Price <= 0 && result.Type == PaymentTypeMoney && withPrice {
            *success = false
            reportError(filePath, node, "A price greater than 0 must be specified with type = Money")
        }
    } else {
        priceNode, _ := getChildOrNil(filePath, node, "price")
        if priceNode != nil {
            reportError(filePath, node, "A price cannot be specified with type = Resource")
        }
    }

    if !*success {
        *success = false
        return PaymentInfo{}
    }

    return result
}

type SlurmFsManagementType string

const (
    SlurmFsManagementTypeEss      SlurmFsManagementType = "ESS"
    SlurmFsManagementTypeScripted SlurmFsManagementType = "Scripted"
)

var SlurmFsManagementTypeOptions = []SlurmFsManagementType{
    SlurmFsManagementTypeEss,
    SlurmFsManagementTypeScripted,
}

type SlurmFsManagement struct {
    Type          SlurmFsManagementType
    Configuration any
}

func (m *SlurmFsManagement) ESS() *SlurmFsManagementEss {
    if m.Type == SlurmFsManagementTypeEss {
        return m.Configuration.(*SlurmFsManagementEss)
    }
    return nil
}

func (m *SlurmFsManagement) Scripted() *SlurmFsManagementScripted {
    if m.Type == SlurmFsManagementTypeScripted {
        return m.Configuration.(*SlurmFsManagementScripted)
    }
    return nil
}

type SlurmFsManagementEss struct {
    ConfigurationFilePath string
    Configuration         struct {
        Valid    bool // Only true in server mode
        Username string
        Password string
        Server   HostInfo
    }
}

type SlurmFsManagementScripted struct {
    WalletUpdated string
    FetchUsage    string
}

func parseServices(serverMode ServerMode, filePath string, services *yaml.Node) (bool, ServicesConfiguration) {
    result := ServicesConfiguration{}
    success := true
    kind := requireChildEnum(filePath, services, "type", ServicesTypeOptions, &success)
    if !success {
        return false, result
    }
    result.Type = kind

    switch kind {
    case ServicesSlurm:
        success, slurm := parseSlurmServices(serverMode, filePath, services)
        if !success {
            return false, result
        }

        result.Configuration = &slurm
    }
    return true, result
}

func parseSlurmServices(serverMode ServerMode, filePath string, services *yaml.Node) (bool, ServicesConfigurationSlurm) {
    cfg := ServicesConfigurationSlurm{}
    success := true

    {
        // Identity management
        identityManagement := requireChild(filePath, services, "identityManagement", &success)
        if !success {
            return false, cfg
        }
        _ = identityManagement

        // TODO
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

            {
                // Management
                managementNode := requireChild(filePath, fsValueNode, "management", &success)
                if !success {
                    return false, cfg
                }

                fs.Management.Type = requireChildEnum(filePath, managementNode, "type", SlurmFsManagementTypeOptions,
                    &success)

                switch fs.Management.Type {
                case SlurmFsManagementTypeEss:
                    ess := SlurmFsManagementEss{}
                    ess.ConfigurationFilePath = requireChildText(filePath, managementNode, "config", &success)

                    if serverMode == ServerModeServer {
                        requireChildFile(filePath, managementNode, "config", FileCheckReadWrite, &success)
                        if !success {
                            return false, cfg
                        }

                        configBytes, err := os.ReadFile(ess.ConfigurationFilePath)
                        if err != nil {
                            reportError(filePath, managementNode, "Failed to read config file: %v", err.Error())
                            return false, cfg
                        }

                        err = yaml.Unmarshal(configBytes, &ess.Configuration)
                        if err != nil {
                            reportError(ess.ConfigurationFilePath, nil, "Failed to parse ESS configuration: %v", err.Error())
                            return false, cfg
                        }

                        if !ess.Configuration.Server.validate(ess.ConfigurationFilePath, nil) {
                            return false, cfg
                        }
                        ess.Configuration.Valid = true
                    }

                    fs.Management.Configuration = &ess

                case SlurmFsManagementTypeScripted:
                    scripted := SlurmFsManagementScripted{}
                    scripted.WalletUpdated = requireChildText(filePath, managementNode, "walletUpdated", &success)
                    scripted.FetchUsage = requireChildText(filePath, managementNode, "fetchUsage", &success)
                    fs.Management.Configuration = &scripted

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

                locator.Entity = requireChildEnum(filePath, locatorNode, "entity", SlurmDriveLocatorEntityTypeOptions, &success)
                locator.Pattern = optionalChildText(filePath, locatorNode, "pattern", &success)
                locator.Script = optionalChildText(filePath, locatorNode, "script", &success)

                if locator.Pattern == "" && locator.Script == "" {
                    success = false
                    reportError(filePath, locatorNode, "You must specify either a pattern or a script!")
                }

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

        if !success {
            return false, cfg
        }
    }

    {
        // Slurm
        slurmNode := requireChild(filePath, services, "slurm", &success)

        {
            management := &cfg.Compute.AccountManagement
            managementNode := requireChild(filePath, slurmNode, "accountManagement", &success)
            managementType := requireChildEnum(filePath, managementNode, "type",
                []string{"Automatic", "Scripted"}, &success)

            management.IsScripted = managementType == "Scripted"
            if management.IsScripted {
                management.AccountMapper = requireChildText(filePath, managementNode, "accountMapper", &success)
                management.FetchUsage = requireChildText(filePath, managementNode, "fetchUsage", &success)
                management.WalletUpdated = requireChildText(filePath, managementNode, "walletUpdated", &success)
            }

            if !success {
                return false, cfg
            }
        }

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
                    if group.Price == nil {
                        reportError(filePath, machineNode, "price must be specified for all machine groups when payment type is Money!")
                        return false, cfg
                    }
                }
            } else {
                for key := range category.Groups {
                    group, _ := category.Groups[key]
                    if group.Price != nil {
                        reportError(filePath, machineNode, "price must not be specified for all machine groups when payment type is Resource!")
                        return false, cfg
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
    result.Partition = requireChildText(filePath, node, "partition", success)
    result.Constraint = requireChildText(filePath, node, "constraint", success)
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

    result.Price = price

    if *success {
        for i := 0; i < len(cpu); i++ {
            gpuCount := 0
            if gpu != nil {
                gpuCount = gpu[i]
            }
            result.Configs = append(result.Configs, SlurmMachineConfiguration{
                Cpu:               cpu[i],
                MemoryInGigabytes: memory[i],
                Gpu:               gpuCount,
            })
        }
    }

    return result
}
