package launcher

import (
	_ "embed"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"time"

	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

//go:embed config/core/config.yaml
var coreConfigFile []byte

func ServiceCore() {
	service := Service{
		Name:     "core",
		Title:    "Core Server",
		Flags:    SvcLogs | SvcExec | SvcNative,
		UiParent: UiParentCore,
	}

	configDir := AddDirectory(service, "config")
	logDir := AddDirectory(service, "logs")

	volumes := []string{
		Mount(filepath.Join(RepoRoot, "core2"), "/opt/ucloud"),
		Mount(filepath.Join(RepoRoot, "provider-integration"), "/opt/provider-integration"),
		Mount(configDir, "/etc/ucloud"),
		Mount(logDir, "/var/log/ucloud"),
	}

	if goCacheDir := os.Getenv("UCLOUD_GO_CACHE_DIR"); goCacheDir != "" {
		pkgDir := filepath.Join(goCacheDir, service.Name)
		_ = os.MkdirAll(pkgDir, 0777)
		volumes = append(volumes, Mount(pkgDir, "/root/go"))

		buildDir := filepath.Join(goCacheDir, service.Name+"-build")
		_ = os.MkdirAll(buildDir, 0777)
		volumes = append(volumes, Mount(buildDir, "/root/.cache/go-build"))
	}

	AddService(service, DockerComposeService{
		Image:      ImDevImage,
		Hostname:   "core2",
		Restart:    "always",
		Ports:      []string{"51245:51233"},
		Command:    []string{"sleep", "inf"},
		Volumes:    volumes,
		Networks:   pinnedNetwork("172.18.0.4"),
	})

	AddInstaller(service, func() {
		_, err := os.Stat(filepath.Join(ComposeDir, "refresh_token.txt"))
		if err != nil {
			refreshTok := util.SecureToken()
			sharedSecret := util.SecureToken()
			configContent := fmt.Sprintf(string(coreConfigFile), refreshTok, sharedSecret)

			_ = os.WriteFile(filepath.Join(configDir, "config.yaml"), []byte(configContent), 0640)
			_ = os.WriteFile(filepath.Join(ComposeDir, "refresh_token.txt"), []byte(refreshTok), 0600)

			RpcClientConfigure(refreshTok)
		}
	})

	AddStartupHook(service, func() {
		_, err := os.Stat(filepath.Join(configDir, ".installer-flag"))
		if err == nil {
			return
		}

		StartServiceEx(service, true)
		deadline := time.Now().Add(90 * time.Second)

		LogOutputRunWork("Waiting for UCloud/Core", func(ch chan string) error {
			rpc.ClientAllowSilentAuthTokenRenewalErrors.Store(true)
			defer rpc.ClientAllowSilentAuthTokenRenewalErrors.Store(false)

			for time.Now().Before(deadline) {
				if EnvironmentIsReady() {
					return nil
				}
				time.Sleep(100 * time.Millisecond)
			}

			result := ComposeExec("Fetching logs", "core", []string{"cat", "/tmp/service.log", "/var/log/ucloud/server.log"}, ExecuteOptions{Silent: true, ContinueOnFailure: true})
			ch <- result.Stdout
			ch <- result.Stderr
			ch <- "Gave up waiting for UCloud/Core. Check logs in core container."

			return fmt.Errorf("Gave up waiting for UCloud/Core")
		})

		LogOutputRunWork("Importing applications", func(ch chan string) error {
			checksum := "62bbef4ea7b32c25808d1b084bb9ca61767d65a3e2a1a8a947426e424ca61159"

			started := false
			var lastError error
			for attempt := 0; attempt < 3 && !started; attempt++ {
				if attempt > 0 {
					ch <- fmt.Sprintf("Could not start the import (%v), retrying (%d/2)...\n", lastError, attempt)
					time.Sleep(2 * time.Second)
				}

				_, herr := orcapi.AppsDevImport.Invoke(orcapi.AppCatalogDevImportRequest{
					Endpoint: fmt.Sprintf("https://launcher-assets.cloud.sdu.dk/%s.zip", checksum),
					Checksum: checksum,
				})

				if herr == nil || herr.StatusCode == http.StatusConflict {
					started = true
				} else {
					lastError = herr.AsError()
				}
			}

			if !started {
				return fmt.Errorf("Could not start the application import: %v", lastError)
			}

			lastMessage := ""
			appDeadline := time.Now().Add(10 * time.Minute)
			for time.Now().Before(appDeadline) {
				status, herr := orcapi.AppsDevImportStatus.Invoke(util.Empty{})
				if herr != nil {
					return herr.AsError()
				}

				if status.Error != "" {
					return fmt.Errorf("%s", status.Error)
				}

				if status.Message != "" && status.Message != lastMessage {
					lastMessage = status.Message
					ch <- status.Message + "\n"
				}

				if status.Complete {
					return nil
				}

				time.Sleep(500 * time.Millisecond)
			}

			return fmt.Errorf("Application import took too long. Last status: %s", lastMessage)
		})

		LogOutputRunWork("Creating admin user", func(ch chan string) error {
			_, herr := fndapi.UsersCreate.Invoke([]fndapi.UsersCreateRequest{
				{
					Username:   "user",
					Password:   "mypassword",
					Email:      "user@ucloud.localhost.direct",
					Role:       util.OptValue[fndapi.PrincipalRole](fndapi.PrincipalAdmin),
					FirstNames: util.OptValue("User"),
					LastName:   util.OptValue("Example"),
				},
			})

			return herr.AsError()
		})

		_ = os.WriteFile(filepath.Join(configDir, ".installer-flag"), []byte("OK"), 0644)
	})

	{
		postgres := Service{
			Name:     "postgres",
			Title:    "Postgres",
			Flags:    SvcLogs,
			UiParent: UiParentCore,
		}

		data := AddVolume(postgres, "data")

		AddService(postgres, DockerComposeService{
			Image:       "postgres:17.0",
			Hostname:    "postgres",
			Restart:     "always",
			Environment: []string{"POSTGRES_PASSWORD=postgrespassword"},
			Ports:       []string{"35432:5432"},
			Networks:    pinnedNetwork("172.18.0.3"),
			Volumes: []string{
				Mount(data, "/var/lib/postgresql/data"),
			},
		})
	}
}
