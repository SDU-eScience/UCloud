package accounting

import (
	"encoding/json"
	"fmt"
	"net/http"
	"runtime"
	"slices"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"
	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

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
	go grantsAwardLoop()
}

// lock order is: grantAppBucket -> grantApplication -> grantSettingsBucket -> grantSettings -> grantIndexBucket
//
// It is assumed that the accounting system never requests information from the grant system. As a result, this system
// is allowed to freely use the functions of the internal accounting system.

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
	Mu                sync.RWMutex
	PublicGrantGivers map[string]util.Empty // grant givers where enabled = true
	Settings          map[string]*grantSettings
}

type grantIndexBucket struct {
	Mu sync.RWMutex

	// Guaranteed to be ordered by time of creation (for a single entity). Contains both applications sent by the entity
	// but also being received. Note that the order is technically by order in which they are created and not
	// timestamps. This can happen if the creation starts before another but locks acquisition happens in reverse order.
	ApplicationsByEntity map[string][]accGrantId
}

type grantApplication struct {
	Mu          sync.RWMutex
	Application *accapi.GrantApplication
	Awarded     bool // true once the service has performed the award, does not mean that persist has happened
}

func (a *grantApplication) lId() accGrantId {
	parsed, _ := strconv.ParseInt(a.Application.Id, 10, 64)
	return accGrantId(parsed)
}

func (a *grantApplication) lDeepCopy() accapi.GrantApplication {
	var result accapi.GrantApplication
	b, _ := json.Marshal(a.Application)
	_ = json.Unmarshal(b, &result)
	return result
}

type grantSettings struct {
	Mu        sync.RWMutex
	ProjectId string
	Settings  *accapi.GrantRequestSettings
}

func grantGetAppBucket(key accGrantId) *grantAppBucket {
	h := util.NonCryptographicHash(key)
	return grantGlobals.AppBuckets[h%len(grantGlobals.AppBuckets)]
}

func grantGetSettingsBucket(key string) *grantSettingsBucket {
	h := util.NonCryptographicHash(key)
	return grantGlobals.SettingBuckets[h%len(grantGlobals.SettingBuckets)]
}

func grantGetIdxBucket(key string) *grantIndexBucket {
	h := util.NonCryptographicHash(key)
	return grantGlobals.IndexBuckets[h%len(grantGlobals.IndexBuckets)]
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

func grantsReadEx(actor rpc.Actor, action grantAuthType, b *grantAppBucket, id accGrantId) (*grantApplication, []grantActorRole) {
	var roles []grantActorRole

	b.Mu.RLock()
	app, ok := b.Applications[id]
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
func grantsRead(actor rpc.Actor, action grantAuthType, id accGrantId) (*grantApplication, []grantActorRole) {
	return grantsReadEx(actor, action, grantGetAppBucket(id), id)
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
		if settings.Enabled && !excluded {
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
		// TODO parent project was replaced with a check on an existing recipient to see if they have had resources
		//   from the grant giver before.

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

func GrantsSubmitRevision(actor rpc.Actor, req accapi.GrantsSubmitRevisionRequest) (int64, *util.HttpError) {
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

	recipient := revision.Recipient
	if req.AlternativeRecipient.Present {
		recipient = req.AlternativeRecipient.Value
	}

	revision.Recipient = recipient

	if !recipient.Valid() {
		return 0, util.HttpErr(http.StatusBadRequest, "invalid recipient")
	}

	if recipient.Type == accapi.RecipientTypeNewProject && recipient.Title.Value == "" {
		return 0, util.HttpErr(http.StatusBadRequest, "project title cannot be empty")
	}

	if recipient.Type == accapi.RecipientTypeNewProject && strings.HasPrefix(recipient.Title.Value, "%") {
		// NOTE(Dan): Used as a hint to the frontend about special projects. Not used for anything backend related.
		return 0, util.HttpErr(http.StatusBadRequest, "project title cannot start with '%%'")
	}

	if recipient.Type != accapi.RecipientTypePersonalWorkspace {
		primaryAffiliation := revision.ParentProjectId.Value
		if !revision.ParentProjectId.Present {
			return 0, util.HttpErr(http.StatusBadRequest, "a primary affiliation must be selected")
		}

		hasAllocFromAffiliation := false
		for _, allocReq := range revision.AllocationRequests {
			if allocReq.GrantGiver == primaryAffiliation && allocReq.BalanceRequested.Present && allocReq.BalanceRequested.Value > 0 {
				hasAllocFromAffiliation = true
				break
			}
		}

		if !hasAllocFromAffiliation {
			return 0, util.HttpErr(http.StatusBadRequest, "no requests made to primary affiliation")
		}
	} else {
		if revision.ParentProjectId.Present {
			return 0, util.HttpErr(http.StatusBadRequest, "a primary affiliation must not be present")
		}
	}

	if !revision.Form.Type.Valid() {
		return 0, util.HttpErr(http.StatusBadRequest, "form type is invalid")
	}

	period := revision.AllocationPeriod

	hasRequest := false
	for _, allocReq := range revision.AllocationRequests {
		// TODO Check that there are no duplicate (grantGiver, category) pairs

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

	// TODO only the grant giver can change reference IDs

	for _, id := range revision.ReferenceIds.GetOrDefault(nil) {
		if id == "" {
			return 0, util.HttpErr(http.StatusBadRequest, "grant reference IDs cannot be empty")
		}

		if err := checkDeicReferenceFormat(id); err != nil {
			return 0, err
		}
	}

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

	b := grantGetAppBucket(id)

	var app *grantApplication
	roles := []grantActorRole{grantActorRoleSubmitter}
	if wasExistingApplication {
		app, roles = grantsReadEx(actor, grantAuthReadWrite, b, id)
		if app == nil {
			return 0, util.HttpErr(http.StatusNotFound, "unknown application")
		}

		app.Mu.Lock()
	} else {
		b.Mu.Lock()
		app = &grantApplication{
			Mu: sync.RWMutex{},
			Application: &accapi.GrantApplication{
				Id:        fmt.Sprint(id),
				CreatedBy: actor.Username,
				CreatedAt: fndapi.Timestamp(now),
				UpdatedAt: fndapi.Timestamp(now),
				Status: accapi.GrantStatus{
					OverallState:   accapi.GrantApplicationStateInProgress,
					StateBreakdown: []accapi.GrantGiverApprovalState{},
					Comments:       []accapi.GrantComment{},
					Revisions:      []accapi.GrantRevision{},
					ProjectTitle:   util.Option[string]{}, // TODO
					ProjectPI:      "",                    // TODO
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

	// TODO check that the suballocator flag is allowed to be set for grant giver initiated

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

	if err == nil && revision.Form.Type == accapi.FormTypeGrantGiverInitiated {
		if len(grantGivers) != 1 {
			err = util.HttpErr(http.StatusForbidden, "invalid allocation request")
		} else {
			for grantGiver, _ := range grantGivers {
				grantGiverInitiatedId = grantGiver
				break
			}

			if !actor.Membership[rpc.ProjectId(grantGiverInitiatedId)].Satisfies(rpc.ProjectRoleAdmin) {
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
					quota, ok := internalWalletTotalQuotaActive(aBucket, w)
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

	overallState := app.Application.Status.OverallState
	if overallState == accapi.GrantApplicationStateApproved || overallState == accapi.GrantApplicationStateRejected {
		// NOTE(Dan): If the application is already closed, then only a small fraction of properties will be used in
		// the update. The values that are mutable are copied and the "revision" variable is replaced by the current
		// revision.

		newIds := revision.ReferenceIds
		*revision = app.Application.CurrentRevision.Document
		revision.ReferenceIds = newIds
	} else if revision.Form.Type == accapi.FormTypeGrantGiverInitiated {
		app.Application.Status.OverallState = accapi.GrantApplicationStateApproved
		app.Application.Status.StateBreakdown = []accapi.GrantGiverApprovalState{
			{
				ProjectId:    grantGiverInitiatedId,
				ProjectTitle: "", // TODO?
				State:        accapi.GrantApplicationStateApproved,
			},
		}

		didApprove = true
	} else {
		// NOTE(Dan): Any change in the application will automatically reset the state to in-progress
		app.Application.Status.OverallState = accapi.GrantApplicationStateInProgress

		breakdown := []accapi.GrantGiverApprovalState{}
		for grantGiver, _ := range grantGivers {
			breakdown = append(breakdown, accapi.GrantGiverApprovalState{
				ProjectId:    grantGiver,
				ProjectTitle: "", // TODO?
				State:        accapi.GrantApplicationStateInProgress,
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

			idxB := grantGetIdxBucket(indexKey)
			idxB.Mu.Lock()
			idxB.ApplicationsByEntity[indexKey] = append(idxB.ApplicationsByEntity[indexKey], id)
			idxB.Mu.Unlock()
		}

		{
			// Receiver indexing
			for grantGiver, _ := range grantGivers {
				idxB := grantGetIdxBucket(grantGiver)
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

	lGrantsPersist(app)

	if didApprove {
		lGrantsAwardResources(app)
	}

	app.Mu.Unlock()

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
	app, _ := grantsReadEx(actor, grantAuthApprover, b, accGrantId(appId))
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
			if allocReq.GrantGiver != source {
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

	return err
}

func GrantsPostComment(actor rpc.Actor, req accapi.GrantsPostCommentRequest) (string, *util.HttpError) {
	if len(req.Comment) > 1024*1024 {
		return "", util.HttpErr(http.StatusForbidden, "your comment is too long")
	}

	appId, _ := strconv.ParseInt(req.ApplicationId, 10, 64)
	b := grantGetAppBucket(accGrantId(appId))
	app, _ := grantsReadEx(actor, grantAuthReadWrite, b, accGrantId(appId))
	if app == nil {
		return "", util.HttpErr(http.StatusNotFound, "unknown application")
	}

	app.Mu.Lock()
	commentId := grantGlobals.CommentIdAcc.Add(1)
	app.Application.Status.Comments = append(app.Application.Status.Comments, accapi.GrantComment{
		Id:        fmt.Sprint(commentId),
		Username:  actor.Username,
		CreatedAt: fndapi.Timestamp(time.Now()),
		Comment:   req.Comment,
	})

	lGrantsPersist(app)
	app.Mu.Unlock()

	// TODO notifications
	return fmt.Sprint(commentId), nil
}

func GrantsDeleteComment(actor rpc.Actor, req accapi.GrantsDeleteCommentRequest) *util.HttpError {
	appId, _ := strconv.ParseInt(req.ApplicationId, 10, 64)
	b := grantGetAppBucket(accGrantId(appId))
	app, roles := grantsReadEx(actor, grantAuthReadWrite, b, accGrantId(appId))
	if app == nil {
		return util.HttpErr(http.StatusNotFound, "unknown application")
	}

	app.Mu.Lock()
	found := false
	for i, comment := range app.Application.Status.Comments {
		if comment.Id == req.CommentId && (slices.Contains(roles, grantActorRoleApprover) || comment.Username == actor.Username) {
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
			log.Info("Failed at project creation: %v", err)
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

		_, err = internalAllocate(now, accBucket, start, end, quota, wallet, parentWallet, util.OptValue(grantedIn))
		if err != nil {
			// This only happens in case of bad input. It should never happen. Not doing a panic here to avoid
			// potential infinite allocations (from the retry loop)
			log.Warn("could not allocate resources in %s: %s", app.Application.Id, err.Error())
		}
	}

	app.Awarded = true
	internalCommitAllocations(grantedIn, nil) // TODO
}

func lGrantsCreateProject(app *grantApplication, title string, pi string) (string, *util.HttpError) {
	if grantGlobals.Testing.Enabled {
		if grantGlobals.Testing.ProjectCreateFailure {
			return "", util.HttpErr(http.StatusBadGateway, "bad gateway")
		} else {
			return fmt.Sprintf("%s-%s", app.Application.Id, title), nil
		}
	} else {
		result, err := fndapi.ProjectInternalCreate.Invoke(fndapi.ProjectInternalCreateRequest{
			BackendId:  fmt.Sprintf("grants/%s", app.Application.Id),
			PiUsername: pi,
		})

		if err != nil {
			return "", err
		} else {
			return result.Id, nil
		}
	}
}

func lGrantsPersist(app *grantApplication) {
	if grantGlobals.Testing.Enabled {
		return
	} else {

	}
	// TODO
}

func GrantsRetrieveGrantGivers(actor rpc.Actor, req accapi.RetrieveGrantGiversRequest) ([]accapi.GrantGiver, *util.HttpError) {
	now := time.Now()
	// TODO parents should be included in list and deduplicated

	recipient := accapi.Recipient{}
	parents := map[string]util.Empty{}

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
		app, _ := grantsReadEx(actor, grantAuthReadWrite, b, accGrantId(appId))
		if app == nil {
			return nil, util.HttpErr(http.StatusNotFound, "unknown application")
		}

		app.Mu.RLock()
		recipient = app.Application.CurrentRevision.Document.Recipient
		app.Mu.RUnlock()
	}

	if recipient.Type == accapi.RecipientTypeExistingProject {
		// NOTE(Dan): Must be outside of switch statement since existing apps can set it to existing project also

		if !actor.Membership[rpc.ProjectId(req.Id)].Satisfies(rpc.ProjectRoleAdmin) {
			return nil, util.HttpErr(http.StatusNotFound, "unknown project")
		} else {
			wallets := internalRetrieveWallets(now, req.Id, walletFilter{RequireActive: false}) // all wallets not just active
			for _, wallet := range wallets {
				for _, group := range wallet.AllocationGroups {
					if group.Parent.Present {
						parents[group.Parent.Value.ProjectId] = util.Empty{}
					}
				}
			}
		}
	}

	// The request is expected to succeed at this point
	// -----------------------------------------------------------------------------------------------------------------

	var result []accapi.GrantGiver

	lAddPotentialGrantGiver := func(b *grantSettingsBucket, grantGiver string) {
		if grantsCanApply(actor, recipient, grantGiver) {
			wallets := internalRetrieveWallets(now, grantGiver, walletFilter{RequireActive: true})
			var categories []accapi.ProductCategory
			for _, wallet := range wallets {
				categories = append(categories, wallet.PaysFor)
			}

			gg := accapi.GrantGiver{
				Id:         grantGiver,
				Title:      "", // TODO
				Categories: categories,
			}

			if sWrapper, ok := b.Settings[grantGiver]; ok {
				sWrapper.Mu.RLock()
				gg.Description = sWrapper.Settings.Description
				gg.Templates = sWrapper.Settings.Templates
				sWrapper.Mu.RUnlock()
			} else {
				const defaultTemplate = "Please describe the reason for applying for resources"
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
		b := grantGetSettingsBucket(parent)
		b.Mu.RLock()
		lAddPotentialGrantGiver(b, parent)
		b.Mu.RUnlock()
	}

	slices.SortFunc(result, func(a, b accapi.GrantGiver) int {
		return strings.Compare(a.Id, b.Id)
	})

	return result, nil
}

func GrantsBrowse(actor rpc.Actor, req accapi.GrantsBrowseRequest) fndapi.PageV2[accapi.GrantApplication] {
	var ids []accGrantId

	nextIdRaw, _ := strconv.ParseInt(req.Next.Value, 10, 64)
	nextId := accGrantId(nextIdRaw)

	idxB := grantGetIdxBucket(actor.Username)
	idxB.Mu.RLock()
	for _, id := range idxB.ApplicationsByEntity[actor.Username] {
		if id > nextId {
			ids = append(ids, id)
		}
	}
	idxB.Mu.RUnlock()

	if actor.Project.Present && actor.Membership[rpc.ProjectId(actor.Project.Value)].Satisfies(rpc.ProjectRoleAdmin) {
		idxB = grantGetIdxBucket(actor.Project.Value)
		idxB.Mu.RLock()
		for _, id := range idxB.ApplicationsByEntity[actor.Project.Value] {
			if id > nextId {
				ids = append(ids, id)
			}
		}
		idxB.Mu.RUnlock()
	}

	slices.SortFunc(ids, func(a, b accGrantId) int {
		if a < b {
			return -1
		} else if a > b {
			return 1
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

		a, roles := grantsRead(actor, grantAuthReadWrite, id)
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
				isSubmitter := slices.Contains(roles, grantActorRoleSubmitter)
				isApprover := slices.Contains(roles, grantActorRoleApprover)

				relevant = false

				if allowIngoing && isSubmitter {
					relevant = true
				} else if allowOutgoing && isApprover {
					relevant = true
				}
			}

			if relevant {
				if len(items) >= itemsPerPage {
					hasMore = true
				} else {
					items = append(items, a.lDeepCopy())
				}
			}

			a.Mu.RUnlock()
		}
	}

	result := fndapi.PageV2[accapi.GrantApplication]{
		Items:        items,
		ItemsPerPage: itemsPerPage,
	}

	if hasMore {
		result.Next.Set(items[len(items)-1].Id)
	}

	return result
}

func GrantsRetrieve(actor rpc.Actor, id string) (accapi.GrantApplication, *util.HttpError) {
	idRaw, _ := strconv.ParseInt(id, 10, 64)
	idActual := accGrantId(idRaw)

	app, _ := grantsRead(actor, grantAuthReadWrite, idActual)
	if app == nil {
		return accapi.GrantApplication{}, util.HttpErr(http.StatusNotFound, "not found")
	} else {
		app.Mu.RLock()
		result := app.lDeepCopy()
		app.Mu.RUnlock()
		return result, nil
	}
}

func GrantsUpdateState(actor rpc.Actor, req accapi.GrantsUpdateStateRequest) *util.HttpError {
	var err *util.HttpError
	idRaw, _ := strconv.ParseInt(req.ApplicationId, 10, 64)
	idActual := accGrantId(idRaw)
	app, roles := grantsRead(actor, grantAuthReadWrite, idActual)
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

				if breakdown.ProjectId == actor.Project.Value {
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
	app.Mu.Unlock()
	return err
}

func GrantsUpdateSettings(actor rpc.Actor, id string, s accapi.GrantRequestSettings) *util.HttpError {
	if !actor.Project.Present || actor.Project.Value != id ||
		!actor.Membership[rpc.ProjectId(id)].Satisfies(rpc.ProjectRoleAdmin) {
		return util.HttpErr(http.StatusForbidden, "forbidden")
	}
	b := grantGetSettingsBucket(id)

	if actor.Role != rpc.RoleAdmin {
		s.Enabled = false
	}

	b.Mu.Lock()
	w, ok := b.Settings[id]
	if !ok {
		w = &grantSettings{
			Mu:        sync.RWMutex{},
			ProjectId: actor.Project.Value,
		}
	}

	w.Settings = &s

	if s.Enabled {
		b.PublicGrantGivers[id] = util.Empty{}
	} else {
		delete(b.PublicGrantGivers, id)
	}

	b.Mu.Unlock()
	return nil
}

func grantsAwardLoop() {
	for {
		if grantGlobals.Testing.Enabled {
			break
		}

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

// TODO notifications
