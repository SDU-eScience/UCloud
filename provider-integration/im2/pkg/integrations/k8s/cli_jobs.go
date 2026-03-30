package k8s

import (
	"encoding/json"
	"flag"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"slices"
	"strings"
	"time"

	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/shared"
	"ucloud.dk/pkg/ipc"
	"ucloud.dk/shared/pkg/cli"
	fnd "ucloud.dk/shared/pkg/foundation"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/util"
)

func HandleJobsCommand() {
	if os.Getuid() != 0 {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "This command must be run as root!")
		os.Exit(1)
	}

	command := ""
	if len(os.Args) >= 3 {
		command = os.Args[2]
	}

	switch {
	case cli.IsListCommand(command):
		req := k8sCliJobsListRequest{}
		fs := flag.NewFlagSet("jobs ls", flag.ExitOnError)
		fs.StringVar(&req.State, "state", "", "The UCloud job state, supports regex")
		fs.StringVar(&req.JobId, "job-id", "", "The UCloud job ID, supports regex")
		fs.StringVar(&req.User, "user", "", "The UCloud username, supports regex")
		fs.StringVar(&req.Project, "project", "", "The UCloud project ID, supports regex")
		fs.StringVar(&req.Application, "application", "", "The application name, supports regex")
		fs.StringVar(&req.Category, "category", "", "The machine category, supports regex")
		fs.StringVar(&req.Queue, "queue", "", "The queue name, supports regex")
		jsonOutput := fs.Bool("json", false, "Print JSON output")
		_ = fs.Parse(os.Args[3:])

		jobs, err := k8sCliJobsList.Invoke(req)
		cli.HandleError("listing jobs", err)

		if *jsonOutput {
			data, err := json.MarshalIndent(jobs, "", "  ")
			cli.HandleError("encoding output", err)
			termio.WriteLine("%s", string(data))
			return
		}

		t := termio.Table{}
		t.AppendHeader("Submitted at")
		t.AppendHeader("UCloud ID")
		t.AppendHeader("State")
		t.AppendHeader("Application")
		t.AppendHeader("Queue")
		t.AppendHeader("Owner")

		for _, job := range jobs {
			t.Cell("%v", cli.FormatTime(job.CreatedAt))
			t.Cell("%v", job.JobId)
			t.Cell("%v", job.State)
			t.Cell("%v", job.Application)
			t.Cell("%v", job.Queue)
			owner := job.User
			if job.Project != "" {
				owner = owner + " (" + job.Project + ")"
			}
			t.Cell("%v", owner)
		}

		t.Print()

	case cli.IsGetCommand(command):
		if len(os.Args) < 4 {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Missing parameter: job id")
			os.Exit(1)
		}

		jobId := os.Args[3]

		fs := flag.NewFlagSet("jobs get", flag.ExitOnError)
		jsonOutput := fs.Bool("json", false, "Print JSON output")
		_ = fs.Parse(os.Args[4:])

		jobs, err := k8sCliJobsList.Invoke(k8sCliJobsListRequest{JobId: "^" + regexp.QuoteMeta(jobId) + "$"})
		cli.HandleError("retrieving job", err)

		if len(jobs) == 0 {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Job not found: %s", jobId)
			os.Exit(1)
		}

		job := jobs[0]

		if *jsonOutput {
			data, err := json.MarshalIndent(job, "", "  ")
			cli.HandleError("encoding output", err)
			termio.WriteLine("%s", string(data))
			return
		}

		f := termio.Frame{}
		f.AppendTitle("Job")
		f.AppendField("ID", job.JobId)
		f.AppendField("State", job.State)
		f.AppendField("Submitted at", cli.FormatTime(job.CreatedAt))
		if job.StartedAt.Present {
			f.AppendField("Started at", cli.FormatTime(job.StartedAt.Value))
		}
		f.AppendSeparator()

		f.AppendField("Application", job.Application)
		f.AppendField("Product", job.ProductId)
		f.AppendField("Category", job.Category)
		f.AppendField("Replicas", fmt.Sprintf("%d", job.Replicas))
		f.AppendField("Queue", job.Queue)
		f.AppendField("Normalization denominator", fmt.Sprintf("%d", job.NormalizationDenominator))
		f.AppendField("Wall-time used", time.Duration(job.WallTimeUsedMillis*int64(time.Millisecond)).String())
		f.AppendField("Accounting units used", fmt.Sprintf("%d", job.AccountingUnitsUsed))
		f.AppendSeparator()

		f.AppendField("User", job.User)
		if job.Project != "" {
			f.AppendField("Project", job.Project)
		}
		f.Print()

	case command == "queue" || command == "queues":
		req := k8sCliJobsQueueRequest{IncludeJobs: true}
		fs := flag.NewFlagSet("jobs queue", flag.ExitOnError)
		fs.StringVar(&req.Queue, "queue", "", "Queue name regex")
		fs.BoolVar(&req.IncludeJobs, "jobs", true, "Include queued jobs in output")
		jsonOutput := fs.Bool("json", false, "Print JSON output")
		_ = fs.Parse(os.Args[3:])

		isDebug := false
		argOffset := 0
		if fs.NArg() > 0 {
			mode := fs.Arg(0)
			isDebug = mode == "debug" || mode == "state"
			if isDebug {
				argOffset = 1
			}
		}

		if req.Queue == "" && fs.NArg() > argOffset {
			req.Queue = "^" + regexp.QuoteMeta(fs.Arg(argOffset)) + "$"
		}

		if isDebug {
			debugResp, err := k8sCliJobsQueueDebug.Invoke(k8sCliJobsQueueDebugRequest{Queue: req.Queue})
			cli.HandleError("reading detailed queue state", err)

			if *jsonOutput {
				data, err := json.MarshalIndent(debugResp, "", "  ")
				cli.HandleError("encoding output", err)
				termio.WriteLine("%s", string(data))
				return
			}

			if len(debugResp) == 0 {
				termio.WriteLine("No scheduler data available.")
				return
			}

			for idx, scheduler := range debugResp {
				if idx > 0 {
					termio.WriteLine("")
				}

				normalizationDenominator := -1
				jobs, _ := k8sCliJobsList.Invoke(k8sCliJobsListRequest{
					Queue: scheduler.Name,
				})

				if len(jobs) > 0 {
					normalizationDenominator = jobs[0].NormalizationDenominator
				}

				frame := termio.Frame{}
				frame.AppendTitle("Queue internals")
				frame.AppendField("Queue", scheduler.Name)
				frame.AppendField("Tick", fmt.Sprintf("%d", scheduler.Time))
				frame.AppendField("Queued entries", fmt.Sprintf("%d", len(scheduler.QueueEntries)))
				frame.AppendField("Replica entries", fmt.Sprintf("%d", len(scheduler.ReplicaEntries)))
				frame.AppendField("Nodes", fmt.Sprintf("%d", len(scheduler.Nodes)))
				if normalizationDenominator > 0 {
					frame.AppendField("Normalization denominator", fmt.Sprintf("%d", normalizationDenominator))
				}
				frame.Print()

				if len(scheduler.Nodes) > 0 {
					termio.WriteLine("")
					termio.WriteStyledLine(termio.Bold, termio.White, 0, "Nodes")
					t := termio.Table{}
					t.AppendHeader("Name")
					t.AppendHeader("Unsched")
					t.AppendHeader("Last seen")
					t.AppendHeader("Remaining")
					t.AppendHeader("Capacity")
					t.AppendHeader("Limits")

					for _, node := range scheduler.Nodes {
						t.Cell("%v", node.Name)
						t.Cell("%v", node.Unschedulable)
						t.Cell("%v", node.LastSeen)
						t.Cell("%v", formatDims(node.Remaining, normalizationDenominator))
						t.Cell("%v", formatDims(node.Capacity, normalizationDenominator))
						t.Cell("%v", formatDims(node.Limits, normalizationDenominator))
					}

					t.Print()
				}

				if len(scheduler.QueueEntries) > 0 {
					termio.WriteLine("")
					termio.WriteStyledLine(termio.Bold, termio.White, 0, "Queue entries")
					t := termio.Table{}
					t.AppendHeader("Job")
					t.AppendHeader("Submitted")
					t.AppendHeader("Replicas")
					t.AppendHeader("Dims")
					t.AppendHeader("Priority")
					t.AppendHeader("Age")
					t.AppendHeader("Fair")
					t.AppendHeader("Size")

					for _, entry := range scheduler.QueueEntries {
						t.Cell("%v", entry.JobId)
						t.Cell("%v", cli.FormatTime(entry.SubmittedAt))
						t.Cell("%v", entry.Replicas)
						t.Cell("%v", formatDims(entry.SchedulerDimensions, normalizationDenominator))
						t.Cell("%.3f", entry.Priority)
						t.Cell("%.3f", entry.Factors.Age)
						t.Cell("%.3f", entry.Factors.FairShare)
						t.Cell("%.3f", entry.Factors.JobSize)
					}

					t.Print()
				}

				if len(scheduler.ReplicaEntries) > 0 {
					termio.WriteLine("")
					termio.WriteStyledLine(termio.Bold, termio.White, 0, "Replica entries")
					t := termio.Table{}
					t.AppendHeader("Job")
					t.AppendHeader("Rank")
					t.AppendHeader("Node")
					t.AppendHeader("Last seen")
					t.AppendHeader("Dims")

					for _, entry := range scheduler.ReplicaEntries {
						t.Cell("%v", entry.JobId)
						t.Cell("%v", entry.Rank)
						t.Cell("%v", entry.Node)
						t.Cell("%v", entry.LastSeen)
						t.Cell("%v", formatDims(entry.SchedulerDimensions, normalizationDenominator))
					}

					t.Print()
				}
			}
			return
		}

		queues, err := k8sCliJobsQueue.Invoke(req)
		cli.HandleError("reading queue state", err)

		if *jsonOutput {
			data, err := json.MarshalIndent(queues, "", "  ")
			cli.HandleError("encoding output", err)
			termio.WriteLine("%s", string(data))
			return
		}

		overview := termio.Table{}
		overview.AppendHeader("Queue")
		overview.AppendHeader("Queued")
		overview.AppendHeader("Running")

		for _, queue := range queues {
			overview.Cell("%v", queue.Name)
			overview.Cell("%v", queue.Queued)
			overview.Cell("%v", queue.Running)
		}

		overview.Print()

		if req.IncludeJobs {
			hasJobs := false
			for _, queue := range queues {
				if len(queue.Jobs) == 0 {
					continue
				}

				hasJobs = true
				termio.WriteLine("")
				termio.WriteStyledLine(termio.Bold, termio.White, 0, "Queued jobs: %s", queue.Name)

				qt := termio.Table{}
				qt.AppendHeader("Submitted at")
				qt.AppendHeader("UCloud ID")
				qt.AppendHeader("Application")
				qt.AppendHeader("Replicas")
				qt.AppendHeader("Owner")

				for _, job := range queue.Jobs {
					qt.Cell("%v", cli.FormatTime(job.CreatedAt))
					qt.Cell("%v", job.JobId)
					qt.Cell("%v", job.Application)
					qt.Cell("%v", job.Replicas)
					owner := job.User
					if job.Project != "" {
						owner = owner + " (" + job.Project + ")"
					}
					qt.Cell("%v", owner)
				}

				qt.Print()
			}

			if !hasJobs {
				termio.WriteLine("")
				termio.WriteLine("No queued jobs.")
			}
		}

	case command == "kill" || command == "stop" || cli.IsDeleteCommand(command):
		if len(os.Args) < 4 {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Missing parameter: one or more job ids")
			os.Exit(1)
		}

		jobIds := slices.Clone(os.Args[3:])
		handleJobOperation(jobIds, k8sCliJobsOperationStop)

	case command == "suspend":
		if len(os.Args) < 4 {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Missing parameter: one or more job ids")
			os.Exit(1)
		}

		jobIds := slices.Clone(os.Args[3:])
		handleJobOperation(jobIds, k8sCliJobsOperationSuspend)

	case command == "unsuspend" || command == "resume":
		if len(os.Args) < 4 {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Missing parameter: one or more job ids")
			os.Exit(1)
		}

		jobIds := slices.Clone(os.Args[3:])
		handleJobOperation(jobIds, k8sCliJobsOperationUnsuspend)

	default:
		writeJobsHelp()
	}
}

func writeJobsHelp() {
	f := termio.Frame{}
	f.AppendTitle("jobs help")
	f.AppendField("", "Inspect and manage active jobs in the Kubernetes integration")
	f.AppendSeparator()
	f.AppendField("ls [flags]", "List active jobs")
	f.AppendField("get <jobId>", "Show detailed information about a single job")
	f.AppendField("queue [queue]", "Show queue state and optionally queued jobs")
	f.AppendField("queue debug [queue]", "Show detailed scheduler internals")
	f.AppendField("kill|stop|rm <jobId...>", "Stop jobs (VMs are suspended, containers are terminated)")
	f.AppendField("suspend <jobId...>", "Suspend one or more virtual machine jobs")
	f.AppendField("unsuspend|resume <jobId...>", "Unsuspend one or more virtual machine jobs")
	f.AppendSeparator()
	f.AppendField("Examples", "ucloud jobs ls --state IN_QUEUE")
	f.AppendField("", "ucloud jobs get j-abc123")
	f.AppendField("", "ucloud jobs queue")
	f.AppendField("", "ucloud jobs queue k80/k80")
	f.AppendField("", "ucloud jobs queue debug")
	f.AppendField("", "ucloud jobs stop j-abc123")
	f.AppendField("", "ucloud jobs suspend j-vm123")
	f.AppendField("", "ucloud jobs resume j-vm123")
	f.Print()
}

func handleJobOperation(jobIds []string, operation k8sCliJobsOperation) {
	response, err := k8sCliJobsOperate.Invoke(k8sCliJobsOperateRequest{JobIds: jobIds, Operation: operation})
	cli.HandleError(string(operation)+" jobs", err)

	succeeded := make([]string, 0, len(response.Successful))
	for id := range response.Successful {
		succeeded = append(succeeded, id)
	}
	slices.Sort(succeeded)

	for _, id := range succeeded {
		verb := response.Successful[id]
		if verb != "" {
			verb = strings.ToUpper(verb[:1]) + verb[1:]
		}
		termio.WriteStyledLine(termio.Bold, termio.Green, 0, "%s: %s", verb, id)
	}

	for _, id := range response.NotFound {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Not found: %s", id)
	}

	if len(response.Failed) > 0 {
		failedIds := make([]string, 0, len(response.Failed))
		for id := range response.Failed {
			failedIds = append(failedIds, id)
		}
		slices.Sort(failedIds)
		for _, id := range failedIds {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Failed: %s (%s)", id, response.Failed[id])
		}
	}

	if len(response.NotFound) > 0 || len(response.Failed) > 0 {
		os.Exit(1)
	}
}

func initJobsCli() {
	k8sCliJobsList.Handler(func(r *ipc.Request[k8sCliJobsListRequest]) ipc.Response[[]k8sCliJob] {
		if r.Uid != 0 {
			return ipc.Response[[]k8sCliJob]{
				StatusCode: http.StatusForbidden,
			}
		}

		var (
			stateRegex,
			jobIdRegex,
			userRegex,
			projectRegex,
			applicationRegex,
			categoryRegex,
			queueRegex *regexp.Regexp
		)

		err := cli.ValidateRegexes(
			&stateRegex, "state", r.Payload.State,
			&jobIdRegex, "job-id", r.Payload.JobId,
			&userRegex, "user", r.Payload.User,
			&projectRegex, "project", r.Payload.Project,
			&applicationRegex, "application", r.Payload.Application,
			&categoryRegex, "category", r.Payload.Category,
			&queueRegex, "queue", r.Payload.Queue,
		)
		if err != nil {
			return ipc.Response[[]k8sCliJob]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: err.Error(),
			}
		}

		jobs := controller.JobsListServer()
		for i, job := range jobs {
			copied := *job
			for _, m := range shared.Machines {
				if m.Name == copied.Specification.Product.Id && m.Category.Name == copied.Specification.Product.Category {
					copied.Status.ResolvedProduct.Set(m)
					break
				}
			}
			jobs[i] = &copied
		}
		result := make([]k8sCliJob, 0, len(jobs))

		for _, job := range jobs {
			entry := k8sCliJobFromJob(job)
			if !matchesRegex(jobIdRegex, entry.JobId) ||
				!matchesRegex(stateRegex, entry.State) ||
				!matchesRegex(userRegex, entry.User) ||
				!matchesRegex(projectRegex, entry.Project) ||
				!matchesRegex(applicationRegex, entry.Application) ||
				!matchesRegex(categoryRegex, entry.Category) ||
				!matchesRegex(queueRegex, entry.Queue) {
				continue
			}

			result = append(result, entry)
		}

		slices.SortFunc(result, func(a, b k8sCliJob) int {
			at := time.Time(a.CreatedAt)
			bt := time.Time(b.CreatedAt)
			if at.After(bt) {
				return -1
			} else if at.Before(bt) {
				return 1
			}

			if a.JobId < b.JobId {
				return -1
			} else if a.JobId > b.JobId {
				return 1
			}
			return 0
		})

		return ipc.Response[[]k8sCliJob]{
			StatusCode: http.StatusOK,
			Payload:    result,
		}
	})

	k8sCliJobsQueue.Handler(func(r *ipc.Request[k8sCliJobsQueueRequest]) ipc.Response[[]k8sCliQueue] {
		if r.Uid != 0 {
			return ipc.Response[[]k8sCliQueue]{
				StatusCode: http.StatusForbidden,
			}
		}

		var queueRegex *regexp.Regexp
		err := cli.ValidateRegexes(&queueRegex, "queue", r.Payload.Queue)
		if err != nil {
			return ipc.Response[[]k8sCliQueue]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: err.Error(),
			}
		}

		queueMap := map[string]*k8sCliQueue{}

		ensureQueue := func(name string) *k8sCliQueue {
			existing := queueMap[name]
			if existing != nil {
				return existing
			}

			elem := &k8sCliQueue{Name: name}
			queueMap[name] = elem
			return elem
		}

		for categoryName, machineCategory := range shared.ServiceConfig.Compute.Machines {
			for _, group := range machineCategory.Groups {
				queueName, ok := schedulerName(categoryName, group.GroupName)
				if !ok {
					continue
				}
				ensureQueue(queueName)
			}
		}

		for categoryName := range controller.IntegratedApplications {
			queueName, ok := schedulerName(categoryName, categoryName)
			if !ok {
				continue
			}
			ensureQueue(queueName)
		}

		for _, job := range controller.JobsListServer() {
			entry := k8sCliJobFromJob(job)
			queueName := entry.Queue
			if queueName == "" {
				continue
			}

			queue := ensureQueue(queueName)
			switch job.Status.State {
			case orc.JobStateInQueue:
				queue.Queued++
				if r.Payload.IncludeJobs {
					queue.Jobs = append(queue.Jobs, entry)
				}
			case orc.JobStateRunning, orc.JobStateSuspended:
				queue.Running++
			}
		}

		statusPtr := schedulerStatus.Load()
		if statusPtr != nil {
			statuses := *statusPtr
			for _, product := range shared.Machines {
				_, group, _, ok := shared.ServiceConfig.Compute.ResolveMachine(product.Name, product.Category.Name)
				if !ok {
					continue
				}

				queueName, ok := schedulerName(product.Category.Name, group.GroupName)
				if !ok {
					continue
				}

				queue := ensureQueue(queueName)
				status := "unknown"
				if reported, found := statuses[product.ToReference()]; found && reported.Present {
					status = string(reported.Value)
				}

				queue.ProductStatuses = append(queue.ProductStatuses, k8sCliQueueProductStatus{
					ProductId: product.Name,
					Category:  product.Category.Name,
					Status:    status,
				})
			}
		}

		var result []k8sCliQueue
		for _, queue := range queueMap {
			if !matchesRegex(queueRegex, queue.Name) {
				continue
			}

			slices.SortFunc(queue.Jobs, func(a, b k8sCliJob) int {
				at := time.Time(a.CreatedAt)
				bt := time.Time(b.CreatedAt)
				if at.Before(bt) {
					return -1
				} else if at.After(bt) {
					return 1
				}
				return strings.Compare(a.JobId, b.JobId)
			})

			slices.SortFunc(queue.ProductStatuses, func(a, b k8sCliQueueProductStatus) int {
				if a.ProductId < b.ProductId {
					return -1
				} else if a.ProductId > b.ProductId {
					return 1
				}
				return 0
			})

			result = append(result, *queue)
		}

		slices.SortFunc(result, func(a, b k8sCliQueue) int {
			if a.Name < b.Name {
				return -1
			} else if a.Name > b.Name {
				return 1
			}
			return 0
		})

		return ipc.Response[[]k8sCliQueue]{
			StatusCode: http.StatusOK,
			Payload:    result,
		}
	})

	k8sCliJobsQueueDebug.Handler(func(r *ipc.Request[k8sCliJobsQueueDebugRequest]) ipc.Response[[]k8sCliQueueDebugScheduler] {
		if r.Uid != 0 {
			return ipc.Response[[]k8sCliQueueDebugScheduler]{
				StatusCode: http.StatusForbidden,
			}
		}

		var queueRegex *regexp.Regexp
		err := cli.ValidateRegexes(&queueRegex, "queue", r.Payload.Queue)
		if err != nil {
			return ipc.Response[[]k8sCliQueueDebugScheduler]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: err.Error(),
			}
		}

		snapshots, err := snapshotSchedulerDebugState(4 * time.Second)
		if err != nil {
			return ipc.Response[[]k8sCliQueueDebugScheduler]{
				StatusCode:   http.StatusInternalServerError,
				ErrorMessage: err.Error(),
			}
		}

		result := make([]k8sCliQueueDebugScheduler, 0, len(snapshots))
		for _, snapshot := range snapshots {
			if !matchesRegex(queueRegex, snapshot.Name) {
				continue
			}
			result = append(result, snapshot)
		}

		slices.SortFunc(result, func(a, b k8sCliQueueDebugScheduler) int {
			if a.Name < b.Name {
				return -1
			} else if a.Name > b.Name {
				return 1
			}
			return 0
		})

		return ipc.Response[[]k8sCliQueueDebugScheduler]{
			StatusCode: http.StatusOK,
			Payload:    result,
		}
	})

	k8sCliJobsOperate.Handler(func(r *ipc.Request[k8sCliJobsOperateRequest]) ipc.Response[k8sCliJobsOperateResponse] {
		if r.Uid != 0 {
			return ipc.Response[k8sCliJobsOperateResponse]{
				StatusCode: http.StatusForbidden,
			}
		}

		if !r.Payload.Operation.Valid() {
			return ipc.Response[k8sCliJobsOperateResponse]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: "unsupported operation",
			}
		}

		response := k8sCliJobsOperateResponse{
			Successful: map[string]string{},
			Failed:     map[string]string{},
		}

		for _, id := range r.Payload.JobIds {
			job, ok := controller.JobRetrieve(id)
			if !ok {
				response.NotFound = append(response.NotFound, id)
				continue
			}

			verb, err := k8sCliApplyJobOperation(job, r.Payload.Operation)
			if err != nil {
				response.Failed[id] = err.Why
				continue
			}

			response.Successful[id] = verb
		}

		slices.Sort(response.NotFound)

		return ipc.Response[k8sCliJobsOperateResponse]{
			StatusCode: http.StatusOK,
			Payload:    response,
		}
	})
}

func k8sCliApplyJobOperation(job *orc.Job, operation k8sCliJobsOperation) (string, *util.HttpError) {
	switch operation {
	case k8sCliJobsOperationStop:
		if backendIsKubevirt(job) {
			err := suspend(*job)
			if err != nil {
				return "", err
			}
			return "suspended", nil
		}

		err := terminate(controller.JobTerminateRequest{Job: job, IsCleanup: false})
		if err != nil {
			return "", err
		}
		return "stopped", nil

	case k8sCliJobsOperationSuspend:
		if !backendIsKubevirt(job) {
			return "", util.UserHttpError("only virtual machine jobs can be suspended")
		}

		err := suspend(*job)
		if err != nil {
			return "", err
		}
		return "suspended", nil

	case k8sCliJobsOperationUnsuspend:
		if !backendIsKubevirt(job) {
			return "", util.UserHttpError("only virtual machine jobs can be unsuspended")
		}

		err := unsuspend(*job)
		if err != nil {
			return "", err
		}
		return "unsuspended", nil

	default:
		return "", util.UserHttpError("unsupported operation")
	}
}

func matchesRegex(expr *regexp.Regexp, value string) bool {
	if expr == nil {
		return true
	}
	return expr.MatchString(value)
}

func formatDims(dims shared.SchedulerDimensions, multiplier int) string {
	cpu := dims.CpuMillis
	gpuString := fmt.Sprint(dims.Gpu)
	if dims.Gpu > 0 {
		gpuString = fmt.Sprintf("%d / %d", dims.Gpu, multiplier)
	} else {
		cpu /= multiplier
	}

	return fmt.Sprintf(
		"cpu=%dm, mem=%s, gpu=%s",
		cpu,
		formatBytes(dims.MemoryInBytes),
		gpuString,
	)
}

func formatBytes(value int) string {
	if value < 0 {
		return "0 B"
	}
	if value >= 1000*1000*1000 {
		return fmt.Sprintf("%.2f GB", float64(value)/1000.0/1000.0/1000.0)
	}
	if value >= 1000*1000 {
		return fmt.Sprintf("%.2f MB", float64(value)/1000.0/1000.0)
	}
	if value >= 1000 {
		return fmt.Sprintf("%.2f KB", float64(value)/1000.0)
	}
	return fmt.Sprintf("%d B", value)
}

func snapshotSchedulerDebugState(timeout time.Duration) ([]k8sCliQueueDebugScheduler, error) {
	paths, err := filepath.Glob("/tmp/scheduler-*.json")
	if err == nil {
		for _, p := range paths {
			_ = os.Remove(p)
		}
	}

	err = os.WriteFile("/tmp/scheduler-dump-requested", []byte("1"), 0600)
	if err != nil {
		return nil, fmt.Errorf("failed to request scheduler dump: %w", err)
	}

	deadline := time.Now().Add(timeout)
	for {
		paths, err = filepath.Glob("/tmp/scheduler-*.json")
		if err == nil && len(paths) > 0 {
			break
		}

		if time.Now().After(deadline) {
			return nil, fmt.Errorf("timed out waiting for scheduler state dump")
		}

		time.Sleep(100 * time.Millisecond)
	}

	type schedulerDump struct {
		Name     string
		Queue    []SchedulerQueueEntry
		Replicas []SchedulerReplicaEntry
		Nodes    map[string]*SchedulerNode
		Time     int
	}

	result := make([]k8sCliQueueDebugScheduler, 0, len(paths))
	for _, path := range paths {
		data, err := os.ReadFile(path)
		if err != nil {
			continue
		}

		var dump schedulerDump
		err = json.Unmarshal(data, &dump)
		if err != nil {
			continue
		}

		nodes := make([]SchedulerNode, 0, len(dump.Nodes))
		for _, node := range dump.Nodes {
			if node == nil {
				continue
			}
			nodes = append(nodes, *node)
		}

		slices.SortFunc(nodes, func(a, b SchedulerNode) int {
			if a.Name < b.Name {
				return -1
			} else if a.Name > b.Name {
				return 1
			}
			return 0
		})

		slices.SortFunc(dump.Queue, func(a, b SchedulerQueueEntry) int {
			if a.JobId < b.JobId {
				return -1
			} else if a.JobId > b.JobId {
				return 1
			}
			return 0
		})

		slices.SortFunc(dump.Replicas, func(a, b SchedulerReplicaEntry) int {
			if a.JobId < b.JobId {
				return -1
			} else if a.JobId > b.JobId {
				return 1
			}

			if a.Rank < b.Rank {
				return -1
			} else if a.Rank > b.Rank {
				return 1
			}

			if a.Node < b.Node {
				return -1
			} else if a.Node > b.Node {
				return 1
			}
			return 0
		})

		result = append(result, k8sCliQueueDebugScheduler{
			Name:           dump.Name,
			Time:           dump.Time,
			QueueEntries:   dump.Queue,
			ReplicaEntries: dump.Replicas,
			Nodes:          nodes,
		})
	}

	return result, nil
}

func k8sCliJobFromJob(job *orc.Job) k8sCliJob {
	runningTime := shared.ComputeRunningTime(job)
	accountingUnitsUsed := int64(0)
	normalizationDenominator := shared.NormalizationDenominatorForCategory(job.Specification.Product.Category)
	if job.Status.ResolvedProduct.Present {
		accountingUnitsUsed = convertJobTimeToAccountingUnits(job, runningTime.TimeConsumed)
		normalizationDenominator = shared.NormalizationDenominatorForCategory(job.Status.ResolvedProduct.Value.Category.Name)
	}

	result := k8sCliJob{
		JobId:                    job.Id,
		CreatedAt:                job.CreatedAt,
		State:                    string(job.Status.State),
		User:                     job.Owner.CreatedBy,
		Project:                  job.Owner.Project.Value,
		Application:              job.Specification.Application.Name,
		Category:                 job.Specification.Product.Category,
		ProductId:                job.Specification.Product.Id,
		Replicas:                 job.Specification.Replicas,
		WallTimeUsedMillis:       runningTime.TimeConsumed.Milliseconds(),
		AccountingUnitsUsed:      accountingUnitsUsed,
		NormalizationDenominator: normalizationDenominator,
	}

	if job.Status.StartedAt.IsSet() {
		result.StartedAt = util.OptValue(job.Status.StartedAt.Value)
	}

	queueName, _ := schedulerNameFromJob(job)
	result.Queue = queueName

	return result
}

func schedulerNameFromJob(job *orc.Job) (string, bool) {
	product := job.Specification.Product
	_, isIApp := controller.IntegratedApplications[product.Category]
	if isIApp {
		return schedulerName(product.Category, product.Category)
	}

	_, group, _, ok := shared.ServiceConfig.Compute.ResolveMachine(product.Id, product.Category)
	if !ok {
		return "", false
	}

	return schedulerName(product.Category, group.GroupName)
}

func schedulerName(category string, group string) (string, bool) {
	mapped, ok := shared.ServiceConfig.Compute.MachineImpersonation[category]
	if !ok {
		mapped = category
	}

	_, isIApp := controller.IntegratedApplications[mapped]
	if !isIApp {
		if _, ok := shared.ServiceConfig.Compute.Machines[mapped]; !ok {
			return "", false
		}
	}

	return mapped, true
}

type k8sCliJobsListRequest struct {
	State       string
	JobId       string
	User        string
	Project     string
	Application string
	Category    string
	Queue       string
}

type k8sCliJob struct {
	JobId                    string
	CreatedAt                fnd.Timestamp
	StartedAt                util.Option[fnd.Timestamp]
	State                    string
	User                     string
	Project                  string
	Application              string
	Category                 string
	ProductId                string
	Replicas                 int
	Queue                    string
	WallTimeUsedMillis       int64
	AccountingUnitsUsed      int64
	NormalizationDenominator int
}

type k8sCliJobsQueueRequest struct {
	Queue       string
	IncludeJobs bool
}

type k8sCliQueue struct {
	Name            string
	Queued          int
	Running         int
	Jobs            []k8sCliJob
	ProductStatuses []k8sCliQueueProductStatus
}

type k8sCliQueueProductStatus struct {
	ProductId string
	Category  string
	Status    string
}

type k8sCliJobsQueueDebugRequest struct {
	Queue string
}

type k8sCliQueueDebugScheduler struct {
	Name           string
	Time           int
	QueueEntries   []SchedulerQueueEntry
	ReplicaEntries []SchedulerReplicaEntry
	Nodes          []SchedulerNode
}

type k8sCliJobsOperateRequest struct {
	JobIds    []string
	Operation k8sCliJobsOperation
}

type k8sCliJobsOperateResponse struct {
	Successful map[string]string
	NotFound   []string
	Failed     map[string]string
}

type k8sCliJobsOperation string

const (
	k8sCliJobsOperationStop      k8sCliJobsOperation = "stop"
	k8sCliJobsOperationSuspend   k8sCliJobsOperation = "suspend"
	k8sCliJobsOperationUnsuspend k8sCliJobsOperation = "unsuspend"
)

func (o k8sCliJobsOperation) Valid() bool {
	switch o {
	case k8sCliJobsOperationStop, k8sCliJobsOperationSuspend, k8sCliJobsOperationUnsuspend:
		return true
	default:
		return false
	}
}

var (
	k8sCliJobsList       = ipc.NewCall[k8sCliJobsListRequest, []k8sCliJob]("cli.k8s.jobs.list")
	k8sCliJobsQueue      = ipc.NewCall[k8sCliJobsQueueRequest, []k8sCliQueue]("cli.k8s.jobs.queue")
	k8sCliJobsQueueDebug = ipc.NewCall[k8sCliJobsQueueDebugRequest, []k8sCliQueueDebugScheduler]("cli.k8s.jobs.queue.debug")
	k8sCliJobsOperate    = ipc.NewCall[k8sCliJobsOperateRequest, k8sCliJobsOperateResponse]("cli.k8s.jobs.operate")
)
