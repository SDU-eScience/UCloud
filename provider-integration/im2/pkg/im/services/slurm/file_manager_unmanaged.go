package slurm

import (
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/log"
)

func InitUnmanagedDrives(name string) FileManagementService {
	return &UnmanagedDrivesManager{}
}

type UnmanagedDrivesManager struct{}

func (u *UnmanagedDrivesManager) HandleQuotaUpdate(drives []LocatedDrive, update *ctrl.NotificationWalletUpdated) {
	for _, drive := range drives {
		err := RegisterDriveInfo(drive)
		if err != nil {
			log.Warn("Failed to register drive with UCloud: %v", err)
		}
	}
}

func (u *UnmanagedDrivesManager) RunAccountingLoop() {
	// Do nothing
}
