package vm_agent

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"gopkg.in/yaml.v3"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

func driveSynchronizeWithFstab() {
	config, _, ok := util.RunCommand([]string{"sudo", "cat", "/var/lib/cloud/instance/cloud-config.txt"})
	if !ok {
		log.Info("No longer allowed to synchronize mounted UCloud drives - This must be done by hand now.")
		return
	}

	var cloudInitDrives struct {
		Mounts [][]string `yaml:"mounts"`
	}
	_ = yaml.Unmarshal([]byte(config), &cloudInitDrives)

	currentFstab, err := os.ReadFile("/etc/fstab")
	if err != nil {
		log.Info("Could not read current fstab, aborting: %s", err)
		return
	}

	newFstab := &strings.Builder{}
	for _, line := range strings.Split(string(currentFstab), "\n") {
		if strings.HasPrefix(line, "ucloud-") {
			continue
		}
		newFstab.WriteString(line + "\n")
	}

	for _, mount := range cloudInitDrives.Mounts {
		if len(mount) != 6 {
			continue
		}

		if !strings.HasPrefix(mount[0], "ucloud-") {
			continue
		}

		_ = os.MkdirAll(mount[1], 0750)

		newFstab.WriteString(fmt.Sprintf("%s %s %s %s %s %s\n", mount[0], mount[1], mount[2], mount[3], mount[4], mount[5]))
	}

	tempFstabPath := filepath.Join(os.TempDir(), util.SecureToken())
	err = os.WriteFile(tempFstabPath, []byte(newFstab.String()), 0644)
	if err != nil {
		log.Info("Failed to create new fstab: %s", err)
		return
	}

	sudoCommands := [][]string{
		{"sudo", "chown", "root:root", tempFstabPath},
		{"sudo", "mv", tempFstabPath, "/etc/fstab"},
		{"sudo", "systemctl", "daemon-reload"},
		{"sudo", "mount", "-a"},
	}

	for _, command := range sudoCommands {
		stdout, stderr, ok := util.RunCommand(command)
		if !ok {
			log.Info("Failed to run command '%v': %s %s", command, stdout, stderr)
			return
		}
	}
}
