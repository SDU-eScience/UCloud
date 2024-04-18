package apm

import "log"

type ProductReference struct {
	Id       string `json:"id"`
	Category string `json:"category"`
	Provider string `json:"provider"`
}

type ProductCategoryIdV2 struct {
	Name     string
	Provider string
}

type ProductCategory struct {
	Name                string
	Provider            string
	ProductType         ProductType
	AccountingUnit      AccountingUnit
	AccountingFrequency AccountingFrequency
	FreeToUse           bool
	AllowSubAllocations bool
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
