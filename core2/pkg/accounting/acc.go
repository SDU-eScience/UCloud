package accounting

import (
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
		/*
			var result []fndapi.FindByStringId
			for _, reqItem := range request.Items {
				id, err := RootAllocate(info.Actor, reqItem)
				if err != nil {
					return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
				} else {
					result = append(result, fndapi.FindByStringId{Id: id})
				}
			}
			return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: result}, nil
		*/
		return fndapi.BulkResponse[fndapi.FindByStringId]{}, nil
	})

	accapi.UpdateAllocation.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[accapi.UpdateAllocationRequest]) (util.Empty, *util.HttpError) {
		//return AllocationUpdate(info.Actor, request.Items)
		return util.Empty{}, nil
	})

	accapi.ReportUsage.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[accapi.ReportUsageRequest]) (fndapi.BulkResponse[bool], *util.HttpError) {
		now := time.Now()
		var result []bool
		for _, reqItem := range request.Items {
			if true {
				return fndapi.BulkResponse[bool]{}, nil // TODO Validate actor
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
		//now := time.Now()
		//return WalletsBrowsePage(now, request, WalletBrowseFilter{
		//	Owner: util.OptValue(actorToOwner(info.Actor)),
		//}), nil
		return fndapi.PageV2[accapi.WalletV2]{}, nil
	})

	accapi.WalletsBrowseInternal.Handler(func(info rpc.RequestInfo, request accapi.WalletsBrowseInternalRequest) (accapi.WalletsBrowseInternalResponse, *util.HttpError) {
		/*
			if !validateOwner(request.Owner) {
				return accapi.WalletsBrowseInternalResponse{}, util.HttpErr(http.StatusNotFound, "unknown owner")
			} else {
				wallets := internalRetrieveWallets(time.Now(), request.Owner.Reference(), walletFilter{
					RequireActive: false,
				})

				return accapi.WalletsBrowseInternalResponse{Wallets: wallets}, nil
			}
		*/
		return accapi.WalletsBrowseInternalResponse{}, nil
	})

	accapi.CheckProviderUsable.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[accapi.CheckProviderUsableRequest]) (fndapi.BulkResponse[accapi.CheckProviderUsableResponse], *util.HttpError) {
		/*
			now := time.Now()

			providerId, ok := strings.CutPrefix(fndapi.ProviderSubjectPrefix, info.Actor.Username)
			if !ok {
				return fndapi.BulkResponse[accapi.CheckProviderUsableResponse]{}, util.HttpErr(http.StatusForbidden, "forbidden")
			}

			var result []accapi.CheckProviderUsableResponse

			for _, reqItem := range request.Items {
				ok = reqItem.Category.Provider == providerId && validateOwner(reqItem.Owner)
				wallet := AccWalletId(0)
				maxUsable := int64(0)

				if ok {
					wallet, ok = internalWalletByReferenceAndCategory(now, reqItem.Owner.Reference(), reqItem.Category)
				}

				if ok {
					maxUsable, ok = internalMaxUsable(now, wallet)
				}

				if ok {
					result = append(result, accapi.CheckProviderUsableResponse{MaxUsable: maxUsable})
				} else {
					return fndapi.BulkResponse[accapi.CheckProviderUsableResponse]{}, util.HttpErr(http.StatusBadRequest, "invalid request")
				}
			}

			return fndapi.BulkResponse[accapi.CheckProviderUsableResponse]{Responses: result}, nil
		*/
		return fndapi.BulkResponse[accapi.CheckProviderUsableResponse]{}, nil
	})

	accapi.FindRelevantProviders.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[accapi.FindRelevantProvidersRequest]) (fndapi.BulkResponse[accapi.FindRelevantProvidersResponse], *util.HttpError) {
		/*
			now := time.Now()

			var result []accapi.FindRelevantProvidersResponse

			for _, reqItem := range request.Items {
				var owners []accapi.WalletOwner

				if reqItem.UseProject {
					owner := accapi.WalletOwnerUser(reqItem.Username)
					if reqItem.Project.Present {
						owner = accapi.WalletOwnerProject(reqItem.Project.Value)
					}

					owners = append(owners, owner)
				} else {
					owners = append(owners, accapi.WalletOwnerUser(reqItem.Username))
					actor, ok := rpc.LookupActor(reqItem.Username)
					if ok {
						for project := range actor.Membership {
							owners = append(owners, accapi.WalletOwnerProject(string(project)))
						}
					}
				}

				providers := map[string]util.Empty{}

				for _, owner := range owners {
					if validateOwner(owner) {
						wallets := internalRetrieveWallets(now, owner.Reference(), walletFilter{
							ProductType:   reqItem.FilterProductType,
							RequireActive: true,
						})

						// TODO free to use

						for _, w := range wallets {
							providers[w.PaysFor.Provider] = util.Empty{}
						}

					} else {
						return fndapi.BulkResponse[accapi.FindRelevantProvidersResponse]{}, util.HttpErr(http.StatusBadRequest, "bad owner supplied")
					}
				}

				var providerArr []string
				for providerId := range providers {
					providerArr = append(providerArr, providerId)
				}

				result = append(result, accapi.FindRelevantProvidersResponse{Providers: providerArr})
			}

			return fndapi.BulkResponse[accapi.FindRelevantProvidersResponse]{Responses: result}, nil
		*/
		return fndapi.BulkResponse[accapi.FindRelevantProvidersResponse]{}, nil
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
		}

		return fndapi.BulkResponse[accapi.FindAllProvidersResponse]{Responses: result}, nil
	})
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
