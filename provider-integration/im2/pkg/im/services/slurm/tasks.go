package slurm

import (
	"fmt"
	"net/http"
	"os"
	"time"
	db "ucloud.dk/pkg/database"

	fnd "ucloud.dk/pkg/foundation"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

type TaskProcessingResult struct {
	Error error

	// If Error is not nil then AllowReschedule can be used to reschedule the task at a later point in time. This value
	// is always ignored if Error is nil.
	AllowReschedule bool
}

type TaskType string

const (
	FileTaskTypeMoveToTrash TaskType = "move_to_trash"
	FileTaskTypeEmptyTrash  TaskType = "empty_trash"
	FileTaskTypeMove        TaskType = "move"
	FileTaskTypeCopy        TaskType = "copy"
	FileTaskTransfer        TaskType = "file_transfer"
)

type TaskInfo struct {
	Id           uint64
	UCloudTaskId util.Option[uint64]
	Uid          uint32
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
}

type TaskStatusUpdate struct {
	Id            uint64
	NewOperation  util.Option[string]
	NewProgress   util.Option[string]
	NewPercentage util.Option[float64] // 0 - 100 both inclusive
	NewState      util.Option[orc.TaskState]
}

var (
	registerTaskCall    = ipc.NewCall[TaskInfoSpecification, uint64]("tasks.register")
	listActiveTasksCall = ipc.NewCall[util.Empty, []TaskInfo]("tasks.listActive")
	postTaskStatusCall  = ipc.NewCall[TaskStatusUpdate, util.Empty]("tasks.postStatus")
)

var taskQueue chan TaskInfo

func InitTaskSystem() {
	if cfg.Mode == cfg.ServerModeServer {
		registerTaskCall.Handler(func(r *ipc.Request[TaskInfoSpecification]) ipc.Response[uint64] {
			ucloudUsername, ok := ctrl.MapLocalToUCloud(r.Uid)
			if !ok {
				return ipc.Response[uint64]{
					StatusCode: http.StatusForbidden,
				}
			}

			spec := r.Payload

			ucloudTaskId := util.OptNone[uint64]()
			if spec.HasUCloudTask {
				flags := orc.TaskFlags(0)
				switch spec.Type {
				case FileTaskTransfer:
					flags |= orc.TaskFlagCanCancel | orc.TaskFlagCanPause

				default:
				}

				sourceSimple := util.FileName(spec.UCloudSource.Value)
				destSimple := util.FileName(spec.UCloudDestination.Value)

				progress := ""
				operation := ""
				switch spec.Type {
				case FileTaskTransfer:
					operation = fmt.Sprintf("Transferring %s to another provider", sourceSimple)
				case FileTaskTypeCopy:
					operation = fmt.Sprintf("Copying %s to %s", sourceSimple, destSimple)

				case FileTaskTypeEmptyTrash:
					operation = "Emptying trash"

				case FileTaskTypeMove:
					operation = "Moving files"

				case FileTaskTypeMoveToTrash:
					operation = "Trashing files"
				}

				id, err := orc.CreateTask(
					ucloudUsername,
					util.OptValue(operation),
					util.OptValue(progress),
					flags,
				)

				if err != nil {
					return ipc.Response[uint64]{
						StatusCode:   http.StatusBadGateway,
						ErrorMessage: fmt.Sprintf("Could not create task in UCloud: %s", err),
					}
				}

				ucloudTaskId.Set(id)
			}

			taskId, ok := db.NewTx2[uint64, bool](func(tx *db.Transaction) (uint64, bool) {
				result, ok := db.Get[struct{ Id uint64 }](
					tx,
					`
						insert into slurm.tasks(ucloud_task_id, owner_uid, task_type, ucloud_source,
							ucloud_destination, conflict_policy, more_info) 
						values (:ucloud_task, :uid, :type, :source, :dest, :conflict, :info)
						returning id
				    `,
					db.Params{
						"type":        spec.Type,
						"ucloud_task": ucloudTaskId.GetPtrOrNil(),
						"uid":         r.Uid,
						"source":      spec.UCloudSource.GetPtrOrNil(),
						"dest":        spec.UCloudDestination.GetPtrOrNil(),
						"conflict":    spec.ConflictPolicy,
						"info":        spec.MoreInfo.GetPtrOrNil(),
					},
				)

				if !ok {
					return 0, false
				}

				return result.Id, true
			})

			if !ok {
				return ipc.Response[uint64]{
					StatusCode:   http.StatusInternalServerError,
					ErrorMessage: "Failed to register task",
				}
			}

			return ipc.Response[uint64]{
				StatusCode: http.StatusOK,
				Payload:    taskId,
			}
		})

		postTaskStatusCall.Handler(func(r *ipc.Request[TaskStatusUpdate]) ipc.Response[util.Empty] {
			ucloudTaskId, ok := db.NewTx2(func(tx *db.Transaction) (uint64, bool) {
				row, ok := db.Get[struct {
					UCloudTaskId uint64
				}](
					tx,
					`
						select
							ucloud_task_id
						from
							slurm.tasks
						where
							id = :id
							and owner_uid = :uid
							and ucloud_task_id is not null
				    `,
					db.Params{
						"id":  r.Payload.Id,
						"uid": r.Uid,
					},
				)

				return row.UCloudTaskId, ok
			})

			if !ok {
				return ipc.Response[util.Empty]{
					StatusCode:   http.StatusNotFound,
					ErrorMessage: "Unknown task supplied!",
				}
			}

			// TODO: Change this once the API is more stable

			newState := orc.TaskStateRunning
			if r.Payload.NewState.Present {
				newState = r.Payload.NewState.Value
			}

			err := orc.PostTaskStatus(ucloudTaskId, orc.TaskStatus{
				State:     newState,
				Operation: r.Payload.NewOperation.Value,
				Progress:  r.Payload.NewProgress.Value,
			})

			if err != nil {
				return ipc.Response[util.Empty]{
					StatusCode:   http.StatusBadGateway,
					ErrorMessage: err.Error(),
				}
			} else {
				if newState == orc.TaskStateSuccess || newState == orc.TaskStateFailure || newState == orc.TaskStateCancelled {
					db.NewTx0(func(tx *db.Transaction) {
						db.Exec(
							tx,
							`delete from slurm.tasks where id = :id`,
							db.Params{
								"id": r.Payload.Id,
							},
						)
					})
				}

				return ipc.Response[util.Empty]{
					StatusCode: http.StatusOK,
				}
			}
		})

		listActiveTasksCall.Handler(func(r *ipc.Request[util.Empty]) ipc.Response[[]TaskInfo] {
			uid := r.Uid

			result := db.NewTx(func(tx *db.Transaction) []TaskInfo {
				rows := db.Select[struct {
					Id                uint64
					TaskType          string
					UCloudSource      string
					UCloudDestination string
					ConflictPolicy    string
					MoreInfo          string
				}](
					tx,
					`
						select
							id,
							task_type,
							coalesce(ucloud_source, '') as ucloud_source,
							coalesce(ucloud_destination, '') as ucloud_destination,
							coalesce(conflict_policy, '') as conflict_policy,
							coalesce(more_info, '') as more_info
						from
							slurm.tasks
						where
							owner_uid = :uid
				    `,
					db.Params{
						"uid": uid,
					},
				)

				var result []TaskInfo
				for _, row := range rows {
					info := TaskInfo{
						Id:           row.Id,
						UCloudTaskId: util.Option[uint64]{},
						Uid:          uid,
						TaskInfoSpecification: TaskInfoSpecification{
							Type:           TaskType(row.TaskType),
							ConflictPolicy: orc.WriteConflictPolicy(row.ConflictPolicy),
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

			return ipc.Response[[]TaskInfo]{
				StatusCode: http.StatusOK,
				Payload:    result,
			}
		})

		// Launch user instances which have pending jobs
		usersWithPendingJobs := db.NewTx(func(tx *db.Transaction) []uint32 {
			rows := db.Select[struct{ OwnerUid uint32 }](
				tx,
				`
					select distinct owner_uid
					from slurm.tasks
			    `,
				db.Params{},
			)

			var result []uint32
			for _, row := range rows {
				result = append(result, row.OwnerUid)
			}
			return result
		})

		for _, uid := range usersWithPendingJobs {
			err := ctrl.LaunchUserInstance(uid)
			if err != nil {
				log.Warn("Failed to launch user instance for %v while attempting to restart tasks! Error: %s", uid, err)
			}
		}
	} else if cfg.Mode == cfg.ServerModeUser {
		taskQueue = make(chan TaskInfo)

		for i := 0; i < 10; i++ {
			go func() {
				for util.IsAlive {
					task := <-taskQueue

					result := TaskProcessingResult{}

					switch task.Type {
					case FileTaskTypeMove:
						result = processMoveTask(&task)

					case FileTaskTypeMoveToTrash:
						result = processMoveToTrash(&task)

					case FileTaskTypeEmptyTrash:
						result = processEmptyTrash(&task)

					case FileTaskTypeCopy:
						result = processCopyTask(&task)

					case FileTaskTransfer:
						result = processTransferTask(&task)
					}

					reschedule := func() {
						go func() {
							time.Sleep(10 * time.Second)
							taskQueue <- task
						}()
					}

					shouldPost := true
					progress := "Task has been completed."
					taskState := orc.TaskStateSuccess
					if result.Error != nil {
						if result.AllowReschedule {
							shouldPost = false
							reschedule()
						}

						taskState = orc.TaskStateFailure
						progress = result.Error.Error()
					}

					if shouldPost {
						_, err := postTaskStatusCall.Invoke(TaskStatusUpdate{
							Id:            task.Id,
							NewProgress:   util.OptValue(progress),
							NewPercentage: util.OptValue(100.0),
							NewState:      util.OptValue(taskState),
						})

						if err != nil {
							log.Warn("Failed to post final status update for task: %s", err)
							reschedule()
						}
					}
				}
			}()
		}

		tasks, err := listActiveTasksCall.Invoke(util.EmptyValue)
		if err != nil {
			log.Warn("Failed to get a list of active background tasks: %s", err)
		} else {
			for _, task := range tasks {
				taskQueue <- task
			}
		}
	}
}

func RegisterTask(task TaskInfoSpecification) error {
	id, err := registerTaskCall.Invoke(task)
	if err != nil {
		return err
	}

	taskQueue <- TaskInfo{
		Id:                    id,
		UCloudTaskId:          util.OptNone[uint64](),
		Uid:                   uint32(os.Getuid()),
		TaskInfoSpecification: task,
	}

	return nil
}
