package launcher

import (
	"fmt"
	"net"
	"os"
	"path/filepath"
	"strings"
)

const ComposeProjectName = "compose"

func ComposeHostDnsServers() []string {
	var result []string
	seen := map[string]bool{}

	for _, path := range []string{"/run/systemd/resolve/resolv.conf", "/etc/resolv.conf"} {
		content, err := os.ReadFile(path)
		if err != nil {
			continue
		}

		for _, line := range strings.Split(string(content), "\n") {
			fields := strings.Fields(line)
			if len(fields) < 2 || fields[0] != "nameserver" {
				continue
			}

			address := fields[1]
			ip := net.ParseIP(address)
			if ip == nil || ip.IsLoopback() || seen[address] {
				continue
			}

			seen[address] = true
			result = append(result, address)
		}

		if len(result) > 0 {
			break
		}
	}

	return result
}

func ComposeExec(title string, containerName string, command []string, opts ExecuteOptions) ExecuteResponse {
	execCommand := ComposeExecCommand(containerName, command, false)
	return StreamingExecute(title, execCommand, opts)
}

func ComposeExecCommand(containerName string, command []string, tty bool) []string {
	result := ComposeBaseCommand()
	result = append(result, "exec")
	if !tty {
		result = append(result, "-T")
	}
	result = append(result, containerName)
	result = append(result, command...)

	return result
}

func ComposeUp(noRecreate bool) {
	command := ComposeBaseCommand()
	command = append(command, []string{"up", "-d"}...)
	if noRecreate {
		command = append(command, "--no-recreate")
	}

	StreamingExecute("Starting compose cluster", command, ExecuteOptions{})
}

func ComposeDown(delete bool) {
	command := ComposeBaseCommand()
	command = append(command, []string{"down"}...)
	if delete {
		command = append(command, "-v")
	}

	title := "Stopping compose cluster"
	if delete {
		title = "Deleting compose cluster"
	}
	StreamingExecute(title, command, ExecuteOptions{})
}

func ComposeClusterExists() bool {
	_, err := os.Stat(filepath.Join(ComposeDir, "docker-compose.yaml"))
	return err == nil
}

func ComposeStartContainer(container string, silent bool) {
	command := ComposeBaseCommand()
	command = append(command, []string{"start", container}...)
	StreamingExecute(fmt.Sprintf("Starting container (%s)", container), command, ExecuteOptions{Silent: silent})
}

func ComposeStopContainer(container string, silent bool) {
	command := ComposeBaseCommand()
	command = append(command, []string{"stop", container}...)
	StreamingExecute(fmt.Sprintf("Stopping container (%s)", container), command, ExecuteOptions{Silent: silent})
}

func ComposeBaseCommand() []string {
	return []string{
		"docker",
		"compose",
		"--project-name",
		ComposeProjectName,
		"--project-directory",
		ComposeDir,
	}
}

func ComposeDeleteProjectData() {
	StreamingExecute(
		"Deleting containers",
		[]string{"sh", "-c", fmt.Sprintf("docker ps -aq --filter label=com.docker.compose.project=%s | xargs -r docker rm -f", ComposeProjectName)},
		ExecuteOptions{},
	)

	StreamingExecute(
		"Deleting volumes",
		[]string{"sh", "-c", fmt.Sprintf("docker volume ls -q --filter label=com.docker.compose.project=%s | xargs -r docker volume rm -f", ComposeProjectName)},
		ExecuteOptions{},
	)

	StreamingExecute(
		"Deleting networks",
		[]string{"sh", "-c", fmt.Sprintf("docker network ls -q --filter label=com.docker.compose.project=%s | xargs -r docker network rm", ComposeProjectName)},
		ExecuteOptions{ContinueOnFailure: true},
	)
}
