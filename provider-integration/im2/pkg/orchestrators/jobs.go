package orchestrators

import (
	"ucloud.dk/pkg/apm"
	fnd "ucloud.dk/pkg/foundation"
)

type JsonObject string

type ExportedParametersRequest struct {
	Application       NameAndVersion       `json:"application"`
	Product           apm.ProductReference `json:"product"`
	Name              string               `json:"name,omitempty"`
	Replicas          int                  `json:"replicas"`
	Parameters        JsonObject           `json:"parameters"`
	Resources         []JsonObject         `json:"resources"`
	TimeAllocation    SimpleDuration       `json:"timeAllocation,omitempty"`
	ResolvedProduct   JsonObject           `json:"resolvedProduct,omitempty"`
	ResolvedSupport   JsonObject           `json:"resolvedSupport,omitempty"`
	AllowDuplicateJob bool                 `json:"allowDuplicateJob"`
	SshEnabled        bool
}

type ExportedParameters struct {
	SiteVersion       int                         `json:"siteVersion"`
	Request           ExportedParametersRequest   `json:"request"`
	ResolvedResources ExportedParametersResources `json:"ResolvedResources"`
	MachineType       JsonObject                  `json:"machineType"`
}

type ExportedParametersResources struct {
	Ingress map[string]Ingress `json:"Ingress"`
}

type JobState string

const (
	JobStateInQueue   JobState = "IN_QUEUE"
	JobStateRunning   JobState = "RUNNING"
	JobStateCanceling JobState = "CANCELING"
	JobStateSuccess   JobState = "SUCCESS"
	JobStateFailure   JobState = "FAILURE"
	JobStateExpired   JobState = "EXPIRED"
	JobStateSuspended JobState = "SUSPENDED"
)

func (jobState *JobState) isFinal() bool {
	switch *jobState {
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
	JobParametersJson   ExportedParameters `json:"jobParametersJson,omitempty"`
	StartedAt           fnd.Timestamp      `json:"startedAt,omitempty"`
	ExpiresAt           fnd.Timestamp      `json:"expiresAt,omitempty"`
	ResolvedApplication Application        `json:"resolvedApplication,omitempty"`
	//override var resolvedSupport: ResolvedSupport<Product.Compute, ComputeSupport>? = null,
	//override var resolvedProduct: Product.Compute? = null,
	AllowRestart bool `json:"allowRestart"`
}

//) : ResourceStatus<Product.Compute, ComputeSupport>

type JobUpdate struct {
	State                  JobState `json:"state,omitempty"`
	OutputFolder           string   `json:"outputFolder,omitempty"`
	Status                 string   `json:"status,omitempty"`
	ExpectedState          JobState `json:"expectedState"`
	ExpectedDifferentState bool     `json:"expectedDifferentState,omitempty"`
	NewTimeAllocation      int64    `json:"newTimeAllocation"`
	AllowRestart           bool     `json:"allowRestart"`
	NewMounts              []string `json:"newMounts"`
}

/*  init {
        if (allowRestart == true) {
            when (state) {
                JobState.SUCCESS,
                JobState.FAILURE,
                JobState.SUSPENDED -> {
                    // This is OK
                }

                else -> {
                    throw RPCException(
                        "Cannot set `allowRestart` with a state of $state!",
                        HttpStatusCode.BadRequest
                    )
                }
            }
        }
    }
}*/

type JobSpecification struct {
	ResourceSpecification
	Application       NameAndVersion               `json:"application"`
	Name              string                       `json:"name,omitempty"`
	Replicas          int                          `json:"replicas"`
	AllowDuplicateJob bool                         `json:"allowDuplicateJob"`
	Parameters        map[string]AppParameterValue `json:"parameters,omitempty"`
	Resources         []AppParameterValue          `json:"resources,omitempty"`
	TimeAllocation    SimpleDuration               `json:"timeAllocation,omitempty"`
	OpenedFile        string                       `json:"openedFile,omitempty"`
	RestartOnExit     bool                         `json:"restartOnExit,omitempty"`
	SshEnabled        bool                         `json:"sshEnabled,omitempty"`
}

/*    init {
        if (name != null && !name.matches(nameRegex)) {
            throw RPCException(
                "Provided job name is invalid. It cannot contain any special characters ($name).",
                HttpStatusCode.BadRequest
            )
        }

        if (name != null && name.length > 200) {
            throw RPCException("Provided job name is too long", HttpStatusCode.BadRequest)
        }
    }

    companion object {
        private val nameRegex = Regex("""[\w ():_-]+""")
    }
}*/

type ComputeProductReference apm.ProductReference

type JobOutput struct {
	OutputFolder string `json:"ouputfolder,omitempty"`
}

//typealias JobsExtendRequest = BulkRequest<JobsExtendRequestItem>
//typealias JobsExtendResponse = BulkResponse<Unit?>

type JobsExtendRequestItem struct {
	JobId         string         `json:"jobId"`
	RequestedTime SimpleDuration `json:"requestedTime"`
}

//typealias JobsSuspendRequest = BulkRequest<JobsSuspendRequestItem>
//typealias JobsSuspendResponse = BulkResponse<Unit?>
//typealias JobsSuspendRequestItem = FindByStringId

//typealias JobsUnsuspendRequest = BulkRequest<JobsUnsuspendRequestItem>
//typealias JobsUnsuspendResponse = BulkResponse<Unit?>
//typealias JobsUnsuspendRequestItem = FindByStringId

func (job *Job) getParameterValues(ofType AppParameterValueType) []AppParameterValue {
	var result []AppParameterValue

	for _, resource := range job.Specification.Resources {
		if resource.Type == ofType {
			result = append(result, resource)
		}
	}

	return result
}

//typealias JobsOpenInteractiveSessionRequest = BulkRequest<JobsOpenInteractiveSessionRequestItem>

//typealias JobsOpenInteractiveSessionResponse = BulkResponse<OpenSessionWithProvider?>

type OpenSessionWithProvider struct {
	ProviderDomain string      `json:"providerDomain"`
	ProviderId     string      `json:"providerId"`
	Session        OpenSession `json:"session"`
}

type OpenSession struct {
	Type              OpenSessionType
	JobId             string
	Rank              int
	DomainOverride    string ``
	RedirectClientTo  string
	SessionIdentifier string
	Url               string
	Password          string
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
