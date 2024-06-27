package slurm

import (
	"fmt"
	"math"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"ucloud.dk/pkg/apm"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	slurmcli "ucloud.dk/pkg/im/slurm"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

var Machines []apm.ProductV2
var machineSupport []orc.JobSupport
var SlurmClient *slurmcli.Client

func InitCompute() ctrl.JobsService {
	loadProducts()

	SlurmClient = slurmcli.NewClient()
	if SlurmClient == nil {
		panic("Failed to initialize SlurmClient!")
	}

	return ctrl.JobsService{
		Submit:           submitJob,
		Terminate:        terminateJob,
		Extend:           extendJob,
		RetrieveProducts: retrieveMachineSupport,
	}
}

func retrieveMachineSupport() []orc.JobSupport {
	return machineSupport
}

func extendJob(_ ctrl.JobExtendRequest) error {
	return &util.HttpError{
		StatusCode: http.StatusBadRequest,
		Why:        "Extension of jobs are not supported by this provider",
	}
}

func terminateJob(request ctrl.JobTerminateRequest) error {
	providerId := parseProviderId(request.Job.ProviderGeneratedId)

	var slurmIdToCancel util.Option[int]
	jobs := SlurmClient.JobList()
	for _, job := range jobs {
		if job.Name == request.Job.Id {
			slurmIdToCancel.Set(job.JobID)
			break
		}

		if providerId.BelongsToUser == job.User && providerId.BelongsToAccount == job.Account && providerId.SlurmId == job.JobID {
			slurmIdToCancel.Set(job.JobID)
			break
		}
	}

	if slurmIdToCancel.IsSet() {
		SlurmClient.JobCancel(slurmIdToCancel.Get())
	} else {
		log.Info("We were requested to terminate job %v but this was not found anywhere in the Slurm database. "+
			"Maybe it has already stopped?", request.Job.Id)
	}

	return nil
}

type parsedProviderJobId struct {
	BelongsToAccount string
	BelongsToUser    string
	SlurmId          int
}

func parseProviderId(providerId string) parsedProviderJobId {
	var result parsedProviderJobId
	if providerId == "" {
		return result
	}

	// Format is: $BelongsToAccount/$BelongsToUser/$SlurmId/$DiscoveryTimestamp

	// NOTE(Dan): Last element contains information about when the job was discovered, currently we only use this
	//for uniqueness, so we don't bother reading it here.

	split := strings.Split(providerId, "/")
	if len(split) != 4 {
		return result
	}

	jobId, err := strconv.Atoi(split[2])
	if err != nil {
		return result
	}

	result.BelongsToAccount = split[0]
	result.BelongsToUser = split[1]
	result.SlurmId = jobId
	return result
}

func submitJob(request ctrl.JobSubmitRequest) error {
	baseJobFolder, ok := FindJobFolder(orc.ResourceOwnerToWalletOwner(request.JobToSubmit.Resource))
	if !ok {
		return &util.HttpError{
			StatusCode: http.StatusInternalServerError,
			Why:        "Unable to create job folder. File permission error?",
		}
	}
	jobFolder := filepath.Join(baseJobFolder, request.JobToSubmit.Id)
	err := os.Mkdir(jobFolder, 0770)
	if err != nil {
		return &util.HttpError{
			StatusCode: http.StatusInternalServerError,
			Why:        "Unable to create job folder. File permission error?",
		}
	}

	sbatchFileContent, err := CreateSBatchFile(request.JobToSubmit, jobFolder)
	if err != nil {
		return err
	}

	sbatchFilePath := filepath.Join(jobFolder, "job.sbatch")
	err = os.WriteFile(sbatchFilePath, []byte(sbatchFileContent), 0770)
	if err != nil {
		return &util.HttpError{
			StatusCode: http.StatusInternalServerError,
			Why:        "Failed to generate sbatch file for job submission. File permission error?",
		}
	}

	_, err = SlurmClient.JobSubmit(sbatchFilePath)
	if err != nil {
		return err
	}
	return nil
}

func loadProducts() {
	for categoryName, category := range ServiceConfig.Compute.Machines {
		productCategory := apm.ProductCategory{
			Name:                categoryName,
			Provider:            cfg.Provider.Id,
			ProductType:         apm.ProductTypeCompute,
			AccountingFrequency: apm.AccountingFrequencyPeriodicMinute,
			FreeToUse:           false,
			AllowSubAllocations: true,
		}

		usePrice := false
		switch category.Payment.Type {
		case cfg.PaymentTypeMoney:
			productCategory.AccountingUnit.Name = category.Payment.Currency
			productCategory.AccountingUnit.NamePlural = category.Payment.Currency
			productCategory.AccountingUnit.DisplayFrequencySuffix = false
			productCategory.AccountingUnit.FloatingPoint = true
			usePrice = true

		case cfg.PaymentTypeResource:
			switch category.Payment.Unit {
			case cfg.MachineResourceTypeCpu:
				productCategory.AccountingUnit.Name = "Core"
			case cfg.MachineResourceTypeGpu:
				productCategory.AccountingUnit.Name = "GPU"
			case cfg.MachineResourceTypeMemory:
				productCategory.AccountingUnit.Name = "GB"
			}

			productCategory.AccountingUnit.NamePlural = productCategory.AccountingUnit.Name
			productCategory.AccountingUnit.FloatingPoint = false
			productCategory.AccountingUnit.DisplayFrequencySuffix = true
		}

		switch category.Payment.Interval {
		case cfg.PaymentIntervalMinutely:
			productCategory.AccountingFrequency = apm.AccountingFrequencyPeriodicMinute
		case cfg.PaymentIntervalHourly:
			productCategory.AccountingFrequency = apm.AccountingFrequencyPeriodicHour
		case cfg.PaymentIntervalDaily:
			productCategory.AccountingFrequency = apm.AccountingFrequencyPeriodicDay
		}

		for groupName, group := range category.Groups {
			for _, machineConfig := range group.Configs {
				name := fmt.Sprintf("%v-%v", groupName, pickResource(group.NameSuffix, machineConfig))
				product := apm.ProductV2{
					Category:    productCategory,
					Name:        name,
					Description: "TODO", // TODO

					ProductType:               apm.ProductTypeCompute,
					Price:                     int64(math.Floor(machineConfig.Price * 1_000_000)),
					HiddenInGrantApplications: false,

					Cpu:          machineConfig.Cpu,
					CpuModel:     group.CpuModel,
					MemoryInGigs: machineConfig.MemoryInGigabytes,
					MemoryModel:  group.MemoryModel,
					Gpu:          machineConfig.Gpu,
					GpuModel:     group.GpuModel,
				}

				if !usePrice {
					product.Price = int64(pickResource(category.Payment.Unit, machineConfig))
				}

				Machines = append(Machines, product)
			}
		}
	}

	for _, machine := range Machines {
		support := orc.JobSupport{
			Product: apm.ProductReference{
				Id:       machine.Name,
				Category: machine.Category.Name,
				Provider: cfg.Provider.Id,
			},
		}

		support.Native.Enabled = true
		support.Native.Web = true
		support.Native.Vnc = true
		support.Native.Logs = true
		support.Native.Terminal = true
		support.Native.Peers = false
		support.Native.TimeExtension = false

		machineSupport = append(machineSupport, support)
	}
}

func pickResource(resource cfg.MachineResourceType, machineConfig cfg.SlurmMachineConfiguration) int {
	switch resource {
	case cfg.MachineResourceTypeCpu:
		return machineConfig.Cpu
	case cfg.MachineResourceTypeGpu:
		return machineConfig.Gpu
	case cfg.MachineResourceTypeMemory:
		return machineConfig.MemoryInGigabytes
	default:
		log.Warn("Unhandled machine resource type: %v", resource)
		return 0
	}
}

func FindJobFolder(owner apm.WalletOwner) (string, bool) {
	drives := EvaluateAllLocators(owner)
	if len(drives) == 0 {
		return "/dev/null", false
	}

	basePath := drives[0].FilePath
	folder := filepath.Join(basePath, JobFolderName)
	if _, err := os.Stat(folder); err != nil {
		err = os.MkdirAll(folder, 0770)
		if err != nil {
			log.Warn("Failed to create job folder: %v %v", folder, err.Error())
			return "/dev/null", false
		}
	}

	return folder, true
}

const JobFolderName = "UCloud Jobs"
