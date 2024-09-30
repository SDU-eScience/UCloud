package slurm

import (
	"fmt"
	"ucloud.dk/pkg/apm"
	fnd "ucloud.dk/pkg/foundation"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

func InitScriptedManager(name string, config *cfg.SlurmFsManagementScripted) FileManagementService {
	svc := &scriptedFileManagementService{}
	svc.name = name
	svc.onUsageReporting.Script = config.OnUsageReporting
	svc.onQuotaUpdated.Script = config.OnQuotaUpdated

	fs := ServiceConfig.FileSystems[name]
	svc.unitInBytes = UnitToBytes(fs.Payment.Unit)

	return svc
}

type scriptedFileManagementDrive struct {
	Title                  string `json:"title"`
	LocatorName            string `json:"locatorName"`
	CategoryName           string `json:"categoryName"`
	FilePath               string `json:"filePath"`
	RecommendedOwnerName   string `json:"recommendedOwnerName"`
	RecommendedGroupName   string `json:"recommendedGroupName"`
	RecommendedPermissions string `json:"recommendedPermissions"`
}

type scriptedFileManagementQuotaReq struct {
	Drive       scriptedFileManagementDrive `json:"drive"`
	QuotaUpdate struct {
		CombinedQuotaInBytes uint64 `json:"combinedQuotaInBytes"`
		Locked               bool   `json:"locked"`
	} `json:"quotaUpdate"`
}

type scriptedFileManagementUsageReq struct {
	LocatorName  string `json:"locatorName"`
	CategoryName string `json:"categoryName"`
	FilePath     string `json:"filePath"`
}

type scriptedFileManagementUsageResp struct {
	UsageInBytes uint64 `json:"usageInBytes"`
}

type scriptedFileManagementService struct {
	name             string
	onQuotaUpdated   ctrl.Script[scriptedFileManagementQuotaReq, util.Empty]
	onUsageReporting ctrl.Script[scriptedFileManagementUsageReq, scriptedFileManagementUsageResp]
	unitInBytes      uint64
}

func (s *scriptedFileManagementService) HandleQuotaUpdate(drives []LocatedDrive, update *ctrl.NotificationWalletUpdated) {
	log.Info("Scripted quota")
	for _, drive := range drives {
		req := scriptedFileManagementQuotaReq{}
		req.Drive = scriptedFileManagementDrive{
			Title:                  drive.Title,
			LocatorName:            drive.LocatorName,
			CategoryName:           drive.CategoryName,
			FilePath:               drive.FilePath,
			RecommendedOwnerName:   drive.RecommendedOwnerName,
			RecommendedGroupName:   drive.RecommendedGroupName,
			RecommendedPermissions: drive.RecommendedPermissions,
		}

		req.QuotaUpdate.Locked = update.Locked
		req.QuotaUpdate.CombinedQuotaInBytes = update.CombinedQuota * s.unitInBytes
		s.onQuotaUpdated.Invoke(req)
	}
}

func (s *scriptedFileManagementService) RunAccountingLoop() {
	var batch []apm.UsageReportItem

	allocations := ctrl.FindAllAllocations(s.name)
	for _, allocation := range allocations {
		locatedDrives := EvaluateAllLocators(allocation.Owner)
		for _, locatedDrive := range locatedDrives {
			req := scriptedFileManagementUsageReq{}
			req.FilePath = locatedDrive.FilePath
			req.CategoryName = locatedDrive.CategoryName
			req.LocatorName = locatedDrive.LocatorName

			resp, ok := s.onUsageReporting.Invoke(req)
			if !ok {
				continue
			}

			unitsUsed := int64(resp.UsageInBytes) / int64(s.unitInBytes)
			batch = append(batch, apm.UsageReportItem{
				IsDeltaCharge: false,
				Owner:         allocation.Owner,
				CategoryIdV2: apm.ProductCategoryIdV2{
					Name:     allocation.Category,
					Provider: cfg.Provider.Id,
				},
				Usage: unitsUsed,
				Description: apm.ChargeDescription{
					Scope: util.OptValue(fmt.Sprintf("%v-%v-%v", cfg.Provider.Id, s.name, locatedDrive.LocatorName)),
				},
			})

			s.flushBatch(&batch, false)
		}
	}
	s.flushBatch(&batch, true)
}

func (s *scriptedFileManagementService) flushBatch(batch *[]apm.UsageReportItem, force bool) {
	current := *batch
	if len(current) == 0 {
		return
	}

	if len(current) < 500 && !force {
		return
	}

	_, err := apm.ReportUsage(fnd.BulkRequest[apm.UsageReportItem]{Items: current})
	if err != nil {
		log.Warn("Failed to send storage accounting batch: %v %v", s.name, err)
	}

	*batch = nil
}
