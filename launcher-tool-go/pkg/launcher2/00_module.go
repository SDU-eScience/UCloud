package launcher2

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/huh"
	"golang.org/x/term"
	"gopkg.in/yaml.v3"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const ImDevImage = "dreg.cloud.sdu.dk/ucloud-dev/integration-module:2025.4.161"
const SlurmImage = "dreg.cloud.sdu.dk/ucloud-dev/slurm:2025.4.161"

var Version string
var RepoRoot string
var HasPty bool
var ComposeDir string
var MenuActionRequested func()

var ClusterFeatures = map[Feature]bool{}

func RpcClientConfigure(refreshToken string) {
	rpc.DefaultClient = &rpc.Client{
		RefreshToken: refreshToken,
		BasePath:     "https://ucloud.localhost.direct",
		Client: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}

func Launch() {
	HasPty = term.IsTerminal(int(os.Stdin.Fd()))
	DocumentationStartRenderer()

	if false {
		StreamingExecute(
			"Testing",
			[]string{"/bin/bash", "-c", "echo 1; sleep 1; echo 2; sleep 1; echo 3; sleep 1;"},
			ExecuteOptions{},
		)
		StreamingExecute(
			"Testing",
			[]string{"/bin/bash", "-c", "echo A; sleep 1; echo B; sleep 1; echo C; sleep 1;"},
			ExecuteOptions{},
		)

		return
	}

	repoRootPath := ""
	if _, err := os.Stat(".git"); err == nil {
		repoRootPath, _ = filepath.Abs(".")
	} else if _, e := os.Stat("../.git"); e == nil {
		repoRootPath, _ = filepath.Abs("..")
	} else {
		log.Fatal("Unable to determine repository root. Please run this script from the root of the repository.")
	}

	RepoRoot = repoRootPath
	ComposeDir = filepath.Join(repoRootPath, ".compose")
	_ = os.MkdirAll(ComposeDir, 0700)

	versionBytes, err := os.ReadFile(repoRootPath + "/backend/version.txt")
	if err != nil {
		log.Fatal("Unable to find version file.")
	}

	refreshTokenBytes, _ := os.ReadFile(repoRootPath + "/.compose/refresh_token.txt")
	refreshToken := strings.TrimSpace(string(refreshTokenBytes))
	Version = strings.TrimSpace(string(versionBytes))

	RpcClientConfigure(refreshToken)

	rpc.ClientAllowSilentAuthTokenRenewalErrors.Store(true)
	ready := refreshToken != "" && EnvironmentIsReady()
	hasCluster := ready
	rpc.ClientAllowSilentAuthTokenRenewalErrors.Store(false)

	if !ready {
		hasCluster = ComposeClusterExists()
	}

	featureBytes, err := os.ReadFile(repoRootPath + "/.compose/features.json")
	if err == nil {
		_ = json.Unmarshal(featureBytes, &ClusterFeatures)
	}

	RegisterServices()

	if !ready {
		if hasCluster {
			confirm := !HasPty

			if HasPty {
				_ = huh.NewConfirm().
					Title("UCloud is not currently running. Do you want to start it?").
					Affirmative("Yes").
					Negative("No").
					Value(&confirm).
					Run()
			}

			if confirm {
				ClusterStart(false)
			}
		} else {
			confirm := !HasPty

			if HasPty {
				_ = huh.NewConfirm().
					Title("Welcome to the UCloud development tool. This will guide you through installing UCloud. Do you want to proceed?").
					Affirmative("Yes").
					Negative("No").
					Value(&confirm).
					Run()
			}

			if confirm {
				ClusterStart(false)
			}
		}
	}

	for {
		SetTerminalTitle("UCloud")
		p := tea.NewProgram(&tuiMenu{}, tea.WithAltScreen())
		_, _ = p.Run()

		if MenuActionRequested != nil {
			MenuActionRequested()
			MenuActionRequested = nil
		} else {
			break
		}
	}
}

type Feature string

const (
	FeatureProviderK8s    Feature = "k8s"
	FeatureProviderSlurm  Feature = "slurm"
	FeatureAddonInference Feature = "ollama"
)

func RegisterServices() {
	Volumes = nil
	AllServices = nil
	Services = nil
	ComposeServices = map[string]DockerComposeService{}
	Installers = map[string]func(){}
	StartupHooks = map[string]func(){}

	_ = os.MkdirAll(RepoRoot+".compose", 0770)

	ServiceCore()
	ServiceFrontend()
	ServiceGateway()
	ProviderK8s()
	ProviderSlurm()
}

type InstallerStep struct {
	Title     string
	Completed bool
}

func InstallerSetProgress(steps []*InstallerStep) {
	b := &strings.Builder{}
	for i, step := range steps {
		if i != 0 {
			b.WriteString("\n")
		}

		if step.Completed {
			b.WriteString(base.Foreground(green).Bold(true).Render("✔"))
		} else {
			b.WriteString(base.Bold(true).Render("·"))
		}
		b.WriteString(" ")
		b.WriteString(step.Title)
	}

	logTuiSidebar = b.String()
}

func ClusterStart(down bool) {
	for _, service := range Services {
		installer, ok := Installers[service.Name]
		if ok {
			installer()
		}
	}

	{
		// Render compose file

		var composeFile struct {
			Services map[string]DockerComposeService `yaml:"services"`
			Volumes  map[string]util.Empty           `yaml:"volumes"`
		}

		composeFile.Services = map[string]DockerComposeService{}
		composeFile.Volumes = map[string]util.Empty{}

		for name, composeService := range ComposeServices {
			composeFile.Services[name] = composeService
		}

		for _, vol := range Volumes {
			composeFile.Volumes[vol] = util.Empty{}
		}

		rendered, _ := yaml.Marshal(composeFile)

		_ = os.WriteFile(filepath.Join(ComposeDir, "docker-compose.yaml"), rendered, 0600)
	}

	startupStep := &InstallerStep{
		Title: "Starting system",
	}
	steps := []*InstallerStep{startupStep}
	startingSteps := map[string]*InstallerStep{}
	for _, svc := range Services {
		step := &InstallerStep{
			Title: fmt.Sprintf("Starting %s: %s", svc.UiParent, svc.Title),
		}
		startingSteps[svc.Name] = step
		steps = append(steps, step)
	}
	InstallerSetProgress(steps)

	if down {
		ComposeDown(false)
	}
	ComposeUp(false)

	for _, service := range Services {
		hook, ok := StartupHooks[service.Name]
		if ok {
			hook()
		}
	}

	startupStep.Completed = true
	InstallerSetProgress(steps)

	for _, service := range Services {
		StartServiceEx(service, true)

		startingSteps[service.Name].Completed = true
		InstallerSetProgress(steps)
	}

	featureBytes, _ := json.Marshal(ClusterFeatures)
	_ = os.WriteFile(RepoRoot+"/.compose/features.json", featureBytes, 0660)
}

func ClusterStop() {
	ComposeDown(false)
}

func ClusterDelete() {
	shuttingDownStep := &InstallerStep{
		Title: "Shutting down",
	}
	volDeleteStep := &InstallerStep{
		Title: "Deleting volumes",
	}
	dataDeleteStep := &InstallerStep{
		Title: "Deleting data",
	}
	steps := []*InstallerStep{shuttingDownStep, volDeleteStep, dataDeleteStep}
	InstallerSetProgress(steps)

	RegisterServices()
	ComposeDown(true)

	shuttingDownStep.Completed = true
	InstallerSetProgress(steps)

	for _, name := range Volumes {
		StreamingExecute(
			"Deleting volume",
			[]string{"docker", "volume", "rm", name, "-f"},
			ExecuteOptions{},
		)
	}
	volDeleteStep.Completed = true
	InstallerSetProgress(steps)

	StreamingExecute(
		"Deleting data",
		[]string{"docker", "run", "--rm", "-v", RepoRoot + "/.compose/:/data", "alpine:3", "/bin/sh", "-c", "rm -rf /data/*"},
		ExecuteOptions{},
	)
}

func StartService(service Service) {
	StartServiceEx(service, false)
}

func StartServiceEx(service Service, streaming bool) ExecuteResponse {
	opts := ExecuteOptions{Silent: !streaming, ContinueOnFailure: !streaming}

	if service.Flags&SvcNative != 0 {
		return ComposeExec(fmt.Sprintf("%s: %s", service.UiParent, service.Title), service.Name, []string{
			"/bin/bash",
			"-c",
			"set -x ; cd /opt/ucloud ; ./service.sh restart",
		}, opts)
	} else {
		ComposeStopContainer(service.Name, !streaming)
		ComposeStartContainer(service.Name, !streaming)
		return ExecuteResponse{}
	}
}

func SetTerminalTitle(title string) {
	fmt.Printf("\x1b]0;%s\x07", title)
}
