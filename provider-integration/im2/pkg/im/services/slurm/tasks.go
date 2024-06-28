package slurm

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"os"
	"strings"
	"time"

	fnd "ucloud.dk/pkg/foundation"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

/**
 * The Posix Task system is responsible for managing a set of file-system related tasks. A task is started when a
 * command comes from the user. At this point, the task system evaluates the task and decides to either complete it
 * immediately or push it to the background. If a task is pushed to the background, it will automatically be restarted
 * in the case of a restart or failure. Tasks which are completed immediately will not do this.
 *
 * The task system depends on the accompanying posix collection plugin. At the root of each drive, this plugin will
 * store a hidden folder (even if `filterHiddenFiles=false`). This folder contains each file per task which is a JSON
 * serialized version of the task.
 */

const taskFolder string = ".ucloud-tasks"
const taskPrefix string = "task_"
const taskSuffix string = ".json"

type FileTaskType string

const (
	FileTaskTypeMoveToTrash FileTaskType = "move_to_trash"
	FileTaskTypeEmptyTrash  FileTaskType = "empty_trash"
	FileTaskTypeMove        FileTaskType = "move"
	FileTaskTypeCopy        FileTaskType = "copy"
)

type FileTask struct {
	Type      FileTaskType
	Title     string
	DriveId   string
	Id        string
	Timestamp fnd.Timestamp
	//FileTaskMoveToTrash
	//FileTaskEmptyTrash
	//FileTaskMove
	FileTaskCopy
}

/*type FileTaskMoveToTrash struct {
	Drive   orc.Drive
	Request []FilesProviderTrashRequestItem
}

type FileTaskEmptyTrash struct {
	Request FilesProviderEmptyTrashRequestItem
}

type FileTaskMove struct {
	Request FilesProviderMoveRequestItem
}*/

type FileTaskCopy struct {
	CopyRequest ctrl.CopyFileRequest
}

var registerCall = ipc.NewCall[FileTask, string]("task.register")
var markAsCompleteCall = ipc.NewCall[string, util.Empty]("task.markAsComplete")

func InitTaskSystem() {
	log.Info("Initializing FileTasks")
	if cfg.Mode == cfg.ServerModeServer {
		registerCall.Handler(func(r *ipc.Request[FileTask]) ipc.Response[string] {
			drive, ok := RetrieveDrive(r.Payload.DriveId)

			if !ok {
				return ipc.Response[string]{
					StatusCode: http.StatusNotFound,
					Payload:    "",
				}
			}

			walletOwner := orc.ResourceOwnerToWalletOwner(drive.Resource)

			if !ctrl.BelongsToWorkspace(walletOwner, r.Uid) {
				return ipc.Response[string]{
					StatusCode: http.StatusNotFound,
					Payload:    "",
				}
			}

			// TODO(Brian) Do something

			return ipc.Response[string]{
				StatusCode: http.StatusOK,
				Payload:    util.RandomToken(16),
			}
		})

		markAsCompleteCall.Handler(func(r *ipc.Request[string]) ipc.Response[util.Empty] {
			log.Info("markAsComplete got: %s", r.Payload)
			return ipc.Response[util.Empty]{
				StatusCode: http.StatusOK,
				Payload:    util.Empty{},
			}
		})
	}
}

func RegisterTask(task FileTask) {

	taskFolder := findAndInitTaskFolder(task.DriveId)

	// (Brian) Register task through ipc
	resp, _ := registerCall.Invoke(task)
	task.Id = resp
	task.Timestamp = fnd.Timestamp(time.Now())

	file, err := os.OpenFile(
		taskFolder+"/"+taskPrefix+task.Id+taskSuffix,
		os.O_CREATE+os.O_RDWR, os.FileMode(0770),
	)
	if err != nil {
		log.Error("Failed to create task file: %v", err)
		return
	}
	defer file.Close()

	taskString, _ := json.Marshal(task)
	_, err = file.WriteString(string(taskString))

	if err != nil {
		log.Error("Failed to write to task file: %v", err)
		return
	}
}

func MarkTaskAsComplete(driveId string, taskId string) {
	filePath := findAndInitTaskFolder(driveId) + "/" + taskPrefix + taskId + taskSuffix
	if _, err := markAsCompleteCall.Invoke(taskId); err != nil {
		log.Error("Failed to mark task as complete %s: %v", taskId, err)
	}

	os.Remove(filePath)
}

func RetrieveCurrentTasks(driveId string) []FileTask {
	result := []FileTask{}

	taskFolder := findAndInitTaskFolder(driveId)

	dirEntries, _ := os.ReadDir(taskFolder)

	for _, entry := range dirEntries {
		if !strings.HasPrefix(entry.Name(), taskPrefix) || !strings.HasSuffix(entry.Name(), taskSuffix) {
			continue
		}

		file, _ := os.ReadFile(taskFolder + "/" + entry.Name())
		task := FileTask{}
		err := json.Unmarshal(file, &task)

		if err != nil {
			log.Error("Unable to unmarshal task %s: %v", entry.Name(), err)
			continue
		}

		result = append(result, task)
	}

	return result
}

func findAndInitTaskFolder(driveId string) string {
	folder, _ := UCloudToInternal(fmt.Sprintf("/%s/%s", driveId, taskFolder))

	if _, err := os.Stat(folder); errors.Is(err, os.ErrNotExist) {
		if err := os.Mkdir(folder, os.FileMode(0770)); err != nil {
			log.Error("Unable to create folder: %v", err)
		}
	}

	return folder
}
