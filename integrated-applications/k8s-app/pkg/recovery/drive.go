package recovery

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"

	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/rest"
	"k8s.io/client-go/tools/clientcmd"

	"ucloud.dk/shared/pkg/ucx"

	"ucloud.dk/iapp/k8s/pkg/backup"
	"ucloud.dk/iapp/k8s/pkg/shared"
)

func recoveryClient(session *ucx.Session, stackId string) (shared.ClusterStateClient, error) {
	client, err := shared.ClusterStateClientNewSession(session, stackId)
	if err != nil {
		return shared.ClusterStateClient{}, err
	}
	return client, nil
}

func recoveryReadCluster(client shared.ClusterStateClient) (shared.ClusterRecord, error) {
	snapshot, err := shared.ClusterStateRead(client)
	if err != nil {
		return shared.ClusterRecord{}, fmt.Errorf("could not read the cluster record: %s", err)
	}
	if !snapshot.Found {
		return shared.ClusterRecord{}, errors.New("the cluster record does not exist")
	}

	record := snapshot.Record
	shared.ClusterStateRecordRevisionSet(&record, snapshot.Revision)
	return record, nil
}

func recoveryReadRecovery(client shared.ClusterStateClient) (shared.ClusterRecoveryRecord, int64, bool, error) {
	snapshot, err := shared.ClusterRecoveryRead(client)
	if err != nil {
		return shared.ClusterRecoveryRecord{}, 0, false, fmt.Errorf("could not read the recovery state: %s", err)
	}
	return snapshot.Record, snapshot.Revision, snapshot.Found, nil
}

func recoveryListBackups(mountPath string) ([]backupEntry, error) {
	dir := filepath.Join(mountPath, "backups")
	entries, err := os.ReadDir(dir)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, err
	}

	result := []backupEntry{}
	for _, entry := range entries {
		if !entry.IsDir() {
			continue
		}

		id := entry.Name()
		createdAt, ok := backup.ParseId(id)
		if !ok {
			continue
		}

		item := backupEntry{Id: id, CreatedAt: createdAt}
		metadata, metadataErr := recoveryReadMetadata(filepath.Join(dir, id))
		if metadataErr == nil {
			item.Release = metadata.Release
			item.SizeBytes = metadata.Snapshot.SizeBytes
			item.Sha256 = metadata.Snapshot.Sha256
			item.CreatedByNode = metadata.CreatedByNode
			item.ServerTokenHash = metadata.Credentials.ServerTokenSha256
			item.AgentTokenHash = metadata.Credentials.AgentTokenSha256
		}
		result = append(result, item)
	}

	sort.Slice(result, func(i, j int) bool {
		return result[i].Id > result[j].Id
	})
	return result, nil
}

func recoveryReadMetadata(dir string) (backup.Metadata, error) {
	data, err := os.ReadFile(filepath.Join(dir, "metadata.json"))
	if err != nil {
		return backup.Metadata{}, err
	}

	var metadata backup.Metadata
	if err := json.Unmarshal(data, &metadata); err != nil {
		return backup.Metadata{}, err
	}
	if metadata.SchemaRevision != backup.SchemaRevision {
		return backup.Metadata{}, errors.New("the backup metadata has an incompatible schema revision")
	}
	return metadata, nil
}

func recoveryReadGeneration(mountPath string, allocationId int) string {
	path := filepath.Join(mountPath, "nodes", strconv.Itoa(allocationId), "input", "node.json")
	data, err := os.ReadFile(path)
	if err != nil {
		return ""
	}

	var parsed struct {
		Generation string `json:"generation"`
	}
	if err := json.Unmarshal(data, &parsed); err != nil {
		return ""
	}
	return strings.TrimSpace(parsed.Generation)
}

const recoveryDecommissionedMarker = "decommissioned"

func recoveryWriteMarker(mountPath string, allocationId int, generation string) error {
	if strings.TrimSpace(generation) == "" {
		generation = "decommissioned"
	}

	dir := filepath.Join(mountPath, "nodes", strconv.Itoa(allocationId), "input")
	if err := os.MkdirAll(dir, 0755); err != nil {
		return err
	}

	return os.WriteFile(filepath.Join(dir, recoveryDecommissionedMarker), []byte(generation), 0600)
}

func recoveryMarkerExists(mountPath string, allocationId int) bool {
	path := filepath.Join(mountPath, "nodes", strconv.Itoa(allocationId), "input", recoveryDecommissionedMarker)
	_, err := os.Stat(path)
	return err == nil
}

func recoveryCanChangeBackup(mountPath string, cluster shared.ClusterRecord) bool {
	nodes := controlPlaneNodes(cluster)
	if len(nodes) == 0 {
		return false
	}

	for _, node := range nodes {
		if !recoveryMarkerExists(mountPath, node.AllocationId) {
			return false
		}
	}
	return true
}

func recoveryReadText(path string) (string, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return "", err
	}

	value := strings.TrimSpace(string(data))
	if value == "" {
		return "", errors.New("the file is empty: " + path)
	}
	return value, nil
}

func recoveryTokenHash(mountPath string, name string) (string, error) {
	data, err := os.ReadFile(filepath.Join(mountPath, "management", "tokens", name))
	if err != nil {
		return "", err
	}

	sum := sha256.Sum256(data)
	return hex.EncodeToString(sum[:]), nil
}

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

	if serviceDns := strings.TrimSpace(cluster.ServiceDnsName); serviceDns != "" {
		config.Host = fmt.Sprintf("https://%s:%d", serviceDns, shared.ApiPort)
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
			Name: "Cluster API", Status: "Unavailable", Detail: "Check the cluster configuration.",
		})
	}

	client, err := kubernetes.NewForConfig(config)
	if err != nil {
		return append(results, recoveryCheckResult{
			Name: "Cluster API", Status: "Unavailable", Detail: "Could not connect. Check the cluster configuration.",
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
