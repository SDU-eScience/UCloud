package dashboard

import (
	"context"
	"fmt"
	"strings"
	"time"

	corev1 "k8s.io/api/core/v1"

	"ucloud.dk/shared/pkg/ucx"
)

const (
	containerLogMaxBytes   = 128 * 1024
	containerLogFlushDelay = 100 * time.Millisecond
)

var containerLogTailCount int64 = 1000

// syncContainerLogStream ensures that a log stream is running iff the current detail is a container
// logs view. It must be called with app.mu held.
func (app *stackUiApp) syncContainerLogStream() {
	parts := strings.Split(app.ResourceDetail, "/")
	if len(parts) != 4 || parts[0] != containersTypeId {
		app.stopContainerLogsLocked()
		return
	}

	namespace := parts[1]
	pod := parts[2]
	container := parts[3]
	app.startContainerLogsLocked(namespace, pod, container)
}

func (app *stackUiApp) startContainerLogsLocked(namespace string, pod string, container string) {
	target := namespace + "/" + pod + "/" + container
	if app.containerLogTarget == target && app.containerLogCancel != nil {
		return
	}

	app.stopContainerLogsLocked()
	if app.session == nil || app.k8sClient == nil {
		return
	}

	session := app.session
	client := app.k8sClient
	ctx, cancel := context.WithCancel(session.Context())

	app.containerLogGeneration++
	generation := app.containerLogGeneration

	app.ContainerLogs = ""
	app.containerLogTarget = target
	app.containerLogCancel = cancel
	app.containerLogPending = ""
	app.containerLogTruncated = false

	go app.runContainerLogs(ctx, client, target, generation, namespace, pod, container)
}

func (app *stackUiApp) stopContainerLogsLocked() {
	if app.containerLogCancel == nil {
		return
	}

	app.containerLogCancel()
	app.containerLogCancel = nil
	app.containerLogTarget = ""
	app.ContainerLogs = ""
	app.containerLogPending = ""
	app.containerLogTruncated = false

	if app.containerLogFlushTimer != nil {
		app.containerLogFlushTimer.Stop()
		app.containerLogFlushTimer = nil
	}
}

func (app *stackUiApp) runContainerLogs(
	ctx context.Context,
	client *K8sClient,
	target string,
	generation uint64,
	namespace string,
	pod string,
	container string,
) {
	options := &corev1.PodLogOptions{
		Container: container,
		Follow:    true,
		TailLines: &containerLogTailCount,
	}

	reader, err := client.Typed.CoreV1().Pods(namespace).GetLogs(pod, options).Stream(ctx)
	if err != nil {
		if ctx.Err() == nil {
			app.appendContainerLogs(target, generation, fmt.Sprintf("Failed to read container logs: %s\r\n", err))
		}
		return
	}
	defer reader.Close()

	buffer := make([]byte, 32*1024)
	for {
		n, readErr := reader.Read(buffer)
		if n > 0 {
			app.appendContainerLogs(target, generation, string(buffer[:n]))
		}
		if readErr != nil {
			if ctx.Err() == nil {
				app.appendContainerLogs(target, generation, "\r\n[log stream ended]\r\n")
			}
			return
		}
	}
}

func (app *stackUiApp) appendContainerLogs(target string, generation uint64, chunk string) {
	app.mu.Lock()
	if app.containerLogTarget != target || app.containerLogGeneration != generation {
		app.mu.Unlock()
		return
	}

	capped := capContainerLogs(app.ContainerLogs, chunk)
	if capped != app.ContainerLogs+chunk {
		app.containerLogTruncated = true
	}
	app.ContainerLogs = capped
	app.containerLogPending += chunk

	if app.containerLogFlushTimer == nil {
		app.containerLogFlushTimer = time.AfterFunc(containerLogFlushDelay, func() {
			app.mu.Lock()
			app.containerLogFlushTimer = nil
			session := app.session
			logs := app.ContainerLogs
			pending := app.containerLogPending
			truncated := app.containerLogTruncated
			active := app.containerLogTarget != ""
			app.containerLogPending = ""
			app.containerLogTruncated = false
			app.mu.Unlock()

			if session == nil || !active || pending == "" {
				return
			}

			if !truncated && session.SendStringAppend("containerLogs", pending) {
				return
			}

			ucx.AppUpdateModelPatch(session, map[string]ucx.Value{
				"containerLogs": ucx.VString(logs),
			})
		})
	}
	app.mu.Unlock()
}

func capContainerLogs(existing string, chunk string) string {
	text := existing + chunk
	if len(text) <= containerLogMaxBytes {
		return text
	}

	trimmed := text[len(text)-containerLogMaxBytes:]
	if idx := strings.IndexByte(trimmed, '\n'); idx >= 0 {
		trimmed = trimmed[idx+1:]
	}
	return "[... logs truncated ...]\n" + trimmed
}
