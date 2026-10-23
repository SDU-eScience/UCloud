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
)

const NodeAgentPhaseIdle = "idle"
const NodeAgentPhaseDownloading = "downloading"
const NodeAgentPhaseSnapshotting = "snapshotting"
const NodeAgentPhaseInstalling = "installing"
const NodeAgentPhaseRestarting = "restarting"
const NodeAgentPhaseVerifying = "verifying"
const NodeAgentPhaseCompleted = "completed"
const NodeAgentPhaseFailed = "failed"

var NodeAgentPhases = []string{
	NodeAgentPhaseDownloading,
	NodeAgentPhaseSnapshotting,
	NodeAgentPhaseInstalling,
	NodeAgentPhaseRestarting,
	NodeAgentPhaseVerifying,
}

const NodeAgentOsPhaseIdle = "idle"
const NodeAgentOsPhaseStaging = "staging"
const NodeAgentOsPhaseStaged = "staged"
const NodeAgentOsPhaseApplying = "applying"
const NodeAgentOsPhaseRebooting = "rebooting"
const NodeAgentOsPhaseVerifying = "verifying"
const NodeAgentOsPhaseCompleted = "completed"
const NodeAgentOsPhaseFailed = "failed"

var NodeAgentOsPhases = []string{
	NodeAgentOsPhaseStaging,
	NodeAgentOsPhaseApplying,
	NodeAgentOsPhaseRebooting,
	NodeAgentOsPhaseVerifying,
}

const (
	NodeAgentOsCommandCheck      = "check"
	NodeAgentOsCommandStage      = "stage"
	NodeAgentOsCommandStageRetry = "stage-retry"
	NodeAgentOsCommandApply      = "apply"
	NodeAgentOsCommandRetry      = "retry"
)

const (
	NodeAgentOsRebootWhenRequired = "whenRequired"
	NodeAgentOsRebootAlways       = "always"
)

const NodeAgentMaxLogBody = 16 * 1024

const NodeAgentPort = 9444

const nodeAgentClientTimeout = 10 * time.Second

const nodeAgentOsCheckClientTimeout = 11 * time.Minute

const nodeAgentBackupClientTimeout = 25 * time.Minute

const NodeAgentTokenHeader = "X-Ucloud-Maintenance-Token"

const NodeAgentOperationHeader = "X-Ucloud-Maintenance-Operation"

const NodeAgentCoordinatorTokensDir = "/etc/ucloud-k8s/management/maintenance-tokens"

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
	Reboot  string `json:"reboot,omitempty"`
}

type NodeAgentBackupResult struct {
	Metadata      backup.Metadata     `json:"metadata"`
	Retained      []backup.BackupInfo `json:"retained,omitempty"`
	PruneWarnings []string            `json:"pruneWarnings,omitempty"`
}

func NodeAgentPhaseActive(phase string) bool {
	for _, candidate := range NodeAgentPhases {
		if phase == candidate {
			return true
		}
	}
	return false
}

func NodeAgentOsPhaseActive(phase string) bool {
	for _, candidate := range NodeAgentOsPhases {
		if phase == candidate {
			return true
		}
	}
	return false
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

func NodeAgentTruncateLine(line string) string {
	if len(line) <= nodeAgentProgressLineMax {
		return line
	}

	cut := nodeAgentProgressLineMax
	for cut > 0 {
		if (line[cut] & 0xC0) != 0x80 {
			break
		}
		cut--
	}
	return line[:cut]
}

const nodeAgentProgressLineMax = 2000

func NodeAgentClientStatus(ctx context.Context, nodeName string, nodeIp string) (NodeAgentStatus, error) {
	return nodeAgentClientStatusCall(ctx, nodeName, nodeIp, http.MethodGet, "/status", "", "")
}

func NodeAgentClientUpgrade(ctx context.Context, nodeName string, nodeIp string, release string, retry bool, operationUid string) (NodeAgentStatus, error) {
	body, err := json.Marshal(struct {
		Release string `json:"release"`
	}{Release: release})
	if err != nil {
		return NodeAgentStatus{}, err
	}

	endpoint := "/upgrade"
	if retry {
		endpoint = "/retry"
	}

	return nodeAgentClientStatusCall(ctx, nodeName, nodeIp, http.MethodPost, endpoint, string(body), operationUid)
}

func NodeAgentClientOsStatus(ctx context.Context, nodeName string, nodeIp string) (NodeAgentOsStatus, error) {
	if err := nodeAgentClientValidateIp(nodeIp); err != nil {
		return NodeAgentOsStatus{}, err
	}

	responseBody, err := nodeAgentClientCall(
		ctx,
		http.MethodGet,
		nodeAgentClientUrl(nodeIp, "/os/status"),
		"",
		nodeName,
		"",
	)
	if err != nil {
		return NodeAgentOsStatus{}, err
	}

	var status NodeAgentOsStatus
	if err := json.Unmarshal(responseBody, &status); err != nil {
		return NodeAgentOsStatus{}, fmt.Errorf("could not parse the node agent response: %s", err)
	}

	return status, nil
}

func NodeAgentClientOsCommand(
	ctx context.Context,
	nodeName string,
	nodeIp string,
	request NodeAgentOsRequest,
	operationUid string,
) (NodeAgentOsStatus, error) {
	body, err := json.Marshal(request)
	if err != nil {
		return NodeAgentOsStatus{}, err
	}

	if err := nodeAgentClientValidateIp(nodeIp); err != nil {
		return NodeAgentOsStatus{}, err
	}

	timeout := nodeAgentClientTimeout
	if request.Command == NodeAgentOsCommandCheck {
		timeout = nodeAgentOsCheckClientTimeout
	}

	responseBody, err := nodeAgentClientCallTimeout(
		ctx,
		http.MethodPost,
		nodeAgentClientUrl(nodeIp, "/os/update"),
		string(body),
		nodeName,
		operationUid,
		timeout,
	)
	if err != nil {
		return NodeAgentOsStatus{}, err
	}

	var status NodeAgentOsStatus
	if err := json.Unmarshal(responseBody, &status); err != nil {
		return NodeAgentOsStatus{}, fmt.Errorf("could not parse the node agent response: %s", err)
	}

	return status, nil
}

func NodeAgentClientBackup(ctx context.Context, nodeName string, nodeIp string, trigger string, operationUid string) (NodeAgentBackupResult, error) {
	body, err := json.Marshal(struct {
		Trigger string `json:"trigger"`
	}{Trigger: trigger})
	if err != nil {
		return NodeAgentBackupResult{}, err
	}

	if err := nodeAgentClientValidateIp(nodeIp); err != nil {
		return NodeAgentBackupResult{}, err
	}

	responseBody, err := nodeAgentClientCallTimeout(
		ctx,
		http.MethodPost,
		nodeAgentClientUrl(nodeIp, "/backup"),
		string(body),
		nodeName,
		operationUid,
		nodeAgentBackupClientTimeout,
	)
	if err != nil {
		return NodeAgentBackupResult{}, err
	}

	var result NodeAgentBackupResult
	if err := json.Unmarshal(responseBody, &result); err != nil {
		return NodeAgentBackupResult{}, fmt.Errorf("could not parse the node agent response: %s", err)
	}

	return result, nil
}

func NodeAgentClientAppendLog(ctx context.Context, nodeName string, nodeIp string, lines []string, operationUid string) error {
	if len(lines) == 0 {
		return nil
	}

	body, err := json.Marshal(struct {
		Lines []string `json:"lines"`
	}{Lines: lines})
	if err != nil {
		return err
	}

	if err := nodeAgentClientValidateIp(nodeIp); err != nil {
		return err
	}

	_, err = nodeAgentClientCall(ctx, http.MethodPost, nodeAgentClientUrl(nodeIp, "/log"), string(body), nodeName, operationUid)
	return err
}

func NodeAgentClientResetLog(ctx context.Context, nodeName string, nodeIp string, operationUid string) error {
	if err := nodeAgentClientValidateIp(nodeIp); err != nil {
		return err
	}

	_, err := nodeAgentClientCall(ctx, http.MethodPost, nodeAgentClientUrl(nodeIp, "/log/reset"), "", nodeName, operationUid)
	return err
}

func nodeAgentClientStatusCall(
	ctx context.Context,
	nodeName string,
	nodeIp string,
	method string,
	endpoint string,
	body string,
	operationUid string,
) (NodeAgentStatus, error) {
	if err := nodeAgentClientValidateIp(nodeIp); err != nil {
		return NodeAgentStatus{}, err
	}

	responseBody, err := nodeAgentClientCall(ctx, method, nodeAgentClientUrl(nodeIp, endpoint), body, nodeName, operationUid)
	if err != nil {
		return NodeAgentStatus{}, err
	}

	var status NodeAgentStatus
	if err := json.Unmarshal(responseBody, &status); err != nil {
		return NodeAgentStatus{}, fmt.Errorf("could not parse the node agent response: %s", err)
	}

	return status, nil
}

func nodeAgentClientCall(ctx context.Context, method string, url string, body string, nodeName string, operationUid string) (json.RawMessage, error) {
	return nodeAgentClientCallTimeout(ctx, method, url, body, nodeName, operationUid, nodeAgentClientTimeout)
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
		return fmt.Errorf("the node agent rejected the request (status %d): %s", statusCode, payload.Error)
	}
	return fmt.Errorf("the node agent rejected the request (status %d)", statusCode)
}

func nodeAgentClientUrl(nodeIp string, endpoint string) string {
	return fmt.Sprintf("http://%s%s", net.JoinHostPort(nodeIp, fmt.Sprintf("%d", NodeAgentPort)), endpoint)
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
		return fmt.Errorf("could not determine the cluster subnet: %s", err)
	}

	if !clusterPrefix.Contains(address) {
		return fmt.Errorf("the node IP address is outside the cluster subnet: %s", nodeIp)
	}

	return nil
}
