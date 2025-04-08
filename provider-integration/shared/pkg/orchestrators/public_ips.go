package orchestrators

import (
	"ucloud.dk/shared/pkg/apm"
	c "ucloud.dk/shared/pkg/client"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/util"
)

type PublicIPSpecification struct {
	Product  apm.ProductReference  `json:"product"`
	Firewall util.Option[Firewall] `json:"firewall"`
}

type Firewall struct {
	OpenPorts []PortRangeAndProto `json:"openPorts"`
}

type FirewallAndIp struct {
	Ip       PublicIp `json:"networkIp"`
	Firewall Firewall `json:"firewall"`
}

type PortRangeAndProto struct {
	Start    int        `json:"start"`
	End      int        `json:"end"`
	Protocol IpProtocol `json:"protocol"`
}

type IpProtocol string

const (
	IpProtocolTcp IpProtocol = "TCP"
	IpProtocolUdp IpProtocol = "UDP"
)

type PublicIpUpdate struct {
	State           util.Option[PublicIpState] `json:"state"`
	ChangeIpAddress util.Option[bool]          `json:"changeIpAddress"`
	NewIpAddress    util.Option[string]        `json:"newIpAddress"`
	Timestamp       fnd.Timestamp              `json:"timestamp"`
	Binding         util.Option[JobBinding]    `json:"binding"`
}

type PublicIpState string

const (
	PublicIpStatePreparing   PublicIpState = "PREPARING"
	PublicIpStateReady       PublicIpState = "READY"
	PublicIpStateUnavailable PublicIpState = "UNAVAILABLE"
)

type JobBinding struct {
	Kind JobBindingKind `json:"kind"`
	Job  string         `json:"job"`
}

type JobBindingKind string

const (
	JobBindingKindBind   JobBindingKind = "BIND"
	JobBindingKindUnbind JobBindingKind = "UNBIND"
)

type PublicIpStatus struct {
	State     PublicIpState       `json:"state"`
	BoundTo   []string            `json:"boundTo"`
	IpAddress util.Option[string] `json:"ipAddress"`
}

type PublicIp struct {
	Resource
	Specification PublicIPSpecification `json:"specification"`
	Status        PublicIpStatus        `json:"status"`
	Updates       []PublicIpUpdate      `json:"updates,omitempty"`
}

type PublicIpSupport struct {
	Product  apm.ProductReference `json:"product"`
	Firewall FirewallSupport      `json:"firewall"`
}

type FirewallSupport struct {
	Enabled bool `json:"enabled"`
}

// API
// =====================================================================================================================

const ipsCtrlNamespace = "networkips.control."
const ipsCtrlContext = "/api/networkips/control/"

type BrowseIpsFlags struct {
	IncludeProduct bool `json:"includeProduct"`
	IncludeUpdates bool `json:"includeUpdates"`
}

func RetrievePublicIp(jobId string, flags BrowseIpsFlags) (PublicIp, error) {
	return c.ApiRetrieve[PublicIp](
		ipsCtrlNamespace+"retrieve",
		ipsCtrlContext,
		"",
		append([]string{"id", jobId}, c.StructToParameters(flags)...),
	)
}

func BrowsePublicIps(next string, flags BrowseIpsFlags) (fnd.PageV2[PublicIp], error) {
	return c.ApiBrowse[fnd.PageV2[PublicIp]](
		ipsCtrlNamespace+"browse",
		ipsCtrlContext,
		"",
		append([]string{"next", next}, c.StructToParameters(flags)...),
	)
}

func UpdatePublicIps(request fnd.BulkRequest[ResourceUpdateAndId[PublicIpUpdate]]) error {
	_, err := c.ApiUpdate[util.Empty](
		ipsCtrlNamespace+"update",
		ipsCtrlContext,
		"update",
		request,
	)
	return err
}
