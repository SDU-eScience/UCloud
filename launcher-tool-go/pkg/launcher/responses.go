package launcher

type ProductBulkResponse struct {
	responses []ProductV2
}

type ProductV2 struct {
	category    ProductCategory
	name        string
	description string
	productType ProductType
	price       int64
}

type ProductCategory struct {
	name                string
	provider            string
	productType         ProductType
	accountingUnit      AccountingUnit
	accountingFrequency AccountingFrequency
}

type ProductType int32

const (
	STORAGE    ProductType = iota
	COMPUTE    ProductType = iota
	INGRESS    ProductType = iota
	LICENSE    ProductType = iota
	NETWORK_IP ProductType = iota
)

type AccountingUnit struct {
	name                   string
	namePlural             string
	floatingPoint          bool
	displayFrequencySuffix bool
}

type AccountingFrequency int32

const (
	ONCE            AccountingFrequency = iota
	PERIODIC_MINUTE AccountingFrequency = iota
	PERIODIC_HOUR   AccountingFrequency = iota
	PERIODIC_DAY    AccountingFrequency = iota
)

type FindByStringIDBulkResponse struct {
	responses []FindByStringId
}

type FindByStringId struct {
	id string
}
