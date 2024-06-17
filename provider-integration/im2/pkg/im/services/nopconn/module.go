package nopconn

import (
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/util"
)

func Init() {
	ctrl.Connections = ctrl.ConnectionService{
		Initiate: func(username string, signingKey util.Option[int]) (redirectToUrl string) {
			uid, err := ctrl.IdentityManagement.HandleAuthentication(username)
			if err != nil {
				return ctrl.ConnectionError(err.Error())
			}

			err = ctrl.RegisterConnectionComplete(username, uid)
			if err != nil {
				return ctrl.ConnectionError(err.Error())
			}

			return cfg.Provider.Hosts.UCloudPublic.ToURL()
		},

		RetrieveManifest: func() ctrl.Manifest {
			return ctrl.Manifest{
				Enabled:                true,
				ExpiresAfterMs:         util.OptNone[uint64](),
				RequiresMessageSigning: false,
			}
		},
	}
}
