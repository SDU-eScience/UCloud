package apm

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"regexp"
	"strconv"
	"strings"

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

var UserCriteriaTypeOptions = []UserCriteriaType{
	UserCriteriaTypeAnyone,
	UserCriteriaTypeEmail,
	UserCriteriaTypeWayf,
}

type UserCriteria struct {
	Type   UserCriteriaType    `json:"type"`
	Domain util.Option[string] `json:"domain"` // email
	Org    util.Option[string] `json:"org"`    // wayf
}

type TemplatesType string

const (
	TemplatesTypePlainText  TemplatesType = "plain_text"
	TemplatesTypeStructured TemplatesType = "structured"
)

type TemplatesStructured struct {
	PersonalProject []FormField `json:"personalProject"`
	NewProject      []FormField `json:"newProject"`
	ExistingProject []FormField `json:"existingProject"`
}
type Templates struct {
	Type       TemplatesType       `json:"type"`
	Structured TemplatesStructured `json:"structured"`

	// Legacy compatibility -- plain_text-case
	PersonalProject string `json:"personalProject"`
	NewProject      string `json:"newProject"`
	ExistingProject string `json:"existingProject"`
}

type FormField struct {
	Name        string           `json:"name"`
	Title       string           `json:"title"`
	Description string           `json:"description"`
	Optional    bool             `json:"optional"`
	MaxLength   util.Option[int] `json:"maxLength"`
	Rows        util.Option[int] `json:"rows"`
}

type GrantApplication struct {
	Id              util.IntOrString    `json:"id"`
	CreatedBy       string              `json:"createdBy"`
	CreatedAt       fnd.Timestamp       `json:"createdAt"`
	UpdatedAt       fnd.Timestamp       `json:"updatedAt"`
	CurrentRevision GrantRevision       `json:"currentRevision"`
	Status          GrantStatus         `json:"status"`
	ProjectId       util.Option[string] `json:"projectId"`
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
	AllocationPeriod   util.Option[Period]   `json:"allocationPeriod"`
}

type FormType string

const (
	FormTypePlainText           FormType = "plain_text"
	FormTypeGrantGiverInitiated FormType = "grant_giver_initiated"
	FormTypeStructured          FormType = "structured"
)

func (f FormType) Valid() bool {
	switch f {
	case FormTypeStructured:
		return true
	case FormTypePlainText:
		return true
	case FormTypeGrantGiverInitiated:
		return true
	default:
		return false
	}
}

func normalizeTitle(title string) string {
	words := strings.Split(title, " ")
	if len(words) == 0 {
		return title
	}

	builder := words[0]

	for i := 1; i < len(words); i++ {
		word := words[i]

		builder += " "

		if word == strings.ToUpper(word) || word == strings.ToLower(word) {
			builder += word
		} else {
			builder += strings.ToLower(word)
		}
	}

	return builder
}

func ParseAnswerFormFields(text string) []AnswerFieldForm {
	lines := strings.Split(text, "\n")

	var sectionSeparators []int
	for i, line := range lines {
		if strings.HasPrefix(line, "---") {
			allDashes := true
			for _, r := range line {
				if r != '-' {
					allDashes = false
					break
				}
			}

			if allDashes {
				sectionSeparators = append(sectionSeparators, i)
			}
		}
	}
	var titles []string
	for _, lineIdx := range sectionSeparators {
		if lineIdx > 0 {
			titles = append(titles, lines[lineIdx-1])
		}
	}

	var answers []string
	currentStartLine := 0

	for i := 0; i <= len(sectionSeparators); i++ {
		end := len(lines)

		if i < len(sectionSeparators) {
			end = sectionSeparators[i] - 1
		}

		var builder strings.Builder

		for row := currentStartLine; row < end; row++ {
			builder.WriteString(lines[row])
			builder.WriteString("\n")
		}

		answer := strings.TrimSpace(builder.String())

		if answer != "" {
			answers = append(answers, answer)
		}

		currentStartLine = end + 2
	}
	var result []AnswerFieldForm

	for i := 0; i < len(titles); i++ {
		answer := ""
		if i < len(answers) {
			answer = answers[i]
		}

		title := normalizeTitle(titles[i])

		field := AnswerFieldForm{
			Name:   title,
			Answer: answer,
			Title:  title,
		}
		result = append(result, field)
	}
	return result
}

func ParseFormFields(text string) []FormField {
	lines := strings.Split(text, "\n")

	var sectionSeparators []int
	for i, line := range lines {
		if strings.HasPrefix(line, "---") {
			allDashes := true
			for _, r := range line {
				if r != '-' {
					allDashes = false
					break
				}
			}

			if allDashes {
				sectionSeparators = append(sectionSeparators, i)
			}
		}
	}
	var titles []string
	for _, lineIdx := range sectionSeparators {
		if lineIdx > 0 {
			titles = append(titles, lines[lineIdx-1])
		}
	}

	foundDescriptionBeforeFirstTitle := false

	var descriptions []string
	currentStartLine := 0

	for i := 0; i <= len(sectionSeparators); i++ {
		end := len(lines)

		if i < len(sectionSeparators) {
			end = sectionSeparators[i] - 1
		}

		var builder strings.Builder

		for row := currentStartLine; row < end; row++ {
			builder.WriteString(lines[row])
			builder.WriteString("\n")
		}

		description := strings.TrimSpace(builder.String())

		if description != "" {
			if i == 0 {
				foundDescriptionBeforeFirstTitle = true
			}

			descriptions = append(descriptions, description)
		} else {
			if i != 0 {
				descriptions = append(descriptions, "")
			}
		}

		currentStartLine = end + 2
	}

	if foundDescriptionBeforeFirstTitle {
		if len(titles) > 0 {
			titles = append([]string{"Introduction"}, titles...)
		} else {
			titles = []string{"Application"}
		}
	}

	prefixesWhichSoundMandatory := []string{
		"Add a ",
		"Describe the ",
		"Provide a ",
		"Please describe the reason for applying",
		"Required:",
	}

	limitRegex := regexp.MustCompile(`max (\d+) ch`)

	var result []FormField

	for i := 0; i < len(titles); i++ {
		description := ""
		if i < len(descriptions) {
			description = descriptions[i]
		}

		title := normalizeTitle(titles[i])

		optional := true
		for _, prefix := range prefixesWhichSoundMandatory {
			if strings.HasPrefix(description, prefix) {
				optional = false
				break
			}
		}

		field := FormField{
			Name:        title,
			Title:       title,
			Description: description,
			Optional:    optional,
		}

		matches := limitRegex.FindAllStringSubmatch(description, -1)
		for _, match := range matches {
			if len(match) >= 2 {
				if limit, err := strconv.Atoi(match[1]); err == nil {
					field.MaxLength = util.OptValue(limit)
				}
			}
		}

		if strings.ToLower(field.Title) == "application" {
			field.MaxLength = util.OptValue(4000)
		}

		limit := 250
		if field.MaxLength.Present {
			limit = field.MaxLength.Value
		}

		rows := min(15, max(2, limit/50))

		if strings.Contains(strings.ToLower(field.Title), "project title") {
			rows = 2
		}

		field.Rows = util.OptValue(rows)

		result = append(result, field)
	}

	// Move large sections to the end
	smallFields := make([]FormField, 0, len(result))
	largeFields := make([]FormField, 0)

	for _, field := range result {
		maxLength := 0
		if field.MaxLength.Present {
			maxLength = field.MaxLength.Value
		}

		if maxLength > 1000 {
			largeFields = append(largeFields, field)
		} else {
			smallFields = append(smallFields, field)
		}
	}
	result = append(smallFields, largeFields...)

	return result
}

type AnswerFieldForm struct {
	Answer string    `json:"answer"`
	Field  FormField `json:"field"`
}

type Form struct {
	Type         FormType          `json:"type"`
	Text         string            `json:"text"` // plain_text, grant_giver_initiated - used for legacy form
	Fields       []AnswerFieldForm `json:"fields"`
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

func RecipientFromReference(typ RecipientType, reference string) Recipient {
	switch typ {
	case "existing_project", RecipientTypeExistingProject:
		return Recipient{
			Type: RecipientTypeExistingProject,
			Id:   util.OptValue(reference),
		}
	case "new_project", RecipientTypeNewProject:
		return Recipient{
			Type:  RecipientTypeNewProject,
			Title: util.OptValue(reference),
		}
	case "personal", RecipientTypePersonalWorkspace:
		return Recipient{
			Type:     RecipientTypePersonalWorkspace,
			Username: util.OptValue(reference),
		}
	default:
		return Recipient{
			Type:     typ,
			Username: util.OptValue(reference),
		}
	}
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
	OptionalUserInfo   fnd.OptionalUserInfo      `json:"optionalUserInfo"`
	OverallState       GrantApplicationState     `json:"overallState"`
	StateBreakdown     []GrantGiverApprovalState `json:"stateBreakdown"`
	Comments           []GrantComment            `json:"comments"`
	Revisions          []GrantRevision           `json:"revisions"`
	ProjectTitle       util.Option[string]       `json:"projectTitle"`
	ProjectPI          string                    `json:"projectPI"`
	HasUnreadComments  bool                      `json:"hasUnreadComments"`
	ApplicationHistory []GrantApplication        `json:"applicationHistory"`
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
	Query        string              `json:"query"`
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

type GrantsExportResponse struct {
	Id               string                `json:"id"`
	Title            string                `json:"title"`
	SubmittedBy      string                `json:"submittedBy"`
	SubmittedAt      fnd.Timestamp         `json:"submittedAt"`
	StartDate        fnd.Timestamp         `json:"startDate"`
	DurationMonths   int                   `json:"durationMonths"`
	State            GrantApplicationState `json:"state"`
	GrantGiver       string                `json:"grantGiver"`
	LastUpdatedAt    fnd.Timestamp         `json:"lastUpdatedAt"`
	Resources        map[string]int        `json:"resources"`
	OptionalUserInfo fnd.OptionalUserInfo  `json:"optionalUserInfo"`
}

var GrantsExport = rpc.Call[util.Empty, []GrantsExportResponse]{
	BaseContext: GrantsNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "export",
	Roles:       rpc.RolesEndUser,
}

type GrantsExportCsvResponse struct {
	FileName string `json:"fileName"`
	CsvData  string `json:"csvData"`
}

var GrantsExportCsv = rpc.Call[util.Empty, GrantsExportCsvResponse]{
	BaseContext: GrantsNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "exportCsv",
	Roles:       rpc.RolesEndUser,
}
