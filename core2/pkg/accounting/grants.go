package accounting

import (
	"encoding/json"
	"fmt"
	"math"
	"net/http"
	"runtime"
	"slices"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"ucloud.dk/core/pkg/coreutil"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// Introduction
// =====================================================================================================================
// This file implements UCloud's grant system. The primary purpose of UCloud's grant system is to facilitate the
// creation of new allocations (see accounting system). The grant systems facilitates this by creating a system where
// users can apply to a selection of "grant givers". In this application, a user will argue for why they need resources
// and explicitly state exactly which resources they need. The grant system features a complete application system, in
// which the approvers (administrators in a grant giver project) can comment on the application and make changes to the
// application. All changes of an application are saved as individual revisions which can be reviewed. Ultimately,
// an application is either rejected or approved by the grant givers. If an application is approved by all grant givers,
// then the application is awarded and allocations are awarded to the recipient.
//
// The core concept in the grant system are:
//
// - Application: The structure containing a single application created by an end-user which is sent to relevant
//                grant givers
// - Revision   : A single revision of an application containing the changes of this version. All applications have at
//                least one revision. A revision contains a document and metadata.
// - Document   : The main content of an application revision. Each revision has exactly one document.
// - Recipient  : The workspace (new, existing or personal) which should receive the allocations if the application is
//                approved.
// - Form       : Contains the main arguments (i.e. text) from the end-user arguing for why the application should be
//                approved.
// - Template   : A grant giver specified template which the form should follow.
// - Settings   : Settings of a grant giver which describe who should be allowed to apply to that specific grant giver.

// Core types and globals
// =====================================================================================================================
// This section contain the core-types as well as the global entry point (grantGlobals). From the global
// data-structure (grantGlobals), it is possible to reach all other parts of the system.
//
// References in the internal system are generally done through numeric IDs. These IDs are all integers, but
// are separate Go types to reduce chance of accidental misuse. New numeric IDs are generated through the XXXIdAcc
// atomics stored in the global structure. As this might imply, IDs are global to the system and are not
// namespaced by their bucket.
//
// From grantGlobals, it is possible to reach a bucket. There is one bucket per data-structure type.
//
// !!! Unlike the accounting system, the grant system loads resources on-demand into the buckets.
//     DO NOT assume that data is already present in the buckets. !!!

// ---------------------------------------------------------------------------------------------------------------------
// !!! MUTEX LOCK ORDER !!!
//
// lock order is: grantAppBucket -> grantApplication -> grantSettingsBucket -> grantSettings -> grantIndexBucket
//
// It is assumed that the accounting system never requests information from the grant system. As a result, this system
// is allowed to freely use the functions of the internal accounting system.
// ---------------------------------------------------------------------------------------------------------------------

var grantGlobals struct {
	AppBuckets     []*grantAppBucket
	SettingBuckets []*grantSettingsBucket
	IndexBuckets   []*grantIndexBucket
	CommentIdAcc   atomic.Int64
	GrantIdAcc     atomic.Int64
	Testing        struct {
		Enabled              bool
		ProjectCreateFailure bool
	}
}

type grantAppBucket struct {
	Mu           sync.RWMutex
	Applications map[accGrantId]*grantApplication
}

type grantSettingsBucket struct {
	Mu                sync.RWMutex          // Protects access to maps directly in the bucket
	PublicGrantGivers map[string]util.Empty // grant givers where enabled = true
	Settings          map[string]*grantSettings
}

type grantIndexBucket struct {
	Mu sync.RWMutex // Protects access to maps directly in the bucket

	// Guaranteed to be ordered by time of creation (for a single entity). Contains both applications sent by the entity
	// but also being received. Note that the order is technically by order in which they are created and not
	// timestamps. This can happen if the creation starts before another but locks acquisition happens in reverse order.
	ApplicationsByEntity map[string][]accGrantId
}

type grantApplication struct {
	Mu          sync.RWMutex
	Application *accapi.GrantApplication
	GiftId      util.Option[int]
	Awarded     bool // true once the service has performed the award, does not mean that persist has happened
}

func (a *grantApplication) lId() accGrantId {
	parsed, _ := strconv.ParseInt(a.Application.Id.Value, 10, 64)
	return accGrantId(parsed)
}

func (a *grantApplication) lDeepCopy() accapi.GrantApplication {
	var result accapi.GrantApplication
	b, _ := json.Marshal(a.Application)
	_ = json.Unmarshal(b, &result)
	return result
}

type grantsProjectInfo struct {
	Pi    string
	Title string
}

var grantsProjectCache = util.NewCache[string, grantsProjectInfo](4 * time.Hour)

func GrantApplicationProcess(app accapi.GrantApplication) accapi.GrantApplication {
	recipient := app.CurrentRevision.Document.Recipient
	if recipient.Type == accapi.RecipientTypeExistingProject {
		projectInfo, ok := grantsProjectCache.Get(recipient.Id.Value, func() (grantsProjectInfo, error) {
			project := db.NewTx(func(tx *db.Transaction) fndapi.Project {
				project, _ := coreutil.ProjectRetrieveFromDatabase(tx, recipient.Id.Value)
				return project
			})

			result := grantsProjectInfo{}
			for _, member := range project.Status.Members {
				if member.Role == fndapi.ProjectRolePI {
					result.Pi = member.Username
					break
				}
			}
			result.Title = project.Specification.Title
			return result, nil
		})

		if ok {
			app.Status.ProjectPI = projectInfo.Pi
			app.Status.ProjectTitle.Set(projectInfo.Title)
		}
	}
	return app
}

type grantSettings struct {
	Mu        sync.RWMutex
	ProjectId string
	Settings  *accapi.GrantRequestSettings
}

func (a *grantSettings) lDeepCopy() accapi.GrantRequestSettings {
	var result accapi.GrantRequestSettings
	b, _ := json.Marshal(a.Settings)
	_ = json.Unmarshal(b, &result)
	return result
}

type grantAuthType int

const (
	grantAuthReadWrite grantAuthType = iota // generic read/write action
	grantAuthApprover                       // approver only action
)

type grantActorRole int

const (
	grantActorRoleSubmitter grantActorRole = iota
	grantActorRoleApprover
)

func grantGetAppBucket(key accGrantId) *grantAppBucket {
	h := util.NonCryptographicHash(key)
	return grantGlobals.AppBuckets[h%len(grantGlobals.AppBuckets)]
}

func grantGetSettingsBucket(key string) *grantSettingsBucket {
	h := util.NonCryptographicHash(key)
	return grantGlobals.SettingBuckets[h%len(grantGlobals.SettingBuckets)]
}

func grantGetIdxBucket(key string, load bool) *grantIndexBucket {
	h := util.NonCryptographicHash(key)
	b := grantGlobals.IndexBuckets[h%len(grantGlobals.IndexBuckets)]
	if load {
		grantsInitIndex(b, key)
	}
	return b
}

// Low-level authorization and validation
// =====================================================================================================================
// This section contain the primary building blocks for accessing parts of the systems which require authorization.
// These APIs will typically load the resource if it has not already been brought into one of the buckets.

func grantsReadEx(actor rpc.Actor, action grantAuthType, b *grantAppBucket, id accGrantId, prefetchHint []accGrantId) (*grantApplication, []grantActorRole) {
	var roles []grantActorRole

	b.Mu.RLock()
	app, ok := b.Applications[id]

	if !ok {
		b.Mu.RUnlock()
		grantsLoad(id, prefetchHint) // NOTE(Dan): This function requires us to release the lock since it might touch multiple buckets
		b.Mu.RLock()

		app, ok = b.Applications[id]
	}

	if ok {
		grantGivers := map[string]util.Empty{}

		app.Mu.RLock()
		for _, rev := range app.Application.Status.Revisions {
			for _, req := range rev.Document.AllocationRequests {
				grantGivers[req.GrantGiver] = util.Empty{}
			}
		}

		for grantGiver, _ := range grantGivers {
			if actor.Membership[rpc.ProjectId(grantGiver)].Satisfies(rpc.ProjectRoleAdmin) {
				roles = append(roles, grantActorRoleApprover)
			}
		}

		recipient := app.Application.CurrentRevision.Document.Recipient
		if recipient.Type == accapi.RecipientTypeExistingProject {
			if actor.Membership[rpc.ProjectId(recipient.Id.Value)].Satisfies(rpc.ProjectRoleAdmin) {
				roles = append(roles, grantActorRoleSubmitter)
			}
		}

		if app.Application.CreatedBy == actor.Username {
			roles = append(roles, grantActorRoleSubmitter)
		}

		app.Mu.RUnlock()
	}
	b.Mu.RUnlock()

	roles = slices.Compact(roles)

	switch action {
	case grantAuthReadWrite:
		// Nothing special

	case grantAuthApprover:
		if !slices.Contains(roles, grantActorRoleApprover) {
			app, roles = nil, nil
		}
	}

	if len(roles) == 0 {
		return nil, nil
	} else {
		return app, roles
	}
}

// grantsRead will read a grantApplication and perform authorization according to the action.
func grantsRead(actor rpc.Actor, action grantAuthType, id accGrantId, prefetchHint []accGrantId) (*grantApplication, []grantActorRole) {
	return grantsReadEx(actor, action, grantGetAppBucket(id), id, prefetchHint)
}

func grantsRetrieveSettings(grantGiver string) (*grantSettings, bool) {
	b := grantGetSettingsBucket(grantGiver)
	b.Mu.RLock()
	settings, ok := b.Settings[grantGiver]
	b.Mu.RUnlock()
	return settings, ok
}

func grantsCanApply(actor rpc.Actor, recipient accapi.Recipient, grantGiver string) bool {
	walletOwner := ""
	allowed := false
	excluded := false

	switch recipient.Type {
	case accapi.RecipientTypePersonalWorkspace:
		walletOwner = actor.Username

	case accapi.RecipientTypeExistingProject:
		walletOwner = recipient.Id.Value
		if !actor.Membership[rpc.ProjectId(recipient.Id.Value)].Satisfies(rpc.ProjectRoleAdmin) {
			excluded = true
		}

	case accapi.RecipientTypeNewProject:
		// No existing owner
	}
	sWrapper, hasSettings := grantsRetrieveSettings(grantGiver)
	if hasSettings {
		sWrapper.Mu.RLock()
		settings := sWrapper.Settings
		if !excluded {
			allowFrom := settings.AllowRequestsFrom
			excludeFrom := settings.ExcludeRequestsFrom

			for _, exclude := range excludeFrom {
				if grantUserCriteriaMatch(actor, exclude) {
					excluded = true
				}
			}

			if excluded {
				allowed = false
			} else {
				for _, include := range allowFrom {
					if grantUserCriteriaMatch(actor, include) {
						allowed = true
						break
					}
				}
			}
		}
		sWrapper.Mu.RUnlock()
	}

	// A user is _always_ allowed to apply to a parent, regardless of settings
	if !allowed && walletOwner != "" {
		wallets := internalRetrieveWallets(time.Now(), walletOwner, walletFilter{})

	outer:
		for _, w := range wallets {
			for _, ag := range w.AllocationGroups {
				hasAnyAllocation := false
				for _, alloc := range ag.Group.Allocations {
					if alloc.Quota > 0 {
						hasAnyAllocation = true
						break
					}
				}

				if hasAnyAllocation && ag.Parent.Present && ag.Parent.Value.ProjectId == grantGiver {
					allowed = true
					break outer
				}
			}
		}
	}

	return allowed
}

func grantUserCriteriaValid(criteria accapi.UserCriteria) *util.HttpError {
	var err *util.HttpError
	util.ValidateEnum(&criteria.Type, accapi.UserCriteriaTypeOptions, "criteria.type", &err)
	return err
}

func grantUserCriteriaMatch(actor rpc.Actor, criteria accapi.UserCriteria) bool {
	switch criteria.Type {
	case accapi.UserCriteriaTypeAnyone:
		return true

	case accapi.UserCriteriaTypeEmail:
		return strings.EqualFold(criteria.Domain.Value, actor.Domain)

	case accapi.UserCriteriaTypeWayf:
		return strings.EqualFold(criteria.Org.Value, actor.OrgId)

	default:
		log.Warn("unknown user criteria type: '%s'", criteria.Type)
		return false
	}
}

// Grant application lifecycle
// =====================================================================================================================
// This section contains the full application lifecycle with a focus on the write calls. The next section contains the
// read calls (retrieve & browse).

func GrantsSubmitRevision(actor rpc.Actor, req accapi.GrantsSubmitRevisionRequest) (int64, *util.HttpError) {
	return GrantsSubmitRevisionEx(actor, req, util.OptNone[int]())
}

func GrantsSubmitRevisionEx(actor rpc.Actor, req accapi.GrantsSubmitRevisionRequest, giftId util.Option[int]) (int64, *util.HttpError) {
	now := time.Now()

	// "Syntactic" validation of the request
	// -----------------------------------------------------------------------------------------------------------------
	// Check that all fields are present and that they fulfill basic validation requirements (e.g. you cannot
	// request a negative quota). This section consists mostly of early returns since no locks are being held yet.

	revision := &req.Revision

	if req.Comment == "" {
		return 0, util.HttpErr(http.StatusBadRequest, "comment must not be empty")
	} else {
		revision.RevisionComment.Set(req.Comment)
	}

	if revision.Form.SubAllocator.GetOrDefault(false) && actor.Username != rpc.ActorSystem.Username {
		if !actor.Project.Present {
			return 0, util.HttpErr(http.StatusBadRequest, "invalid active project with suballocator field set")
		}

		_, isAllocator := actor.AllocatorProjects[actor.Project.Value]
		if !isAllocator {
			return 0, util.HttpErr(http.StatusBadRequest, "invalid active project with suballocator field set")
		}
	}

	recipient := revision.Recipient
	if req.AlternativeRecipient.Present {
		recipient = req.AlternativeRecipient.Value
	}

	revision.Recipient = recipient

	if !recipient.Valid() {
		return 0, util.HttpErr(http.StatusBadRequest, "invalid recipient")
	}

	if recipient.Type == accapi.RecipientTypeNewProject {
		if err := util.ValidateStringE(&recipient.Title.Value, "recipient.title", 0); err != nil {
			return 0, err
		}

		if strings.HasPrefix(recipient.Title.Value, "%") {
			// NOTE(Dan): Used as a hint to the frontend about special projects. Not used for anything backend related.
			return 0, util.HttpErr(http.StatusBadRequest, "project title cannot start with '%%'")
		}
	}

	if !revision.Form.Type.Valid() {
		return 0, util.HttpErr(http.StatusBadRequest, "form type is invalid")
	}

	period := revision.AllocationPeriod

	requestsSeen := map[util.Tuple3[string, string, string]]util.Empty{}
	hasRequest := false
	for _, allocReq := range revision.AllocationRequests {
		if allocReq.BalanceRequested.Present {
			if allocReq.BalanceRequested.GetOrDefault(0) > 0 {
				hasRequest = true

				if !period.Present && allocReq.Period.End.Present {
					period.Set(allocReq.Period)
				}

				if recipient.Type == accapi.RecipientTypeExistingProject && allocReq.GrantGiver == recipient.Id.Value {
					return 0, util.HttpErr(http.StatusBadRequest, "you cannot allocate resources to yourself")
				}
			} else if allocReq.BalanceRequested.GetOrDefault(0) < 0 {
				return 0, util.HttpErr(http.StatusBadRequest, "requested quota cannot be negative")
			}
		}

		key := util.Tuple3[string, string, string]{allocReq.Category, allocReq.Provider, allocReq.GrantGiver}
		if _, seen := requestsSeen[key]; seen {
			return 0, util.HttpErr(http.StatusBadRequest, "duplicate request")
		} else {
			requestsSeen[key] = util.Empty{}
		}
	}

	if !hasRequest {
		return 0, util.HttpErr(http.StatusBadRequest, "application contains no requests")
	}

	if !period.Present || !period.Value.End.Present {
		return 0, util.HttpErr(http.StatusBadRequest, "application must contain an end-date")
	}

	if period.Present && period.Value.Start.Present && period.Value.End.Present {
		if period.Value.Start.Value.Time().After(period.Value.End.Value.Time()) {
			return 0, util.HttpErr(http.StatusBadRequest, "application has an invalid period specified")
		}
	}

	filteredRefs := []string{}
	for _, id := range revision.ReferenceIds.GetOrDefault(nil) {
		if err := checkDeicReferenceFormat(id); err != nil {
			return 0, err
		}

		if id != "" {
			filteredRefs = append(filteredRefs, id)
		}
	}
	revision.ReferenceIds.Set(filteredRefs)

	if revision.Form.Type == accapi.FormTypeGrantGiverInitiated && req.ApplicationId.Present {
		return 0, util.HttpErr(http.StatusBadRequest, "grant giver initiated applications must be new applications")
	}

	if !req.ApplicationId.Present && recipient.Type == accapi.RecipientTypeExistingProject {
		if !actor.Membership[rpc.ProjectId(recipient.Id.Value)].Satisfies(rpc.ProjectRoleAdmin) {
			return 0, util.HttpErr(http.StatusBadRequest, "you are not allowed to apply to this project")
		}
	}

	// Read and locate the grantApplication
	// -----------------------------------------------------------------------------------------------------------------
	// If the grant application is an existing one, then it will be loaded from the cache or database. This step will
	// in that case authorize that the actor is allowed to perform a submission on the application.

	id := accGrantId(0)
	wasExistingApplication := req.ApplicationId.Present
	if wasExistingApplication {
		parsed, err := strconv.ParseInt(req.ApplicationId.Value, 10, 64)
		if err != nil {
			return 0, util.HttpErr(http.StatusNotFound, "unknown application")
		}

		id = accGrantId(parsed)
	} else {
		id = accGrantId(grantGlobals.GrantIdAcc.Add(1))
	}

	if wasExistingApplication && giftId.Present {
		return 0, util.HttpErr(http.StatusBadRequest, "gift id should not be specified here")
	}

	b := grantGetAppBucket(id)

	var app *grantApplication
	roles := []grantActorRole{grantActorRoleSubmitter}
	if wasExistingApplication {
		app, roles = grantsReadEx(actor, grantAuthReadWrite, b, id, nil)
		if app == nil {
			return 0, util.HttpErr(http.StatusNotFound, "unknown application")
		}

		app.Mu.Lock()
	} else {
		b.Mu.Lock()
		app = &grantApplication{
			Mu: sync.RWMutex{},
			Application: &accapi.GrantApplication{
				Id:        util.IntOrString{Value: fmt.Sprint(id)},
				CreatedBy: actor.Username,
				CreatedAt: fndapi.Timestamp(now),
				UpdatedAt: fndapi.Timestamp(now),
				Status: accapi.GrantStatus{
					OverallState:   accapi.GrantApplicationStateInProgress,
					StateBreakdown: []accapi.GrantGiverApprovalState{},
					Comments:       []accapi.GrantComment{},
					Revisions:      []accapi.GrantRevision{},
				},
			},
		}

		b.Applications[id] = app
		app.Mu.Lock()
	}

	// Semantic validation
	// -----------------------------------------------------------------------------------------------------------------
	// Perform semantic validation which ensures that all references within the revision make sense semantically in its
	// broader context. Checks should only be placed in this section if they are impossible to perform without reading
	// details from the internal data structures.
	//
	// NOTE(Dan): Request is syntactically valid and authorized. app mutex is locked here, bucket mutex
	// has is held for new applications. The request can still be rejected for authorization reasons within the
	// revision itself.

	if !slices.Contains(roles, grantActorRoleApprover) {
		revision.ReferenceIds = app.Application.CurrentRevision.Document.ReferenceIds
	}

	var err *util.HttpError

	grantGiverInitiatedId := ""
	grantGivers := map[string][]accapi.ProductCategory{}
	for _, allocReq := range revision.AllocationRequests {
		category, catErr := ProductCategoryRetrieve(actor, allocReq.Category, allocReq.Provider)
		if catErr != nil {
			err = catErr
			break
		} else {
			grantGivers[allocReq.GrantGiver] = append(grantGivers[allocReq.GrantGiver], category)
		}
	}

	if err == nil && giftId.Present && revision.Form.Type != accapi.FormTypeGrantGiverInitiated {
		err = util.HttpErr(http.StatusInternalServerError, "gift id should not be present")
	}

	if err == nil && revision.Form.Type == accapi.FormTypeGrantGiverInitiated {
		if len(grantGivers) != 1 {
			err = util.HttpErr(http.StatusForbidden, "invalid allocation request")
		} else {
			for grantGiver, _ := range grantGivers {
				grantGiverInitiatedId = grantGiver
				break
			}

			if actor.Username != rpc.ActorSystem.Username && !actor.Membership[rpc.ProjectId(grantGiverInitiatedId)].Satisfies(rpc.ProjectRoleAdmin) {
				err = util.HttpErr(http.StatusForbidden, "you are not an administrator of this project")
			}
		}
	}

	if err == nil && app.Application.Status.OverallState != accapi.GrantApplicationStateInProgress {
		if !slices.Contains(roles, grantActorRoleApprover) {
			err = util.HttpErr(http.StatusBadRequest, "application has been closed and cannot be changed further")
		}
	}

	if err == nil {
		senderActor, ok := actor, true
		if actor.Username != app.Application.CreatedBy {
			senderActor, ok = rpc.LookupActor(app.Application.CreatedBy)
		}

		if !ok {
			err = util.HttpErr(http.StatusInternalServerError, "internal error")
		} else {
		outer:
			for grantGiver, categories := range grantGivers {
				if revision.Form.Type != accapi.FormTypeGrantGiverInitiated && !grantsCanApply(senderActor, recipient, grantGiver) {
					err = util.HttpErr(http.StatusBadRequest, "you cannot apply to these grant givers")
				}

				owner := internalOwnerByReference(grantGiver)
				for _, cat := range categories {
					aBucket := internalBucketOrInit(cat)
					w := internalWalletByOwner(aBucket, now, owner.Id)
					quota, ok := internalWalletTotalQuotaContributing(aBucket, w)
					if quota == 0 || !ok {
						err = util.HttpErr(
							http.StatusBadRequest,
							"%s/%s cannot be requested in this application",
							cat.Name,
							cat.Provider,
						)
						break outer
					}
				}
			}
		}
	}

	if err != nil {
		if !wasExistingApplication {
			delete(b.Applications, id)
			app.Mu.Unlock()
			b.Mu.Unlock()
		} else {
			app.Mu.Unlock()
		}
		return 0, err
	}

	if !wasExistingApplication {
		b.Mu.Unlock()
	}

	// Data update and sync
	// -----------------------------------------------------------------------------------------------------------------
	// Application is now guaranteed to be semantically valid and the request is expected to succeed. The application
	// mutex is still held.

	didApprove := false
	app.Application.UpdatedAt = fndapi.Timestamp(now)

	prevState := app.Application.Status.OverallState
	if prevState == accapi.GrantApplicationStateApproved || prevState == accapi.GrantApplicationStateRejected {
		// NOTE(Dan): If the application is already closed, then only a small fraction of properties will be used in
		// the update. The values that are mutable are copied and the "revision" variable is replaced by the current
		// revision.

		newIds := revision.ReferenceIds
		*revision = app.Application.CurrentRevision.Document
		revision.RevisionComment.Set(req.Comment)
		revision.ReferenceIds = newIds
	} else if revision.Form.Type == accapi.FormTypeGrantGiverInitiated {
		app.Application.Status.OverallState = accapi.GrantApplicationStateApproved
		app.Application.Status.StateBreakdown = []accapi.GrantGiverApprovalState{
			{
				ProjectId: grantGiverInitiatedId,
				State:     accapi.GrantApplicationStateApproved,
			},
		}

		didApprove = true
	} else {
		// NOTE(Dan): Any change in the application will automatically reset the state to in-progress
		app.Application.Status.OverallState = accapi.GrantApplicationStateInProgress

		breakdown := []accapi.GrantGiverApprovalState{}
		for grantGiver, _ := range grantGivers {
			breakdown = append(breakdown, accapi.GrantGiverApprovalState{
				ProjectId: grantGiver,
				State:     accapi.GrantApplicationStateInProgress,
			})
		}
		app.Application.Status.StateBreakdown = breakdown
	}

	// Swap active revision and persist
	// -----------------------------------------------------------------------------------------------------------------
	// Do not mutate "revision" after this point.

	if !wasExistingApplication {
		{
			// Sender indexing
			indexKey := ""
			switch recipient.Type {
			case accapi.RecipientTypeNewProject, accapi.RecipientTypePersonalWorkspace:
				indexKey = app.Application.CreatedBy
			case accapi.RecipientTypeExistingProject:
				indexKey = recipient.Id.Value
			}

			idxB := grantGetIdxBucket(indexKey, true)
			idxB.Mu.Lock()
			idxB.ApplicationsByEntity[indexKey] = append(idxB.ApplicationsByEntity[indexKey], id)
			idxB.Mu.Unlock()
		}

		{
			// Receiver indexing
			for grantGiver, _ := range grantGivers {
				idxB := grantGetIdxBucket(grantGiver, true)
				idxB.Mu.Lock()
				idxB.ApplicationsByEntity[grantGiver] = append(idxB.ApplicationsByEntity[grantGiver], id)
				idxB.Mu.Unlock()
			}
		}
	}

	revToInsert := accapi.GrantRevision{
		CreatedAt:      fndapi.Timestamp(now),
		UpdatedBy:      actor.Username,
		RevisionNumber: len(app.Application.Status.Revisions) + 1,
		Document:       *revision,
	}

	app.Application.Status.Revisions = append(app.Application.Status.Revisions, revToInsert)
	app.Application.CurrentRevision = revToInsert

	if giftId.Present {
		app.GiftId = giftId
	}

	lGrantsPersist(app)

	if didApprove {
		lGrantsAwardResources(app)
	}

	appCopy := *app.Application

	app.Mu.Unlock()

	if !giftId.Present {
		// We can send a notification

		newState := appCopy.Status.OverallState

		switch {
		case !wasExistingApplication && newState == accapi.GrantApplicationStateInProgress:
			// New application
			grantHandleEvent(grantEvent{
				Type:                   grantEvApplicationSubmitted,
				EventSourceIsApplicant: actor.Username == appCopy.CreatedBy,
				Actor:                  actor,
				Application:            appCopy,
			})

		case wasExistingApplication && newState == accapi.GrantApplicationStateInProgress:
			// Update to existing
			grantHandleEvent(grantEvent{
				Type:                   grantEvRevisionSubmitted,
				EventSourceIsApplicant: actor.Username == appCopy.CreatedBy,
				Actor:                  actor,
				Application:            appCopy,
			})
		}
	}

	return int64(id), nil
}

func GrantsTransfer(actor rpc.Actor, req accapi.GrantsTransferRequest) *util.HttpError {
	now := time.Now()

	if !actor.Project.Present {
		return util.HttpErr(http.StatusForbidden, "you cannot transfer this application")
	}

	source := actor.Project.Value
	if !actor.Membership[rpc.ProjectId(source)].Satisfies(rpc.ProjectRoleAdmin) {
		return util.HttpErr(http.StatusForbidden, "you cannot transfer this application")
	}

	appId, _ := strconv.ParseInt(req.ApplicationId, 10, 64)
	b := grantGetAppBucket(accGrantId(appId))
	app, _ := grantsReadEx(actor, grantAuthApprover, b, accGrantId(appId), nil)
	if app == nil {
		return util.HttpErr(http.StatusNotFound, "not found")
	}

	var err *util.HttpError
	revisionRequest := accapi.GrantsSubmitRevisionRequest{
		Revision:      accapi.GrantDocument{}, // filled out below
		Comment:       "Application transferred to a new grant giver",
		ApplicationId: util.OptValue(req.ApplicationId),
	}

	app.Mu.RLock()
	sender, ok := rpc.LookupActor(app.Application.CreatedBy)
	if !ok {
		log.Warn("user no longer exists in grant application transfer: %v %v", req.ApplicationId, app.Application.CreatedBy)
		err = util.HttpErr(http.StatusInternalServerError, "internal error")
	}

	if err == nil {
		if !grantsCanApply(sender, app.Application.CurrentRevision.Document.Recipient, req.Target) {
			err = util.HttpErr(http.StatusForbidden, "this application cannot be transferred to that target")
		}
	}

	if err == nil {
		if app.Application.Status.OverallState != accapi.GrantApplicationStateInProgress {
			err = util.HttpErr(http.StatusBadRequest, "you cannot transfer an application after it has been closed")
		}
	}

	if err == nil {
		// Locate the part that we are transferring and determine the new transfer set.

		requests := app.Application.CurrentRevision.Document.AllocationRequests
		didTransferAny := false
		var newRequests []accapi.AllocationRequest
		for _, allocReq := range requests {
			if allocReq.GrantGiver != string(source) {
				newRequests = append(newRequests, allocReq)
			} else {
				wallets := internalRetrieveWallets(now, req.Target, walletFilter{
					Category:      util.OptValue(allocReq.Category),
					Provider:      util.OptValue(allocReq.Provider),
					RequireActive: true,
				})

				if len(wallets) == 1 {
					allocReq.GrantGiver = req.Target
					newRequests = append(newRequests, allocReq)
					didTransferAny = true
				}
			}
		}

		if !didTransferAny {
			err = util.HttpErr(
				http.StatusBadRequest,
				"none of the requested resources are available at the target grant giver",
			)
		} else {
			revisionRequest.Revision = app.Application.CurrentRevision.Document
			revisionRequest.Revision.AllocationRequests = newRequests
		}
	}

	app.Mu.RUnlock()

	if err == nil {
		_, err = GrantsSubmitRevision(actor, revisionRequest)
	}

	// TODO call grantHandleEvent() for grant transfer here

	return err
}

func GrantsPostComment(actor rpc.Actor, req accapi.GrantsPostCommentRequest) (string, *util.HttpError) {
	if len(req.Comment) > 1024*1024 {
		return "", util.HttpErr(http.StatusForbidden, "your comment is too long")
	}

	appId, _ := strconv.ParseInt(req.ApplicationId, 10, 64)
	b := grantGetAppBucket(accGrantId(appId))
	app, _ := grantsReadEx(actor, grantAuthReadWrite, b, accGrantId(appId), nil)
	if app == nil {
		return "", util.HttpErr(http.StatusNotFound, "unknown application")
	}

	app.Mu.Lock()
	commentId := grantGlobals.CommentIdAcc.Add(1)
	app.Application.Status.Comments = append(app.Application.Status.Comments, accapi.GrantComment{
		Id:        util.IntOrString{fmt.Sprint(commentId)},
		Username:  actor.Username,
		CreatedAt: fndapi.Timestamp(time.Now()),
		Comment:   req.Comment,
	})

	lGrantsPersist(app)
	appCopy := *app.Application
	app.Mu.Unlock()

	grantHandleEvent(grantEvent{
		Type:                   grantEvNewComment,
		EventSourceIsApplicant: actor.Username == appCopy.CreatedBy,
		Actor:                  actor,
		Application:            appCopy,
	})

	return fmt.Sprint(commentId), nil
}

func GrantsDeleteComment(actor rpc.Actor, req accapi.GrantsDeleteCommentRequest) *util.HttpError {
	appId, _ := strconv.ParseInt(req.ApplicationId, 10, 64)
	b := grantGetAppBucket(accGrantId(appId))
	app, roles := grantsReadEx(actor, grantAuthReadWrite, b, accGrantId(appId), nil)
	if app == nil {
		return util.HttpErr(http.StatusNotFound, "unknown application")
	}

	app.Mu.Lock()
	found := false
	for i, comment := range app.Application.Status.Comments {
		if comment.Id.Value == req.CommentId && (slices.Contains(roles, grantActorRoleApprover) || comment.Username == actor.Username) {
			app.Application.Status.Comments = util.RemoveAtIndex(app.Application.Status.Comments, i)
			found = true
			break
		}
	}
	if found {
		lGrantsPersist(app)
	}
	app.Mu.Unlock()

	if found {
		return nil
	} else {
		return util.HttpErr(http.StatusNotFound, "unknown comment")
	}
}

func GrantsUpdateState(actor rpc.Actor, req accapi.GrantsUpdateStateRequest) *util.HttpError {
	var err *util.HttpError
	idRaw, _ := strconv.ParseInt(req.ApplicationId, 10, 64)
	idActual := accGrantId(idRaw)
	app, roles := grantsRead(actor, grantAuthReadWrite, idActual, nil)
	if app == nil {
		return util.HttpErr(http.StatusNotFound, "not found")
	}

	if req.NewState == accapi.GrantApplicationStateClosed && !slices.Contains(roles, grantActorRoleSubmitter) {
		return util.HttpErr(http.StatusBadRequest, "grant givers cannot withdraw an application")
	}

	// TODO we probably need to check a lot more enums in the code base. Remove this comment when certain that all
	//   parts of the code is OK.
	if _, ok := util.VerifyEnum(req.NewState, accapi.GrantApplicationStates); !ok {
		return util.HttpErr(http.StatusBadRequest, "invalid state")
	}

	if req.NewState != accapi.GrantApplicationStateClosed {
		if !slices.Contains(roles, grantActorRoleApprover) {
			return util.HttpErr(http.StatusForbidden, "you cannot change this state")
		}

		if !actor.Project.Present {
			return util.HttpErr(http.StatusBadRequest, "no project given, who do you represent?")
		}
	}

	if req.NewState == accapi.GrantApplicationStateInProgress {
		return util.HttpErr(http.StatusBadRequest, "you cannot update an application to be in progress")
	}

	app.Mu.Lock()

	prevState := app.Application.Status.OverallState

	if app.Application.Status.OverallState != accapi.GrantApplicationStateInProgress {
		err = util.HttpErr(http.StatusBadRequest, "application is no longer active")
	}

	if err == nil {
		// Operation is now expected to succeed in most cases

		if req.NewState == accapi.GrantApplicationStateClosed {
			app.Application.Status.OverallState = accapi.GrantApplicationStateClosed
			lGrantsPersist(app)
		} else {
			newOverallState := accapi.GrantApplicationStateInProgress
			anyRejected := false
			allApproved := true
			anyUpdated := false

			for i, _ := range app.Application.Status.StateBreakdown {
				breakdown := &app.Application.Status.StateBreakdown[i]

				if breakdown.ProjectId == string(actor.Project.Value) {
					breakdown.State = req.NewState
					anyUpdated = true
				}

				if breakdown.State != accapi.GrantApplicationStateApproved {
					allApproved = false
				}

				if breakdown.State == accapi.GrantApplicationStateRejected {
					anyRejected = true
					break
				}
			}

			if !anyUpdated {
				err = util.HttpErr(http.StatusBadRequest, "which project do you represent?")
			} else {
				if anyRejected {
					newOverallState = accapi.GrantApplicationStateRejected
				} else if allApproved {
					newOverallState = accapi.GrantApplicationStateApproved
				}

				app.Application.Status.OverallState = newOverallState
				lGrantsPersist(app)

				if newOverallState == accapi.GrantApplicationStateApproved {
					lGrantsAwardResources(app)
				}
			}
		}
	}

	appCopy := *app.Application

	app.Mu.Unlock()

	newState := appCopy.Status.OverallState
	didChangeState := prevState != newState
	if didChangeState {
		if newState == accapi.GrantApplicationStateRejected {
			// Application was rejected
			grantHandleEvent(grantEvent{
				Type:                   grantEvApplicationRejected,
				EventSourceIsApplicant: actor.Username == appCopy.CreatedBy,
				Actor:                  actor,
				Application:            appCopy,
			})
		} else if newState == accapi.GrantApplicationStateApproved {
			// Application was approved
			grantHandleEvent(grantEvent{
				Type:                   grantEvGrantAwarded,
				EventSourceIsApplicant: actor.Username == appCopy.CreatedBy,
				Actor:                  actor,
				Application:            appCopy,
			})
		}
	}
	return err
}

// Grant application retrieval
// =====================================================================================================================
// Public functions for retrieving grant applications.

func GrantsBrowse(actor rpc.Actor, req accapi.GrantsBrowseRequest) fndapi.PageV2[accapi.GrantApplication] {
	var ids []accGrantId

	nextIdRaw, err := strconv.ParseInt(req.Next.Value, 10, 64)
	nextId := accGrantId(nextIdRaw)
	if !req.Next.Present || err != nil {
		nextId = accGrantId(math.MaxInt)
	}

	idxB := grantGetIdxBucket(actor.Username, true)
	idxB.Mu.RLock()
	for _, id := range idxB.ApplicationsByEntity[actor.Username] {
		if id < nextId {
			ids = append(ids, id)
		}
	}
	idxB.Mu.RUnlock()

	if actor.Project.Present && actor.Membership[actor.Project.Value].Satisfies(rpc.ProjectRoleAdmin) {
		idxB = grantGetIdxBucket(string(actor.Project.Value), true)
		idxB.Mu.RLock()
		for _, id := range idxB.ApplicationsByEntity[string(actor.Project.Value)] {
			if id < nextId {
				ids = append(ids, id)
			}
		}
		idxB.Mu.RUnlock()
	}

	slices.SortFunc(ids, func(a, b accGrantId) int {
		if a < b {
			return 1
		} else if a > b {
			return -1
		} else {
			return 0
		}
	})

	filter := req.Filter.GetOrDefault(accapi.GrantApplicationFilterActive)

	itemsPerPage := fndapi.ItemsPerPage(req.ItemsPerPage)
	var items []accapi.GrantApplication
	hasMore := false

	for index, id := range ids {
		if hasMore {
			break
		} else if index > 0 && ids[index-1] == id {
			continue
		}

		a, roles := grantsRead(actor, grantAuthReadWrite, id, ids)
		if a != nil {
			a.Mu.RLock()

			relevant := false
			if filter == accapi.GrantApplicationFilterShowAll {
				relevant = true
			} else if a.Application.Status.OverallState == accapi.GrantApplicationStateInProgress && filter == accapi.GrantApplicationFilterActive {
				relevant = true
			} else if a.Application.Status.OverallState != accapi.GrantApplicationStateInProgress && filter == accapi.GrantApplicationFilterInactive {
				relevant = true
			}

			if relevant {
				allowIngoing := req.IncludeIngoingApplications.GetOrDefault(false)
				allowOutgoing := req.IncludeOutgoingApplications.GetOrDefault(false)
				isSubmitter := slices.Contains(roles, grantActorRoleSubmitter) && lGrantApplicationIsSubmitterInActiveProjectNoAuth(actor, a)
				isApprover := slices.Contains(roles, grantActorRoleApprover) && lGrantApplicationIsApproverInActiveProjectNoAuth(actor, a)

				relevant = false

				if allowIngoing && isApprover {
					relevant = true
				} else if allowOutgoing && isSubmitter {
					relevant = true
				}
			}

			if relevant {
				if len(items) >= itemsPerPage {
					hasMore = true
					a.Mu.RUnlock()
				} else {
					deepCopy := a.lDeepCopy()
					a.Mu.RUnlock()
					items = append(items, GrantApplicationProcess(deepCopy))
				}
			} else {
				a.Mu.RUnlock()
			}
		}
	}

	result := fndapi.PageV2[accapi.GrantApplication]{
		Items:        items,
		ItemsPerPage: itemsPerPage,
	}

	if hasMore {
		result.Next.Set(items[len(items)-1].Id.Value)
	}

	return result
}

// NOTE(Dan): This function assumes auth has already taken place.
func lGrantApplicationIsSubmitterInActiveProjectNoAuth(actor rpc.Actor, a *grantApplication) bool {
	recipient := a.Application.CurrentRevision.Document.Recipient
	if recipient.Type == accapi.RecipientTypePersonalWorkspace && !actor.Project.Present {
		return true
	} else if recipient.Type == accapi.RecipientTypeNewProject && !actor.Project.Present {
		return true
	} else if recipient.Type == accapi.RecipientTypeExistingProject && string(actor.Project.Value) == recipient.Id.Value {
		return true
	} else {
		return false
	}
}

// NOTE(Dan): This function assumes auth has already taken place.
func lGrantApplicationIsApproverInActiveProjectNoAuth(actor rpc.Actor, a *grantApplication) bool {
	if !actor.Project.Present {
		return false
	}

	reqs := a.Application.CurrentRevision.Document.AllocationRequests
	for _, req := range reqs {
		if req.GrantGiver == string(actor.Project.Value) {
			return true
		}
	}
	return false
}

func GrantsRetrieve(actor rpc.Actor, id string) (accapi.GrantApplication, *util.HttpError) {
	idRaw, _ := strconv.ParseInt(id, 10, 64)
	idActual := accGrantId(idRaw)

	app, _ := grantsRead(actor, grantAuthReadWrite, idActual, nil)
	if app == nil {
		return accapi.GrantApplication{}, util.HttpErr(http.StatusNotFound, "not found")
	} else {
		app.Mu.RLock()
		result := app.lDeepCopy()
		app.Mu.RUnlock()
		return GrantApplicationProcess(result), nil
	}
}

// Grant giver settings and retrieval
// =====================================================================================================================
// Public functions for retrieving grant givers and working with their associated settings. This includes retrieving
// and updating a grant giver's logo.

func GrantsRetrieveGrantGivers(actor rpc.Actor, req accapi.RetrieveGrantGiversRequest) ([]accapi.GrantGiver, *util.HttpError) {
	now := time.Now()

	recipient := accapi.Recipient{}
	parents := map[string]util.Empty{}

	applicantActor := actor

	if req.Type == accapi.RetrieveGrantGiversTypeExistingProject && req.Id == "" {
		req.Type = accapi.RetrieveGrantGiversTypePersonalWorkspace
	}

	switch req.Type {
	case accapi.RetrieveGrantGiversTypeNewProject:
		recipient.Title = util.OptValue(req.Title)
		recipient.Type = accapi.RecipientTypeNewProject

	case accapi.RetrieveGrantGiversTypeExistingProject:
		recipient.Id = util.OptValue(req.Id)
		recipient.Type = accapi.RecipientTypeExistingProject

	case accapi.RetrieveGrantGiversTypePersonalWorkspace:
		recipient.Username.Set(actor.Username)
		recipient.Type = accapi.RecipientTypePersonalWorkspace

	case accapi.RetrieveGrantGiversTypeExistingApplication:
		appId, _ := strconv.ParseInt(req.Id, 10, 64)
		b := grantGetAppBucket(accGrantId(appId))
		app, _ := grantsReadEx(actor, grantAuthReadWrite, b, accGrantId(appId), nil)
		if app == nil {
			return nil, util.HttpErr(http.StatusNotFound, "unknown application")
		}

		app.Mu.RLock()
		recipient = app.Application.CurrentRevision.Document.Recipient
		createdBy := app.Application.CreatedBy
		app.Mu.RUnlock()

		createdByActor, ok := rpc.LookupActor(createdBy)
		if !ok {
			return nil, util.HttpErr(http.StatusInternalServerError, "corrupt application, unknown applicant")
		}

		applicantActor = createdByActor
	}

	if recipient.Type == accapi.RecipientTypeExistingProject {
		// NOTE(Dan): Must be outside of switch statement since existing apps can set it to existing project also
		// NOTE(Dan): Should not check membership here since it might be done by grant giver

		wallets := internalRetrieveWallets(now, recipient.Id.Value, walletFilter{RequireActive: false}) // all wallets not just active
		for _, wallet := range wallets {
			for _, group := range wallet.AllocationGroups {
				if group.Parent.Present {
					parents[group.Parent.Value.ProjectId] = util.Empty{}
				}
			}
		}
	}

	// The request is expected to succeed at this point
	// -----------------------------------------------------------------------------------------------------------------

	var result []accapi.GrantGiver

	lAddPotentialGrantGiver := func(b *grantSettingsBucket, grantGiver string) {
		if grantsCanApply(applicantActor, recipient, grantGiver) {
			wallets := internalRetrieveWallets(now, grantGiver, walletFilter{RequireActive: true})
			var categories []accapi.ProductCategory
			for _, wallet := range wallets {
				categories = append(categories, wallet.PaysFor)
			}

			gg := accapi.GrantGiver{
				Id:         grantGiver,
				Categories: util.NonNilSlice(categories),
			}

			if sWrapper, ok := b.Settings[grantGiver]; ok {
				sWrapper.Mu.RLock()
				gg.Description = sWrapper.Settings.Description
				gg.Templates = sWrapper.Settings.Templates
				sWrapper.Mu.RUnlock()
			} else {
				gg.Description = ""
				gg.Templates = accapi.Templates{
					Type:            accapi.TemplatesTypePlainText,
					PersonalProject: defaultTemplate,
					NewProject:      defaultTemplate,
					ExistingProject: defaultTemplate,
				}
			}
			result = append(result, gg)
		}
	}

	for _, b := range grantGlobals.SettingBuckets {
		b.Mu.RLock()
		for grantGiver, _ := range b.PublicGrantGivers {
			if _, ok := parents[grantGiver]; !ok {
				lAddPotentialGrantGiver(b, grantGiver)
			}
		}
		b.Mu.RUnlock()
	}

	for parent, _ := range parents {
		if parent != "" {
			b := grantGetSettingsBucket(parent)
			b.Mu.RLock()
			lAddPotentialGrantGiver(b, parent)
			b.Mu.RUnlock()
		}
	}

	slices.SortFunc(result, func(a, b accapi.GrantGiver) int {
		return strings.Compare(a.Id, b.Id)
	})

	return util.NonNilSlice(result), nil
}

func GrantsUpdateSettings(actor rpc.Actor, id string, s accapi.GrantRequestSettings) *util.HttpError {
	if !actor.Project.Present || string(actor.Project.Value) != id ||
		!actor.Membership[rpc.ProjectId(id)].Satisfies(rpc.ProjectRoleAdmin) {
		return util.HttpErr(http.StatusForbidden, "forbidden")
	}
	b := grantGetSettingsBucket(id)

	for _, r := range s.AllowRequestsFrom {
		if err := grantUserCriteriaValid(r); err != nil {
			return err
		}
	}

	for _, r := range s.ExcludeRequestsFrom {
		if err := grantUserCriteriaValid(r); err != nil {
			return err
		}
	}

	b.Mu.Lock()
	w, ok := b.Settings[id]
	if !ok {
		w = &grantSettings{
			Mu:        sync.RWMutex{},
			ProjectId: string(actor.Project.Value),
		}
		b.Settings[id] = w
	}
	b.Mu.Unlock()

	if s.Enabled {
		b.PublicGrantGivers[string(actor.Project.Value)] = util.Empty{}
	} else {
		delete(b.Settings, string(actor.Project.Value))
	}

	w.Mu.Lock()
	w.Settings = &s
	lGrantsPersistSettings(w)
	w.Mu.Unlock()

	return nil
}

func GrantsRetrieveSettings(actor rpc.Actor) (accapi.GrantRequestSettings, *util.HttpError) {
	if !actor.Project.Present || !actor.Membership[rpc.ProjectId(actor.Project.Value)].Satisfies(rpc.ProjectRoleAdmin) {
		return accapi.GrantRequestSettings{}, util.HttpErr(http.StatusForbidden, "forbidden")
	}

	b := grantGetSettingsBucket(string(actor.Project.Value))

	b.Mu.RLock()
	w, ok := b.Settings[string(actor.Project.Value)]
	if !ok {
		b.Mu.RUnlock()
		{
			b.Mu.Lock()
			w = &grantSettings{
				Mu:        sync.RWMutex{},
				ProjectId: string(actor.Project.Value),
				Settings: &accapi.GrantRequestSettings{
					Enabled:             false,
					Description:         "No description provided",
					AllowRequestsFrom:   []accapi.UserCriteria{},
					ExcludeRequestsFrom: []accapi.UserCriteria{},
					Templates: accapi.Templates{
						Type:            accapi.TemplatesTypePlainText,
						PersonalProject: defaultTemplate,
						NewProject:      defaultTemplate,
						ExistingProject: defaultTemplate,
					},
				},
			}
			b.Settings[string(actor.Project.Value)] = w
			b.Mu.Unlock()
		}
		b.Mu.RLock()
	}
	result := w.lDeepCopy()
	b.Mu.RUnlock()
	return result, nil
}

func GrantsUploadLogo(actor rpc.Actor, logo []byte) *util.HttpError {
	if !actor.Project.Present || !actor.Membership[rpc.ProjectId(actor.Project.Value)].Satisfies(rpc.ProjectRoleAdmin) {
		return util.HttpErr(http.StatusForbidden, "forbidden")
	}

	rescaled, err := rescaleLogo(logo, 512, 512)
	if err != nil {
		return util.HttpErr(http.StatusBadRequest, "invalid or too large logo provided")
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into "grant".logos(project_id, data) 
				values (:id, :data)
				on conflict (project_id) do update set
					data = excluded.data
		    `,
			db.Params{
				"id":   actor.Project.Value,
				"data": rescaled,
			},
		)
	})

	return nil
}

func GrantsRetrieveLogo(id string) ([]byte, *util.HttpError) {
	logo, ok := db.NewTx2(func(tx *db.Transaction) ([]byte, bool) {
		row, ok := db.Get[struct {
			Data []byte
		}](
			tx,
			`select data from "grant".logos where project_id = :project`,
			db.Params{
				"project": id,
			},
		)

		return row.Data, ok
	})

	if ok {
		return logo, nil
	} else {
		return nil, util.HttpErr(http.StatusNotFound, "not found")
	}
}

// Resource awarding
// =====================================================================================================================
// This section takes care of awarding resources. This includes a function which handles (grantsAwardLoop) which handles
// approved functions that could not be awarded immediately due to a fault in some external system (e.g. project
// creation). The system is resilient towards partial creations.

func lGrantsAwardResources(app *grantApplication) {
	// Steps:
	// 1. Mark as success but awarded=false (call persist before this function)
	// 2. Lookup project by grant ID, create if needed
	// 3. Award through accounting system
	// 4. When accounting system persists, set awarded to true
	//
	// On startup, all approved applications with awarded=false are awarded again. This will never create duplicate
	// projects due to the grant ID being added. Accounting information is never partial. Project may temporarily dangle
	// without any awarded resources.

	if app.Awarded {
		return
	}

	owner := accapi.WalletOwner{}

	recipient := app.Application.CurrentRevision.Document.Recipient
	switch recipient.Type {
	case accapi.RecipientTypeExistingProject:
		owner = accapi.WalletOwnerProject(recipient.Id.Value)

	case accapi.RecipientTypeNewProject:
		projectId, err := lGrantsCreateProject(app, recipient.Title.Value, app.Application.CreatedBy)
		if err != nil {
			log.Warn("Failed at project creation: %s", err)
			return
		} else {
			owner = accapi.WalletOwnerProject(projectId)
		}

	case accapi.RecipientTypePersonalWorkspace:
		owner = accapi.WalletOwnerProject(recipient.Username.Value)

	default:
		panic(fmt.Sprintf("unhandled recipient type: %v", recipient.Type))
	}

	now := time.Now()
	period := app.Application.CurrentRevision.Document.AllocationPeriod
	start := period.Value.Start.Value.Time()
	end := period.Value.End.Value.Time()
	if !period.Value.Start.Present {
		start = now
	}

	if !period.Value.End.Present {
		end = start.AddDate(1, 0, 0)
	}

	accOwner := internalOwnerByReference(owner.Reference()).Id
	grantedIn := app.lId()

	requests := app.Application.CurrentRevision.Document.AllocationRequests
	for _, req := range requests {
		quota := req.BalanceRequested.Value
		if !req.BalanceRequested.Present {
			quota = 0
		}
		if quota <= 0 {
			continue
		}

		cat, err := ProductCategoryRetrieve(rpc.ActorSystem, req.Category, req.Provider)
		if err != nil {
			panic(fmt.Sprintf("could not allocate resources in %s: %s", app.Application.Id, err.Error()))
		}

		accBucket := internalBucketOrInit(cat)
		wallet := internalWalletByOwner(accBucket, now, accOwner)

		parentOwner := internalOwnerByReference(req.GrantGiver).Id
		parentWallet := internalWalletByOwner(accBucket, now, parentOwner)

		_, err = internalAllocateNoCommit(now, accBucket, start, end, quota, wallet, parentWallet, util.OptValue(grantedIn))
		if err != nil {
			// This only happens in case of bad input. It should never happen. Not doing a panic here to avoid
			// potential infinite allocations (from the retry loop)
			log.Warn("could not allocate resources in %s: %s", app.Application.Id, err.Error())
		}
	}

	app.Awarded = true
	internalCommitGrantAllocations(grantedIn, func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update "grant".applications
				set
					synchronized = true
				where
					id = :id
		    `,
			db.Params{
				"id": app.lId(),
			},
		)

		if app.GiftId.Present {
			db.Exec(
				tx,
				`
					insert into "grant".gifts_claimed(gift_id, user_id, claimed_at) 
					values (:gift_id, :username, now())
					on conflict (gift_id, user_id)
					do update set claimed_at = excluded.claimed_at;
			    `,
				db.Params{
					"username": app.Application.CurrentRevision.Document.Recipient.Username.Value,
					"gift_id":  app.GiftId.Value,
				},
			)
		}
	})
}

func lGrantsCreateProject(app *grantApplication, title string, pi string) (string, *util.HttpError) {
	if grantGlobals.Testing.Enabled {
		if grantGlobals.Testing.ProjectCreateFailure {
			return "", util.HttpErr(http.StatusBadGateway, "bad gateway")
		} else {
			return fmt.Sprintf("%s-%s", app.Application.Id, title), nil
		}
	} else {
		breakdown := app.Application.Status.StateBreakdown
		parent := util.OptNone[string]()
		if len(breakdown) > 0 {
			parent.Set(breakdown[0].ProjectId)
		}
		result, err := fndapi.ProjectInternalCreate.Invoke(fndapi.ProjectInternalCreateRequest{
			Title:        title,
			BackendId:    fmt.Sprintf("grants/%s", app.Application.Id.Value),
			PiUsername:   pi,
			SubAllocator: app.Application.CurrentRevision.Document.Form.SubAllocator,
			Parent:       parent,
		})

		if err != nil {
			return "", err
		} else {
			return result.Id, nil
		}
	}
}

func grantsAwardLoop() {
	if grantGlobals.Testing.Enabled {
		return
	}

	time.Sleep(10 * time.Second) // wait a bit to make sure that everything is ready

	for {
		for _, b := range grantGlobals.AppBuckets {
			b.Mu.RLock()
			for _, g := range b.Applications {
				g.Mu.Lock()
				if !g.Awarded && g.Application.Status.OverallState == accapi.GrantApplicationStateApproved {
					lGrantsAwardResources(g)
				}
				g.Mu.Unlock()
			}
			b.Mu.RUnlock()
		}

		time.Sleep(30 * time.Second)
	}
}

// Initialization and RPC
// =====================================================================================================================
// This section contains the initialization logic and RPC handlers. Note that this function will is testing aware and
// turns off parts of the code which cannot run during tests.

func initGrants() {
	grantGlobals.AppBuckets = nil
	grantGlobals.SettingBuckets = nil
	grantGlobals.IndexBuckets = nil

	for i := 0; i < runtime.NumCPU(); i++ {
		grantGlobals.AppBuckets = append(grantGlobals.AppBuckets, &grantAppBucket{
			Applications: make(map[accGrantId]*grantApplication),
		})

		grantGlobals.SettingBuckets = append(grantGlobals.SettingBuckets, &grantSettingsBucket{
			PublicGrantGivers: make(map[string]util.Empty),
			Settings:          make(map[string]*grantSettings),
		})

		grantGlobals.IndexBuckets = append(grantGlobals.IndexBuckets, &grantIndexBucket{
			ApplicationsByEntity: make(map[string][]accGrantId),
		})
	}

	grantsLoadUnawarded()
	grantsLoadSettings()
	go grantsAwardLoop()

	if !grantGlobals.Testing.Enabled {
		accapi.GrantsBrowse.Handler(func(info rpc.RequestInfo, request accapi.GrantsBrowseRequest) (fndapi.PageV2[accapi.GrantApplication], *util.HttpError) {
			return GrantsBrowse(info.Actor, request), nil
		})

		accapi.GrantsRetrieve.Handler(func(info rpc.RequestInfo, request fndapi.FindByStringId) (accapi.GrantApplication, *util.HttpError) {
			return GrantsRetrieve(info.Actor, request.Id)
		})

		accapi.GrantsSubmitRevision.Handler(func(info rpc.RequestInfo, request accapi.GrantsSubmitRevisionRequest) (fndapi.FindByStringId, *util.HttpError) {
			id, err := GrantsSubmitRevision(info.Actor, request)
			if err != nil {
				return fndapi.FindByStringId{}, err
			} else {
				return fndapi.FindByStringId{Id: fmt.Sprint(id)}, nil
			}
		})

		accapi.GrantsUpdateState.Handler(func(info rpc.RequestInfo, request accapi.GrantsUpdateStateRequest) (util.Empty, *util.HttpError) {
			return util.Empty{}, GrantsUpdateState(info.Actor, request)
		})

		accapi.GrantsTransfer.Handler(func(info rpc.RequestInfo, request accapi.GrantsTransferRequest) (util.Empty, *util.HttpError) {
			return util.Empty{}, GrantsTransfer(info.Actor, request)
		})

		accapi.RetrieveGrantGivers.Handler(func(info rpc.RequestInfo, request accapi.RetrieveGrantGiversRequest) (accapi.RetrieveGrantGiversResponse, *util.HttpError) {
			grantGivers, err := GrantsRetrieveGrantGivers(info.Actor, request)
			return accapi.RetrieveGrantGiversResponse{GrantGivers: grantGivers}, err
		})

		accapi.GrantsPostComment.Handler(func(info rpc.RequestInfo, request accapi.GrantsPostCommentRequest) (fndapi.FindByStringId, *util.HttpError) {
			id, err := GrantsPostComment(info.Actor, request)
			if err != nil {
				return fndapi.FindByStringId{}, err
			} else {
				return fndapi.FindByStringId{Id: fmt.Sprint(id)}, nil
			}
		})

		accapi.GrantsDeleteComment.Handler(func(info rpc.RequestInfo, request accapi.GrantsDeleteCommentRequest) (util.Empty, *util.HttpError) {
			return util.Empty{}, GrantsDeleteComment(info.Actor, request)
		})

		accapi.GrantsRetrieveRequestSettings.Handler(func(info rpc.RequestInfo, request util.Empty) (accapi.GrantRequestSettings, *util.HttpError) {
			return GrantsRetrieveSettings(info.Actor)
		})

		accapi.GrantsUpdateRequestSettings.Handler(func(info rpc.RequestInfo, request accapi.GrantRequestSettings) (util.Empty, *util.HttpError) {
			if !info.Actor.Project.Present {
				return util.Empty{}, util.HttpErr(http.StatusBadRequest, "bad request - no project")
			}
			return util.Empty{}, GrantsUpdateSettings(info.Actor, string(info.Actor.Project.Value), request)
		})

		accapi.GrantsUploadLogo.Handler(func(info rpc.RequestInfo, request []byte) (util.Empty, *util.HttpError) {
			return util.Empty{}, GrantsUploadLogo(info.Actor, request)
		})

		accapi.GrantsRetrieveLogo.Handler(func(info rpc.RequestInfo, request accapi.GrantsRetrieveLogoRequest) ([]byte, *util.HttpError) {
			return GrantsRetrieveLogo(request.ProjectId)
		})
	}
}

// Odds and ends
// =====================================================================================================================
// Various pieces of constants and other functionality that does not belong anywhere else.

const defaultTemplate = "Please describe the reason for applying for resources"

// Notifications
// =====================================================================================================================
// Sends notifications when grant events occur.
type grantEventType int

const (
	grantEvNewComment grantEventType = iota
	grantEvGrantAwarded
	grantEvApplicationRejected
	grantEvApplicationSubmitted
	grantEvRevisionSubmitted
)

type grantEvent struct {
	Type                   grantEventType
	EventSourceIsApplicant bool
	Actor                  rpc.Actor
	Application            accapi.GrantApplication
}

func grantHandleEvent(event grantEvent) {
	err1 := grantSendNotification(event)
	err2 := grantSendEmail(event)
	if err1 != nil {
		log.Warn("Failed to send notification: %s", err1)
	}
	if err2 != nil {
		log.Warn("Failed to send email: %s", err2)
	}
}

func grantGetRecipientTitle(event grantEvent) string {
	app := &event.Application
	recipient := app.CurrentRevision.Document.Recipient

	switch recipient.Type {
	case accapi.RecipientTypeExistingProject:
		projectId := recipient.Id.Value
		return db.NewTx(func(tx *db.Transaction) string {
			project, ok := coreutil.ProjectRetrieveFromDatabase(tx, projectId)
			if ok {
				return project.Specification.Title
			}
			return ""
		})
	case accapi.RecipientTypeNewProject:
		return recipient.Title.Value
	case accapi.RecipientTypePersonalWorkspace:
		return fmt.Sprintf("Personal workspace of %s", recipient.Username.Value)
	default:
		return ""
	}
}

func truncateRecipientTitle(event grantEvent) string {
	title := grantGetRecipientTitle(event)
	if len(title) >= 30 {
		return title[:30] + "..."
	}
	return title
}

func grantSendNotification(event grantEvent) *util.HttpError {
	if grantGlobals.Testing.Enabled {
		return nil
	}

	var recipients []string

	if event.EventSourceIsApplicant {
		app := &event.Application
		reqs := app.CurrentRevision.Document.AllocationRequests
		reviewerSet := map[string]util.Empty{}
		for _, req := range reqs {
			reviewerSet[req.GrantGiver] = util.Empty{}
		}

		reviewerUsers := map[string]util.Empty{}
		db.NewTx0(func(tx *db.Transaction) {
			for reviewer := range reviewerSet {
				project, ok := coreutil.ProjectRetrieveFromDatabase(tx, reviewer)
				if ok {
					for _, member := range project.Status.Members {
						if member.Role.Satisfies(fndapi.ProjectRoleAdmin) {
							reviewerUsers[member.Username] = util.Empty{}
						}
					}
				}
			}
		})

		for user := range reviewerUsers {
			recipients = append(recipients, user)
		}
	} else {
		applicant := event.Application.CreatedBy
		recipients = append(recipients, applicant)
	}

	for _, recipient := range recipients {
		notification := fndapi.Notification{}

		meta := map[string]any{
			"appId": event.Application.Id,
		}

		switch event.Type {
		case grantEvNewComment:
			notification.Type = "NEW_GRANT_COMMENT"
			notification.Message = fmt.Sprintf("Comment added in \"%s\"", truncateRecipientTitle(event))
			meta["title"] = fmt.Sprintf("New comment by %s", event.Actor.Username)
			meta["avatar"] = event.Actor.Username
		case grantEvApplicationSubmitted:
			notification.Type = "NEW_GRANT_APPLICATION"
			notification.Message = fmt.Sprintf("\"%s\" was submitted by %s", truncateRecipientTitle(event), event.Actor.Username)
			meta["title"] = fmt.Sprintf("A new application was submitted")
		case grantEvGrantAwarded:
			notification.Type = "GRANT_APPLICATION_RESPONSE"
			notification.Message = fmt.Sprintf("\"%s\", has been approved by %s", truncateRecipientTitle(event), event.Actor.Username)
			meta["title"] = "Grant awarded"
		case grantEvApplicationRejected:
			notification.Type = "GRANT_APPLICATION_RESPONSE"
			notification.Message = fmt.Sprintf("\"%s\", has been rejected by %s", truncateRecipientTitle(event), event.Actor.Username)
			meta["title"] = "Grant rejected"
		case grantEvRevisionSubmitted:
			notification.Type = "GRANT_APPLICATION_UPDATED"
			notification.Message = fmt.Sprintf("Grant revision submitted by %s", event.Actor.Username)
			meta["title"] = fmt.Sprintf("Application updated: \"%s\"", truncateRecipientTitle(event))
			meta["avatar"] = event.Actor.Username
			// TODO make grantEv for grantTransfer and insert here
		}

		metaJson, _ := json.Marshal(meta)
		notification.Meta.Set(metaJson)

		_, err := fndapi.NotificationsCreate.Invoke(fndapi.NotificationsCreateRequest{
			User:         recipient,
			Notification: notification,
		})
		if err != nil {
			return err
		}
	}

	return nil
}

func grantSendEmail(event grantEvent) *util.HttpError {
	if grantGlobals.Testing.Enabled {
		return nil
	}

	var recipients []string

	if event.EventSourceIsApplicant {
		app := &event.Application
		reqs := app.CurrentRevision.Document.AllocationRequests
		reviewerSet := map[string]util.Empty{}
		for _, req := range reqs {
			reviewerSet[req.GrantGiver] = util.Empty{}
		}

		reviewerUsers := map[string]util.Empty{}
		db.NewTx0(func(tx *db.Transaction) {
			for reviewer := range reviewerSet {
				project, ok := coreutil.ProjectRetrieveFromDatabase(tx, reviewer)
				if ok {
					for _, member := range project.Status.Members {
						if member.Role.Satisfies(fndapi.ProjectRoleAdmin) {
							reviewerUsers[member.Username] = util.Empty{}
						}
					}
				}
			}
		})

		for user := range reviewerUsers {
			recipients = append(recipients, user)
		}
	} else {
		applicant := event.Application.CreatedBy
		recipients = append(recipients, applicant)
	}

	applicantProjectTitle := ""
	currDoc := event.Application.CurrentRevision.Document
	switch currDoc.Recipient.Type {
	case accapi.RecipientTypeNewProject:
		applicantProjectTitle = currDoc.Recipient.Title.Value
	case accapi.RecipientTypePersonalWorkspace:
		applicantProjectTitle = fmt.Sprintf("personal workspace of: %v", event.Application.CreatedBy)
	case accapi.RecipientTypeExistingProject:
		projectId := currDoc.Recipient.Id.Value
		applicantProjectTitle = db.NewTx(func(tx *db.Transaction) string {
			project, ok := coreutil.ProjectRetrieveFromDatabase(tx, projectId)
			if !ok {
				return projectId
			} else {
				return project.Id
			}
		})
	}

	mailTemplate := map[string]any{
		"sender":                event.Application.CreatedBy,
		"applicantProjectTitle": applicantProjectTitle,
	}

	switch event.Type {
	case grantEvNewComment:
		mailTemplate["type"] = fndapi.MailTypeNewComment
	case grantEvApplicationSubmitted:
		mailTemplate["type"] = fndapi.MailTypeNewGrantApplication
	case grantEvGrantAwarded:
		mailTemplate["type"] = fndapi.MailTypeApplicationApproved
	case grantEvApplicationRejected:
		mailTemplate["type"] = fndapi.MailTypeApplicationRejected
	case grantEvRevisionSubmitted:
		mailTemplate["type"] = fndapi.MailTypeApplicationUpdated
		// TODO make grantEv for grantTransfer and insert here
	}

	mailBytes, _ := json.Marshal(mailTemplate)
	mail := fndapi.Mail(mailBytes)

	for _, recipient := range recipients {
		_, err := fndapi.MailSendToUser.Invoke(fndapi.BulkRequestOf(
			fndapi.MailSendToUserRequest{
				Receiver: recipient,
				Mail:     mail,
			}),
		)
		if err != nil {
			return err
		}
	}

	return nil
}
