package launcher

import (
	"context"
	"log"
	"os"
	"path/filepath"
	"slices"
	"strconv"
	"strings"
	"ucloud.dk/launcher/pkg/termio"
)

type Json struct {
	encoded string
}

const imDevImage = "dreg.cloud.sdu.dk/ucloud-dev/integration-module:2024.1.35"
const slurmImage = "dreg.cloud.sdu.dk/ucloud-dev/slurm:2024.1.35"

type PortAllocator interface {
	Allocate(port int) int
}

type abstractPortAllocator struct{ PortAllocator }

type Direct struct {
	abstractPortAllocator
}

func (d Direct) allocate(port int) int {
	return port
}

type Remapped struct {
	abstractPortAllocator
	portAllocator  int
	allocatedPorts map[int]int
}

func (r Remapped) allocate(port int) int {
	r.allocatedPorts[port] = r.portAllocator
	r.portAllocator++
	return r.portAllocator
}

type Environment struct {
	name        string
	repoRoot    LFile
	doWriteFile bool
}

func GetDataDirectory() string {
	path, _ := filepath.Abs(currentEnvironment.Name())
	exists, _ := os.Stat(path)
	if exists == nil {
		HardCheck(os.MkdirAll(path, 644))
	}
	return path
}

type ComposeBuilder struct {
	environment Environment
	volumes     map[string]string
	services    map[string]Json
}

func (c ComposeBuilder) createComposeFile() string {
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

func (c ComposeBuilder) service(
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
	AllServices[name] = &Service{
		containerName:        name,
		title:                title,
		logsSupported:        logsSupported,
		execSupported:        execSupported,
		useServiceConvention: serviceConvention,
		address:              address,
		uiHelp:               uiHelp,
	}
}

func (e Environment) createComposeFile(services []ComposeService) LFile {
	disableRemoteFileWriting := !e.doWriteFile
	loadingTitle := ""
	if e.doWriteFile {
		loadingTitle = "Creating compose environment..."
	} else {
		loadingTitle = "Initializing service list..."
	}

	var lfile = LocalFile{}
	termio.LoadingIndicator(
		loadingTitle,
		func(output *os.File) error {
			var err error
			cb := ComposeBuilder{e, map[string]string{}, map[string]Json{}}
			for _, service := range cb.services {
				context.TODO()
			}

			allAddons := ListAddons()
			for providerName, addons := range allAddons {
				context.TODO()
			}

			file, err := os.Open(GetDataDirectory())
			if err != nil {
				disableRemoteFileWriting = false
				return err
			}
			lfile := LocalFile{file, abstractLFile{}}
			childFile := lfile.Child("docker-compose.yaml")
			childFile.File.WriteString(cb.createComposeFile())
			return err
		},
	)
	disableRemoteFileWriting = false
	return lfile.LFile
}

type ComposeService struct {
}

type Provider struct {
	ComposeService
	name                string
	title               string
	canRegisterProducts bool
	addons              map[string]string
}

func NewProvider() *Provider {
	return &Provider{
		name:                "",
		title:               "",
		canRegisterProducts: false,
		addons:              map[string]string{},
	}
}

var AllProviders = []Provider{
	Kubernetes,
	Slurm,
	GoSlurm,
	GoKubernetes,
}

func providerFromName(name string) *Provider {
	found := slices.IndexFunc(AllProviders, func(provider Provider) bool {
		return provider.name == name
	})
	if found == -1 {
		log.Fatal("No such provider: " + name)
	}
	return &AllProviders[found]
}

type UCloudBackend struct {
	ComposeService
}

func (uc UCloudBackend) build(cb ComposeBuilder) {
	dataDirectory, err := os.Open(GetDataDirectory())
	HardCheck(err)
	logs := LocalFile{File: dataDirectory}.Child("logs")
	homeDir := LocalFile{File: dataDirectory}.Child("backend-home")
	configDir := LocalFile{File: dataDirectory}.Child("backend-config")
	gradleDir := LocalFile{File: dataDirectory}.Child("backend-gradle")
	postgresDataDir := LocalFile{File: dataDirectory}.Child("pg-data")

	cb.service(
		"backend",
		"UCloud/Core: Backend",
		Json{
			//language=json
			`
				{
					"image": "$imDevImage",
					"command": ["sleep", "inf"],
					"restart": "always",
					"hostname": "backend",
					"ports": [
						"` + strconv.Itoa(portAllocator.Allocate(8080)) + `:8080",
						"` + strconv.Itoa(portAllocator.Allocate(11412)) + `:11412",
						"` + strconv.Itoa(portAllocator.Allocate(51231)) + `:51231"
					],
					"volumes": [
						"` + cb.environment.repoRoot.GetAbsolutePath() + `/backend:/opt/ucloud",
						"` + cb.environment.repoRoot.GetAbsolutePath() + `/frontend-web/webclient:/opt/frontend",
						"` + logs.GetAbsolutePath() + `:/var/log/ucloud",
						"` + configDir.GetAbsolutePath() + `:/etc/ucloud",
						"` + homeDir.GetAbsolutePath() + `:/home",
						"` + gradleDir.GetAbsolutePath() + `:/root/.gradle"
					]
				}
		`},
		true,
		true,
		true,
		"",
		"",
	)

	cb.service(
		"postgres",
		"UCloud/Core: Postgres",
		Json{
			//language=json
			`
			{
				"image": "postgres:15.0",
				"hostname": "postgres",
				"restart": "always",
				"environment":{
					"POSTGRES_PASSWORD": "postgrespassword"
				},
				"volumes": [
					"` + postgresDataDir.GetAbsolutePath() + `:/var/lib/postgresql/data",
					"` + cb.environment.repoRoot.GetAbsolutePath() + `/backend:/opt/ucloud"
				],
				"ports": [
					"` + strconv.Itoa(portAllocator.Allocate(35432)) + `:5432"
				]
			}`,
		},
		true,
		true,
		false,
		"",
		"",
	)

	cb.service(
		"pgweb",
		"UCloud/Core: Postgres UI",
		Json{
			//language=json
			`
			{
				"image": "sosedoff/pgweb",
				"hostname": "pgweb",
				"restart": "always",
				"environment": {
					"DATABASE_URL": "postgres://postgres:postgrespassword@postgres:5432/postgres?sslmode=disable"
				}
			}`,
		},
		true,
		true,
		false,
		"https://postgres.localhost.direct",
		`
			The postgres interface is connected to the database of UCloud/Core. You don't need any credentials. 
		
			If you wish to connect via psql or some tool:
		
			Hostname: localhost<br>
			Port: 35432<br>
			Database: postgres<br>
			Username: postgres<br>
			Password: postgrespassword
		`,
	)

	redisDataDir := LocalFile{File: dataDirectory}.Child("redis-data")
	cb.service(
		"redis",
		"UCloud/Core: Redis",
		Json{
			//language=json
			`
			{
				"image": "redis:5.0.9",
				"hostname": "redis",
				"restart": "always",
				"volumes": [
					"` + redisDataDir.GetAbsolutePath() + `:/data"
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

type UCloudFrontend struct {
	ComposeService
}

func (uf UCloudFrontend) build(cb ComposeBuilder) {
	cb.service(
		"frontend",
		"UCloud/Core: Frontend",
		Json{
			//language=json
			`
				{
					"image": "node",
					"command": ["sh", "-c", "npm install ; npm run start:compose"],
					"restart": "always",
					"hostname": "frontend",
					"working_dir": "/opt/ucloud",
					"volumes": [
					"` + cb.environment.repoRoot.GetAbsolutePath() + `/frontend-web/webclient:/opt/ucloud"
				]
				}
			`,
		},
		true,
		true,
		false,
		"https://ucloud.localhost.direct",
		`
			Default credentials to access UCloud:
			
				Username: user<br>
				Password: mypassword<br>
		`,
	)
}

type ProviderCredentials struct {
	publicKey    string
	refreshToken string
	projectId    string
}

type Kubernetes struct {
	Provider
}

func NewKubernetes() Kubernetes {
	return Kubernetes{
		Provider{name: "k8", title: "Kubernetes", canRegisterProducts: true, addons: map[string]string{}},
	}
}

func (k Kubernetes) build(cb ComposeBuilder) {
	dataDirectory, err := os.Open(GetDataDirectory())
	HardCheck(err)
	k8Provider := LocalFile{File: dataDirectory}.Child("k8")
	k3sDir := k8Provider.Child("k3s")
	k3sOutput := k3sDir.Child("output")

	k3sData := "k3sdata"
	cb.volumes[k3sData] = k3sData
	k3sCni := "k3scni"
	cb.volumes[k3sCni] = k3sCni
	k3sKubelet := "k3skubelet"
	cb.volumes[k3sKubelet] = k3sKubelet
	k3sEtc := "k3setcd"
	cb.volumes[k3sEtc] = k3sEtc

	imDir := k8Provider.Child("im")
	imGradle := imDir.Child("gradle")
	imData := imDir.Child("data")
	imStorage := imDir.Child("storage")
	imStorage.Child("home")
	imStorage.Child("projects")
	imStorage.Child("collections")
	imLogs := LocalFile{File: dataDirectory}.Child("logs")

	passwdDir := imDir.Child("passwd")
	passwdFile := passwdDir.Child("passwd")
	groupFile := passwdDir.Child("group")
	shadowFile := passwdDir.Child("shadow")
	passFileInfo, _ := os.Open(passwdFile.File.Name())

	if passFileInfo != nil {
		passwdFile.File.WriteString(
			`
				ucloud:x:998:998::/home/ucloud:/bin/sh
				ucloudalt:x:11042:11042::/home/ucloudalt:/bin/sh
				`,
		)
		groupFile.File.WriteString(
			`
				ucloud:x:998:
				ucloudalt:x:11042:
			`,
		)
		shadowFile.File.WriteString(
			`
				ucloud:!:19110::::::
				ucloudalt:!:19110::::::
			`,
		)
	}

	cb.service(
		"k3",
		"K8 Provider: K3s Node",
		Json{
			//language=json
			`
			{
				"image": "rancher/k3s:v1.21.6-rc2-k3s1",
				"privileged": true,
				"tmpfs": ["/run", "/var/run"],
				"environment": [
				"K3S_KUBECONFIG_OUTPUT=/output/kubeconfig.yaml",
				"K3S_KUBECONFIG_MODE=666"
			],
				"command": ["server"],
				"hostname": "k3",
				"restart": "always",
				"volumes": [
				"` + k3sOutput.GetAbsolutePath() + `:/output",
				"` + k3sData + `:/var/lib/rancher/k3s",
				"` + k3sCni + `:/var/lib/cni",
				"` + k3sKubelet + `:/var/lib/kubelet",
				"` + k3sEtc + `:/etc/rancher",
				"` + imStorage.GetAbsolutePath() + `:/mnt/storage"
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

	cb.service(
		"k8",
		"K8 Provider: Integration module",
		Json{
			//language=json
			`
			{
				"image": "$imDevImage",
				"command": ["sleep", "inf"],
				"hostname": "k8",
				"ports": ["` + strconv.Itoa(portAllocator.Allocate(51232)) + `:51232"],
				"volumes": [
				"` + imGradle.GetAbsolutePath() + `:/root/.gradle",
				"` + imData.GetAbsolutePath() + `:/etc/ucloud",
				"` + imLogs.GetAbsolutePath() + `:/var/log/ucloud",
				"` + k3sOutput.GetAbsolutePath() + `:/mnt/k3s",
				"` + imStorage.GetAbsolutePath() + `:/mnt/storage",
				"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/integration-module:/opt/ucloud",
				"` + passwdDir.GetAbsolutePath() + `:/mnt/passwd"
			]
			}
			`,
		},
		true,
		true,
		true,
		"",
		"",
	)

	cb.service(
		"k8pgweb",
		"K8 Provider: Postgres UI",
		Json{
			//language=json
			`
			{
				"image": "sosedoff/pgweb",
				"hostname": "k8pgweb",
				"restart": "always",
				"environment":{
					"DATABASE_URL": "postgres://postgres:postgrespassword@k8:5432/postgres?sslmode=disable"
				}
			}
			`,
		},
		true,
		true,
		false,
		"https://k8-pg.localhost.direct",
		"",
	)
}

func (k Kubernetes) install(credentials ProviderCredentials) {
	k8Provider := LocalFile{File: currentEnvironment}.Child("k8")
	imDir := k8Provider.Child("im")
	imData := imDir.Child("data")

	installMarker := imData.Child(".install-marker")
	info, err := os.Stat(installMarker.File.Name())
	HardCheck(err)
	if info != nil {
		return
	}
	imData.Child("core.yaml").WriteText(
		//language=yaml
		`
			providerId: k8
			launchRealUserInstances: false
			allowRootMode: true
			developmentMode: true
			hosts:
				ucloud:
					host: backend
					scheme: http
					port: 8080
				self:
					host: k8.localhost.direct
					scheme: https
					port: 443
			cors:
				allowHosts: ["ucloud.localhost.direct"]
	`)

	imData.Child("server.yaml").WriteText(
		//language=yaml
		`
			refreshToken: ${credentials.refreshToken}
			envoy:
				executable: /usr/bin/envoy
				funceWrapper: false
				directory: /var/run/ucloud/envoy
			database:
				type: Embedded
				directory: /etc/ucloud/pgsql
				host: 0.0.0.0
				password: postgrespassword
	`)

	imData.Child("ucloud_crt.pem").WriteText(credentials.publicKey)

	imData.Child("products.yaml").WriteText(
		//language=yaml
		`
			compute:
				syncthing:
					cost: { type: Free }
					syncthing:
						description: A product for use in syncthing
						cpu: 1
						memory: 1
						gpu: 0
				cpu:
					cost: { type: Money }
					template:
						cpu: [1, 2, 200]
						memory: 1
						description: An example CPU machine with 1 vCPU.
						pricePerHour: 0.5
				cpu-h:
					cost:
						type: Resource
						interval: Minutely
					template:
						cpu: [1, 2]
						memory: 1
						description: An example CPU machine with 1 vCPU.
				storage:
					storage:
						cost:
							type: Resource
							unit: GB
						storage:
							description: An example storage system
						share:
							description: This drive type is used for shares only.
						project-home:
							description: This drive type is used for member files of a project only.
				publicLinks:
					public-link:
						cost: { type: Free }
						public-link:
							description: An example public link
				publicIps:
					public-ip:
						cost:
							type: Resource
							unit: IP
						public-ip:
							description: A _fake_ public IP product
				licenses:
					license:
						cost: { type: Resource }
						license:
							description: A _fake_ license
							tags: ["fake", "license"]
			`,
	)

	imData.Child("plugins.yaml").WriteText(
		//language=yaml
		`
			connection:
				type: UCloud
				redirectTo: https://ucloud.localhost.direct
				insecureMessageSigningForDevelopmentPurposesOnly: true
				
			jobs:
				default:
					type: UCloud
					matches: "*"
					kubernetes:
						namespace: ucloud-apps
					scheduler: Pods
					developmentMode:
						fakeIpMount: true
						fakeMemoryAllocation: true
						usePortForwarding: true
				
			fileCollections:
				default:
					type: UCloud
					matches: "*"
				
			files:
				default:
					type: UCloud
					matches: "*"
					mountLocation: "/mnt/storage"
				
			ingresses:
				default:
					type: UCloud
					matches: "*"
					domainPrefix: k8-app-
					domainSuffix: .localhost.direct
				
			publicIps:
				default:
					type: UCloud
					matches: "*"
					iface: dummy
					gatewayCidr: null
				
			licenses:
				default:
					type: Generic
					matches: "*"
				
			shares:
				default:
					type: UCloud
					matches: "*"
		`,
	)

	compose.Exec(
		currentEnvironment,
		"k8",
		[]string{
			"sh",
			"-c",
			// NOTE(Dan): We have to use the UID/GID here instead of UCloud since the user hasn't been
			// created yet.
			"chown -R 11042:11042 /mnt/storage/*",
		},
		false,
	).streamOutput().executeToText()

	compose.Exec(
		currentEnvironment,
		"k8",
		[]string{
			"sh", "-c",
			`
			while ! test -e "/mnt/k3s/kubeconfig.yaml"; do
			sleep 1
			echo "Waiting for Kubernetes to be ready..."
			done
			`,
		},
		false,
	).streamOutput().executeToText()

	compose.Exec()
		currentEnvironment,
		"k8",
		[]string{
			"sed",
			"-i",
			"s/127.0.0.1/k3/g",
			"/mnt/k3s/kubeconfig.yaml",
		},
		false,
	).streamOutput().executeToText()

	compose.Exec(
		currentEnvironment,
		"k8",
		[]string{
			"kubectl",
			"--kubeconfig",
			"/mnt/k3s/kubeconfig.yaml",
			"create",
			"namespace",
			"ucloud-apps",
		},
		false,
	).streamOutput().executeToText()

	compose.Exec(
		currentEnvironment,
		"k8",
		[]string{
			"sh",
			"-c",
			`
			cat > /tmp/pvc.yml << EOF
			---
			apiVersion: v1
			kind: PersistentVolume
			metadata:
				name: cephfs
				namespace: ucloud-apps
			spec:
				capacity:
					storage: 1000Gi
				volumeMode: Filesystem
				accessModes:
					- ReadWriteMany
				persistentVolumeReclaimPolicy: Retain
				storageClassName: ""
				hostPath:
					path: "/mnt/storage"

			---
			apiVersion: v1
			kind: PersistentVolumeClaim
			metadata:
				name: cephfs
				namespace: ucloud-apps
			spec:
				accessModes:
					- ReadWriteMany
				storageClassName: ""
				volumeName: cephfs
				resources:
					requests:
						storage: 1000Gi
			EOF
			`,
		},
	false,
	).streamOutput().executeToText()

	compose.Exec(
		currentEnvironment,
		"k8",
		[]string{"kubectl", "--kubeconfig", "/mnt/k3s/kubeconfig.yaml", "create", "-f", "/tmp/pvc.yml"},
		false,
	).streamOutput().executeToText()

	compose.Exec(
		currentEnvironment,
		"k8",
		[]string{"rm", "/tmp/pvc.yml"},
		false,
	).streamOutput().executeToText()

	installMarker.WriteText("done")
}

type GoKubernetes struct {
	Provider
}

func NewGoKubernetes() *GoKubernetes {
	return &GoKubernetes{
		Provider{
			ComposeService:      ComposeService{},
			name:                "gok8s",
			title:               "Kubernetes (IM2)",
			canRegisterProducts: true,
			addons: make(map[string]string),
		},
	}
}

func (k *GoKubernetes) build(cb ComposeBuilder) {
	dataDirectory, err := os.Open(GetDataDirectory())
	HardCheck(err)
	k8Provider := LocalFile{File: dataDirectory}.Child("im2k8")
	k3sDir := k8Provider.Child("k3s")
	k3sOutput := k3sDir.Child("output")

	k3sData := "im2k3sdata"
	cb.volumes[k3sData] = k3sData
	k3sCni := "im2k3scni"
	cb.volumes[k3sCni] = k3sCni
	k3sKubelet := "im2k3skubelet"
	cb.volumes[k3sKubelet] = k3sKubelet
	k3sEtc := "im2k3setcd"
	cb.volumes[k3sEtc] = k3sEtc

	imDir := k8Provider.Child("im")
	imData := imDir.Child("data")
	imStorage := imDir.Child("storage")
	imStorage.Child("home")
	imStorage.Child("projects")
	imStorage.Child("collections")
	imStorage.Child("trash")

	cb.service(
		"im2k3",
		"K8 Provider: K3s Node",
		Json{
			//language=json
			`
			{
				"image": "rancher/k3s:v1.21.6-rc2-k3s1",
				"privileged": true,
				"tmpfs": ["/run", "/var/run"],
				"environment": [
					"K3S_KUBECONFIG_OUTPUT=/output/kubeconfig.yaml",
					"K3S_KUBECONFIG_MODE=666"
				],
				"command": ["server"],
				"hostname": "im2k3",
				"restart": "always",
				"volumes": [
					"` + k3sOutput.GetAbsolutePath() + `:/output",
					"` + k3sData + `:/var/lib/rancher/k3s",
					"` + k3sCni + `:/var/lib/cni",
					"` + k3sKubelet + `:/var/lib/kubelet",
					"` + k3sEtc + `:/etc/rancher",
					"` + imStorage.GetAbsolutePath() + `:/mnt/storage"
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

	cb.service(
		"gok8s",
		"K8 Provider: Integration module",
		Json{
			//language=json
			`
			{
				"image": "$imDevImage",
				"command": ["sleep", "inf"],
				"hostname": "gok8s",
				"ports": ["${portAllocator.allocate(51240)}:51233"],
				"volumes": [
					"` + imData.GetAbsolutePath() + `:/etc/ucloud",
					"` + k3sOutput.GetAbsolutePath() + `:/mnt/k3s",
					"` + imStorage.GetAbsolutePath() + `:/mnt/storage",
					"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/im2:/opt/ucloud",
					"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/gonja:/opt/gonja"
				]
			}
			`,
		},
	true,
	true,
	true,
	"",
	"",
	)

	postgresDataDir := cb.environment.repoRoot.Child("go-slurm-pg-data")
	cb.service(
		"go-k8s-postgres",
		"Kubernetes (IM2): Postgres",
		Json{
			//language=json
			`
				{
					"image": "postgres:15.0",
					"hostname": "go-k8s-postgres",
					"restart": "always",
					"environment":{
						"POSTGRES_PASSWORD": "postgrespassword"
					},
					"volumes": [
						"` + postgresDataDir.GetAbsolutePath() + `:/var/lib/postgresql/data",
						"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/im2:/opt/ucloud",
						"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/gonja:/opt/gonja"
					],
					"ports": [
						"` + strconv.Itoa(portAllocator.Allocate(51241)) + `:5432"
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

func (k *GoKubernetes) install() {
	k8Provider := currentEnvironment.Child("im2k8")
	imDir := k8Provider.Child("im")
	imData := imDir.Child("data")

	installMarker := imData.Child(".install-marker")
	if installMarker.Exists() { return }

	compose.Exec(
		currentEnvironment,
		"gok8s",
		[]string{
			"sh", "-c", `
				while ! test -e "/mnt/k3s/kubeconfig.yaml"; do
				sleep 1
				echo "Waiting for Kubernetes to be ready..."
				done
				`,
		},
		false,
	).streamOutput().executeToText()

	compose.Exec(
		currentEnvironment,
		"gok8s",
		[]string{
			"sed",
			"-i",
			"s/127.0.0.1/im2k3/g",
			"/mnt/k3s/kubeconfig.yaml",
		},
		false,
	).streamOutput().executeText()

	compose.Exec(
		currentEnvironment,
		"gok8s",
		[]string{
			"kubectl",
			"--kubeconfig",
			"/mnt/k3s/kubeconfig.yaml",
			"create",
			"namespace",
			"ucloud-apps",
		},
		false,
	).streamOutput().executeText()

	compose.Exec(
		currentEnvironment,
		"gok8s",
		[]string {
			"sh",
			"-c",
			`
				cat > /tmp/pvc.yml << EOF
				---
				apiVersion: v1
				kind: PersistentVolume
				metadata:
					name: cephfs
					namespace: ucloud-apps
				spec:
					capacity:
						storage: 1000Gi
					volumeMode: Filesystem
					accessModes:
						- ReadWriteMany
					persistentVolumeReclaimPolicy: Retain
					storageClassName: ""
					hostPath:
						path: "/mnt/storage"
		
				---
				apiVersion: v1
				kind: PersistentVolumeClaim
				metadata:
					name: cephfs
					namespace: ucloud-apps
				spec:
					accessModes:
						- ReadWriteMany
					storageClassName: ""
					volumeName: cephfs
					resources:
						requests:
							storage: 1000Gi
				EOF
			`,
		},
	false,
	).streamOutput().executeText()

	compose.Exec(
		currentEnvironment,
		"gok8s",
		[]string {
			"kubectl",
			"--kubeconfig",
			"/mnt/k3s/kubeconfig.yaml",
			"create",
			"-f",
			"/tmp/pvc.yml",
		},
		false,
	).streamOutput().executeText()

	compose.Exec(
		currentEnvironment,
		"gok8s",
		[]string { "rm", "/tmp/pvc.yml" },
		false,
	).streamOutput().executeText()

	installMarker.WriteText("done")
}

type Slurm struct {
	Provider
	// NOTE(Dan): Please keep this number relatively stable. This will break existing installations if it moves
	// around too much.
	numberOfSlurmNodes int
}

func newSlurmProvider(numberOfSlurmNodes int) *Slurm {
	return &Slurm{
		Provider{
			ComposeService:      ComposeService{},
			name:                "slurm",
			title:               "slurm",
			canRegisterProducts: true,
			addons: make(map[string]string),
		},
		numberOfSlurmNodes,
	}
}

func (s Slurm) build(cb ComposeBuilder)  {
	dataDirectory, err := os.Open(GetDataDirectory())
	HardCheck(err)
	slurmProvider := LocalFile{File: dataDirectory}.Child("slurm")

	imDir := slurmProvider.Child("im")
	imGralde := imDir.Child("gradle")
	imData := imDir.Child("data")
	imLogs := LocalFile{File: dataDirectory}.Child("logs")

	passwdDir := imDir.Child("passwd")
	passwdFile := passwdDir.Child("passwd")
	groupFile := passwdDir.Child("group")
	shadowFile := passwdDir.Child("shadow")
	if (passwdFile.Exists()) {
		passwdFile.WriteText("")
		groupFile.WriteText("")
		shadowFile.WriteText("")
	}

	info := slurmBuild(cb, s, imDir)
	imHome := info.imHome
	imWork := info.imWork
	etcSlurm := info.etcSlurm

	cb.service(
		)


}