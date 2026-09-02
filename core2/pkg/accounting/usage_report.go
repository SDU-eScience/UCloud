package accounting

import (
	"cmp"
	"encoding/json"
	"fmt"
	"slices"
	"sort"
	"sync"
	"sync/atomic"
	"time"

	"golang.org/x/exp/maps"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// Most reports will have a size of around 128 bytes. Some are likely to go up to around 16K in size.
// For a single day, this means that we will use around 4MB storing these with hourly snapshots when we don't account
// for space saved from collapsing reports. Once collapsed, we are likely to only store around 500K a day.

type internalUsageReport struct {
	Wallet           AccWalletId
	ValidFrom        time.Time
	ValidUntil       util.Option[time.Time] // Most recent report will not set this (valid until "now")
	Kpis             internalUsageReportKpis
	SubProjectHealth internalSubProjectHealth
	UsageOverTime    internalUsageOverTime

	Dirty bool
}

func (r *internalUsageReport) ToApi() accapi.UsageReport {
	return accapi.UsageReport{
		Title:            "",  // Not set by this function
		ProductsCovered:  nil, // Not set by this function
		ValidFrom:        fndapi.Timestamp(r.ValidFrom),
		ValidUntil:       fndapi.Timestamp(r.ValidUntil.GetOrDefault(time.Now())),
		Kpis:             r.Kpis.ToApi(),
		SubProjectHealth: r.SubProjectHealth.ToApi(),
		UsageOverTime:    r.UsageOverTime.ToApi(),
	}
}

type internalUsageReportKpis struct {
	QuotaAtStart       int64 // At creation: combined quota from all allocations which contribute quota
	ActiveQuotaAtStart int64 // At creation: combined quota from all allocations that are active
	QuotaAtEnd         int64 // Latest (in period): combined quota from all allocations which contribute quota
	ActiveQuotaAtEnd   int64 // Latest (in period): combined quota from all allocations that are active

	MaxUsableAtStart  int64
	MaxUsableAtEnd    int64
	LocalUsageAtStart int64
	LocalUsageAtEnd   int64
	TotalUsageAtStart int64
	TotalUsageAtEnd   int64
	// Active usage can be derived by determining retired usage from the inactive allocations

	TotalAllocatedAtStart int64
	TotalAllocatedAtEnd   int64

	NextMeaningfulExpiration util.Option[time.Time]
}

func (r *internalUsageReportKpis) ToApi() accapi.UsageReportKpis {
	return accapi.UsageReportKpis{
		QuotaAtStart:          r.QuotaAtStart,
		ActiveQuotaAtStart:    r.ActiveQuotaAtStart,
		QuotaAtEnd:            r.QuotaAtEnd,
		ActiveQuotaAtEnd:      r.ActiveQuotaAtEnd,
		MaxUsableAtStart:      r.MaxUsableAtStart,
		MaxUsableAtEnd:        r.MaxUsableAtEnd,
		LocalUsageAtStart:     r.LocalUsageAtStart,
		LocalUsageAtEnd:       r.LocalUsageAtEnd,
		TotalUsageAtStart:     r.TotalUsageAtStart,
		TotalUsageAtEnd:       r.TotalUsageAtEnd,
		TotalAllocatedAtStart: r.TotalAllocatedAtStart,
		TotalAllocatedAtEnd:   r.TotalAllocatedAtEnd,
		NextMeaningfulExpiration: util.Option[fndapi.Timestamp]{
			Present: r.NextMeaningfulExpiration.Present,
			Value:   fndapi.Timestamp(r.NextMeaningfulExpiration.Value),
		},
	}
}

type internalUsageOverTimeDeltaDataPoint struct {
	Timestamp time.Time
	Child     util.Option[AccWalletId]
	Change    int64
}

func (r *internalUsageOverTimeDeltaDataPoint) ToApi() accapi.UsageReportDeltaDataPoint {
	child := setChild(r.Child)

	return accapi.UsageReportDeltaDataPoint{
		Timestamp: fndapi.Timestamp(r.Timestamp),
		Change:    r.Change,
		Child:     child,
	}
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

func setChild(givenChild util.Option[AccWalletId]) util.Option[string] {
	if !givenChild.Present {
		return util.OptNone[string]()
	}

	child, ok := walletToUsageReportChild(givenChild.Value)
	if !ok {
		return util.OptNone[string]()
	}

	return util.OptValue(child.Key)
}

type internalUsageOverTimeAbsoluteChildrenDataPoint struct {
	Timestamp time.Time
	Usage     int64
	Child     util.Option[AccWalletId]
}

func (d *internalUsageOverTimeAbsoluteChildrenDataPoint) ToApi() accapi.UsageReportAbsoluteChildrenDataPoint {
	child := setChild(d.Child)
	return accapi.UsageReportAbsoluteChildrenDataPoint{
		Timestamp: fndapi.Timestamp(d.Timestamp),
		Usage:     d.Usage,
		Child:     child,
	}
}

type internalUsageOverTimeAbsoluteDataPoint struct {
	Timestamp time.Time
	Usage     int64
	Quota     int64
}

func (r *internalUsageOverTimeAbsoluteDataPoint) ToApi() accapi.UsageReportAbsoluteDataPoint {
	utilizationPercent100 := 0.0
	if r.Quota != 0 {
		utilizationPercent100 = float64(r.Usage) / float64(r.Quota) * 100.0
	}

	return accapi.UsageReportAbsoluteDataPoint{
		Timestamp:             fndapi.Timestamp(r.Timestamp),
		Usage:                 r.Usage,
		UtilizationPercent100: utilizationPercent100,
	}
}

type internalUsageOverTime struct {
	// Contains all changes. Ordered by timestamp then by child (null first).
	//
	// NOTE(Dan, 07/10/25): We currently have around 600 jobs a day. Given that, this should imply that the delta
	// array will remain small enough that we can actually do this.
	Delta []internalUsageOverTimeDeltaDataPoint

	// absolute usage from the wallet level
	Absolute []internalUsageOverTimeAbsoluteDataPoint

	// absolute children usage
	ChildrenAbsolute []internalUsageOverTimeAbsoluteChildrenDataPoint
}

func (r *internalUsageOverTime) ToApi() accapi.UsageReportOverTime {
	res := accapi.UsageReportOverTime{}
	res.Delta = make([]accapi.UsageReportDeltaDataPoint, len(r.Delta))
	res.Absolute = make([]accapi.UsageReportAbsoluteDataPoint, len(r.Absolute))
	res.ChildrenAbsolute = make([]accapi.UsageReportAbsoluteChildrenDataPoint, len(r.ChildrenAbsolute))

	for i := 0; i < len(res.Delta); i++ {
		res.Delta[i] = r.Delta[i].ToApi()
	}

	for i := 0; i < len(res.Absolute); i++ {
		res.Absolute[i] = r.Absolute[i].ToApi()
	}

	for i := 0; i < len(res.ChildrenAbsolute); i++ {
		res.ChildrenAbsolute[i] = r.ChildrenAbsolute[i].ToApi()
	}
	return res
}

type internalSubProjectHealth struct {
	SubProjectCount int

	// Ok, UnderUtilized, AtRisk will sum to SubProjectCount

	Ok            int
	UnderUtilized int
	AtRisk        int

	// 0 >= Idle <= SubProjectCount
	Idle int
}

func (r *internalSubProjectHealth) ToApi() accapi.UsageReportSubProjectHealth {
	return accapi.UsageReportSubProjectHealth{
		SubProjectCount: r.SubProjectCount,
		Ok:              r.Ok,
		UnderUtilized:   r.UnderUtilized,
		AtRisk:          r.AtRisk,
		Idle:            r.Idle,
	}
}

type internalGroupHealth int

const (
	internalGroupHealthOk internalGroupHealth = iota
	internalGroupHealthUnderUtilized
	internalGroupHealthAtRisk
)

type internalWalletSnapshot struct {
	Id        AccWalletId
	Timestamp time.Time
	Category  accapi.ProductCategory

	Quota          int64
	ActiveQuota    int64
	MaxUsable      int64
	LocalUsage     int64
	TotalUsage     int64
	TotalAllocated int64

	UsageByParent             map[AccWalletId]int64
	QuotaByParentActive       map[AccWalletId]int64
	QuotaByParentContributing map[AccWalletId]int64
	HealthByParent            map[AccWalletId]internalGroupHealth

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

		accapi.UsageRetrieve.Handler(func(info rpc.RequestInfo, request accapi.UsageRetrieveRequest) (accapi.UsageRetrieveResponse, *util.HttpError) {
			now := time.Now()
			reference := string(info.Actor.Project.Value)
			if !info.Actor.Project.Present || reference == "" {
				reference = info.Actor.Username
			}

			owner := internalOwnerByReference(reference)
			wallets := internalRetrieveWallets(now, reference, walletFilter{
				RequireActive: false,
			})
			type aggregatedReport struct {
				Reports          []internalUsageReportWithProduct
				Title            string
				UnitAndFrequency accapi.AccountingUnitAndFrequency
				Products         []accapi.ProductCategoryIdV2
			}

			computeReportsByUnit := map[string]*aggregatedReport{}
			storageReportsTimeBased := &aggregatedReport{}
			storageReportsCapacity := &aggregatedReport{}

			var reports []accapi.UsageReport
			for _, w := range wallets {
				productType := w.PaysFor.ProductType
				if productType != accapi.ProductTypeCompute && productType != accapi.ProductTypeStorage {
					continue
				}

				b := internalBucketOrInit(w.PaysFor)
				walletId := internalWalletByOwner(b, now, owner.Id)

				startTime := fndapi.TimeFromUnixMilli(request.Start).Time()
				endTime := fndapi.TimeFromUnixMilli(request.End).Time()

				historicReports := usageRetrieveHistoricReports(startTime, endTime, walletId)
				historicReportsWithProduct := make(
					[]internalUsageReportWithProduct,
					0,
					len(historicReports),
				)

				for _, h := range historicReports {
					historicReportsWithProduct = append(
						historicReportsWithProduct,
						internalUsageReportWithProduct{
							Product: w.PaysFor,
							Report:  h,
						},
					)
				}
				{
					// Per-category report
					// -----------------------------------------------------------------------------------------------------
					report :=
						usageCollapseReports(historicReportsWithProduct)
					apiReport := report.ToApi()
					apiReport.Title = w.PaysFor.Name
					apiReport.ProductsCovered = []accapi.ProductCategoryIdV2{w.PaysFor.ToId()}
					apiReport.UnitAndFrequency = accapi.AccountingUnitAndFrequency{
						Unit:      w.PaysFor.AccountingUnit,
						Frequency: w.PaysFor.AccountingFrequency,
					}

					reports = append(reports, apiReport)
				}

				{
					// Unit-aggregated report
					// -----------------------------------------------------------------------------------------------------
					// NOTE(Dan): We must not modify the historicReports directly since they might store array data in a
					// cache. That is, this is only a shallow copy, not a deep copy.

					scalingFactor := 1.0
					var report *aggregatedReport
					freq := w.PaysFor.AccountingFrequency

					if productType == accapi.ProductTypeCompute {
						scalingFactor = float64(freq.ToMinutes()) / 60.0

						if freq.IsPeriodic() {
							current, ok := computeReportsByUnit[w.PaysFor.AccountingUnit.Name]
							if !ok {
								report = &aggregatedReport{
									Title: fmt.Sprintf("%s-hours", w.PaysFor.AccountingUnit.Name),
									UnitAndFrequency: accapi.AccountingUnitAndFrequency{
										Unit:      w.PaysFor.AccountingUnit,
										Frequency: accapi.AccountingFrequencyPeriodicHour,
									},
								}
								computeReportsByUnit[w.PaysFor.AccountingUnit.Name] = report
							} else {
								report = current
							}
						} else {
							report = nil // skip it
						}

					} else if productType == accapi.ProductTypeStorage {
						if w.PaysFor.AccountingUnit.Name == "GB" {
							if freq.IsPeriodic() {
								scalingFactor = float64(freq.ToMinutes()) / (60.0 * 24.0)
								report = storageReportsTimeBased
								report.Title = "GB-days"
								report.UnitAndFrequency = accapi.AccountingUnitAndFrequency{
									Unit:      w.PaysFor.AccountingUnit,
									Frequency: accapi.AccountingFrequencyPeriodicDay,
								}
							} else {
								report = storageReportsCapacity
								report.Title = "GB"
								report.UnitAndFrequency = accapi.AccountingUnitAndFrequency{
									Unit:      w.PaysFor.AccountingUnit,
									Frequency: accapi.AccountingFrequencyOnce,
								}
							}
						} else {
							report = nil // TODO?
						}
					}

					// TODO Money units

					rescaleI64 := func(data *int64) {
						*data = int64(float64(*data) * scalingFactor)
					}

					if report != nil {
						report.Products = append(report.Products, w.PaysFor.ToId())

						for _, readOnly := range historicReports {
							var prev internalUsageReport
							util.SlowDeepCopy(readOnly, &prev)

							rescaleI64(&prev.Kpis.QuotaAtStart)
							rescaleI64(&prev.Kpis.ActiveQuotaAtStart)
							rescaleI64(&prev.Kpis.QuotaAtEnd)
							rescaleI64(&prev.Kpis.ActiveQuotaAtEnd)
							rescaleI64(&prev.Kpis.MaxUsableAtStart)
							rescaleI64(&prev.Kpis.MaxUsableAtEnd)
							rescaleI64(&prev.Kpis.LocalUsageAtStart)
							rescaleI64(&prev.Kpis.LocalUsageAtEnd)
							rescaleI64(&prev.Kpis.TotalUsageAtStart)
							rescaleI64(&prev.Kpis.TotalUsageAtEnd)
							rescaleI64(&prev.Kpis.TotalAllocatedAtStart)
							rescaleI64(&prev.Kpis.TotalAllocatedAtEnd)

							for i := range prev.UsageOverTime.Delta {
								rescaleI64(&prev.UsageOverTime.Delta[i].Change)
							}

							for i := range prev.UsageOverTime.Absolute {
								rescaleI64(&prev.UsageOverTime.Absolute[i].Usage)
								rescaleI64(&prev.UsageOverTime.Absolute[i].Quota)
							}

							for i := range prev.UsageOverTime.ChildrenAbsolute {
								rescaleI64(&prev.UsageOverTime.ChildrenAbsolute[i].Usage)
							}

							report.Reports = append(report.Reports, internalUsageReportWithProduct{w.PaysFor, prev})
						}
					}
				}
			}

			allAggregatedReports := append([]*aggregatedReport{}, storageReportsCapacity, storageReportsTimeBased)
			for _, report := range computeReportsByUnit {
				allAggregatedReports = append(allAggregatedReports, report)
			}
			for _, report := range allAggregatedReports {
				if report.Title == "" || len(report.Products) == 0 {
					continue
				}

				collapsed := usageCollapseReports(report.Reports)
				apiReport := collapsed.ToApi()
				apiReport.Title = report.Title
				apiReport.ProductsCovered = report.Products
				apiReport.UnitAndFrequency = report.UnitAndFrequency

				reports = append(reports, apiReport)
			}

			return accapi.UsageRetrieveResponse{Reports: util.NonNilSlice(reports)}, nil
		})
	}

	reportGlobals.Ready.Store(true)
}

func usageRetrieveHistoricReports(from time.Time, until time.Time, wallet AccWalletId) []internalUsageReport {
	// NOTE(Dan, 15/10/2025): Current tests will break in the year 2100, but I will let that be a problem for the
	// future.
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

	var result []internalUsageReport
	current := from
	for current.Before(until) {
		report, ok := usageRetrieveHistoric(current, wallet)

		if ok {
			result = append(result, report)
		}

		current = current.AddDate(0, 0, 1)
	}

	if nowTrunc == until || until.After(nowTrunc) {
		g := &reportGlobals
		g.Mu.RLock()
		currentReport, ok := g.Reports[wallet]
		if ok {
			result = append(result, *currentReport)
		}
		g.Mu.RUnlock()
	}

	return result
}

type absolutePointByProduct struct {
	Usage int64
	Quota int64
}

type absoluteTimeline map[time.Time]absolutePointByProduct

func appendOrReplaceAbsolute(
	points *[]internalUsageOverTimeAbsoluteDataPoint,
	point internalUsageOverTimeAbsoluteDataPoint,
) {
	for i := range *points {
		if (*points)[i].Timestamp.Equal(point.Timestamp) {
			(*points)[i] = point
			return
		}
	}

	*points = append(*points, point)

	sort.Slice(*points, func(i, j int) bool {
		return (*points)[i].Timestamp.Before((*points)[j].Timestamp)
	})
}

type internalUsageReportWithProduct struct {
	Product accapi.ProductCategory
	Report  internalUsageReport
}

func usageCollapseReports(reports []internalUsageReportWithProduct) internalUsageReport {
	if len(reports) == 0 {
		return internalUsageReport{}
	}

	sort.SliceStable(reports, func(i, j int) bool {
		return reports[i].Report.ValidFrom.Before(reports[j].Report.ValidFrom)
	})

	firstReport := reports[0].Report
	lastReport := reports[len(reports)-1].Report

	result := internalUsageReport{
		Wallet:     firstReport.Wallet,
		ValidFrom:  firstReport.ValidFrom,
		ValidUntil: util.OptValue(lastReport.ValidUntil.GetOrDefault(lastReport.ValidFrom)),
	}

	reportsByProducts := make(map[accapi.ProductCategory][]internalUsageReport)

	for _, reportWithProd := range reports {
		reportsByProducts[reportWithProd.Product] =
			append(reportsByProducts[reportWithProd.Product], reportWithProd.Report)
	}

	quotaAtStart := int64(0)
	activeQuotaAtStart := int64(0)
	maxUsableAtStart := int64(0)
	localUsageAtStart := int64(0)
	totalUsageAtStart := int64(0)
	totalAllocatedAtStart := int64(0)

	quotaAtEnd := int64(0)
	activeQuotaAtEnd := int64(0)
	maxUsableAtEnd := int64(0)
	localUsageAtEnd := int64(0)
	totalUsageAtEnd := int64(0)
	totalAllocatedAtEnd := int64(0)

	var currentNextMeaningfulExpiration util.Option[time.Time]

	for _, r := range reportsByProducts {
		fr := r[0]
		lr := r[len(r)-1]

		quotaAtStart += fr.Kpis.QuotaAtStart
		activeQuotaAtStart += fr.Kpis.ActiveQuotaAtStart
		maxUsableAtStart += fr.Kpis.MaxUsableAtStart
		localUsageAtStart += fr.Kpis.LocalUsageAtStart
		totalUsageAtStart += fr.Kpis.TotalUsageAtStart
		totalAllocatedAtStart += fr.Kpis.TotalAllocatedAtStart

		quotaAtEnd += lr.Kpis.QuotaAtEnd
		activeQuotaAtEnd += lr.Kpis.ActiveQuotaAtEnd
		maxUsableAtEnd += lr.Kpis.MaxUsableAtEnd
		localUsageAtEnd += lr.Kpis.LocalUsageAtEnd
		totalUsageAtEnd += lr.Kpis.TotalUsageAtEnd
		totalAllocatedAtEnd += lr.Kpis.TotalAllocatedAtEnd

		if lr.Kpis.NextMeaningfulExpiration.Present {
			if !currentNextMeaningfulExpiration.Present {
				currentNextMeaningfulExpiration = lr.Kpis.NextMeaningfulExpiration
			} else {
				if lr.Kpis.NextMeaningfulExpiration.Value.Before(currentNextMeaningfulExpiration.Value) {
					currentNextMeaningfulExpiration = lr.Kpis.NextMeaningfulExpiration
				}
			}
		}
	}

	result.Kpis = internalUsageReportKpis{
		QuotaAtStart:          quotaAtStart,
		ActiveQuotaAtStart:    activeQuotaAtStart,
		MaxUsableAtStart:      maxUsableAtStart,
		LocalUsageAtStart:     localUsageAtStart,
		TotalUsageAtStart:     totalUsageAtStart,
		TotalAllocatedAtStart: totalAllocatedAtStart,

		QuotaAtEnd:          quotaAtEnd,
		ActiveQuotaAtEnd:    activeQuotaAtEnd,
		MaxUsableAtEnd:      maxUsableAtEnd,
		LocalUsageAtEnd:     localUsageAtEnd,
		TotalUsageAtEnd:     totalUsageAtEnd,
		TotalAllocatedAtEnd: totalAllocatedAtEnd,

		NextMeaningfulExpiration: currentNextMeaningfulExpiration,
	}

	result.SubProjectHealth = lastReport.SubProjectHealth // NOTE(Dan): Idle is recomputed below

	// API child key -> representative wallet ID.
	//
	// Multiple wallets can belong to the same child/project, so when collapsing
	// usage by owner.Reference we keep one representative wallet ID. This allows
	// the internal representation to continue using AccWalletId.
	childWallets := map[string]AccWalletId{}

	// Every timestamp seen in the reports.
	allTimestamps := map[time.Time]util.Empty{}

	// Absolute usage for each child.
	// child -> timestamp -> usage
	absoluteUsageByChild := map[string]map[time.Time]int64{}

	// Delta data grouped by child (for later)
	deltaByChild := map[string]map[time.Time]int64{}
	allDeltaTimestamps := map[time.Time]util.Empty{}

	absoluteByProduct := map[accapi.ProductCategory]absoluteTimeline{}

	for _, reportWithProduct := range reports {
		report := reportWithProduct.Report
		product := reportWithProduct.Product

		abstimeline := absoluteByProduct[product]

		if abstimeline == nil {
			abstimeline = absoluteTimeline{}
			absoluteByProduct[product] = abstimeline
		}

		for _, point := range report.UsageOverTime.Absolute {
			// Same product + timestamp = same absolute snapshot.
			// Later report wins.
			abstimeline[point.Timestamp] = absolutePointByProduct{
				Usage: point.Usage,
				Quota: point.Quota,
			}

			allTimestamps[point.Timestamp] = util.Empty{}
		}

		// Delta timeline
		for _, item := range report.UsageOverTime.Delta {
			if !item.Child.Present {
				continue
			}

			child, ok := walletToUsageReportChild(item.Child.Value)
			if !ok || child.Key == "" {
				continue
			}

			if _, exists := childWallets[child.Key]; !exists {
				childWallets[child.Key] = child.Wallet
			}

			timeline, ok := deltaByChild[child.Key]
			if !ok {
				timeline = make(map[time.Time]int64)
				deltaByChild[child.Key] = timeline
			}

			// Multiple changes can happen at the same timestamp.
			timeline[item.Timestamp] += item.Change

			allDeltaTimestamps[item.Timestamp] = util.Empty{}
			allTimestamps[item.Timestamp] = util.Empty{}

		}

		for _, item := range report.UsageOverTime.ChildrenAbsolute {
			if !item.Child.Present {
				continue
			}

			child, ok := walletToUsageReportChild(item.Child.Value)
			if !ok || child.Key == "" {
				continue
			}

			if _, exists := childWallets[child.Key]; !exists {
				childWallets[child.Key] = child.Wallet
			}

			timeline, ok := absoluteUsageByChild[child.Key]
			if !ok {
				timeline = make(map[time.Time]int64)
				absoluteUsageByChild[child.Key] = timeline
			}

			// Snapshot: don't add duplicate observations for the same child/timestamp.
			timeline[item.Timestamp] = item.Usage

			allTimestamps[item.Timestamp] = util.Empty{}
		}
	}

	timestamps := make([]time.Time, 0, len(allTimestamps))

	for timestamp := range allTimestamps {
		timestamps = append(timestamps, timestamp)
	}

	sort.Slice(timestamps, func(i, j int) bool {
		return timestamps[i].Before(timestamps[j])
	})

	if len(timestamps) == 0 {
		return result
	}

	filledAbsoluteByProduct := make(
		map[accapi.ProductCategory]absoluteTimeline,
		len(absoluteByProduct),
	)

	for product, timeline := range absoluteByProduct {
		if len(timeline) == 0 {
			continue
		}

		knownTimestamps := make([]time.Time, 0, len(timeline))

		for timestamp := range timeline {
			knownTimestamps = append(knownTimestamps, timestamp)
		}

		slices.SortFunc(knownTimestamps, func(a, b time.Time) int {
			return a.Compare(b)
		})

		// Your required semantics:
		//
		// before first observation -> first known value
		// between observations      -> previous known value
		// after last observation    -> last known value
		lastPoint := timeline[knownTimestamps[0]]

		filled := make(absoluteTimeline, len(timestamps))

		for _, timestamp := range timestamps {
			if point, ok := timeline[timestamp]; ok {
				lastPoint = point
			}

			filled[timestamp] = lastPoint
		}

		filledAbsoluteByProduct[product] = filled
	}

	result.UsageOverTime.Absolute = make([]internalUsageOverTimeAbsoluteDataPoint, 0, len(timestamps))

	for _, timestamp := range timestamps {
		var usage int64
		var quota int64

		for _, timeline := range filledAbsoluteByProduct {
			point := timeline[timestamp]

			usage += point.Usage
			quota += point.Quota
		}

		result.UsageOverTime.Absolute = append(
			result.UsageOverTime.Absolute,
			internalUsageOverTimeAbsoluteDataPoint{
				Timestamp: timestamp,
				Usage:     usage,
				Quota:     quota,
			},
		)
	}

	// child -> timestamp -> usage (with gaps filled)
	filledUsageByChild := make(map[string]map[time.Time]int64)

	for child, timeline := range absoluteUsageByChild {
		filled := make(map[time.Time]int64)

		// Sort the timestamps where this child has a datapoint.
		childTimestamps := make([]time.Time, 0, len(timeline))
		for ts := range timeline {
			childTimestamps = append(childTimestamps, ts)
		}

		slices.SortFunc(childTimestamps, func(a, b time.Time) int {
			return a.Compare(b)
		})

		// Shouldn't happen, but be safe.
		if len(childTimestamps) == 0 {
			continue
		}

		currentUsage := timeline[childTimestamps[0]]
		nextIndex := 0

		for _, ts := range timestamps {
			// Advance whenever we reach another real datapoint.
			if nextIndex < len(childTimestamps) &&
				ts.Equal(childTimestamps[nextIndex]) {

				currentUsage = timeline[childTimestamps[nextIndex]]
				nextIndex++
			}

			filled[ts] = currentUsage
		}

		filledUsageByChild[child] = filled
	}

	// Determine each child's usage at the end of the reporting period.
	finalUsage := make(map[string]int64)

	lastTimestamp := timestamps[len(timestamps)-1]

	for child, timeline := range filledUsageByChild {
		finalUsage[child] = timeline[lastTimestamp]
	}

	// Select the top 10 users by final usage.
	topUsers := util.TopNKeys(finalUsage, 10)

	// Convert to a set for efficient lookups.
	topUserSet := make(map[string]util.Empty, len(topUsers))
	for _, child := range topUsers {
		topUserSet[child] = util.Empty{}
	}

	// child -> timestamp -> datapoint
	collapsedByChild := make(map[AccWalletId]map[time.Time]internalUsageOverTimeAbsoluteChildrenDataPoint)

	const usageReportOtherChild AccWalletId = -1

	for child, timeline := range filledUsageByChild {
		// Decide whether this child gets its own series
		outputChild := usageReportOtherChild

		if _, ok := topUserSet[child]; ok {
			outputChild = childWallets[child]
		}

		series, ok := collapsedByChild[outputChild]
		if !ok {
			series = make(map[time.Time]internalUsageOverTimeAbsoluteChildrenDataPoint)
			collapsedByChild[outputChild] = series
		}

		for _, ts := range timestamps {
			usage := timeline[ts]

			entry, exists := series[ts]
			if !exists {
				entry = internalUsageOverTimeAbsoluteChildrenDataPoint{
					Timestamp: ts,
					Child:     util.OptValue(outputChild),
					Usage:     usage,
				}
			} else {
				// Only happens for "Other"
				entry.Usage += usage
			}

			series[ts] = entry
		}
	}

	for _, series := range collapsedByChild {
		for _, point := range series {
			result.UsageOverTime.ChildrenAbsolute = append(
				result.UsageOverTime.ChildrenAbsolute,
				point,
			)
		}
	}

	slices.SortFunc(
		result.UsageOverTime.ChildrenAbsolute,
		func(a, b internalUsageOverTimeAbsoluteChildrenDataPoint) int {
			if a.Timestamp.Before(b.Timestamp) {
				return -1
			}
			if a.Timestamp.After(b.Timestamp) {
				return 1
			}

			aChild := a.Child.GetOrDefault(-2)
			bChild := b.Child.GetOrDefault(-2)

			return cmp.Compare(aChild, bChild)
		},
	)

	//Delta

	// child -> timestamp -> filled delta value
	filledDeltaByChild := make(map[string]map[time.Time]int64)

	deltaTimestamps := maps.Keys(allDeltaTimestamps)
	slices.SortFunc(deltaTimestamps, func(a, b time.Time) int {
		return a.Compare(b)
	})

	for child, timeline := range deltaByChild {
		filled := make(map[time.Time]int64)

		for _, ts := range deltaTimestamps {
			change, ok := timeline[ts]

			if ok {
				filled[ts] = change
			} else {
				// Missing delta means no activity.
				filled[ts] = 0
			}
		}

		filledDeltaByChild[child] = filled
	}

	// child -> timestamp -> delta datapoint
	collapsedDeltaByChild := make(
		map[util.Option[AccWalletId]]map[time.Time]internalUsageOverTimeDeltaDataPoint,
	)

	for child, timeline := range filledDeltaByChild {
		// Decide if this child gets its own series
		outputChild := util.OptValue(usageReportOtherChild)

		if _, ok := topUserSet[child]; ok {
			outputChild = util.OptValue(childWallets[child])
		}

		series, ok := collapsedDeltaByChild[outputChild]
		if !ok {
			series = make(map[time.Time]internalUsageOverTimeDeltaDataPoint)
			collapsedDeltaByChild[outputChild] = series
		}

		for _, ts := range deltaTimestamps {
			change := timeline[ts]

			entry, exists := series[ts]
			if !exists {
				entry = internalUsageOverTimeDeltaDataPoint{
					Timestamp: ts,
					Child:     outputChild,
					Change:    change,
				}
			} else {
				// Only happens for "Other"
				entry.Change += change
			}

			series[ts] = entry
		}
	}
	for _, series := range collapsedDeltaByChild {
		for _, point := range series {
			result.UsageOverTime.Delta = append(
				result.UsageOverTime.Delta,
				point,
			)
		}
	}
	slices.SortFunc(
		result.UsageOverTime.Delta,
		func(a, b internalUsageOverTimeDeltaDataPoint) int {
			if a.Timestamp.Before(b.Timestamp) {
				return -1
			}
			if a.Timestamp.After(b.Timestamp) {
				return 1
			}

			aChild := a.Child.GetOrDefault(-2)
			bChild := b.Child.GetOrDefault(-2)

			return cmp.Compare(aChild, bChild)
		},
	)

	return result
}

func usageRetrieveHistoric(now time.Time, wallet AccWalletId) (internalUsageReport, bool) {
	var result internalUsageReport

	g := &reportGlobals
	now = util.StartOfDayUTC(now)
	ok := false
	{
		g.Mu.RLock()
		var dictOnDay map[AccWalletId]int
		dictOnDay, ok = g.HistoricCacheIndex[now]
		slot := -1
		if ok {
			slot, ok = dictOnDay[wallet]
		}

		if ok {
			entry := &g.HistoricCache[slot]
			if entry.InUse {
				entry.LastUsedAt.Store(util.Pointer(time.Now()))
				result = entry.Report
			} else {
				ok = false
			}
		}
		g.Mu.RUnlock()
	}

	if !ok && !accGlobals.TestingEnabled {
		reports := db.NewTx(func(tx *db.Transaction) []internalUsageReport {
			rows := db.Select[struct {
				ReportData string
			}](
				tx,
				`
					select report_data
					from accounting.usage_report
					where
						wallet_id = :wallet
						and (
							valid_from >= (:valid_from::timestamptz - ('90 days'::interval))
							and valid_from <= (:valid_from::timestamptz + ('90 days'::interval)) -- prefetch 90 days in both directions
						)
					order by valid_from
			    `,
				db.Params{
					"wallet":     wallet,
					"valid_from": now,
				},
			)

			var reports []internalUsageReport
			for _, row := range rows {
				var report internalUsageReport
				_ = json.Unmarshal([]byte(row.ReportData), &report)
				reports = append(reports, report)
			}

			return reports
		})

		g.Mu.Lock()
		for _, report := range reports {
			lUsageCacheReport(&report)
		}

		{
			var dictOnDay map[AccWalletId]int
			dictOnDay, ok = g.HistoricCacheIndex[now]
			slot := -1
			if ok {
				slot, ok = dictOnDay[wallet]
			}

			if ok {
				entry := &g.HistoricCache[slot]
				entry.LastUsedAt.Store(util.Pointer(time.Now()))
				result = entry.Report
			}
		}

		g.Mu.Unlock()
	}

	return result, ok
}

func lUsageRetireReport(report *internalUsageReport, b *db.Batch) {
	lUsageCacheReport(report)
	lUsagePersistReport(report, b)
}

func lUsagePersistReport(report *internalUsageReport, b *db.Batch) {
	if accGlobals.TestingEnabled {
		return
	}

	reportJson, _ := json.Marshal(report)
	walletId := report.Wallet
	validFrom := report.ValidFrom

	db.BatchExec(
		b,
		`
			insert into accounting.usage_report(wallet_id, valid_from, report_format, report_data)
			values (:wallet, :valid_from, 1, :data)
			on conflict (wallet_id, valid_from) do update set 
				report_data = excluded.report_data
		`,
		db.Params{
			"wallet":     walletId,
			"valid_from": validFrom,
			"data":       string(reportJson),
		},
	)
}

func lUsageCacheReport(report *internalUsageReport) {
	g := &reportGlobals
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
		log.Fatal("no space in cache? internal error")
	}

	dictOnDay, ok := g.HistoricCacheIndex[report.ValidFrom]
	if !ok {
		dictOnDay = map[AccWalletId]int{}
		g.HistoricCacheIndex[report.ValidFrom] = dictOnDay
	}

	dictOnDay[report.Wallet] = slot
}

func lUsageEvictHistoricCache() {
	g := &reportGlobals

	if g.HistoricCacheSlotsAvailable == 0 {
		oldestEntry := time.Now()
		for i := range g.HistoricCache {
			entry := &g.HistoricCache[i]
			if entry.InUse {
				usedAt := *entry.LastUsedAt.Load()
				if usedAt.Before(oldestEntry) {
					oldestEntry = usedAt
				}
			}
		}

		evictBefore := oldestEntry.Add(60 * time.Minute)

		for i := range g.HistoricCache {
			entry := &g.HistoricCache[i]
			if entry.InUse && entry.LastUsedAt.Load().Before(evictBefore) {
				idx, ok := g.HistoricCacheIndex[entry.Report.ValidFrom]
				if ok {
					delete(idx, entry.Report.Wallet)
				}

				entry.InUse = false
				entry.Report = internalUsageReport{}
				entry.LastUsedAt.Store(util.Pointer(time.Now()))
				g.HistoricCacheSlotsAvailable++
			}
		}
	}
}

func usageSample(now time.Time) {
	usageSampleEx(now, nil)
}

func usageSampleEx(now time.Time, bucketFilter func(cat accapi.ProductCategory) bool) {
	batch := db.BatchNewDeferred()
	startOfDay := util.StartOfDayUTC(now)

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
			wallet := lSnapshotWallet(startOfDay, b, w)
			snapshotsById[w.Id] = wallet

			if !accGlobals.TestingEnabled {
				jsonSnapshot, _ := json.Marshal(wallet.Current)

				db.BatchExec(
					batch,
					`
						insert into accounting.wallet_snapshots(id, snapshot) 
						values (:id, :snapshot) 
						on conflict (id) do update set 
							snapshot = excluded.snapshot,
							created_at = now()
				    `,
					db.Params{
						"id":       wallet.Current.Id,
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
			r := lUsageSampleEnsureReport(now, snapshotsById[AccWalletId(wId)], batch)
			r.SubProjectHealth = internalSubProjectHealth{}
		}

		for _, wId := range walletIds {
			lUsageSampleWallet(now, snapshotsById[AccWalletId(wId)], batch)
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

func lSnapshotWallet(now time.Time, b *internalBucket, w *internalWallet) internalSnapshotComparison {
	prev, ok := reportGlobals.Snapshots[w.Id]
	if !ok {
		prev = internalWalletSnapshot{
			Id:                        w.Id,
			Timestamp:                 now.AddDate(0, 0, -1),
			Quota:                     0,
			ActiveQuota:               0,
			MaxUsable:                 0,
			LocalUsage:                0,
			TotalUsage:                0,
			TotalAllocated:            0,
			UsageByParent:             map[AccWalletId]int64{},
			QuotaByParentActive:       map[AccWalletId]int64{},
			QuotaByParentContributing: map[AccWalletId]int64{},
			HealthByParent:            map[AccWalletId]internalGroupHealth{},
			Category:                  b.Category,
			NextMeaningfulExpiration:  util.OptNone[time.Time](),
		}
	}

	current := internalWalletSnapshot{
		Id:                        w.Id,
		Timestamp:                 now,
		Quota:                     lInternalWalletTotalQuotaContributing(b, w),
		ActiveQuota:               lInternalWalletTotalQuotaFromActiveAllocations(b, w),
		MaxUsable:                 lInternalMaxUsable(b, now, w),
		LocalUsage:                w.LocalUsage,
		TotalUsage:                lInternalWalletTotalUsageInNode(b, w),
		TotalAllocated:            lInternalWalletTotalAllocatedContributing(b, w),
		UsageByParent:             map[AccWalletId]int64{},
		QuotaByParentActive:       map[AccWalletId]int64{},
		QuotaByParentContributing: map[AccWalletId]int64{},
		HealthByParent:            map[AccWalletId]internalGroupHealth{},
		Category:                  b.Category,
	}

	minimumMeaningfulQuota := int64(float64(current.ActiveQuota) * 0.1)
	earliestExpiration := util.OptNone[time.Time]()

	for parent, group := range w.AllocationsByParent {
		current.UsageByParent[parent] = group.TreeUsage
		contributingQuota := lInternalGroupTotalQuotaContributing(b, group)
		activeQuota := lInternalGroupTotalQuotaFromActiveAllocations(b, group)
		current.QuotaByParentContributing[parent] = contributingQuota
		current.QuotaByParentActive[parent] = activeQuota

		for allocId := range group.Allocations {
			alloc := b.AllocationsById[allocId]
			if alloc.Active && alloc.Quota > minimumMeaningfulQuota {
				if !earliestExpiration.Present || alloc.End.Before(earliestExpiration.Value) {
					earliestExpiration.Set(alloc.End)
				}
			}
		}

		// Determined expected usage (linear usage assumption)
		quotaIn30Days := int64(0)
		retiredUsage := activeQuota - contributingQuota
		activeUsage := group.TreeUsage - retiredUsage

		health := internalGroupHealthOk

		in30Days := now.AddDate(0, 0, 30)

		totalExpectedUsage := retiredUsage
		for allocId := range group.Allocations {
			alloc := b.AllocationsById[allocId]
			if alloc.Active {
				allocationDuration := alloc.End.Sub(alloc.Start)
				timeRemaining := max(alloc.End.Sub(now), 0)
				timeUsed := allocationDuration - timeRemaining
				timePercentageUsed := float64(timeUsed) / float64(allocationDuration)

				expectedUsage := float64(alloc.Quota) * timePercentageUsed
				totalExpectedUsage += int64(expectedUsage)

				if timeRemaining >= 30*24*time.Hour {
					quotaIn30Days += alloc.Quota
				}
			} else {
				if alloc.Start.Before(in30Days) && alloc.End.After(in30Days) {
					quotaIn30Days += alloc.Quota
				}
			}
		}

		if float64(activeUsage) >= float64(quotaIn30Days)*0.8 && quotaIn30Days < activeQuota {
			health = internalGroupHealthAtRisk
		} else if float64(activeUsage) >= float64(activeQuota)*0.9 {
			health = internalGroupHealthAtRisk
		} else if float64(activeUsage) < float64(totalExpectedUsage)*0.5 {
			health = internalGroupHealthUnderUtilized
		}

		current.HealthByParent[parent] = health
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
			Wallet:     currWallet.Id,
			ValidFrom:  startOfDay,
			ValidUntil: util.Option[time.Time]{},

			Kpis: internalUsageReportKpis{
				QuotaAtStart:          prevWallet.Quota,
				ActiveQuotaAtStart:    prevWallet.ActiveQuota,
				MaxUsableAtStart:      prevWallet.MaxUsable,
				LocalUsageAtStart:     prevWallet.LocalUsage,
				TotalUsageAtStart:     prevWallet.TotalUsage,
				TotalAllocatedAtStart: prevWallet.TotalAllocated,
			},

			// Will be recomputed:
			SubProjectHealth: internalSubProjectHealth{},
			UsageOverTime:    internalUsageOverTime{},
		}

		reportGlobals.Reports[currWallet.Id] = report
	}

	return report
}

func lUsageSampleWallet(now time.Time, cmp internalSnapshotComparison, b *db.Batch) {
	prevWallet := cmp.Previous
	currWallet := cmp.Current

	report := lUsageSampleEnsureReport(now, cmp, b)

	kpis := &report.Kpis
	kpis.QuotaAtEnd = currWallet.Quota
	kpis.ActiveQuotaAtEnd = currWallet.ActiveQuota
	kpis.MaxUsableAtEnd = currWallet.MaxUsable
	kpis.LocalUsageAtEnd = currWallet.LocalUsage
	kpis.TotalUsageAtEnd = currWallet.TotalUsage
	kpis.TotalAllocatedAtEnd = currWallet.TotalAllocated
	kpis.NextMeaningfulExpiration = currWallet.NextMeaningfulExpiration

	{
		prevUsage := prevWallet.LocalUsage
		currUsage := currWallet.LocalUsage
		delta := currUsage - prevUsage

		if delta != 0 {
			report.UsageOverTime.Delta = append(report.UsageOverTime.Delta, internalUsageOverTimeDeltaDataPoint{
				Timestamp: now,
				Child:     util.Option[AccWalletId]{},
				Change:    delta,
			})

			report.Dirty = true
		}

		if prevWallet.LocalUsage != currWallet.LocalUsage || prevWallet.TotalUsage != currWallet.TotalUsage || currWallet.Quota != prevWallet.Quota {

			appendOrReplaceAbsolute(
				&report.UsageOverTime.Absolute,
				internalUsageOverTimeAbsoluteDataPoint{
					Timestamp: now,
					Usage:     currWallet.TotalUsage,
					Quota:     currWallet.Quota,
				},
			)

			report.UsageOverTime.ChildrenAbsolute = append(report.UsageOverTime.ChildrenAbsolute, internalUsageOverTimeAbsoluteChildrenDataPoint{
				Timestamp: now,
				Usage:     currWallet.TotalUsage,
				Child:     util.Option[AccWalletId]{},
			})

			report.Dirty = true
		}
	}

	for parent, usage := range currWallet.UsageByParent {
		if parent == 0 {
			continue
		}
		prevUsage := prevWallet.UsageByParent[parent]
		delta := usage - prevUsage

		parentReport := reportGlobals.Reports[parent]
		parentReport.SubProjectHealth.SubProjectCount++

		parentReport.UsageOverTime.ChildrenAbsolute = append(
			parentReport.UsageOverTime.ChildrenAbsolute,
			internalUsageOverTimeAbsoluteChildrenDataPoint{
				Timestamp: now,
				Usage:     usage,
				Child:     util.OptValue(currWallet.Id),
			})
		parentReport.Dirty = true

		if delta != 0 {
			parentReport.UsageOverTime.Delta = append(
				parentReport.UsageOverTime.Delta,
				internalUsageOverTimeDeltaDataPoint{
					Timestamp: now,
					Child:     util.OptValue(currWallet.Id),
					Change:    delta,
				},
			)

		} else {
			parentReport.SubProjectHealth.Idle++
		}

		switch currWallet.HealthByParent[parent] {
		case internalGroupHealthOk:
			parentReport.SubProjectHealth.Ok++
		case internalGroupHealthUnderUtilized:
			parentReport.SubProjectHealth.UnderUtilized++
		case internalGroupHealthAtRisk:
			parentReport.SubProjectHealth.AtRisk++
		}
	}

	if report.Dirty {
		lUsagePersistReport(report, b)
	}
}
