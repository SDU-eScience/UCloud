package foundation

import (
	"os"

	cfg "ucloud.dk/core/pkg/config"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func loadImage(path string) []byte {
	b, err := os.ReadFile(path)
	if err != nil {
		log.Error("Failed to load image: %s", err)
		return nil
	}
	return b
}
func toBrandingApi(b *cfg.Branding) fndapi.Branding {

	return fndapi.Branding{
		DeploymentName: b.DeploymentName,
		DataProtection: util.OptValue(toBrandingLinkApi(&b.DataProtection.Value)),
		StatusPage:     util.OptValue(toBrandingLinkApi(&b.StatusPage.Value)),
		Documentation:  util.OptValue(toBrandingLinkApi(&b.Documentation.Value)),
		SupportEmail:   b.SupportEmail,
		FaqLink:        b.FaqLink,
		LoginPage:      toBrandingLoginPageApi(&b.LoginPage),
	}

}

func toBrandingLoginPageApi(blp *cfg.BrandingLoginPage) fndapi.BrandingLoginPage {
	return fndapi.BrandingLoginPage{
		Type:              fndapi.BrandingLoginPageType(blp.Type),
		PrimaryLogoUrl:    blp.PrimaryLogoUrl,
		SecondaryLogoUrls: blp.SecondaryLogoUrls,
	}
}

func toBrandingLinkApi(bl *cfg.BrandingLink) fndapi.BrandingLink {
	if bl == nil {
		return fndapi.BrandingLink{}
	}
	return fndapi.BrandingLink{
		Title: bl.Title,
		Href:  bl.Href,
	}

}

func initBranding() {
	fndapi.BrandingRetrieve.Handler(func(info rpc.RequestInfo, request util.Empty) (fndapi.Branding, *util.HttpError) {
		b := cfg.Configuration.Branding
		return toBrandingApi(&b), nil
	})

	fndapi.BrandingRetrieveImage.Handler(func(info rpc.RequestInfo, request fndapi.BrandingImageRequest) ([]byte, *util.HttpError) {
		config := cfg.Configuration
		absolutePath, ok := config.BrandingImageAbsolutePath[request.Name]
		if !ok {
			return nil, nil
		}
		return loadImage(absolutePath), nil
	})
}
