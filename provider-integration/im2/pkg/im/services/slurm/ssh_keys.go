package slurm

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	ctrl "ucloud.dk/pkg/im/controller"
	orc "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/util"
)

func InitializeSshKeys() ctrl.SshKeyService {
	return ctrl.SshKeyService{
		OnKeyUploaded: onKeyUploaded,
	}
}

const ucloudIntegrationMarker = "ucloud-integration"

func onKeyUploaded(username string, keys []orc.SshKey) *util.HttpError {
	if !ServiceConfig.Ssh.InstallKeys {
		return nil
	}

	homeDir, err := os.UserHomeDir()
	if err != nil {
		return util.UserHttpError("getting home directory: %v", err)
	}

	sshDir := filepath.Join(homeDir, ".ssh")
	if err := os.MkdirAll(sshDir, 0700); err != nil {
		return util.UserHttpError("creating SSH directory: %v", err)
	}

	authorizedKeysFile := filepath.Join(sshDir, "authorized_keys")
	info, err := os.Stat(authorizedKeysFile)
	if err == nil && info.IsDir() {
		return util.UserHttpError("~/.ssh/authorized_keys is a directory, not a file")
	}

	// Read existing authorized keys
	var authorizedKeys []string
	if data, err := os.ReadFile(authorizedKeysFile); err == nil {
		lines := strings.Split(string(data), "\n")
		for _, line := range lines {
			trimmed := strings.TrimSpace(line)
			if trimmed != "" {
				authorizedKeys = append(authorizedKeys, trimmed)
			}
		}
	} else if !os.IsNotExist(err) {
		return util.UserHttpError("reading authorized_keys: %v", err)
	}

	// Start building the new file. First skipping all UCloud marked keys and then adding in the new keys.
	newFile := ""
	for _, key := range authorizedKeys {
		if !strings.HasSuffix(key, ucloudIntegrationMarker) {
			newFile += key
			newFile += "\n"
		}
	}

	for _, key := range keys {
		withMarker := strings.TrimSpace(key.Specification.Key) + " " + ucloudIntegrationMarker
		newFile += withMarker
		newFile += "\n"
	}

	// Write updated keys to a temporary file in order to perform an atomic replacement later
	tempFile := filepath.Join(sshDir, fmt.Sprintf("authorized_keys-%d.in-progress", time.Now().Unix()))

	if err := os.WriteFile(tempFile, []byte(newFile), 0600); err != nil {
		return util.UserHttpError("writing to temporary file: %v", err)
	}

	if err := os.Rename(tempFile, authorizedKeysFile); err != nil {
		_ = os.Remove(tempFile) // Cleanup temporary file
		return util.UserHttpError("replacing authorized_keys file: %v", err)
	}

	return nil
}
