package slurm

import (
	"errors"
	"fmt"
	"math"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
	"ucloud.dk/pkg/apm"
	fnd "ucloud.dk/pkg/foundation"
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

	if cfg.Mode == cfg.ServerModeServer {
		go func() {
			for util.IsAlive {
				loopComputeMonitoring()
				loopAccounting()
				time.Sleep(5 * time.Second)
			}
		}()
	}

	return ctrl.JobsService{
		Submit:            submitJob,
		Terminate:         terminateJob,
		Extend:            extendJob,
		RetrieveProducts:  retrieveMachineSupport,
		Follow:            follow,
		HandleShell:       handleShell,
		OpenWebSession:    openWebSession,
		ServerFindIngress: serverFindIngress,
	}
}

var nextComputeAccountingTime = time.Now()

func loopAccounting() {
	now := time.Now()
	if now.After(nextComputeAccountingTime) {
		billing := Accounting.FetchUsage()

		var reportItems []apm.UsageReportItem
		for owner, usage := range billing {
			reportItems = append(reportItems,
				apm.UsageReportItem{
					IsDeltaCharge: false,
					Owner:         owner.Owner,
					CategoryIdV2: apm.ProductCategoryIdV2{
						Name:     owner.AssociatedWithCategory,
						Provider: cfg.Provider.Id,
					},
					Usage: usage,
				},
			)

			if len(reportItems) > 500 {
				_, err := apm.ReportUsage(fnd.BulkRequest[apm.UsageReportItem]{Items: reportItems})
				if err != nil {
					log.Warn("Failed to report usage: %v", err)
				}
			}
		}

		if len(reportItems) > 0 {
			_, err := apm.ReportUsage(fnd.BulkRequest[apm.UsageReportItem]{Items: reportItems})
			if err != nil {
				log.Warn("Failed to report usage: %v", err)
			}
		}

		nextComputeAccountingTime = now.Add(30 * time.Second)
	}
}

func loopComputeMonitoring() {
	jobs := SlurmClient.JobList()

	batch := ctrl.BeginJobUpdates()
	activeJobs := batch.GetJobs()
	defer batch.End()

	jobsBySlurmId := make(map[int]string)
	for jobId, job := range activeJobs {
		parsed, ok := parseProviderId(job.ProviderGeneratedId)
		if !ok {
			continue
		}

		jobsBySlurmId[parsed.SlurmId] = jobId
	}

	for _, job := range jobs {
		stateInfo, ok := slurmToUCloudState[job.State]
		if !ok {
			continue
		}

		ucloudId, ok := jobsBySlurmId[job.JobID]
		if !ok {
			continue
		}

		batch.TrackState(ucloudId, stateInfo.State, util.OptValue(stateInfo.Message))
	}
}

type ucloudStateInfo struct {
	State   orc.JobState
	Message string
}

var slurmToUCloudState = map[string]ucloudStateInfo{
	"PENDING":       {orc.JobStateInQueue, "Your job is currently in the queue"},
	"CONFIGURING":   {orc.JobStateInQueue, "Your job is currently in the queue (CONFIGURING)"},
	"RESV_DEL_HOLD": {orc.JobStateInQueue, "Your job is currently in the queue (RESV_DEL_HOLD)"},
	"REQUEUE_FED":   {orc.JobStateInQueue, "Your job is currently in the queue (REQUEUE_FED)"},
	"SUSPENDED":     {orc.JobStateInQueue, "Your job is currently in the queue (SUSPENDED)"},

	"REQUEUE_HOLD": {orc.JobStateInQueue, "Your job is currently held for requeue"},
	"REQUEUED":     {orc.JobStateInQueue, "Your job is currently in the queue (REQUEUED)"},
	"RESIZING":     {orc.JobStateInQueue, "Your job is currently in the queue (RESIZING)"},

	"RUNNING":      {orc.JobStateRunning, "Your job is now running"},
	"COMPLETING":   {orc.JobStateRunning, "Your job is now running and about to complete"},
	"SIGNALING":    {orc.JobStateRunning, "Your job is now running and about to complete"},
	"SPECIAL_EXIT": {orc.JobStateRunning, "Your job is now running and about to complete"},
	"STAGE_OUT":    {orc.JobStateRunning, "Your job is now running and about to complete"},

	"STOPPED": {orc.JobStateRunning, "Your job is now running and about to complete"},

	"COMPLETED": {orc.JobStateSuccess, "Your job has successfully completed"},
	"CANCELLED": {orc.JobStateSuccess, "Your job has successfully completed, due to a cancel"},
	"FAILED":    {orc.JobStateSuccess, "Your job has completed with a Slurm status of FAILED"},

	"OUT_OF_MEMORY": {orc.JobStateSuccess, "Your job was terminated with an out of memory error"},

	"BOOT_FAIL": {orc.JobStateFailure, "Your job has failed (BOOT_FAIL)"},
	"NODE_FAIL": {orc.JobStateFailure, "Your job has failed (NODE_FAIL)"},

	"REVOKED":   {orc.JobStateFailure, "Your job has failed (REVOKED)"},
	"PREEMPTED": {orc.JobStateFailure, "Your job was preempted by Slurm"},

	"DEADLINE": {orc.JobStateExpired, "Your job has expired (DEADLINE)"},
	"TIMEOUT":  {orc.JobStateExpired, "Your job has expired (TIMEOUT)"},
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
	providerId, ok := parseProviderId(request.Job.ProviderGeneratedId)
	if !ok {
		// Nothing to do
		return nil
	}

	var slurmIdToCancel util.Option[int]
	jobs := SlurmClient.JobList()
	for _, job := range jobs {
		if job.Name == request.Job.Id {
			slurmIdToCancel.Set(job.JobID)
			break
		}

		if providerId.BelongsToAccount == job.Account && providerId.SlurmId == job.JobID {
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
	SlurmId          int
}

func (i parsedProviderJobId) String() string {
	return fmt.Sprintf("%v/%v/%v", i.BelongsToAccount, i.SlurmId, time.Now().UnixMilli())
}

func parseProviderId(providerId string) (parsedProviderJobId, bool) {
	var result parsedProviderJobId
	if providerId == "" {
		return result, false
	}

	// Format is: $BelongsToAccount/$SlurmId/$DiscoveryTimestamp

	// NOTE(Dan): Last element contains information about when the job was discovered, currently we only use this
	//for uniqueness, so we don't bother reading it here.

	split := strings.Split(providerId, "/")
	if len(split) != 3 {
		return result, false
	}

	jobId, err := strconv.Atoi(split[1])
	if err != nil {
		return result, false
	}

	result.BelongsToAccount = split[0]
	result.SlurmId = jobId
	return result, true
}

func submitJob(request ctrl.JobSubmitRequest) (util.Option[string], error) {
	baseJobFolder, ok := FindJobFolder(orc.ResourceOwnerToWalletOwner(request.JobToSubmit.Resource))

	if !ok {
		return util.OptNone[string](), &util.HttpError{
			StatusCode: http.StatusInternalServerError,
			Why:        "Unable to create job folder. File permission error?",
		}
	}
	jobFolder := filepath.Join(baseJobFolder, request.JobToSubmit.Id)
	err := os.Mkdir(jobFolder, 0770)
	if err != nil {
		return util.OptNone[string](), &util.HttpError{
			StatusCode: http.StatusInternalServerError,
			Why:        "Unable to create job folder. File permission error?",
		}
	}

	accountName := ""
	{
		accounts := AccountMapper.UCloudConfigurationFindSlurmAccount(SlurmJobConfiguration{
			Owner:              orc.ResourceOwnerToWalletOwner(request.JobToSubmit.Resource),
			EstimatedProduct:   request.JobToSubmit.Specification.Product,
			EstimatedNodeCount: request.JobToSubmit.Specification.Replicas,
		})

		if len(accounts) != 1 {
			return util.OptNone[string](), &util.HttpError{
				StatusCode: http.StatusInternalServerError,
				Why:        "Ambiguous number of accounts",
			}
		}

		accountName = accounts[0]
	}

	sbatchFileContent, err := CreateSBatchFile(request.JobToSubmit, jobFolder, accountName)
	if err != nil {
		return util.OptNone[string](), err
	}

	sbatchFilePath := filepath.Join(jobFolder, "job.sbatch")
	err = os.WriteFile(sbatchFilePath, []byte(sbatchFileContent), 0770)
	if err != nil {
		return util.OptNone[string](), &util.HttpError{
			StatusCode: http.StatusInternalServerError,
			Why:        "Failed to generate sbatch file for job submission. File permission error?",
		}
	}

	slurmId, err := SlurmClient.JobSubmit(sbatchFilePath)
	if err != nil {
		return util.OptNone[string](), err
	}

	providerId := parsedProviderJobId{
		BelongsToAccount: accountName,
		SlurmId:          slurmId,
	}.String()

	job := request.JobToSubmit
	job.ProviderGeneratedId = providerId
	ctrl.TrackNewJob(*job)

	return util.OptValue(providerId), nil
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
		support.Native.Web = ServiceConfig.Compute.Web.Enabled
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

type trackedLogFile struct {
	Rank   int
	Stdout bool
	File   *os.File
}

//goland:noinspection GoDeferInLoop
func follow(session *ctrl.FollowJobSession) {
	var logFiles []trackedLogFile

	// Open relevant files (might take multiple loops to achieve this)
	for util.IsAlive && session.Alive {
		job, ok := ctrl.RetrieveJob(session.Job.Id)
		if !ok {
			break
		}

		if job.Status.State != orc.JobStateRunning {
			// Wait for job to start
			time.Sleep(1 * time.Second)
			continue
		}

		jobFolder, ok := FindJobFolder(orc.ResourceOwnerToWalletOwner(job.Resource))
		if !ok {
			log.Info("Could not resolve internal folder needed for logs: %v", jobFolder)
			break
		}

		outputFolder := filepath.Join(jobFolder, job.Id)

		for rank := 0; rank < job.Specification.Replicas; rank++ {
			stdout, e1 := os.Open(filepath.Join(outputFolder, fmt.Sprintf("std-%d.out", rank)))
			if e1 == nil {
				logFiles = append(logFiles, trackedLogFile{
					Rank:   rank,
					Stdout: true,
					File:   stdout,
				})
				defer util.SilentClose(stdout)
			}

			stderr, e2 := os.Open(filepath.Join(outputFolder, fmt.Sprintf("std-%d.err", rank)))
			if e2 == nil {
				logFiles = append(logFiles, trackedLogFile{
					Rank:   rank,
					Stdout: false,
					File:   stderr,
				})
				defer util.SilentClose(stdout)
			}
		}

		{
			scriptOut, e1 := os.Open(filepath.Join(outputFolder, "stdout.txt"))
			if e1 == nil {
				logFiles = append(logFiles, trackedLogFile{
					Rank:   0,
					Stdout: true,
					File:   scriptOut,
				})
				defer util.SilentClose(scriptOut)
			}

			scriptErr, e2 := os.Open(filepath.Join(outputFolder, "stderr.txt"))
			if e2 == nil {
				logFiles = append(logFiles, trackedLogFile{
					Rank:   0,
					Stdout: false,
					File:   scriptErr,
				})
				defer util.SilentClose(scriptErr)
			}
		}

		break
	}

	readBuffer := make([]byte, 1024*4)

	// Watch log files
	for util.IsAlive && session.Alive {
		job, ok := ctrl.RetrieveJob(session.Job.Id)
		if !ok {
			break
		}

		if job.Status.State != orc.JobStateRunning {
			break
		}

		for _, logFile := range logFiles {
			now := time.Now()
			deadline := now.Add(5 * time.Millisecond)
			_ = logFile.File.SetReadDeadline(deadline)

			bytesRead, _ := logFile.File.Read(readBuffer)
			if bytesRead > 0 {
				message := string(readBuffer[:bytesRead])
				var stdout util.Option[string]
				var stderr util.Option[string]
				if logFile.Stdout {
					stdout.Set(message)
				} else {
					stderr.Set(message)
				}

				session.EmitLogs(logFile.Rank, stdout, stderr)
			}
		}

		time.Sleep(200 * time.Millisecond)
	}
}

func serverFindIngress(job *orc.Job) ctrl.ConfiguredWebIngress {
	return ctrl.ConfiguredWebIngress{
		IsPublic:     false,
		TargetDomain: ServiceConfig.Compute.Web.Prefix + job.Id + ServiceConfig.Compute.Web.Suffix,
	}
}

func openWebSession(job *orc.Job, rank int) (cfg.HostInfo, error) {
	parsedId, ok := parseProviderId(job.ProviderGeneratedId)
	if !ok {
		return cfg.HostInfo{}, errors.New("could not parse provider id")
	}

	nodes := SlurmClient.JobGetNodeList(parsedId.SlurmId)
	if len(nodes) <= rank {
		return cfg.HostInfo{}, errors.New("could not find slurm node")
	}

	jobFolder, ok := FindJobFolder(orc.ResourceOwnerToWalletOwner(job.Resource))
	if !ok {
		return cfg.HostInfo{}, fmt.Errorf("could not resolve internal folder needed for logs: %v", jobFolder)
	}

	outputFolder := filepath.Join(jobFolder, job.Id)
	portFilePath := filepath.Join(outputFolder, AllocatedPortFile)
	portFileData, err := os.ReadFile(portFilePath)
	if err != nil {
		return cfg.HostInfo{}, fmt.Errorf("could not read port file: %v", err)
	}
	allocatedPort, err := strconv.Atoi(strings.TrimSpace(string(portFileData)))
	if err != nil {
		return cfg.HostInfo{}, fmt.Errorf("corrupt port file: %v", err)
	}

	return cfg.HostInfo{
		Address: nodes[rank],
		Port:    allocatedPort,
		Scheme:  "http",
	}, nil
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
