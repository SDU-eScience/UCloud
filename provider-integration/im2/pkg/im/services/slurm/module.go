package slurm

import (
	"fmt"
	"net/http"
	"os"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/pkg/im/services/idscripted"
	"ucloud.dk/pkg/log"
)

var ServiceConfig *cfg.ServicesConfigurationSlurm

func Init(config *cfg.ServicesConfigurationSlurm) {
	ServiceConfig = config

	ctrl.LaunchUserInstances = true
	ctrl.Files = InitializeFiles()

	switch config.IdentityManagement.Type {
	case cfg.IdentityManagementTypeScripted:
		idscripted.Init(config.IdentityManagement.Scripted())
	}

	// IPC
	if cfg.Mode == cfg.ServerModeServer {
		Hello.Handler(func(r *ipc.Request[string]) ipc.Response {
			log.Info("Handling hello message from %v", r.Uid)
			return ipc.Response{
				StatusCode: http.StatusOK,
				Payload:    fmt.Sprintf("Hello %v! My name is %v. I have received this message: \"%v\".", r.Uid, os.Getuid(), r.Payload),
			}
		})

		driveIpcServer()
	} else if cfg.Mode == cfg.ServerModeUser {
		resp, err := Hello.Invoke(fmt.Sprintf("This is a message from %v.", os.Getuid()))
		log.Info("IPC response received! %v %v", resp, err)
	}
}

var Hello = ipc.NewCall[string, string]("slurm.hello")
