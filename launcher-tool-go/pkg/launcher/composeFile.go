package launcher

import (
	"context"
	"log"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"ucloud.dk/launcher/pkg/termio"
)

type Json struct {
	encoded string
}

const imDevImage = "dreg.cloud.sdu.dk/ucloud-dev/integration-module:2024.1.35"
const slurmImage = "dreg.cloud.sdu.dk/ucloud-dev/slurm:2024.1.35"

type PortAllocator interface {
	Allocate(port int) int
}

type abstractPortAllocator struct{ PortAllocator }

type Direct struct {
	abstractPortAllocator
}

func (d Direct) allocate(port int) int {
	return port
}

type Remapped struct {
	abstractPortAllocator
	portAllocator  int
	allocatedPorts map[int]int
}

func (r Remapped) allocate(port int) int {
	r.allocatedPorts[port] = r.portAllocator
	r.portAllocator++
	return r.portAllocator
}

type Environment struct {
	name        string
	repoRoot    LFile
	doWriteFile bool
}

func GetDataDirectory() string {
	path, _ := filepath.Abs(currentEnvironment.Name())
	exists, _ := os.Stat(path)
	if exists == nil {
		HardCheck(os.MkdirAll(path, 644))
	}
	return path
}

type ComposeBuilder struct {
	environment Environment
	volumes     map[string]string
	services    map[string]Json
}

func (c ComposeBuilder) createComposeFile() string {
	sb := strings.Builder{}
	sb.WriteString(`{ "services": {`)
	var index = 0
	for key, service := range c.services {
		if index != 0 {
			sb.WriteString(", ")
		}
		sb.WriteString(`"`)
		sb.WriteString(key)
		sb.WriteString(`"`)
		sb.WriteString(":")
		sb.WriteString(service.encoded)
		index++
	}
	sb.WriteString("}, ")
	sb.WriteString(` "volumes": {`)
	index = 0
	for _, v := range c.volumes {
		if index != 0 {
			sb.WriteString(", ")
		}
		sb.WriteString(` "` + v + `": {}`)
		index++
	}

	var prefix = ""
	if composeName == "" {
		prefix = c.environment.name + "_"
	} else {
		prefix = composeName + "_"
	}

	for _, v := range c.volumes {
		allVolumeNames = append(allVolumeNames, prefix+v)
	}
	sb.WriteString("} }")
	return sb.String()
}

func (c ComposeBuilder) service(
	name string,
	title string,
	compose Json,
	logsSupported bool,
	execSupported bool,
	serviceConvention bool,
	address string,
	uiHelp string,
) {
	c.services[name] = compose
	AllServices[name] = &Service{
		containerName:        name,
		title:                title,
		logsSupported:        logsSupported,
		execSupported:        execSupported,
		useServiceConvention: serviceConvention,
		address:              address,
		uiHelp:               uiHelp,
	}
}

func (e Environment) createComposeFile(services []ComposeService) LFile {
	disableRemoteFileWriting := !e.doWriteFile
	loadingTitle := ""
	if e.doWriteFile {
		loadingTitle = "Creating compose environment..."
	} else {
		loadingTitle = "Initializing service list..."
	}

	var lfile = LocalFile{}
	termio.LoadingIndicator(
		loadingTitle,
		func(output *os.File) error {
			var err error
			cb := ComposeBuilder{e, map[string]string{}, map[string]Json{}}
			for _, service := range cb.services {
				context.TODO()
			}

			allAddons := ListAddons()
			for providerName, addons := range allAddons {
				context.TODO()
			}

			file, err := os.Open(GetDataDirectory())
			if err != nil {
				disableRemoteFileWriting = false
				return err
			}
			lfile := LocalFile{file, abstractLFile{}}
			childFile := lfile.Child("docker-compose.yaml")
			childFile.File.WriteString(cb.createComposeFile())
			return err
		},
	)
	disableRemoteFileWriting = false
	return lfile.LFile
}

type ComposeService struct {
}

type Provider struct {
	ComposeService
	name                string
	title               string
	canRegisterProducts bool
	addons              map[string]string
}

func NewProvider() *Provider {
	return &Provider{
		name:                "",
		title:               "",
		canRegisterProducts: false,
		addons:              map[string]string{},
	}
}

var AllProviders = []Provider{
	Kubernetes,
	Slurm,
	GoSlurm,
	GoKubernetes,
}

func providerFromName(name string) *Provider {
	found := slices.IndexFunc(AllProviders, func(provider Provider) bool {
		return provider.name == name
	})
	if found == -1 {
		log.Fatal("No such provider: " + name)
	}
	return &AllProviders[found]
}

type UCloudBackend struct {
	ComposeService
}

func (uc UCloudBackend) build(cb ComposeBuilder) {
	dataDirecory, err := os.Open(GetDataDirectory())
	HardCheck(err)
	logs := LocalFile{File: dataDirecory}.Child("logs")
	homeDir := LocalFile{File: dataDirecory}.Child("backend-home")

}
