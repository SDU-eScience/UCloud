package vm_agent

import (
	"os"
	"strings"

	"gopkg.in/yaml.v3"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

func driveSynchronizeWithFstab() {
	config, _, ok := util.RunCommand([]string{"sudo", "cat", "/etc/ucloud/mounts.yml"})
	if !ok {
		log.Info("No longer allowed to synchronize mounted UCloud drives - This must be done by hand now.")
		return
	}

	var cloudInitDrives struct {
		Mounts [][]string `yaml:"mounts"`
	}
	_ = yaml.Unmarshal([]byte(config), &cloudInitDrives)

	for _, mount := range cloudInitDrives.Mounts {
		if len(mount) != 2 {
			continue
		}

		if !strings.HasPrefix(mount[0], "ucloud-") {
			continue
		}

		command := []string{"sudo", "mkdir", "-p", mount[1]}
		stdout, stderr, ok := util.RunCommand(command)
		if !ok {
			log.Info("Failed to run command '%v': %s %s", command, stdout, stderr)
			return
		}

		_ = os.MkdirAll(mount[1], 0750)
		command = []string{"sudo", "mount", "-t", "virtiofs", mount[0], mount[1]}
		stdout, stderr, ok = util.RunCommand(command)
		if !ok {
			log.Info("Failed to run command '%v': %s %s", command, stdout, stderr)
			return
		}
	}
}
