package main

import (
	"fmt"
	"net/http"

	"ucloud.dk/shared/pkg/rpc"
	ucxdemo "ucloud.dk/shared/pkg/ucx/demo"
	"ucloud.dk/shared/pkg/util"
)

func main() {
	upstreamServer := &rpc.Server{
		Mux: http.NewServeMux(),
	}

	streamCall := rpc.Call[util.Empty, util.Empty]{
		BaseContext: "/",
		Convention:  rpc.ConventionWebSocket,
		Roles:       rpc.RolesPublic,
	}

	streamCall.HandlerEx(upstreamServer, func(info rpc.RequestInfo, request util.Empty) (util.Empty, *util.HttpError) {
		return ucxdemo.RunDemoSession(info)
	})

	s := &http.Server{
		Addr:    fmt.Sprintf(":%v", 8080),
		Handler: upstreamServer.Mux,
	}
	_ = s.ListenAndServe()
}
