package slurm

import (
	"fmt"
	"time"
	"ucloud.dk/pkg/apm"
	fnd "ucloud.dk/pkg/foundation"
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
		mapping, filesetName, ok := g.resolveLocatedDrive(drive, update.Category.Name)
		if !ok {
			continue
		}

		filesQuota := 0
		if update.Locked {
			filesQuota = 1
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

func (g *GpfsManager) resolveLocatedDrive(drive LocatedDrive, category string) (cfg.GpfsMapping, string, bool) {
	mapping, ok := g.config.Mapping[drive.LocatorName]
	if !ok {
		log.Error("Could not determine GPFS mapping for category=%v, locator=%v", category, drive.LocatorName)
		return cfg.GpfsMapping{}, "", false
	}

	return mapping, util.InjectParametersIntoString(mapping.FileSetPattern, drive.Parameters), true
}

func (g *GpfsManager) RunAccountingLoop() {
	var batch []apm.UsageReportItem

	allocations := ctrl.FindAllAllocations(g.name)
	for _, allocation := range allocations {
		locatedDrives := EvaluateAllLocators(allocation.Owner)
		for _, locatedDrive := range locatedDrives {
			mapping, filesetName, ok := g.resolveLocatedDrive(locatedDrive, allocation.Category)
			if !ok {
				continue
			}

			fileset, ok := g.client.FilesetQuery(mapping.FileSystem, filesetName)
			if !ok {
				continue
			}

			unitsUsed := int64(fileset.UsageBytes) / int64(g.unitInBytes)
			batch = append(batch, apm.UsageReportItem{
				IsDeltaCharge: false,
				Owner:         allocation.Owner,
				CategoryIdV2: apm.ProductCategoryIdV2{
					Name:     allocation.Category,
					Provider: cfg.Provider.Id,
				},
				Usage: unitsUsed,
				Description: apm.ChargeDescription{
					Scope: util.OptValue(fmt.Sprintf("%v-%v-%v", cfg.Provider.Id, g.name, locatedDrive.LocatorName)),
				},
			})

			g.flushBatch(&batch, false)
		}
	}
	g.flushBatch(&batch, true)
}

func (g *GpfsManager) flushBatch(batch *[]apm.UsageReportItem, force bool) {
	current := *batch
	if len(current) == 0 {
		return
	}

	if len(current) < 500 && !force {
		return
	}

	_, err := apm.ReportUsage(fnd.BulkRequest[apm.UsageReportItem]{Items: current})
	if err != nil {
		log.Warn("Failed to send GPFS accounting batch: %v %v", g.name, err)
	}

	*batch = nil
}
