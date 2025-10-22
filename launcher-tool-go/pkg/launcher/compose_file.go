package launcher

import (
	_ "embed"
	"encoding/json"
	"fmt"
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

const imDevImage = "dreg.cloud.sdu.dk/ucloud-dev/integration-module:2025.3.3"
const slurmImage = "dreg.cloud.sdu.dk/ucloud-dev/slurm:2024.1.35"

type PortAllocator interface {
	Allocate(port int) int
}

type Direct struct{}

func (d Direct) Allocate(port int) int {
	return port
}

type Environment struct {
	name        string
	repoRoot    LFile
	doWriteFile bool
}

func GetDataDirectory() string {
	path := currentEnvironment.GetAbsolutePath()
	exists, _ := os.Stat(path)
	if exists == nil {
		HardCheck(os.MkdirAll(path, 0644))
	}
	return path
}

type ComposeBuilder struct {
	environment Environment
	volumes     map[string]string
	services    map[string]Json
}

func (c ComposeBuilder) CreateComposeFile() string {
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

func (c ComposeBuilder) Service(
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
	AllServices[name] = Service{
		containerName:        name,
		title:                title,
		logsSupported:        logsSupported,
		execSupported:        execSupported,
		useServiceConvention: serviceConvention,
		address:              address,
		uiHelp:               TrimIndent(uiHelp),
	}
}

func (e Environment) CreateComposeFile(services []ComposeService) LFile {
	loadingTitle := ""
	if e.doWriteFile {
		loadingTitle = "Creating compose environment..."
	} else {
		loadingTitle = "Initializing service list..."
	}

	var lfile LFile
	err := termio.LoadingIndicator(
		loadingTitle,
		func() error {
			var err error
			cb := ComposeBuilder{e, map[string]string{}, map[string]Json{}}
			for _, service := range services {
				service.Build(cb)
			}

			allAddons := ListAddons()
			for providerName, allAddon := range allAddons {
				for _, addon := range allAddon {
					provider := ProviderFromName(providerName)
					v, ok := provider.(*GoSlurm)
					if ok {
						v.BuildAddon(cb, addon)
					}

				}
			}

			absPath, _ := filepath.Abs(GetDataDirectory())

			lfile := NewFile(absPath)
			childFile := lfile.Child("docker-compose.yaml", false)
			childFile.WriteText(cb.CreateComposeFile())
			return err
		},
	)
	HardCheck(err)
	return lfile
}

type ComposeService interface {
	Build(cb ComposeBuilder)
}

type Provider interface {
	Name() string
	Title() string
	CanRegisterProducts() bool
	Addons() map[string]string
	Install(credentials ProviderCredentials)
	Build(cb ComposeBuilder)
}

var goSlurm GoSlurm
var goKubernetes GoKubernetes

var AllProviders []Provider

var AllProviderNames = []string{
	"k8",
	"slurm",
	"go-slurm",
	"gok8s",
}

func GenerateProviders() {
	goSlurm = NewGoSlurm(true, 2)
	AllProviders = append(AllProviders, &goSlurm)
	AllProviders = append(AllProviders, &goKubernetes)
}

func ProviderFromName(name string) Provider {
	if !slices.Contains(AllProviderNames, name) {
		log.Fatal("Unknown Provider " + name)
	}
	found := slices.IndexFunc(AllProviders, func(provider Provider) bool {
		return provider.Name() == name
	})
	if found == -1 {
		log.Fatal("No such provider: " + name)
	}
	return AllProviders[found]
}

type ProviderCredentials struct {
	publicKey    string
	refreshToken string
	projectId    string
}

type ComposeServiceJson struct {
	Image       string            `json:"image"`
	Command     []string          `json:"command"`
	Restart     string            `json:"restart"`
	Hostname    string            `json:"hostname"`
	Ports       []string          `json:"ports"`
	Volumes     []string          `json:"volumes"`
	Environment map[string]string `json:"environment,omitempty"`
}

func (s ComposeServiceJson) ToJson() Json {
	data, _ := json.Marshal(s)
	return Json{string(data)}
}

func ComposeVolume(local string, container string) string {
	return fmt.Sprintf("%s:%s", local, container)
}

func ComposePort(local int, container int) string {
	return fmt.Sprintf("%d:%d", local, container)
}
