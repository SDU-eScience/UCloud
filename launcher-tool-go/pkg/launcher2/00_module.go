package launcher2

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"time"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/huh"
	"golang.org/x/term"
	"gopkg.in/yaml.v3"
	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
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
	if os.Getenv("NO_TERM") != "" {
		HasPty = false
	}

	cliCommand := ""
	if len(os.Args) > 1 {
		cliCommand = os.Args[1]
	}

	DocumentationStartRenderer()

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

	if cliCommand == "init-test" {
		ClusterFeatures = map[Feature]bool{
			FeatureProviderK8s: true,
		}
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

	switch cliCommand {
	case "":
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
	case "init-test":
		// Do nothing (service was already initialized)
	case "test":
		TestsRun("user", "mypassword")
	case "env":
		if slices.Contains(os.Args, "delete") {
			ClusterDelete()
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

func TestsRun(adminUser, adminPass string) {
	serviceToken := rpc.DefaultClient.RefreshToken
	defer func() {
		RpcClientConfigure(serviceToken)
	}()

	var adminActor rpc.CorePrincipalBaseClaims
	LogOutputRunWork("Looking up admin user", func(ch chan string) error {
		actor, err := fndapi.AuthLookupUser.Invoke(fndapi.FindByStringId{Id: adminUser})
		if err != nil {
			return err
		}

		adminActor = actor
		return nil
	})

	providersToTest := map[string]rpc.ProjectId{}
	if ClusterFeatures[FeatureProviderK8s] {
		projectId, ok := adminActor.ProviderProjects["k8s"]
		if ok {
			providersToTest["k8s"] = projectId
		}
	}
	if ClusterFeatures[FeatureProviderSlurm] {
		projectId, ok := adminActor.ProviderProjects["slurm"]
		if ok {
			providersToTest["slurm"] = projectId
		}
	}

	LogOutputRunWork("Determining test environment", func(ch chan string) error {
		if len(providersToTest) == 0 {
			return fmt.Errorf("this cluster has no available providers")
		}
		return nil
	})

	adminToken := ""

	LogOutputRunWork("Authenticating", func(ch chan string) error {
		result, err := fndapi.AuthPasswordLoginServer.Invoke(fndapi.PasswordLoginRequest{
			Username: adminUser,
			Password: adminPass,
		})

		if err != nil {
			return err
		}

		adminToken = result.RefreshToken
		return nil
	})

	RpcClientConfigure(adminToken)

	users := []fndapi.UsersCreateRequest{
		{
			Username:   util.SecureToken(),
			Password:   util.SecureToken(),
			Email:      fmt.Sprintf("%s@localhost.direct", util.SecureToken()),
			Role:       util.OptValue(fndapi.PrincipalUser),
			FirstNames: util.OptValue("Test"),
			LastName:   util.OptValue("User"),
		},
		{
			Username:   util.SecureToken(),
			Password:   util.SecureToken(),
			Email:      fmt.Sprintf("%s@localhost.direct", util.SecureToken()),
			Role:       util.OptValue(fndapi.PrincipalUser),
			FirstNames: util.OptValue("Test"),
			LastName:   util.OptValue("User"),
		},
	}

	var userTokens []string

	LogOutputRunWork("Creating users", func(ch chan string) error {
		_, err := fndapi.UsersCreate.Invoke(users)
		if err != nil {
			return fmt.Errorf("failed to create users: %s", err)
		}

		for _, user := range users {
			toks, err := fndapi.AuthPasswordLoginServer.Invoke(fndapi.PasswordLoginRequest{
				Username: user.Username,
				Password: user.Password,
			})

			if err != nil {
				return err
			}

			userTokens = append(userTokens, toks.RefreshToken)
		}
		return nil
	})

	productsByProviderAndType := map[string]map[accapi.ProductType]accapi.ProductV2{}

	LogOutputRunWork("Granting test resources", func(ch chan string) error {
		for provider, projectId := range providersToTest {
			projectHeader := http.Header{}
			projectHeader.Add("Project", string(projectId))
			rpcOpts := rpc.InvokeOpts{Headers: projectHeader}

			ch <- "Determining product selection\n"

			wallets, err := accapi.WalletsBrowse.Invoke(accapi.WalletsBrowseRequest{
				ItemsPerPage: 250,
			})

			if err != nil {
				return err
			}

			var products fndapi.PageV2[accapi.ProductV2]
			for range 120 {
				products, _ = accapi.ProductsBrowse.Invoke(accapi.ProductsBrowseRequest{
					ItemsPerPage:   250,
					ProductsFilter: accapi.ProductsFilter{FilterProvider: util.OptValue(provider)},
				})

				if len(products.Items) > 0 {
					break
				} else {
					ch <- "Waiting for products to be available...\n"
					time.Sleep(1 * time.Second)
				}
			}

			productsByType := map[accapi.ProductType]accapi.ProductV2{}
			for _, product := range products.Items {
				existing, hasExisting := productsByType[product.ProductType]
				if !hasExisting {
					if product.ProductType != accapi.ProductTypeCompute || !product.Category.FreeToUse {
						productsByType[product.ProductType] = product
					}
				} else {
					if product.ProductType == accapi.ProductTypeCompute && !product.HiddenInGrantApplications && !product.Category.FreeToUse {
						if product.MemoryInGigs < existing.MemoryInGigs {
							productsByType[product.ProductType] = product
						}
					} else if product.ProductType != accapi.ProductTypeCompute && product.Name == product.Category.Name {
						productsByType[product.ProductType] = product
					}
				}
			}
			productsByProviderAndType[provider] = productsByType

			ch <- "Preparing root allocations\n"

			var missingRootAllocations []accapi.RootAllocateRequest
			for _, product := range productsByType {
				found := false
				for _, wallet := range wallets.Items {
					if wallet.MaxUsable > 0 && wallet.PaysFor.ToId() == product.Category.ToId() {
						found = true
						break
					}
				}

				if !found && !product.Category.FreeToUse {
					quota := int64(1000)
					if product.Category.AccountingUnit.FloatingPoint {
						quota *= 1_000_000
					} else if product.Category.AccountingFrequency == accapi.AccountingFrequencyPeriodicMinute {
						quota *= 60
					}

					missingRootAllocations = append(missingRootAllocations, accapi.RootAllocateRequest{
						Category: product.Category.ToId(),
						Quota:    quota,
						Start:    fndapi.Timestamp(time.Now()),
						End:      fndapi.Timestamp(time.Now().AddDate(0, 0, 7)),
					})
				}
			}

			if len(missingRootAllocations) > 0 {
				_, err = accapi.RootAllocate.InvokeEx(rpc.DefaultClient, fndapi.BulkRequestOf(missingRootAllocations...), rpcOpts)
				if err != nil {
					return err
				}
			}

			ch <- fmt.Sprintf("Granting resources: %#v\n", productsByProviderAndType)

			var allocationRequests []accapi.AllocationRequest
			for _, product := range productsByType {
				quota := int64(1000)
				if product.Category.AccountingUnit.FloatingPoint {
					quota *= 1_000_000
				} else if product.Category.AccountingFrequency == accapi.AccountingFrequencyPeriodicMinute {
					quota *= 60
				}

				allocationRequests = append(allocationRequests, accapi.AllocationRequest{
					Category:         product.Category.Name,
					Provider:         product.Category.Provider,
					GrantGiver:       string(projectId),
					BalanceRequested: util.OptValue(quota),
				})
			}

			_, err = accapi.GrantsSubmitRevision.InvokeEx(rpc.DefaultClient, accapi.GrantsSubmitRevisionRequest{
				Revision: accapi.GrantDocument{
					Recipient: accapi.Recipient{
						Type:     accapi.RecipientTypePersonalWorkspace,
						Username: util.OptValue[string](users[0].Username),
					},
					AllocationRequests: allocationRequests,
					AllocationPeriod: util.OptValue(accapi.Period{
						Start: util.OptValue[fndapi.Timestamp](fndapi.Timestamp(time.Now())),
						End:   util.OptValue[fndapi.Timestamp](fndapi.Timestamp(time.Now().AddDate(0, 0, 7))),
					}),
					Form: accapi.Form{
						Type: accapi.FormTypeGrantGiverInitiated,
						Text: "Grant test resources",
					},
				},
				Comment: "Grant test resources",
			}, rpcOpts)

			if err != nil {
				return err
			}
		}
		return nil
	})

	RpcClientConfigure(userTokens[0])

	LogOutputRunWork("Connecting to providers", func(ch chan string) error {
		page, err := orcapi.ProviderIntegrationBrowse.Invoke(orcapi.ProviderIntegrationBrowseRequest{
			ItemsPerPage: 250,
		})

		if err != nil {
			return err
		}

		for _, item := range page.Items {
			for i := range 30 {
				if !item.Connected {
					ch <- "Attempting to contact provider...\n"
					_, err = orcapi.ProviderIntegrationConnect.Invoke(orcapi.ProviderIntegrationConnectRequest{
						Provider: item.Provider,
					})

					if err == nil {
						break
					} else {
						ch <- fmt.Sprintf("Could not contact provider: %d %s\n", err.StatusCode, err.Why)
						if strings.Contains(err.Why, "already connected") {
							break
						} else if i == 29 {
							return err
						} else {
							time.Sleep(1)
						}
					}
				}
			}
		}

		return nil
	})

	LogOutputRunWork("Setting default user options", func(ch chan string) error {
		_, err := fndapi.UsersUpdateOptionalInfo.Invoke(fndapi.OptionalUserInfo{
			OrganizationFullName: util.OptValue[string]("Test org"),
			Department:           util.OptValue[string]("Test department"),
			ResearchField:        util.OptValue[string]("Other"),
			Position:             util.OptValue[string]("Robot"),
			Gender:               util.OptValue[string]("Other"),
		})

		if err != nil {
			return err
		}

		_, err = fndapi.NotificationsUpdateSettings.Invoke(fndapi.NotificationSettings{JobStartedOrStopped: false})
		if err != nil {
			return err
		}

		return nil
	})

	LogOutputRunWork("Preparing test data", func(ch chan string) error {
		testInfoPath := filepath.Join(RepoRoot, "frontend-web/webclient/tests/test_data.json")
		err := os.MkdirAll(filepath.Dir(testInfoPath), 0750)
		if err != nil {
			return err
		}

		testInfo := map[string]any{
			"location_origin":               "https://ucloud.localhost.direct",
			"providers":                     providersToTest,
			"products_by_provider_and_type": productsByProviderAndType,
			"users": map[string]any{
				"with_resources": map[string]any{
					"username": users[0].Username,
					"password": users[0].Password,
				},
				"without_resources": map[string]any{
					"username": users[1].Username,
					"password": users[1].Password,
				},
			},
		}
		testInfoData, _ := json.Marshal(testInfo)
		err = os.WriteFile(testInfoPath, testInfoData, 0640)
		if err != nil {
			return fmt.Errorf("unable to write test_data.json: %s", err)
		}
		return nil
	})

	StreamingExecute("Preparing tests", []string{"npx", "playwright", "install", "--with-deps"}, ExecuteOptions{
		WorkingDir: util.OptValue(filepath.Join(RepoRoot, "frontend-web/webclient")),
	})

	testCommand := []string{"npx", "playwright", "test", "--ui"}
	if !HasPty {
		testCommand = []string{"npx", "playwright", "test"}
	}

	StreamingExecute("Running tests", testCommand, ExecuteOptions{
		WorkingDir: util.OptValue(filepath.Join(RepoRoot, "frontend-web/webclient")),
	})
}
