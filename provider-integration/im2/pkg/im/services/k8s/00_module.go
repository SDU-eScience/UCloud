package k8s

import (
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/k8s/filesystem"
	"ucloud.dk/pkg/im/services/k8s/shared"
	"ucloud.dk/pkg/util"
)

func Init(config *cfg.ServicesConfigurationKubernetes) {
	shared.ServiceConfig = config
	shared.Init()

	ctrl.LaunchUserInstances = false

	ctrl.InitJobDatabase()
	ctrl.InitDriveDatabase()
	ctrl.InitScriptsLogDatabase()
	ctrl.Connections = ctrl.ConnectionService{
		Initiate: func(username string, signingKey util.Option[int]) (redirectToUrl string) {
			_ = ctrl.RegisterConnectionComplete(username, ctrl.UnknownUser, true)
			return cfg.Provider.Hosts.UCloudPublic.ToURL()
		},
		Unlink: func(username string, uid uint32) error {
			return nil
		},
		RetrieveManifest: func() ctrl.Manifest {
			return ctrl.Manifest{
				Enabled:                true,
				ExpiresAfterMs:         util.Option[uint64]{},
				RequiresMessageSigning: false,
			}
		},
	}

	ctrl.Files = filesystem.InitFiles()
	ctrl.Jobs = InitCompute()

	filesystem.InitTaskSystem()

	ctrl.ApmHandler.HandleNotification = func(update *ctrl.NotificationWalletUpdated) {

	}

	ctrl.RegisterProducts(shared.Machines)
	ctrl.RegisterProducts(shared.StorageProducts)
	ctrl.RegisterProducts(shared.LinkProducts)
	ctrl.RegisterProducts(shared.IpProducts)
	ctrl.RegisterProducts(shared.LicenseProducts)
}
