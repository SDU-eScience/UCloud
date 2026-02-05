package orchestrators

import (
	"encoding/json"
	"fmt"
	"slices"
	"strings"

	"ucloud.dk/gonja/v2/exec"
	"ucloud.dk/pkg/im/controller"
	"ucloud.dk/shared/pkg/apm"
	c "ucloud.dk/shared/pkg/client"
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
	Name              string               `json:"name,omitempty"`
	Replicas          int                  `json:"replicas"`
	Parameters        json.RawMessage      `json:"parameters"`
	Resources         []json.RawMessage    `json:"resources"`
	TimeAllocation    SimpleDuration       `json:"timeAllocation,omitempty"`
	ResolvedProduct   json.RawMessage      `json:"resolvedProduct,omitempty"`
	ResolvedSupport   json.RawMessage      `json:"resolvedSupport,omitempty"`
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
	State               JobState
	JobParametersJson   ExportedParameters         `json:"jobParametersJson,omitempty"`
	StartedAt           util.Option[fnd.Timestamp] `json:"startedAt,omitempty"`
	ExpiresAt           util.Option[fnd.Timestamp] `json:"expiresAt,omitempty"`
	ResolvedApplication Application                `json:"resolvedApplication,omitempty"`
	ResolvedProduct     apm.ProductV2              `json:"resolvedProduct,omitempty"`
	AllowRestart        bool                       `json:"allowRestart"`
}

type JobUpdate struct {
	State                  util.Option[JobState] `json:"state"`
	OutputFolder           util.Option[string]   `json:"outputFolder"`
	Status                 util.Option[string]   `json:"status"`
	ExpectedState          util.Option[JobState] `json:"expectedState"`
	ExpectedDifferentState util.Option[bool]     `json:"expectedDifferentState"`
	NewTimeAllocation      util.Option[int64]    `json:"newTimeAllocation"`
	AllowRestart           util.Option[bool]     `json:"allowRestart"`
	NewMounts              util.Option[[]string] `json:"newMounts"`
	Timestamp              fnd.Timestamp         `json:"timestamp"`
}

type JobSpecification struct {
	ResourceSpecification
	Application       NameAndVersion               `json:"application"`
	Name              string                       `json:"name,omitempty"`
	Replicas          int                          `json:"replicas"`
	AllowDuplicateJob bool                         `json:"allowDuplicateJob"`
	Parameters        map[string]AppParameterValue `json:"parameters"`
	Resources         []AppParameterValue          `json:"resources"`
	TimeAllocation    util.Option[SimpleDuration]  `json:"timeAllocation,omitempty"`
	OpenedFile        string                       `json:"openedFile,omitempty"`
	RestartOnExit     bool                         `json:"restartOnExit,omitempty"`
	SshEnabled        bool                         `json:"sshEnabled,omitempty"`
}

type ComputeProductReference apm.ProductReference

type JobOutput struct {
	OutputFolder util.Option[string] `json:"ouputfolder"`
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

func genericDefaultParse[T any](input json.RawMessage) (T, bool) {
	var asDirect T
	err := json.Unmarshal(input, &asDirect)
	if err == nil {
		return asDirect, true
	}

	var asWrapped struct{ Value T }
	err = json.Unmarshal(input, &asWrapped)
	if err == nil {
		return asWrapped.Value, true
	} else {
		return asDirect, false
	}
}

func readDefaultValue(t ApplicationParameterType, input json.RawMessage) (AppParameterValue, bool) {
	if string(input) == "null" {
		return AppParameterValue{}, false
	}

	switch t {
	case ApplicationParameterTypeBoolean:
		v, ok := genericDefaultParse[bool](input)
		if ok {
			return AppParameterValueBoolean(v), true
		}

	case ApplicationParameterTypeInteger:
		v, ok := genericDefaultParse[int64](input)
		if ok {
			return AppParameterValueInteger(v), true
		}

	case ApplicationParameterTypeFloatingPoint:
		v, ok := genericDefaultParse[float64](input)
		if ok {
			return AppParameterValueFloatingPoint(v), true
		}

	case ApplicationParameterTypeText, ApplicationParameterTypeTextArea, ApplicationParameterTypeEnumeration:
		v, ok := genericDefaultParse[string](input)
		if ok {
			return AppParameterValueText(v), true
		}
	}

	return AppParameterValue{}, false
}

func ReadParameterValuesFromJob(job *Job, application *ApplicationInvocationDescription) map[string]ParamAndValue {
	parameters := make(map[string]ParamAndValue)

	allParameters := application.Parameters
	for _, value := range job.Specification.Parameters {
		if value.Type == AppParameterValueTypeWorkflow {
			inputs := value.Specification.Inputs
			for _, input := range inputs {
				allParameters = append(allParameters, input)
			}
		}
	}

	for _, param := range allParameters {
		if param.DefaultValue == nil {
			continue
		}

		value, ok := readDefaultValue(param.Type, param.DefaultValue)
		if !ok {
			continue
		}

		if !VerifyParameterType(&param, &value) {
			continue
		}

		parameters[param.Name] = ParamAndValue{
			Parameter: param,
			Value:     value,
		}
	}

	for paramName, value := range job.Specification.Parameters {
		if strings.HasPrefix(paramName, "_injected_") {
			parameters[paramName] = ParamAndValue{
				Value: value,
			}
		} else {
			var parameter util.Option[ApplicationParameter]
			for _, jobParam := range allParameters {
				if jobParam.Name == paramName {
					parameter.Set(jobParam)
					break
				}
			}

			if !parameter.IsSet() {
				continue
			}

			param := parameter.Get()
			if !VerifyParameterType(&param, &value) {
				continue
			}

			parameters[paramName] = ParamAndValue{
				Parameter: param,
				Value:     value,
			}
		}
	}
	return parameters
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
}

type UniversalBackendSupport struct {
	Enabled       bool `json:"enabled,omitempty"`
	Web           bool `json:"web,omitempty"`
	Vnc           bool `json:"vnc,omitempty"`
	Logs          bool `json:"logs,omitempty"`
	Terminal      bool `json:"terminal,omitempty"`
	Peers         bool `json:"peers,omitempty"`
	TimeExtension bool `json:"timeExtension,omitempty"`
}

// API
// =====================================================================================================================

const jobsCtrlNamespace = "jobs.control."
const jobsCtrlContext = "/api/jobs/control/"

type BrowseJobsFlags struct {
	FilterApplication  util.Option[string]   `json:"filterApplication"`
	FilterState        util.Option[JobState] `json:"filterState"`
	IncludeParameters  bool                  `json:"includeParameters"`
	IncludeApplication bool                  `json:"includeApplication"`
	IncludeProduct     bool                  `json:"includeProduct"`
	IncludeUpdates     bool                  `json:"includeUpdates"`
}

func RetrieveJob(jobId string, flags BrowseJobsFlags) (Job, error) {
	return c.ApiRetrieve[Job](
		jobsCtrlNamespace+"retrieve",
		jobsCtrlContext,
		"",
		append([]string{"id", jobId}, c.StructToParameters(flags)...),
	)
}

func BrowseJobs(next string, flags BrowseJobsFlags) (fnd.PageV2[Job], error) {
	return c.ApiBrowse[fnd.PageV2[Job]](
		jobsCtrlNamespace+"browse",
		jobsCtrlContext,
		"",
		append([]string{"next", next}, c.StructToParameters(flags)...),
	)
}

func UpdateJobs(request fnd.BulkRequest[ResourceUpdateAndId[JobUpdate]]) error {
	_, err := c.ApiUpdate[util.Empty](
		jobsCtrlNamespace+"update",
		jobsCtrlContext,
		"update",
		request,
	)
	return err
}

func RegisterJobs(request fnd.BulkRequest[ProviderRegisteredResource[JobSpecification]]) (fnd.BulkResponse[fnd.FindByStringId], error) {
	return c.ApiRegister[fnd.BulkResponse[fnd.FindByStringId]](
		jobsCtrlNamespace,
		jobsCtrlContext,
		"register",
		request,
	)
}

func BrowseSshKeys(jobId string) ([]SshKey, error) {
	type req struct {
		JobId        string `json:"jobId"`
		ItemsPerPage int    `json:"itemsPerPage"`
	}

	page, err := c.ApiUpdate[fnd.PageV2[SshKey]](
		jobsCtrlNamespace+"browseSshKeys",
		jobsCtrlContext,
		"browseSshKeys",
		req{
			JobId:        jobId,
			ItemsPerPage: 250,
		},
	)

	if err != nil {
		return nil, err
	}

	return page.Items, nil
}
