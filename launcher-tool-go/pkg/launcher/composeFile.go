package launcher

import (
	"context"
	_ "embed"
	"encoding/base64"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"slices"
	"strconv"
	"strings"
	"sync"
	"time"
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
	imGradle := imDir.Child("gradle")
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
		"slurm",
		"Slurm Provider: Integration module",
		Json{
			//language=json
			`
			{
				"image": "$imDevImage",
				"command": ["sleep", "inf"],
				"hostname": "slurm",
				"volumes": [
					"` + imGradle.GetAbsolutePath() + `:/root/.gradle",
					"` + imData.GetAbsolutePath() + `:/etc/ucloud",
					"` + imLogs.GetAbsolutePath() + `:/var/log/ucloud",
					"` + imHome.GetAbsolutePath() + `:/home",
					"` + imWork.GetAbsolutePath() + `:/work",
					"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/integration-module:/opt/ucloud",
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

	cb.service(
		"slurmpgweb",
		"Slurm Provider: Postgres UI",
		Json{
			//language=json
			`
				{
					"image": "sosedoff/pgweb",
					"hostname": "slurmpgweb",
					"restart": "always",
					"environment":{
						"DATABASE_URL": "postgres://postgres:postgrespassword@slurm:5432/postgres?sslmode=disable"
					}
				}
			`,
		},
		true,
		true,
		false,
		"https://slurm-pg.localhost.direct",
		"",
	)
}

func (s Slurm) install(credentials ProviderCredentials) {
	slurmProvider := currentEnvironment.Child("slurm")
	imDir := slurmProvider.Child("im")
	imData := imDir.Child("data")

	installMarker := imData.Child(".install-marker")
	if installMarker.Exists() { return }

	imData.Child("core.yaml").WriteText(
		//language=yaml
		`
			providerId: slurm
			launchRealUserInstances: true
			allowRootMode: false
			developmentMode: true
			disableInsecureFileCheckIUnderstandThatThisIsABadIdeaButSomeDevEnvironmentsAreBuggy: true
			hosts:
				ucloud:
					host: backend
					scheme: http
					port: 8080
				self:
					host: slurm.localhost.direct
					scheme: https
					port: 443
			cors:
				allowHosts: ["ucloud.localhost.direct"]
		`,
	)

	imData.Child("server.yaml").WriteText(
		//language=yaml
		`
			refreshToken: `+ credentials.refreshToken + `
			envoy:
				executable: /usr/bin/envoy
				funceWrapper: false
				directory: /var/run/ucloud/envoy
			database:
				type: Embedded
				host: 0.0.0.0
				directory: /etc/ucloud/pgsql
				password: postgrespassword
		`,
	)

	imData.Child("ucloud_crt.pem").WriteText(credentials.publicKey)

	imData.Child("products.yaml").WriteText(
		//language=yaml
		`
			compute:
				cpu:
					allowSubAllocations: false
					cost:
						type: Resource
						interval: Minutely
						unit: Cpu
					template:
						cpu: [1, 2, 200]
						memory: 1
						description: An example CPU machine with 1 vCPU.
				storage:
					storage:
						allowSubAllocations: false
						cost:
							type: Resource
							unit: GB
						storage:
							description: An example storage system
		`,
	)

	imData.Child("plugins.yaml").WriteText(
		//language=yaml
		`
		connection:
			type: UCloud
			redirectTo: https://ucloud.localhost.direct
			insecureMessageSigningForDevelopmentPurposesOnly: true
			extensions:
				onConnectionComplete: /etc/ucloud/extensions/ucloud-connection
		
		allocations:
			type: Extension
			extensions:
				onWalletUpdated: /etc/ucloud/extensions/on-wallet-updated
		
		jobs:
			default:
				type: Slurm
				matches: "*"
				partition: normal
				useFakeMemoryAllocations: true
				accountMapper:
					type: Extension
					extension: /etc/ucloud/extensions/slurm-account-extension
				terminal:
					type: SSH
					generateSshKeys: true
				web:
					type: Simple
					domainPrefix: slurm-
					domainSuffix: .localhost.direct
				extensions:
					fetchComputeUsage: /etc/ucloud/extensions/fetch-compute-usage
			
			fileCollections:
				default:
					type: Posix
					matches: "*"
					accounting: /etc/ucloud/extensions/storage-du-accounting
					extensions:
						driveLocator: /etc/ucloud/extensions/drive-locator
				
			files:
				default:
					type: Posix
					matches: "*"
			
			projects:
				type: Simple
				unixGroupNamespace: 42000
				extensions:
					all: /etc/ucloud/extensions/project-extension
		`,
	)

	slurmInstall("slurm")
	installMarker.WriteText("done")

}

type GoSlurm struct {
	Provider
	canRegisterProducts bool
}

func NewGoSlurm(canRegisterProducts bool) *GoSlurm  {
	return &GoSlurm{
		Provider{
			ComposeService:      ComposeService{},
			name:                "go-slurm",
			title:               "Slurm (Go test)",
			canRegisterProducts: true,
			addons: make(map[string]string),
		},
		canRegisterProducts,
	}
}

func (gs GoSlurm) build(cb ComposeBuilder) {
	dataDirectory, err := os.Open(GetDataDirectory())
	HardCheck(err)
	provider := LocalFile{File: dataDirectory}.Child("go-slurm")

	imDir := provider.Child("im")
	imGradle := imDir.Child("gradle")
	imData := imDir.Child("data")
	imLogs := LocalFile{File: dataDirectory}.Child("logs")

	passwdDir := imDir.Child("passwd")
	passwdFile := passwdDir.Child("passwd")
	groupFile := passwdFile.Child("group")
	shadowFile := passwdFile.Child("shadow")
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

	info := slurmBuild(cb, gs, imDir)
	imHome := info.imHome
	imWork := info.imWork
	etcSlurm := info.etcSlurm

	cb.service(
		"go-slurm",
		"Slurm (Go test)",
		Json{
			//language=json
			`
			{
				"image": "$imDevImage",
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
					"` + cb.environment.repoRoot.GetAbsolutePath()+ `/provider-integration/integration-module/example-extensions/simple:/etc/ucloud/extensions",
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

	postgresDataDir := LocalFile{ File: dataDirectory }.Child("go-slurm-pg-data")

	cb.service(
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
					"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/gonja:/opt/gonja"
				],
				"ports": [
					"${portAllocator.allocate(51239)}:5432"
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

func (gs GoSlurm) install(credentials ProviderCredentials)  {
	dataDirectory, err := os.Open(GetDataDirectory())
	HardCheck(err)
	slurmProvider := LocalFile{File: dataDirectory}.Child("go-slurm")
	imDir := slurmProvider.Child("im")
	imDataDir := imDir.Child("data")

	installMarker := imDataDir.Child(".install-marker")
	if installMarker.Exists() { return }

	imDataDir.Child("server.yaml").WriteText(
		//language=yaml
		`
			refreshToken: ` + credentials.refreshToken + ` 
		`,
	)

	imDataDir.Child("ucloud_crt.pem").WriteText(credentials.publicKey)

	slurmInstall("go-slurm")

	installMarker.WriteText("done")
}

const FREE_IPA_ADDON = "free-ipa"


func (gs GoSlurm) Addons() {
	gs.addons[FREE_IPA_ADDON] = FREE_IPA_ADDON
}

func (gs GoSlurm) buildAddon(cb ComposeBuilder, addon string) {
	switch addon {
		case FREE_IPA_ADDON: {
			freeIpa := "freeipaDataDir"
			cb.volumes[freeIpa] = freeIpa

			cb.service(
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
							"`+ freeIpa + `:/data:Z",
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

func (gs GoSlurm) installAddon(addon string)  {
	switch addon {
		case FREE_IPA_ADDON: {
			compose.Exec(
				currentEnvironment,
				"go-slurm",
				[]string{
					"sh", "-c", `
					while ! curl --silent -f http: //ipa.ucloud/ipa/config/ca.crt > /dev/null; do
					sleep 1
					date
					echo "Waiting for FreeIPA to be ready - Test #1 (expected to take up to 15 minutes)..."
					done
					`,
				},
				false,
			).streamOutput().executeToText()

			compose.Exec(
				currentEnvironment,
				"free_ipa",
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
			).streamOutput().executeToText()
		}
	}
}

func enrollClient( client string) {
	fmt.Println("Enrolling " + client + " in FreeIPA...")

	// NOTE(Dan): This will "fail" with a bunch of errors and warnings because of systemd.
	// It will, however, actually do all it needs to do. As a result, we supress the output
	// and always exit 0. sssd will fail if freeipa is not ready.
	compose.Exec(
		currentEnvironment,
		client,
		[]string{
			"sh", "-c", `
			ipa-client-install --domain ipa.ucloud --server ipa.ucloud --no-ntp \
			--no-dns-sshfp --principal=admin --password=adminadmin --force-join --unattended || true;
			`
		},
		false,
	).executeToText()

	// NOTE(Dan): This one is a bit flakey. Try a few times, this usually works.
	compose.Exec(
		currentEnvironment,
		client,
		[]string{
			"sh",
			"-c",
			"sssd || (sleep 1; sssd) || (sleep 1; sssd) || (sleep 1; sssd) || (sleep 1; sssd) " +
				"|| (sleep 1; sssd) || (sleep 1; sssd) || (sleep 1; sssd) || " +
				"(sleep 1; sssd) || (sleep 1; sssd) || (sleep 1; sssd) || (sleep 1; sssd)"
		},
		true,
	).streamOutput().executeToText()
}

func (gs GoSlurm) startAddon(addon string) {
	switch addon {
		case FREE_IPA_ADDON: {
			clientsToEnroll := []string{
				"go-slurm",
				"c1",
				"c2",
				"slurmctld",
				"slurmdbd",
			}
			wg := sync.WaitGroup{}
			wg.Add(len(clientsToEnroll))
			for _, client := range clientsToEnroll {
				go enrollClient(client)
			}
			wg.Wait()
		}
	}
}

type GateWay struct {
	ComposeService
}

//go:embed tls.crt
var crt []byte

//go:embed tls.key
var key []byte

func (gw GateWay) build(cb ComposeBuilder) {
	dataDirectory, err := os.Open(GetDataDirectory())
	HardCheck(err)
	gatewayDir := LocalFile{File: dataDirectory}.Child("gateway")
	gatewayData := gatewayDir.Child("data")
	certificates := gatewayDir.Child("certs")

	var decodeCrt = make([]byte, len(crt)+1000)
	_, err = base64.StdEncoding.Decode(decodeCrt, crt)
	HardCheck(err)

	crtString := string(decodeCrt)
	crtWithoutReturn := strings.Replace(crtString, "\r", "", -1)
	formattedCrt := strings.Replace(crtWithoutReturn, "\n", "", -1)

	var decodeKey = make([]byte, len(key)+1000)
	_, err = base64.StdEncoding.Decode(decodeKey, key)
	HardCheck(err)

	keyString := string(decodeKey)
	keyWithoutReturn := strings.Replace(keyString, "\r", "", -1)
	formattedKey := strings.Replace(keyWithoutReturn, "\n", "", -1)

	certificates.Child("tls.crt").WriteBytes([]byte(formattedCrt))
	certificates.Child("tls.key").WriteBytes([]byte(formattedKey))

	gatewayConfig := gatewayDir.Child("Caddyfile")
	gatewayConfig.WriteText(
		`
			{
				order grpc_web before reverse_proxy
			}
			
			https://ucloud.localhost.direct {
				grpc_web
				reverse_proxy /api/auth-callback-csrf frontend:9000
				reverse_proxy /api/auth-callback frontend:9000
				reverse_proxy /api/sync-callback frontend:9000
				reverse_proxy /assets frontend:9000
				reverse_proxy /favicon.ico frontend:9000
				reverse_proxy /favicon.svg frontend:9000
				reverse_proxy /AppVersion.txt frontend:9000
				reverse_proxy /Images/* frontend:9000
				reverse_proxy /app frontend:9000
				reverse_proxy /app/* frontend:9000
				reverse_proxy /@* frontend:9000
				reverse_proxy /node_modules/* frontend:9000
				reverse_proxy /site.config.json frontend:9000
				reverse_proxy /api/* backend:8080
				reverse_proxy /auth/* backend:8080
				reverse_proxy / frontend:9000
				reverse_proxy /avatar.AvatarService/* h2c://backend:11412

				header {
					Cross-Origin-Opener-Policy "same-origin"
					Cross-Origin-Embedder-Policy "require-corp"
				}
			}

			https://postgres.localhost.direct {
				reverse_proxy pgweb:8081
			}

			https://k8.localhost.direct {
				reverse_proxy k8:8889
			}

			https://k8-pg.localhost.direct {
				reverse_proxy k8pgweb:8081
			}

			https://slurm.localhost.direct {
				reverse_proxy slurm:8889
			}

			https://go-slurm.localhost.direct {
				reverse_proxy go-slurm:8889
			}

			https://go-k8s.localhost.direct {
				reverse_proxy gok8s:8889
			}

			https://slurm-pg.localhost.direct {
				reverse_proxy slurmpgweb:8081
			}

			https://ipa.localhost.direct {
				handle / {
					redir https://ipa.localhost.direct/ipa/ui/
				}

				handle {
					reverse_proxy https://free-ipa {
						header_up Host ipa.ucloud
						header_up Referer "https://ipa.ucloud{uri}"

						transport http {
							tls
							tls_insecure_skip_verify
						}
					}
				}
			}

			*.localhost.direct {
				tls /certs/tls.crt /certs/tls.key

				@k8apps {
					header_regexp k8app Host ^k8-.*
				}
				reverse_proxy @k8apps k8:8889

				@slurmapps {
					header_regexp slurmapp Host ^slurm-.*
				}
				reverse_proxy @slurmapps slurm:8889

				@goslurmapps {
					header_regexp goslurmapp Host ^goslurm-.*
				}
				reverse_proxy @goslurmapps go-slurm:8889

				@gok8sapps {
					header_regexp gok8sapp Host ^gok8s-.*
				}
				reverse_proxy @gok8sapps gok8s:8889
			}
		`,
	)

	cb.service(
		"gateway",
		"Gateway",
		Json{
			// NOTE: The gateway is from this repo with no changes:
			// https://github.com/mholt/caddy-grpc-web
			// language=json
			`
				{
					"image": "dreg.cloud.sdu.dk/ucloud/caddy-gateway:1",
					"restart": "always",
					"volumes": [
						"` + gatewayData.GetAbsolutePath() + `:/data",
						"` + gatewayConfig.GetAbsolutePath() + `:/etc/caddy/Caddyfile",
						"` + certificates.GetAbsolutePath() + `:/certs"
					],
					"ports": [
						"` + strconv.Itoa(portAllocator.Allocate(80)) + `:80",
						"` + strconv.Itoa(portAllocator.Allocate(443)) + `:443"
					],
					"hostname": "gateway"
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

type SlurmInfo struct {
	imHome LFile
	imWork LFile
	etcSlurm string
}

func (s Slurm) slurmBuild(cb ComposeBuilder, service ComposeService, imDir LFile) SlurmInfo {
	imHome := imDir.Child("home")
	imWork := imDir.Child("work")
	imMySQLDb := "immysql"
	cb.volumes[imMySQLDb] = imMySQLDb
	etcMunge := "etc_munge"
	cb.volumes[etcMunge] = etcMunge
	etcSlurm := "etc_slurm"
	cb.volumes[etcSlurm] = etcSlurm
	logSlurm := "log_slurm"
	cb.volumes[logSlurm] = logSlurm

	passwdDir := imDir.Child("passwd")
	passwdFile := passwdDir.Child("passwd")
	groupFile := passwdDir.Child("group")
	shadowFile := passwdDir.Child("shadow")

	if !passwdFile.Exists() {
		passwdFile.WriteText("")
		groupFile.WriteText("")
		shadowFile.WriteText("")
	}

	cb.service(
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

	cb.service(
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

	cb.service(
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

	for id := range s.numberOfSlurmNodes {
		cb.service(
			"c" + strconv.Itoa(id),
			"Slurm Provider: Compute node $id",
			Json{
				//language=json
				`
				{
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
	return SlurmInfo{ imHome: imHome, imWork: imWork, etcSlurm: etcSlurm}
}

func slurmInstall(providerContainer string)  {
	for range 30 {
		success := compose.Exec(
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
		).allowFailure().streamOutput().executeToText() != ""

		if success { break }
		time.Sleep(2 * time.Second)
	}

	// These are mounted into the container, but permissions are wrong
	compose.Exec(
		currentEnvironment,
		providerContainer,
		[]string{
			"sh",
			"-c",
			"chmod 0755 -R /etc/ucloud/extensions",
		},
		false,
	).streamOutput().executeToText()

	// This is to avoid rebuilding the image when the Slurm configuration changes
	compose.Exec(
		currentEnvironment,
		"slurmctld",
		[]string{
			"cp",
			"-v",
			"/opt/ucloud/docker/slurm/slurm.conf",
			"/etc/slurm",
		},
		false,
	).streamOutput().executeToText()

	compose.Exec(
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
	).streamOutput().executeToText()

	// Restart slurmctld in case configuration file has changed
	compose.Stop(currentEnvironment, "slurmctld").streamOutput().executeToText()
	compose.Start(currentEnvironment, "slurmctld").streamOutput().executeToText()
}