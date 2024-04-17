package slurm

import (
	"bytes"
	"os/exec"
	"regexp"
	"strings"

	"ucloud.dk/pkg/log"
)

func validate_name(s string) bool {
	re := regexp.MustCompile(`^([a-z][a-z0-9_-]+)$`)
	return re.MatchString(s)
}

func run_command(arg []string) (string, bool) {
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	str := strings.Join(arg[:], " ")
	log.Debug("slurm command: %s", str)

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
