package controller

import (
	"fmt"
	"net/http"
	cfg "ucloud.dk/pkg/im/config"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

var Tasks TaskService

type TaskService struct {
	OnResume func(id uint64) error
	OnCancel func(id uint64) error
	OnPause  func(id uint64) error
}

func controllerTasks(mux *http.ServeMux) {
	if RunsUserCode() {
		baseContext := fmt.Sprintf("/ucloud/%v/tasks/", cfg.Provider.Id)

		type pauseReq struct {
			Id             uint64        `json:"id"`
			RequestedState orc.TaskState `json:"requestedState"`
		}

		mux.HandleFunc(baseContext+"pauseOrCancel", HttpUpdateHandler[pauseReq](
			0,
			func(w http.ResponseWriter, r *http.Request, request pauseReq) {
				var fn func(id uint64) error = nil
				if request.RequestedState == orc.TaskStateCancelled {
					fn = Tasks.OnCancel
				} else if request.RequestedState == orc.TaskStateSuspended {
					fn = Tasks.OnPause
				} else if request.RequestedState == orc.TaskStateRunning {
					fn = Tasks.OnResume
				}

				var err error
				if fn != nil {
					err = fn(request.Id)
				}

				sendResponseOrError(w, util.Empty{}, err)
			},
		))
	}
}
