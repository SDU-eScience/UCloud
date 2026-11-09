package accounting

import (
	"bytes"
	"compress/gzip"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"runtime/debug"
	"slices"
	"strconv"
	"strings"
	"time"

	cfg "ucloud.dk/core/pkg/config"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/log"
)

const accountingUpgradeVersion = "accountingV6"

type accountingUpgradeScope struct {
	WalletId      int64
	Key           string
	Usage         int64
	LastUpdatedAt time.Time
}

type accountingUpgradeWallet struct {
	Id              int64
	WalletOwner     int64
	ProductCategory int64
	LocalUsage      int64
	Provider        string
	Category        string
	ProductType     string
}

type accountingUpgradeResource struct {
	Id          int64
	Provider    string
	Category    string
	ProductType string
}

type accountingUpgradeMapping struct {
	OriginalWallet int64
	OriginalKey    string
	WalletId       int64
	Key            string
	Usage          int64
	Rule           string
}

type accountingUpgradeProject struct {
	Id     string
	Title  string
	Parent string
}

type accountingUpgradeCapacity struct {
	WalletId    AccWalletId
	LocalBefore int64
	LocalAfter  int64
	Scoped      int64
	Unscoped    int64
}

type accountingUpgradeBundle struct {
	Version         string
	RunId           string
	CapturedAt      time.Time
	InputHash       string
	Status          string
	Phase           string
	Raw             map[string][]json.RawMessage
	Projects        []accountingUpgradeProject
	Mappings        []accountingUpgradeMapping
	Cycles          findingsRepairPlan
	Duplicates      duplicateRepairReport
	Repairs         findingsRepairReport
	Capacity        []accountingUpgradeCapacity
	Before          AccountingAuditCapture
	After           AccountingAuditCapture
	Warnings        []string
	Blockers        []string
	DurationSeconds float64
	Validation      map[string]bool
	ArtifactVersion int
	Build           map[string]string
}

func accountingRepairTransactionMode(tx *db.Transaction, apply bool) {
	if !apply {
		db.Exec(
			tx,
			"set transaction isolation level repeatable read, read only",
			db.Params{},
		)
		return
	}
	db.Exec(
		tx,
		"set transaction isolation level serializable",
		db.Params{},
	)
	accountingUpgradeLockTables(tx)
}

func accountingUpgradeLockTables(tx *db.Transaction) {
	db.Exec(
		tx,
		`lock table accounting.wallets_v2, accounting.allocation_groups,
		accounting.wallet_allocations_v2, accounting.scoped_usage, accounting.wallet_samples_v2,
		accounting.wallet_snapshots, accounting.usage_report, accounting.intermediate_usage in access exclusive mode`,
		db.Params{},
	)
}

func accountingUpgradeDirectory() string {
	if path := os.Getenv("UCLOUD_ACCOUNTING_UPGRADE_DIR"); path != "" {
		return path
	}
	base := "/var/log/ucloud"
	if cfg.Configuration != nil && cfg.Configuration.Logs.Directory != "" {
		base = cfg.Configuration.Logs.Directory
	}
	return filepath.Join(base, "accounting-upgrade")
}

func accountingUpgradeEncode(value any) []byte {
	data, err := json.Marshal(value)
	if err != nil {
		panic(err)
	}
	var output bytes.Buffer
	writer := gzip.NewWriter(&output)
	if _, err = writer.Write(data); err != nil {
		panic(err)
	}
	if err = writer.Close(); err != nil {
		panic(err)
	}
	return output.Bytes()
}

func accountingUpgradeWrite(directory, name string, data []byte) {
	if err := os.MkdirAll(directory, 0700); err != nil {
		panic(err)
	}
	file, err := os.CreateTemp(directory, ".accounting-*")
	if err != nil {
		panic(err)
	}
	path := file.Name()
	defer os.Remove(path)
	if _, err = file.Write(data); err == nil {
		err = file.Sync()
	}
	closeErr := file.Close()
	if err != nil {
		panic(err)
	}
	if closeErr != nil {
		panic(closeErr)
	}
	if err = os.Rename(path, filepath.Join(directory, name)); err != nil {
		panic(err)
	}
	dir, err := os.Open(directory)
	if err != nil {
		panic(err)
	}
	defer dir.Close()
	if err = dir.Sync(); err != nil {
		panic(err)
	}
}

func accountingUpgradeCheck(tx *db.Transaction, bundle *accountingUpgradeBundle) {
	if err := tx.PeekError(); err != nil {
		panic(fmt.Sprintf("accounting upgrade database failure in %s: %v", bundle.Phase, err))
	}
	if len(bundle.Blockers) != 0 {
		panic(strings.Join(bundle.Blockers, "; "))
	}
}

func AccountingUpgrade(tx *db.Transaction) {
	started := time.Now()
	tx.NoDevResetThisIsNotAHackIPromise = true
	bundle := accountingUpgradeBundle{Version: accountingUpgradeVersion, ArtifactVersion: 1, Status: "attempting", Phase: "capture", Build: map[string]string{}}
	if info, ok := debug.ReadBuildInfo(); ok {
		bundle.Build["goVersion"] = info.GoVersion
		for _, setting := range info.Settings {
			if setting.Key == "vcs.revision" || setting.Key == "vcs.modified" || setting.Key == "GOOS" || setting.Key == "GOARCH" {
				bundle.Build[setting.Key] = setting.Value
			}
		}
	}
	directory := ""
	defer func() {
		if failure := recover(); failure != nil {
			bundle.Status = "blocked"
			bundle.Blockers = append(bundle.Blockers, fmt.Sprint(failure))
			if directory != "" {
				accountingUpgradeWrite(directory, "failure.json.gz", accountingUpgradeEncode(bundle))
				accountingUpgradeExport(bundle)
			}
			panic(failure)
		}
	}()
	accountingUpgradeLockTables(tx)
	now, ok := db.Get[struct{ Now time.Time }](
		tx,
		"select transaction_timestamp() as now",
		db.Params{},
	)
	if !ok {
		panic("cannot capture accounting upgrade time")
	}
	bundle.CapturedAt = now.Now
	bundle.Raw = accountingUpgradeRaw(tx)
	accountingUpgradeCheck(tx, &bundle)
	encoded, err := json.Marshal(bundle.Raw)
	if err != nil {
		panic(err)
	}
	hash := sha256.Sum256(encoded)
	bundle.InputHash = hex.EncodeToString(hash[:])
	bundle.RunId = now.Now.UTC().Format("20060102T150405.000000000Z") + "-" + bundle.InputHash[:12]
	directory = filepath.Join(accountingUpgradeDirectory(), bundle.RunId)
	accountingUpgradeWrite(directory, "source.json.gz", accountingUpgradeEncode(bundle.Raw))
	log.Info("Accounting upgrade evidence: %s", directory)
	bundle.Projects = db.Select[accountingUpgradeProject](
		tx,
		`select id, title, coalesce(parent, '') as parent from project.projects order by id`,
		db.Params{},
	)
	accountingUpgradeValidateSource(&bundle)
	accountingUpgradeCheck(tx, &bundle)
	bundle.Phase = "scope mapping"
	accountingUpgradeScopes(tx, &bundle)
	accountingUpgradeCheck(tx, &bundle)
	bundle.Before = accountingUpgradeCapture(tx, now.Now, true)
	bundle.Phase = "approved cycle and duplicate repairs"
	accountingUpgradeCycles(tx, &bundle)
	bundle.Duplicates = accountingRepairDuplicatesTx(tx, true)
	bundle.Blockers = append(bundle.Blockers, bundle.Duplicates.Blockers...)
	accountingUpgradeCheck(tx, &bundle)
	bundle.Phase = "approved findings repairs"
	bundle.Repairs = accountingRepairFindingsTx(tx, true)
	bundle.Blockers = append(bundle.Blockers, bundle.Repairs.Blockers...)
	accountingUpgradeCheck(tx, &bundle)
	bundle.Phase = "derived flow"
	accountingUpgradeFlow(tx, &bundle)
	accountingUpgradeCheck(tx, &bundle)
	bundle.Phase = "validation"
	bundle.After = accountingUpgradeCapture(tx, now.Now, false)
	for _, bucket := range accGlobals.BucketsByCategory {
		if err := lValidateAccountingTree(bucket, now.Now); err != nil {
			bundle.Blockers = append(bundle.Blockers, err.Error())
		}
	}
	for _, finding := range bundle.After.Findings {
		if finding.Code == "periodic-flow-below-retired-usage" || finding.Code == "negative-scoped-usage" {
			bundle.Blockers = append(bundle.Blockers, finding.Details)
		}
	}
	accountingUpgradeValidateFacts(&bundle)
	accountingUpgradeCheck(tx, &bundle)
	db.Exec(
		tx,
		`create unique index wallets_v2_owner_category_unique on accounting.wallets_v2(wallet_owner, product_category)`,
		db.Params{},
	)
	db.Exec(
		tx,
		`create unique index allocation_groups_wallet_parent_unique on accounting.allocation_groups(associated_wallet, coalesce(parent_wallet, 0))`,
		db.Params{},
	)
	bundle.Phase = "validated pending commit"
	bundle.Status = "validated"
	bundle.DurationSeconds = time.Since(started).Seconds()
	accountingUpgradeCheck(tx, &bundle)
	accountingUpgradeWrite(directory, "candidate.json.gz", accountingUpgradeEncode(bundle))
	db.Exec(
		tx,
		`create table accounting.upgrade_runs (
			version text primary key, run_id text not null unique, captured_at timestamptz not null,
			proof bytea not null
		)`,
		db.Params{},
	)
	db.Exec(
		tx,
		`insert into accounting.upgrade_runs(version, run_id, captured_at, proof) values (:version, :run_id, :at, :proof)`,
		db.Params{"version": accountingUpgradeVersion, "run_id": bundle.RunId, "at": now.Now, "proof": accountingUpgradeEncode(bundle)},
	)
	accountingUpgradeCheck(tx, &bundle)
}

func accountingUpgradeRaw(tx *db.Transaction) map[string][]json.RawMessage {
	losers := db.Select[struct{ Id int64 }](
		tx,
		`
			select id
			from
				(
					select
						id,
						row_number() over(partition by wallet_owner, product_category order by id) as rank
					from
						accounting.wallets_v2
				) w
			where
				rank > 1
			order by id
		`,
		db.Params{},
	)
	ids := []int64{}
	for _, loser := range losers {
		ids = append(ids, loser.Id)
	}
	queries := []struct{ Name, Query string }{
		{"owners", `select to_jsonb(r)::text as data from accounting.wallet_owner r order by id`},
		{"wallets", `select to_jsonb(r)::text as data from accounting.wallets_v2 r order by id`},
		{"groups", `select to_jsonb(r)::text as data from accounting.allocation_groups r order by id`},
		{"allocations", `select to_jsonb(r)::text as data from accounting.wallet_allocations_v2 r order by id`},
		{"scopes", `select to_jsonb(r)::text as data from accounting.scoped_usage r order by key, to_jsonb(r)::text`},
		{"categories", `select to_jsonb(r)::text as data from accounting.product_categories r order by id`},
		{"units", `select to_jsonb(r)::text as data from accounting.accounting_units r order by id`},
		{"products", `select to_jsonb(r)::text as data from accounting.products r order by id`},
		{
			"samples",
			`select to_jsonb(r)::text as data
			from
				accounting.wallet_samples_v2 r
			where wallet_id = any(cast(:ids as int8[]))
			order by wallet_id, sampled_at`,
		},
		{
			"snapshots",
			`select to_jsonb(r)::text as data
			from
				accounting.wallet_snapshots r
			where id = any(cast(:ids as int8[]))
			order by id`,
		},
		{
			"reports",
			`select to_jsonb(r)::text as data
			from
				accounting.usage_report r
			where wallet_id = any(cast(:ids as int8[]))
			order by wallet_id, valid_from`,
		},
		{
			"intermediate",
			`select to_jsonb(r)::text as data
			from
				accounting.intermediate_usage r
			where wallet_id = any(cast(:ids as int8[]))
			order by wallet_id, to_jsonb(r)::text`,
		},
		{"projects", `select jsonb_build_object('id', id, 'title', title, 'parent', parent)::text as data from project.projects order by id`},
		{"resource-mapping", `
			select jsonb_build_object('id', r.id, 'provider', pc.provider, 'category', pc.category, 'productType', pc.product_type)::text as data
			from
				provider.resource r
				join accounting.products p on p.id = r.product
				join accounting.product_categories pc on pc.id = p.category
			where r.id::text in (select substring(key from '([0-9]+)$') from accounting.scoped_usage)
			order by r.id
		`},
	}
	result := map[string][]json.RawMessage{}
	for _, query := range queries {
		rows := db.Select[struct{ Data string }](
			tx,
			query.Query,
			db.Params{"ids": ids},
		)
		result[query.Name] = []json.RawMessage{}
		for _, row := range rows {
			result[query.Name] = append(result[query.Name], json.RawMessage(row.Data))
		}
	}
	return result
}

func accountingUpgradeScopes(tx *db.Transaction, bundle *accountingUpgradeBundle) {
	wallets := db.Select[accountingUpgradeWallet](
		tx,
		`select w.id, w.wallet_owner, w.product_category, w.local_usage,
			pc.provider, pc.category, pc.product_type
		from
			accounting.wallets_v2 w
			join accounting.product_categories pc on pc.id = w.product_category
		order by w.id`,
		db.Params{},
	)
	resources := db.Select[accountingUpgradeResource](
		tx,
		`select r.id, pc.provider, pc.category, pc.product_type
		from
			provider.resource r
			join accounting.products p on p.id = r.product
			join accounting.product_categories pc on pc.id = p.category
		where r.id::text in (select substring(key from '([0-9]+)$') from accounting.scoped_usage) order by r.id`,
		db.Params{},
	)
	byResource := map[int64]accountingUpgradeResource{}
	for _, resource := range resources {
		byResource[resource.Id] = resource
	}
	byWallet := map[int64]accountingUpgradeWallet{}
	byOwner := map[int64][]accountingUpgradeWallet{}
	canonical := map[string]int64{}
	for _, wallet := range wallets {
		byWallet[wallet.Id] = wallet
		key := fmt.Sprintf("%d/%d", wallet.WalletOwner, wallet.ProductCategory)
		if _, exists := canonical[key]; !exists {
			canonical[key] = wallet.Id
			byOwner[wallet.WalletOwner] = append(byOwner[wallet.WalletOwner], wallet)
		}
	}
	columns, _ := db.Get[struct{ Wallet bool }](
		tx,
		`select exists(
			select 1
			from information_schema.columns
			where
				table_schema = 'accounting'
				and table_name = 'scoped_usage'
				and column_name = 'wallet_id'
		) as wallet`,
		db.Params{},
	)
	var scopes []accountingUpgradeScope
	if columns.Wallet {
		scopes = db.Select[accountingUpgradeScope](
			tx,
			`select wallet_id, key, usage, last_updated_at from accounting.scoped_usage order by wallet_id, key`,
			db.Params{},
		)
	} else {
		scopes = db.Select[accountingUpgradeScope](
			tx,
			`select 0::int8 as wallet_id, key, usage, transaction_timestamp() as last_updated_at from accounting.scoped_usage order by key`,
			db.Params{},
		)
		bundle.Warnings = append(
			bundle.Warnings,
			"Legacy scope observation times are unknown; assigned cutover timestamps are not measurements or "+
				"consumption events",
		)
	}
	targets := map[string]accountingUpgradeScope{}
	for _, scope := range scopes {
		if scope.LastUpdatedAt.After(bundle.CapturedAt) {
			bundle.Blockers = append(bundle.Blockers, fmt.Sprintf("scope %q has a future observation time", scope.Key))
		}
		originalKey := scope.Key
		ownerId := int64(0)
		candidates := []accountingUpgradeWallet{}
		if scope.WalletId != 0 {
			if wallet, exists := byWallet[scope.WalletId]; exists {
				ownerId = wallet.WalletOwner
				candidates = append(candidates, byWallet[canonical[fmt.Sprintf("%d/%d", wallet.WalletOwner, wallet.ProductCategory)]])
			}
		} else if prefix, key, ok := strings.Cut(scope.Key, "\n"); ok {
			ownerId, _ = strconv.ParseInt(prefix, 10, 64)
			scope.Key = key
			candidates = byOwner[ownerId]
		}
		if ownerId == 0 || len(candidates) == 0 {
			bundle.Blockers = append(bundle.Blockers, fmt.Sprintf("scope %q has no retained wallet identity", originalKey))
			continue
		}
		selected, key := accountingUpgradeScopeTarget(scope.Key, candidates, byResource)
		if selected == 0 {
			bundle.Blockers = append(bundle.Blockers, fmt.Sprintf("scope %q cannot be assigned to one product wallet", originalKey))
			continue
		}
		bundle.Mappings = append(bundle.Mappings, accountingUpgradeMapping{
			OriginalWallet: scope.WalletId, OriginalKey: originalKey,
			WalletId: selected, Key: key, Usage: scope.Usage, Rule: "retained-owner-product",
		})
		scope.WalletId = selected
		scope.Key = key
		targetKey := fmt.Sprintf("%d\n%s", selected, key)
		if old, exists := targets[targetKey]; exists {
			bundle.Warnings = append(bundle.Warnings, fmt.Sprintf("duplicate scope %s uses approved maximum-usage rule", targetKey))
			if old.Usage >= scope.Usage {
				continue
			}
		}
		targets[targetKey] = scope
	}
	accountingUpgradeCheck(tx, bundle)
	db.Exec(
		tx,
		`create table accounting.scoped_usage_legacy_v6 as table accounting.scoped_usage`,
		db.Params{},
	)
	db.Exec(
		tx,
		`drop table accounting.scoped_usage`,
		db.Params{},
	)
	db.Exec(
		tx,
		`create table accounting.scoped_usage (
			wallet_id int8 not null references accounting.wallets_v2(id), key text not null,
			usage int8 not null, last_updated_at timestamptz not null,
			constraint scoped_usage_wallet_key_v6 primary key(wallet_id, key)
		)`,
		db.Params{},
	)
	keys := make([]string, 0, len(targets))
	for key := range targets {
		keys = append(keys, key)
	}
	slices.Sort(keys)
	ids, names, values, times := []int64{}, []string{}, []int64{}, []time.Time{}
	for _, key := range keys {
		scope := targets[key]
		ids = append(ids, scope.WalletId)
		names = append(names, scope.Key)
		values = append(values, scope.Usage)
		times = append(times, scope.LastUpdatedAt)
	}
	db.Exec(
		tx,
		`insert into accounting.scoped_usage(wallet_id, key, usage, last_updated_at)
		select unnest(cast(:ids as int8[])), unnest(cast(:keys as text[])),
			unnest(cast(:usage as int8[])), unnest(cast(:times as timestamptz[]))`,
		db.Params{"ids": ids, "keys": names, "usage": values, "times": times},
	)
}

func accountingUpgradeScopeTarget(key string, candidates []accountingUpgradeWallet, resources map[int64]accountingUpgradeResource) (int64, string) {
	if strings.HasPrefix(key, "inference-") {
		matches := []accountingUpgradeWallet{}
		for _, wallet := range candidates {
			if strings.HasPrefix(key, "inference-"+wallet.Provider+"-"+wallet.Category+"-") {
				matches = append(matches, wallet)
			}
		}
		if len(matches) == 1 {
			return matches[0].Id, key
		}
		return 0, key
	}
	resourceId := int64(0)
	explicitProvider := ""
	if value, ok := strings.CutPrefix(key, "job-"); ok {
		resourceId, _ = strconv.ParseInt(value, 10, 64)
	}
	if value, ok := strings.CutPrefix(key, "repository-"); ok {
		resourceId, _ = strconv.ParseInt(value, 10, 64)
	}
	if resourceId == 0 {
		resourceId, _ = strconv.ParseInt(key, 10, 64)
	}
	for _, wallet := range candidates {
		if value, ok := strings.CutPrefix(key, "drive-"+wallet.Provider+"-"); ok {
			if id, err := strconv.ParseInt(value, 10, 64); err == nil && id > 0 {
				resourceId, explicitProvider = id, wallet.Provider
			}
		}
	}
	if explicitProvider != "" {
		filtered := []accountingUpgradeWallet{}
		for _, wallet := range candidates {
			if wallet.Provider == explicitProvider {
				filtered = append(filtered, wallet)
			}
		}
		candidates = filtered
	}
	resource, exists := resources[resourceId]
	if exists {
		matches := []accountingUpgradeWallet{}
		for _, wallet := range candidates {
			if wallet.Category == resource.Category &&
				(wallet.Provider == resource.Provider || resource.Provider == "ucloud" || wallet.Provider == explicitProvider) {
				matches = append(matches, wallet)
			}
		}
		if len(matches) == 1 {
			if key == strconv.FormatInt(resourceId, 10) && resource.ProductType == "COMPUTE" {
				key = "job-" + key
			}
			return matches[0].Id, key
		}
		return 0, key
	}
	productType := ""
	if strings.HasPrefix(key, "job-") {
		productType = "COMPUTE"
	}
	if strings.HasPrefix(key, "drive-") || strings.HasPrefix(key, "repository-") {
		productType = "STORAGE"
	}
	if productType != "" {
		filtered := []accountingUpgradeWallet{}
		for _, wallet := range candidates {
			if wallet.ProductType == productType {
				filtered = append(filtered, wallet)
			}
		}
		candidates = filtered
	}
	if len(candidates) == 1 {
		return candidates[0].Id, key
	}
	return 0, key
}

func accountingUpgradeCycles(tx *db.Transaction, bundle *accountingUpgradeBundle) {
	wallets := db.Select[duplicateRepairWalletRow](
		tx,
		`select id, wallet_owner, product_category, local_usage from accounting.wallets_v2 order by id`,
		db.Params{},
	)
	canonical := map[string]int64{}
	keep := map[int64]int64{}
	for _, wallet := range wallets {
		key := fmt.Sprintf("%d/%d", wallet.WalletOwner, wallet.ProductCategory)
		if canonical[key] == 0 {
			canonical[key] = wallet.Id
		}
		keep[wallet.Id] = canonical[key]
	}
	groups := selectFindingsRepairGroups(tx)
	for i := range groups {
		group := &groups[i]
		if keep[group.AssociatedWallet] == 0 || group.ParentWallet.Valid && keep[group.ParentWallet.Int64] == 0 {
			bundle.Blockers = append(bundle.Blockers, fmt.Sprintf("group %d has a missing wallet", group.Id))
		}
		group.AssociatedWallet = keep[group.AssociatedWallet]
		if group.ParentWallet.Valid {
			group.ParentWallet.Int64 = keep[group.ParentWallet.Int64]
		}
	}
	accountingUpgradeCheck(tx, bundle)
	bundle.Cycles = planFindingsRepairCycles(groups, selectFindingsRepairAllocations(tx))
	ids := []int64{}
	for _, deletion := range bundle.Cycles.AllocationDeletions {
		ids = append(ids, deletion.Id)
	}
	db.Exec(
		tx,
		`delete from accounting.wallet_allocations_v2 where id = any(cast(:ids as int8[]))`,
		db.Params{"ids": ids},
	)
	ids = []int64{}
	for _, deletion := range bundle.Cycles.GroupDeletions {
		ids = append(ids, deletion.Id)
	}
	db.Exec(
		tx,
		`delete from accounting.allocation_groups where id = any(cast(:ids as int8[]))`,
		db.Params{"ids": ids},
	)
}

func accountingUpgradeCapture(tx *db.Transaction, now time.Time, legacy bool) AccountingAuditCapture {
	snapshot := analyzeSnapshotFromTx(tx, now)
	for _, bucket := range accGlobals.BucketsByCategory {
		bucket.legacyProjection = legacy
		if legacy {
			for _, allocation := range bucket.AllocationsById {
				allocation.Active = allocation.Start.Before(now)
			}
		}
	}
	result := AccountingAuditCapture{
		SchemaVersion: accountingAuditSchemaVersion, CapturedAt: now, Database: "upgrade",
		Totals: snapshot.Totals, Findings: accountingAuditFindings(snapshot),
		Wallets: captureAccountingAuditWallets(tx, now), Groups: captureAccountingAuditGroups(tx),
		Allocations: captureAccountingAuditAllocations(tx, now), Scopes: captureAccountingAuditScopes(tx),
	}
	allocationCounts := map[AccWalletId]int{}
	for _, allocation := range result.Allocations {
		allocationCounts[allocation.AssociatedWallet]++
	}
	duplicateGroups := map[AccWalletId]bool{}
	groups := map[[2]AccWalletId]bool{}
	for _, group := range result.Groups {
		key := [2]AccWalletId{group.AssociatedWallet, group.ParentWallet}
		if groups[key] {
			duplicateGroups[group.AssociatedWallet] = true
		}
		groups[key] = true
	}
	for i := range result.Wallets {
		wallet := &result.Wallets[i]
		wallet.AllocationCount = allocationCounts[wallet.Id]
		if legacy && duplicateGroups[wallet.Id] {
			wallet.DerivedError = "legacy duplicate groups have no unique visible projection"
		}
	}
	return result
}

func accountingUpgradeFlow(tx *db.Transaction, bundle *accountingUpgradeBundle) {
	productsLoadFromTx(tx)
	accountingLoadFromTx(tx, bundle.CapturedAt, func(message string) { bundle.Blockers = append(bundle.Blockers, message) })
	accountingUpgradeCheck(tx, bundle)
	categories := make([]accapi.ProductCategoryIdV2, 0, len(accGlobals.BucketsByCategory))
	for category := range accGlobals.BucketsByCategory {
		categories = append(categories, category)
	}
	slices.SortFunc(categories, func(a, b accapi.ProductCategoryIdV2) int {
		return strings.Compare(a.Provider+"/"+a.Name, b.Provider+"/"+b.Name)
	})
	for _, category := range categories {
		bucket := accGlobals.BucketsByCategory[category]
		bucket.suppressNotifications = true
		for _, wallet := range bucket.WalletsById {
			for _, group := range wallet.AllocationsByParent {
				group.TreeUsage = 0
				if !bucket.IsCapacityBased() {
					group.TreeUsage = lInternalGroupTotalRetired(bucket, group)
				}
				if parent := bucket.WalletsById[group.ParentWallet]; parent != nil {
					parent.ChildrenUsage[wallet.Id] = group.TreeUsage
				}
			}
		}
		if bucket.IsCapacityBased() {
			bundle.Warnings = append(
				bundle.Warnings,
				fmt.Sprintf("%s/%s capacity uses retained reported usage; no fresh provider measurement", category.Provider, category.Name),
			)
			ids := []AccWalletId{}
			for id := range bucket.WalletsById {
				ids = append(ids, id)
			}
			slices.Sort(ids)
			for _, id := range ids {
				wallet := bucket.WalletsById[id]
				scoped := int64(0)
				for _, scope := range wallet.ScopedUsage {
					var overflow bool
					scoped, overflow = checkedAccountingAdd(scoped, scope.Usage)
					if overflow {
						bundle.Blockers = append(bundle.Blockers, fmt.Sprintf("wallet %d scope total overflows", id))
						break
					}
				}
				local := wallet.LocalUsage
				wallet.LocalUsage = max(local, scoped)
				bundle.Capacity = append(bundle.Capacity, accountingUpgradeCapacity{
					WalletId: id, LocalBefore: local, LocalAfter: wallet.LocalUsage,
					Scoped: scoped, Unscoped: max(0, local-scoped),
				})
			}
			accountingUpgradeCheck(tx, bundle)
			lInternalTransitionAllocations(bucket, bundle.CapturedAt, false)
			lInternalRerouteCapacityUsage(bucket, bundle.CapturedAt)
		} else {
			pending := map[AccWalletId]int{}
			queue := []AccWalletId{}
			for id, wallet := range bucket.WalletsById {
				pending[id] = len(wallet.ChildrenUsage)
				if pending[id] == 0 {
					queue = append(queue, id)
				}
			}
			processed := 0
			for len(queue) > 0 {
				slices.Sort(queue)
				id := queue[0]
				queue = queue[1:]
				wallet := bucket.WalletsById[id]
				processed++
				excess := lInternalWalletTotalUsageInNode(bucket, wallet) - lInternalWalletTotalPropagatedUsage(bucket, wallet)
				if excess < 0 {
					bundle.Blockers = append(bundle.Blockers, fmt.Sprintf("wallet %d retired charges exceed retained consumption", id))
					continue
				}
				if excess > 0 {
					lInternalReportUsage(bucket, bundle.CapturedAt, wallet, excess)
				}
				for _, parentId := range accountingWalletParentIds(wallet) {
					if parentId == 0 {
						continue
					}
					pending[parentId]--
					if pending[parentId] == 0 {
						queue = append(queue, parentId)
					}
				}
			}
			if processed != len(bucket.WalletsById) {
				bundle.Blockers = append(bundle.Blockers, "unresolved cycle in "+category.Provider+"/"+category.Name)
			}
			all := map[AccWalletId]bool{}
			for id := range bucket.WalletsById {
				all[id] = true
			}
			lInternalReevaluateAffected(bucket, bundle.CapturedAt, all)
		}
		accountingUpgradeCheck(tx, bundle)
		if err := lValidateAccountingTree(bucket, bundle.CapturedAt); err != nil {
			bundle.Blockers = append(bundle.Blockers, err.Error())
		}
		accountingUpgradeCheck(tx, bundle)
		for _, wallet := range bucket.WalletsById {
			lInternalMarkSignificantUpdate(bucket, bundle.CapturedAt, wallet)
		}
		persistAccountingReflow(tx, bucket, nil, accountingReflowNoScopes, bundle.CapturedAt)
	}
}

func AccountingUpgradeExport() {
	proof := db.NewTx(func(tx *db.Transaction) []byte {
		row, _ := db.Get[struct{ Exists bool }](
			tx,
			`select to_regclass('accounting.upgrade_runs') is not null as exists`,
			db.Params{},
		)
		if !row.Exists {
			_, completed := db.Get[struct{ Id string }](
				tx,
				`select id from completed_migrations where id = :version`,
				db.Params{"version": accountingUpgradeVersion},
			)
			if completed {
				panic("accountingV6 completed without an automatic-upgrade proof; restore the pre-upgrade dataset")
			}
			return nil
		}
		stored, ok := db.Get[struct{ Proof []byte }](
			tx,
			`select proof from accounting.upgrade_runs where version = :version`,
			db.Params{"version": accountingUpgradeVersion},
		)
		if !ok {
			panic("accounting upgrade completion proof is missing")
		}
		return stored.Proof
	})
	if len(proof) == 0 {
		return
	}
	reader, err := gzip.NewReader(bytes.NewReader(proof))
	if err != nil {
		panic(err)
	}
	data, err := io.ReadAll(reader)
	if err != nil {
		panic(err)
	}
	if err = reader.Close(); err != nil {
		panic(err)
	}
	var bundle accountingUpgradeBundle
	if err = json.Unmarshal(data, &bundle); err != nil {
		panic(err)
	}
	bundle.Status = "committed"
	accountingUpgradeExport(bundle)
}
