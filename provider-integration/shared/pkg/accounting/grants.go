package apm

import (
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

type GrantRequestSettings struct {
	Enabled             bool           `json:"enabled"`
	Description         string         `json:"description"`
	AllowRequestsFrom   []UserCriteria `json:"allowRequestsFrom"`
	ExcludeRequestsFrom []UserCriteria `json:"excludeRequestsFrom"`
	Templates           Templates      `json:"templates"`
}

type GrantGiver struct {
	Id          string            `json:"id"`
	Title       string            `json:"title"`
	Description string            `json:"description"`
	Templates   Templates         `json:"templates"`
	Categories  []ProductCategory `json:"categories"`
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
	Id              string        `json:"id"`
	CreatedBy       string        `json:"createdBy"`
	CreatedAt       fnd.Timestamp `json:"createdAt"`
	UpdatedAt       fnd.Timestamp `json:"updatedAt"`
	CurrentRevision GrantRevision `json:"currentRevision"`
	Status          GrantStatus   `json:"status"`
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

type Recipient struct {
	Type     RecipientType       `json:"type"`
	Id       util.Option[string] `json:"id"`       // existingProject
	Title    util.Option[string] `json:"title"`    // newProject
	Username util.Option[string] `json:"username"` // personalWorkspace
}

type AllocationRequest struct {
	Category         string              `json:"category"`
	Provider         string              `json:"provider"`
	GrantGiver       string              `json:"grantGiver"`
	BalanceRequested util.Option[int64]  `json:"balanceRequested"`
	Period           Period              `json:"period"`
	GrantGiverTitle  util.Option[string] `json:"grantGiverTitle"`
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
	ProjectId    string                `json:"projectId"`
	ProjectTitle string                `json:"projectTitle"`
	State        GrantApplicationState `json:"state"`
}

type GrantComment struct {
	Id        string        `json:"id"`
	Username  string        `json:"username"`
	CreatedAt fnd.Timestamp `json:"createdAt"`
	Comment   string        `json:"comment"`
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
	GrantGivers []GrantGiver
}

var RetrieveGrantGivers = rpc.Call[RetrieveGrantGiversRequest, RetrieveGrantGiversResponse]{
	BaseContext: GrantsNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "retrieveGrantGivers",
	Roles:       rpc.RolesEndUser,
}

// TODO comments and request settings
