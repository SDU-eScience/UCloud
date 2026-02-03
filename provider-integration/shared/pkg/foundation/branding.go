package foundation

import (
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const brandingBaseContext = "branding"

type BrandingLink struct {
	Title string `json:"title"`
	Href  string `json:"href"`
}

type BrandingLoginPageType int

const (
	// BrandingLoginPageGeneric will use a more generic branding style. It will allow the use of the BrandingLoginPage
	// options. A UCloud logo will _always_ be present at the bottom of the login page.
	BrandingLoginPageGeneric BrandingLoginPageType = iota

	// BrandingLoginPageDeic will use the DeiC branding for the deployment. This will completely switch the theme,
	// and it will automatically enable a branded "WAYF" login button. None of the login page options are needed for
	// this profile.
	BrandingLoginPageDeic
)

type BrandingLoginPage struct {
	Type              BrandingLoginPageType `json:"type"`
	PrimaryLogoUrl    string                `json:"primaryLogoUrl"`
	SecondaryLogoUrls []string              `json:"secondaryLogoUrls"`
}

type Branding struct {
	DeploymentName string                    `json:"deploymentName"` // used for the login page and other places that might need it
	DataProtection util.Option[BrandingLink] `json:"dataProtection"` // used for the user menu
	StatusPage     util.Option[BrandingLink] `json:"statusPage"`     // used for the user menu
	Documentation  util.Option[BrandingLink] `json:"documentation"`  // default is our doc link
	SupportEmail   util.Option[string]       `json:"supportEmail"`   // used for the login page and support box
	FaqLink        util.Option[string]       `json:"faqLink"`        // used for the support box
	LoginPage      BrandingLoginPage         `json:"loginPage"`
}

var BrandingRetrieve = rpc.Call[util.Empty, Branding]{
	BaseContext: brandingBaseContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesPublic,
}
