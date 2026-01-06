package launcher2

import (
	_ "embed"
	"errors"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"strings"

	"gopkg.in/yaml.v3"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

//go:embed config/slurm/config.yaml
var slurmProviderConfig []byte

//go:embed config/slurm/secrets.yaml
var slurmProviderSecrets []byte

//go:embed config/slurm/free-ipa-enroll.sh
var slurmFreeIpaEnroll []byte

//go:embed config/slurm/gpfs_mock.yml
var slurmGpfsMock []byte

func ProviderSlurm() {
	// IM & postgres
	// -----------------------------------------------------------------------------------------------------------------

	provider := Service{
		Name:     "slurm-im",
		Title:    "IM",
		Flags:    SvcExec | SvcLogs | SvcNative | SvcFreeIpa,
		UiParent: UiParentSlurm,
		Feature:  FeatureProviderSlurm,
	}

	imConfig := AddDirectory(provider, "data")
	imLogs := AddDirectory(provider, "logs")
	passwdDir := AddDirectory(provider, "passwd")
	imHome := AddDirectory(provider, "home")
	imWork := AddDirectory(provider, "work")

	etcSlurm := AddDirectory(provider, "slurm-config")

	AddService(provider, DockerComposeService{
		Image:       ImDevImage,
		Hostname:    "go-slurm.ucloud",
		Init:        true,
		Command:     []string{"sleep", "inf"},
		VolumesFrom: []string{"slurmdbd:ro"},
		Ports: []string{
			"51233:51233",
			"51234:51234",
			"51235:51235",
			"51236:51236",
			"51237:51237",
			"51238:51238",
			"41493:41493",
		},
		Volumes: []string{
			Mount(imConfig, "/etc/ucloud"),
			Mount(imLogs, "/var/log/ucloud"),
			Mount(imHome, "/home"),
			Mount(imWork, "/work"),
			Mount(passwdDir, "/mnt/passwd"),
			Mount(etcSlurm, "/etc/slurm-llnl"),
			Mount(filepath.Join(RepoRoot, "/provider-integration/im2"), "/opt/ucloud"),
			Mount(filepath.Join(RepoRoot, "/provider-integration/gonja"), "/opt/gonja"),
			Mount(filepath.Join(RepoRoot, "/provider-integration/pgxscan"), "/opt/pgxscan"),
			Mount(filepath.Join(RepoRoot, "/provider-integration/shared"), "/opt/shared"),
			Mount(filepath.Join(RepoRoot, "/provider-integration/walk"), "/opt/walk"),
		},
	})

	AddInstaller(provider, func() {
		if _, err := os.Stat(filepath.Join(passwdDir, "passwd")); err == nil {
			_ = os.WriteFile(filepath.Join(passwdDir, "passwd"), []byte("ucloud:x:11042:11042::/home/ucloud:/bin/sh"), 0660)
			_ = os.WriteFile(filepath.Join(passwdDir, "group"), []byte("ucloud:x:11042:"), 0660)
			_ = os.WriteFile(filepath.Join(passwdDir, "shadow"), []byte("ucloud:!:19110::::::"), 0660)
		}
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
				Title:        "Provider Slurm",
				BackendId:    "provider-slurm",
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
					Id:     "slurm",
					Domain: "slurm-im",
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
						"address": "slurm-postgres",
					},
				},
			}, 0600)
			if err != nil {
				return err
			}

			err = os.WriteFile(filepath.Join(imConfig, "config.yaml"), slurmProviderConfig, 0660)
			if err != nil {
				return err
			}

			err = os.WriteFile(filepath.Join(imConfig, "secrets.yaml"), slurmProviderSecrets, 0770)
			if err != nil {
				return err
			}

			err = os.WriteFile(filepath.Join(imConfig, "gpfs_mock.yaml"), slurmGpfsMock, 0660)
			if err != nil {
				return err
			}

			return nil
		})
	})

	{
		postgres := Service{
			Name:     "slurm-postgres",
			Title:    "Postgres (for IM)",
			Flags:    SvcExec | SvcLogs,
			UiParent: UiParentSlurm,
			Feature:  FeatureProviderSlurm,
		}

		data := AddVolume(postgres, "data")

		AddService(postgres, DockerComposeService{
			Image:       "postgres:15.0",
			Hostname:    "slurm-postgres",
			Restart:     "always",
			Environment: []string{"POSTGRES_PASSWORD=postgrespassword"},
			Ports:       []string{"51239:5432"},
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

	// Slurm Cluster
	// -----------------------------------------------------------------------------------------------------------------

	slurmMySql := Service{
		Name:     "slurm-mysql",
		Title:    "MySQL (for Slurm)",
		Flags:    SvcExec | SvcLogs,
		UiParent: UiParentSlurm,
		Feature:  FeatureProviderSlurm,
	}

	slurmMySqlData := AddVolume(slurmMySql, "data")

	AddService(slurmMySql, DockerComposeService{
		Image:    "mysql:8.3.0",
		Hostname: "mysql",
		Restart:  "always",
		Ports:    []string{"3306:3306"},
		Environment: []string{
			"MYSQL_RANDOM_ROOT_PASSWORD=yes",
			"MYSQL_DATABASE=slurm_acct_db",
			"MYSQL_USER=slurm",
			"MYSQL_PASSWORD=password",
		},
		Volumes: []string{
			Mount(slurmMySqlData, "/var/lib/mysql"),
		},
	})

	slurmDbd := Service{
		Name:     "slurmdbd",
		Title:    "slurmdbd",
		Flags:    SvcExec | SvcLogs | SvcFreeIpa,
		UiParent: UiParentSlurm,
		Feature:  FeatureProviderSlurm,
	}

	etcMunge := AddVolume(slurmDbd, "munge")
	slurmLogs := AddVolume(slurmDbd, "slurm-logs")

	AddService(slurmDbd, DockerComposeService{
		Image:     SlurmImage,
		Hostname:  "slurmdbd.ucloud",
		Restart:   "always",
		DependsOn: []string{slurmMySql.Name},
		Command:   []string{"slurmdbd", "sshd", "user-sync"},
		Volumes: []string{
			Mount(passwdDir, "/mnt/passwd"),
			Mount(imHome, "/home"),
			Mount(imWork, "/work"),
			Mount(etcMunge, "/etc/munge"),
			Mount(etcSlurm, "/etc/slurm"),
			Mount(slurmLogs, "/var/log/slurm"),
		},
	})

	slurmCtld := Service{
		Name:     "slurmctld",
		Title:    "slurmctld",
		Flags:    SvcExec | SvcLogs | SvcFreeIpa,
		UiParent: UiParentSlurm,
		Feature:  FeatureProviderSlurm,
	}

	AddService(slurmCtld, DockerComposeService{
		Image:     SlurmImage,
		Hostname:  "slurmctld.ucloud",
		Restart:   "always",
		DependsOn: []string{slurmDbd.Name},
		Command:   []string{"slurmctld", "sshd", "user-sync"},
		Init:      true,
		Volumes: []string{
			Mount(passwdDir, "/mnt/passwd"),
			Mount(imHome, "/home"),
			Mount(imWork, "/work"),
			Mount(etcMunge, "/etc/munge"),
			Mount(etcSlurm, "/etc/slurm"),
			Mount(slurmLogs, "/var/log/slurm"),
			Mount(filepath.Join(RepoRoot, "/provider-integration/docker/slurm"), "/opt/slurm-image"), // needed for config file
		},
	})

	var computeNodes []Service

	for i := 1; i <= 2; i++ {
		computeNode := Service{
			Name:     fmt.Sprintf("slurm-c%d", i),
			Title:    fmt.Sprintf("CPU Node %d", i),
			Flags:    SvcLogs | SvcExec | SvcFreeIpa,
			UiParent: UiParentSlurm,
			Feature:  FeatureProviderSlurm,
		}

		AddService(computeNode, DockerComposeService{
			Image:     SlurmImage,
			Hostname:  fmt.Sprintf("c%d.ucloud", i),
			Restart:   "always",
			DependsOn: []string{slurmCtld.Name},
			Command:   []string{"slurmd", "sshd", "user-sync"},
			Volumes: []string{
				Mount(passwdDir, "/mnt/passwd"),
				Mount(imHome, "/home"),
				Mount(imWork, "/work"),
				Mount(etcMunge, "/etc/munge"),
				Mount(etcSlurm, "/etc/slurm"),
				Mount(slurmLogs, "/var/log/slurm"),
			},
		})

		computeNodes = append(computeNodes, computeNode)
	}

	// NOTE(Dan): The last compute node is hooked to ensure that the cluster is fully up and running.
	AddStartupHook(computeNodes[len(computeNodes)-1], func() {
		_, err := os.Stat(filepath.Join(etcSlurm, ".installer-flag"))
		installerHasRun := err == nil

		defer func() {
			_ = os.WriteFile(filepath.Join(etcSlurm, ".installer-flag"), []byte("flag"), 0644)
		}()

		// This is to avoid rebuilding the image when the Slurm configuration changes
		ComposeExec(
			"Preparing files",
			slurmCtld.Name,
			[]string{
				"sh", "-c",
				"cp -v /opt/slurm-image/*.conf /etc/slurm ; chmod 600 /etc/slurm/*.conf",
			},
			ExecuteOptions{},
		)

		if !installerHasRun {
			const maxAttempts = 30
			for range maxAttempts {
				res := ComposeExec(
					"Registering cluster",
					slurmCtld.Name,
					[]string{
						"bash", "-c",
						"/usr/bin/sacctmgr --immediate add cluster name=linux ; STATUS=$? ; sleep 2",
					},
					ExecuteOptions{ContinueOnFailure: true},
				)
				success := strings.Contains(res.Stdout+res.Stderr, "Not adding")

				if success {
					break
				}
			}
		}

		ComposeExec(
			"Preparing files",
			slurmCtld.Name,
			[]string{
				"/usr/bin/sacctmgr",
				"--immediate",
				"add",
				"qos",
				"standard",
			},
			ExecuteOptions{ContinueOnFailure: true},
		)

		// Restart slurmctld in case configuration file has changed
		StartServiceEx(slurmCtld, true)
	})

	// FreeIPA
	// -----------------------------------------------------------------------------------------------------------------
	freeIpa := Service{
		Name:     "free-ipa",
		Title:    "FreeIPA",
		Flags:    SvcExec | SvcLogs,
		UiParent: UiParentSlurm,
		Feature:  FeatureProviderSlurm,
	}

	freeIpaData := AddVolume(freeIpa, "data")

	AddService(freeIpa, DockerComposeService{
		Image:    "quay.io/freeipa/freeipa-server:almalinux-9",
		Hostname: "ipa.ucloud",
		Command: []string{
			"ipa-server-install",
			"--domain=free-ipa.ucloud",
			"--realm=FREE-IPA.UCLOUD",
			"--netbios-name=FREE-IPA",
			"--no-ntp",
			"--skip-mem-check",
			"--ds-password=adminadmin",
			"--admin-password=adminadmin",
			"--unattended",
		},
		Environment: []string{
			"DEBUG_TRACE=true",
			"DEBUG_NO_EXIT=true",
		},
		Privileged: false,
		Init:       false,
		Sysctls: map[string]string{
			"net.ipv6.conf.all.disable_ipv6": "0",
		},
		SecurityOpt:     []string{"seccomp:unconfined"},
		Cgroup:          "host",
		StopGracePeriod: "2s",
		Volumes: []string{
			Mount(freeIpaData, "/data:Z"),
			Mount("/sys/fs/cgroup", "/sys/fs/cgroup"),
		},
	})

	AddStartupHook(freeIpa, func() {
		_, err := os.Stat(filepath.Join(freeIpaData, ".installer-flag"))
		installerHasRun := err == nil

		defer func() {
			_ = os.WriteFile(filepath.Join(freeIpaData, ".installer-flag"), []byte("flag"), 0644)
		}()

		if !installerHasRun {
			ComposeExec(
				"Waiting for FreeIPA 1/2",
				provider.Name,
				[]string{
					"sh", "-c", `
					while ! curl --silent -f http://ipa.ucloud/ipa/config/ca.crt > /dev/null; do
						sleep 1
						date
						echo "Waiting for FreeIPA to be ready - Test #1 (expected to take up to 15 minutes)..."
					done
					`,
				},
				ExecuteOptions{},
			)

			ComposeExec(
				"Waiting for FreeIPA 2/2",
				freeIpa.Name,
				[]string{
					"sh", "-c", `
					while ! echo adminadmin | kinit admin; do
						sleep 1
						date
						echo "Waiting for FreeIPA to be ready - Test #2 (expected to take a few minutes)..."
					done

					echo "FreeIPA is now ready!"
					`,
				},
				ExecuteOptions{},
			)
		}

		for _, svc := range Services {
			if svc.Flags&SvcFreeIpa != 0 {
				// NOTE(Dan): This will "fail" with a bunch of errors and warnings because of systemd.
				// It will, however, actually do all it needs to do. As a result, we suppress the output
				// and always exit 0. sssd will fail if FreeIPA is not ready.
				ComposeExec(
					fmt.Sprintf("Enrolling %s into FreeIPA", svc.Title),
					svc.Name,
					[]string{
						"sh", "-c", `
							ipa-client-install --domain ipa.ucloud --server ipa.ucloud --no-ntp \
							--no-dns-sshfp --principal=admin --password=adminadmin --force-join --unattended &> /dev/null || true;
						`,
					},
					ExecuteOptions{},
				)

				ComposeExec(
					fmt.Sprintf("Enrolling %s into FreeIPA", svc.Title),
					svc.Name,
					[]string{
						"sh",
						"-c",
						fmt.Sprintf("cat << 'EOF' > /tmp/free-ipa-enroll.sh\n%s\nEOF", string(slurmFreeIpaEnroll)),
					},
					ExecuteOptions{},
				)

				// NOTE(Dan): This one is a bit flakey. Try a few times, this usually works.
				ComposeExec(
					fmt.Sprintf("Enrolling %s into FreeIPA", svc.Title),
					svc.Name,
					[]string{
						"bash",
						"/tmp/free-ipa-enroll.sh",
					},
					ExecuteOptions{},
				)
			}
		}
	})
}
