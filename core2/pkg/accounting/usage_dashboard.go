package accounting

import (
	"slices"
	"sort"
	"sync"
	"sync/atomic"
	"time"
	accapi "ucloud.dk/shared/pkg/accounting"
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
	Mu                 sync.RWMutex
	Dashboards         map[accWalletId]*internalUsageDashboard
	Snapshots          map[accWalletId]internalWalletSnapshot
	HistoricCache      []*dashboardCacheEntry
	HistoricCacheIndex map[time.Time]map[accWalletId]int
}

type dashboardCacheEntry struct {
	Slot       int
	LastUsedAt atomic.Pointer[time.Time]
	Dashboard  *internalUsageDashboard
}

func initUsageDashboards() {
	dashboardGlobals.Dashboards = map[accWalletId]*internalUsageDashboard{}
	dashboardGlobals.Snapshots = map[accWalletId]internalWalletSnapshot{}
}

func usageRetrieveHistoric(now time.Time, wallet accWalletId) (*internalUsageDashboard, bool) {
	var result *internalUsageDashboard

	now = util.StartOfDayUTC(now)

	g := &dashboardGlobals
	g.Mu.RLock()
	dictOnDay, ok := g.HistoricCacheIndex[now]
	slot := -1
	if ok {
		slot, ok = dictOnDay[wallet]
	}

	if ok {
		entry := g.HistoricCache[slot]
		entry.LastUsedAt.Store(util.Pointer(time.Now()))
		result = entry.Dashboard
	}
	g.Mu.RUnlock()

	return result, ok
}

func usageSample(now time.Time) {
	now = util.StartOfDayUTC(now)

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
			snapshotsById[w.Id] = lSnapshotWallet(now, b, w)
		}
	}

	for _, dashboard := range dashboardGlobals.Dashboards {
		dashboard.Dirty = false

		dashboard.SubProjectHealth = internalSubProjectHealth{}
		dashboard.SubProjectUtilization = internalSubProjectUtilization{}
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
	prevWallet := cmp.Previous
	currWallet := cmp.Current

	dashboard, ok := dashboardGlobals.Dashboards[currWallet.Id]
	if !ok || dashboard.ValidFrom.Before(now) {
		if ok && dashboard.ValidFrom.Before(now) {
			// TODO Save old dashboard
			// TODO Possibly extend old dashboard if no changes
		}

		dashboard = &internalUsageDashboard{
			Wallet:     currWallet.Id,
			ValidFrom:  now,
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
