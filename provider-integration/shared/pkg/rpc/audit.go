package rpc

import (
	"encoding/json"
	time "time"

	"gopkg.in/yaml.v3"
	"ucloud.dk/shared/pkg/cfgutil"
	"ucloud.dk/shared/pkg/util"
)

var AuditConsumer func(event HttpCallLogEntry)

type ElasticConfig struct {
	Address     string `yaml:"address"`
	Port        int    `yaml:"port"`
	Scheme      string `yaml:"scheme"`
	Credentials struct {
		Username string `yaml:"username"`
		Password string `yaml:"password"`
	}
	GatherNode bool `yaml:"gatherNode"`
}

func ElasticConfigRetrieve(filePath string, document *yaml.Node) (util.Option[ElasticConfig], bool) {
	success := true
	elasticNode, _ := cfgutil.GetChildOrNil(filePath, document, "elasticsearch")
	var elasticConfig ElasticConfig
	if elasticNode != nil {
		elasticCredNode := cfgutil.RequireChild(filePath, elasticNode, "credentials", &success)
		cfgutil.Decode(filePath, elasticNode, &elasticConfig, &success)

		if elasticConfig.Port <= 0 || elasticConfig.Port >= 1024*64 {
			success = false
		}
		elasticConfig.Credentials.Username = cfgutil.RequireChildText(filePath, elasticCredNode, "username", &success)
		elasticConfig.Credentials.Password = cfgutil.RequireChildText(filePath, elasticCredNode, "password", &success)

		if success {
			return util.OptValue(elasticConfig), true
		} else {
			return util.OptNone[ElasticConfig](), false
		}
	}
	return util.OptNone[ElasticConfig](), true
}

type ServiceInstance struct {
	Definition ServiceDefinition   `json:"definition"`
	Hostname   string              `json:"hostname"`
	Port       int                 `json:"port"`
	IpAddress  util.Option[string] `json:"ipAddress"`
}

type ServiceDefinition struct {
	Name    string `json:"name"`
	Version string `json:"version"`
}

type HttpCallLogEntry struct {
	JobId             string                              `json:"jobId"`
	HandledBy         ServiceInstance                     `json:"handledBy"` // dummy value/not set
	CausedBy          util.Option[string]                 `json:"causedBy"`  // not set
	RequestName       string                              `json:"requestName"`
	UserAgent         util.Option[string]                 `json:"userAgent"`
	RemoteOrigin      string                              `json:"remoteOrigin"`
	Token             util.Option[SecurityPrincipalToken] `json:"token"`
	RequestSize       uint64                              `json:"requestSize"`
	RequestJson       util.Option[json.RawMessage]        `json:"requestJson"`
	ResponseCode      int                                 `json:"responseCode"`
	ResponseTime      uint64                              `json:"responseTime"`
	ResponseTimeNanos uint64                              `json:"responseTimeNanos"`
	Expiry            uint64                              `json:"expiry"`
	Project           util.Option[string]                 `json:"project"`
	ReceivedAt        time.Time                           `json:"receivedAt"`
}

type SecurityPrincipalToken struct {
	Principal              SecurityPrincipal   `json:"principal"`
	Scopes                 []string            `json:"scopes"` // not set
	IssuedAt               uint64              `json:"issuedAt"`
	ExpiresAt              uint64              `json:"expiresAt"`
	PublicSessionReference util.Option[string] `json:"publicSessionReference"`
	ExtendedBy             util.Option[string] `json:"extendedBy"`      // not set
	ExtendedByChain        []string            `json:"extendedByChain"` // not set
}

type SecurityPrincipal struct {
	Username                 string              `json:"username"`
	Role                     string              `json:"role"`
	FirstName                string              `json:"firstName"`                // not set
	LastName                 string              `json:"lastName"`                 // not set
	Email                    util.Option[string] `json:"email"`                    // not set
	TwoFactorAuthentication  bool                `json:"twoFactorAuthentication"`  // not set
	PrincipalType            util.Option[string] `json:"principalType"`            // not set
	ServiceAgreementAccepted bool                `json:"serviceAgreementAccepted"` // not set
	Organization             util.Option[string] `json:"organization"`             // not set
}
