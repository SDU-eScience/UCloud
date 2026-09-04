package command

import (
	"encoding/json"
	"fmt"
	"sort"
	"strconv"
	"strings"

	apm "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/cli"
	fnd "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/util"
	"ucloud.dk/ucloud_cli/pkg/shared"
)

type JobGetCommand struct {
	JobID  string `positional:"job-id" usage:"Job ID"`
	Output string `flag:"output" usage:"Output format: table or json"`
}

type JobListCommand struct {
	Workspace string `flag:"workspace" usage:"Workspace to list jobs for"`
	State     string `flag:"state" usage:"Job state"`
	App       string `flag:"app" usage:"Application name"`
	Provider  string `flag:"provider" usage:"Provider name"`
}
type JobCreateCommand struct {
	App        string            `flag:"app" usage:"Application name"`
	Product    string            `flag:"product" usage:"Product name"`
	Name       string            `flag:"name" usage:"Job name"`
	Time       int               `flag:"time" usage:"Time in minutes"`
	SSH        bool              `flag:"ssh" usage:"Use SSH"`
	Folder     string            `flag:"folder" usage:"Folder path to mount, e.g. /19/mysubdrive/"`
	PublicLink string            `flag:"public-link" usage:"Public link"`
	Workspace  string            `flag:"workspace" usage:"Workspace to create the job in"`
	Parameters map[string]string `flag:"param" usage:"eg. image=ubuntu"`
}

type JobDeleteCommand struct {
	JobID string `positional:"job-id" usage:"Job ID"`
}

type JobRenameCommand struct {
	JobID   string `positional:"job-id" usage:"Job ID"`
	NewName string `positional:"new-name" usage:"New job name"`
}

type JobSearchCommand struct {
	JobName string `positional:"job-name" usage:"Job name"`
}

type JobExtendCommand struct {
	JobID string `positional:"job-id" usage:"Job ID"`
	Time  int    `flag:"time" usage:"Time in minutes"`
}

type JobResumeCommand struct {
	JobID string `positional:"job-id" usage:"Job ID"`
}

type JobTerminateCommand struct {
	JobID string `positional:"job-id" usage:"Job ID"`
}

type JobSuspendCommand struct {
	JobID string `positional:"job-id" usage:"Job ID"`
}

type JobLogsCommand struct {
	JobID string `positional:"job-id" usage:"Job ID"`
}

type JobShellCommand struct {
	JobID string `positional:"job-id" usage:"Job ID"`
	Rank  int    `flag:"rank" usage:"Rank"`
}

type JobWebCommand struct {
	JobID string `positional:"job-id" usage:"Job ID"`
}

type JobVNCCommand struct {
	JobID string `positional:"job-id" usage:"Job ID"`
}

type JobOpenCommand struct {
	JobID string `positional:"job-id" usage:"Job ID"`
}

type JobAttachCommand struct {
	JobID          string `positional:"job-id" usage:"Job ID"`
	PublicIp       string `flag:"public-ip" usage:"Public IP"`
	PublicLink     string `flag:"public-link" usage:"Public link"`
	PrivateNetwork string `flag:"private-network" usage:"Private network"`
}

type JobDetachCommand struct {
	JobID    string `positional:"job-id" usage:"Job ID"`
	PublicIp string `flag:"public-ip" usage:"Public IP"`
}

var JobCommands = map[string]CommandFunc{
	"rename":    func() Command { return &JobRenameCommand{} },
	"search":    func() Command { return &JobSearchCommand{} },
	"suspend":   func() Command { return &JobSuspendCommand{} },
	"extend":    func() Command { return &JobExtendCommand{} },
	"list":      func() Command { return &JobListCommand{} },
	"get":       func() Command { return &JobGetCommand{} },
	"create":    func() Command { return &JobCreateCommand{} },
	"delete":    func() Command { return &JobDeleteCommand{} },
	"terminate": func() Command { return &JobTerminateCommand{} },
	"resume":    func() Command { return &JobResumeCommand{} },
	// Attach and detach
	"attach": func() Command { return &JobAttachCommand{} },
	"detach": func() Command { return &JobDetachCommand{} },
	// Interactive commands
	"vnc":   func() Command { return &JobVNCCommand{} },
	"open":  func() Command { return &JobOpenCommand{} },
	"web":   func() Command { return &JobWebCommand{} },
	"shell": func() Command { return &JobShellCommand{} },
	"logs":  func() Command { return &JobLogsCommand{} },
}

func retrieveJobs() (map[string]orcapi.Job, error) {
	result, httpErr := orcapi.JobsBrowse.Invoke(orcapi.JobsBrowseRequest{})
	if httpErr.AsError() != nil {
		return map[string]orcapi.Job{}, fmt.Errorf("failed to list jobs: %s", httpErr.Why)
	}
	jobs := make(map[string]orcapi.Job)
	for _, job := range result.Items {
		repoName := job.Specification.Name
		jobs[repoName] = job

	}
	return jobs, nil
}

func printJobs(workspace string, jobs map[string]orcapi.Job) {
	fmt.Printf("Jobs of %s:\n", workspace)
	t := termio.Table{}
	t.AppendHeader("Provider")
	t.AppendHeader("Application")
	t.AppendHeader("JobId")
	t.AppendHeader("JobName")
	t.AppendHeader("JobStatus")
	t.AppendHeader("Owner")
	t.AppendHeader("JobStartTime")

	for _, job := range jobs {
		t.Cell("%v", job.Specification.Product.Provider)
		t.Cell("%v", job.Specification.Application.Name)
		t.Cell(job.Id)
		t.Cell("%v", job.Specification.Name)
		t.Cell("%v", job.Status.State)
		t.Cell("%v", job.Owner.Project.GetOrDefault(""))
		t.Cell("%v", cli.FormatTime(job.CreatedAt))
	}
	t.Print()
}

func (c JobRenameCommand) Execute() error {
	cfg, err := shared.ReadConfig()
	if err != nil {
		panic(err)
	}
	cfg.InitUCloudClient()

	if c.JobID == "" {
		return fmt.Errorf("this command requires a job id, use: ucloud job rename <job-id> <new-name>")
	}
	if c.NewName == "" {
		return fmt.Errorf("this command requires a new name, use: ucloud job rename <job-id> <new-name>")
	}

	_, httpErr := orcapi.JobsRename.Invoke(fnd.BulkRequestOf(orcapi.JobRenameRequest{
		Id:       c.JobID,
		NewTitle: c.NewName,
	}))
	if httpErr != nil {
		return fmt.Errorf("failed to rename job: %s", httpErr.Why)
	}

	fmt.Printf("Job renamed: %s -> %s\n", c.JobID, c.NewName)
	return nil
}

func (c JobSearchCommand) Execute() error {
	cfg, err := shared.ReadConfig()
	if err != nil {
		panic(err)
	}
	cfg.InitUCloudClient()

	if c.JobName == "" {
		return fmt.Errorf("this command requires a job name, use: ucloud job search <job-name>")
	}

	result, httpErr := orcapi.JobsSearch.Invoke(orcapi.JobsSearchRequest{
		Query: c.JobName,
	})
	if httpErr != nil {
		return fmt.Errorf("failed to search for jobs: %s", httpErr.Why)
	}

	jobs := make(map[string]orcapi.Job)
	for _, job := range result.Items {
		jobs[job.Id] = job
	}
	printJobs(c.JobName, jobs)
	return nil
}

func (c JobSuspendCommand) Execute() error {
	cfg, err := shared.ReadConfig()
	if err != nil {
		panic(err)
	}
	cfg.InitUCloudClient()

	if c.JobID == "" {
		return fmt.Errorf("this command requires a job id, use: ucloud job suspend <job-id>")
	}

	_, httpErr := orcapi.JobsSuspend.Invoke(fnd.BulkRequestOf(fnd.FindByStringId{
		Id: c.JobID,
	}))
	if httpErr != nil {
		return fmt.Errorf("failed to suspend job: %s", httpErr.Why)
	}

	fmt.Printf("Job suspended: %s\n", c.JobID)
	return nil
}

func (c JobExtendCommand) Execute() error {
	cfg, err := shared.ReadConfig()
	if err != nil {
		panic(err)
	}
	cfg.InitUCloudClient()

	if c.JobID == "" {
		return fmt.Errorf("this command requires a job id, use: ucloud job extend <job-id> --time <minutes>")
	}
	if c.Time <= 0 {
		return fmt.Errorf("this command requires a positive time in minutes, use: ucloud job extend <job-id> --time <minutes>")
	}

	_, httpErr := orcapi.JobsExtend.Invoke(fnd.BulkRequestOf(orcapi.JobsExtendRequestItem{
		JobId: c.JobID,
		RequestedTime: orcapi.SimpleDuration{
			Hours:   c.Time / 60,
			Minutes: c.Time % 60,
		},
	}))
	if httpErr != nil {
		return fmt.Errorf("failed to extend job: %s", httpErr.Why)
	}

	fmt.Printf("Job extended: %s by %d minutes\n", c.JobID, c.Time)
	return nil
}

func (c JobGetCommand) Execute() error {
	cfg, err := shared.ReadConfig()
	if err != nil {
		panic(err)
	}
	cfg.InitUCloudClient()

	if c.JobID == "" {
		return fmt.Errorf("this command requires a job id, use: ucloud job get <job-id>")
	}

	job, httpErr := orcapi.JobsRetrieve.Invoke(orcapi.JobsRetrieveRequest{
		Id: c.JobID,
	})
	if httpErr != nil {
		return fmt.Errorf("failed to retrieve job: %s", httpErr.Why)
	}

	switch c.Output {
	case "", "table":
		printJobDetails(job)
	case "json":
		data, err := json.MarshalIndent(job, "", "  ")
		if err != nil {
			return fmt.Errorf("failed to encode job as json: %s", err)
		}
		fmt.Println(string(data))
	default:
		return fmt.Errorf("unknown output format %q, expected table or json", c.Output)
	}

	return nil
}

func printJobDetails(job orcapi.Job) {
	t := termio.Table{}
	t.AppendHeader("Field")
	t.AppendHeader("Value")
	t.Cell("JobId")
	t.Cell(job.Id)
	t.Cell("JobName")
	t.Cell("%v", job.Specification.Name)
	t.Cell("Application")
	t.Cell("%v", job.Specification.Application.Name)
	t.Cell("Version")
	t.Cell("%v", job.Specification.Application.Version)
	t.Cell("Provider")
	t.Cell("%v", job.Specification.Product.Provider)
	t.Cell("Product")
	t.Cell("%v", job.Specification.Product.Id)
	t.Cell("ProductCategory")
	t.Cell("%v", job.Specification.Product.Category)
	t.Cell("State")
	t.Cell("%v", job.Status.State)
	t.Cell("Owner")
	t.Cell("%v", job.Owner.Project.GetOrDefault(""))
	t.Cell("CreatedAt")
	t.Cell("%v", cli.FormatTime(job.CreatedAt))
	t.Print()
}

func filterByState(jobs map[string]orcapi.Job, state string) map[string]orcapi.Job {
	if state == "" {
		return jobs
	}
	filteredJobs := make(map[string]orcapi.Job)
	for id, job := range jobs {
		if string(job.Status.State) == state {
			filteredJobs[id] = job
		}
	}
	return filteredJobs
}

func filterByApp(jobs map[string]orcapi.Job, app string) map[string]orcapi.Job {
	if app == "" {
		return jobs
	}
	filteredJobs := make(map[string]orcapi.Job)
	for id, job := range jobs {
		if job.Specification.Name == app {
			filteredJobs[id] = job
		}
	}
	return filteredJobs
}
func filterByProvider(jobs map[string]orcapi.Job, provider string) map[string]orcapi.Job {
	if provider == "" {
		return jobs
	}
	filteredJobs := make(map[string]orcapi.Job)
	for id, job := range jobs {
		if job.Specification.Product.Provider == provider {
			filteredJobs[id] = job
		}
	}
	return filteredJobs
}

func (c JobListCommand) Execute() error {
	cfg, err := shared.ReadConfig()
	if err != nil {
		panic(err)
	}
	cfg.InitUCloudClient()
	currentWs := c.Workspace
	if c.Workspace == "" && cfg.CurrentWorkspace.IsEmpty() {
		return fmt.Errorf("this command requires a workspace, either by specifying a workspace with --workspace or ucloud workspace use <workspace>")
	}

	if cfg.CurrentWorkspace.Present {
		currentWs = cfg.CurrentWorkspace.Value
	}

	ws, err := findWorkspace(currentWs)
	if err != nil {
		return err
	}
	shared.SetActiveWorkspace(ws.Id)

	jobs, err := retrieveJobs()
	jobs = filterByState(jobs, c.State)
	jobs = filterByApp(jobs, c.App)
	jobs = filterByProvider(jobs, c.Provider)
	if err != nil {
		return err
	}
	printJobs(currentWs, jobs)
	return nil
}

// looks up an application by its name (e.g. "terminal-ubuntu").
// The name may include a version suffix (e.g. "terminal-ubuntu:1.0.1"); when
// omitted the newest version of the application is returned.
func findApp(appName string) (*orcapi.Application, error) {
	if appName == "" {
		return nil, fmt.Errorf("no application specified, use --app <name>")
	}

	request := orcapi.AppCatalogFindByNameAndVersionRequest{
		AppName: appName,
	}
	if name, version, found := strings.Cut(appName, ":"); found {
		if name == "" || version == "" {
			return nil, fmt.Errorf("invalid application %q, expected <name> or <name>:<version>", appName)
		}
		request.AppName = name
		request.AppVersion = util.OptValue(version)
	}

	app, httpErr := orcapi.AppsFindByNameAndVersion.Invoke(request)
	if httpErr != nil {
		if request.AppVersion.Present {
			return nil, appVersionNotFoundError(request.AppName, request.AppVersion.Value, httpErr.Why)
		}
		return nil, fmt.Errorf("no application found with name %s: %s", appName, httpErr.Why)
	}
	return &app, nil
}

// Builds an error for a failed name+version lookup.
// When possible it lists the versions that actually exist for the application
// so the user can immediately correct the command.
func appVersionNotFoundError(name, version, why string) error {
	group, groupErr := orcapi.AppsFindGroupByApplication.Invoke(orcapi.AppCatalogFindGroupByApplicationRequest{
		AppName: name,
	})
	if groupErr != nil || len(group.Status.Applications) == 0 {
		return fmt.Errorf("no application found with name %s and version %s: %s", name, version, why)
	}

	versions := make([]string, 0, len(group.Status.Applications))
	seen := make(map[string]bool, len(group.Status.Applications))
	for _, a := range group.Status.Applications {
		if seen[a.Metadata.Version] {
			continue
		}
		seen[a.Metadata.Version] = true
		versions = append(versions, a.Metadata.Version)
	}
	sort.Strings(versions)

	return fmt.Errorf(
		"application %s has no version %s. Available versions: %s",
		name, version, strings.Join(versions, ", "),
	)
}

// Picks a compute product capable of running the given application.
// Only products in categories the active workspace has allocations for are considered.
// productName may be given as "name" or "provider/name"; when empty the first
// supported product is used.
func findProductForApp(app *orcapi.Application, productName string) (apm.ProductReference, error) {
	wantProvider, wantName := "", ""
	if productName != "" {
		p, n, found := strings.Cut(productName, "/")
		if found && (p == "" || n == "") {
			return apm.ProductReference{}, fmt.Errorf("invalid product %q, expected <name> or <provider>/<name>", productName)
		}
		wantProvider, wantName = p, n
	}

	support, httpErr := orcapi.JobsRetrieveProducts.Invoke(util.Empty{})
	if httpErr != nil {
		return apm.ProductReference{}, fmt.Errorf("failed to retrieve products: %s", httpErr.Why)
	}

	// Categories the active workspace has allocations for.
	wallets, httpErr := apm.WalletsBrowse.Invoke(apm.WalletsBrowseRequest{})
	if httpErr != nil {
		return apm.ProductReference{}, fmt.Errorf("failed to retrieve wallets: %s", httpErr.Why)
	}
	paidCategories := make(map[string]bool)
	for _, wallet := range wallets.Items {
		if wallet.HasAllocations() {
			paidCategories[fmt.Sprintf("%s/%s", wallet.PaysFor.Provider, wallet.PaysFor.Name)] = true
		}
	}

	backend := app.Invocation.Tool.Tool.Value.Description.Backend

	var fallback apm.ProductReference
	found := false
	for _, products := range support.ProductsByProvider {
		for _, resolved := range products {
			if !productSupportsBackend(resolved.Support, backend) {
				continue
			}
			if !paidCategories[fmt.Sprintf("%s/%s", resolved.Product.Category.Provider, resolved.Product.Category.Name)] {
				continue
			}
			if wantName != "" {
				if resolved.Product.Name != wantName {
					continue
				}
				if wantProvider != "" && resolved.Product.Category.Provider != wantProvider {
					continue
				}
			}
			ref := resolved.Product.ToReference()
			if wantName != "" {
				return ref, nil
			}
			if !found {
				fallback = ref
				found = true
			}
		}
	}

	if wantName != "" {
		return apm.ProductReference{}, fmt.Errorf("product %s is not available for application %s", productName, app.Metadata.Name)
	}
	if !found {
		return apm.ProductReference{}, fmt.Errorf("no products available to run application %s", app.Metadata.Name)
	}
	return fallback, nil
}

func productSupportsBackend(support orcapi.JobSupport, backend orcapi.ToolBackend) bool {
	switch backend {
	case orcapi.ToolBackendDocker:
		return support.Docker.Enabled
	case orcapi.ToolBackendVirtualMachine:
		return support.VirtualMachine.Enabled
	case orcapi.ToolBackendNative:
		return support.Native.Enabled
	default:
		return false
	}
}

func buildParameters(app *orcapi.Application, userParams map[string]string) (map[string]orcapi.AppParameterValue, error) {
	if len(userParams) == 0 {
		return map[string]orcapi.AppParameterValue{}, nil
	}

	declared := make(map[string]orcapi.ApplicationParameter)
	for _, p := range app.Invocation.Parameters {
		declared[p.Name] = p
	}

	result := make(map[string]orcapi.AppParameterValue, len(userParams))
	for key, rawValue := range userParams {
		param, ok := declared[key]
		if !ok {
			return nil, fmt.Errorf("unknown parameter %q for application %s", key, app.Metadata.Name)
		}

		value, err := parseParameterValue(param, rawValue)
		if err != nil {
			return nil, err
		}
		result[key] = value
	}
	return result, nil
}

func parseParameterValue(param orcapi.ApplicationParameter, rawValue string) (orcapi.AppParameterValue, error) {
	switch param.Type {
	case orcapi.ApplicationParameterTypeInteger:
		n, err := strconv.ParseInt(rawValue, 10, 64)
		if err != nil {
			return orcapi.AppParameterValue{}, fmt.Errorf("parameter %q expects an integer, got %q", param.Name, rawValue)
		}
		return orcapi.AppParameterValueInteger(n), nil
	case orcapi.ApplicationParameterTypeFloatingPoint:
		f, err := strconv.ParseFloat(rawValue, 64)
		if err != nil {
			return orcapi.AppParameterValue{}, fmt.Errorf("parameter %q expects a floating point number, got %q", param.Name, rawValue)
		}
		return orcapi.AppParameterValueFloatingPoint(f), nil
	case orcapi.ApplicationParameterTypeBoolean:
		b, err := strconv.ParseBool(rawValue)
		if err != nil {
			return orcapi.AppParameterValue{}, fmt.Errorf("parameter %q expects a boolean, got %q", param.Name, rawValue)
		}
		return orcapi.AppParameterValueBoolean(b), nil
	case orcapi.ApplicationParameterTypeText, orcapi.ApplicationParameterTypeTextArea:
		return orcapi.AppParameterValueText(rawValue), nil
	case orcapi.ApplicationParameterTypeEnumeration:
		for _, opt := range param.Options {
			if opt.Value == rawValue {
				return orcapi.AppParameterValueText(rawValue), nil
			}
		}
		return orcapi.AppParameterValue{}, fmt.Errorf("parameter %q does not allow value %q", param.Name, rawValue)
	default:
		return orcapi.AppParameterValue{}, fmt.Errorf("parameter %q of type %s is not supported by the CLI", param.Name, param.Type)
	}
}

func createApp(job JobCreateCommand, app *orcapi.Application) error {
	product, err := findProductForApp(app, job.Product)
	if err != nil {
		return err
	}

	parameters, err := buildParameters(app, job.Parameters)
	if err != nil {
		return err
	}

	spec := orcapi.JobSpecification{
		ResourceSpecification: orcapi.ResourceSpecification{
			Product: product,
		},
		Application: orcapi.NameAndVersion{
			Name:    app.Metadata.Name,
			Version: app.Metadata.Version,
		},
		Name:       job.Name,
		Replicas:   1,
		Parameters: parameters,
		SshEnabled: job.SSH,
		Resources:  []orcapi.AppParameterValue{},
	}

	if job.Time > 0 {
		spec.TimeAllocation = util.OptValue(orcapi.SimpleDuration{
			Hours:   job.Time / 60,
			Minutes: job.Time % 60,
		})
	}
	if job.Folder != "" {
		file := orcapi.AppParameterValue{
			Type: orcapi.AppParameterValueTypeFile,
			Path: job.Folder,
		}
		spec.Resources = append(spec.Resources, file)
	}

	response, httpErr := orcapi.JobsCreate.Invoke(fnd.BulkRequestOf(spec))
	if httpErr != nil {
		return fmt.Errorf("failed to create job: %s", httpErr.Why)
	}

	for _, created := range response.Responses {
		fmt.Printf("Job created: %s\n", created.Id)
	}
	return nil
}

func (c JobCreateCommand) Execute() error {
	cfg, err := shared.ReadConfig()
	if err != nil {
		panic(err)
	}
	cfg.InitUCloudClient()

	if c.App == "" {
		return fmt.Errorf("this command requires an application, use --app <name>")
	}

	// Jobs are created in the context of a workspace (defaults to the
	// personal workspace if none is active).
	currentWs := c.Workspace
	if currentWs == "" {
		currentWs = cfg.CurrentWorkspace.GetOrDefault("")
	}
	if currentWs != "" {
		ws, err := findWorkspace(currentWs)
		if err != nil {
			return err
		}
		shared.SetActiveWorkspace(ws.Id)
	}

	foundApp, appErr := findApp(c.App)
	if appErr != nil {
		return appErr
	}

	return createApp(c, foundApp)
}

func (c JobDeleteCommand) Execute() error {
	// UCloud has no delete operation for jobs; terminating a job stops it
	// and releases its resources. Delete is currently an alias for terminate.
	if c.JobID == "" {
		return fmt.Errorf("this command requires a job id, use: ucloud job delete <job-id>")
	}

	cfg, err := shared.ReadConfig()
	if err != nil {
		panic(err)
	}
	cfg.InitUCloudClient()

	_, httpErr := orcapi.JobsTerminate.Invoke(fnd.BulkRequestOf(fnd.FindByStringId{
		Id: c.JobID,
	}))
	if httpErr != nil {
		return fmt.Errorf("failed to delete job: %s", httpErr.Why)
	}

	fmt.Printf("Job deleted: %s\n", c.JobID)
	return nil
}

func (c JobTerminateCommand) Execute() error {
	cfg, err := shared.ReadConfig()
	if err != nil {
		panic(err)
	}
	cfg.InitUCloudClient()

	if c.JobID == "" {
		return fmt.Errorf("this command requires a job id, use: ucloud job terminate <job-id>")
	}

	_, httpErr := orcapi.JobsTerminate.Invoke(fnd.BulkRequestOf(fnd.FindByStringId{
		Id: c.JobID,
	}))
	if httpErr != nil {
		return fmt.Errorf("failed to terminate job: %s", httpErr.Why)
	}

	fmt.Printf("Job terminated: %s\n", c.JobID)
	return nil
}

func (c JobResumeCommand) Execute() error {
	cfg, err := shared.ReadConfig()
	if err != nil {
		panic(err)
	}
	cfg.InitUCloudClient()

	if c.JobID == "" {
		return fmt.Errorf("this command requires a job id, use: ucloud job resume <job-id>")
	}

	_, httpErr := orcapi.JobsUnsuspend.Invoke(fnd.BulkRequestOf(fnd.FindByStringId{
		Id: c.JobID,
	}))
	if httpErr != nil {
		return fmt.Errorf("failed to resume job: %s", httpErr.Why)
	}

	fmt.Printf("Job resumed: %s\n", c.JobID)
	return nil
}

func (c JobAttachCommand) Execute() error {
	return fmt.Errorf("job attach not implemented")
}

func (c JobDetachCommand) Execute() error {
	return fmt.Errorf("job detach not implemented")
}

func (c JobVNCCommand) Execute() error {
	return fmt.Errorf("job vnc not implemented")
}

func (c JobOpenCommand) Execute() error {
	return fmt.Errorf("job open not implemented")
}

func (c JobWebCommand) Execute() error {
	return fmt.Errorf("job web not implemented")
}

func (c JobShellCommand) Execute() error {
	return fmt.Errorf("job shell not implemented")
}

func (c JobLogsCommand) Execute() error {
	return fmt.Errorf("job logs not implemented")
}
