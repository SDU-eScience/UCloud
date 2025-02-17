package containers

import (
	"context"
	"io"
	core "k8s.io/api/core/v1"
	"k8s.io/client-go/tools/remotecommand"
	"time"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
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

	podName := idAndRankToPodName(session.Job.Id, session.Rank)
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
			Container: "user-job",
			Command:   command,
		}, ExecCodec)

	exec, err := remotecommand.NewSPDYExecutor(K8sConfig, "POST", execRequest.URL())

	if err != nil {
		return
	}

	go func() {
		defer close(readOutStop)
		buf := make([]byte, 1024*4)

		for session.Alive {
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

		for session.Alive {
			n, err := stderrReader.Read(buf)
			if err != nil {
				break
			}

			session.EmitData(buf[:n])
		}
	}()

	go func() {
		defer close(writeStop)

		for util.IsAlive && session.Alive {
			select {
			case event := <-session.InputEvents:
				switch event.Type {
				case ctrl.ShellEventTypeInput:
					_, err = stdinWriter.Write([]byte(event.Data))
					if err != nil {
						session.Alive = false
						log.Info("Error while writing to master: %v", err)
						break
					}

				case ctrl.ShellEventTypeResize:
					resizeChannel <- remotecommand.TerminalSize{
						Width:  uint16(event.Cols),
						Height: uint16(event.Rows),
					}
				}

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
}
