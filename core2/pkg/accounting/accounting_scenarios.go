package accounting

import (
	"fmt"
	"math/rand/v2"
	"net"
	"net/http"
	"slices"
	"strings"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type accountingScenarioRequest struct {
	Scenario string `json:"scenario"`
}

type accountingScenarioProject struct {
	Id         string `json:"id"`
	Title      string `json:"title"`
	PiUsername string `json:"piUsername"`
}

type accountingScenarioCheckpoint struct {
	Label            string                             `json:"label"`
	Now              int64                              `json:"now"`
	Wallets          []accapi.WalletV2                  `json:"wallets"`
	WalletsByProject map[string][]accapi.WalletV2       `json:"walletsByProject"`
	Expected         accountingScenarioExpected         `json:"expected"`
	ChildProjectId   string                             `json:"childProjectId"`
	Category         accapi.ProductCategoryIdV2         `json:"category"`
	Actual           accountingScenarioExpected         `json:"actual"`
	ChecksByProject  map[string]accountingScenarioCheck `json:"checksByProject,omitempty"`
}

type accountingScenarioCheck struct {
	Expected accountingScenarioExpected `json:"expected"`
	Actual   accountingScenarioExpected `json:"actual"`
}

type accountingScenarioResult struct {
	RunId         string                               `json:"runId"`
	FormatVersion int                                  `json:"formatVersion"`
	Scenario      string                               `json:"scenario"`
	Description   string                               `json:"description"`
	ProjectId     string                               `json:"projectId"`
	ProjectInfo   map[string]accountingScenarioProject `json:"projectInfo"`
	Projects      map[string]string                    `json:"projects"`
	Category      accapi.ProductCategoryIdV2           `json:"category"`
	Categories    []accapi.ProductCategoryIdV2         `json:"categories,omitempty"`
	AllocationIds []int64                              `json:"allocationIds"`
	GrantIds      []int64                              `json:"grantIds"`
	Checkpoints   []accountingScenarioCheckpoint       `json:"checkpoints"`
	UsageSamples  int                                  `json:"usageSamples"`
	Failure       string                               `json:"failure,omitempty"`
}

var accountingScenarioRunCall = rpc.Call[accountingScenarioRequest, accountingScenarioResult]{
	BaseContext: "accounting/v2/usage",
	Convention:  rpc.ConventionUpdate,
	Operation:   "runScenario",
	Roles:       rpc.RolesPrivileged,
}

func accountingScenarioInit() {
	if !util.DevelopmentModeEnabled() {
		return
	}
	accountingScenarioRunCall.Handler(func(info rpc.RequestInfo, request accountingScenarioRequest) (accountingScenarioResult, *util.HttpError) {
		piUsername := info.Actor.Username
		launcherRun := info.Actor.Role == rpc.RoleService
		if launcherRun {
			host, _, _ := net.SplitHostPort(info.HttpRequest.RemoteAddr)
			if info.Actor.Username != rpc.ActorSystem.Username || !net.ParseIP(host).IsLoopback() || !accountingScenarioLauncherCore() {
				return accountingScenarioResult{}, util.HttpErr(http.StatusForbidden, "Forbidden")
			}
			piUsername = "user"
		}
		isLongRunning := request.Scenario == accountingScenarioPortfolioName || request.Scenario == accountingScenarioUsageAllocatorName
		if isLongRunning {
			controller := http.NewResponseController(info.HttpWriter)
			_ = controller.SetWriteDeadline(time.Now().Add(accountingScenarioPortfolioTimeout))
		}
		return accountingScenarioRun(piUsername, request.Scenario, launcherRun)
	})
}

func accountingScenarioBuildProducts(runId string, definition accountingScenarioDefinition) ([]string, map[string]accapi.ProductV2, map[string]int64) {
	if len(definition.Products) == 0 {
		product := usageGenTimeBasedCPUOne
		if definition.Capacity {
			product = usageGenCapacityBased
		}
		product.Category.Name = runId
		product.Name = runId
		product.Description = definition.Description
		product.Category.AccountingFrequency = accapi.AccountingFrequencyPeriodicHour
		if definition.MinuteUnits {
			product.Category.AccountingFrequency = accapi.AccountingFrequencyPeriodicMinute
		}
		if definition.Capacity {
			product.Category.AccountingFrequency = accapi.AccountingFrequencyOnce
		}
		rootQuota := definition.RootQuota
		if rootQuota == 0 {
			rootQuota = 1000000
		}
		return []string{""},
			map[string]accapi.ProductV2{"": product},
			map[string]int64{"": rootQuota}
	}

	keys := make([]string, 0, len(definition.Products))
	products := make(map[string]accapi.ProductV2, len(definition.Products))
	rootQuotas := make(map[string]int64, len(definition.Products))

	for _, spec := range definition.Products {
		name := runId + "-" + spec.Slug
		product := accapi.ProductV2{
			Type: accapi.ProductTypeCCreate(spec.ProductType),
			Category: accapi.ProductCategory{
				Name:                name,
				Provider:            "usagegen",
				ProductType:         spec.ProductType,
				AccountingUnit:      spec.Unit,
				AccountingFrequency: spec.Frequency,
				AccountingModel:     spec.AccountingModel,
				FreeToUse:           false,
				AllowSubAllocations: true,
			},
			Name:                      name,
			Description:               definition.Description,
			ProductType:               spec.ProductType,
			Price:                     1,
			HiddenInGrantApplications: true,
		}
		if spec.ProductType == accapi.ProductTypeCompute {
			product.Cpu = 1
			product.MemoryInGigs = 1
		}
		rootQuota := spec.RootQuota
		if rootQuota == 0 {
			rootQuota = definition.RootQuota
		}
		if rootQuota == 0 {
			rootQuota = 1000000
		}
		keys = append(keys, spec.Key)
		products[spec.Key] = product
		rootQuotas[spec.Key] = rootQuota
	}

	return keys, products, rootQuotas
}

func accountingScenarioRun(piUsername string, name string, launcherRun bool) (accountingScenarioResult, *util.HttpError) {
	if !util.DevelopmentModeEnabled() {
		return accountingScenarioResult{}, util.HttpErr(http.StatusForbidden, "accounting scenarios are only available in development")
	}
	definitions := accountingScenarioDefinitions()
	index := slices.IndexFunc(definitions, func(definition accountingScenarioDefinition) bool {
		return definition.Name == name
	})
	if index < 0 {
		return accountingScenarioResult{}, util.HttpErr(http.StatusBadRequest, "unknown accounting scenario %q", name)
	}
	definition := definitions[index]
	now := time.Now().UTC().Truncate(time.Millisecond)
	runId := name + "-" + util.RandomTokenNoTs(6)
	var shortId [4]byte
	for i := range shortId {
		shortId[i] = byte('A' + rand.IntN(26))
	}
	result := accountingScenarioResult{
		FormatVersion: 1,
		RunId:         runId,
		Scenario:      name,
		Description:   definition.Description,
		Projects:      map[string]string{},
		ProjectInfo:   map[string]accountingScenarioProject{},
	}
	roles := []string{"root"}
	firstDay := -365
	for _, step := range definition.Steps {
		firstDay = min(firstDay, step.At)
		owner := step.Owner
		if owner == "" {
			owner = "workspace"
		}
		for _, role := range []string{step.Parent, owner} {
			if role != "" && !slices.Contains(roles, role) {
				roles = append(roles, role)
			}
		}
	}
	for _, role := range roles {
		subCode := strings.ToUpper(role)
		switch role {
		case "workspace":
			subCode = "WORK"
		case "downstream":
			subCode = "DOWN"
		}
		title := fmt.Sprintf("AS-%s-%s-%s", definition.Code, shortId, subCode)
		projectPi := piUsername
		if launcherRun && len(definition.UserOwnedRoles) > 0 {
			if !slices.Contains(definition.UserOwnedRoles, role) {
				projectPi = rpc.ActorSystem.Username
			}
		} else if launcherRun && (name == accountingScenarioPortfolioName || name == accountingScenarioUsageAllocatorName) && role != "root" {
			projectPi = rpc.ActorSystem.Username
		}
		subAllocatorOpt := util.OptNone[bool]()
		if slices.Contains(definition.SubAllocatorRoles, role) {
			subAllocatorOpt.Set(true)
		}
		project, err := fndapi.ProjectInternalCreate.Invoke(fndapi.ProjectInternalCreateRequest{
			Title:        title,
			BackendId:    "accounting-scenario-" + runId + "-" + role,
			PiUsername:   projectPi,
			SubAllocator: subAllocatorOpt,
		})
		if err != nil {
			result.Failure = err.Error()
			return result, nil
		}
		result.Projects[role] = project.Id
		result.ProjectInfo[project.Id] = accountingScenarioProject{
			Id:         project.Id,
			Title:      title,
			PiUsername: projectPi,
		}
	}
	primaryRole := definition.PrimaryProjectRole
	if primaryRole == "" {
		primaryRole = "root"
	}
	result.ProjectId = result.Projects[primaryRole]

	productKeys, productsByKey, rootQuotasByKey := accountingScenarioBuildProducts(runId, definition)
	productsToCreate := make([]accapi.ProductV2, 0, len(productKeys))
	for _, key := range productKeys {
		productsToCreate = append(productsToCreate, productsByKey[key])
	}
	err := ProductCreate(rpc.ActorSystem, productsToCreate)
	if err != nil {
		result.Failure = err.Error()
		return result, nil
	}

	primaryKey := productKeys[0]
	bucketsByKey := make(map[string]*internalBucket, len(productKeys))
	allBuckets := make([]*internalBucket, 0, len(productKeys))
	categorySet := make(map[accapi.ProductCategoryIdV2]util.Empty, len(productKeys))
	for _, key := range productKeys {
		cat := productsByKey[key].Category
		catId := cat.ToId()
		result.Categories = append(result.Categories, catId)
		categorySet[catId] = util.Empty{}
		b := internalBucketOrInit(cat)
		bucketsByKey[key] = b
		allBuckets = append(allBuckets, b)
	}
	primaryCat := productsByKey[primaryKey].Category
	result.Category = primaryCat.ToId()

	resolveProductKey := func(stepProduct string) string {
		if stepProduct == "" {
			return primaryKey
		}
		return stepProduct
	}

	at := func(day int) time.Time {
		return now.AddDate(0, 0, day)
	}

	bucketFilter := func(cat accapi.ProductCategory) bool {
		_, ok := categorySet[cat.ToId()]
		return ok
	}

	bucketPtrFilter := func(b *internalBucket) bool {
		return bucketFilter(b.Category)
	}

	accountingScansDisabled.Store(true)
	defer accountingScansDisabled.Store(false)

	walletsByProduct := make(map[string]map[string]AccWalletId, len(productKeys))
	initErr := func() *util.HttpError {
		accountingProcessMutex.Lock()
		defer accountingProcessMutex.Unlock()

		for _, pKey := range productKeys {
			b := bucketsByKey[pKey]
			wMap := make(map[string]AccWalletId, len(roles))
			for _, role := range roles {
				owner := internalOwnerByReference(result.Projects[role])
				wMap[role] = internalWalletByOwner(b, at(firstDay), owner.Id)
			}
			walletsByProduct[pKey] = wMap

			rootAllocation, allocErr := internalAllocateNoCommit(
				at(firstDay),
				b,
				at(firstDay),
				at(365),
				rootQuotasByKey[pKey],
				wMap["root"],
				internalGraphRoot,
				util.OptNone[accGrantId](),
			)
			if allocErr != nil {
				return allocErr
			}
			internalCommitAllocation(b, at(firstDay), rootAllocation)
			result.AllocationIds = append(result.AllocationIds, int64(rootAllocation))
		}
		return nil
	}()
	if initErr != nil {
		result.Failure = initErr.Error()
		return result, nil
	}

	accountingProcessTasksNow(at(firstDay), bucketPtrFilter)

	accountingProcessMutex.Lock()
	replayErr := func() *util.HttpError {
		defer accountingProcessMutex.Unlock()

		grantByStep := map[int]accGrantId{}
		if !definition.SkipGrantRecords {
			for stepIndex, step := range definition.Steps {
				if step.Kind != "allocate" {
					continue
				}
				if _, exists := grantByStep[stepIndex]; exists {
					continue
				}
				pKey := resolveProductKey(step.Product)
				category := productsByKey[pKey].Category

				groupedSteps := []int{stepIndex}
				if step.Package != "" {
					for next := stepIndex + 1; next < len(definition.Steps); next++ {
						candidate := definition.Steps[next]
						samePackage := candidate.Kind == "allocate" && candidate.Package == step.Package
						sameProduct := resolveProductKey(candidate.Product) == pKey
						sameRelationship := candidate.Parent == step.Parent && candidate.Owner == step.Owner
						if samePackage && sameProduct && sameRelationship {
							groupedSteps = append(groupedSteps, next)
						}
					}
				}
				parent := step.Parent
				if parent == "" {
					parent = "root"
				}
				owner := step.Owner
				if owner == "" {
					owner = "workspace"
				}
				requests := make([]accapi.AllocationRequest, 0, len(groupedSteps))
				start, end := at(step.Start), at(step.End)
				for _, groupedIndex := range groupedSteps {
					slice := definition.Steps[groupedIndex]
					sliceStart, sliceEnd := at(slice.Start), at(slice.End)
					if sliceStart.Before(start) {
						start = sliceStart
					}
					if sliceEnd.After(end) {
						end = sliceEnd
					}
					requests = append(requests, accapi.AllocationRequest{
						Category:         category.Name,
						Provider:         category.Provider,
						GrantGiver:       result.Projects[parent],
						BalanceRequested: util.OptValue(slice.Quota),
						Period: accapi.Period{
							Start: util.OptValue(fndapi.Timestamp(at(slice.Start))),
							End:   util.OptValue(fndapi.Timestamp(at(slice.End))),
						},
						AllocationPackage: slice.Package != "",
					})
				}
				grantId := accountingScenarioGrant(
					piUsername,
					result.ProjectInfo[result.Projects[owner]].PiUsername,
					result.Projects[owner],
					result.Projects[parent],
					at(step.At),
					start,
					end,
					requests,
				)
				result.GrantIds = append(result.GrantIds, int64(grantId))
				for _, groupedIndex := range groupedSteps {
					grantByStep[groupedIndex] = grantId
				}
			}
		}

		var lastSample time.Time
		sampleInterval := time.Duration(definition.SampleIntervalHours) * time.Hour

		sampleAt := func(ts time.Time) {
			if !lastSample.IsZero() && !ts.After(lastSample) {
				return
			}
			for _, b := range allBuckets {
				internalScanAllocations(b, ts)
			}
			usageSampleEx(ts, bucketFilter)
			lastSample = ts
			result.UsageSamples++
		}

		fillSamplesBefore := func(target time.Time) {
			if sampleInterval <= 0 || lastSample.IsZero() {
				return
			}
			cursor := lastSample.Add(sampleInterval)
			for cursor.Before(target) {
				sampleAt(cursor)
				cursor = cursor.Add(sampleInterval)
			}
		}

		var pendingSampleTime time.Time
		hasPendingSample := false

		for stepIndex, step := range definition.Steps {
			when := at(step.At).Add(time.Duration(step.AtMillis) * time.Millisecond)
			if hasPendingSample && when.After(pendingSampleTime) {
				sampleAt(pendingSampleTime)
				hasPendingSample = false
			}
			fillSamplesBefore(when)

			pKey := resolveProductKey(step.Product)
			bucket := bucketsByKey[pKey]
			category := productsByKey[pKey].Category
			wallets := walletsByProduct[pKey]

			owner := step.Owner
			if owner == "" {
				owner = "workspace"
			}
			parent := step.Parent
			if parent == "" {
				parent = "root"
			}

			switch step.Kind {
			case "allocate":
				grantOpt := util.OptNone[accGrantId]()
				if grantId, ok := grantByStep[stepIndex]; ok {
					grantOpt.Set(grantId)
				}
				allocationId, allocErr := internalAllocateNoCommit(
					when,
					bucket,
					at(step.Start),
					at(step.End),
					step.Quota,
					wallets[owner],
					wallets[parent],
					grantOpt,
				)
				if allocErr != nil {
					return allocErr
				}
				bucket.Mu.Lock()
				bucket.AllocationsById[allocationId].AllocationPackage = step.Package != ""
				bucket.Mu.Unlock()
				internalCommitAllocation(bucket, when, allocationId)
				result.AllocationIds = append(result.AllocationIds, int64(allocationId))
				pendingSampleTime = when
				hasPendingSample = true
			case "charge":
				_, reportErr := internalReportUsage(when, accapi.ReportUsageRequest{
					IsDeltaCharge: true,
					Owner:         accapi.WalletOwnerProject(result.Projects[owner]),
					CategoryIdV2:  category.ToId(),
					Usage:         step.Quota,
					Description:   accapi.ChargeDescription{},
				})
				if reportErr != nil {
					return reportErr
				}
				pendingSampleTime = when
				hasPendingSample = true
			case "sample":
				sampleAt(when)
				hasPendingSample = false
			case "checkpoint":
				for _, b := range allBuckets {
					internalScanAllocations(b, when)
					invariantErr := internalValidateAccountingTree(b, when)
					if invariantErr != nil {
						return util.HttpErr(http.StatusInternalServerError, "%s: %s", step.Label, invariantErr)
					}
				}
				isCapacity := category.AccountingModel == accapi.AccountingModelCapacity
				checkpoint := accountingScenarioCheckpoint{
					Label:            step.Label,
					Now:              when.UnixMilli(),
					WalletsByProject: map[string][]accapi.WalletV2{},
					ChildProjectId:   result.Projects[owner],
					Category:         category.ToId(),
				}
				if step.Expected != nil {
					checkpoint.Expected = *step.Expected
				}
				for _, role := range roles {
					wallet, ok := internalRetrieveWallet(when, wallets[role], true)
					if !ok {
						return util.HttpErr(http.StatusInternalServerError, "missing scenario wallet")
					}
					checkpoint.WalletsByProject[result.Projects[role]] = []accapi.WalletV2{wallet}
				}
				checkpoint.Wallets = checkpoint.WalletsByProject[result.Projects["root"]]
				actual := accountingScenarioReadAmounts(checkpoint.Wallets[0], checkpoint.ChildProjectId, isCapacity, when)
				checkpoint.Actual = actual
				var mismatch *util.HttpError
				if len(step.ExpectedByOwner) > 0 {
					checkpoint.ChecksByProject = make(map[string]accountingScenarioCheck, len(step.ExpectedByOwner))
					for _, role := range roles {
						expected, ok := step.ExpectedByOwner[role]
						if !ok {
							continue
						}
						projectId := result.Projects[role]
						amounts := accountingScenarioReadAmounts(checkpoint.Wallets[0], projectId, isCapacity, when)
						checkpoint.ChecksByProject[projectId] = accountingScenarioCheck{
							Expected: expected,
							Actual:   amounts,
						}
						if amounts != expected && mismatch == nil {
							mismatch = util.HttpErr(http.StatusInternalServerError, "%s (%s): expected %+v; got %+v", step.Label, role, expected, amounts)
						}
					}
				}
				result.Checkpoints = append(result.Checkpoints, checkpoint)
				if mismatch != nil {
					return mismatch
				}
				if step.Expected != nil && actual != *step.Expected {
					return util.HttpErr(http.StatusInternalServerError, "%s: expected %+v; got %+v", step.Label, *step.Expected, actual)
				}
				pendingSampleTime = when
				hasPendingSample = true
			}
		}

		if hasPendingSample {
			sampleAt(pendingSampleTime)
		}
		fillSamplesBefore(now)
		sampleAt(now)

		return nil
	}()
	accountingProcessTasksNow(time.Now(), nil)
	db.NewTx0(func(tx *db.Transaction) {
		for _, id := range result.GrantIds {
			db.Exec(
				tx,
				`
					update "grant".applications
					set synchronized = true
					where id = :id
				`,
				db.Params{
					"id": id,
				},
			)
		}
	})
	if replayErr != nil {
		result.Failure = replayErr.Error()
	}
	return result, nil
}

func accountingScenarioReadAmounts(wallet accapi.WalletV2, childId string, capacity bool, now time.Time) accountingScenarioExpected {
	result := accountingScenarioExpected{}
	for _, child := range wallet.Children {
		if child.Child.Value.ProjectId != childId {
			continue
		}
		group := child.Group
		result.Used += group.Usage
		result.ActiveQuota += group.UiOnlyActiveQuota
		result.ActiveUsed += group.UiOnlyActiveUsage
		result.ActiveBalance += group.UiOnlyActiveQuota - group.UiOnlyActiveUsage
		for _, allocation := range group.Allocations {
			quota := allocation.Quota
			if allocation.Retired {
				quota = allocation.RetiredQuota
				if !capacity {
					result.RetiredUsed += allocation.RetiredUsage
					result.ExpiredUnused += quota - allocation.RetiredUsage
				}
			} else if allocation.StartDate.Time().After(now) {
				result.Future += quota
			}
			result.Granted += quota
		}
	}
	return result
}

func accountingScenarioGrant(
	username string,
	projectPi string,
	recipient string,
	parent string,
	now time.Time,
	start time.Time,
	end time.Time,
	requests []accapi.AllocationRequest,
) accGrantId {
	id := accGrantId(grantGlobals.GrantIdAcc.Add(1))
	revision := accapi.GrantRevision{
		CreatedAt:      fndapi.Timestamp(now),
		UpdatedBy:      username,
		RevisionNumber: 1,
		Document: accapi.GrantDocument{
			Recipient: accapi.Recipient{
				Type: accapi.RecipientTypeExistingProject,
				Id:   util.OptValue(recipient),
			},
			AllocationRequests: requests,
			Form: accapi.Form{
				Type: accapi.FormTypePlainText,
				Text: "Development accounting scenario. Usage is allocation attribution, not consumption within the allocation dates.",
			},
			AllocationPeriod: util.OptValue(accapi.Period{
				Start: util.OptValue(fndapi.Timestamp(start)),
				End:   util.OptValue(fndapi.Timestamp(end)),
			}),
		},
	}
	app := &grantApplication{
		Awarded: true,
		Application: &accapi.GrantApplication{
			Id:              util.IntOrString{Value: fmt.Sprint(id)},
			CreatedBy:       username,
			CreatedAt:       fndapi.Timestamp(now),
			UpdatedAt:       fndapi.Timestamp(now),
			ProjectId:       util.OptValue(recipient),
			CurrentRevision: revision,
			Status: accapi.GrantStatus{
				OverallState: accapi.GrantApplicationStateApproved,
				ProjectPI:    projectPi,
				Revisions:    []accapi.GrantRevision{revision},
				StateBreakdown: []accapi.GrantGiverApprovalState{{
					ProjectId:     parent,
					LastUpdatedBy: username,
					State:         accapi.GrantApplicationStateApproved,
				}},
			},
		},
	}
	lGrantsPersist(app)
	bucket := grantGetAppBucket(id)
	bucket.Mu.Lock()
	bucket.Applications[id] = app
	bucket.Mu.Unlock()
	for _, entity := range []string{recipient, parent} {
		index := grantGetIdxBucket(entity, true)
		index.Mu.Lock()
		index.ApplicationsByEntity[entity] = append(index.ApplicationsByEntity[entity], id)
		index.Mu.Unlock()
	}
	return id
}
