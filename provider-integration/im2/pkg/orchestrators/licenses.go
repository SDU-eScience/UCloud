package orchestrators

import (
	"ucloud.dk/pkg/apm"
	c "ucloud.dk/pkg/client"
	fnd "ucloud.dk/pkg/foundation"
)

type LicenseServer struct {
	Resource
	Specification LicenseServerSpecification `json:"specification"`
	Status        LicenseServerStatus        `json:"status"`
	Updates       []LicenseServerUpdate      `json:"updates,omitempty"`
}

type LicenseServerSpecification struct {
	Product apm.ProductReference `json:"product"`
	Address string
	Port    int
	License string
	ResourceSpecification
}

type LicenseServerStatus struct {
	State LicenseServerState `json:"state"`
}

type LicenseServerState string

type LicenseServerUpdate struct {
	State LicenseServerState `json:"state,omitempty"`
	ResourceUpdate
}

const (
	LicenseServerStatePreparing   PublicIpState = "PREPARING"
	LicenseServerStateReady       PublicIpState = "READY"
	LicenseServerStateUnavailable PublicIpState = "UNAVAILABLE"
)

type LicenseServerSupport struct {
	Product apm.ProductReference `json:"product"`
}

// API
// =====================================================================================================================

const licenseCtrlNamespace = "licenses.control."
const licenseCtrlContext = "/api/licenses/control/"

type BrowseLicensesFlags struct {
	IncludeProduct bool `json:"includeProduct"`
	IncludeUpdates bool `json:"includeUpdates"`
}

/*
	func RetrievePublicIp(jobId string, flags BrowseIpsFlags) (PublicIp, error) {
		return c.ApiRetrieve[PublicIp](
			ipsCtrlNamespace+"retrieve",
			ipsCtrlContext,
			"",
			append([]string{"id", jobId}, c.StructToParameters(flags)...),
		)
	}
*/
func BrowseLicenses(next string, flags BrowseLicensesFlags) (fnd.PageV2[LicenseServer], error) {
	return c.ApiBrowse[fnd.PageV2[LicenseServer]](
		licenseCtrlNamespace+"browse",
		licenseCtrlContext,
		"",
		append([]string{"next", next}, c.StructToParameters(flags)...),
	)
}

/*
func UpdatePublicIps(request fnd.BulkRequest[ResourceUpdateAndId[PublicIpUpdate]]) error {
	_, err := c.ApiUpdate[util.Empty](
		ipsCtrlNamespace+"update",
		ipsCtrlContext,
		"update",
		request,
	)
	return err
}
*/
