package foundation

import (
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// Model
// =====================================================================================================================

type Task struct {
	Id            int                 `json:"taskId"`
	CreatedAt     Timestamp           `json:"createdAt"`
	ModifiedAt    Timestamp           `json:"modifiedAt"`
	CreatedBy     string              `json:"createdBy"`
	Provider      string              `json:"provider"`
	Status        TaskStatus          `json:"status"`
	Specification TaskSpecification   `json:"specification"`
	Icon          util.Option[string] `json:"icon"`
}

type TaskState string

const (
	TaskStateInQueue   TaskState = "IN_QUEUE"
	TaskStateSuspended TaskState = "SUSPENDED"
	TaskStateRunning   TaskState = "RUNNING"
	TaskStateCancelled TaskState = "CANCELLED"
	TaskStateFailure   TaskState = "FAILURE"
	TaskStateSuccess   TaskState = "SUCCESS"
)

var TaskStateOptions = []TaskState{
	TaskStateInQueue,
	TaskStateSuspended,
	TaskStateRunning,
	TaskStateCancelled,
	TaskStateFailure,
	TaskStateSuccess,
}

type TaskFlags int

const (
	TaskFlagCanPause TaskFlags = 1 << iota
	TaskFlagCanCancel
)

type TaskStatus struct {
	State              TaskState            `json:"state"`
	Title              util.Option[string]  `json:"title"`
	Body               util.Option[string]  `json:"body"`
	Progress           util.Option[string]  `json:"progress"`
	ProgressPercentage util.Option[float64] `json:"progressPercentage"`
}

type TaskSpecification struct {
	CanPause  bool `json:"canPause"`
	CanCancel bool `json:"canCancel"`
}

// API
// =====================================================================================================================
// NOTE(Dan): In practice, the parts which speak to the providers live in the orchestration layer, but all parts of the
// API is listed here for simplicity.

const tasksNamespace = "tasks"

type TasksBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`
}

var TasksBrowse = rpc.Call[TasksBrowseRequest, PageV2[Task]]{
	BaseContext: tasksNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

var TasksRetrieve = rpc.Call[FindByIntId, Task]{
	BaseContext: tasksNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

type TasksCreateRequest struct {
	User      string              `json:"user"`
	Title     util.Option[string] `json:"title"`
	Body      util.Option[string] `json:"body"`
	Progress  util.Option[string] `json:"progress"`
	CanPause  bool                `json:"canPause"`
	CanCancel bool                `json:"canCancel"`
	Icon      util.Option[string] `json:"icon"`
}

var TasksCreate = rpc.Call[TasksCreateRequest, Task]{
	BaseContext: tasksNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesProvider,
}

type TasksPostStatusRequest struct {
	Update TasksPostStatusRequestUpdate `json:"update"`
}

type TasksPostStatusRequestUpdate struct {
	Id         int        `json:"taskId"`
	ModifiedAt Timestamp  `json:"modifiedAt"`
	NewStatus  TaskStatus `json:"newStatus"`
}

var TasksPostStatus = rpc.Call[TasksPostStatusRequest, util.Empty]{
	BaseContext: tasksNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "postStatus",
	Roles:       rpc.RolesProvider,
}

var TasksMarkAsComplete = rpc.Call[FindByIntId, util.Empty]{
	BaseContext: tasksNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "markAsComplete",
	Roles:       rpc.RolesProvider,
}

type TasksPauseOrCancelRequest struct {
	Id             int       `json:"id"`
	RequestedState TaskState `json:"requestedState"`
}

var TasksPauseOrCancel = rpc.Call[TasksPauseOrCancelRequest, util.Empty]{
	BaseContext: tasksNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "pauseOrCancel",
	Roles:       rpc.RolesEndUser,
}

var TasksListen = rpc.Call[util.Empty, util.Empty]{
	BaseContext: tasksNamespace,
	Convention:  rpc.ConventionWebSocket,
}

var TasksProviderPauseOrCancel = rpc.Call[TasksPauseOrCancelRequest, util.Empty]{
	BaseContext: "ucloud/" + rpc.ProviderPlaceholder + "/tasks",
	Convention:  rpc.ConventionUpdate,
	Operation:   "pauseOrCancel",
	Roles:       rpc.RolesService,
}
