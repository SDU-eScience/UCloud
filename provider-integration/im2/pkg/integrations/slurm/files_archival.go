package slurm

import (
	"encoding/json"
	"fmt"
	"time"

	fnd "ucloud.dk/shared/pkg/foundation"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type archivalCopyPair struct {
	Source      string `json:"source"`
	Destination string `json:"destination"`
}

// archivalTaskData is persisted in MoreInfo for archival tasks (the slurm.tasks more_info column).
type archivalTaskData struct {
	DatasetId string             `json:"datasetId"`
	Copies    []archivalCopyPair `json:"copies"`
}

func archival(_ rpc.Actor, request orc.FilesProviderArchivalRequest) *util.HttpError {
	copies := make([]archivalCopyPair, 0, len(request.Data))
	for _, item := range request.Data {
		copies = append(copies, archivalCopyPair{
			Source:      item.SourcePath,
			Destination: item.DestinationPath,
		})
	}

	return RegisterArchivalTask(request.DatasetId, copies)
}

// RegisterArchivalTask creates the single UCloud task covering an entire archival request. The task
// is created for the requesting user with Meta {"datasetId": ...} and canCancel enabled. Call this
// from the existing `archival` entry point (wired into InitializeFiles) with the source/destination
// pairs of the request.
func RegisterArchivalTask(datasetId string, copies []archivalCopyPair) *util.HttpError {
	if len(copies) == 0 {
		return util.UserHttpError("No files were supplied for archival")
	}

	data := archivalTaskData{DatasetId: datasetId, Copies: copies}
	raw, err := json.Marshal(data)
	if err != nil {
		return util.ServerHttpError("Could not encode archival request")
	}

	return RegisterTask(TaskInfoSpecification{
		Type:          FileTaskTypeArchival,
		CreatedAt:     fnd.Timestamp(time.Now()),
		MoreInfo:      util.OptValue(string(raw)),
		Meta:          util.OptValue(json.RawMessage(fmt.Sprintf(`{"datasetId":%q}`, datasetId))),
		HasUCloudTask: true,
		Icon:          "heroArchiveBox",
	})
}

func processArchivalTask(task *TaskInfo) TaskProcessingResult {
	var data archivalTaskData
	if err := json.Unmarshal([]byte(task.MoreInfo.Value), &data); err != nil {
		return TaskProcessingResult{
			Error:           fmt.Errorf("Malformed archival request"),
			AllowReschedule: false,
		}
	}

	total := len(data.Copies)
	if total == 0 {
		return TaskProcessingResult{}
	}

	completed := 0

	postAggregated := func(state fnd.TaskState, progress string) {
		task.Status.Store(&fnd.TaskStatus{
			State:              state,
			Title:              util.OptValue("Copying data into dataset"),
			Body:               util.OptValue(fmt.Sprintf("%d / %d copies completed", completed, total)),
			Progress:           util.OptValue(progress),
			ProgressPercentage: util.OptValue(float64(completed) / float64(total) * 100),
		})
	}

	postAggregated(fnd.TaskStateRunning, "")

	for i, pair := range data.Copies {
		if task.UserRequestedState.Load() != nil {
			// The user paused/cancelled the task. Returning without an error makes the task system
			// post the user-requested state.
			return TaskProcessingResult{}
		}

		sourcePath, ok1 := UCloudToInternal(pair.Source)
		destPath, ok2 := UCloudToInternal(pair.Destination)
		if !ok1 || !ok2 {
			return TaskProcessingResult{
				Error:           fmt.Errorf("Unable to resolve %q or %q", pair.Source, pair.Destination),
				AllowReschedule: false,
			}
		}

		index := i
		err := copyIntoDestination(sourcePath, destPath, func(status fnd.TaskStatus) {
			aggregate := float64(index) / float64(total) * 100
			if enginePct := status.ProgressPercentage.GetOrDefault(-1.0); enginePct >= 0 {
				aggregate = (float64(index) + enginePct/100) / float64(total) * 100
			}

			task.Status.Store(&fnd.TaskStatus{
				State:              fnd.TaskStateRunning,
				Title:              util.OptValue("Copying data into dataset"),
				Body:               util.OptValue(fmt.Sprintf("%d / %d copies completed — %s", index, total, status.Body.GetOrDefault(""))),
				Progress:           status.Progress,
				ProgressPercentage: util.OptValue(aggregate),
			})
		})

		if err != nil {
			return TaskProcessingResult{
				Error:           fmt.Errorf("Copy %d of %d failed: %s", i+1, total, err),
				AllowReschedule: false,
			}
		}

		completed++
		postAggregated(fnd.TaskStateRunning, "")
	}

	return TaskProcessingResult{}
}
