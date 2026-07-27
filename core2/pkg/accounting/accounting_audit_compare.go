package accounting

import (
	"bytes"
	"cmp"
	"encoding/json"
	"fmt"
	"html/template"
	"slices"
	"strconv"
	"strings"
	"time"
)

type AccountingAuditComparison struct {
	BeforeCapturedAt time.Time                       `json:"beforeCapturedAt"`
	AfterCapturedAt  time.Time                       `json:"afterCapturedAt"`
	Database         string                          `json:"database"`
	SeverityCounts   map[string]int                  `json:"severityCounts"`
	Projects         []AccountingAuditProjectSummary `json:"projects"`
	Changes          []AccountingAuditChange         `json:"changes"`
	JobUsage         AccountingAuditJobUsageSummary  `json:"jobUsage"`
}

type AccountingAuditJobUsageSummary struct {
	Checked   int `json:"checked"`
	Matched   int `json:"matched"`
	Tolerated int `json:"tolerated"`
	Mismatch  int `json:"mismatch"`
	Skipped   int `json:"skipped"`
}

type AccountingAuditProjectSummary struct {
	Severity       string                       `json:"severity"`
	OwnerKind      string                       `json:"ownerKind"`
	OwnerReference string                       `json:"ownerReference"`
	ProjectTitle   string                       `json:"projectTitle,omitempty"`
	WalletId       AccWalletId                  `json:"walletId"`
	Bucket         string                       `json:"bucket"`
	Unit           string                       `json:"unit"`
	LocalDelta     int64                        `json:"localDelta"`
	FlowDelta      int64                        `json:"flowDelta"`
	ExcessDelta    int64                        `json:"excessDelta"`
	MaxUsableDelta int64                        `json:"maxUsableDelta"`
	LockBefore     bool                         `json:"lockBefore"`
	LockAfter      bool                         `json:"lockAfter"`
	Allocations    int                          `json:"allocations"`
	Changes        int                          `json:"changes"`
	DeltaOverflow  bool                         `json:"deltaOverflow"`
	Before         AccountingAuditWalletMetrics `json:"before"`
	After          AccountingAuditWalletMetrics `json:"after"`
}

// AccountingAuditWalletMetrics is the captured subset that most closely matches the values returned by wallet retrieval.
type AccountingAuditWalletMetrics struct {
	Present         bool   `json:"present"`
	Loaded          bool   `json:"loaded"`
	LocalUsage      int64  `json:"localUsage"`
	TotalUsage      int64  `json:"totalUsage"`
	Quota           int64  `json:"quota"`
	TotalAllocated  int64  `json:"totalAllocated"`
	MaxUsable       int64  `json:"maxUsable"`
	ExcessUsage     int64  `json:"excessUsage"`
	PropagatedUsage int64  `json:"propagatedUsage"`
	ChildUsage      int64  `json:"childUsage"`
	ScopedUsage     int64  `json:"scopedUsage"`
	AllocationCount int    `json:"allocationCount"`
	ParentCount     int    `json:"parentCount"`
	ChildCount      int    `json:"childCount"`
	Locked          bool   `json:"locked"`
	DerivedError    string `json:"derivedError,omitempty"`
}

type AccountingAuditChange struct {
	Severity       string      `json:"severity"`
	OwnerKind      string      `json:"ownerKind,omitempty"`
	OwnerReference string      `json:"ownerReference,omitempty"`
	ProjectTitle   string      `json:"projectTitle,omitempty"`
	WalletId       AccWalletId `json:"walletId,omitempty"`
	Bucket         string      `json:"bucket,omitempty"`
	Entity         string      `json:"entity"`
	EntityId       string      `json:"entityId"`
	Field          string      `json:"field"`
	Before         string      `json:"before,omitempty"`
	After          string      `json:"after,omitempty"`
	Reason         string      `json:"reason"`
}

func CompareAccountingAudits(before, after AccountingAuditCapture) (AccountingAuditComparison, error) {
	if err := validateAccountingAuditCapture(before); err != nil {
		return AccountingAuditComparison{}, fmt.Errorf("invalid before capture: %w", err)
	}
	if err := validateAccountingAuditCapture(after); err != nil {
		return AccountingAuditComparison{}, fmt.Errorf("invalid after capture: %w", err)
	}
	if before.Database != after.Database {
		return AccountingAuditComparison{}, fmt.Errorf("captures are from different databases: %q and %q", before.Database, after.Database)
	}
	if !after.CapturedAt.After(before.CapturedAt) {
		return AccountingAuditComparison{}, fmt.Errorf("after capture must be newer than before capture")
	}
	result := AccountingAuditComparison{
		BeforeCapturedAt: before.CapturedAt,
		AfterCapturedAt:  after.CapturedAt,
		Database:         before.Database,
		SeverityCounts:   map[string]int{"critical": 0, "high": 0, "medium": 0, "info": 0},
		Changes:          []AccountingAuditChange{},
	}

	beforeWallets := auditWalletMap(before.Wallets)
	afterWallets := auditWalletMap(after.Wallets)
	for id, oldWallet := range beforeWallets {
		newWallet, exists := afterWallets[id]
		if !exists {
			appendAccountingAuditChange(&result, auditChangeForWallet(oldWallet, "critical", "wallet", fmt.Sprint(id), "existence", "present", "deleted", "wallet was deleted"))
			continue
		}
		compareAccountingAuditWallet(&result, oldWallet, newWallet)
	}
	for id, wallet := range afterWallets {
		if _, exists := beforeWallets[id]; !exists {
			appendAccountingAuditChange(&result, auditChangeForWallet(wallet, "high", "wallet", fmt.Sprint(id), "existence", "absent", "present", "wallet was added"))
		}
	}

	compareAccountingAuditGroups(&result, before.Groups, after.Groups, beforeWallets, afterWallets)
	compareAccountingAuditAllocations(&result, before.Allocations, after.Allocations, beforeWallets, afterWallets)
	compareAccountingAuditScopes(&result, before.Scopes, after.Scopes, beforeWallets, afterWallets)
	compareAccountingAuditJobUsage(&result, after.JobUsage, beforeWallets, afterWallets)
	compareAccountingAuditFindings(&result, before.Findings, after.Findings, beforeWallets, afterWallets)

	slices.SortFunc(result.Changes, compareAccountingAuditChanges)
	result.Projects = summarizeAccountingAuditProjects(result.Changes, beforeWallets, afterWallets)
	return result, nil
}

func compareAccountingAuditJobUsage(result *AccountingAuditComparison, checks []AccountingAuditJobUsage, beforeWallets, afterWallets map[AccWalletId]AccountingAuditWallet) {
	result.JobUsage.Checked = len(checks)
	for _, check := range checks {
		wallet := auditWalletForChange(check.WalletId, beforeWallets, afterWallets)
		if check.Error != "" {
			result.JobUsage.Skipped++
			appendAccountingAuditChange(result, auditChangeForWallet(wallet, "medium", "job-usage", check.Key, "verification", fmt.Sprint(check.ScopedUsage), "not checked", check.Error))
		} else if check.ScopedUsage >= check.ExpectedUsageMin && check.ScopedUsage <= check.ExpectedUsageMax {
			result.JobUsage.Matched++
		} else if check.ScopedUsage >= check.AcceptedUsageMin && check.ScopedUsage <= check.AcceptedUsageMax {
			result.JobUsage.Tolerated++
		} else {
			result.JobUsage.Mismatch++
			expected := accountingAuditInt64Range(check.ExpectedUsageMin, check.ExpectedUsageMax)
			accepted := accountingAuditInt64Range(check.AcceptedUsageMin, check.AcceptedUsageMax)
			usageDistance := check.ExpectedUsageMin - check.ScopedUsage
			if check.ScopedUsage > check.ExpectedUsageMax {
				usageDistance = check.ScopedUsage - check.ExpectedUsageMax
			}
			minuteDistance := float64(usageDistance) / check.UsagePerMinute
			reason := fmt.Sprintf("%d RUNNING minutes from %s; expected %s, one-minute tolerance %s; off by %d accounting units (approximately %.1f wall minutes)", check.RuntimeMinutes, check.RuntimeSource, expected, accepted, usageDistance, minuteDistance)
			appendAccountingAuditChange(result, auditChangeForWallet(wallet, "critical", "job-usage", check.Key, "usage", fmt.Sprint(check.ScopedUsage), expected, reason))
		}
	}
}

func accountingAuditInt64Range(minimum, maximum int64) string {
	if minimum == maximum {
		return fmt.Sprint(minimum)
	}
	return fmt.Sprintf("%d..%d", minimum, maximum)
}

func auditWalletMap(wallets []AccountingAuditWallet) map[AccWalletId]AccountingAuditWallet {
	result := make(map[AccWalletId]AccountingAuditWallet, len(wallets))
	for _, wallet := range wallets {
		result[wallet.Id] = wallet
	}
	return result
}

func compareAccountingAuditWallet(result *AccountingAuditComparison, before, after AccountingAuditWallet) {
	identity := []struct {
		field  string
		before string
		after  string
	}{
		{"ownerId", fmt.Sprint(before.OwnerId), fmt.Sprint(after.OwnerId)},
		{"ownerKind", before.OwnerKind, after.OwnerKind},
		{"ownerReference", before.OwnerReference, after.OwnerReference},
		{"provider", before.Provider, after.Provider},
		{"category", before.Category, after.Category},
		{"accountingFrequency", before.AccountingFrequency, after.AccountingFrequency},
		{"unitName", before.UnitName, after.UnitName},
		{"unitNamePlural", before.UnitNamePlural, after.UnitNamePlural},
		{"unitFloatingPoint", strconv.FormatBool(before.UnitFloatingPoint), strconv.FormatBool(after.UnitFloatingPoint)},
	}
	for _, value := range identity {
		if value.before != value.after {
			appendAccountingAuditChange(result, auditChangeForWallet(after, "critical", "wallet", fmt.Sprint(after.Id), value.field, value.before, value.after, "wallet identity changed"))
		}
	}
	if before.ProjectTitle != after.ProjectTitle {
		appendAccountingAuditChange(result, auditChangeForWallet(after, "info", "wallet", fmt.Sprint(after.Id), "projectTitle", before.ProjectTitle, after.ProjectTitle, "project title changed"))
	}
	if before.ParentProjectId != after.ParentProjectId {
		appendAccountingAuditChange(result, auditChangeForWallet(after, "medium", "wallet", fmt.Sprint(after.Id), "parentProjectId", before.ParentProjectId, after.ParentProjectId, "organizational parent changed"))
	}

	compareAuditInt64(result, after, "localUsage", before.LocalUsage, after.LocalUsage, "high", "provider-reported usage changed")
	compareAuditInt64(result, after, "localRetiredUsage", before.LocalRetiredUsage, after.LocalRetiredUsage, "info", "legacy retired local usage was reset")
	compareAuditInt64(result, after, "scopedUsage", before.ScopedUsage, after.ScopedUsage, "high", "sum of scoped usage changed")
	compareAuditInt64(result, after, "childUsage", before.ChildUsage, after.ChildUsage, "medium", "usage received from child wallets changed")
	compareAuditInt64(result, after, "totalUsage", before.TotalUsage, after.TotalUsage, "medium", "wallet total usage changed")
	compareAuditInt64(result, after, "propagatedUsage", before.PropagatedUsage, after.PropagatedUsage, "medium", "usage routed to parent wallets changed")
	compareAuditInt64(result, after, "recomputedExcessUsage", before.RecomputedExcessUsage, after.RecomputedExcessUsage, "high", "usage not covered by parent flow changed")
	compareAuditInt64(result, after, "incomingContributingQuota", before.IncomingContributingQuota, after.IncomingContributingQuota, "high", "incoming usable quota changed")
	compareAuditInt64(result, after, "outgoingContributingQuota", before.OutgoingContributingQuota, after.OutgoingContributingQuota, "medium", "quota granted to child wallets changed")
	compareAuditInt64(result, after, "recomputedRetiredAllocated", before.RecomputedRetiredAllocated, after.RecomputedRetiredAllocated, "medium", "retired allocation total changed")
	if before.DerivedError == "" && after.DerivedError == "" {
		compareAuditInt64(result, after, "maxUsable", before.MaxUsable, after.MaxUsable, "high", "additional routable usage changed")
	}
	compareAuditInt64(result, after, "persistedExcessUsage", before.PersistedExcessUsage, after.PersistedExcessUsage, "info", "persisted legacy cache changed")
	compareAuditInt64(result, after, "persistedTotalAllocated", before.PersistedTotalAllocated, after.PersistedTotalAllocated, "info", "persisted legacy cache changed")
	compareAuditInt64(result, after, "persistedRetiredAllocated", before.PersistedRetiredAllocated, after.PersistedRetiredAllocated, "info", "persisted legacy cache changed")
	compareAuditInt64(result, after, "scopeCount", int64(before.ScopeCount), int64(after.ScopeCount), "medium", "number of scoped usage baselines changed")
	compareAuditInt64(result, after, "allocationCount", int64(before.AllocationCount), int64(after.AllocationCount), "high", "number of allocations belonging to wallet changed")
	compareAuditInt64(result, after, "parentCount", int64(before.ParentCount), int64(after.ParentCount), "medium", "number of funding parents changed")
	compareAuditInt64(result, after, "childCount", int64(before.ChildCount), int64(after.ChildCount), "medium", "number of funded children changed")
	if !before.LastSignificantUpdateAt.Equal(after.LastSignificantUpdateAt) {
		appendAccountingAuditChange(result, auditChangeForWallet(after, "info", "wallet", fmt.Sprint(after.Id), "lastSignificantUpdateAt", before.LastSignificantUpdateAt.Format(time.RFC3339Nano), after.LastSignificantUpdateAt.Format(time.RFC3339Nano), "last significant accounting update changed"))
	}
	if before.Loaded != after.Loaded {
		appendAccountingAuditChange(result, auditChangeForWallet(after, "high", "wallet", fmt.Sprint(after.Id), "loaded", strconv.FormatBool(before.Loaded), strconv.FormatBool(after.Loaded), "wallet loadability changed"))
	}
	if before.DerivedError == "" && after.DerivedError == "" && before.DerivedLocked != after.DerivedLocked {
		appendAccountingAuditChange(result, auditChangeForWallet(after, "high", "wallet", fmt.Sprint(after.Id), "derivedLocked", strconv.FormatBool(before.DerivedLocked), strconv.FormatBool(after.DerivedLocked), "derived wallet availability changed"))
	}

	if before.WasLocked != after.WasLocked {
		severity := "high"
		reason := "persisted wallet lock state changed"
		if !before.WasLocked && after.WasLocked && before.AllocationCount == 0 && after.AllocationCount == 0 {
			severity = "info"
			reason = "wallet with no allocations changed from the buggy initial unlocked state to locked"
		} else {
			appendAccountingAuditChange(result, auditChangeForWallet(after, severity, "wallet", fmt.Sprint(after.Id), "wasLocked", strconv.FormatBool(before.WasLocked), strconv.FormatBool(after.WasLocked), reason))
		}
	}
	if before.DerivedError != after.DerivedError {
		appendAccountingAuditChange(result, auditChangeForWallet(after, "medium", "wallet", fmt.Sprint(after.Id), "derivedError", before.DerivedError, after.DerivedError, "availability of derived accounting values changed"))
	}
}

func compareAuditInt64(result *AccountingAuditComparison, wallet AccountingAuditWallet, field string, before, after int64, severity, reason string) {
	if before == after {
		return
	}
	appendAccountingAuditChange(result, auditChangeForWallet(wallet, severity, "wallet", fmt.Sprint(wallet.Id), field, fmt.Sprint(before), fmt.Sprint(after), reason))
}

func compareAccountingAuditGroups(result *AccountingAuditComparison, before, after []AccountingAuditGroup, beforeWallets, afterWallets map[AccWalletId]AccountingAuditWallet) {
	oldItems := map[accGroupId]AccountingAuditGroup{}
	newItems := map[accGroupId]AccountingAuditGroup{}
	for _, item := range before {
		oldItems[item.Id] = item
	}
	for _, item := range after {
		newItems[item.Id] = item
	}
	for id, oldItem := range oldItems {
		newItem, exists := newItems[id]
		wallet := auditWalletForChange(oldItem.AssociatedWallet, beforeWallets, afterWallets)
		if !exists {
			appendAccountingAuditChange(result, auditChangeForWallet(wallet, "high", "group", fmt.Sprint(id), "existence", "present", "deleted", "allocation group was deleted"))
			continue
		}
		wallet = auditWalletForChange(newItem.AssociatedWallet, beforeWallets, afterWallets)
		if oldItem.AssociatedWallet != newItem.AssociatedWallet || oldItem.ParentWallet != newItem.ParentWallet {
			appendAccountingAuditChange(result, auditChangeForWallet(wallet, "critical", "group", fmt.Sprint(id), "endpoints", fmt.Sprintf("%d->%d", oldItem.ParentWallet, oldItem.AssociatedWallet), fmt.Sprintf("%d->%d", newItem.ParentWallet, newItem.AssociatedWallet), "allocation group endpoints changed"))
		}
		compareAuditEntityInt64(result, wallet, "group", fmt.Sprint(id), "treeUsage", oldItem.TreeUsage, newItem.TreeUsage, "medium", "routed usage changed")
		//compareAuditEntityInt64(result, wallet, "group", fmt.Sprint(id), "retiredTreeUsage", oldItem.RetiredTreeUsage, newItem.RetiredTreeUsage, "info", "legacy retired tree usage was reset")
		compareAuditEntityInt64(result, wallet, "group", fmt.Sprint(id), "contributingQuota", oldItem.ContributingQuota, newItem.ContributingQuota, "medium", "group contributing quota changed")
		compareAuditEntityInt64(result, wallet, "group", fmt.Sprint(id), "retiredUsageFloor", oldItem.RetiredUsageFloor, newItem.RetiredUsageFloor, "medium", "retired usage attribution changed")
		compareAuditEntityInt64(result, wallet, "group", fmt.Sprint(id), "allocationCount", int64(oldItem.AllocationCount), int64(newItem.AllocationCount), "high", "number of allocations in group changed")
	}
	for id, item := range newItems {
		if _, exists := oldItems[id]; !exists {
			wallet := auditWalletForChange(item.AssociatedWallet, beforeWallets, afterWallets)
			appendAccountingAuditChange(result, auditChangeForWallet(wallet, "high", "group", fmt.Sprint(id), "existence", "absent", "present", "allocation group was added"))
		}
	}
}

func compareAccountingAuditAllocations(result *AccountingAuditComparison, before, after []AccountingAuditAllocation, beforeWallets, afterWallets map[AccWalletId]AccountingAuditWallet) {
	oldItems := map[accAllocId]AccountingAuditAllocation{}
	newItems := map[accAllocId]AccountingAuditAllocation{}
	for _, item := range before {
		oldItems[item.Id] = item
	}
	for _, item := range after {
		newItems[item.Id] = item
	}
	for id, oldItem := range oldItems {
		newItem, exists := newItems[id]
		wallet := auditWalletForChange(oldItem.AssociatedWallet, beforeWallets, afterWallets)
		if !exists {
			appendAccountingAuditChange(result, auditChangeForWallet(wallet, "high", "allocation", fmt.Sprint(id), "existence", "present", "deleted", "allocation was deleted"))
			continue
		}
		wallet = auditWalletForChange(newItem.AssociatedWallet, beforeWallets, afterWallets)
		if oldItem.GroupId != newItem.GroupId || oldItem.AssociatedWallet != newItem.AssociatedWallet || oldItem.ParentWallet != newItem.ParentWallet {
			appendAccountingAuditChange(result, auditChangeForWallet(wallet, "critical", "allocation", fmt.Sprint(id), "placement", fmt.Sprintf("group=%d %d->%d", oldItem.GroupId, oldItem.ParentWallet, oldItem.AssociatedWallet), fmt.Sprintf("group=%d %d->%d", newItem.GroupId, newItem.ParentWallet, newItem.AssociatedWallet), "allocation placement changed"))
		}
		if oldItem.GrantId != newItem.GrantId {
			appendAccountingAuditChange(result, auditChangeForWallet(wallet, "critical", "allocation", fmt.Sprint(id), "grantId", fmt.Sprint(oldItem.GrantId), fmt.Sprint(newItem.GrantId), "grant association changed"))
		}
		if !oldItem.Start.Equal(newItem.Start) || !oldItem.End.Equal(newItem.End) {
			appendAccountingAuditChange(result, auditChangeForWallet(wallet, "high", "allocation", fmt.Sprint(id), "interval", oldItem.Start.Format(time.RFC3339)+" / "+oldItem.End.Format(time.RFC3339), newItem.Start.Format(time.RFC3339)+" / "+newItem.End.Format(time.RFC3339), "allocation validity interval changed"))
		}
		compareAuditEntityInt64(result, wallet, "allocation", fmt.Sprint(id), "quota", oldItem.Quota, newItem.Quota, "high", "allocation quota changed")
		compareAuditEntityInt64(result, wallet, "allocation", fmt.Sprint(id), "retiredUsage", oldItem.RetiredUsage, newItem.RetiredUsage, "medium", "retired usage attribution changed")
		compareAuditEntityInt64(result, wallet, "allocation", fmt.Sprint(id), "retiredQuota", oldItem.RetiredQuota, newItem.RetiredQuota, "medium", "retirement rewind quota changed")
		if oldItem.Retired != newItem.Retired {
			appendAccountingAuditChange(result, auditChangeForWallet(wallet, "high", "allocation", fmt.Sprint(id), "retired", strconv.FormatBool(oldItem.Retired), strconv.FormatBool(newItem.Retired), "allocation retirement state changed"))
		}
	}
	for id, item := range newItems {
		if _, exists := oldItems[id]; !exists {
			wallet := auditWalletForChange(item.AssociatedWallet, beforeWallets, afterWallets)
			appendAccountingAuditChange(result, auditChangeForWallet(wallet, "high", "allocation", fmt.Sprint(id), "existence", "absent", "present", "allocation was added"))
		}
	}
}

func compareAccountingAuditScopes(result *AccountingAuditComparison, before, after []AccountingAuditScope, beforeWallets, afterWallets map[AccWalletId]AccountingAuditWallet) {
	key := func(scope AccountingAuditScope) string { return fmt.Sprintf("%d\x00%s", scope.WalletId, scope.Key) }
	oldItems := map[string]AccountingAuditScope{}
	newItems := map[string]AccountingAuditScope{}
	for _, item := range before {
		oldItems[key(item)] = item
	}
	for _, item := range after {
		newItems[key(item)] = item
	}
	for id, oldItem := range oldItems {
		newItem, exists := newItems[id]
		wallet := auditWalletForChange(oldItem.WalletId, beforeWallets, afterWallets)
		if !exists {
			appendAccountingAuditChange(result, auditChangeForWallet(wallet, "critical", "scope", oldItem.Key, "existence", "present", "deleted", "scoped usage baseline was deleted"))
			continue
		}
		compareAuditEntityInt64(result, wallet, "scope", oldItem.Key, "usage", oldItem.Usage, newItem.Usage, "critical", "scoped usage baseline changed")
		if !oldItem.LastUpdatedAt.Equal(newItem.LastUpdatedAt) {
			appendAccountingAuditChange(result, auditChangeForWallet(wallet, "medium", "scope", oldItem.Key, "lastUpdatedAt", oldItem.LastUpdatedAt.Format(time.RFC3339Nano), newItem.LastUpdatedAt.Format(time.RFC3339Nano), "scope timestamp changed"))
		}
	}
	for id, item := range newItems {
		if _, exists := oldItems[id]; !exists {
			severity := "high"
			reason := "scoped usage baseline was added"
			if item.Key == accountingUnscopedRepairKey {
				severity = "info"
				reason = "mixed reflow added the reserved unscoped baseline"
			}
			wallet := auditWalletForChange(item.WalletId, beforeWallets, afterWallets)
			appendAccountingAuditChange(result, auditChangeForWallet(wallet, severity, "scope", item.Key, "existence", "absent", "present", reason))
		}
	}
}

func compareAccountingAuditFindings(result *AccountingAuditComparison, before, after []AccountingAuditFinding, beforeWallets, afterWallets map[AccWalletId]AccountingAuditWallet) {
	key := accountingAuditFindingKey
	oldItems := map[string]AccountingAuditFinding{}
	newItems := map[string]AccountingAuditFinding{}
	for _, item := range before {
		oldItems[key(item)] = item
	}
	for _, item := range after {
		newItems[key(item)] = item
	}
	for id, item := range oldItems {
		newItem, exists := newItems[id]
		if exists {
			if item.Details != newItem.Details || item.Impact != newItem.Impact {
				wallet := walletForAuditFinding(newItem, beforeWallets, afterWallets)
				change := auditChangeForWallet(wallet, "medium", "finding", item.Code, "details", item.Details, newItem.Details, "existing accounting finding changed")
				change.Bucket = item.Bucket
				appendAccountingAuditChange(result, change)
			}
			continue
		}
		//wallet := walletForAuditFinding(item, beforeWallets, afterWallets)
		//change := auditChangeForWallet(wallet, "info", "finding", item.Code, "existence", "present", "resolved", "accounting finding was resolved")
		//change.Bucket = item.Bucket
		//appendAccountingAuditChange(result, change)
	}
	for id, item := range newItems {
		if _, exists := oldItems[id]; exists {
			continue
		}
		wallet := walletForAuditFinding(item, beforeWallets, afterWallets)
		change := auditChangeForWallet(wallet, "critical", "finding", item.Code, "existence", "absent", "present", "new accounting finding: "+item.Details)
		change.Bucket = item.Bucket
		appendAccountingAuditChange(result, change)
	}
}

func accountingAuditFindingKey(item AccountingAuditFinding) string {
	walletIds := append([]AccWalletId(nil), item.WalletIds...)
	groupIds := append([]accGroupId(nil), item.GroupIds...)
	allocationIds := append([]accAllocId(nil), item.AllocationIds...)
	slices.Sort(walletIds)
	slices.Sort(groupIds)
	slices.Sort(allocationIds)
	identity := fmt.Sprintf("%s\x00%s\x00%v\x00%v\x00%v", item.Bucket, item.Code, walletIds, groupIds, allocationIds)
	if len(walletIds) == 0 && len(groupIds) == 0 && len(allocationIds) == 0 {
		identity += "\x00" + item.Details
	}
	return identity
}

func walletForAuditFinding(item AccountingAuditFinding, before, after map[AccWalletId]AccountingAuditWallet) AccountingAuditWallet {
	if len(item.WalletIds) == 0 {
		return AccountingAuditWallet{}
	}
	return auditWalletForChange(item.WalletIds[0], before, after)
}

func auditWalletForChange(id AccWalletId, before, after map[AccWalletId]AccountingAuditWallet) AccountingAuditWallet {
	if wallet, ok := after[id]; ok {
		return wallet
	}
	return before[id]
}

func compareAuditEntityInt64(result *AccountingAuditComparison, wallet AccountingAuditWallet, entity, entityId, field string, before, after int64, severity, reason string) {
	if before == after {
		return
	}
	appendAccountingAuditChange(result, auditChangeForWallet(wallet, severity, entity, entityId, field, fmt.Sprint(before), fmt.Sprint(after), reason))
}

func auditChangeForWallet(wallet AccountingAuditWallet, severity, entity, entityId, field, before, after, reason string) AccountingAuditChange {
	return AccountingAuditChange{
		Severity: severity, OwnerKind: wallet.OwnerKind, OwnerReference: wallet.OwnerReference,
		ProjectTitle: wallet.ProjectTitle, WalletId: wallet.Id, Bucket: auditWalletBucket(wallet),
		Entity: entity, EntityId: entityId, Field: field, Before: before, After: after, Reason: reason,
	}
}

func auditWalletBucket(wallet AccountingAuditWallet) string {
	if wallet.Provider == "" && wallet.Category == "" {
		return ""
	}
	return wallet.Provider + "/" + wallet.Category
}

func appendAccountingAuditChange(result *AccountingAuditComparison, change AccountingAuditChange) {
	result.Changes = append(result.Changes, change)
	result.SeverityCounts[change.Severity]++
}

func accountingAuditSeverityRank(severity string) int {
	switch severity {
	case "critical":
		return 0
	case "high":
		return 1
	case "medium":
		return 2
	default:
		return 3
	}
}

func compareAccountingAuditChanges(a, b AccountingAuditChange) int {
	if order := strings.Compare(accountingAuditBucketCategory(a.Bucket), accountingAuditBucketCategory(b.Bucket)); order != 0 {
		return order
	}
	if order := strings.Compare(accountingAuditOwnerDisplay(a.ProjectTitle, a.OwnerReference), accountingAuditOwnerDisplay(b.ProjectTitle, b.OwnerReference)); order != 0 {
		return order
	}
	if order := strings.Compare(a.Bucket, b.Bucket); order != 0 {
		return order
	}
	if order := strings.Compare(a.OwnerReference, b.OwnerReference); order != 0 {
		return order
	}
	if order := cmp.Compare(accountingAuditSeverityRank(a.Severity), accountingAuditSeverityRank(b.Severity)); order != 0 {
		return order
	}
	if order := cmp.Compare(a.WalletId, b.WalletId); order != 0 {
		return order
	}
	if order := strings.Compare(a.Entity, b.Entity); order != 0 {
		return order
	}
	if order := strings.Compare(a.EntityId, b.EntityId); order != 0 {
		return order
	}
	return strings.Compare(a.Field, b.Field)
}

func accountingAuditBucketCategory(bucket string) string {
	if _, category, ok := strings.Cut(bucket, "/"); ok {
		return category
	}
	return bucket
}

func accountingAuditOwnerDisplay(projectTitle, ownerReference string) string {
	if projectTitle != "" {
		return projectTitle
	}
	return ownerReference
}

func summarizeAccountingAuditProjects(changes []AccountingAuditChange, before, after map[AccWalletId]AccountingAuditWallet) []AccountingAuditProjectSummary {
	resultByWallet := map[AccWalletId]*AccountingAuditProjectSummary{}
	for _, change := range changes {
		if change.WalletId == 0 {
			continue
		}
		summary := resultByWallet[change.WalletId]
		if summary == nil {
			oldWallet := before[change.WalletId]
			newWallet := after[change.WalletId]
			wallet := newWallet
			if wallet.Id == 0 {
				wallet = oldWallet
			}
			localDelta, localOverflow := checkedAccountingSub(newWallet.LocalUsage, oldWallet.LocalUsage)
			flowDelta, flowOverflow := checkedAccountingSub(newWallet.PropagatedUsage, oldWallet.PropagatedUsage)
			excessDelta, excessOverflow := checkedAccountingSub(newWallet.RecomputedExcessUsage, oldWallet.RecomputedExcessUsage)
			maxUsableDelta, maxUsableOverflow := checkedAccountingSub(newWallet.MaxUsable, oldWallet.MaxUsable)
			summary = &AccountingAuditProjectSummary{
				Severity: change.Severity, OwnerKind: wallet.OwnerKind, OwnerReference: wallet.OwnerReference,
				ProjectTitle: wallet.ProjectTitle, WalletId: wallet.Id, Bucket: auditWalletBucket(wallet),
				Unit: wallet.UnitNamePlural, LocalDelta: localDelta,
				FlowDelta: flowDelta, ExcessDelta: excessDelta, MaxUsableDelta: maxUsableDelta,
				LockBefore: oldWallet.WasLocked, LockAfter: newWallet.WasLocked,
				Allocations:   newWallet.AllocationCount,
				DeltaOverflow: localOverflow || flowOverflow || excessOverflow || maxUsableOverflow,
				Before:        accountingAuditWalletMetrics(oldWallet),
				After:         accountingAuditWalletMetrics(newWallet),
			}
			resultByWallet[change.WalletId] = summary
		}
		if accountingAuditSeverityRank(change.Severity) < accountingAuditSeverityRank(summary.Severity) {
			summary.Severity = change.Severity
		}
		summary.Changes++
	}
	result := make([]AccountingAuditProjectSummary, 0, len(resultByWallet))
	for _, summary := range resultByWallet {
		if summary.Severity == "info" {
			continue
		}
		result = append(result, *summary)
	}
	slices.SortFunc(result, func(a, b AccountingAuditProjectSummary) int {
		if order := strings.Compare(accountingAuditBucketCategory(a.Bucket), accountingAuditBucketCategory(b.Bucket)); order != 0 {
			return order
		}
		if order := strings.Compare(accountingAuditOwnerDisplay(a.ProjectTitle, a.OwnerReference), accountingAuditOwnerDisplay(b.ProjectTitle, b.OwnerReference)); order != 0 {
			return order
		}
		if order := strings.Compare(a.Bucket, b.Bucket); order != 0 {
			return order
		}
		if order := strings.Compare(a.OwnerReference, b.OwnerReference); order != 0 {
			return order
		}
		return cmp.Compare(accountingAuditSeverityRank(a.Severity), accountingAuditSeverityRank(b.Severity))
	})
	return result
}

func accountingAuditWalletMetrics(wallet AccountingAuditWallet) AccountingAuditWalletMetrics {
	return AccountingAuditWalletMetrics{
		Present: wallet.Id != 0, Loaded: wallet.Loaded,
		LocalUsage: wallet.LocalUsage, TotalUsage: wallet.TotalUsage,
		Quota: wallet.IncomingContributingQuota, TotalAllocated: wallet.OutgoingContributingQuota,
		MaxUsable: wallet.MaxUsable, ExcessUsage: wallet.RecomputedExcessUsage,
		PropagatedUsage: wallet.PropagatedUsage, ChildUsage: wallet.ChildUsage, ScopedUsage: wallet.ScopedUsage,
		AllocationCount: wallet.AllocationCount, ParentCount: wallet.ParentCount, ChildCount: wallet.ChildCount,
		Locked: wallet.WasLocked, DerivedError: wallet.DerivedError,
	}
}

func RenderAccountingAuditTerminal(report AccountingAuditComparison) []byte {
	var output bytes.Buffer
	fmt.Fprintf(&output, "Accounting reflow consequence report\n")
	fmt.Fprintf(&output, "Database: %s\n", report.Database)
	fmt.Fprintf(&output, "Window: %s -> %s\n", report.BeforeCapturedAt.UTC().Format(time.RFC3339), report.AfterCapturedAt.UTC().Format(time.RFC3339))
	fmt.Fprintf(&output, "Changes: %d  critical=%d high=%d medium=%d info=%d\n\n", len(report.Changes), report.SeverityCounts["critical"], report.SeverityCounts["high"], report.SeverityCounts["medium"], report.SeverityCounts["info"])
	fmt.Fprintf(&output, "Job usage checks: %d  matched=%d tolerated=%d mismatch=%d skipped=%d\n\n", report.JobUsage.Checked, report.JobUsage.Matched, report.JobUsage.Tolerated, report.JobUsage.Mismatch, report.JobUsage.Skipped)
	fmt.Fprintf(&output, "Changed wallets\n")
	for _, project := range report.Projects {
		if project.Severity == "info" {
			continue
		}
		lock := "unchanged"
		if project.LockBefore != project.LockAfter {
			lock = fmt.Sprintf("%t -> %t", project.LockBefore, project.LockAfter)
		}
		name := safeAccountingAuditTerminal(accountingAuditOwnerDisplay(project.ProjectTitle, project.OwnerReference))
		overflow := ""
		if project.DeltaOverflow {
			overflow = " delta-overflow=true"
		}
		fmt.Fprintf(&output, "%-8s %-36s %-28s wallet=%d unit=%s local=%+d flow=%+d excess=%+d maxUsable=%+d lock=%s changes=%d%s\n", strings.ToUpper(project.Severity), name, safeAccountingAuditTerminal(project.Bucket), project.WalletId, safeAccountingAuditTerminal(project.Unit), project.LocalDelta, project.FlowDelta, project.ExcessDelta, project.MaxUsableDelta, lock, project.Changes, overflow)
	}
	fmt.Fprintf(&output, "\nAll changes\n")
	for _, change := range report.Changes {
		owner := safeAccountingAuditTerminal(change.OwnerKind + ":" + change.OwnerReference)
		if owner == "" {
			owner = "global"
		} else if change.OwnerReference == "" {
			owner = "global"
		}
		fmt.Fprintf(&output, "%-8s %-24s %-24s %s:%s %s: %q -> %q (%s)\n", strings.ToUpper(change.Severity), owner, safeAccountingAuditTerminal(change.Bucket), safeAccountingAuditTerminal(change.Entity), safeAccountingAuditTerminal(change.EntityId), safeAccountingAuditTerminal(change.Field), change.Before, change.After, safeAccountingAuditTerminal(change.Reason))
	}
	fmt.Fprintf(&output, "\nNote: current accounting state is compared; historical wallet snapshots and usage reports are not rewritten by reflow.\n")
	return output.Bytes()
}

func safeAccountingAuditTerminal(value string) string {
	return strings.Map(func(char rune) rune {
		if char < 32 || char == 127 {
			return '?'
		}
		return char
	}, value)
}

func RenderAccountingAuditHTML(report AccountingAuditComparison) ([]byte, error) {
	// Keep rendering deterministic even when callers construct a report without CompareAccountingAudits.
	report.Projects = append([]AccountingAuditProjectSummary(nil), report.Projects...)
	report.Projects = slices.DeleteFunc(report.Projects, func(project AccountingAuditProjectSummary) bool {
		return project.Severity == "info"
	})
	report.Changes = append([]AccountingAuditChange(nil), report.Changes...)
	slices.SortFunc(report.Projects, func(a, b AccountingAuditProjectSummary) int {
		if order := strings.Compare(accountingAuditBucketCategory(a.Bucket), accountingAuditBucketCategory(b.Bucket)); order != 0 {
			return order
		}
		if order := strings.Compare(accountingAuditOwnerDisplay(a.ProjectTitle, a.OwnerReference), accountingAuditOwnerDisplay(b.ProjectTitle, b.OwnerReference)); order != 0 {
			return order
		}
		if order := strings.Compare(a.Bucket, b.Bucket); order != 0 {
			return order
		}
		if order := strings.Compare(a.OwnerReference, b.OwnerReference); order != 0 {
			return order
		}
		return cmp.Compare(a.WalletId, b.WalletId)
	})
	slices.SortFunc(report.Changes, compareAccountingAuditChanges)
	encodedReport, err := json.Marshal(report)
	if err != nil {
		return nil, err
	}

	const page = `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Accounting reflow consequence report</title><style>
:root{color-scheme:light dark;--critical:#c62828;--high:#ef6c00;--medium:#8a6d00;--info:#326ca8;--line:#8885;--surface:#fff;--row-height:40px}*{box-sizing:border-box}body{font:14px/1.45 system-ui,sans-serif;margin:0;padding:2rem;background:#f5f3ee;color:#202124}main{max-width:1600px;margin:auto}.hero{background:#17212b;color:#fff;padding:1.5rem 2rem;border-radius:12px}h1{margin:0 0 .4rem}h2{margin-bottom:.35rem}.counts{display:flex;gap:.7rem;flex-wrap:wrap;margin-top:1rem}.badge{padding:.35rem .65rem;border-radius:99px;background:#ffffff18}section{margin-top:1.5rem;background:var(--surface);padding:1rem;border-radius:10px;box-shadow:0 2px 12px #0001}.explanation{max-width:1000px;margin:.25rem 0 1rem;color:#555}.controls{display:flex;align-items:center;gap:.6rem;flex-wrap:wrap;margin-bottom:.8rem}input,select{padding:.6rem;border:1px solid var(--line);border-radius:6px;background:var(--surface);color:inherit}.result-count,.muted{color:#666}.vtable{height:min(62vh,650px);min-height:280px;overflow:auto;border:1px solid var(--line);border-radius:7px;position:relative}.vhead,.vrow{display:grid;grid-template-columns:var(--columns);min-width:var(--table-width);align-items:center}.vhead{position:sticky;top:0;height:42px;background:var(--surface);z-index:2;border-bottom:2px solid var(--line);font-weight:700}.vcanvas{position:relative;min-width:var(--table-width)}.vrow{position:absolute;left:0;right:0;height:var(--row-height);border-bottom:1px solid var(--line)}.vrow.critical{border-left:5px solid var(--critical)}.vrow.high{border-left:5px solid var(--high)}.vrow.medium{border-left:5px solid var(--medium)}.vrow.info{border-left:5px solid var(--info)}.cell{padding:.55rem;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.sev{font-weight:700;text-transform:uppercase}.empty{padding:2rem;color:#666}.status-note{padding:.75rem 1rem;background:#326ca812;border-left:4px solid var(--info);border-radius:4px}.why{font-weight:600}@media(max-width:700px){body{padding:.75rem}.hero{padding:1.1rem}section{padding:.7rem}.vtable{height:60vh}}@media(prefers-color-scheme:dark){body{background:#111820;color:#e8eaed;--surface:#1d2731}.explanation,.muted,.result-count,.empty{color:#aaa}}
.vrow.selectable{cursor:pointer}.vrow.selectable:hover,.vrow.selectable:focus{background:#326ca814;outline:2px solid #326ca855;outline-offset:-2px}.wallet-detail{margin-top:1rem;padding:1rem;border:1px solid var(--line);border-radius:7px}.wallet-detail h3{margin:0 0 .25rem}.metric-table{width:100%;border-collapse:collapse;margin-top:.8rem}.metric-table th,.metric-table td{text-align:right;padding:.45rem .6rem;border-bottom:1px solid var(--line);white-space:nowrap}.metric-table th:first-child,.metric-table td:first-child{text-align:left}.metric-table th{font-weight:700}.metric-core td:first-child{font-weight:650}@media(max-width:700px){.wallet-detail{overflow:auto}}
</style></head><body><main><div class="hero"><h1>Accounting reflow consequence report</h1><div>{{.Database}}</div><div>{{time .BeforeCapturedAt}} &rarr; {{time .AfterCapturedAt}}</div><div class="counts"><span class="badge">{{len .Changes}} changes</span><span class="badge">{{count .SeverityCounts "critical"}} critical</span><span class="badge">{{count .SeverityCounts "high"}} high</span><span class="badge">{{count .SeverityCounts "medium"}} medium</span><span class="badge">{{count .SeverityCounts "info"}} info</span></div></div>
<section><h2>Changed wallets</h2><p class="explanation"><strong>One row represents one wallet with at least one medium, high, or critical change.</strong> Wallets with only informational changes are omitted from this overview but remain in “All changes.” Its severity is the most severe change associated with that wallet. The four delta columns cover only selected usage totals, so a critical row can legitimately show zero in every delta column when, for example, a scoped baseline, wallet identity, allocation placement, grant association, or accounting finding changed. “Why flagged” shows the detailed reason that assigned the row's severity. Select a row to inspect the captured wallet values before and after.</p><div class="controls"><label for="project-sort">Sort by</label><select id="project-sort"><option value="category">Category, then owner</option><option value="severity">Severity</option><option value="title">Owner</option><option value="owner">Owner reference</option><option value="local">Local delta (largest absolute)</option><option value="flow">Flow delta (largest absolute)</option><option value="excess">Excess delta (largest absolute)</option><option value="maxUsable">Max usable delta (largest absolute)</option><option value="changes">Number of changes</option></select></div><div id="projects"></div><div id="wallet-detail" class="wallet-detail muted">Select a wallet row to see before and after wallet metrics.</div></section>
<section><h2>Job scoped usage</h2><p class="explanation">Checks exact <code>job-&lt;id&gt;</code> resource suffixes against cumulative RUNNING intervals through the scope's last update. Any legacy wallet-owner prefix in the stored key is ignored. When RUNNING state updates are unavailable, runtime falls back to the job's start and completion timestamps. The scoped amount is converted back through replicas, product resources, fraction, accounting interval, and price. Both that result and the source runtime are floored to whole minutes. Results up to one additional wall minute away are tolerated after applying the same accounting scaling and are not reported as errors.</p><div class="counts"><span class="badge">{{.JobUsage.Checked}} checked</span><span class="badge">{{.JobUsage.Matched}} matched</span><span class="badge">{{.JobUsage.Tolerated}} within one minute</span><span class="badge">{{.JobUsage.Mismatch}} mismatched</span><span class="badge">{{.JobUsage.Skipped}} skipped</span></div></section>
<section><h2>All changes</h2><div class="controls"><input id="search" placeholder="Filter owner or title"><select id="severity"><option value="">All severities</option><option>critical</option><option>high</option><option>medium</option><option>info</option></select><label for="change-sort">Sort by</label><select id="change-sort"><option value="category">Category, then owner</option><option value="severity">Severity</option><option value="owner">Owner</option><option value="wallet">Wallet ID</option><option value="entity">Entity and field</option></select><span id="change-count" class="result-count"></span></div><div id="changes"></div></section>
<p class="muted status-note">Current accounting state is compared. Historical wallet snapshots and usage reports are not rewritten by reflow. Rows are sorted by category, owner, bucket/provider, and owner reference.</p></main>
<script id="report-data" type="application/json">{{.JSON}}</script><script>
const report=JSON.parse(document.getElementById('report-data').textContent);
const rowHeight=40,overscan=8;
const text=value=>value===null||value===undefined?'':String(value);
const signed=value=>value>0?'+'+value:String(value);
const ownerName=item=>item.projectTitle||item.ownerReference||'Unknown owner';
const changesByWallet=new Map();
for(const change of report.changes){const key=text(change.walletId);if(!changesByWallet.has(key))changesByWallet.set(key,[]);changesByWallet.get(key).push(change)}
function reasonsFor(project){const reasons=[],matches=(changesByWallet.get(text(project.walletId))||[]).filter(change=>change.severity===project.severity);for(const change of matches){if(!reasons.includes(change.reason))reasons.push(change.reason)}const shown=reasons.slice(0,2).join('; ');return shown+(reasons.length>2?' (+'+(reasons.length-2)+' more)':'')}
function virtualTable(target,columns,rows,onSelect){
  const root=document.getElementById(target),viewport=document.createElement('div'),header=document.createElement('div'),canvas=document.createElement('div');
  const grid=columns.map(column=>column.width).join(' '),width=columns.reduce((sum,column)=>sum+column.pixels,0)+'px';
  viewport.className='vtable';viewport.style.setProperty('--columns',grid);viewport.style.setProperty('--table-width',width);
  header.className='vhead';for(const column of columns){const cell=document.createElement('div');cell.className='cell';cell.textContent=column.label;header.appendChild(cell)}
  canvas.className='vcanvas';viewport.append(header,canvas);root.replaceChildren(viewport);
  let current=rows,frame=0;
  function render(){frame=0;canvas.style.height=(current.length*rowHeight)+'px';const start=Math.max(0,Math.floor(viewport.scrollTop/rowHeight)-overscan),end=Math.min(current.length,start+Math.ceil(viewport.clientHeight/rowHeight)+overscan*2),fragment=document.createDocumentFragment();for(let index=start;index<end;index++){const item=current[index],row=document.createElement('div');row.className='vrow '+(item.severity||'');row.style.top=(index*rowHeight)+'px';for(const column of columns){const value=text(column.value(item)),cell=document.createElement('div');cell.className='cell '+(column.className||'');cell.textContent=value;cell.title=value;row.appendChild(cell)}if(onSelect){row.classList.add('selectable');row.tabIndex=0;row.addEventListener('click',()=>onSelect(item));row.addEventListener('keydown',event=>{if(event.key==='Enter'||event.key===' '){event.preventDefault();onSelect(item)}})}fragment.appendChild(row)}canvas.replaceChildren(fragment);if(!current.length){const empty=document.createElement('div');empty.className='empty';empty.textContent='No matching rows';canvas.replaceChildren(empty)}}
  function schedule(){if(!frame)frame=requestAnimationFrame(render)}viewport.addEventListener('scroll',schedule,{passive:true});window.addEventListener('resize',schedule,{passive:true});render();return next=>{current=next;viewport.scrollTop=0;render()}
}
const projectColumns=[
  {label:'Severity',width:'90px',pixels:90,className:'sev',value:p=>p.severity},{label:'Bucket',width:'210px',pixels:210,value:p=>p.bucket},{label:'Owner',width:'300px',pixels:300,value:ownerName},{label:'Wallet',width:'90px',pixels:90,value:p=>p.walletId},{label:'Unit',width:'110px',pixels:110,value:p=>p.unit},{label:'Local Δ',width:'100px',pixels:100,value:p=>signed(p.localDelta)},{label:'Flow Δ',width:'100px',pixels:100,value:p=>signed(p.flowDelta)},{label:'Excess Δ',width:'100px',pixels:100,value:p=>signed(p.excessDelta)},{label:'Max usable Δ',width:'120px',pixels:120,value:p=>signed(p.maxUsableDelta)},{label:'Lock',width:'130px',pixels:130,value:p=>p.lockBefore===p.lockAfter?'unchanged':p.lockBefore+' → '+p.lockAfter},{label:'Changes',width:'90px',pixels:90,value:p=>p.changes},{label:'Why flagged',width:'420px',pixels:420,className:'why',value:reasonsFor}
];
const walletDetail=document.getElementById('wallet-detail');
const metricRows=[['Local usage','localUsage',true],['Total usage','totalUsage',true],['Incoming quota','quota',true],['Total allocated to children','totalAllocated',true],['Max usable','maxUsable',true],['Excess usage','excessUsage',true],['Propagated usage','propagatedUsage',false],['Usage from children','childUsage',false],['Scoped usage total','scopedUsage',false],['Allocations','allocationCount',false],['Funding parents','parentCount',false],['Funded children','childCount',false],['Locked','locked',false],['Loaded','loaded',false],['Derived error','derivedError',false]];
function showWalletDetails(project){const heading=document.createElement('h3'),note=document.createElement('div'),table=document.createElement('table'),thead=document.createElement('thead'),headRow=document.createElement('tr'),tbody=document.createElement('tbody');heading.textContent=ownerName(project)+' · '+project.bucket+' · wallet '+project.walletId;note.className='muted';note.textContent='Approximate wallet retrieval values from each capture. Usage and quota values use '+(project.unit||'the accounting unit')+'.';for(const label of['Metric','Before','After','Delta']){const th=document.createElement('th');th.textContent=label;headRow.appendChild(th)}thead.appendChild(headRow);for(const[label,key,core]of metricRows){const row=document.createElement('tr');if(core)row.className='metric-core';const before=project.before||{},after=project.after||{},numeric=typeof before[key]==='number'&&typeof after[key]==='number',values=[label,before.present===false?'—':text(before[key]),after.present===false?'—':text(after[key]),before.present===false||after.present===false?'':numeric?signed(after[key]-before[key]):before[key]===after[key]?'unchanged':'changed'];for(const value of values){const cell=document.createElement('td');cell.textContent=value;cell.title=value;row.appendChild(cell)}tbody.appendChild(row)}table.className='metric-table';table.append(thead,tbody);walletDetail.className='wallet-detail';walletDetail.replaceChildren(heading,note,table)}
const setProjects=virtualTable('projects',projectColumns,report.projects,showWalletDetails),projectSort=document.getElementById('project-sort'),collator=new Intl.Collator(undefined,{numeric:true,sensitivity:'base'}),severityRank={critical:0,high:1,medium:2,info:3};
function defaultProjectOrder(a,b){const category=value=>{const parts=text(value).split('/');return parts.length>1?parts.slice(1).join('/'):parts[0]};return collator.compare(category(a.bucket),category(b.bucket))||collator.compare(ownerName(a),ownerName(b))||collator.compare(text(a.bucket),text(b.bucket))||collator.compare(text(a.ownerReference),text(b.ownerReference))||Number(a.walletId)-Number(b.walletId)}
function sortProjects(){const key=projectSort.value,projects=[...report.projects],byMagnitude=field=>(a,b)=>Math.abs(Number(b[field]))-Math.abs(Number(a[field]))||defaultProjectOrder(a,b);let compare=defaultProjectOrder;switch(key){case'severity':compare=(a,b)=>(severityRank[a.severity]??4)-(severityRank[b.severity]??4)||defaultProjectOrder(a,b);break;case'title':compare=(a,b)=>collator.compare(ownerName(a),ownerName(b))||defaultProjectOrder(a,b);break;case'owner':compare=(a,b)=>collator.compare(text(a.ownerReference),text(b.ownerReference))||defaultProjectOrder(a,b);break;case'local':compare=byMagnitude('localDelta');break;case'flow':compare=byMagnitude('flowDelta');break;case'excess':compare=byMagnitude('excessDelta');break;case'maxUsable':compare=byMagnitude('maxUsableDelta');break;case'changes':compare=(a,b)=>Number(b.changes)-Number(a.changes)||defaultProjectOrder(a,b)}projects.sort(compare);setProjects(projects)}
projectSort.addEventListener('change',sortProjects);
const changeColumns=[
  {label:'Severity',width:'90px',pixels:90,className:'sev',value:c=>c.severity},{label:'Bucket',width:'200px',pixels:200,value:c=>c.bucket},{label:'Owner',width:'300px',pixels:300,value:ownerName},{label:'Wallet',width:'85px',pixels:85,value:c=>c.walletId},{label:'Entity',width:'220px',pixels:220,value:c=>c.entity+':'+c.entityId},{label:'Field',width:'150px',pixels:150,value:c=>c.field},{label:'Before',width:'220px',pixels:220,value:c=>c.before},{label:'After',width:'220px',pixels:220,value:c=>c.after},{label:'Reason',width:'390px',pixels:390,value:c=>c.reason}
];
const setChanges=virtualTable('changes',changeColumns,report.changes),search=document.getElementById('search'),severity=document.getElementById('severity'),changeSort=document.getElementById('change-sort'),count=document.getElementById('change-count');let filterTimer;
function defaultChangeOrder(a,b){const category=value=>{const parts=text(value).split('/');return parts.length>1?parts.slice(1).join('/'):parts[0]};return collator.compare(category(a.bucket),category(b.bucket))||collator.compare(ownerName(a),ownerName(b))||collator.compare(text(a.bucket),text(b.bucket))||collator.compare(text(a.ownerReference),text(b.ownerReference))||Number(a.walletId)-Number(b.walletId)||collator.compare(text(a.entity),text(b.entity))||collator.compare(text(a.entityId),text(b.entityId))||collator.compare(text(a.field),text(b.field))}
function changeOrder(){switch(changeSort.value){case'severity':return(a,b)=>(severityRank[a.severity]??4)-(severityRank[b.severity]??4)||defaultChangeOrder(a,b);case'owner':return(a,b)=>collator.compare(ownerName(a),ownerName(b))||defaultChangeOrder(a,b);case'wallet':return(a,b)=>Number(a.walletId)-Number(b.walletId)||defaultChangeOrder(a,b);case'entity':return(a,b)=>collator.compare(text(a.entity),text(b.entity))||collator.compare(text(a.entityId),text(b.entityId))||collator.compare(text(a.field),text(b.field))||defaultChangeOrder(a,b);default:return defaultChangeOrder}}
function filterChanges(){const query=search.value.trim().toLowerCase(),level=severity.value,filtered=report.changes.filter(change=>(!level||change.severity===level)&&(!query||[change.ownerReference,change.projectTitle].some(value=>text(value).toLowerCase().includes(query))));filtered.sort(changeOrder());setChanges(filtered);count.textContent=filtered.length+' of '+report.changes.length+' changes'}
function scheduleFilter(){clearTimeout(filterTimer);filterTimer=setTimeout(filterChanges,80)}search.addEventListener('input',scheduleFilter);severity.addEventListener('change',filterChanges);changeSort.addEventListener('change',filterChanges);filterChanges();
</script></body></html>`
	functions := template.FuncMap{
		"time":  func(value time.Time) string { return value.UTC().Format(time.RFC3339) },
		"count": func(value map[string]int, severity string) int { return value[severity] },
	}
	parsed, err := template.New("report").Funcs(functions).Parse(page)
	if err != nil {
		return nil, err
	}
	var output bytes.Buffer
	data := struct {
		AccountingAuditComparison
		JSON template.JS
	}{AccountingAuditComparison: report, JSON: template.JS(encodedReport)}
	if err := parsed.Execute(&output, data); err != nil {
		return nil, err
	}
	return output.Bytes(), nil
}
