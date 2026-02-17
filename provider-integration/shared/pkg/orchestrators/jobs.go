package orchestrators

import (
	"encoding/json"
	"fmt"
	"strings"

	"ucloud.dk/shared/pkg/rpc"

	apm "ucloud.dk/shared/pkg/accounting"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

type SshKey struct {
	Id            string              `json:"id"`
	Owner         string              `json:"owner"`
	CreatedAt     fnd.Timestamp       `json:"createdAt"`
	Fingerprint   string              `json:"fingerprint"`
	Specification SshKeySpecification `json:"specification"`
}

type SshKeySpecification struct {
	Title string `json:"title"`
	Key   string `json:"key"`
}

type ExportedParametersRequest struct {
	Application       NameAndVersion       `json:"application"`
	Product           apm.ProductReference `json:"product"`
	Name              string               `json:"name"`
	Replicas          int                  `json:"replicas"`
	Parameters        json.RawMessage      `json:"parameters"`
	Resources         json.RawMessage      `json:"resources"`
	TimeAllocation    SimpleDuration       `json:"timeAllocation"`
	ResolvedProduct   json.RawMessage      `json:"resolvedProduct"`
	ResolvedSupport   json.RawMessage      `json:"resolvedSupport"`
	AllowDuplicateJob bool                 `json:"allowDuplicateJob"`
	SshEnabled        bool                 `json:"sshEnabled"`
}

type ExportedParameters struct {
	SiteVersion       int                         `json:"siteVersion"`
	Request           ExportedParametersRequest   `json:"request"`
	ResolvedResources ExportedParametersResources `json:"resolvedResources"`
	MachineType       json.RawMessage             `json:"machineType"`
}

type ExportedParametersResources struct {
	Ingress map[string]Ingress `json:"ingress"`
}

type DynamicTarget struct {
	Rank        int                    `json:"rank"`
	Type        InteractiveSessionType `json:"type"`
	Target      string                 `json:"target"`
	Port        int                    `json:"port"`
	DefaultName util.Option[string]    `json:"defaultName"`
}

type JobState string

const (
	JobStateInQueue   JobState = "IN_QUEUE"
	JobStateRunning   JobState = "RUNNING"
	JobStateSuccess   JobState = "SUCCESS"
	JobStateFailure   JobState = "FAILURE"
	JobStateExpired   JobState = "EXPIRED"
	JobStateSuspended JobState = "SUSPENDED"
)

func (jobState JobState) IsFinal() bool {
	switch jobState {
	case JobStateSuccess, JobStateFailure, JobStateExpired:
		return true
	default:
		return false
	}
}

type InteractiveSessionType string

const (
	InteractiveSessionTypeWeb   InteractiveSessionType = "WEB"
	InteractiveSessionTypeVnc   InteractiveSessionType = "VNC"
	InteractiveSessionTypeShell InteractiveSessionType = "SHELL"
)

type Job struct {
	Resource
	Updates       []JobUpdate      `json:"updates"`
	Specification JobSpecification `json:"specification"`
	Status        JobStatus        `json:"status"`
	Output        JobOutput        `json:"output"`
}

type JobStatus struct {
	State               JobState                                 `json:"state"`
	JobParametersJson   util.Option[ExportedParameters]          `json:"jobParametersJson,omitempty"`
	StartedAt           util.Option[fnd.Timestamp]               `json:"startedAt,omitempty"`
	ExpiresAt           util.Option[fnd.Timestamp]               `json:"expiresAt,omitempty"`
	ResolvedApplication util.Option[Application]                 `json:"resolvedApplication,omitempty"`
	ResolvedProduct     util.Option[apm.ProductV2]               `json:"resolvedProduct,omitempty"`
	ResolvedSupport     util.Option[ResolvedSupport[JobSupport]] `json:"resolvedSupport"`
	AllowRestart        bool                                     `json:"allowRestart"`
}

type JobUpdate struct {
	State                  util.Option[JobState] `json:"state"`
	OutputFolder           util.Option[string]   `json:"outputFolder"`
	Status                 util.Option[string]   `json:"status"`
	ExpectedState          util.Option[JobState] `json:"expectedState"`
	ExpectedDifferentState util.Option[bool]     `json:"expectedDifferentState"`
	NewTimeAllocation      util.Option[int64]    `json:"newTimeAllocation"`
	AllowRestart           util.Option[bool]     `json:"allowRestart"` // deprecated
	NewMounts              util.Option[[]string] `json:"newMounts"`    // deprecated
	Timestamp              fnd.Timestamp         `json:"timestamp"`
}

type JobSpecification struct {
	Product           apm.ProductReference         `json:"product"`
	Application       NameAndVersion               `json:"application"`
	Name              string                       `json:"name,omitempty"`
	Replicas          int                          `json:"replicas"`
	AllowDuplicateJob bool                         `json:"allowDuplicateJob"` // deprecated
	Parameters        map[string]AppParameterValue `json:"parameters"`
	Resources         []AppParameterValue          `json:"resources"`
	TimeAllocation    util.Option[SimpleDuration]  `json:"timeAllocation,omitempty"`
	OpenedFile        string                       `json:"openedFile,omitempty"`
	RestartOnExit     bool                         `json:"restartOnExit,omitempty"` // deprecated
	SshEnabled        bool                         `json:"sshEnabled,omitempty"`
}

type ComputeProductReference apm.ProductReference

type JobOutput struct {
	OutputFolder util.Option[string] `json:"outputFolder"`
}

type JobsExtendRequestItem struct {
	JobId         string         `json:"jobId"`
	RequestedTime SimpleDuration `json:"requestedTime"`
}

func (job *Job) getParameterValues(ofType AppParameterValueType) []AppParameterValue {
	var result []AppParameterValue

	for _, resource := range job.Specification.Resources {
		if resource.Type == ofType {
			result = append(result, resource)
		}
	}

	return result
}

type OpenSessionWithProvider struct {
	ProviderDomain string      `json:"providerDomain"`
	ProviderId     string      `json:"providerId"`
	Session        OpenSession `json:"session"`
}

type OpenSession struct {
	Type           OpenSessionType `json:"type"`
	JobId          string          `json:"jobId"`
	Rank           int             `json:"rank"`
	DomainOverride string          `json:"domainOverride"`

	RedirectClientTo string `json:"redirectClientTo,omitempty"` // Web

	SessionIdentifier string `json:"sessionIdentifier,omitempty"` // Shell

	Url      string `json:"url,omitempty"`      // VNC
	Password string `json:"password,omitempty"` // VNC
}

type OpenSessionType string

const (
	OpenSessionTypeShell OpenSessionType = "shell"
	OpenSessionTypeWeb   OpenSessionType = "web"
	OpenSessionTypeVnc   OpenSessionType = "vnc"
)

func OpenSessionShell(jobId string, rank int, sessionIdentifier string, domainOverride string) OpenSession {
	return OpenSession{
		Type:              OpenSessionTypeShell,
		JobId:             jobId,
		Rank:              rank,
		SessionIdentifier: sessionIdentifier,
		DomainOverride:    domainOverride,
	}
}

func OpenSessionWeb(jobId string, rank int, redirectClientTo string, domainOverride string) OpenSession {
	return OpenSession{
		Type:             OpenSessionTypeWeb,
		JobId:            jobId,
		Rank:             rank,
		RedirectClientTo: redirectClientTo,
		DomainOverride:   domainOverride,
	}
}

func OpenSessionVnc(jobId string, rank int, url string, password string, domainOverride string) OpenSession {
	return OpenSession{
		Type:           OpenSessionTypeVnc,
		JobId:          jobId,
		Url:            url,
		Password:       password,
		DomainOverride: domainOverride,
	}
}

// NOTE(Dan): This is slightly different from how escapeBash works in the Kotlin version since this also automatically
// wraps it in single quotes. This is how it was used in all cases anyway, so this makes it slightly simpler to use.

func EscapeBash(s string) string {
	builder := &strings.Builder{}
	builder.WriteRune('\'')
	for _, c := range []rune(s) {
		if c == '\'' {
			builder.WriteString("'\"'\"'")
		} else {
			builder.WriteRune(c)
		}
	}
	builder.WriteRune('\'')
	return builder.String()
}

type ParamAndValue struct {
	Parameter ApplicationParameter
	Value     AppParameterValue
}

type ArgBuilder func(value ParamAndValue) string

func DefaultArgBuilder(fileMapper func(ucloudPath string) string) ArgBuilder {
	return func(pv ParamAndValue) string {
		param := pv.Parameter
		value := pv.Value

		switch param.Type {
		case ApplicationParameterTypeTextArea:
			fallthrough
		case ApplicationParameterTypeText:
			return fmt.Sprint(value.Value)

		case ApplicationParameterTypeFloatingPoint:
			fallthrough
		case ApplicationParameterTypeInteger:
			return fmt.Sprint(value.Value)

		case ApplicationParameterTypeBoolean:
			b := value.Value.(bool)
			res := ""
			if b {
				res = param.TrueValue
			} else {
				res = param.FalseValue
			}

			if res == "" {
				if b {
					return "true"
				} else {
					return "false"
				}
			} else {
				return res
			}

		case ApplicationParameterTypeEnumeration:
			stringified := fmt.Sprint(value.Value)

			for _, opt := range param.Options {
				if opt.Name == stringified {
					return opt.Value
				}
			}
			return stringified

		case ApplicationParameterTypeInputFile:
			fallthrough
		case ApplicationParameterTypeInputDirectory:
			return fileMapper(value.Path)

		case ApplicationParameterTypePeer:
			return value.Hostname

		case ApplicationParameterTypeLicenseServer:
			fallthrough
		case ApplicationParameterTypeNetworkIp:
			fallthrough
		case ApplicationParameterTypeIngress:
			return value.Id

		case ApplicationParameterTypeWorkflow:
			return ""

		default:
			log.Warn("Unhandled value type: %v", param.Type)
			return ""
		}
	}
}

func VerifyParameterType(param *ApplicationParameter, value *AppParameterValue) bool {
	switch param.Type {
	case ApplicationParameterTypeInputDirectory:
		fallthrough
	case ApplicationParameterTypeInputFile:
		if value.Type != AppParameterValueTypeFile {
			return false
		}

	case ApplicationParameterTypeBoolean:
		if value.Type != AppParameterValueTypeBoolean {
			return false
		}

	case ApplicationParameterTypeFloatingPoint:
		if value.Type != AppParameterValueTypeFloatingPoint {
			return false
		}

	case ApplicationParameterTypeIngress:
		if value.Type != AppParameterValueTypeIngress {
			return false
		}

	case ApplicationParameterTypeInteger:
		if value.Type != AppParameterValueTypeInteger {
			return false
		}

	case ApplicationParameterTypeLicenseServer:
		if value.Type != AppParameterValueTypeLicense {
			return false
		}

	case ApplicationParameterTypeNetworkIp:
		if value.Type != AppParameterValueTypeNetwork {
			return false
		}

	case ApplicationParameterTypePeer:
		if value.Type != AppParameterValueTypePeer {
			return false
		}

	case ApplicationParameterTypeText:
		if value.Type != AppParameterValueTypeText {
			return false
		}

	case ApplicationParameterTypeEnumeration:
		if value.Type != AppParameterValueTypeText {
			return false
		}

	case ApplicationParameterTypeTextArea:
		if value.Type != AppParameterValueTypeText {
			return false
		}

	case ApplicationParameterTypeModuleList:
		if value.Type != AppParameterValueTypeModuleList {
			return false
		}
	}
	return true
}

type JobSupport struct {
	Product apm.ProductReference `json:"product"`
	Docker  struct {
		UniversalBackendSupport
	} `json:"docker"`
	VirtualMachine struct {
		UniversalBackendSupport
		Suspension bool `json:"suspension,omitempty"`
	} `json:"virtualMachine"`
	Native struct {
		UniversalBackendSupport
	} `json:"native"`
	QueueStatus util.Option[JobQueueStatus] `json:"queueStatus"`
}

type JobQueueStatus string

const (
	JobQueueAvailable JobQueueStatus = "AVAILABLE"
	JobQueueBusy      JobQueueStatus = "BUSY"
	JobQueueFull      JobQueueStatus = "FULL"
)

type UniversalBackendSupport struct {
	Enabled       bool `json:"enabled,omitempty"`
	Web           bool `json:"web,omitempty"`
	Vnc           bool `json:"vnc,omitempty"`
	Logs          bool `json:"logs,omitempty"`
	Terminal      bool `json:"terminal,omitempty"`
	Peers         bool `json:"peers,omitempty"`
	TimeExtension bool `json:"timeExtension,omitempty"`
}

// Job API
// =====================================================================================================================

type JobFlags struct {
	ResourceFlags
	FilterApplication  util.Option[string]   `json:"filterApplication"`
	FilterState        util.Option[JobState] `json:"filterState"`
	IncludeParameters  bool                  `json:"includeParameters"`
	IncludeApplication bool                  `json:"includeApplication"`
}

const jobNamespace = "jobs"
const jobProviderNamespace = "ucloud/" + rpc.ProviderPlaceholder + "/jobs"

var JobsCreate = rpc.Call[fnd.BulkRequest[JobSpecification], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: jobNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

var JobsTerminate = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]{
	BaseContext: jobNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "terminate",
}

var JobsExtend = rpc.Call[fnd.BulkRequest[JobsExtendRequestItem], fnd.BulkResponse[util.Empty]]{
	BaseContext: jobNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "extend",
}

var JobsSuspend = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]{
	BaseContext: jobNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "suspend",
}

var JobsUnsuspend = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]{
	BaseContext: jobNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "unsuspend",
}

type JobsOpenInteractiveSessionRequestItem struct {
	Id          string                 `json:"id"`
	Rank        int                    `json:"rank"`
	SessionType InteractiveSessionType `json:"sessionType"`
	Target      util.Option[string]    `json:"target"`
}

var JobsOpenInteractiveSession = rpc.Call[fnd.BulkRequest[JobsOpenInteractiveSessionRequestItem], fnd.BulkResponse[OpenSessionWithProvider]]{
	BaseContext: jobNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "interactiveSession",
}

type JobsRequestDynamicParametersRequest struct {
	Application NameAndVersion `json:"application"`
}

type JobsRequestDynamicParametersResponse struct {
	ParametersByProvider map[string][]ApplicationParameter `json:"parametersByProvider"`
}

var JobsRequestDynamicParameters = rpc.Call[JobsRequestDynamicParametersRequest, JobsRequestDynamicParametersResponse]{
	BaseContext: jobNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "requestDynamicParameters",
}

type JobsOpenTerminalInFolderRequestItem struct {
	Folder string `json:"folder"`
}

var JobsOpenTerminalInFolder = rpc.Call[fnd.BulkRequest[JobsOpenTerminalInFolderRequestItem], fnd.BulkResponse[OpenSessionWithProvider]]{
	BaseContext: jobNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "openTerminalInFolder",
}

type JobRenameRequest struct {
	Id       string `json:"id"`
	NewTitle string `json:"newTitle"`
}

var JobsRename = rpc.Call[fnd.BulkRequest[JobRenameRequest], util.Empty]{
	BaseContext: jobNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "rename",
}

type JobsSearchRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`
	Query        string              `json:"query"`

	JobFlags
}

var JobsSearch = rpc.Call[JobsSearchRequest, fnd.PageV2[Job]]{
	BaseContext: jobNamespace,
	Convention:  rpc.ConventionSearch,
	Roles:       rpc.RolesEndUser,
}

type JobsBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	JobFlags
}

var JobsBrowse = rpc.Call[JobsBrowseRequest, fnd.PageV2[Job]]{
	BaseContext: jobNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

type JobsRetrieveRequest struct {
	Id string
	JobFlags
}

var JobsRetrieve = rpc.Call[JobsRetrieveRequest, Job]{
	BaseContext: jobNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

var JobsUpdateAcl = rpc.Call[fnd.BulkRequest[UpdatedAcl], fnd.BulkResponse[util.Empty]]{
	BaseContext: jobNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateAcl",
}

var JobsRetrieveProducts = rpc.Call[util.Empty, SupportByProvider[JobSupport]]{
	BaseContext: jobNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
	Operation:   "products",
}

const JobsFollowEndpoint = "/api/jobs"

type JobLogMessage struct {
	Rank    int                 `json:"rank"`
	Stdout  util.Option[string] `json:"stdout"`
	Stderr  util.Option[string] `json:"stderr"`
	Channel util.Option[string] `json:"channel"`
}

type JobsFollowMessage struct {
	Updates    []JobUpdate            `json:"updates"`
	Log        []JobLogMessage        `json:"log"`
	NewStatus  util.Option[JobStatus] `json:"newStatus"`
	InitialJob util.Option[Job]       `json:"initialJob"`
}

// Job Control API
// =====================================================================================================================

const jobControlNamespace = "jobs/control"

type JobsControlRetrieveRequest struct {
	Id string `json:"id"`
	JobFlags
}

var JobsControlRetrieve = rpc.Call[JobsControlRetrieveRequest, Job]{
	BaseContext: jobControlNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesProvider,
}

type JobsControlBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	JobFlags
}

var JobsControlBrowse = rpc.Call[JobsControlBrowseRequest, fnd.PageV2[Job]]{
	BaseContext: jobControlNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesProvider,
}

var JobsControlRegister = rpc.Call[fnd.BulkRequest[ProviderRegisteredResource[JobSpecification]], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: jobControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesProvider,
	Operation:   "register",
}

var JobsControlAddUpdate = rpc.Call[fnd.BulkRequest[ResourceUpdateAndId[JobUpdate]], util.Empty]{
	BaseContext: jobControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesProvider,
	Operation:   "update",
}

type JobsControlBrowseSshKeysRequest struct {
	JobId       string `json:"jobId"`
	FilterOwner bool   `json:"owner"`
}

var JobsControlBrowseSshKeys = rpc.Call[JobsControlBrowseSshKeysRequest, fnd.PageV2[SshKey]]{
	Convention:  rpc.ConventionUpdate,
	BaseContext: jobControlNamespace,
	Operation:   "browseSshKeys",
	Roles:       rpc.RoleProvider,
}

type JobsLegacyCheckCreditsRequest struct {
	Id       string `json:"id"`
	ChargeId string `json:"chargeId"`
	Units    int    `json:"units"`
	Periods  int    `json:"periods"`
}

type JobsLegacyCheckCreditsResponse struct {
	InsufficientFunds []fnd.FindByStringId `json:"insufficientFunds"`
	DuplicateCharges  []fnd.FindByStringId `json:"duplicateCharges"`
}

var JobsControlChargeCredits = rpc.Call[fnd.BulkRequest[JobsLegacyCheckCreditsRequest], JobsLegacyCheckCreditsResponse]{
	BaseContext: jobControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "chargeCredits",
	Roles:       rpc.RoleProvider,
}

var JobsControlCheckCredits = rpc.Call[fnd.BulkRequest[JobsLegacyCheckCreditsRequest], JobsLegacyCheckCreditsResponse]{
	BaseContext: jobControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "checkCredits",
	Roles:       rpc.RoleProvider,
}

// Job Provider API
// =====================================================================================================================

var JobsProviderCreate = rpc.Call[fnd.BulkRequest[Job], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: jobProviderNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesPrivileged,
}

var JobsProviderRetrieveProducts = rpc.Call[util.Empty, fnd.BulkResponse[JobSupport]]{
	BaseContext: jobProviderNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesPrivileged,
	Operation:   "products",
}

var JobsProviderUpdateAcl = rpc.Call[fnd.BulkRequest[UpdatedAclWithResource[Job]], fnd.BulkResponse[util.Empty]]{
	BaseContext: jobProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "updateAcl",
}

type JobsProviderExtendRequestItem struct {
	Job           Job            `json:"job"`
	RequestedTime SimpleDuration `json:"requestedTime"`
}

var JobsProviderExtend = rpc.Call[fnd.BulkRequest[JobsProviderExtendRequestItem], fnd.BulkResponse[util.Empty]]{
	BaseContext: jobProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "extend",
}

var JobsProviderTerminate = rpc.Call[fnd.BulkRequest[Job], fnd.BulkResponse[util.Empty]]{
	BaseContext: jobProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "terminate",
}

type JobsProviderSuspendRequestItem struct {
	Job Job `json:"job"`
}

var JobsProviderSuspend = rpc.Call[fnd.BulkRequest[JobsProviderSuspendRequestItem], fnd.BulkResponse[util.Empty]]{
	BaseContext: jobProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "suspend",
}

type JobsProviderUnsuspendRequestItem struct {
	Job Job `json:"job"`
}

var JobsProviderUnsuspend = rpc.Call[fnd.BulkRequest[JobsProviderUnsuspendRequestItem], fnd.BulkResponse[util.Empty]]{
	BaseContext: jobProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "unsuspend",
}

type JobsProviderOpenInteractiveSessionRequestItem struct {
	Job         Job                    `json:"job"`
	Rank        int                    `json:"rank"`
	SessionType InteractiveSessionType `json:"sessionType"`
	Target      util.Option[string]    `json:"target"`
}

var JobsProviderOpenInteractiveSession = rpc.Call[fnd.BulkRequest[JobsProviderOpenInteractiveSessionRequestItem], fnd.BulkResponse[OpenSession]]{
	BaseContext: jobProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "interactiveSession",
}

type JobsProviderRequestDynamicParametersRequest struct {
	Owner       ResourceOwner `json:"owner"`
	Application Application   `json:"application"`
}

type JobsProviderRequestDynamicParametersResponse struct {
	Parameters []ApplicationParameter `json:"parameters"`
}

var JobsProviderRequestDynamicParameters = rpc.Call[JobsProviderRequestDynamicParametersRequest, JobsProviderRequestDynamicParametersResponse]{
	BaseContext: jobProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "requestDynamicParameters",
}

var JobsProviderOpenTerminalInFolder = rpc.Call[fnd.BulkRequest[JobsOpenTerminalInFolderRequestItem], fnd.BulkResponse[OpenSession]]{
	BaseContext: jobProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "openTerminalInFolder",
}

func JobsProviderFollowEndpoint(providerId string) string {
	return fmt.Sprintf("/ucloud/jobs.provider.%s/websocket", providerId)
}

type JobsProviderFollowRequest struct {
	Type string `json:"type"` // must be "init"
	Job  Job    `json:"job"`
}

/*
var JobsProviderRename = rpc.Call[fnd.BulkRequest[JobRenameRequest], fnd.BulkResponse[util.Empty]]{
	BaseContext: jobProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesService,
	Operation:   "rename",
}
*/
