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
		internalCompleteScan(now, func(buckets []*internalBucket, scopes []*scopedUsage) {
			// TODO save stuff to db
		})
		time.Sleep(30 * time.Second)
	}
}
