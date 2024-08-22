package slurm

import (
	"fmt"
	"os"
	"os/user"
	"syscall"
	"time"
	"ucloud.dk/pkg/termio"
	"ucloud.dk/pkg/util"

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
	if cfg.Mode == cfg.ServerModeServer {
		ctrl.InitJobDatabase()
	}

	ctrl.Files = InitializeFiles()
	InitTaskSystem()
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

func HandlePlugin(pluginName string) {
	ServiceConfig = cfg.Services.Slurm()
	switch pluginName {
	case "connect":
		connectionUrl, err := ctrl.InitiateReverseConnectionFromUser.Invoke(util.EmptyValue)
		if err != nil {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "%s", err)
			os.Exit(1)
		}

		termio.WriteLine("You can finish the connection by going to: %s", connectionUrl)
		termio.WriteLine("")
		termio.WriteStyledLine(termio.Bold, 0, 0, "Waiting for connection to complete... (Please keep this window open)")
		for {
			ucloudUsername, err := ctrl.Whoami.Invoke(util.EmptyValue)
			if err == nil {
				localUserinfo, err := user.LookupId(fmt.Sprint(os.Getuid()))
				localUsername := ""
				if err != nil {
					localUsername = fmt.Sprintf("#%v", os.Getuid())
				} else {
					localUsername = localUserinfo.Username
				}
				termio.WriteStyledLine(termio.Bold, termio.Green, 0, "Connection complete! Welcome '%s'/'%s'", localUsername, ucloudUsername)
				break
			} else {
				time.Sleep(1 * time.Second)
			}
		}
	}
}

func handleApmNotification(update *ctrl.NotificationWalletUpdated) {
	drives := EvaluateLocators(update.Owner, update.Category.Name)
	FileManager(update.Category.Name).HandleQuotaUpdate(drives, update)
	Accounting.OnWalletUpdated(update)
}
