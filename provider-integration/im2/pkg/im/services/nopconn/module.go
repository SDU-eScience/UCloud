package nopconn

import (
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/util"
)

func Init() {
	ctrl.Connections = ctrl.ConnectionService{
		Initiate: func(username string, signingKey util.Option[int]) (string, error) {
			if ctrl.IdentityManagement.InitiateConnection != nil {
				return ctrl.IdentityManagement.InitiateConnection(username)
			} else {
				uid, err := ctrl.IdentityManagement.HandleAuthentication(username)
				if err != nil {
					return ctrl.ConnectionError(err.Error()), nil
				}

				err = ctrl.RegisterConnectionComplete(username, uid, true)
				if err != nil {
					return ctrl.ConnectionError(err.Error()), nil
				}

				return cfg.Provider.Hosts.UCloudPublic.ToURL(), nil
			}
		},

		RetrieveManifest: func() ctrl.Manifest {
			return ctrl.Manifest{
				Enabled:                true,
				RequiresMessageSigning: false,
				UnmanagedConnections:   ctrl.IdentityManagement.UnmanagedConnections,
				ExpireAfterMs:          ctrl.IdentityManagement.ExpiresAfter,
			}
		},

		RetrieveCondition: func() ctrl.Condition {
			return ctrl.Condition{
				Page:  "https://status.cloud.sdu.dk",
				Level: ctrl.ConditionLevelNormal,
			}
		},
	}
}
