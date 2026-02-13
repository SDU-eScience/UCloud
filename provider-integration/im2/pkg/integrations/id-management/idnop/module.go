package idnop

import (
	cfg "ucloud.dk/pkg/config"
	ctrl "ucloud.dk/pkg/controller"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
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
					return ConnectionError(err.Error()), nil
				}

				err = ctrl.IdmRegisterCompleted(username, uid, true).AsError()
				if err != nil {
					return ConnectionError(err.Error()), nil
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

func ConnectionError(error string) string {
	return "TODO" // TODO
}
