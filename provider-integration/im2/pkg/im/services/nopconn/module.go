package nopconn

import (
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/util"
)

func Init() {
	ctrl.Connections = ctrl.ConnectionService{
		Initiate: func(username string, signingKey util.Option[int]) (string, *util.HttpError) {
			if ctrl.IdentityManagement.InitiateConnection != nil {
				return ctrl.IdentityManagement.InitiateConnection(username)
			} else {
				uid, err := ctrl.IdentityManagement.HandleAuthentication(username)
				if err != nil {
					return ctrl.ConnectionError(err.Error()), nil
				}

				err = ctrl.RegisterConnectionComplete(username, uid, true).AsError()
				if err != nil {
					return ctrl.ConnectionError(err.Error()), nil
				}

				return cfg.Provider.Hosts.UCloudPublic.ToURL(), nil
			}
		},

		RetrieveManifest: func() orcapi.ProviderIntegrationManifest {
			return orcapi.ProviderIntegrationManifest{
				Enabled:       true,
				ExpireAfterMs: ctrl.IdentityManagement.ExpiresAfter,
			}
		},
	}
}
