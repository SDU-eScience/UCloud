package launcher2

import (
	_ "embed"
	"fmt"
	"os"
	"path/filepath"
	"time"

	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
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

	AddService(service, DockerComposeService{
		Image:    ImDevImage,
		Hostname: "core2",
		Restart:  "always",
		Ports:    []string{"51245:51233"},
		Command:  []string{"sleep", "inf"},
		Volumes: []string{
			Mount(filepath.Join(RepoRoot, "core2"), "/opt/ucloud"),
			Mount(filepath.Join(RepoRoot, "provider-integration"), "/opt/provider-integration"),
			Mount(configDir, "/etc/ucloud"),
		},
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
		deadline := time.Now().Add(30 * time.Second)

		LogOutputRunWork("Waiting for UCloud/Core", func(ch chan string) error {
			rpc.ClientAllowSilentAuthTokenRenewalErrors.Store(true)
			defer rpc.ClientAllowSilentAuthTokenRenewalErrors.Store(false)

			for time.Now().Before(deadline) {
				if EnvironmentIsReady() {
					return nil
				}
				time.Sleep(100 * time.Millisecond)
			}

			result := ComposeExec("Fetching logs", "core", []string{"cat", "/tmp/service.log", "/var/log/ucloud/server.log"}, ExecuteOptions{Silent: true})
			ch <- result.Stdout
			ch <- result.Stderr
			ch <- "Gave up waiting for UCloud/Core. Check logs in core container."

			return fmt.Errorf("Gave up waiting for UCloud/Core")
		})

		LogOutputRunWork("Importing applications", func(ch chan string) error {
			checksum := "ea9ab32f52379756df5f5cbbcefb33928c49ef8e2c6b135a5048a459e40bc6b2"
			_, herr := orcapi.AppsDevImport.Invoke(orcapi.AppCatalogDevImportRequest{
				Endpoint: fmt.Sprintf("https://launcher-assets.cloud.sdu.dk/%s.zip", checksum),
				Checksum: checksum,
			})

			if herr != nil {
				return herr.AsError()
			}

			success := false
			appDeadline := time.Now().Add(1 * time.Minute)
			for time.Now().Before(appDeadline) {
				ok, herr := orcapi.AppsImportIsDone.Invoke(util.Empty{})
				if herr == nil && ok {
					success = true
					break
				}

				time.Sleep(100 * time.Millisecond)
			}

			if !success {
				return fmt.Errorf("Application import took too long")
			}

			return nil
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
			Image:       "postgres:15.0",
			Hostname:    "postgres",
			Restart:     "always",
			Environment: []string{"POSTGRES_PASSWORD=postgrespassword"},
			Ports:       []string{"35432:5432"},
			Volumes: []string{
				Mount(data, "/var/lib/postgresql/data"),
			},
		})
	}
}
