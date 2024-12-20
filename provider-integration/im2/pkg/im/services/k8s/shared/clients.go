package shared

import (
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/rest"
	"k8s.io/client-go/tools/clientcmd"
	kvclient "kubevirt.io/client-go/kubecli"
	"os"
	"ucloud.dk/pkg/log"
)

var K8sClient *kubernetes.Clientset
var K8sConfig *rest.Config
var KubevirtClient kvclient.KubevirtClient

func initClients() {
	composeFile := "/mnt/k3s/kubeconfig.yaml"
	_, err := os.Stat(composeFile)

	var k8sClient *kubernetes.Clientset = nil
	var k8sConfig *rest.Config = nil

	if err == nil {
		k8sConfig, err = clientcmd.BuildConfigFromFlags("", composeFile)
		if err == nil {
			c, err := kubernetes.NewForConfig(k8sConfig)
			if err == nil {
				k8sClient = c
			}
		}
	}

	if k8sClient == nil {
		k8sConfig, err = rest.InClusterConfig()
		if err == nil {
			c, err := kubernetes.NewForConfig(k8sConfig)
			if err == nil {
				k8sClient = c
			}
		}
	}

	if k8sClient == nil || k8sConfig == nil {
		log.Error("Could not connect to Kubernetes through any of the known configuration methods")
		os.Exit(1)
		return
	}

	kubevirt, err := kvclient.GetKubevirtClientFromRESTConfig(k8sConfig)
	if err == nil {
		KubevirtClient = kubevirt
	}

	K8sClient = k8sClient
	K8sConfig = k8sConfig
}
