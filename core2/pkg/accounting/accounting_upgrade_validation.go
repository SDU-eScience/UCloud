package accounting

import (
	"encoding/json"
	"fmt"
	"time"
)

func accountingUpgradeValidateSource(bundle *accountingUpgradeBundle) {
	categories := map[int64]bool{}
	for _, raw := range bundle.Raw["categories"] {
		var row struct{ Id int64 }
		if err := json.Unmarshal(raw, &row); err != nil {
			panic(err)
		}
		categories[row.Id] = true
	}
	owners := map[int64]bool{}
	references := map[string]bool{}
	for _, raw := range bundle.Raw["owners"] {
		var row struct {
			Id       int64   `json:"id"`
			Username *string `json:"username"`
			Project  *string `json:"project_id"`
		}
		if err := json.Unmarshal(raw, &row); err != nil {
			panic(err)
		}
		if (row.Username == nil) == (row.Project == nil) {
			bundle.Blockers = append(bundle.Blockers, fmt.Sprintf("owner %d has no unambiguous owner type", row.Id))
			continue
		}
		reference := ""
		if row.Username != nil {
			reference = *row.Username
		} else {
			reference = *row.Project
		}
		if reference == "" || references[reference] {
			bundle.Blockers = append(bundle.Blockers, fmt.Sprintf("owner %d has an empty or duplicate reference", row.Id))
		}
		references[reference] = true
		owners[row.Id] = true
	}
	wallets := map[int64]int64{}
	for _, raw := range bundle.Raw["wallets"] {
		var row struct {
			Id       int64 `json:"id"`
			Owner    int64 `json:"wallet_owner"`
			Category int64 `json:"product_category"`
		}
		if err := json.Unmarshal(raw, &row); err != nil {
			panic(err)
		}
		if !owners[row.Owner] {
			bundle.Blockers = append(bundle.Blockers, fmt.Sprintf("wallet %d references unavailable owner %d", row.Id, row.Owner))
		}
		if !categories[row.Category] {
			bundle.Blockers = append(bundle.Blockers, fmt.Sprintf("wallet %d references unavailable category %d", row.Id, row.Category))
		}
		wallets[row.Id] = row.Category
	}
	groups := map[int64]bool{}
	for _, raw := range bundle.Raw["groups"] {
		var row struct {
			Id     int64  `json:"id"`
			Wallet int64  `json:"associated_wallet"`
			Parent *int64 `json:"parent_wallet"`
		}
		if err := json.Unmarshal(raw, &row); err != nil {
			panic(err)
		}
		category, exists := wallets[row.Wallet]
		if !exists {
			bundle.Blockers = append(bundle.Blockers, fmt.Sprintf("group %d references unavailable recipient", row.Id))
		}
		if row.Parent != nil {
			parentCategory, parentExists := wallets[*row.Parent]
			if !parentExists || parentCategory != category {
				bundle.Blockers = append(bundle.Blockers, fmt.Sprintf("group %d has a missing or cross-category parent", row.Id))
			}
		}
		groups[row.Id] = true
	}
	for _, raw := range bundle.Raw["allocations"] {
		var row struct {
			Id    int64 `json:"id"`
			Group int64 `json:"associated_allocation_group"`
		}
		if err := json.Unmarshal(raw, &row); err != nil {
			panic(err)
		}
		if !groups[row.Group] {
			bundle.Blockers = append(bundle.Blockers, fmt.Sprintf("allocation %d references unavailable group %d", row.Id, row.Group))
		}
	}
}

func accountingUpgradeValidateFacts(bundle *accountingUpgradeBundle) {
	wallets := []duplicateRepairWalletRow{}
	for _, raw := range bundle.Raw["wallets"] {
		var row struct {
			Id       int64 `json:"id"`
			Owner    int64 `json:"wallet_owner"`
			Category int64 `json:"product_category"`
			Usage    int64 `json:"local_usage"`
		}
		if err := json.Unmarshal(raw, &row); err != nil {
			panic(err)
		}
		wallets = append(wallets, duplicateRepairWalletRow{Id: row.Id, WalletOwner: row.Owner, ProductCategory: row.Category, LocalUsage: row.Usage})
	}
	ids, usage := duplicateRepairCanonicalUsage(wallets)
	expected := map[int64]int64{}
	for i, id := range ids {
		expected[id] = max(0, usage[i])
	}
	for _, capacity := range bundle.Capacity {
		if expected[int64(capacity.WalletId)] != capacity.LocalBefore {
			bundle.Blockers = append(
				bundle.Blockers,
				fmt.Sprintf("wallet %d changed before capacity reconciliation without an approved fact rule", capacity.WalletId),
			)
		}
		expected[int64(capacity.WalletId)] = max(capacity.LocalBefore, capacity.Scoped)
	}
	seen := map[int64]bool{}
	for _, wallet := range bundle.After.Wallets {
		id := int64(wallet.Id)
		if !wallet.Loaded || wallet.DerivedError != "" {
			bundle.Blockers = append(bundle.Blockers, fmt.Sprintf("wallet %d has no valid normal-reader projection: %s", id, wallet.DerivedError))
		}
		value, exists := expected[id]
		if !exists || value != wallet.LocalUsage {
			bundle.Blockers = append(bundle.Blockers, fmt.Sprintf("wallet %d expected approved local usage %d, got %d", id, value, wallet.LocalUsage))
		}
		seen[id] = true
	}
	for id := range expected {
		if !seen[id] {
			bundle.Blockers = append(bundle.Blockers, fmt.Sprintf("canonical wallet %d disappeared", id))
		}
	}
	removed := map[int64]bool{}
	for _, deletion := range bundle.Cycles.AllocationDeletions {
		removed[deletion.Id] = true
	}
	for _, deletion := range bundle.Repairs.AllocationDeletions {
		removed[deletion.Id] = true
	}
	allocations := map[int64]findingsRepairAllocationUpdate{}
	for _, raw := range bundle.Raw["allocations"] {
		var row struct {
			Id           int64
			Quota        int64
			Start        time.Time `json:"allocation_start_time"`
			End          time.Time `json:"allocation_end_time"`
			Retired      bool
			RetiredUsage int64 `json:"retired_usage"`
			RetiredQuota int64 `json:"retired_quota"`
		}
		if err := json.Unmarshal(raw, &row); err != nil {
			panic(err)
		}
		if !removed[row.Id] {
			allocations[row.Id] = findingsRepairAllocationUpdate{
				Id: row.Id, Quota: row.Quota, Start: row.Start, End: row.End,
				Retired: row.Retired, RetiredUsage: row.RetiredUsage, RetiredQuota: row.RetiredQuota,
			}
		}
	}
	for _, update := range bundle.Repairs.AllocationUpdates {
		if _, exists := allocations[update.Id]; !exists {
			bundle.Blockers = append(bundle.Blockers, fmt.Sprintf("repair updates unavailable allocation %d", update.Id))
			continue
		}
		allocations[update.Id] = update
	}
	for _, allocation := range bundle.After.Allocations {
		id := int64(allocation.Id)
		expected, exists := allocations[id]
		if !exists {
			bundle.Blockers = append(bundle.Blockers, fmt.Sprintf("unexpected allocation %d after upgrade", id))
		} else if allocation.Quota != expected.Quota ||
			!allocation.Start.Equal(expected.Start) ||
			!allocation.End.Equal(expected.End) ||
			allocation.Retired != expected.Retired ||
			allocation.RetiredUsage != expected.RetiredUsage ||
			allocation.RetiredQuota != expected.RetiredQuota {
			bundle.Blockers = append(bundle.Blockers, fmt.Sprintf("allocation %d changed beyond the approved repair rules", id))
		}
		delete(allocations, id)
	}
	for id := range allocations {
		bundle.Blockers = append(bundle.Blockers, fmt.Sprintf("allocation %d disappeared without an approved cycle rule", id))
	}
	baselines := map[string]int64{}
	for _, mapping := range bundle.Mappings {
		key := fmt.Sprintf("%d\n%s", mapping.WalletId, mapping.Key)
		baselines[key] = max(baselines[key], mapping.Usage)
	}
	for _, scope := range bundle.After.Scopes {
		key := fmt.Sprintf("%d\n%s", scope.WalletId, scope.Key)
		value, exists := baselines[key]
		if !exists || scope.Usage != value {
			bundle.Blockers = append(bundle.Blockers, fmt.Sprintf("scope %q changed beyond the approved maximum and clamp rules", key))
		}
		delete(baselines, key)
	}
	for key := range baselines {
		bundle.Blockers = append(bundle.Blockers, fmt.Sprintf("scope baseline %q disappeared", key))
	}
	if len(bundle.Mappings) != len(bundle.Raw["scopes"]) {
		bundle.Blockers = append(bundle.Blockers, "not every original scope has a recorded mapping")
	}
	if len(bundle.Blockers) == 0 {
		bundle.Validation = map[string]bool{
			"canonicalWalletUsage":       true,
			"allocationIdsAccounted":     true,
			"allocationFactsPreserved":   true,
			"scopeBaselinesPreserved":    true,
			"scopeRowsAccounted":         true,
			"normalReaderFlowInvariants": true,
		}
	}
}
