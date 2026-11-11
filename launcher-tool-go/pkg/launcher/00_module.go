package launcher

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"slices"
	"strings"
	"time"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/huh"
	"golang.org/x/term"
	"gopkg.in/yaml.v3"
	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const ImDevImage = "dreg.cloud.sdu.dk/ucloud-dev/integration-module:2026.2.15"
const SlurmImage = "dreg.cloud.sdu.dk/ucloud-dev/slurm:2025.4.165"
const PostgresImage = "postgres:17.0"
const PostgresVolumeSuffix = "data-17"

var Version string
var CliVersion string
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

	if cliCommand == "generate-report" {
		reportGenerator()
		return
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

	versionBytes, err := os.ReadFile(filepath.Join(repoRootPath, "core2/version.txt"))
	if err != nil {
		log.Fatal("Unable to find version file.")
	}

	Version = strings.TrimSpace(string(versionBytes))

	cliVersionBytes, err := os.ReadFile(filepath.Join(repoRootPath, "ucloud-cli", "version.txt"))
	if err != nil {
		log.Fatal("Unable to find CLI version file.")
	}
	CliVersion = strings.TrimSpace(string(cliVersionBytes))
	if CliVersion == "" {
		log.Fatal("CLI version file is empty.")
	}

	featureBytes, err := os.ReadFile(filepath.Join(repoRootPath, ".compose", "features.json"))
	if err == nil {
		_ = json.Unmarshal(featureBytes, &ClusterFeatures)
	}

	RegisterServices()

	if cliCommand == "env" && slices.Contains(os.Args, "delete") {
		ClusterDelete()
		return
	}

	if cliCommand == "init-test" {
		if ClusterFeatures == nil {
			ClusterFeatures = map[Feature]bool{}
		}
		ClusterFeatures[FeatureProviderK8s] = true
		RegisterServices()
		ClusterDelete()

		ClusterFeatures = map[Feature]bool{
			FeatureProviderK8s: true,
		}
		RegisterServices()
		ClusterStart(false)
		return
	}

	refreshTokenBytes, _ := os.ReadFile(filepath.Join(repoRootPath, ".compose/refresh_token.txt"))
	refreshToken := strings.TrimSpace(string(refreshTokenBytes))

	RpcClientConfigure(refreshToken)

	rpc.ClientAllowSilentAuthTokenRenewalErrors.Store(true)
	ready := refreshToken != "" && EnvironmentIsReady()
	hasCluster := ready
	rpc.ClientAllowSilentAuthTokenRenewalErrors.Store(false)

	if !ready {
		hasCluster = ComposeClusterExists()
	}

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
	case "test":
		TestsRun("user", "mypassword", os.Args[2:], false)
	case "test-ui":
		TestsRun("user", "mypassword", os.Args[2:], true)
	}
}

type Feature string

const (
	FeatureProviderK8s    Feature = "k8s"
	FeatureProviderSlurm  Feature = "slurm"
	FeatureAddonInference Feature = "inference"
	FeatureAddonKubeVirt  Feature = "kubevirt"
)

func RegisterServices() {
	Volumes = nil
	AllServices = nil
	Services = nil
	ComposeServices = map[string]DockerComposeService{}
	Installers = map[string]func(){}
	StartupHooks = map[string]func(){}

	_ = os.MkdirAll(filepath.Join(RepoRoot, ".compose"), 0770)

	ServiceCore()
	ServiceFrontend()
	ServiceCliDistribution()
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
			Networks map[string]DockerComposeNetwork `yaml:"networks,omitempty"`
		}

		composeFile.Services = map[string]DockerComposeService{}
		composeFile.Volumes = map[string]util.Empty{}
		composeFile.Networks = map[string]DockerComposeNetwork{}

		for name, composeService := range ComposeServices {
			composeFile.Services[name] = composeService
		}

		for _, vol := range Volumes {
			composeFile.Volumes[vol] = util.Empty{}
		}

		mtu := os.Getenv("UCLOUD_DOCKER_MTU")
		if mtu == "" {
			mtu = "1400"
		}

		composeFile.Networks["default"] = DockerComposeNetwork{
			Ipam: DockerComposeIpam{
				Config: []DockerComposeIpamConfig{
					{Subnet: "172.18.0.0/16"},
				},
			},
			DriverOpts: map[string]string{
				"com.docker.network.driver.mtu": mtu,
			},
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
	_ = os.WriteFile(filepath.Join(RepoRoot, ".compose/features.json"), featureBytes, 0660)
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
	if ComposeClusterExists() {
		ComposeDown(true)
	}

	shuttingDownStep.Completed = true
	InstallerSetProgress(steps)

	ComposeDeleteProjectData()
	volDeleteStep.Completed = true
	InstallerSetProgress(steps)

	StreamingExecute(
		"Deleting data",
		[]string{"docker", "run", "--rm", "-v", filepath.Join(RepoRoot, ".compose") + ":/data", "alpine:3", "/bin/sh", "-c", "rm -rf /data/*"},
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

func TestsRun(adminUser, adminPass string, testArgs []string, ui bool) {
	serviceToken := rpc.DefaultClient.RefreshToken
	defer func() {
		RpcClientConfigure(serviceToken)
	}()

	userDataPath, manuallyConfiguredUsers := os.LookupEnv("UCLOUD_PLAYWRIGHT_USER_DATA")
	if userDataPath == "" {
		userDataPath = filepath.Join(ComposeDir, "playwright-users.json")
	}
	if !manuallyConfiguredUsers {
		userDataPath = filepath.Join(ComposeDir, "playwright-users.json")
		testInfoPath := filepath.Join(RepoRoot, "frontend-web/webclient/tests/test_data.json")
		if _, userDataErr := os.Stat(userDataPath); userDataErr == nil {
			if _, testInfoErr := os.Stat(testInfoPath); testInfoErr == nil {
				PlaywrightRun(testArgs, ui, userDataPath, false)
				return
			}
		}
	}

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

	LogOutputRunWork("Setting up admin user", func(ch chan string) error {
		toks, err := fndapi.AuthPasswordLoginServer.Invoke(fndapi.PasswordLoginRequest{
			Username: "user",
			Password: "mypassword",
		})

		if err != nil {
			return err
		}

		userTokens = append(userTokens, toks.RefreshToken)
		return nil
	})

	productsByProviderAndType := map[string]map[accapi.ProductType]accapi.ProductV2{}

	requiredProductTypes := map[string][]accapi.ProductType{
		"k8s": {
			accapi.ProductTypeCompute,
			accapi.ProductTypeStorage,
			accapi.ProductTypeIngress,
			accapi.ProductTypeLicense,
			accapi.ProductTypeNetworkIp,
			accapi.ProductTypePrivateNetwork,
		},
		"slurm": {
			accapi.ProductTypeCompute,
			accapi.ProductTypeStorage,
		},
	}

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
			required := requiredProductTypes[provider]
			for range 180 {
				products, _ = accapi.ProductsBrowse.Invoke(accapi.ProductsBrowseRequest{
					ItemsPerPage:   250,
					ProductsFilter: accapi.ProductsFilter{FilterProvider: util.OptValue(provider)},
				})

				if productsContainTypes(products.Items, required) {
					break
				}

				ch <- "Waiting for products to be available...\n"
				time.Sleep(1 * time.Second)
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
					quota := allocateQuota(product, 1000)

					missingRootAllocations = append(missingRootAllocations, accapi.RootAllocateRequest{
						Category: product.Category.ToId(),
						Quota:    quota,
						Start:    fndapi.Timestamp(time.Now().AddDate(0, -1, 0)),
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
				if product.Category.FreeToUse {
					continue
				}

				quota := allocateQuota(product, 1000)

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

	type contextUser struct {
		Username string
		Password string
		Token    string
	}

	contextUsers := make([]contextUser, 0, 3)
	projectTitle := ""

	LogOutputRunWork("Preparing Playwright users", func(ch chan string) error {
		for _, kind := range []string{"pi", "admin", "user"} {
			username := fmt.Sprintf("test-user-%s-%s", kind, util.SecureToken()[:8])
			password := util.SecureToken()

			_, err := fndapi.UsersCreate.Invoke([]fndapi.UsersCreateRequest{{
				Username:   username,
				Password:   password,
				Email:      fmt.Sprintf("%s@localhost.direct", username),
				Role:       util.OptValue(fndapi.PrincipalUser),
				FirstNames: util.OptValue("Test"),
				LastName:   util.OptValue("User"),
			}})

			if err != nil {
				return fmt.Errorf("failed to create test user: %s", err)
			}

			tokens, err := fndapi.AuthPasswordLoginServer.Invoke(fndapi.PasswordLoginRequest{
				Username: username,
				Password: password,
			})

			if err != nil {
				return err
			}

			contextUsers = append(contextUsers, contextUser{
				Username: username,
				Password: password,
				Token:    tokens.RefreshToken,
			})
		}

		projectTitle = "ProjectTitle" + util.SecureToken()[:6]
		projectResponse, err := fndapi.ProjectCreate.Invoke(fndapi.BulkRequestOf(fndapi.ProjectSpecification{
			Title:               projectTitle,
			CanConsumeResources: true,
		}))

		if err != nil {
			return fmt.Errorf("failed to create test project: %s", err)
		}

		projectId := projectResponse.Responses[0].Id
		projectOpts := rpc.InvokeOpts{Headers: http.Header{"Project": {projectId}}}

		adminLogin, loginErr := fndapi.AuthPasswordLoginServer.Invoke(fndapi.PasswordLoginRequest{
			Username: adminUser,
			Password: adminPass,
		})

		if loginErr != nil {
			return loginErr
		}

		adminToken = adminLogin.RefreshToken
		RpcClientConfigure(adminToken)

		var invites []fndapi.ProjectCreateInviteRequest
		for _, user := range contextUsers {
			invites = append(invites, fndapi.ProjectCreateInviteRequest{Recipient: user.Username})
		}

		_, err = fndapi.ProjectCreateInvite.InvokeEx(rpc.DefaultClient, fndapi.BulkRequestOf(invites...), projectOpts)
		if err != nil {
			return fmt.Errorf("failed to invite test users: %s", err)
		}

		for i := range contextUsers {
			RpcClientConfigure(contextUsers[i].Token)
			_, err = fndapi.ProjectAcceptInvite.InvokeEx(
				rpc.DefaultClient,
				fndapi.BulkRequestOf(fndapi.ProjectAcceptInviteRequest{Project: projectId}),
				rpc.InvokeOpts{},
			)

			if err != nil {
				return fmt.Errorf("failed to accept project invite: %s", err)
			}

			userLogin, loginErr := fndapi.AuthPasswordLoginServer.Invoke(fndapi.PasswordLoginRequest{
				Username: contextUsers[i].Username,
				Password: contextUsers[i].Password,
			})

			if loginErr != nil {
				return loginErr
			}

			contextUsers[i].Token = userLogin.RefreshToken
		}

		RpcClientConfigure(adminToken)
		_, err = fndapi.ProjectMemberChangeRole.InvokeEx(
			rpc.DefaultClient,
			fndapi.BulkRequestOf(
				fndapi.ProjectMemberChangeRoleRequest{Username: contextUsers[0].Username, Role: fndapi.ProjectRolePI},
				fndapi.ProjectMemberChangeRoleRequest{Username: contextUsers[1].Username, Role: fndapi.ProjectRoleAdmin},
				fndapi.ProjectMemberChangeRoleRequest{Username: contextUsers[2].Username, Role: fndapi.ProjectRoleUser},
			),
			projectOpts,
		)

		if err != nil {
			return fmt.Errorf("failed to assign project roles: %s", err)
		}

		piLogin, loginErr := fndapi.AuthPasswordLoginServer.Invoke(fndapi.PasswordLoginRequest{
			Username: contextUsers[0].Username,
			Password: contextUsers[0].Password,
		})

		if loginErr != nil {
			return loginErr
		}

		contextUsers[0].Token = piLogin.RefreshToken
		RpcClientConfigure(contextUsers[0].Token)
		_, err = fndapi.ProjectRemoveMember.InvokeEx(
			rpc.DefaultClient,
			fndapi.BulkRequestOf(fndapi.ProjectRemoveMemberRequest{Username: adminUser}),
			projectOpts,
		)

		if err != nil {
			return fmt.Errorf("failed to remove admin from test project: %s", err)
		}

		adminLogin, loginErr = fndapi.AuthPasswordLoginServer.Invoke(fndapi.PasswordLoginRequest{
			Username: adminUser,
			Password: adminPass,
		})

		if loginErr != nil {
			return loginErr
		}

		adminToken = adminLogin.RefreshToken
		RpcClientConfigure(adminToken)

		providerName := "k8s"
		if _, ok := productsByProviderAndType[providerName]; !ok {
			for name := range productsByProviderAndType {
				providerName = name
				break
			}
		}

		productsByType, ok := productsByProviderAndType[providerName]
		if !ok {
			return fmt.Errorf("no products available for project setup")
		}

		providerProjectId, ok := providersToTest[providerName]
		if !ok {
			return fmt.Errorf("missing provider project for %s", providerName)
		}

		var projectAllocations []accapi.AllocationRequest
		for _, product := range productsByType {
			if product.Category.FreeToUse {
				continue
			}

			quota := int64(1)
			switch product.ProductType {
			case accapi.ProductTypeCompute:
				quota = 50
			case accapi.ProductTypeStorage:
				quota = 10
			case accapi.ProductTypeLicense:
				quota = 3
			case accapi.ProductTypeNetworkIp:
				quota = 5
			}
			quota = allocateQuota(product, quota)

			projectAllocations = append(projectAllocations, accapi.AllocationRequest{
				Category:         product.Category.Name,
				Provider:         product.Category.Provider,
				GrantGiver:       string(providerProjectId),
				BalanceRequested: util.OptValue(quota),
			})
		}

		providerOpts := rpc.InvokeOpts{Headers: http.Header{"Project": {string(providerProjectId)}}}
		allocationPeriod := util.OptValue(accapi.Period{
			Start: util.OptValue[fndapi.Timestamp](fndapi.Timestamp(time.Now())),
			End:   util.OptValue[fndapi.Timestamp](fndapi.Timestamp(time.Now().AddDate(0, 0, 7))),
		})

		_, err = accapi.GrantsSubmitRevision.InvokeEx(rpc.DefaultClient, accapi.GrantsSubmitRevisionRequest{
			Revision: accapi.GrantDocument{
				Recipient: accapi.Recipient{
					Type: accapi.RecipientTypeExistingProject,
					Id:   util.OptValue(projectId),
				},
				AllocationRequests: projectAllocations,
				AllocationPeriod:   allocationPeriod,
				Form: accapi.Form{
					Type: accapi.FormTypeGrantGiverInitiated,
					Text: "Grant test resources",
				},
			},
			Comment: "Grant test resources",
		}, providerOpts)

		if err != nil {
			return fmt.Errorf("failed to grant project resources: %s", err)
		}

		storageProduct, hasStorage := productsByType[accapi.ProductTypeStorage]
		if hasStorage {
			for _, user := range contextUsers {
				_, err = accapi.GrantsSubmitRevision.InvokeEx(rpc.DefaultClient, accapi.GrantsSubmitRevisionRequest{
					Revision: accapi.GrantDocument{
						Recipient: accapi.Recipient{
							Type:     accapi.RecipientTypePersonalWorkspace,
							Username: util.OptValue(user.Username),
						},
						AllocationRequests: []accapi.AllocationRequest{{
							Category:         storageProduct.Category.Name,
							Provider:         storageProduct.Category.Provider,
							GrantGiver:       string(providerProjectId),
							BalanceRequested: util.OptValue(allocateQuota(storageProduct, 1)),
						}},
						AllocationPeriod: allocationPeriod,
						Form: accapi.Form{
							Type: accapi.FormTypeGrantGiverInitiated,
							Text: "Grant test resources",
						},
					},
					Comment: "Grant test resources",
				}, providerOpts)

				if err != nil {
					return fmt.Errorf("failed to grant personal resources: %s", err)
				}
			}
		}

		userTokens = append(userTokens, contextUsers[0].Token, contextUsers[1].Token, contextUsers[2].Token)

		testUsers := map[string]any{
			"Project PI": map[string]string{
				"username": contextUsers[0].Username,
				"password": contextUsers[0].Password,
			},
			"Project Admin": map[string]string{
				"username": contextUsers[1].Username,
				"password": contextUsers[1].Password,
			},
			"Project User": map[string]string{
				"username": contextUsers[2].Username,
				"password": contextUsers[2].Password,
			},
			"Personal Workspace": map[string]string{
				"username": users[0].Username,
				"password": users[0].Password,
			},
			"projectName":       projectTitle,
			"providerId":        providerName,
			"providerProjectId": string(providerProjectId),
		}

		encoded, marshalErr := json.Marshal(testUsers)
		if marshalErr != nil {
			return marshalErr
		}

		if err := os.WriteFile(userDataPath, encoded, 0640); err != nil {
			return fmt.Errorf("unable to write %s: %s", userDataPath, err)
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
							time.Sleep(1 * time.Second)
						}
					}
				}
			}
		}

		return nil
	})

	for _, tok := range userTokens {
		RpcClientConfigure(tok)
		LogOutputRunWork("Setting default user options", func(ch chan string) error {
			_, err := fndapi.UsersUpdateOptionalInfo.Invoke(fndapi.OptionalUserInfo{
				OrganizationFullName: util.OptValue[string]("Test org"),
				Department:           util.OptValue[string]("Test department"),
				Unit:                 util.OptValue("Test unit"),
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
	}

	RpcClientConfigure(userTokens[0])

	LogOutputRunWork("Preparing test data", func(ch chan string) error {
		testInfoPath := filepath.Join(RepoRoot, "frontend-web/webclient/tests/test_data.json")
		err := os.MkdirAll(filepath.Dir(testInfoPath), 0750)
		if err != nil {
			return err
		}

		testInfo := map[string]any{
			"location_origin": "https://ucloud.localhost.direct",
			"users": map[string]any{
				"with_resources": map[string]any{
					"username": users[0].Username,
					"password": users[0].Password,
				},
				"without_resources": map[string]any{
					"username": users[1].Username,
					"password": users[1].Password,
				},
				"admin": map[string]any{
					"username": "user",
					"password": "mypassword",
				},
			},
			"login_cookie": nil,
		}
		testInfoData, _ := json.Marshal(testInfo)
		err = os.WriteFile(testInfoPath, testInfoData, 0640)
		if err != nil {
			return fmt.Errorf("unable to write test_data.json: %s", err)
		}
		return nil
	})

	PlaywrightRun(testArgs, ui, userDataPath, false)
}

func PlaywrightRun(testArgs []string, ui bool, userDataPath string, prepareUsers bool) {
	workingDir := util.OptValue(filepath.Join(RepoRoot, "frontend-web/webclient"))
	installCommand := []string{"npx", "playwright", "install"}
	if !isFedoraLinux() {
		installCommand = append(installCommand, "--with-deps")
	}
	StreamingExecute("Preparing tests", installCommand, ExecuteOptions{WorkingDir: workingDir})

	testEnvironment := append(os.Environ(), "UCLOUD_PLAYWRIGHT_USER_DATA="+userDataPath)
	if prepareUsers {
		StreamingExecute("Preparing Playwright users", []string{
			"npx", "playwright", "test", "--project=setup",
		}, ExecuteOptions{
			WorkingDir:  workingDir,
			Environment: append(testEnvironment, "UCLOUD_PLAYWRIGHT_PREPARE_USERS=1"),
		})
	}

	testCommand := append([]string{"npx", "playwright", "test"}, testArgs...)
	if ui {
		testCommand = append(testCommand, "--ui")
	}

	StreamingExecute("Running tests", testCommand, ExecuteOptions{
		WorkingDir:  workingDir,
		Environment: testEnvironment,
	})
}

func productsContainTypes(products []accapi.ProductV2, required []accapi.ProductType) bool {
	if len(required) == 0 {
		return len(products) > 0
	}

	present := map[accapi.ProductType]bool{}
	for _, product := range products {
		present[product.ProductType] = true
	}

	for _, productType := range required {
		if !present[productType] {
			return false
		}
	}

	return true
}

func allocateQuota(product accapi.ProductV2, base int64) int64 {
	if product.Category.AccountingUnit.FloatingPoint {
		return base * 1_000_000
	}

	if product.ProductType == accapi.ProductTypeStorage && product.Category.AccountingUnit.Name == "B" {
		return base * 1_000_000_000
	}

	switch product.Category.AccountingFrequency {
	case accapi.AccountingFrequencyPeriodicSecond:
		return base * 60 * 60
	case accapi.AccountingFrequencyPeriodicMinute:
		return base * 60
	default:
		return base
	}
}

func isFedoraLinux() bool {
	if runtime.GOOS != "linux" {
		return false
	}

	osRelease, err := os.ReadFile("/etc/os-release")
	if err != nil {
		return false
	}

	for _, line := range strings.Split(string(osRelease), "\n") {
		line = strings.TrimSpace(line)
		if line == "ID=fedora" || line == `ID="fedora"` {
			return true
		}
	}
	return false
}
