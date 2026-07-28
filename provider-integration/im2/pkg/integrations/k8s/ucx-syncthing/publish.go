package ucx_syncthing

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"strings"
	"time"

	syncthing_metrics "ucloud.dk/pkg/integrations/k8s/syncthing-metrics"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
)

const metricsPublishInterval = 15 * time.Second

type metricsPublisher struct {
	token  string
	client *rpc.Client
}

func newMetricsPublisher() (*metricsPublisher, error) {
	tokenBytes, err := os.ReadFile("/etc/ucloud/token")
	if err != nil {
		return nil, fmt.Errorf("read job token: %w", err)
	}
	token := strings.TrimSpace(strings.SplitN(string(tokenBytes), "\n", 2)[0])
	if token == "" {
		return nil, fmt.Errorf("job token is empty")
	}

	hostBytes, err := os.ReadFile("/opt/ucloud/provider-hostname.txt")
	if err != nil {
		return nil, fmt.Errorf("read provider hostname: %w", err)
	}
	host := strings.TrimSpace(string(hostBytes))
	if host == "" {
		return nil, fmt.Errorf("provider hostname is empty")
	}

	return &metricsPublisher{
		token: token,
		client: &rpc.Client{
			BasePath: fmt.Sprintf("http://%s:42000", host),
			Client:   &http.Client{Timeout: 10 * time.Second},
		},
	}, nil
}

func runMetricsPublisher(ctx context.Context, store *stateStore, publisher *metricsPublisher) {
	publish := func() {
		_, err := syncthing_metrics.Publish.InvokeEx(publisher.client, syncthing_metrics.PublishRequest{
			Token:    publisher.token,
			Snapshot: store.readMetrics(),
		}, rpc.InvokeOpts{})
		if err != nil && ctx.Err() == nil {
			log.Warn("UCX Syncthing: failed to publish metrics: %v", err)
		}
	}

	publish()
	ticker := time.NewTicker(metricsPublishInterval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			publish()
		}
	}
}
