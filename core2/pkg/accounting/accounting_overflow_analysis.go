package accounting

import (
	"database/sql"
	"fmt"
	"slices"
	"strings"
	"time"

	"gopkg.in/yaml.v3"
	db "ucloud.dk/shared/pkg/database"
)

type accountingOverflowOwner struct {
	Kind            string
	Reference       string
	ProjectTitle    string
	ParentProjectId string
}

type accountingOverflowOwnerRow struct {
	WalletId        AccWalletId
	Username        sql.NullString
	ProjectId       sql.NullString
	ProjectTitle    sql.NullString
	ParentProjectId sql.NullString
}

type accountingOverflowWallet struct {
	WalletId                  AccWalletId `yaml:"walletId"`
	OwnerKind                 string      `yaml:"ownerKind"`
	OwnerReference            string      `yaml:"ownerReference"`
	ProjectTitle              string      `yaml:"projectTitle,omitempty"`
	ParentProjectId           string      `yaml:"parentProjectId,omitempty"`
	LocalUsage                int64       `yaml:"localUsage"`
	ChildFlow                 int64       `yaml:"childFlow"`
	TotalUsage                int64       `yaml:"-"`
	PropagatedUsage           int64       `yaml:"propagatedUsage"`
	IncomingContributingQuota int64       `yaml:"incomingContributingQuota"`
	OutgoingContributingQuota int64       `yaml:"outgoingContributingQuota"`
	OverflowCapacity          int64       `yaml:"overflowCapacity"`
	OverflowFlow              int64       `yaml:"overflowFlow"`
	SyntheticOverflowEligible bool        `yaml:"-"`
	IncomingGroupCount        int         `yaml:"-"`
	ArithmeticValid           bool        `yaml:"-"`
	Errors                    []string    `yaml:"errors,omitempty"`
}

type accountingUnfundedUsage struct {
	WalletId        AccWalletId `yaml:"walletId"`
	OwnerKind       string      `yaml:"ownerKind"`
	OwnerReference  string      `yaml:"ownerReference"`
	ProjectTitle    string      `yaml:"projectTitle,omitempty"`
	ParentProjectId string      `yaml:"parentProjectId,omitempty"`
	LocalUsage      int64       `yaml:"localUsage"`
	Reason          string      `yaml:"reason"`
}

type accountingOverflowComponent struct {
	OverflowWallets          []accountingOverflowWallet `yaml:"overflowWallets"`
	UnfundedUsage            []accountingUnfundedUsage  `yaml:"-"`
	DirectLocalUsage         int64                      `yaml:"directLocalUsage"`
	LeafLocalUsage           int64                      `yaml:"leafLocalUsage"`
	InternalLocalUsage       int64                      `yaml:"internalLocalUsage,omitempty"`
	PersistedRootFlow        int64                      `yaml:"persistedRootFlow"`
	SyntheticOverflowFlow    int64                      `yaml:"syntheticOverflowFlow"`
	NonSyntheticExcess       int64                      `yaml:"nonSyntheticExcess,omitempty"`
	ReconciliationDifference int64                      `yaml:"reconciliationDifference,omitempty"`
	MultipleParents          bool                       `yaml:"multipleParents,omitempty"`
	MixedRootAndParentFunds  bool                       `yaml:"mixedRootAndParentFunding,omitempty"`
	Errors                   []string                   `yaml:"errors,omitempty"`
}

type accountingOverflowBucket struct {
	Provider              string                        `yaml:"provider"`
	Category              string                        `yaml:"category"`
	Unit                  string                        `yaml:"unit"`
	SyntheticOverflowFlow int64                         `yaml:"syntheticOverflowFlow,omitempty"`
	Components            []accountingOverflowComponent `yaml:"components,omitempty"`
	UnfundedUsage         []accountingUnfundedUsage     `yaml:"unfundedUsage,omitempty"`
}

type accountingOverflowSummary struct {
	Buckets          int `yaml:"buckets"`
	OverflowRoots    int `yaml:"overflowRoots"`
	UnfundedWallets  int `yaml:"unfundedWallets"`
	BrokenComponents int `yaml:"brokenComponents"`
	LoadErrors       int `yaml:"loadErrors"`
}

type accountingOverflowRootTotal struct {
	Provider                   string `yaml:"provider"`
	Category                   string `yaml:"category"`
	Unit                       string `yaml:"unit"`
	OverflowRoots              int    `yaml:"overflowRoots"`
	FlowedThroughOverflowRoots int64  `yaml:"flowedThroughOverflowRoots"`
}

type accountingOverflowReport struct {
	AnalyzedAt         time.Time                     `yaml:"analyzedAt"`
	Provider           string                        `yaml:"provider,omitempty"`
	Category           string                        `yaml:"category,omitempty"`
	Summary            accountingOverflowSummary     `yaml:"summary"`
	OverflowRootTotals []accountingOverflowRootTotal `yaml:"overflowRootTotals"`
	LoadErrors         []string                      `yaml:"loadErrors,omitempty"`
	Buckets            []accountingOverflowBucket    `yaml:"buckets"`
}

// AnalyzeAccountingOverflow reconstructs operation-local synthetic overflow flow from persisted accounting state.
func AnalyzeAccountingOverflow(provider, category string) []byte {
	report := db.NewTx(func(tx *db.Transaction) accountingOverflowReport {
		db.Exec(tx, "set transaction isolation level repeatable read, read only", db.Params{})
		nowRow, ok := db.Get[struct{ Now time.Time }](tx, "select transaction_timestamp() as now", db.Params{})
		if !ok {
			panic("could not retrieve accounting overflow analysis time")
		}

		owners := loadAccountingOverflowOwners(tx)
		productsLoadFromTx(tx)
		var loadErrors []string
		accountingLoadFromTx(tx, nowRow.Now, func(message string) {
			loadErrors = append(loadErrors, message)
		})
		slices.Sort(loadErrors)
		result := analyzeLoadedAccountingOverflow(nowRow.Now, provider, category, owners)
		result.LoadErrors = loadErrors
		result.Summary.LoadErrors = len(loadErrors)
		return result
	})

	encoded, err := yaml.Marshal(report)
	if err != nil {
		panic(err)
	}
	return encoded
}

func loadAccountingOverflowOwners(tx *db.Transaction) map[AccWalletId]accountingOverflowOwner {
	rows := db.Select[accountingOverflowOwnerRow](tx, `
		select w.id as wallet_id, wo.username, wo.project_id,
			p.title as project_title, p.parent as parent_project_id
		from accounting.wallets_v2 w
		left join accounting.wallet_owner wo on wo.id = w.wallet_owner
		left join project.projects p on p.id = wo.project_id
		order by w.id
	`, db.Params{})
	result := make(map[AccWalletId]accountingOverflowOwner, len(rows))
	for _, row := range rows {
		owner := accountingOverflowOwner{Kind: "user", Reference: row.Username.String}
		if row.ProjectId.Valid {
			owner.Kind = "project"
			owner.Reference = row.ProjectId.String
			owner.ProjectTitle = row.ProjectTitle.String
			owner.ParentProjectId = row.ParentProjectId.String
		}
		result[row.WalletId] = owner
	}
	return result
}

func analyzeLoadedAccountingOverflow(now time.Time, provider, category string, owners map[AccWalletId]accountingOverflowOwner) accountingOverflowReport {
	result := accountingOverflowReport{
		AnalyzedAt:         now,
		Provider:           provider,
		Category:           category,
		OverflowRootTotals: []accountingOverflowRootTotal{},
		Buckets:            []accountingOverflowBucket{},
	}
	var buckets []*internalBucket
	for _, bucket := range accGlobals.BucketsByCategory {
		if provider != "" && bucket.Category.Provider != provider {
			continue
		}
		if category != "" && bucket.Category.Name != category {
			continue
		}
		buckets = append(buckets, bucket)
	}
	slices.SortFunc(buckets, func(a, b *internalBucket) int {
		if order := strings.Compare(a.Category.Provider, b.Category.Provider); order != 0 {
			return order
		}
		return strings.Compare(a.Category.Name, b.Category.Name)
	})

	for _, bucket := range buckets {
		item := analyzeAccountingOverflowBucket(bucket, owners)
		if len(item.Components) == 0 && len(item.UnfundedUsage) == 0 {
			continue
		}
		total := accountingOverflowRootTotal{
			Provider:                   item.Provider,
			Category:                   item.Category,
			Unit:                       item.Unit,
			FlowedThroughOverflowRoots: item.SyntheticOverflowFlow,
		}
		result.Summary.Buckets++
		for _, component := range item.Components {
			result.Summary.OverflowRoots += len(component.OverflowWallets)
			total.OverflowRoots += len(component.OverflowWallets)
			if component.ReconciliationDifference != 0 || len(component.Errors) > 0 {
				result.Summary.BrokenComponents++
			}
		}
		result.Summary.UnfundedWallets += len(item.UnfundedUsage)
		if total.FlowedThroughOverflowRoots > 0 {
			result.OverflowRootTotals = append(result.OverflowRootTotals, total)
		}
		result.Buckets = append(result.Buckets, item)
	}
	return result
}

func analyzeAccountingOverflowBucket(bucket *internalBucket, owners map[AccWalletId]accountingOverflowOwner) accountingOverflowBucket {
	result := accountingOverflowBucket{
		Provider:      bucket.Category.Provider,
		Category:      bucket.Category.Name,
		Unit:          bucket.Category.AccountingUnit.Name,
		Components:    []accountingOverflowComponent{},
		UnfundedUsage: []accountingUnfundedUsage{},
	}
	adjacent := map[AccWalletId][]AccWalletId{}
	for walletId, wallet := range bucket.WalletsById {
		for parentId := range wallet.AllocationsByParent {
			if parentId == internalGraphRoot || bucket.WalletsById[parentId] == nil {
				continue
			}
			adjacent[walletId] = append(adjacent[walletId], parentId)
			adjacent[parentId] = append(adjacent[parentId], walletId)
		}
	}

	walletIds := make([]AccWalletId, 0, len(bucket.WalletsById))
	for walletId := range bucket.WalletsById {
		walletIds = append(walletIds, walletId)
	}
	slices.Sort(walletIds)
	seen := map[AccWalletId]bool{}
	for _, first := range walletIds {
		if seen[first] {
			continue
		}
		seen[first] = true
		componentIds := []AccWalletId{first}
		for index := 0; index < len(componentIds); index++ {
			for _, next := range adjacent[componentIds[index]] {
				if !seen[next] {
					seen[next] = true
					componentIds = append(componentIds, next)
				}
			}
		}
		slices.Sort(componentIds)
		component := analyzeAccountingOverflowComponent(bucket, componentIds, owners)
		result.UnfundedUsage = append(result.UnfundedUsage, component.UnfundedUsage...)
		if component.SyntheticOverflowFlow > 0 || len(component.Errors) > 0 {
			result.SyntheticOverflowFlow += component.SyntheticOverflowFlow
			result.Components = append(result.Components, component)
		}
	}
	return result
}

func analyzeAccountingOverflowComponent(bucket *internalBucket, walletIds []AccWalletId, owners map[AccWalletId]accountingOverflowOwner) accountingOverflowComponent {
	result := accountingOverflowComponent{
		OverflowWallets: []accountingOverflowWallet{},
		UnfundedUsage:   []accountingUnfundedUsage{},
	}
	hasPersistedRoot := false
	for _, walletId := range walletIds {
		wallet := bucket.WalletsById[walletId]
		metric := accountingOverflowMetric(bucket, wallet, owners[walletId])
		result.DirectLocalUsage += metric.LocalUsage
		if len(wallet.ChildrenUsage) == 0 {
			result.LeafLocalUsage += metric.LocalUsage
		} else {
			result.InternalLocalUsage += metric.LocalUsage
		}

		realParents := 0
		hasRoot := false
		rootFlow := int64(0)
		for parentId, group := range wallet.AllocationsByParent {
			if parentId == internalGraphRoot {
				hasRoot = true
				rootFlow += group.TreeUsage
			} else {
				realParents++
			}
		}
		if len(wallet.AllocationsByParent) > 1 {
			result.MultipleParents = true
		}
		if hasRoot && realParents > 0 {
			result.MixedRootAndParentFunds = true
		}
		if hasRoot {
			hasPersistedRoot = true
			result.PersistedRootFlow += rootFlow
		}

		if metric.SyntheticOverflowEligible && metric.OverflowFlow > 0 {
			result.OverflowWallets = append(result.OverflowWallets, metric)
			result.SyntheticOverflowFlow += metric.OverflowFlow
		} else if metric.OverflowFlow > 0 {
			result.NonSyntheticExcess += metric.OverflowFlow
		}
		result.Errors = append(result.Errors, metric.Errors...)
	}

	var overflow bool
	reconciled, itemOverflow := checkedAccountingAdd(result.PersistedRootFlow, result.SyntheticOverflowFlow)
	overflow = overflow || itemOverflow
	reconciled, itemOverflow = checkedAccountingAdd(reconciled, result.NonSyntheticExcess)
	overflow = overflow || itemOverflow
	result.ReconciliationDifference, itemOverflow = checkedAccountingSub(result.DirectLocalUsage, reconciled)
	overflow = overflow || itemOverflow
	if overflow {
		result.Errors = append(result.Errors, "component reconciliation overflows int64")
	}
	if !hasPersistedRoot && result.DirectLocalUsage != 0 {
		for _, walletId := range walletIds {
			wallet := bucket.WalletsById[walletId]
			if wallet.LocalUsage == 0 {
				continue
			}
			owner := owners[walletId]
			result.UnfundedUsage = append(result.UnfundedUsage, accountingUnfundedUsage{
				WalletId:        walletId,
				OwnerKind:       owner.Kind,
				OwnerReference:  owner.Reference,
				ProjectTitle:    owner.ProjectTitle,
				ParentProjectId: owner.ParentProjectId,
				LocalUsage:      wallet.LocalUsage,
				Reason:          "wallet has usage but its allocation component has no persisted root allocation",
			})
		}
	}
	if accountingOverflowComponentHasCycle(bucket, walletIds) {
		result.Errors = append(result.Errors, "component contains an allocation cycle")
	}
	slices.Sort(result.Errors)
	return result
}

func accountingOverflowComponentHasCycle(bucket *internalBucket, walletIds []AccWalletId) bool {
	color := map[AccWalletId]uint8{}
	var visit func(AccWalletId) bool
	visit = func(walletId AccWalletId) bool {
		if color[walletId] == 1 {
			return true
		}
		if color[walletId] == 2 {
			return false
		}
		color[walletId] = 1
		for parentId := range bucket.WalletsById[walletId].AllocationsByParent {
			if parentId != internalGraphRoot && bucket.WalletsById[parentId] != nil && visit(parentId) {
				return true
			}
		}
		color[walletId] = 2
		return false
	}
	for _, walletId := range walletIds {
		if visit(walletId) {
			return true
		}
	}
	return false
}

func accountingOverflowMetric(bucket *internalBucket, wallet *internalWallet, owner accountingOverflowOwner) accountingOverflowWallet {
	result := accountingOverflowWallet{
		WalletId:           wallet.Id,
		OwnerKind:          owner.Kind,
		OwnerReference:     owner.Reference,
		ProjectTitle:       owner.ProjectTitle,
		ParentProjectId:    owner.ParentProjectId,
		LocalUsage:         wallet.LocalUsage,
		IncomingGroupCount: len(wallet.AllocationsByParent),
		ArithmeticValid:    true,
	}
	add := func(target *int64, value int64, label string) {
		var overflow bool
		*target, overflow = checkedAccountingAdd(*target, value)
		if overflow {
			result.ArithmeticValid = false
			result.Errors = append(result.Errors, fmt.Sprintf("wallet %d %s overflows int64", wallet.Id, label))
		}
	}
	for _, usage := range wallet.ChildrenUsage {
		add(&result.ChildFlow, usage, "child flow")
	}
	result.TotalUsage = result.LocalUsage
	add(&result.TotalUsage, result.ChildFlow, "total usage")
	for _, group := range wallet.AllocationsByParent {
		add(&result.PropagatedUsage, group.TreeUsage, "propagated usage")
		add(&result.IncomingContributingQuota, lInternalGroupTotalQuotaContributing(bucket, group), "incoming contributing quota")
	}
	for childId := range wallet.ChildrenUsage {
		child := bucket.WalletsById[childId]
		if child != nil && child.AllocationsByParent[wallet.Id] != nil {
			add(&result.OutgoingContributingQuota, lInternalGroupTotalQuotaContributing(bucket, child.AllocationsByParent[wallet.Id]), "outgoing contributing quota")
		}
	}

	totalWithLocal, overflow := checkedAccountingAdd(result.OutgoingContributingQuota, result.LocalUsage)
	if overflow {
		result.ArithmeticValid = false
		result.Errors = append(result.Errors, fmt.Sprintf("wallet %d overflow capacity overflows int64", wallet.Id))
	}
	result.OverflowCapacity, overflow = checkedAccountingSub(totalWithLocal, result.IncomingContributingQuota)
	if overflow {
		result.ArithmeticValid = false
		result.Errors = append(result.Errors, fmt.Sprintf("wallet %d overflow capacity overflows int64", wallet.Id))
	}
	result.OverflowFlow, overflow = checkedAccountingSub(result.TotalUsage, result.PropagatedUsage)
	if overflow {
		result.ArithmeticValid = false
		result.Errors = append(result.Errors, fmt.Sprintf("wallet %d overflow flow overflows int64", wallet.Id))
	}
	result.SyntheticOverflowEligible = result.ArithmeticValid && result.IncomingGroupCount > 0 && result.OverflowCapacity > 0
	if result.SyntheticOverflowEligible {
		if result.OverflowFlow < 0 || result.OverflowFlow > result.OverflowCapacity {
			result.Errors = append(result.Errors, fmt.Sprintf("wallet %d uses %d of overflow capacity %d", wallet.Id, result.OverflowFlow, result.OverflowCapacity))
		}
	}
	return result
}
