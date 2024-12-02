package launcher

import (
	"strings"
)

var compose DockerCompose

type DockerCompose interface {
	Up(directory LFile, noRecreate bool)
	Down(directory LFile, deleteVolumes bool)
	Ps(directory LFile)
	Logs(directory LFile, container string)
	Start(directory LFile, container string)
	Stop(directory LFile, container string)
	Exec(directory LFile, container string, command []string, tty bool)
}

type Classic struct {
	exe string
}

func (c Classic) Base(directory LFile) []string {
	cname := composeName
	list := []string{
		c.exe,
		"--project-directory",
		directory.GetAbsolutePath(),
	}
	if cname != "" {
		list = append(list, "-p")
		list = append(list, cname)
	}
	return list
}

func (c Classic) Up(directory LFile, noRecreate bool) {
	list := c.Base(directory)
	list = append(list, "up")
	list = append(list, "-d")
	if noRecreate {
		list = append(list, "--no-recreate")
	}

	ExecutableCommand{
		list,
		directory,
		"",
		false,
		1000 * 60 * 5,
		false,

	}
}

func (c Classic) Down(directory LFile, deleteVolumes bool) {
	list := c.Base(directory)
	list = append(list, "down")
	if deleteVolumes {
		list = append(list, "-v")
	}
	ExecutableCommand{
		list,
		directory,
		"",
		false,
		1000 * 60 * 5,
		false,
	}
}

func (c Classic) Ps(directory LFile) {
	list := c.Base(directory)
	list = append(list, "ps")
	ExecutableCommand{
		list,
		directory,
		"",
		true,
		1000 * 60 * 5,
		false,
	}
}

func (c Classic) Logs(directory LFile, container string) {
	list := c.Base(directory)
	list = append(list, "logs")
	list = append(list, "--follow")
	list = append(list, "--no-log-prefix")
	list = append(list, container)
	ExecutableCommand{
		list,
		directory,
		"",
		false,
		1000 * 60 * 5,
		false,
	}
}

func (c Classic) Exec(directory LFile, container string, command []string, tty bool) {
	list := c.Base(directory)
	list = append(list, "exec")
	if !tty {
		list = append(list, "-T")
	}
	list = append(list, container)
	for _, s := range command {
		list = append(list, s)
	}
	ExecutableCommand{
		list,
		directory,
		"",
		false,
		1000 * 60 * 5,
		false,
	}
}

func (c Classic) Start(directory LFile, container string) {
	list := c.Base(directory)
	list = append(list, "start")
	list = append(list, container)
	ExecutableCommand{
		list,
		directory,
		"",
		false,
		1000 * 60 * 5,
		false,
	}
}

func (c Classic) Stop(directory LFile, container string) {
	list := c.Base(directory)
	list = append(list, "stop")
	list = append(list, container)
	ExecutableCommand{
		list,
		directory,
		"",
		false,
		1000 * 60 * 5,
		false,
	}
}

func NewClassicCompose(exe string) *Classic {
	return &Classic{exe: exe}}
}

type Plugin struct{
	exe string
}

func (p Plugin) Base(directory LFile) []string {
	compName := composeName
	list := []string {
		p.exe,
		"compose",
		"--project-directory",
		directory.GetAbsolutePath(),
	}
	if compName != "" {
		list = append(list, "-p")
		list = append(list, compName)
	}
	return list
}

func (p Plugin) Up(directory LFile, noRecreate bool) {
	list := p.Base(directory)
	list = append(list, "up")
	list = append(list, "-d")
	if noRecreate {
		list = append(list, "--no-recreate")
	}
	ExecutableCommand{
		list,
		directory,
		"",
		false,
		1000 * 60 * 5,
		false,
	}
}

func (p Plugin) Down(directory LFile, deleteVolumes bool) {
	list := p.Base(directory)
	if (!deleteVolumes) {
		list = append(list, "down")
	} else {
		list = append(list, "rm")
		list = append(list, "--stop")
		list = append(list, "--volumes")
		list = append(list, "--force")
	}
	ExecutableCommand{
		list,
		directory,
		"",
		false,
		1000 * 60 * 5,
		false,
	}
}

func (p Plugin) Ps(directory LFile) {
	list := p.Base(directory)
	list = append(list, "ps")
	ExecutableCommand{
		list,
		directory,
		"",
		true,
		1000 * 60 * 5,
		false,
	}
}

func (p Plugin) Logs(directory LFile, container string) {
	list := p.Base(directory)
	list = append(list, "logs")
	list = append(list, "--follow")
	list = append(list, "--no-log-prefix")
	list = append(list, container)
	ExecutableCommand{
		list,
		directory,
		"",
		false,
		1000 * 60 * 5,
		false,
	}
}

func (p Plugin) Exec(directory LFile, container string, command []string, tty bool) {
	list := p.Base(directory)
	list = append(list, "exec")
	if !tty {
		list = append(list, "-T")
	}
	list = append(list, container)
	for _, s := range command {
		list = append(list, s)
	}
	ExecutableCommand{
		list,
		directory,
		"",
		false,
		1000 * 60 * 5,
		false,
	}
}

func (p Plugin) Start(directory LFile, container string) {
	list := p.Base(directory)
	list = append(list, "start")
	list = append(list, container)
	ExecutableCommand{
		list,
		directory,
		"",
		false,
		1000 * 60 * 5,
		false,
	}
}

func (p Plugin) Stop(directory LFile, container string) {
	list := p.Base(directory)
	list = append(list, "stop")
	list = append(list, container)
	ExecutableCommand{
		list,
		directory,
		"",
		false,
		1000 * 60 * 5,
		false,
	}
}

func NewPluginCompose(exe string) *Plugin {
	return &Plugin{exe: exe}
}

func FindDocker() string {
	return ExecuteCommand([]string{"/usr/bin/which", "docker"}, false)
}

func FindCompose() DockerCompose {
	dockerExe := FindDocker()

	var output = ExecuteCommand([]string{dockerExe, "compose", "version"}, true)
	hasPluginStyle := strings.Contains(strings.ToLower(output), "docker compose")

	if hasPluginStyle {
		return NewPluginCompose(dockerExe)
	}

	dockerComposeExe := strings.TrimSpace(ExecuteCommand([]string{"/usr/bin/which", "docker-compose"}, true))

	hasClassicStyle := len(dockerComposeExe) > 0
	if hasClassicStyle {
		return NewClassicCompose(dockerComposeExe)
	}

	//Should never be reached but is required
	panic("Could not find docker compose!")
}
