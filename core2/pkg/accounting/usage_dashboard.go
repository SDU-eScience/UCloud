package accounting

import (
	"slices"
	"sort"
	"sync"
	"sync/atomic"
	"time"
	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

// Most of these will have a size of around 128 bytes. Some are likely to go up to around 16K in size.
// For a single day, this means that we will use around 4MB storing these with hourly snapshots when we don't account
// for space saved from collapsing dashboards. Once collapsed, we are likely to only store around 500K a day.

type internalUsageDashboard struct {
	Wallet                accWalletId
	ValidFrom             time.Time
	ValidUntil            util.Option[time.Time] // Most recent dashboard will not set this (valid until "now")
	Kpis                  internalUsageDashboardKpis
	SubProjectHealth      internalSubProjectHealth
	UsageOverTime         internalUsageOverTime
	SubProjectUtilization internalSubProjectUtilization

	Dirty bool
}

type internalUsageDashboardKpis struct {
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
	// TODO We need to think about how retirement affects this
}

type internalSubProjectUtilizationHistogramBucket struct {
	MinInclusive100 float64
	MaxExclusive100 float64
	Count           int
}

type internalSubProjectUtilization struct {
	Histogram []internalSubProjectUtilizationHistogramBucket
}

type internalUsageOverTimeDeltaDataPoint struct {
	Timestamp time.Time
	Child     util.Option[accWalletId]
	Change    int64
}

type internalUsageOverTimeAbsoluteDataPoint struct {
	Timestamp             time.Time
	Usage                 int64
	UtilizationPercent100 float64
}

type internalUsageOverTime struct {
	// Contains all changes. Ordered by timestamp then by child (null first).
	//
	// NOTE(Dan, 07/10/25): We currently have around 600 jobs a day. Given that, this should imply that the delta
	// array will remain small enough that we can actually do this.
	Delta []internalUsageOverTimeDeltaDataPoint

	// absolute usage from the wallet level
	Absolute []internalUsageOverTimeAbsoluteDataPoint
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

type internalGroupHealth int

const (
	internalGroupHealthOk            internalGroupHealth = iota
	internalGroupHealthUnderUtilized internalGroupHealth = iota
	internalGroupHealthAtRisk        internalGroupHealth = iota
)

type internalWalletSnapshot struct {
	Id        accWalletId
	Timestamp time.Time
	Category  accapi.ProductCategory

	Quota          int64
	ActiveQuota    int64
	MaxUsable      int64
	LocalUsage     int64
	TotalUsage     int64
	TotalAllocated int64

	UsageByParent             map[accWalletId]int64
	QuotaByParentActive       map[accWalletId]int64
	QuotaByParentContributing map[accWalletId]int64
	HealthByParent            map[accWalletId]internalGroupHealth
}

type internalSnapshotComparison struct {
	Previous internalWalletSnapshot
	Current  internalWalletSnapshot
}

var dashboardGlobals struct {
	Mu                          sync.RWMutex
	Dashboards                  map[accWalletId]*internalUsageDashboard
	Snapshots                   map[accWalletId]internalWalletSnapshot
	HistoricCache               []dashboardCacheEntry
	HistoricCacheSlotsAvailable int
	HistoricCacheLastEmptySlot  int
	HistoricCacheIndex          map[time.Time]map[accWalletId]int
}

type dashboardCacheEntry struct {
	InUse      bool
	LastUsedAt atomic.Pointer[time.Time]
	Dashboard  internalUsageDashboard
}

func initUsageDashboards() {
	dashboardGlobals.Dashboards = map[accWalletId]*internalUsageDashboard{}
	dashboardGlobals.Snapshots = map[accWalletId]internalWalletSnapshot{}
	dashboardGlobals.HistoricCache = make([]dashboardCacheEntry, 1024*1024)
	dashboardGlobals.HistoricCacheIndex = map[time.Time]map[accWalletId]int{}
	dashboardGlobals.HistoricCacheSlotsAvailable = len(dashboardGlobals.HistoricCache)
}

func usageRetrieveHistoricDashboards(from time.Time, until time.Time, wallet accWalletId) []internalUsageDashboard {
	// NOTE(Dan, 15/10/2025): Current tests will break in the year 2100, but I will let that be a problem for the
	// future.
	now := time.Now()
	earliestFromTime := now.Add(-100 * 365 * 24 * time.Hour)
	if from.Before(earliestFromTime) {
		from = earliestFromTime
	}

	from = util.StartOfDayUTC(from)
	until = util.StartOfDayUTC(until)

	if until.Before(from) {
		return nil
	}

	var result []internalUsageDashboard
	current := from
	for current.Before(until) {
		dashboard, ok := usageRetrieveHistoric(current, wallet)

		if ok {
			result = append(result, dashboard)
		}

		current = current.AddDate(0, 0, 1)
	}

	if now == until || now.After(until) {
		g := &dashboardGlobals
		g.Mu.RLock()
		currentDashboard, ok := g.Dashboards[wallet]
		if ok {
			result = append(result, *currentDashboard)
		}
		g.Mu.RUnlock()
	}

	// TODO These results can be evicted from the cache while we are working with them
	return result
}

func usageCollapseDashboards(dashboards []internalUsageDashboard) internalUsageDashboard {
	if len(dashboards) == 0 {
		return internalUsageDashboard{}
	}

	firstDashboard := dashboards[0]
	lastDashboard := dashboards[len(dashboards)-1]

	result := internalUsageDashboard{
		Wallet:     firstDashboard.Wallet,
		ValidFrom:  firstDashboard.ValidFrom,
		ValidUntil: util.OptValue(lastDashboard.ValidUntil.GetOrDefault(lastDashboard.ValidFrom)),
	}

	result.Kpis = internalUsageDashboardKpis{
		QuotaAtStart:          firstDashboard.Kpis.QuotaAtStart,
		ActiveQuotaAtStart:    firstDashboard.Kpis.ActiveQuotaAtStart,
		MaxUsableAtStart:      firstDashboard.Kpis.MaxUsableAtStart,
		LocalUsageAtStart:     firstDashboard.Kpis.LocalUsageAtStart,
		TotalUsageAtStart:     firstDashboard.Kpis.TotalUsageAtStart,
		TotalAllocatedAtStart: firstDashboard.Kpis.TotalAllocatedAtStart,

		QuotaAtEnd:          lastDashboard.Kpis.QuotaAtEnd,
		ActiveQuotaAtEnd:    lastDashboard.Kpis.ActiveQuotaAtEnd,
		MaxUsableAtEnd:      lastDashboard.Kpis.MaxUsableAtEnd,
		LocalUsageAtEnd:     lastDashboard.Kpis.LocalUsageAtEnd,
		TotalUsageAtEnd:     lastDashboard.Kpis.TotalUsageAtEnd,
		TotalAllocatedAtEnd: lastDashboard.Kpis.TotalAllocatedAtEnd,
	}

	result.SubProjectHealth = lastDashboard.SubProjectHealth // NOTE(Dan): Idle is recomputed below
	result.SubProjectUtilization = lastDashboard.SubProjectUtilization

	deltaUsageByChild := map[accWalletId]int64{}
	for _, dashboard := range dashboards {
		for _, item := range dashboard.UsageOverTime.Delta {
			if item.Child.Present {
				deltaUsageByChild[item.Child.Value] = deltaUsageByChild[item.Child.Value] + item.Change
			}
		}

		for _, item := range dashboard.UsageOverTime.Absolute {
			result.UsageOverTime.Absolute = append(result.UsageOverTime.Absolute, item)
		}
	}
	result.SubProjectHealth.Idle = result.SubProjectHealth.SubProjectCount - len(deltaUsageByChild)

	topUsersFromChildren := util.TopNKeys(deltaUsageByChild, 10)
	deltaDataPointsByChild := map[util.Option[accWalletId]]map[time.Time]internalUsageOverTimeDeltaDataPoint{}
	allDeltaTimestamps := map[time.Time]util.Empty{}

	for _, dashboard := range dashboards {
		for _, item := range dashboard.UsageOverTime.Delta {
			itemCopy := item
			if !item.Child.Present {
				itemCopy.Child = util.OptNone[accWalletId]()
			} else {
				if !slices.Contains(topUsersFromChildren, item.Child.Value) {
					itemCopy.Child = util.OptValue(accWalletId(-1))
				}
			}

			m, ok := deltaDataPointsByChild[itemCopy.Child]
			if !ok {
				m = map[time.Time]internalUsageOverTimeDeltaDataPoint{}
				deltaDataPointsByChild[itemCopy.Child] = m
			}

			curr, ok := m[itemCopy.Timestamp]
			if ok {
				curr.Change += itemCopy.Change
			} else {
				curr = itemCopy
			}
			deltaDataPointsByChild[itemCopy.Child][itemCopy.Timestamp] = curr
			allDeltaTimestamps[itemCopy.Timestamp] = util.Empty{}
		}
	}

	// Ensure that all timestamps are filled out
	for child, m := range deltaDataPointsByChild {
		for ts := range allDeltaTimestamps {
			_, ok := m[ts]
			if !ok {
				m[ts] = internalUsageOverTimeDeltaDataPoint{
					Timestamp: ts,
					Child:     child,
					Change:    0,
				}
			}
		}
	}

	{
		for _, dataMap := range deltaDataPointsByChild {
			var data []internalUsageOverTimeDeltaDataPoint

			for _, item := range dataMap {
				data = append(data, item)
			}
			slices.SortFunc(data, func(a, b internalUsageOverTimeDeltaDataPoint) int {
				return a.Timestamp.Compare(b.Timestamp)
			})

			// NOTE(Dan): The step size controls roughly how many elements we want to display before we consolidate
			// data points. In this case, we are aiming to store up to 540 before we start consolidation. Which
			// corresponds to roughly 90 days with sampling every 4 hours.
			stepSize := max(1.0, float64(len(data))/540.0)
			acc := 0.0

			currentEntry := internalUsageOverTimeDeltaDataPoint{}
			needNewEntry := true

			for _, entry := range data {
				acc += 1
				if acc >= stepSize {
					acc -= stepSize

					if currentEntry.Change != -1 {
						result.UsageOverTime.Delta = append(result.UsageOverTime.Delta, currentEntry)
						needNewEntry = true
					}
				}

				if needNewEntry {
					currentEntry = internalUsageOverTimeDeltaDataPoint{
						Timestamp: entry.Timestamp,
						Child:     entry.Child,
						Change:    0,
					}
					needNewEntry = false
				}

				currentEntry.Change += entry.Change
			}

			if !needNewEntry {
				result.UsageOverTime.Delta = append(result.UsageOverTime.Delta, currentEntry)
			}
		}
	}

	slices.SortFunc(result.UsageOverTime.Delta, func(a, b internalUsageOverTimeDeltaDataPoint) int {
		if a.Timestamp.Before(b.Timestamp) {
			return -1
		} else if a.Timestamp.After(b.Timestamp) {
			return 1
		} else {
			aId := a.Child.GetOrDefault(-2)
			bId := b.Child.GetOrDefault(-2)
			if aId < bId {
				return -1
			} else if aId > bId {
				return 1
			} else {
				return 0
			}
		}
	})

	return result
}

func usageRetrieveHistoric(now time.Time, wallet accWalletId) (internalUsageDashboard, bool) {
	var result internalUsageDashboard

	now = util.StartOfDayUTC(now)

	g := &dashboardGlobals
	g.Mu.RLock()
	dictOnDay, ok := g.HistoricCacheIndex[now]
	slot := -1
	if ok {
		slot, ok = dictOnDay[wallet]
	}

	if ok {
		entry := &g.HistoricCache[slot]
		entry.LastUsedAt.Store(util.Pointer(time.Now()))
		result = entry.Dashboard
	} else {
		// TODO This will require reading from the database. Ideally it should pre-fetch longer periods since this
		//   function is invoked in a loop.
	}
	g.Mu.RUnlock()

	return result, ok
}

func lUsageRetireDashboard(dashboard *internalUsageDashboard) {
	// TODO Persistence
	g := &dashboardGlobals
	lUsageEvictHistoricCache()

	slot := -1

	for iteration := 0; iteration < len(g.HistoricCache); iteration++ {
		i := (iteration + g.HistoricCacheLastEmptySlot) % len(g.HistoricCache)
		entry := &g.HistoricCache[i]
		if !entry.InUse {
			slot = i
			entry.InUse = true
			entry.LastUsedAt.Store(util.Pointer(time.Now()))
			entry.Dashboard = *dashboard
			g.HistoricCacheSlotsAvailable--
			g.HistoricCacheLastEmptySlot = i
			break
		}
	}

	if slot == -1 {
		log.Fatal("no space in cache? internal error")
	}

	dictOnDay, ok := g.HistoricCacheIndex[dashboard.ValidFrom]
	if !ok {
		dictOnDay = map[accWalletId]int{}
		g.HistoricCacheIndex[dashboard.ValidFrom] = dictOnDay
	}

	dictOnDay[dashboard.Wallet] = slot
}

func lUsageEvictHistoricCache() {
	g := &dashboardGlobals

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
				entry.InUse = false
				entry.Dashboard = internalUsageDashboard{}
				entry.LastUsedAt.Store(util.Pointer(time.Now()))
				g.HistoricCacheSlotsAvailable++
			}
		}
	}
}

func lUsageSample(now time.Time) {
	startOfDay := util.StartOfDayUTC(now)

	dashboardGlobals.Mu.Lock()

	// assume that mostRecentDashboards are locked in this function

	var buckets []*internalBucket
	accGlobals.Mu.Lock()
	for _, b := range accGlobals.BucketsByCategory {
		buckets = append(buckets, b)
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

	snapshotsById := map[accWalletId]internalSnapshotComparison{}
	for _, b := range buckets {
		for _, w := range b.WalletsById {
			snapshotsById[w.Id] = lSnapshotWallet(startOfDay, b, w)
		}
	}

	for _, dashboard := range dashboardGlobals.Dashboards {
		dashboard.Dirty = false
	}

	for _, b := range buckets {
		var walletIds []int
		for _, w := range b.WalletsById {
			walletIds = append(walletIds, int(w.Id))
		}
		sort.Ints(walletIds)

		for _, wId := range walletIds {
			lUsageSampleWallet(now, snapshotsById[accWalletId(wId)])
		}
	}

	for _, b := range buckets {
		b.Mu.Unlock()
	}

	accGlobals.Mu.Unlock()

	dashboardGlobals.Mu.Unlock()
}

func lSnapshotWallet(now time.Time, b *internalBucket, w *internalWallet) internalSnapshotComparison {
	prev, ok := dashboardGlobals.Snapshots[w.Id]
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
			UsageByParent:             map[accWalletId]int64{},
			QuotaByParentActive:       map[accWalletId]int64{},
			QuotaByParentContributing: map[accWalletId]int64{},
			HealthByParent:            map[accWalletId]internalGroupHealth{},
			Category:                  b.Category,
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
		UsageByParent:             map[accWalletId]int64{},
		QuotaByParentActive:       map[accWalletId]int64{},
		QuotaByParentContributing: map[accWalletId]int64{},
		HealthByParent:            map[accWalletId]internalGroupHealth{},
		Category:                  b.Category,
	}

	for parent, group := range w.AllocationsByParent {
		current.UsageByParent[parent] = group.TreeUsage
		contributingQuota := lInternalGroupTotalQuotaContributing(b, group)
		activeQuota := lInternalGroupTotalQuotaFromActiveAllocations(b, group)
		current.QuotaByParentContributing[parent] = contributingQuota
		current.QuotaByParentActive[parent] = activeQuota

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
				timeRemaining := max(now.Sub(alloc.End), 0)
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

	dashboardGlobals.Snapshots[w.Id] = current

	return internalSnapshotComparison{
		Previous: prev,
		Current:  current,
	}
}

func lUsageSampleWallet(now time.Time, cmp internalSnapshotComparison) {
	startOfDay := util.StartOfDayUTC(now)

	prevWallet := cmp.Previous
	currWallet := cmp.Current

	dashboard, ok := dashboardGlobals.Dashboards[currWallet.Id]
	if !ok || dashboard.ValidFrom.Before(startOfDay) {
		if ok && dashboard.ValidFrom.Before(startOfDay) {
			// TODO Possibly extend old dashboard if no changes

			lUsageRetireDashboard(dashboard)
		}

		dashboard = &internalUsageDashboard{
			Wallet:     currWallet.Id,
			ValidFrom:  startOfDay,
			ValidUntil: util.Option[time.Time]{},

			Kpis: internalUsageDashboardKpis{
				QuotaAtStart:          prevWallet.Quota,
				ActiveQuotaAtStart:    prevWallet.ActiveQuota,
				MaxUsableAtStart:      prevWallet.MaxUsable,
				LocalUsageAtStart:     prevWallet.LocalUsage,
				TotalUsageAtStart:     prevWallet.TotalUsage,
				TotalAllocatedAtStart: prevWallet.TotalAllocated,
			},

			// Will be recomputed:
			SubProjectHealth:      internalSubProjectHealth{},
			UsageOverTime:         internalUsageOverTime{},
			SubProjectUtilization: internalSubProjectUtilization{},
		}

		dashboardGlobals.Dashboards[currWallet.Id] = dashboard
	}
	dashboard.SubProjectHealth = internalSubProjectHealth{}
	dashboard.SubProjectUtilization = internalSubProjectUtilization{}

	kpis := &dashboard.Kpis
	kpis.QuotaAtEnd = currWallet.Quota
	kpis.ActiveQuotaAtEnd = currWallet.ActiveQuota
	kpis.MaxUsableAtEnd = currWallet.MaxUsable
	kpis.LocalUsageAtEnd = currWallet.LocalUsage
	kpis.TotalUsageAtEnd = currWallet.TotalUsage
	kpis.TotalAllocatedAtEnd = currWallet.TotalAllocated

	{
		prevUsage := prevWallet.LocalUsage
		currUsage := currWallet.LocalUsage
		delta := currUsage - prevUsage

		if delta != 0 {
			dashboard.UsageOverTime.Delta = append(dashboard.UsageOverTime.Delta, internalUsageOverTimeDeltaDataPoint{
				Timestamp: now,
				Child:     util.Option[accWalletId]{},
				Change:    delta,
			})

			dashboard.Dirty = true
		}

		if prevWallet.LocalUsage != currWallet.LocalUsage || prevWallet.TotalUsage != currWallet.TotalUsage || currWallet.Quota != prevWallet.Quota {
			utilizationPercent100 := 0.0
			if currWallet.Quota != 0 {
				utilizationPercent100 = float64(currWallet.TotalUsage) / float64(currWallet.Quota)
			}

			dashboard.UsageOverTime.Absolute = append(dashboard.UsageOverTime.Absolute, internalUsageOverTimeAbsoluteDataPoint{
				Timestamp:             now,
				Usage:                 currWallet.TotalUsage,
				UtilizationPercent100: utilizationPercent100,
			})

			dashboard.Dirty = true
		}
	}

	for parent, usage := range currWallet.UsageByParent {
		prevUsage := prevWallet.UsageByParent[parent]
		delta := usage - prevUsage

		if parent != 0 {
			parentDashboard := dashboardGlobals.Dashboards[parent]
			parentDashboard.SubProjectHealth.SubProjectCount++

			if delta != 0 {
				parentDashboard.UsageOverTime.Delta = append(
					parentDashboard.UsageOverTime.Delta,
					internalUsageOverTimeDeltaDataPoint{
						Timestamp: now,
						Child:     util.OptValue(currWallet.Id),
						Change:    delta,
					},
				)

				parentDashboard.Dirty = true
			} else {
				parentDashboard.SubProjectHealth.Idle++
			}

			switch currWallet.HealthByParent[parent] {
			case internalGroupHealthOk:
				parentDashboard.SubProjectHealth.Ok++
			case internalGroupHealthUnderUtilized:
				parentDashboard.SubProjectHealth.UnderUtilized++
			case internalGroupHealthAtRisk:
				parentDashboard.SubProjectHealth.AtRisk++
			}
		}
	}

	// TODO utilization
}
