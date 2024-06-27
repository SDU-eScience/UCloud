package slurm

import (
	"syscall"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/idfreeipa"
	"ucloud.dk/pkg/im/services/idscripted"
	"ucloud.dk/pkg/im/services/nopconn"
)

var ServiceConfig *cfg.ServicesConfigurationSlurm

func Init(config *cfg.ServicesConfigurationSlurm) {
	ServiceConfig = config

	ctrl.LaunchUserInstances = true
	ctrl.Files = InitializeFiles()
	ctrl.Jobs = InitCompute()
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
		nopconn.Init()

		switch config.IdentityManagement.Type {
		case cfg.IdentityManagementTypeScripted:
			idscripted.Init(config.IdentityManagement.Scripted())
		case cfg.IdentityManagementTypeFreeIpa:
			idfreeipa.Init(config.IdentityManagement.FreeIPA())
		}
	}

	// APM
	if cfg.Mode == cfg.ServerModeServer {
		ctrl.ApmHandler.HandleNotification = handleApmNotification
	}

	// IPC
	if cfg.Mode == cfg.ServerModeServer {
		driveIpcServer()
	}
}

func handleApmNotification(update *ctrl.NotificationWalletUpdated) {
	drives := EvaluateLocators(update.Owner, update.Category.Name)
	FileManager(update.Category.Name).HandleQuotaUpdate(drives, update)
	Accounting.OnWalletUpdated(update)
}
