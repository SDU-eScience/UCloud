package accounting

import (
	"database/sql"
	"strings"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
)

const accountingAuditSchemaVersion = 2

type AccountingAuditCapture struct {
	SchemaVersion int                         `json:"schemaVersion"`
	CapturedAt    time.Time                   `json:"capturedAt"`
	Database      string                      `json:"database"`
	Totals        snapshotTotals              `json:"totals"`
	Findings      []AccountingAuditFinding    `json:"findings"`
	Wallets       []AccountingAuditWallet     `json:"wallets"`
	Groups        []AccountingAuditGroup      `json:"groups"`
	Allocations   []AccountingAuditAllocation `json:"allocations"`
	Scopes        []AccountingAuditScope      `json:"scopes"`
}

type AccountingAuditFinding struct {
	Bucket        string        `json:"bucket,omitempty"`
	Code          string        `json:"code"`
	WalletIds     []AccWalletId `json:"walletIds,omitempty"`
	GroupIds      []accGroupId  `json:"groupIds,omitempty"`
	AllocationIds []accAllocId  `json:"allocationIds,omitempty"`
	Details       string        `json:"details"`
	Impact        string        `json:"impact,omitempty"`
}

type AccountingAuditWallet struct {
	Id                         AccWalletId `json:"id"`
	OwnerId                    int64       `json:"ownerId"`
	OwnerKind                  string      `json:"ownerKind"`
	OwnerReference             string      `json:"ownerReference"`
	ProjectTitle               string      `json:"projectTitle,omitempty"`
	ParentProjectId            string      `json:"parentProjectId,omitempty"`
	Provider                   string      `json:"provider"`
	Category                   string      `json:"category"`
	AccountingFrequency        string      `json:"accountingFrequency"`
	ProductType                string      `json:"productType"`
	UnitName                   string      `json:"unitName"`
	UnitNamePlural             string      `json:"unitNamePlural"`
	UnitFloatingPoint          bool        `json:"unitFloatingPoint"`
	UnitFrequencySuffix        bool        `json:"unitFrequencySuffix"`
	LocalUsage                 int64       `json:"localUsage"`
	LocalRetiredUsage          int64       `json:"localRetiredUsage"`
	PersistedExcessUsage       int64       `json:"persistedExcessUsage"`
	PersistedTotalAllocated    int64       `json:"persistedTotalAllocated"`
	PersistedRetiredAllocated  int64       `json:"persistedRetiredAllocated"`
	WasLocked                  bool        `json:"wasLocked"`
	LastSignificantUpdateAt    time.Time   `json:"lastSignificantUpdateAt"`
	Loaded                     bool        `json:"loaded"`
	DerivedError               string      `json:"derivedError,omitempty"`
	ScopeCount                 int         `json:"scopeCount"`
	ScopedUsage                int64       `json:"scopedUsage"`
	AllocationCount            int         `json:"allocationCount"`
	ParentCount                int         `json:"parentCount"`
	ChildCount                 int         `json:"childCount"`
	ChildUsage                 int64       `json:"childUsage"`
	TotalUsage                 int64       `json:"totalUsage"`
	PropagatedUsage            int64       `json:"propagatedUsage"`
	RecomputedExcessUsage      int64       `json:"recomputedExcessUsage"`
	IncomingContributingQuota  int64       `json:"incomingContributingQuota"`
	OutgoingContributingQuota  int64       `json:"outgoingContributingQuota"`
	RecomputedRetiredAllocated int64       `json:"recomputedRetiredAllocated"`
	MaxUsable                  int64       `json:"maxUsable"`
	DerivedLocked              bool        `json:"derivedLocked"`
}

type AccountingAuditGroup struct {
	Id                accGroupId  `json:"id"`
	AssociatedWallet  AccWalletId `json:"associatedWallet"`
	ParentWallet      AccWalletId `json:"parentWallet"`
	TreeUsage         int64       `json:"treeUsage"`
	RetiredTreeUsage  int64       `json:"retiredTreeUsage"`
	AllocationCount   int         `json:"allocationCount"`
	ContributingQuota int64       `json:"contributingQuota"`
	RetiredUsageFloor int64       `json:"retiredUsageFloor"`
}

type AccountingAuditAllocation struct {
	Id               accAllocId  `json:"id"`
	GroupId          accGroupId  `json:"groupId"`
	AssociatedWallet AccWalletId `json:"associatedWallet"`
	ParentWallet     AccWalletId `json:"parentWallet"`
	GrantId          int64       `json:"grantId,omitempty"`
	Quota            int64       `json:"quota"`
	Start            time.Time   `json:"start"`
	End              time.Time   `json:"end"`
	Retired          bool        `json:"retired"`
	RetiredUsage     int64       `json:"retiredUsage"`
	RetiredQuota     int64       `json:"retiredQuota"`
	Lifecycle        string      `json:"lifecycle"`
}

type AccountingAuditScope struct {
	WalletId      AccWalletId `json:"walletId"`
	Key           string      `json:"key"`
	Usage         int64       `json:"usage"`
	LastUpdatedAt time.Time   `json:"lastUpdatedAt"`
}

type accountingAuditWalletRow struct {
	Id                      AccWalletId
	OwnerId                 sql.NullInt64
	Username                sql.NullString
	ProjectId               sql.NullString
	ProjectTitle            sql.NullString
	ParentProjectId         sql.NullString
	Provider                sql.NullString
	Category                sql.NullString
	AccountingFrequency     sql.NullString
	ProductType             sql.NullString
	UnitName                sql.NullString
	UnitNamePlural          sql.NullString
	UnitFloatingPoint       sql.NullBool
	UnitFrequencySuffix     sql.NullBool
	LocalUsage              int64
	LocalRetiredUsage       int64
	ExcessUsage             int64
	TotalAllocated          int64
	TotalRetiredAllocated   int64
	WasLocked               bool
	LastSignificantUpdateAt time.Time
}

func accountingAuditFindings(snapshot snapshotReport) []AccountingAuditFinding {
	result := make([]AccountingAuditFinding, 0, snapshot.Summary.Findings)
	appendFinding := func(bucket string, finding snapshotFinding) {
		result = append(result, AccountingAuditFinding{
			Bucket:        bucket,
			Code:          finding.Code,
			WalletIds:     finding.WalletIds,
			GroupIds:      finding.GroupIds,
			AllocationIds: finding.AllocationIds,
			Details:       finding.Details,
			Impact:        finding.Impact,
		})
	}
	for _, finding := range snapshot.Findings {
		appendFinding("", finding)
	}
	for _, bucket := range snapshot.Buckets {
		for _, finding := range bucket.Findings {
			appendFinding(bucket.Provider+"/"+bucket.Category, finding)
		}
	}
	return result
}

func captureAccountingAuditWallets(tx *db.Transaction, now time.Time) []AccountingAuditWallet {
	rows := db.Select[accountingAuditWalletRow](
		tx,
		`
			select w.id, wo.id as owner_id, wo.username, wo.project_id,
				p.title as project_title, p.parent as parent_project_id,
			pc.provider, pc.category, pc.accounting_frequency, pc.product_type,
				u.name as unit_name, u.name_plural as unit_name_plural,
			u.floating_point as unit_floating_point, u.display_frequency_suffix as unit_frequency_suffix,
				w.local_usage, w.local_retired_usage, w.excess_usage,
				w.total_allocated, w.total_retired_allocated,
				w.was_locked, w.last_significant_update_at
			from accounting.wallets_v2 w
			left join accounting.wallet_owner wo on wo.id = w.wallet_owner
			left join accounting.product_categories pc on pc.id = w.product_category
			left join accounting.accounting_units u on u.id = pc.accounting_unit
			left join project.projects p on p.id = wo.project_id
			order by w.id
		`,
		db.Params{},
	)
	result := make([]AccountingAuditWallet, 0, len(rows))
	for _, row := range rows {
		ownerKind := "user"
		ownerReference := row.Username.String
		if row.ProjectId.Valid {
			ownerKind = "project"
			ownerReference = row.ProjectId.String
		}
		item := AccountingAuditWallet{
			Id:                        row.Id,
			OwnerId:                   row.OwnerId.Int64,
			OwnerKind:                 ownerKind,
			OwnerReference:            ownerReference,
			ProjectTitle:              row.ProjectTitle.String,
			ParentProjectId:           row.ParentProjectId.String,
			Provider:                  row.Provider.String,
			Category:                  row.Category.String,
			AccountingFrequency:       row.AccountingFrequency.String,
			ProductType:               row.ProductType.String,
			UnitName:                  row.UnitName.String,
			UnitNamePlural:            row.UnitNamePlural.String,
			UnitFloatingPoint:         row.UnitFloatingPoint.Bool,
			UnitFrequencySuffix:       row.UnitFrequencySuffix.Bool,
			LocalUsage:                row.LocalUsage,
			LocalRetiredUsage:         row.LocalRetiredUsage,
			PersistedExcessUsage:      row.ExcessUsage,
			PersistedTotalAllocated:   row.TotalAllocated,
			PersistedRetiredAllocated: row.TotalRetiredAllocated,
			WasLocked:                 row.WasLocked,
			LastSignificantUpdateAt:   row.LastSignificantUpdateAt,
		}
		bucket := accGlobals.BucketsByCategory[accapi.ProductCategoryIdV2{Name: row.Category.String, Provider: row.Provider.String}]
		if bucket == nil || bucket.WalletsById[row.Id] == nil {
			item.DerivedError = "wallet was not loaded into the accounting model"
			result = append(result, item)
			continue
		}
		wallet := bucket.WalletsById[row.Id]
		item.Loaded = true
		item.ParentCount = len(wallet.AllocationsByParent)
		item.ChildCount = len(wallet.ChildrenUsage)
		for _, usage := range wallet.ChildrenUsage {
			item.ChildUsage += usage
		}
		item.TotalUsage = lInternalWalletTotalUsageInNode(bucket, wallet)
		item.PropagatedUsage = lInternalWalletTotalPropagatedUsage(bucket, wallet)
		item.IncomingContributingQuota = lInternalWalletTotalQuotaContributing(bucket, wallet)
		item.OutgoingContributingQuota = lInternalWalletTotalAllocatedContributing(bucket, wallet)
		derived, overflowed := recomputeSnapshotWalletValues(bucket, wallet)
		item.RecomputedExcessUsage = derived.ExcessUsage
		item.RecomputedRetiredAllocated = derived.TotalRetiredAllocated
		if len(overflowed) > 0 {
			item.DerivedError = "overflow while recomputing " + strings.Join(overflowed, ", ")
		}
		for _, scope := range wallet.ScopedUsage {
			item.ScopeCount++
			var overflow bool
			item.ScopedUsage, overflow = checkedAccountingAdd(item.ScopedUsage, scope.Usage)
			if overflow {
				item.DerivedError = strings.TrimSpace(item.DerivedError + "; scoped usage overflows int64")
			}
		}
		maxUsable, err := lAccountingMaxUsableSafely(bucket, now, wallet)
		if err != nil {
			item.DerivedError = strings.TrimSpace(item.DerivedError + "; " + err.Error())
		} else {
			item.MaxUsable = maxUsable
			item.DerivedLocked = maxUsable <= 0
		}
		result = append(result, item)
	}
	return result
}

func captureAccountingAuditGroups(tx *db.Transaction) []AccountingAuditGroup {
	rows := db.Select[struct {
		Id               accGroupId
		AssociatedWallet AccWalletId
		ParentWallet     sql.NullInt64
		TreeUsage        int64
		RetiredTreeUsage int64
	}](
		tx,
		`
			select id, associated_wallet, parent_wallet, tree_usage, retired_tree_usage
			from accounting.allocation_groups
			order by id
		`,
		db.Params{},
	)
	result := make([]AccountingAuditGroup, 0, len(rows))
	for _, row := range rows {
		item := AccountingAuditGroup{
			Id:               row.Id,
			AssociatedWallet: row.AssociatedWallet,
			ParentWallet:     AccWalletId(row.ParentWallet.Int64),
			TreeUsage:        row.TreeUsage,
			RetiredTreeUsage: row.RetiredTreeUsage,
		}
		bucket, wallet, ok := internalWalletById(row.AssociatedWallet)
		if ok {
			group := wallet.AllocationsByParent[item.ParentWallet]
			if group != nil && group.Id == item.Id {
				item.AllocationCount = len(group.Allocations)
				item.ContributingQuota = lInternalGroupTotalQuotaContributing(bucket, group)
				for allocationId := range group.Allocations {
					allocation := bucket.AllocationsById[allocationId]
					if allocation != nil && allocation.Retired {
						item.RetiredUsageFloor += allocation.RetiredUsage
					}
				}
			}
		}
		result = append(result, item)
	}
	return result
}

func captureAccountingAuditAllocations(tx *db.Transaction, now time.Time) []AccountingAuditAllocation {
	rows := db.Select[struct {
		Id               accAllocId
		GroupId          sql.NullInt64
		AssociatedWallet sql.NullInt64
		ParentWallet     sql.NullInt64
		GrantId          sql.NullInt64
		Quota            int64
		Start            time.Time
		End              time.Time
		Retired          bool
		RetiredUsage     sql.NullInt64
		RetiredQuota     sql.NullInt64
	}](
		tx,
		`
			select a.id, a.associated_allocation_group as group_id,
				ag.associated_wallet, ag.parent_wallet, a.granted_in as grant_id,
				a.quota, a.allocation_start_time as start, a.allocation_end_time as end,
				a.retired, a.retired_usage, a.retired_quota
			from accounting.wallet_allocations_v2 a
			left join accounting.allocation_groups ag on ag.id = a.associated_allocation_group
			order by a.id
		`,
		db.Params{},
	)
	result := make([]AccountingAuditAllocation, 0, len(rows))
	for _, row := range rows {
		lifecycle := "current"
		if now.Before(row.Start) {
			lifecycle = "future"
		} else if !now.Before(row.End) {
			lifecycle = "expired"
		}
		result = append(result, AccountingAuditAllocation{
			Id:               row.Id,
			GroupId:          accGroupId(row.GroupId.Int64),
			AssociatedWallet: AccWalletId(row.AssociatedWallet.Int64),
			ParentWallet:     AccWalletId(row.ParentWallet.Int64),
			GrantId:          row.GrantId.Int64,
			Quota:            row.Quota,
			Start:            row.Start,
			End:              row.End,
			Retired:          row.Retired,
			RetiredUsage:     row.RetiredUsage.Int64,
			RetiredQuota:     row.RetiredQuota.Int64,
			Lifecycle:        lifecycle,
		})
	}
	return result
}

func captureAccountingAuditScopes(tx *db.Transaction) []AccountingAuditScope {
	return db.Select[AccountingAuditScope](
		tx,
		`
			select wallet_id, key, usage, last_updated_at
			from accounting.scoped_usage
			order by wallet_id, key
		`,
		db.Params{},
	)
}
