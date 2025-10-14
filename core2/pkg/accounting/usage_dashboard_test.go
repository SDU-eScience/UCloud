package accounting

import (
	"encoding/json"
	"fmt"
	"testing"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

func TestName(t *testing.T) {
	e := newEnv(t, timeCategory)
	e.TimeInMinutes = true

	initUsageDashboards()

	var roots []string

	reported := int64(0)

	api := UsageGenApi{
		AllocateEx: func(now, start, end int, quota int64, recipientRef, parentRef string) {
			if parentRef == "" {
				roots = util.AppendUnique(roots, recipientRef)
			}
			log.Info("AllocateEx(now = %v, start = %v, end = %v, quota = %v, recipient = %v, parent = %v)",
				now, start, end, quota, recipientRef, parentRef)
			e.AllocateEx(now, start, end, quota, recipientRef, parentRef)
		},
		ReportDelta: func(now int, ownerRef string, usage int64) {
			reported += usage
			e.ReportDelta(now, ownerRef, usage)
		},
		Checkpoint: func(now int) {
			usageSample(e.Tm(now))
			for _, root := range roots {
				e.Snapshot(fmt.Sprintf("Root = %v, Time = %v", root, now), root, false)
			}
		},
	}

	config := UsageGenConfig{
		Days:            7,
		BreadthPerLevel: []int{1, 8, 10, 8},
		Seed:            42,
	}
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

		//owner := e.Owner(next.Title)
		//wallet := e.Bucket.WalletsById[e.Wallet(owner, timeAtEnd)]
		//apiWallet := lInternalWalletToApi(timeAtEnd, e.Bucket, wallet, owner.WalletOwner(), true)
		//log.Info("%v: actual = %v, l1 = %v, l2 = %v (Total = %v, MaxUsable = %v)",
		//	next.Title, wallet.LocalUsage, next.LocalUsage, next.LocalUsage2, apiWallet.TotalUsage, apiWallet.MaxUsable)
	}

	{
		owner := e.Owner(rootProject.Title)
		w := e.Wallet(owner, timeAtEnd)
		dashboard := dashboardGlobals.Dashboards[w]
		pretty, _ := json.MarshalIndent(dashboard, "", "    ")
		log.Info("%v", string(pretty))
	}
}
