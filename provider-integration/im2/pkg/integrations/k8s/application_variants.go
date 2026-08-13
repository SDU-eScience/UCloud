package k8s

import (
	"encoding/json"
	"fmt"
	"time"

	"ucloud.dk/pkg/integrations/k8s/registry"
	fnd "ucloud.dk/shared/pkg/foundation"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initApplicationVariants() {
	orc.ApplicationVariantsProviderValidateImage.Handler(func(info rpc.RequestInfo, request orc.ApplicationVariantValidateImageRequest) (orc.ApplicationVariantValidateImageResponse, *util.HttpError) {
		return registry.ValidateApplicationVariantImage(request.Owner, request.Image, request.RequireProjectAccess)
	})

	orc.JobsProviderCreateApplicationVariant.Handler(func(info rpc.RequestInfo, request orc.JobsProviderCreateApplicationVariantRequest) (fnd.Task, *util.HttpError) {
		meta, _ := json.Marshal(struct {
			JobId       string             `json:"jobId"`
			Application orc.NameAndVersion `json:"application"`
		}{
			JobId: request.Job.Id,
			Application: orc.NameAndVersion{
				Name:    fmt.Sprintf("variant-%d", request.VariantId),
				Version: fmt.Sprintf("r%d", request.Revision),
			},
		})
		task, err := fnd.TasksCreate.Invoke(fnd.TasksCreateRequest{
			User:      request.RequestedBy,
			Title:     util.OptValue("Saving flavor"),
			Progress:  util.OptValue("Getting ready to save your flavor"),
			CanCancel: false,
			Icon:      util.OptValue("heroSquare3Stack3D"),
			Meta:      util.OptValue(json.RawMessage(meta)),
		})
		if err != nil {
			return fnd.Task{}, err
		}
		_, err = startContainerSnapshotAsync(
			request.Job.Id,
			request.Image,
			request.Rank,
			request.VariantId,
			task.Id,
			request.BaseApplication,
			request.RequestedBy,
		)
		if err != nil {
			postContainerSnapshotTask(task.Id, fnd.TaskStateFailure, "We could not save your flavor. "+err.Why, util.OptNone[string]())
			_, _ = orc.ApplicationVariantsControlCompleteSnapshot.Invoke(orc.ApplicationVariantCompleteSnapshotRequest{
				VariantId: request.VariantId, TaskId: task.Id, Failure: util.OptValue(err.Why),
			})
			return fnd.Task{}, err
		}
		postContainerSnapshotTask(task.Id, fnd.TaskStateRunning, "Saving your flavor", util.OptNone[string]())
		return task, nil
	})
}

func postContainerSnapshotTask(taskId int, state fnd.TaskState, progress string, body util.Option[string]) {
	status := fnd.TaskStatus{
		State:    state,
		Title:    util.OptValue("Saving flavor"),
		Body:     body,
		Progress: util.OptValue(progress),
	}
	if state == fnd.TaskStateSuccess {
		status.ProgressPercentage.Set(100)
	}
	_, _ = fnd.TasksPostStatus.Invoke(fnd.TasksPostStatusRequest{Update: fnd.TasksPostStatusRequestUpdate{
		Id: taskId, ModifiedAt: fnd.Timestamp(time.Now()), NewStatus: status,
	}})
}
