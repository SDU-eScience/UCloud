package recovery

import (
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

	"ucloud.dk/iapp/k8s/pkg/backup"
	"ucloud.dk/iapp/k8s/pkg/shared"
)

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

func recoveryValidateBackup(mountPath string, id string) error {
	dir := filepath.Join(mountPath, "backups", id)
	metadata, err := recoveryReadMetadata(dir)
	if err != nil {
		return fmt.Errorf("could not read the metadata of the backup %s: %s", id, err)
	}

	if metadata.BackupId != id {
		return fmt.Errorf("the metadata of the backup %s names the backup %s", id, metadata.BackupId)
	}
	if metadata.Release == "" {
		return fmt.Errorf("the backup %s has no Kubernetes version", id)
	}
	if metadata.Snapshot.Sha256 == "" {
		return fmt.Errorf("the backup %s has no snapshot checksum", id)
	}
	if metadata.Snapshot.SizeBytes <= 0 {
		return fmt.Errorf("the backup %s has an invalid snapshot size", id)
	}

	snapshotPath := filepath.Join(dir, metadata.Snapshot.File)
	info, err := os.Stat(snapshotPath)
	if err != nil {
		return fmt.Errorf("could not read the snapshot of the backup %s: %s", id, err)
	}
	if !info.Mode().IsRegular() {
		return fmt.Errorf("the snapshot of the backup %s is not a regular file: %s", id, snapshotPath)
	}
	if info.Size() != metadata.Snapshot.SizeBytes {
		return fmt.Errorf("the snapshot of the backup %s has %d bytes, but the metadata says %d bytes", id, info.Size(), metadata.Snapshot.SizeBytes)
	}

	if err := recoveryValidateBackupToken(dir, "server-token", metadata.Credentials.ServerTokenSha256); err != nil {
		return err
	}
	if err := recoveryValidateBackupToken(dir, "agent-token", metadata.Credentials.AgentTokenSha256); err != nil {
		return err
	}

	return nil
}

func recoveryValidateBackupToken(dir string, name string, expected string) error {
	path := filepath.Join(dir, "credentials", name)
	data, err := os.ReadFile(path)
	if err != nil {
		return fmt.Errorf("could not read the backup file %s: %s", path, err)
	}
	if len(data) == 0 {
		return fmt.Errorf("the backup file %s is empty", path)
	}

	sum := sha256.Sum256(data)
	actual := hex.EncodeToString(sum[:])
	if actual != expected {
		return fmt.Errorf("the backup file %s does not match the checksum in the metadata", path)
	}
	return nil
}

func recoveryPinBackup(mountPath string, id string) error {
	path := filepath.Join(mountPath, "backups", ".recovery-pin")
	if err := os.WriteFile(path, []byte(id), 0660); err != nil {
		return fmt.Errorf("could not pin the backup %s: %s", id, err)
	}
	return nil
}

func recoveryClearPin(mountPath string, id string) {
	path := filepath.Join(mountPath, "backups", ".recovery-pin")
	data, err := os.ReadFile(path)
	if err != nil || strings.TrimSpace(string(data)) != id {
		return
	}
	_ = os.Remove(path)
}

func recoveryReadGeneration(mountPath string, internalId int) string {
	path := filepath.Join(mountPath, "nodes", strconv.Itoa(internalId), "input", "node.json")
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
	return parsed.Generation
}

const recoveryDecommissionedMarker = "decommissioned"

func recoveryWriteMarker(mountPath string, internalId int, generation string) error {
	if generation == "" {
		generation = "decommissioned"
	}

	dir := filepath.Join(mountPath, "nodes", strconv.Itoa(internalId), "input")
	if err := os.MkdirAll(dir, 0755); err != nil {
		return err
	}

	return os.WriteFile(filepath.Join(dir, recoveryDecommissionedMarker), []byte(generation), 0600)
}

func recoveryMarkerExists(mountPath string, internalId int) bool {
	path := filepath.Join(mountPath, "nodes", strconv.Itoa(internalId), "input", recoveryDecommissionedMarker)
	_, err := os.Stat(path)
	return err == nil
}

func recoveryCanChangeBackup(mountPath string, cluster shared.ClusterRecord) bool {
	nodes := controlPlaneNodes(cluster)
	if len(nodes) == 0 {
		return false
	}

	for _, node := range nodes {
		if !recoveryMarkerExists(mountPath, node.InternalId) {
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
