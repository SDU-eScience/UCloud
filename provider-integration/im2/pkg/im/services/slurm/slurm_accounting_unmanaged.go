package slurm

import ctrl "ucloud.dk/pkg/im/controller"

func InitUnmanagedAccountManagement() AccountingService {
	service := &unmanagedAccountManagementService{}

	ctrl.OnConnectionComplete(func(username string, uid uint32) {
		// Do nothing
	})

	ctrl.OnProjectNotification(func(update *ctrl.NotificationProjectUpdated) {
		// Do nothing
	})

	return service
}

type unmanagedAccountManagementService struct{}

func (u unmanagedAccountManagementService) OnWalletUpdated(update *ctrl.NotificationWalletUpdated) {
	// Do nothing
}

func (u unmanagedAccountManagementService) FetchUsage() map[SlurmAccountOwner]int64 {
	return make(map[SlurmAccountOwner]int64)
}
