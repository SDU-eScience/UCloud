package config

import (
	"crypto/rsa"
	"crypto/x509"
	"encoding/pem"
	"errors"
	"fmt"
	"gopkg.in/yaml.v3"
	"net"
	"os"
	"path"
	"path/filepath"
	"regexp"
	"strings"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

type ServerMode int

const (
	ServerModeUser ServerMode = iota
	ServerModeServer
	ServerModeProxy
	ServerModePlugin
)

var Mode ServerMode
var Provider *ProviderConfiguration
var Services *ServicesConfiguration
var Server *ServerConfiguration

var secretsPath string
var secrets *yaml.Node

var enableErrorReporting = true

func reportError(path string, node *yaml.Node, format string, args ...any) {
	if !enableErrorReporting {
		return
	}

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

func optionalChildBool(path string, node *yaml.Node, child string) (value bool, ok bool) {
	enableErrorReporting = false
	value = requireChildBool(path, node, child, &ok)
	enableErrorReporting = true
	return
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
		temporaryFile := path.Join(text, "."+util.RandomToken(16))
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

func readAndParse(filePath string) *yaml.Node {
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
		return nil
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
		return nil
	}

	return &document
}

func requireSecrets(reason string) (string, *yaml.Node) {
	if secrets == nil {
		reportError("", &dummyNode, "Could not find secrets.yaml but it is required for this configuration. %v", reason)
		return "", nil
	}

	return secretsPath, secrets
}

func Parse(serverMode ServerMode, configDir string) bool {
	Mode = serverMode

	success := true

	if Mode == ServerModeServer {
		filePath := filepath.Join(configDir, "secrets.yml")
		fileBytes, err := os.ReadFile(filePath)
		if err != nil && !os.IsExist(err) {
			reportError(filePath, nil, "Could not read secrets.yml file. Underlying error: %v", err)
			return false
		}

		if err == nil {
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

			secretsPath = filePath
			secrets = &document
		}
	}

	if Mode == ServerModeServer {
		filePath := filepath.Join(configDir, "server.yml")
		document := readAndParse(filePath)
		if document == nil {
			return false
		}

		success, server := parseServer(filePath, document)
		if !success {
			return false
		}
		Server = &server
	}

	filePath := filepath.Join(configDir, "config.yml")
	document := readAndParse(filePath)
	if document == nil {
		return false
	}

	providerNode := requireChild(filePath, document, "provider", &success)
	if !success {
		return false
	}
	success, providerConfig := parseProvider(filePath, providerNode)
	if !success {
		return false
	}

	servicesNode := requireChild(filePath, document, "services", &success)
	if !success {
		return false
	}
	success, servicesConfig := parseServices(serverMode, filePath, servicesNode)
	if !success {
		return false
	}

	Provider = &providerConfig
	Services = &servicesConfig

	return true
}

type HostInfo struct {
	Address string `yaml:"address"`
	Port    int    `yaml:"port"`
	Scheme  string `yaml:"scheme"`
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

func (h HostInfo) ToURL() string {
	port := h.Port
	scheme := h.Scheme
	if scheme != "http" && scheme != "https" {
		if port == 443 || port == 8443 {
			scheme = "https"
		} else {
			scheme = "http"
		}
	}

	if port == 0 {
		if scheme == "https" {
			port = 443
		} else {
			port = 80
		}
	}

	return fmt.Sprintf("%v://%v:%v", scheme, h.Address, port)
}

type ServerConfiguration struct {
	RefreshToken string
}

func parseServer(filePath string, provider *yaml.Node) (bool, ServerConfiguration) {
	success := true
	cfg := ServerConfiguration{}
	cfg.RefreshToken = requireChildText(filePath, provider, "refreshToken", &success)
	return success, cfg
}

type ProviderConfiguration struct {
	Id string

	Hosts struct {
		UCloud       HostInfo
		Self         HostInfo
		UCloudPublic HostInfo
		SelfPublic   HostInfo
	}

	Ipc struct {
		Directory string
	}

	Envoy struct {
		StateDirectory            string
		FunceWrapper              bool
		Executable                string
		InternalAddressToProvider string
		ManagedExternally         bool
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

	cfg.Id = requireChildText(filePath, provider, "id", &success)

	{
		// Hosts section
		hosts := requireChild(filePath, provider, "hosts", &success)
		ucloudHost := requireChild(filePath, hosts, "ucloud", &success)
		selfHost := requireChild(filePath, hosts, "self", &success)
		decode(filePath, ucloudHost, &cfg.Hosts.UCloud, &success)
		decode(filePath, selfHost, &cfg.Hosts.Self, &success)

		ucloudPublic, _ := getChildOrNil(filePath, hosts, "ucloudPublic")
		if ucloudPublic != nil {
			decode(filePath, ucloudPublic, &cfg.Hosts.UCloudPublic, &success)
		} else {
			cfg.Hosts.UCloudPublic = cfg.Hosts.UCloud
		}

		selfPublic, _ := getChildOrNil(filePath, hosts, "selfPublic")
		if selfPublic != nil {
			decode(filePath, selfPublic, &cfg.Hosts.SelfPublic, &success)
		} else {
			cfg.Hosts.SelfPublic = cfg.Hosts.Self
		}

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
		ipcDirModeRequired := FileCheckRead
		if Mode == ServerModeServer {
			ipcDirModeRequired = FileCheckReadWrite
		}
		fmt.Printf("Mode is %v so we will require %v\n", Mode, ipcDirModeRequired)
		directory := requireChildFolder(filePath, ipc, "directory", ipcDirModeRequired, &success)
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

	if Mode == ServerModeServer {
		// Envoy section
		envoy := requireChild(filePath, provider, "envoy", &success)

		directory := requireChildFolder(filePath, envoy, "directory", FileCheckReadWrite, &success)
		cfg.Envoy.StateDirectory = directory

		exe := requireChildFile(filePath, envoy, "executable", FileCheckRead, &success)
		cfg.Envoy.Executable = exe

		funceWrapper, _ := optionalChildBool(filePath, envoy, "funceWrapper")
		cfg.Envoy.FunceWrapper = funceWrapper

		internalAddressToProvider := optionalChildText(filePath, envoy, "internalAddressToProvider", &success)
		if internalAddressToProvider == "" {
			internalAddressToProvider = "127.0.0.1"
		}
		cfg.Envoy.InternalAddressToProvider = internalAddressToProvider

		managedExternally, _ := optionalChildBool(filePath, envoy, "managedExternally")
		cfg.Envoy.ManagedExternally = managedExternally

		if !success {
			return false, cfg
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

type PaymentInterval string

const (
	PaymentIntervalMinutely PaymentInterval = "Minutely"
	PaymentIntervalHourly   PaymentInterval = "Hourly"
	PaymentIntervalDaily    PaymentInterval = "Daily"
)

var PaymentIntervalOptions = []PaymentInterval{
	PaymentIntervalMinutely,
	PaymentIntervalHourly,
	PaymentIntervalDaily,
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

type IdentityManagementType string

const (
	IdentityManagementTypeScripted IdentityManagementType = "Scripted"
	IdentityManagementTypeFreeIpa  IdentityManagementType = "FreeIPA"
)

var IdentityManagementTypeOptions = []IdentityManagementType{
	IdentityManagementTypeScripted,
	IdentityManagementTypeFreeIpa,
}

type IdentityManagement struct {
	Type          IdentityManagementType
	Configuration any
}

type IdentityManagementScripted struct {
	CreateUser     string
	SyncUserGroups string
}

type IdentityManagementFreeIPA struct {
	Url        string
	VerifyTls  bool
	CaCertFile util.Option[string]
	Username   string
	Password   string
	GroupName  string
}

func (m *IdentityManagement) Scripted() *IdentityManagementScripted {
	if m.Type == IdentityManagementTypeScripted {
		return m.Configuration.(*IdentityManagementScripted)
	}
	return nil
}

func (m *IdentityManagement) FreeIPA() *IdentityManagementFreeIPA {
	if m.Type == IdentityManagementTypeFreeIpa {
		return m.Configuration.(*IdentityManagementFreeIPA)
	}
	return nil
}

func parseIdentityManagement(filePath string, node *yaml.Node) (bool, IdentityManagement) {
	var result IdentityManagement
	success := true
	mType := requireChildEnum(filePath, node, "type", IdentityManagementTypeOptions, &success)
	result.Type = mType
	if !success {
		return false, result
	}

	switch result.Type {
	case IdentityManagementTypeScripted:
		ok, config := parseIdentityManagementScripted(filePath, node)
		if !ok {
			return false, result
		}

		result.Configuration = &config

	case IdentityManagementTypeFreeIpa:
		if Mode == ServerModeServer {
			sPath, secretsNode := requireSecrets("FreeIPA identity management has secret configuration!")
			if secretsNode == nil {
				return false, result
			}

			freeipaNode := requireChild(sPath, secretsNode, "freeipa", &success)
			if !success {
				return false, result
			}

			ok, config := parseIdentityManagementFreeIpa(sPath, freeipaNode)
			if !ok {
				return false, result
			}
			result.Configuration = &config
		} else {
			result.Configuration = &IdentityManagementFreeIPA{}
		}
	}

	return success, result
}

func parseIdentityManagementScripted(filePath string, node *yaml.Node) (bool, IdentityManagementScripted) {
	var result IdentityManagementScripted
	success := true
	result.CreateUser = requireChildFile(filePath, node, "createUser", FileCheckRead, &success)
	result.SyncUserGroups = requireChildFile(filePath, node, "syncUserGroups", FileCheckRead, &success)
	return success, result
}

func parseIdentityManagementFreeIpa(filePath string, node *yaml.Node) (bool, IdentityManagementFreeIPA) {
	var result IdentityManagementFreeIPA
	success := true

	result.Url = requireChildText(filePath, node, "url", &success)

	verifyTls, hasVerifyTls := optionalChildBool(filePath, node, "verifyTls")
	result.VerifyTls = verifyTls || !hasVerifyTls

	caCertFile := optionalChildText(filePath, node, "caCertFile", &success)
	if caCertFile != "" {
		requireChildFile(filePath, node, "caCertFile", FileCheckRead, &success)
		result.CaCertFile.Set(caCertFile)
	}

	result.Username = requireChildText(filePath, node, "username", &success)
	result.Password = requireChildText(filePath, node, "password", &success)
	groupName := optionalChildText(filePath, node, "groupName", &success)
	if groupName != "" {
		result.GroupName = groupName
	} else {
		result.GroupName = "ucloud_users"
	}

	return success, result
}

func ReadPublicKey(configDir string) *rsa.PublicKey {
	content, _ := os.ReadFile(configDir + "/ucloud_key.pub")
	if content == nil {
		return nil
	}

	var keyBuilder strings.Builder
	keyBuilder.WriteString("-----BEGIN PUBLIC KEY-----\n")
	keyBuilder.WriteString(chunkString(string(content), 64))
	keyBuilder.WriteString("\n-----END PUBLIC KEY-----\n")

	key := keyBuilder.String()

	block, _ := pem.Decode([]byte(key))
	if block == nil {
		return nil
	}

	pubKey, _ := x509.ParsePKIXPublicKey(block.Bytes)
	if pubKey == nil {
		return nil
	}

	rsaKey, _ := pubKey.(*rsa.PublicKey)
	return rsaKey
}

func chunkString(input string, chunkSize int) string {
	var builder strings.Builder
	for i, c := range input {
		if i != 0 && i%chunkSize == 0 {
			builder.WriteString("\n")
		}
		builder.WriteRune(c)
	}
	return builder.String()
}
