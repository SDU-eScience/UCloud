package slurm

import (
	"encoding/json"
	"errors"
	"fmt"
	"math"
	"net/http"
	"os"
	"os/user"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"
	"ucloud.dk/pkg/im/ipc"

	"ucloud.dk/pkg/apm"
	fnd "ucloud.dk/pkg/foundation"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	slurmcli "ucloud.dk/pkg/im/external/slurm"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

var Machines []apm.ProductV2
var machineSupport []orc.JobSupport
var SlurmClient *slurmcli.Client

var jobNameUnsafeRegex *regexp.Regexp = regexp.MustCompile(`[^\w ():_-]`)
var unknownApplication = orc.NameAndVersion{Name: "unknown", Version: "unknown"}

var ipcRegisterJobUpdate = ipc.NewCall[[]orc.ResourceUpdateAndId[orc.JobUpdate], util.Empty]("slurm.register_job_update")

func InitCompute() ctrl.JobsService {
	loadComputeProducts()

	SlurmClient = slurmcli.NewClient()
	if SlurmClient == nil && len(Machines) > 0 {
		panic("Failed to initialize SlurmClient!")
	}

	if cfg.Mode == cfg.ServerModeServer {
		ipcRegisterJobUpdate.Handler(func(r *ipc.Request[[]orc.ResourceUpdateAndId[orc.JobUpdate]]) ipc.Response[util.Empty] {
			length := len(r.Payload)
			for i := 0; i < length; i++ {
				item := &r.Payload[i]
				job, ok := ctrl.RetrieveJob(item.Id)

				if !ok {
					continue
				}

				if !ctrl.BelongsToWorkspace(orc.ResourceOwnerToWalletOwner(job.Resource), r.Uid) {
					continue
				}
			}

			err := orc.UpdateJobs(fnd.BulkRequest[orc.ResourceUpdateAndId[orc.JobUpdate]]{Items: r.Payload})
			code := http.StatusOK
			errorMessage := ""
			if err != nil {
				code = http.StatusBadGateway
				errorMessage = err.Error()
			}

			return ipc.Response[util.Empty]{
				StatusCode:   code,
				ErrorMessage: errorMessage,
			}
		})

		go func() {
			if len(Machines) == 0 {
				return
			}

			for util.IsAlive {
				loopComputeMonitoring()
				loopAccounting()
				time.Sleep(5 * time.Second)
			}
		}()
	}

	return ctrl.JobsService{
		Submit:                   submitJob,
		Terminate:                terminateJob,
		Extend:                   extendJob,
		RetrieveProducts:         retrieveMachineSupport,
		Follow:                   follow,
		HandleShell:              handleShell,
		OpenWebSession:           openWebSession,
		ServerFindIngress:        serverFindIngress,
		RequestDynamicParameters: requestDynamicParameters,
	}
}

func requestDynamicParameters(owner orc.ResourceOwner, app *orc.Application) []orc.ApplicationParameter {
	if owner.Project == "" {
		return nil
	}

	project, ok := ctrl.GetLastKnownProject(owner.Project)
	if !ok {
		return nil
	}

	if !project.Status.PersonalProviderProjectFor.Present {
		return nil
	}

	accounts := []string{"unknown"}
	myUser, err := user.Current()
	if err == nil {
		accounts = SlurmClient.UserListAccounts(myUser.Username)
	}

	var opts []orc.EnumOption
	for _, account := range accounts {
		opts = append(opts, orc.EnumOption{
			Name:  account,
			Value: account,
		})
	}

	return []orc.ApplicationParameter{
		orc.ApplicationParameterEnumeration(
			SlurmAccountParameter,
			false,
			"Slurm account",
			"The slurm account to use for this job.",
			opts,
		),
	}
}

const InjectedPrefix = "_injected_"
const SlurmAccountParameter = InjectedPrefix + "slurmAccount"

var nextComputeAccountingTime = time.Now()

// Get jobs and report usage to UCloud core
func loopAccounting() {
	now := time.Now()
	if now.After(nextComputeAccountingTime) {
		billing := Accounting.FetchUsageInMinutes()

		var reportItems []apm.UsageReportItem
		for owner, seconds := range billing {
			machineCategory := cfg.Services.Slurm().Compute.Machines[owner.AssociatedWithCategory]

			var usageMillis float64 = float64(seconds) * 1000 * 60
			var usage float64 = 0

			if machineCategory.Payment.Type == cfg.PaymentTypeMoney {
				switch machineCategory.Payment.Interval {
				case cfg.PaymentIntervalMinutely:
					usage = usageMillis / 1000.0 / 60.0
				case cfg.PaymentIntervalHourly:
					usage = usageMillis / 1000 / 60 / 60
				case cfg.PaymentIntervalDaily:
					usage = usageMillis / 1000 / 60 / 60 / 24
				}

				usage *= machineCategory.Payment.Price
			} else {
				usage = usageMillis / 1000.0 / 60.0
			}

			reportItems = append(reportItems,
				apm.UsageReportItem{
					IsDeltaCharge: false,
					Owner:         owner.Owner,
					CategoryIdV2: apm.ProductCategoryIdV2{
						Name:     owner.AssociatedWithCategory,
						Provider: cfg.Provider.Id,
					},
					Usage: int64(usage),
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

// Monitor running jobs, and register unknown jobs to UCloud
func loopComputeMonitoring() {
	jobs := SlurmClient.JobList()

	batch := ctrl.BeginJobUpdates()
	activeJobs := ctrl.GetJobs()

	jobsBySlurmId := make(map[int]string)
	for jobId, job := range activeJobs {
		parsed, ok := parseJobProviderId(job.ProviderGeneratedId)
		if !ok {
			continue
		}

		jobsBySlurmId[parsed.SlurmId] = jobId
	}

	unknownJobs := []*slurmcli.Job{}

	for _, slurmJob := range jobs {
		stateInfo, ok := slurmToUCloudState[slurmJob.State]
		if !ok {
			continue
		}

		ucloudId, ok := jobsBySlurmId[slurmJob.JobID]
		if !ok {
			if stateInfo.State == orc.JobStateInQueue || stateInfo.State == orc.JobStateRunning {
				unknownJobs = append(unknownJobs, &slurmJob)
			}
			continue
		}

		didUpdate := batch.TrackState(ucloudId, stateInfo.State, util.OptValue(stateInfo.Message))
		if didUpdate {
			nodeList := SlurmClient.JobGetNodeList(slurmJob.JobID)
			if len(nodeList) > 0 {
				batch.TrackAssignedNodes(ucloudId, nodeList)
			}
		}
	}

	// Register unknown jobs
	toRegister := []orc.ProviderRegisteredResource[orc.JobSpecification]{}
	for _, slurmJob := range unknownJobs {
		if slurmJob.Account == "" {
			continue
		}

		slurmCfg := AccountMapper.ServerSlurmJobToConfiguration(slurmJob)

		if !slurmCfg.Present {
			continue
		}

		desiredName := fmt.Sprintf("%s (SlurmID: %d)", slurmJob.Name, slurmJob.JobID)
		safeName := jobNameUnsafeRegex.ReplaceAllString(desiredName, "")

		timeAllocation := util.Option[orc.SimpleDuration]{}
		timeAllocation.Set(orc.SimpleDurationFromMillis(int64(slurmJob.TimeLimit) * 1000))

		createdBy := util.Option[string]{Value: slurmCfg.Value.UCloudUsername, Present: true}
		projectId := util.Option[string]{}

		if slurmCfg.Value.Owner.Type == apm.WalletOwnerTypeProject {
			projectId.Set(slurmCfg.Value.Owner.ProjectId)
		}

		providerJobId := util.Option[string]{
			Value: parsedProviderJobId{
				BelongsToAccount: slurmJob.Account,
				SlurmId:          slurmJob.JobID,
			}.String(),
			Present: true,
		}

		newJobResource := orc.ProviderRegisteredResource[orc.JobSpecification]{
			Spec: orc.JobSpecification{
				Name:                  safeName,
				Application:           unknownApplication,
				ResourceSpecification: orc.ResourceSpecification{Product: slurmCfg.Value.EstimatedProduct},
				Replicas:              slurmCfg.Value.EstimatedNodeCount,
				Parameters:            make(map[string]orc.AppParameterValue),
				Resources:             []orc.AppParameterValue{},
				TimeAllocation:        timeAllocation,
			},
			ProviderGeneratedId: providerJobId,
			CreatedBy:           createdBy,
			Project:             projectId,
		}

		comment, _ := SlurmClient.JobComment(slurmJob.JobID)
		if comment != ucloudSlurmComment {
			toRegister = append(toRegister, newJobResource)
		}
	}
	batch.End()

	for _, chunk := range util.ChunkBy(toRegister, 100) {
		if len(chunk) > 0 {
			response, err := orc.RegisterJobs(
				fnd.BulkRequest[orc.ProviderRegisteredResource[orc.JobSpecification]]{
					Items: chunk,
				},
			)

			if err != nil {
				log.Warn("Error while registering jobs: %s", err.Error())
			}

			for _, registeredJob := range response.Responses {
				job, err := orc.RetrieveJob(registeredJob.Id, orc.BrowseJobsFlags{})

				if err != nil {
					log.Warn("Error while retrieving job: %s", err.Error())
				}

				ctrl.TrackNewJob(job)
			}
		}
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
	providerId, ok := parseJobProviderId(request.Job.ProviderGeneratedId)
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

func parseJobProviderId(providerId string) (parsedProviderJobId, bool) {
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
		jobCfg := SlurmJobConfiguration{
			Owner:              orc.ResourceOwnerToWalletOwner(request.JobToSubmit.Resource),
			EstimatedProduct:   request.JobToSubmit.Specification.Product,
			EstimatedNodeCount: request.JobToSubmit.Specification.Replicas,
			Job:                util.OptValue(request.JobToSubmit),
		}

		accounts := AccountMapper.UCloudConfigurationFindSlurmAccount(jobCfg)

		if len(accounts) != 1 {
			return util.OptNone[string](), &util.HttpError{
				StatusCode: http.StatusInternalServerError,
				Why:        "Ambiguous number of accounts",
			}
		}

		accountName = accounts[0]
	}

	sbatchResult := CreateSBatchFile(request.JobToSubmit, jobFolder, accountName)
	err = sbatchResult.Error
	sbatchFileContent := sbatchResult.Content
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

	var updates []orc.ResourceUpdateAndId[orc.JobUpdate]

	for _, target := range sbatchResult.DynamicTargets {
		targetAsJson, _ := json.Marshal(target)

		updates = append(updates, orc.ResourceUpdateAndId[orc.JobUpdate]{
			Id: job.Id,
			Update: orc.JobUpdate{
				Status: util.OptValue(fmt.Sprintf("Target: %s", string(targetAsJson))),
			},
		})
	}

	if ServiceConfig.Ssh.Enabled {
		portString := ""
		host := ServiceConfig.Ssh.Host
		if host.Port != 22 {
			portString = fmt.Sprintf(" -p %d", host.Port)
		}

		uinfo, err := user.Current()
		username := ""
		if err == nil {
			username = uinfo.Username
		} else {
			username = fmt.Sprint(os.Getuid())
		}

		updates = append(updates, orc.ResourceUpdateAndId[orc.JobUpdate]{
			Id: job.Id,
			Update: orc.JobUpdate{
				Status: util.OptValue(
					fmt.Sprintf("SSH: Connected! Available at: ssh %s@%s"+portString, username, host.Address),
				),
			},
		})
	}

	if len(updates) > 0 {
		// NOTE(Dan): Error is ignored since there is no obvious way to recover here. Ignoring the error is probably
		// the best we can do.
		_, _ = ipcRegisterJobUpdate.Invoke(updates)
	}

	return util.OptValue(providerId), nil
}

func loadComputeProducts() {
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
					Type:        apm.ProductTypeCCompute,
					Category:    productCategory,
					Name:        name,
					Description: "A compute product", // TODO

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

	// open relevant files (might take multiple loops to achieve this)
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
	parsedId, ok := parseJobProviderId(job.ProviderGeneratedId)
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

	if owner.ProjectId != "" {
		dir, err := os.UserHomeDir()

		if err == nil {
			project, ok := ctrl.GetLastKnownProject(owner.ProjectId)
			if !ok {
				basePath = dir
			} else if project.Status.PersonalProviderProjectFor.Present {
				basePath = dir
			}
		}
	}

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
