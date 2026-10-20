package nodeagent

import (
	"bufio"
	"context"
	"errors"
	"io"
	"os"
	"os/exec"
	"strings"
	"time"

	"ucloud.dk/iapp/k8s/pkg/shared"
)

func osRunPackageCommand(ctx context.Context, timeout time.Duration, env []string, args ...string) error {
	commandCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	reader, writer := io.Pipe()
	command := exec.CommandContext(commandCtx, "apt-get", args...)
	command.Env = append(os.Environ(), env...)
	command.Env = append(command.Env, "LC_ALL=C")
	command.Stdout = writer
	command.Stderr = writer
	command.WaitDelay = 5 * time.Second
	lastLine := ""
	done := make(chan struct{})
	go func() {
		defer close(done)
		scanner := bufio.NewScanner(reader)
		scanner.Buffer(make([]byte, 4096), 256*1024)
		for scanner.Scan() {
			for _, part := range strings.Split(scanner.Text(), "\r") {
				line := strings.TrimSpace(part)
				if line == "" {
					continue
				}
				line = shared.NodeAgentTruncateLine(line)
				progressText(line)
				lastLine = line
			}
		}
		if scanner.Err() != nil {
			progressText("Some package output could not be displayed")
			_, _ = io.Copy(io.Discard, reader)
		}
	}()
	err := command.Run()
	_ = writer.Close()
	<-done
	_ = reader.Close()
	if err != nil {
		if lastLine == "" {
			lastLine = "the package manager did not report a reason"
		}
		return errors.New(lastLine)
	}
	return nil
}
