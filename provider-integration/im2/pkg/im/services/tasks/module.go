package tasks

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"strings"

	fnd "ucloud.dk/pkg/foundation"
	"ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/slurm"
	"ucloud.dk/pkg/log"
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
	Request controller.CopyFileRequest
}

type FileTaskSpecification struct {
	title string
}

func RegisterTask(task FileTask) {

	taskFolder := findAndInitTaskFolder(task.DriveId)

	// TODO(Brian) Register task through ipc
	//response := ipc.NewCall[FileTaskSpecification, string]("tasks.Register")

	file, err := os.OpenFile(
		taskFolder+"/"+taskPrefix+task.Id+taskSuffix,
		os.O_CREATE+os.O_RDWR, os.FileMode(int(770)),
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

	// TODO(Brian)
	//pluginContext.ipcClient.sendRequest(TaskIpc.markAsComplete, FindByStringId(taskId))

	os.Remove(filePath)
}

func RetrieveCurrentTasks(driveId string) []FileTask {
	result := []FileTask{}

	taskFolder := findAndInitTaskFolder(driveId)

	dirEntries, _ := os.ReadDir(taskFolder)

	for _, entry := range dirEntries {
		if strings.HasPrefix(entry.Name(), taskPrefix) && strings.HasSuffix(entry.Name(), taskSuffix) {
			file, _ := os.ReadFile(taskFolder + "/" + entry.Name())
			task := FileTask{}
			err := json.Unmarshal(file, &task)

			if err != nil {
				log.Error("Unable to unmarshal task %s: %v", entry.Name(), err)
				continue
			}

			result = append(result, task)
		}
	}

	return result
}

func findAndInitTaskFolder(driveId string) string {
	folder, _ := slurm.UCloudToInternal(fmt.Sprintf("/%s/%s", driveId, taskFolder))

	if _, err := os.Stat(folder); errors.Is(err, os.ErrNotExist) {
		os.Mkdir(
			folder,
			os.FileMode(int(770)),
		)
	}

	return folder
}
