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

type usageInferenceNodeSnapshot struct {
	OwnerRef              string
	ParentIds             []AccWalletId
	HasRootAlloc          bool
	LiveParentTreeUsage   map[AccWalletId]int64
	LiveParentActiveQuota map[AccWalletId]int64
}

type usageInferenceProviderAttribution struct {
	ExclusiveOwners map[string]util.Empty
	DailyWeights    map[string]map[string]float64
}

func usageResolveInferenceProviderId(providerId string) string {
	isScenarioProvider := providerId == "usagegen" || providerId == "usagegen-alt"
	if util.DevelopmentModeEnabled() && accountingScenarioLauncherCore() && isScenarioProvider {
		return "k8s"
	}
	return providerId
}

func usageBuildInferenceBreakdown(
	selectedWallets []usageWalletWithScale,
	pinnedChildren []string,
	startTime time.Time,
	endTime time.Time,
	bucketStarts []time.Time,
	bucketDur time.Duration,
) util.Option[accapi.UsageInferenceBreakdown] {
	var inferenceWallets []usageWalletWithScale
	for _, w := range selectedWallets {
		if w.Live.Category.ProductType == accapi.ProductTypeInference {
			inferenceWallets = append(inferenceWallets, w)
		}
	}
	if len(inferenceWallets) == 0 || accGlobals.TestingEnabled {
		return util.OptNone[accapi.UsageInferenceBreakdown]()
	}

	startDay := util.StartOfDayUTC(startTime)
	endDay := util.StartOfDayUTC(endTime)
	startDateStr := startDay.Format("2006-01-02")
	endDateStr := endDay.Format("2006-01-02")

	pinnedSet := map[string]util.Empty{}
	for _, p := range pinnedChildren {
		pinnedSet[p] = util.Empty{}
	}

	attrByProvider := map[string]*usageInferenceProviderAttribution{}
	for _, w := range inferenceWallets {
		providerId := usageResolveInferenceProviderId(w.Live.Category.Provider)
		if providerId == "" {
			continue
		}
		attr, ok := attrByProvider[providerId]
		if !ok {
			attr = &usageInferenceProviderAttribution{
				ExclusiveOwners: map[string]util.Empty{},
				DailyWeights:    map[string]map[string]float64{},
			}
			attrByProvider[providerId] = attr
		}
		usageCollectWalletInferenceAttribution(w, pinnedSet, startDay, endDay, attr)
	}

	if len(attrByProvider) == 0 {
		return util.OptNone[accapi.UsageInferenceBreakdown]()
	}

	providerIds := make([]string, 0, len(attrByProvider))
	for pId := range attrByProvider {
		providerIds = append(providerIds, pId)
	}
	slices.Sort(providerIds)

	var allRows []orcapi.InferenceUsageBreakdownDailyRow
	totalCoFunded := 0
	anySucceeded := false
	anyAttempted := false

	for _, providerId := range providerIds {
		attr := attrByProvider[providerId]
		exclusiveOwners := make([]string, 0, len(attr.ExclusiveOwners))
		for owner := range attr.ExclusiveOwners {
			exclusiveOwners = append(exclusiveOwners, owner)
		}
		slices.Sort(exclusiveOwners)

		var dailyWeights []orcapi.InferenceUsageDailyWeight
		for owner, dayMap := range attr.DailyWeights {
			if _, isExclusive := attr.ExclusiveOwners[owner]; isExclusive {
				continue
			}
			for dayStr, weight := range dayMap {
				if weight <= 1e-6 {
					continue
				}
				if weight > 1.0 {
					weight = 1.0
				}
				dailyWeights = append(dailyWeights, orcapi.InferenceUsageDailyWeight{
					Owner:  owner,
					Day:    dayStr,
					Weight: weight,
				})
			}
		}

		if len(exclusiveOwners) == 0 && len(dailyWeights) == 0 {
			anySucceeded = true
			continue
		}

		slices.SortFunc(dailyWeights, func(a, b orcapi.InferenceUsageDailyWeight) int {
			if c := cmp.Compare(a.Owner, b.Owner); c != 0 {
				return c
			}
			return cmp.Compare(a.Day, b.Day)
		})

		anyAttempted = true
		resp, err := orcapi.InferenceUsageBreakdownInternal.Invoke(orcapi.InferenceUsageBreakdownInternalRequest{
			ProviderId:      providerId,
			StartDate:       startDateStr,
			EndDate:         endDateStr,
			ExclusiveOwners: exclusiveOwners,
			DailyWeights:    dailyWeights,
		})
		if err != nil {
			log.Warn("Could not retrieve inference usage breakdown from provider %s: %s", providerId, err.Error())
			continue
		}
		anySucceeded = true
		totalCoFunded += resp.CoFundedWorkspaceCount
		allRows = append(allRows, resp.Rows...)
	}

	if anyAttempted && !anySucceeded {
		return util.OptValue(accapi.UsageInferenceBreakdown{
			Available: false,
			Models:    []accapi.UsageInferenceModelSummary{},
			Timeline:  []accapi.UsageInferenceTimelineBucket{},
		})
	}

	return util.OptValue(usageAssembleInferenceBreakdown(
		allRows,
		totalCoFunded,
		startDay,
		endDay,
		bucketStarts,
		bucketDur,
		startTime,
		endTime,
	))
}

func usageCollectWalletInferenceAttribution(
	w usageWalletWithScale,
	pinnedSet map[string]util.Empty,
	startDay time.Time,
	endDay time.Time,
	out *usageInferenceProviderAttribution,
) {
	catId := accapi.ProductCategoryIdV2{
		Name:     w.Live.Category.Name,
		Provider: w.Live.Category.Provider,
	}

	accGlobals.Mu.RLock()
	b := accGlobals.BucketsByCategory[catId]
	if b == nil {
		accGlobals.Mu.RUnlock()
		return
	}
	b.Mu.RLock()

	rootWallet := b.WalletsById[w.Live.WalletId]
	if rootWallet == nil {
		b.Mu.RUnlock()
		accGlobals.Mu.RUnlock()
		return
	}

	rootOwnerRef := ""
	if rootOwner := accGlobals.OwnersById[rootWallet.OwnedBy]; rootOwner != nil {
		rootOwnerRef = rootOwner.Reference
	}

	hasPinnedFilter := len(pinnedSet) > 0
	_, localPinned := pinnedSet[""]
	includeRootLocal := !hasPinnedFilter || localPinned

	allowedDirectSet := map[AccWalletId]bool{}
	var bfsQueue []AccWalletId
	descendants := map[AccWalletId]*usageInferenceNodeSnapshot{}

	for childId := range rootWallet.ChildrenUsage {
		childWallet := b.WalletsById[childId]
		if childWallet == nil || childWallet.Id == rootWallet.Id {
			continue
		}
		group := childWallet.AllocationsByParent[rootWallet.Id]
		if group == nil {
			continue
		}
		edgeRelevant := lInternalGroupHasCommittedAllocation(b, group) || group.TreeUsage > 0
		if !edgeRelevant {
			continue
		}
		childOwner := accGlobals.OwnersById[childWallet.OwnedBy]
		if childOwner == nil || childOwner.Reference == "" {
			continue
		}
		if hasPinnedFilter {
			if _, isPinned := pinnedSet[childOwner.Reference]; !isPinned {
				continue
			}
		}
		allowedDirectSet[childId] = true
		bfsQueue = append(bfsQueue, childId)
	}

	for len(bfsQueue) > 0 {
		currId := bfsQueue[0]
		bfsQueue = bfsQueue[1:]
		if _, visited := descendants[currId]; visited {
			continue
		}
		currWallet := b.WalletsById[currId]
		if currWallet == nil {
			continue
		}
		currOwner := accGlobals.OwnersById[currWallet.OwnedBy]
		if currOwner == nil || currOwner.Reference == "" {
			continue
		}

		snap := &usageInferenceNodeSnapshot{
			OwnerRef:              currOwner.Reference,
			LiveParentTreeUsage:   map[AccWalletId]int64{},
			LiveParentActiveQuota: map[AccWalletId]int64{},
		}

		for parentId, group := range currWallet.AllocationsByParent {
			if group == nil {
				continue
			}
			edgeRelevant := lInternalGroupHasCommittedAllocation(b, group) || group.TreeUsage > 0
			if !edgeRelevant {
				continue
			}
			activeQuota := lInternalGroupTotalQuotaFromActiveAllocations(b, group)
			snap.LiveParentTreeUsage[parentId] = group.TreeUsage
			snap.LiveParentActiveQuota[parentId] = activeQuota
			if parentId == internalGraphRoot {
				snap.HasRootAlloc = true
			} else {
				snap.ParentIds = append(snap.ParentIds, parentId)
			}
		}

		descendants[currId] = snap

		for childId := range currWallet.ChildrenUsage {
			if childId == rootWallet.Id {
				continue
			}
			if _, visited := descendants[childId]; visited {
				continue
			}
			childWallet := b.WalletsById[childId]
			if childWallet == nil {
				continue
			}
			group := childWallet.AllocationsByParent[currWallet.Id]
			if group == nil {
				continue
			}
			edgeRelevant := lInternalGroupHasCommittedAllocation(b, group) || group.TreeUsage > 0
			if !edgeRelevant {
				continue
			}
			bfsQueue = append(bfsQueue, childId)
		}
	}

	rootWalletId := rootWallet.Id
	b.Mu.RUnlock()
	accGlobals.Mu.RUnlock()

	if includeRootLocal && rootOwnerRef != "" {
		out.ExclusiveOwners[rootOwnerRef] = util.Empty{}
	}

	if len(descendants) == 0 {
		return
	}

	isExclusive := make(map[AccWalletId]bool, len(descendants))
	for id := range descendants {
		isExclusive[id] = true
	}

	for {
		changed := false
		for id, node := range descendants {
			if !isExclusive[id] {
				continue
			}
			if node.HasRootAlloc || len(node.ParentIds) == 0 {
				isExclusive[id] = false
				changed = true
				continue
			}
			for _, parentId := range node.ParentIds {
				parentInScope := (parentId == rootWalletId && allowedDirectSet[id]) || isExclusive[parentId]
				if !parentInScope {
					isExclusive[id] = false
					changed = true
					break
				}
			}
		}
		if !changed {
			break
		}
	}

	var coFundedIds []AccWalletId
	for id, node := range descendants {
		if isExclusive[id] {
			out.ExclusiveOwners[node.OwnerRef] = util.Empty{}
		} else {
			coFundedIds = append(coFundedIds, id)
		}
	}

	if len(coFundedIds) == 0 {
		return
	}

	type walletEdgeKey struct {
		Parent AccWalletId
		Child  AccWalletId
	}

	coFundedSet := make(map[AccWalletId]bool, len(coFundedIds))
	neededParentReports := map[AccWalletId]util.Empty{}
	neededRootAllocWallets := map[AccWalletId]util.Empty{}
	for _, id := range coFundedIds {
		coFundedSet[id] = true
		node := descendants[id]
		for _, parentId := range node.ParentIds {
			neededParentReports[parentId] = util.Empty{}
		}
		if node.HasRootAlloc {
			neededRootAllocWallets[id] = util.Empty{}
		}
	}

	edgeSpendByDay := map[string]map[walletEdgeKey]int64{}
	periodEdgeSpend := map[walletEdgeKey]int64{}
	hasV2Day := map[string]bool{}

	for parentId := range neededParentReports {
		reports := usageRetrieveHistoricReports(startDay, endDay, parentId)
		for _, r := range reports {
			if r.Format < usageReportFormatV2 {
				continue
			}
			rDay := util.StartOfDayUTC(r.ValidFrom).Format("2006-01-02")
			hasV2Day[rDay] = true
			for _, ev := range r.UsageOverTime.ChildEvents {
				if !ev.Child.Present || ev.UsageChange <= 0 {
					continue
				}
				childId := ev.Child.Value
				if !coFundedSet[childId] {
					continue
				}
				day := util.StartOfDayUTC(ev.Timestamp)
				if day.Before(startDay) || day.After(endDay) {
					continue
				}
				dayStr := day.Format("2006-01-02")
				dayEdges, ok := edgeSpendByDay[dayStr]
				if !ok {
					dayEdges = map[walletEdgeKey]int64{}
					edgeSpendByDay[dayStr] = dayEdges
				}
				edge := walletEdgeKey{Parent: parentId, Child: childId}
				dayEdges[edge] += ev.UsageChange
				periodEdgeSpend[edge] += ev.UsageChange
			}
		}
	}

	walletTotalSpendByDay := map[string]map[AccWalletId]int64{}
	periodWalletTotalSpend := map[AccWalletId]int64{}
	for walletId := range neededRootAllocWallets {
		reports := usageRetrieveHistoricReports(startDay, endDay, walletId)
		for _, r := range reports {
			if r.Format < usageReportFormatV2 {
				continue
			}
			for _, pt := range r.UsageOverTime.WalletSeries {
				if pt.UsageChange <= 0 {
					continue
				}
				day := util.StartOfDayUTC(pt.Timestamp)
				if day.Before(startDay) || day.After(endDay) {
					continue
				}
				dayStr := day.Format("2006-01-02")
				dayTotals, ok := walletTotalSpendByDay[dayStr]
				if !ok {
					dayTotals = map[AccWalletId]int64{}
					walletTotalSpendByDay[dayStr] = dayTotals
				}
				dayTotals[walletId] += pt.UsageChange
				periodWalletTotalSpend[walletId] += pt.UsageChange
			}
		}
	}

	computeShares := func(
		getEdgeSpend func(parentId AccWalletId, childId AccWalletId) float64,
		getRootSpend func(childId AccWalletId, sumRealParents float64) float64,
		fallback map[AccWalletId]float64,
	) map[AccWalletId]float64 {
		shares := make(map[AccWalletId]float64, len(descendants))
		for id := range descendants {
			if isExclusive[id] {
				shares[id] = 1.0
			}
		}

		for iter := 0; iter < 12; iter++ {
			maxDiff := 0.0
			for _, id := range coFundedIds {
				node := descendants[id]
				inTree := 0.0
				total := 0.0
				for _, parentId := range node.ParentIds {
					sp := getEdgeSpend(parentId, id)
					if sp <= 0 {
						continue
					}
					total += sp
					if parentId == rootWalletId && allowedDirectSet[id] {
						inTree += sp
					} else if parentShare, ok := shares[parentId]; ok {
						inTree += sp * parentShare
					}
				}
				if node.HasRootAlloc {
					rootSp := getRootSpend(id, total)
					if rootSp > 0 {
						total += rootSp
					}
				}

				next := 0.0
				if total > 0 {
					next = inTree / total
					if next < 0 {
						next = 0
					}
					if next > 1 {
						next = 1
					}
				} else if fallback != nil {
					next = fallback[id]
				}

				diff := math.Abs(next - shares[id])
				if diff > maxDiff {
					maxDiff = diff
				}
				shares[id] = next
			}
			if maxDiff < 1e-6 {
				break
			}
		}

		return shares
	}

	liveActiveShares := computeShares(
		func(parentId AccWalletId, childId AccWalletId) float64 {
			return float64(descendants[childId].LiveParentActiveQuota[parentId])
		},
		func(childId AccWalletId, _ float64) float64 {
			return float64(descendants[childId].LiveParentActiveQuota[internalGraphRoot])
		},
		nil,
	)

	liveUsageShares := computeShares(
		func(parentId AccWalletId, childId AccWalletId) float64 {
			return float64(descendants[childId].LiveParentTreeUsage[parentId])
		},
		func(childId AccWalletId, _ float64) float64 {
			return float64(descendants[childId].LiveParentTreeUsage[internalGraphRoot])
		},
		liveActiveShares,
	)

	periodShares := computeShares(
		func(parentId AccWalletId, childId AccWalletId) float64 {
			return float64(periodEdgeSpend[walletEdgeKey{Parent: parentId, Child: childId}])
		},
		func(childId AccWalletId, sumRealParents float64) float64 {
			total := float64(periodWalletTotalSpend[childId])
			if total > sumRealParents {
				return total - sumRealParents
			}
			return 0
		},
		liveActiveShares,
	)

	for cur := startDay; !cur.After(endDay); cur = cur.AddDate(0, 0, 1) {
		dayStr := cur.Format("2006-01-02")
		dayEdges := edgeSpendByDay[dayStr]
		dayTotals := walletTotalSpendByDay[dayStr]

		fallback := periodShares
		if !hasV2Day[dayStr] {
			fallback = liveUsageShares
		}

		dayShares := computeShares(
			func(parentId AccWalletId, childId AccWalletId) float64 {
				if dayEdges == nil {
					return 0
				}
				return float64(dayEdges[walletEdgeKey{Parent: parentId, Child: childId}])
			},
			func(childId AccWalletId, sumRealParents float64) float64 {
				if dayTotals == nil {
					return 0
				}
				total := float64(dayTotals[childId])
				if total > sumRealParents {
					return total - sumRealParents
				}
				return 0
			},
			fallback,
		)

		for _, id := range coFundedIds {
			wShare := dayShares[id]
			if wShare <= 1e-6 {
				continue
			}
			ownerRef := descendants[id].OwnerRef
			dayMap, ok := out.DailyWeights[ownerRef]
			if !ok {
				dayMap = map[string]float64{}
				out.DailyWeights[ownerRef] = dayMap
			}
			if wShare > dayMap[dayStr] {
				dayMap[dayStr] = wShare
			}
		}
	}
}

func usageAssembleInferenceBreakdown(
	rows []orcapi.InferenceUsageBreakdownDailyRow,
	coFundedCount int,
	startDay time.Time,
	endDay time.Time,
	bucketStarts []time.Time,
	bucketDur time.Duration,
	startTime time.Time,
	endTime time.Time,
) accapi.UsageInferenceBreakdown {
	nBuckets := len(bucketStarts)
	timeline := make([]accapi.UsageInferenceTimelineBucket, nBuckets)
	bucketModels := make([]map[string]*accapi.UsageInferenceModelBucketValue, nBuckets)
	for i, bStart := range bucketStarts {
		timeline[i] = accapi.UsageInferenceTimelineBucket{
			Timestamp:    fndapi.Timestamp(usageBucketStart(bStart, startTime)),
			EndTimestamp: fndapi.Timestamp(usageBucketEnd(bStart, bucketDur, endTime)),
			ByModel:      []accapi.UsageInferenceModelBucketValue{},
		}
		bucketModels[i] = map[string]*accapi.UsageInferenceModelBucketValue{}
	}

	modelTotals := map[string]*accapi.UsageInferenceModelSummary{}
	hasUnpriced := false
	hasVideoEstimate := false

	for _, row := range rows {
		dayTime, err := time.Parse("2006-01-02", row.Day)
		if err != nil {
			continue
		}
		dayTime = dayTime.UTC()

		rowCost := row.EstimatedCachedInputCost +
			row.EstimatedInputCost +
			row.EstimatedOutputCost +
			row.EstimatedImageCost +
			row.EstimatedAudioCost +
			row.EstimatedVideoCost

		if !row.ModelPriced {
			hasUnpriced = true
		}
		if row.VideoUsedDefaultTierPricing {
			hasVideoEstimate = true
		}

		summary, ok := modelTotals[row.Model]
		if !ok {
			title := row.ModelTitle
			if title == "" {
				title = row.Model
			}
			summary = &accapi.UsageInferenceModelSummary{
				Model:  row.Model,
				Title:  title,
				Priced: row.ModelPriced,
			}
			modelTotals[row.Model] = summary
		} else if row.ModelPriced {
			summary.Priced = true
			if row.ModelTitle != "" {
				summary.Title = row.ModelTitle
			}
		}

		summary.EstimatedCost += rowCost

		summary.Tokens.CachedInput += row.CachedInputTokens
		summary.Tokens.Input += row.InputTokens
		summary.Tokens.Output += row.OutputTokens

		summary.ImageCount += row.ImageCount
		summary.ImagePixels += row.ImagePixels
		summary.AudioMilliseconds += row.AudioMilliseconds
		summary.VideoSeconds += row.VideoSeconds

		if nBuckets > 0 {
			idx := usageFindBucketIndex(bucketStarts, bucketDur, dayTime)
			if idx < 0 {
				if !dayTime.After(startDay) {
					idx = 0
				} else if !dayTime.Before(endDay) {
					idx = nBuckets - 1
				}
			}
			if idx >= 0 && idx < nBuckets {
				entry, hasEntry := bucketModels[idx][row.Model]
				if !hasEntry {
					entry = &accapi.UsageInferenceModelBucketValue{Model: row.Model}
					bucketModels[idx][row.Model] = entry
				}
				entry.EstimatedCost.CachedInput += row.EstimatedCachedInputCost
				entry.EstimatedCost.Input += row.EstimatedInputCost
				entry.EstimatedCost.Output += row.EstimatedOutputCost
				entry.EstimatedCost.Image += row.EstimatedImageCost
				entry.EstimatedCost.Audio += row.EstimatedAudioCost
				entry.EstimatedCost.Video += row.EstimatedVideoCost
				entry.Tokens.CachedInput += row.CachedInputTokens
				entry.Tokens.Input += row.InputTokens
				entry.Tokens.Output += row.OutputTokens
			}
		}
	}

	models := make([]accapi.UsageInferenceModelSummary, 0, len(modelTotals))
	for _, summary := range modelTotals {
		models = append(models, *summary)
	}
	slices.SortFunc(models, func(a, b accapi.UsageInferenceModelSummary) int {
		if c := cmp.Compare(b.EstimatedCost, a.EstimatedCost); c != 0 {
			return c
		}
		if c := cmp.Compare(usageInferenceTotalTokens(b.Tokens), usageInferenceTotalTokens(a.Tokens)); c != 0 {
			return c
		}
		return cmp.Compare(a.Model, b.Model)
	})

	for i := 0; i < nBuckets; i++ {
		byModel := make([]accapi.UsageInferenceModelBucketValue, 0, len(bucketModels[i]))
		for _, entry := range bucketModels[i] {
			if entry.EstimatedCost != (accapi.UsageInferenceCostBreakdown{}) || entry.Tokens != (accapi.UsageInferenceTokenBreakdown{}) {
				byModel = append(byModel, *entry)
			}
		}
		slices.SortFunc(byModel, func(a, b accapi.UsageInferenceModelBucketValue) int {
			return cmp.Compare(a.Model, b.Model)
		})
		timeline[i].ByModel = byModel
	}

	return accapi.UsageInferenceBreakdown{
		Available:              true,
		BucketDurationMs:       bucketDur.Milliseconds(),
		CoFundedWorkspaceCount: coFundedCount,
		HasUnpricedModels:      hasUnpriced,
		HasVideoEstimate:       hasVideoEstimate,
		Models:                 models,
		Timeline:               timeline,
	}
}

func usageInferenceTotalTokens(tokens accapi.UsageInferenceTokenBreakdown) int64 {
	return tokens.CachedInput + tokens.Input + tokens.Output
}
