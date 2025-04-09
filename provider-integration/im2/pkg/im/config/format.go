package config

import (
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"encoding/binary"
	"encoding/pem"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"gopkg.in/yaml.v3"
	"ucloud.dk/shared/pkg/cfgutil"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/util"
)

type ServerMode int

const (
	ServerModeUser ServerMode = iota
	ServerModeServer
	ServerModeProxy
	ServerModePlugin
)

var OwnEnvoySecret = "invalidkeymustneverbeused" + util.RandomToken(16)
var Mode ServerMode
var Provider *ProviderConfiguration
var Services *ServicesConfiguration
var Server *ServerConfiguration

var secretsPath string
var secrets *yaml.Node

type Jwk struct {
	Kty string `json:"kty"`
	N   string `json:"n"`
	E   string `json:"e"`
	Alg string `json:"alg"`
	Use string `json:"use"`
}

type JwkSet struct {
	Keys []Jwk `json:"keys"`
}

var Jwks JwkSet

func requireSecrets(reason string) (string, *yaml.Node) {
	if secrets == nil {
		cfgutil.ReportError("", &cfgutil.DummyNode, "Could not find secrets.yaml but it is required for this configuration. %v", reason)
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

		if err != nil {
			filePath = filepath.Join(configDir, "secrets.yaml")
			fileBytes, err = os.ReadFile(filePath)
		}

		if err == nil {
			var document yaml.Node

			err = yaml.Unmarshal(fileBytes, &document)
			if err != nil {
				cfgutil.ReportError(
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
		filePath, document := cfgutil.ReadAndParse(configDir, "server")
		if document == nil {
			return false
		}

		success, server := parseServer(filePath, document)
		if !success {
			return false
		}
		Server = &server
	}

	filePath, document := cfgutil.ReadAndParse(configDir, "config")
	if document == nil {
		return false
	}

	providerNode := cfgutil.RequireChild(filePath, document, "provider", &success)
	if !success {
		return false
	}
	success, providerConfig := parseProvider(filePath, providerNode)
	if !success {
		return false
	}

	servicesNode := cfgutil.RequireChild(filePath, document, "services", &success)
	if !success {
		return false
	}
	success, servicesConfig := parseServices(serverMode, filePath, servicesNode)
	if !success {
		return false
	}

	Provider = &providerConfig
	Services = &servicesConfig

	key := readPublicKey(configDir)
	if key == nil {
		cfgutil.ReportError(configDir, nil, "Failed to parse/locate public key in %v", configDir)
		return false
	}
	eBytes := make([]byte, 4)
	binary.LittleEndian.PutUint32(eBytes, uint32(key.E))
	jwkKey := Jwk{
		Kty: "RSA",
		Alg: "RS256",
		Use: "sig",
		N:   base64.URLEncoding.EncodeToString(key.N.Bytes()),
		E:   base64.URLEncoding.EncodeToString(eBytes[0:3]),
	}

	Jwks = JwkSet{Keys: []Jwk{jwkKey}}

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
			cfgutil.ReportError(filePath, node, "Port 0 is not a valid choice. Did you remember to specify a port?")
		} else {
			cfgutil.ReportError(filePath, node, "Invalid TCP/IP port specified %v", h.Port)
		}
		return false
	}

	return true
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

	portIsDefault := (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
	if portIsDefault {
		return fmt.Sprintf("%v://%v", scheme, h.Address)
	} else {
		return fmt.Sprintf("%v://%v:%v", scheme, h.Address, port)
	}
}

func (h HostInfo) ToWebSocketUrl() string {
	url := h.ToURL()
	url = strings.ReplaceAll(url, "http://", "ws://")
	url = strings.ReplaceAll(url, "https://", "wss://")
	return url
}

type ServerConfiguration struct {
	RefreshToken string

	Database struct {
		Embedded              bool
		EmbeddedDataDirectory string

		Host     HostInfo
		Username string
		Password string
		Database string
		Ssl      bool
	}
}

func parseServer(filePath string, provider *yaml.Node) (bool, ServerConfiguration) {
	success := true
	cfg := ServerConfiguration{}

	// Parse simple properties
	cfg.RefreshToken = cfgutil.RequireChildText(filePath, provider, "refreshToken", &success)

	// Parse the database section
	dbNode, _ := cfgutil.GetChildOrNil(filePath, provider, "database")
	if dbNode != nil {
		cfg.Database.Embedded = cfgutil.RequireChildBool(filePath, dbNode, "embedded", &success)
	} else {
		cfg.Database.Embedded = true
	}

	if !cfg.Database.Embedded {
		hostNode := cfgutil.RequireChild(filePath, dbNode, "host", &success)
		cfgutil.Decode(filePath, hostNode, &cfg.Database.Host, &success)
		if cfg.Database.Host.Port == 0 {
			cfg.Database.Host.Port = 5432
		}

		cfg.Database.Username = cfgutil.RequireChildText(filePath, dbNode, "username", &success)
		cfg.Database.Password = cfgutil.RequireChildText(filePath, dbNode, "password", &success)
		cfg.Database.Database = cfgutil.RequireChildText(filePath, dbNode, "database", &success)
		cfg.Database.Ssl = cfgutil.RequireChildBool(filePath, dbNode, "ssl", &success)
	} else {
		db := &cfg.Database
		if dbNode != nil {
			db.EmbeddedDataDirectory = cfgutil.OptionalChildText(filePath, dbNode, "directory", &success)
		}

		if db.EmbeddedDataDirectory == "" {
			db.EmbeddedDataDirectory = "/etc/ucloud/postgres"
		}

		if success {
			_, err := os.Stat(db.EmbeddedDataDirectory)
			if err != nil {
				if os.IsNotExist(err) {
					err = os.Mkdir(db.EmbeddedDataDirectory, 0700)
					if err != nil {
						cfgutil.ReportError(filePath, dbNode, "Could not create postgres data directory at %v: %v", db.EmbeddedDataDirectory, err)
						success = false
					}
				}
			}
		}

		if success {
			// NOTE(Dan): This cannot go in the data dir since the file will be deleted by the embedded postgres instance
			passwordFile := filepath.Join(filepath.Dir(filePath), ".psql-password")
			password, err := os.ReadFile(passwordFile)
			if err != nil {
				password = []byte(util.RandomToken(32))
				err = os.WriteFile(passwordFile, password, 0600)
				if err != nil {
					cfgutil.ReportError(filePath, dbNode, "Could not write postgres password file to disk: %v", err)
					success = false
				}
			}

			db.Host = HostInfo{
				Address: "127.0.0.1",
				Port:    5432,
			}

			db.Username = "ucloud"
			db.Password = string(password)
			db.Database = "ucloud"
			db.Ssl = false
		}
	}

	{
		node := dbNode
		if node == nil {
			node = provider
		}

		cfg.Database.Host.validate(filePath, node)
	}

	return success, cfg
}

type EnvoyListenMode string

const (
	EnvoyListenModeUnix EnvoyListenMode = "Unix"
	EnvoyListenModeTcp  EnvoyListenMode = "Tcp"
)

var envoyListenModes = []EnvoyListenMode{
	EnvoyListenModeUnix,
	EnvoyListenModeTcp,
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
		ListenMode                EnvoyListenMode
	}

	Logs struct {
		Directory string
		Rotation  struct {
			Enabled               bool `yaml:"enabled"`
			RetentionPeriodInDays int  `yaml:"retentionPeriodInDays"`
		}
	}

	Maintenance struct {
		UserAllowList []string
	}
}

func parseProvider(filePath string, provider *yaml.Node) (bool, ProviderConfiguration) {
	cfg := ProviderConfiguration{}
	success := true

	cfg.Id = cfgutil.RequireChildText(filePath, provider, "id", &success)

	{
		// Hosts section
		hosts := cfgutil.RequireChild(filePath, provider, "hosts", &success)
		ucloudHost := cfgutil.RequireChild(filePath, hosts, "ucloud", &success)
		selfHost := cfgutil.RequireChild(filePath, hosts, "self", &success)
		cfgutil.Decode(filePath, ucloudHost, &cfg.Hosts.UCloud, &success)
		cfgutil.Decode(filePath, selfHost, &cfg.Hosts.Self, &success)

		ucloudPublic, _ := cfgutil.GetChildOrNil(filePath, hosts, "ucloudPublic")
		if ucloudPublic != nil {
			cfgutil.Decode(filePath, ucloudPublic, &cfg.Hosts.UCloudPublic, &success)
		} else {
			cfg.Hosts.UCloudPublic = cfg.Hosts.UCloud
		}

		selfPublic, _ := cfgutil.GetChildOrNil(filePath, hosts, "selfPublic")
		if selfPublic != nil {
			cfgutil.Decode(filePath, selfPublic, &cfg.Hosts.SelfPublic, &success)
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
		ipc := cfgutil.RequireChild(filePath, provider, "ipc", &success)
		ipcDirModeRequired := cfgutil.FileCheckRead
		if Mode == ServerModeServer {
			ipcDirModeRequired = cfgutil.FileCheckReadWrite
		}
		directory := cfgutil.RequireChildFolder(filePath, ipc, "directory", ipcDirModeRequired, &success)
		cfg.Ipc.Directory = directory
		if !success {
			return false, cfg
		}
	}

	{
		// Logs section
		logs := cfgutil.RequireChild(filePath, provider, "logs", &success)
		directory := cfgutil.RequireChildFolder(filePath, logs, "directory", cfgutil.FileCheckReadWrite, &success)
		cfg.Logs.Directory = directory
		if !success {
			return false, cfg
		}

		rotationNode, _ := cfgutil.GetChildOrNil(filePath, logs, "rotation")
		if rotationNode != nil {
			cfgutil.Decode(filePath, rotationNode, &cfg.Logs.Rotation, &success)
			if !success {
				return false, cfg
			}

			rotation := &cfg.Logs.Rotation
			if rotation.Enabled {
				if rotation.RetentionPeriodInDays <= 0 {
					cfgutil.ReportError(filePath, rotationNode, "retentionPeriodInDays must be specified and must be greater than zero")
					return false, cfg
				}
			}
		}
	}

	{
		// Maintenance section
		maintenance, _ := cfgutil.GetChildOrNil(filePath, provider, "maintenance")
		if maintenance != nil {
			allowListNode := cfgutil.RequireChild(filePath, maintenance, "userAllowList", &success)

			var userAllowList []string
			cfgutil.Decode(filePath, allowListNode, &userAllowList, &success)

			cfg.Maintenance.UserAllowList = userAllowList
		}
	}

	if Mode == ServerModeServer {
		// Envoy section
		envoy := cfgutil.RequireChild(filePath, provider, "envoy", &success)

		directory := cfgutil.RequireChildFolder(filePath, envoy, "directory", cfgutil.FileCheckReadWrite, &success)
		cfg.Envoy.StateDirectory = directory

		managedExternally, _ := cfgutil.OptionalChildBool(filePath, envoy, "managedExternally")
		cfg.Envoy.ManagedExternally = managedExternally

		listenMode, hasListenMode := cfgutil.OptionalChildEnum(filePath, envoy, "listenMode", envoyListenModes, &success)
		if !hasListenMode {
			listenMode = EnvoyListenModeUnix
		}
		cfg.Envoy.ListenMode = listenMode

		if !cfg.Envoy.ManagedExternally {
			exe := cfgutil.RequireChildFile(filePath, envoy, "executable", cfgutil.FileCheckRead, &success)
			cfg.Envoy.Executable = exe

			funceWrapper, _ := cfgutil.OptionalChildBool(filePath, envoy, "funceWrapper")
			cfg.Envoy.FunceWrapper = funceWrapper
		}

		internalAddressToProvider := cfgutil.OptionalChildText(filePath, envoy, "internalAddressToProvider", &success)
		if internalAddressToProvider == "" {
			internalAddressToProvider = "127.0.0.1"
		}
		cfg.Envoy.InternalAddressToProvider = internalAddressToProvider

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
)

var ServicesTypeOptions = []ServicesType{
	ServicesSlurm,
	ServicesKubernetes,
}

type ServicesConfiguration struct {
	Type      ServicesType
	Unmanaged bool

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

	result.Type = cfgutil.RequireChildEnum(filePath, node, "type", PaymentTypeOptions, success)

	if cfgutil.HasChild(node, "unit") {
		result.Unit = cfgutil.RequireChildEnum(filePath, node, "unit", validUnits, success)
	}

	if cfgutil.HasChild(node, "interval") {
		result.Interval = cfgutil.RequireChildEnum(filePath, node, "interval", PaymentIntervalOptions, success)
	}

	if result.Type == PaymentTypeMoney {
		if withPrice {
			result.Price = cfgutil.RequireChildFloat(filePath, node, "price", success)
		}
		result.Currency = cfgutil.RequireChildText(filePath, node, "currency", success)
		if result.Price <= 0 && result.Type == PaymentTypeMoney && withPrice {
			*success = false
			cfgutil.ReportError(filePath, node, "A price greater than 0 must be specified with type = Money")
		}
	} else {
		priceNode, _ := cfgutil.GetChildOrNil(filePath, node, "price")
		if priceNode != nil {
			cfgutil.ReportError(filePath, node, "A price cannot be specified with type = Resource")
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
	kind := cfgutil.RequireChildEnum(filePath, services, "type", ServicesTypeOptions, &success)
	if !success {
		return false, result
	}
	result.Type = kind

	isUnmanaged, ok := cfgutil.OptionalChildBool(filePath, services, "unmanaged")
	result.Unmanaged = isUnmanaged && ok

	switch kind {
	case ServicesSlurm:
		success, slurm := parseSlurmServices(result.Unmanaged, serverMode, filePath, services)
		if !success {
			return false, result
		}

		result.Configuration = &slurm

	case ServicesKubernetes:
		success, slurm := parseKubernetesServices(result.Unmanaged, serverMode, filePath, services)
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
	IdentityManagementTypeOidc     IdentityManagementType = "OIDC"
	IdentityManagementTypeNone     IdentityManagementType = "None"
)

var IdentityManagementTypeOptions = []IdentityManagementType{
	IdentityManagementTypeScripted,
	IdentityManagementTypeFreeIpa,
	IdentityManagementTypeOidc,
}

type IdentityManagement struct {
	Type          IdentityManagementType
	Configuration any
}

type IdentityManagementScripted struct {
	OnUserConnected  string
	OnProjectUpdated string
}

type IdentityManagementOidc struct {
	OnUserConnected string
	Issuer          string
	ClientId        string
	ClientSecret    string
	Scopes          []string
	ExpiresAfterMs  uint64
}

type IdentityManagementFreeIPA struct {
	Url             string
	VerifyTls       bool
	CaCertFile      util.Option[string]
	Username        string
	Password        string
	GroupName       string
	ProjectStrategy fnd.ProjectTitleStrategy
	ProjectPrefix   string // only if ProjectStrategy is ProjectTitleDate
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

func (m *IdentityManagement) OIDC() *IdentityManagementOidc {
	if m.Type == IdentityManagementTypeOidc {
		return m.Configuration.(*IdentityManagementOidc)
	}
	return nil
}

func parseIdentityManagement(filePath string, node *yaml.Node) (bool, IdentityManagement) {
	var result IdentityManagement
	success := true
	mType := cfgutil.RequireChildEnum(filePath, node, "type", IdentityManagementTypeOptions, &success)
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

			freeipaNode := cfgutil.RequireChild(sPath, secretsNode, "freeipa", &success)
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

	case IdentityManagementTypeOidc:
		if Mode == ServerModeServer {
			sPath, secretsNode := requireSecrets("OIDC identity management has secret configuration!")
			if secretsNode == nil {
				return false, result
			}

			oidcNode := cfgutil.RequireChild(sPath, secretsNode, "oidc", &success)
			if !success {
				return false, result
			}

			ok, config := parseIdentityManagementOidc(sPath, oidcNode)
			if !ok {
				return false, result
			}
			result.Configuration = &config
		} else {
			result.Configuration = &IdentityManagementOidc{}
		}
	}

	return success, result
}

func parseIdentityManagementScripted(filePath string, node *yaml.Node) (bool, IdentityManagementScripted) {
	var result IdentityManagementScripted
	success := true
	result.OnUserConnected = cfgutil.RequireChildFile(filePath, node, "onUserConnected", cfgutil.FileCheckRead, &success)
	result.OnProjectUpdated = cfgutil.RequireChildFile(filePath, node, "onProjectUpdated", cfgutil.FileCheckRead, &success)
	return success, result
}

func parseIdentityManagementFreeIpa(filePath string, node *yaml.Node) (bool, IdentityManagementFreeIPA) {
	var result IdentityManagementFreeIPA
	success := true

	result.Url = cfgutil.RequireChildText(filePath, node, "url", &success)

	verifyTls, hasVerifyTls := cfgutil.OptionalChildBool(filePath, node, "verifyTls")
	result.VerifyTls = verifyTls || !hasVerifyTls

	caCertFile := cfgutil.OptionalChildText(filePath, node, "caCertFile", &success)
	if caCertFile != "" {
		cfgutil.RequireChildFile(filePath, node, "caCertFile", cfgutil.FileCheckRead, &success)
		result.CaCertFile.Set(caCertFile)
	}

	result.Username = cfgutil.RequireChildText(filePath, node, "username", &success)
	result.Password = cfgutil.RequireChildText(filePath, node, "password", &success)
	groupName := cfgutil.OptionalChildText(filePath, node, "groupName", &success)
	if groupName != "" {
		result.GroupName = groupName
	} else {
		result.GroupName = "ucloud_users"
	}

	titleStrategy := cfgutil.OptionalChildText(filePath, node, "projectStrategy", &success)
	if titleStrategy != "" {
		switch titleStrategy {
		case "Default":
			result.ProjectStrategy = fnd.ProjectTitleDefault

		case "Date":
			result.ProjectStrategy = fnd.ProjectTitleDate
			result.ProjectPrefix = cfgutil.OptionalChildText(filePath, node, "projectPrefix", &success)
			if result.ProjectPrefix == "" {
				result.ProjectPrefix = "p"
			}

		case "UUID":
			result.ProjectStrategy = fnd.ProjectTitleUuid

		default:
			success = false
			badNode, _ := cfgutil.GetChildOrNil(filePath, node, "projectStrategy")
			cfgutil.ReportError(filePath, badNode, titleStrategy, "Unknown title strategy, use one of: 'Default', 'Date', 'UUID'")
		}
	}

	return success, result
}

func parseIdentityManagementOidc(filePath string, node *yaml.Node) (bool, IdentityManagementOidc) {
	var result IdentityManagementOidc
	success := true

	result.OnUserConnected = cfgutil.RequireChildFile(filePath, node, "onUserConnected", cfgutil.FileCheckRead, &success)
	result.Issuer = cfgutil.RequireChildText(filePath, node, "issuer", &success)
	result.ClientId = cfgutil.RequireChildText(filePath, node, "clientId", &success)
	result.ClientSecret = cfgutil.RequireChildText(filePath, node, "clientSecret", &success)
	result.ExpiresAfterMs = uint64(
		cfgutil.OptionalChildInt(filePath, node, "expiresAfterMs", &success).GetOrDefault(1000 * 60 * 60 * 24 * 7),
	)

	if child, err := cfgutil.GetChildOrNil(filePath, node, "scopes"); child != nil && err == nil {
		cfgutil.Decode(filePath, child, &result.Scopes, &success)
	}

	return success, result
}

func readPublicKey(configDir string) *rsa.PublicKey {
	keyFiles := []string{"ucloud_key.pub", "ucloud_crt.pem"}
	for _, key := range keyFiles {
		content, _ := os.ReadFile(filepath.Join(configDir, key))
		if content == nil {
			continue
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
	return nil
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
