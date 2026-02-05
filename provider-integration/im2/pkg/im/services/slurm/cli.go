package slurm

import (
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/shared/pkg/log"
)

func HandleCli(command string) {
	ServiceConfig = cfg.Services.Slurm()
	switch command {
	case "slurm-accounts":
		HandleSlurmAccountsCommand()

	case "users":
		HandleUsersCommand()

	case "projects":
		HandleProjectsCommand()

	case "jobs":
		HandleJobsCommand()

	case "drives":
		HandleDrivesCommand()

	case "scripts":
		HandleScriptsCommand()

	case "tasks":
		HandleTasksCommand()
	}
}

func InitCliServer() {
	if cfg.Mode != cfg.ServerModeServer {
		log.Error("InitCliServer called in the wrong mode!")
		return
	}

	HandleProjectsCommandServer()
	HandleUsersCommandServer()
	HandleJobsCommandServer()
	HandleDrivesCommandServer()
	HandleTasksCommandServer()
}
