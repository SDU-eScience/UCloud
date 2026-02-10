package launcher

import (
	_ "embed"
	"errors"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"time"

	apm "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"

	orcapi "ucloud.dk/shared/pkg/orchestrators"

	"gopkg.in/yaml.v3"
)

//go:embed config/k8s/config.yaml
var k8sProviderConfig []byte

//go:embed config/k8s/init.sh
var k8sInitScript []byte

//go:embed config/k8s/kubevirt_init.sh
var k8sKubevirtInitScript []byte

func ProviderK8s() {
	provider := Service{
		Name:     "k8s",
		Title:    "IM",
		Flags:    SvcExec | SvcLogs | SvcNative,
		UiParent: UiParentK8s,
		Feature:  FeatureProviderK8s,
	}

	k3sOutput := AddDirectory(provider, "k3s-output")
	storage := AddDirectory(provider, "storage")
	imConfig := AddDirectory(provider, "config")
	logDir := AddDirectory(provider, "logs")

	volumes := []string{
		Mount(imConfig, "/etc/ucloud"),
		Mount(logDir, "/var/log/ucloud"),
		Mount(k3sOutput, "/mnt/k3s"),
		Mount(storage, "/mnt/storage"),
		Mount(filepath.Join(RepoRoot, "/provider-integration/im2"), "/opt/ucloud"),
		Mount(filepath.Join(RepoRoot, "/provider-integration/gonja"), "/opt/gonja"),
		Mount(filepath.Join(RepoRoot, "/provider-integration/pgxscan"), "/opt/pgxscan"),
		Mount(filepath.Join(RepoRoot, "/provider-integration/shared"), "/opt/shared"),
		Mount(filepath.Join(RepoRoot, "/provider-integration/walk"), "/opt/walk"),
	}

	if goCacheDir := os.Getenv("UCLOUD_GO_CACHE_DIR"); goCacheDir != "" {
		dir := filepath.Join(goCacheDir, provider.Name)
		_ = os.MkdirAll(dir, 0777)
		volumes = append(volumes, Mount(dir, "/root/go"))

		buildDir := filepath.Join(goCacheDir, provider.Name+"-build")
		_ = os.MkdirAll(buildDir, 0777)
		volumes = append(volumes, Mount(buildDir, "/root/.cache/go-build"))
	}

	AddService(provider, DockerComposeService{
		Image:    ImDevImage,
		Hostname: "k8s",
		Ports:    []string{"51240:51233"},
		Command:  []string{"sleep", "inf"},
		Volumes:  volumes,
	})

	writeYaml := func(path string, data map[string]any, perm os.FileMode) error {
		dataBytes, _ := yaml.Marshal(data)
		err := os.WriteFile(path, dataBytes, perm)
		return err
	}

	AddStartupHook(provider, func() {
		_, err := os.Stat(filepath.Join(imConfig, ".installer-flag"))
		if err == nil {
			return
		}

		defer func() {
			_ = os.WriteFile(filepath.Join(imConfig, ".installer-flag"), []byte("flag"), 0644)
		}()

		var projectId string
		publicKey := ""
		refreshToken := ""

		LogOutputRunWork("Registering provider", func(ch chan string) error {
			projectResp, herr := fndapi.ProjectInternalCreate.Invoke(fndapi.ProjectInternalCreateRequest{
				Title:        "Provider K8s",
				BackendId:    "provider-k8s",
				PiUsername:   "user",
				SubAllocator: util.OptValue(true),
			})

			if herr != nil {
				return errors.Join(fmt.Errorf("failed to register provider"), herr.AsError())
			}

			projectId = projectResp.Id

			projectHeaders := http.Header{}
			projectHeaders.Add("Project", projectId)

			resp, herr := orcapi.ProviderCreate.InvokeEx(
				rpc.DefaultClient,
				fndapi.BulkRequestOf(orcapi.ProviderSpecification{
					Id:     "k8s",
					Domain: "k8s",
					Https:  false,
					Port:   8889,
				}),
				rpc.InvokeOpts{Headers: projectHeaders},
			)

			if herr != nil {
				return errors.Join(fmt.Errorf("failed to create provider"), herr.AsError())
			}

			providerId := resp.Responses[0].Id
			providerResp, herr := orcapi.ProviderRetrieve.Invoke(orcapi.ProviderRetrieveRequest{
				Id: providerId,
			})

			publicKey = providerResp.PublicKey
			refreshToken = providerResp.RefreshToken

			if herr != nil {
				return errors.Join(fmt.Errorf("failed to retrieve provider"), herr.AsError())
			}

			_, herr = apm.GrantsUpdateRequestSettings.InvokeEx(
				rpc.DefaultClient,
				apm.GrantRequestSettings{
					Enabled:     true,
					Description: "An example grant allocator",
					AllowRequestsFrom: []apm.UserCriteria{{
						Type: apm.UserCriteriaTypeAnyone,
					}},
					ExcludeRequestsFrom: []apm.UserCriteria{},
					Templates: apm.Templates{
						Type:            apm.TemplatesTypePlainText,
						PersonalProject: "Please describe why you are applying for resources",
						NewProject:      "Please describe why you are applying for resources",
						ExistingProject: "Please describe why you are applying for resources",
					},
				},
				rpc.InvokeOpts{Headers: projectHeaders},
			)

			if herr != nil {
				return errors.Join(fmt.Errorf("failed to update settings"), herr.AsError())
			}

			return nil
		})

		LogOutputRunWork("Creating configuration files", func(ch chan string) error {
			err := os.WriteFile(filepath.Join(imConfig, "ucloud_crt.pem"), []byte(publicKey), 0600)
			if err != nil {
				return err
			}

			err = writeYaml(filepath.Join(imConfig, "server.yaml"), map[string]any{
				"refreshToken": refreshToken,
				"database": map[string]any{
					"embedded": false,
					"username": "postgres",
					"password": "postgrespassword",
					"database": "postgres",
					"ssl":      false,
					"host": map[string]any{
						"address": "k8s-postgres",
					},
				},
			}, 0600)
			if err != nil {
				return err
			}

			err = os.WriteFile(filepath.Join(imConfig, "config.yaml"), k8sProviderConfig, 0660)
			if err != nil {
				return err
			}

			err = os.WriteFile(filepath.Join(imConfig, "init.sh"), k8sInitScript, 0770)
			if err != nil {
				return err
			}

			return nil
		})

		ComposeExec("Provisioning K8s resources", provider.Name, []string{"bash", "/etc/ucloud/init.sh"}, ExecuteOptions{})
		StartServiceEx(provider, true)
		for i := range 30 {
			res := ComposeExec(
				"Provisioning test IPs",
				provider.Name,
				[]string{"ucloud", "ips", "add", "10.99.0.0/24"},
				ExecuteOptions{ContinueOnFailure: i < 29},
			)

			if res.ExitCode == 0 {
				break
			} else {
				time.Sleep(1 * time.Second)
			}
		}
		ComposeExec(
			"Provisioning test license",
			provider.Name,
			[]string{"ucloud", "license", "add", "test-license", "--license=test"},
			ExecuteOptions{},
		)
	})

	{
		k3s := Service{
			Name:     "k3s",
			Title:    "K3s cluster",
			Flags:    SvcExec | SvcLogs,
			UiParent: UiParentK8s,
			Feature:  FeatureProviderK8s,
		}

		data := AddVolume(k3s, "data")
		cni := AddVolume(k3s, "cni")
		kubelet := AddVolume(k3s, "kubelet")
		etc := AddVolume(k3s, "etc")

		AddService(k3s, DockerComposeService{
			Image:    "rancher/k3s:v1.31.12-rc1-k3s1",
			Hostname: "im2k3",
			Restart:  "always",
			Environment: []string{
				"K3S_KUBECONFIG_OUTPUT=/output/kubeconfig.yaml",
				"K3S_KUBECONFIG_MODE=666",
				"K3S_FLANNEL_BACKEND=host-gw",
			},
			Privileged: true,
			Tmpfs:      []string{"/run", "/var/run"},
			Command:    []string{"server", "--disable=traefik", "--disable-network-policy"},
			Volumes: []string{
				Mount(k3sOutput, "/output"),
				Mount(data, "/var/lib/rancher/k3s"),
				Mount(cni, "/var/lib/cni"),
				Mount(kubelet, "/var/lib/kubelet"),
				Mount(etc, "/etc/rancher"),
				Mount(storage, "/mnt/storage"),
				Mount(imConfig, "/etc/ucloud"),
			},
		})
	}

	{
		postgres := Service{
			Name:     "k8s-postgres",
			Title:    "Postgres",
			Flags:    SvcExec | SvcLogs,
			UiParent: UiParentK8s,
			Feature:  FeatureProviderK8s,
		}

		data := AddVolume(postgres, "data")

		AddService(postgres, DockerComposeService{
			Image:       "postgres:15.0",
			Hostname:    "k8s-postgres",
			Restart:     "always",
			Environment: []string{"POSTGRES_PASSWORD=postgrespassword"},
			Ports:       []string{"51241:5432"},
			Volumes: []string{
				Mount(data, "/var/lib/postgresql/data"),
				Mount(filepath.Join(RepoRoot, "/provider-integration/im2"), "/opt/ucloud"),
				Mount(filepath.Join(RepoRoot, "/provider-integration/gonja"), "/opt/gonja"),
				Mount(filepath.Join(RepoRoot, "/provider-integration/pgxscan"), "/opt/pgxscan"),
				Mount(filepath.Join(RepoRoot, "/provider-integration/shared"), "/opt/shared"),
				Mount(filepath.Join(RepoRoot, "/provider-integration/walk"), "/opt/walk"),
			},
		})
	}

	{
		ollama := Service{
			Name:      "ollama",
			Title:     "Ollama",
			Flags:     SvcExec | SvcLogs,
			UiParent:  UiParentK8s,
			Feature:   FeatureAddonInference,
			DependsOn: util.OptValue(FeatureProviderK8s),
		}

		vol := AddVolume(ollama, "data")

		AddService(ollama, DockerComposeService{
			Image:    "ollama/ollama:0.12.11-rc1",
			Hostname: "ollama",
			Restart:  "always",
			Volumes: []string{
				Mount(vol, "/root/.ollama"),
			},
		})
	}

	{
		kubeVirt := Service{
			Name:      "kubevirt",
			Title:     "KubeVirt",
			Flags:     0,
			UiParent:  UiParentK8s,
			Feature:   FeatureAddonKubeVirt,
			DependsOn: util.OptValue(FeatureProviderK8s),
		}

		AddService(kubeVirt, DockerComposeService{
			Image:    "alpine:3",
			Hostname: "kubevirt",
			Restart:  "always",
			Command:  []string{"sleep", "inf"},
		})

		AddStartupHook(kubeVirt, func() {
			ComposeExec(
				"Waiting for K3s to be ready",
				"k3s",
				[]string{"sh", "-c", "until kubectl get nodes >/dev/null 2>&1; do sleep 1; done"},
				ExecuteOptions{},
			)

			ComposeExec(
				"Installing KubeVirt",
				"k3s",
				[]string{
					"sh",
					"-c",
					`
						set -e
						mount --make-rshared /var/run
						mount --make-rshared /
					`,
				},
				ExecuteOptions{},
			)

			ComposeExec(
				"Installing KubeVirt",
				provider.Name,
				[]string{
					"sh",
					"-c",
					string(k8sKubevirtInitScript),
				},
				ExecuteOptions{},
			)
		})
	}
}
