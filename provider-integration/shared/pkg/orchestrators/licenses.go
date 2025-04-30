package orchestrators

import (
	"ucloud.dk/shared/pkg/apm"
	c "ucloud.dk/shared/pkg/client"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/util"
)

type License struct {
	Resource
	Specification LicenseSpecification `json:"specification"`
	Status        LicenseStatus        `json:"status"`
	Updates       []LicenseUpdate      `json:"updates,omitempty"`
}

type LicenseSpecification struct {
	Product apm.ProductReference `json:"product"`
}

type LicenseStatus struct {
	State   LicenseState `json:"state"`
	BoundTo []string     `json:"boundTo"`
}

type LicenseUpdate struct {
	Timestamp fnd.Timestamp             `json:"timestamp"`
	State     util.Option[LicenseState] `json:"state,omitempty"`
	Binding   util.Option[JobBinding]   `json:"binding"`
	Status    util.Option[string]       `json:"status"`
}

type LicenseState string

const (
	LicenseStatePreparing   LicenseState = "PREPARING"
	LicenseStateReady       LicenseState = "READY"
	LicenseStateUnavailable LicenseState = "UNAVAILABLE"
)

type LicenseSupport struct {
	Product apm.ProductReference `json:"product"`
}

// API
// =====================================================================================================================

const licenseCtrlNamespace = "licenses.control."
const licenseCtrlContext = "/api/licenses/control/"

type LicenseIncludeFlags struct {
	IncludeProduct bool `json:"includeProduct"`
	IncludeUpdates bool `json:"includeUpdates"`
	IncludeSupport bool `json:"includeSupport"`
}

func BrowseLicenses(next string, flags LicenseIncludeFlags) (fnd.PageV2[License], error) {
	return c.ApiBrowse[fnd.PageV2[License]](
		licenseCtrlNamespace+"browse",
		licenseCtrlContext,
		"",
		append([]string{"next", next}, c.StructToParameters(flags)...),
	)
}

func UpdateLicenses(request fnd.BulkRequest[ResourceUpdateAndId[LicenseUpdate]]) error {
	_, err := c.ApiUpdate[util.Empty](
		licenseCtrlNamespace+"update",
		licenseCtrlContext,
		"update",
		request,
	)
	return err
}
