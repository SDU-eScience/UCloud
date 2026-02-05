package containers

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	core "k8s.io/api/core/v1"
	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/tools/remotecommand"
	"sync/atomic"
	"time"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/k8s/shared"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/util"
)

type shellResizeQueue struct {
	channel chan remotecommand.TerminalSize
}

func (t *shellResizeQueue) Next() *remotecommand.TerminalSize {
	next, ok := <-t.channel
	if !ok {
		return nil
	} else {
		return &next
	}
}

func handleShell(session *ctrl.ShellSession, cols int, rows int) {
	isNewSession := true
	for util.IsAlive && session.Alive {
		allowRetry := handleShellNoRetry(session, cols, rows, isNewSession)
		isNewSession = false

		if allowRetry {
			if session.Alive && util.IsAlive && session.Folder != "" {
				owner := orc.ResourceOwner{CreatedBy: session.UCloudUsername}
				config := ctrl.RetrieveIAppConfiguration(integratedTerminalAppName, owner)
				if config.Present {
					job, ok := ctrl.RetrieveJob(config.Value.JobId)
					if ok && !job.Status.State.IsFinal() {
						continue
					}
				}
			}
		}

		break
	}
}

func handleShellNoRetry(session *ctrl.ShellSession, cols int, rows int, isNewSession bool) bool {
	jobId := ""
	rank := 0
	waitForJob := false
	lastActivity := &atomic.Int64{}

	if session.Folder != "" {
		driveToMount := util.GetOptionalElement(util.Components(session.Folder), 0).Value

		owner := orc.ResourceOwner{CreatedBy: session.UCloudUsername}
		config := ctrl.RetrieveIAppConfiguration(integratedTerminalAppName, owner)
		var newConfiguration util.Option[iappTermConfig]
		if config.Present {
			var parsedConfig iappTermConfig
			_ = json.Unmarshal(config.Value.Configuration, &parsedConfig)

			job, ok := ctrl.RetrieveJob(config.Value.JobId)
			if !ok || job.Status.State != orc.JobStateRunning {
				newConfiguration.Set(parsedConfig)

				startedAt := job.Status.StartedAt.GetOrDefault(fnd.Timestamp(time.Now())).Time()
				if time.Now().Sub(startedAt) > itermInactivityDuration {
					parsedConfig.Folders = nil
				}
			}

			isMountedAlready := false
			for _, folder := range parsedConfig.Folders {
				fDriveId := util.GetOptionalElement(util.Components(folder), 0).Value

				if driveToMount == fDriveId {
					isMountedAlready = true
					break
				}
			}

			if !isMountedAlready {
				parsedConfig.Folders = append(parsedConfig.Folders, "/"+driveToMount)
				newConfiguration.Set(parsedConfig)
			}
		} else {
			newConfiguration.Set(iappTermConfig{Folders: []string{"/" + driveToMount}})
		}

		if newConfiguration.Present {
			data, _ := json.Marshal(newConfiguration.Value)

			err := ctrl.ConfigureIApp(
				integratedTerminalAppName,
				owner,
				util.OptNone[string](),
				data,
			)

			if err != nil {
				log.Info("Failure while configuring integrated terminal: %s", err)
				return false
			}

			config = ctrl.RetrieveIAppConfiguration(integratedTerminalAppName, owner)
		}

		if !config.Present {
			log.Info("Could not find job after configuring the integrated terminal!")
			return false
		}

		jobId = config.Value.JobId
		rank = 0
		waitForJob = true

		lastActivity = iappGetLastKeyPress(jobId, time.Now())
	} else {
		jobId = session.Job.Id
		rank = session.Rank
		waitForJob = false
	}

	if isNewSession {
		lastActivity.Store(time.Now().UnixMilli())
	}

	podName := idAndRankToPodName(jobId, rank)

	if waitForJob {
		clearScreen := []byte("\033[2J\033[H") // ANSI escape code to clear and move to start
		spinner := []string{".", ":", ":", "."}

		waitCount := 0
		for util.IsAlive && session.Alive {
			job, ok := ctrl.RetrieveJob(jobId)
			if !ok {
				log.Info("No longer able to see integrated terminal job while waiting for it!")
				session.EmitData([]byte("Job is no longer known - Internal error?"))
				return false
			}

			if !isNewSession && time.Now().Sub(time.UnixMilli(lastActivity.Load())) > itermInactivityDuration {
				session.EmitData(clearScreen)
				session.EmitData([]byte("Your session has expired. Please open a new one.\r\n"))
				return false
			}

			if job.Status.State.IsFinal() {
				session.EmitData(clearScreen)
				session.EmitData([]byte("Job is no longer available - Internal error?"))
				return false
			}

			if reason := shared.IsJobLocked(job); reason.Present {
				session.EmitData(clearScreen)
				session.EmitData([]byte(fmt.Sprintf("Unable to open a new terminal: %s.\r\n", reason.Value.Reason)))
				return false
			}

			if job.Status.State == orc.JobStateRunning {
				if session.Folder != "" {
					// NOTE(Dan): The following check ensures that this terminal session does not start before the
					// folder requested is actually ready. We do this by retrieving the latest iapp configuration and
					// checking if the handler believes that this is configuration is sufficient for it to run.
					kctx, kcancel := context.WithTimeout(context.Background(), 3*time.Second)
					pod, err := K8sClient.CoreV1().Pods(Namespace).Get(kctx, podName, meta.GetOptions{})
					kcancel()

					if err == nil {
						activeIApps := ctrl.RetrieveIAppsByJobId()
						iappConfig, iappOk := activeIApps[job.Id]
						handler, handlerOk := IApps[integratedTerminalAppName]

						iappEtag := util.OptMapGet(pod.Annotations, IAppAnnotationEtag)

						shouldRun := handlerOk &&
							iappOk &&
							iappEtag.Present &&
							handler.ShouldRun(job, iappConfig.Configuration) &&
							iappEtag.Value == iappConfig.ETag

						if shouldRun {
							break
						}
					}
				} else {
					break
				}
			}

			if waitCount%(len(spinner)*10) == 0 {
				if waitCount != 0 {
					session.EmitData([]byte("\r\n"))
				} else {
					session.EmitData(clearScreen)
				}
				session.EmitData([]byte("A terminal is being prepared for you...\r\n"))
			}
			session.EmitData([]byte(spinner[waitCount%len(spinner)]))

			// NOTE(Dan): Do not store activity here, it will go into an infinite loop where it will start and stop.
			waitCount++
			time.Sleep(500 * time.Millisecond)
		}

		session.EmitData(clearScreen)
	}

	if !session.Alive {
		return false
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	stdinReader, stdinWriter := io.Pipe()
	stdoutReader, stdoutWriter := io.Pipe()
	stderrReader, stderrWriter := io.Pipe()

	streamStop := make(chan error)
	writeStop := make(chan error)
	readOutStop := make(chan error)
	readErrStop := make(chan error)

	resizeChannel := make(chan remotecommand.TerminalSize)

	command := []string{"/bin/bash"}

	execRequest := K8sClient.CoreV1().RESTClient().
		Post().
		Resource("pods").
		Name(podName).
		Namespace(Namespace).
		SubResource("exec").
		VersionedParams(&core.PodExecOptions{
			Stdin:     true,
			Stdout:    true,
			Stderr:    true,
			TTY:       true,
			Container: ContainerUserJob,
			Command:   command,
		}, ExecCodec)

	exec, err := remotecommand.NewSPDYExecutor(K8sConfig, "POST", execRequest.URL())

	if err != nil {
		return false
	}

	go func() {
		defer close(readOutStop)
		buf := make([]byte, 1024*4)

	outer:
		for session.Alive {
			select {
			case <-ctx.Done():
				break outer
			default:
			}

			n, err := stdoutReader.Read(buf)
			if err != nil {
				break
			}

			session.EmitData(buf[:n])
		}
	}()

	go func() {
		defer close(readErrStop)
		buf := make([]byte, 1024*4)

	outer:
		for session.Alive {
			select {
			case <-ctx.Done():
				break outer
			default:
			}

			n, err := stderrReader.Read(buf)
			if err != nil {
				break
			}

			session.EmitData(buf[:n])
		}
	}()

	go func() {
		defer close(writeStop)

	outer:
		for util.IsAlive && session.Alive {
			select {
			case event := <-session.InputEvents:
				switch event.Type {
				case ctrl.ShellEventTypeInput:
					lastActivity.Store(time.Now().UnixMilli())
					_, err = stdinWriter.Write([]byte(event.Data))
					if err != nil {
						session.Alive = false
						break
					}

				case ctrl.ShellEventTypeResize:
					lastActivity.Store(time.Now().UnixMilli())
					resizeChannel <- remotecommand.TerminalSize{
						Width:  uint16(event.Cols),
						Height: uint16(event.Rows),
					}
				}

			case <-ctx.Done():
				break outer

			case _ = <-time.After(1 * time.Second):
				continue
			}
		}
	}()

	go func() {
		select {
		case <-streamStop:
		case <-writeStop:
		case <-readOutStop:
		case <-readErrStop:
		}

		cancel()
	}()

	go func() {
		resizeChannel <- remotecommand.TerminalSize{
			Width:  uint16(cols),
			Height: uint16(rows),
		}
	}()

	// This will only return if the stream ends
	_ = exec.StreamWithContext(ctx, remotecommand.StreamOptions{
		Stdin:             stdinReader,
		Stdout:            stdoutWriter,
		Stderr:            stderrWriter,
		Tty:               true,
		TerminalSizeQueue: &shellResizeQueue{channel: resizeChannel},
	})

	return true
}
