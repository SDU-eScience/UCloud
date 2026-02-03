package orchestrators

import (
	apm "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const providerBrandingBaseContext = "providers/branding"

type ProviderBranding struct {
	Id                 string                               `json:"id"`
	Title              string                               `json:"title"`
	ShortTitle         string                               `json:"shortTitle"`
	ShortDescription   string                               `json:"shortDescription"`
	Description        string                               `json:"description"`
	Url                string                               `json:"url"`
	Sections           []ProviderBrandingSection            `json:"sections"` // renamed from texts
	ProductDescription []ProviderBrandingProductDescription `json:"productDescription"`
}

type ProviderBrandingSection struct {
	Description string              `json:"description"`
	Image       util.Option[string] `json:"image"`
}

type ProviderBrandingProductDescription struct {
	Category         apm.ProductCategoryIdV2 `json:"category"`
	ShortDescription string                  `json:"shortDescription"` // shown in grant applications
	Section          ProviderBrandingSection `json:"section"`          // shown on provider brand page and SKU page
}

type ProviderBrandingBrowseResponse struct {
	Providers map[string]ProviderBranding `json:"providers"`
}

var ProviderBrandingBrowse = rpc.Call[util.Empty, ProviderBrandingBrowseResponse]{
	BaseContext: providerBrandingBaseContext,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesPublic,
}
