package util

import (
	"bytes"
	"os/exec"
	"strings"
	"ucloud.dk/pkg/log"
)

func RunCommand(arg []string) (string, bool) {
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	cmd := exec.Command(arg[0], arg[1:]...)
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	err := cmd.Run()
	if err != nil {
		s := strings.TrimSpace(stderr.String())
		log.Info("Command failed: %s", s)
	}

	return strings.TrimSpace(stdout.String()), err == nil
}
