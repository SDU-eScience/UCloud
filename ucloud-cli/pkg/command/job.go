package command

import (
	"fmt"

	"ucloud.dk/shared/pkg/cli"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/ucloud_cli/pkg/shared"
)

type JobGetCommand struct {
	JobID string `positional:"job-id" usage:"Job ID"`
}

type JobListCommand struct {
	Workspace string `flag:"workspace" usage:"Workspace to list jobs for"`
	State     string `flag:"state" usage:"Job state"`
	App       string `flag:"app" usage:"Application name"`
	Provider  string `flag:"provider" usage:"Provider name"`
}
type JobCreateCommand struct {
	Application string            `flag:"app" usage:"Application name"`
	Product     string            `flag:"prod" usage:"Product name"`
	Name        string            `flag:"name" usage:"Job name"`
	Time        int               `flag:"time" usage:"Time in minutes"`
	SSH         bool              `flag:"ssh" usage:"Use SSH"`
	Folder      string            `flag:"folder" usage:"Folder name"`
	PublicLink  string            `flag:"public-link" usage:"Public link"`
	Parameters  map[string]string `flag:"param" usage:"eg. image=ubuntu"`
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
	return fmt.Errorf("job rename not implemented")
}

func (c JobSearchCommand) Execute() error {
	return fmt.Errorf("job search not implemented")
}

func (c JobSuspendCommand) Execute() error {
	return fmt.Errorf("job suspend not implemented")
}

func (c JobExtendCommand) Execute() error {
	return fmt.Errorf("job extend not implemented")
}

func (c JobGetCommand) Execute() error {
	return fmt.Errorf("job get not implemented")
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

func (c JobCreateCommand) Execute() error {
	return fmt.Errorf("job create not implemented")
}

func (c JobDeleteCommand) Execute() error {
	return fmt.Errorf("job delete not implemented")
}

func (c JobTerminateCommand) Execute() error {
	return fmt.Errorf("job terminate not implemented")
}

func (c JobResumeCommand) Execute() error {
	return fmt.Errorf("job resume not implemented")
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
