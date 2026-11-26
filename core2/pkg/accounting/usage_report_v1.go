package accounting

import (
	"cmp"
	"fmt"
	"slices"
	"sort"
	"time"

	"golang.org/x/exp/maps"
	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func usageInitV1Handlers() {
	accapi.UsageRetrieve.Handler(usageHandleV1Retrieve)
}

func usageHandleV1Retrieve(
	info rpc.RequestInfo,
	request accapi.UsageRetrieveRequest,
) (accapi.UsageRetrieveResponse, *util.HttpError) {
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
		AccountingModel  accapi.AccountingModel
		Products         []accapi.ProductCategoryIdV2
	}

	computeReportsByUnit := map[string]*aggregatedReport{}
	storageReportsByUnit := map[string]*aggregatedReport{}

	var reports []accapi.UsageReport
	for _, w := range wallets {
		productType := w.PaysFor.ProductType
		if productType != accapi.ProductTypeCompute && productType != accapi.ProductTypeStorage && productType != accapi.ProductTypeInference {
			continue
		}

		b := internalBucketOrInit(w.PaysFor)
		walletId := internalWalletByOwner(b, now, owner.Id)

		startTime := fndapi.TimeFromUnixMilli(request.Start).Time()
		endTime := fndapi.TimeFromUnixMilli(request.End).Time()

		historicReports := usageRetrieveHistoricReports(startTime, endTime, walletId)
		for i := range historicReports {
			historicReports[i] = usageReportV1Compat(historicReports[i], endTime)
		}

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
			report := usageCollapseReports(historicReportsWithProduct)
			apiReport := report.ToApiV1()
			apiReport.AccountingModel = w.PaysFor.AccountingModel
			apiReport.Title = w.PaysFor.Name
			apiReport.ProductsCovered = []accapi.ProductCategoryIdV2{w.PaysFor.ToId()}
			apiReport.UnitAndFrequency = accapi.AccountingUnitAndFrequency{
				Unit:      w.PaysFor.AccountingUnit,
				Frequency: w.PaysFor.AccountingFrequency,
			}

			reports = append(reports, apiReport)
		}

		{
			scalingFactor := 1.0
			var report *aggregatedReport
			freq := w.PaysFor.AccountingFrequency

			if productType == accapi.ProductTypeCompute {
				scalingFactor = float64(freq.ToSeconds()) / (60.0 * 60.0)

				if freq.IsPeriodic() {
					key := string(w.PaysFor.AccountingModel) + "/" + w.PaysFor.AccountingUnit.Name
					current, ok := computeReportsByUnit[key]
					if !ok {
						report = &aggregatedReport{
							Title:           fmt.Sprintf("%s-hours", w.PaysFor.AccountingUnit.Name),
							AccountingModel: w.PaysFor.AccountingModel,
							UnitAndFrequency: accapi.AccountingUnitAndFrequency{
								Unit:      w.PaysFor.AccountingUnit,
								Frequency: accapi.AccountingFrequencyPeriodicHour,
							},
						}
						computeReportsByUnit[key] = report
					} else {
						report = current
					}
				} else {
					report = nil
				}

			} else if productType == accapi.ProductTypeStorage {
				if w.PaysFor.AccountingUnit.Name == "GB" {
					title := "GB"
					frequency := accapi.AccountingFrequencyOnce
					if freq.IsPeriodic() {
						scalingFactor = float64(freq.ToSeconds()) / (60.0 * 60.0 * 24.0)
						title = "GB-days"
						frequency = accapi.AccountingFrequencyPeriodicDay
					}
					key := string(w.PaysFor.AccountingModel) + "/" + title
					report = storageReportsByUnit[key]
					if report == nil {
						report = &aggregatedReport{
							Title:           title,
							AccountingModel: w.PaysFor.AccountingModel,
							UnitAndFrequency: accapi.AccountingUnitAndFrequency{
								Unit:      w.PaysFor.AccountingUnit,
								Frequency: frequency,
							},
						}
						storageReportsByUnit[key] = report
					}
				} else {
					report = nil
				}
			}

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

	var allAggregatedReports []*aggregatedReport
	for _, report := range storageReportsByUnit {
		allAggregatedReports = append(allAggregatedReports, report)
	}
	for _, report := range computeReportsByUnit {
		allAggregatedReports = append(allAggregatedReports, report)
	}
	titleCounts := map[string]int{}
	for _, report := range allAggregatedReports {
		titleCounts[report.Title]++
	}
	for _, report := range allAggregatedReports {
		if report.Title == "" || len(report.Products) == 0 {
			continue
		}

		collapsed := usageCollapseReports(report.Reports)
		apiReport := collapsed.ToApiV1()
		apiReport.Title = report.Title
		if titleCounts[report.Title] > 1 {
			apiReport.Title = fmt.Sprintf("%s (%s)", report.Title, report.AccountingModel)
		}
		apiReport.ProductsCovered = report.Products
		apiReport.UnitAndFrequency = report.UnitAndFrequency
		apiReport.AccountingModel = report.AccountingModel

		reports = append(reports, apiReport)
	}

	return accapi.UsageRetrieveResponse{Reports: util.NonNilSlice(reports)}, nil
}

func usageReportV1Compat(report internalUsageReport, endTime time.Time) internalUsageReport {
	if report.Format < usageReportFormatV2 {
		return report
	}

	result := report
	result.UsageOverTime = internalUsageOverTime{
		Delta:            make([]internalUsageOverTimeDeltaDataPoint, 0, len(report.UsageOverTime.ChildEvents)),
		Absolute:         make([]internalUsageOverTimeAbsoluteDataPoint, 0, len(report.UsageOverTime.WalletSeries)),
		ChildrenAbsolute: make([]internalUsageOverTimeAbsoluteChildrenDataPoint, 0, len(report.UsageOverTime.ChildDayEnd)),
	}

	for _, event := range report.UsageOverTime.ChildEvents {
		result.UsageOverTime.Delta = append(result.UsageOverTime.Delta, internalUsageOverTimeDeltaDataPoint{
			Timestamp: event.Timestamp,
			Child:     event.Child,
			Change:    event.UsageChange + event.FlowShift,
		})
	}

	for _, sample := range report.UsageOverTime.WalletSeries {
		result.UsageOverTime.Absolute = append(result.UsageOverTime.Absolute, internalUsageOverTimeAbsoluteDataPoint{
			Timestamp: sample.Timestamp,
			Usage:     sample.TotalUsage,
			Quota:     sample.Quota,
		})
	}

	reportEnd := report.ValidUntil.GetOrDefault(report.ValidFrom.Add(24 * time.Hour))
	if reportEnd.IsZero() {
		reportEnd = report.ValidFrom
	}
	if !endTime.IsZero() && reportEnd.After(endTime) {
		reportEnd = endTime
	}
	if reportEnd.Before(report.ValidFrom) {
		reportEnd = report.ValidFrom
	}

	if len(result.UsageOverTime.Absolute) == 0 {
		result.UsageOverTime.Absolute = []internalUsageOverTimeAbsoluteDataPoint{
			{
				Timestamp: report.ValidFrom,
				Usage:     report.Kpis.TotalUsageAtStart,
				Quota:     report.Kpis.QuotaAtStart,
			},
			{
				Timestamp: reportEnd,
				Usage:     report.Kpis.TotalUsageAtEnd,
				Quota:     report.Kpis.QuotaAtEnd,
			},
		}
	}

	for child, snapshot := range report.UsageOverTime.ChildDayEnd {
		result.UsageOverTime.ChildrenAbsolute = append(result.UsageOverTime.ChildrenAbsolute, internalUsageOverTimeAbsoluteChildrenDataPoint{
			Timestamp: reportEnd,
			Usage:     snapshot.TreeUsage,
			Child:     util.OptValue(child),
		})
	}

	slices.SortFunc(result.UsageOverTime.Delta, func(a, b internalUsageOverTimeDeltaDataPoint) int {
		if result := a.Timestamp.Compare(b.Timestamp); result != 0 {
			return result
		}
		return cmp.Compare(a.Child.GetOrDefault(-2), b.Child.GetOrDefault(-2))
	})

	slices.SortFunc(result.UsageOverTime.Absolute, func(a, b internalUsageOverTimeAbsoluteDataPoint) int {
		return a.Timestamp.Compare(b.Timestamp)
	})

	slices.SortFunc(result.UsageOverTime.ChildrenAbsolute, func(a, b internalUsageOverTimeAbsoluteChildrenDataPoint) int {
		if result := a.Timestamp.Compare(b.Timestamp); result != 0 {
			return result
		}
		return cmp.Compare(a.Child.GetOrDefault(-2), b.Child.GetOrDefault(-2))
	})

	return result
}

func (r *internalUsageReport) ToApiV1() accapi.UsageReport {
	return accapi.UsageReport{
		ValidFrom:        fndapi.Timestamp(r.ValidFrom),
		ValidUntil:       fndapi.Timestamp(r.ValidUntil.GetOrDefault(time.Now())),
		Kpis:             r.Kpis.ToApiV1(),
		SubProjectHealth: accapi.UsageReportSubProjectHealth{},
		UsageOverTime:    r.UsageOverTime.ToApiV1(),
	}
}

func (r *internalUsageReportKpis) ToApiV1() accapi.UsageReportKpis {
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

func (r *internalUsageOverTimeDeltaDataPoint) ToApiV1() accapi.UsageReportDeltaDataPoint {
	return accapi.UsageReportDeltaDataPoint{
		Timestamp: fndapi.Timestamp(r.Timestamp),
		Change:    r.Change,
		Child:     setChild(r.Child),
	}
}

func (d *internalUsageOverTimeAbsoluteChildrenDataPoint) ToApiV1() accapi.UsageReportAbsoluteChildrenDataPoint {
	return accapi.UsageReportAbsoluteChildrenDataPoint{
		Timestamp: fndapi.Timestamp(d.Timestamp),
		Usage:     d.Usage,
		Child:     setChild(d.Child),
	}
}

func (r *internalUsageOverTimeAbsoluteDataPoint) ToApiV1() accapi.UsageReportAbsoluteDataPoint {
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

func (r *internalUsageOverTime) ToApiV1() accapi.UsageReportOverTime {
	res := accapi.UsageReportOverTime{
		Delta:            make([]accapi.UsageReportDeltaDataPoint, len(r.Delta)),
		Absolute:         make([]accapi.UsageReportAbsoluteDataPoint, len(r.Absolute)),
		ChildrenAbsolute: make([]accapi.UsageReportAbsoluteChildrenDataPoint, len(r.ChildrenAbsolute)),
	}

	for i := range res.Delta {
		res.Delta[i] = r.Delta[i].ToApiV1()
	}

	for i := range res.Absolute {
		res.Absolute[i] = r.Absolute[i].ToApiV1()
	}

	for i := range res.ChildrenAbsolute {
		res.ChildrenAbsolute[i] = r.ChildrenAbsolute[i].ToApiV1()
	}
	return res
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

type absolutePointByProduct struct {
	Usage int64
	Quota int64
}

type absoluteTimeline map[time.Time]absolutePointByProduct

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

	childWallets := map[string]AccWalletId{}
	allTimestamps := map[time.Time]util.Empty{}
	absoluteUsageByChild := map[string]map[accapi.ProductCategory]map[time.Time]int64{}
	deltaByChild := map[string]map[time.Time]int64{}
	deltaByChildAndProduct := map[string]map[accapi.ProductCategory]map[time.Time]int64{}
	allDeltaTimestamps := map[time.Time]util.Empty{}
	absoluteByProduct := map[accapi.ProductCategory]absoluteTimeline{}

	for _, reportWithProduct := range reports {
		report := reportWithProduct.Report
		product := reportWithProduct.Product

		abstimeline := absoluteByProduct[product]

		if abstimeline == nil {
			abstimeline = absoluteTimeline{
				report.ValidFrom: {
					Usage: report.Kpis.TotalUsageAtStart,
					Quota: report.Kpis.QuotaAtStart,
				},
			}
			absoluteByProduct[product] = abstimeline
			allTimestamps[report.ValidFrom] = util.Empty{}
		}

		for _, point := range report.UsageOverTime.Absolute {
			abstimeline[point.Timestamp] = absolutePointByProduct{
				Usage: point.Usage,
				Quota: point.Quota,
			}

			allTimestamps[point.Timestamp] = util.Empty{}
		}

		for _, item := range report.UsageOverTime.Delta {
			childKey := ""
			if item.Child.Present {
				child, ok := walletToUsageReportChild(item.Child.Value)
				if !ok || child.Key == "" {
					continue
				}

				childKey = child.Key
				if _, exists := childWallets[childKey]; !exists {
					childWallets[childKey] = child.Wallet
				}

				byProduct := deltaByChildAndProduct[childKey]
				if byProduct == nil {
					byProduct = make(map[accapi.ProductCategory]map[time.Time]int64)
					deltaByChildAndProduct[childKey] = byProduct
				}
				if byProduct[product] == nil {
					byProduct[product] = make(map[time.Time]int64)
				}
				byProduct[product][item.Timestamp] += item.Change
			}

			timeline, ok := deltaByChild[childKey]
			if !ok {
				timeline = make(map[time.Time]int64)
				deltaByChild[childKey] = timeline
			}

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

			byProduct, ok := absoluteUsageByChild[child.Key]
			if !ok {
				byProduct = make(map[accapi.ProductCategory]map[time.Time]int64)
				absoluteUsageByChild[child.Key] = byProduct
			}

			timeline, ok := byProduct[product]
			if !ok {
				timeline = make(map[time.Time]int64)
				byProduct[product] = timeline
			}

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

		lastPoint := absolutePointByProduct{}
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

	filledUsageByChild := make(map[string]map[time.Time]int64)

	for child, byProduct := range absoluteUsageByChild {
		filled := make(map[time.Time]int64)

		for product, timeline := range byProduct {
			var firstTimestamp time.Time
			for ts := range timeline {
				if firstTimestamp.IsZero() || ts.Before(firstTimestamp) {
					firstTimestamp = ts
				}
			}

			if len(timeline) == 0 {
				continue
			}

			start := reportsByProducts[product][0].ValidFrom
			if firstTimestamp.After(start) {
				startingUsage := timeline[firstTimestamp]
				for ts, change := range deltaByChildAndProduct[child][product] {
					if !ts.After(firstTimestamp) {
						startingUsage -= change
					}
				}
				timeline[start] = startingUsage
			}

			currentUsage := int64(0)

			for _, ts := range timestamps {
				if usage, ok := timeline[ts]; ok {
					currentUsage = usage
				}

				filled[ts] += currentUsage
			}
		}

		filledUsageByChild[child] = filled
	}

	finalUsage := make(map[string]int64)
	lastTimestamp := timestamps[len(timestamps)-1]

	for child, timeline := range filledUsageByChild {
		finalUsage[child] = timeline[lastTimestamp]
	}

	topUsers := util.TopNKeys(finalUsage, 10)

	topUserSet := make(map[string]util.Empty, len(topUsers))
	for _, child := range topUsers {
		topUserSet[child] = util.Empty{}
	}

	collapsedByChild := make(map[AccWalletId]map[time.Time]internalUsageOverTimeAbsoluteChildrenDataPoint)

	const usageReportOtherChild AccWalletId = -1

	for child, timeline := range filledUsageByChild {
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
			if result := a.Timestamp.Compare(b.Timestamp); result != 0 {
				return result
			}

			return cmp.Compare(a.Child.GetOrDefault(-2), b.Child.GetOrDefault(-2))
		},
	)

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
				filled[ts] = 0
			}
		}

		filledDeltaByChild[child] = filled
	}

	collapsedDeltaByChild := make(
		map[util.Option[AccWalletId]]map[time.Time]internalUsageOverTimeDeltaDataPoint,
	)

	for child, timeline := range filledDeltaByChild {
		outputChild := util.OptValue(usageReportOtherChild)

		if child == "" {
			outputChild = util.OptNone[AccWalletId]()
		} else if _, ok := topUserSet[child]; ok {
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
			if result := a.Timestamp.Compare(b.Timestamp); result != 0 {
				return result
			}

			return cmp.Compare(a.Child.GetOrDefault(-2), b.Child.GetOrDefault(-2))
		},
	)

	return result
}
