package orchestrators

import (
	"ucloud.dk/pkg/apm"
)

type Ingress struct {
	Resource
	Specification IngressSpecification `json:"specification"`
	Status        IngressStatus        `json:"status"`
	Updates       []IngressUpdate      `json:"updates"`
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
	State IngressState `json:"state,omitempty"`
	ResourceUpdate
}

type IngressState string

const (
	IngressStatePreparing   IngressState = "PREPARING"
	IngressStateReady       IngressState = "READY"
	IngressStateUnavailable IngressState = "UNAVAILABLE"
)
