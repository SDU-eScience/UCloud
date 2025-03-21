package launcher

import (
	_ "embed"
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

type Direct struct{}

func (d Direct) Allocate(port int) int {
	return port
}

type Remapped struct {
	portAllocator  int
	allocatedPorts map[int]int
}

func (r Remapped) Allocate(port int) int {
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
	path := currentEnvironment.GetAbsolutePath()
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

func (c ComposeBuilder) CreateComposeFile() string {
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

func (c ComposeBuilder) Service(
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
	AllServices[name] = Service{
		containerName:        name,
		title:                title,
		logsSupported:        logsSupported,
		execSupported:        execSupported,
		useServiceConvention: serviceConvention,
		address:              address,
		uiHelp:               uiHelp,
	}
}

func (e Environment) CreateComposeFile(services []ComposeService) LFile {
	disableRemoteFileWriting = !e.doWriteFile
	loadingTitle := ""
	if e.doWriteFile {
		loadingTitle = "Creating compose environment..."
	} else {
		loadingTitle = "Initializing service list..."
	}

	var lfile LFile
	err := termio.LoadingIndicator(
		loadingTitle,
		func(output *os.File) error {
			var err error
			cb := ComposeBuilder{e, map[string]string{}, map[string]Json{}}
			for _, service := range services {
				service.Build(cb)
			}

			allAddons := ListAddons()
			for providerName, allAddon := range allAddons {
				for _, addon := range allAddon {
					provider := ProviderFromName(providerName)
					v, ok := provider.(*GoSlurm)
					if ok {
						v.BuildAddon(cb, addon)
					}

				}
			}

			absPath, _ := filepath.Abs(GetDataDirectory())

			lfile := NewFile(absPath)
			childFile := lfile.Child("docker-compose.yaml", false)
			childFile.WriteText(cb.CreateComposeFile())
			return err
		},
	)
	HardCheck(err)
	disableRemoteFileWriting = false
	return lfile
}

type ComposeService interface {
	Build(cb ComposeBuilder)
}

type Provider interface {
	Name() string
	Title() string
	CanRegisterProducts() bool
	Addons() map[string]string
	Install(credentials ProviderCredentials)
	Build(cb ComposeBuilder)
}

var kubernetes Kubernetes
var slurm Slurm
var goSlurm GoSlurm
var goKubernetes GoKubernetes

var AllProviders []Provider

var AllProviderNames = []string{
	"k8",
	"slurm",
	"go-slurm",
	"gok8s",
}

func GenerateProviders() {
	kubernetes = NewKubernetes()
	AllProviders = append(AllProviders, &kubernetes)
	slurm = NewSlurmProvider(2)
	AllProviders = append(AllProviders, &slurm)
	goSlurm = NewGoSlurm(false)
	AllProviders = append(AllProviders, &goSlurm)
	goKubernetes = NewGoKubernetes()
	AllProviders = append(AllProviders, &goKubernetes)
}

func ProviderFromName(name string) Provider {
	if !slices.Contains(AllProviderNames, name) {
		log.Fatal("Unknown Provider " + name)
	}
	found := slices.IndexFunc(AllProviders, func(provider Provider) bool {
		return provider.Name() == name
	})
	if found == -1 {
		log.Fatal("No such provider: " + name)
	}
	return AllProviders[found]
}

type UCloudBackend struct{}

func (uc *UCloudBackend) Build(cb ComposeBuilder) {
	dataDir := GetDataDirectory()
	logs := NewFile(dataDir).Child("logs", true)
	homeDir := NewFile(dataDir).Child("backend-home", true)
	configDir := NewFile(dataDir).Child("backend-config", true)
	gradleDir := NewFile(dataDir).Child("backend-gradle", true)
	postgresDataDir := NewFile(dataDir).Child("pg-data", true)

	cb.Service(
		"backend",
		"UCloud/Core: Backend",
		Json{
			//language=json
			`
				{
					"image": "` + imDevImage + `",
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

	cb.Service(
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

	cb.Service(
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
					"PGWEB_DATABASE_URL": "postgres://postgres:postgrespassword@postgres:5432/postgres?sslmode=disable"
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

	redisDataDir := NewFile(dataDir).Child("redis-data", true)
	cb.Service(
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

type UCloudFrontend struct{}

func (uf *UCloudFrontend) Build(cb ComposeBuilder) {
	cb.Service(
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
	name                string
	title               string
	canRegisterProducts bool
	addons              map[string]string
}

func NewKubernetes() Kubernetes {
	return Kubernetes{
		name:                "k8",
		title:               "Kubernetes",
		canRegisterProducts: true,
		addons:              map[string]string{},
	}
}

func (k *Kubernetes) Name() string {
	return k.name
}

func (k *Kubernetes) Title() string {
	return k.title
}

func (k *Kubernetes) CanRegisterProducts() bool {
	return k.canRegisterProducts
}

func (k *Kubernetes) Addons() map[string]string {
	return k.addons
}

func (k *Kubernetes) Build(cb ComposeBuilder) {
	dataDir := GetDataDirectory()
	k8Provider := NewFile(dataDir).Child("k8", true)
	k3sDir := k8Provider.Child("k3s", true)
	k3sOutput := k3sDir.Child("output", true)

	k3sData := "k3sdata"
	cb.volumes[k3sData] = k3sData
	k3sCni := "k3scni"
	cb.volumes[k3sCni] = k3sCni
	k3sKubelet := "k3skubelet"
	cb.volumes[k3sKubelet] = k3sKubelet
	k3sEtc := "k3setc"
	cb.volumes[k3sEtc] = k3sEtc

	imDir := k8Provider.Child("im", true)
	imGradle := imDir.Child("gradle", true)
	imData := imDir.Child("data", true)
	imStorage := imDir.Child("storage", true)
	imStorage.Child("home", true)
	imStorage.Child("projects", true)
	imStorage.Child("collections", true)
	imLogs := NewFile(dataDir).Child("logs", true)

	passwdDir := imDir.Child("passwd", true)
	passwdFile := passwdDir.Child("passwd", false)
	groupFile := passwdDir.Child("group", false)
	shadowFile := passwdDir.Child("shadow", false)
	passFileInfo, _ := os.Open(passwdFile.Name())

	if passFileInfo != nil {
		passwdFile.WriteText(
			`
				ucloud:x:998:998::/home/ucloud:/bin/sh
				ucloudalt:x:11042:11042::/home/ucloudalt:/bin/sh
				`,
		)
		groupFile.WriteText(
			`
				ucloud:x:998:
				ucloudalt:x:11042:
			`,
		)
		shadowFile.WriteText(
			`
				ucloud:!:19110::::::
				ucloudalt:!:19110::::::
			`,
		)
	}

	cb.Service(
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

	cb.Service(
		"k8",
		"K8 Provider: Integration module",
		Json{
			//language=json
			`
			{
				"image": "` + imDevImage + `",
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

	cb.Service(
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
					"PGWEB_DATABASE_URL": "postgres://postgres:postgrespassword@k8:5432/postgres?sslmode=disable"
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

//go:embed config/Kubernetes/plugins.yaml
var KubernetesPlugins []byte

//go:embed config/Kubernetes/products.yaml
var KubernetesProducts []byte

//go:embed config/Kubernetes/core.yaml
var KubernetesCore []byte

func (k *Kubernetes) Install(credentials ProviderCredentials) {
	k8Provider := currentEnvironment.Child("k8", true)
	imDir := k8Provider.Child("im", true)
	imData := imDir.Child("data", true)

	installMarker := imData.Child(".install-marker", false)
	lines := readLines(installMarker.GetAbsolutePath())
	if len(lines) != 0 {
		return
	}

	imData.Child("core.yaml", false).WriteBytes(KubernetesCore)

	imData.Child("server.yaml", false).WriteText(
		//language=yaml
		`refreshToken: ` + credentials.refreshToken + `
envoy:
  executable: /usr/bin/envoy
  funceWrapper: false
  directory: /var/run/ucloud/envoy
database:
  type: Embedded
  directory: /etc/ucloud/pgsql
  host: 0.0.0.0
  password: postgrespassword`)

	imData.Child("ucloud_crt.pem", false).WriteText(credentials.publicKey)

	imData.Child("products.yaml", false).WriteBytes(KubernetesProducts)

	imData.Child("plugins.yaml", false).WriteBytes(KubernetesPlugins)

	var executeCom = compose.Exec(
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
	)
	executeCom.SetStreamOutput()
	executeCom.ExecuteToText()

	executeCom2 := compose.Exec(
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
	)
	executeCom2.SetStreamOutput()
	executeCom2.ExecuteToText()

	executeCom3 := compose.Exec(
		currentEnvironment,
		"k8",
		[]string{
			"sed",
			"-i",
			"s/127.0.0.1/k3/g",
			"/mnt/k3s/kubeconfig.yaml",
		},
		false,
	)
	executeCom3.SetStreamOutput()
	executeCom3.ExecuteToText()

	executeCom4 := compose.Exec(
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
	)
	executeCom4.SetStreamOutput()
	executeCom4.SetAllowFailure()
	executeCom4.ExecuteToText()

	executeCom5 := compose.Exec(
		currentEnvironment,
		"k8",
		[]string{
			"sh",
			"-c",
			`cat > /tmp/pvc.yml << EOF
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
EOF`,
		},
		false,
	)
	executeCom5.SetStreamOutput()
	executeCom5.ExecuteToText()

	executeCom6 := compose.Exec(
		currentEnvironment,
		"k8",
		[]string{"kubectl", "--kubeconfig", "/mnt/k3s/kubeconfig.yaml", "create", "-f", "/tmp/pvc.yml"},
		false,
	)
	executeCom6.SetStreamOutput()
	executeCom6.SetAllowFailure()
	executeCom6.ExecuteToText()

	executeCom7 := compose.Exec(
		currentEnvironment,
		"k8",
		[]string{"rm", "/tmp/pvc.yml"},
		false,
	)
	executeCom7.SetStreamOutput()
	executeCom7.ExecuteToText()

	installMarker.WriteText("done")
}

type GoKubernetes struct {
	name                string
	title               string
	canRegisterProducts bool
	addons              map[string]string
}

func NewGoKubernetes() GoKubernetes {
	return GoKubernetes{
		name:                "gok8s",
		title:               "Kubernetes (IM2)",
		canRegisterProducts: true,
		addons:              make(map[string]string),
	}
}

func (g *GoKubernetes) Name() string {
	return g.name
}

func (g *GoKubernetes) Title() string {
	return g.title
}

func (g *GoKubernetes) CanRegisterProducts() bool {
	return g.canRegisterProducts
}

func (g *GoKubernetes) Addons() map[string]string {
	return g.addons
}

func (k *GoKubernetes) Build(cb ComposeBuilder) {
	dataDir := GetDataDirectory()
	k8Provider := NewFile(dataDir).Child("im2k8", true)
	k3sDir := k8Provider.Child("k3s", true)
	k3sOutput := k3sDir.Child("output", true)

	k3sData := "im2k3sdata"
	cb.volumes[k3sData] = k3sData
	k3sCni := "im2k3scni"
	cb.volumes[k3sCni] = k3sCni
	k3sKubelet := "im2k3skubelet"
	cb.volumes[k3sKubelet] = k3sKubelet
	k3sEtc := "im2k3setc"
	cb.volumes[k3sEtc] = k3sEtc

	imDir := k8Provider.Child("im", true)
	imData := imDir.Child("data", true)
	imStorage := imDir.Child("storage", true)
	imStorage.Child("home", true)
	imStorage.Child("projects", true)
	imStorage.Child("collections", true)
	imStorage.Child("trash", true)

	cb.Service(
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

	cb.Service(
		"gok8s",
		"K8 Provider: Integration module",
		Json{
			//language=json
			`
			{
				"image": "` + imDevImage + `",
				"command": ["sleep", "inf"],
				"hostname": "gok8s",
				"ports": ["` + strconv.Itoa(portAllocator.Allocate(51240)) + `:51233"],
				"volumes": [
					"` + imData.GetAbsolutePath() + `:/etc/ucloud",
					"` + k3sOutput.GetAbsolutePath() + `:/mnt/k3s",
					"` + imStorage.GetAbsolutePath() + `:/mnt/storage",
					"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/im2:/opt/ucloud",
					"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/gonja:/opt/gonja",
					"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/walk:/opt/walk"
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

	postgresDataDir := NewFile(dataDir).Child("go-k8-pg-data", true)
	cb.Service(
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
						"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/gonja:/opt/gonja",
						"` + cb.environment.repoRoot.GetAbsolutePath() + `/provider-integration/walk:/opt/walk"
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

//go:embed config/goKubernetes/config.yaml
var goKubernetesConfig []byte

func (k *GoKubernetes) Install(credentials ProviderCredentials) {
	k8Provider := currentEnvironment.Child("im2k8", true)
	imDir := k8Provider.Child("im", true)
	imData := imDir.Child("data", true)

	installMarker := imData.Child(".install-marker", false)
	if len(readLines(installMarker.GetAbsolutePath())) != 0 {
		return
	}

	imData.Child("ucloud_crt.pem", false).WriteText(credentials.publicKey)

	imData.Child("server.yaml", false).WriteText(
		//language=yaml
		`refreshToken: ` + credentials.refreshToken + `
database:
  embedded: false
  username: postgres
  password: postgrespassword
  database: postgres
  ssl: false
  host:
    address: go-k8s-postgres`,
	)

	imData.Child("config.yaml", false).WriteBytes(goKubernetesConfig)

	var executeCom = compose.Exec(
		currentEnvironment,
		"gok8s",
		[]string{
			"sh", "-c", `
				while ! test -e "/mnt/k3s/kubeconfig.yaml"; do
				sleep 1
				echo "Waiting for Kubernetes to be ready..."
				done`,
		},
		false,
	)
	executeCom.SetStreamOutput()
	executeCom.ExecuteToText()

	executeCom = compose.Exec(
		currentEnvironment,
		"gok8s",
		[]string{
			"sed",
			"-i",
			"s/127.0.0.1/im2k3/g",
			"/mnt/k3s/kubeconfig.yaml",
		},
		false,
	)
	executeCom.SetStreamOutput()
	executeCom.ExecuteToText()

	executeCom = compose.Exec(
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
	)

	executeCom.SetStreamOutput()
	executeCom.SetAllowFailure()
	executeCom.ExecuteToText()

	executeCom = compose.Exec(
		currentEnvironment,
		"gok8s",
		[]string{
			"sh",
			"-c",
			`cat > /tmp/pvc.yml << EOF
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
EOF`,
		},
		false,
	)
	executeCom.SetStreamOutput()
	executeCom.ExecuteToText()

	executeCom = compose.Exec(
		currentEnvironment,
		"gok8s",
		[]string{
			"kubectl",
			"--kubeconfig",
			"/mnt/k3s/kubeconfig.yaml",
			"create",
			"-f",
			"/tmp/pvc.yml",
		},
		false,
	)
	executeCom.SetAllowFailure()
	executeCom.SetStreamOutput()
	executeCom.ExecuteToText()

	executeCom = compose.Exec(
		currentEnvironment,
		"gok8s",
		[]string{"rm", "/tmp/pvc.yml"},
		false,
	)
	executeCom.SetStreamOutput()
	executeCom.ExecuteToText()

	installMarker.WriteText("done")
}

type Slurm struct {
	name                string
	title               string
	canRegisterProducts bool
	addons              map[string]string
	// NOTE(Dan): Please keep this number relatively stable. This will break existing installations if it moves
	// around too much.
	numberOfSlurmNodes int
}

func NewSlurmProvider(numberOfSlurmNodes int) Slurm {
	return Slurm{
		name:                "slurm",
		title:               "slurm",
		canRegisterProducts: true,
		addons:              make(map[string]string),
		numberOfSlurmNodes:  numberOfSlurmNodes,
	}
}

func (s *Slurm) Name() string {
	return s.name
}

func (s *Slurm) Title() string {
	return s.title
}

func (s *Slurm) CanRegisterProducts() bool {
	return s.canRegisterProducts
}

func (s *Slurm) Addons() map[string]string {
	return s.addons
}

func (s *Slurm) Build(cb ComposeBuilder) {
	dataDir := GetDataDirectory()
	slurmProvider := NewFile(dataDir).Child("slurm", true)

	imDir := slurmProvider.Child("im", true)
	imGradle := imDir.Child("gradle", true)
	imData := imDir.Child("data", true)
	imLogs := NewFile(dataDir).Child("logs", true)

	passwdDir := imDir.Child("passwd", true)
	passwdFile := passwdDir.Child("passwd", false)
	groupFile := passwdDir.Child("group", false)
	shadowFile := passwdDir.Child("shadow", false)
	if passwdFile.Exists() {
		passwdFile.WriteText("")
		groupFile.WriteText("")
		shadowFile.WriteText("")
	}

	info := SlurmBuild(cb, s, imDir)
	imHome := info.imHome
	imWork := info.imWork
	etcSlurm := info.etcSlurm

	cb.Service(
		"slurm",
		"Slurm Provider: Integration module",
		Json{
			//language=json
			`
			{
				"image": "` + imDevImage + `",
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

	cb.Service(
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
						"PGWEB_DATABASE_URL": "postgres://postgres:postgrespassword@slurm:5432/postgres?sslmode=disable"
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

//go:embed config/Slurm/core.yaml
var SlurmCore []byte

//go:embed config/Slurm/products.yaml
var SlurmProducts []byte

//go:embed config/Slurm/plugins.yaml
var SlurmPlugins []byte

func (s *Slurm) Install(credentials ProviderCredentials) {
	slurmProvider := currentEnvironment.Child("slurm", true)
	imDir := slurmProvider.Child("im", true)
	imData := imDir.Child("data", true)

	installMarker := imData.Child(".install-marker", false)
	if len(readLines(installMarker.GetAbsolutePath())) != 0 {
		return
	}

	imData.Child("core.yaml", false).WriteBytes(SlurmCore)

	imData.Child("server.yaml", false).WriteText(
		//language=yaml
		`refreshToken: ` + credentials.refreshToken + `
envoy:
  executable: /usr/bin/envoy
  funceWrapper: false
  directory: /var/run/ucloud/envoy
database:
  type: Embedded
  host: 0.0.0.0
  directory: /etc/ucloud/pgsql
  password: postgrespassword`,
	)

	imData.Child("ucloud_crt.pem", false).WriteText(credentials.publicKey)

	imData.Child("products.yaml", false).WriteBytes(SlurmProducts)

	imData.Child("plugins.yaml", false).WriteBytes(SlurmPlugins)

	SlurmInstall("slurm")
	installMarker.WriteText("done")

}

const FREE_IPA_ADDON = "free-ipa"

type GoSlurm struct {
	name                string
	title               string
	canRegisterProducts bool
	addons              map[string]string
}

func NewGoSlurm(canRegisterProducts bool) GoSlurm {
	addons := make(map[string]string)
	addons[FREE_IPA_ADDON] = FREE_IPA_ADDON
	return GoSlurm{
		name:                "go-slurm",
		title:               "Slurm (Go test)",
		canRegisterProducts: canRegisterProducts,
		addons:              addons,
	}
}

func (g *GoSlurm) Name() string {
	return g.name
}

func (g *GoSlurm) Title() string {
	return g.title
}

func (g *GoSlurm) CanRegisterProducts() bool {
	return g.canRegisterProducts
}

func (g *GoSlurm) Addons() map[string]string {
	return g.addons
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

//go:embed config/GoSlurm/config.yaml
var GoSlurmConfig []byte

//go:embed config/GoSlurm/secrets.yaml
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
	case FREE_IPA_ADDON:
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
	case FREE_IPA_ADDON:
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
	fmt.Println("Enrolling " + client + " in FreeIPA...")

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
}

func (gs *GoSlurm) StartAddon(addon string) {
	switch addon {
	case FREE_IPA_ADDON:
		{
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
				go EnrollClient(client)
			}
			wg.Wait()
		}
	}
}

type GateWay struct {
	didAppendInstall bool
}

func (gw *GateWay) Build(cb ComposeBuilder) {
	dataDir := GetDataDirectory()
	gatewayDir := NewFile(dataDir).Child("gateway", true)
	gatewayData := gatewayDir.Child("data", true)
	certificates := gatewayDir.Child("certs", true)

	cert := certificates.Child("tls.crt", false)
	key := certificates.Child("tls.key", false)

	if len(readLines(cert.GetAbsolutePath())) == 0 || len(readLines(key.GetAbsolutePath())) == 0 && !gw.didAppendInstall {
		gw.didAppendInstall = true
		PostExecFile.WriteString("\n " + repoRoot.GetAbsolutePath() + "/launcher-go install-certs\n\n")
	}

	gatewayConfig := gatewayDir.Child("Caddyfile", false)
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

		  	https://go-k8s-metrics.localhost.direct {
				reverse_proxy gok8s:7867
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

	cb.Service(
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
	imHome   LFile
	imWork   LFile
	etcSlurm string
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

	v, ok := service.(*Slurm)
	if ok {
		for id := range v.numberOfSlurmNodes {
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
