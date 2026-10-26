package iapp

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"

	fnd "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/util"
)

type Client struct {
	Session *ucx.Session
}

func (c *Client) Close() {
	if c != nil && c.Session != nil {
		c.Session.Cancel()
	}
}

func ConnectApp(ctx context.Context, name string, version util.Option[string]) (*Client, error) {
	app, httpErr := orcapi.AppsFindByNameAndVersion.Invoke(orcapi.AppCatalogFindByNameAndVersionRequest{
		AppName:    name,
		AppVersion: version,
	})
	if httpErr != nil {
		return nil, fmt.Errorf("could not find application %s: %s", name, httpErr.Why)
	}

	payload, err := json.Marshal(orcapi.AppUcxConnectRequest{
		Name:       app.Metadata.Name,
		Version:    app.Metadata.Version,
		ClientKind: orcapi.UcxClientHeadless,
		Updates:    orcapi.UcxUpdatesMessages,
	})
	if err != nil {
		return nil, fmt.Errorf("could not prepare the connection to %s: %w", name, err)
	}

	return connect(ctx, "/api/hpc/apps/ucx/connect", string(payload))
}

func ConnectCluster(ctx context.Context, name string) (*Client, error) {
	stack, httpErr := orcapi.StacksRetrieve.Invoke(fnd.FindByStringId{Id: name})
	if httpErr != nil {
		return nil, fmt.Errorf("could not find cluster %s: %s", name, httpErr.Why)
	}

	status := stack.Status.GetOrDefault(orcapi.StackStatus{})
	jobId := status.UcxConnectJobId.GetOrDefault("")
	if jobId == "" {
		return nil, fmt.Errorf("cluster %s is not available for management right now", name)
	}

	payload, err := json.Marshal(orcapi.AppUcxConnectJobRequest{
		JobId:      jobId,
		ClientKind: orcapi.UcxClientHeadless,
		Updates:    orcapi.UcxUpdatesMessages,
	})
	if err != nil {
		return nil, fmt.Errorf("could not prepare the connection to %s: %w", name, err)
	}

	return connect(ctx, "/api/hpc/apps/ucx/connectJob", string(payload))
}

func connect(ctx context.Context, path string, sysHello string) (*Client, error) {
	if rpc.DefaultClient == nil {
		return nil, fmt.Errorf("the UCloud client is not initialized")
	}

	accessToken := rpc.DefaultClient.RetrieveAccessTokenOrRefresh()
	if accessToken == "" {
		return nil, fmt.Errorf("could not authenticate with UCloud")
	}

	url, err := websocketURL(rpc.DefaultClient.BasePath, path)
	if err != nil {
		return nil, err
	}

	projectId := rpc.DefaultClient.ProjectId.GetOrDefault("")
	session, err := ucx.Connect(ctx, url, accessToken+"\n"+projectId, sysHello)
	if err != nil {
		return nil, err
	}

	if _, err := ucx.WaitForMount(ctx, session); err != nil {
		session.Cancel()
		return nil, fmt.Errorf("the application did not become ready: %w", err)
	}

	return &Client{Session: session}, nil
}

func websocketURL(basePath string, path string) (string, error) {
	base := strings.TrimSuffix(strings.TrimSpace(basePath), "/")
	path = "/" + strings.TrimPrefix(path, "/")

	switch {
	case strings.HasPrefix(base, "https://"):
		return "wss://" + strings.TrimPrefix(base, "https://") + path, nil
	case strings.HasPrefix(base, "http://"):
		return "ws://" + strings.TrimPrefix(base, "http://") + path, nil
	case strings.HasPrefix(base, "wss://"), strings.HasPrefix(base, "ws://"):
		return base + path, nil
	default:
		return "", fmt.Errorf("unsupported UCloud address %q", basePath)
	}
}
