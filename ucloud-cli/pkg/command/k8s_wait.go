package command

import (
	"context"
	"fmt"
	"time"

	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
)

const (
	k8sWaitTimeout      = 30 * time.Minute
	k8sWaitPollInterval = 5 * time.Second
)

func k8sClusterFailure(response k8sapi.StatusResponse, name string) error {
	if response.Phase != k8sapi.ClusterPhaseError &&
		response.Phase != k8sapi.ClusterPhaseCleanup &&
		response.FailureReason == "" {
		return nil
	}

	reason := response.FailureReason
	if reason == "" {
		reason = "the cluster entered the error phase"
	}
	return fmt.Errorf(
		"the cluster did not become ready: %s; use the dashboard at %s",
		reason,
		k8sValueOrDash(k8sDashboardURL(name)),
	)
}

func k8sWaitSleep(ctx context.Context) bool {
	timer := time.NewTimer(k8sWaitPollInterval)
	defer timer.Stop()

	select {
	case <-ctx.Done():
		return false
	case <-timer.C:
		return true
	}
}

func k8sWaitTimeoutError(name string) error {
	return fmt.Errorf(
		"the cluster did not become ready within %s; use the dashboard at %s",
		k8sWaitTimeout,
		k8sValueOrDash(k8sDashboardURL(name)),
	)
}

func k8sPoolWaitTimeoutError(name string, pool string) error {
	return fmt.Errorf(
		"the pool %s did not reach the target within %s; use the dashboard at %s",
		pool,
		k8sWaitTimeout,
		k8sValueOrDash(k8sDashboardURL(name)),
	)
}

func k8sNodeStateLabel(state string) string {
	switch state {
	case k8sapi.NodeStateReady:
		return "ready"
	case k8sapi.NodeStateNotReady:
		return "not ready"
	case k8sapi.NodeStateUnknown:
		return "unknown"
	default:
		return "pending"
	}
}
