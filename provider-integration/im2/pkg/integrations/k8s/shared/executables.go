package shared

import (
	"fmt"
	"io"
	"os"
	"path/filepath"

	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

func InitExecutables() {
	// Small utility function to put ucmetrics, ucviz and similar into a shared directory which can be mounted by jobs
	// to gain access to these applications.
	conf := ServiceConfig
	dirPath := filepath.Join(conf.FileSystem.MountPoint, ExecutablesDir)
	err := os.MkdirAll(dirPath, 0755)
	if err != nil {
		log.Fatal("Failed to create UCloud exe dir: %s", err)
	}

	exeCopy("ucloud", dirPath)
	exeCopy("ucmetrics", dirPath)
	exeCopy("ucviz", dirPath)
}

func exeCopy(name string, targetDir string) {
	sourceFd, err := os.OpenFile(fmt.Sprintf("/usr/bin/%s", name), os.O_RDONLY, 0)
	if err != nil {
		log.Fatal("Failed to open file at /usr/bin/%s: %s", name, err)
	}

	defer util.SilentClose(sourceFd)

	tempFile := filepath.Join(targetDir, util.SecureToken())
	targetFd, err := os.OpenFile(tempFile, os.O_WRONLY|os.O_TRUNC|os.O_CREATE, 0755)
	if err != nil {
		log.Fatal("Failed to open file at %s: %s", tempFile, err)
	}

	_, err = io.Copy(targetFd, sourceFd)
	util.SilentClose(targetFd)
	if err != nil {
		log.Fatal("Failed to copy file at %s: %s", tempFile, err)
	}

	err = os.Rename(tempFile, filepath.Join(targetDir, name))
	if err != nil {
		log.Fatal("Failed to replace file at %s: %s", tempFile, err)
	}
}

const ExecutablesDir = "ucloud-exe"
