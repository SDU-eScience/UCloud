package iapp

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
	"sync"
	"time"

	fnd "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/util"
)

const iappMountTimeout = 3 * time.Minute

type Client struct {
	Session *ucx.Session

	mu                 sync.Mutex
	lastMessage        string
	lastMessageSuccess bool
	messageHandler     func(message string, success bool, level string)
}

func (c *Client) Close() {
	if c != nil && c.Session != nil {
		c.Session.Cancel()
	}
}

func (c *Client) SetMessageHandler(handler func(message string, success bool, level string)) {
	c.mu.Lock()
	c.messageHandler = handler
	c.mu.Unlock()
}

func (c *Client) LastFailure() (string, bool) {
	c.mu.Lock()
	defer c.mu.Unlock()

	if c.lastMessage == "" || c.lastMessageSuccess {
		return "", false
	}
	return c.lastMessage, true
}

func ConnectAppWithProvider(
	ctx context.Context,
	name string,
	version util.Option[string],
	provider util.Option[string],
) (*Client, error) {
	app, httpErr := orcapi.AppsFindByNameAndVersion.Invoke(orcapi.AppCatalogFindByNameAndVersionRequest{
		AppName:    name,
		AppVersion: version,
	})
	if httpErr != nil {
		return nil, fmt.Errorf("could not find application %s: %s", termio.QuoteName(name), httpErr.Why)
	}

	payload, err := json.Marshal(orcapi.AppUcxConnectRequest{
		Name:       app.Metadata.Name,
		Version:    app.Metadata.Version,
		Provider:   provider,
		ClientKind: orcapi.UcxClientHeadless,
		Updates:    orcapi.UcxUpdatesMessages,
	})
	if err != nil {
		return nil, fmt.Errorf("could not prepare the connection to %s: %w", termio.QuoteName(name), err)
	}

	return connect(ctx, "/api/hpc/apps/ucx/connect", string(payload))
}

func RetrieveCluster(name string) (orcapi.Stack, error) {
	stack, httpErr := orcapi.StacksRetrieve.Invoke(fnd.FindByStringId{Id: name})
	if httpErr != nil {
		return orcapi.Stack{}, fmt.Errorf("could not find cluster %s: %s", termio.QuoteName(name), httpErr.Why)
	}
	return stack, nil
}

func ConnectStack(ctx context.Context, stack orcapi.Stack) (*Client, error) {
	status := stack.Status.GetOrDefault(orcapi.StackStatus{})
	jobId := status.UcxConnectJobId.GetOrDefault("")
	if jobId == "" {
		return nil, fmt.Errorf("cluster %s is not available for management right now", termio.QuoteName(stack.Id))
	}

	payload, err := json.Marshal(orcapi.AppUcxConnectJobRequest{
		JobId:      jobId,
		ClientKind: orcapi.UcxClientHeadless,
		Updates:    orcapi.UcxUpdatesMessages,
	})
	if err != nil {
		return nil, fmt.Errorf("could not prepare the connection to %s: %w", termio.QuoteName(stack.Id), err)
	}

	return connect(ctx, "/api/hpc/apps/ucx/connectJob", string(payload))
}

func Call[Req any, Resp any](ctx context.Context, c *Client, call ucx.Rpc[Req, Resp], request Req) (Resp, error) {
	return call.InvokeEx(ctx, c.Session, request)
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

	client := &Client{Session: session}
	client.registerClientHandlers()

	mountCtx, mountCancel := context.WithTimeout(ctx, iappMountTimeout)
	defer mountCancel()

	if _, err := ucx.WaitForMount(mountCtx, session); err != nil {
		session.Cancel()
		return nil, fmt.Errorf("the application did not become ready: %w", err)
	}

	go client.drainIncoming()

	return client, nil
}

func (c *Client) registerClientHandlers() {
	ucxapi.UiSendMessage.Handler(c.Session, func(ctx context.Context, request ucxapi.UiSendMessageRequest) (util.Empty, error) {
		c.mu.Lock()
		c.lastMessage = request.Message
		c.lastMessageSuccess = request.Success
		handler := c.messageHandler
		c.mu.Unlock()

		if handler != nil && request.Message != "" {
			handler(request.Message, request.Success, request.Level)
		}
		return util.Empty{}, nil
	})

	registerNoopRpc(c.Session, ucxapi.StackOpen)
	registerNoopRpc(c.Session, ucxapi.StackInfo)
	registerNoopRpc(c.Session, ucxapi.StackRefresh)
	registerNoopRpc(c.Session, ucxapi.StackShowResources)
	registerNoopRpc(c.Session, ucxapi.StackDelete)
	registerNoopRpc(c.Session, ucxapi.StackDownloadFile)
	registerNoopRpc(c.Session, ucxapi.StackCopyFile)
	registerNoopRpc(c.Session, ucxapi.RouterPushPage)
	registerNoopRpc(c.Session, ucxapi.OpenUrl)
	registerNoopRpc(c.Session, ucxapi.TerminalOpenShellToJob)
	registerNoopRpc(c.Session, ucxapi.SwitchProvider)
}

func registerNoopRpc[Req any, Resp any](session *ucx.Session, call ucx.Rpc[Req, Resp]) {
	call.Handler(session, func(ctx context.Context, request Req) (Resp, error) {
		var zero Resp
		return zero, nil
	})
}

func (c *Client) drainIncoming() {
	for {
		select {
		case <-c.Session.Context().Done():
			return
		case _, ok := <-c.Session.Incoming():
			if !ok {
				return
			}
		}
	}
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
