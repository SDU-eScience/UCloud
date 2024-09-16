package slurm

import (
	"flag"
	"fmt"
	"net/http"
	"os"
	"os/user"
	"regexp"
	db "ucloud.dk/pkg/database"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/pkg/termio"
	"ucloud.dk/pkg/util"
)

func HandleProjectsCommand() {
	// Invoked via 'ucloud projects <subcommand> [options]'

	if os.Getuid() != 0 {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "This command must be run as root!")
		os.Exit(1)
	}

	command := ""
	if len(os.Args) >= 3 {
		command = os.Args[2]
	}

	switch {
	case isListCommand(command):
		var (
			ucloudName string
			ucloudId   string
			localName  string
			localGid   string
		)

		fs := flag.NewFlagSet("", flag.ExitOnError)
		fs.StringVar(&ucloudName, "ucloud-name", "", "Query by UCloud name, supports regex")
		fs.StringVar(&ucloudId, "ucloud-id", "", "Query by UCloud ID, supports regex")
		fs.StringVar(&localName, "local-name", "", "Query by local name, supports regex")
		fs.StringVar(&localGid, "local-id", "", "Query by local GID, supports regex")
		_ = fs.Parse(os.Args[3:])

		result, err := cliProjectsList.Invoke(
			cliProjectsListRequest{
				UCloudName: ucloudName,
				UCloudId:   ucloudId,
				LocalName:  localName,
				LocalGid:   localGid,
			},
		)

		cliHandleError("listing projects", err)

		t := termio.Table{}
		t.AppendHeader("UCloud name")
		t.AppendHeader("UCloud ID")
		t.AppendHeader("Local name")
		t.AppendHeader("Local GID")
		for _, mapping := range result {
			t.Cell("%v", mapping.UCloudName)
			t.Cell("%v", mapping.UCloudId)
			t.Cell("%v", mapping.LocalName)
			t.Cell("%v", mapping.LocalGid)
		}
		t.Print()
		termio.WriteLine("")

	case isReplaceCommand(command):
		var (
			oldGid uint64
			newGid uint64
		)

		fs := flag.NewFlagSet("", flag.ExitOnError)
		fs.Uint64Var(&oldGid, "old-id", 0, "The old GID of the project to replace")
		fs.Uint64Var(&newGid, "new-id", 0, "The new GID of the project to replace")
		_ = fs.Parse(os.Args[3:])

		if oldGid == 0 || newGid == 0 {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Both old-id and new-id must be specified")
			os.Exit(1)
		}

		_, err := cliProjectsReplace.Invoke(cliProjectsReplaceRequest{
			OldGid: uint32(oldGid),
			NewGid: uint32(newGid),
		})

		cliHandleError("replacing project id", err)
		termio.WriteStyledLine(termio.Bold, termio.Green, 0, "OK")

	case isAddCommand(command):
		termio.WriteStyledLine(
			termio.Bold,
			termio.Red,
			0,
			"It is not possible to create a project mapping through the CLI. All projects must be created "+
				"through UCloud/Core unless completely unmanaged. It is possible to replace one project mapping "+
				"with another through the 'ucloud projects replace' command.",
		)
		os.Exit(1)

	case isDeleteCommand(command):
		termio.WriteStyledLine(
			termio.Bold,
			termio.Red,
			0,
			"It is not possible to delete a project mapping through the CLI. Instead, such projects have to be "+
				"deleted through UCloud. As of 30/08/24 this functionality is not currently implemented.",
		)
		os.Exit(1)
	}
}

func HandleProjectsCommandServer() {
	cliProjectsList.Handler(func(r *ipc.Request[cliProjectsListRequest]) ipc.Response[[]cliProject] {
		if r.Uid != 0 {
			return ipc.Response[[]cliProject]{
				StatusCode: http.StatusForbidden,
			}
		}

		var localNameRegex *regexp.Regexp
		err := cliValidateRegexes(
			nil, "ucloud-name", r.Payload.UCloudName,
			nil, "ucloud-id", r.Payload.UCloudId,
			&localNameRegex, "local-name", r.Payload.LocalName,
			nil, "local-gid", r.Payload.LocalGid,
		)
		if err != nil {
			return ipc.Response[[]cliProject]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: err.Error(),
			}
		}

		result, err := db.NewTx2(func(tx *db.Transaction) ([]cliProject, error) {
			rows := db.Select[struct {
				Id   string
				Name string
				Gid  string
			}](
				tx,
				`
					select
						c.ucloud_project_id as id,
						p.ucloud_project#>>'{specification,title}' as name,
						c.gid as gid
					from
						project_connections c
						join tracked_projects p on c.ucloud_project_id = p.project_id
					where
						(
							:ucloud_id = ''
							or c.ucloud_project_id ~ :ucloud_id
						)
						and (
							:ucloud_name = ''
							or p.ucloud_project#>>'{specification,title}' ~ :ucloud_name
						)
						and (
							:local_gid = ''
							or cast(c.gid as text) ~ :local_gid
						)
				`,
				db.Params{
					"ucloud_id":   r.Payload.UCloudId,
					"ucloud_name": r.Payload.UCloudName,
					"local_gid":   r.Payload.LocalGid,
				},
			)

			if err := tx.ConsumeError(); err != nil {
				return nil, fmt.Errorf("invalid regex supplied (%s)", err)
			}

			var result []cliProject
			for _, row := range rows {
				groupIdString := fmt.Sprint(row.Gid)
				groupName := groupIdString
				group, err := user.LookupGroupId(groupIdString)
				if err == nil {
					groupName = group.Name
				}

				if r.Payload.LocalName != "" {
					if !localNameRegex.MatchString(groupName) {
						continue
					}
				}

				result = append(result, cliProject{
					UCloudName: row.Name,
					UCloudId:   row.Id,
					LocalName:  groupName,
					LocalGid:   row.Gid,
				})
			}

			return result, nil
		})

		if err != nil {
			return ipc.Response[[]cliProject]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: err.Error(),
			}
		}

		return ipc.Response[[]cliProject]{
			StatusCode: http.StatusOK,
			Payload:    result,
		}
	})

	cliProjectsReplace.Handler(func(r *ipc.Request[cliProjectsReplaceRequest]) ipc.Response[util.Empty] {
		if r.Uid != 0 {
			return ipc.Response[util.Empty]{
				StatusCode: http.StatusForbidden,
			}
		}

		_, err := user.LookupGroupId(fmt.Sprint(r.Payload.NewGid))
		if err != nil {
			return ipc.Response[util.Empty]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: fmt.Sprintf("Unable to lookup group with ID %v", r.Payload.NewGid),
			}
		}

		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					update project_connections
					set gid = :new_gid 
					where
						gid = :old_gid
				`,
				db.Params{
					"old_gid": r.Payload.OldGid,
					"new_gid": r.Payload.NewGid,
				},
			)
		})

		return ipc.Response[util.Empty]{
			StatusCode: http.StatusOK,
		}
	})
}

type cliProjectsListRequest struct {
	UCloudName string
	UCloudId   string
	LocalName  string
	LocalGid   string
}

type cliProject struct {
	UCloudName string
	UCloudId   string
	LocalName  string
	LocalGid   string
}

type cliProjectsReplaceRequest struct {
	OldGid uint32
	NewGid uint32
}

var (
	cliProjectsList    = ipc.NewCall[cliProjectsListRequest, []cliProject]("cli.slurm.projects.list")
	cliProjectsReplace = ipc.NewCall[cliProjectsReplaceRequest, util.Empty]("cli.slurm.projects.replace")
)
