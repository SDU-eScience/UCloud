package orchestrator

import (
	"time"
	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/util"
)

var productCache = util.NewCache[string, []accapi.ProductV2](1 * time.Minute)

func productsByProvider(providerId string) []accapi.ProductV2 {
	products, _ := productCache.Get(providerId, func() ([]accapi.ProductV2, error) {
		var result []accapi.ProductV2
		next := util.OptNone[string]()
		for {
			products, err := accapi.ProductsBrowse.Invoke(accapi.ProductsBrowseRequest{
				ItemsPerPage: 1000,
				Next:         next,
				ProductsFilter: accapi.ProductsFilter{
					FilterProvider: util.OptValue(providerId),
				},
			})

			if err != nil {
				return nil, err
			}

			result = append(result, products.Items...)
			next = products.Next
			if !next.Present {
				break
			}
		}

		return result, nil
	})

	return products
}

func productFromCache(ref accapi.ProductReference) (accapi.ProductV2, bool) {
	allProducts := productsByProvider(ref.Provider)
	for _, product := range allProducts {
		if product.Name == ref.Id && product.Category.Name == ref.Category {
			return product, true
		}
	}
	return accapi.ProductV2{}, false
}
