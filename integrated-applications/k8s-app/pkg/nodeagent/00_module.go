// Introduction
// =====================================================================================================================
// This file implements the node agent of the UCloud Kubernetes stack. The node agent is a daemon that runs as root
// on every node of a cluster. The main responsibilities of this system are:
//
// - Establishing the identity of the node from its input mount
// - Serving the maintenance API on the private address of the node
// - Authenticating commands from the coordinator through a shared maintenance token
// - Rejecting commands from coordinator operations that the node has superseded
//
// The node agent exists because node upgrades require root access on the node itself. The coordinator runs on a
// control plane node and cannot perform these actions itself. It drives the node agent over the maintenance API
// instead. The agent only accepts commands that arrive on the cluster network and that carry a valid maintenance
// token.
//
// Lifecycle
// ---------------------------------------------------------------------------------------------------------------------
// The agent starts when systemd launches it with the agent subcommand. It reads its identity, recovers the state of
// an interrupted upgrade, and then serves until the process stops. The agent never exits on its own during normal
// operation. A failed upgrade leaves its state on disk so that an operator can retry it.
//
// Identity and trust
// ---------------------------------------------------------------------------------------------------------------------
// The identity of a node comes from the input mount, which the provisioner fills before the node boots. The identity
// fixes the name, the address, and the role of the node. The agent refuses to start if the recorded address is not
// assigned to the node. A wrong address would make the agent unreachable on the network it is expected to serve on.
//
// Trust is one-directional. The coordinator proves itself to the node with the maintenance token. Commands carry
// the uid of the maintenance operation that owns them. The node learns nothing about the coordinator beyond these
// two facts.
//
// Command ownership
// ---------------------------------------------------------------------------------------------------------------------
// The coordinator may crash and restart while an upgrade runs. A restarted coordinator creates a new maintenance
// operation with a new uid. The fence records the uid of the newest operation that the node has obeyed. A command
// from an older operation is rejected while an upgrade is in flight. This stops a coordinator that was paused and
// resumed from issuing commands that no longer match the state of the node.
//
// The fence and the upgrade state are separate files on purpose. The fence answers who may command the node. The
// state answers what the node is doing. The fence survives every operation, while the state is rewritten as the
// upgrade moves through its phases.
//
// Concurrency
// ---------------------------------------------------------------------------------------------------------------------
// The handlers serialize command processing with a single command mutex. The status endpoint takes the same mutex so
// that a status read never observes a half-written command decision. The upgrade itself runs in a background
// goroutine and reports progress through its own state file. The goroutine takes the state mutex for each read and
// write of the state file, which keeps every state transition atomic with respect to readers.

package nodeagent

import (
	"context"
	"crypto/subtle"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/netip"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"sync"
	"time"

	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/log"
)

// Constants and package state
// =====================================================================================================================

const (
	stateSchemaRevision = 1
	stateName           = "upgrade-state.json"

	fenceStateName      = "fence-state.json"
	fenceSchemaRevision = 2

	inputDir             = "/etc/ucloud-k8s/input"
	nodeJsonName         = "node.json"
	maintenanceTokenName = "maintenance-enrollment-secret"

	serviceUid = 11042
	serviceGid = 11042

	addressRetryInterval = 10 * time.Second

	readHeaderTimeout = 10 * time.Second
	readTimeout       = 15 * time.Second
	writeTimeout      = 15 * time.Second

	maxRequestBody = 4 * 1024

	roleMaxLen = 30
)

var roleRe = regexp.MustCompile(`^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$`)

var globals struct {
	Mu       sync.Mutex
	Identity Config

	CommandMu sync.Mutex
}

type Config struct {
	NodeName  string
	IpAddress string
	Role      string
}

type nodeFile struct {
	Hostname    string `json:"hostname"`
	IpAddress   string `json:"ipAddress"`
	Role        string `json:"role"`
	FirstServer bool   `json:"firstServer"`
}

func identitySnapshot() Config {
	globals.Mu.Lock()
	defer globals.Mu.Unlock()
	return globals.Identity
}

// Serving
// =====================================================================================================================
// The agent serves four endpoints. The coordinator polls /status, submits /upgrade and /retry, and appends progress
// lines through /log. Only /status responds to GET.

func Serve(ctx context.Context) error {
	identityConfig, err := Identity(ctx)
	if err != nil {
		return err
	}

	globals.Mu.Lock()
	globals.Identity = identityConfig
	globals.Mu.Unlock()

	recoverInterrupted()

	mux := http.NewServeMux()
	mux.HandleFunc("/status", handleStatus)
	mux.HandleFunc("/upgrade", handleUpgrade)
	mux.HandleFunc("/retry", handleRetry)
	mux.HandleFunc("/log", handleLog)

	server := &http.Server{
		Addr:              net.JoinHostPort(identityConfig.IpAddress, fmt.Sprintf("%d", shared.NodeAgentPort)),
		Handler:           mux,
		ReadHeaderTimeout: readHeaderTimeout,
		ReadTimeout:       readTimeout,
		WriteTimeout:      writeTimeout,
	}

	serveErr := make(chan error, 1)
	go func() {
		err := server.ListenAndServe()
		if err != nil && !errors.Is(err, http.ErrServerClosed) {
			serveErr <- err
			return
		}
		serveErr <- nil
	}()

	select {
	case <-ctx.Done():
		shutdownCtx, cancel := context.WithTimeout(context.Background(), stopTimeout)
		defer cancel()
		_ = server.Shutdown(shutdownCtx)
		return nil
	case err := <-serveErr:
		return err
	}
}

// Node identity
// =====================================================================================================================

func Identity(ctx context.Context) (Config, error) {
	nodeJson, err := readNodeConfig(nodeJsonPath())
	if err != nil {
		return Config{}, fmt.Errorf("the node identity could not be established: %s", err)
	}

	err = waitForLocalAddress(ctx, nodeJson.IpAddress)
	if err != nil {
		return Config{}, fmt.Errorf("the node identity could not be established: %s", err)
	}

	return Config{
		NodeName:  nodeJson.Hostname,
		IpAddress: nodeJson.IpAddress,
		Role:      nodeJson.Role,
	}, nil
}

func readNodeConfig(path string) (nodeFile, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nodeFile{}, err
	}

	var nodeJson nodeFile
	err = json.Unmarshal(data, &nodeJson)
	if err != nil {
		return nodeFile{}, fmt.Errorf("the node configuration at %s is invalid: %s", path, err)
	}

	if strings.TrimSpace(nodeJson.Hostname) == "" {
		return nodeFile{}, fmt.Errorf("the node configuration at %s has no hostname", path)
	}

	address, err := netip.ParseAddr(strings.TrimSpace(nodeJson.IpAddress))
	if err != nil {
		return nodeFile{}, fmt.Errorf(
			"the node configuration at %s has an invalid IP address: %s", path, nodeJson.IpAddress)
	}

	clusterPrefix, err := netip.ParsePrefix(shared.ClusterVmCidr)
	if err != nil {
		return nodeFile{}, err
	}

	if !clusterPrefix.Contains(address) {
		return nodeFile{}, fmt.Errorf(
			"the node IP address %s is outside the cluster subnet %s", nodeJson.IpAddress, shared.ClusterVmCidr)
	}

	if nodeJson.Role != shared.GroupControlPlane && !roleValid(nodeJson.Role) {
		return nodeFile{}, fmt.Errorf(
			"the node configuration at %s has an unknown role: %s", path, nodeJson.Role)
	}

	return nodeJson, nil
}

func waitForLocalAddress(ctx context.Context, ipAddress string) error {
	address, err := netip.ParseAddr(strings.TrimSpace(ipAddress))
	if err != nil {
		return fmt.Errorf("the node IP address is invalid: %s", ipAddress)
	}

	for {
		if localAddressAssigned(address) {
			return nil
		}

		select {
		case <-ctx.Done():
			return fmt.Errorf(
				"the private address %s was not assigned to this node within %s", ipAddress, ctx.Err())
		case <-time.After(addressRetryInterval):
		}
	}
}

func localAddressAssigned(address netip.Addr) bool {
	interfaces, err := net.Interfaces()
	if err != nil {
		return false
	}

	for _, iface := range interfaces {
		addresses, err := iface.Addrs()
		if err != nil {
			continue
		}

		for _, assigned := range addresses {
			prefix, err := netip.ParsePrefix(assigned.String())
			if err != nil {
				continue
			}

			if prefix.Addr().Compare(address) == 0 {
				return true
			}
		}
	}

	return false
}

func nodeJsonPath() string {
	return filepath.Join(inputDir, nodeJsonName)
}

func maintenanceTokenPath() string {
	return filepath.Join(inputDir, maintenanceTokenName)
}

// Command handlers
// =====================================================================================================================
// Every handler first authenticates the peer. The upgrade and retry handlers then validate the command against the
// fence before they touch the state file.

func handleStatus(writer http.ResponseWriter, request *http.Request) {
	if !authorizePeer(writer, request) {
		return
	}
	if request.Method != http.MethodGet {
		writeError(writer, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	globals.CommandMu.Lock()
	defer globals.CommandMu.Unlock()
	writeStatus(writer, http.StatusOK)
}

func handleUpgrade(writer http.ResponseWriter, request *http.Request) {
	handleUpgradeOrRetry(writer, request, false)
}

func handleRetry(writer http.ResponseWriter, request *http.Request) {
	handleUpgradeOrRetry(writer, request, true)
}

func handleUpgradeOrRetry(writer http.ResponseWriter, request *http.Request, retry bool) {
	if !authorizePeer(writer, request) {
		return
	}
	if request.Method != http.MethodPost {
		writeError(writer, http.StatusMethodNotAllowed, "this endpoint only accepts POST")
		return
	}

	release, ok := decodeRelease(writer, request)
	if !ok {
		return
	}

	globals.CommandMu.Lock()
	defer globals.CommandMu.Unlock()

	operationUid, ok := readCommandAuthorization(writer, request)
	if !ok {
		return
	}

	state := stateLoad()
	if state.NeedsStateRecovery {
		writeError(writer, http.StatusInternalServerError, state.Error)
		return
	}

	fenceErr := validateFence(operationUid, shared.NodeAgentPhaseActive(state.Phase))
	if fenceErr != nil {
		log.Warn("k8s-app node agent: the command was rejected because its ownership could not be verified: %s", fenceErr)
		writeError(writer, http.StatusForbidden, fenceErr.Error())
		return
	}

	if shared.NodeAgentPhaseActive(state.Phase) {
		if state.Release == release {
			if err := acceptOperation(operationUid); err != nil {
				writeError(writer, http.StatusInternalServerError, err.Error())
				return
			}
			writeStatus(writer, http.StatusOK)
			return
		}
		writeError(writer, http.StatusConflict, fmt.Sprintf(
			"an upgrade to %s is already running; a different target was rejected",
			state.Release,
		))
		return
	}

	if state.Phase == shared.NodeAgentPhaseFailed {
		if state.Release == "" {
			writeError(writer, http.StatusInternalServerError, "the upgrade state is corrupted; manual recovery is required")
			return
		}
		if !retry {
			writeError(writer, http.StatusConflict, "the failed upgrade requires an explicit retry")
			return
		}
		if state.Release != release {
			writeError(writer, http.StatusConflict, fmt.Sprintf(
				"the failed upgrade targets %s; the retry does not match",
				state.Release,
			))
			return
		}
		startUpgrade(writer, release, state, operationUid)
		return
	}

	if state.Phase == shared.NodeAgentPhaseCompleted {
		if state.Release != release {
			if retry {
				writeError(writer, http.StatusConflict, "there is no failed upgrade to retry")
				return
			}
			startUpgrade(writer, release, state, operationUid)
			return
		}
		if state.PreviousRelease == "" {
			writeError(writer, http.StatusInternalServerError, "the completed upgrade has no recorded previous release; manual verification is required")
			return
		}
		startUpgrade(writer, release, state, operationUid)
		return
	}

	if state.Release == "" {
		if retry {
			writeError(writer, http.StatusConflict, "there is no failed upgrade to retry")
			return
		}
		startUpgrade(writer, release, state, operationUid)
		return
	}

	writeError(writer, http.StatusInternalServerError, fmt.Sprintf(
		"the upgrade state reports phase %s without a target release; manual recovery is required",
		state.Phase,
	))
}

func decodeRelease(writer http.ResponseWriter, request *http.Request) (string, bool) {
	body := request.Body
	if request.ContentLength > maxRequestBody {
		writeError(writer, http.StatusRequestEntityTooLarge, "the request body is too large")
		return "", false
	}
	body = http.MaxBytesReader(writer, body, maxRequestBody)

	var parsed struct {
		Release string `json:"release"`
	}
	decoder := json.NewDecoder(body)
	decoder.DisallowUnknownFields()
	err := decoder.Decode(&parsed)
	if err != nil {
		writeError(writer, http.StatusBadRequest, fmt.Sprintf("invalid request body: %s", err))
		return "", false
	}
	if decoder.Decode(&struct{}{}) != io.EOF {
		writeError(writer, http.StatusBadRequest, "the request body must contain a single JSON object")
		return "", false
	}
	if strings.TrimSpace(parsed.Release) == "" {
		writeError(writer, http.StatusBadRequest, "a release is required")
		return "", false
	}
	if _, known := shared.ReleaseByExactVersion(parsed.Release); !known {
		writeError(writer, http.StatusBadRequest, fmt.Sprintf("unknown release: %s", parsed.Release))
		return "", false
	}
	return parsed.Release, true
}

func startUpgrade(writer http.ResponseWriter, release string, state upgradeState, operationUid string) {
	current := stateLoad()
	stateChanged := current.Phase != state.Phase || current.Release != state.Release || !current.UpdatedAt.Equal(state.UpdatedAt)
	if current.NeedsStateRecovery || stateChanged {
		writeError(writer, http.StatusConflict, "the upgrade state changed; read its status before submitting again")
		return
	}

	fence, fenceErr := fenceLoad()
	if fenceErr != nil {
		writeError(writer, http.StatusInternalServerError, fmt.Sprintf("could not read the fence state: %s", fenceErr))
		return
	}
	if fence.LastOperation != "" && fence.LastOperation != operationUid && shared.NodeAgentPhaseActive(current.Phase) {
		writeError(writer, http.StatusConflict, "the command comes from obsolete node ownership and was rejected")
		return
	}

	fenceErr = fenceStore(operationUid)
	if fenceErr != nil {
		writeError(writer, http.StatusInternalServerError, fmt.Sprintf("could not persist the fence state: %s", fenceErr))
		return
	}

	identity := identitySnapshot()
	previousRelease := ""
	if state.Release == release {
		previousRelease = state.PreviousRelease
	}
	persistErr := stateStore(&upgradeState{
		SchemaRevision:  stateSchemaRevision,
		NodeName:        identity.NodeName,
		IpAddress:       identity.IpAddress,
		Role:            identity.Role,
		Release:         release,
		PreviousRelease: previousRelease,
		Phase:           shared.NodeAgentPhaseDownloading,
		UpdatedAt:       time.Now().UTC(),
	})
	if persistErr != nil {
		writeError(writer, http.StatusInternalServerError, fmt.Sprintf("could not persist the accepted upgrade: %s", persistErr))
		return
	}

	go runUpgrade(release, state)

	writeStatus(writer, http.StatusAccepted)
}

// Peer authentication
// =====================================================================================================================

func authorizePeer(writer http.ResponseWriter, request *http.Request) bool {
	token := strings.TrimSpace(request.Header.Get(shared.NodeAgentTokenHeader))
	if token == "" {
		writeError(writer, http.StatusUnauthorized, "a maintenance token is required")
		return false
	}

	expected, err := maintenanceToken()
	if err != nil {
		writeError(writer, http.StatusInternalServerError, "the maintenance token could not be read")
		return false
	}

	if subtle.ConstantTimeCompare([]byte(token), []byte(expected)) != 1 {
		writeError(writer, http.StatusForbidden, "the maintenance token was rejected")
		return false
	}

	return true
}

func maintenanceToken() (string, error) {
	data, err := os.ReadFile(maintenanceTokenPath())
	if err != nil {
		return "", err
	}

	token := strings.TrimSpace(string(data))
	if token == "" {
		return "", errors.New("the maintenance token is empty")
	}

	return token, nil
}

// Response helpers
// =====================================================================================================================

func writeStatus(writer http.ResponseWriter, statusCode int) {
	identityConfig := identitySnapshot()

	status, err := statusFromState(identityConfig)
	if err != nil {
		log.Warn("k8s-app node agent: could not read the upgrade state: %s", err)
		writeError(writer, http.StatusInternalServerError, "could not read the upgrade state")
		return
	}

	writer.Header().Set("Content-Type", "application/json")
	writer.WriteHeader(statusCode)
	_ = json.NewEncoder(writer).Encode(status)
}

func writeError(writer http.ResponseWriter, statusCode int, message string) {
	writer.Header().Set("Content-Type", "application/json")
	writer.WriteHeader(statusCode)
	_ = json.NewEncoder(writer).Encode(map[string]string{"error": message})
}

// Command ownership
// =====================================================================================================================
// The fence is consulted on every command and stored before any state transition is accepted. A command is only
// rejected when an upgrade is in flight. A new operation supersedes the recorded one as soon as the node is idle.

func readCommandAuthorization(writer http.ResponseWriter, request *http.Request) (string, bool) {
	operationUid := strings.TrimSpace(request.Header.Get(shared.NodeAgentOperationHeader))
	if operationUid == "" {
		writeError(writer, http.StatusUnauthorized, "the command requires an operation credential")
		return "", false
	}

	return operationUid, true
}

func validateCommandOwnership(operationUid string) error {
	return validateFence(operationUid, shared.NodeAgentPhaseActive(stateLoad().Phase))
}

func validateFence(operationUid string, busy bool) error {
	fence, err := fenceLoad()
	if err != nil {
		return err
	}

	if fence.LastOperation != "" && fence.LastOperation != operationUid && busy {
		return fmt.Errorf(
			"the command comes from operation %s, but this node last obeyed operation %s",
			operationUid,
			fence.LastOperation,
		)
	}

	return nil
}

func acceptOperation(operationUid string) error {
	return fenceStore(operationUid)
}

func fenceLoad() (fenceState, error) {
	data, err := os.ReadFile(fenceStatePath())
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return fenceState{SchemaRevision: fenceSchemaRevision}, nil
		}
		return fenceState{}, err
	}

	var state fenceState
	err = json.Unmarshal(data, &state)
	if err != nil {
		return fenceState{}, fmt.Errorf("the fence state is invalid: %s", err)
	}

	if state.SchemaRevision != fenceSchemaRevision {
		return fenceState{}, errors.New("the fence state was written by an incompatible version of the application")
	}

	return state, nil
}

func fenceStore(operationUid string) error {
	state := fenceState{
		SchemaRevision: fenceSchemaRevision,
		LastOperation:  operationUid,
	}

	data, err := json.MarshalIndent(state, "", "  ")
	if err != nil {
		return err
	}

	return writeFileAtomic(fenceStatePath(), data, 0600)
}

func fenceStatePath() string {
	return filepath.Join(workingDir(), fenceStateName)
}

type fenceState struct {
	SchemaRevision int    `json:"schemaRevision"`
	LastOperation  string `json:"lastOperation"`
}
