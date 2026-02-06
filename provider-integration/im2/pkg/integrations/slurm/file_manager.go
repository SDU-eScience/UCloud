package slurm

import (
	"time"

	"ucloud.dk/pkg/config"
	"ucloud.dk/pkg/controller"
	apm "ucloud.dk/shared/pkg/accounting"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

type FileManagementService interface {
	HandleQuotaUpdate(drives []LocatedDrive, update *controller.NotificationWalletUpdated)
	RunAccountingLoop()
}

var fileManagers map[string]FileManagementService

func InitFileManagers() {
	fileManagers = make(map[string]FileManagementService)

	for name, fsConfig := range ServiceConfig.FileSystems {
		switch fsConfig.Management.Type {
		case config.SlurmFsManagementTypeScripted:
			fileManagers[name] = InitScriptedManager(name, fsConfig.Management.Scripted())
		case config.SlurmFsManagementTypeGpfs:
			fileManagers[name] = InitGpfsManager(name, fsConfig.Management.GPFS())
		case config.SlurmFsManagementTypeNone:
			fileManagers[name] = InitUnmanagedDrives(name)
		}
	}

	controller.OnConnectionComplete(func(username string, uid uint32) {
		if config.Services.Unmanaged {
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

		var gifts []apm.RegisterProviderGiftRequest
		for category, quota := range quotaByCategory {
			gifts = append(gifts, apm.RegisterProviderGiftRequest{
				OwnerUsername: username,
				Category:      apm.ProductCategoryIdV2{Name: category, Provider: config.Provider.Id},
				Quota:         quota,
			})
		}

		if len(gifts) > 0 {
			_, err := apm.RegisterProviderGift.Invoke(fnd.BulkRequest[apm.RegisterProviderGiftRequest]{Items: gifts})
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

func (n *NopFileManagementService) HandleQuotaUpdate(drives []LocatedDrive, update *controller.NotificationWalletUpdated) {
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
