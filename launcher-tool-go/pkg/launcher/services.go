package launcher

import (
	"fmt"
	"os"
	"path/filepath"

	"ucloud.dk/shared/pkg/util"
)

var Volumes []string
var AllServices []Service
var Services []Service
var ComposeServices = map[string]DockerComposeService{}
var Installers = map[string]func(){}
var StartupHooks = map[string]func(){}

type ServiceFlag uint64

const (
	SvcLogs ServiceFlag = 1 << iota
	SvcExec
	SvcNative
	SvcFreeIpa
)

const (
	UiParentCore  = "UCloud/Core"
	UiParentK8s   = "IM/K8s"
	UiParentSlurm = "IM/Slurm"
)

type Service struct {
	Name      string
	Title     string
	Flags     ServiceFlag
	UiParent  string
	Feature   Feature
	DependsOn util.Option[Feature]
}

func (s *Service) Enabled() bool {
	return s.Feature == "" || ClusterFeatures[s.Feature]
}

func AddDirectory(owner Service, dirName string) string {
	if owner.Enabled() {
		dir := fmt.Sprintf("%s-%s", owner.Name, dirName)
		path := filepath.Join(ComposeDir, dir)
		_ = os.MkdirAll(path, 0770)
		return path
	} else {
		return ""
	}
}

func AddVolume(owner Service, volName string) string {
	if owner.Enabled() {
		name := fmt.Sprintf("%s-%s", owner.Name, volName)
		Volumes = append(Volumes, name)
		return name
	} else {
		return ""
	}
}

func AddService(owner Service, service DockerComposeService) {
	AllServices = append(AllServices, owner)
	if owner.Enabled() {
		Services = append(Services, owner)
		ComposeServices[owner.Name] = service
	}
}

func AddInstaller(owner Service, installer func()) {
	if owner.Enabled() {
		Installers[owner.Name] = installer
	}
}

func AddStartupHook(owner Service, hook func()) {
	if owner.Enabled() {
		// Runs after compose start-up but before the native service startup.
		StartupHooks[owner.Name] = hook
	}
}

type DockerComposeService struct {
	Image           string                                 `yaml:"image,omitempty"`
	Hostname        string                                 `yaml:"hostname,omitempty"`
	Restart         string                                 `yaml:"restart,omitempty"`
	DependsOn       []string                               `yaml:"depends_on,omitempty"`
	Environment     []string                               `yaml:"environment,omitempty"`
	Volumes         []string                               `yaml:"volumes,omitempty"`
	VolumesFrom     []string                               `yaml:"volumes_from,omitempty"`
	Ports           []string                               `yaml:"ports,omitempty"`
	Init            bool                                   `yaml:"init,omitempty"`
	Sysctls         map[string]string                      `yaml:"sysctls,omitempty"`
	SecurityOpt     []string                               `yaml:"security_opt,omitempty"`
	Privileged      bool                                   `yaml:"privileged,omitempty"`
	Tmpfs           []string                               `yaml:"tmpfs"`
	Entrypoint      []string                               `yaml:"entrypoint,omitempty"`
	Command         []string                               `yaml:"command,omitempty"`
	WorkingDir      string                                 `yaml:"working_dir,omitempty"`
	Cgroup          string                                 `yaml:"cgroup,omitempty"`
	StopGracePeriod string                                 `yaml:"stop_grace_period,omitempty"`
	Networks        map[string]DockerComposeServiceNetwork `yaml:"networks,omitempty"`
}

type DockerComposeServiceNetwork struct {
	Aliases     []string `yaml:"aliases,omitempty"`
	Ipv4Address string   `yaml:"ipv4_address,omitempty"`
}

type DockerComposeNetwork struct {
	Ipam       DockerComposeIpam `yaml:"ipam,omitempty"`
	DriverOpts map[string]string `yaml:"driver_opts,omitempty"`
}

type DockerComposeIpam struct {
	Config []DockerComposeIpamConfig `yaml:"config,omitempty"`
}

type DockerComposeIpamConfig struct {
	Subnet string `yaml:"subnet,omitempty"`
}

func Mount(volName string, mountPath string) string {
	return fmt.Sprintf("%s:%s", volName, mountPath)
}

func pinnedNetwork(ipv4Address string) map[string]DockerComposeServiceNetwork {
	return map[string]DockerComposeServiceNetwork{
		"default": {Ipv4Address: ipv4Address},
	}
}

func IntegratedApplicationsDir() string {
	dir := os.Getenv("UCLOUD_INTEGRATED_APPLICATIONS_DIR")
	if dir == "" {
		dir = filepath.Join(RepoRoot, "integrated-applications")
	}

	info, err := os.Stat(dir)
	if err != nil || !info.IsDir() {
		return ""
	}
	return dir
}
