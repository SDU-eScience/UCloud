package launcher

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"os"
	"os/exec"
	"sync"

	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

type ExecuteOptions struct {
	Environment       []string
	WorkingDir        util.Option[string]
	ContinueOnFailure bool
	Silent            bool
}

type ExecuteResponse struct {
	ExitCode int
	Stdout   string
	Stderr   string
}

func ExecuteWithLog(command []string) (chan string, context.CancelFunc) {
	cmd := exec.Command(command[0], command[1:]...)
	outPipe, err1 := cmd.StdoutPipe()
	errPipe, err2 := cmd.StderrPipe()
	if err1 != nil || err2 != nil {
		log.Fatal("Could not create pipes for command execution: %s %s", err1, err2)
	}

	messageChan := make(chan string, 128)

	wg := sync.WaitGroup{}

	launchReader := func(pipe io.ReadCloser) {
		wg.Add(1)
		go func() {
			defer util.SilentClose(pipe)
			defer wg.Done()

			buf := make([]byte, 1024*8)

			for {
				n, err := pipe.Read(buf)
				if err != nil {
					break
				}

				messageChan <- string(buf[:n])
			}
		}()
	}

	launchReader(outPipe)
	launchReader(errPipe)

	err := cmd.Start()
	if err != nil {
		log.Fatal("Exec: %v: %s", err)
	}

	go func() {
		_ = cmd.Wait()
		close(messageChan)
	}()

	return messageChan, func() {
		_ = cmd.Process.Kill()
	}
}

func StreamingExecute(title string, command []string, opts ExecuteOptions) ExecuteResponse {
	cmd := exec.Command(command[0], command[1:]...)
	cmd.Env = opts.Environment
	if opts.WorkingDir.Present {
		cmd.Dir = opts.WorkingDir.Value
	}

	mutableTitle := title

	outPipe, err1 := cmd.StdoutPipe()
	errPipe, err2 := cmd.StderrPipe()

	if err1 != nil || err2 != nil {
		log.Fatal("Could not create pipes for command execution: %s %s", err1, err2)
	}

	outBuilder := &bytes.Buffer{}
	errBuilder := &bytes.Buffer{}
	messageChan := make(chan string, 128)

	readerWg := sync.WaitGroup{}

	launchReader := func(pipe io.ReadCloser, builder *bytes.Buffer) {
		readerWg.Add(1)
		go func() {
			defer readerWg.Done()

			buf := make([]byte, 1024*8)

			for {
				n, err := pipe.Read(buf)
				if err != nil {
					break
				}

				builder.Write(buf[:n])
				messageChan <- string(buf[:n])
			}
		}()
	}

	launchReader(outPipe, outBuilder)
	launchReader(errPipe, errBuilder)

	consumerDone := make(chan struct{})
	if !opts.Silent {
		if HasPty {
			go func() {
				defer close(consumerDone)
				LogOutputTui(&mutableTitle, messageChan)
			}()
		} else {
			fmt.Println(title)

			go func() {
				defer close(consumerDone)

				for msg := range messageChan {
					fmt.Print(msg)
				}
			}()
		}
	} else {
		go func() {
			defer close(consumerDone)

			for range messageChan {
			}
		}()
	}

	err := cmd.Start()
	if err != nil {
		log.Fatal("Exec: %v: %s", err)
	}

	readersDone := make(chan struct{})
	go func() {
		defer close(readersDone)
		readerWg.Wait()
	}()

	select {
	case <-readersDone:
	case <-consumerDone:
		go func() {
			for range messageChan {
			}
		}()

		readerWg.Wait()
	}

	_ = cmd.Wait()
	code := cmd.ProcessState.ExitCode()

	if !opts.ContinueOnFailure && code != 0 {
		if opts.Silent || !HasPty {
			fmt.Printf("Process failed with exit code %d\n", code)
			os.Exit(1)
		}

		mutableTitle = "❌ " + title

		stream := func(message string) {
			select {
			case messageChan <- message:
			case <-consumerDone:
			}
		}

		stream("\n\n")
		stream(fmt.Sprintf("Process failed with exit code %d\n", code))
		stream(fmt.Sprintf("Command: %#v\n", command))
		if opts.WorkingDir.Present {
			stream(fmt.Sprintf("Dir: %v\n", opts.WorkingDir.Value))
		}
		if len(opts.Environment) > 0 {
			stream(fmt.Sprintf("Env: %#v\n", opts.Environment))
		}
	}

	close(messageChan)
	<-consumerDone

	return ExecuteResponse{
		ExitCode: code,
		Stdout:   outBuilder.String(),
		Stderr:   errBuilder.String(),
	}
}
