package config

import (
	"fmt"
	"os"
	"strings"

	"gopkg.in/yaml.v3"
	"ucloud.dk/shared/pkg/cfgutil"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var Configuration *ConfigurationFormat

type ConfigurationFormat struct {
	RefreshToken string
	Database     Database
	SelfAddress  HostInfo
	SelfPublic   HostInfo

	TokenValidation struct {
		SharedSecret      string
		PublicCertificate string
		PrivateKey        string
	}

	OpenIdConnect util.Option[OidcAuthentication]

	Logs struct {
		Directory string
		Rotation  struct {
			Enabled               bool `yaml:"enabled"`
			RetentionPeriodInDays int  `yaml:"retentionPeriodInDays"`
		}
	}

	Emails struct {
		Enabled bool
	}

	Accounting struct {
		ComputeUnitsLowFundsNotificationLimitInCH int64 `yaml:"computeUnitsLowFundsNotificationLimitInCH"`
		StorageUnitsLowFundsNotificationLimitInGB int64 `yaml:"StorageUnitsLowFundsNotificationLimitInGB"`
	}

	ServiceLicenseAgreement struct {
		Version int
		Text    string
	}

	SlackHook util.Option[string]

	ElasticSearch util.Option[rpc.ElasticConfig]

	RequireMfa bool
}

type Database struct {
	Host     HostInfo
	Username string
	Password string
	Database string
	Ssl      bool
}

type OidcAuthentication struct {
	Issuer       string
	ClientId     string
	ClientSecret string
	Scopes       []string
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

func Parse(configDir string) bool {
	success := true

	filePath, document := cfgutil.ReadAndParse(configDir, "config")
	if document == nil {
		return false
	}

	cfg := &ConfigurationFormat{}
	Configuration = cfg

	// Parse simple properties
	cfg.RefreshToken = cfgutil.RequireChildText(filePath, document, "refreshToken", &success)

	addrNode := cfgutil.RequireChild(filePath, document, "selfAddress", &success)
	cfgutil.Decode(filePath, addrNode, &cfg.SelfAddress, &success)

	addrPublic, _ := cfgutil.GetChildOrNil(filePath, document, "selfPublic")
	if addrPublic != nil {
		cfgutil.Decode(filePath, addrPublic, &cfg.SelfPublic, &success)
	} else {
		cfg.SelfPublic = cfg.SelfAddress
	}

	cfg.RequireMfa, _ = cfgutil.OptionalChildBool(filePath, document, "requireMfa")

	elastic, ok := rpc.ElasticConfigRetrieve(filePath, document)
	if !ok {
		success = false
	} else {
		cfg.ElasticSearch = elastic
	}

	// Token validation
	{
		tokenValidation := cfgutil.RequireChild(filePath, document, "tokenValidation", &success)
		cfg.TokenValidation.SharedSecret = cfgutil.OptionalChildText(filePath, tokenValidation, "sharedSecret", &success)
		cfg.TokenValidation.PublicCertificate = cfgutil.OptionalChildText(filePath, tokenValidation, "certificate", &success)
		cfg.TokenValidation.PrivateKey = cfgutil.OptionalChildText(filePath, tokenValidation, "privateKey", &success)

		tokenValidationMethodCount := 0
		if cfg.TokenValidation.SharedSecret != "" {
			tokenValidationMethodCount++
		}
		if cfg.TokenValidation.PublicCertificate != "" {
			tokenValidationMethodCount++
		}

		if tokenValidationMethodCount != 1 {
			cfgutil.ReportError(filePath, tokenValidation, "Must specify exactly one token validation method")
			success = false
		}
	}

	// Parse the database section
	dbNode := cfgutil.RequireChild(filePath, document, "database", &success)
	hostNode := cfgutil.RequireChild(filePath, dbNode, "host", &success)
	cfgutil.Decode(filePath, hostNode, &cfg.Database.Host, &success)
	if cfg.Database.Host.Port == 0 {
		cfg.Database.Host.Port = 5432
	}

	cfg.Database.Username = cfgutil.RequireChildText(filePath, dbNode, "username", &success)
	cfg.Database.Password = cfgutil.RequireChildText(filePath, dbNode, "password", &success)
	cfg.Database.Database = cfgutil.RequireChildText(filePath, dbNode, "database", &success)
	cfg.Database.Ssl = cfgutil.RequireChildBool(filePath, dbNode, "ssl", &success)

	{
		// Logs section
		logs := cfgutil.RequireChild(filePath, document, "logs", &success)
		directory := cfgutil.RequireChildFolder(filePath, logs, "directory", cfgutil.FileCheckReadWrite, &success)
		cfg.Logs.Directory = directory
		if !success {
			return false
		}

		rotationNode, _ := cfgutil.GetChildOrNil(filePath, logs, "rotation")
		if rotationNode != nil {
			cfgutil.Decode(filePath, rotationNode, &cfg.Logs.Rotation, &success)
			if !success {
				return false
			}

			rotation := &cfg.Logs.Rotation
			if rotation.Enabled {
				if rotation.RetentionPeriodInDays <= 0 {
					cfgutil.ReportError(filePath, rotationNode, "retentionPeriodInDays must be specified and must be greater than zero")
					return false
				}
			}
		}
	}

	slackHook := cfgutil.OptionalChildText(filePath, document, "slackHook", &success)
	if slackHook != "" {
		cfg.SlackHook.Set(slackHook)
	}

	// SLA section
	sla, _ := cfgutil.GetChildOrNil(filePath, document, "serviceLicenseAgreement")
	if sla != nil {
		cfg.ServiceLicenseAgreement.Version = int(cfgutil.RequireChildInt(filePath, sla, "version", &success))

		path := cfgutil.RequireChildFile(filePath, sla, "path", cfgutil.FileCheckRead, &success)
		if success {
			text, err := os.ReadFile(path)
			if err != nil {
				cfgutil.ReportError(filePath, sla, "Could not read SLA text: %s", err)
				success = false
			} else {
				cfg.ServiceLicenseAgreement.Text = string(text)
			}
		}
	}

	oidc, _ := cfgutil.GetChildOrNil(filePath, document, "openIdConnect")
	if oidc != nil {
		ok, oidcConfig := parseOidcAuthentication(filePath, oidc)
		if !ok {
			success = false
		} else {
			cfg.OpenIdConnect.Set(oidcConfig)
		}
	}

	return success
}

func parseOidcAuthentication(filePath string, node *yaml.Node) (bool, OidcAuthentication) {
	var result OidcAuthentication
	success := true

	result.Issuer = cfgutil.RequireChildText(filePath, node, "issuer", &success)
	result.ClientId = cfgutil.RequireChildText(filePath, node, "clientId", &success)
	result.ClientSecret = cfgutil.RequireChildText(filePath, node, "clientSecret", &success)

	if child, err := cfgutil.GetChildOrNil(filePath, node, "scopes"); child != nil && err == nil {
		cfgutil.Decode(filePath, child, &result.Scopes, &success)
	}

	return success, result
}
