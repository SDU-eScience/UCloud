package accounting

import (
	"encoding/json"
	"fmt"
	"math"
	"strings"
	"time"

	"ucloud.dk/core/pkg/config"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
)

type lowFundsWalletSnapshot struct {
	Owner       accapi.WalletOwner
	Category    accapi.ProductCategory
	MaxUsable   int64
	Quota       int64
	TotalUsage  int64
	LocalUsage  int64
	ActiveUsage int64
	ActiveQuota int64
}

type lowFundsConfig struct {
	LowThresholdPercent      float64
	RecoveryThresholdPercent float64
	MinimumComputeHours      int64
	MinimumStorageBytes      int64
	MinimumInferenceTokens   int64
}

type lowFundsState string

const (
	lowFundsHealthy   lowFundsState = "healthy"
	lowFundsLow       lowFundsState = "low"
	lowFundsExhausted lowFundsState = "exhausted"
)

type lowFundsStoredState struct {
	State lowFundsState
}

type lowFundsDecision struct {
	Eligible         bool
	NewState         lowFundsState
	Notify           bool
	Reason           string
	Remaining        int64
	ActiveUsage      int64
	EffectiveTotal   int64
	RemainingPercent float64
}

type promiseExpiresSoonSnapshot struct {
	ProjectId string
	Category  accapi.ProductCategory
	PromiseId PromiseId
	Start     time.Time
	End       time.Time
	Quota     int64
}

type lowFundsNotificationResource struct {
	WorkspaceTitle   string  `json:"workspaceTitle"`
	Category         string  `json:"category"`
	Provider         string  `json:"provider"`
	NotificationType string  `json:"notificationType"`
	State            string  `json:"state,omitempty"`
	Remaining        int64   `json:"remaining,omitempty"`
	ActiveUsage      int64   `json:"activeUsage,omitempty"`
	EffectiveTotal   int64   `json:"effectiveTotal,omitempty"`
	PercentRemaining float64 `json:"percentRemaining,omitempty"`
	ExpiresAt        string  `json:"expiresAt,omitempty"`
	Quota            int64   `json:"quota,omitempty"`
}

type lowFundsStateKey struct {
	ProjectId string
	Provider  string
	Category  string
}

type promiseExpiryStateKey struct {
	ProjectId string
	Provider  string
	Category  string
	PromiseId PromiseId
}

func initLowFundsScan() {
	lowFundsLimitInPercent := config.Configuration.Accounting.LowFundsLimitInPercent
	if lowFundsLimitInPercent == 0.0 {
		lowFundsLimitInPercent = 10.0
	}

	go func() {
		// Make sure that the scan does not start before the system is up and running.
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
	now := time.Now()

	cfg := lowFundsConfig{
		LowThresholdPercent:      float64(lowFundsLimitInPercent),
		RecoveryThresholdPercent: math.Max(float64(lowFundsLimitInPercent)*2, 20.0),
		MinimumComputeHours:      1000,
		MinimumStorageBytes:      50 * 1000 * 1000 * 1000,
		MinimumInferenceTokens:   1000 * 1000,
	}

	wallets, expiringPromises := lowFundsCollectSnapshots(now)
	lowStates := lowFundsLoadStates()
	expiryStates := promiseExpiryLoadStates()

	projectNotifications := map[string][]lowFundsNotificationResource{}
	lowUpdates := map[lowFundsStateKey]lowFundsDecision{}
	expiryUpdates := map[promiseExpiryStateKey]time.Time{}
	expiresSoonCategories := map[lowFundsStateKey]bool{}

	for _, promise := range expiringPromises {
		key := promiseExpiryStateKey{
			ProjectId: promise.ProjectId,
			Provider:  promise.Category.Provider,
			Category:  promise.Category.Name,
			PromiseId: promise.PromiseId,
		}
		expiresSoonCategories[lowFundsStateKey{ProjectId: promise.ProjectId, Provider: promise.Category.Provider, Category: promise.Category.Name}] = true
		if expiryStates[key] {
			continue
		}

		expiryUpdates[key] = promise.End
		projectNotifications[promise.ProjectId] = append(projectNotifications[promise.ProjectId], lowFundsNotificationResource{
			Category:         promise.Category.Name,
			Provider:         promise.Category.Provider,
			NotificationType: "expiresSoon",
			State:            "expires soon",
			ExpiresAt:        promise.End.Format(time.RFC3339),
			Quota:            promise.Quota,
		})
	}

	for _, wallet := range wallets {
		if wallet.Owner.Type != accapi.WalletOwnerTypeProject {
			continue
		}

		key := lowFundsStateKey{ProjectId: wallet.Owner.ProjectId, Provider: wallet.Category.Provider, Category: wallet.Category.Name}
		decision := lowFundsEvaluate(now, wallet, lowStates[key], cfg)
		if !decision.Eligible {
			continue
		}

		lowUpdates[key] = decision
		if decision.Notify && !expiresSoonCategories[key] {
			projectNotifications[key.ProjectId] = append(projectNotifications[key.ProjectId], lowFundsNotificationResource{
				Category:         wallet.Category.Name,
				Provider:         wallet.Category.Provider,
				NotificationType: "lowFunds",
				State:            string(decision.NewState),
				Remaining:        decision.Remaining,
				ActiveUsage:      decision.ActiveUsage,
				EffectiveTotal:   decision.EffectiveTotal,
				PercentRemaining: decision.RemainingPercent,
			})
		}
	}

	projectIds := make([]string, 0, len(projectNotifications))
	for projectId := range projectNotifications {
		projectIds = append(projectIds, projectId)
	}
	projectInfo := lowFundsProjectInfo(projectIds)

	var bulkRequest fndapi.BulkRequest[fndapi.MailSendToUserRequest]
	for projectId, resources := range projectNotifications {
		info := projectInfo[projectId]
		if len(info.Recipients) == 0 {
			log.Warn("No PI/admin recipients found for low-funds notification in project %s", projectId)
			continue
		}

		for i := range resources {
			resources[i].WorkspaceTitle = info.Title
		}

		for _, recipient := range info.Recipients {
			rawMail := map[string]any{
				"type":      fndapi.MailTypeLowFunds,
				"resources": resources,
			}

			mailData, _ := json.Marshal(rawMail)
			bulkRequest.Items = append(bulkRequest.Items, fndapi.MailSendToUserRequest{Receiver: recipient, Mail: mailData})
		}
	}

	if len(bulkRequest.Items) > 0 {
		_, err := fndapi.MailSendToUser.Invoke(bulkRequest)
		if err != nil {
			log.Warn("Failed to send low-funds email: %s", err)
			return
		}
	}

	lowFundsPersistStates(now, lowUpdates)
	promiseExpiryPersistStates(now, expiryUpdates)
}

func lowFundsEvaluate(now time.Time, snapshot lowFundsWalletSnapshot, previous lowFundsStoredState, cfg lowFundsConfig) lowFundsDecision {
	_ = now
	decision := lowFundsDecision{NewState: lowFundsHealthy, Reason: "healthy"}
	if snapshot.Owner.Type != accapi.WalletOwnerTypeProject {
		decision.Reason = "not_project_wallet"
		return decision
	}

	minimum, ok := lowFundsMinimumMeaningfulAllocation(snapshot.Category, cfg)
	if !ok {
		decision.Reason = "ineligible_product"
		return decision
	}
	decision.Eligible = true
	decision.Remaining = snapshot.MaxUsable
	decision.ActiveUsage = snapshot.ActiveUsage
	decision.EffectiveTotal = snapshot.MaxUsable + snapshot.ActiveUsage
	if decision.EffectiveTotal <= 0 {
		decision.Reason = "empty_wallet"
		return decision
	}
	if decision.EffectiveTotal < minimum {
		decision.Reason = "below_minimum_meaningful_allocation"
		return decision
	}

	decision.RemainingPercent = (float64(decision.Remaining) / float64(decision.EffectiveTotal)) * 100.0
	if decision.Remaining == 0 {
		decision.NewState = lowFundsExhausted
	} else if previous.State == lowFundsLow || previous.State == lowFundsExhausted {
		if decision.RemainingPercent < cfg.RecoveryThresholdPercent {
			decision.NewState = previous.State
		} else if decision.RemainingPercent <= cfg.LowThresholdPercent {
			decision.NewState = lowFundsLow
		}
	} else if decision.RemainingPercent <= cfg.LowThresholdPercent {
		decision.NewState = lowFundsLow
	}

	switch {
	case previous.State == "" || previous.State == lowFundsHealthy:
		decision.Notify = decision.NewState == lowFundsLow || decision.NewState == lowFundsExhausted
	case previous.State == lowFundsLow:
		decision.Notify = decision.NewState == lowFundsExhausted
	}
	decision.Reason = string(decision.NewState)
	return decision
}

func lowFundsMinimumMeaningfulAllocation(category accapi.ProductCategory, cfg lowFundsConfig) (int64, bool) {
	if category.Name == "inference" {
		return cfg.MinimumInferenceTokens, true
	}

	switch category.ProductType {
	case accapi.ProductTypeCompute:
		if !category.AccountingFrequency.IsPeriodic() {
			return 0, false
		}
		return ceilDiv(cfg.MinimumComputeHours*60, category.AccountingFrequency.ToMinutes()), true
	case accapi.ProductTypeStorage:
		minimum, ok := storageBytesToUnit(cfg.MinimumStorageBytes, category.AccountingUnit.Name)
		return minimum, ok
	default:
		return 0, false
	}
}

func ceilDiv(a, b int64) int64 {
	if b <= 0 {
		return a
	}
	return (a + b - 1) / b
}

func storageBytesToUnit(bytes int64, unit string) (int64, bool) {
	switch strings.ToLower(unit) {
	case "b", "byte", "bytes":
		return bytes, true
	case "kb", "kbyte", "kbytes", "kilobyte", "kilobytes":
		return ceilDiv(bytes, 1000), true
	case "mb", "mbyte", "mbytes", "megabyte", "megabytes":
		return ceilDiv(bytes, 1000*1000), true
	case "gb", "gbyte", "gbytes", "gigabyte", "gigabytes":
		return ceilDiv(bytes, 1000*1000*1000), true
	case "tb", "tbyte", "tbytes", "terabyte", "terabytes":
		return ceilDiv(bytes, 1000*1000*1000*1000), true
	default:
		return 0, false
	}
}

func lowFundsCollectSnapshots(now time.Time) ([]lowFundsWalletSnapshot, []promiseExpiresSoonSnapshot) {
	accGlobals.Mu.RLock()
	trees := make([]*AccountingTree, 0, len(accGlobals.Trees))
	for _, tree := range accGlobals.Trees {
		trees = append(trees, tree)
	}
	accGlobals.Mu.RUnlock()

	var wallets []lowFundsWalletSnapshot
	var promises []promiseExpiresSoonSnapshot
	for _, tree := range trees {
		tree.Mu.RLock()
		for _, wallet := range tree.WalletsById {
			apiWallet := walletToApi(now, tree, &tree.PromiseTree, wallet, false)
			wallets = append(wallets, lowFundsWalletSnapshot{
				Owner:       apiWallet.Owner,
				Category:    apiWallet.PaysFor,
				MaxUsable:   apiWallet.MaxUsable,
				Quota:       apiWallet.Quota,
				TotalUsage:  apiWallet.TotalUsage,
				LocalUsage:  apiWallet.LocalUsage,
				ActiveUsage: apiWallet.UiOnlyActiveUsage,
				ActiveQuota: apiWallet.UiOnlyActiveQuota,
			})
		}

		for _, promise := range tree.PromiseTree.PromisesById {
			if !promiseExpiresSoon(now, promise) {
				continue
			}
			child := tree.WalletsById[promise.Child]
			if child == nil || child.Owner.Type != accapi.WalletOwnerTypeProject {
				continue
			}
			promises = append(promises, promiseExpiresSoonSnapshot{
				ProjectId: child.Owner.ProjectId,
				Category:  tree.Category,
				PromiseId: promise.Id,
				Start:     promise.Start,
				End:       promise.End,
				Quota:     promise.Quota,
			})
		}
		tree.Mu.RUnlock()
	}
	return wallets, promises
}

func promiseExpiresSoon(now time.Time, promise *Promise) bool {
	return promise != nil &&
		promiseActive(now, promise) &&
		!promise.End.After(now.Add(30*24*time.Hour)) &&
		promise.End.Sub(promise.Start) >= 90*24*time.Hour
}

func lowFundsLoadStates() map[lowFundsStateKey]lowFundsStoredState {
	result := map[lowFundsStateKey]lowFundsStoredState{}
	db.NewTx0(func(tx *db.Transaction) {
		rows := db.Select[struct {
			ProjectId string
			Provider  string
			Category  string
			State     string
		}](tx, `select project_id, provider, category, state from accounting.low_funds_notification_state`, db.Params{})
		for _, row := range rows {
			result[lowFundsStateKey{ProjectId: row.ProjectId, Provider: row.Provider, Category: row.Category}] = lowFundsStoredState{State: lowFundsState(row.State)}
		}
	})
	return result
}

func promiseExpiryLoadStates() map[promiseExpiryStateKey]bool {
	result := map[promiseExpiryStateKey]bool{}
	db.NewTx0(func(tx *db.Transaction) {
		rows := db.Select[struct {
			ProjectId string
			Provider  string
			Category  string
			PromiseId int
		}](tx, `select project_id, provider, category, promise_id from accounting.promise_expiry_notification_state`, db.Params{})
		for _, row := range rows {
			result[promiseExpiryStateKey{ProjectId: row.ProjectId, Provider: row.Provider, Category: row.Category, PromiseId: PromiseId(row.PromiseId)}] = true
		}
	})
	return result
}

func lowFundsPersistStates(now time.Time, updates map[lowFundsStateKey]lowFundsDecision) {
	if len(updates) == 0 {
		return
	}
	db.NewTx0(func(tx *db.Transaction) {
		batch := db.BatchNew(tx)
		for key, decision := range updates {
			db.BatchExec(batch, `
				insert into accounting.low_funds_notification_state(project_id, provider, category, state, last_observed_at,
					last_notified_at, last_max_usable, last_active_usage, last_effective_total, notification_count)
				values (:project_id, :provider, :category, :state, :now, case when :notified then :now else null end,
					:max_usable, :active_usage, :effective_total, case when :notified then 1 else 0 end)
				on conflict (project_id, provider, category) do update set
					state = excluded.state,
					last_observed_at = excluded.last_observed_at,
					last_notified_at = case when :notified then excluded.last_notified_at else accounting.low_funds_notification_state.last_notified_at end,
					last_max_usable = excluded.last_max_usable,
					last_active_usage = excluded.last_active_usage,
					last_effective_total = excluded.last_effective_total,
					notification_count = accounting.low_funds_notification_state.notification_count + case when :notified then 1 else 0 end
			`, db.Params{
				"project_id":      key.ProjectId,
				"provider":        key.Provider,
				"category":        key.Category,
				"state":           string(decision.NewState),
				"now":             now,
				"notified":        decision.Notify,
				"max_usable":      decision.Remaining,
				"active_usage":    decision.ActiveUsage,
				"effective_total": decision.EffectiveTotal,
			})
		}
		db.BatchSend(batch)
	})
}

func promiseExpiryPersistStates(now time.Time, updates map[promiseExpiryStateKey]time.Time) {
	if len(updates) == 0 {
		return
	}
	db.NewTx0(func(tx *db.Transaction) {
		batch := db.BatchNew(tx)
		for key, expiresAt := range updates {
			db.BatchExec(batch, `
				insert into accounting.promise_expiry_notification_state(project_id, provider, category, promise_id, last_notified_at, promise_expires_at)
				values (:project_id, :provider, :category, :promise_id, :now, :expires_at)
				on conflict (project_id, provider, category, promise_id) do update set
					last_notified_at = excluded.last_notified_at,
					promise_expires_at = excluded.promise_expires_at
			`, db.Params{
				"project_id": key.ProjectId,
				"provider":   key.Provider,
				"category":   key.Category,
				"promise_id": int(key.PromiseId),
				"now":        now,
				"expires_at": expiresAt,
			})
		}
		db.BatchSend(batch)
	})
}

type lowFundsProjectNotificationInfo struct {
	Title      string
	Recipients []string
}

func lowFundsProjectInfo(projectIds []string) map[string]lowFundsProjectNotificationInfo {
	result := map[string]lowFundsProjectNotificationInfo{}
	if len(projectIds) == 0 {
		return result
	}
	db.NewTx0(func(tx *db.Transaction) {
		rows := db.Select[struct {
			ProjectId string
			Title     string
			Username  string
			Role      string
		}](tx, `
			select p.id project_id, p.title, pm.username, pm.role
			from project.projects p
			join project.project_members pm on p.id = pm.project_id
			where p.id = some(:project_ids) and (pm.role = 'PI' or pm.role = 'ADMIN')
			order by p.id, pm.username
		`, db.Params{"project_ids": projectIds})
		seen := map[string]bool{}
		for _, row := range rows {
			role := rpc.ProjectRole(row.Role)
			if !role.Satisfies(rpc.ProjectRoleAdmin) {
				continue
			}
			info := result[row.ProjectId]
			info.Title = row.Title
			seenKey := fmt.Sprintf("%s/%s", row.ProjectId, row.Username)
			if !seen[seenKey] {
				info.Recipients = append(info.Recipients, row.Username)
				seen[seenKey] = true
			}
			result[row.ProjectId] = info
		}
	})
	for _, projectId := range projectIds {
		info := result[projectId]
		if info.Title == "" {
			info.Title = projectId
		}
		result[projectId] = info
	}
	return result
}
