package orchestrator

import (
	"net/http"
	"ucloud.dk/core/pkg/coreutil"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initTasks() {
	// NOTE(Dan): The vast majority of the task system is implemented in the foundation layer. Only the parts which
	// need to speak (outgoing) to the provider is implemented in this layer.

	fndapi.TasksPauseOrCancel.Handler(func(info rpc.RequestInfo, request fndapi.TasksPauseOrCancelRequest) (util.Empty, *util.HttpError) {
		owner, ok := coreutil.TaskRetrieveOwner(request.Id)
		if !ok || owner.User != info.Actor.Username {
			return util.Empty{}, util.HttpErr(http.StatusNotFound, "unknown task requested")
		}

		_, err := InvokeProvider(owner.Provider, fndapi.TasksProviderPauseOrCancel, request, ProviderCallOpts{
			Username: util.OptValue(info.Actor.Username),
			Reason:   util.OptValue("user initiated pause/cancel"),
		})

		return util.Empty{}, err
	})
}
