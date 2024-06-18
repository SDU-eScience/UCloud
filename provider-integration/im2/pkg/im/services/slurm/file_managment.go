package slurm

import (
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
)

type FileManagementService interface {
	HandleQuotaUpdate(drives []LocatedDrive, update *ctrl.NotificationWalletUpdated)
}

var fileManagers map[string]FileManagementService

func InitFileManagers() {
	fileManagers = make(map[string]FileManagementService)

	for name, fsConfig := range ServiceConfig.FileSystems {
		switch fsConfig.Management.Type {
		case cfg.SlurmFsManagementTypeScripted:
		case cfg.SlurmFsManagementTypeGpfs:
			fileManagers[name] = InitGpfsManager(name, fsConfig.Management.GPFS())
		}
	}
}

type NopFileManagementService struct{}

func (n *NopFileManagementService) HandleQuotaUpdate(drives []LocatedDrive, update *ctrl.NotificationWalletUpdated) {
	// Do nothing
}

func FileManager(categoryName string) FileManagementService {
	manager, ok := fileManagers[categoryName]
	if !ok {
		return &NopFileManagementService{}
	}
	return manager
}
