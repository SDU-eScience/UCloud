package slurm

import (
	"fmt"
	"time"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/gpfs"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

func InitGpfsManager(name string, config *cfg.SlurmFsManagementGpfs) FileManagementService {
	fs := ServiceConfig.FileSystems[name]
	client := gpfs.NewClient(config.Server.ToURL(), config.VerifyTls, config.CaCertFile.Get())
	if !client.Authenticate(config.Username, config.Password) {
		log.Error("Failed to authenticate with GPFS at %v@%v!", config.Username, config.Server.ToURL())
	}

	return &GpfsManager{
		name:        name,
		config:      config,
		client:      client,
		unitInBytes: UnitToBytes(fs.Payment.Unit),
	}
}

type GpfsManager struct {
	name        string
	config      *cfg.SlurmFsManagementGpfs
	client      *gpfs.Client
	unitInBytes uint64
}

func (g *GpfsManager) HandleQuotaUpdate(drives []LocatedDrive, update *ctrl.NotificationWalletUpdated) {
	for _, drive := range drives {
		mapping, ok := g.config.Mapping[drive.LocatorName]
		if !ok {
			log.Error("Could not determine GPFS mapping for category=%v, locator=%v", update.Category.Name,
				drive.LocatorName)
			continue
		}

		filesetName := util.InjectParametersIntoString(mapping.FileSetPattern, drive.Parameters)
		filesQuota := 1
		if update.Locked {
			filesQuota = 0
		}

		if !g.client.FilesetExists(mapping.FileSystem, filesetName) {
			g.client.FilesetCreate(&gpfs.Fileset{
				Name:        filesetName,
				Filesystem:  mapping.FileSystem,
				Description: "UCloud managed drive",
				Path:        drive.FilePath,
				Permissions: drive.RecommendedPermissions,
				Owner:       fmt.Sprintf("%v:%v", drive.RecommendedOwnerName, drive.RecommendedGroupName),
				Parent:      mapping.ParentFileSet,
				Created:     time.Now(),
			})
		}

		g.client.FilesetQuota(&gpfs.Fileset{
			Name:       filesetName,
			Filesystem: mapping.FileSystem,
			QuotaBytes: int(update.CombinedQuota * g.unitInBytes),
			QuotaFiles: filesQuota,
		})

		err := RegisterDriveInfo(drive)
		if err != nil {
			// TODO How do we make sure this can run again at a later time?
			log.Warn("Failed to register drive with UCloud: %v", err)
		}
	}
}
