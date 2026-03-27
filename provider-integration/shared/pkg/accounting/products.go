package apm

import (
	"log"

	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"

	"ucloud.dk/shared/pkg/util"
)

type ProductReference struct {
	Id       string `json:"id"`
	Category string `json:"category"`
	Provider string `json:"provider"`
}

type ProductCategoryIdV2 struct {
	Name     string `json:"name,omitempty"`
	Provider string `json:"provider,omitempty"`
}

type ProductCategory struct {
	Name                string              `json:"name,omitempty"`
	Provider            string              `json:"provider,omitempty"`
	ProductType         ProductType         `json:"productType,omitempty"`
	AccountingUnit      AccountingUnit      `json:"accountingUnit"`
	AccountingFrequency AccountingFrequency `json:"accountingFrequency,omitempty"`
	FreeToUse           bool                `json:"freeToUse,omitempty"`
	AllowSubAllocations bool                `json:"allowSubAllocations,omitempty"`
}

func (c *ProductCategory) ToId() ProductCategoryIdV2 {
	return ProductCategoryIdV2{
		Name:     c.Name,
		Provider: c.Provider,
	}
}

type ProductType string

const (
	ProductTypeCompute        ProductType = "COMPUTE"
	ProductTypeStorage        ProductType = "STORAGE"
	ProductTypeIngress        ProductType = "INGRESS"
	ProductTypeLicense        ProductType = "LICENSE"
	ProductTypeNetworkIp      ProductType = "NETWORK_IP"
	ProductTypePrivateNetwork ProductType = "PRIVATE_NETWORK"
)

type AccountingUnitAndFrequency struct {
	Unit      AccountingUnit      `json:"unit"`
	Frequency AccountingFrequency `json:"frequency"`
}

type AccountingUnit struct {
	Name                   string `json:"name"`
	NamePlural             string `json:"namePlural"`
	FloatingPoint          bool   `json:"floatingPoint"`
	DisplayFrequencySuffix bool   `json:"displayFrequencySuffix"`
}

type AccountingFrequency string

const (
	AccountingFrequencyOnce           AccountingFrequency = "ONCE"
	AccountingFrequencyPeriodicMinute AccountingFrequency = "PERIODIC_MINUTE"
	AccountingFrequencyPeriodicHour   AccountingFrequency = "PERIODIC_HOUR"
	AccountingFrequencyPeriodicDay    AccountingFrequency = "PERIODIC_DAY"
)

func (f AccountingFrequency) ToMinutes() int64 {
	switch f {
	case AccountingFrequencyOnce:
		return 1
	case AccountingFrequencyPeriodicMinute:
		return 1
	case AccountingFrequencyPeriodicHour:
		return 60
	case AccountingFrequencyPeriodicDay:
		return 60 * 24
	default:
		log.Printf("Invalid accounting frequency passed: '%v'\n", f)
		return 1
	}
}

func (f AccountingFrequency) IsPeriodic() bool {
	switch f {
	case AccountingFrequencyOnce:
		return false
	case AccountingFrequencyPeriodicMinute:
		return true
	case AccountingFrequencyPeriodicHour:
		return true
	case AccountingFrequencyPeriodicDay:
		return true
	default:
		log.Printf("Invalid accounting frequency passed: '%v'\n", f)
		return false
	}
}

type ProductTypeC string

const (
	ProductTypeCStorage        ProductTypeC = "storage"
	ProductTypeCCompute        ProductTypeC = "compute"
	ProductTypeCIngress        ProductTypeC = "ingress"
	ProductTypeCLicense        ProductTypeC = "license"
	ProductTypeCNetworkIp      ProductTypeC = "network_ip"
	ProductTypeCPrivateNetwork ProductTypeC = "private_network"
)

func ProductTypeCCreate(t ProductType) ProductTypeC {
	switch t {
	case ProductTypeCompute:
		return ProductTypeCCompute
	case ProductTypeStorage:
		return ProductTypeCStorage
	case ProductTypeIngress:
		return ProductTypeCIngress
	case ProductTypeLicense:
		return ProductTypeCLicense
	case ProductTypeNetworkIp:
		return ProductTypeCNetworkIp
	case ProductTypePrivateNetwork:
		return ProductTypeCPrivateNetwork
	default:
		panic("unknown product type")
	}
}

type ProductV2 struct {
	Type                      ProductTypeC       `json:"type"`
	Category                  ProductCategory    `json:"category"`
	Name                      string             `json:"name"`
	Description               string             `json:"description"`
	ProductType               ProductType        `json:"productType"`
	Price                     int64              `json:"price"`
	HiddenInGrantApplications bool               `json:"hiddenInGrantApplications"`
	Usage                     util.Option[int64] `json:"usage"`

	// Valid only if ProductType == ProductTypeCompute. Most values are optional and can be 0.

	Cpu          int    `json:"cpu,omitempty"`
	CpuModel     string `json:"cpuModel,omitempty"`
	MemoryInGigs int    `json:"memoryInGigs,omitempty"`
	MemoryModel  string `json:"memoryModel,omitempty"`
	Gpu          int    `json:"gpu,omitempty"`
	GpuModel     string `json:"gpuModel,omitempty"`

	// Accounting information only given when requested

	Balance          int64 `json:"balance"`
	MaxUsableBalance int64 `json:"maxUsableBalance"`

	PricePerUnit int64 `json:"pricePerUnit"` // deprecated - only here for backwards compatibility with openstack gw
}

func (p *ProductV2) ToReference() ProductReference {
	return ProductReference{
		Id:       p.Name,
		Category: p.Category.Name,
		Provider: p.Category.Provider,
	}
}

// API
// =====================================================================================================================

const ProductsNamespace = "products/v2"

type ProductsFilter struct {
	FilterName        util.Option[string]      `json:"filterName"`
	FilterProductType util.Option[ProductType] `json:"filterProductType"`
	FilterProvider    util.Option[string]      `json:"filterProvider"`
	FilterCategory    util.Option[string]      `json:"filterCategory"`
	FilterUsable      util.Option[bool]        `json:"filterUsable"`
	IncludeBalance    util.Option[bool]        `json:"includeBalance"`
	IncludeMaxBalance util.Option[bool]        `json:"includeMaxBalance"`
}

type ProductsBrowseRequest struct {
	ItemsPerPage   int                 `json:"itemsPerPage"`
	Next           util.Option[string] `json:"next"`
	ProductsFilter `json:"productsFilter"`
}

var ProductsCreate = rpc.Call[fnd.BulkRequest[ProductV2], util.Empty]{
	BaseContext: ProductsNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesProvider,
}

var ProductsRetrieve = rpc.Call[ProductsFilter, ProductV2]{
	BaseContext: ProductsNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesPublic,
}

var ProductsBrowse = rpc.Call[ProductsBrowseRequest, fnd.PageV2[ProductV2]]{
	BaseContext: ProductsNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesPublic,
}
