package accounting

import (
	"bytes"
	"compress/gzip"
	"database/sql"
	"encoding/json"
	"fmt"
	"io"
	"math"
	"os"
	"path/filepath"
	"slices"
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
	JobUsage      []AccountingAuditJobUsage   `json:"jobUsage"`
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
	UnitName                   string      `json:"unitName"`
	UnitNamePlural             string      `json:"unitNamePlural"`
	UnitFloatingPoint          bool        `json:"unitFloatingPoint"`
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

type AccountingAuditJobUsage struct {
	WalletId         AccWalletId `json:"walletId"`
	Key              string      `json:"key"`
	JobId            int64       `json:"jobId"`
	State            string      `json:"state,omitempty"`
	ScopedUsage      int64       `json:"scopedUsage"`
	RuntimeMillis    int64       `json:"runtimeMillis"`
	RuntimeMinutes   int64       `json:"runtimeMinutes"`
	RuntimeSource    string      `json:"runtimeSource"`
	ExpectedUsageMin int64       `json:"expectedUsageMin"`
	ExpectedUsageMax int64       `json:"expectedUsageMax"`
	AcceptedUsageMin int64       `json:"acceptedUsageMin"`
	AcceptedUsageMax int64       `json:"acceptedUsageMax"`
	UsagePerMinute   float64     `json:"usagePerMinute"`
	Error            string      `json:"error,omitempty"`
}

type accountingAuditJobUsageRow struct {
	WalletId              AccWalletId
	Key                   string
	JobId                 int64
	ScopedUsage           int64
	State                 sql.NullString
	Replicas              sql.NullInt64
	AccountingFrequency   sql.NullString
	UnitName              sql.NullString
	UnitFloatingPoint     sql.NullBool
	Price                 sql.NullInt64
	Cpu                   sql.NullInt64
	Gpu                   sql.NullInt64
	MemoryInGigs          sql.NullInt64
	FractionNumerator     sql.NullInt64
	FractionDenominator   sql.NullInt64
	RuntimeMillis         sql.NullInt64
	FallbackRuntimeMillis sql.NullInt64
	RunningUpdates        int64
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
	UnitName                sql.NullString
	UnitNamePlural          sql.NullString
	UnitFloatingPoint       sql.NullBool
	LocalUsage              int64
	LocalRetiredUsage       int64
	ExcessUsage             int64
	TotalAllocated          int64
	TotalRetiredAllocated   int64
	WasLocked               bool
	LastSignificantUpdateAt time.Time
}

func CaptureAccountingAudit(databaseName string) AccountingAuditCapture {
	return db.NewTx(func(tx *db.Transaction) AccountingAuditCapture {
		db.Exec(
			tx,
			"set transaction isolation level repeatable read, read only",
			db.Params{},
		)
		nowRow, ok := db.Get[struct{ Now time.Time }](
			tx,
			"select transaction_timestamp() as now",
			db.Params{},
		)
		if !ok {
			panic("could not retrieve accounting audit transaction time")
		}

		snapshot := analyzeSnapshotFromTx(tx, nowRow.Now)
		result := AccountingAuditCapture{
			SchemaVersion: accountingAuditSchemaVersion,
			CapturedAt:    nowRow.Now,
			Database:      databaseName,
			Totals:        snapshot.Totals,
			Findings:      accountingAuditFindings(snapshot),
			Wallets:       captureAccountingAuditWallets(tx, nowRow.Now),
			Groups:        captureAccountingAuditGroups(tx),
			Allocations:   captureAccountingAuditAllocations(tx, nowRow.Now),
			Scopes:        captureAccountingAuditScopes(tx),
			JobUsage:      captureAccountingAuditJobUsage(tx, nowRow.Now),
		}
		allocationCountByWallet := map[AccWalletId]int{}
		for _, allocation := range result.Allocations {
			allocationCountByWallet[allocation.AssociatedWallet]++
		}
		for index := range result.Wallets {
			result.Wallets[index].AllocationCount = allocationCountByWallet[result.Wallets[index].Id]
		}
		return result
	})
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
				pc.provider, pc.category, pc.accounting_frequency,
				u.name as unit_name, u.name_plural as unit_name_plural,
				u.floating_point as unit_floating_point,
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
			UnitName:                  row.UnitName.String,
			UnitNamePlural:            row.UnitNamePlural.String,
			UnitFloatingPoint:         row.UnitFloatingPoint.Bool,
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

func captureAccountingAuditJobUsage(tx *db.Transaction, capturedAt time.Time) []AccountingAuditJobUsage {
	rows := db.Select[accountingAuditJobUsageRow](
		tx,
		`
			select s.wallet_id, s.resource_suffix as key, coalesce(j.resource, 0) as job_id,
				s.usage as scoped_usage, j.current_state as state, j.replicas,
				pc.accounting_frequency, unit.name as unit_name,
				unit.floating_point as unit_floating_point,
				p.price, p.cpu, p.gpu, p.memory_in_gigs,
				p.fraction_numerator, p.fraction_denominator,
				runtime.runtime_millis,
				case
					when j.started_at is null then null
					else greatest(
						0,
						floor(extract(epoch from ((
							case
								when j.current_state = 'RUNNING' and s.last_updated_at > j.started_at then s.last_updated_at
								when j.current_state = 'RUNNING' then cast(:captured_at as timestamptz)
								else j.last_update
							end
						) - j.started_at)) * 1000)
					)::bigint
				end as fallback_runtime_millis,
				coalesce(runtime.running_updates, 0) as running_updates
			from accounting.scoped_usage s
			left join app_orchestrator.jobs j on s.resource_suffix = 'job-' || j.resource::text
			left join provider.resource r on r.id = j.resource
			left join accounting.products p on p.id = r.product
			left join accounting.product_categories pc on pc.id = p.category
			left join accounting.accounting_units unit on unit.id = pc.accounting_unit
			left join lateral (
				select floor(sum(extract(epoch from
					least(coalesce(transitions.next_at, s.last_updated_at), s.last_updated_at) - transitions.created_at
				) * 1000))::bigint as runtime_millis, count(*) as running_updates
				from (
					select u.created_at, u.extra ->> 'state' as state,
						lead(u.created_at) over (order by u.created_at, u.id) as next_at
					from provider.resource_update u
					where u.resource = j.resource
						and u.extra ->> 'state' is not null
						and u.created_at <= s.last_updated_at
				) transitions
				where transitions.state = 'RUNNING'
			) runtime on true
			where s.resource_suffix ~ '^job-[0-9]+$'
			order by s.wallet_id, s.resource_suffix
		`,
		db.Params{"captured_at": capturedAt},
	)

	result := make([]AccountingAuditJobUsage, 0, len(rows))
	for _, row := range rows {
		item := AccountingAuditJobUsage{
			WalletId: row.WalletId, Key: row.Key, JobId: row.JobId,
			State: row.State.String, ScopedUsage: row.ScopedUsage,
			RuntimeMillis: row.RuntimeMillis.Int64,
		}
		if !row.State.Valid {
			item.Error = "job does not exist"
		} else if row.RunningUpdates == 0 && !row.FallbackRuntimeMillis.Valid {
			item.Error = "job has neither a RUNNING state update nor usable start/completion timestamps"
		} else if !row.AccountingFrequency.Valid || !row.UnitName.Valid || !row.UnitFloatingPoint.Valid || !row.Price.Valid || !row.Replicas.Valid {
			item.Error = "job product accounting metadata is incomplete"
		} else {
			if row.RunningUpdates > 0 {
				item.RuntimeSource = "state updates"
			} else {
				item.RuntimeMillis = row.FallbackRuntimeMillis.Int64
				item.RuntimeSource = "job timestamps"
			}
			item.RuntimeMinutes = item.RuntimeMillis / int64(time.Minute/time.Millisecond)
			baseMinutes := int64(1)
			switch row.AccountingFrequency.String {
			case string(accapi.AccountingFrequencyPeriodicMinute):
			case string(accapi.AccountingFrequencyPeriodicHour):
				baseMinutes = 60
			case string(accapi.AccountingFrequencyPeriodicDay):
				baseMinutes = 24 * 60
			default:
				item.Error = "job product does not use a periodic accounting frequency"
			}
			if item.Error == "" {
				productUnits := float64(row.Replicas.Int64)
				if !row.UnitFloatingPoint.Bool {
					switch strings.ToLower(row.UnitName.String) {
					case "core", "cores":
						productUnits *= float64(row.Cpu.Int64)
					case "gpu", "gpus":
						productUnits *= float64(row.Gpu.Int64)
					case "gb":
						productUnits *= float64(row.MemoryInGigs.Int64)
					default:
						item.Error = "job product uses an unsupported resource accounting unit"
					}
				}
				if row.FractionNumerator.Int64 > 0 && row.FractionDenominator.Int64 > 0 {
					productUnits *= float64(row.FractionNumerator.Int64) / float64(row.FractionDenominator.Int64)
				}
				price := float64(1)
				if row.UnitFloatingPoint.Bool {
					price = float64(row.Price.Int64) / 1_000_000
				}
				scale := productUnits * price
				if row.UnitFloatingPoint.Bool {
					scale *= 1_000_000
				}
				if item.Error == "" && scale > 0 && !math.IsInf(scale, 0) && !math.IsNaN(scale) {
					item.ExpectedUsageMin, item.ExpectedUsageMax = accountingAuditExpectedJobUsageRange(item.RuntimeMinutes, baseMinutes, scale)
					item.AcceptedUsageMin, item.AcceptedUsageMax = accountingAuditExpectedJobUsageInterval(max(int64(0), item.RuntimeMinutes-5), item.RuntimeMinutes+6, baseMinutes, scale)
					item.UsagePerMinute = scale / float64(baseMinutes)
				} else if item.Error == "" {
					item.Error = "job product accounting scale is invalid"
				}
			}
		}
		result = append(result, item)
	}
	return result
}

func accountingAuditExpectedJobUsageRange(runtimeMinutes, baseMinutes int64, scale float64) (int64, int64) {
	return accountingAuditExpectedJobUsageInterval(runtimeMinutes, runtimeMinutes+1, baseMinutes, scale)
}

func accountingAuditExpectedJobUsageInterval(startMinutes, endMinutes int64, baseMinutes int64, scale float64) (int64, int64) {
	minimum := scale * float64(startMinutes) / float64(baseMinutes)
	maximumExclusive := scale * float64(endMinutes) / float64(baseMinutes)
	return int64(minimum), int64(math.Ceil(maximumExclusive) - 1)
}

func EncodeAccountingAuditCapture(capture AccountingAuditCapture) ([]byte, error) {
	var result bytes.Buffer
	compressed := gzip.NewWriter(&result)
	encoder := json.NewEncoder(compressed)
	encoder.SetEscapeHTML(false)
	if err := encoder.Encode(capture); err != nil {
		_ = compressed.Close()
		return nil, err
	}
	if err := compressed.Close(); err != nil {
		return nil, err
	}
	return result.Bytes(), nil
}

func DecodeAccountingAuditCapture(input io.Reader) (AccountingAuditCapture, error) {
	compressed, err := gzip.NewReader(input)
	if err != nil {
		return AccountingAuditCapture{}, fmt.Errorf("could not open compressed audit capture: %w", err)
	}
	defer compressed.Close()
	var result AccountingAuditCapture
	decoder := json.NewDecoder(compressed)
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&result); err != nil {
		return AccountingAuditCapture{}, fmt.Errorf("could not decode audit capture: %w", err)
	}
	var trailing any
	if err := decoder.Decode(&trailing); err != io.EOF {
		if err == nil {
			return AccountingAuditCapture{}, fmt.Errorf("audit capture contains more than one JSON value")
		}
		return AccountingAuditCapture{}, fmt.Errorf("could not finish decoding audit capture: %w", err)
	}
	if result.SchemaVersion != accountingAuditSchemaVersion {
		return AccountingAuditCapture{}, fmt.Errorf("unsupported accounting audit schema version %d", result.SchemaVersion)
	}
	return result, nil
}

func ReadAccountingAuditCapture(path string) (AccountingAuditCapture, error) {
	file, err := os.Open(path)
	if err != nil {
		return AccountingAuditCapture{}, err
	}
	defer file.Close()
	return DecodeAccountingAuditCapture(file)
}

func WriteAccountingAuditFile(path string, content []byte) error {
	directory := filepath.Dir(path)
	temporary, err := os.CreateTemp(directory, ".accounting-audit-*")
	if err != nil {
		return err
	}
	temporaryPath := temporary.Name()
	defer os.Remove(temporaryPath)
	if err := temporary.Chmod(0600); err != nil {
		_ = temporary.Close()
		return err
	}
	if _, err := temporary.Write(content); err != nil {
		_ = temporary.Close()
		return err
	}
	if err := temporary.Close(); err != nil {
		return err
	}
	return os.Rename(temporaryPath, path)
}

func validateAccountingAuditCapture(capture AccountingAuditCapture) error {
	if capture.SchemaVersion != accountingAuditSchemaVersion {
		return fmt.Errorf("unsupported accounting audit schema version %d", capture.SchemaVersion)
	}
	if capture.CapturedAt.IsZero() {
		return fmt.Errorf("capture has no timestamp")
	}
	if capture.Totals.Wallets != len(capture.Wallets) || capture.Totals.Groups != len(capture.Groups) || capture.Totals.Allocations != len(capture.Allocations) || capture.Totals.Scopes != len(capture.Scopes) {
		return fmt.Errorf("capture entity counts do not match database totals")
	}
	walletIds := make([]AccWalletId, 0, len(capture.Wallets))
	for _, wallet := range capture.Wallets {
		walletIds = append(walletIds, wallet.Id)
	}
	slices.Sort(walletIds)
	for index := 1; index < len(walletIds); index++ {
		if walletIds[index] == walletIds[index-1] {
			return fmt.Errorf("capture contains duplicate wallet %d", walletIds[index])
		}
	}
	groupIds := make([]accGroupId, 0, len(capture.Groups))
	for _, group := range capture.Groups {
		groupIds = append(groupIds, group.Id)
	}
	slices.Sort(groupIds)
	for index := 1; index < len(groupIds); index++ {
		if groupIds[index] == groupIds[index-1] {
			return fmt.Errorf("capture contains duplicate group %d", groupIds[index])
		}
	}
	allocationIds := make([]accAllocId, 0, len(capture.Allocations))
	allocationCountByWallet := map[AccWalletId]int{}
	for _, allocation := range capture.Allocations {
		allocationIds = append(allocationIds, allocation.Id)
		allocationCountByWallet[allocation.AssociatedWallet]++
	}
	slices.Sort(allocationIds)
	for index := 1; index < len(allocationIds); index++ {
		if allocationIds[index] == allocationIds[index-1] {
			return fmt.Errorf("capture contains duplicate allocation %d", allocationIds[index])
		}
	}
	for _, wallet := range capture.Wallets {
		if wallet.AllocationCount != allocationCountByWallet[wallet.Id] {
			return fmt.Errorf("wallet %d allocation count does not match captured allocations", wallet.Id)
		}
	}
	scopeKeys := make([]string, 0, len(capture.Scopes))
	for _, scope := range capture.Scopes {
		scopeKeys = append(scopeKeys, fmt.Sprintf("%d\x00%s", scope.WalletId, scope.Key))
	}
	slices.Sort(scopeKeys)
	for index := 1; index < len(scopeKeys); index++ {
		if scopeKeys[index] == scopeKeys[index-1] {
			return fmt.Errorf("capture contains duplicate scope identity")
		}
	}
	return nil
}
