package slurm

import (
	"bytes"
	"os/exec"
	"regexp"
	"strings"

	"ucloud.dk/pkg/log"
)

func validateName(s string) bool {
	re := regexp.MustCompile(`^([a-z][a-z0-9_-]+)$`)
	return re.MatchString(s)
}

func runCommand(arg []string) (string, bool) {
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	str := strings.Join(arg[:], " ")
	log.Debug("slurm command: %s", str)

	if true {
		// TODO(Dan): Exit here to make sure we don't accidentally start running commands on Hippo
		return "", false
	}

	cmd := exec.Command("ssh", "hippo-fe", str) // "ssh", "hippo-fe", str ----- "bash", "-c", str
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	err := cmd.Run()
	if err != nil {
		s := strings.TrimSpace(stderr.String())
		log.Debug("slurm command failed: %s", s)
	}

	return strings.TrimSpace(stdout.String()), (err == nil)
}
