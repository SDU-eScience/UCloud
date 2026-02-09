package slurm

import (
	"ucloud.dk/pkg/controller"
)

func InitUnmanagedAccountManagement() AccountingService {
	service := &unmanagedAccountManagementService{}

	controller.IdmAddOnCompleteHandler(func(username string, uid uint32) {
		// Do nothing
	})

	controller.IdmAddProjectEvHandler(func(update *controller.EventProjectUpdated) {
		// Do nothing
	})

	return service
}

type unmanagedAccountManagementService struct{}

func (u unmanagedAccountManagementService) OnWalletUpdated(update *controller.EventWalletUpdated) {
	// Do nothing
}

func (u unmanagedAccountManagementService) FetchUsageInMinutes() map[SlurmAccountOwner]int64 {
	return make(map[SlurmAccountOwner]int64)
}
