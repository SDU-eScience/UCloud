package creator

import (
	"context"
	"errors"
	"fmt"
	"time"

	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	"ucloud.dk/shared/pkg/util"

	"ucloud.dk/iapp/k8s/pkg/shared"
)

func (app *k8sApp) registerRpcHandlers() {
	session := *app.Session()

	k8sapi.Releases.Handler(session, func(_ context.Context, _ util.Empty) (k8sapi.ReleasesResponse, error) {
		now := time.Now().UTC()
		releases := shared.CreationReleases(now)

		result := make([]k8sapi.Release, 0, len(releases))
		for i, release := range releases {
			var eolAt int64
			if !release.EolAt.IsZero() {
				eolAt = release.EolAt.Unix()
			}

			result = append(result, k8sapi.Release{
				Version: release.Release,
				EolAt:   eolAt,
				Default: i == 0,
			})
		}

		return k8sapi.ReleasesResponse{Releases: result}, nil
	})

	k8sapi.Deploy.Handler(session, func(_ context.Context, request k8sapi.DeployRequest) (k8sapi.DeployResponse, error) {
		app.mu.Lock()
		if app.DeployBusy || app.SwitchBusy {
			app.mu.Unlock()
			return k8sapi.DeployResponse{}, fmt.Errorf("the application is busy")
		}
		app.DeployBusy = true
		provider := app.HostProvider
		app.mu.Unlock()

		defer func() {
			app.mu.Lock()
			app.DeployBusy = false
			app.mu.Unlock()
		}()

		stack, ok, failure := creatorDeploy(app, request, provider)
		if !ok {
			if failure == "" {
				failure = "the cluster could not be created"
			}
			return k8sapi.DeployResponse{}, errors.New(failure)
		}

		name := stack.InstanceId
		if name == "" {
			name = request.Name
		}

		return k8sapi.DeployResponse{Name: name, Ok: true}, nil
	})
}
