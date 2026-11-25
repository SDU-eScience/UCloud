package accounting

import (
	"encoding/json"
	"math"
	"slices"
	"sort"
	"sync"
	"sync/atomic"
	"time"

	"golang.org/x/exp/maps"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

// Most reports will have a size of around 128 bytes. Some are likely to go up to around 16K in size.
// For a single day, this means that we will use around 4MB storing these with hourly snapshots when we don't account
// for space saved from collapsing reports. Once collapsed, we are likely to only store around 500K a day.

const (
	usageReportFormatV1 = 1
	usageReportFormatV2 = 2
)

type internalUsageReport struct {
	Format        int
	Wallet        AccWalletId
	ValidFrom     time.Time
	ValidUntil    util.Option[time.Time]
	Kpis          internalUsageReportKpis
	UsageOverTime internalUsageOverTime

	Dirty bool
}

type internalUsageReportKpis struct {
	QuotaAtStart           int64
	ActiveQuotaAtStart     int64
	QuotaAtEnd             int64
	ActiveQuotaAtEnd       int64
	ActiveUsageAtStart     int64
	ActiveUsageAtEnd       int64
	RetiredUsageAtStart    int64
	RetiredUsageAtEnd      int64
	ActiveAllocatedAtStart int64
	ActiveAllocatedAtEnd   int64

	MaxUsableAtStart  int64
	MaxUsableAtEnd    int64
	LocalUsageAtStart int64
	LocalUsageAtEnd   int64
	TotalUsageAtStart int64
	TotalUsageAtEnd   int64

	TotalAllocatedAtStart int64
	TotalAllocatedAtEnd   int64

	NextMeaningfulExpiration util.Option[time.Time]
}

type internalUsageOverTimeDeltaDataPoint struct {
	Timestamp time.Time
	Child     util.Option[AccWalletId]
	Change    int64
}

type UsageReportChild struct {
	Key    string
	Wallet AccWalletId
}

func walletToUsageReportChild(child AccWalletId) (UsageReportChild, bool) {
	// Negative wallet IDs are synthetic IDs used for aggregated values,
	// such as the "Other" bucket.
	if child < 0 {
		return UsageReportChild{
			Key:    "Other",
			Wallet: child,
		}, true
	}

	// Resolve the wallet.
	bucket, wallet, ok := internalWalletById(child)
	if !ok {
		return UsageReportChild{}, false
	}

	// Get the owner of the wallet.
	bucket.Mu.RLock()
	ownerId := wallet.OwnedBy
	bucket.Mu.RUnlock()

	// Resolve the owner reference.
	accGlobals.Mu.RLock()
	owner, ok := accGlobals.OwnersById[ownerId]
	accGlobals.Mu.RUnlock()

	if !ok {
		return UsageReportChild{}, false
	}

	if owner.Reference == "" {
		return UsageReportChild{}, false
	}

	return UsageReportChild{
		Key:    owner.Reference,
		Wallet: child,
	}, true
}

type internalUsageOverTimeAbsoluteChildrenDataPoint struct {
	Timestamp time.Time
	Usage     int64
	Child     util.Option[AccWalletId]
}

type internalUsageOverTimeAbsoluteDataPoint struct {
	Timestamp time.Time
	Usage     int64
	Quota     int64
}

func (d *internalUsageOverTimeAbsoluteDataPoint) UnmarshalJSON(data []byte) error {
	var aux struct {
		Timestamp             time.Time
		Usage                 int64
		Quota                 *int64   `json:"Quota"`
		UtilizationPercent100 *float64 `json:"UtilizationPercent100"`
	}

	if err := json.Unmarshal(data, &aux); err != nil {
		return err
	}

	d.Timestamp = aux.Timestamp
	d.Usage = aux.Usage

	switch {
	case aux.Quota != nil:
		d.Quota = *aux.Quota

	case aux.UtilizationPercent100 != nil:
		if *aux.UtilizationPercent100 <= 0 || aux.Usage <= 0 {
			d.Quota = 0
			break
		}
		d.Quota = int64(math.Round(float64(aux.Usage) * 100.0 / *aux.UtilizationPercent100))

	default:
		d.Quota = 0
	}

	return nil
}

type internalUsageWalletSample struct {
	Timestamp        time.Time
	TotalUsage       int64
	LocalUsage       int64
	ActiveUsage      int64
	RetiredUsage     int64
	Quota            int64
	ActiveQuota      int64
	MaxUsable        int64
	ActiveAllocated  int64
	UsageChange      int64 `json:",omitempty"`
	LocalUsageChange int64 `json:",omitempty"`
	FlowShift        int64 `json:",omitempty"`
}

type internalUsageChildDelta struct {
	Timestamp   time.Time
	Child       util.Option[AccWalletId]
	UsageChange int64
	FlowShift   int64
}

type internalUsageChildDaySnapshot struct {
	TreeUsage     int64
	ActiveUsage   int64
	ActiveQuota   int64
	Quota         int64 `json:",omitempty"`
	QuotaRecorded bool  `json:",omitempty"`
	RetiredUsage  int64
}

type internalUsageOverTime struct {
	Delta            []internalUsageOverTimeDeltaDataPoint            `json:",omitempty"`
	Absolute         []internalUsageOverTimeAbsoluteDataPoint         `json:",omitempty"`
	ChildrenAbsolute []internalUsageOverTimeAbsoluteChildrenDataPoint `json:",omitempty"`
	WalletSeries     []internalUsageWalletSample                      `json:",omitempty"`
	ChildEvents      []internalUsageChildDelta                        `json:",omitempty"`
	ChildDayEnd      map[AccWalletId]internalUsageChildDaySnapshot    `json:",omitempty"`
}

type internalWalletSnapshot struct {
	Id        AccWalletId
	Timestamp time.Time
	Category  accapi.ProductCategory

	Quota           int64
	ActiveQuota     int64
	ActiveUsage     int64
	RetiredUsage    int64
	MaxUsable       int64
	LocalUsage      int64
	TotalUsage      int64
	TotalAllocated  int64
	ActiveAllocated int64

	UsageByParent             map[AccWalletId]int64
	ActiveUsageByParent       map[AccWalletId]int64
	RetiredUsageByParent      map[AccWalletId]int64
	QuotaByParentActive       map[AccWalletId]int64
	QuotaByParentContributing map[AccWalletId]int64

	NextMeaningfulExpiration util.Option[time.Time]
}

type internalSnapshotComparison struct {
	Previous internalWalletSnapshot
	Current  internalWalletSnapshot
}

var reportGlobals struct {
	Ready                       atomic.Bool
	Mu                          sync.RWMutex
	Reports                     map[AccWalletId]*internalUsageReport
	Snapshots                   map[AccWalletId]internalWalletSnapshot
	HistoricCache               []reportCacheEntry
	HistoricCacheSlotsAvailable int
	HistoricCacheLastEmptySlot  int
	HistoricCacheIndex          map[time.Time]map[AccWalletId]int
	HistoricCheckedDays         map[AccWalletId]map[time.Time]util.Empty
}

type reportCacheEntry struct {
	InUse      bool
	LastUsedAt atomic.Pointer[time.Time]
	Report     internalUsageReport
}

func initUsageReports() {
	g := &reportGlobals
	g.Reports = map[AccWalletId]*internalUsageReport{}
	g.Snapshots = map[AccWalletId]internalWalletSnapshot{}
	g.HistoricCache = make([]reportCacheEntry, 1024*128)
	g.HistoricCacheIndex = map[time.Time]map[AccWalletId]int{}
	g.HistoricCheckedDays = map[AccWalletId]map[time.Time]util.Empty{}
	g.HistoricCacheSlotsAvailable = len(reportGlobals.HistoricCache)

	if !accGlobals.TestingEnabled {
		snapshots := db.NewTx(func(tx *db.Transaction) []internalWalletSnapshot {
			rows := db.Select[struct {
				Id       int64
				Snapshot string
			}](
				tx,
				`
					select id, snapshot
					from accounting.wallet_snapshots
			    `,
				db.Params{},
			)

			var result []internalWalletSnapshot
			for _, row := range rows {
				var item internalWalletSnapshot
				err := json.Unmarshal([]byte(row.Snapshot), &item)
				if err == nil {
					result = append(result, item)
				}
			}
			return result
		})

		for _, snapshot := range snapshots {
			g.Snapshots[snapshot.Id] = snapshot
		}

		usageInitV2Handlers()
	}

	reportGlobals.Ready.Store(true)
}

func usageRetrieveHistoricReports(from time.Time, until time.Time, wallet AccWalletId) []internalUsageReport {
	now := time.Now()
	nowTrunc := util.StartOfDayUTC(now)
	earliestFromTime := now.Add(-100 * 365 * 24 * time.Hour)
	if from.Before(earliestFromTime) {
		from = earliestFromTime
	}

	from = util.StartOfDayUTC(from)
	until = util.StartOfDayUTC(until)

	if until.Before(from) {
		return nil
	}

	historicEnd := until
	if historicEnd.After(nowTrunc) {
		historicEnd = nowTrunc
	}

	g := &reportGlobals
	if from.Before(historicEnd) && !accGlobals.TestingEnabled {
		var firstMissing time.Time
		var lastMissing time.Time
		hasMissing := false

		g.Mu.RLock()
		checked := g.HistoricCheckedDays[wallet]
		for cur := from; cur.Before(historicEnd); cur = cur.AddDate(0, 0, 1) {
			dictOnDay, hasDay := g.HistoricCacheIndex[cur]
			inCache := false
			if hasDay {
				if slot, hasSlot := dictOnDay[wallet]; hasSlot && g.HistoricCache[slot].InUse {
					inCache = true
				}
			}
			isChecked := false
			if checked != nil {
				_, isChecked = checked[cur]
			}
			if !inCache && !isChecked {
				if !hasMissing {
					firstMissing = cur
					hasMissing = true
				}
				lastMissing = cur
			}
		}
		g.Mu.RUnlock()

		if hasMissing {
			fetchFrom := firstMissing.AddDate(0, 0, -30)
			fetchUntil := lastMissing.AddDate(0, 0, 31)
			if fetchUntil.After(nowTrunc) {
				fetchUntil = nowTrunc
			}

			reports := db.NewTx(func(tx *db.Transaction) []internalUsageReport {
				rows := db.Select[struct {
					ReportFormat int
					ReportData   string
				}](
					tx,
					`
						select
							report_format,
							report_data
						from
							accounting.usage_report
						where
							wallet_id = :wallet
							and valid_from >= :fetch_from
							and valid_from < :fetch_until
						order by valid_from
					`,
					db.Params{
						"wallet":      int64(wallet),
						"fetch_from":  fetchFrom,
						"fetch_until": fetchUntil,
					},
				)

				result := make([]internalUsageReport, 0, len(rows))
				for _, row := range rows {
					var report internalUsageReport
					_ = json.Unmarshal([]byte(row.ReportData), &report)
					if report.Format == 0 {
						if row.ReportFormat >= usageReportFormatV2 {
							report.Format = row.ReportFormat
						} else {
							report.Format = usageReportFormatV1
						}
					}
					result = append(result, report)
				}
				return result
			})

			g.Mu.Lock()
			for i := range reports {
				lUsageCacheReport(&reports[i])
			}
			checkedMap := g.HistoricCheckedDays[wallet]
			if checkedMap == nil {
				checkedMap = map[time.Time]util.Empty{}
				g.HistoricCheckedDays[wallet] = checkedMap
			}
			for cur := fetchFrom; cur.Before(fetchUntil); cur = cur.AddDate(0, 0, 1) {
				checkedMap[cur] = util.Empty{}
			}
			g.Mu.Unlock()
		}
	}

	var result []internalUsageReport
	nowPtr := util.Pointer(time.Now())

	g.Mu.RLock()
	for cur := from; cur.Before(historicEnd); cur = cur.AddDate(0, 0, 1) {
		dictOnDay, hasDay := g.HistoricCacheIndex[cur]
		if !hasDay {
			continue
		}
		slot, hasSlot := dictOnDay[wallet]
		if !hasSlot {
			continue
		}
		entry := &g.HistoricCache[slot]
		if !entry.InUse {
			continue
		}
		entry.LastUsedAt.Store(nowPtr)
		result = append(result, entry.Report)
	}

	if nowTrunc == until || until.After(nowTrunc) {
		currentReport, ok := g.Reports[wallet]
		if ok {
			result = append(result, accountingCloneUsageReport(currentReport))
		}
	}
	g.Mu.RUnlock()

	return result
}

func accountingCloneUsageReport(report *internalUsageReport) internalUsageReport {
	result := *report
	result.UsageOverTime.Delta = slices.Clone(report.UsageOverTime.Delta)
	result.UsageOverTime.Absolute = slices.Clone(report.UsageOverTime.Absolute)
	result.UsageOverTime.ChildrenAbsolute = slices.Clone(report.UsageOverTime.ChildrenAbsolute)
	result.UsageOverTime.WalletSeries = slices.Clone(report.UsageOverTime.WalletSeries)
	result.UsageOverTime.ChildEvents = slices.Clone(report.UsageOverTime.ChildEvents)
	if report.UsageOverTime.ChildDayEnd != nil {
		result.UsageOverTime.ChildDayEnd = maps.Clone(report.UsageOverTime.ChildDayEnd)
	}
	return result
}

func lUsageRetireReport(report *internalUsageReport, b *db.Batch) {
	lUsageCacheReport(report)
	lUsagePersistReport(report, b)
}

func lUsagePersistReport(report *internalUsageReport, b *db.Batch) {
	if accGlobals.TestingEnabled {
		return
	}

	formatVersion := report.Format
	if formatVersion == 0 {
		formatVersion = usageReportFormatV2
		report.Format = formatVersion
	}

	reportJson, _ := json.Marshal(report)
	walletId := report.Wallet
	validFrom := report.ValidFrom

	db.BatchExec(
		b,
		`
			insert into accounting.usage_report(wallet_id, valid_from, report_format, report_data)
			select
				cast(:wallet as int),
				:valid_from,
				:report_format,
				cast(:data as jsonb)
			where
				exists (
					select 1
					from
						accounting.wallets_v2
					where
						id = cast(:wallet as int)
				)
			on conflict (wallet_id, valid_from) do update set
				report_format = excluded.report_format,
				report_data = excluded.report_data
		`,
		db.Params{
			"wallet":        int64(walletId),
			"valid_from":    validFrom,
			"report_format": formatVersion,
			"data":          string(reportJson),
		},
	)
}

func lUsageCacheReport(report *internalUsageReport) {
	g := &reportGlobals

	if dictOnDay, ok := g.HistoricCacheIndex[report.ValidFrom]; ok {
		if existingSlot, exists := dictOnDay[report.Wallet]; exists {
			entry := &g.HistoricCache[existingSlot]
			if entry.InUse {
				entry.LastUsedAt.Store(util.Pointer(time.Now()))
				entry.Report = *report
				return
			}
		}
	}

	lUsageEvictHistoricCache()

	slot := -1

	for iteration := 0; iteration < len(g.HistoricCache); iteration++ {
		i := (iteration + g.HistoricCacheLastEmptySlot) % len(g.HistoricCache)
		entry := &g.HistoricCache[i]
		if !entry.InUse {
			slot = i
			entry.InUse = true
			entry.LastUsedAt.Store(util.Pointer(time.Now()))
			entry.Report = *report
			g.HistoricCacheSlotsAvailable--
			g.HistoricCacheLastEmptySlot = i
			break
		}
	}

	if slot == -1 {
		log.Warn("failed to cache usage report: no space in cache")
		return
	}

	dictOnDay, ok := g.HistoricCacheIndex[report.ValidFrom]
	if !ok {
		dictOnDay = map[AccWalletId]int{}
		g.HistoricCacheIndex[report.ValidFrom] = dictOnDay
	}

	dictOnDay[report.Wallet] = slot
}

func lUsageEvictHistoricCacheEntry(slot int) {
	g := &reportGlobals
	entry := &g.HistoricCache[slot]
	idx, ok := g.HistoricCacheIndex[entry.Report.ValidFrom]
	if ok {
		delete(idx, entry.Report.Wallet)
	}
	if checked, hasChecked := g.HistoricCheckedDays[entry.Report.Wallet]; hasChecked {
		delete(checked, entry.Report.ValidFrom)
	}

	entry.InUse = false
	entry.Report = internalUsageReport{}
	entry.LastUsedAt.Store(util.Pointer(time.Now()))
	g.HistoricCacheSlotsAvailable++
}

func lUsageEvictHistoricCache() {
	g := &reportGlobals

	if g.HistoricCacheSlotsAvailable != 0 {
		return
	}

	oldestEntry := time.Now()
	oldestSlot := -1
	for i := range g.HistoricCache {
		entry := &g.HistoricCache[i]
		if entry.InUse {
			usedAt := *entry.LastUsedAt.Load()
			if usedAt.Before(oldestEntry) {
				oldestEntry = usedAt
				oldestSlot = i
			}
		}
	}

	evictBefore := oldestEntry.Add(60 * time.Minute)
	evicted := false
	for i := range g.HistoricCache {
		entry := &g.HistoricCache[i]
		if entry.InUse && entry.LastUsedAt.Load().Before(evictBefore) {
			lUsageEvictHistoricCacheEntry(i)
			evicted = true
		}
	}

	if !evicted && oldestSlot >= 0 {
		lUsageEvictHistoricCacheEntry(oldestSlot)
	}
}

func usageSample(now time.Time) {
	usageSampleEx(now, nil)
}

func usageSampleEx(now time.Time, bucketFilter func(cat accapi.ProductCategory) bool) {
	batch := db.BatchNewDeferred()

	reportGlobals.Mu.Lock()

	var buckets []*internalBucket
	accGlobals.Mu.Lock()
	for _, b := range accGlobals.BucketsByCategory {
		if bucketFilter == nil || bucketFilter(b.Category) {
			buckets = append(buckets, b)
		}
	}

	slices.SortFunc(buckets, func(a, b *internalBucket) int {
		if a.Category.Provider < b.Category.Provider {
			return -1
		} else if a.Category.Provider > b.Category.Provider {
			return 1
		} else if a.Category.Name < b.Category.Name {
			return -1
		} else if a.Category.Name > b.Category.Name {
			return 1
		} else {
			return 0
		}
	})

	for _, b := range buckets {
		b.Mu.Lock()
	}

	snapshotsById := map[AccWalletId]internalSnapshotComparison{}
	for _, b := range buckets {
		for _, w := range b.WalletsById {
			wallet := lSnapshotWallet(now, b, w)
			snapshotsById[w.Id] = wallet

			if !accGlobals.TestingEnabled {
				jsonSnapshot, _ := json.Marshal(wallet.Current)

				db.BatchExec(
					batch,
					`
						insert into accounting.wallet_snapshots(id, snapshot)
						select
							cast(:id as int8),
							cast(:snapshot as jsonb)
						where
							exists (
								select 1
								from
									accounting.wallets_v2
								where
									id = cast(:id as int8)
							)
						on conflict (id) do update set
							snapshot = excluded.snapshot,
							created_at = now()
				    `,
					db.Params{
						"id":       int64(wallet.Current.Id),
						"snapshot": string(jsonSnapshot),
					},
				)
			}
		}
	}

	for _, report := range reportGlobals.Reports {
		report.Dirty = false
	}

	for _, b := range buckets {
		var walletIds []int
		for _, w := range b.WalletsById {
			walletIds = append(walletIds, int(w.Id))
		}
		sort.Ints(walletIds)

		for _, wId := range walletIds {
			lUsageSampleEnsureReport(now, snapshotsById[AccWalletId(wId)], batch)
		}

		for _, wId := range walletIds {
			lUsageSampleWallet(now, snapshotsById[AccWalletId(wId)], batch)
		}
	}

	for _, report := range reportGlobals.Reports {
		if report.Dirty {
			lUsagePersistReport(report, batch)
		}
	}

	for _, b := range buckets {
		b.Mu.Unlock()
	}

	accGlobals.Mu.Unlock()

	reportGlobals.Mu.Unlock()

	if !accGlobals.TestingEnabled {
		db.NewTx0(func(tx *db.Transaction) {
			db.BatchSendDeferred(tx, batch)
		})
	}
}

func lUsageWalletActiveAllocated(b *internalBucket, w *internalWallet) int64 {
	sum := int64(0)
	for childId := range w.ChildrenUsage {
		child := b.WalletsById[childId]
		if child == nil {
			continue
		}
		childGroup := child.AllocationsByParent[w.Id]
		if childGroup == nil {
			continue
		}
		for allocId := range childGroup.Allocations {
			childAlloc := b.AllocationsById[allocId]
			if childAlloc != nil && childAlloc.Committed && childAlloc.Active && !childAlloc.Retired {
				sum += childAlloc.Quota
			}
		}
	}
	return sum
}

func lSnapshotWallet(now time.Time, b *internalBucket, w *internalWallet) internalSnapshotComparison {
	prev, ok := reportGlobals.Snapshots[w.Id]
	if !ok {
		prev = internalWalletSnapshot{
			Id:                        w.Id,
			Timestamp:                 now.AddDate(0, 0, -1),
			Quota:                     0,
			ActiveQuota:               0,
			ActiveUsage:               0,
			RetiredUsage:              0,
			MaxUsable:                 0,
			LocalUsage:                0,
			TotalUsage:                0,
			TotalAllocated:            0,
			ActiveAllocated:           0,
			UsageByParent:             map[AccWalletId]int64{},
			ActiveUsageByParent:       map[AccWalletId]int64{},
			RetiredUsageByParent:      map[AccWalletId]int64{},
			QuotaByParentActive:       map[AccWalletId]int64{},
			QuotaByParentContributing: map[AccWalletId]int64{},
			Category:                  b.Category,
			NextMeaningfulExpiration:  util.OptNone[time.Time](),
		}
	}

	totalUsage := lInternalWalletTotalUsageInNode(b, w)
	retiredTotal := int64(0)
	if !b.IsCapacityBased() {
		for _, group := range w.AllocationsByParent {
			retiredTotal += lInternalGroupTotalRetired(b, group)
		}
	}
	activeUsageTotal := max(int64(0), totalUsage-retiredTotal)
	if b.IsCapacityBased() {
		activeUsageTotal = totalUsage
	}

	current := internalWalletSnapshot{
		Id:                        w.Id,
		Timestamp:                 now,
		Quota:                     lInternalWalletTotalQuotaContributing(b, w),
		ActiveQuota:               lInternalWalletTotalQuotaFromActiveAllocations(b, w),
		ActiveUsage:               activeUsageTotal,
		RetiredUsage:              retiredTotal,
		MaxUsable:                 lInternalMaxUsable(b, now, w),
		LocalUsage:                w.LocalUsage,
		TotalUsage:                totalUsage,
		TotalAllocated:            lInternalWalletTotalAllocatedContributing(b, w),
		ActiveAllocated:           lUsageWalletActiveAllocated(b, w),
		UsageByParent:             map[AccWalletId]int64{},
		ActiveUsageByParent:       map[AccWalletId]int64{},
		RetiredUsageByParent:      map[AccWalletId]int64{},
		QuotaByParentActive:       map[AccWalletId]int64{},
		QuotaByParentContributing: map[AccWalletId]int64{},
		Category:                  b.Category,
	}

	minimumMeaningfulQuota := int64(float64(current.ActiveQuota) * 0.1)
	earliestExpiration := util.OptNone[time.Time]()

	for parent, group := range w.AllocationsByParent {
		current.UsageByParent[parent] = group.TreeUsage
		contributingQuota := lInternalGroupTotalQuotaContributing(b, group)
		activeQuota := lInternalGroupTotalQuotaFromActiveAllocations(b, group)
		retiredUsage := int64(0)
		if !b.IsCapacityBased() {
			retiredUsage = lInternalGroupTotalRetired(b, group)
		}
		activeUsage := max(int64(0), group.TreeUsage-retiredUsage)
		if b.IsCapacityBased() {
			activeUsage = group.TreeUsage
		}

		current.ActiveUsageByParent[parent] = activeUsage
		current.RetiredUsageByParent[parent] = retiredUsage
		current.QuotaByParentContributing[parent] = contributingQuota
		current.QuotaByParentActive[parent] = activeQuota

		for allocId := range group.Allocations {
			alloc := b.AllocationsById[allocId]
			if alloc.Committed && alloc.Active && !alloc.Retired && alloc.Quota > minimumMeaningfulQuota {
				if !earliestExpiration.Present || alloc.End.Before(earliestExpiration.Value) {
					earliestExpiration.Set(alloc.End)
				}
			}
		}

	}

	current.NextMeaningfulExpiration = earliestExpiration

	reportGlobals.Snapshots[w.Id] = current

	return internalSnapshotComparison{
		Previous: prev,
		Current:  current,
	}
}

func lUsageSampleEnsureReport(now time.Time, cmp internalSnapshotComparison, b *db.Batch) *internalUsageReport {
	startOfDay := util.StartOfDayUTC(now)

	prevWallet := cmp.Previous
	currWallet := cmp.Current

	report, ok := reportGlobals.Reports[currWallet.Id]
	if !ok || report.ValidFrom.Before(startOfDay) {
		if ok && report.ValidFrom.Before(startOfDay) {
			lUsageRetireReport(report, b)
		}

		report = &internalUsageReport{
			Format:     usageReportFormatV2,
			Wallet:     currWallet.Id,
			ValidFrom:  startOfDay,
			ValidUntil: util.Option[time.Time]{},

			Kpis: internalUsageReportKpis{
				QuotaAtStart:           prevWallet.Quota,
				ActiveQuotaAtStart:     prevWallet.ActiveQuota,
				ActiveUsageAtStart:     prevWallet.ActiveUsage,
				RetiredUsageAtStart:    prevWallet.RetiredUsage,
				ActiveAllocatedAtStart: prevWallet.ActiveAllocated,
				MaxUsableAtStart:       prevWallet.MaxUsable,
				LocalUsageAtStart:      prevWallet.LocalUsage,
				TotalUsageAtStart:      prevWallet.TotalUsage,
				TotalAllocatedAtStart:  prevWallet.TotalAllocated,
			},

			UsageOverTime: internalUsageOverTime{
				ChildDayEnd: map[AccWalletId]internalUsageChildDaySnapshot{},
			},
		}

		reportGlobals.Reports[currWallet.Id] = report
	}

	if report.Format < usageReportFormatV2 {
		report.Format = usageReportFormatV2
	}
	if report.UsageOverTime.ChildDayEnd == nil {
		report.UsageOverTime.ChildDayEnd = map[AccWalletId]internalUsageChildDaySnapshot{}
	}

	return report
}

func lUsageAppendOrReplaceWalletSeries(
	points *[]internalUsageWalletSample,
	point internalUsageWalletSample,
) {
	for i := range *points {
		if (*points)[i].Timestamp.Equal(point.Timestamp) {
			point.UsageChange += (*points)[i].UsageChange
			point.LocalUsageChange += (*points)[i].LocalUsageChange
			point.FlowShift += (*points)[i].FlowShift
			(*points)[i] = point
			return
		}
	}

	*points = append(*points, point)

	sort.Slice(*points, func(i, j int) bool {
		return (*points)[i].Timestamp.Before((*points)[j].Timestamp)
	})
}

func lUsageRecordWalletChange(
	report *internalUsageReport,
	now time.Time,
	usageChange int64,
	localUsageChange int64,
	flowShift int64,
) {
	if usageChange == 0 && localUsageChange == 0 && flowShift == 0 {
		return
	}
	for i := len(report.UsageOverTime.WalletSeries) - 1; i >= 0; i-- {
		if report.UsageOverTime.WalletSeries[i].Timestamp.Equal(now) {
			report.UsageOverTime.WalletSeries[i].UsageChange += usageChange
			report.UsageOverTime.WalletSeries[i].LocalUsageChange += localUsageChange
			report.UsageOverTime.WalletSeries[i].FlowShift += flowShift
			return
		}
	}
	report.UsageOverTime.WalletSeries = append(report.UsageOverTime.WalletSeries, internalUsageWalletSample{
		Timestamp:        now,
		TotalUsage:       report.Kpis.TotalUsageAtEnd,
		LocalUsage:       report.Kpis.LocalUsageAtEnd,
		ActiveUsage:      report.Kpis.ActiveUsageAtEnd,
		RetiredUsage:     report.Kpis.RetiredUsageAtEnd,
		Quota:            report.Kpis.QuotaAtEnd,
		ActiveQuota:      report.Kpis.ActiveQuotaAtEnd,
		MaxUsable:        report.Kpis.MaxUsableAtEnd,
		ActiveAllocated:  report.Kpis.ActiveAllocatedAtEnd,
		UsageChange:      usageChange,
		LocalUsageChange: localUsageChange,
		FlowShift:        flowShift,
	})
	sort.Slice(report.UsageOverTime.WalletSeries, func(i, j int) bool {
		return report.UsageOverTime.WalletSeries[i].Timestamp.Before(report.UsageOverTime.WalletSeries[j].Timestamp)
	})
}

func lUsageSplitChildEdgeDelta(
	isCapacity bool,
	prevWallet internalWalletSnapshot,
	currWallet internalWalletSnapshot,
) map[AccWalletId][2]int64 {
	result := map[AccWalletId][2]int64{}

	allParents := map[AccWalletId]util.Empty{}
	for parent := range prevWallet.UsageByParent {
		if parent != 0 {
			allParents[parent] = util.Empty{}
		}
	}
	for parent := range currWallet.UsageByParent {
		if parent != 0 {
			allParents[parent] = util.Empty{}
		}
	}

	if isCapacity {
		for parent := range allParents {
			edgeDelta := currWallet.UsageByParent[parent] - prevWallet.UsageByParent[parent]
			result[parent] = [2]int64{edgeDelta, 0}
		}
		return result
	}

	edgeDeltas := map[AccWalletId]int64{}
	shiftedOut := int64(0)
	sumPos := int64(0)
	for parent := range allParents {
		edgeDelta := currWallet.UsageByParent[parent] - prevWallet.UsageByParent[parent]
		edgeDeltas[parent] = edgeDelta
		if edgeDelta < 0 {
			shiftedOut += -edgeDelta
		} else if edgeDelta > 0 {
			sumPos += edgeDelta
		}
	}

	childTrueDelta := max(int64(0), currWallet.TotalUsage-prevWallet.TotalUsage)
	totalPosShift := int64(0)
	if shiftedOut > 0 && sumPos > childTrueDelta {
		totalPosShift = min(shiftedOut, sumPos-childTrueDelta)
	}

	for parent, edgeDelta := range edgeDeltas {
		if edgeDelta < 0 {
			result[parent] = [2]int64{0, edgeDelta}
		} else if edgeDelta > 0 && totalPosShift > 0 && sumPos > 0 {
			shift := int64(math.Round(float64(edgeDelta) * float64(totalPosShift) / float64(sumPos)))
			if shift > edgeDelta {
				shift = edgeDelta
			}
			result[parent] = [2]int64{edgeDelta - shift, shift}
		} else {
			result[parent] = [2]int64{edgeDelta, 0}
		}
	}

	return result
}

func lUsageSampleWallet(now time.Time, cmp internalSnapshotComparison, b *db.Batch) {
	prevWallet := cmp.Previous
	currWallet := cmp.Current

	report := lUsageSampleEnsureReport(now, cmp, b)

	kpis := &report.Kpis
	kpis.QuotaAtEnd = currWallet.Quota
	kpis.ActiveQuotaAtEnd = currWallet.ActiveQuota
	kpis.ActiveUsageAtEnd = currWallet.ActiveUsage
	kpis.RetiredUsageAtEnd = currWallet.RetiredUsage
	kpis.ActiveAllocatedAtEnd = currWallet.ActiveAllocated
	kpis.MaxUsableAtEnd = currWallet.MaxUsable
	kpis.LocalUsageAtEnd = currWallet.LocalUsage
	kpis.TotalUsageAtEnd = currWallet.TotalUsage
	kpis.TotalAllocatedAtEnd = currWallet.TotalAllocated
	kpis.NextMeaningfulExpiration = currWallet.NextMeaningfulExpiration

	isCapacity := currWallet.Category.AccountingModel == accapi.AccountingModelCapacity

	{
		prevUsage := prevWallet.LocalUsage
		currUsage := currWallet.LocalUsage
		delta := currUsage - prevUsage

		if delta != 0 {
			report.UsageOverTime.ChildEvents = append(report.UsageOverTime.ChildEvents, internalUsageChildDelta{
				Timestamp:   now,
				Child:       util.Option[AccWalletId]{},
				UsageChange: delta,
				FlowShift:   0,
			})

			report.Dirty = true
		}

		walletChanged := prevWallet.LocalUsage != currWallet.LocalUsage ||
			prevWallet.TotalUsage != currWallet.TotalUsage ||
			prevWallet.Quota != currWallet.Quota ||
			prevWallet.ActiveQuota != currWallet.ActiveQuota ||
			prevWallet.ActiveUsage != currWallet.ActiveUsage ||
			prevWallet.MaxUsable != currWallet.MaxUsable ||
			prevWallet.ActiveAllocated != currWallet.ActiveAllocated ||
			len(report.UsageOverTime.WalletSeries) == 0

		if walletChanged || delta != 0 {
			lUsageAppendOrReplaceWalletSeries(
				&report.UsageOverTime.WalletSeries,
				internalUsageWalletSample{
					Timestamp:        now,
					TotalUsage:       currWallet.TotalUsage,
					LocalUsage:       currWallet.LocalUsage,
					ActiveUsage:      currWallet.ActiveUsage,
					RetiredUsage:     currWallet.RetiredUsage,
					Quota:            currWallet.Quota,
					ActiveQuota:      currWallet.ActiveQuota,
					MaxUsable:        currWallet.MaxUsable,
					ActiveAllocated:  currWallet.ActiveAllocated,
					UsageChange:      delta,
					LocalUsageChange: delta,
					FlowShift:        0,
				},
			)
			report.Dirty = true
		}
	}

	splitDeltas := lUsageSplitChildEdgeDelta(isCapacity, prevWallet, currWallet)

	for parent, usage := range currWallet.UsageByParent {
		if parent == 0 {
			continue
		}
		split := splitDeltas[parent]
		usageChange := split[0]
		flowShift := split[1]

		parentReport := reportGlobals.Reports[parent]
		if parentReport == nil {
			continue
		}
		if parentReport.UsageOverTime.ChildDayEnd == nil {
			parentReport.UsageOverTime.ChildDayEnd = map[AccWalletId]internalUsageChildDaySnapshot{}
		}

		parentReport.Dirty = true

		parentReport.UsageOverTime.ChildDayEnd[currWallet.Id] = internalUsageChildDaySnapshot{
			TreeUsage:     usage,
			ActiveUsage:   currWallet.ActiveUsageByParent[parent],
			ActiveQuota:   currWallet.QuotaByParentActive[parent],
			Quota:         currWallet.QuotaByParentContributing[parent],
			QuotaRecorded: true,
			RetiredUsage:  currWallet.RetiredUsageByParent[parent],
		}

		if usageChange != 0 || flowShift != 0 {
			parentReport.UsageOverTime.ChildEvents = append(
				parentReport.UsageOverTime.ChildEvents,
				internalUsageChildDelta{
					Timestamp:   now,
					Child:       util.OptValue(currWallet.Id),
					UsageChange: usageChange,
					FlowShift:   flowShift,
				},
			)
			lUsageRecordWalletChange(parentReport, now, usageChange, 0, flowShift)
		}
	}
}
