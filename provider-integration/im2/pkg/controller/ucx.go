package controller

import (
	"net/http"

	ws "github.com/gorilla/websocket"
	fnd "ucloud.dk/shared/pkg/foundation"
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
	OnStackDeleted             func(request orcapi.StacksProviderDeleteRequest) *util.HttpError
	OnStackSpawnDeclaredJob    func(request orcapi.StacksProviderSpawnDeclaredJobRequest) (fnd.FindByStringId, *util.HttpError)
}

func initUcxApplications() {
	if RunsServerCode() {
		orcapi.StacksProviderDelete.Handler(func(info rpc.RequestInfo, request orcapi.StacksProviderDeleteRequest) (util.Empty, *util.HttpError) {
			if UcxApplications.OnStackDeleted == nil {
				return util.Empty{}, util.HttpErr(http.StatusBadRequest, "stack deletion is not supported by this provider")
			}
			return util.Empty{}, UcxApplications.OnStackDeleted(request)
		})
		orcapi.StacksProviderSpawnDeclaredJob.Handler(func(info rpc.RequestInfo, request orcapi.StacksProviderSpawnDeclaredJobRequest) (fnd.FindByStringId, *util.HttpError) {
			if UcxApplications.OnStackSpawnDeclaredJob == nil {
				return fnd.FindByStringId{}, util.HttpErr(http.StatusBadRequest, "declared job spawning is not supported by this provider")
			}
			return UcxApplications.OnStackSpawnDeclaredJob(request)
		})
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
