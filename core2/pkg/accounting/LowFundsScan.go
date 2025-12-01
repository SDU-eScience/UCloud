package accounting

import (
	"encoding/json"
	"time"

	"ucloud.dk/core/pkg/config"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
)

// Limits the number on first run to only active during last week
var lastScan = time.Now().Add(-24 * 7 * time.Hour)
var coreHourLowFundsLimit = int64(0)
var storageLowFundsLimit = int64(0)

func InitLowFundsScan() {
	// Make sure that the scan does not start before the system is up and running
	time.Sleep(5 * time.Second)

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

type Resource struct {
	WorkspaceTitle string `json:"workspaceTitle"`
	Category       string `json:"category"`
	Provider       string `json:"provider"`
}

func lowFundsScan() {
	log.Debug("Starting low funds scan")

	WalletsToNotify, WalletsToResetNotification, ProjectToNotifications := db.NewTx3(func(tx *db.Transaction) ([]int, []int, map[string][]Resource) {
		relevantWallets := db.Select[struct {
			WalletId            int
			LowBalanceNotified  bool
			CurrentUsage        int64
			CurrentQuota        int64
			ProductType         accapi.ProductType
			AccountingFrequency accapi.AccountingFrequency
			CategoryName        string
			Provider            string
			Username            string
			IsProject           bool
			ProjectTitle        string
		}](
			tx,
			`
				with newRows as (
					select
						wallet_id,
						(report_data->'Kpis'->'TotalUsageAtEnd')::int - (report_data->'Kpis'->'TotalUsageAtStart')::int usageChange,
						(report_data->'Kpis'->'ActiveQuotaAtEnd')::int - (report_data->'Kpis'->'ActiveQuotaAtStart')::int quotaChange,
						(report_data->'Kpis'->'TotalUsageAtEnd')::int usage,
						(report_data->'Kpis'->'ActiveQuotaAtEnd')::int quota,
						valid_from
					from accounting.usage_report
					where valid_from > :last_scan
				),
				newest as (
					select *
					from newRows nr
					where valid_from = (select max(valid_from) from newRows nr2 where nr.wallet_id = nr2.wallet_id)
					)
				select
					nr.wallet_id,
					w.low_balance_notified,
					nr.usage current_usage,
					nr.quota current_quota,
					pc.product_type,
					pc.accounting_frequency,
					pc.category category_name,
					pc.provider,
					coalesce(pm.username, wo.username) username,
					wo.project_id is not null is_project,
					coalesce(p.title, concat('Personal workspace - ', wo.username)) project_title
				from newest nr join
					accounting.wallets_v2 w on w.id = nr.wallet_id join
					accounting.wallet_owner wo on w.wallet_owner = wo.id join
					accounting.product_categories pc on w.product_category = pc.id left join
					project.project_members pm on wo.project_id = pm.project_id and pm.role = 'PI' left join
					project.projects p on wo.project_id = p.id
				where (usageChange != 0 or quotaChange != 0) and (pc.product_type = 'COMPUTE' or pc.product_type = 'STORAGE')
				order by wo.project_id, username;
			`,
			db.Params{
				"last_scan": lastScan,
			},
		)

		var WalletsToNotify []int
		var WalletsToResetNotification []int
		ProjectToNotifications := make(map[string][]Resource)

		for _, row := range relevantWallets {
			limit := int64(0)
			if row.ProductType == accapi.ProductTypeStorage {
				limit = storageLowFundsLimit
			}
			if row.ProductType == accapi.ProductTypeCompute {
				limit = coreHourLowFundsLimit
			}
			remaining := AccountingDbValuesToReadableFormat(row.AccountingFrequency, row.CurrentQuota-row.CurrentUsage)
			if remaining < limit && !row.LowBalanceNotified {
				notifications := ProjectToNotifications[row.Username]
				notifications = append(notifications, Resource{
					row.ProjectTitle,
					row.CategoryName,
					row.Provider,
				})
				ProjectToNotifications[row.Username] = notifications
				WalletsToNotify = append(WalletsToNotify, row.WalletId)
			}
			if remaining > limit && row.LowBalanceNotified {
				WalletsToResetNotification = append(WalletsToResetNotification, row.WalletId)
			}
		}

		return WalletsToNotify, WalletsToResetNotification, ProjectToNotifications
	})

	var bulkRequest fndapi.BulkRequest[fndapi.MailSendToUserRequest]
	for key, resources := range ProjectToNotifications {
		rawMail := map[string]any{
			"type":      fndapi.MailTypeLowFunds,
			"resources": resources,
		}

		mailData, _ := json.Marshal(rawMail)
		bulkRequest.Items = append(
			bulkRequest.Items,
			fndapi.MailSendToUserRequest{
				Receiver: key,
				Mail:     mailData,
			},
		)
	}

	_, err := fndapi.MailSendToUser.Invoke(bulkRequest)

	if err != nil {
		log.Warn("Failed to LowFunds Emails", "error", err)
		return
	}

	db.NewTx0(func(tx *db.Transaction) {
		// Updating notification tracker for wallets below limit
		updateLowBalanceNotified(tx, WalletsToNotify, true)

		// Resetting notification tracker for wallets that is above limit again.
		updateLowBalanceNotified(tx, WalletsToResetNotification, false)
	})

	log.Debug("Low funds scan finished")
}

func updateLowBalanceNotified(tx *db.Transaction, walletIds []int, newState bool) {
	batch := db.BatchNew(tx)
	for _, walletId := range walletIds {
		db.BatchExec(
			batch,
			`
					update accounting.wallets_v2 
					set low_balance_notified = :new_state
					where id = :wallet_id
				`,
			db.Params{
				"wallet_id": walletId,
				"new_state": newState,
			},
		)
	}
	db.BatchSend(batch)
}
