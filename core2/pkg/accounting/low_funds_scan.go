package accounting

import (
	"encoding/json"

	"ucloud.dk/core/pkg/config"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
)

func initLowFundsScan() {
	computeLowFundsLimit := config.Configuration.Accounting.ComputeUnitsLowFundsNotificationLimitInCH
	if computeLowFundsLimit == 0 {
		computeLowFundsLimit = 20
	}

	storageLowFundsLimit := config.Configuration.Accounting.StorageUnitsLowFundsNotificationLimitInGB
	if storageLowFundsLimit == 0 {
		storageLowFundsLimit = 100
	}

	/*
		go func() {
			// Make sure that the scan does not start before the system is up and running
			time.Sleep(5 * time.Second)

			ticker := time.NewTicker(12 * time.Hour)
			defer ticker.Stop()

			for {
				<-ticker.C
				lowFundsScan(computeLowFundsLimit, storageLowFundsLimit)
			}
		}()
	*/
}

func lowFundsScan(coreHourLowFundsLimit int64, storageLowFundsLimit int64) {
	log.Debug("Starting low funds scan")

	type Resource struct {
		WorkspaceTitle string `json:"workspaceTitle"`
		Category       string `json:"category"`
		Provider       string `json:"provider"`
	}

	WalletsToNotify, WalletsToResetNotification, UsersToNotifications := db.NewTx3(func(tx *db.Transaction) ([]int, []int, map[string][]Resource) {
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
				with summarized_wallets as (
					select
						w.id wallet_id,
						sum(ag.tree_usage) current_usage,
						sum(alloc.quota) current_quota
					from accounting.wallet_allocations_v2 alloc join
					accounting.allocation_groups ag on alloc.associated_allocation_group = ag.id join
					accounting.wallets_v2 w on ag.associated_wallet = w.id
					where
						alloc.allocation_start_time <= now() and
						alloc.allocation_end_time >= now()
					group by w.id
					order by w.id
				)
				select
					w.id wallet_id,
					w.low_balance_notified,
					sw.current_usage,
					sw.current_quota,
					pc.product_type,
					pc.accounting_frequency,
					pc.category category_name,
					pc.provider,
					coalesce(pm.username, wo.username) username,
					wo.project_id is not null is_project,
					coalesce(p.title, concat('Personal workspace - ', wo.username)) project_title
				from
					summarized_wallets sw join
						accounting.wallets_v2 w on w.id = sw.wallet_id join
						accounting.product_categories pc on w.product_category = pc.id join
						accounting.wallet_owner wo on w.wallet_owner = wo.id left join
						project.projects p on wo.project_id = p.id left join
						project.project_members pm on p.id = pm.project_id and pm.role = 'PI'
				where (pc.product_type = 'COMPUTE' or pc.product_type = 'STORAGE')
				order by w.id
			`,
			db.Params{},
		)

		var walletsToNotify []int
		var walletsToResetNotification []int
		usersToNotifications := make(map[string][]Resource)

		for _, row := range relevantWallets {
			limit := int64(0)
			if row.ProductType == accapi.ProductTypeStorage {
				limit = storageLowFundsLimit
			}
			if row.ProductType == accapi.ProductTypeCompute {
				limit = coreHourLowFundsLimit
			}

			// Converting to more readable format for the mail
			rawAmount := row.CurrentQuota - row.CurrentUsage
			remaining := int64(0)
			switch row.AccountingFrequency {
			case accapi.AccountingFrequencyOnce:
				remaining = rawAmount
			case accapi.AccountingFrequencyPeriodicMinute:
				remaining = rawAmount / 60
			case accapi.AccountingFrequencyPeriodicHour:
				remaining = rawAmount
			case accapi.AccountingFrequencyPeriodicDay:
				remaining = rawAmount * 24
			default:
				log.Warn("Invalid accounting frequency passed: '%v'\n", row.AccountingFrequency)
			}

			if remaining < limit && !row.LowBalanceNotified {
				notifications := usersToNotifications[row.Username]
				notifications = append(notifications, Resource{
					row.ProjectTitle,
					row.CategoryName,
					row.Provider,
				})
				usersToNotifications[row.Username] = notifications
				walletsToNotify = append(walletsToNotify, row.WalletId)
			}
			if remaining > limit && row.LowBalanceNotified {
				walletsToResetNotification = append(walletsToResetNotification, row.WalletId)
			}
		}

		return walletsToNotify, walletsToResetNotification, usersToNotifications
	})

	var bulkRequest fndapi.BulkRequest[fndapi.MailSendToUserRequest]
	for key, resources := range UsersToNotifications {
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
		log.Warn("Failed to send low-funds email: %s", err)
		return
	}

	if len(bulkRequest.Items) > 0 {
		db.NewTx0(func(tx *db.Transaction) {
			// Updating notification tracker for wallets below limit
			updateLowBalanceNotified(tx, WalletsToNotify, true)

			// Resetting notification tracker for wallets that is above limit again.
			updateLowBalanceNotified(tx, WalletsToResetNotification, false)
		})
	}

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
