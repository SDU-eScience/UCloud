package tasks

import (
	"errors"
	"fmt"
	"os"

	fnd "ucloud.dk/pkg/foundation"
	"ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/slurm"
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

func RegisterTask(task FileTask) {

	taskFolder := findAndInitTaskFolder(task.DriveId)

	/*
	   // NOTE(Dan): We first find the task folder to make sure that the arguments are valid

	   	val response = pluginContext.ipcClient.sendRequest(TaskIpc.register, TaskSpecification(task.title))
	   	task.id = response.id
	   	task.timestamp = Time.now()
	   	task.collectionId = task.collectionId

	   	val file = NativeFile.open(
	   	    taskFolder.path + "/" + TASK_PREFIX + task.id + TASK_SUFFIX,
	   	    readOnly = false,
	   	    truncateIfNeeded = true
	   	)

	   	file.writeText(
	   	    defaultMapper.encodeToString(PosixTask.serializer(), task),
	   	    autoClose = true
	   	)
	*/
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
