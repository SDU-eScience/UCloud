package controller

import (
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var Tasks TaskService

type TaskService struct {
	OnResume func(id int) *util.HttpError
	OnCancel func(id int) *util.HttpError
	OnPause  func(id int) *util.HttpError
}

func initTasks() {
	if RunsUserCode() {
		fnd.TasksProviderPauseOrCancel.Handler(func(info rpc.RequestInfo, request fnd.TasksPauseOrCancelRequest) (util.Empty, *util.HttpError) {
			var fn func(id int) *util.HttpError = nil
			if request.RequestedState == fnd.TaskStateCancelled {
				fn = Tasks.OnCancel
			} else if request.RequestedState == fnd.TaskStateSuspended {
				fn = Tasks.OnPause
			} else if request.RequestedState == fnd.TaskStateRunning {
				fn = Tasks.OnResume
			}

			var err *util.HttpError
			if fn != nil {
				err = fn(request.Id)
			}

			return util.Empty{}, err
		})
	}
}
