package creator

import (
	"fmt"
	"strings"

	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"

	"ucloud.dk/iapp/k8s/pkg/shared"
)

func creatorDeployRequest(app *k8sApp) k8sapi.DeployRequest {
	pools := make([]k8sapi.PoolSpec, 0, len(app.Pools))
	for _, pool := range app.Pools {
		pools = append(pools, k8sapi.PoolSpec{
			Name:    pool.Name,
			Machine: pool.Machine,
			Nodes:   pool.Nodes,
			Disk:    pool.DiskGb,
		})
	}

	return k8sapi.DeployRequest{
		Name:       app.ClusterId,
		Provider:   app.ServiceProvider,
		K8sVersion: app.K8sVersion,
		ControlPlane: k8sapi.ControlPlaneSpec{
			Machine: app.ControlPlaneMachine,
			Nodes:   app.ControlPlaneNodes,
			Disk:    app.ControlPlaneDiskGb,
		},
		WorkerPools: pools,
	}
}

func creatorDeploy(app ucx.Application, request k8sapi.DeployRequest, provider string) (*ucxsvc.Stack, bool, string) {
	request = k8sapi.NormalizeDeployRequest(request)
	if request.Provider == "" {
		request.Provider = strings.TrimSpace(provider)
	}
	if provider != "" && request.Provider != provider {
		return &ucxsvc.Stack{}, false, fmt.Sprintf(
			"the request provider %s does not match the application provider %s",
			request.Provider,
			provider,
		)
	}

	if err := k8sapi.ValidateDeployRequest(request); err != nil {
		return &ucxsvc.Stack{}, false, err.Error()
	}

	if _, ok := shared.ReleaseByExactVersion(request.K8sVersion); !ok {
		return &ucxsvc.Stack{}, false, "unknown Kubernetes version: " + request.K8sVersion
	}

	return shared.ClusterCreate(app, request)
}
