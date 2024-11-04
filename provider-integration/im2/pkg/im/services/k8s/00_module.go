package k8s

import (
	"ucloud.dk/pkg/apm"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
)

var ServiceConfig *cfg.ServicesConfigurationKubernetes

var (
	Machines        []apm.ProductV2
	StorageProducts []apm.ProductV2
	LinkProducts    []apm.ProductV2
	IpProducts      []apm.ProductV2
	LicenseProducts []apm.ProductV2
)

func Init(config *cfg.ServicesConfigurationKubernetes) {
	ServiceConfig = config

	ctrl.LaunchUserInstances = false

	ctrl.InitJobDatabase()
	ctrl.InitDriveDatabase()
	ctrl.InitScriptsLogDatabase()

	ctrl.Files = InitFiles()
	ctrl.Jobs = InitCompute()

	InitTaskSystem()

	ctrl.ApmHandler.HandleNotification = func(update *ctrl.NotificationWalletUpdated) {

	}

	ctrl.RegisterProducts(Machines)
	ctrl.RegisterProducts(StorageProducts)
	ctrl.RegisterProducts(LinkProducts)
	ctrl.RegisterProducts(IpProducts)
	ctrl.RegisterProducts(LicenseProducts)
}
