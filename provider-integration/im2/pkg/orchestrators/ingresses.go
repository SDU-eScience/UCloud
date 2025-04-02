package orchestrators

import (
	"ucloud.dk/pkg/apm"
	c "ucloud.dk/pkg/client"
	fnd "ucloud.dk/pkg/foundation"
)

type Ingress struct {
	Resource
	Specification IngressSpecification `json:"specification"`
	Status        IngressStatus        `json:"status"`
	Updates       []IngressUpdate      `json:"updates"`
}

type IngressSupport struct {
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
	State IngressState `json:"state,omitempty"`
	ResourceUpdate
}

type IngressState string

const (
	IngressStatePreparing   IngressState = "PREPARING"
	IngressStateReady       IngressState = "READY"
	IngressStateUnavailable IngressState = "UNAVAILABLE"
)

// API
// =====================================================================================================================

const ingressesCtrlNamespace = "ingresses.control."
const ingressesCtrlContext = "/api/ingresses/control/"

type BrowseIngressesFlags struct {
	IncludeProduct bool `json:"includeProduct"`
	IncludeUpdates bool `json:"includeUpdates"`
}

func BrowseIngresses(next string, flags BrowseIngressesFlags) (fnd.PageV2[Ingress], error) {
	return c.ApiBrowse[fnd.PageV2[Ingress]](
		ingressesCtrlNamespace+"browse",
		ingressesCtrlContext,
		"",
		append([]string{"next", next}, c.StructToParameters(flags)...),
	)
}
