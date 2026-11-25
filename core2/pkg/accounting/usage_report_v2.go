package accounting

import (
	"cmp"
	"fmt"
	"slices"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type usageAllocWindow struct {
	Start        time.Time
	End          time.Time
	Quota        int64
	RetiredQuota int64
	RetiredUsage int64
	Retired      bool
	Active       bool
}

type usageSubAllocatorLive struct {
	ActiveDownstreamCount     int
	DownstreamActiveAllocated int64
	DirectUsage               int64
	DownstreamUsage           int64
}

type usageLiveChildInfo struct {
	ChildWalletId     AccWalletId
	ChildKey          string
	TreeUsage         int64
	ActiveUsage       int64
	ActiveQuota       int64
	Quota             int64
	RetiredUsage      int64
	NextExpiration    util.Option[time.Time]
	ActiveAllocations []usageAllocWindow
	IsSubAllocator    bool
	SubAllocator      usageSubAllocatorLive
}

type usageWalletLiveState struct {
	WalletId                 AccWalletId
	Category                 accapi.ProductCategory
	TotalUsage               int64
	LocalUsage               int64
	ActiveUsage              int64
	RetiredUsage             int64
	Quota                    int64
	ActiveQuota              int64
	MaxUsable                int64
	TotalAllocated           int64
	ActiveAllocated          int64
	NextMeaningfulExpiration util.Option[time.Time]
	IncomingAllocations      []usageAllocWindow
	ChildrenLoaded           bool
	Children                 map[string]usageLiveChildInfo
	ChildrenByWalletId       map[AccWalletId]string
}

type usageUnitGroupDescriptor struct {
	Key             string
	BaseTitle       string
	AccountingModel accapi.AccountingModel
	ProductType     accapi.ProductType
	Unit            accapi.AccountingUnit
}

type usageUnitGroupEntry struct {
	Descriptor     usageUnitGroupDescriptor
	GroupFrequency accapi.AccountingFrequency
	Wallets        []usageWalletWithScale
}

type usageWalletWithScale struct {
	Live        usageWalletLiveState
	ScaleFactor int64
	Reports     []internalUsageReport
}

type usageChildAccumulator struct {
	Child              string
	IsLocal            bool
	IsSubAllocator     bool
	PeriodUsage        int64
	CurrentUsage       int64
	PeakUsage          int64
	ActiveUsage        int64
	ActiveQuota        int64
	Quota              int64
	HasQuotaSample     bool
	ActiveQuotaUnknown bool
	QuotaUnknown       bool
	RetiredUsage       int64
	TotalUsage         int64
	NextExpiration     util.Option[time.Time]
	ActiveAllocations  []usageAllocWindow
	SubAllocator       usageSubAllocatorLive
}

func usageInitV2Handlers() {
	accapi.UsageOverviewRetrieve.Handler(usageHandleOverviewRetrieve)
	accapi.UsageDetailRetrieve.Handler(usageHandleDetailRetrieve)
}

func usageResolveActorReference(info rpc.RequestInfo) string {
	reference := string(info.Actor.Project.Value)
	if !info.Actor.Project.Present || reference == "" {
		reference = info.Actor.Username
	}
	return reference
}

func usageNormalizeRequestWindow(startMs uint64, endMs uint64, now time.Time) (time.Time, time.Time) {
	startTime := fndapi.TimeFromUnixMilli(startMs).Time().UTC()
	endTime := fndapi.TimeFromUnixMilli(endMs).Time().UTC()
	if endTime.After(now) {
		endTime = now
	}
	if endTime.Before(startTime) {
		endTime = startTime
	}
	return startTime, endTime
}

func usageDescribeUnitGroup(cat accapi.ProductCategory) usageUnitGroupDescriptor {
	desiredFreq := cat.AccountingFrequency
	if cat.AccountingFrequency.IsPeriodic() {
		if cat.ProductType == accapi.ProductTypeCompute {
			desiredFreq = accapi.AccountingFrequencyPeriodicHour
		} else {
			desiredFreq = accapi.AccountingFrequencyPeriodicDay
		}
	}

	title := cat.AccountingUnit.NamePlural
	if title == "" {
		title = cat.AccountingUnit.Name
	}
	if cat.AccountingFrequency.IsPeriodic() && cat.AccountingUnit.DisplayFrequencySuffix {
		suffix := "days"
		if desiredFreq == accapi.AccountingFrequencyPeriodicHour {
			suffix = "hours"
		}
		title = fmt.Sprintf("%s-%s", cat.AccountingUnit.Name, suffix)
	}

	key := fmt.Sprintf(
		"%s/%s/%s/%t/%t/%s/%s",
		cat.AccountingModel,
		cat.AccountingUnit.Name,
		desiredFreq,
		cat.AccountingUnit.FloatingPoint,
		cat.AccountingUnit.DisplayFrequencySuffix,
		cat.Provider,
		cat.ProductType,
	)

	return usageUnitGroupDescriptor{
		Key:             key,
		BaseTitle:       title,
		AccountingModel: cat.AccountingModel,
		ProductType:     cat.ProductType,
		Unit:            cat.AccountingUnit,
	}
}

func usageProductKey(cat accapi.ProductCategory) string {
	return fmt.Sprintf("%s/%s", cat.Provider, cat.Name)
}

func usageProductIdKey(id accapi.ProductCategoryIdV2) string {
	return fmt.Sprintf("%s/%s", id.Provider, id.Name)
}

func usageProductTypePriority(productType accapi.ProductType) int {
	switch productType {
	case accapi.ProductTypeCompute:
		return 0
	case accapi.ProductTypeStorage:
		return 1
	case accapi.ProductTypeInference:
		return 2
	case accapi.ProductTypeNetworkIp:
		return 3
	case accapi.ProductTypeLicense:
		return 4
	case accapi.ProductTypeIngress:
		return 5
	case accapi.ProductTypePrivateNetwork:
		return 6
	case accapi.ProductTypePrivateNetworkIp:
		return 7
	default:
		return 8
	}
}

func usageFindNextMeaningfulExpiration(b *internalBucket, group *internalGroup) util.Option[time.Time] {
	activeQuota := lInternalGroupTotalQuotaFromActiveAllocations(b, group)
	minMeaningful := int64(float64(activeQuota) * 0.1)
	earliest := util.OptNone[time.Time]()

	for allocId := range group.Allocations {
		alloc := b.AllocationsById[allocId]
		if alloc == nil || !alloc.Committed {
			continue
		}
		if !alloc.Active || alloc.Retired || alloc.Quota <= minMeaningful {
			continue
		}
		if !earliest.Present || alloc.End.Before(earliest.Value) {
			earliest.Set(alloc.End)
		}
	}

	return earliest
}

func usageCollectLiveWalletState(
	now time.Time,
	b *internalBucket,
	walletId AccWalletId,
	includeChildren bool,
) (usageWalletLiveState, bool) {
	accGlobals.Mu.RLock()
	b.Mu.RLock()
	defer b.Mu.RUnlock()
	defer accGlobals.Mu.RUnlock()

	w := b.WalletsById[walletId]
	if w == nil {
		return usageWalletLiveState{}, false
	}

	totalUsage := lInternalWalletTotalUsageInNode(b, w)
	retiredTotal := int64(0)
	if !b.IsCapacityBased() {
		for _, group := range w.AllocationsByParent {
			retiredTotal += lInternalGroupTotalRetired(b, group)
		}
	}
	activeUsage := max(int64(0), totalUsage-retiredTotal)
	if b.IsCapacityBased() {
		activeUsage = totalUsage
	}
	activeQuota := lInternalWalletTotalQuotaFromActiveAllocations(b, w)

	minMeaningful := int64(float64(activeQuota) * 0.1)
	nextExpiry := util.OptNone[time.Time]()
	var incoming []usageAllocWindow
	for _, group := range w.AllocationsByParent {
		for allocId := range group.Allocations {
			alloc := b.AllocationsById[allocId]
			if alloc == nil || !alloc.Committed {
				continue
			}
			incoming = append(incoming, usageAllocWindow{
				Start:        alloc.Start,
				End:          alloc.End,
				Quota:        alloc.Quota,
				RetiredQuota: alloc.RetiredQuota,
				RetiredUsage: alloc.RetiredUsage,
				Retired:      alloc.Retired,
				Active:       alloc.Active,
			})
			if alloc.Active && !alloc.Retired && alloc.Quota > minMeaningful {
				if !nextExpiry.Present || alloc.End.Before(nextExpiry.Value) {
					nextExpiry.Set(alloc.End)
				}
			}
		}
	}

	var children map[string]usageLiveChildInfo
	var childrenByWallet map[AccWalletId]string

	if includeChildren {
		children = make(map[string]usageLiveChildInfo, len(w.ChildrenUsage))
		childrenByWallet = make(map[AccWalletId]string, len(w.ChildrenUsage))

		for childId := range w.ChildrenUsage {
			childWallet := b.WalletsById[childId]
			if childWallet == nil {
				continue
			}
			group := childWallet.AllocationsByParent[w.Id]
			if group == nil || !lInternalGroupHasCommittedAllocation(b, group) {
				continue
			}
			owner := accGlobals.OwnersById[childWallet.OwnedBy]
			if owner == nil || owner.Reference == "" {
				continue
			}

			childRetired := int64(0)
			if !b.IsCapacityBased() {
				childRetired = lInternalGroupTotalRetired(b, group)
			}
			childActiveUsage := max(int64(0), group.TreeUsage-childRetired)
			if b.IsCapacityBased() {
				childActiveUsage = group.TreeUsage
			}
			childActiveQuota := lInternalGroupTotalQuotaFromActiveAllocations(b, group)
			childQuota := lInternalGroupTotalQuotaContributing(b, group)
			childExpiry := usageFindNextMeaningfulExpiration(b, group)

			var childAllocs []usageAllocWindow
			for allocId := range group.Allocations {
				alloc := b.AllocationsById[allocId]
				if alloc == nil || !alloc.Committed || alloc.Retired || !alloc.End.After(now) || alloc.Quota <= 0 {
					continue
				}
				childAllocs = append(childAllocs, usageAllocWindow{
					Start:        alloc.Start,
					End:          alloc.End,
					Quota:        alloc.Quota,
					RetiredQuota: alloc.RetiredQuota,
					RetiredUsage: alloc.RetiredUsage,
					Retired:      alloc.Retired,
					Active:       alloc.Active,
				})
			}

			isSubAllocator := false
			subAgg := usageSubAllocatorLive{}

			for grandChildId := range childWallet.ChildrenUsage {
				grandWallet := b.WalletsById[grandChildId]
				if grandWallet == nil {
					continue
				}
				grandGroup := grandWallet.AllocationsByParent[childWallet.Id]
				if grandGroup == nil || !lInternalGroupHasCommittedAllocation(b, grandGroup) {
					continue
				}
				isSubAllocator = true
				grandActiveQuota := lInternalGroupTotalQuotaFromActiveAllocations(b, grandGroup)
				grandRetired := int64(0)
				if !b.IsCapacityBased() {
					grandRetired = lInternalGroupTotalRetired(b, grandGroup)
				}
				grandActiveUsage := max(int64(0), grandGroup.TreeUsage-grandRetired)
				if b.IsCapacityBased() {
					grandActiveUsage = grandGroup.TreeUsage
				}
				subAgg.DownstreamUsage += grandActiveUsage
				if grandActiveQuota > 0 || grandActiveUsage > 0 {
					subAgg.ActiveDownstreamCount++
					subAgg.DownstreamActiveAllocated += grandActiveQuota
				}
			}

			if b.IsCapacityBased() {
				subAgg.DirectUsage = childWallet.LocalUsage
			} else if childWallet.LocalUsage > 0 {
				subAgg.DirectUsage = min(childWallet.LocalUsage, max(int64(0), childActiveUsage-subAgg.DownstreamUsage))
			}

			childrenByWallet[childId] = owner.Reference
			children[owner.Reference] = usageLiveChildInfo{
				ChildWalletId:     childId,
				ChildKey:          owner.Reference,
				TreeUsage:         group.TreeUsage,
				ActiveUsage:       childActiveUsage,
				ActiveQuota:       childActiveQuota,
				Quota:             childQuota,
				RetiredUsage:      childRetired,
				NextExpiration:    childExpiry,
				ActiveAllocations: childAllocs,
				IsSubAllocator:    isSubAllocator,
				SubAllocator:      subAgg,
			}
		}
	}

	return usageWalletLiveState{
		WalletId:                 walletId,
		Category:                 b.Category,
		TotalUsage:               totalUsage,
		LocalUsage:               w.LocalUsage,
		ActiveUsage:              activeUsage,
		RetiredUsage:             retiredTotal,
		Quota:                    lInternalWalletTotalQuotaContributing(b, w),
		ActiveQuota:              activeQuota,
		MaxUsable:                lInternalMaxUsable(b, now, w),
		TotalAllocated:           lInternalWalletTotalAllocatedContributing(b, w),
		ActiveAllocated:          lUsageWalletActiveAllocated(b, w),
		NextMeaningfulExpiration: nextExpiry,
		IncomingAllocations:      incoming,
		ChildrenLoaded:           includeChildren,
		Children:                 children,
		ChildrenByWalletId:       childrenByWallet,
	}, true
}

func usageBuildActorUnitGroups(
	now time.Time,
	reference string,
	startTime time.Time,
	endTime time.Time,
	includeChildrenForUnitKey string,
	includeChildrenAll bool,
) []*usageUnitGroupEntry {
	if reference == "" {
		return nil
	}

	accGlobals.Mu.RLock()
	owner := accGlobals.OwnersByReference[reference]
	if owner == nil {
		accGlobals.Mu.RUnlock()
		return nil
	}
	ownerId := owner.Id

	type bucketWallet struct {
		Bucket   *internalBucket
		WalletId AccWalletId
		Category accapi.ProductCategory
	}
	var candidates []bucketWallet
	for _, b := range accGlobals.BucketsByCategory {
		if b.Category.FreeToUse {
			continue
		}
		b.Mu.RLock()
		w := b.WalletsByOwner[ownerId]
		if w != nil {
			hasActivity := lInternalWalletTotalUsageInNode(b, w) > 0 || len(w.ChildrenUsage) > 0
			if !hasActivity {
				for _, group := range w.AllocationsByParent {
					if lInternalGroupHasCommittedAllocation(b, group) {
						hasActivity = true
						break
					}
				}
			}
			if hasActivity {
				candidates = append(candidates, bucketWallet{
					Bucket:   b,
					WalletId: w.Id,
					Category: b.Category,
				})
			}
		}
		b.Mu.RUnlock()
	}
	accGlobals.Mu.RUnlock()

	groupsByKey := map[string]*usageUnitGroupEntry{}
	for _, cand := range candidates {
		desc := usageDescribeUnitGroup(cand.Category)
		loadChildren := includeChildrenAll || (includeChildrenForUnitKey != "" && desc.Key == includeChildrenForUnitKey)

		live, ok := usageCollectLiveWalletState(now, cand.Bucket, cand.WalletId, loadChildren)
		if !ok {
			continue
		}

		reports := usageRetrieveHistoricReports(startTime, endTime, cand.WalletId)
		hasAllocOrUsage := len(live.IncomingAllocations) > 0 || live.TotalUsage > 0 || live.TotalAllocated > 0
		if !hasAllocOrUsage {
			continue
		}

		entry, ok := groupsByKey[desc.Key]
		if !ok {
			entry = &usageUnitGroupEntry{
				Descriptor:     desc,
				GroupFrequency: cand.Category.AccountingFrequency,
			}
			groupsByKey[desc.Key] = entry
		} else {
			if cand.Category.AccountingFrequency.IsPeriodic() &&
				entry.GroupFrequency.IsPeriodic() &&
				cand.Category.AccountingFrequency.ToSeconds() < entry.GroupFrequency.ToSeconds() {
				entry.GroupFrequency = cand.Category.AccountingFrequency
			}
			if usageProductTypePriority(cand.Category.ProductType) < usageProductTypePriority(entry.Descriptor.ProductType) {
				entry.Descriptor.ProductType = cand.Category.ProductType
			}
		}

		entry.Wallets = append(entry.Wallets, usageWalletWithScale{
			Live:        live,
			ScaleFactor: 1,
			Reports:     reports,
		})
	}

	result := make([]*usageUnitGroupEntry, 0, len(groupsByKey))
	for _, entry := range groupsByKey {
		for i := range entry.Wallets {
			cat := entry.Wallets[i].Live.Category
			scale := int64(1)
			if cat.AccountingFrequency.IsPeriodic() &&
				entry.GroupFrequency.IsPeriodic() &&
				cat.AccountingUnit.DisplayFrequencySuffix {
				denom := entry.GroupFrequency.ToSeconds()
				if denom > 0 {
					scale = max(int64(1), cat.AccountingFrequency.ToSeconds()/denom)
				}
			}
			entry.Wallets[i].ScaleFactor = scale
		}

		slices.SortFunc(entry.Wallets, func(a, b usageWalletWithScale) int {
			if c := cmp.Compare(a.Live.Category.Provider, b.Live.Category.Provider); c != 0 {
				return c
			}
			if c := cmp.Compare(
				usageProductTypePriority(a.Live.Category.ProductType),
				usageProductTypePriority(b.Live.Category.ProductType),
			); c != 0 {
				return c
			}
			return cmp.Compare(a.Live.Category.Name, b.Live.Category.Name)
		})

		result = append(result, entry)
	}

	titleCounts := map[string]int{}
	for _, entry := range result {
		titleCounts[entry.Descriptor.BaseTitle]++
	}
	for _, entry := range result {
		if titleCounts[entry.Descriptor.BaseTitle] > 1 {
			modelLabel := "Consumption"
			if entry.Descriptor.AccountingModel == accapi.AccountingModelCapacity {
				modelLabel = "Capacity"
			}
			entry.Descriptor.BaseTitle = fmt.Sprintf("%s (%s)", entry.Descriptor.BaseTitle, modelLabel)
		}
	}

	slices.SortFunc(result, func(a, b *usageUnitGroupEntry) int {
		if c := cmp.Compare(
			usageProductTypePriority(a.Descriptor.ProductType),
			usageProductTypePriority(b.Descriptor.ProductType),
		); c != 0 {
			return c
		}
		if c := cmp.Compare(a.Descriptor.AccountingModel, b.Descriptor.AccountingModel); c != 0 {
			return c
		}
		return cmp.Compare(a.Descriptor.BaseTitle, b.Descriptor.BaseTitle)
	})

	return result
}

func usageAllocActiveQuotaAt(allocs []usageAllocWindow, at time.Time) int64 {
	sum := int64(0)
	for _, alloc := range allocs {
		inWindow := (!alloc.Start.After(at)) && at.Before(alloc.End)
		if !inWindow {
			continue
		}
		if alloc.Retired && alloc.RetiredQuota > 0 {
			sum += alloc.RetiredQuota
		} else {
			sum += alloc.Quota
		}
	}
	return sum
}

func usageAllocRetiredUsageAt(allocs []usageAllocWindow, isCapacity bool, at time.Time) int64 {
	if isCapacity {
		return 0
	}
	sum := int64(0)
	for _, alloc := range allocs {
		if alloc.Retired && !alloc.End.After(at) {
			sum += alloc.RetiredUsage
		}
	}
	return sum
}

func usageHydrateWalletSeries(
	w usageWalletWithScale,
	startTime time.Time,
	endTime time.Time,
	now time.Time,
) ([]internalUsageWalletSample, bool) {
	isCapacity := w.Live.Category.AccountingModel == accapi.AccountingModelCapacity
	scale := w.ScaleFactor
	hasData := len(w.Reports) > 0
	var samples []internalUsageWalletSample

	for _, r := range w.Reports {
		if r.Format >= usageReportFormatV2 && len(r.UsageOverTime.WalletSeries) > 0 {
			for _, pt := range r.UsageOverTime.WalletSeries {
				samples = append(samples, internalUsageWalletSample{
					Timestamp:        pt.Timestamp,
					TotalUsage:       pt.TotalUsage * scale,
					LocalUsage:       pt.LocalUsage * scale,
					ActiveUsage:      pt.ActiveUsage * scale,
					RetiredUsage:     pt.RetiredUsage * scale,
					Quota:            pt.Quota * scale,
					ActiveQuota:      pt.ActiveQuota * scale,
					MaxUsable:        pt.MaxUsable * scale,
					ActiveAllocated:  pt.ActiveAllocated * scale,
					UsageChange:      pt.UsageChange * scale,
					LocalUsageChange: pt.LocalUsageChange * scale,
					FlowShift:        pt.FlowShift * scale,
				})
			}
			continue
		}

		startRetired := usageAllocRetiredUsageAt(w.Live.IncomingAllocations, isCapacity, r.ValidFrom)
		if startRetired > r.Kpis.TotalUsageAtStart {
			startRetired = r.Kpis.TotalUsageAtStart
		}
		startActiveQuota := r.Kpis.ActiveQuotaAtStart
		if startActiveQuota == 0 {
			startActiveQuota = usageAllocActiveQuotaAt(w.Live.IncomingAllocations, r.ValidFrom)
		}
		startActiveUsage := r.Kpis.TotalUsageAtStart
		if !isCapacity {
			startActiveUsage = max(int64(0), r.Kpis.TotalUsageAtStart-startRetired)
		}

		samples = append(samples, internalUsageWalletSample{
			Timestamp:       r.ValidFrom,
			TotalUsage:      r.Kpis.TotalUsageAtStart * scale,
			LocalUsage:      r.Kpis.LocalUsageAtStart * scale,
			ActiveUsage:     startActiveUsage * scale,
			RetiredUsage:    startRetired * scale,
			Quota:           r.Kpis.QuotaAtStart * scale,
			ActiveQuota:     startActiveQuota * scale,
			MaxUsable:       r.Kpis.MaxUsableAtStart * scale,
			ActiveAllocated: r.Kpis.TotalAllocatedAtStart * scale,
		})

		for _, pt := range r.UsageOverTime.Absolute {
			retiredAt := usageAllocRetiredUsageAt(w.Live.IncomingAllocations, isCapacity, pt.Timestamp)
			if retiredAt > pt.Usage {
				retiredAt = pt.Usage
			}
			activeQuotaAt := usageAllocActiveQuotaAt(w.Live.IncomingAllocations, pt.Timestamp)
			if activeQuotaAt == 0 && r.Kpis.ActiveQuotaAtEnd > 0 {
				activeQuotaAt = r.Kpis.ActiveQuotaAtEnd
			}
			activeUsageAt := pt.Usage
			if !isCapacity {
				activeUsageAt = max(int64(0), pt.Usage-retiredAt)
			}
			samples = append(samples, internalUsageWalletSample{
				Timestamp:       pt.Timestamp,
				TotalUsage:      pt.Usage * scale,
				LocalUsage:      r.Kpis.LocalUsageAtEnd * scale,
				ActiveUsage:     activeUsageAt * scale,
				RetiredUsage:    retiredAt * scale,
				Quota:           pt.Quota * scale,
				ActiveQuota:     activeQuotaAt * scale,
				MaxUsable:       r.Kpis.MaxUsableAtEnd * scale,
				ActiveAllocated: r.Kpis.TotalAllocatedAtEnd * scale,
			})
		}

		endTs := r.ValidUntil.GetOrDefault(r.ValidFrom.Add(24 * time.Hour))
		if endTs.After(now) {
			endTs = now
		}
		endRetired := usageAllocRetiredUsageAt(w.Live.IncomingAllocations, isCapacity, endTs)
		if endRetired > r.Kpis.TotalUsageAtEnd {
			endRetired = r.Kpis.TotalUsageAtEnd
		}
		endActiveQuota := r.Kpis.ActiveQuotaAtEnd
		if endActiveQuota == 0 {
			endActiveQuota = usageAllocActiveQuotaAt(w.Live.IncomingAllocations, endTs)
		}
		endActiveUsage := r.Kpis.TotalUsageAtEnd
		if !isCapacity {
			endActiveUsage = max(int64(0), r.Kpis.TotalUsageAtEnd-endRetired)
		}
		samples = append(samples, internalUsageWalletSample{
			Timestamp:       endTs,
			TotalUsage:      r.Kpis.TotalUsageAtEnd * scale,
			LocalUsage:      r.Kpis.LocalUsageAtEnd * scale,
			ActiveUsage:     endActiveUsage * scale,
			RetiredUsage:    endRetired * scale,
			Quota:           r.Kpis.QuotaAtEnd * scale,
			ActiveQuota:     endActiveQuota * scale,
			MaxUsable:       r.Kpis.MaxUsableAtEnd * scale,
			ActiveAllocated: r.Kpis.TotalAllocatedAtEnd * scale,
		})
	}

	if !hasData {
		return nil, false
	}

	includeLive := !endTime.Before(now.Add(-4 * time.Hour))
	if includeLive {
		ts := endTime
		if ts.After(now) {
			ts = now
		}
		samples = append(samples, internalUsageWalletSample{
			Timestamp:       ts,
			TotalUsage:      w.Live.TotalUsage * scale,
			LocalUsage:      w.Live.LocalUsage * scale,
			ActiveUsage:     w.Live.ActiveUsage * scale,
			RetiredUsage:    w.Live.RetiredUsage * scale,
			Quota:           w.Live.Quota * scale,
			ActiveQuota:     w.Live.ActiveQuota * scale,
			MaxUsable:       w.Live.MaxUsable * scale,
			ActiveAllocated: w.Live.ActiveAllocated * scale,
		})
	}

	slices.SortFunc(samples, func(a, b internalUsageWalletSample) int {
		return a.Timestamp.Compare(b.Timestamp)
	})

	return samples, true
}

type usageScaledChildDelta struct {
	Timestamp   time.Time
	ChildKey    string
	UsageChange int64
	FlowShift   int64
}

func usageResolveChildKey(w usageWalletWithScale, child util.Option[AccWalletId]) string {
	if !child.Present {
		return ""
	}
	if key, ok := w.Live.ChildrenByWalletId[child.Value]; ok {
		return key
	}
	resolved, ok := walletToUsageReportChild(child.Value)
	if !ok {
		return ""
	}
	return resolved.Key
}

func usageHydrateChildDeltas(
	w usageWalletWithScale,
	startTime time.Time,
	endTime time.Time,
) []usageScaledChildDelta {
	isCapacity := w.Live.Category.AccountingModel == accapi.AccountingModelCapacity
	scale := w.ScaleFactor
	var deltas []usageScaledChildDelta

	for _, r := range w.Reports {
		if r.Format >= usageReportFormatV2 && len(r.UsageOverTime.ChildEvents) > 0 {
			for _, ev := range r.UsageOverTime.ChildEvents {
				if ev.Timestamp.Before(startTime) || ev.Timestamp.After(endTime) {
					continue
				}
				key := usageResolveChildKey(w, ev.Child)
				deltas = append(deltas, usageScaledChildDelta{
					Timestamp:   ev.Timestamp,
					ChildKey:    key,
					UsageChange: ev.UsageChange * scale,
					FlowShift:   ev.FlowShift * scale,
				})
			}
			continue
		}

		for _, d := range r.UsageOverTime.Delta {
			if d.Timestamp.Before(startTime) || d.Timestamp.After(endTime) {
				continue
			}
			key := usageResolveChildKey(w, d.Child)
			usageChange := d.Change
			flowShift := int64(0)
			if !isCapacity && d.Change < 0 {
				usageChange = 0
				flowShift = d.Change
			}
			deltas = append(deltas, usageScaledChildDelta{
				Timestamp:   d.Timestamp,
				ChildKey:    key,
				UsageChange: usageChange * scale,
				FlowShift:   flowShift * scale,
			})
		}
	}

	return deltas
}

var usageBucketDurationCandidates = []time.Duration{
	4 * time.Hour,
	12 * time.Hour,
	24 * time.Hour,
	3 * 24 * time.Hour,
	7 * 24 * time.Hour,
	14 * 24 * time.Hour,
	30 * 24 * time.Hour,
}

func usageChooseBucketDuration(startTime time.Time, endTime time.Time, maxBuckets int) time.Duration {
	span := endTime.Sub(startTime)
	if span <= 0 {
		return 4 * time.Hour
	}

	if maxBuckets <= 0 {
		if span <= 8*24*time.Hour {
			return 4 * time.Hour
		}
		if span <= 95*24*time.Hour {
			return 24 * time.Hour
		}
		if span <= 400*24*time.Hour {
			return 7 * 24 * time.Hour
		}
		return 30 * 24 * time.Hour
	}

	for _, dur := range usageBucketDurationCandidates {
		if int(span/dur)+1 <= maxBuckets {
			return dur
		}
	}
	return 30 * 24 * time.Hour
}

func usageBuildBucketStarts(startTime time.Time, endTime time.Time, dur time.Duration) []time.Time {
	aligned := util.StartOfDayUTC(startTime)
	if dur < 24*time.Hour {
		hours := int(dur / time.Hour)
		if hours > 0 {
			h := (startTime.UTC().Hour() / hours) * hours
			aligned = time.Date(startTime.UTC().Year(), startTime.UTC().Month(), startTime.UTC().Day(), h, 0, 0, 0, time.UTC)
		}
	}
	var buckets []time.Time
	cursor := aligned
	for !cursor.After(endTime) {
		buckets = append(buckets, cursor)
		cursor = cursor.Add(dur)
		if len(buckets) > 400 {
			break
		}
	}
	if len(buckets) == 0 {
		buckets = append(buckets, aligned)
	}
	return buckets
}

func usageFindBucketIndex(buckets []time.Time, dur time.Duration, ts time.Time) int {
	if len(buckets) == 0 {
		return -1
	}
	if ts.Before(buckets[0]) {
		return -1
	}
	idx := int(ts.Sub(buckets[0]) / dur)
	if idx < 0 {
		return -1
	}
	if idx >= len(buckets) {
		return len(buckets) - 1
	}
	return idx
}

func usageFindNextQuotaDrop(
	allocs []usageAllocWindow,
	now time.Time,
	isCapacity bool,
	currentActiveQuota int64,
	currentUsage int64,
) util.Option[accapi.UsageQuotaDrop] {
	var endTimes []time.Time
	for _, alloc := range allocs {
		if alloc.Retired || !alloc.End.After(now) || alloc.Quota <= 0 {
			continue
		}
		endTimes = append(endTimes, alloc.End)
	}
	if len(endTimes) == 0 {
		return util.OptNone[accapi.UsageQuotaDrop]()
	}

	slices.SortFunc(endTimes, func(a, b time.Time) int {
		return a.Compare(b)
	})

	quotaAt := func(ts time.Time) int64 {
		sum := int64(0)
		for _, alloc := range allocs {
			if alloc.Retired || alloc.Quota <= 0 {
				continue
			}
			if !alloc.Start.After(ts) && alloc.End.After(ts) {
				sum += alloc.Quota
			}
		}
		return sum
	}

	var prevChecked time.Time
	hasPrev := false
	firstDrop := util.OptNone[accapi.UsageQuotaDrop]()
	for _, endTs := range endTimes {
		if hasPrev && endTs.Sub(prevChecked) < 24*time.Hour {
			continue
		}
		prevChecked = endTs
		hasPrev = true

		beforeTs := endTs.Add(-time.Second)
		if beforeTs.Before(now) {
			beforeTs = now
		}
		quotaBefore := quotaAt(beforeTs)
		if beforeTs.Equal(now) && currentActiveQuota > quotaBefore {
			quotaBefore = currentActiveQuota
		}
		afterTs := endTs.Add(24 * time.Hour)
		quotaAfter := quotaAt(afterTs)
		if quotaAfter < quotaBefore {
			hasConsequence := (isCapacity && currentUsage > quotaAfter) || (!isCapacity && quotaBefore > 0 && quotaAfter == 0)
			drop := accapi.UsageQuotaDrop{
				Timestamp:   fndapi.Timestamp(endTs),
				QuotaBefore: quotaBefore,
				QuotaAfter:  quotaAfter,
				WouldExceed: hasConsequence,
			}
			if !firstDrop.Present {
				firstDrop.Set(drop)
			}
			withinWarningWindow := endTs.Sub(now) <= 90*24*time.Hour
			if hasConsequence && (!firstDrop.Value.WouldExceed || firstDrop.Value.Timestamp.Time().Equal(endTs)) && withinWarningWindow {
				return util.OptValue(drop)
			}
			if hasConsequence && firstDrop.Value.Timestamp.Time().Equal(endTs) {
				return util.OptValue(drop)
			}
		}
	}

	return firstDrop
}

func usageComputeNextQuotaDrop(
	wallets []usageWalletWithScale,
	now time.Time,
	isCapacity bool,
	currentActiveQuota int64,
	currentUsage int64,
) util.Option[accapi.UsageQuotaDrop] {
	var scaled []usageAllocWindow
	for _, w := range wallets {
		for _, alloc := range w.Live.IncomingAllocations {
			if alloc.Retired || !alloc.End.After(now) || alloc.Quota <= 0 {
				continue
			}
			a := alloc
			a.Quota = alloc.Quota * w.ScaleFactor
			scaled = append(scaled, a)
		}
	}
	return usageFindNextQuotaDrop(scaled, now, isCapacity, currentActiveQuota, currentUsage)
}

func usageComputeLifecycleEvents(
	wallets []usageWalletWithScale,
	startTime time.Time,
	endTime time.Time,
	now time.Time,
) []accapi.UsageLifecycleEvent {
	type eventKey struct {
		DayUtc     int64
		Type       string
		ProductKey string
	}

	multiProduct := len(wallets) > 1
	grouped := map[eventKey]*accapi.UsageLifecycleEvent{}

	addEvent := func(ts time.Time, evType string, quotaDelta int64, productKey string) {
		day := util.StartOfDayUTC(ts)
		key := eventKey{
			DayUtc:     day.Unix(),
			Type:       evType,
			ProductKey: productKey,
		}
		existing, ok := grouped[key]
		if !ok {
			existing = &accapi.UsageLifecycleEvent{
				Timestamp:  fndapi.Timestamp(day),
				Type:       evType,
				QuotaDelta: 0,
				Count:      0,
				ProductKey: productKey,
			}
			grouped[key] = existing
		}
		existing.QuotaDelta += quotaDelta
		existing.Count++
	}

	var earliestUpcoming time.Time
	hasUpcoming := false
	upcomingDelta := int64(0)
	upcomingCount := 0

	for _, w := range wallets {
		pKey := ""
		if multiProduct {
			pKey = usageProductKey(w.Live.Category)
		}
		for _, alloc := range w.Live.IncomingAllocations {
			effectiveQuota := alloc.Quota
			if alloc.Retired && alloc.RetiredQuota > 0 {
				effectiveQuota = alloc.RetiredQuota
			}
			scaledQuota := effectiveQuota * w.ScaleFactor

			if !alloc.Start.Before(startTime) && !alloc.Start.After(endTime) {
				addEvent(alloc.Start, "start", scaledQuota, pKey)
			}

			if !alloc.End.Before(startTime) && !alloc.End.After(endTime) {
				evType := "expire"
				if alloc.Retired {
					evType = "retire"
				}
				addEvent(alloc.End, evType, -scaledQuota, pKey)
			} else if alloc.Active && !alloc.Retired && alloc.End.After(endTime) && alloc.End.After(now) {
				if !hasUpcoming || alloc.End.Before(earliestUpcoming) {
					earliestUpcoming = alloc.End
					hasUpcoming = true
					upcomingDelta = -scaledQuota
					upcomingCount = 1
				} else if util.StartOfDayUTC(alloc.End).Equal(util.StartOfDayUTC(earliestUpcoming)) {
					upcomingDelta -= scaledQuota
					upcomingCount++
				}
			}
		}
	}

	events := make([]accapi.UsageLifecycleEvent, 0, len(grouped)+1)
	for _, ev := range grouped {
		events = append(events, *ev)
	}
	if hasUpcoming {
		events = append(events, accapi.UsageLifecycleEvent{
			Timestamp:  fndapi.Timestamp(util.StartOfDayUTC(earliestUpcoming)),
			Type:       "upcoming_expire",
			QuotaDelta: upcomingDelta,
			Count:      upcomingCount,
			ProductKey: "",
		})
	}

	slices.SortFunc(events, func(a, b accapi.UsageLifecycleEvent) int {
		if c := a.Timestamp.Time().Compare(b.Timestamp.Time()); c != 0 {
			return c
		}
		return cmp.Compare(a.Type, b.Type)
	})

	return events
}

type usageOverviewSummary struct {
	HasData      bool
	PeriodUsage  int64
	ActiveUsage  int64
	ActiveQuota  int64
	RetiredUsage int64
	Quota        int64
	TotalUsage   int64
	PeakUsage    int64
	Sparkline    []accapi.UsageSparklinePoint
}

func usageAggregatePeakByBucket(
	series [][]internalUsageWalletSample,
	buckets []time.Time,
	dur time.Duration,
) []int64 {
	peaks := make([]int64, len(buckets))
	if len(buckets) == 0 {
		return peaks
	}

	values := make([]int64, len(series))
	cursors := make([]int, len(series))

	advanceTo := func(ts time.Time) {
		for i, samples := range series {
			for cursors[i] < len(samples) && !samples[cursors[i]].Timestamp.After(ts) {
				values[i] = samples[cursors[i]].ActiveUsage
				cursors[i]++
			}
		}
	}
	sumValues := func() int64 {
		sum := int64(0)
		for _, v := range values {
			sum += v
		}
		return sum
	}

	for bucketIdx, bucketStart := range buckets {
		bucketEnd := bucketStart.Add(dur)
		advanceTo(bucketStart)
		peaks[bucketIdx] = sumValues()

		var timestamps []time.Time
		for i, samples := range series {
			for j := cursors[i]; j < len(samples) && samples[j].Timestamp.Before(bucketEnd); j++ {
				timestamps = append(timestamps, samples[j].Timestamp)
			}
		}
		slices.SortFunc(timestamps, func(a, b time.Time) int {
			return a.Compare(b)
		})

		var lastProcessed time.Time
		for _, ts := range timestamps {
			if !lastProcessed.IsZero() && ts.Equal(lastProcessed) {
				continue
			}
			lastProcessed = ts
			advanceTo(ts)
			peak := sumValues()
			if peak > peaks[bucketIdx] {
				peaks[bucketIdx] = peak
			}
		}
	}

	return peaks
}

func usageSummarizeWalletsForOverview(
	wallets []usageWalletWithScale,
	isCapacity bool,
	startTime time.Time,
	endTime time.Time,
	now time.Time,
	sparkBuckets []time.Time,
	sparkDur time.Duration,
) usageOverviewSummary {
	nBuckets := len(sparkBuckets)
	bucketChange := make([]int64, nBuckets)
	bucketUsage := make([]int64, nBuckets)
	bucketQuota := make([]int64, nBuckets)
	series := make([][]internalUsageWalletSample, 0, len(wallets))

	summary := usageOverviewSummary{
		Sparkline: make([]accapi.UsageSparklinePoint, nBuckets),
	}

	for _, w := range wallets {
		samples, hasData := usageHydrateWalletSeries(w, startTime, endTime, now)
		series = append(series, samples)
		if hasData {
			summary.HasData = true
		}

		if !isCapacity {
			hasV1 := false
			hasWalletSeriesChanges := false
			for _, r := range w.Reports {
				if r.Format < usageReportFormatV2 {
					hasV1 = true
				}
			}
			for _, s := range samples {
				if s.UsageChange != 0 || s.LocalUsageChange != 0 {
					hasWalletSeriesChanges = true
					break
				}
			}
			if !hasV1 && hasWalletSeriesChanges {
				for _, s := range samples {
					if s.Timestamp.Before(startTime) || s.Timestamp.After(endTime) {
						continue
					}
					if s.UsageChange == 0 && s.LocalUsageChange == 0 {
						continue
					}
					summary.PeriodUsage += s.UsageChange
					idx := usageFindBucketIndex(sparkBuckets, sparkDur, s.Timestamp)
					if idx >= 0 {
						bucketChange[idx] += s.UsageChange
					}
				}
			} else {
				deltas := usageHydrateChildDeltas(w, startTime, endTime)
				for _, d := range deltas {
					summary.PeriodUsage += d.UsageChange
					idx := usageFindBucketIndex(sparkBuckets, sparkDur, d.Timestamp)
					if idx >= 0 {
						bucketChange[idx] += d.UsageChange
					}
				}
			}
		}

		sampleIdx := 0
		var lastSample internalUsageWalletSample
		hasSample := false
		for i, bStart := range sparkBuckets {
			bEnd := bStart.Add(sparkDur)
			for sampleIdx < len(samples) && !samples[sampleIdx].Timestamp.After(bEnd) {
				lastSample = samples[sampleIdx]
				hasSample = true
				sampleIdx++
			}
			if hasSample {
				bucketUsage[i] += lastSample.ActiveUsage
				bucketQuota[i] += lastSample.ActiveQuota
			}
		}

		var endSample internalUsageWalletSample
		if len(samples) > 0 {
			endSample = samples[len(samples)-1]
		}
		summary.ActiveUsage += endSample.ActiveUsage
		summary.ActiveQuota += endSample.ActiveQuota
		summary.RetiredUsage += endSample.RetiredUsage
		summary.Quota += endSample.Quota
		summary.TotalUsage += endSample.TotalUsage
	}

	peaks := usageAggregatePeakByBucket(series, sparkBuckets, sparkDur)
	for i, bStart := range sparkBuckets {
		if peaks[i] > summary.PeakUsage {
			summary.PeakUsage = peaks[i]
		}
		val := bucketChange[i]
		if isCapacity {
			val = bucketUsage[i]
		}
		summary.Sparkline[i] = accapi.UsageSparklinePoint{
			Timestamp: fndapi.Timestamp(bStart),
			Value:     val,
			Quota:     bucketQuota[i],
		}
	}

	if isCapacity {
		summary.PeriodUsage = summary.PeakUsage
	}

	return summary
}

func usageHandleOverviewRetrieve(
	info rpc.RequestInfo,
	request accapi.UsageOverviewRequest,
) (accapi.UsageOverviewResponse, *util.HttpError) {
	now := time.Now().UTC()
	reference := usageResolveActorReference(info)
	startTime, endTime := usageNormalizeRequestWindow(request.Start, request.End, now)

	entries := usageBuildActorUnitGroups(now, reference, startTime, endTime, request.InitialUnitGroupKey.GetOrDefault(""), false)
	sparkDur := usageChooseBucketDuration(startTime, endTime, 24)
	sparkBuckets := usageBuildBucketStarts(startTime, endTime, sparkDur)

	groups := make([]accapi.UsageUnitGroup, 0, len(entries))
	hasData := false
	for _, entry := range entries {
		isCapacity := entry.Descriptor.AccountingModel == accapi.AccountingModelCapacity

		summary := usageSummarizeWalletsForOverview(
			entry.Wallets,
			isCapacity,
			startTime,
			endTime,
			now,
			sparkBuckets,
			sparkDur,
		)
		if summary.HasData {
			hasData = true
		}

		nextExpiry := util.OptNone[fndapi.Timestamp]()
		for _, w := range entry.Wallets {
			if !w.Live.NextMeaningfulExpiration.Present {
				continue
			}
			exp := fndapi.Timestamp(w.Live.NextMeaningfulExpiration.Value)
			if !nextExpiry.Present || exp.Time().Before(nextExpiry.Value.Time()) {
				nextExpiry.Set(exp)
			}
		}
		nextQuotaDrop := usageComputeNextQuotaDrop(entry.Wallets, now, isCapacity, summary.ActiveQuota, summary.ActiveUsage)

		products := make([]accapi.UsageProductSummary, 0, len(entry.Wallets))
		for _, w := range entry.Wallets {
			single := []usageWalletWithScale{w}
			productSummary := usageSummarizeWalletsForOverview(
				single,
				isCapacity,
				startTime,
				endTime,
				now,
				sparkBuckets,
				sparkDur,
			)

			productNextExpiry := util.OptNone[fndapi.Timestamp]()
			if w.Live.NextMeaningfulExpiration.Present {
				productNextExpiry.Set(fndapi.Timestamp(w.Live.NextMeaningfulExpiration.Value))
			}
			productNextQuotaDrop := usageComputeNextQuotaDrop(
				single,
				now,
				isCapacity,
				productSummary.ActiveQuota,
				productSummary.ActiveUsage,
			)

			products = append(products, accapi.UsageProductSummary{
				Category:                 w.Live.Category,
				PeriodUsage:              productSummary.PeriodUsage,
				ActiveUsage:              productSummary.ActiveUsage,
				ActiveQuota:              productSummary.ActiveQuota,
				RetiredUsage:             productSummary.RetiredUsage,
				Quota:                    productSummary.Quota,
				TotalUsage:               productSummary.TotalUsage,
				PeakUsage:                productSummary.PeakUsage,
				NextMeaningfulExpiration: productNextExpiry,
				NextQuotaDrop:            productNextQuotaDrop,
				Sparkline:                util.NonNilSlice(productSummary.Sparkline),
			})
		}

		groups = append(groups, accapi.UsageUnitGroup{
			Key:             entry.Descriptor.Key,
			Title:           entry.Descriptor.BaseTitle,
			AccountingModel: entry.Descriptor.AccountingModel,
			ProductType:     entry.Descriptor.ProductType,
			UnitAndFrequency: accapi.AccountingUnitAndFrequency{
				Unit:      entry.Descriptor.Unit,
				Frequency: entry.GroupFrequency,
			},
			PeriodUsage:              summary.PeriodUsage,
			ActiveUsage:              summary.ActiveUsage,
			ActiveQuota:              summary.ActiveQuota,
			RetiredUsage:             summary.RetiredUsage,
			Quota:                    summary.Quota,
			TotalUsage:               summary.TotalUsage,
			PeakUsage:                summary.PeakUsage,
			NextMeaningfulExpiration: nextExpiry,
			NextQuotaDrop:            nextQuotaDrop,
			Sparkline:                util.NonNilSlice(summary.Sparkline),
			Products:                 util.NonNilSlice(products),
		})
	}

	initialDetail := util.OptNone[accapi.UsageDetailResponse]()
	if len(entries) > 0 {
		var initialEntry *usageUnitGroupEntry
		if request.InitialUnitGroupKey.Present && request.InitialUnitGroupKey.Value != "" {
			for _, entry := range entries {
				if entry.Descriptor.Key == request.InitialUnitGroupKey.Value {
					initialEntry = entry
					break
				}
			}
		}
		if initialEntry == nil {
			initialEntry = entries[0]
		}
		for i := range initialEntry.Wallets {
			if !initialEntry.Wallets[i].Live.ChildrenLoaded {
				b := internalBucketOrInit(initialEntry.Wallets[i].Live.Category)
				if loaded, ok := usageCollectLiveWalletState(now, b, initialEntry.Wallets[i].Live.WalletId, true); ok {
					initialEntry.Wallets[i].Live = loaded
				}
			}
		}
		initialDetail.Set(usageBuildDetailResponse(initialEntry, nil, nil, startTime, endTime, now))
	}

	return accapi.UsageOverviewResponse{
		HasData:       hasData,
		UnitGroups:    util.NonNilSlice(groups),
		InitialDetail: initialDetail,
	}, nil
}

func usageClassifyUtilizationBucket(
	isCapacity bool,
	periodUsage int64,
	activeUsage int64,
	activeQuota int64,
	quotaKnown bool,
) (float64, string) {
	if !quotaKnown {
		return 0.0, "unknown"
	}
	if activeQuota <= 0 {
		if activeUsage > 0 {
			return 100.0, "over"
		}
		return 0.0, "idle"
	}

	pct := (float64(activeUsage) / float64(activeQuota)) * 100.0
	if pct >= 100.0 {
		return pct, "over"
	}
	if activeUsage == 0 && (!isCapacity && periodUsage == 0) {
		return 0.0, "idle"
	}
	if activeUsage == 0 && isCapacity {
		return 0.0, "idle"
	}
	if pct < 25.0 {
		return pct, "low"
	}
	if pct < 75.0 {
		return pct, "medium"
	}
	if pct < 90.0 {
		return pct, "high"
	}
	return pct, "near_full"
}

var usageHistogramBucketDefinitions = []struct {
	Key   string
	Label string
}{
	{Key: "idle", Label: "0% (Idle)"},
	{Key: "low", Label: "1–25%"},
	{Key: "medium", Label: "25–75%"},
	{Key: "high", Label: "75–90%"},
	{Key: "near_full", Label: "90–100%"},
	{Key: "over", Label: "Exhausted / Over"},
}

func usageBuildHistogramBuckets(rows []accapi.UsageChildRow) []accapi.UsageHistogramBucket {
	defs := usageHistogramBucketDefinitions

	indexByKey := map[string]int{}
	buckets := make([]accapi.UsageHistogramBucket, len(defs))
	for i, d := range defs {
		indexByKey[d.Key] = i
		buckets[i] = accapi.UsageHistogramBucket{
			Key:   d.Key,
			Label: d.Label,
		}
	}

	for _, row := range rows {
		if row.IsLocal {
			continue
		}
		idx, ok := indexByKey[row.UtilizationBucket]
		if !ok {
			continue
		}
		buckets[idx].Count++
		buckets[idx].TotalPeriodUsage += row.PeriodUsage
		buckets[idx].TotalActiveQuota += row.ActiveQuota
	}

	return buckets
}

func usageEmptyDetailResponse() accapi.UsageDetailResponse {
	return accapi.UsageDetailResponse{
		ProductsCovered: []accapi.ProductCategory{},
		Timeline:        []accapi.UsageTimelineBucket{},
		DailyActivity:   []accapi.UsageDailyActivityPoint{},
		Events:          []accapi.UsageLifecycleEvent{},
		SubProjectSummary: accapi.UsageSubProjectSummary{
			UtilizationBuckets: []accapi.UsageHistogramBucket{},
		},
		Children: []accapi.UsageChildRow{},
	}
}

func usageHandleDetailRetrieve(
	info rpc.RequestInfo,
	request accapi.UsageDetailRequest,
) (accapi.UsageDetailResponse, *util.HttpError) {
	now := time.Now().UTC()
	reference := usageResolveActorReference(info)
	startTime, endTime := usageNormalizeRequestWindow(request.Start, request.End, now)

	entries := usageBuildActorUnitGroups(now, reference, startTime, endTime, request.UnitGroupKey, request.UnitGroupKey == "")
	var target *usageUnitGroupEntry
	for _, entry := range entries {
		if entry.Descriptor.Key == request.UnitGroupKey {
			target = entry
			break
		}
	}
	if target == nil && len(entries) > 0 && request.UnitGroupKey == "" {
		target = entries[0]
	}
	if target == nil {
		return usageEmptyDetailResponse(), nil
	}

	return usageBuildDetailResponse(target, request.Products, request.PinnedChildren, startTime, endTime, now), nil
}

func usageAccumulateReportDailyActivity(
	reports []internalUsageReport,
	scale int64,
	minDay time.Time,
	maxDay time.Time,
	dailyChangeByDay map[int64]int64,
) {
	for _, r := range reports {
		if r.Format >= usageReportFormatV2 && len(r.UsageOverTime.ChildEvents) > 0 {
			for _, ev := range r.UsageOverTime.ChildEvents {
				if ev.UsageChange <= 0 {
					continue
				}
				day := util.StartOfDayUTC(ev.Timestamp)
				if day.Before(minDay) || day.After(maxDay) {
					continue
				}
				dailyChangeByDay[day.Unix()] += ev.UsageChange * scale
			}
			continue
		}

		if r.Format >= usageReportFormatV2 && len(r.UsageOverTime.WalletSeries) > 0 {
			for _, pt := range r.UsageOverTime.WalletSeries {
				if pt.UsageChange <= 0 {
					continue
				}
				day := util.StartOfDayUTC(pt.Timestamp)
				if day.Before(minDay) || day.After(maxDay) {
					continue
				}
				dailyChangeByDay[day.Unix()] += pt.UsageChange * scale
			}
			continue
		}

		for _, d := range r.UsageOverTime.Delta {
			if d.Change <= 0 {
				continue
			}
			day := util.StartOfDayUTC(d.Timestamp)
			if day.Before(minDay) || day.After(maxDay) {
				continue
			}
			dailyChangeByDay[day.Unix()] += d.Change * scale
		}
	}
}

type usageChildDaySnapshotAt struct {
	Timestamp        time.Time
	Snap             internalUsageChildDaySnapshot
	ActiveQuotaKnown bool
	QuotaKnown       bool
}

func usageBuildDetailResponse(
	target *usageUnitGroupEntry,
	requestedProducts []accapi.ProductCategoryIdV2,
	pinnedChildren []string,
	startTime time.Time,
	endTime time.Time,
	now time.Time,
) accapi.UsageDetailResponse {
	// Select the products requested by the caller.
	productFilter := map[string]util.Empty{}
	for _, p := range requestedProducts {
		productFilter[usageProductIdKey(p)] = util.Empty{}
	}

	var selectedWallets []usageWalletWithScale
	for _, w := range target.Wallets {
		if len(productFilter) > 0 {
			if _, ok := productFilter[usageProductKey(w.Live.Category)]; !ok {
				continue
			}
		}
		selectedWallets = append(selectedWallets, w)
	}

	isCapacity := target.Descriptor.AccountingModel == accapi.AccountingModelCapacity
	bucketDur := usageChooseBucketDuration(startTime, endTime, 0)
	bucketStarts := usageBuildBucketStarts(startTime, endTime, bucketDur)

	response := usageEmptyDetailResponse()
	response.UnitGroupKey = target.Descriptor.Key
	response.Title = target.Descriptor.BaseTitle
	response.AccountingModel = target.Descriptor.AccountingModel
	response.ProductType = target.Descriptor.ProductType
	response.UnitAndFrequency = accapi.AccountingUnitAndFrequency{
		Unit:      target.Descriptor.Unit,
		Frequency: target.GroupFrequency,
	}
	response.BucketDurationMs = bucketDur.Milliseconds()
	response.ValidFrom = fndapi.Timestamp(startTime)
	response.ValidUntil = fndapi.Timestamp(endTime)
	for _, w := range selectedWallets {
		response.ProductsCovered = append(response.ProductsCovered, w.Live.Category)
	}
	response.ProductsCovered = util.NonNilSlice(response.ProductsCovered)
	if len(selectedWallets) == 1 {
		response.Title = selectedWallets[0].Live.Category.Name
	}
	if len(selectedWallets) == 0 {
		return response
	}

	// Load report samples for the selected products.
	samplesByWallet := make([][]internalUsageWalletSample, 0, len(selectedWallets))
	hasData := false
	for _, w := range selectedWallets {
		samples, walletHasData := usageHydrateWalletSeries(w, startTime, endTime, now)
		if walletHasData {
			hasData = true
		}
		samplesByWallet = append(samplesByWallet, samples)
	}
	if !hasData {
		return response
	}
	response.HasData = true

	// Build the timeline buckets.
	nBuckets := len(bucketStarts)
	timeline := make([]accapi.UsageTimelineBucket, nBuckets)
	for i, bStart := range bucketStarts {
		timeline[i] = accapi.UsageTimelineBucket{
			Timestamp:    fndapi.Timestamp(bStart),
			EndTimestamp: fndapi.Timestamp(bStart.Add(bucketDur)),
		}
	}
	peaks := usageAggregatePeakByBucket(samplesByWallet, bucketStarts, bucketDur)

	bucketByProductChange := make([]map[string]int64, nBuckets)
	bucketByProductUsage := make([]map[string]int64, nBuckets)
	bucketByChildChange := make([]map[string]int64, nBuckets)
	bucketByChildUsage := make([]map[string]int64, nBuckets)
	for i := 0; i < nBuckets; i++ {
		bucketByProductChange[i] = map[string]int64{}
		bucketByProductUsage[i] = map[string]int64{}
		bucketByChildChange[i] = map[string]int64{}
		bucketByChildUsage[i] = map[string]int64{}
	}

	childAccum := map[string]*usageChildAccumulator{}
	ensureChild := func(key string) *usageChildAccumulator {
		c, ok := childAccum[key]
		if !ok {
			c = &usageChildAccumulator{
				Child:   key,
				IsLocal: key == "",
			}
			childAccum[key] = c
		}
		return c
	}

	kpis := accapi.UsageDetailKpis{}
	for i := range peaks {
		timeline[i].PeakUsage = peaks[i]
		if peaks[i] > kpis.PeakUsage {
			kpis.PeakUsage = peaks[i]
		}
	}

	// Load daily activity around the requested period.
	dailyChangeByDay := map[int64]int64{}
	useLiveEnd := !endTime.Before(now.Add(-4 * time.Hour))
	startDay := util.StartOfDayUTC(startTime)
	endDay := util.StartOfDayUTC(endTime)
	nowDay := util.StartOfDayUTC(now)
	pastActivityStart := startDay.AddDate(0, 0, -365)
	pastActivityEnd := startDay.AddDate(0, 0, -1)
	futureActivityStart := endDay.AddDate(0, 0, 1)
	futureActivityEnd := endDay.AddDate(0, 0, 365)
	if futureActivityEnd.After(nowDay) {
		futureActivityEnd = nowDay
	}

	// Accumulate per-product series and KPIs.
	for walletIdx, w := range selectedWallets {
		pKey := usageProductKey(w.Live.Category)
		scale := w.ScaleFactor
		samples := samplesByWallet[walletIdx]
		deltas := usageHydrateChildDeltas(w, startTime, endTime)

		if !isCapacity {
			pastReports := usageRetrieveHistoricReports(pastActivityStart, startDay, w.Live.WalletId)
			usageAccumulateReportDailyActivity(pastReports, scale, pastActivityStart, pastActivityEnd, dailyChangeByDay)
			if endDay.Before(nowDay) {
				futureFetchUntil := futureActivityEnd.AddDate(0, 0, 1)
				futureReports := usageRetrieveHistoricReports(futureActivityStart, futureFetchUntil, w.Live.WalletId)
				usageAccumulateReportDailyActivity(futureReports, scale, futureActivityStart, futureActivityEnd, dailyChangeByDay)
			}
		}

		for _, d := range deltas {
			if isCapacity {
				continue
			}
			idx := usageFindBucketIndex(bucketStarts, bucketDur, d.Timestamp)
			kpis.PeriodUsage += d.UsageChange
			if d.ChildKey == "" {
				kpis.PeriodLocalUsage += d.UsageChange
			} else {
				kpis.PeriodChildrenUsage += d.UsageChange
			}
			if d.UsageChange > 0 {
				dayUnix := util.StartOfDayUTC(d.Timestamp).Unix()
				dailyChangeByDay[dayUnix] += d.UsageChange
			}
			if idx >= 0 {
				timeline[idx].UsageChange += d.UsageChange
				timeline[idx].FlowShift += d.FlowShift
				if d.ChildKey == "" {
					timeline[idx].LocalUsageChange += d.UsageChange
				}
				bucketByProductChange[idx][pKey] += d.UsageChange
				bucketByChildChange[idx][d.ChildKey] += d.UsageChange
			}
			child := ensureChild(d.ChildKey)
			child.PeriodUsage += d.UsageChange
		}

		sampleIdx := 0
		var lastSample internalUsageWalletSample
		hasSample := false
		for i, bStart := range bucketStarts {
			bEnd := bStart.Add(bucketDur)
			for sampleIdx < len(samples) && !samples[sampleIdx].Timestamp.After(bEnd) {
				lastSample = samples[sampleIdx]
				hasSample = true
				sampleIdx++
			}
			if hasSample {
				timeline[i].ActiveUsage += lastSample.ActiveUsage
				timeline[i].RetiredUsage += lastSample.RetiredUsage
				timeline[i].TotalUsage += lastSample.TotalUsage
				timeline[i].LocalUsage += lastSample.LocalUsage
				timeline[i].ActiveQuota += lastSample.ActiveQuota
				timeline[i].Quota += lastSample.Quota
				timeline[i].MaxUsable += lastSample.MaxUsable
				bucketByProductUsage[i][pKey] += lastSample.ActiveUsage
				bucketByChildUsage[i][""] += lastSample.LocalUsage
			}
		}

		var startSample internalUsageWalletSample
		var endSample internalUsageWalletSample
		if len(samples) > 0 {
			startSample = samples[0]
			endSample = samples[len(samples)-1]
		}
		kpis.ActiveUsageAtStart += startSample.ActiveUsage
		kpis.ActiveUsageAtEnd += endSample.ActiveUsage
		kpis.ActiveQuotaAtStart += startSample.ActiveQuota
		kpis.ActiveQuotaAtEnd += endSample.ActiveQuota
		kpis.RetiredUsageAtStart += startSample.RetiredUsage
		kpis.RetiredUsageAtEnd += endSample.RetiredUsage
		kpis.TotalUsageAtStart += startSample.TotalUsage
		kpis.TotalUsageAtEnd += endSample.TotalUsage
		kpis.QuotaAtStart += startSample.Quota
		kpis.QuotaAtEnd += endSample.Quota
		kpis.LocalUsageAtStart += startSample.LocalUsage
		kpis.LocalUsageAtEnd += endSample.LocalUsage
		kpis.MaxUsableAtEnd += endSample.MaxUsable
		kpis.ActiveAllocatedAtEnd += endSample.ActiveAllocated
		kpis.TotalAllocatedAtEnd += w.Live.TotalAllocated * scale

		if w.Live.NextMeaningfulExpiration.Present {
			exp := fndapi.Timestamp(w.Live.NextMeaningfulExpiration.Value)
			if !kpis.NextMeaningfulExpiration.Present || exp.Time().Before(kpis.NextMeaningfulExpiration.Value.Time()) {
				kpis.NextMeaningfulExpiration.Set(exp)
			}
		}

		if endSample.LocalUsage > 0 || kpis.PeriodLocalUsage > 0 {
			localAcc := ensureChild("")
			localAcc.CurrentUsage += endSample.LocalUsage
			localAcc.ActiveUsage += endSample.LocalUsage
			localAcc.TotalUsage += endSample.LocalUsage
		}

		// Load per-child snapshots for the selected period.
		childSnapshotsByDay := map[string][]usageChildDaySnapshotAt{}
		for _, r := range w.Reports {
			dayTs := r.ValidUntil.GetOrDefault(r.ValidFrom.Add(24 * time.Hour))
			if dayTs.After(endTime) {
				dayTs = endTime
			}
			if r.Format >= usageReportFormatV2 && len(r.UsageOverTime.ChildDayEnd) > 0 {
				for childWalletId, snap := range r.UsageOverTime.ChildDayEnd {
					key := usageResolveChildKey(w, util.OptValue(childWalletId))
					if key == "" {
						continue
					}
					childSnapshotsByDay[key] = append(childSnapshotsByDay[key], usageChildDaySnapshotAt{
						Timestamp:        dayTs,
						Snap:             snap,
						ActiveQuotaKnown: true,
						QuotaKnown:       snap.QuotaRecorded || snap.Quota > 0,
					})
				}
				continue
			}

			latestPerChild := map[string]struct {
				Ts  time.Time
				Val int64
			}{}
			for _, pt := range r.UsageOverTime.ChildrenAbsolute {
				if !pt.Child.Present {
					continue
				}
				key := usageResolveChildKey(w, pt.Child)
				if key == "" {
					continue
				}
				cur, ok := latestPerChild[key]
				if !ok || pt.Timestamp.After(cur.Ts) {
					latestPerChild[key] = struct {
						Ts  time.Time
						Val int64
					}{
						Ts:  pt.Timestamp,
						Val: pt.Usage,
					}
				}
			}
			for key, obs := range latestPerChild {
				childSnapshotsByDay[key] = append(childSnapshotsByDay[key], usageChildDaySnapshotAt{
					Timestamp: obs.Ts,
					Snap: internalUsageChildDaySnapshot{
						TreeUsage:   obs.Val,
						ActiveUsage: obs.Val,
					},
				})
			}
		}

		// Accumulate per-child usage into the timeline buckets.
		allChildKeys := map[string]util.Empty{}
		for key := range w.Live.Children {
			allChildKeys[key] = util.Empty{}
		}
		for key := range childSnapshotsByDay {
			allChildKeys[key] = util.Empty{}
		}

		for childKey := range allChildKeys {
			liveChild, hasLive := w.Live.Children[childKey]
			snaps := childSnapshotsByDay[childKey]
			slices.SortFunc(snaps, func(a, b usageChildDaySnapshotAt) int {
				return a.Timestamp.Compare(b.Timestamp)
			})

			snapIdx := 0
			var lastChildUsage int64
			hasSnap := false
			for i, bStart := range bucketStarts {
				bEnd := bStart.Add(bucketDur)
				for snapIdx < len(snaps) && !snaps[snapIdx].Timestamp.After(bEnd) {
					val := snaps[snapIdx].Snap.ActiveUsage * scale
					if !isCapacity && val == 0 && snaps[snapIdx].Snap.TreeUsage > 0 {
						val = snaps[snapIdx].Snap.TreeUsage * scale
					}
					lastChildUsage = val
					hasSnap = true
					snapIdx++
				}
				if hasSnap {
					bucketByChildUsage[i][childKey] += lastChildUsage
				} else if useLiveEnd && hasLive && i == nBuckets-1 {
					bucketByChildUsage[i][childKey] += liveChild.ActiveUsage * scale
				}
			}

			acc := ensureChild(childKey)
			if useLiveEnd && hasLive {
				acc.HasQuotaSample = true
				acc.CurrentUsage += liveChild.ActiveUsage * scale
				acc.ActiveUsage += liveChild.ActiveUsage * scale
				acc.ActiveQuota += liveChild.ActiveQuota * scale
				acc.Quota += liveChild.Quota * scale
				acc.RetiredUsage += liveChild.RetiredUsage * scale
				acc.TotalUsage += liveChild.TreeUsage * scale
				if liveChild.NextExpiration.Present {
					if !acc.NextExpiration.Present || liveChild.NextExpiration.Value.Before(acc.NextExpiration.Value) {
						acc.NextExpiration.Set(liveChild.NextExpiration.Value)
					}
				}
				for _, alloc := range liveChild.ActiveAllocations {
					scaledAlloc := alloc
					scaledAlloc.Quota = alloc.Quota * scale
					acc.ActiveAllocations = append(acc.ActiveAllocations, scaledAlloc)
				}
				if liveChild.IsSubAllocator {
					acc.IsSubAllocator = true
					acc.SubAllocator.ActiveDownstreamCount += liveChild.SubAllocator.ActiveDownstreamCount
					acc.SubAllocator.DownstreamActiveAllocated += liveChild.SubAllocator.DownstreamActiveAllocated * scale
					acc.SubAllocator.DirectUsage += liveChild.SubAllocator.DirectUsage * scale
					acc.SubAllocator.DownstreamUsage += liveChild.SubAllocator.DownstreamUsage * scale
				}
			} else if len(snaps) > 0 {
				lastEntry := snaps[len(snaps)-1]
				last := lastEntry.Snap
				acc.HasQuotaSample = true
				acc.CurrentUsage += last.ActiveUsage * scale
				acc.ActiveUsage += last.ActiveUsage * scale
				acc.ActiveQuota += last.ActiveQuota * scale
				acc.Quota += last.Quota * scale
				if !lastEntry.ActiveQuotaKnown {
					acc.ActiveQuotaUnknown = true
				}
				if !lastEntry.QuotaKnown {
					acc.QuotaUnknown = true
				}
				acc.RetiredUsage += last.RetiredUsage * scale
				acc.TotalUsage += last.TreeUsage * scale
				if hasLive && liveChild.IsSubAllocator {
					acc.IsSubAllocator = true
				}
			}
		}
	}

	// Finalize KPIs for capacity-based products.
	if isCapacity {
		sumOccupancy := int64(0)
		for _, pt := range timeline {
			sumOccupancy += pt.ActiveUsage
		}
		if nBuckets > 0 {
			kpis.AvgUsage = sumOccupancy / int64(nBuckets)
		}
		kpis.PeriodUsage = kpis.PeakUsage
		kpis.PeriodLocalUsage = kpis.LocalUsageAtEnd
		kpis.PeriodChildrenUsage = max(int64(0), kpis.TotalUsageAtEnd-kpis.LocalUsageAtEnd)
	}

	kpis.UpstreamCapped = kpis.ActiveQuotaAtEnd > 0 && (kpis.ActiveUsageAtEnd+kpis.MaxUsableAtEnd) < kpis.ActiveQuotaAtEnd
	kpis.NextQuotaDrop = usageComputeNextQuotaDrop(selectedWallets, now, isCapacity, kpis.ActiveQuotaAtEnd, kpis.ActiveUsageAtEnd)

	// Build child rows and the sub-project summary.
	childPeaks := map[string]int64{}
	for i := 0; i < nBuckets; i++ {
		for key, usage := range bucketByChildUsage[i] {
			if usage > childPeaks[key] {
				childPeaks[key] = usage
			}
		}
	}

	childRows := make([]accapi.UsageChildRow, 0, len(childAccum))
	subSummary := accapi.UsageSubProjectSummary{}
	for _, acc := range childAccum {
		childPeak := childPeaks[acc.Child]
		if isCapacity {
			acc.PeriodUsage = childPeak
		}

		isRelevant := acc.IsLocal || acc.PeriodUsage > 0 || acc.ActiveQuota > 0 || acc.ActiveUsage > 0 || acc.TotalUsage > 0
		if !isRelevant {
			continue
		}

		activeQuotaKnown := acc.IsLocal || (acc.HasQuotaSample && !acc.ActiveQuotaUnknown)
		quotaKnown := acc.IsLocal || (acc.HasQuotaSample && !acc.QuotaUnknown)
		utilPct, utilBucket := usageClassifyUtilizationBucket(
			isCapacity,
			acc.PeriodUsage,
			acc.ActiveUsage,
			acc.ActiveQuota,
			activeQuotaKnown,
		)

		if !acc.IsLocal {
			subSummary.TotalCount++
			if acc.IsSubAllocator {
				subSummary.SubAllocatorCount++
				kpis.DownstreamActiveAllocated += acc.SubAllocator.DownstreamActiveAllocated
			}
		}

		subOpt := util.OptNone[accapi.UsageSubAllocatorAggregate]()
		if acc.IsSubAllocator {
			reallocatedPct := 0.0
			if acc.ActiveQuota > 0 {
				reallocatedPct = (float64(acc.SubAllocator.DownstreamActiveAllocated) / float64(acc.ActiveQuota)) * 100.0
			}
			subOpt.Set(accapi.UsageSubAllocatorAggregate{
				ActiveDownstreamCount:     acc.SubAllocator.ActiveDownstreamCount,
				DownstreamActiveAllocated: acc.SubAllocator.DownstreamActiveAllocated,
				ReallocatedPercent:        reallocatedPct,
				DirectUsage:               acc.SubAllocator.DirectUsage,
				DownstreamUsage:           acc.SubAllocator.DownstreamUsage,
			})
		}

		nextExpOpt := util.OptNone[fndapi.Timestamp]()
		if acc.NextExpiration.Present {
			nextExpOpt.Set(fndapi.Timestamp(acc.NextExpiration.Value))
		}

		nextDropOpt := util.OptNone[accapi.UsageQuotaDrop]()
		if !acc.IsLocal && len(acc.ActiveAllocations) > 0 {
			nextDropOpt = usageFindNextQuotaDrop(acc.ActiveAllocations, now, isCapacity, acc.ActiveQuota, acc.ActiveUsage)
		}

		childRows = append(childRows, accapi.UsageChildRow{
			Child:              acc.Child,
			IsLocal:            acc.IsLocal,
			IsSubAllocator:     acc.IsSubAllocator,
			PeriodUsage:        acc.PeriodUsage,
			CurrentUsage:       acc.CurrentUsage,
			PeakUsage:          childPeak,
			ActiveUsage:        acc.ActiveUsage,
			ActiveQuota:        acc.ActiveQuota,
			ActiveQuotaKnown:   activeQuotaKnown,
			Quota:              acc.Quota,
			QuotaKnown:         quotaKnown,
			RetiredUsage:       acc.RetiredUsage,
			TotalUsage:         acc.TotalUsage,
			UtilizationPercent: utilPct,
			UtilizationBucket:  utilBucket,
			NextExpiration:     nextExpOpt,
			NextQuotaDrop:      nextDropOpt,
			SubAllocator:       subOpt,
		})
	}

	slices.SortFunc(childRows, func(a, b accapi.UsageChildRow) int {
		if c := cmp.Compare(b.PeriodUsage, a.PeriodUsage); c != 0 {
			return c
		}
		if c := cmp.Compare(b.ActiveUsage, a.ActiveUsage); c != 0 {
			return c
		}
		if c := cmp.Compare(b.ActiveQuota, a.ActiveQuota); c != 0 {
			return c
		}
		return cmp.Compare(a.Child, b.Child)
	})

	subSummary.UtilizationBuckets = usageBuildHistogramBuckets(childRows)

	// Build per-bucket series for products and children.
	pinnedSet := map[string]util.Empty{}
	for _, p := range pinnedChildren {
		pinnedSet[p] = util.Empty{}
	}

	namedSeriesSet := map[string]util.Empty{
		"": util.Empty{},
	}
	for k := range pinnedSet {
		namedSeriesSet[k] = util.Empty{}
	}
	namedLimit := 8
	namedCount := 0
	for _, row := range childRows {
		if row.IsLocal {
			continue
		}
		if namedCount < namedLimit {
			namedSeriesSet[row.Child] = util.Empty{}
			namedCount++
		}
	}

	for i := 0; i < nBuckets; i++ {
		prodKeys := map[string]util.Empty{}
		for k := range bucketByProductChange[i] {
			prodKeys[k] = util.Empty{}
		}
		for k := range bucketByProductUsage[i] {
			prodKeys[k] = util.Empty{}
		}
		byProduct := make([]accapi.UsageSeriesValue, 0, len(prodKeys))
		for k := range prodKeys {
			byProduct = append(byProduct, accapi.UsageSeriesValue{
				Key:    k,
				Change: bucketByProductChange[i][k],
				Usage:  bucketByProductUsage[i][k],
			})
		}
		slices.SortFunc(byProduct, func(a, b accapi.UsageSeriesValue) int {
			return cmp.Compare(a.Key, b.Key)
		})
		timeline[i].ByProduct = byProduct
		childSeries := map[string]*accapi.UsageSeriesValue{}
		addChildSeries := func(rawKey string, change int64, usage int64) {
			outKey := "Other"
			if _, ok := namedSeriesSet[rawKey]; ok {
				outKey = rawKey
			}
			entry, ok := childSeries[outKey]
			if !ok {
				entry = &accapi.UsageSeriesValue{Key: outKey}
				childSeries[outKey] = entry
			}
			entry.Change += change
			entry.Usage += usage
		}

		allBucketChildKeys := map[string]util.Empty{}
		for k := range bucketByChildChange[i] {
			allBucketChildKeys[k] = util.Empty{}
		}
		for k := range bucketByChildUsage[i] {
			allBucketChildKeys[k] = util.Empty{}
		}
		for k := range allBucketChildKeys {
			addChildSeries(k, bucketByChildChange[i][k], bucketByChildUsage[i][k])
		}

		byChild := make([]accapi.UsageSeriesValue, 0, len(childSeries))
		for _, v := range childSeries {
			if v.Change != 0 || v.Usage != 0 {
				byChild = append(byChild, *v)
			}
		}
		slices.SortFunc(byChild, func(a, b accapi.UsageSeriesValue) int {
			return cmp.Compare(a.Key, b.Key)
		})
		timeline[i].ByChild = byChild
	}

	events := usageComputeLifecycleEvents(selectedWallets, startTime, endTime, now)

	// Build the daily activity series.
	dailyActivity := make([]accapi.UsageDailyActivityPoint, 0, len(dailyChangeByDay))
	for dayUnix, change := range dailyChangeByDay {
		if change <= 0 {
			continue
		}
		dailyActivity = append(dailyActivity, accapi.UsageDailyActivityPoint{
			Timestamp:   fndapi.Timestamp(time.Unix(dayUnix, 0).UTC()),
			UsageChange: change,
		})
	}
	slices.SortFunc(dailyActivity, func(a, b accapi.UsageDailyActivityPoint) int {
		return a.Timestamp.Time().Compare(b.Timestamp.Time())
	})

	// Build the final response.
	response.Kpis = kpis
	response.Timeline = util.NonNilSlice(timeline)
	response.DailyActivity = util.NonNilSlice(dailyActivity)
	response.Events = util.NonNilSlice(events)
	response.SubProjectSummary = subSummary
	response.Children = util.NonNilSlice(childRows)
	response.InferenceBreakdown = usageBuildInferenceBreakdown(
		selectedWallets,
		pinnedChildren,
		startTime,
		endTime,
		bucketStarts,
		bucketDur,
	)
	return response
}
