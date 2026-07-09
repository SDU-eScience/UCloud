package containers

import (
	"bytes"
	"context"
	"errors"
	"strings"
	"sync"
	"time"

	core "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/tools/remotecommand"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/shared"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

type terminalBackend struct{}

type terminalCommandHandle struct {
	cancel context.CancelFunc
	done   chan struct{}

	resize chan remotecommand.TerminalSize

	mutex sync.Mutex
	err   *util.HttpError
	tty   bool
}

func (h *terminalCommandHandle) Wait() {
	<-h.done
}

func (h *terminalCommandHandle) Resize(cols, rows int) {
	if !h.tty || h.resize == nil {
		return
	}

	size := remotecommand.TerminalSize{Width: uint16(cols), Height: uint16(rows)}
	select {
	case h.resize <- size:
		return
	default:
	}

	select {
	case <-h.resize:
	default:
	}

	select {
	case h.resize <- size:
	default:
	}
}

func (h *terminalCommandHandle) Kill() {
	h.cancel()
}

func (h *terminalCommandHandle) Err() *util.HttpError {
	h.mutex.Lock()
	defer h.mutex.Unlock()
	return h.err
}

func (h *terminalCommandHandle) fail(err *util.HttpError) {
	if err == nil {
		return
	}

	h.mutex.Lock()
	if h.err == nil {
		h.err = err
	}
	h.mutex.Unlock()
}

func (backend *terminalBackend) Start(cmd *shared.TerminalCmd) (shared.TerminalCommandHandle, *util.HttpError) {
	if cmd == nil {
		return nil, util.ServerHttpError("terminal command is not available")
	}
	if cmd.Sandbox == nil {
		return nil, util.ServerHttpError("terminal sandbox is not available")
	}
	if cmd.Path == "" {
		return nil, util.UserHttpError("command name must not be empty")
	}

	podName := idAndRankToPodName(cmd.Sandbox.JobId, 0)
	if err := waitForTerminalCommandTarget(cmd, podName); err != nil {
		return nil, err
	}
	command, err := buildTerminalCommand(cmd)
	if err != nil {
		return nil, err
	}

	stdin := cmd.Stdin

	stdout := cmd.Stdout

	stderr := cmd.Stderr

	ctx, cancel := context.WithCancel(context.Background())
	handle := &terminalCommandHandle{
		cancel: cancel,
		done:   make(chan struct{}),
		tty:    cmd.TTY,
	}

	streamOptions := remotecommand.StreamOptions{
		Stdin:  stdin,
		Stdout: stdout,
		Stderr: stderr,
		Tty:    cmd.TTY,
	}
	if cmd.TTY {
		resizeChannel := make(chan remotecommand.TerminalSize, 8)
		handle.resize = resizeChannel
		streamOptions.TerminalSizeQueue = &shellResizeQueue{channel: resizeChannel}
		resizeChannel <- remotecommand.TerminalSize{Width: uint16(cmd.Cols), Height: uint16(cmd.Rows)}
	}

	execRequest := K8sClient.CoreV1().RESTClient().
		Post().
		Resource("pods").
		Name(podName).
		Namespace(Namespace).
		SubResource("exec").
		VersionedParams(&core.PodExecOptions{
			Stdin:     cmd.Stdin != nil,
			Stdout:    cmd.Stdout != nil,
			Stderr:    cmd.Stderr != nil,
			TTY:       cmd.TTY,
			Container: ContainerUserJob,
			Command:   command,
		}, ExecCodec)

	exec, execErr := remotecommand.NewSPDYExecutor(K8sConfig, "POST", execRequest.URL())
	if execErr != nil {
		cancel()
		return nil, util.HttpErrorFromErr(execErr)
	}

	go func() {
		defer close(handle.done)
		defer cancel()

		execErr := exec.StreamWithContext(ctx, streamOptions)
		if execErr != nil && !errors.Is(execErr, context.Canceled) {
			handle.fail(util.HttpErrorFromErr(execErr))
		}
	}()

	return handle, nil
}

func waitForTerminalCommandTarget(cmd *shared.TerminalCmd, podName string) *util.HttpError {
	deadline := time.Now().Add(5 * time.Minute)
	for time.Now().Before(deadline) {
		job, ok := controller.JobRetrieve(cmd.Sandbox.JobId)
		if !ok {
			return util.UserHttpError("sandbox job not found")
		}
		if job.Status.State.IsFinal() {
			return util.UserHttpError("sandbox job is no longer running")
		}
		if reason := shared.IsJobLocked(job); reason.Present {
			if reason.Value.Err != nil {
				return reason.Value.Err
			}
			return util.UserHttpError("%s", reason.Value.Reason)
		}

		pod, err := K8sClient.CoreV1().Pods(Namespace).Get(context.Background(), podName, metav1.GetOptions{})
		if err == nil && pod.Status.Phase == core.PodRunning {
			for _, status := range pod.Status.ContainerStatuses {
				if status.Name == ContainerUserJob && status.Ready {
					return nil
				}
			}
		}

		time.Sleep(2 * time.Second)
	}
	return util.UserHttpError("sandbox did not become ready before the command timed out")
}

func buildTerminalCommand(cmd *shared.TerminalCmd) ([]string, *util.HttpError) {
	if len(cmd.Args) == 0 {
		return []string{cmd.Path}, nil
	}

	if cmd.Dir == "" && len(cmd.Env) == 0 {
		return append([]string{}, cmd.Args...), nil
	}

	var script bytes.Buffer
	if cmd.Dir != "" {
		script.WriteString("cd ")
		script.WriteString(orcapi.EscapeBash(cmd.Dir))
		script.WriteString(" && ")
	}

	for _, env := range cmd.Env {
		key, value, ok := strings.Cut(env, "=")
		if !ok || key == "" {
			return nil, util.UserHttpError("invalid environment entry: %s", env)
		}

		script.WriteString("export ")
		script.WriteString(key)
		script.WriteString("=")
		script.WriteString(orcapi.EscapeBash(value))
		script.WriteString(" && ")
	}

	script.WriteString("exec ")
	script.WriteString(orcapi.EscapeBash(cmd.Path))
	for _, arg := range cmd.Args[1:] {
		script.WriteString(" ")
		script.WriteString(orcapi.EscapeBash(arg))
	}

	return []string{"/bin/bash", "-lc", script.String()}, nil
}
