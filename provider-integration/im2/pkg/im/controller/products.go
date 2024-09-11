package controller

import (
	"os"
	"ucloud.dk/pkg/apm"
	"ucloud.dk/pkg/log"
)

func RegisterProducts(products []apm.ProductV2) {
	existingCategories := map[string]apm.ProductCategory{}
	existingProducts := map[string]map[string]apm.ProductV2{}
	next := ""
	for {
		page, err := apm.BrowseProducts(next, apm.ProductsFilter{})
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
		oldCategory := existingCategories[categoryId]

		if newCategory.AccountingUnit != oldCategory.AccountingUnit {
			log.Error(
				"Product with category %v has changed accounting unit which is not allowed. Please create a new category instead.",
				categoryId,
			)
			os.Exit(1)
		}

		if newCategory.AccountingFrequency != oldCategory.AccountingFrequency {
			log.Error(
				"Product with category %v has changed accounting frequency which is not allowed. Please create a new category instead.",
				categoryId,
			)
			os.Exit(1)
		}

		if newCategory.FreeToUse != oldCategory.FreeToUse {
			log.Error(
				"Product with category %v has changed freeToUse flag which is not allowed. Please create a new category instead.",
				categoryId,
			)
			os.Exit(1)
		}

		var items []apm.ProductV2
		for _, item := range catItems {
			items = append(items, item)
		}
		err := apm.CreateProducts(items)

		if err != nil {
			log.Error("Failed to register products in UCloud/Core: %s", err)
			os.Exit(1)
		}
	}
}