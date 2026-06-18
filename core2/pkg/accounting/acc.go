package accounting

import (
	"fmt"
	"net/http"
	"os"
	"slices"
	"sync"
	"sync/atomic"
	"time"

	lru "github.com/hashicorp/golang-lru/v2/expirable"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initAccounting() {
	accountingLoad()
	go accountingProcessTasks()

	accapi.RootAllocate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[accapi.RootAllocateRequest]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		var result []fndapi.FindByStringId
		for _, reqItem := range request.Items {
			id, err := RootAllocate(info.Actor, reqItem.Category, reqItem.Start.Time(), reqItem.End.Time(), reqItem.Quota)
			if err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			} else {
				result = append(result, fndapi.FindByStringId{Id: fmt.Sprint(id)})
			}
		}
		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: result}, nil
	})

	accapi.UpdateAllocation.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[accapi.UpdateAllocationRequest]) (util.Empty, *util.HttpError) {
		now := time.Now()
		if err := updateAllocationVerifyActor(info.Actor); err != nil {
			return util.Empty{}, err
		}

		for _, reqItem := range request.Items {
			category, err := updateAllocationVerifyOwner(info.Actor, AllocationId(reqItem.AllocationId))
			if err != nil {
				return util.Empty{}, err
			}

			_, _, err = AllocationUpdate(
				now,
				category,
				AllocationId(reqItem.AllocationId),
				reqItem.NewQuota,
				util.OptMap(reqItem.NewStart, func(value fndapi.Timestamp) time.Time { return value.Time() }),
				util.OptMap(reqItem.NewEnd, func(value fndapi.Timestamp) time.Time { return value.Time() }),
			)
			if err != nil {
				return util.Empty{}, err
			}
		}

		return util.Empty{}, nil
	})

	accapi.ReportUsage.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[accapi.ReportUsageRequest]) (fndapi.BulkResponse[bool], *util.HttpError) {
		now := time.Now()
		var result []bool
		for _, reqItem := range request.Items {
			if !validateOwner(reqItem.Owner) {
				return fndapi.BulkResponse[bool]{}, util.HttpErr(http.StatusBadRequest, "unknown owner specified")
			}
			resp, err := UsageReport(now, reqItem)
			if err != nil {
				return fndapi.BulkResponse[bool]{}, err
			} else {
				result = append(result, resp)
			}
		}
		return fndapi.BulkResponse[bool]{Responses: result}, nil
	})

	accapi.WalletsBrowse.Handler(func(info rpc.RequestInfo, request accapi.WalletsBrowseRequest) (fndapi.PageV2[accapi.WalletV2], *util.HttpError) {
		return WalletsBrowsePaginated(info.Actor, request), nil
	})

	accapi.WalletsBrowseInternal.Handler(func(info rpc.RequestInfo, request accapi.WalletsBrowseInternalRequest) (accapi.WalletsBrowseInternalResponse, *util.HttpError) {
		if !validateOwner(request.Owner) {
			return accapi.WalletsBrowseInternalResponse{}, util.HttpErr(http.StatusNotFound, "unknown owner")
		}

		wallets := WalletsBrowseOwnerAt(time.Now(), util.OptValue(request.Owner), WalletBrowseFilter{})
		return accapi.WalletsBrowseInternalResponse{Wallets: wallets}, nil
	})

	accapi.CheckProviderUsable.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[accapi.CheckProviderUsableRequest]) (fndapi.BulkResponse[accapi.CheckProviderUsableResponse], *util.HttpError) {
		var result []accapi.CheckProviderUsableResponse

		for _, reqItem := range request.Items {
			resultItem, err := WalletsCheckProviderUsable(info.Actor, reqItem.Owner, reqItem.Category)
			if err != nil {
				return fndapi.BulkResponse[accapi.CheckProviderUsableResponse]{}, err
			}

			result = append(result, resultItem)
		}

		return fndapi.BulkResponse[accapi.CheckProviderUsableResponse]{Responses: result}, nil
	})

	accapi.FindRelevantProviders.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[accapi.FindRelevantProvidersRequest]) (fndapi.BulkResponse[accapi.FindRelevantProvidersResponse], *util.HttpError) {
		var result []accapi.FindRelevantProvidersResponse

		for _, reqItem := range request.Items {
			var owners []accapi.WalletOwner

			if reqItem.UseProject {
				owner := accapi.WalletOwnerUser(reqItem.Username)
				if reqItem.Project.Present {
					owner = accapi.WalletOwnerProject(reqItem.Project.Value)
				}

				if validateOwner(owner) {
					owners = append(owners, owner)
				}
			} else {
				owners = append(owners, accapi.WalletOwnerUser(reqItem.Username))
				actor, ok := rpc.LookupActor(reqItem.Username)
				if ok {
					for project := range actor.Membership {
						owners = append(owners, accapi.WalletOwnerProject(string(project)))
					}
				}
			}

			result = append(result, FindRelevantProviders(owners, reqItem.FilterProductType))
		}

		return fndapi.BulkResponse[accapi.FindRelevantProvidersResponse]{Responses: result}, nil
	})

	accapi.FindAllProviders.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[accapi.FindAllProvidersRequest]) (fndapi.BulkResponse[accapi.FindAllProvidersResponse], *util.HttpError) {
		var result []accapi.FindAllProvidersResponse

		categories := ProductCategories()
		for _, reqItem := range request.Items {
			providers := map[string]util.Empty{}

			for _, cat := range categories {
				if cat.FreeToUse || reqItem.IncludeFreeToUse.GetOrDefault(false) {
					if !reqItem.FilterProductType.Present || reqItem.FilterProductType.Value == cat.ProductType {
						providers[cat.Provider] = util.Empty{}
					}
				}
			}

			var resp accapi.FindAllProvidersResponse
			for provider := range providers {
				resp.Providers = append(resp.Providers, provider)
			}
			result = append(result, resp)
		}

		return fndapi.BulkResponse[accapi.FindAllProvidersResponse]{Responses: result}, nil
	})
}

func updateAllocationVerifyActor(actor rpc.Actor) *util.HttpError {
	if !actor.Project.Present || !actor.Membership[actor.Project.Value].Satisfies(rpc.ProjectRoleAdmin) {
		return util.HttpErr(http.StatusForbidden, "You are not allowed to update allocations in this project")
	}
	return nil
}

func updateAllocationVerifyOwner(actor rpc.Actor, allocationId AllocationId) (accapi.ProductCategoryIdV2, *util.HttpError) {
	activeProject := string(actor.Project.Value)

	accGlobals.Mu.RLock()
	trees := make([]*AccountingTree, 0, len(accGlobals.Trees))
	for _, tree := range accGlobals.Trees {
		trees = append(trees, tree)
	}
	accGlobals.Mu.RUnlock()

	for _, tree := range trees {
		tree.Mu.RLock()
		allocation := tree.AllocationsById[allocationId]
		if allocation == nil {
			tree.Mu.RUnlock()
			continue
		}

		if !allocation.Parent.Present {
			tree.Mu.RUnlock()
			return accapi.ProductCategoryIdV2{}, util.HttpErr(http.StatusForbidden, "You are not allowed to update root allocations")
		}

		parent := tree.AllocationsById[allocation.Parent.Value]
		if parent == nil {
			tree.Mu.RUnlock()
			return accapi.ProductCategoryIdV2{}, util.HttpErr(http.StatusNotFound, "unknown parent allocation")
		}

		parentWallet := tree.WalletsById[parent.Wallet]
		if parentWallet == nil {
			tree.Mu.RUnlock()
			return accapi.ProductCategoryIdV2{}, util.HttpErr(http.StatusNotFound, "unknown parent wallet")
		}

		category := tree.Category.ToId()
		parentOwner := parentWallet.Owner
		tree.Mu.RUnlock()

		if parentOwner.Type != accapi.WalletOwnerTypeProject || parentOwner.ProjectId != activeProject {
			return accapi.ProductCategoryIdV2{}, util.HttpErr(http.StatusForbidden, "You are not allowed to update allocations created by another project")
		}

		return category, nil
	}

	return accapi.ProductCategoryIdV2{}, util.HttpErr(http.StatusNotFound, "unknown allocation")
}

var validatedOwners = lru.NewLRU[string, util.Empty](1024*4, nil, 10*time.Minute)

func validateOwner(owner accapi.WalletOwner) bool {
	_, valid := validatedOwners.Get(owner.Reference())
	if valid {
		return true
	}

	result := false
	switch owner.Type {
	case accapi.WalletOwnerTypeUser:
		_, ok := rpc.LookupActor(owner.Username)
		result = ok

	case accapi.WalletOwnerTypeProject:
		_, err := fndapi.ProjectRetrieveMetadata.Invoke(fndapi.FindByStringId{
			Id: owner.ProjectId,
		})
		result = err == nil
	}

	if result {
		validatedOwners.Add(owner.Reference(), util.Empty{})
	}

	return result
}

var (
	accountingScansDisabled              = atomic.Bool{}
	accountingProcessMutex               = sync.Mutex{}
	accountingScanUsageReportCanResumeAt = time.Now().Add(10 * time.Minute)
	usageReportSamplingHours             = []int{0, 4, 8, 12, 16, 20}
)

func accountingProcessTasksNow(now time.Time) {
	accountingProcessMutex.Lock()
	defer accountingProcessMutex.Unlock()

	timer := util.NewTimer()

	accountingPersist()

	// NOTE(Dan): This is a very simple version of a reliable cron-job which runs in our code and does not require
	// anything special at all. This only works because it is perfectly safe to sample too many times. This code does
	// reasonable protection against sampling too many times, but if the Core ends up crashing at the right time, then
	// multiple samples may occur. This is not a problem necessarily and may even be the correct thing to do, in the
	// case that the crash occurred mid-sampling.
	forceUsageReport := false
	if util.DevelopmentModeEnabled() {
		_, err := os.Stat("/tmp/usage_report_now")
		if err == nil {
			err = os.Remove("/tmp/usage_report_now")
			if err != nil {
				log.Info("Unlink err: %s", err)
			}
			forceUsageReport = true
		}
	}

	now = time.Now()
	if reportGlobals.Ready.Load() {
		if now.After(accountingScanUsageReportCanResumeAt) || forceUsageReport {
			if slices.Contains(usageReportSamplingHours, now.Hour()) && now.Minute() < 10 || forceUsageReport {
				accountingScanUsageReportCanResumeAt = time.Now().Add(15 * time.Minute)

				timer.Mark()
				usageSample(now)
				accountingSampleDuration.Observe(timer.Mark().Seconds())
			}
		}
	}
}

func accountingProcessTasks() {
	for {
		if !accountingScansDisabled.Load() {
			accountingProcessTasksNow(time.Now())
		}
		time.Sleep(30 * time.Second)
	}
}

var (
	accountingAllocationsUpdated = promauto.NewCounter(prometheus.CounterOpts{
		Namespace: "ucloud",
		Subsystem: "accounting",
		Name:      "allocations_updated_total",
		Help:      "Number of total allocations updated in the persistence layer",
	})

	accountingWalletsUpdated = promauto.NewCounter(prometheus.CounterOpts{
		Namespace: "ucloud",
		Subsystem: "accounting",
		Name:      "wallets_updated_total",
		Help:      "Number of total wallets updated in the persistence layer",
	})

	accountingScansDuration = promauto.NewSummary(prometheus.SummaryOpts{
		Namespace: "ucloud",
		Subsystem: "accounting",
		Name:      "scan_duration_seconds",
		Help:      "Summary of the duration (in seconds) it takes to complete a scan",
		Objectives: map[float64]float64{
			0.5:  0.01,
			0.75: 0.01,
			0.95: 0.01,
			0.99: 0.01,
		},
	})

	accountingSampleDuration = promauto.NewSummary(prometheus.SummaryOpts{
		Namespace: "ucloud",
		Subsystem: "accounting",
		Name:      "sample_duration_seconds",
		Help:      "Summary of the duration (in seconds) it takes to complete a usage sampling cycle",
		Objectives: map[float64]float64{
			0.5:  0.01,
			0.75: 0.01,
			0.95: 0.01,
			0.99: 0.01,
		},
	})
)
