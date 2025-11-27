package accounting

import (
	"time"

	"ucloud.dk/core/pkg/config"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database2"
)

// Limits the number on first run to only active during last week
var lastScan = time.Now().Add(-24 * 7 * time.Hour)
var coreHourLowFundsLimit = int64(0)
var storageLowFundsLimit = int64(0)

func InitLowFundsScan() {
	coreHourLowFundsLimit = config.Configuration.Accounting.ComputeUnitsLowFundsNotificationLimitInCH
	if coreHourLowFundsLimit == 0 {
		coreHourLowFundsLimit = 20
	}

	storageLowFundsLimit = config.Configuration.Accounting.StorageUnitsLowFundsNotificationLimitInGB
	if storageLowFundsLimit == 0 {
		storageLowFundsLimit = 100
	}

	for {
		if time.Since(lastScan).Hours() >= 24 {
			lowFundsScan()
			lastScan = time.Now()
		}
		time.Sleep(24 * time.Hour)
	}
}

func lowFundsScan() {
	db.NewTx0(func(tx *db.Transaction) {
		relevantWallets := db.Select[struct {
			WalletId            int
			LowBalanceNotified  bool
			CurrentUsage        int64
			CurrentQuota        int64
			ProductType         accapi.ProductType
			AccountingFrequency accapi.AccountingFrequency
		}](
			tx,
			`
				with newRows as (
					select
						wallet_id,
						(report_data->'Kpis'->'TotalUsageAtEnd')::int - (report_data->'Kpis'->'TotalUsageAtStart')::int usageChange,
						(report_data->'Kpis'->'ActiveQuotaAtEnd')::int - (report_data->'Kpis'->'ActiveQuotaAtStart')::int quotaChange,
						(report_data->'Kpis'->'TotalUsageAtEnd')::int usage,
						(report_data->'Kpis'->'ActiveQuotaAtEnd')::int quota
				
					from accounting.usage_report
					where valid_from > :last_scan
				)
				select nr.wallet_id, w.low_balance_notified, nr.usage, nr.quota, pc.product_type, pc.accounting_frequency
				from newRows nr join
					accounting.wallets_v2 w on w.id = nr.wallet_id join
					accounting.product_categories pc on w.product_category = pc.id
				where (usageChange != 0 or quotaChange != 0) and (pc.product_type = 'COMPUTE' or pc.product_type = 'STORAGE');
			`,
			db.Params{
				"last_scan": lastScan,
			},
		)
		for _, row := range relevantWallets {
			limit := int64(0)
			if row.ProductType == accapi.ProductTypeStorage {
				limit = storageLowFundsLimit
			}
			if row.ProductType == accapi.ProductTypeCompute {
				limit = coreHourLowFundsLimit
			}
			
			if row.CurrentQuota-row.CurrentUsage < limit {

			}
		}

	})
}
