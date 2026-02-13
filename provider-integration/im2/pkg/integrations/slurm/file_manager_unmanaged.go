package slurm

import (
	ctrl "ucloud.dk/pkg/controller"
	"ucloud.dk/shared/pkg/log"
)

func InitUnmanagedDrives(name string) FileManagementService {
	return &UnmanagedDrivesManager{}
}

type UnmanagedDrivesManager struct{}

func (u *UnmanagedDrivesManager) HandleQuotaUpdate(drives []LocatedDrive, update *ctrl.EventWalletUpdated) {
	log.Info("Unmanaged quota")
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
