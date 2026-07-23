package accounting

import (
	"fmt"
	"slices"
	"strings"
	"time"

	"gopkg.in/yaml.v3"
	db "ucloud.dk/shared/pkg/database"
)

type snapshotTotals struct {
	Buckets     int `yaml:"buckets"`
	Wallets     int `yaml:"wallets"`
	Groups      int `yaml:"groups"`
	Allocations int `yaml:"allocations"`
	Scopes      int `yaml:"scopes"`
}

type snapshotSummary struct {
	BrokenBuckets   int `yaml:"brokenBuckets"`
	AffectedWallets int `yaml:"affectedWallets"`
	Findings        int `yaml:"findings"`
	LoadErrors      int `yaml:"loadErrors"`
}

type snapshotFinding struct {
	Code            string        `yaml:"code"`
	WalletIds       []AccWalletId `yaml:"walletIds,omitempty"`
	GroupIds        []accGroupId  `yaml:"groupIds,omitempty"`
	AllocationIds   []accAllocId  `yaml:"allocationIds,omitempty"`
	PersistedValue  *int64        `yaml:"persistedValue,omitempty"`
	RecomputedValue *int64        `yaml:"recomputedValue,omitempty"`
	Details         string        `yaml:"details"`
	Impact          string        `yaml:"impact,omitempty"`
}

type snapshotPersistedWalletValues struct {
	Id                    AccWalletId
	ExcessUsage           int64
	TotalAllocated        int64
	TotalRetiredAllocated int64
}

type snapshotBucket struct {
	Provider          string            `yaml:"provider"`
	Category          string            `yaml:"category"`
	CapacityBased     bool              `yaml:"capacityBased"`
	Wallets           int               `yaml:"wallets"`
	AffectedWalletIds []AccWalletId     `yaml:"affectedWalletIds,omitempty"`
	Findings          []snapshotFinding `yaml:"findings"`
}

type snapshotReport struct {
	Totals   snapshotTotals    `yaml:"totals"`
	Summary  snapshotSummary   `yaml:"summary"`
	Findings []snapshotFinding `yaml:"findings"`
	Buckets  []snapshotBucket  `yaml:"buckets"`
}

func AnalyzeSnapshot() []byte {
	report := db.NewTx(func(tx *db.Transaction) snapshotReport {
		db.Exec(tx, "set transaction isolation level repeatable read, read only", db.Params{})
		now, _ := db.Get[struct{ Now time.Time }](tx, "select transaction_timestamp() as now", db.Params{})

		result := snapshotReport{
			Findings: []snapshotFinding{},
			Buckets:  []snapshotBucket{},
		}
		result.Totals, _ = db.Get[snapshotTotals](tx, `
			select
				(select count(*) from accounting.product_categories) as buckets,
				(select count(*) from accounting.wallets_v2) as wallets,
				(select count(*) from accounting.allocation_groups) as groups,
				(select count(*) from accounting.wallet_allocations_v2) as allocations,
				(select count(*) from accounting.scoped_usage) as scopes
		`, db.Params{})
		persistedWalletValues := map[AccWalletId]snapshotPersistedWalletValues{}
		for _, row := range db.Select[snapshotPersistedWalletValues](tx, `
			select id, excess_usage, total_allocated, total_retired_allocated
			from accounting.wallets_v2
			order by id
		`, db.Params{}) {
			persistedWalletValues[row.Id] = row
		}

		productsLoadFromTx(tx)
		accountingLoadFromTx(tx, now.Now, func(message string) {
			result.Summary.LoadErrors++
			result.Findings = append(result.Findings, snapshotFinding{
				Code:    snapshotLoadFindingCode(message),
				Details: message,
				Impact:  "the malformed row and rows depending on it were excluded from wallet checks",
			})
		})
		for _, scope := range accGlobals.Usage {
			if scope.Usage < 0 {
				result.Findings = append(result.Findings, snapshotFinding{
					Code:    "negative-scoped-usage",
					Details: fmt.Sprintf("scope %q has usage %d", scope.Key, scope.Usage),
					Impact:  "absolute reports against this scope use an invalid baseline",
				})
			}
		}
		result.Buckets, result.Summary.AffectedWallets = analyzeLoadedSnapshot(now.Now, persistedWalletValues)
		result.Summary.BrokenBuckets = len(result.Buckets)
		result.Summary.Findings = len(result.Findings)
		for _, bucket := range result.Buckets {
			result.Summary.Findings += len(bucket.Findings)
		}
		slices.SortFunc(result.Findings, compareSnapshotFindings)
		return result
	})

	encoded, err := yaml.Marshal(report)
	if err != nil {
		panic(err)
	}
	return encoded
}

func analyzeLoadedSnapshot(now time.Time, persistedWalletValues map[AccWalletId]snapshotPersistedWalletValues) ([]snapshotBucket, int) {
	var buckets []*internalBucket
	for _, bucket := range accGlobals.BucketsByCategory {
		buckets = append(buckets, bucket)
	}
	slices.SortFunc(buckets, func(a, b *internalBucket) int {
		if result := strings.Compare(a.Category.Provider, b.Category.Provider); result != 0 {
			return result
		}
		return strings.Compare(a.Category.Name, b.Category.Name)
	})

	var result []snapshotBucket
	totalAffectedWallets := 0
	for _, bucket := range buckets {
		seen := map[string]bool{}
		affected := map[AccWalletId]bool{}
		var findings []snapshotFinding

		cycleErrors := lValidateAccountingAcyclic(bucket)
		for _, err := range cycleErrors {
			finding := snapshotFinding{
				Code:    "allocation-cycle",
				Details: err.Error(),
				Impact:  "flow construction is unsafe for this bucket",
			}
			var first, second int
			if _, scanErr := fmt.Sscanf(err.Error(), "allocation graph contains a cycle through wallets %d and %d", &first, &second); scanErr == nil {
				finding.WalletIds = []AccWalletId{AccWalletId(first), AccWalletId(second)}
				affected[AccWalletId(first)] = true
				affected[AccWalletId(second)] = true
			}
			findings = append(findings, finding)
			seen[err.Error()] = true
		}

		walletIds := make([]AccWalletId, 0, len(bucket.WalletsById))
		for walletId := range bucket.WalletsById {
			walletIds = append(walletIds, walletId)
		}
		slices.Sort(walletIds)
		for _, walletId := range walletIds {
			wallet := bucket.WalletsById[walletId]
			for _, err := range lValidateAccountingWalletWithLockCheck(bucket, now, wallet, len(cycleErrors) == 0) {
				if seen[err.Error()] {
					continue
				}
				seen[err.Error()] = true
				affected[walletId] = true
				code := snapshotFindingCode(err.Error())
				findings = append(findings, snapshotFinding{
					Code:      code,
					WalletIds: []AccWalletId{walletId},
					Details:   err.Error(),
					Impact:    snapshotFindingImpact(code),
				})
			}

			persisted, ok := persistedWalletValues[walletId]
			if !ok {
				continue
			}
			derived, overflowedFields := recomputeSnapshotWalletValues(bucket, wallet)
			for _, field := range overflowedFields {
				affected[walletId] = true
				findings = append(findings, snapshotFinding{
					Code:      "arithmetic-overflow",
					WalletIds: []AccWalletId{walletId},
					Details:   fmt.Sprintf("wallet %d cannot recompute %s because the calculation overflows int64", walletId, field),
					Impact:    "the persisted value cannot be checked safely",
				})
			}
			if !slices.Contains(overflowedFields, "excess usage") {
				appendSnapshotValueDivergence(&findings, affected, walletId, "excess-usage-divergence", "excess usage", persisted.ExcessUsage, derived.ExcessUsage)
			}
			if !slices.Contains(overflowedFields, "total allocated") {
				appendSnapshotValueDivergence(&findings, affected, walletId, "total-allocated-divergence", "total allocated", persisted.TotalAllocated, derived.TotalAllocated)
			}
			if !slices.Contains(overflowedFields, "total retired allocated") {
				appendSnapshotValueDivergence(&findings, affected, walletId, "total-retired-allocated-divergence", "total retired allocated", persisted.TotalRetiredAllocated, derived.TotalRetiredAllocated)
			}
		}

		for _, wallet := range bucket.WalletsById {
			for _, group := range wallet.AllocationsByParent {
				for allocationId := range group.Allocations {
					allocation := bucket.AllocationsById[allocationId]
					if allocation != nil && allocation.Retired && now.Before(allocation.End) {
						affected[wallet.Id] = true
						findings = append(findings, snapshotFinding{
							Code:          "allocation-retired-before-end",
							WalletIds:     []AccWalletId{wallet.Id},
							GroupIds:      []accGroupId{group.Id},
							AllocationIds: []accAllocId{allocation.Id},
							Details:       fmt.Sprintf("allocation %d is retired but does not end until %s", allocation.Id, allocation.End.UTC().Format(time.RFC3339)),
							Impact:        "the allocation is unavailable before the end of its validity interval",
						})
					}
				}

				if bucket.IsCapacityBased() {
					continue
				}
				retiredFloor := int64(0)
				overflow := false
				for allocationId := range group.Allocations {
					allocation := bucket.AllocationsById[allocationId]
					if allocation != nil && allocation.Retired {
						var itemOverflow bool
						retiredFloor, itemOverflow = checkedAccountingAdd(retiredFloor, allocation.RetiredUsage)
						overflow = overflow || itemOverflow
					}
				}
				if !overflow && group.TreeUsage < retiredFloor {
					affected[wallet.Id] = true
					findings = append(findings, snapshotFinding{
						Code:      "periodic-flow-below-retired-usage",
						WalletIds: []AccWalletId{wallet.Id},
						GroupIds:  []accGroupId{group.Id},
						Details:   fmt.Sprintf("group %d has tree usage %d below retired committed usage %d", group.Id, group.TreeUsage, retiredFloor),
						Impact:    "historic periodic usage is no longer fully represented by group flow",
					})
				}
			}
		}

		if len(findings) == 0 {
			continue
		}
		affectedWalletIds := make([]AccWalletId, 0, len(affected))
		for walletId := range affected {
			affectedWalletIds = append(affectedWalletIds, walletId)
		}
		slices.Sort(affectedWalletIds)
		slices.SortFunc(findings, compareSnapshotFindings)
		totalAffectedWallets += len(affectedWalletIds)
		result = append(result, snapshotBucket{
			Provider:          bucket.Category.Provider,
			Category:          bucket.Category.Name,
			CapacityBased:     bucket.IsCapacityBased(),
			Wallets:           len(bucket.WalletsById),
			AffectedWalletIds: affectedWalletIds,
			Findings:          findings,
		})
	}
	return result, totalAffectedWallets
}

func recomputeSnapshotWalletValues(bucket *internalBucket, wallet *internalWallet) (snapshotPersistedWalletValues, []string) {
	result := snapshotPersistedWalletValues{Id: wallet.Id, ExcessUsage: wallet.LocalUsage}
	var overflowedFields []string
	excessOverflow := false
	for _, childUsage := range wallet.ChildrenUsage {
		var overflow bool
		result.ExcessUsage, overflow = checkedAccountingAdd(result.ExcessUsage, childUsage)
		excessOverflow = excessOverflow || overflow
	}
	for _, group := range wallet.AllocationsByParent {
		if group == nil {
			continue
		}
		var overflow bool
		result.ExcessUsage, overflow = checkedAccountingSub(result.ExcessUsage, group.TreeUsage)
		excessOverflow = excessOverflow || overflow
	}
	if excessOverflow {
		overflowedFields = append(overflowedFields, "excess usage")
	}

	totalAllocatedOverflow := false
	totalRetiredAllocatedOverflow := false
	for _, allocation := range bucket.AllocationsById {
		if allocation == nil || allocation.Parent != wallet.Id || !allocation.Committed {
			continue
		}
		if allocation.Retired {
			var overflow bool
			result.TotalRetiredAllocated, overflow = checkedAccountingAdd(result.TotalRetiredAllocated, allocation.RetiredQuota)
			totalRetiredAllocatedOverflow = totalRetiredAllocatedOverflow || overflow
		} else if allocation.Active {
			var overflow bool
			result.TotalAllocated, overflow = checkedAccountingAdd(result.TotalAllocated, allocation.Quota)
			totalAllocatedOverflow = totalAllocatedOverflow || overflow
		}
	}
	if totalAllocatedOverflow {
		overflowedFields = append(overflowedFields, "total allocated")
	}
	if totalRetiredAllocatedOverflow {
		overflowedFields = append(overflowedFields, "total retired allocated")
	}
	return result, overflowedFields
}

func appendSnapshotValueDivergence(findings *[]snapshotFinding, affected map[AccWalletId]bool, walletId AccWalletId, code, field string, persisted, recomputed int64) {
	if persisted == recomputed {
		return
	}
	affected[walletId] = true
	*findings = append(*findings, snapshotFinding{
		Code:            code,
		WalletIds:       []AccWalletId{walletId},
		PersistedValue:  &persisted,
		RecomputedValue: &recomputed,
		Details:         fmt.Sprintf("wallet %d has persisted %s %d but recomputed value is %d", walletId, field, persisted, recomputed),
		Impact:          "the persisted legacy wallet cache disagrees with values derived from accounting flow and allocations",
	})
}

func snapshotFindingCode(message string) string {
	switch {
	case strings.Contains(message, "negative"):
		return "negative-value"
	case strings.Contains(message, "overflow"):
		return "arithmetic-overflow"
	case strings.Contains(message, "exceeds contributing quota"):
		return "group-flow-exceeds-quota"
	case strings.Contains(message, "propagates"):
		return "flow-conservation"
	case strings.Contains(message, "mirrored"), strings.Contains(message, "child usage"):
		return "parent-child-flow-mismatch"
	case strings.Contains(message, "WasLocked"):
		return "stale-lock-state"
	case strings.Contains(message, "validity interval"), strings.Contains(message, "exclusive end"), strings.Contains(message, "invalid interval"):
		return "allocation-lifecycle"
	case strings.Contains(message, "retired"):
		return "retirement-state"
	default:
		return "accounting-invariant"
	}
}

func snapshotLoadFindingCode(message string) string {
	switch {
	case strings.HasPrefix(message, "duplicate accounting wallets"):
		return "duplicate-wallet"
	case strings.HasPrefix(message, "duplicate accounting allocation groups"):
		return "duplicate-allocation-group"
	default:
		return "unloadable-accounting-row"
	}
}

func snapshotFindingImpact(code string) string {
	switch code {
	case "negative-value", "arithmetic-overflow":
		return "accounting arithmetic cannot safely use this state"
	case "group-flow-exceeds-quota":
		return "the residual graph would contain negative capacity"
	case "flow-conservation":
		return "more usage is propagated than exists at the wallet"
	case "parent-child-flow-mismatch":
		return "the same allocation flow has conflicting persisted values"
	case "stale-lock-state":
		return "the persisted availability state disagrees with current usable quota"
	case "allocation-lifecycle", "retirement-state":
		return "allocation availability or retained periodic quota is inconsistent"
	default:
		return "the persisted wallet does not satisfy the accounting model"
	}
}

func compareSnapshotFindings(a, b snapshotFinding) int {
	if result := strings.Compare(a.Code, b.Code); result != 0 {
		return result
	}
	return strings.Compare(a.Details, b.Details)
}
