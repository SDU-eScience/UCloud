package vm_agent

import (
	"os"
	"strings"

	"gopkg.in/yaml.v3"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

func driveSynchronizeWithFstab() {
	// The base virtiofs mounts must exist before mounting user drives below them. Otherwise a later /work mount can hide
	// user drives that were mounted while /work still referred to the root filesystem.
	stdout, stderr, ok := util.RunCommand([]string{"sudo", "mount", "-a", "-t", "virtiofs"})
	if !ok {
		log.Info("Failed to mount virtiofs entries from fstab: %s %s", stdout, stderr)
	}

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
			continue
		}

		_ = os.MkdirAll(mount[1], 0750)
		_, _, mounted := util.RunCommand([]string{"mountpoint", "-q", mount[1]})
		if mounted {
			continue
		}

		command = []string{"sudo", "mount", "-t", "virtiofs", mount[0], mount[1]}
		stdout, stderr, ok = util.RunCommand(command)
		if !ok {
			log.Info("Failed to run command '%v': %s %s", command, stdout, stderr)
		}
	}
}
