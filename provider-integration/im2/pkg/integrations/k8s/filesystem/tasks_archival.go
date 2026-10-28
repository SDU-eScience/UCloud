package filesystem

import (
	"encoding/json"
	"fmt"
	"net/http"
	"os"

	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const taskEnvPayload = "UCLOUD_TASK_PAYLOAD"

// ArchivalCopy is a single source -> destination copy of an archival request.
type ArchivalCopy struct {
	Source      string `json:"source"`
	Destination string `json:"destination"`
}

// archivalTaskPayload is passed to the task-processor job through taskEnvPayload.
type archivalTaskPayload struct {
	DatasetId string         `json:"datasetId"`
	Copies    []ArchivalCopy `json:"copies"`
}

func archivalCopy(actor rpc.Actor, request orc.FilesProviderArchivalRequest) *util.HttpError {
	copies := make([]ArchivalCopy, 0, len(request.Data))
	for _, item := range request.Data {
		copies = append(copies, ArchivalCopy{
			Source:      item.SourcePath,
			Destination: item.DestinationPath,
		})
	}

	return ArchivalSubmit(actor, request.DatasetId, copies)
}

// ArchivalSubmit creates the single UCloud task for an archival request and submits one background
// job which performs every copy. Call this from the existing `archivalCopy` entry point.
func ArchivalSubmit(actor rpc.Actor, datasetId string, copies []ArchivalCopy) *util.HttpError {
	if len(copies) == 0 {
		return util.UserHttpError("No files were supplied for archival")
	}

	mounts := []TaskMount{}
	seen := map[string]bool{}
	addMount := func(ucloudPath string) {
		parent := util.Parent(ucloudPath)
		if parent == "/" {
			parent = ucloudPath
		}
		if !seen[parent] {
			seen[parent] = true
			mounts = append(mounts, TaskMount{UCloudPath: parent})
		}
	}
	for _, pair := range copies {
		addMount(pair.Source)
		addMount(pair.Destination)
	}

	spec := TaskSpec{
		Type:      TaskTypeArchival,
		DatasetId: datasetId,
		Copies:    copies,
		Mounts:    mounts,
		Meta:      util.OptValue(json.RawMessage(fmt.Sprintf(`{"datasetId":%q}`, datasetId))),
	}
	spec.CreationState.Username = actor.Username
	spec.CreationState.Title = "Archival"
	spec.CreationState.Icon = "heroArchiveBox"

	return TaskSubmit(spec)
}

func task2ProcessArchival(_ TaskSpec) *util.HttpError {
	if os.Getenv(taskEnvId) == "" {
		log.Fatal("This code can only run inside of a task.")
		return util.HttpErr(http.StatusInternalServerError, "internal error")
	}

	var payload archivalTaskPayload
	if err := json.Unmarshal([]byte(os.Getenv(taskEnvPayload)), &payload); err != nil {
		return util.HttpErr(http.StatusBadRequest, "malformed archival payload")
	}

	total := len(payload.Copies)
	if total == 0 {
		return nil
	}

	completed := 0

	postAggregated := func(state fnd.TaskState, message string) {
		taskProcessorPostUpdate(fnd.TaskStatus{
			State:              state,
			Title:              util.OptValue("Archival"),
			Body:               util.OptValue(fmt.Sprintf("%d / %d copies completed", completed, total)),
			Progress:           util.OptValue(message),
			ProgressPercentage: util.OptValue(float64(completed) / float64(total) * 100),
		})
	}

	postAggregated(fnd.TaskStateRunning, "")

	for i, pair := range payload.Copies {
		postAggregated(fnd.TaskStateRunning, fmt.Sprintf("Copying %s (%d of %d)", util.FileName(pair.Source), i+1, total))

		copyErr := task2ProcessCopy(TaskSpec{
			Source:      pair.Source,
			Destination: pair.Destination,
		})
		if copyErr != nil {
			return util.HttpErr(copyErr.StatusCode, "copy %d of %d failed: %s", i+1, total, copyErr.Why)
		}

		completed++
		postAggregated(fnd.TaskStateRunning, "")
	}

	return nil
}
