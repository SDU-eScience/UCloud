package filesystem

import (
	"fmt"
	"sync/atomic"
	"time"

	ctrl "ucloud.dk/pkg/im/controller"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/util"
)

type TaskProcessingResult struct {
	Error error

	// If Error is not nil then AllowReschedule can be used to reschedule the task at a later point in time. This value
	// is always ignored if Error is nil.
	AllowReschedule bool
}

type TaskType string

const (
	FileTaskTypeCopy            TaskType = "copy"
	FileTaskTransfer            TaskType = "file_transfer"
	FileTaskTransferDestination TaskType = "file_transfer_destination"
)

type TaskInfo struct {
	Id                 int
	UCloudTaskId       util.Option[int]
	BackoffSeconds     int
	Done               atomic.Bool
	Status             atomic.Pointer[fnd.TaskStatus]
	UserRequestedState atomic.Pointer[fnd.TaskState]
	Paused             bool
	TaskInfoSpecification
}

type TaskInfoSpecification struct {
	Type              TaskType
	CreatedAt         fnd.Timestamp
	UCloudSource      util.Option[string]
	UCloudDestination util.Option[string]
	ConflictPolicy    orc.WriteConflictPolicy
	MoreInfo          util.Option[string]
	HasUCloudTask     bool
	Icon              string
	UCloudUsername    string
}

func (spec *TaskInfoSpecification) DefaultStatus() fnd.TaskStatus {
	sourceSimple := util.FileName(spec.UCloudSource.Value)
	destSimple := util.FileName(spec.UCloudDestination.Value)

	progress := ""
	operation := ""
	switch spec.Type {
	case FileTaskTransfer:
		operation = fmt.Sprintf("Transferring %s to another provider", sourceSimple)

	case FileTaskTypeCopy:
		operation = fmt.Sprintf("Copying %s to %s", sourceSimple, destSimple)
	}

	return fnd.TaskStatus{
		State:              fnd.TaskStateRunning,
		Title:              util.OptValue(operation),
		Progress:           util.OptValue(progress),
		ProgressPercentage: util.OptValue(0.0),
	}
}

type TaskStatusUpdate struct {
	Id            int
	NewOperation  util.Option[string]
	NewBody       util.Option[string]
	NewProgress   util.Option[string]
	NewPercentage util.Option[float64] // 0 - 100 both inclusive
	NewState      util.Option[fnd.TaskState]
}

var taskFrontendQueue chan *TaskInfo

func InitTaskSystem() {
	type userStateTransition struct {
		Id    int
		State fnd.TaskState
	}
	taskStateTransition := make(chan userStateTransition)
	taskFrontendQueue = make(chan *TaskInfo, 100)
	taskBackendQueue := make(chan *TaskInfo, 100)

	doTransition := func(id int, state fnd.TaskState) *util.HttpError {
		taskStateTransition <- userStateTransition{
			Id:    id,
			State: state,
		}
		return nil
	}

	ctrl.Tasks = ctrl.TaskService{
		OnCancel: func(id int) *util.HttpError {
			return doTransition(id, fnd.TaskStateCancelled)
		},
		OnPause: func(id int) *util.HttpError {
			return doTransition(id, fnd.TaskStateSuspended)
		},
		OnResume: func(id int) *util.HttpError {
			return doTransition(id, fnd.TaskStateRunning)
		},
	}

	go func() {
		knownTasks := map[int]*TaskInfo{}
		knownStatus := map[int]fnd.TaskStatus{}

		ticker := time.NewTicker(250 * time.Millisecond)

		for util.IsAlive {
			select {
			case transition := <-taskStateTransition:
				if transition.State == fnd.TaskStateRunning {
					tasks := ListAllActiveTasks()
					for _, task := range tasks {
						if task.UCloudTaskId.Present && task.UCloudTaskId.Value == transition.Id {
							_ = PostTaskStatus(task.UCloudUsername, TaskStatusUpdate{
								Id:            task.Id,
								NewBody:       util.OptValue(""),
								NewProgress:   util.OptValue("Task is being resumed..."),
								NewPercentage: util.OptValue(-1.0),
								NewState:      util.OptValue(fnd.TaskStateInQueue),
							})
							taskFrontendQueue <- task
							break
						}
					}
				} else {
					var foundTask *TaskInfo = nil
					for _, task := range knownTasks {
						if task.HasUCloudTask && task.UCloudTaskId.Value == transition.Id {
							foundTask = task
							break
						}
					}

					if foundTask != nil {
						status := foundTask.Status.Load()
						_ = PostTaskStatus(foundTask.UCloudUsername, TaskStatusUpdate{
							Id:            foundTask.Id,
							NewBody:       status.Body,
							NewProgress:   status.Progress,
							NewPercentage: status.ProgressPercentage,
							NewState:      util.OptValue(transition.State),
						})

						foundTask.UserRequestedState.Store(&transition.State)
					} else {
						// TODO Somehow send a terminate message. This is a bit more complicated since we do not have
						//   a way of checking permissions. Probably some IPC which terminates the task if it is
						//   unknown.
					}
				}

			case task := <-taskFrontendQueue:
				if task.HasUCloudTask {
					knownTasks[task.Id] = task
					load := task.Status.Load()
					if load == nil {
						defaultStatus := task.DefaultStatus()
						load = &defaultStatus
					}
					knownStatus[task.Id] = *load
				}
				taskBackendQueue <- task

			case <-ticker.C:
				for id, task := range knownTasks {
					if task.Done.Load() {
						delete(knownTasks, id)
						delete(knownStatus, id)
					} else {
						newStatusPtr := task.Status.Load()
						if newStatusPtr == nil {
							defaultStatus := task.DefaultStatus()
							newStatusPtr = &defaultStatus
						}
						newStatus := *newStatusPtr

						oldStatus, _ := knownStatus[id]
						if newStatus != oldStatus {
							err := PostTaskStatus(task.UCloudUsername, TaskStatusUpdate{
								Id:            id,
								NewOperation:  newStatus.Title,
								NewProgress:   newStatus.Progress,
								NewPercentage: newStatus.ProgressPercentage,
								NewBody:       newStatus.Body,
								NewState:      util.OptValue(newStatus.State),
							})

							if err == nil {
								knownStatus[id] = newStatus
							}
						}
					}
				}
			}
		}
	}()

	for i := 0; i < 10; i++ {
		go func() {
			for util.IsAlive {
				task := <-taskBackendQueue

				result := TaskProcessingResult{}

				switch task.Type {
				case FileTaskTypeCopy:
					result = processCopyTask(task)

				case FileTaskTransfer:
					result = processTransferTask(task)

				// The following tasks are handled externally and not through a process task. These must register
				// that they are done through a manual PostTaskStatus.
				case FileTaskTransferDestination:
					continue
				}

				wasRescheduled := false
				reschedule := func() {
					wasRescheduled = true

					go func() {
						seconds := task.BackoffSeconds
						if seconds <= 0 {
							seconds = 4
						}
						task.BackoffSeconds = seconds * 2
						if task.BackoffSeconds >= 60*5 {
							task.BackoffSeconds = 60 * 5
						}

						// NOTE(Dan): Rescheduling skips the frontend queue since the task is still active,
						// just sleeping for a bit.
						for seconds > 0 {
							currentStatus := task.Status.Load()
							task.Status.Store(&fnd.TaskStatus{
								State:              fnd.TaskStateInQueue,
								Title:              currentStatus.Title,
								Progress:           util.OptValue(fmt.Sprintf("Retrying in %v seconds", seconds)),
								Body:               util.OptValue(""),
								ProgressPercentage: util.OptValue(-1.0),
							})

							time.Sleep(time.Duration(1) * time.Second)
							seconds--
						}

						taskBackendQueue <- task
					}()
				}

				requestedState := task.UserRequestedState.Load()
				if requestedState != nil {
					if result.Error != nil && !result.AllowReschedule {
						// Ignore that the user requested a state transition, force an error
						_ = PostTaskStatus(task.UCloudUsername, TaskStatusUpdate{
							Id:            task.Id,
							NewBody:       util.OptValue(""),
							NewProgress:   util.OptValue(result.Error.Error()),
							NewPercentage: util.OptValue(100.0),
							NewState:      util.OptValue(fnd.TaskStateFailure),
						})

						task.Done.Store(true)
					} else {
						// NOTE(Dan): This intentionally ignores the error if we are pausing the task.
						msg := "Task has been paused"
						if *requestedState == fnd.TaskStateCancelled {
							msg = "Task has been cancelled"
							if result.Error != nil {
								msg = result.Error.Error()
							}
						}

						_ = PostTaskStatus(task.UCloudUsername, TaskStatusUpdate{
							Id:            task.Id,
							NewBody:       util.OptValue(""),
							NewProgress:   util.OptValue(msg),
							NewPercentage: util.OptValue(0.0),
							NewState:      util.OptValue(*requestedState),
						})

						task.Done.Store(true)
					}
				} else {
					shouldPost := true
					operation := task.DefaultStatus().Title
					progress := "Task has been completed."
					taskState := fnd.TaskStateSuccess
					if result.Error != nil {
						if result.AllowReschedule {
							shouldPost = false
							reschedule()
						}

						taskState = fnd.TaskStateFailure
						progress = result.Error.Error()
					}

					if shouldPost {
						err := PostTaskStatus(task.UCloudUsername, TaskStatusUpdate{
							Id:            task.Id,
							NewProgress:   util.OptValue(progress),
							NewPercentage: util.OptValue(100.0),
							NewState:      util.OptValue(taskState),
							NewBody:       util.OptValue(""),
							NewOperation:  operation,
						})

						if err != nil {
							log.Warn("Failed to post final status update for task: %s", err)
							reschedule()
						}
					}

					if !wasRescheduled {
						task.Done.Store(true)
					}
				}
			}
		}()
	}

	tasks := ListAllActiveTasks()
	if len(tasks) > 0 {
		log.Info("Restarting %v tasks", len(tasks))
		for _, task := range tasks {
			if task.Paused {
				continue
			}
			taskFrontendQueue <- task
		}
	}
}

func RegisterTask(spec TaskInfoSpecification) *util.HttpError {
	ucloudTaskId := util.OptNone[int]()
	if spec.HasUCloudTask {
		flags := fnd.TaskFlags(0)
		switch spec.Type {
		case FileTaskTransfer:
			flags |= fnd.TaskFlagCanCancel | fnd.TaskFlagCanPause

		default:
		}

		status := spec.DefaultStatus()

		id, err := fnd.TasksCreate.Invoke(
			fnd.TasksCreateRequest{
				User:      spec.UCloudUsername,
				Title:     status.Title,
				Progress:  status.Progress,
				CanPause:  flags&fnd.TaskFlagCanPause != 0,
				CanCancel: flags&fnd.TaskFlagCanCancel != 0,
				Icon:      util.OptStringIfNotEmpty(spec.Icon),
			},
		)

		if err != nil {
			return util.ServerHttpError("Could not create task in UCloud: %s", err)
		}

		ucloudTaskId.Set(id.Id)
	}

	taskId, ok := db.NewTx2[int, bool](func(tx *db.Transaction) (int, bool) {
		result, ok := db.Get[struct{ Id int }](
			tx,
			`
				insert into k8s.tasks(ucloud_task_id, ucloud_username, task_type, ucloud_source,
					ucloud_destination, conflict_policy, more_info) 
				values (:ucloud_task, :owner_username, :type, :source, :dest, :conflict, :info)
				returning id
			`,
			db.Params{
				"type":           spec.Type,
				"ucloud_task":    ucloudTaskId.GetPtrOrNil(),
				"owner_username": spec.UCloudUsername,
				"source":         spec.UCloudSource.GetPtrOrNil(),
				"dest":           spec.UCloudDestination.GetPtrOrNil(),
				"conflict":       spec.ConflictPolicy,
				"info":           spec.MoreInfo.GetPtrOrNil(),
			},
		)

		if !ok {
			return 0, false
		}

		return result.Id, true
	})

	if !ok {
		return util.ServerHttpError("Failed to register task")
	}

	t := &TaskInfo{
		Id:                    taskId,
		UCloudTaskId:          ucloudTaskId,
		TaskInfoSpecification: spec,
	}

	defaultStatus := t.DefaultStatus()
	t.Status.Store(&defaultStatus)
	taskFrontendQueue <- t

	return nil
}

func ListAllActiveTasks() []*TaskInfo {
	return ListActiveTasks("")
}

func ListActiveTasks(username string) []*TaskInfo {
	result := db.NewTx(func(tx *db.Transaction) []*TaskInfo {
		rows := db.Select[struct {
			Id                int
			TaskType          string
			UCloudSource      string
			UCloudDestination string
			ConflictPolicy    string
			MoreInfo          string
			UCloudTaskId      int64
			Paused            bool
			UCloudUsername    string
		}](
			tx,
			`
				select
					id,
					task_type,
					coalesce(ucloud_source, '') as ucloud_source,
					coalesce(ucloud_destination, '') as ucloud_destination,
					coalesce(conflict_policy, '') as conflict_policy,
					coalesce(more_info, '') as more_info,
					coalesce(ucloud_task_id, -1) as ucloud_task_id,
					paused,
					ucloud_username
				from
					k8s.tasks
				where
					:username = ''
					or ucloud_username = :username
			`,
			db.Params{
				"username": username,
			},
		)

		var result []*TaskInfo
		for _, row := range rows {
			info := &TaskInfo{
				Id:           row.Id,
				UCloudTaskId: util.OptValue(int(row.UCloudTaskId)),
				Paused:       row.Paused,
				TaskInfoSpecification: TaskInfoSpecification{
					Type:           TaskType(row.TaskType),
					ConflictPolicy: orc.WriteConflictPolicy(row.ConflictPolicy),
					HasUCloudTask:  row.UCloudTaskId != -1,
					UCloudUsername: row.UCloudUsername,
				},
			}

			if row.UCloudSource != "" {
				info.UCloudSource.Set(row.UCloudSource)
			}

			if row.UCloudDestination != "" {
				info.UCloudDestination.Set(row.UCloudDestination)
			}

			if row.MoreInfo != "" {
				info.MoreInfo.Set(row.MoreInfo)
			}

			result = append(result, info)
		}

		return result
	})

	return result
}

func PostTaskStatus(username string, status TaskStatusUpdate) *util.HttpError {
	ucloudTaskId, ok := db.NewTx2(func(tx *db.Transaction) (int, bool) {
		row, ok := db.Get[struct {
			UCloudTaskId int
		}](
			tx,
			`
				select
					coalesce(ucloud_task_id, -1) as ucloud_task_id
				from
					k8s.tasks
				where
					id = :id
					and ucloud_username = :username
			`,
			db.Params{
				"id":       status.Id,
				"username": username,
			},
		)

		return row.UCloudTaskId, ok
	})

	if !ok {
		return util.UserHttpError("unknown task supplied")
	}

	// TODO: Change this once the API is more stable

	newState := fnd.TaskStateRunning
	if status.NewState.Present {
		newState = status.NewState.Value
	}

	if ucloudTaskId >= 0 {
		_, err := fnd.TasksPostStatus.Invoke(fnd.TasksPostStatusRequest{Update: fnd.TasksPostStatusRequestUpdate{
			Id: ucloudTaskId,
			NewStatus: fnd.TaskStatus{
				State:              newState,
				Title:              status.NewOperation,
				Progress:           status.NewProgress,
				ProgressPercentage: status.NewPercentage,
				Body:               status.NewBody,
			},
		}})

		if err != nil {
			return err
		}
	}

	if newState == fnd.TaskStateSuspended {
		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`update k8s.tasks set paused = true where id = :id`,
				db.Params{
					"id": status.Id,
				},
			)
		})
	} else if newState == fnd.TaskStateSuccess || newState == fnd.TaskStateFailure || newState == fnd.TaskStateCancelled {
		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`delete from k8s.tasks where id = :id`,
				db.Params{
					"id": status.Id,
				},
			)
		})
	} else {
		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`update k8s.tasks set paused = false where id = :id`,
				db.Params{
					"id": status.Id,
				},
			)
		})
	}

	return nil
}
