package orchestrators

import (
	"ucloud.dk/pkg/apm"
	c "ucloud.dk/pkg/client"
	fnd "ucloud.dk/pkg/foundation"
	"ucloud.dk/pkg/util"
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

// API
// =====================================================================================================================

const ingressCtrlNamespace = "ingresses.control."
const ingressCtrlContext = "/api/ingresses/control/"

type BrowseIngressesFlags struct {
	IncludeProduct bool `json:"includeProduct"`
	IncludeUpdates bool `json:"includeUpdates"`
}

func BrowseIngresses(next string, flags BrowseIngressesFlags) (fnd.PageV2[Ingress], error) {
	return c.ApiBrowse[fnd.PageV2[Ingress]](
		ingressCtrlNamespace+"browse",
		ingressCtrlContext,
		"",
		append([]string{"next", next}, c.StructToParameters(flags)...),
	)
}

func UpdateIngresses(request fnd.BulkRequest[ResourceUpdateAndId[IngressUpdate]]) error {
	_, err := c.ApiUpdate[util.Empty](
		ingressCtrlNamespace+"update",
		ingressCtrlContext,
		"update",
		request,
	)
	return err
}
