package config

import (
	"fmt"
	"gopkg.in/yaml.v3"
	"strings"
	"ucloud.dk/shared/pkg/cfgutil"
)

var Configuration *ConfigurationFormat

type ConfigurationFormat struct {
	RefreshToken    string
	Database        Database
	TokenValidation struct {
		SharedSecret      string
		PublicCertificate string
	}
	Logs struct {
		Directory string
		Rotation  struct {
			Enabled               bool `yaml:"enabled"`
			RetentionPeriodInDays int  `yaml:"retentionPeriodInDays"`
		}
	}
}

type Database struct {
	Host     HostInfo
	Username string
	Password string
	Database string
	Ssl      bool
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

	// Token validation
	{
		tokenValidation := cfgutil.RequireChild(filePath, document, "tokenValidation", &success)
		cfg.TokenValidation.SharedSecret = cfgutil.OptionalChildText(filePath, tokenValidation, "sharedSecret", &success)
		cfg.TokenValidation.PublicCertificate = cfgutil.OptionalChildText(filePath, tokenValidation, "certificate", &success)

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

	return success
}
