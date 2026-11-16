package recovery

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"time"

	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/rest"
	"k8s.io/client-go/tools/clientcmd"

	"ucloud.dk/iapp/k8s/pkg/shared"
)

func recoveryApiConfig(mountPath string, cluster shared.ClusterRecord) (*rest.Config, error) {
	kubeconfigPath := filepath.Join(mountPath, "management", "kubeconfig-internal")
	data, err := os.ReadFile(kubeconfigPath)
	if err != nil {
		return nil, fmt.Errorf("the kubeconfig could not be read (%s)", err)
	}

	config, err := clientcmd.RESTConfigFromKubeConfig(data)
	if err != nil {
		return nil, fmt.Errorf("the kubeconfig is invalid (%s)", err)
	}

	if cluster.ServiceDnsName != "" {
		config.Host = fmt.Sprintf("https://%s:%d", cluster.ServiceDnsName, shared.ApiPort)
	}
	config.Timeout = 15 * time.Second
	return config, nil
}

func recoveryApiProbe(mountPath string, cluster shared.ClusterRecord) error {
	config, err := recoveryApiConfig(mountPath, cluster)
	if err != nil {
		return err
	}
	config.Timeout = 10 * time.Second

	client, err := kubernetes.NewForConfig(config)
	if err != nil {
		return err
	}

	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	if _, err := client.CoreV1().RESTClient().Get().AbsPath("/readyz").DoRaw(ctx); err != nil {
		return err
	}
	return nil
}

func recoveryApiChecks(mountPath string, cluster shared.ClusterRecord, record shared.ClusterRecoveryRecord) []recoveryCheckResult {
	results := []recoveryCheckResult{}

	config, err := recoveryApiConfig(mountPath, cluster)
	if err != nil {
		return append(results, recoveryCheckResult{
			Name:   "Cluster API",
			Status: "Unavailable",
			Detail: "Could not check the cluster configuration. Check the control-plane logs or contact your service provider.",
		})
	}

	client, err := kubernetes.NewForConfig(config)
	if err != nil {
		return append(results, recoveryCheckResult{
			Name:   "Cluster API",
			Status: "Unavailable",
			Detail: "Could not check the cluster configuration. Check the control-plane logs or contact your service provider.",
		})
	}

	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()

	_, readyErr := client.CoreV1().RESTClient().Get().AbsPath("/readyz").DoRaw(ctx)
	if readyErr != nil {
		results = append(results, recoveryCheckResult{
			Name: "Cluster API", Status: "Not ready", Detail: "Check the recovery logs and try again.",
		})
	} else {
		results = append(results, recoveryCheckResult{Name: "Cluster API", Status: "Ready", Ok: true})
	}

	nodes, listErr := client.CoreV1().Nodes().List(ctx, metav1.ListOptions{})
	if listErr != nil {
		results = append(results, recoveryCheckResult{
			Name: "Cluster nodes", Status: "Unavailable", Detail: "Could not check node readiness. Try again when the cluster responds.",
		})
		return results
	}

	recorded := map[string]bool{}
	for _, node := range controlPlaneNodes(cluster) {
		recorded[node.Hostname] = true
	}
	seen := map[string]bool{}
	for _, node := range nodes.Items {
		ready := false
		for _, condition := range node.Status.Conditions {
			if condition.Type == corev1.NodeReady && condition.Status == corev1.ConditionTrue {
				ready = true
			}
		}
		role := "Worker node"
		if recorded[node.Name] {
			role = "Control-plane node"
			seen[node.Name] = true
		}
		status := "Not ready"
		if ready {
			status = "Ready"
		}
		result := recoveryCheckResult{
			Name: node.Name, Status: status, Detail: role, Ok: ready,
		}
		if recorded[node.Name] && record.BackupRelease != "" && node.Status.NodeInfo.KubeletVersion != record.BackupRelease {
			result.Status = "Version mismatch"
			result.Detail = fmt.Sprintf("Kubernetes version %s differs from backup version %s.", node.Status.NodeInfo.KubeletVersion, record.BackupRelease)
			result.Ok = false
		}
		results = append(results, result)
	}
	for _, node := range controlPlaneNodes(cluster) {
		if !seen[node.Hostname] {
			results = append(results, recoveryCheckResult{
				Name: node.Hostname, Status: "Not connected", Detail: "This control-plane node has not joined the cluster yet.",
			})
		}
	}

	return results
}
