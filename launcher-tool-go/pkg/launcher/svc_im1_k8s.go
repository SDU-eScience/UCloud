package launcher

import (
	_ "embed"
	"os"
	"strconv"
)

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

//go:embed config/im1k8s/plugins.yaml
var KubernetesPlugins []byte

//go:embed config/im1k8s/products.yaml
var KubernetesProducts []byte

//go:embed config/im1k8s/core.yaml
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
