package shared

import (
	"context"
	"os"

	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	"ucloud.dk/shared/pkg/ucx"
)

const (
	ModeCreator   = k8sapi.AppModeCreator
	ModeDashboard = k8sapi.AppModeDashboard
	ModeRecovery  = k8sapi.AppModeRecovery
)

func RegisterRpcHandlers(app ucx.Application, mode string) {
	session := *app.Session()

	k8sapi.Ping.Handler(session, func(_ context.Context, request k8sapi.PingRequest) (k8sapi.PingResponse, error) {
		return k8sapi.PingResponse{
			AppName:    os.Getenv("UCLOUD_UCX_APP_NAME"),
			AppVersion: os.Getenv("UCLOUD_UCX_APP_VERSION"),
			Mode:       mode,
			Headless:   ucx.AppHeadless(app),
			Message:    request.Message,
		}, nil
	})
}
