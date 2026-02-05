package slurm

import (
	"fmt"
	"syscall"
	"time"

	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	gpfs2 "ucloud.dk/pkg/im/external/gpfs"
	apm "ucloud.dk/shared/pkg/accounting"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

func InitGpfsManager(name string, config *cfg.SlurmFsManagementGpfs) FileManagementService {
	fs := ServiceConfig.FileSystems[name]
	client := gpfs2.NewClient(config.Server.ToURL(), config.VerifyTls, config.CaCertFile.Get())
	if !client.Authenticate(config.Username, config.Password) {
		log.Error("Failed to authenticate with GPFS at %v@%v!", config.Username, config.Server.ToURL())
	}

	// NOTE(Dan): This is some annoying extra code branch to ensure that IM/User doesn't start before we have created
	// a fileset in GPFS. If it would, then we risk automatic creation of the folder outside GPFS. This also makes the
	// "Connect" annoyingly slow since we _have_ to wait for the fileset creation to finish.
	g := &GpfsManager{
		name:        name,
		config:      config,
		client:      client,
		unitInBytes: UnitToBytes(fs.Payment.Unit),
	}

	ctrl.OnConnectionComplete(func(username string, uid uint32) {
		if cfg.Services.Unmanaged {
			return
		}

		drives := EvaluateAllLocators(apm.WalletOwnerUser(username))
		for _, drive := range drives {
			mapping, filesetName, ok := g.resolveLocatedDrive(drive, drive.CategoryName)
			if !ok {
				continue
			}

			if !g.client.FilesetExists(mapping.FileSystem, filesetName) {
				g.client.FilesetCreate(&gpfs2.Fileset{
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
		}
	})

	return g
}

type GpfsManager struct {
	name        string
	config      *cfg.SlurmFsManagementGpfs
	client      *gpfs2.Client
	unitInBytes uint64
}

func (g *GpfsManager) HandleQuotaUpdate(drives []LocatedDrive, update *ctrl.NotificationWalletUpdated) {
	for _, drive := range drives {
		log.Info("Handle GPFS quota update for %s (locked=%v): %v GB", drive.FilePath, update.Locked, update.CombinedQuota)
		mapping, filesetName, ok := g.resolveLocatedDrive(drive, update.Category.Name)
		if !ok {
			continue
		}

		filesQuota := 0
		if update.Locked {
			filesQuota = 1
		}

		byteQuota := int(update.CombinedQuota * g.unitInBytes)
		if byteQuota == 0 {
			// NOTE(Dan): A quota of 0 means unlimited and 1 doesn't work, set it to 50MB instead
			byteQuota = 50 * 1000 * 1000
		}

		if !g.client.FilesetExists(mapping.FileSystem, filesetName) {
			g.client.FilesetCreate(&gpfs2.Fileset{
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

		g.client.FilesetQuota(&gpfs2.Fileset{
			Name:       filesetName,
			Filesystem: mapping.FileSystem,
			QuotaBytes: byteQuota,
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

func getDiskUsage(path string) (uint64, error) {
	var stat syscall.Statfs_t

	err := syscall.Statfs(path, &stat)
	if err != nil {
		return 0, err
	}

	total := stat.Blocks * uint64(stat.Bsize)          // Total space
	used := total - (stat.Bavail * uint64(stat.Bsize)) // Used space (user-available blocks)

	return used, nil
}

func (g *GpfsManager) RunAccountingLoop() {
	var batch []apm.ReportUsageRequest

	if g.config.UseStatFsForAccounting {
		drives := ctrl.EnumerateKnownDrives()
		log.Info("Accounting %v drives", len(drives))
		for _, d := range drives {
			internalPath := DriveToLocalPath(d)

			usageBytes, err := getDiskUsage(internalPath)
			if err != nil {
				continue
			}

			unitsUsed := int64(usageBytes) / int64(g.unitInBytes)

			batch = append(batch, apm.ReportUsageRequest{
				IsDeltaCharge: false,
				Owner:         apm.WalletOwnerFromIds(d.Owner.CreatedBy, d.Owner.Project.Value),
				CategoryIdV2: apm.ProductCategoryIdV2{
					Name:     d.Specification.Product.Category,
					Provider: cfg.Provider.Id,
				},
				Usage: unitsUsed,
				Description: apm.ChargeDescription{
					Scope: util.OptValue(fmt.Sprintf("%v-%v-%v", cfg.Provider.Id, g.name, d.Id)),
				},
			})

			g.flushBatch(&batch, false)
		}
	} else {
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

				batch = append(batch, apm.ReportUsageRequest{
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
	}

	g.flushBatch(&batch, true)
}

func (g *GpfsManager) flushBatch(batch *[]apm.ReportUsageRequest, force bool) {
	current := *batch
	if len(current) == 0 {
		return
	}

	if len(current) < 500 && !force {
		return
	}

	_, err := apm.ReportUsage.Invoke(fnd.BulkRequest[apm.ReportUsageRequest]{Items: current})
	if err != nil {
		log.Warn("Failed to send GPFS accounting batch: %v %v", g.name, err)
	}

	*batch = nil
}
