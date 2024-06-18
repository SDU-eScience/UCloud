package idfreeipa

import (
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/freeipa"
)

var client *freeipa.Client
var config *cfg.IdentityManagementFreeIPA

func Init(configuration *cfg.IdentityManagementFreeIPA) {
	config = configuration
	client = freeipa.NewClient(config.Url, config.VerifyTls, config.CaCertFile.Get(), config.Username, config.Password)

	ctrl.IdentityManagement.HandleAuthentication = handleAuthentication
	ctrl.IdentityManagement.HandleProjectNotification = handleProjectNotification
}
