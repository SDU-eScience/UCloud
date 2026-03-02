package slurm

import (
	"net/http"
	"syscall"

	cfg "ucloud.dk/pkg/config"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/id-management/idfreeipa"
	"ucloud.dk/pkg/integrations/id-management/idoidc"
	"ucloud.dk/pkg/integrations/id-management/idscripted"
	"ucloud.dk/pkg/integrations/id-management/idnop"
)

var ServiceConfig *cfg.ServicesConfigurationSlurm

func Init(config *cfg.ServicesConfigurationSlurm, mux *http.ServeMux) {
	ServiceConfig = config

	controller.LaunchUserInstances = true
	if cfg.Mode == cfg.ServerModeServer {
		controller.InitJobDatabase()
		controller.InitDriveDatabase()
		controller.InitScriptsLogDatabase()
	}

	controller.Files = InitializeFiles()
	InitTaskSystem()
	controller.Jobs = InitCompute()
	controller.SshKeys = InitializeSshKeys()
	InitAccountManagement()

	if cfg.Mode == cfg.ServerModeUser {
		// NOTE(Dan): Set the umask required for this service. This is done to make sure that projects have predictable
		// results regardless of how the underlying system is configured. Without this, it is entirely possible that
		// users can create directories which no other member can use.
		syscall.Umask(0007)
	}

	if cfg.Mode == cfg.ServerModeServer {
		InitFileManagers()
	}

	// Identity management
	if cfg.Mode == cfg.ServerModeServer {
		idnop.Init()

		switch config.IdentityManagement.Type {
		case cfg.IdentityManagementTypeScripted:
			idscripted.Init(config.IdentityManagement.Scripted())
		case cfg.IdentityManagementTypeFreeIpa:
			idfreeipa.Init(config.IdentityManagement.FreeIPA())
		case cfg.IdentityManagementTypeOidc:
			idoidc.Init(config.IdentityManagement.OIDC(), mux)
		}

		InitCliServer()
	}

	// APM
	if cfg.Mode == cfg.ServerModeServer {
		controller.EventHandler.HandleNotification = handleApmNotification
	}

	// IPC
	if cfg.Mode == cfg.ServerModeServer {
		driveIpcServer()
	}

	// Products
	if cfg.Mode == cfg.ServerModeServer {
		controller.ProductsRegister(Machines)
		controller.ProductsRegister(StorageProducts)
	}
}

func InitLater(config *cfg.ServicesConfigurationSlurm) {

}

func handleApmNotification(update *controller.EventWalletUpdated) {
	drives := EvaluateLocators(update.Owner, update.Category.Name)
	FileManager(update.Category.Name).HandleQuotaUpdate(drives, update)
	Accounting.OnWalletUpdated(update)
}
