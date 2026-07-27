package accounting

import (
	"encoding/base64"
	"net/http"
	"slices"
	"sort"
	"strings"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func UsageBreakdownBrowse(actor rpc.Actor, request accapi.UsageBreakdownBrowseRequest) (fndapi.PageV2[accapi.UsageBreakdownItem], *util.HttpError) {
	reference := actor.Username
	if actor.Project.Present {
		if !actor.Membership[actor.Project.Value].Satisfies(rpc.ProjectRoleAdmin) {
			return fndapi.EmptyPage[accapi.UsageBreakdownItem](), util.HttpErr(http.StatusForbidden, "You need admin privileges in your project to view the usage breakdown")
		}
		reference = string(actor.Project.Value)
	}

	items := internalUsageBreakdown(time.Now(), reference, request.Category)
	return usageBreakdownPage(items, request.ItemsPerPage, request.Next), nil
}

func internalUsageBreakdown(now time.Time, reference string, category accapi.ProductCategoryIdV2) []accapi.UsageBreakdownItem {
	accGlobals.Mu.RLock()
	defer accGlobals.Mu.RUnlock()

	owner := accGlobals.OwnersByReference[reference]
	bucket := accGlobals.BucketsByCategory[category]
	if owner == nil || bucket == nil {
		return []accapi.UsageBreakdownItem{}
	}

	bucket.Mu.Lock()
	defer bucket.Mu.Unlock()

	wallet := bucket.WalletsByOwner[owner.Id]
	if wallet == nil {
		return []accapi.UsageBreakdownItem{}
	}

	relevantWallets := []AccWalletId{wallet.Id}
	visitedWallets := map[AccWalletId]bool{}
	for len(relevantWallets) > 0 {
		walletId := relevantWallets[0]
		relevantWallets = relevantWallets[1:]
		if visitedWallets[walletId] {
			continue
		}
		visitedWallets[walletId] = true
		currentWallet := bucket.WalletsById[walletId]
		if currentWallet == nil {
			continue
		}
		for childId := range currentWallet.ChildrenUsage {
			relevantWallets = append(relevantWallets, childId)
		}
	}

	walletIds := make([]AccWalletId, 0, len(visitedWallets))
	for walletId := range visitedWallets {
		walletIds = append(walletIds, walletId)
	}
	lInternalTransitionWallets(bucket, now, false, walletIds...)
	items := make([]accapi.UsageBreakdownItem, 0, len(wallet.ScopedUsage)+len(wallet.ChildrenUsage))

	for key, scope := range wallet.ScopedUsage {
		resource, ok := usageBreakdownParseResource(key)
		if !ok || scope.Usage == 0 {
			continue
		}

		items = append(items, accapi.UsageBreakdownItem{
			Type:          accapi.UsageBreakdownItemTypeResource,
			Usage:         scope.Usage,
			Resource:      util.OptValue(resource),
			LastUpdatedAt: util.OptValue(fndapi.Timestamp(scope.LastUpdatedAt)),
		})
	}

	for childId := range wallet.ChildrenUsage {
		childWallet := bucket.WalletsById[childId]
		if childWallet == nil {
			continue
		}

		group := childWallet.AllocationsByParent[wallet.Id]
		if group == nil || !lInternalGroupHasCommittedAllocation(bucket, group) || group.TreeUsage == 0 {
			continue
		}

		childOwner := accGlobals.OwnersById[childWallet.OwnedBy]
		if childOwner == nil {
			continue
		}

		items = append(items, accapi.UsageBreakdownItem{
			Type:      accapi.UsageBreakdownItemTypeWorkspace,
			Usage:     group.TreeUsage,
			Workspace: util.OptValue(childOwner.WalletOwner()),
		})
	}

	slices.SortFunc(items, func(a, b accapi.UsageBreakdownItem) int {
		return strings.Compare(usageBreakdownItemKey(a), usageBreakdownItemKey(b))
	})
	return items
}

func usageBreakdownParseResource(key string) (accapi.UsageBreakdownResource, bool) {
	if id, ok := strings.CutPrefix(key, "drive-"); ok && id != "" {
		return accapi.UsageBreakdownResource{Type: accapi.UsageBreakdownResourceTypeDrive, Id: id}, true
	}
	if id, ok := strings.CutPrefix(key, "job-"); ok && id != "" {
		return accapi.UsageBreakdownResource{Type: accapi.UsageBreakdownResourceTypeJob, Id: id}, true
	}
	return accapi.UsageBreakdownResource{}, false
}

func usageBreakdownItemKey(item accapi.UsageBreakdownItem) string {
	rawKey := string(item.Type)
	if item.Resource.Present {
		rawKey = "resource\x00" + string(item.Resource.Value.Type) + "\x00" + item.Resource.Value.Id
	} else if item.Workspace.Present {
		rawKey = "workspace\x00" + string(item.Workspace.Value.Type) + "\x00" + item.Workspace.Value.Reference()
	}
	return base64.RawURLEncoding.EncodeToString([]byte(rawKey))
}

func usageBreakdownPage(items []accapi.UsageBreakdownItem, requestedPageSize int, next util.Option[string]) fndapi.PageV2[accapi.UsageBreakdownItem] {
	pageSize := fndapi.ItemsPerPage(requestedPageSize)
	start := 0
	if next.Present {
		start = sort.Search(len(items), func(index int) bool {
			return usageBreakdownItemKey(items[index]) > next.Value
		})
	}

	end := min(start+pageSize, len(items))
	page := fndapi.PageV2[accapi.UsageBreakdownItem]{
		Items:        items[start:end],
		ItemsPerPage: pageSize,
	}
	if end < len(items) {
		page.Next.Set(usageBreakdownItemKey(items[end-1]))
	}
	return page
}
