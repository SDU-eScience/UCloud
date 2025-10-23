package accounting

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"testing"
	"time"
	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

func TestName(t *testing.T) {
	e := newEnv(t, timeCategory)
	e.TimeInMinutes = true

	initUsageReports()

	var roots []string

	reported := int64(0)

	allocateCalls := 0
	reportCalls := 0
	checkpointCalls := 0

	api := UsageGenApi{
		AllocateEx: func(now, start, end int, quota int64, recipientRef, parentRef string) {
			allocateCalls++
			if parentRef == "" {
				roots = util.AppendUnique(roots, recipientRef)
			}
			e.AllocateEx(now, start, end, quota, recipientRef, parentRef)
		},
		ReportDelta: func(now int, ownerRef string, usage int64) {
			reportCalls++
			reported += usage
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
		Days:               30,
		BreadthPerLevel:    []int{1, 8, 100, 8},
		Seed:               42,
		CheckpointInterval: 60 * 4,
		ReportingInterval:  60 * 2,
		Expiration:         true,
	}
	reportGlobals.HistoricCache = make([]reportCacheEntry, 1024*1024*32) // set a very large cache for testing
	rootProject := UsageGenGenerate(api, config)
	timeAtEnd := e.Tm(1440 * config.Days)

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

	{
		owner := e.Owner("UGTest_0_0")
		w := e.Wallet(owner, timeAtEnd)
		dashboards := usageRetrieveHistoricReports(e.Tm(0), timeAtEnd, w)

		tempDir, _ := os.MkdirTemp("", "")
		for _, dashboard := range dashboards {
			pretty, _ := json.MarshalIndent(dashboard, "", "    ")
			_ = os.WriteFile(filepath.Join(tempDir, fmt.Sprintf("daily-%v.json", dashboard.ValidFrom.Format(time.DateOnly))), pretty, 0660)
		}

		log.Info("-----------------------")

		collapsed := usageCollapseReports(dashboards)
		{
			pretty, _ := json.MarshalIndent(collapsed, "", "    ")
			_ = os.WriteFile(filepath.Join(tempDir, "combined.json"), pretty, 0660)
		}
		log.Info("Reports in: %v", tempDir)

		log.Info("-----------------------")

		for _, point := range collapsed.UsageOverTime.Absolute {
			fmt.Printf("%v,%v,%v\n", point.Timestamp.Format(time.DateTime), point.Usage, point.UtilizationPercent100)
		}

		fmt.Printf("\n\n")

		{
			data := make(map[string]map[string]int64) // timestamp -> child -> value
			childSet := make(map[string]bool)
			var timestamps []string

			for _, point := range collapsed.UsageOverTime.Delta {
				ts := point.Timestamp.Format(time.DateTime)
				child := fmt.Sprintf("%v", point.Child.GetOrDefault(-2))
				value := point.Change

				if _, ok := data[ts]; !ok {
					data[ts] = make(map[string]int64)
					timestamps = append(timestamps, ts)
				}
				data[ts][child] = data[ts][child] + value
				childSet[child] = true
			}

			sort.Strings(timestamps)
			var children []string
			for c := range childSet {
				children = append(children, c)
			}
			sort.Strings(children)

			fmt.Printf("timestamp")
			for _, c := range children {
				fmt.Printf(",%s", c)
			}
			fmt.Println()

			for _, ts := range timestamps {
				fmt.Printf("%s", ts)
				for _, c := range children {
					fmt.Printf(",%v", data[ts][c])
				}
				fmt.Println()
			}
		}

		log.Info("Allocate = %v, Report = %v, Checkpoint = %v", allocateCalls, reportCalls, checkpointCalls)
	}
}
