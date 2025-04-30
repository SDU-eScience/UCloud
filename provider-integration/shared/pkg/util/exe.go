package util

import (
	"bytes"
	"os/exec"
	"strings"
)

func RunCommand(arg []string) (string, string, bool) {
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	cmd := exec.Command(arg[0], arg[1:]...)
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	err := cmd.Run()

	stdoutResult := strings.TrimSpace(stdout.String())
	stderrResult := strings.TrimSpace(stderr.String())

	return stdoutResult, stderrResult, err == nil
}
