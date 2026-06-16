package accounting

import (
	"database/sql"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func accountingLoad() {
	var loadTimes struct {
		WalletOwners time.Duration
		ScopedUsage  time.Duration
		Wallets      time.Duration
		Promises     time.Duration
		Allocations  time.Duration
	}

	db.NewTx0(func(tx *db.Transaction) {
		timer := util.NewTimer()
		accGlobals.Usage = map[string]*ScopedUsage{}
		accGlobals.Trees = map[accapi.ProductCategoryIdV2]*AccountingTree{}
		accGlobals.OwnersByReference = map[string]*walletOwner{}
		accGlobals.OwnersById = map[OwnerId]*walletOwner{}

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
			owner := &walletOwner{
				Id:    OwnerId(row.Id),
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
			accGlobals.Usage[row.Key] = &ScopedUsage{
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
			ProductCategoryName     string
			ProductCategoryProvider string
			Consumed                int64
			Locked                  bool
			LastSignificantUpdateAt time.Time
			PromiseDemandEwma       int64
			PromiseDemandObserved   int64
			PromiseDemandTrend      int64
			PromiseDemandUpdatedAt  sql.Null[time.Time]
			LowBalanceNotified      bool
		}](
			tx,
			`
				select w.id, wallet_owner, 
					pc.category as product_category_name, pc.provider as product_category_provider, 
					consumed, locked, last_significant_update_at, 
					promise_demand_ewma, promise_demand_observed, promise_demand_trend, 
					promise_demand_updated_at, low_balance_notified
				from 
					accounting.wallets_acc2 w
					join accounting.product_categories pc on w.product_category = pc.id
		    `,
			db.Params{},
		)

		for _, row := range walletRows {
			category, err := ProductCategoryRetrieve(rpc.ActorSystem, row.ProductCategoryName, row.ProductCategoryProvider)
			if err != nil {
				log.Warn("Could not load wallet with id=%v in category %v/%v", row.Id, row.ProductCategoryName, row.ProductCategoryProvider)
				continue
			}

			CategoryEnsure(category)
			_ = treeMutate(category.ToId(), func(tree *AccountingTree) *util.HttpError {
				w := &Wallet{
					Id:                      WalletId(row.Id),
					Allocations:             nil,
					Consumed:                row.Consumed,
					Locked:                  row.Locked,
					Owner:                   accGlobals.OwnersById[OwnerId(row.WalletOwner)].WalletOwner(),
					Category:                category.ToId(),
					LastSignificantUpdateAt: row.LastSignificantUpdateAt,
					PromiseDemandEwma:       row.PromiseDemandEwma,
					PromiseDemandObserved:   row.PromiseDemandObserved,
					PromiseDemandTrend:      row.PromiseDemandTrend,
					PromiseTrendUpdatedAt:   util.SqlNullToOpt(row.PromiseDemandUpdatedAt).GetOrDefault(time.Time{}),
				}

				tree.WalletsByOwner[w.Owner.Reference()] = w
				tree.WalletsById[w.Id] = w

				if int64(w.Id) > accGlobals.WalletIdAcc.Load() {
					accGlobals.WalletIdAcc.Store(int64(w.Id))
				}

				if w.LastSignificantUpdateAt.After(tree.SignificantUpdateAt) {
					tree.SignificantUpdateAt = w.LastSignificantUpdateAt
				}

				return nil
			})
		}
		loadTimes.Wallets = timer.Mark()

		// Promises
		// -------------------------------------------------------------------------------------------------------------
		promiseRows := db.Select[struct {
			Id           int
			CatName      string
			CatProvider  string
			ParentWallet int
			ChildWallet  int
			StartTime    time.Time
			EndTime      time.Time
			Quota        int64
			GrantId      sql.Null[int]
		}](
			tx,
			`
				select p.id, parent_wallet, child_wallet, start_time, end_time, quota, grant_id, 
					pc.category as cat_name, pc.provider as cat_provider
				from 
					accounting.promises_acc2 p
					join accounting.product_categories pc on p.product_category = pc.id
		    `,
			db.Params{},
		)

		for _, row := range promiseRows {
			category, err := ProductCategoryRetrieve(rpc.ActorSystem, row.CatName, row.CatProvider)
			if err != nil {
				log.Warn("Could not load wallet with id=%v in category %v/%v", row.Id, row.CatName, row.CatProvider)
				continue
			}

			accGlobals.Mu.RLock()
			tree := accGlobals.Trees[category.ToId()]
			accGlobals.Mu.RUnlock()
			if tree == nil {
				continue
			}

			tree.Mu.Lock()
			promiseTree := &tree.PromiseTree
			p := &Promise{
				Id:     PromiseId(row.Id),
				Parent: WalletId(row.ParentWallet),
				Child:  WalletId(row.ChildWallet),
				Start:  row.StartTime,
				End:    row.EndTime,
				Quota:  row.Quota,
			}
			if row.GrantId.Valid {
				p.Grant.Set(GrantId(row.GrantId.V))
			}

			promiseTree.PromisesById[p.Id] = p
			promiseTree.PromisesByParent[p.Parent] = append(promiseTree.PromisesByParent[p.Parent], p.Id)
			promiseTree.PromisesByChild[p.Child] = append(promiseTree.PromisesByChild[p.Child], p.Id)
			tree.Mu.Unlock()

			if int64(p.Id) > promiseGlobals.PromiseIdAcc.Load() {
				promiseGlobals.PromiseIdAcc.Store(int64(p.Id))
			}
		}
		loadTimes.Promises = timer.Mark()

		// Allocations
		// -------------------------------------------------------------------------------------------------------------
		allocationRows := db.Select[struct {
			Id               int
			Wallet           int
			Parent           sql.Null[int]
			StartTime        time.Time
			EndTime          time.Time
			QuotaSelf        int64
			QuotaChildren    int64
			ConsumedSelf     int64
			ReservedChildren int64
			RetiredQuota     int64
			RetiredUsage     int64
			Activated        bool
			Retired          bool
			GrantId          sql.Null[int]
			PromiseId        sql.Null[int]
			CatName          string
			CatProvider      string
		}](
			tx,
			`
				select a.id, wallet, parent_allocation, start_time, end_time, quota_self, quota_children, 
					consumed_self, reserved_children, retired_quota, retired_usage, activated, 
					retired, grant_id, promise_id, pc.category as cat_name, pc.provider as cat_provider
				from 
					accounting.allocations_acc2 a
					join accounting.product_categories pc on a.product_category = pc.id
		    `,
			db.Params{},
		)

		for _, row := range allocationRows {
			category, err := ProductCategoryRetrieve(rpc.ActorSystem, row.CatName, row.CatProvider)
			if err != nil {
				log.Warn("Could not load wallet with id=%v in category %v/%v", row.Id, row.CatName, row.CatProvider)
				continue
			}

			CategoryEnsure(category)
			_ = treeMutate(category.ToId(), func(tree *AccountingTree) *util.HttpError {
				w := tree.WalletsById[WalletId(row.Wallet)]
				a := &Allocation{
					Id:               AllocationId(row.Id),
					Wallet:           WalletId(row.Wallet),
					Start:            row.StartTime,
					End:              row.EndTime,
					QuotaSelf:        row.QuotaSelf,
					QuotaChildren:    row.QuotaChildren,
					ConsumedSelf:     row.ConsumedSelf,
					ReservedChildren: row.ReservedChildren,
					RetiredQuota:     row.RetiredQuota,
					RetiredUsage:     row.RetiredUsage,
					Activated:        row.Activated,
					Retired:          row.Retired,

					Parent:   util.Option[AllocationId]{},
					Children: nil,
					Grant:    util.Option[GrantId]{},
					Promise:  util.Option[PromiseId]{},
				}

				if row.Parent.Valid {
					a.Parent.Set(AllocationId(row.Parent.V))
					tree.AllocationsById[a.Parent.Value].Children = append(tree.AllocationsById[a.Parent.Value].Children, a.Id)
				}

				if row.GrantId.Valid {
					a.Grant.Set(GrantId(row.GrantId.V))
				}

				if row.PromiseId.Valid {
					a.Promise.Set(PromiseId(row.PromiseId.V))
				}

				w.Allocations = append(w.Allocations, a.Id)
				tree.AllocationsById[a.Id] = a

				if int64(a.Id) > accGlobals.AllocIdAcc.Load() {
					promiseGlobals.PromiseIdAcc.Store(int64(a.Id))
				}

				return nil
			})
		}
	})
}
