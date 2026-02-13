package slurm

import (
	"encoding/json"
	"flag"
	"fmt"
	"net/http"
	"os"
	"regexp"
	"slices"
	"strings"
	"time"

	slurmcli "ucloud.dk/pkg/external/slurm"
	"ucloud.dk/pkg/external/user"
	"ucloud.dk/pkg/ipc"
	"ucloud.dk/shared/pkg/cli"
	db "ucloud.dk/shared/pkg/database"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/termio"
)

func HandleJobsCommand() {
	// Invoked via 'ucloud jobs <subcommand> [options]'
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
		req := cliJobsListRequest{}
		req.Parse()

		jobs, err := cliJobsList.Invoke(req)
		cli.HandleError("listing jobs", err)

		t := termio.Table{}
		t.AppendHeader("Submitted at")
		t.AppendHeader("UCloud ID")
		t.AppendHeader("Slurm ID")
		t.AppendHeader("State")
		t.AppendHeader("Application")
		t.AppendHeader("Project (local)")

		for _, job := range jobs {
			t.Cell("%v", cli.FormatTime(job.Job.CreatedAt))
			t.Cell("%v", job.Job.Id)
			t.Cell("%v", job.SlurmId)
			t.Cell("%v", job.Job.Status.State)
			t.Cell("%v", job.Job.Specification.Application.Name)
			if job.LocalGid != 0 {
				t.Cell("%v", job.LocalGroupName)
			} else {
				t.Cell("%v", job.LocalUsername)
			}
		}

		t.Print()

	case cli.IsGetCommand(command):
		req := cliJobsListRequest{IncludeSlurmStats: true}
		req.Parse()

		jobs, err := cliJobsList.Invoke(req)
		cli.HandleError("listing jobs", err)

		if len(jobs) > 1 {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Query has returned more than one job. Please be more specific in your query.")
			os.Exit(1)
		}

		if len(jobs) == 0 {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Query has returned no jobs. Please try again with a different query.")
			os.Exit(1)
		}

		job := jobs[0]
		jobSpec := &job.Job.Specification

		f := termio.Frame{}
		{
			f.AppendTitle("UCloud metadata")

			f.AppendField("ID", job.Job.Id)
			f.AppendField("Submitted at", cli.FormatTime(job.Job.CreatedAt))
			if job.Job.Status.StartedAt.IsSet() {
				f.AppendField("Started at", cli.FormatTime(job.Job.Status.StartedAt.Value))
			}
			f.AppendField("Current state", string(job.Job.Status.State))
			if job.SlurmJob != nil && job.SlurmJob.State == "FAILED" && job.Job.Status.State == orc.JobStateSuccess {
				f.AppendField(
					"",
					"This job had a non-zero exit code which Slurms considers a failure but UCloud considers a success",
				)
			}

			f.AppendSeparator()

			f.AppendField("Submitted by", job.Job.Owner.CreatedBy)
			if job.Job.Owner.Project.Value != "" {
				f.AppendField("Project", fmt.Sprintf("%v (ID: %v)", job.WorkspaceTitle, job.Job.Owner.Project.Value))
			}
			f.AppendSeparator()

			f.AppendField("Application", jobSpec.Application.Name)
			f.AppendField("Version", jobSpec.Application.Version)
			f.AppendSeparator()

			f.AppendField("Nodes", fmt.Sprint(jobSpec.Replicas))
			f.AppendField("Machine slice", jobSpec.Product.Id)
			f.AppendField("Machine type", jobSpec.Product.Category)
			if jobSpec.TimeAllocation.IsSet() {
				alloc := jobSpec.TimeAllocation.Value
				duration := time.Duration(0)
				duration += time.Duration(alloc.Hours) * time.Hour
				duration += time.Duration(alloc.Minutes) * time.Minute
				duration += time.Duration(alloc.Seconds) * time.Second

				f.AppendField("Time allocation", fmt.Sprint(duration))
			}
			f.AppendSeparator()
		}

		{
			s := job.SlurmJob
			f.AppendTitle("Slurm metadata")
			f.AppendField("ID", fmt.Sprint(job.SlurmId))
			if job.SlurmJob != nil {
				f.AppendField("Name", s.Name)
				f.AppendField("State", s.State)
			}
			f.AppendSeparator()

			f.AppendField("Account", fmt.Sprint(job.Account))
			f.AppendField("Partition", fmt.Sprint(job.Partition))
			if job.SlurmJob != nil {
				f.AppendField("QoS", s.QoS)
			}
			f.AppendSeparator()

			{
				maxLength := max(len(job.LocalUsername), len(job.LocalGroupName))
				usernamePadding := strings.Repeat(" ", max(0, maxLength-len(job.LocalUsername)))
				groupPadding := strings.Repeat(" ", max(0, maxLength-len(job.LocalGroupName)))
				f.AppendField("User", fmt.Sprintf("%v%v (UID: %v)", job.LocalUsername, usernamePadding, job.LocalUid))
				f.AppendField("Group", fmt.Sprintf("%v%v (GID: %v)", job.LocalGroupName, groupPadding, job.LocalGid))
			}
			f.AppendSeparator()

			if job.SlurmJob != nil {
				f.AppendField("Allocated nodes", s.NodeList)
				f.AppendSeparator()

				f.AppendField("Elapsed", fmt.Sprint(time.Duration(s.Elapsed)*time.Second))
				f.AppendField("Time limit", fmt.Sprint(time.Duration(s.TimeLimit)*time.Second))
				f.AppendSeparator()

				if len(s.AllocTRES) > 0 {
					var keys []string
					for k, _ := range s.AllocTRES {
						keys = append(keys, k)
					}
					slices.Sort(keys)

					for _, k := range keys {
						val, _ := s.AllocTRES[k]
						f.AppendField(fmt.Sprintf("TRES/%v", k), fmt.Sprint(val))
					}
					f.AppendSeparator()
				}
			}
		}

		{
			argBuilder := orc.DefaultArgBuilder(func(ucloudPath string) string {
				return ucloudPath
			})

			f.AppendTitle("Application parameters")

			app := &job.Job.Status.ResolvedApplication.Value

			for k, v := range job.Job.Specification.Parameters {
				ok := false
				pv := orc.ParamAndValue{Value: v}

				for _, p := range app.Invocation.Parameters {
					if p.Name == k {
						pv.Parameter = p
						ok = true
						break
					}
				}

				if !ok {
					continue
				}

				f.AppendField(k, argBuilder(pv))
			}

			for _, mount := range job.Job.Specification.Resources {
				if mount.Type == orc.AppParameterValueTypeFile {
					f.AppendField("Mounted folder", mount.Path)
				} else if mount.Type == orc.AppParameterValueTypeIngress {
					f.AppendField("Public link", mount.Id)
				} else if mount.Type == orc.AppParameterValueTypeNetwork {
					f.AppendField("Public IP", mount.Id)
				}
			}
		}

		f.Print()
		termio.WriteLine("")

	case cli.IsAddCommand(command):
		termio.WriteStyledLine(
			termio.Bold,
			termio.Red,
			0,
			"It is not possible to add new jobs into the system through the command-line interface.",
		)

		os.Exit(1)
	}
}

func HandleJobsCommandServer() {
	cliJobsList.Handler(func(r *ipc.Request[cliJobsListRequest]) ipc.Response[[]cliJob] {
		if r.Uid != 0 {
			return ipc.Response[[]cliJob]{
				StatusCode: http.StatusForbidden,
			}
		}

		var (
			stateRegex,
			ucloudIdRegex,
			slurmIdRegex,
			applicationRegex,
			localUsernameRegex,
			localGroupNameRegex,
			localUidRegex,
			localGidRegex,
			ucloudProjectIdRegex,
			partitionRegex *regexp.Regexp
		)

		err := cli.ValidateRegexes(
			&stateRegex, "state", r.Payload.State,
			&ucloudIdRegex, "ucloud-id", r.Payload.UCloudId,
			&slurmIdRegex, "slurm-id", r.Payload.SlurmId,
			&applicationRegex, "application", r.Payload.Application,
			&localUsernameRegex, "local-username", r.Payload.LocalUsername,
			&localGroupNameRegex, "local-group-name", r.Payload.LocalGroupName,
			&localUidRegex, "local-uid", r.Payload.LocalUid,
			&localGidRegex, "local-gid", r.Payload.LocalGid,
			&partitionRegex, "partition", r.Payload.Partition,
			nil, "nodes", r.Payload.Nodes,
			&ucloudProjectIdRegex, "ucloud-project-id", r.Payload.UCloudProjectId,
		)

		if err != nil {
			return ipc.Response[[]cliJob]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: err.Error(),
			}
		}

		categoryList := []string{}
		if r.Payload.Partition != "" {
			for category, machineConfig := range ServiceConfig.Compute.Machines {
				if partitionRegex.MatchString(machineConfig.Partition) {
					categoryList = append(categoryList, category)
				}
			}
		}

		jobs := db.NewTx(func(tx *db.Transaction) []cliJob {
			rows := db.Select[struct {
				JobId    string
				Uid      uint32
				Gid      uint32
				Title    string
				Resource string
			}](
				tx,
				`
					select
						j.job_id,
						c.uid,
						p.gid,
						tp.ucloud_project#>>'{specification,title}' as title,
						j.resource
					from
						tracked_jobs j
						left join connections c on j.created_by = c.ucloud_username
						left join project_connections p on j.project_id = p.ucloud_project_id
						left join tracked_projects tp on p.ucloud_project_id = tp.project_id
					where
						(
							:state = ''
							or j.state ~ state
						)
						and (
							:ucloud_id = ''
							or j.job_id ~ :ucloud_id
						)
						and (
							:application = ''
							or j.resource#>>'{specification,application,name}' ~ :application
						)
						and (
							:local_uid = ''
							or cast(c.uid as text) ~ :local_uid
						)
						and (
							:local_gid = ''
							or cast(p.gid as text) ~ :local_gid
						)
						and (
							cardinality(cast(:category as text[])) = 0
							or j.product_category = some(:category)
						)
						and (
							:ucloud_project_id = ''
							or tp.ucloud_project#>>'{id}' ~ :ucloud_project_id
						)
						and (
						    :nodes = ''
							or array_to_string(j.allocated_nodes, ',') ~ :nodes -- This feels wrong
						)
					order by
						j.resource#>>'{createdAt}' desc
			    `,
				db.Params{
					"state":             r.Payload.State,
					"ucloud_id":         r.Payload.UCloudId,
					"application":       r.Payload.Application,
					"local_uid":         r.Payload.LocalUid,
					"local_gid":         r.Payload.LocalGid,
					"category":          categoryList,
					"ucloud_project_id": r.Payload.UCloudProjectId,
					"nodes":             r.Payload.Nodes,
				},
			)

			var jobs []cliJob

			for _, row := range rows {
				var job orc.Job
				err = json.Unmarshal([]byte(row.Resource), &job)
				if err != nil {
					continue
				}

				slurmInfo, ok := parseJobProviderId(job.ProviderGeneratedId)
				if !ok {
					continue
				}

				if row.Title == "" {
					row.Title = job.Owner.CreatedBy
				}

				uidString := fmt.Sprint(row.Uid)
				gidString := fmt.Sprint(row.Gid)

				username := uidString
				groupName := gidString
				uinfo, err := user.LookupId(uidString)
				if err == nil {
					username = uinfo.Username
				}

				ginfo, err := user.LookupGroupId(gidString)
				if err == nil {
					groupName = ginfo.Name
				}

				if localUsernameRegex != nil && !localUsernameRegex.MatchString(username) {
					continue
				}

				if localGroupNameRegex != nil && !localGroupNameRegex.MatchString(groupName) {
					continue
				}

				machineConfig, ok := ServiceConfig.Compute.Machines[job.Specification.Product.Category]
				if !ok {
					continue
				}

				if slurmIdRegex != nil && !slurmIdRegex.MatchString(fmt.Sprint(slurmInfo.SlurmId)) {
					continue
				}

				elem := cliJob{
					Job:            &job,
					SlurmId:        slurmInfo.SlurmId,
					WorkspaceTitle: row.Title,
					LocalUid:       row.Uid,
					LocalGid:       row.Gid,
					LocalUsername:  username,
					LocalGroupName: groupName,
					Partition:      machineConfig.Partition,
					Account:        slurmInfo.BelongsToAccount,
				}

				if r.Payload.IncludeSlurmStats {
					elem.SlurmJob = SlurmClient.JobQuery(slurmInfo.SlurmId)
				}

				jobs = append(jobs, elem)
			}

			return jobs
		})

		return ipc.Response[[]cliJob]{
			StatusCode: http.StatusOK,
			Payload:    jobs,
		}
	})
}

type cliJobsListRequest struct {
	State             string
	UCloudId          string
	SlurmId           string
	Application       string
	LocalUsername     string
	LocalGroupName    string
	LocalUid          string
	LocalGid          string
	Partition         string
	UCloudProjectId   string
	Nodes             string
	IncludeSlurmStats bool
}

func (c *cliJobsListRequest) Parse() {
	fs := flag.NewFlagSet("", flag.ExitOnError)
	fs.StringVar(&c.State, "state", "", "The UCloud state of the job, supports regex")
	fs.StringVar(&c.UCloudId, "ucloud-id", "", "The UCloud ID of the job, supports regex")
	fs.StringVar(&c.UCloudProjectId, "ucloud-project-id", "", "The UCloud project ID of the job, supports regex")
	fs.StringVar(&c.SlurmId, "slurm-id", "", "The Slurm ID of the job, supports regex")
	fs.StringVar(&c.Application, "application", "", "The UCloud application name of the job, supports regex")
	fs.StringVar(&c.LocalUsername, "local-user", "", "The local username of the user who started the job, supports regex")
	fs.StringVar(&c.LocalGroupName, "local-group", "", "The local group name of the group who submitted the job, supports regex")
	fs.StringVar(&c.LocalUid, "local-uid", "", "The local UID of the user who submitted the job, supports regex")
	fs.StringVar(&c.LocalGid, "local-gid", "", "The local gid of the group who submitted the job, supports regex")
	fs.StringVar(&c.Partition, "partition", "", "The Slurm partition which the job was submitted to, supports regex")
	fs.StringVar(&c.Nodes, "nodes", "", "A regex matching one or more nodes which the job was assigned to")
	_ = fs.Parse(os.Args[3:])
}

type cliJob struct {
	Job            *orc.Job
	SlurmId        int
	WorkspaceTitle string
	Partition      string
	Account        string

	LocalUid       uint32
	LocalGid       uint32
	LocalUsername  string
	LocalGroupName string

	// Information about the SlurmJob. Only included if cliJobsListRequest.IncludeSlurmStats is true, can be null if
	// no longer present in the database.
	SlurmJob *slurmcli.Job
}

var (
	cliJobsList = ipc.NewCall[cliJobsListRequest, []cliJob]("cli.slurm.jobs.list")
)
