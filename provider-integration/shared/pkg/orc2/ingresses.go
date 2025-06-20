package orchestrators

import (
	"ucloud.dk/shared/pkg/apm"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/util"
)

type Ingress struct {
	Resource
	Specification IngressSpecification `json:"specification"`
	Status        IngressStatus        `json:"status"`
	Updates       []IngressUpdate      `json:"updates"`
}

type IngressSupport struct {
	Prefix  string               `json:"domainPrefix"`
	Suffix  string               `json:"domainSuffix"`
	Product apm.ProductReference `json:"product"`
}

type IngressSpecification struct {
	Domain  string               `json:"domain"`
	Product apm.ProductReference `json:"product"`
	ResourceSpecification
}

type IngressStatus struct {
	BoundTo []string     `json:"boundTo"`
	State   IngressState `json:"state"`
}

type IngressUpdate struct {
	State     util.Option[IngressState] `json:"state,omitempty"`
	Timestamp fnd.Timestamp             `json:"timestamp"`
	Status    util.Option[string]       `json:"status,omitempty"`
}

type IngressState string

const (
	IngressStatePreparing   IngressState = "PREPARING"
	IngressStateReady       IngressState = "READY"
	IngressStateUnavailable IngressState = "UNAVAILABLE"
)
