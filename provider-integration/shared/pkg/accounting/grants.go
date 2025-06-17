package apm

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type GrantApplicationFilter string

const (
	GrantApplicationFilterShowAll  GrantApplicationFilter = "SHOW_ALL"
	GrantApplicationFilterActive   GrantApplicationFilter = "ACTIVE"
	GrantApplicationFilterInactive GrantApplicationFilter = "INACTIVE"
)

type GrantApplicationState string

const (
	GrantApplicationStateApproved   GrantApplicationState = "APPROVED"
	GrantApplicationStateRejected   GrantApplicationState = "REJECTED"
	GrantApplicationStateClosed     GrantApplicationState = "CLOSED"
	GrantApplicationStateInProgress GrantApplicationState = "IN_PROGRESS"
)

var GrantApplicationStates = []GrantApplicationState{
	GrantApplicationStateApproved,
	GrantApplicationStateRejected,
	GrantApplicationStateClosed,
	GrantApplicationStateInProgress,
}

type GrantRequestSettings struct {
	Enabled             bool           `json:"enabled"`
	Description         string         `json:"description"`
	AllowRequestsFrom   []UserCriteria `json:"allowRequestsFrom"`
	ExcludeRequestsFrom []UserCriteria `json:"excludeRequestsFrom"`
	Templates           Templates      `json:"templates"`
}

func (s GrantRequestSettings) MarshalJSON() ([]byte, error) {
	type wrapper struct {
		Enabled             bool           `json:"enabled"`
		Description         string         `json:"description"`
		AllowRequestsFrom   []UserCriteria `json:"allowRequestsFrom"`
		ExcludeRequestsFrom []UserCriteria `json:"excludeRequestsFrom"`
		Templates           Templates      `json:"templates"`
	}

	if s.AllowRequestsFrom == nil {
		s.AllowRequestsFrom = []UserCriteria{}
	}
	if s.ExcludeRequestsFrom == nil {
		s.ExcludeRequestsFrom = []UserCriteria{}
	}

	w := wrapper{
		Enabled:             s.Enabled,
		Description:         s.Description,
		AllowRequestsFrom:   s.AllowRequestsFrom,
		ExcludeRequestsFrom: s.ExcludeRequestsFrom,
		Templates:           s.Templates,
	}

	return json.Marshal(w)
}

type GrantGiver struct {
	Id          string            `json:"id"`
	Description string            `json:"description"`
	Templates   Templates         `json:"templates"`
	Categories  []ProductCategory `json:"categories"`
}

func (gg GrantGiver) MarshalJSON() ([]byte, error) {
	type wrapper struct {
		Id          string            `json:"id"`
		Title       string            `json:"title"`
		Description string            `json:"description"`
		Templates   Templates         `json:"templates"`
		Categories  []ProductCategory `json:"categories"`
	}

	w := wrapper{
		Id:          gg.Id,
		Title:       gg.Id,
		Description: gg.Description,
		Templates:   gg.Templates,
		Categories:  gg.Categories,
	}

	return json.Marshal(w)
}

type UserCriteriaType string

const (
	UserCriteriaTypeAnyone UserCriteriaType = "anyone"
	UserCriteriaTypeEmail  UserCriteriaType = "email"
	UserCriteriaTypeWayf   UserCriteriaType = "wayf"
)

type UserCriteria struct {
	Type   UserCriteriaType    `json:"type"`
	Domain util.Option[string] `json:"domain"` // email
	Org    util.Option[string] `json:"org"`    // wayf
}

type TemplatesType string

const (
	TemplatesTypePlainText TemplatesType = "plain_text"
)

type Templates struct {
	Type            TemplatesType `json:"type"`
	PersonalProject string        `json:"personalProject"` // plain_text
	NewProject      string        `json:"newProject"`      // plain_text
	ExistingProject string        `json:"existingProject"` // plain_text
}

type GrantApplication struct {
	Id              util.IntOrString `json:"id"`
	CreatedBy       string           `json:"createdBy"`
	CreatedAt       fnd.Timestamp    `json:"createdAt"`
	UpdatedAt       fnd.Timestamp    `json:"updatedAt"`
	CurrentRevision GrantRevision    `json:"currentRevision"`
	Status          GrantStatus      `json:"status"`
}

type GrantRevision struct {
	CreatedAt      fnd.Timestamp `json:"createdAt"`
	UpdatedBy      string        `json:"updatedBy"`
	RevisionNumber int           `json:"revisionNumber"`
	Document       GrantDocument `json:"document"`
}

type GrantDocument struct {
	Recipient          Recipient             `json:"recipient"`
	AllocationRequests []AllocationRequest   `json:"allocationRequests"`
	Form               Form                  `json:"form"`
	ReferenceIds       util.Option[[]string] `json:"referenceIds"`
	RevisionComment    util.Option[string]   `json:"revisionComment"`
	ParentProjectId    util.Option[string]   `json:"parentProjectId"`
	AllocationPeriod   util.Option[Period]   `json:"allocationPeriod"`
}

type FormType string

const (
	FormTypePlainText           FormType = "plain_text"
	FormTypeGrantGiverInitiated FormType = "grant_giver_initiated"
)

func (f FormType) Valid() bool {
	switch f {
	case FormTypePlainText:
		return true
	case FormTypeGrantGiverInitiated:
		return true
	default:
		return false
	}
}

type Form struct {
	Type         FormType          `json:"type"`
	Text         string            `json:"text"`         // plain_text, grant_giver_initiated
	SubAllocator util.Option[bool] `json:"subAllocator"` // grant_giver_initiated
}

type RecipientType string

const (
	RecipientTypeExistingProject   RecipientType = "existingProject"
	RecipientTypeNewProject        RecipientType = "newProject"
	RecipientTypePersonalWorkspace RecipientType = "personalWorkspace"
)

func (t RecipientType) Valid() bool {
	switch t {
	case RecipientTypeExistingProject:
		return true
	case RecipientTypeNewProject:
		return true
	case RecipientTypePersonalWorkspace:
		return true
	default:
		return false
	}
}

type Recipient struct {
	Type     RecipientType       `json:"type"`
	Id       util.Option[string] `json:"id"`       // existingProject
	Title    util.Option[string] `json:"title"`    // newProject
	Username util.Option[string] `json:"username"` // personalWorkspace
}

func (r *Recipient) Reference() util.Option[string] {
	switch r.Type {
	case RecipientTypeExistingProject:
		return r.Id
	case RecipientTypeNewProject:
		return r.Title
	case RecipientTypePersonalWorkspace:
		return r.Username
	default:
		return util.OptNone[string]()
	}
}

func (r *Recipient) Valid() bool {
	return r.Reference().Present
}

type AllocationRequest struct {
	Category         string             `json:"category"`
	Provider         string             `json:"provider"`
	GrantGiver       string             `json:"grantGiver"`
	BalanceRequested util.Option[int64] `json:"balanceRequested"`
	Period           Period             `json:"period"`
}

func (ar AllocationRequest) MarshalJSON() ([]byte, error) {
	type wrapper struct {
		Category         string              `json:"category"`
		Provider         string              `json:"provider"`
		GrantGiver       string              `json:"grantGiver"`
		BalanceRequested util.Option[int64]  `json:"balanceRequested"`
		Period           Period              `json:"period"`
		GrantGiverTitle  util.Option[string] `json:"grantGiverTitle"`
	}

	w := wrapper{
		Category:         ar.Category,
		Provider:         ar.Provider,
		GrantGiver:       ar.GrantGiver,
		BalanceRequested: ar.BalanceRequested,
		Period:           ar.Period,
		GrantGiverTitle:  util.OptValue(ar.GrantGiver),
	}

	return json.Marshal(w)
}

type Period struct {
	Start util.Option[fnd.Timestamp] `json:"start"`
	End   util.Option[fnd.Timestamp] `json:"end"`
}

type GrantStatus struct {
	OverallState   GrantApplicationState     `json:"overallState"`
	StateBreakdown []GrantGiverApprovalState `json:"stateBreakdown"`
	Comments       []GrantComment            `json:"comments"`
	Revisions      []GrantRevision           `json:"revisions"`
	ProjectTitle   util.Option[string]       `json:"projectTitle"`
	ProjectPI      string                    `json:"projectPI"`
}

type GrantGiverApprovalState struct {
	ProjectId string                `json:"projectId"`
	State     GrantApplicationState `json:"state"`
}

func (ga GrantGiverApprovalState) MarshalJSON() ([]byte, error) {
	type wrapper struct {
		ProjectId    string                `json:"projectId"`
		ProjectTitle string                `json:"projectTitle"`
		State        GrantApplicationState `json:"state"`
	}

	w := wrapper{
		ProjectId:    ga.ProjectId,
		ProjectTitle: ga.ProjectId,
		State:        ga.State,
	}
	return json.Marshal(w)
}

type GrantComment struct {
	Id        util.IntOrString `json:"id"`
	Username  string           `json:"username"`
	CreatedAt fnd.Timestamp    `json:"createdAt"`
	Comment   string           `json:"comment"`
}

// API
// =====================================================================================================================

const GrantsNamespace = "grants/v2"

type GrantsBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	Filter                      util.Option[GrantApplicationFilter] `json:"filter"`
	IncludeIngoingApplications  util.Option[bool]                   `json:"includeIngoingApplications"`
	IncludeOutgoingApplications util.Option[bool]                   `json:"includeOutgoingApplications"`
}

var GrantsBrowse = rpc.Call[GrantsBrowseRequest, fnd.PageV2[GrantApplication]]{
	BaseContext: GrantsNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

var GrantsRetrieve = rpc.Call[fnd.FindByStringId, GrantApplication]{
	BaseContext: GrantsNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

type GrantsSubmitRevisionRequest struct {
	Revision             GrantDocument          `json:"revision"`
	Comment              string                 `json:"comment"`
	ApplicationId        util.Option[string]    `json:"applicationId"`
	AlternativeRecipient util.Option[Recipient] `json:"alternativeRecipient"`
}

var GrantsSubmitRevision = rpc.Call[GrantsSubmitRevisionRequest, fnd.FindByStringId]{
	BaseContext: GrantsNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "submitRevision",
	Roles:       rpc.RolesEndUser,
}

type GrantsUpdateStateRequest struct {
	ApplicationId string                `json:"applicationId"`
	NewState      GrantApplicationState `json:"newState"`
}

var GrantsUpdateState = rpc.Call[GrantsUpdateStateRequest, util.Empty]{
	BaseContext: GrantsNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "updateState",
	Roles:       rpc.RolesEndUser,
}

type GrantsTransferRequest struct {
	ApplicationId string `json:"applicationId"`
	Target        string `json:"target"`
	Comment       string `json:"comment"`
	// source derived from project header
}

var GrantsTransfer = rpc.Call[GrantsTransferRequest, util.Empty]{
	BaseContext: GrantsNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "transfer",
	Roles:       rpc.RolesEndUser,
}

type RetrieveGrantGiversType string

const (
	RetrieveGrantGiversTypePersonalWorkspace   RetrieveGrantGiversType = "PersonalWorkspace"
	RetrieveGrantGiversTypeNewProject          RetrieveGrantGiversType = "NewProject"
	RetrieveGrantGiversTypeExistingProject     RetrieveGrantGiversType = "ExistingProject"
	RetrieveGrantGiversTypeExistingApplication RetrieveGrantGiversType = "ExistingApplication"
)

type RetrieveGrantGiversRequest struct {
	Type  RetrieveGrantGiversType `json:"type"`
	Id    string                  `json:"id"`
	Title string                  `json:"title"`
}

type RetrieveGrantGiversResponse struct {
	GrantGivers []GrantGiver `json:"grantGivers"`
}

func (g RetrieveGrantGiversResponse) MarshalJSON() ([]byte, error) {
	if g.GrantGivers == nil {
		g.GrantGivers = []GrantGiver{}
	}

	type wrapper struct {
		GrantGivers []GrantGiver `json:"grantGivers"`
	}

	return json.Marshal(wrapper{GrantGivers: g.GrantGivers})
}

var RetrieveGrantGivers = rpc.Call[RetrieveGrantGiversRequest, RetrieveGrantGiversResponse]{
	BaseContext: GrantsNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "retrieveGrantGivers",
	Roles:       rpc.RolesEndUser,
}

type GrantsPostCommentRequest struct {
	ApplicationId string `json:"applicationId"`
	Comment       string `json:"comment"`
}

var GrantsPostComment = rpc.Call[GrantsPostCommentRequest, fnd.FindByStringId]{
	BaseContext: GrantsNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "postComment",
	Roles:       rpc.RolesEndUser,
}

type GrantsDeleteCommentRequest struct {
	ApplicationId string `json:"applicationId"`
	CommentId     string `json:"commentId"`
}

var GrantsDeleteComment = rpc.Call[GrantsDeleteCommentRequest, util.Empty]{
	BaseContext: GrantsNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "deleteComment",
	Roles:       rpc.RolesEndUser,
}

var GrantsUpdateRequestSettings = rpc.Call[GrantRequestSettings, util.Empty]{
	BaseContext: GrantsNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "updateRequestSettings",
	Roles:       rpc.RolesEndUser | rpc.RolesService,
}

var GrantsRetrieveRequestSettings = rpc.Call[util.Empty, GrantRequestSettings]{
	BaseContext: GrantsNamespace,
	Convention:  rpc.ConventionRetrieve,
	Operation:   "requestSettings",
	Roles:       rpc.RolesEndUser,
}

var GrantsUploadLogo = rpc.Call[[]byte, util.Empty]{
	BaseContext: GrantsNamespace,
	Operation:   "uploadLogo",
	Convention:  rpc.ConventionCustom,
	Roles:       rpc.RolesEndUser,

	CustomMethod: http.MethodPost,
	CustomPath:   fmt.Sprintf("/api/%s/uploadLogo", GrantsNamespace),
	CustomServerParser: func(w http.ResponseWriter, r *http.Request) ([]byte, *util.HttpError) {
		result, err := io.ReadAll(io.LimitReader(r.Body, 1024*1024*5))
		if err != nil {
			return nil, util.HttpErr(http.StatusBadRequest, "Logo was not correctly received")
		} else {
			return result, nil
		}
	},
	CustomClientHandler: func(self *rpc.Call[[]byte, util.Empty], client *rpc.Client, request []byte) (util.Empty, *util.HttpError) {
		panic("client not implemented")
	},
}

type GrantsRetrieveLogoRequest struct {
	ProjectId string `json:"projectId"`
}

var GrantsRetrieveLogo = rpc.Call[GrantsRetrieveLogoRequest, []byte]{
	BaseContext: GrantsNamespace,
	Operation:   "retrieveLogo",
	Convention:  rpc.ConventionCustom,
	Roles:       rpc.RolesPublic,

	CustomMethod: http.MethodGet,
	CustomPath:   fmt.Sprintf("/api/%s/retrieveLogo", GrantsNamespace),
	CustomServerParser: func(w http.ResponseWriter, r *http.Request) (GrantsRetrieveLogoRequest, *util.HttpError) {
		return rpc.ParseRequestFromQuery[GrantsRetrieveLogoRequest](w, r)
	},
	CustomServerProducer: func(response []byte, err *util.HttpError, w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "image/any")
		if err != nil {
			w.WriteHeader(err.StatusCode)
		} else {
			w.WriteHeader(http.StatusOK)
		}
		_, _ = w.Write(response)
	},
	CustomClientHandler: func(self *rpc.Call[GrantsRetrieveLogoRequest, []byte], client *rpc.Client, request GrantsRetrieveLogoRequest) ([]byte, *util.HttpError) {
		panic("client not implemented")
	},
}
