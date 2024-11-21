package launcher

import (
	"log"
	"strings"
)

type DockerCompose interface {
	Up(directory LFile, noRecreate bool)
	Down(directory LFile, noRecreate bool)
	Ps(directory LFile)
	Logs(directory LFile, container string)
	Start(directory LFile, container string)
	Stop(directory LFile, container string)
	Exec(directory LFile, container string, command []string, tty bool)
}

type abstractDockerCompose struct{ DockerCompose }

type Classic struct{ abstractDockerCompose }

func (c Classic) Base(directory LFile) {
}

func (c Classic) Up(directory LFile, noRecreate bool) {

}

func (c Classic) Down(directory LFile, deleteVolumes bool) {

}

func (c Classic) Ps(directory LFile) {

}

func (c Classic) Logs(directory LFile, container string) {

}

func (c Classic) Exec(directory LFile, container string, command []string, tty bool) {

}

func (c Classic) Start(directory LFile, container string) {

}

func (c Classic) Stop(directory LFile, container string) {

}

func NewClassicCompose(exe string) *Classic {
	classic := Classic{abstractDockerCompose{}}
	return &classic
}

type Plugin struct{ abstractDockerCompose }

func (p Plugin) Base(directory LFile) {

}

func (p Plugin) Up(directory LFile, noRecreate bool) {

}

func (p Plugin) Down(directory LFile, deleteVolumes bool) {

}

func (p Plugin) Ps(directory LFile) {

}

func (p Plugin) Logs(directory LFile, container string) {

}

func (p Plugin) Exec(directory LFile, container string, command []string, tty bool) {

}

func (p Plugin) Start(directory LFile, container string) {

}

func (p Plugin) Stop(directory LFile, container string) {

}

func newPluginCompose(exe string) *Plugin {
	plugin := Plugin{abstractDockerCompose{}}
	return &plugin
}

func FindDocker() string {
	return ExecuteCommand([]string{"/usr/bin/which", "docker"}, false)
}

func FindCompose() DockerCompose {
	dockerExe := FindDocker()

	var output = ExecuteCommand([]string{dockerExe, "compose", "version"}, true)
	hasPluginStyle := strings.Contains(strings.ToLower(output), "docker compose")

	if hasPluginStyle {
		return newPluginCompose(dockerExe)
	}

	dockerComposeExe := strings.TrimSpace(ExecuteCommand([]string{"/usr/bin/which", "docker-compose"}, true))

	hasClassicStyle := len(dockerComposeExe) > 0
	if hasClassicStyle {
		return NewClassicCompose(dockerComposeExe)
	}

	log.Fatal("Could not find docker compose!")

	//Is never reached but return is required
	return abstractDockerCompose{}
}
