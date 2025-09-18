package launcher

import (
	_ "embed"
	"log"
	"strconv"
	"sync"
	"time"
	"ucloud.dk/launcher/pkg/termio"
)

const FreeIpaAddon = "free-ipa"

type GoSlurm struct {
	numberOfSlurmNodes  int
	canRegisterProducts bool
}

func NewGoSlurm(canRegisterProducts bool, numberOfSlurmNodes int) GoSlurm {
	return GoSlurm{
		numberOfSlurmNodes:  numberOfSlurmNodes,
		canRegisterProducts: canRegisterProducts,
	}
}

func (gs *GoSlurm) Name() string {
	return "go-slurm"
}

func (gs *GoSlurm) Title() string {
	return "IM2/Slurm"
}

func (gs *GoSlurm) CanRegisterProducts() bool {
	return gs.canRegisterProducts
}

func (gs *GoSlurm) Addons() map[string]string {
	return map[string]string{
		FreeIpaAddon: FreeIpaAddon,
	}
}

func (gs *GoSlurm) Build(cb ComposeBuilder) {
	dataDir := GetDataDirectory()
	provider := NewFile(dataDir).Child("go-slurm", true)

	imDir := provider.Child("im", true)
	imGradle := imDir.Child("gradle", true)
	imData := imDir.Child("data", true)
	imLogs := NewFile(dataDir).Child("logs", true)

	passwdDir := imDir.Child("passwd", true)
	passwdFile := passwdDir.Child("passwd", false)
	groupFile := passwdDir.Child("group", false)
	shadowFile := passwdDir.Child("shadow", false)
	if passwdFile.Exists() {
		passwdFile.WriteText(
			`
				ucloud:x:11042:11042::/home/ucloud:/bin/sh
			`,
		)

		groupFile.WriteText(
			`
				ucloud:x:11042:
			`,
		)

		shadowFile.WriteText(
			`
				ucloud:!:19110::::::
			`,
		)
	}

	info := SlurmBuild(cb, gs, imDir)
	imHome := info.imHome
	imWork := info.imWork
	etcSlurm := info.etcSlurm

	cb.Service(
		"go-slurm",
		"Slurm (Go test)",
		Json{
			//language=json
			`
			{
				"image": "` + imDevImage + `",
				"command": ["sleep", "inf"],
				"hostname": "go-slurm.ucloud",
				"init": true,
				"ports": [
					"` + strconv.Itoa(portAllocator.Allocate(51233)) + `:51233",
					"` + strconv.Itoa(portAllocator.Allocate(51234)) + `:51234",
					"` + strconv.Itoa(portAllocator.Allocate(51235)) + `:51235",
					"` + strconv.Itoa(portAllocator.Allocate(51236)) + `:51236",
					"` + strconv.Itoa(portAllocator.Allocate(51237)) + `:51237",
					"` + strconv.Itoa(portAllocator.Allocate(51238)) + `:51238",
					"` + strconv.Itoa(portAllocator.Allocate(41493)) + `:41493"
				],
				"volumes": [
					"` + imGradle.GetAbsolutePath() + `:/root/.gradle",
					"` + imData.GetAbsolutePath() + `:/etc/ucloud",
					"` + imLogs.GetAbsolutePath() + `:/var/log/ucloud",
					"` + imHome.GetAbsolutePath() + `:/home",
					"` + imWork.GetAbsolutePath() + `:/work",
					"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/im2:/opt/ucloud",
					"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/gonja:/opt/gonja",
					"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/gonja:/opt/pgxscan",
					"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/shared:/opt/shared",
					"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/walk:/opt/walk",
					"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/integration-module/example-extensions/simple:/etc/ucloud/extensions",
					"` + etcSlurm + `:/etc/slurm-llnl",
					"` + passwdDir.GetAbsolutePath() + `:/mnt/passwd"
				],
				"volumes_from": ["slurmdbd:ro"]
			}
			`,
		},
		true,
		true,
		true,
		"",
		"",
	)

	postgresDataDir := NewFile(dataDir).Child("go-slurm-pg-data", true)

	cb.Service(
		"go-slurm-postgres",
		"Slurm (Go): Postgres",
		Json{
			//language=json
			`
			{
				"image": "postgres:15.0",
				"hostname": "go-slurm-postgres",
				"restart": "always",
				"environment":{
					"POSTGRES_PASSWORD": "postgrespassword"
				},
				"volumes": [
					"` + postgresDataDir.GetAbsolutePath() + `:/var/lib/postgresql/data",
					"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/im2:/opt/ucloud",
					"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/gonja:/opt/gonja",
					"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/pgxscan:/opt/pgxscan",
					"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/shared:/opt/shared",
					"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/walk:/opt/walk"
				],
				"ports": [
					"` + strconv.Itoa(portAllocator.Allocate(51239)) + `:5432"
				]
			}
			`,
		},
		true,
		true,
		false,
		"",
		"",
	)
}

//go:embed config/slurm/config.yaml
var GoSlurmConfig []byte

//go:embed config/slurm/secrets.yaml
var GoSlurmSecrets []byte

func (gs *GoSlurm) Install(credentials ProviderCredentials) {
	dataDir := GetDataDirectory()
	slurmProvider := NewFile(dataDir).Child("go-slurm", true)
	imDir := slurmProvider.Child("im", true)
	imDataDir := imDir.Child("data", true)

	installMarker := imDataDir.Child(".install-marker", false)
	if len(readLines(installMarker.GetAbsolutePath())) != 0 {
		return
	}

	imDataDir.Child("server.yaml", false).WriteText(
		//language=yaml
		`refreshToken: ` + credentials.refreshToken + `
database:
  embedded: false
  username: postgres
  password: postgrespassword
  database: postgres
  ssl: false
  host:
    address: go-slurm-postgres`,
	)

	imDataDir.Child("ucloud_crt.pem", false).WriteText(credentials.publicKey)

	imDataDir.Child("config.yaml", false).WriteBytes(GoSlurmConfig)

	imDataDir.Child("secrets.yaml", false).WriteBytes(GoSlurmSecrets)

	SlurmInstall("go-slurm")

	installMarker.WriteText("done")
}

func (gs *GoSlurm) BuildAddon(cb ComposeBuilder, addon string) {
	switch addon {
	case FreeIpaAddon:
		{
			freeIpa := "freeipaDataDir"
			cb.volumes[freeIpa] = freeIpa

			cb.Service(
				"free-ipa",
				"FreeIPA",
				Json{
					//language=json
					`
					{
						"image": "quay.io/freeipa/freeipa-server:almalinux-9",
						"command": ["ipa-server-install", "--domain=free-ipa.ucloud", "--realm=FREE-IPA.UCLOUD", "--netbios-name=FREE-IPA", "--no-ntp", "--skip-mem-check", "--ds-password=adminadmin", "--admin-password=adminadmin", "--unattended"],
						"environment":{
							"DEBUG_TRACE": "true",
							"DEBUG_NO_EXIT": "true"
						},
						"hostname": "ipa.ucloud",
						"init": false,
						"privileged": false,
						"volumes": [
							"` + freeIpa + `:/data:Z",
							"/sys/fs/cgroup:/sys/fs/cgroup"
						],
						"sysctls":{
							"net.ipv6.conf.all.disable_ipv6": "0"
						},
						"security_opt": [
							"seccomp:unconfined"
						],
						"cgroup": "host",
						"stop_grace_period": "2s"
					}
					`,
				},
				true,
				true,
				false,
				"",
				"",
			)
		}
	}
}

func (gs *GoSlurm) InstallAddon(addon string) {
	switch addon {
	case FreeIpaAddon:
		{
			var executeCom = compose.Exec(
				currentEnvironment,
				"go-slurm",
				[]string{
					"sh", "-c", `
					while ! curl --silent -f http://ipa.ucloud/ipa/config/ca.crt > /dev/null; do
						sleep 1
						date
						echo "Waiting for FreeIPA to be ready - Test #1 (expected to take up to 15 minutes)..."
					done
					`,
				},
				false,
			)
			executeCom.SetStreamOutput()
			executeCom.ExecuteToText()

			executeCom = compose.Exec(
				currentEnvironment,
				"free-ipa",
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
				false,
			)
			executeCom.SetStreamOutput()
			executeCom.ExecuteToText()
		}
	}
}

func EnrollClient(client string) {
	termio.WriteLine("Enrolling " + client + " in FreeIPA...")

	// NOTE(Dan): This will "fail" with a bunch of errors and warnings because of systemd.
	// It will, however, actually do all it needs to do. As a result, we supress the output
	// and always exit 0. sssd will fail if freeipa is not ready.
	var executeCom = compose.Exec(
		currentEnvironment,
		client,
		[]string{
			"sh", "-c", `
			ipa-client-install --domain ipa.ucloud --server ipa.ucloud --no-ntp \
			--no-dns-sshfp --principal=admin --password=adminadmin --force-join --unattended || true;
			`,
		},
		false,
	)

	executeCom.ExecuteToText()

	// NOTE(Dan): This one is a bit flakey. Try a few times, this usually works.
	executeCom = compose.Exec(
		currentEnvironment,
		client,
		[]string{
			"sh",
			"-c",
			"sssd || (sleep 1; sssd) || (sleep 1; sssd) || (sleep 1; sssd) || (sleep 1; sssd) " +
				"|| (sleep 1; sssd) || (sleep 1; sssd) || (sleep 1; sssd) || " +
				"(sleep 1; sssd) || (sleep 1; sssd) || (sleep 1; sssd) || (sleep 1; sssd)",
		},
		true,
	)
	executeCom.SetStreamOutput()
	executeCom.ExecuteToText()
	termio.WriteLine("Client " + client + " has been enrolled in FreeIPA!")
}

func (gs *GoSlurm) StartAddon(addon string) {
	switch addon {
	case FreeIpaAddon:
		{
			clientsToEnroll := []string{
				"go-slurm",
				"c1",
				"c2",
				"slurmctld",
				"slurmdbd",
			}
			wg := sync.WaitGroup{}
			for _, client := range clientsToEnroll {
				wg.Add(1)
				go func() {
					defer wg.Done()
					EnrollClient(client)
				}()
			}
			wg.Wait()
		}
	}
}

type SlurmInfo struct {
	imHome   LFile
	imWork   LFile
	etcSlurm string
}

func BuildComputeContainers(
	cb *ComposeBuilder,
	numberOfSlurmNodes int,
	passwdDir LFile,
	imHome LFile,
	imWork LFile,
	etcMunge string,
	etcSlurm string,
	logSlurm string,
) {
	for id := 1; id <= numberOfSlurmNodes; id++ {
		cb.Service(
			"c"+strconv.Itoa(id),
			"Slurm Provider: Compute node "+strconv.Itoa(id),
			Json{
				//language=json
				`{
					"image": "` + slurmImage + `",
					"command": ["slurmd", "sshd", "user-sync"],
					"hostname": "c` + strconv.Itoa(id) + `.ucloud",
					"volumes": [
						"` + passwdDir.GetAbsolutePath() + `:/mnt/passwd",
						"` + imHome.GetAbsolutePath() + `:/home",
						"` + imWork.GetAbsolutePath() + `:/work",
						"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/integration-module:/opt/ucloud",
						"` + etcMunge + `:/etc/munge",
						"` + etcSlurm + `:/etc/slurm",
						"` + logSlurm + `:/var/log/slurm"
					],
					"depends_on": ["slurmctld"],
					"restart": "always"
				}`,
			},
			true,
			true,
			false,
			"",
			"",
		)
	}
}

func SlurmBuild(cb ComposeBuilder, service ComposeService, imDir LFile) SlurmInfo {
	imHome := imDir.Child("home", true)
	imWork := imDir.Child("work", true)
	imMySQLDb := "immysql"
	cb.volumes[imMySQLDb] = imMySQLDb
	etcMunge := "etc_munge"
	cb.volumes[etcMunge] = etcMunge
	etcSlurm := "etc_slurm"
	cb.volumes[etcSlurm] = etcSlurm
	logSlurm := "log_slurm"
	cb.volumes[logSlurm] = logSlurm

	passwdDir := imDir.Child("passwd", true)
	passwdDir.Child("passwd", false)
	passwdDir.Child("group", false)
	passwdDir.Child("shadow", false)

	cb.Service(
		"mysql",
		"Slurm Provider: MySQL (SlurmDB)",
		Json{
			//language=json
			`
				{
					"image": "mysql:8.3.0",
					"hostname": "mysql",
					"ports": [
						"` + strconv.Itoa(portAllocator.Allocate(3306)) + `:3306"
					],
					"environment":{
						"MYSQL_RANDOM_ROOT_PASSWORD": "yes",
						"MYSQL_DATABASE": "slurm_acct_db",
						"MYSQL_USER": "slurm",
						"MYSQL_PASSWORD": "password"
					},
					"volumes": [
						"` + imMySQLDb + `:/var/lib/mysql"
					],
					"restart": "always"
				}
			`,
		},
		true,
		true,
		false,
		"",
		"",
	)

	cb.Service(
		"slurmdbd",
		"Slurm Provider: slurmdbd",
		Json{
			//language=json
			`
			{
				"image": "` + slurmImage + `",
				"command": ["slurmdbd", "sshd", "user-sync"],
				"hostname": "slurmdbd.ucloud",
				"volumes": [
					"` + passwdDir.GetAbsolutePath() + `:/mnt/passwd",
					"` + imHome.GetAbsolutePath() + `:/home",
					"` + imWork.GetAbsolutePath() + `:/work",
					"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/integration-module:/opt/ucloud",
					"` + etcMunge + `:/etc/munge",
					"` + etcSlurm + `:/etc/slurm",
					"` + logSlurm + `:/var/log/slurm"
				],
				"depends_on": ["mysql"],
				"restart": "always"
			}
			`,
		},
		true,
		true,
		false,
		"",
		"",
	)

	cb.Service(
		"slurmctld",
		"Slurm Provider: slurmctld",
		Json{
			//language=json
			`
			{
				"image": "` + slurmImage + `",
				"command": ["slurmctld", "sshd", "user-sync"],
				"hostname": "slurmctld.ucloud",
				"volumes": [
					"` + passwdDir.GetAbsolutePath() + `:/mnt/passwd",
					"` + imHome.GetAbsolutePath() + `:/home",
					"` + imWork.GetAbsolutePath() + `:/work",
					"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/integration-module:/opt/ucloud",
					"` + etcMunge + `:/etc/munge",
					"` + etcSlurm + `:/etc/slurm",
					"` + logSlurm + `:/var/log/slurm"
				],
				"depends_on": ["slurmdbd"],
				"restart": "always"
			}
			`,
		},
		true,
		true,
		false,
		"",
		"",
	)

	switch v := service.(type) {
	case *GoSlurm:
		{
			BuildComputeContainers(&cb, v.numberOfSlurmNodes, passwdDir, imHome, imWork, etcMunge, etcSlurm, logSlurm)
		}
	default:
		log.Println("Attempting to build slurm with a non slurm service")
	}

	return SlurmInfo{imHome: imHome, imWork: imWork, etcSlurm: etcSlurm}
}

func SlurmInstall(providerContainer string) {
	for range 30 {
		executeCom := compose.Exec(
			currentEnvironment,
			"slurmctld",
			[]string{
				"/usr/bin/sacctmgr",
				"--immediate",
				"add",
				"cluster",
				"name=linux",
			},
			false,
		)
		executeCom.SetStreamOutput()
		executeCom.SetAllowFailure()
		success := executeCom.ExecuteToText().First != ""

		if success {
			break
		}

		time.Sleep(2 * time.Second)
	}

	// These are mounted into the container, but permissions are wrong
	var executeCom = compose.Exec(
		currentEnvironment,
		providerContainer,
		[]string{
			"sh",
			"-c",
			"chmod 0755 -R /etc/ucloud/extensions",
		},
		false,
	)

	executeCom.SetStreamOutput()
	executeCom.ExecuteToText()

	// This is to avoid rebuilding the image when the Slurm configuration changes
	executeCom = compose.Exec(
		currentEnvironment,
		"slurmctld",
		[]string{
			"cp",
			"-v",
			"/opt/ucloud/docker/slurm/slurm.conf",
			"/etc/slurm",
		},
		false,
	)
	executeCom.SetStreamOutput()
	executeCom.ExecuteToText()

	executeCom = compose.Exec(
		currentEnvironment,
		"slurmctld",
		[]string{
			"/usr/bin/sacctmgr",
			"--immediate",
			"add",
			"qos",
			"standard",
		},
		false,
	)
	executeCom.SetStreamOutput()
	executeCom.SetAllowFailure()
	executeCom.ExecuteToText()

	// Restart slurmctld in case configuration file has changed
	var stopCom = compose.Stop(currentEnvironment, "slurmctld")
	stopCom.SetStreamOutput()
	stopCom.ExecuteToText()
	var startCom = compose.Start(currentEnvironment, "slurmctld")
	startCom.SetStreamOutput()
	startCom.ExecuteToText()
}
