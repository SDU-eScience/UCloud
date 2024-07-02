package apm

import (
	"log"
	c "ucloud.dk/pkg/client"
	fnd "ucloud.dk/pkg/foundation"
	"ucloud.dk/pkg/util"
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

type ProductType string

const (
	ProductTypeCompute   ProductType = "COMPUTE"
	ProductTypeStorage   ProductType = "STORAGE"
	ProductTypeIngress   ProductType = "INGRESS"
	ProductTypeLicense   ProductType = "LICENSE"
	ProductTypeNetworkIp ProductType = "NETWORK_IP"
)

type AccountingUnit struct {
	Name                   string
	NamePlural             string
	FloatingPoint          bool
	DisplayFrequencySuffix bool
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
		return 60
	case AccountingFrequencyPeriodicHour:
		return 60 * 60
	case AccountingFrequencyPeriodicDay:
		return 60 * 60 * 24
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

type ProductV2 struct {
	Category                  ProductCategory `json:"category"`
	Name                      string          `json:"name,omitempty"`
	Description               string          `json:"description,omitempty"`
	ProductType               ProductType     `json:"productType,omitempty"`
	Price                     int64           `json:"price,omitempty"`
	HiddenInGrantApplications bool            `json:"hiddenInGrantApplications,omitempty"`
	Usage                     int64           `json:"usage,omitempty"`

	// Valid only if ProductType == ProductTypeCompute. Most values are optional and can be 0.

	Cpu          int    `json:"cpu,omitempty"`
	CpuModel     string `json:"cpuModel,omitempty"`
	MemoryInGigs int    `json:"memoryInGigs,omitempty"`
	MemoryModel  string `json:"memoryModel,omitempty"`
	Gpu          int    `json:"gpu,omitempty"`
	GpuModel     string `json:"gpuModel,omitempty"`
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

const productContext = "/api/products/v2/"
const productsNamespace = "products.v2."

type ProductsFilter struct {
	FilterName        util.Option[string]
	FilterProductType util.Option[ProductType]
	FilterProvider    util.Option[string]
	FilterCategory    util.Option[string]
	FilterUsable      util.Option[bool]
	IncludeBalance    util.Option[bool]
	IncludeMaxBalance util.Option[bool]
}

func BrowseProducts(next string, filter ProductsFilter) (fnd.PageV2[ProductV2], error) {
	return c.ApiBrowse[fnd.PageV2[ProductV2]](
		productsNamespace+"browse",
		productContext,
		"",
		append(c.StructToParameters(filter), "next", next, "itemsPerPage", "250"),
	)
}
