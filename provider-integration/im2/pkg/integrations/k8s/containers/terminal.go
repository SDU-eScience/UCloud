package containers

import (
	"context"
	"fmt"
	"io"
	"sync/atomic"
	"time"

	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/tools/remotecommand"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/shared"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
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

func handleShell(session *controller.ShellSession, cols int, rows int) {
	isNewSession := true
	for util.IsAlive && session.Alive {
		allowRetry := handleShellNoRetry(session, cols, rows, isNewSession)
		isNewSession = false

		if allowRetry {
			if session.Alive && util.IsAlive && session.Folder != "" {
				owner := orc.ResourceOwner{CreatedBy: session.UCloudUsername}
				config := controller.IAppRetrieveConfiguration(integratedTerminalAppName, owner)
				if config.Present {
					job, ok := controller.JobRetrieve(config.Value.JobId)
					if ok && !job.Status.State.IsFinal() {
						continue
					}
				}
			}
		}

		break
	}
}

func handleShellNoRetry(session *controller.ShellSession, cols int, rows int, isNewSession bool) bool {
	jobId := ""
	rank := 0
	waitForJob := false
	lastActivity := &atomic.Int64{}
	var sandbox *shared.TerminalSandbox

	if session.Folder != "" {
		driveToMount := util.GetOptionalElement(util.Components(session.Folder), 0).Value

		owner := orc.ResourceOwner{CreatedBy: session.UCloudUsername}
		var err *util.HttpError
		sandbox, err = shared.TerminalOpen(owner, []string{"/" + driveToMount})
		if err != nil {
			log.Info("Failure while configuring integrated terminal: %s", err)
			return false
		}

		jobId = sandbox.JobId
		rank = 0
		waitForJob = true

		lastActivity.Store(time.Now().UnixMilli())
	} else {
		var err *util.HttpError
		sandbox, err = shared.TerminalOpenToJob(session.Job.Id)
		if err != nil {
			log.Info("Failure while configuring integrated terminal: %s", err)
			return false
		}

		jobId = sandbox.JobId
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
			job, ok := controller.JobRetrieve(jobId)
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
						activeIApps := controller.IAppRetrieveAllByJobId()
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

	if len(sandbox.Warnings) > 0 {
		for _, warning := range sandbox.Warnings {
			session.EmitData([]byte(fmt.Sprintf("Warning: %s\r\n", warning)))
		}
		session.EmitData([]byte("\r\n"))
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	stdinReader, stdinWriter := io.Pipe()
	stdoutReader, stdoutWriter := io.Pipe()
	stderrReader, stderrWriter := io.Pipe()

	cmd := sandbox.Command("/bin/bash")
	cmd.Stdin = stdinReader
	cmd.Stdout = stdoutWriter
	cmd.Stderr = stderrWriter
	cmd.TTY = true
	cmd.Cols = cols
	cmd.Rows = rows
	cmd.Start()
	if cmd.Err() != nil {
		log.Info("Failed to start integrated terminal command: %s", cmd.Err())
		return false
	}

	commandDone := make(chan util.Empty)
	go func() {
		cmd.Wait()
		close(commandDone)
	}()

	go func() {
		buf := make([]byte, 1024*4)
		for session.Alive {
			select {
			case <-ctx.Done():
				return
			default:
			}

			n, err := stdoutReader.Read(buf)
			if err != nil {
				return
			}

			session.EmitData(buf[:n])
		}
	}()

	go func() {
		buf := make([]byte, 1024*4)
		for session.Alive {
			select {
			case <-ctx.Done():
				return
			default:
			}

			n, err := stderrReader.Read(buf)
			if err != nil {
				return
			}

			session.EmitData(buf[:n])
		}
	}()

	go func() {
		for util.IsAlive && session.Alive {
			select {
			case event := <-session.InputEvents:
				switch event.Type {
				case controller.ShellEventTypeInput:
					lastActivity.Store(time.Now().UnixMilli())
					if session.Folder != "" {
						_ = shared.TerminalLease(sandbox.Owner, itermInactivityDuration)
					}
					if _, err := stdinWriter.Write([]byte(event.Data)); err != nil {
						session.Alive = false
						cmd.Kill()
						return
					}

				case controller.ShellEventTypeResize:
					lastActivity.Store(time.Now().UnixMilli())
					cmd.Resize(event.Cols, event.Rows)
				}

			case <-ctx.Done():
				return

			case <-time.After(1 * time.Second):
				continue
			}
		}
	}()

	go func() {
		select {
		case <-commandDone:
		case <-ctx.Done():
		}
		cancel()
	}()

	go func() {
		<-ctx.Done()
		cmd.Kill()
	}()

	<-commandDone
	return true
}
