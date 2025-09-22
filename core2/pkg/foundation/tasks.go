package foundation

import (
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initTasks() {
	followCall := rpc.Call[util.Empty, util.Empty]{
		BaseContext: "tasks",
		Convention:  rpc.ConventionWebSocket,
	}

	followCall.Handler(func(info rpc.RequestInfo, request util.Empty) (util.Empty, *util.HttpError) {
		conn := info.WebSocket
		for {
			_, _, err := conn.ReadMessage()
			if err != nil {
				break
			}
		}
		util.SilentClose(conn)

		return util.Empty{}, nil
	})
}
