package launcher

import (
	"strings"
)

var compose DockerCompose

func SetCompose(dc DockerCompose) {
	compose = dc
}

func GetCompose() DockerCompose {
	return compose
}

type DockerCompose interface {
	Up(directory LFile, noRecreate bool) ExecutableCommandInterface
	Down(directory LFile, deleteVolumes bool) ExecutableCommandInterface
	Ps(directory LFile) ExecutableCommandInterface
	Logs(directory LFile, container string) ExecutableCommandInterface
	Start(directory LFile, container string) ExecutableCommandInterface
	Stop(directory LFile, container string) ExecutableCommandInterface
	Exec(directory LFile, container string, command []string, tty bool, allowFailure bool, streamOutput bool) ExecutableCommandInterface
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

func (c Classic) Up(directory LFile, noRecreate bool) ExecutableCommandInterface {
	list := c.Base(directory)
	list = append(list, "up")
	list = append(list, "-d")
	if noRecreate {
		list = append(list, "--no-recreate")
	}

	return LocalExecutableCommand{
		list,
		directory,
		PostProcessorFunc,
		false,
		1000 * 60 * 5,
		false,
	}
}

func (c Classic) Down(directory LFile, deleteVolumes bool) ExecutableCommandInterface {
	list := c.Base(directory)
	list = append(list, "down")
	if deleteVolumes {
		list = append(list, "-v")
	}

	return LocalExecutableCommand{
		list,
		directory,
		PostProcessorFunc,
		false,
		1000 * 60 * 5,
		false,
	}
}

func (c Classic) Ps(directory LFile) ExecutableCommandInterface {
	list := c.Base(directory)
	list = append(list, "ps")
	return LocalExecutableCommand{
		list,
		directory,
		PostProcessorFunc,
		true,
		1000 * 60 * 5,
		false,
	}
}

func (c Classic) Logs(directory LFile, container string) ExecutableCommandInterface {
	list := c.Base(directory)
	list = append(list, "logs")
	list = append(list, "--follow")
	list = append(list, "--no-log-prefix")
	list = append(list, container)
	return LocalExecutableCommand{
		list,
		directory,
		PostProcessorFunc,
		false,
		1000 * 60 * 5,
		false,
	}
}

func (c Classic) Exec(directory LFile, container string, command []string, tty bool, allowFailure bool, streamOutput bool) ExecutableCommandInterface {
	list := c.Base(directory)
	list = append(list, "exec")
	if !tty {
		list = append(list, "-T")
	}
	list = append(list, container)
	for _, s := range command {
		list = append(list, s)
	}

	return LocalExecutableCommand{
		list,
		directory,
		PostProcessorFunc,
		allowFailure,
		1000 * 60 * 5,
		streamOutput,
	}
}

func (c Classic) Start(directory LFile, container string) ExecutableCommandInterface {
	list := c.Base(directory)
	list = append(list, "start")
	list = append(list, container)
	return LocalExecutableCommand{
		list,
		directory,
		PostProcessorFunc,
		false,
		1000 * 60 * 5,
		false,
	}
}

func (c Classic) Stop(directory LFile, container string) ExecutableCommandInterface {
	list := c.Base(directory)
	list = append(list, "stop")
	list = append(list, container)
	return LocalExecutableCommand{
		list,
		directory,
		PostProcessorFunc,
		false,
		1000 * 60 * 5,
		false,
	}
}

func NewClassicCompose(exe string) *Classic {
	return &Classic{exe: exe}
}

type Plugin struct {
	exe string
}

func (p Plugin) Base(directory LFile) []string {
	compName := composeName
	list := []string{
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

func (p Plugin) Up(directory LFile, noRecreate bool) ExecutableCommandInterface {
	list := p.Base(directory)
	list = append(list, "up")
	list = append(list, "-d")
	if noRecreate {
		list = append(list, "--no-recreate")
	}
	return LocalExecutableCommand{
		list,
		directory,
		PostProcessorFunc,
		false,
		1000 * 60 * 5,
		false,
	}
}

func (p Plugin) Down(directory LFile, deleteVolumes bool) ExecutableCommandInterface {
	list := p.Base(directory)
	if !deleteVolumes {
		list = append(list, "down")
	} else {
		list = append(list, "rm")
		list = append(list, "--stop")
		list = append(list, "--volumes")
		list = append(list, "--force")
	}
	return LocalExecutableCommand{
		list,
		directory,
		PostProcessorFunc,
		false,
		1000 * 60 * 5,
		false,
	}
}

func (p Plugin) Ps(directory LFile) ExecutableCommandInterface {
	list := p.Base(directory)
	list = append(list, "ps")
	return LocalExecutableCommand{
		list,
		directory,
		PostProcessorFunc,
		true,
		1000 * 60 * 5,
		false,
	}
}

func (p Plugin) Logs(directory LFile, container string) ExecutableCommandInterface {
	list := p.Base(directory)
	list = append(list, "logs")
	list = append(list, "--follow")
	list = append(list, "--no-log-prefix")
	list = append(list, container)
	return LocalExecutableCommand{
		list,
		directory,
		PostProcessorFunc,
		false,
		1000 * 60 * 5,
		false,
	}
}

func (p Plugin) Exec(directory LFile, container string, command []string, tty bool, allowFailure bool, streamOutput bool) ExecutableCommandInterface {
	list := p.Base(directory)
	list = append(list, "exec")
	if !tty {
		list = append(list, "-T")
	}
	list = append(list, container)
	for _, s := range command {
		list = append(list, s)
	}
	return LocalExecutableCommand{
		list,
		directory,
		PostProcessorFunc,
		allowFailure,
		1000 * 60 * 5,
		streamOutput,
	}
}

func (p Plugin) Start(directory LFile, container string) ExecutableCommandInterface {
	list := p.Base(directory)
	list = append(list, "start")
	list = append(list, container)
	return LocalExecutableCommand{
		list,
		directory,
		PostProcessorFunc,
		false,
		1000 * 60 * 5,
		false,
	}
}

func (p Plugin) Stop(directory LFile, container string) ExecutableCommandInterface {
	list := p.Base(directory)
	list = append(list, "stop")
	list = append(list, container)
	return LocalExecutableCommand{
		list,
		directory,
		PostProcessorFunc,
		false,
		1000 * 60 * 5,
		false,
	}
}

func NewPluginCompose(exe string) *Plugin {
	return &Plugin{exe: exe}
}

func FindDocker() string {
	return strings.TrimSuffix(ExecuteCommand([]string{"/usr/bin/which", "docker"}, false), "\n")
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
