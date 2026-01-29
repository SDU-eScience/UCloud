package rpc

import (
	"encoding/json"
	time "time"

	"ucloud.dk/shared/pkg/util"
)

var AuditConsumer func(event HttpCallLogEntry)

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
