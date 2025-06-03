package accounting

import (
	"fmt"
	"hash/fnv"
	"net/http"
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

}

// lock order is: grantAppBucket -> grantApplication -> grantSettingsBucket -> grantSettings
//
// It is assumed that the accounting system never requests information from the grant system. As a result, this system
// is allowed to freely use the functions of the internal accounting system.

var grantGlobals struct {
	AppBuckets     []*grantAppBucket
	SettingBuckets []*grantSettingsBucket
	CommentIdAcc   atomic.Int64
	GrantIdAcc     atomic.Int64
}

type grantAppBucket struct {
	Mu           sync.RWMutex
	Applications map[int64]*grantApplication
}

type grantSettingsBucket struct {
	Mu                sync.RWMutex
	PublicGrantGivers map[string]util.Empty // grant givers where enabled = true
	Settings          map[string]*grantSettings
}

type grantApplication struct {
	Mu          sync.RWMutex
	Application *accapi.GrantApplication
}

type grantSettings struct {
	Mu        sync.RWMutex
	ProjectId string
	Settings  *accapi.GrantRequestSettings
}

func grantAB(key any) *grantAppBucket {
	h := fnv.New32a()
	_, err := h.Write([]byte(fmt.Sprint(key)))
	if err != nil {
		panic("hash fail: " + err.Error())
	}
	return grantGlobals.AppBuckets[int(h.Sum32())%len(grantGlobals.AppBuckets)]
}

func grantSB(key any) *grantSettingsBucket {
	h := fnv.New32a()
	_, err := h.Write([]byte(fmt.Sprint(key)))
	if err != nil {
		panic("hash fail: " + err.Error())
	}
	return grantGlobals.SettingBuckets[int(h.Sum32())%len(grantGlobals.SettingBuckets)]
}

type grantAuthType int

const (
	grantAuthRead      grantAuthType = iota // generic read action
	grantAuthSubmit                         // generic write action (e.g. comment or revision)
	grantAuthApprover                       // approver only action
	grantAuthRequester                      // requester only action
)

type grantActorRole int

const (
	grantActorRoleSubmitter grantActorRole = iota
	grantActorRoleApprover
)

func grantsRead(actor rpc.Actor, action grantAuthType, b *grantAppBucket, id int64) (*grantApplication, grantActorRole) {
	return nil, grantActorRoleSubmitter
}

func grantsRetrieveSettings(grantGiver string) (*grantSettings, bool) {
	b := grantSB(grantGiver)
	b.Mu.RLock()
	settings, ok := b.Settings[grantGiver]
	b.Mu.RUnlock()
	return settings, ok
}

func grantsCanApply(actor rpc.Actor, recipient accapi.Recipient, grantGiver string) bool {
	sWrapper, ok := grantsRetrieveSettings(grantGiver)
	if !ok {
		return false
	}

	sWrapper.Mu.RLock()

	walletOwner := ""
	allowed := false

	switch recipient.Type {
	case accapi.RecipientTypePersonalWorkspace:
		walletOwner = actor.Username

	case accapi.RecipientTypeExistingProject:
		walletOwner = recipient.Id.Value

	case accapi.RecipientTypeNewProject:
		// No existing owner
	}

	settings := sWrapper.Settings
	if settings.Enabled {
		allowFrom := settings.AllowRequestsFrom
		excludeFrom := settings.ExcludeRequestsFrom

		excluded := false
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

	sWrapper.Mu.RUnlock()

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
		return 0, util.HttpErr(http.StatusBadRequest, "project title cannot start with '%'")
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

	if revision.Form.Type == accapi.FormTypeGrantGiverInitiated && !revision.Form.SubAllocator.Present {
		return 0, util.HttpErr(http.StatusBadRequest, "sub-allocator field must be present")
	}

	// Read and locate the grantApplication
	// -----------------------------------------------------------------------------------------------------------------
	// If the grant application is an existing one, then it will be loaded from the cache or database. This step will
	// in that case authorize that the actor is allowed to perform a submission on the application.

	id := int64(0)
	wasExistingApplication := req.ApplicationId.Present
	if wasExistingApplication {
		parsed, err := strconv.ParseInt(req.ApplicationId.Value, 10, 64)
		if err != nil {
			return 0, util.HttpErr(http.StatusNotFound, "unknown application")
		}

		id = parsed
	} else {
		id = grantGlobals.GrantIdAcc.Add(1)
	}

	b := grantAB(id)

	var app *grantApplication
	role := grantActorRoleSubmitter
	if wasExistingApplication {
		app, role = grantsRead(actor, grantAuthSubmit, b, id)
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

	if role != grantActorRoleSubmitter {
		revision.ReferenceIds = app.Application.CurrentRevision.Document.ReferenceIds
	}

	// TODO check that the suballocator flag is allowed to be set for grant giver initiated

	var err *util.HttpError

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
			var grantGiverId string
			for grantGiver, _ := range grantGivers {
				grantGiverId = grantGiver
				break
			}

			if !actor.Membership[rpc.ProjectId(grantGiverId)].Satisfies(rpc.ProjectRoleAdmin) {
				err = util.HttpErr(http.StatusForbidden, "you are not an administrator of this project")
			}
		}
	}

	if err == nil {
	outer:
		for grantGiver, categories := range grantGivers {
			if revision.Form.Type != accapi.FormTypeGrantGiverInitiated && !grantsCanApply(actor, recipient, grantGiver) {
				// TODO This check should only be made if the sender is the original creator
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

	if err != nil {
		if !wasExistingApplication {
			delete(b.Applications, id)
		}
		app.Mu.Unlock()
		b.Mu.Unlock()
		return 0, err
	}

	if !wasExistingApplication {
		b.Mu.Unlock()
	}

	// Data update and sync
	// -----------------------------------------------------------------------------------------------------------------
	// Application is now guaranteed to be semantically valid and the request is expected to succeed. The application
	// mutex is still held.

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
		// TODO very different rules apply in this case
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

	revToInsert := accapi.GrantRevision{
		CreatedAt:      fndapi.Timestamp(now),
		UpdatedBy:      actor.Username,
		RevisionNumber: len(app.Application.Status.Revisions) + 1,
		Document:       *revision,
	}

	app.Application.Status.Revisions = append(app.Application.Status.Revisions, revToInsert)
	app.Application.CurrentRevision = revToInsert

	lGrantsPersist(app)

	if app.Application.Status.OverallState == accapi.GrantApplicationStateApproved {
		lGrantsAwardResources(app)
	}

	app.Mu.Unlock()

	return id, nil
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
	b := grantAB(appId)
	app, _ := grantsRead(actor, grantAuthApprover, b, appId)
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

func GrantsPostComment(actor rpc.Actor, req accapi.GrantsPostCommentRequest) *util.HttpError {
	if len(req.Comment) > 1024*1024 {
		return util.HttpErr(http.StatusForbidden, "your comment is too long")
	}

	appId, _ := strconv.ParseInt(req.ApplicationId, 10, 64)
	b := grantAB(req.ApplicationId)
	app, _ := grantsRead(actor, grantAuthRead, b, appId)
	if app == nil {
		return util.HttpErr(http.StatusNotFound, "unknown application")
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
	return nil
}

func GrantsDeleteComment(actor rpc.Actor, req accapi.GrantsDeleteCommentRequest) *util.HttpError {
	appId, _ := strconv.ParseInt(req.ApplicationId, 10, 64)
	b := grantAB(req.ApplicationId)
	app, role := grantsRead(actor, grantAuthRead, b, appId)
	if app == nil {
		return util.HttpErr(http.StatusNotFound, "unknown application")
	}

	app.Mu.Lock()
	found := false
	for i, comment := range app.Application.Status.Comments {
		if comment.Id == req.CommentId && (role == grantActorRoleApprover || comment.Username == actor.Username) {
			util.RemoveAtIndex(app.Application.Status.Comments, i)
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

}

func lGrantsPersist(app *grantApplication) {
	// TODO
}

func GrantsRetrieveGrantGivers(actor rpc.Actor, req accapi.RetrieveGrantGiversRequest) ([]accapi.GrantGiver, *util.HttpError) {
	now := time.Now()
	// TODO parents should be included in list and deduplicated

	recipient := accapi.Recipient{}

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
		b := grantAB(appId)
		app, _ := grantsRead(actor, grantAuthRead, b, appId)
		if app == nil {
			return nil, util.HttpErr(http.StatusNotFound, "unknown application")
		}

		app.Mu.RLock()
		recipient = app.Application.CurrentRevision.Document.Recipient
		app.Mu.RUnlock()
	}

	// The request is expected to succeed at this point
	// -----------------------------------------------------------------------------------------------------------------

	var result []accapi.GrantGiver
	for _, b := range grantGlobals.SettingBuckets {
		b.Mu.RLock()
		for grantGiver, _ := range b.PublicGrantGivers {
			sWrapper, ok := b.Settings[grantGiver]
			sWrapper.Mu.RLock()

			if ok && grantsCanApply(actor, recipient, grantGiver) {
				wallets := internalRetrieveWallets(now, grantGiver, walletFilter{RequireActive: true})
				var categories []accapi.ProductCategory
				for _, wallet := range wallets {
					categories = append(categories, wallet.PaysFor)
				}

				gg := accapi.GrantGiver{
					Id:          grantGiver,
					Title:       "", // TODO
					Description: sWrapper.Settings.Description,
					Templates:   sWrapper.Settings.Templates,
					Categories:  categories,
				}

				result = append(result, gg)
			}

			sWrapper.Mu.RUnlock()
		}
		b.Mu.RUnlock()
	}

	return result, nil
}

// TODO notifications
