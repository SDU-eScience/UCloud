package accounting

import (
	"fmt"
	"net/http"
	"strings"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var usageGenTimeBased = accapi.ProductV2{
	Type: accapi.ProductTypeCCompute,
	Category: accapi.ProductCategory{
		Name:        "cpu",
		Provider:    "usagegen",
		ProductType: accapi.ProductTypeCompute,
		AccountingUnit: accapi.AccountingUnit{
			Name:                   "Core",
			NamePlural:             "Core",
			FloatingPoint:          false,
			DisplayFrequencySuffix: true,
		},
		AccountingFrequency: accapi.AccountingFrequencyPeriodicMinute,
		FreeToUse:           false,
		AllowSubAllocations: true,
	},
	Name:                      "cpu",
	Description:               "CPU product for usage gen",
	ProductType:               accapi.ProductTypeCompute,
	Price:                     1,
	HiddenInGrantApplications: true,
	Cpu:                       1,
	MemoryInGigs:              1,
}

var usageGenCapacityBased = accapi.ProductV2{
	Type: accapi.ProductTypeCStorage,
	Category: accapi.ProductCategory{
		Name:        "storage",
		Provider:    "usagegen",
		ProductType: accapi.ProductTypeStorage,
		AccountingUnit: accapi.AccountingUnit{
			Name:       "GB",
			NamePlural: "GB",
		},
		AccountingFrequency: accapi.AccountingFrequencyOnce,
		FreeToUse:           false,
		AllowSubAllocations: true,
	},
	Name:                      "storage",
	Description:               "Storage product for usage gen",
	ProductType:               accapi.ProductTypeStorage,
	HiddenInGrantApplications: true,
}

var usageGenProducts = []accapi.ProductV2{
	usageGenTimeBased,
	usageGenCapacityBased,
}

func initUsageGenerator() {
	if util.DevelopmentModeEnabled() {
		// NOTE(Dan): This code is not guaranteed to be safe to run on a real system. It is likely to accidentally
		// activate/retire allocations which shouldn't be.

		for _, p := range usageGenProducts {
			_, err := ProductRetrieve(rpc.ActorSystem, accapi.ProductsFilter{
				FilterName:     util.OptValue(p.Name),
				FilterCategory: util.OptValue(p.Category.Name),
				FilterProvider: util.OptValue(p.Category.Provider),
			})

			if err != nil {
				err = ProductCreate(rpc.ActorSystem, []accapi.ProductV2{p})
				if err != nil {
					log.Fatal("Could not generate usage gen product (%#v): %s", p, err)
				}
			}
		}

		accapi.UsageGenerate.Handler(func(info rpc.RequestInfo, request accapi.UsageGenConfig) (util.Empty, *util.HttpError) {
			return util.Empty{}, usageGenReal(info.Actor, request)
		})
	}
}

func usageGenReal(actor rpc.Actor, request accapi.UsageGenConfig) *util.HttpError {
	timeStart := util.StartOfDayUTC(time.Now()).AddDate(0, -6, 0)
	timeEnd := util.StartOfDayUTC(time.Now()).AddDate(0, 1, 0)

	titleBase := fmt.Sprintf("usegen_%v", time.Now().Format(time.DateTime))

	rootProject, err := fndapi.ProjectInternalCreate.Invoke(fndapi.ProjectInternalCreateRequest{
		Title:      titleBase,
		BackendId:  titleBase,
		PiUsername: actor.Username,
	})

	if err != nil {
		return util.HttpErr(http.StatusInternalServerError, "could not create root project: %s", err)
	}

	providerId := rpc.ProviderId(usageGenTimeBased.Category.Provider)

	providerActor := actor
	providerActor.Project = util.OptValue(rpc.ProjectId(rootProject.Id))
	providerActor.Membership = rpc.ProjectMembership{
		rpc.ProjectId(rootProject.Id): rpc.ProjectRolePI,
	}
	providerActor.ProviderProjects = rpc.ProviderProjects{
		providerId: rpc.ProjectId(rootProject.Id),
	}

	for _, p := range usageGenProducts {
		_, err = RootAllocate(providerActor, accapi.RootAllocateRequest{
			Category: p.Category.ToId(),
			Quota:    100_000_000_000,
			Start:    fndapi.Timestamp(timeStart),
			End:      fndapi.Timestamp(timeEnd),
		})

		if err != nil {
			return util.HttpErr(http.StatusInternalServerError, "could not root allocate: %s", err)
		}
	}

	projectsByRef := map[string]string{
		"": rootProject.Id,
	}

	tm := func(when int) time.Time {
		return timeStart.Add(time.Duration(when) * time.Minute)
	}

	b := internalBucketOrInit(usageGenTimeBased.Category)

	apiBaseTitle := ""
	var apiFailure *util.HttpError = nil
	api := UsageGenApi{
		AllocateEx: func(now, start, end int, quota int64, recipientRef, parentRef string) {
			if apiFailure != nil {
				return
			}

			if parentRef == "" {
				apiBaseTitle = recipientRef
				projectsByRef[recipientRef] = rootProject.Id
				return
			}

			nowTime := tm(now)
			startTime := tm(start)
			endTime := tm(end)
			if !request.Expiration {
				endTime = timeEnd
			}

			// Create sub-project
			// ---------------------------------------------------------------------------------------------------------
			suffixTitle, _ := strings.CutPrefix(recipientRef, apiBaseTitle)

			subProject, err := fndapi.ProjectInternalCreate.Invoke(fndapi.ProjectInternalCreateRequest{
				Title:      titleBase + suffixTitle,
				BackendId:  titleBase + suffixTitle,
				PiUsername: actor.Username,
			})

			if err != nil {
				apiFailure = err
				return
			}

			projectsByRef[recipientRef] = subProject.Id

			// Allocate resources from parent
			// ---------------------------------------------------------------------------------------------------------
			recipientOwner := internalOwnerByReference(subProject.Id)
			recipientWallet := internalWalletByOwner(b, nowTime, recipientOwner.Id)

			parentId := projectsByRef[parentRef]
			senderOwner := internalOwnerByReference(parentId)
			senderWallet := internalWalletByOwner(b, nowTime, senderOwner.Id)

			log.Info("Allocating %v -> %v (%v -> %v): %v", parentId, subProject.Id, senderWallet, recipientWallet, quota)

			var allocId accAllocId
			allocId, err = internalAllocateNoCommit(nowTime, b, startTime, endTime, quota, recipientWallet,
				senderWallet, util.OptNone[accGrantId]())

			if err != nil {
				apiFailure = err
				return
			}

			internalCommitAllocation(b, allocId)

			maxUsable, ok := internalMaxUsable(nowTime, recipientWallet)
			if ok && maxUsable == 0 {
				log.Info("max usable is now %v", maxUsable)
			}
		},

		ReportDelta: func(now int, ownerRef string, usage int64) {
			if apiFailure != nil {
				return
			}

			nowTime := tm(now)
			owner := internalOwnerByReference(projectsByRef[ownerRef])
			wallet := internalWalletByOwner(b, nowTime, owner.Id)
			maxUsable, ok := internalMaxUsable(nowTime, wallet)
			if ok && maxUsable == 0 {
				log.Info("delta: max usable is now %v", maxUsable)
			}

			_, err := internalReportUsage(nowTime, accapi.ReportUsageRequest{
				IsDeltaCharge: true,
				Owner:         owner.WalletOwner(),
				CategoryIdV2:  usageGenTimeBased.Category.ToId(),
				Usage:         usage,
				Description:   accapi.ChargeDescription{},
			})

			if err != nil {
				apiFailure = err
			}
		},

		Checkpoint: func(now int) {
			if apiFailure != nil {
				return
			}

			nowTime := tm(now)

			accountingProcessTasksNow(nowTime, func(bucket *internalBucket) bool {
				return b.Category.Name == bucket.Category.Name && b.Category.Provider == bucket.Category.Provider
			})

			usageSampleEx(nowTime, func(cat accapi.ProductCategory) bool {
				return b.Category.Name == cat.Name && b.Category.Provider == cat.Provider
			})
		},
	}

	accountingScansDisabled.Store(true)
	UsageGenGenerate(api, request)
	accountingScansDisabled.Store(false)
	return apiFailure
}
