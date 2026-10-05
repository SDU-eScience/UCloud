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

const NodeAgentMaxLogBody = 16 * 1024

const NodeAgentPort = 9444

const nodeAgentClientTimeout = 10 * time.Second

const NodeAgentTokenHeader = "X-Ucloud-Maintenance-Token"

const NodeAgentOperationHeader = "X-Ucloud-Maintenance-Operation"

const nodeAgentCoordinatorTokensDir = "/etc/ucloud-k8s/management/maintenance-tokens"

type NodeAgentStatus struct {
	NodeName  string    `json:"nodeName"`
	Release   string    `json:"release"`
	Phase     string    `json:"phase"`
	Error     string    `json:"error"`
	UpdatedAt time.Time `json:"updatedAt"`
}

func NodeAgentPhaseActive(phase string) bool {
	for _, candidate := range NodeAgentPhases {
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
	token, err := nodeAgentCoordinatorToken(nodeName)
	if err != nil {
		return nil, err
	}

	requestCtx, cancel := context.WithTimeout(ctx, nodeAgentClientTimeout)
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

	client := &http.Client{Timeout: nodeAgentClientTimeout}
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
	path := filepath.Join(nodeAgentCoordinatorTokensDir, SanitizeForPath(nodeName))

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
	trimmed := strings.TrimSpace(nodeIp)
	if trimmed == "" {
		return fmt.Errorf("no node IP address was provided")
	}

	address, err := netip.ParseAddr(trimmed)
	if err != nil {
		return fmt.Errorf("the node IP address is invalid: %s", trimmed)
	}

	clusterPrefix, err := netip.ParsePrefix(ClusterVmCidr)
	if err != nil {
		return fmt.Errorf("could not determine the cluster subnet: %s", err)
	}

	if !clusterPrefix.Contains(address) {
		return fmt.Errorf("the node IP address is outside the cluster subnet: %s", trimmed)
	}

	return nil
}
