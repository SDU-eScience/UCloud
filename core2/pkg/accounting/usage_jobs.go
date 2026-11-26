package accounting

import (
	"cmp"
	"math"
	"slices"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

func usageBuildJobBreakdown(
	selectedWallets []usageWalletWithScale,
	pinnedChildren []string,
	startTime time.Time,
	endTime time.Time,
	bucketStarts []time.Time,
	bucketDur time.Duration,
) util.Option[accapi.UsageJobBreakdown] {
	var computeWallets []usageWalletWithScale
	for _, w := range selectedWallets {
		if w.Live.Category.ProductType == accapi.ProductTypeCompute {
			computeWallets = append(computeWallets, w)
		}
	}
	if len(computeWallets) == 0 || len(bucketStarts) == 0 || accGlobals.TestingEnabled {
		return util.OptNone[accapi.UsageJobBreakdown]()
	}

	startDay := util.StartOfDayUTC(startTime)
	endDay := util.StartOfDayUTC(endTime)

	pinnedSet := map[string]util.Empty{}
	for _, p := range pinnedChildren {
		pinnedSet[p] = util.Empty{}
	}

	categories := make([]orcapi.JobUsageCategoryFilter, 0, len(computeWallets))
	for _, w := range computeWallets {
		attr := &usageInferenceProviderAttribution{
			ExclusiveOwners: map[string]util.Empty{},
			DailyWeights:    map[string]map[string]float64{},
		}
		usageCollectWalletInferenceAttribution(w, pinnedSet, startDay, endDay, attr)
		category := orcapi.JobUsageCategoryFilter{
			Provider: w.Live.Category.Provider,
			Category: w.Live.Category.Name,
		}
		for owner := range attr.ExclusiveOwners {
			category.ExclusiveOwners = append(category.ExclusiveOwners, owner)
		}
		slices.Sort(category.ExclusiveOwners)
		for owner, dayMap := range attr.DailyWeights {
			if _, isExclusive := attr.ExclusiveOwners[owner]; isExclusive {
				continue
			}
			for day, weight := range dayMap {
				if weight <= 1e-6 {
					continue
				}
				category.DailyWeights = append(category.DailyWeights, orcapi.JobUsageDailyWeight{
					Owner:  owner,
					Day:    day,
					Weight: min(weight, 1.0),
				})
			}
		}
		slices.SortFunc(category.DailyWeights, func(a, b orcapi.JobUsageDailyWeight) int {
			if c := cmp.Compare(a.Owner, b.Owner); c != 0 {
				return c
			}
			return cmp.Compare(a.Day, b.Day)
		})
		if len(category.ExclusiveOwners) > 0 || len(category.DailyWeights) > 0 {
			categories = append(categories, category)
		}
	}

	if len(categories) == 0 {
		return util.OptValue(usageAssembleJobBreakdown(
			orcapi.JobUsageBreakdownResponse{},
			startDay,
			endDay,
			bucketStarts,
			bucketDur,
			startTime,
			endTime,
		))
	}

	resp, err := orcapi.JobUsageBreakdownInternal.Invoke(orcapi.JobUsageBreakdownInternalRequest{
		Start:         fndapi.Timestamp(startTime),
		End:           fndapi.Timestamp(endTime),
		BucketStart:   fndapi.Timestamp(bucketStarts[0]),
		BucketSeconds: int64(bucketDur / time.Second),
		BucketCount:   len(bucketStarts),
		Categories:    categories,
	})
	if err != nil {
		log.Warn("Could not retrieve job usage breakdown: %s", err.Error())
		return util.OptValue(accapi.UsageJobBreakdown{
			Available:    false,
			Applications: []accapi.UsageJobApplicationSummary{},
			Products:     []accapi.UsageJobProductSummary{},
			Timeline:     []accapi.UsageJobTimelineBucket{},
		})
	}

	return util.OptValue(usageAssembleJobBreakdown(
		resp,
		startDay,
		endDay,
		bucketStarts,
		bucketDur,
		startTime,
		endTime,
	))
}

func usageAssembleJobBreakdown(
	resp orcapi.JobUsageBreakdownResponse,
	startDay time.Time,
	endDay time.Time,
	bucketStarts []time.Time,
	bucketDur time.Duration,
	startTime time.Time,
	endTime time.Time,
) accapi.UsageJobBreakdown {
	nBuckets := len(bucketStarts)
	timeline := make([]accapi.UsageJobTimelineBucket, nBuckets)

	type sliceKey struct {
		Application string
		ProductKey  string
	}
	type sliceAccum struct {
		Application    string
		ProductKey     string
		EstimatedHours float64
		RequestedHours float64
		Completed      float64
		Failed         float64
		Expired        float64
		Active         float64
	}
	bucketSlices := make([]map[sliceKey]*sliceAccum, nBuckets)
	for i, bStart := range bucketStarts {
		timeline[i] = accapi.UsageJobTimelineBucket{
			Timestamp:    fndapi.Timestamp(usageBucketStart(bStart, startTime)),
			EndTimestamp: fndapi.Timestamp(usageBucketEnd(bStart, bucketDur, endTime)),
			BySlice:      []accapi.UsageJobSliceBucketValue{},
		}
		bucketSlices[i] = map[sliceKey]*sliceAccum{}
	}

	type appAccum struct {
		Application    string
		EstimatedHours float64
		Completed      float64
		Failed         float64
		Expired        float64
		Active         float64
		ProductJobs    map[string]float64
	}
	type prodKey struct {
		Product  string
		Provider string
	}
	type prodAccum struct {
		Product        string
		Provider       string
		EstimatedHours float64
		RequestedHours float64
		Completed      float64
		Failed         float64
		Expired        float64
		Active         float64
	}

	appMap := map[string]*appAccum{}
	prodMap := map[prodKey]*prodAccum{}

	addFloatOutcome := func(completed *float64, failed *float64, expired *float64, active *float64, outcome string, val float64) {
		switch outcome {
		case "completed":
			*completed += val
		case "failed":
			*failed += val
		case "expired":
			*expired += val
		default:
			*active += val
		}
	}

	resolveBucketIdx := func(dayStr string) int {
		if nBuckets == 0 {
			return -1
		}
		dayTime, err := time.Parse("2006-01-02", dayStr)
		if err != nil {
			return -1
		}
		dayTime = dayTime.UTC()
		idx := usageFindBucketIndex(bucketStarts, bucketDur, dayTime)
		if idx < 0 {
			if !dayTime.After(startDay) {
				idx = 0
			} else if !dayTime.Before(endDay) {
				idx = nBuckets - 1
			}
		}
		if idx < 0 || idx >= nBuckets {
			return -1
		}
		return idx
	}

	for _, row := range resp.DailyRows {
		if row.JobCount <= 0 && row.EstimatedHours <= 0 && row.RequestedHours <= 0 {
			continue
		}

		app, hasApp := appMap[row.Application]
		if !hasApp {
			app = &appAccum{
				Application: row.Application,
				ProductJobs: map[string]float64{},
			}
			appMap[row.Application] = app
		}
		app.EstimatedHours += row.EstimatedHours
		addFloatOutcome(&app.Completed, &app.Failed, &app.Expired, &app.Active, row.Outcome, row.JobCount)
		productKey := row.Provider + "/" + row.Product
		app.ProductJobs[productKey] += row.JobCount

		pKey := prodKey{Product: row.Product, Provider: row.Provider}
		prod, hasProd := prodMap[pKey]
		if !hasProd {
			prod = &prodAccum{
				Product:  row.Product,
				Provider: row.Provider,
			}
			prodMap[pKey] = prod
		}
		prod.EstimatedHours += row.EstimatedHours
		prod.RequestedHours += row.RequestedHours
		addFloatOutcome(&prod.Completed, &prod.Failed, &prod.Expired, &prod.Active, row.Outcome, row.JobCount)

		idx := resolveBucketIdx(row.Day)
		if idx >= 0 {
			sKey := sliceKey{Application: row.Application, ProductKey: productKey}
			sl, hasSl := bucketSlices[idx][sKey]
			if !hasSl {
				sl = &sliceAccum{
					Application: row.Application,
					ProductKey:  productKey,
				}
				bucketSlices[idx][sKey] = sl
			}
			sl.EstimatedHours += row.EstimatedHours
			sl.RequestedHours += row.RequestedHours
			addFloatOutcome(&sl.Completed, &sl.Failed, &sl.Expired, &sl.Active, row.Outcome, row.JobCount)
		}
	}

	for _, qRow := range resp.BucketQueueRows {
		idx := qRow.BucketIndex
		if idx < 0 || idx >= nBuckets {
			continue
		}
		timeline[idx].QueueSamples = qRow.SampleCount
		timeline[idx].AvgQueueSeconds = qRow.AvgQueueSeconds
		timeline[idx].P50QueueSeconds = qRow.P50QueueSeconds
		timeline[idx].P90QueueSeconds = qRow.P90QueueSeconds
	}

	roundOutcomes := func(completed float64, failed float64, expired float64, active float64) accapi.UsageJobOutcomeCounts {
		return accapi.UsageJobOutcomeCounts{
			Completed: int64(math.Round(completed)),
			Failed:    int64(math.Round(failed)),
			Expired:   int64(math.Round(expired)),
			Active:    int64(math.Round(active)),
		}
	}
	countJobs := func(outcomes accapi.UsageJobOutcomeCounts) int64 {
		return outcomes.Completed + outcomes.Failed + outcomes.Expired + outcomes.Active
	}

	for i := 0; i < nBuckets; i++ {
		slicesList := make([]accapi.UsageJobSliceBucketValue, 0, len(bucketSlices[i]))
		for _, sl := range bucketSlices[i] {
			outcomes := roundOutcomes(sl.Completed, sl.Failed, sl.Expired, sl.Active)
			if countJobs(outcomes) == 0 && sl.EstimatedHours <= 0 && sl.RequestedHours <= 0 {
				continue
			}
			slicesList = append(slicesList, accapi.UsageJobSliceBucketValue{
				Application:    sl.Application,
				ProductKey:     sl.ProductKey,
				EstimatedHours: sl.EstimatedHours,
				RequestedHours: sl.RequestedHours,
				Outcomes:       outcomes,
			})
		}
		slices.SortFunc(slicesList, func(a, b accapi.UsageJobSliceBucketValue) int {
			if c := cmp.Compare(a.Application, b.Application); c != 0 {
				return c
			}
			return cmp.Compare(a.ProductKey, b.ProductKey)
		})
		timeline[i].BySlice = slicesList

	}

	applications := make([]accapi.UsageJobApplicationSummary, 0, len(appMap))
	for _, app := range appMap {
		outcomes := roundOutcomes(app.Completed, app.Failed, app.Expired, app.Active)

		type prodCount struct {
			Name  string
			Count float64
		}
		topList := make([]prodCount, 0, len(app.ProductJobs))
		for pName, cnt := range app.ProductJobs {
			if cnt > 0 {
				topList = append(topList, prodCount{Name: pName, Count: cnt})
			}
		}
		slices.SortFunc(topList, func(a, b prodCount) int {
			if c := cmp.Compare(b.Count, a.Count); c != 0 {
				return c
			}
			return cmp.Compare(a.Name, b.Name)
		})
		topProducts := make([]string, 0, min(3, len(topList)))
		for i := 0; i < len(topList) && i < 3; i++ {
			topProducts = append(topProducts, topList[i].Name)
		}

		applications = append(applications, accapi.UsageJobApplicationSummary{
			Application:    app.Application,
			EstimatedHours: app.EstimatedHours,
			Outcomes:       outcomes,
			TopProductKeys: topProducts,
		})
	}
	slices.SortFunc(applications, func(a, b accapi.UsageJobApplicationSummary) int {
		if c := cmp.Compare(b.EstimatedHours, a.EstimatedHours); c != 0 {
			return c
		}
		if c := cmp.Compare(countJobs(b.Outcomes), countJobs(a.Outcomes)); c != 0 {
			return c
		}
		return cmp.Compare(a.Application, b.Application)
	})

	queueByProd := make(map[prodKey]orcapi.JobUsageProductQueueRow, len(resp.ProductQueueRows))
	for _, qRow := range resp.ProductQueueRows {
		queueByProd[prodKey{Product: qRow.Product, Provider: qRow.Provider}] = qRow
	}

	products := make([]accapi.UsageJobProductSummary, 0, len(prodMap))
	for pKey, prod := range prodMap {
		outcomes := roundOutcomes(prod.Completed, prod.Failed, prod.Expired, prod.Active)
		qRow := queueByProd[pKey]
		products = append(products, accapi.UsageJobProductSummary{
			Product:         prod.Product,
			Provider:        prod.Provider,
			Cpu:             qRow.Cpu,
			Gpu:             qRow.Gpu,
			MemoryInGigs:    qRow.MemoryInGigs,
			Jobs:            countJobs(outcomes),
			EstimatedHours:  prod.EstimatedHours,
			RequestedHours:  prod.RequestedHours,
			QueueSamples:    qRow.SampleCount,
			AvgQueueSeconds: qRow.AvgQueueSeconds,
			P50QueueSeconds: qRow.P50QueueSeconds,
			P90QueueSeconds: qRow.P90QueueSeconds,
			MaxQueueSeconds: qRow.MaxQueueSeconds,
		})
	}
	slices.SortFunc(products, func(a, b accapi.UsageJobProductSummary) int {
		if c := cmp.Compare(b.EstimatedHours, a.EstimatedHours); c != 0 {
			return c
		}
		if c := cmp.Compare(b.Jobs, a.Jobs); c != 0 {
			return c
		}
		if c := cmp.Compare(a.Product, b.Product); c != 0 {
			return c
		}
		return cmp.Compare(a.Provider, b.Provider)
	})

	return accapi.UsageJobBreakdown{
		Available:              true,
		BucketDurationMs:       bucketDur.Milliseconds(),
		CoFundedWorkspaceCount: resp.CoFundedWorkspaceCount,
		Applications:           applications,
		Products:               products,
		Timeline:               timeline,
	}
}
