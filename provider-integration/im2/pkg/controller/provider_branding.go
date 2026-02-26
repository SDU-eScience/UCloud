package controller

import (
	"os"

	cfg "ucloud.dk/pkg/config"
	apm "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func loadImage(path string) []byte {
	b, err := os.ReadFile(path)
	if err != nil {
		log.Error("Failed load image file: %s", err)
		return nil
	}
	return b
}

func initProviderBranding() {
	if RunsServerCode() {

		orcapi.ProviderBrandingRetrieve.Handler(func(info rpc.RequestInfo, request util.Empty) (orcapi.ProviderBranding, *util.HttpError) {
			pb := cfg.Provider.ProviderBranding
			providerBranding := orcapi.ProviderBranding{
				Title:            pb.Title,
				ShortTitle:       pb.ShortTitle,
				ShortDescription: pb.ShortDescription,
				Description:      pb.Description,
			}
			for _, section := range pb.Sections {
				providerBranding.Sections = append(providerBranding.Sections, orcapi.ProviderBrandingSection{
					Description: section.Description,
					Image:       section.Image,
				})
			}

			for _, prodDescription := range pb.ProductDescription {
				providerBranding.ProductDescription = append(providerBranding.ProductDescription, orcapi.ProviderBrandingProductDescription{
					ShortDescription: prodDescription.ShortDescription,
					Section:          orcapi.ProviderBrandingSection{Description: prodDescription.Section.Description, Image: prodDescription.Section.Image},
					Category:         apm.ProductCategoryIdV2{Name: prodDescription.Category.Name, Provider: prodDescription.Category.Provider},
				})
			}
			return providerBranding, nil
		})

		orcapi.ProviderBrandingRetrieveImage.Handler(func(info rpc.RequestInfo, request orcapi.ProviderBrandingImageRequest) ([]byte, *util.HttpError) {

			log.Error("%+v", request)
			log.Error("Failed load image file: %+v", info.HttpRequest)
			return loadImage(request.Name), nil
		})
	}
}
