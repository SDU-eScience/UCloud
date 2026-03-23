package controller

import (
	"os"

	cfg "ucloud.dk/pkg/config"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
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

func initProviderBranding() {
	if RunsServerCode() {
		orcapi.ProviderBrandingRetrieve.Handler(func(info rpc.RequestInfo, request util.Empty) (orcapi.ProviderBranding, *util.HttpError) {
			pb := cfg.Provider.ProviderBranding

			providerBranding := orcapi.ProviderBranding{
				Id:               cfg.Provider.Id,
				Title:            pb.Title,
				ShortTitle:       pb.ShortTitle,
				ShortDescription: pb.ShortDescription,
				Description:      pb.DescriptionFilePath,
				Logo:             pb.Logo,
				Url:              pb.Url,
			}
		
			providerBranding.Sections = make([]orcapi.ProviderBrandingSection, 0)
			providerBranding.ProductDescription = make([]orcapi.ProviderBrandingProductDescription, 0)

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
					Category:         prodDescription.Category,
				})
			}
			return providerBranding, nil
		})

		// Returning the bytes of the image, if it fails, we will return no bytes
		orcapi.ProviderBrandingRetrieveImage.Handler(func(info rpc.RequestInfo, request orcapi.ProviderBrandingImageRequest) ([]byte, *util.HttpError) {
			providerConfig := cfg.Provider
			absolutePath, ok := providerConfig.ProviderBrandingImageAbsolutePath[request.Name]
			if !ok {
				return nil, nil
			}
			return loadImage(absolutePath), nil
		})
	}
}
