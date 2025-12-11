package accounting

import (
	"net/http"
	"runtime"
	"slices"
	"sync"
	"sync/atomic"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// Core types and globals
// =====================================================================================================================
// Mutex lock order: store -> bucket

type giftId int

var giftGlobals struct {
	TestingEnabled bool
	Buckets        []*giftBucket
	Store          *giftStore
	IdAcc          atomic.Int64
}

type giftStore struct {
	Mu    sync.RWMutex
	Gifts map[giftId]*internalGift
}

type giftBucket struct {
	Mu           sync.RWMutex
	ClaimsByUser map[string]*internalGiftClaims
}

type internalGift struct {
	Id          giftId
	OwnedBy     string
	Resources   []accapi.AllocationRequest
	Criteria    []accapi.UserCriteria
	RenewEvery  int
	Title       string
	Description string
}

type internalGiftClaims struct {
	PreviousClaims map[giftId]time.Time
}

// Public API
// =====================================================================================================================

func lGiftCanClaim(now time.Time, actor rpc.Actor, claims map[giftId]time.Time, gift *internalGift) bool {
	lastClaimed, hasBeenClaimed := claims[gift.Id]
	nextClaimAt := lastClaimed.AddDate(0, gift.RenewEvery, 0)

	canClaim := !hasBeenClaimed ||
		(gift.RenewEvery != 0 && now.After(nextClaimAt))

	if canClaim {
		for _, criteria := range gift.Criteria {
			if grantUserCriteriaMatch(actor, criteria) {
				return true
			}
		}
	}
	return false
}

func GiftsAvailable(now time.Time, actor rpc.Actor) accapi.GiftsAvailableResponse {
	var result []fndapi.FindByIntId

	b := giftsBucketByUser(actor.Username)
	s := giftGlobals.Store
	s.Mu.RLock()
	b.Mu.RLock()

	claims := b.ClaimsByUser[actor.Username].PreviousClaims
	for _, gift := range s.Gifts {
		if lGiftCanClaim(now, actor, claims, gift) {
			result = append(result, fndapi.FindByIntId{Id: int(gift.Id)})
		}
	}

	b.Mu.RUnlock()
	s.Mu.RUnlock()

	return accapi.GiftsAvailableResponse{Gifts: util.NonNilSlice(result)}
}

func GiftsClaim(now time.Time, actor rpc.Actor, id int) *util.HttpError {
	b := giftsBucketByUser(actor.Username)
	s := giftGlobals.Store
	s.Mu.RLock()
	b.Mu.Lock()

	claims := b.ClaimsByUser[actor.Username].PreviousClaims
	gift, ok := s.Gifts[giftId(id)]
	canClaim := ok && lGiftCanClaim(now, actor, claims, gift)
	if canClaim {
		durationInMonths := gift.RenewEvery
		if durationInMonths <= 0 {
			durationInMonths = 12
		}

		resourceExpiration := now.AddDate(0, durationInMonths, 0)

		request := accapi.GrantsSubmitRevisionRequest{
			Revision: accapi.GrantDocument{
				Recipient: accapi.Recipient{
					Type:     accapi.RecipientTypePersonalWorkspace,
					Username: util.OptValue(actor.Username),
				},
				AllocationRequests: nil,
				Form: accapi.Form{
					Type:         accapi.FormTypeGrantGiverInitiated,
					Text:         "Gifted automatically",
					SubAllocator: util.OptValue(false),
				},
				RevisionComment: util.OptValue("Gifted automatically"),
				AllocationPeriod: util.OptValue[accapi.Period](accapi.Period{
					Start: util.OptValue(fndapi.Timestamp(now)),
					End:   util.OptValue(fndapi.Timestamp(resourceExpiration)),
				}),
			},
			Comment: "Gifted automatically",
		}

		request.AlternativeRecipient.Set(request.Revision.Recipient)

		for _, resc := range gift.Resources {
			request.Revision.AllocationRequests = append(request.Revision.AllocationRequests, resc)
		}

		_, err := GrantsSubmitRevisionEx(
			rpc.ActorSystem,
			request,
			util.OptValue(id),
		)

		if err != nil {
			log.Fatal("Unable to claim gift in grant system, this should not be possible: %v", err)
		}

		claims[giftId(id)] = now
	}

	b.Mu.Unlock()
	s.Mu.RUnlock()

	if !canClaim {
		return util.HttpErr(http.StatusForbidden, "unable to claim this gift")
	} else {
		return nil
	}
}

func GiftsBrowse(actor rpc.Actor) ([]accapi.GiftWithCriteria, *util.HttpError) {
	if !actor.Project.Present {
		return nil, nil
	}

	providerId := rpc.ProviderId("")
	for pId, projectId := range actor.ProviderProjects {
		if projectId == actor.Project.Value {
			providerId = pId
			break
		}
	}

	if providerId == "" {
		return nil, nil
	}

	s := giftGlobals.Store
	s.Mu.RLock()
	var result []accapi.GiftWithCriteria
	for _, gift := range s.Gifts {
		if gift.OwnedBy == string(actor.Project.Value) {
			item := accapi.GiftWithCriteria{
				Id: int(gift.Id),
				Gift: accapi.Gift{
					Title:            gift.Title,
					Description:      gift.Description,
					Resources:        nil, // set later
					ResourcesOwnedBy: gift.OwnedBy,
					RenewEvery:       gift.RenewEvery,
				},
				Criteria: nil, // set later
			}

			item.Gift.Resources = make([]accapi.AllocationRequest, len(gift.Resources))
			copy(item.Gift.Resources, gift.Resources)

			item.Criteria = make([]accapi.UserCriteria, len(gift.Criteria))
			copy(item.Criteria, gift.Criteria)

			result = append(result, item)
		}
	}
	s.Mu.RUnlock()

	slices.SortFunc(result, func(a, b accapi.GiftWithCriteria) int {
		if a.Id < b.Id {
			return -1
		} else if a.Id > b.Id {
			return 1
		} else {
			return 0
		}
	})

	return result, nil
}

func GiftsCreate(actor rpc.Actor, spec accapi.GiftWithCriteria) (int, *util.HttpError) {
	var err *util.HttpError
	util.ValidateString(&spec.Title, "title", 0, &err)
	util.ValidateString(&spec.Description, "description", 0, &err)
	if err == nil && len(spec.Resources) == 0 {
		err = util.HttpErr(http.StatusBadRequest, "no resources specified")
	}
	if err == nil && len(spec.Criteria) == 0 {
		err = util.HttpErr(http.StatusBadRequest, "no criteria specified")
	}
	if err == nil && spec.RenewEvery < 0 {
		err = util.HttpErr(http.StatusBadRequest, "invalid renew every policy")
	}
	if err == nil && !actor.Project.Present {
		err = util.HttpErr(http.StatusBadRequest, "project not selected")
	}

	providerId := rpc.ProviderId("")
	if err == nil {
		for pId, projectId := range actor.ProviderProjects {
			if projectId == actor.Project.Value {
				providerId = pId
				break
			}
		}

		if providerId == "" {
			err = util.HttpErr(http.StatusForbidden, "you cannot create a gift from this project")
		}
	}

	if err == nil {
		for i := 0; i < len(spec.Resources); i++ {
			resc := spec.Resources[i]
			resc.GrantGiver = spec.ResourcesOwnedBy
			spec.Resources[i] = resc

			if resc.Provider != string(providerId) {
				err = util.HttpErr(http.StatusForbidden, "you cannot grant this resource")
			}
		}
	}

	wallets := WalletsBrowse(actor, accapi.WalletsBrowseRequest{
		ItemsPerPage: 100,
	}).Items

	for _, resource := range spec.Resources {
		found := false
		for _, wallet := range wallets {
			if resource.Category == wallet.PaysFor.Name && resource.Provider == wallet.PaysFor.Provider {
				found = true
				break
			}
		}
		if !found {
			return -1, util.HttpErr(http.StatusPaymentRequired, "Cannot create a gift to this resource %s (%s) without matching wallet", resource.Category, resource.Provider)
		}
	}

	if err == nil {
		for _, c := range spec.Criteria {
			if err = grantUserCriteriaValid(c); err != nil {
				break
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	if err != nil {
		return 0, err
	}

	spec.ResourcesOwnedBy = string(actor.Project.Value)

	spec.Id = int(giftGlobals.IdAcc.Add(1))

	s := giftGlobals.Store
	s.Mu.Lock()
	s.Gifts[giftId(spec.Id)] = &internalGift{
		Id:          giftId(spec.Id),
		OwnedBy:     spec.ResourcesOwnedBy,
		Resources:   spec.Resources,
		Criteria:    spec.Criteria,
		RenewEvery:  spec.RenewEvery,
		Title:       spec.Title,
		Description: spec.Description,
	}
	s.Mu.Unlock()

	giftPersist(spec)

	return spec.Id, nil
}

func GiftsDelete(actor rpc.Actor, id int) *util.HttpError {
	var err *util.HttpError
	if !actor.Project.Present {
		err = util.HttpErr(http.StatusBadRequest, "project not selected")
	}

	if err != nil {
		return err
	}

	s := giftGlobals.Store
	s.Mu.Lock()
	gift, ok := s.Gifts[giftId(id)]
	if ok {
		if gift.OwnedBy == string(actor.Project.Value) {
			delete(s.Gifts, giftId(id))
		} else {
			err = util.HttpErr(http.StatusForbidden, "you cannot delete this gift")
		}
	} else {
		err = util.HttpErr(http.StatusForbidden, "you cannot delete this gift")
	}
	s.Mu.Unlock()

	if err == nil {
		giftPersistDeletion(id)
	}
	return err
}

// Initialization and RPC
// =====================================================================================================================
// This section contains the initialization logic and RPC handlers. Note that this function will is testing aware and
// turns off parts of the code which cannot run during tests.

func initGifts() {
	g := &giftGlobals
	g.Buckets = nil
	g.Store = &giftStore{Gifts: map[giftId]*internalGift{}}

	for i := 0; i < runtime.NumCPU(); i++ {
		g.Buckets = append(g.Buckets, &giftBucket{
			ClaimsByUser: map[string]*internalGiftClaims{},
		})
	}

	if !g.TestingEnabled {
		giftsLoad()

		accapi.GiftsAvailable.Handler(func(info rpc.RequestInfo, request util.Empty) (accapi.GiftsAvailableResponse, *util.HttpError) {
			return GiftsAvailable(time.Now(), info.Actor), nil
		})

		accapi.GiftsClaim.Handler(func(info rpc.RequestInfo, request accapi.GiftsFindById) (util.Empty, *util.HttpError) {
			return util.Empty{}, GiftsClaim(time.Now(), info.Actor, request.GiftId)
		})

		accapi.GiftsCreate.Handler(func(info rpc.RequestInfo, request accapi.GiftWithCriteria) (fndapi.FindByIntId, *util.HttpError) {
			id, err := GiftsCreate(info.Actor, request)
			if err == nil {
				return fndapi.FindByIntId{Id: id}, nil
			} else {
				return fndapi.FindByIntId{}, err
			}
		})

		accapi.GiftsDelete.Handler(func(info rpc.RequestInfo, request accapi.GiftsFindById) (util.Empty, *util.HttpError) {
			return util.Empty{}, GiftsDelete(info.Actor, request.GiftId)
		})

		accapi.GiftsBrowse.Handler(func(info rpc.RequestInfo, request accapi.GiftsBrowseRequest) (fndapi.PageV2[accapi.GiftWithCriteria], *util.HttpError) {
			items, err := GiftsBrowse(info.Actor)
			if err != nil {
				return fndapi.PageV2[accapi.GiftWithCriteria]{}, err
			} else {
				return fndapi.PageV2[accapi.GiftWithCriteria]{Items: items, ItemsPerPage: len(items)}, nil
			}
		})
	}
}

// Bucket retrieval and persistence
// =====================================================================================================================

func giftsBucketByUser(username string) *giftBucket {
	b := giftGlobals.Buckets[util.NonCryptographicHash(username)%len(giftGlobals.Buckets)]
	b.Mu.RLock()
	_, ok := b.ClaimsByUser[username]
	b.Mu.RUnlock()

	if !ok {
		if !giftGlobals.TestingEnabled {
			type rowType struct {
				GiftId    int
				ClaimedAt time.Time
			}

			rows := db.NewTx(func(tx *db.Transaction) []rowType {
				return db.Select[rowType](
					tx,
					`
						select gift_id, max(claimed_at) as claimed_at
						from "grant".gifts_claimed
						where user_id = :username
						group by gift_id
					`,
					db.Params{
						"username": username,
					},
				)
			})

			b.Mu.Lock()
			_, ok = b.ClaimsByUser[username]
			if !ok {
				claims := &internalGiftClaims{PreviousClaims: map[giftId]time.Time{}}
				for _, row := range rows {
					claims.PreviousClaims[giftId(row.GiftId)] = row.ClaimedAt
				}
				b.ClaimsByUser[username] = claims
			}
			b.Mu.Unlock()
		} else {
			b.Mu.Lock()
			b.ClaimsByUser[username] = &internalGiftClaims{PreviousClaims: map[giftId]time.Time{}}
			b.Mu.Unlock()
		}
	}
	return b
}

func giftsLoad() {
	if giftGlobals.TestingEnabled {
		return
	}

	gifts := db.NewTx(func(tx *db.Transaction) map[giftId]*internalGift {
		giftRows := db.Select[struct {
			Id               int
			ResourcesOwnedBy string
			Title            string
			Description      string
			RenewalPolicy    int
		}](
			tx,
			`
				select id, resources_owned_by, title, description, renewal_policy
				from "grant".gifts
		    `,
			db.Params{},
		)

		giftResources := db.Select[struct {
			GiftId   int
			Quota    int
			Category string
			Provider string
		}](
			tx,
			`
				select gift_id, coalesce(credits, coalesce(quota, 0)) as quota, pc.category, pc.provider
				from
					"grant".gift_resources r
					join accounting.product_categories pc on r.product_category = pc.id
		    `,
			db.Params{},
		)

		giftCriteria := db.Select[struct {
			GiftId      int
			Type        string
			ApplicantId string
		}](
			tx,
			`
				select gift_id, type, coalesce(applicant_id, '') as applicant_id
				from "grant".gifts_user_criteria
		    `,
			db.Params{},
		)

		maxId := 0
		gifts := map[giftId]*internalGift{}
		for _, row := range giftRows {
			if row.Id > maxId {
				maxId = row.Id
			}
			gifts[giftId(row.Id)] = &internalGift{
				Id:          giftId(row.Id),
				OwnedBy:     row.ResourcesOwnedBy,
				RenewEvery:  row.RenewalPolicy,
				Title:       row.Title,
				Description: row.Description,
			}
		}

		for _, resc := range giftResources {
			g := gifts[giftId(resc.GiftId)]
			g.Resources = append(g.Resources, accapi.AllocationRequest{
				Category:         resc.Category,
				Provider:         resc.Provider,
				GrantGiver:       g.OwnedBy,
				BalanceRequested: util.OptValue(int64(resc.Quota)),
			})
		}

		for _, criteria := range giftCriteria {
			g := gifts[giftId(criteria.GiftId)]
			appId := util.OptStringIfNotEmpty(criteria.ApplicantId)

			g.Criteria = append(g.Criteria, accapi.UserCriteria{
				Type:   accapi.UserCriteriaType(criteria.Type),
				Domain: appId,
				Org:    appId,
			})
		}

		giftGlobals.IdAcc.Store(int64(maxId))

		return gifts
	})

	giftGlobals.Store.Gifts = gifts
}

func giftPersist(spec accapi.GiftWithCriteria) {
	if giftGlobals.TestingEnabled {
		return
	}

	db.NewTx0(func(tx *db.Transaction) {
		b := db.BatchNew(tx)

		db.BatchExec(
			b,
			`
				insert into "grant".gifts(id, resources_owned_by, title, description, renewal_policy) 
				values (:id, :owned_by, :title, :description, :renew_every)
		    `,
			db.Params{
				"id":          spec.Id,
				"owned_by":    spec.ResourcesOwnedBy,
				"title":       spec.Title,
				"description": spec.Description,
				"renew_every": spec.RenewEvery,
			},
		)

		for _, c := range spec.Criteria {
			db.BatchExec(
				b,
				`
					insert into "grant".gifts_user_criteria(gift_id, type, applicant_id) 
					values (:gift_id, :ctype, :cid)
			    `,
				db.Params{
					"gift_id": spec.Id,
					"ctype":   c.Type,
					"cid":     c.Domain.GetOrDefault(c.Org.GetOrDefault("")),
				},
			)
		}

		for _, r := range spec.Resources {
			db.BatchExec(
				b,
				`
					insert into "grant".gift_resources(gift_id, credits, quota, product_category) 
					select :gift_id, :quota, :quota, pc.id
					from
						accounting.product_categories pc
					where 
						pc.category = :category
						and pc.provider = :provider
			    `,
				db.Params{
					"gift_id":  spec.Id,
					"quota":    r.BalanceRequested.Value,
					"category": r.Category,
					"provider": r.Provider,
				},
			)
		}

		db.BatchSend(b)
	})
}

func giftPersistDeletion(id int) {
	if giftGlobals.TestingEnabled {
		return
	}

	db.NewTx0(func(tx *db.Transaction) {
		b := db.BatchNew(tx)

		db.BatchExec(
			b,
			`
				delete from "grant".gift_resources
				where gift_id = :id
		    `,
			db.Params{
				"id": id,
			},
		)

		db.BatchExec(
			b,
			`
				delete from "grant".gifts_user_criteria
				where gift_id = :id
		    `,
			db.Params{
				"id": id,
			},
		)

		db.BatchExec(
			b,
			`
				delete from "grant".gifts_claimed
				where gift_id = :id
		    `,
			db.Params{
				"id": id,
			},
		)

		db.BatchExec(
			b,
			`
				delete from "grant".gifts
				where id = :id
		    `,
			db.Params{
				"id": id,
			},
		)

		db.BatchSend(b)
	})
}
