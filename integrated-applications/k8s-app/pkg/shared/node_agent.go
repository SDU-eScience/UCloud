package shared

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/netip"
	"os"
	"path/filepath"
	"strings"
	"time"

	"k8s.io/apimachinery/pkg/util/version"

	"ucloud.dk/iapp/k8s/pkg/backup"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

const (
	NodeAgentPhaseIdle         = "idle"
	NodeAgentPhaseDownloading  = "downloading"
	NodeAgentPhaseSnapshotting = "snapshotting"
	NodeAgentPhaseInstalling   = "installing"
	NodeAgentPhaseRestarting   = "restarting"
	NodeAgentPhaseVerifying    = "verifying"
	NodeAgentPhaseCompleted    = "completed"
	NodeAgentPhaseFailed       = "failed"

	NodeAgentOsPhaseIdle      = "idle"
	NodeAgentOsPhaseStaging   = "staging"
	NodeAgentOsPhaseStaged    = "staged"
	NodeAgentOsPhaseApplying  = "applying"
	NodeAgentOsPhaseRebooting = "rebooting"
	NodeAgentOsPhaseVerifying = "verifying"
	NodeAgentOsPhaseCompleted = "completed"
	NodeAgentOsPhaseFailed    = "failed"

	NodeAgentOsCommandCheck      = "check"
	NodeAgentOsCommandStage      = "stage"
	NodeAgentOsCommandStageRetry = "stage-retry"
	NodeAgentOsCommandApply      = "apply"
	NodeAgentOsCommandRetry      = "retry"

	NodeAgentPort            = 9444
	NodeAgentMaxLogBody      = 16 * 1024
	NodeAgentProgressLineMax = 2000

	nodeAgentClientTimeout        = 10 * time.Second
	nodeAgentOsCheckClientTimeout = 11 * time.Minute
	nodeAgentBackupClientTimeout  = 25 * time.Minute

	NodeAgentTokenHeader          = "X-Ucloud-Maintenance-Token"
	NodeAgentOperationHeader      = "X-Ucloud-Maintenance-Operation"
	NodeAgentCoordinatorTokensDir = "/etc/ucloud-k8s/management/maintenance-tokens"
)

var NodeAgentPhases = []string{
	NodeAgentPhaseDownloading,
	NodeAgentPhaseSnapshotting,
	NodeAgentPhaseInstalling,
	NodeAgentPhaseRestarting,
	NodeAgentPhaseVerifying,
}

var NodeAgentOsPhases = []string{
	NodeAgentOsPhaseStaging,
	NodeAgentOsPhaseApplying,
	NodeAgentOsPhaseRebooting,
	NodeAgentOsPhaseVerifying,
}

type NodeAgentStatus struct {
	Progress     NodeAgentProgress `json:"progress"`
	OperationUid string            `json:"operationUid,omitempty"`
	NodeName     string            `json:"nodeName"`
	Release      string            `json:"release"`
	Phase        string            `json:"phase"`
	Error        string            `json:"error"`
	UpdatedAt    time.Time         `json:"updatedAt"`
}

type NodeAgentOsStatus struct {
	Progress           NodeAgentProgress `json:"progress"`
	StagingSupported   bool              `json:"stagingSupported,omitempty"`
	OperationUid       string            `json:"operationUid,omitempty"`
	NodeName           string            `json:"nodeName"`
	Phase              string            `json:"phase"`
	Error              string            `json:"error"`
	NeedsRecovery      bool              `json:"needsRecovery"`
	NeedsStateRecovery bool              `json:"needsStateRecovery,omitempty"`
	Pending            int               `json:"pending"`
	Security           int               `json:"security"`
	RebootRequired     bool              `json:"rebootRequired"`
	KernelRunning      string            `json:"kernelRunning,omitempty"`
	KernelPending      string            `json:"kernelPending,omitempty"`
	CheckedAt          time.Time         `json:"checkedAt,omitempty"`
	CheckError         string            `json:"checkError,omitempty"`
	BootId             string            `json:"bootId,omitempty"`
	UpdatedAt          time.Time         `json:"updatedAt"`
}

type NodeAgentProgress struct {
	OperationUid string                   `json:"operationUid,omitempty"`
	Percent      int                      `json:"percent"`
	Stage        string                   `json:"stage,omitempty"`
	Detail       string                   `json:"detail,omitempty"`
	UpdatedAt    time.Time                `json:"updatedAt,omitempty"`
	Events       []NodeAgentProgressEvent `json:"events,omitempty"`
}

type NodeAgentProgressEvent struct {
	At   time.Time `json:"at"`
	Text string    `json:"text"`
}

type NodeAgentOsRequest struct {
	Command string `json:"command"`
}

type NodeAgentBackupResult struct {
	Metadata      backup.Metadata     `json:"metadata"`
	Retained      []backup.BackupInfo `json:"retained,omitempty"`
	PruneWarnings []string            `json:"pruneWarnings,omitempty"`
}

func NodeAgentUpgradeAllowed(currentVersion string, targetVersion string) bool {
	if !strings.HasPrefix(currentVersion, "v") || !strings.HasPrefix(targetVersion, "v") {
		return false
	}

	current, err := version.ParseSemantic(currentVersion)
	if err != nil {
		return false
	}

	target, err := version.ParseSemantic(targetVersion)
	if err != nil {
		return false
	}

	if target.Major() != current.Major() {
		return false
	}

	if target.Minor() == current.Minor() {
		return target.Patch() > current.Patch()
	}

	return target.Minor() == current.Minor()+1
}

type nodeAgentUpgradeRequest struct {
	Release string `json:"release"`
}

type nodeAgentBackupRequest struct {
	Trigger string `json:"trigger"`
}

type nodeAgentLogRequest struct {
	Lines []string `json:"lines"`
}

func NodeAgentClientStatus(ctx context.Context, nodeName string, nodeIp string) (NodeAgentStatus, error) {
	return nodeAgentClientGet[NodeAgentStatus](ctx, nodeName, nodeIp, "/status")
}

func NodeAgentClientUpgrade(ctx context.Context, nodeName string, nodeIp string, release string, retry bool, operationUid string) (NodeAgentStatus, error) {
	endpoint := "/upgrade"
	if retry {
		endpoint = "/retry"
	}

	return nodeAgentClientPost[NodeAgentStatus](
		ctx,
		nodeName,
		nodeIp,
		endpoint,
		nodeAgentUpgradeRequest{Release: release},
		operationUid,
		nodeAgentClientTimeout,
	)
}

func NodeAgentClientOsStatus(ctx context.Context, nodeName string, nodeIp string) (NodeAgentOsStatus, error) {
	return nodeAgentClientGet[NodeAgentOsStatus](ctx, nodeName, nodeIp, "/os/status")
}

func NodeAgentClientOsCommand(
	ctx context.Context,
	nodeName string,
	nodeIp string,
	request NodeAgentOsRequest,
	operationUid string,
) (NodeAgentOsStatus, error) {
	timeout := nodeAgentClientTimeout
	if request.Command == NodeAgentOsCommandCheck {
		timeout = nodeAgentOsCheckClientTimeout
	}

	return nodeAgentClientPost[NodeAgentOsStatus](
		ctx,
		nodeName,
		nodeIp,
		"/os/update",
		request,
		operationUid,
		timeout,
	)
}

func NodeAgentClientBackup(ctx context.Context, nodeName string, nodeIp string, trigger string, operationUid string) (NodeAgentBackupResult, error) {
	return nodeAgentClientPost[NodeAgentBackupResult](
		ctx,
		nodeName,
		nodeIp,
		"/backup",
		nodeAgentBackupRequest{Trigger: trigger},
		operationUid,
		nodeAgentBackupClientTimeout,
	)
}

func NodeAgentClientAppendLog(ctx context.Context, nodeName string, nodeIp string, lines []string, operationUid string) error {
	if len(lines) == 0 {
		return nil
	}

	_, err := nodeAgentClientPost[struct{}](
		ctx,
		nodeName,
		nodeIp,
		"/log",
		nodeAgentLogRequest{Lines: lines},
		operationUid,
		nodeAgentClientTimeout,
	)
	return err
}

func NodeAgentClientResetLog(ctx context.Context, nodeName string, nodeIp string, operationUid string) error {
	_, err := nodeAgentClientPost[struct{}](
		ctx,
		nodeName,
		nodeIp,
		"/log/reset",
		nil,
		operationUid,
		nodeAgentClientTimeout,
	)
	return err
}

func nodeAgentClientGet[T any](ctx context.Context, nodeName string, nodeIp string, endpoint string) (T, error) {
	return nodeAgentClientRequest[T](
		ctx,
		nodeName,
		nodeIp,
		http.MethodGet,
		endpoint,
		nil,
		"",
		nodeAgentClientTimeout,
	)
}

func nodeAgentClientPost[T any](
	ctx context.Context,
	nodeName string,
	nodeIp string,
	endpoint string,
	payload any,
	operationUid string,
	timeout time.Duration,
) (T, error) {
	return nodeAgentClientRequest[T](
		ctx,
		nodeName,
		nodeIp,
		http.MethodPost,
		endpoint,
		payload,
		operationUid,
		timeout,
	)
}

func nodeAgentClientRequest[T any](
	ctx context.Context,
	nodeName string,
	nodeIp string,
	method string,
	endpoint string,
	payload any,
	operationUid string,
	timeout time.Duration,
) (T, error) {
	var zero T

	if err := nodeAgentClientValidateIp(nodeIp); err != nil {
		return zero, err
	}

	var body string
	if payload != nil {
		body = string(util.MustJsonMarshal(payload))
	}

	responseBody, err := nodeAgentClientCallTimeout(
		ctx,
		method,
		fmt.Sprintf("http://%s%s", net.JoinHostPort(nodeIp, fmt.Sprintf("%d", NodeAgentPort)), endpoint),
		body,
		nodeName,
		operationUid,
		timeout,
	)
	if err != nil {
		return zero, err
	}

	var result T
	if err := json.Unmarshal(responseBody, &result); err != nil {
		return zero, fmt.Errorf("could not read the node maintenance response: %s", err)
	}

	return result, nil
}

func nodeAgentClientCallTimeout(
	ctx context.Context,
	method string,
	url string,
	body string,
	nodeName string,
	operationUid string,
	timeout time.Duration,
) (json.RawMessage, error) {
	token, err := nodeAgentCoordinatorToken(nodeName)
	if err != nil {
		return nil, err
	}

	requestCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	var requestBody io.Reader
	if body != "" {
		requestBody = bytes.NewReader([]byte(body))
	}

	request, err := http.NewRequestWithContext(requestCtx, method, url, requestBody)
	if err != nil {
		return nil, err
	}
	request.Header.Set("Content-Type", "application/json")
	request.Header.Set(NodeAgentTokenHeader, token)
	if operationUid != "" {
		request.Header.Set(NodeAgentOperationHeader, operationUid)
	}

	client := &http.Client{Timeout: timeout}
	defer client.CloseIdleConnections()

	response, err := client.Do(request)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()

	responseBody, err := io.ReadAll(io.LimitReader(response.Body, 1<<20))
	if err != nil {
		return nil, err
	}

	if response.StatusCode != http.StatusOK && response.StatusCode != http.StatusAccepted {
		return nil, nodeAgentClientError(response.StatusCode, responseBody)
	}

	return json.RawMessage(responseBody), nil
}

func nodeAgentCoordinatorToken(nodeName string) (string, error) {
	path := filepath.Join(NodeAgentCoordinatorTokensDir, SanitizeForPath(nodeName))

	data, err := os.ReadFile(path)
	if err != nil {
		return "", fmt.Errorf("the maintenance token of node %s is not available: %s", nodeName, err)
	}

	token := strings.TrimSpace(string(data))
	if token == "" {
		return "", fmt.Errorf("the maintenance token of node %s is empty", nodeName)
	}

	return token, nil
}

func nodeAgentClientError(statusCode int, responseBody []byte) error {
	var payload struct {
		Error string `json:"error"`
	}
	if err := json.Unmarshal(responseBody, &payload); err == nil && payload.Error != "" {
		return fmt.Errorf("the node rejected the maintenance request (status %d): %s", statusCode, payload.Error)
	}
	return fmt.Errorf("the node rejected the maintenance request (status %d)", statusCode)
}

func nodeAgentClientValidateIp(nodeIp string) error {
	if nodeIp == "" {
		return fmt.Errorf("no node IP address was provided")
	}

	address, err := netip.ParseAddr(nodeIp)
	if err != nil {
		return fmt.Errorf("the node IP address is invalid: %s", nodeIp)
	}

	clusterPrefix, err := netip.ParsePrefix(ClusterVmCidr)
	if err != nil {
		log.Fatal("k8s-app: the cluster subnet %s is invalid: %s", ClusterVmCidr, err)
	}

	if !clusterPrefix.Contains(address) {
		return fmt.Errorf("the node IP address is outside the cluster subnet: %s", nodeIp)
	}

	return nil
}
