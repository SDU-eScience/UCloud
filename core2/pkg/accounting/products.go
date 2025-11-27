package accounting

import (
	"database/sql"
	"fmt"
	"net/http"
	"slices"
	"strings"
	"sync"

	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var productsByProvider = struct {
	Mu        sync.RWMutex
	Providers []string // sorted keys stored present in AppBuckets
	Buckets   map[string]*providerBucket
}{}

type providerBucket struct {
	Mu       sync.RWMutex
	Products []accapi.ProductV2
}

func initProducts() {
	productsLoad()

	accapi.ProductsRetrieve.Handler(func(info rpc.RequestInfo, request accapi.ProductsFilter) (accapi.ProductV2, *util.HttpError) {
		return ProductRetrieve(info.Actor, request)
	})

	accapi.ProductsBrowse.Handler(func(info rpc.RequestInfo, request accapi.ProductsBrowseRequest) (fndapi.PageV2[accapi.ProductV2], *util.HttpError) {
		return ProductBrowse(info.Actor, request)
	})

	accapi.ProductsCreate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[accapi.ProductV2]) (util.Empty, *util.HttpError) {
		err := ProductCreate(info.Actor, request.Items)
		return util.Empty{}, err
	})
}

func ProductRetrieve(actor rpc.Actor, filter accapi.ProductsFilter) (accapi.ProductV2, *util.HttpError) {
	name := filter.FilterName.GetOrDefault("")
	category := filter.FilterCategory.GetOrDefault("")
	provider := filter.FilterProvider.GetOrDefault("")

	if name == "" || category == "" || provider == "" {
		return accapi.ProductV2{}, util.HttpErr(http.StatusBadRequest, "name, category and provider must be supplied")
	}

	productsByProvider.Mu.RLock()
	bucket, ok := productsByProvider.Buckets[provider]
	productsByProvider.Mu.RUnlock()

	if !ok {
		return accapi.ProductV2{}, util.HttpErr(http.StatusNotFound, "unknown product")
	}

	result := accapi.ProductV2{}
	ok = false
	bucket.Mu.RLock()
	for _, p := range bucket.Products {
		if productFilterApplies(p, filter) {
			result = p
			ok = true
			break
		}
	}
	bucket.Mu.RUnlock()

	if ok {
		products := productsPostProcess(actor, []accapi.ProductV2{result}, filter)
		if len(products) == 1 {
			return products[0], nil
		}
	}

	return accapi.ProductV2{}, util.HttpErr(http.StatusNotFound, "unknown product")
}

func ProductBrowse(actor rpc.Actor, request accapi.ProductsBrowseRequest) (fndapi.PageV2[accapi.ProductV2], *util.HttpError) {
	var nextProvider, nextCategory, nextName string
	var items []accapi.ProductV2
	itemsPerPage := fndapi.ItemsPerPage(request.ItemsPerPage)

	hasCursor := false
	if request.Next.Present {
		split := strings.SplitN(request.Next.Value, "@", 3)
		if len(split) == 3 {
			nextProvider = split[0]
			nextCategory = split[1]
			nextName = split[2]
			hasCursor = true
		}
	}

	productsByProvider.Mu.RLock()
	for _, providerId := range productsByProvider.Providers {
		if len(items) >= itemsPerPage {
			break
		}

		if request.FilterProvider.Present && request.FilterProvider.Value != providerId {
			continue
		}

		// If we have a cursor, skip all providers strictly before nextProvider.
		if hasCursor && strings.Compare(providerId, nextProvider) < 0 {
			continue
		}

		bucket, ok := productsByProvider.Buckets[providerId]
		if !ok {
			continue
		}

		bucket.Mu.RLock()
		for _, p := range bucket.Products {
			if len(items) >= itemsPerPage {
				break
			}

			if hasCursor && providerId == nextProvider {
				// Same provider as the cursor; advance strictly past (nextCategory, nextName)
				catCmp := strings.Compare(p.Category.Name, nextCategory)
				if catCmp < 0 || (catCmp == 0 && strings.Compare(p.Name, nextName) <= 0) {
					continue
				}
			}

			if productFilterApplies(p, request.ProductsFilter) {
				items = append(items, p)
			}
		}
		bucket.Mu.RUnlock()
	}
	productsByProvider.Mu.RUnlock()

	result := fndapi.PageV2[accapi.ProductV2]{
		ItemsPerPage: itemsPerPage,
	}

	if len(items) == itemsPerPage {
		last := items[len(items)-1]
		result.Next.Set(fmt.Sprintf("%s@%s@%s", last.Category.Provider, last.Category.Name, last.Name))

		if result.Next.Value == request.Next.Value {
			log.Warn("productBrowse invariant error")
			result.Next.Clear()
		}
	}

	result.Items = productsPostProcess(actor, items, request.ProductsFilter)
	if len(result.Items) == 0 && result.Next.Present {
		// All items were removed by the filter, but more items could be found. Try to find them from the next page.
		request.Next = result.Next
		return ProductBrowse(actor, request)
	}

	return result, nil
}

func ProductCreate(actor rpc.Actor, products []accapi.ProductV2) *util.HttpError {
	if actor.Username != rpc.ActorSystem.Username {
		if actor.Role != rpc.RoleProvider {
			return util.HttpErr(http.StatusForbidden, "forbidden")
		}

		providerId, ok := strings.CutPrefix(actor.Username, fndapi.ProviderSubjectPrefix)
		if !ok {
			return util.HttpErr(http.StatusForbidden, "forbidden")
		}

		for _, p := range products {
			if p.Category.Provider != providerId {
				return util.HttpErr(http.StatusForbidden, "forbidden")
			}
		}
	} else {
		if len(products) > 0 {
			providerId := products[0].Category.Provider
			for _, p := range products {
				if p.Category.Provider != providerId {
					return util.HttpErr(http.StatusForbidden, "forbidden")
				}
			}
		}
	}

	if len(products) == 0 {
		return util.HttpErr(http.StatusBadRequest, "no products supplied")
	}

	providerId := products[0].Category.Provider

	bucket := util.ReadOrInsertBucket(&productsByProvider.Mu, productsByProvider.Buckets, providerId, nil)

	bucket.Mu.Lock()

	var err *util.HttpError

	// NOTE(Dan): Constraints on the products are largely implemented by the database. As a result, we just fire off
	// the query and see if we succeed. If we do, we can start replacing products.
	db.NewTx0(func(tx *db.Transaction) {
		for _, item := range products {
			db.Exec(
				tx,
				`
					with
						acinsert as (
							insert into accounting.accounting_units 
								(name, name_plural, floating_point, display_frequency_suffix)
							values
								(:acname, :name_plural, :floating, :display) 
							on conflict
								(name, name_plural, floating_point, display_frequency_suffix)
							do update set
								name_plural = :name_plural
							returning
								id 
						),
						inserts as (
							select 
								:provider provider, 
								:category category, 
								cast(:product_type as accounting.product_type) product_type, 
								ac.id accounting_unit,
								:frequency frequency,
								cast(:charge_type as accounting.charge_type) charge_t,
								cast(:free_to_use as bool) free,
								cast(:allow_sub_allocations as bool) allow_sub_allocations
							from acinsert ac
						)
					insert into accounting.product_categories
						(provider, category, product_type, accounting_unit, accounting_frequency, charge_type,
						 free_to_use, allow_sub_allocations) 
					select
						provider, category, product_type, accounting_unit, frequency, charge_t,
						free, allow_sub_allocations
					from inserts
					on conflict (provider, category)  
					do update set
						product_type = excluded.product_type,
						allow_sub_allocations = excluded.allow_sub_allocations
			    `,
				db.Params{
					"provider": item.Category.Provider,
					"category": item.Category.Name,

					"acname":      item.Category.AccountingUnit.Name,
					"name_plural": item.Category.AccountingUnit.NamePlural,
					"floating":    item.Category.AccountingUnit.FloatingPoint,
					"display":     item.Category.AccountingUnit.DisplayFrequencySuffix,

					"frequency":             string(item.Category.AccountingFrequency),
					"product_type":          string(item.ProductType),
					"free_to_use":           item.Category.FreeToUse,
					"charge_type":           translateToChargeType(item.Category),
					"allow_sub_allocations": item.Category.AllowSubAllocations,
				},
			)
		}

		var names []string
		var prices []int64
		var cpus []int
		var gpus []int
		var memoryInGigs []int
		var cpuModel []sql.NullString
		var memoryModel []sql.NullString
		var gpuModel []sql.NullString
		var licenseTags []sql.NullString
		var categories []string
		var providers []string
		var description []string

		for _, req := range products {
			names = append(names, req.Name)
			prices = append(prices, req.Price)
			categories = append(categories, req.Category.Name)
			providers = append(providers, req.Category.Provider)
			licenseTags = append(licenseTags, sql.NullString{})
			description = append(description, req.Description)

			cpus = append(cpus, req.Cpu)
			gpus = append(gpus, req.Gpu)
			memoryInGigs = append(memoryInGigs, req.MemoryInGigs)

			cpuModel = append(cpuModel, util.OptSqlStringIfNotEmpty(req.CpuModel))
			memoryModel = append(memoryModel, util.OptSqlStringIfNotEmpty(req.MemoryModel))
			gpuModel = append(gpuModel, util.OptSqlStringIfNotEmpty(req.GpuModel))
		}

		db.Exec(
			tx,
			`
				with requests as (
					select
						unnest(cast(:names as text[])) uname,
						unnest(cast(:prices as bigint[])) price,
						unnest(cast(:cpus as int[])) cpu,
						unnest(cast(:gpus as int[])) gpu,
						unnest(cast(:memory_in_gigs as int[])) memory_in_gigs,
						unnest(cast(:categories as text[])) category,
						unnest(cast(:providers as text[])) provider,
						unnest(cast(:license_tags as jsonb[])) license_tags,
						unnest(cast(:description as text[])) description,
						unnest(cast(:cpu_model as text[])) cpu_model,
						unnest(cast(:gpu_model as text[])) gpu_model,
						unnest(cast(:memory_model as text[])) memory_model
				)
				insert into accounting.products
					(name, price, cpu, gpu, memory_in_gigs, license_tags, category,
					  version, description, cpu_model, gpu_model, memory_model) 
				select
					req.uname, req.price, req.cpu, req.gpu, req.memory_in_gigs, req.license_tags,
					pc.id, 1, req.description, req.cpu_model, req.gpu_model, req.memory_model
				from
					requests req join
					accounting.product_categories pc on
						req.category = pc.category and
						req.provider = pc.provider left join
					accounting.products existing on
						req.uname = existing.name and
						existing.category = pc.id
				on conflict (name, category, version)
				do update set
					price = excluded.price,
					cpu = excluded.cpu,
					gpu = excluded.gpu,
					memory_in_gigs = excluded.memory_in_gigs,
					license_tags = excluded.license_tags,
					description = excluded.description,
					cpu_model = excluded.cpu_model,
					gpu_model = excluded.gpu_model,
					memory_model = excluded.memory_model
		    `,
			db.Params{
				"names":          names,
				"prices":         prices,
				"cpus":           cpus,
				"gpus":           gpus,
				"memory_in_gigs": memoryInGigs,
				"categories":     categories,
				"providers":      providers,
				"license_tags":   licenseTags,
				"description":    description,
				"cpu_model":      cpuModel,
				"gpu_model":      gpuModel,
				"memory_model":   memoryModel,
			},
		)

		dbErr := tx.ConsumeError()
		if dbErr != nil {
			err = util.HttpErr(http.StatusBadRequest, "%s", dbErr.Error())
		}
	})

	if err == nil {
		sortNeeded := false
		for _, newProduct := range products {
			found := false
			for i, oldProduct := range bucket.Products {
				if newProduct.Name == oldProduct.Name && newProduct.Category.Name == oldProduct.Category.Name {
					bucket.Products[i] = newProduct
					found = true
					break
				}
			}

			if !found {
				bucket.Products = append(bucket.Products, newProduct)
				sortNeeded = true
			}
		}

		if sortNeeded {
			slices.SortFunc(bucket.Products, func(a, b accapi.ProductV2) int {
				cmp := strings.Compare(a.Category.Name, b.Category.Name)
				if cmp != 0 {
					return cmp
				}

				cmp = strings.Compare(a.Name, b.Name)
				if cmp != 0 {
					return cmp
				}

				return 0
			})
		}
	}

	bucket.Mu.Unlock()

	return err
}

func ProductCategoryRetrieve(actor rpc.Actor, name, provider string) (accapi.ProductCategory, *util.HttpError) {
	results, err := ProductBrowse(actor, accapi.ProductsBrowseRequest{
		ItemsPerPage: 1,
		ProductsFilter: accapi.ProductsFilter{
			FilterProvider:    util.OptValue(provider),
			FilterCategory:    util.OptValue(name),
			FilterUsable:      util.OptValue(false),
			IncludeBalance:    util.OptValue(false),
			IncludeMaxBalance: util.OptValue(false),
		},
	})
	if err == nil {
		if len(results.Items) > 0 {
			return results.Items[0].Category, nil
		}
		err = util.HttpErr(http.StatusNotFound, "category not found")
	}
	return accapi.ProductCategory{}, err
}

func ProductCategories() []accapi.ProductCategory {
	productsByProvider.Mu.RLock()
	providerIds := make([]string, len(productsByProvider.Providers))
	copy(providerIds, productsByProvider.Providers)
	productsByProvider.Mu.RUnlock()

	slices.Sort(providerIds)

	var result []accapi.ProductCategory
	for _, providerId := range providerIds {
		result = append(result, ProductCategoriesByProvider(rpc.ActorSystem, providerId)...)
	}

	return result
}

func ProductCategoriesByProvider(actor rpc.Actor, provider string) []accapi.ProductCategory {
	results, err := ProductBrowse(actor, accapi.ProductsBrowseRequest{
		ItemsPerPage: 10000,
		ProductsFilter: accapi.ProductsFilter{
			FilterProvider: util.OptValue(provider),
		},
	})

	var result []accapi.ProductCategory
	categorySet := map[string]util.Empty{}

	if err == nil {
		for _, item := range results.Items {
			if _, exists := categorySet[item.Category.Name]; !exists {
				categorySet[item.Category.Name] = util.Empty{}
				result = append(result, item.Category)
			}
		}
	}

	return result
}

func translateToChargeType(category accapi.ProductCategory) string {
	switch category.AccountingFrequency {
	case accapi.AccountingFrequencyOnce:
		return "DIFFERENTIAL_QUOTA"
	case accapi.AccountingFrequencyPeriodicMinute, accapi.AccountingFrequencyPeriodicHour, accapi.AccountingFrequencyPeriodicDay:
		return "ABSOLUTE"
	default:
		return "ABSOLUTE"
	}
}

func productsPostProcess(actor rpc.Actor, products []accapi.ProductV2, filter accapi.ProductsFilter) []accapi.ProductV2 {
	accountingInfoNeeded := false
	if filter.FilterUsable.Present && filter.FilterUsable.Value {
		accountingInfoNeeded = true
	}

	if filter.IncludeBalance.Present && filter.IncludeBalance.Value {
		accountingInfoNeeded = true
	}

	if filter.IncludeMaxBalance.Present && filter.IncludeMaxBalance.Value {
		accountingInfoNeeded = true
	}

	if rpc.RolesEndUser&actor.Role == 0 {
		accountingInfoNeeded = false // only end-users have accounting info to return
	}

	if !accountingInfoNeeded {
		return products
	}

	wallets := WalletsBrowse(
		actor,
		accapi.WalletsBrowseRequest{},
	)

	for _, wallet := range wallets.Items {
		for i, product := range products {
			if wallet.PaysFor == product.Category {
				if filter.IncludeBalance.Present && filter.IncludeBalance.Value {
					products[i].Balance = wallet.Quota - wallet.TotalUsage
				}
				if filter.IncludeMaxBalance.Present && filter.IncludeMaxBalance.Value {
					products[i].MaxUsableBalance = wallet.MaxUsable
				}
			}
		}
	}
	return products
}

func productFilterApplies(product accapi.ProductV2, filter accapi.ProductsFilter) bool {
	if filter.FilterName.Present {
		if product.Name != filter.FilterName.Value {
			return false
		}
	}

	if filter.FilterProductType.Present {
		if product.ProductType != filter.FilterProductType.Value {
			return false
		}
	}

	if filter.FilterProvider.Present {
		if product.Category.Provider != filter.FilterProvider.Value {
			return false
		}
	}

	if filter.FilterCategory.Present {
		if product.Category.Name != filter.FilterCategory.Value {
			return false
		}
	}

	// NOTE(Dan): Remaining filters require accounting info and are processed later
	return true
}

func productsLoad() {
	db.NewTx0(func(tx *db.Transaction) {
		providers := map[string]util.Empty{}
		productsByProvider.Buckets = make(map[string]*providerBucket)
		rows := db.Select[struct {
			Name                      string
			Category                  string
			Provider                  string
			Price                     int64
			Description               string
			Cpu                       sql.NullInt32
			Gpu                       sql.NullInt32
			MemoryInGigs              sql.NullInt32
			CpuModel                  sql.NullString
			GpuModel                  sql.NullString
			MemoryModel               sql.NullString
			HiddenInGrantApplications bool

			ProductType         string
			AccountingFrequency string
			FreeToUse           bool
			AllowSubAllocations bool

			UnitName          string
			UnitNamePlural    string
			UnitFreqSuffix    bool
			UnitFloatingPoint bool
		}](
			tx,
			`
				select
					p.name, 
					pc.category,
					pc.provider,
					price,
					description,
					cpu, gpu, memory_in_gigs,
					cpu_model, gpu_model, memory_model,
					hidden_in_grant_applications,
					
					pc.product_type,
					pc.accounting_frequency,
					pc.free_to_use,
					pc.allow_sub_allocations,
					
					u.name as unit_name,
					u.name_plural as unit_name_plural,
					u.display_frequency_suffix as unit_freq_suffix,
					u.floating_point as unit_floating_point
				from
					accounting.products p 
					join accounting.product_categories pc on p.category = pc.id
					join accounting.accounting_units u on pc.accounting_unit = u.id
				order by pc.provider, pc.category, p.name
		    `,
			db.Params{},
		)

		for _, row := range rows {
			cat := accapi.ProductCategory{
				Name:        row.Category,
				Provider:    row.Provider,
				ProductType: accapi.ProductType(row.ProductType),
				AccountingUnit: accapi.AccountingUnit{
					Name:                   row.UnitName,
					NamePlural:             row.UnitNamePlural,
					FloatingPoint:          row.UnitFloatingPoint,
					DisplayFrequencySuffix: row.UnitFreqSuffix,
				},
				AccountingFrequency: accapi.AccountingFrequency(row.AccountingFrequency),
				FreeToUse:           row.FreeToUse,
				AllowSubAllocations: row.AllowSubAllocations,
			}

			p := accapi.ProductV2{
				Type:                      accapi.ProductTypeCCreate(accapi.ProductType(row.ProductType)),
				Category:                  cat,
				Name:                      row.Name,
				Description:               row.Description,
				ProductType:               accapi.ProductType(row.ProductType),
				Price:                     row.Price,
				HiddenInGrantApplications: row.HiddenInGrantApplications,
				Usage:                     util.OptNone[int64](),
				Cpu:                       int(row.Cpu.Int32),
				CpuModel:                  row.CpuModel.String,
				MemoryInGigs:              int(row.MemoryInGigs.Int32),
				MemoryModel:               row.MemoryModel.String,
				Gpu:                       int(row.Gpu.Int32),
				GpuModel:                  row.GpuModel.String,
			}

			// NOTE(Dan): ordering done by query
			bucket := util.ReadOrInsertBucket(&productsByProvider.Mu, productsByProvider.Buckets, p.Category.Provider, nil)
			bucket.Products = append(bucket.Products, p)

			providers[p.Category.Provider] = util.Empty{}
		}

		var providersArr []string
		for provider := range providers {
			providersArr = append(providersArr, provider)
		}
		slices.Sort(providersArr)
		productsByProvider.Providers = providersArr
	})
}
