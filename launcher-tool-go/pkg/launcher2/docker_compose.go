package launcher2

import (
	"fmt"
	"os"
	"path/filepath"
)

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
		"--project-directory",
		ComposeDir,
	}
}
