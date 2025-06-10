package accounting

import (
	"slices"
	accapi "ucloud.dk/shared/pkg/accounting"
)

func resetProducts() {
	productsByProvider.Providers = nil
	productsByProvider.Buckets = make(map[string]*providerBucket)
}

func createTestProduct(product accapi.ProductV2) {
	productsByProvider.Mu.Lock()
	b, ok := productsByProvider.Buckets[product.Category.Provider]
	if !ok {
		productsByProvider.Providers = append(productsByProvider.Providers, product.Category.Provider)
		slices.Sort(productsByProvider.Providers)

		b = &providerBucket{}
		productsByProvider.Buckets[product.Category.Provider] = b
	}
	productsByProvider.Mu.Unlock()

	b.Mu.Lock()
	b.Products = append(b.Products, product)
	b.Mu.Unlock()
}
