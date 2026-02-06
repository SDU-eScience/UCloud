package slurm

import (
	"ucloud.dk/pkg/controller"
)

func InitUnmanagedAccountManagement() AccountingService {
	service := &unmanagedAccountManagementService{}

	controller.OnConnectionComplete(func(username string, uid uint32) {
		// Do nothing
	})

	controller.OnProjectNotification(func(update *controller.NotificationProjectUpdated) {
		// Do nothing
	})

	return service
}

type unmanagedAccountManagementService struct{}

func (u unmanagedAccountManagementService) OnWalletUpdated(update *controller.NotificationWalletUpdated) {
	// Do nothing
}

func (u unmanagedAccountManagementService) FetchUsageInMinutes() map[SlurmAccountOwner]int64 {
	return make(map[SlurmAccountOwner]int64)
}
