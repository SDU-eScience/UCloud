package orchestrators

import (
	c "ucloud.dk/shared/pkg/client"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/util"
)

type Task struct {
	TaskId        uint64            `json:"taskId"`
	CreatedAt     fnd.Timestamp     `json:"createdAt"`
	ModifiedAt    fnd.Timestamp     `json:"modifiedAt"`
	CreatedBy     string            `json:"createdBy"`
	Provider      string            `json:"provider"`
	Status        TaskStatus        `json:"status"`
	Specification TaskSpecification `json:"specification"`
	Icon          string            `json:"icon"`
}

type TaskSpecification struct {
	CanPause  bool `json:"canPause"`
	CanCancel bool `json:"canCancel"`
}

type TaskStatus struct {
	State              TaskState            `json:"state"`
	Title              util.Option[string]  `json:"title"`
	Body               util.Option[string]  `json:"body"`
	Progress           util.Option[string]  `json:"progress"`
	ProgressPercentage util.Option[float64] `json:"progressPercentage"`
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

const tasksBaseContext = "/api/tasks/"
const tasksNamespace = "tasks."

type TaskFlags int

const (
	TaskFlagCanPause TaskFlags = 1 << iota
	TaskFlagCanCancel
)

func CreateTask(username string, operation, progress util.Option[string], icon string, flags TaskFlags) (uint64, error) {
	type req struct {
		User      string              `json:"user"`
		Operation util.Option[string] `json:"operation"`
		Progress  util.Option[string] `json:"progress"`
		CanPause  bool                `json:"canPause"`
		CanCancel bool                `json:"canCancel"`
		Icon      string              `json:"icon"`
	}

	res, err := c.ApiCreate[Task](
		tasksNamespace+"create",
		tasksBaseContext,
		"",
		req{
			User:      username,
			Operation: operation,
			Progress:  progress,
			Icon:      icon,
			CanPause:  flags&TaskFlagCanPause != 0,
			CanCancel: flags&TaskFlagCanCancel != 0,
		},
	)

	if err != nil {
		return 0, err
	}

	return res.TaskId, nil
}

func PostTaskStatus(id uint64, status TaskStatus) error {
	type req struct {
		Update struct {
			TaskId    uint64     `json:"taskId"`
			NewStatus TaskStatus `json:"newStatus"`
		} `json:"update"`
	}

	r := req{}
	r.Update.TaskId = id
	r.Update.NewStatus = status

	_, err := c.ApiUpdate[util.Empty](
		tasksNamespace+"postStatus",
		tasksBaseContext,
		"postStatus",
		r,
	)

	return err
}
