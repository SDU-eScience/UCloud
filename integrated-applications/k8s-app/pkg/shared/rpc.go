package shared

import (
	"context"
	"os"

	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	"ucloud.dk/shared/pkg/ucx"
)

const (
	ModeCreator   = "creator"
	ModeDashboard = "dashboard"
	ModeRecovery  = "recovery"
)

func RegisterRpcHandlers(app ucx.Application, mode string) {
	session := *app.Session()
	if session == nil {
		return
	}

	k8sapi.Ping.Handler(session, func(ctx context.Context, request k8sapi.PingRequest) (k8sapi.PingResponse, error) {
		return k8sapi.PingResponse{
			AppName:    os.Getenv("UCLOUD_UCX_APP_NAME"),
			AppVersion: os.Getenv("UCLOUD_UCX_APP_VERSION"),
			Mode:       mode,
			Headless:   ucx.AppHeadless(app),
			Message:    request.Message,
		}, nil
	})
}
