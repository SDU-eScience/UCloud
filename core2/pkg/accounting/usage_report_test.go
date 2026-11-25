package accounting

import (
	"fmt"
	"testing"

	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

func TestUsageGenSimulation(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping test in short mode.")
		return
	}
	e := newEnv(t, timeCategory)
	e.TimeInMinutes = true

	initUsageReports()

	var roots []string

	allocateCalls := 0
	reportCalls := 0
	checkpointCalls := 0

	api := UsageGenApi{
		AllocateEx: func(product UsageGenProduct, now, start, end int, quota int64, recipientRef, parentRef string) {
			allocateCalls++
			if parentRef == "" {
				roots = util.AppendUnique(roots, recipientRef)
			}
			e.AllocateEx(now, start, end, quota, recipientRef, parentRef)
		},
		ReportDelta: func(product UsageGenProduct, now int, ownerRef string, usage int64) {
			reportCalls++
			e.ReportDelta(now, ownerRef, usage)
		},
		Checkpoint: func(now int) {
			checkpointCalls++
			e.Scan(now)
			usageSample(e.Tm(now))

			for _, root := range roots {
				e.Snapshot(fmt.Sprintf("Root = %v, Time = %v", root, now), root, false)
			}
		},
	}

	config := accapi.UsageGenConfig{
		Days:               7,
		BreadthPerLevel:    []int{1, 8, 100, 8},
		Seed:               42,
		CheckpointInterval: 60 * 4,
		ReportingInterval:  60 * 2,
		Expiration:         true,
	}
	reportGlobals.HistoricCache = make([]reportCacheEntry, 1024*1024*32) // set a very large cache for testing
	rootProject := UsageGenGenerate(api, config)

	e.Snapshot(rootProject.Title, rootProject.Title, false)

	var stack []*UsageGenProject
	stack = append(stack, rootProject)
	for len(stack) > 0 {
		var next *UsageGenProject
		next, stack = util.PopHead(stack)
		for _, child := range next.Children {
			stack = append(stack, child)
		}
	}

	log.Info("Allocate = %v, Report = %v, Checkpoint = %v", allocateCalls, reportCalls, checkpointCalls)
}
