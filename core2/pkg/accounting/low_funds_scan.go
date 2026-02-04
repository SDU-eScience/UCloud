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

func initLowFundsScan() {
	lowFundsLimitInPercent := config.Configuration.Accounting.LowFundsLimitInPercent
	if lowFundsLimitInPercent == 0.0 {
		lowFundsLimitInPercent = 10.0
	}

	go func() {
		// Make sure that the scan does not start before the system is up and running
		time.Sleep(5 * time.Second)

		ticker := time.NewTicker(12 * time.Hour)
		defer ticker.Stop()

		for {
			<-ticker.C
			lowFundsScan(lowFundsLimitInPercent)
		}
	}()
}

func lowFundsScan(lowFundsLimitInPercent float32) {
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
				with
					active_quota as (
						select
							w.id,
							sum(alloc.quota) current_quota
						from accounting.wallet_allocations_v2 alloc join
							 accounting.allocation_groups ag on alloc.associated_allocation_group = ag.id join
							 accounting.wallets_v2 w on ag.associated_wallet = w.id
						where
							alloc.allocation_start_time <= now() and alloc.allocation_end_time >= now()
						group by w.id
						order by w.id
					),
					active_usage as (
						select
							w.id,
							sum(ag.tree_usage) current_usage
						from accounting.wallet_allocations_v2 alloc join
							 accounting.allocation_groups ag on alloc.associated_allocation_group = ag.id join
							 accounting.wallets_v2 w on ag.associated_wallet = w.id
						where
							alloc.allocation_start_time <= now() and alloc.allocation_end_time >= now()
						group by w.id
						order by w.id
					)
				select
					w.id wallet_id,
					w.low_balance_notified,
					au.current_usage active_usage,
					aq.current_quota active_quota,
					pc.product_type,
					pc.accounting_frequency,
					pc.category category_name,
					pc.provider,
					coalesce(pm.username, wo.username) username,
					wo.project_id is not null is_project,
					coalesce(p.title, concat('Personal workspace - ', wo.username)) project_title
				from
					accounting.wallets_v2 w join
					active_usage au on w.id = au.id join
					active_quota aq on w.id = aq.id join
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
			// Converting to more readable format for the mail
			currentQuota := float32(row.CurrentQuota)
			currentUsage := float32(row.CurrentUsage)
			lowBalanceNotified := row.LowBalanceNotified
			if row.CurrentQuota == 0 {
				continue
			}
			percentageRemaining := (currentQuota - currentUsage) / currentQuota * 100.0

			if percentageRemaining < lowFundsLimitInPercent && !lowBalanceNotified {
				notifications := usersToNotifications[row.Username]
				notifications = append(notifications, Resource{
					row.ProjectTitle,
					row.CategoryName,
					row.Provider,
				})
				usersToNotifications[row.Username] = notifications
				walletsToNotify = append(walletsToNotify, row.WalletId)
			}
			if percentageRemaining > lowFundsLimitInPercent && lowBalanceNotified {
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

	if len(WalletsToNotify) > 0 || len(WalletsToResetNotification) > 0 {
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
