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
	AllocationIds []int64                              `json:"allocationIds"`
	GrantIds      []int64                              `json:"grantIds"`
	Checkpoints   []accountingScenarioCheckpoint       `json:"checkpoints"`
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
		if request.Scenario == accountingScenarioPortfolioName {
			controller := http.NewResponseController(info.HttpWriter)
			_ = controller.SetWriteDeadline(time.Now().Add(accountingScenarioPortfolioTimeout))
		}
		return accountingScenarioRun(piUsername, request.Scenario, launcherRun)
	})
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
		if launcherRun && name == accountingScenarioPortfolioName && role != "root" {
			projectPi = rpc.ActorSystem.Username
		}
		project, err := fndapi.ProjectInternalCreate.Invoke(fndapi.ProjectInternalCreateRequest{
			Title:      title,
			BackendId:  "accounting-scenario-" + runId + "-" + role,
			PiUsername: projectPi,
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
	result.ProjectId = result.Projects["root"]
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
	err := ProductCreate(rpc.ActorSystem, []accapi.ProductV2{product})
	if err != nil {
		result.Failure = err.Error()
		return result, nil
	}
	category := product.Category
	result.Category = category.ToId()
	bucket := internalBucketOrInit(category)
	at := func(day int) time.Time {
		return now.AddDate(0, 0, day)
	}

	accountingProcessMutex.Lock()
	replayErr := func() *util.HttpError {
		defer accountingProcessMutex.Unlock()
		wallets := map[string]AccWalletId{}
		for _, role := range roles {
			owner := internalOwnerByReference(result.Projects[role])
			wallets[role] = internalWalletByOwner(bucket, at(firstDay), owner.Id)
		}
		rootQuota := definition.RootQuota
		if rootQuota == 0 {
			rootQuota = 1000000
		}
		rootAllocation, allocErr := internalAllocateNoCommit(
			at(firstDay),
			bucket,
			at(firstDay),
			at(365),
			rootQuota,
			wallets["root"],
			internalGraphRoot,
			util.OptNone[accGrantId](),
		)
		if allocErr != nil {
			return allocErr
		}
		internalCommitAllocation(bucket, at(firstDay), rootAllocation)
		result.AllocationIds = append(result.AllocationIds, int64(rootAllocation))

		grantByStep := map[int]accGrantId{}
		for stepIndex, step := range definition.Steps {
			if step.Kind != "allocate" {
				continue
			}
			if _, exists := grantByStep[stepIndex]; exists {
				continue
			}
			groupedSteps := []int{stepIndex}
			if step.Package != "" {
				for next := stepIndex + 1; next < len(definition.Steps); next++ {
					candidate := definition.Steps[next]
					samePackage := candidate.Kind == "allocate" && candidate.Package == step.Package
					sameRelationship := candidate.Parent == step.Parent && candidate.Owner == step.Owner
					if samePackage && sameRelationship {
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

		for stepIndex, step := range definition.Steps {
			when := at(step.At).Add(time.Duration(step.AtMillis) * time.Millisecond)
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
				allocationId, allocErr := internalAllocateNoCommit(
					when,
					bucket,
					at(step.Start),
					at(step.End),
					step.Quota,
					wallets[owner],
					wallets[parent],
					util.OptValue(grantByStep[stepIndex]),
				)
				if allocErr != nil {
					return allocErr
				}
				bucket.Mu.Lock()
				bucket.AllocationsById[allocationId].AllocationPackage = step.Package != ""
				bucket.Mu.Unlock()
				internalCommitAllocation(bucket, when, allocationId)
				result.AllocationIds = append(result.AllocationIds, int64(allocationId))
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
			case "checkpoint":
				internalScanAllocations(bucket, when)
				invariantErr := internalValidateAccountingTree(bucket, when)
				if invariantErr != nil {
					return util.HttpErr(http.StatusInternalServerError, "%s: %s", step.Label, invariantErr)
				}
				checkpoint := accountingScenarioCheckpoint{
					Label:            step.Label,
					Now:              when.UnixMilli(),
					WalletsByProject: map[string][]accapi.WalletV2{},
					Expected:         *step.Expected,
					ChildProjectId:   result.Projects[owner],
					Category:         category.ToId(),
				}
				for _, role := range roles {
					wallet, ok := internalRetrieveWallet(when, wallets[role], true)
					if !ok {
						return util.HttpErr(http.StatusInternalServerError, "missing scenario wallet")
					}
					checkpoint.WalletsByProject[result.Projects[role]] = []accapi.WalletV2{wallet}
				}
				checkpoint.Wallets = checkpoint.WalletsByProject[result.ProjectId]
				actual := accountingScenarioReadAmounts(checkpoint.Wallets[0], checkpoint.ChildProjectId, definition.Capacity, when)
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
						amounts := accountingScenarioReadAmounts(checkpoint.Wallets[0], projectId, definition.Capacity, when)
						checkpoint.ChecksByProject[projectId] = accountingScenarioCheck{Expected: expected, Actual: amounts}
						if amounts != expected && mismatch == nil {
							mismatch = util.HttpErr(http.StatusInternalServerError, "%s (%s): expected %+v; got %+v", step.Label, role, expected, amounts)
						}
					}
				}
				result.Checkpoints = append(result.Checkpoints, checkpoint)
				if mismatch != nil {
					return mismatch
				}
				if actual != *step.Expected {
					return util.HttpErr(http.StatusInternalServerError, "%s: expected %+v; got %+v", step.Label, *step.Expected, actual)
				}
			}
		}
		return nil
	}()
	accountingProcessTasksNow(time.Now(), nil)
	db.NewTx0(func(tx *db.Transaction) {
		for _, id := range result.GrantIds {
			db.Exec(
				tx,
				`update "grant".applications set synchronized = true where id = :id`,
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
