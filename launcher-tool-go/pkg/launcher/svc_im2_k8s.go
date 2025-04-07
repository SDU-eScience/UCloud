package launcher

import (
	_ "embed"
	"strconv"
)

type GoKubernetes struct {
	name                string
	title               string
	canRegisterProducts bool
	addons              map[string]string
}

func (g *GoKubernetes) Name() string {
	return "gok8s"
}

func (g *GoKubernetes) Title() string {
	return "IM2/K8s"
}

func (g *GoKubernetes) CanRegisterProducts() bool {
	return false
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

//go:embed config/im2k8s/config.yaml
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
