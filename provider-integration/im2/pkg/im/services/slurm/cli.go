package slurm

import (
	"flag"
	"fmt"
	"net/http"
	"os"
	"os/user"
	"regexp"
	"strconv"
	db "ucloud.dk/pkg/database"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/termio"
	"ucloud.dk/pkg/util"
)

func InitCliServer() {
	if cfg.Mode != cfg.ServerModeServer {
		log.Error("InitCliServer called in the wrong mode!")
		return
	}

	cliProjectsList.Handler(func(r *ipc.Request[cliProjectsListRequest]) ipc.Response[[]cliProject] {
		if r.Uid != 0 {
			return ipc.Response[[]cliProject]{
				StatusCode: http.StatusForbidden,
			}
		}

		_, err := regexp.CompilePOSIX(r.Payload.UCloudName)
		if err != nil {
			return ipc.Response[[]cliProject]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: fmt.Sprintf("ucloud-name: %s", err),
			}
		}

		_, err = regexp.CompilePOSIX(r.Payload.UCloudId)
		if err != nil {
			return ipc.Response[[]cliProject]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: fmt.Sprintf("ucloud-id: %s", err),
			}
		}

		localNameRegex, err := regexp.CompilePOSIX(r.Payload.LocalName)
		if err != nil {
			return ipc.Response[[]cliProject]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: fmt.Sprintf("local-name: %s", err),
			}
		}

		_, err = regexp.CompilePOSIX(r.Payload.LocalGid)
		if err != nil {
			return ipc.Response[[]cliProject]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: fmt.Sprintf("local-gid: %s", err),
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

	cliUsersList.Handler(func(r *ipc.Request[cliUsersListRequest]) ipc.Response[[]cliUserMapping] {
		if r.Uid != 0 {
			return ipc.Response[[]cliUserMapping]{
				StatusCode: http.StatusForbidden,
			}
		}

		_, err := regexp.CompilePOSIX(r.Payload.UCloudName)
		if err != nil {
			return ipc.Response[[]cliUserMapping]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: fmt.Sprintf("ucloud-name: %s", err),
			}
		}

		localRegex, err := regexp.CompilePOSIX(r.Payload.LocalName)
		if err != nil {
			return ipc.Response[[]cliUserMapping]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: fmt.Sprintf("ucloud-name: %s", err),
			}
		}

		_, err = regexp.CompilePOSIX(r.Payload.LocalUid)
		if err != nil {
			return ipc.Response[[]cliUserMapping]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: fmt.Sprintf("local-uid: %s", err),
			}
		}

		result, err := db.NewTx2(func(tx *db.Transaction) ([]cliUserMapping, error) {
			rows := db.Select[struct {
				UCloudUsername string
				Uid            uint32
			}](
				tx,
				`
					select ucloud_username, uid
					from connections
					where
						(
							:ucloud_username = ''
							or ucloud_username ~ :ucloud_username
						)
						and (
						    :local_uid = ''
						    or cast(uid as text) ~ :local_uid
						)
					order by ucloud_username
					limit 250
			    `,
				db.Params{
					"ucloud_username": r.Payload.UCloudName,
					"local_uid":       r.Payload.LocalUid,
				},
			)

			if err := tx.ConsumeError(); err != nil {
				return nil, fmt.Errorf("invalid regex supplied (%s)", err)
			}

			var result []cliUserMapping
			for _, row := range rows {
				uidString := fmt.Sprint(row.Uid)
				localUsername := ""
				localUser, _ := user.LookupId(uidString)
				if localUser != nil {
					localUsername = localUser.Username
				}

				if r.Payload.LocalName != "" {
					if !localRegex.MatchString(localUsername) {
						continue
					}
				}

				result = append(result, cliUserMapping{
					UCloudName: row.UCloudUsername,
					LocalName:  localUsername,
					Uid:        row.Uid,
				})
			}

			return result, nil
		})

		if err != nil {
			return ipc.Response[[]cliUserMapping]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: fmt.Sprintf("%s", err),
			}
		}
		return ipc.Response[[]cliUserMapping]{
			StatusCode: http.StatusOK,
			Payload:    result,
		}
	})

	cliUsersAdd.Handler(func(r *ipc.Request[cliUsersAddRequest]) ipc.Response[util.Empty] {
		if r.Uid != 0 {
			return ipc.Response[util.Empty]{
				StatusCode: http.StatusForbidden,
			}
		}

		uinfo, err := user.Lookup(r.Payload.LocalName)
		if err != nil {
			return ipc.Response[util.Empty]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: fmt.Sprintf("%s", err),
			}
		}

		nuid, _ := strconv.Atoi(uinfo.Uid)
		err = ctrl.RegisterConnectionComplete(r.Payload.UCloudName, uint32(nuid), true)
		if err != nil {
			return ipc.Response[util.Empty]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: fmt.Sprintf("%s", err),
			}
		}

		return ipc.Response[util.Empty]{
			StatusCode: http.StatusOK,
		}
	})

	cliUsersDelete.Handler(func(r *ipc.Request[cliUsersDeleteRequest]) ipc.Response[cliUsersDeleteResponse] {
		if r.Uid != 0 {
			return ipc.Response[cliUsersDeleteResponse]{
				StatusCode: http.StatusForbidden,
			}
		}

		// TODO Make sure the Core actually deletes the personal provider projects in this case

		var errors []string
		for _, uid := range r.Payload.Uids {
			err := ctrl.RemoveConnection(uid)
			if err != nil {
				errors = append(errors, err.Error())
			} else {
				errors = append(errors, "")
			}
		}

		return ipc.Response[cliUsersDeleteResponse]{
			StatusCode: http.StatusOK,
			Payload:    cliUsersDeleteResponse{Failures: errors},
		}
	})
}

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

func HandleUsersCommand() {
	// Invoked via 'ucloud users <subcommand> [options]'

	if os.Getuid() != 0 {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "This command must be run as root!")
		os.Exit(1)
	}

	var (
		ucloudName string
		localName  string
		localUid   string
	)

	fs := flag.NewFlagSet("", flag.ExitOnError)
	fs.StringVar(&ucloudName, "ucloud-name", "", "Query by UCloud name, supports regex")
	fs.StringVar(&localName, "local-name", "", "Query by a local username, supports regex")
	fs.StringVar(&localUid, "local-uid", "", "Query by a local UID, supports regex")

	printUsers := func() []cliUserMapping {
		result, err := cliUsersList.Invoke(cliUsersListRequest{
			UCloudName: ucloudName,
			LocalName:  localName,
			LocalUid:   localUid,
		})

		cliHandleError("fetching user mapping", err)

		t := termio.Table{}
		t.AppendHeader("UCloud username")
		t.AppendHeader("Local username")
		t.AppendHeader("Local UID")
		for _, mapping := range result {
			t.Cell("%v", mapping.UCloudName)
			t.Cell("%v", mapping.LocalName)
			t.Cell("%v", mapping.Uid)
		}
		t.Print()
		termio.WriteLine("")
		return result
	}

	command := ""
	if len(os.Args) >= 3 {
		command = os.Args[2]
		_ = fs.Parse(os.Args[3:])
	}

	switch {
	case isListCommand(command):
		_ = printUsers()

	case isAddCommand(command):
		if ucloudName == "" {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "ucloud-name must be supplied")
			os.Exit(1)
		}

		if localName == "" {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "local-name must be supplied")
			os.Exit(1)
		}

		if localUid != "" {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "local-uid is ignored for this command")
			os.Exit(1)
		}

		_, err := cliUsersAdd.Invoke(
			cliUsersAddRequest{
				UCloudName: ucloudName,
				LocalName:  localName,
			},
		)

		cliHandleError("adding user", err)
		termio.WriteStyledLine(termio.Bold, termio.Green, 0, "OK")

	case isDeleteCommand(command):
		if ucloudName == "" && localName == "" && localUid == "" {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "either ucloud-name, local-name or local-uid must be supplied")
			os.Exit(1)
		}

		termio.WriteStyledLine(termio.Bold, 0, 0, "This command will delete the following connections:")
		termio.WriteLine("")

		rows := printUsers()
		termio.WriteLine("")
		if len(rows) == 0 {
			termio.WriteLine("No such account, try again with a different query.")
			os.Exit(0)
		}

		shouldDelete, _ := termio.ConfirmPrompt(
			"Are you sure you want to delete these user mappings? No users will be deleted from the system.",
			termio.ConfirmValueFalse,
			termio.ConfirmPromptExitOnCancel,
		)

		if !shouldDelete {
			os.Exit(0)
		}

		var uids []uint32
		for _, row := range rows {
			uids = append(uids, row.Uid)
		}

		resp, err := cliUsersDelete.Invoke(cliUsersDeleteRequest{Uids: uids})
		cliHandleError("deleting users", err)

		for i, failure := range resp.Failures {
			if failure != "" {
				termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Failed to delete user %s (uid: %v): %s", rows[i].LocalName, rows[i].Uid, failure)
			}
		}

		termio.WriteStyledLine(termio.Bold, termio.Green, 0, "OK")
	}
}

type cliUsersListRequest struct {
	UCloudName string
	LocalName  string
	LocalUid   string
}

type cliUserMapping struct {
	UCloudName string
	LocalName  string
	Uid        uint32
}

type cliUsersAddRequest struct {
	UCloudName string
	LocalName  string
}

type cliUsersDeleteRequest struct {
	Uids []uint32
}

type cliUsersDeleteResponse struct {
	Failures []string // Empty string if none. Will have the same length as request.Uids
}

var (
	cliUsersList   = ipc.NewCall[cliUsersListRequest, []cliUserMapping]("slurm.cli.users.list")
	cliUsersAdd    = ipc.NewCall[cliUsersAddRequest, util.Empty]("slurm.cli.users.add")
	cliUsersDelete = ipc.NewCall[cliUsersDeleteRequest, cliUsersDeleteResponse]("slurm.cli.users.delete")
)

func isListCommand(command string) bool {
	switch command {
	case "ls":
		fallthrough
	case "list":
		return true
	default:
		return false
	}
}

func isAddCommand(command string) bool {
	switch command {
	case "add":
		return true
	default:
		return false
	}
}

func isDeleteCommand(command string) bool {
	switch command {
	case "del":
		fallthrough
	case "delete":
		fallthrough
	case "rm":
		fallthrough
	case "remove":
		return true
	default:
		return false
	}
}

func isReplaceCommand(command string) bool {
	switch command {
	case "replace":
		return true
	default:
		return false
	}
}

func cliHandleError(context string, err error) {
	if err == nil {
		return
	}

	if context == "" {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "%s", context, err.Error())
	} else {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "%s: %s", context, err.Error())
	}

	os.Exit(1)
}
