package shared

import (
	"context"
	"fmt"
	"io"
	"net"
	"os"
	"path/filepath"
	"time"

	cfg "ucloud.dk/pkg/config"
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
	exeCopy("vmagent", dirPath)

	if util.DevelopmentModeEnabled() {
		// NOTE(Dan): The development setup has various issues with connecting the K8s DNS to the Docker Compose DNS.
		// As a result, it is much simpler to just point the VMs directly at the IP address of the integration module.
		ips := func() []net.IP {
			ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer cancel()

			ips, err := net.DefaultResolver.LookupIP(ctx, "ip", cfg.Provider.Hosts.Self.Address)
			if err != nil {
				return nil
			}
			return ips
		}()

		if len(ips) > 0 {
			_ = os.WriteFile(filepath.Join(dirPath, "provider-ip.txt"), []byte(ips[0].String()), 0644)
		}
	}
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
