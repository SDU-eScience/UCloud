package shared

import (
	ctrl "ucloud.dk/pkg/controller"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

func InitSshKeys() {
	ctrl.SshKeys = ctrl.SshKeyService{OnKeyUploaded: sshKeyUploaded}
}

var sshKeyListeners []func(username string, keys []orc.SshKey)

func SshKeyAddListener(listener func(username string, keys []orc.SshKey)) {
	sshKeyListeners = append(sshKeyListeners, listener)
}

func sshKeyUploaded(username string, keys []orc.SshKey) *util.HttpError {
	if err := ctrl.SshKeyTrackNew(username, keys); err != nil {
		return err
	}

	for _, listener := range sshKeyListeners {
		listener(username, keys)
	}
	return nil
}
