package accounting

import (
	"database/sql"
	"fmt"
	"net/http"
	"strings"
	"time"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initAccounting() {
	accountingLoad()
	go accountingProcessTasks()

	accapi.WalletsBrowse.Handler(func(info rpc.RequestInfo, request accapi.WalletsBrowseRequest) (fndapi.PageV2[accapi.WalletV2], *util.HttpError) {
		return WalletsBrowse(info.Actor, request), nil
	})
}

func RootAllocate(actor rpc.Actor, request accapi.RootAllocateRequest) (string, *util.HttpError) {
	if actor.Role&rpc.RolesEndUser == 0 {
		return "", util.HttpErr(http.StatusForbidden, "You are not allowed to create a root allocation!")
	}

	if !actor.Project.Present {
		return "", util.HttpErr(http.StatusForbidden, "Cannot perform a root allocation in a personal workspace!")
	}

	projectId, ok := actor.ProviderProjects[rpc.ProviderId(request.Category.Provider)]

	if !ok || string(projectId) != actor.Project.Value {
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
	recipientOwner := internalOwnerByReference(actor.Project.Value)
	recipient := internalWalletByOwner(bucket, now, recipientOwner.Id)

	id, err := internalAllocate(
		now,
		bucket,
		request.Start.Time(),
		request.End.Time(),
		request.Quota,
		recipient,
		internalGraphRoot,
		util.OptNone[accGrantId](),
	)

	return fmt.Sprint(id), err
}

func ReportUsage(actor rpc.Actor, request accapi.ReportUsageRequest) (bool, *util.HttpError) {
	providerId, ok := strings.CutPrefix(fndapi.ProviderSubjectPrefix, actor.Username)
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

	success, err := internalReportUsage(time.Now(), request)
	return success, err
}

func validateOwner(owner accapi.WalletOwner) bool {
	return false // TODO
}

func WalletsBrowse(actor rpc.Actor, request accapi.WalletsBrowseRequest) fndapi.PageV2[accapi.WalletV2] {
	reference := actor.Project.GetOrDefault(actor.Username)
	allWallets := internalRetrieveWallets(time.Now(), reference, walletFilter{RequireActive: false})

	result := fndapi.PageV2[accapi.WalletV2]{}

	for _, item := range allWallets {
		if request.ChildrenQuery.Present {
			// TODO
		}

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
	return result
}

func accountingLoad() {
	db.NewTx0(func(tx *db.Transaction) {
		accGlobals.OwnersByReference = map[string]*internalOwner{}
		accGlobals.OwnersById = map[accOwnerId]*internalOwner{}
		accGlobals.Usage = map[string]*scopedUsage{}
		accGlobals.BucketsByCategory = map[accapi.ProductCategoryIdV2]*internalBucket{}

		// Wallet owners
		// -------------------------------------------------------------------------------------------------------------
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
				Id:                    accWalletId(row.Id),
				OwnedBy:               accOwnerId(row.WalletOwner),
				LocalUsage:            row.LocalUsage,
				AllocationsByParent:   make(map[accWalletId]*internalGroup),
				ChildrenUsage:         make(map[accWalletId]int64),
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
				AssociatedWallet: accWalletId(row.AssociatedWallet),
				ParentWallet:     accWalletId(row.ParentWallet),
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
					alloc.retired_usage
				from
					accounting.wallet_allocations_v2 alloc
					join accounting.allocation_groups ag on alloc.associated_allocation_group = ag.id
		    `,
			db.Params{},
		)

		for _, row := range allocations {
			b, wallet, ok := internalWalletById(accWalletId(row.WalletId))

			if ok {
				ag, ok := wallet.AllocationsByParent[accWalletId(row.ParentWallet)]
				if !ok {
					panic(fmt.Sprintf("inconsistent DB or load alloc = %v", row.Id))
				}

				ag.Allocations[accAllocId(row.Id)] = util.Empty{}

				alloc := &internalAllocation{
					Id:           accAllocId(row.Id),
					BelongsTo:    wallet.Id,
					Parent:       accWalletId(row.ParentWallet),
					Group:        accGroupId(row.AllocGroup),
					Quota:        row.Quota,
					Start:        row.AllocationStartTime,
					End:          row.AllocationEndTime,
					Retired:      row.Retired,
					RetiredUsage: row.RetiredUsage,
					RetiredQuota: 0,                                         // TODO We don't know this
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
	})
}

func accountingProcessTasks() {
	for {
		now := time.Now()
		internalCompleteScan(now, func(buckets []*internalBucket, scopes []*scopedUsage, onPersistHandlers []internalOnPersistHandler) {
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
			}{}

			groupRequests := struct {
				Id        []int64
				Parent    []int64 // 0 -> null
				Wallet    []int64
				TreeUsage []int64
			}{}

			var handlersToTrigger []internalOnPersistHandler

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
							handlersToTrigger = append(handlersToTrigger, handler)
							delete(persistHandlersByGrant, alloc.GrantedIn.Value)

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

			db.NewTx0(func(tx *db.Transaction) {
				if len(ownerRequest.Id) > 0 {
					db.Exec(
						tx,
						`
							with data as (
								select
									unnest(:id) as id,
									unnest(:project) as project,
									unnest(:username) as username
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
									unnest(:id) as id,
									unnest(:wallet_owner) as wallet_owner,
									unnest(:category_name) as category_name,
									unnest(:category_provider) as category_provider,
									unnest(:local_usage) as local_usage,
									unnest(:was_locked) as was_locked,
									unnest(:last_significant_update_at) as last_significant_update_at
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

				if len(allocationRequests.Id) > 0 {
					db.Exec(
						tx,
						`
							with data as (
								select
									unnest(:id) as id,
									unnest(:parent) as parent,
									unnest(:wallet) as wallet,
									unnest(:tree_usage) as tree_usage
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
								d.tree_usage
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
					db.Exec(
						tx,
						`
							with data as (
								select
									unnest(:id) as id,
									unnest(:allocation_group) as allocation_group,
									unnest(:granted_in) as granted_in,
									unnest(:quota) as quota,
									unnest(:start) as start_time,
									unnest(:end) as end_time,
									unnest(:retired) as retired,
									unnest(:retired_usage) as retired_usage
							)
							insert into accounting.wallet_allocations_v2
								(id, associated_allocation_group, granted_in, quota, allocation_start_time, 
									allocation_end_time, retired, retired_usage) 
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
								d.retired_usage
							from
								data d
							on conflict (id) do update set
								granted_in = excluded.granted_in,
								quota = excluded.quota,
								allocation_start_time = excluded.allocation_start_time,
								allocation_end_time = excluded.allocation_end_time,
								retired = excluded.retired,
								retired_usage = excluded.retired_usage
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
						},
					)
				}

				if len(usageRequests.Key) > 0 {
					db.Exec(
						tx,
						`
							with data as (
								select
									unnest(:key) as key,
									unnest(:usage) as usage
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
				remainingHandlers = append(remainingHandlers, handler)
			}
			accGlobals.OnPersistHandlers = remainingHandlers
		})
		time.Sleep(30 * time.Second)
	}
}
