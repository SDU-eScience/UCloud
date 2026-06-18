package accounting

import (
	"cmp"
	"database/sql"
	"slices"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func accountingLoad() {
	log.Info("Accounting loading")
	defer func() {
		log.Info("Accounting has been loaded")
	}()
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
			ParentAllocation sql.Null[int]
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
				order by a.id
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

				if row.ParentAllocation.Valid {
					a.Parent.Set(AllocationId(row.ParentAllocation.V))
					log.Info("tree: %#v", tree.AllocationsById)
					log.Info("%v %v", row.Id, row.ParentAllocation.V)
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
					accGlobals.AllocIdAcc.Store(int64(a.Id))
				}

				return nil
			})
		}
	})

	now := time.Now()
	accountingRepairLoadedConsumption(now)
	for _, tree := range accGlobals.Trees {
		lifecycleScan(now, tree)

		for _, wallet := range tree.WalletsById {
			PromiseReconcile(now, tree.Category.ToId(), wallet.Owner, util.OptNone[int64]())
		}
	}
}

// accountingRepairLoadedConsumption repairs migrated acc2 state where wallet.Consumed was copied from the old system,
// but the same usage was not distributed onto concrete low-level allocations.
//
// The repair deliberately reuses UsageReport instead of duplicating allocation distribution and promise materialization
// rules in SQL. Parents are repaired before children so local parent consumption reduces the concrete capacity available
// to child promise materializations.
func accountingRepairLoadedConsumption(now time.Time) {
	type repairItem struct {
		category accapi.ProductCategoryIdV2
		owner    accapi.WalletOwner
		walletId WalletId
		depth    int
		usage    int64
	}

	items := []repairItem{}
	accGlobals.Mu.RLock()
	for _, tree := range accGlobals.Trees {
		tree.Mu.RLock()
		for _, wallet := range tree.WalletsById {
			allocationUsage := int64(0)
			for _, allocationId := range wallet.Allocations {
				allocation := tree.AllocationsById[allocationId]
				if allocation != nil {
					allocationUsage += allocation.ConsumedSelf
				}
			}
			if wallet.Consumed > 0 && wallet.Consumed != allocationUsage {
				items = append(items, repairItem{
					category: tree.Category.ToId(),
					owner:    wallet.Owner,
					walletId: wallet.Id,
					depth:    promiseWalletDepth(&tree.PromiseTree, wallet.Id),
					usage:    wallet.Consumed,
				})
			}
		}
		tree.Mu.RUnlock()
	}
	accGlobals.Mu.RUnlock()

	slices.SortFunc(items, func(a, b repairItem) int {
		if a.depth != b.depth {
			return cmp.Compare(a.depth, b.depth)
		}
		return cmp.Compare(int(a.walletId), int(b.walletId))
	})

	for _, item := range items {
		_, err := UsageReport(now, accapi.ReportUsageRequest{
			Owner:        item.owner,
			CategoryIdV2: item.category,
			Usage:        item.usage,
		})
		if err != nil {
			log.Warn("Could not repair loaded accounting consumption for wallet %v: %v", item.walletId, err)
		}
	}
}

func accountingPersist() {
	if accGlobals.TestingEnabled {
		return
	}

	accGlobals.Mu.Lock()

	for _, tree := range accGlobals.Trees {
		tree.Mu.Lock()
	}

	for _, usage := range accGlobals.Usage {
		usage.Mu.Lock()
	}

	defer func() {
		for _, usage := range accGlobals.Usage {
			usage.Dirty = false
			usage.Mu.Unlock()
		}

		for _, tree := range accGlobals.Trees {
			for _, wallet := range tree.WalletsById {
				wallet.Dirty = false
			}
			for _, allocation := range tree.AllocationsById {
				allocation.Dirty = false
			}
			for _, promise := range tree.PromiseTree.PromisesById {
				promise.Dirty = false
			}

			tree.Mu.Unlock()
		}

		for _, owner := range accGlobals.OwnersById {
			owner.Dirty = false
		}

		accGlobals.Mu.Unlock()
	}()

	db.NewTx0(func(tx *db.Transaction) {
		b := db.BatchNew(tx)

		for _, owner := range accGlobals.OwnersById {
			if !owner.Dirty {
				continue
			}
			username := sql.NullString{}
			projectId := sql.NullString{}
			if projectRegex.MatchString(owner.Reference) {
				projectId = sql.NullString{Valid: true, String: owner.Reference}
			} else {
				username = sql.NullString{Valid: true, String: owner.Reference}
			}
			db.BatchExec(
				b,
				`
					insert into accounting.wallet_owner(id, username, project_id)
					values (:id, :username, :project_id)
					on conflict (id) do update set
						username = excluded.username,
						project_id = excluded.project_id
			    `,
				db.Params{
					"id":         owner.Id,
					"username":   username,
					"project_id": projectId,
				},
			)
		}

		for _, usage := range accGlobals.Usage {
			if !usage.Dirty {
				continue
			}
			db.BatchExec(
				b,
				`
					insert into accounting.scoped_usage(key, usage)
					values (:key, :usage)
					on conflict (key) do update set
						usage = excluded.usage
			    `,
				db.Params{
					"key":   usage.Key,
					"usage": usage.Usage,
				},
			)
		}

		for _, tree := range accGlobals.Trees {
			for _, wallet := range tree.WalletsById {
				if !wallet.Dirty {
					continue
				}
				trendUpdatedAt := sql.Null[time.Time]{}
				if !wallet.PromiseTrendUpdatedAt.IsZero() {
					trendUpdatedAt = sql.Null[time.Time]{Valid: true, V: wallet.PromiseTrendUpdatedAt}
				}
				db.BatchExec(
					b,
					`
					insert into accounting.wallets_acc2(
						id, wallet_owner, product_category, consumed, locked, last_significant_update_at,
						promise_demand_ewma, promise_demand_observed, promise_demand_trend,
						promise_demand_updated_at, low_balance_notified
					)
					select :id, wo.id, pc.id, :consumed, :locked, :last_significant_update_at,
						:promise_demand_ewma, :promise_demand_observed, :promise_demand_trend,
						:promise_demand_updated_at, false
					from accounting.wallet_owner wo, accounting.product_categories pc
					where coalesce(wo.username, wo.project_id) = :wallet_owner
						and pc.category = :category
						and pc.provider = :provider
					on conflict (id) do update set
						wallet_owner = excluded.wallet_owner,
						product_category = excluded.product_category,
						consumed = excluded.consumed,
						locked = excluded.locked,
						last_significant_update_at = excluded.last_significant_update_at,
						promise_demand_ewma = excluded.promise_demand_ewma,
						promise_demand_observed = excluded.promise_demand_observed,
						promise_demand_trend = excluded.promise_demand_trend,
						promise_demand_updated_at = excluded.promise_demand_updated_at
			    `,
					db.Params{
						"id":                         wallet.Id,
						"wallet_owner":               wallet.Owner.Reference(),
						"category":                   tree.Category.Name,
						"provider":                   tree.Category.Provider,
						"consumed":                   wallet.Consumed,
						"locked":                     wallet.Locked,
						"last_significant_update_at": wallet.LastSignificantUpdateAt,
						"promise_demand_ewma":        wallet.PromiseDemandEwma,
						"promise_demand_observed":    wallet.PromiseDemandObserved,
						"promise_demand_trend":       wallet.PromiseDemandTrend,
						"promise_demand_updated_at":  trendUpdatedAt,
					},
				)
			}

			for _, promise := range tree.PromiseTree.PromisesById {
				if !promise.Dirty {
					continue
				}
				db.BatchExec(
					b,
					`
					insert into accounting.promises_acc2(
						id, product_category, parent_wallet, child_wallet, start_time, end_time, quota, grant_id
					)
					select :id, pc.id, :parent_wallet, :child_wallet, :start_time, :end_time, :quota, :grant_id
					from accounting.product_categories pc
					where pc.category = :category
						and pc.provider = :provider
					on conflict (id) do update set
						product_category = excluded.product_category,
						parent_wallet = excluded.parent_wallet,
						child_wallet = excluded.child_wallet,
						start_time = excluded.start_time,
						end_time = excluded.end_time,
						quota = excluded.quota,
						grant_id = excluded.grant_id
			    `,
					db.Params{
						"id":            promise.Id,
						"category":      tree.Category.Name,
						"provider":      tree.Category.Provider,
						"parent_wallet": promise.Parent,
						"child_wallet":  promise.Child,
						"start_time":    promise.Start,
						"end_time":      promise.End,
						"quota":         promise.Quota,
						"grant_id":      promise.Grant.Sql(),
					},
				)
				if promise.Grant.Present {
					db.BatchExec(
						b,
						`
							update "grant".applications
							set synchronized = true
							where id = :id
					    `,
						db.Params{
							"id": promise.Grant.Value,
						},
					)
				}
			}

			for _, allocation := range tree.AllocationsById {
				if !allocation.Dirty {
					continue
				}
				db.BatchExec(
					b,
					`
					insert into accounting.allocations_acc2(
						id, product_category, wallet, parent_allocation, start_time, end_time,
						quota_self, quota_children, consumed_self, reserved_children,
						retired_quota, retired_usage, activated, retired, grant_id, promise_id
					)
					select :id, pc.id, :wallet, :parent_allocation, :start_time, :end_time,
						:quota_self, :quota_children, :consumed_self, :reserved_children,
						:retired_quota, :retired_usage, :activated, :retired, :grant_id, :promise_id
					from accounting.product_categories pc
					where pc.category = :category
						and pc.provider = :provider
					on conflict (id) do update set
						product_category = excluded.product_category,
						wallet = excluded.wallet,
						parent_allocation = excluded.parent_allocation,
						start_time = excluded.start_time,
						end_time = excluded.end_time,
						quota_self = excluded.quota_self,
						quota_children = excluded.quota_children,
						consumed_self = excluded.consumed_self,
						reserved_children = excluded.reserved_children,
						retired_quota = excluded.retired_quota,
						retired_usage = excluded.retired_usage,
						activated = excluded.activated,
						retired = excluded.retired,
						grant_id = excluded.grant_id,
						promise_id = excluded.promise_id
			    `,
					db.Params{
						"id":                allocation.Id,
						"category":          tree.Category.Name,
						"provider":          tree.Category.Provider,
						"wallet":            allocation.Wallet,
						"parent_allocation": allocation.Parent.Sql(),
						"start_time":        allocation.Start,
						"end_time":          allocation.End,
						"quota_self":        allocation.QuotaSelf,
						"quota_children":    allocation.QuotaChildren,
						"consumed_self":     allocation.ConsumedSelf,
						"reserved_children": allocation.ReservedChildren,
						"retired_quota":     allocation.RetiredQuota,
						"retired_usage":     allocation.RetiredUsage,
						"activated":         allocation.Activated,
						"retired":           allocation.Retired,
						"grant_id":          allocation.Grant.Sql(),
						"promise_id":        allocation.Promise.Sql(),
					},
				)
			}
		}

		db.BatchSend(b)
	})
}
