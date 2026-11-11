package controller

import (
	"encoding/json"
	"os"
	"sync"

	cfg "ucloud.dk/pkg/config"
	apm "ucloud.dk/shared/pkg/accounting"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

var productCategoriesMu sync.RWMutex
var productCategories = map[string]apm.ProductCategory{}

func productCategoryStore(category apm.ProductCategory) {
	productCategoriesMu.Lock()
	productCategories[category.Name] = category
	productCategoriesMu.Unlock()
}

func ProductCategoryAccountingUnitBytes(name string) int64 {
	productCategoriesMu.RLock()
	category, ok := productCategories[name]
	productCategoriesMu.RUnlock()
	if !ok {
		return 1_000_000_000
	}

	switch category.AccountingUnit.Name {
	case "B":
		return 1
	case "KB":
		return 1_000
	case "MB":
		return 1_000_000
	case "GB":
		return 1_000_000_000
	case "TB":
		return 1_000_000_000_000
	case "PB":
		return 1_000_000_000_000_000
	case "EB":
		return 1_000_000_000_000_000_000
	default:
		return 1_000_000_000
	}
}

func ProductsRegister(products []apm.ProductV2) {
	existingCategories := map[string]apm.ProductCategory{}
	existingProducts := map[string]map[string]apm.ProductV2{}
	next := ""
	for {
		page, err := apm.ProductsBrowse.Invoke(apm.ProductsBrowseRequest{
			Next: util.OptStringIfNotEmpty(next),
			ProductsFilter: apm.ProductsFilter{
				FilterProvider: util.OptValue(cfg.Provider.Id),
			},
		})
		if err != nil {
			log.Error("Unable to fetch products from UCloud: %v", err)
			os.Exit(1)
		}

		for _, p := range page.Items {
			category, ok := existingProducts[p.Category.Name]
			if !ok {
				category = map[string]apm.ProductV2{}
			}

			category[p.Name] = p
			existingProducts[p.Category.Name] = category

			existingCategories[p.Category.Name] = p.Category
			productCategoryStore(p.Category)
		}

		if page.Next.IsEmpty() {
			break
		}
		next = page.Next.Get()
	}

	newCategories := map[string]apm.ProductCategory{}
	newProducts := map[string]map[string]apm.ProductV2{}
	for _, p := range products {
		category, ok := newProducts[p.Category.Name]
		if !ok {
			category = map[string]apm.ProductV2{}
		}

		category[p.Name] = p
		newProducts[p.Category.Name] = category

		newCategories[p.Category.Name] = p.Category
	}

	for categoryId, catItems := range newProducts {
		newCategory := newCategories[categoryId]
		oldCategory, exists := existingCategories[categoryId]
		if exists {
			newJson, _ := json.Marshal(newCategories)
			oldJson, _ := json.Marshal(oldCategory)
			if newCategory.AccountingUnit != oldCategory.AccountingUnit {
				log.Error(
					"Product with category %v has changed accounting unit which is not allowed. Please create a new category instead.",
					categoryId,
				)
				log.Error("New: %v", string(newJson))
				log.Error("Old: %v", string(oldJson))
				os.Exit(1)
			}

			if newCategory.AccountingFrequency != oldCategory.AccountingFrequency {
				log.Error(
					"Product with category %v has changed accounting frequency which is not allowed. Please create a new category instead.",
					categoryId,
				)
				log.Error("New: %v", string(newJson))
				log.Error("Old: %v", string(oldJson))
				os.Exit(1)
			}

			if newCategory.FreeToUse != oldCategory.FreeToUse {
				log.Error(
					"Product with category %v has changed freeToUse flag which is not allowed. Please create a new category instead.",
					categoryId,
				)
				log.Error("New: %v", string(newJson))
				log.Error("Old: %v", string(oldJson))
				os.Exit(1)
			}
		}

		productCategoryStore(newCategory)

		var items []apm.ProductV2
		for _, item := range catItems {
			items = append(items, item)
		}
		_, err := apm.ProductsCreate.Invoke(fnd.BulkRequest[apm.ProductV2]{Items: items})

		if err != nil {
			log.Error("Failed to register products in UCloud/Core: %s", err)
			os.Exit(1)
		}
	}
}
