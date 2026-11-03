package shared

import (
	"context"
	"os"

	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/util"
)

func RegisterRpcHandlers(app ucx.Application, mode string) {
	session := *app.Session()

	k8sapi.AppInfo.Handler(session, func(_ context.Context, _ util.Empty) (k8sapi.AppInfoResponse, error) {
		return k8sapi.AppInfoResponse{
			AppName:    os.Getenv("UCLOUD_UCX_APP_NAME"),
			AppVersion: os.Getenv("UCLOUD_UCX_APP_VERSION"),
			Mode:       mode,
			Headless:   ucx.AppHeadless(app),
		}, nil
	})
}
