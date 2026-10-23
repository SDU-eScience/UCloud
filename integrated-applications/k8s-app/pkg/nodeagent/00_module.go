// Introduction
// =====================================================================================================================
// This package implements the node agent of the UCloud Kubernetes stack. The agent is a daemon. It runs as root on
// every node of a cluster. The maintenance coordinator on a control plane node drives the agent over the private
// network. The agent performs the work that needs root access on the node itself. The main responsibilities are:
//
// - Establishing the identity of the node from its input mount
// - Serving the maintenance API on the private address of the node
// - Authenticating commands from the coordinator with a shared maintenance token
// - Rejecting commands from coordinator operations that the node has superseded
// - Upgrading the Kubernetes binary and restarting the service
// - Updating the operating system and rebooting when the kernel changes
// - Writing control-plane backups to the backups mount
// - Reporting progress to the dashboard through the work mount
//
// The package is split into the files below. Read this file first. It explains the process and the trust model.
//
// - 00_module.go: 	starts the agent, establishes identity and serves the API.
// - api.go: 		contains the HTTP endpoints and the command checks.
// - upgrade.go: 	runs the Kubernetes upgrade and writes control-plane backups.
// - os_update.go: 	runs the operating-system update from check to verification.
// - logs.go: 		reports progress and serves the node log endpoints.
//
// Lifecycle
// ---------------------------------------------------------------------------------------------------------------------
// systemd starts the agent with the agent subcommand. The agent reads its identity. It recovers the state of an
// interrupted upgrade or operating-system update. It then serves until the process stops. The agent does not exit on
// its own during normal operation. A failed operation leaves its state on disk. An operator can then retry it.
//
// Identity and trust
// ---------------------------------------------------------------------------------------------------------------------
// The identity of the node comes from the input mount. The provisioner fills the mount before the node boots. The
// identity fixes the name, the address and the role of the node. The agent refuses to start if the recorded address
// is not assigned to the node. A wrong address would make the agent unreachable on the network it must serve.
//
// Trust is one-directional. The coordinator proves itself to the node with the maintenance token. Each command also
// carries the uid of the maintenance operation that owns it. The node learns nothing about the coordinator beyond
// these two facts.
//
// Command ownership
// ---------------------------------------------------------------------------------------------------------------------
// The coordinator may crash and restart while an operation runs. A restarted coordinator creates a new operation with
// a new uid. The fence records the uid of the newest operation that the node has obeyed. The agent rejects a command
// from an older operation while an upgrade or an operating-system update is in flight. This stops a paused
// coordinator from issuing stale commands when it resumes.
//
// The fence and the operation state are separate files on purpose. The fence answers who may command the node. The
// state answers what the node is doing. The fence survives every operation. The state is rewritten as the operation
// moves through its phases.
//
// State files
// ---------------------------------------------------------------------------------------------------------------------
// The agent keeps its state under /var/lib/ucloud-k8s/maintenance. The upgrade state records the Kubernetes release
// and its phase. The operating-system state records the package counts, the kernel and its phase. The fence records
// the last accepted operation. A write replaces the old file in one step, so a reader never sees a partial file. A
// state file that another version of the application wrote is reported as a recovery case and is not used.
//
// Concurrency
// ---------------------------------------------------------------------------------------------------------------------
// The handlers serialize command processing with one command mutex. The status endpoints take the same mutex. A status
// read therefore never observes a half-written command decision. The work itself runs in a background goroutine. The
// goroutine reports through the state file and the progress file. It takes the state mutex for each read and write,
// which keeps every state transition atomic for readers.

package nodeagent

import (
	"context"
	"crypto/subtle"
	"encoding/json"
	"errors"
	"fmt"
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
)

// Constants and package state
// =====================================================================================================================
// These constants name the state files and the input mount. The timeouts bound the HTTP server. The package state
// holds the node identity and the locks that serialize commands and state writes.

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
	BackupMu  sync.Mutex
}

// Configuration
// =====================================================================================================================
// Config holds the identity of the node after startup. nodeFile matches the JSON file that the provisioner writes
// into the input mount.

type Config struct {
	NodeName   string
	IpAddress  string
	Role       string
	K8sVersion string
}

type nodeFile struct {
	Hostname    string `json:"hostname"`
	IpAddress   string `json:"ipAddress"`
	Role        string `json:"role"`
	FirstServer bool   `json:"firstServer"`
	K8sVersion  string `json:"k8sVersion"`
}

func identitySnapshot() Config {
	globals.Mu.Lock()
	defer globals.Mu.Unlock()
	return globals.Identity
}

// Serving
// =====================================================================================================================
// The agent serves the status endpoints, the command endpoints and the log endpoints. The coordinator is the only
// client. Only the status endpoints accept GET. Every other endpoint accepts POST.
//
// The server listens on the private address of the node. It stops when the context is cancelled. The agent starts
// the token publisher on a control plane node before it serves.

func Serve(ctx context.Context) error {
	identityConfig, err := Identity(ctx)
	if err != nil {
		return err
	}

	globals.Mu.Lock()
	globals.Identity = identityConfig
	globals.Mu.Unlock()

	recoverInterrupted()
	recoverInterruptedOs()

	if identityConfig.Role == shared.GroupControlPlane {
		go startTokenPublisher()
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/status", requirePeer(http.MethodGet, handleStatus))
	mux.HandleFunc("/upgrade", requirePeer(http.MethodPost, handleUpgrade))
	mux.HandleFunc("/retry", requirePeer(http.MethodPost, handleRetry))
	mux.HandleFunc("/backup", requirePeer(http.MethodPost, handleBackup))
	mux.HandleFunc("/os/status", requirePeer(http.MethodGet, handleOsStatus))
	mux.HandleFunc("/os/update", requirePeer(http.MethodPost, handleOsUpdate))
	mux.HandleFunc("/log", requirePeer(http.MethodPost, handleLog))
	mux.HandleFunc("/log/reset", requirePeer(http.MethodPost, handleLogReset))

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
// The agent reads node.json from the input mount. It checks the address and the role. It then waits until the address
// is assigned to one of the local interfaces. The wait matters at boot, because the private address can appear after
// the process starts.

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
		NodeName:   nodeJson.Hostname,
		IpAddress:  nodeJson.IpAddress,
		Role:       nodeJson.Role,
		K8sVersion: nodeJson.K8sVersion,
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

	if nodeJson.Hostname == "" {
		return nodeFile{}, fmt.Errorf("the node configuration at %s has no hostname", path)
	}

	address, err := netip.ParseAddr(nodeJson.IpAddress)
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
	address, err := netip.ParseAddr(ipAddress)
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

// Peer authentication
// =====================================================================================================================
// Every request must carry the maintenance token in its header. The agent compares the token in constant time. A
// missing token and a wrong token have different status codes. Both stop the request.

func requirePeer(method string, next func(http.ResponseWriter, *http.Request)) http.HandlerFunc {
	return func(writer http.ResponseWriter, request *http.Request) {
		if !authorizePeer(writer, request) {
			return
		}
		if request.Method != method {
			message := "this endpoint only accepts POST"
			if method == http.MethodGet {
				message = "method not allowed"
			}
			writeError(writer, http.StatusMethodNotAllowed, message)
			return
		}
		next(writer, request)
	}
}

func authorizePeer(writer http.ResponseWriter, request *http.Request) bool {
	token := request.Header.Get(shared.NodeAgentTokenHeader)
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

// Command ownership
// =====================================================================================================================
// The fence is a small file next to the operation state. It holds the uid of the last operation that the node obeyed.
// The agent reads the operation uid from the request header. A command from another uid is rejected while an
// operation is active. When the node is idle, the next operation replaces the recorded uid.
//
// The handlers validate the fence before they write any state. They store the accepted uid before the work starts.

type fenceState struct {
	SchemaRevision int    `json:"schemaRevision"`
	LastOperation  string `json:"lastOperation"`
}

func fenceStatePath() string {
	return filepath.Join(stagingDir, fenceStateName)
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

func readCommandAuthorization(writer http.ResponseWriter, request *http.Request) (string, bool) {
	operationUid := request.Header.Get(shared.NodeAgentOperationHeader)
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
