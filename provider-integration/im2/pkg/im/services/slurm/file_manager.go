package slurm

import (
	"time"
	"ucloud.dk/shared/pkg/apm"
	fnd "ucloud.dk/shared/pkg/foundation"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
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

	ctrl.OnConnectionComplete(func(username string, uid uint32) {
		if cfg.Services.Unmanaged {
			return
		}

		log.Info("Registering home drive of user %v (UID: %v)", username, uid)
		drives := EvaluateAllLocators(apm.WalletOwnerUser(username))
		quotaByCategory := map[string]int64{}
		for _, drive := range drives {
			fs := ServiceConfig.FileSystems[drive.CategoryName]
			locator := fs.DriveLocators[drive.LocatorName]
			free := locator.FreeQuota
			if free.Present {
				prev, _ := quotaByCategory[drive.CategoryName]
				quotaByCategory[drive.CategoryName] = prev + free.Value
			}
		}

		var gifts []apm.RegisteredProviderGift
		for category, quota := range quotaByCategory {
			gifts = append(gifts, apm.RegisteredProviderGift{
				OwnerUsername: username,
				Category:      apm.ProductCategoryIdV2{Name: category, Provider: cfg.Provider.Id},
				Quota:         quota,
			})
		}

		if len(gifts) > 0 {
			err := apm.RegisterProviderGift(fnd.BulkRequest[apm.RegisteredProviderGift]{Items: gifts})
			if err != nil {
				log.Info("Failed to register home-drive gift for user: %v (UID: %v): %v", username, uid, err)
			}
		}
	})

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
