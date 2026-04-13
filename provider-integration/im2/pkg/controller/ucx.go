package controller

import (
	"net/http"

	ws "github.com/gorilla/websocket"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/util"
)

var UcxApplications UcxApplicationService

type UcxApplicationService struct {
	OnConnect                  func(conn *ws.Conn)
	OnConnectJob               func(conn *ws.Conn)
	InferencePlaygroundFactory func(owner orcapi.ResourceOwner, sessionId string) ucx.Application
}

func initUcxApplications() {
	if RunsServerCode() {
		orcapi.AppUcxConnectProvider.Handler(func(info rpc.RequestInfo, request util.Empty) (util.Empty, *util.HttpError) {
			handler := UcxApplications.OnConnect
			if handler == nil {
				return util.Empty{}, util.HttpErr(http.StatusForbidden, "this operation is not supported by the provider")
			} else {
				handler(info.WebSocket)
				return util.Empty{}, nil
			}
		})

		orcapi.AppUcxConnectJobProvider.Handler(func(info rpc.RequestInfo, request util.Empty) (util.Empty, *util.HttpError) {
			handler := UcxApplications.OnConnectJob
			if handler == nil {
				return util.Empty{}, util.HttpErr(http.StatusForbidden, "this operation is not supported by the provider")
			} else {
				handler(info.WebSocket)
				return util.Empty{}, nil
			}
		})
	}
}
