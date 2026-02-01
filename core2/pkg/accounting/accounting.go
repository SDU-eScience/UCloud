package accounting

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"slices"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	lru "github.com/hashicorp/golang-lru/v2/expirable"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database2"
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
			id, err := RootAllocate(info.Actor, reqItem)
			if err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			} else {
				result = append(result, fndapi.FindByStringId{Id: id})
			}
		}
		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: result}, nil
	})

	accapi.UpdateAllocation.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[accapi.UpdateAllocationRequest]) (util.Empty, *util.HttpError) {
		return UpdateAllocation(info.Actor, request.Items)
	})

	accapi.ReportUsage.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[accapi.ReportUsageRequest]) (fndapi.BulkResponse[bool], *util.HttpError) {
		var result []bool
		for _, reqItem := range request.Items {
			resp, err := ReportUsage(info.Actor, reqItem)
			if err != nil {
				return fndapi.BulkResponse[bool]{}, err
			} else {
				result = append(result, resp)
			}
		}
		return fndapi.BulkResponse[bool]{Responses: result}, nil
	})

	accapi.WalletsBrowse.Handler(func(info rpc.RequestInfo, request accapi.WalletsBrowseRequest) (fndapi.PageV2[accapi.WalletV2], *util.HttpError) {
		log.Info("browse handler")
		return WalletsBrowse(info.Actor, request), nil
	})

	accapi.WalletsBrowseInternal.Handler(func(info rpc.RequestInfo, request accapi.WalletsBrowseInternalRequest) (accapi.WalletsBrowseInternalResponse, *util.HttpError) {
		if !validateOwner(request.Owner) {
			return accapi.WalletsBrowseInternalResponse{}, util.HttpErr(http.StatusNotFound, "unknown owner")
		} else {
			wallets := internalRetrieveWallets(time.Now(), request.Owner.Reference(), walletFilter{
				RequireActive: false,
			})

			return accapi.WalletsBrowseInternalResponse{Wallets: wallets}, nil
		}
	})

	accapi.CheckProviderUsable.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[accapi.CheckProviderUsableRequest]) (fndapi.BulkResponse[accapi.CheckProviderUsableResponse], *util.HttpError) {
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
	})

	accapi.FindRelevantProviders.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[accapi.FindRelevantProvidersRequest]) (fndapi.BulkResponse[accapi.FindRelevantProvidersResponse], *util.HttpError) {
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

	accapi.WalletsAdminDebug.Handler(func(info rpc.RequestInfo, request accapi.WalletDebugRequest) (accapi.WalletDebugResponse, *util.HttpError) {
		graph, _ := internalGetMermaidGraph(time.Now(), AccWalletId(request.WalletId))
		return accapi.WalletDebugResponse{
			MermaidGraph: graph,
			StateDump:    json.RawMessage("{}"),
		}, nil
	})
}

func RootAllocate(actor rpc.Actor, request accapi.RootAllocateRequest) (string, *util.HttpError) {
	if actor.Role&rpc.RolesEndUser == 0 {
		return "", util.HttpErr(http.StatusForbidden, "You are not allowed to create a root allocation!")
	}

	if !actor.Project.Present || actor.Project.Value == "" {
		return "", util.HttpErr(http.StatusForbidden, "Cannot perform a root allocation in a personal workspace!")
	}

	projectId, ok := actor.ProviderProjects[rpc.ProviderId(request.Category.Provider)]

	if !ok || projectId != actor.Project.Value {
		return "", util.HttpErr(http.StatusForbidden, "You are not allowed to create a root allocation!")
	}

	role := fndapi.ProjectRole(actor.Membership[projectId])
	if !role.Satisfies(fndapi.ProjectRoleAdmin) {
		return "", util.HttpErr(http.StatusForbidden, "You are not allowed to create a root allocation!")
	}

	category, err := ProductCategoryRetrieve(actor, request.Category.Name, request.Category.Provider)

	if err != nil {
		return "", util.HttpErr(http.StatusForbidden, "This category does not exist")
	}

	now := time.Now()

	bucket := internalBucketOrInit(category)
	recipientOwner := internalOwnerByReference(string(actor.Project.Value))
	recipient := internalWalletByOwner(bucket, now, recipientOwner.Id)

	id, err := internalAllocateNoCommit(
		now,
		bucket,
		request.Start.Time(),
		request.End.Time(),
		request.Quota,
		recipient,
		internalGraphRoot,
		util.OptNone[accGrantId](),
	)

	internalCommitAllocation(bucket, id)

	return fmt.Sprint(id), err
}

func UpdateAllocation(actor rpc.Actor, requests []accapi.UpdateAllocationRequest) (util.Empty, *util.HttpError) {
	for _, request := range requests {
		if !actor.Membership[actor.Project.Value].Satisfies(rpc.ProjectRoleAdmin) {
			return util.Empty{}, util.HttpErr(http.StatusForbidden, "You need admin privileges in your project to perform this action")
		}

		bucket, _, ok := internalWalletByAllocationId(accAllocId(request.AllocationId))
		// If wallet cannot be found just skip
		if !ok {
			continue
		}

		reference := actor.Username
		if actor.Project.Present && actor.Project.Value != "" {
			reference = string(actor.Project.Value)
		}
		iOwner := internalOwnerByReference(reference)

		grantedIn, comment, err := internalUpdateAllocation(iOwner, time.Now(), bucket, accAllocId(request.AllocationId), request.NewQuota, request.NewStart, request.NewEnd)

		// If update failed will break the update
		if err != nil {
			return util.Empty{}, err
		}

		if grantedIn != accGrantId(0) {
			_, commentErr := GrantsPostComment(
				actor,
				accapi.GrantsPostCommentRequest{
					ApplicationId: fmt.Sprint(grantedIn),
					Comment:       comment,
				},
			)
			if commentErr != nil {
				// Should not fail the update that we cannot post a comment
				log.Warn("Error posting comment: %s", err)
			}
		}
	}
	return util.Empty{}, nil
}

func ReportUsage(actor rpc.Actor, request accapi.ReportUsageRequest) (bool, *util.HttpError) {
	providerId, ok := strings.CutPrefix(actor.Username, fndapi.ProviderSubjectPrefix)
	if !ok {
		return false, util.HttpErr(http.StatusForbidden, "You cannot report usage")
	}

	if providerId != request.CategoryIdV2.Provider {
		return false, util.HttpErr(http.StatusForbidden, "You cannot report usage for this product")
	}

	_, err := ProductCategoryRetrieve(actor, request.CategoryIdV2.Name, request.CategoryIdV2.Provider)

	if err != nil {
		return false, util.HttpErr(http.StatusForbidden, "This category does not exist")
	}

	if !validateOwner(request.Owner) {
		return false, util.HttpErr(http.StatusForbidden, "This user/project does not exist")
	}

	if !request.IsDeltaCharge && request.Usage < 0 {
		return false, util.HttpErr(http.StatusForbidden, "Absolute usage cannot be negative")
	}

	category, err := ProductCategoryRetrieve(actor, request.CategoryIdV2.Name, request.CategoryIdV2.Provider)
	if err == nil {
		if category.FreeToUse {
			return true, nil
		}

		internalBucketOrInit(category)
		success, err := internalReportUsage(time.Now(), request)
		return success, err
	} else {
		return false, err
	}
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

func WalletV2ByAllocationID(actor rpc.Actor, allocationId int) (AccWalletId, accapi.WalletV2, bool) {
	if actor.Username == rpc.ActorSystem.Username {
		walletId, wallet, found := internalRetrieveWalletByAllocationId(time.Now(), allocationId)
		if found {
			return walletId, wallet, true
		}
	}
	return AccWalletId(-1), accapi.WalletV2{}, false
}

func WalletsBrowse(actor rpc.Actor, request accapi.WalletsBrowseRequest) fndapi.PageV2[accapi.WalletV2] {
	log.Info("WalletsBrowse 1")
	reference := actor.Username
	if actor.Project.Present {
		reference = string(actor.Project.Value)
	}
	allWallets := internalRetrieveWallets(time.Now(), reference, walletFilter{
		RequireActive:   false,
		IncludeChildren: request.IncludeChildren,
	})
	log.Info("WalletsBrowse 2")

	result := fndapi.PageV2[accapi.WalletV2]{}

	for _, item := range allWallets {
		if request.FilterType.Present {
			if item.PaysFor.ProductType != request.FilterType.Value {
				continue
			}
		}

		if !request.IncludeChildren {
			item.Children = nil
		}

		result.Items = append(result.Items, item)
	}

	result.ItemsPerPage = len(result.Items)
	result.Items = util.NonNilSlice(result.Items)
	log.Info("WalletsBrowse 3")
	return result
}

func AccountingGraphRetrieval(walletId AccWalletId) (string, bool) {
	return internalGetMermaidGraph(time.Now(), walletId)
}

func RetrieveAncestors(now time.Time, category accapi.ProductCategoryIdV2, owner accapi.WalletOwner) []accapi.WalletV2 {
	return internalRetrieveAncestors(now, category, owner)
}

func accountingLoad() {
	var loadTimes struct {
		WalletOwners     time.Duration
		ScopedUsage      time.Duration
		Wallets          time.Duration
		AllocationGroups time.Duration
		Allocations      time.Duration
	}

	db.NewTx0(func(tx *db.Transaction) {
		timer := util.NewTimer()
		accGlobals.OwnersByReference = map[string]*internalOwner{}
		accGlobals.OwnersById = map[accOwnerId]*internalOwner{}
		accGlobals.Usage = map[string]*scopedUsage{}
		accGlobals.BucketsByCategory = map[accapi.ProductCategoryIdV2]*internalBucket{}

		// Wallet owners
		// -------------------------------------------------------------------------------------------------------------
		timer.Mark()
		walletOwners := db.Select[struct {
			Id        int
			Username  sql.NullString
			ProjectId sql.NullString
		}](
			tx,
			`
				select id, username, project_id
				from accounting.wallet_owner wo
		    `,
			db.Params{},
		)

		for _, row := range walletOwners {
			owner := &internalOwner{
				Id:    accOwnerId(row.Id),
				Dirty: false,
			}

			if row.Username.Valid {
				owner.Reference = row.Username.String
			} else if row.ProjectId.Valid {
				owner.Reference = row.ProjectId.String
			}

			accGlobals.OwnersByReference[owner.Reference] = owner
			accGlobals.OwnersById[owner.Id] = owner

			if int64(owner.Id) > accGlobals.OwnerIdAcc.Load() {
				accGlobals.OwnerIdAcc.Store(int64(owner.Id))
			}
		}

		loadTimes.WalletOwners = timer.Mark()

		// Scoped usage
		// -------------------------------------------------------------------------------------------------------------
		usageRows := db.Select[struct {
			Key   string
			Usage int64
		}](
			tx,
			`
				select key, usage
				from accounting.scoped_usage
		    `,
			db.Params{},
		)

		for _, row := range usageRows {
			accGlobals.Usage[row.Key] = &scopedUsage{
				Key:   row.Key,
				Usage: row.Usage,
			}
		}

		loadTimes.ScopedUsage = timer.Mark()

		// Wallets
		// -------------------------------------------------------------------------------------------------------------
		walletRows := db.Select[struct {
			Id                      int
			WalletOwner             int
			CatName                 string
			CatProvider             string
			LocalUsage              int64
			WasLocked               bool
			LastSignificantUpdateAt time.Time
		}](
			tx,
			`
				select
					w.id, w.wallet_owner, 
					pc.category as cat_name, pc.provider as cat_provider,
					w.local_usage, w.was_locked, w.last_significant_update_at
				from
					accounting.wallets_v2 w
					join accounting.product_categories pc on w.product_category = pc.id
		    `,
			db.Params{},
		)

		for _, row := range walletRows {
			category, err := ProductCategoryRetrieve(rpc.ActorSystem, row.CatName, row.CatProvider)
			if err != nil {
				log.Warn("Could not load wallet with id=%v in category %v/%v", row.Id, row.CatName, row.CatProvider)
				continue
			}

			b := internalBucketOrInit(category)

			wallet := &internalWallet{
				Id:                    AccWalletId(row.Id),
				OwnedBy:               accOwnerId(row.WalletOwner),
				LocalUsage:            row.LocalUsage,
				AllocationsByParent:   make(map[AccWalletId]*internalGroup),
				ChildrenUsage:         make(map[AccWalletId]int64),
				Dirty:                 false,
				WasLocked:             row.WasLocked,
				LastSignificantUpdate: row.LastSignificantUpdateAt,
			}

			b.WalletsById[wallet.Id] = wallet
			b.WalletsByOwner[wallet.OwnedBy] = wallet

			if wallet.LastSignificantUpdate.After(b.SignificantUpdateAt) {
				b.SignificantUpdateAt = wallet.LastSignificantUpdate
			}

			if int64(row.Id) > accGlobals.WalletIdAcc.Load() {
				accGlobals.WalletIdAcc.Store(int64(row.Id))
			}
		}

		loadTimes.Wallets = timer.Mark()

		// Allocation groups
		// -------------------------------------------------------------------------------------------------------------
		allocationGroups := db.Select[struct {
			Id               int
			ParentWallet     int
			AssociatedWallet int
			TreeUsage        int64
		}](
			tx,
			`
				select ag.id, coalesce(ag.parent_wallet, 0) as parent_wallet, ag.associated_wallet, ag.tree_usage
				from accounting.allocation_groups ag
		    `,
			db.Params{},
		)

		for _, row := range allocationGroups {
			group := &internalGroup{
				Id:               accGroupId(row.Id),
				AssociatedWallet: AccWalletId(row.AssociatedWallet),
				ParentWallet:     AccWalletId(row.ParentWallet),
				TreeUsage:        row.TreeUsage,
				Allocations:      make(map[accAllocId]util.Empty),
				Dirty:            false,
			}

			_, associatedWallet, ok := internalWalletById(group.AssociatedWallet)
			_, parentWallet, hasParent := internalWalletById(group.ParentWallet)
			if ok {
				associatedWallet.AllocationsByParent[group.ParentWallet] = group
				if hasParent {
					parentWallet.ChildrenUsage[group.AssociatedWallet] = group.TreeUsage
				}
			}

			if int64(row.Id) > accGlobals.GroupIdAcc.Load() {
				accGlobals.GroupIdAcc.Store(int64(row.Id))
			}
		}

		loadTimes.AllocationGroups = timer.Mark()

		// Allocations
		// -------------------------------------------------------------------------------------------------------------
		allocations := db.Select[struct {
			Id                  int
			WalletId            int
			ParentWallet        int
			GrantedIn           int
			AllocGroup          int
			Quota               int64
			AllocationStartTime time.Time
			AllocationEndTime   time.Time
			Retired             bool
			RetiredUsage        int64
			RetiredQuota        int64
		}](
			tx,
			`
				select
					alloc.id,
					ag.associated_wallet as wallet_id,
					coalesce(ag.parent_wallet, 0) as parent_wallet,
					coalesce(alloc.granted_in, 0) as granted_in,
					alloc.associated_allocation_group as alloc_group,
					alloc.quota,
					alloc.allocation_start_time,
					alloc.allocation_end_time,
					alloc.retired,
					alloc.retired_usage,
					alloc.retired_quota
				from
					accounting.wallet_allocations_v2 alloc
					join accounting.allocation_groups ag on alloc.associated_allocation_group = ag.id
		    `,
			db.Params{},
		)

		for _, row := range allocations {
			b, wallet, ok := internalWalletById(AccWalletId(row.WalletId))

			if ok {
				ag, ok := wallet.AllocationsByParent[AccWalletId(row.ParentWallet)]
				if !ok {
					panic(fmt.Sprintf("inconsistent DB or load alloc = %v", row.Id))
				}

				ag.Allocations[accAllocId(row.Id)] = util.Empty{}

				alloc := &internalAllocation{
					Id:           accAllocId(row.Id),
					BelongsTo:    wallet.Id,
					Parent:       AccWalletId(row.ParentWallet),
					Group:        accGroupId(row.AllocGroup),
					Quota:        row.Quota,
					Start:        row.AllocationStartTime,
					End:          row.AllocationEndTime,
					Retired:      row.Retired,
					RetiredUsage: row.RetiredUsage,
					RetiredQuota: row.RetiredQuota,
					Active:       time.Now().After(row.AllocationStartTime), // TODO we don't know if it was activated
					Dirty:        false,
					Committed:    true,
				}

				if row.GrantedIn != 0 {
					alloc.GrantedIn.Set(accGrantId(row.GrantedIn))
				}

				b.AllocationsById[accAllocId(row.Id)] = alloc
			}

			if int64(row.Id) > accGlobals.AllocIdAcc.Load() {
				accGlobals.AllocIdAcc.Store(int64(row.Id))
			}
		}

		loadTimes.Allocations = timer.Mark()
	})

	log.Info(
		"Accounting system is ready."+
			"\n\tLoad times:"+
			"\n\t\tWallet owners: %v"+
			"\n\t\tScoped usage: %v"+
			"\n\t\tWallets: %v"+
			"\n\t\tAllocation groups: %v"+
			"\n\t\tAllocations: %v",
		loadTimes.WalletOwners, loadTimes.ScopedUsage, loadTimes.Wallets, loadTimes.AllocationGroups,
		loadTimes.Allocations,
	)
}

var accountingScansDisabled = atomic.Bool{}

var accountingProcessMutex = sync.Mutex{}
var accountingScanUsageReportCanResumeAt = time.Now().Add(10 * time.Minute)
var usageReportSamplingHours = []int{0, 4, 8, 12, 16, 20}

func accountingProcessTasksNow(now time.Time, filter func(b *internalBucket) bool) {
	accountingProcessMutex.Lock()

	timer := util.NewTimer()
	internalCompleteScan(now, func(buckets []*internalBucket, scopes []*scopedUsage, onPersistHandlers []internalOnPersistHandler) {
		var actualBuckets []*internalBucket
		for _, b := range buckets {
			if filter == nil || filter(b) {
				actualBuckets = append(actualBuckets, b)
			}
		}
		buckets = actualBuckets

		persistHandlersByGrant := map[accGrantId]internalOnPersistHandler{}
		for _, handler := range onPersistHandlers {
			persistHandlersByGrant[handler.GrantId] = handler
		}

		ownerRequest := struct {
			Id        []int64
			Username  []string
			ProjectId []string
		}{}

		walletRequest := struct {
			Id                      []int64
			WalletOwner             []int64
			CategoryName            []string
			CategoryProvider        []string
			LocalUsage              []int64
			WasLocked               []bool
			LastSignificantUpdateAt []int64
		}{}

		usageRequests := struct {
			Key   []string
			Usage []int64
		}{}

		allocationRequests := struct {
			Id              []int64
			AllocationGroup []int64
			GrantedIn       []int64 // 0 -> null
			Quota           []int64
			Start           []int64
			End             []int64
			Retired         []bool
			RetiredUsage    []int64
			RetiredQuota    []int64
		}{}

		groupRequests := struct {
			Id        []int64
			Parent    []int64 // 0 -> null
			Wallet    []int64
			TreeUsage []int64
		}{}

		handlersToTrigger := map[accGrantId]internalOnPersistHandler{}

		for _, owner := range accGlobals.OwnersById {
			if owner.Dirty {
				ownerRequest.Id = append(ownerRequest.Id, int64(owner.Id))
				wo := owner.WalletOwner()
				if wo.Type == accapi.WalletOwnerTypeUser {
					ownerRequest.Username = append(ownerRequest.Username, wo.Username)
					ownerRequest.ProjectId = append(ownerRequest.ProjectId, "")
				} else {
					ownerRequest.ProjectId = append(ownerRequest.ProjectId, wo.ProjectId)
					ownerRequest.Username = append(ownerRequest.Username, "")
				}
				owner.Dirty = false
			}
		}

		for _, b := range buckets {
			for _, wallet := range b.WalletsById {
				if wallet.Dirty {
					walletRequest.Id = append(walletRequest.Id, int64(wallet.Id))
					walletRequest.WalletOwner = append(walletRequest.WalletOwner, int64(wallet.OwnedBy))
					walletRequest.CategoryName = append(walletRequest.CategoryName, b.Category.Name)
					walletRequest.CategoryProvider = append(walletRequest.CategoryProvider, b.Category.Provider)
					walletRequest.LocalUsage = append(walletRequest.LocalUsage, wallet.LocalUsage)
					walletRequest.WasLocked = append(walletRequest.WasLocked, wallet.WasLocked)
					walletRequest.LastSignificantUpdateAt = append(walletRequest.LastSignificantUpdateAt, wallet.LastSignificantUpdate.UnixMilli())

					wallet.Dirty = false
				}

				for _, ag := range wallet.AllocationsByParent {
					if ag.Dirty {
						groupRequests.Id = append(groupRequests.Id, int64(ag.Id))
						groupRequests.Parent = append(groupRequests.Parent, int64(ag.ParentWallet))
						groupRequests.Wallet = append(groupRequests.Wallet, int64(ag.AssociatedWallet))
						groupRequests.TreeUsage = append(groupRequests.TreeUsage, ag.TreeUsage)

						ag.Dirty = false
					}
				}
			}

			for _, alloc := range b.AllocationsById {
				if !alloc.Committed && alloc.GrantedIn.Present {
					handler, ok := persistHandlersByGrant[alloc.GrantedIn.Value]
					if ok {
						handlersToTrigger[alloc.GrantedIn.Value] = handler
						alloc.Committed = true
					}
				}

				if alloc.Dirty && alloc.Committed {
					allocationRequests.Id = append(allocationRequests.Id, int64(alloc.Id))
					allocationRequests.AllocationGroup = append(allocationRequests.AllocationGroup, int64(alloc.Group))
					allocationRequests.GrantedIn = append(allocationRequests.GrantedIn, int64(alloc.GrantedIn.GetOrDefault(0)))
					allocationRequests.Quota = append(allocationRequests.Quota, alloc.Quota)
					allocationRequests.Start = append(allocationRequests.Start, alloc.Start.UnixMilli())
					allocationRequests.End = append(allocationRequests.End, alloc.End.UnixMilli())
					allocationRequests.Retired = append(allocationRequests.Retired, alloc.Retired)
					allocationRequests.RetiredUsage = append(allocationRequests.RetiredUsage, alloc.RetiredUsage)
					allocationRequests.RetiredQuota = append(allocationRequests.RetiredQuota, alloc.RetiredQuota)

					alloc.Dirty = false
				}
			}
		}

		for _, scope := range scopes {
			if scope.Dirty {
				usageRequests.Key = append(usageRequests.Key, scope.Key)
				usageRequests.Usage = append(usageRequests.Usage, scope.Usage)
				scope.Dirty = false
			}
		}

		accountingWalletsUpdated.Add(float64(len(walletRequest.Id)))
		accountingAllocationsUpdated.Add(float64(len(allocationRequests.Id)))

		db.NewTx0(func(tx *db.Transaction) {
			if len(ownerRequest.Id) > 0 {
				db.Exec(
					tx,
					`
							with data as (
								select
									unnest(cast(:id as int8[])) as id,
									unnest(cast(:project as text[])) as project,
									unnest(cast(:username as text[])) as username
							)
							insert into accounting.wallet_owner(id, username, project_id) 
							select
								d.id,
								case
									when d.username = '' then null
									else d.username
								end,
								case
									when d.project = '' then null
									else d.project
								end
							from
								data d
							on conflict do nothing
						`,
					db.Params{
						"id":       ownerRequest.Id,
						"project":  ownerRequest.ProjectId,
						"username": ownerRequest.Username,
					},
				)
			}

			if len(walletRequest.Id) > 0 {
				db.Exec(
					tx,
					`
							with data as (
								select
									unnest(cast(:id as int8[])) as id,
									unnest(cast(:wallet_owner as int8[])) as wallet_owner,
									unnest(cast(:category_name as text[])) as category_name,
									unnest(cast(:category_provider as text[])) as category_provider,
									unnest(cast(:local_usage as int8[])) as local_usage,
									unnest(cast(:was_locked as bool[])) as was_locked,
									unnest(cast(:last_significant_update_at as int8[])) as last_significant_update_at
							)
							insert into accounting.wallets_v2
								(id, wallet_owner, product_category, local_usage, local_retired_usage, excess_usage, 
								total_allocated, total_retired_allocated, was_locked, last_significant_update_at) 
							select
								d.id,
								d.wallet_owner,
								pc.id,
								d.local_usage,
								0,
								0,
								0,
								0,
								d.was_locked,
								to_timestamp(d.last_significant_update_at / 1000.0)
							from
								data d
								join accounting.product_categories pc on 
									pc.category = d.category_name
									and pc.provider = d.category_provider
							on conflict (id) do update set
								local_usage = excluded.local_usage,
								was_locked = excluded.was_locked,
								last_significant_update_at = excluded.last_significant_update_at
						`,
					db.Params{
						"id":                         walletRequest.Id,
						"wallet_owner":               walletRequest.WalletOwner,
						"category_name":              walletRequest.CategoryName,
						"category_provider":          walletRequest.CategoryProvider,
						"local_usage":                walletRequest.LocalUsage,
						"was_locked":                 walletRequest.WasLocked,
						"last_significant_update_at": walletRequest.LastSignificantUpdateAt,
					},
				)
			}

			if len(groupRequests.Id) > 0 {
				db.Exec(
					tx,
					`
							with data as (
								select
									unnest(cast(:id as int8[])) as id,
									unnest(cast(:parent as int8[])) as parent,
									unnest(cast(:wallet as int8[])) as wallet,
									unnest(cast(:tree_usage as int8[])) as tree_usage
							)
							insert into accounting.allocation_groups(id, parent_wallet, associated_wallet, tree_usage, 
								retired_tree_usage) 
							select
								d.id,
								case
									when d.parent = 0 then null
									else d.parent
								end,
								d.wallet,
								d.tree_usage,
								0
							from
								data d
							on conflict (id) do update set
								tree_usage = excluded.tree_usage						                               
						`,
					db.Params{
						"id":         groupRequests.Id,
						"parent":     groupRequests.Parent,
						"wallet":     groupRequests.Wallet,
						"tree_usage": groupRequests.TreeUsage,
					},
				)
			}

			if len(allocationRequests.Id) > 0 {
				log.Info("Synchronizing %v allocations", len(allocationRequests.Id))
				db.Exec(
					tx,
					`
							with data as (
								select
									unnest(cast(:id as int8[])) as id,
									unnest(cast(:allocation_group as int8[])) as allocation_group,
									unnest(cast(:granted_in as int8[])) as granted_in,
									unnest(cast(:quota as int8[])) as quota,
									unnest(cast(:start as int8[])) as start_time,
									unnest(cast(:end as int8[])) as end_time,
									unnest(cast(:retired as bool[])) as retired,
									unnest(cast(:retired_usage as int8[])) as retired_usage,
									unnest(cast(:retired_quota as int8[])) as retired_quota
							)
							insert into accounting.wallet_allocations_v2
								(id, associated_allocation_group, granted_in, quota, allocation_start_time, 
									allocation_end_time, retired, retired_usage, retired_quota) 
							select
								d.id,
								d.allocation_group,
								case
									when d.granted_in = 0 then null
									else d.granted_in
								end,
								d.quota,
								to_timestamp(d.start_time / 1000.0),
								to_timestamp(d.end_time / 1000.0),
								d.retired,
								d.retired_usage,
								d.retired_quota
							from
								data d
							on conflict (id) do update set
								granted_in = excluded.granted_in,
								quota = excluded.quota,
								allocation_start_time = excluded.allocation_start_time,
								allocation_end_time = excluded.allocation_end_time,
								retired = excluded.retired,
								retired_usage = excluded.retired_usage,
								retired_quota = excluded.retired_quota
						`,
					db.Params{
						"id":               allocationRequests.Id,
						"allocation_group": allocationRequests.AllocationGroup,
						"granted_in":       allocationRequests.GrantedIn,
						"quota":            allocationRequests.Quota,
						"start":            allocationRequests.Start,
						"end":              allocationRequests.End,
						"retired":          allocationRequests.Retired,
						"retired_usage":    allocationRequests.RetiredUsage,
						"retired_quota":    allocationRequests.RetiredQuota,
					},
				)
			}

			if len(usageRequests.Key) > 0 {
				db.Exec(
					tx,
					`
						with data as (
							select
								unnest(cast(:key as text[])) as key,
								unnest(cast(:usage as int8[])) as usage
						)
						insert into accounting.scoped_usage(key, usage) 
						select
							key,
							usage
						from
							data d
						on conflict (key) do update set
							usage = excluded.usage
					`,
					db.Params{
						"key":   usageRequests.Key,
						"usage": usageRequests.Usage,
					},
				)
			}

			for _, handler := range handlersToTrigger {
				handler.OnPersist(tx)
			}
		})

		var remainingHandlers []internalOnPersistHandler
		for _, handler := range persistHandlersByGrant {
			if _, wasHandled := handlersToTrigger[handler.GrantId]; !wasHandled {
				remainingHandlers = append(remainingHandlers, handler)
			}
		}
		accGlobals.OnPersistHandlers = remainingHandlers
	})

	accountingProcessMutex.Unlock()
	accountingScansDuration.Observe(timer.Mark().Seconds())

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
			accountingProcessTasksNow(time.Now(), nil)
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
