package slurm

import (
	"time"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

type FileManagementService interface {
	HandleQuotaUpdate(drives []LocatedDrive, update *ctrl.NotificationWalletUpdated)
	RunAccountingLoop()
}

var fileManagers map[string]FileManagementService

func InitFileManagers() {
	fileManagers = make(map[string]FileManagementService)

	for name, fsConfig := range ServiceConfig.FileSystems {
		switch fsConfig.Management.Type {
		case cfg.SlurmFsManagementTypeScripted:
			fileManagers[name] = InitScriptedManager(name, fsConfig.Management.Scripted())
		case cfg.SlurmFsManagementTypeGpfs:
			fileManagers[name] = InitGpfsManager(name, fsConfig.Management.GPFS())
		case cfg.SlurmFsManagementTypeNone:
			fileManagers[name] = InitUnmanagedDrives(name)
		}
	}

	go func() {
		next := time.Now()
		for util.IsAlive {
			now := time.Now()
			if now.After(next) {
				for _, manager := range fileManagers {
					manager.RunAccountingLoop()
				}

				next = time.Now().Add(5 * time.Minute)
			}

			time.Sleep(10 * time.Second)
		}
	}()
}

type NopFileManagementService struct{}

func (n *NopFileManagementService) HandleQuotaUpdate(drives []LocatedDrive, update *ctrl.NotificationWalletUpdated) {
	log.Info("nop handle quota update")
	// Do nothing
}

func (n *NopFileManagementService) RunAccountingLoop() {}

func FileManager(categoryName string) FileManagementService {
	manager, ok := fileManagers[categoryName]
	if !ok {
		return &NopFileManagementService{}
	}
	return manager
}
